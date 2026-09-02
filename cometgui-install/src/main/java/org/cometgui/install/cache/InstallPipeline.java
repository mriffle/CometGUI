/*
 * CometGUI -- Comet to Percolator proteomics search workflow with provenance.
 * Copyright (C) 2026 The CometGUI authors.
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License, version 3, as published
 * by the Free Software Foundation. It is distributed WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for details.
 *
 * The full licence is the LICENSE file at the root of this repository. If it
 * is missing, see <https://www.gnu.org/licenses/gpl-3.0.html>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.cometgui.install.cache;

import java.io.IOException;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.HashService;
import org.cometgui.domain.tools.InstallPhase;
import org.cometgui.domain.tools.InstallProgress;
import org.cometgui.domain.tools.InstallProgressListener;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.install.archive.ArtefactExtractor;
import org.cometgui.install.download.DownloadCancellation;
import org.cometgui.install.registry.ArchiveMember;
import org.cometgui.install.registry.ArtefactCompanion;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.install.verify.VerifiedArtefact;
import org.cometgui.provenance.json.CanonicalTimestamp;

/**
 * One install, run one step at a time.
 *
 * <p>The specification's eight steps are {@link InstallStep}, and this class runs exactly those.
 * Its constructor builds an action for every constant of that enumeration and refuses to be built
 * if one is missing, so a ninth step that nobody implements stops the installer rather than being
 * skipped quietly.
 *
 * <h2>Why the steps are separately runnable</h2>
 *
 * <p>Two reasons, and neither is testing. {@link org.cometgui.domain.tools.InstallHandle#cancel()}
 * promises that an install "stops when it reaches a point where it safely can", and a step boundary
 * is that point -- so cancellation is checked between steps and nowhere else. And {@code R-TOOL-04}
 * requires an interrupted install to leave nothing that reports itself installed, which is a claim
 * about every step boundary rather than about one of them.
 *
 * <p>{@link ArtefactInstaller#install} is the ordinary way in and runs the loop itself. Driving the
 * steps by hand runs the same actions in the same order over the same object; it does not reach a
 * seam production leaves alone.
 *
 * <h2>Everything is built somewhere it can be thrown away</h2>
 *
 * <p>The payload is extracted, checked, fixed up and probed inside a staging directory under {@code
 * cache/staging}, and only step 7 puts anything in the tool cache. That is not tidiness: the
 * extraction guard writes nothing outside the directory it is given, but a rejection part way
 * through leaves what it had already written, so the directory it is given has to be one nobody
 * will ever look in. {@link #close()} deletes it unless the install finished.
 *
 * <h2>What happens if the final move is refused</h2>
 *
 * <p>Step 7 is this product's most contended file-system operation, and the decision taken here is
 * deliberate and narrow.
 *
 * <ul>
 *   <li><strong>{@link AtomicMoveNotSupportedException} is re-thrown, never handled.</strong> It
 *       cannot happen -- staging and the tool cache are two directories under one root, so the move
 *       is a same-file-system rename -- and if it ever did, the honest answer is to fail. A
 *       copy-then-delete fallback is <em>not</em> atomic, and installing it here would quietly
 *       replace the guarantee {@code R-TOOL-04} rests on with a hope.
 *   <li><strong>Any other file-system refusal is reported as {@link
 *       InstallFailure#CACHE_CONTENDED}, with no retry.</strong> On Windows a rename can be refused
 *       because another process holds a file open -- {@code STATUS.rst} names the Provenance UI, a
 *       virus scanner and a sync client as exactly such processes -- and it arrives as an access
 *       denial or a sharing violation. <strong>There is no retry loop</strong>, because no machine
 *       in this project can produce that condition and a retry policy nobody can test is a
 *       protection nobody has seen work. The diagnostic names the directory, says which kinds of
 *       process cause it and what to do, and the staging directory is discarded, so the cache is
 *       left exactly as it was.
 *   <li>{@link NoSuchFileException} and {@link DirectoryNotEmptyException} are re-thrown too: they
 *       mean something structural, not contention, and labelling them contention would send a
 *       reader looking for a virus scanner that is not there.
 * </ul>
 */
public final class InstallPipeline implements AutoCloseable {

    /** What one step does. */
    @FunctionalInterface
    private interface StepAction {
        void run() throws IOException;
    }

    /** The record being installed. */
    private final ArtefactRecord record;

    /** Steps 1 and 2. */
    private final VerifiedArtefactSource source;

    /** Step 3. */
    private final ArtefactExtractor extractor;

    /** Step 5. */
    private final PlatformFixups fixups;

    /** Step 6, implemented by phase 05 units 6 and 7. */
    private final ToolProbe probe;

    /** Step 4's digests and step 8's recorded checksums. */
    private final HashService hashes;

    /** Read once, at step 8, for the marker's timestamp. */
    private final Clock clock;

    /** Told where the install has got to, before every step. */
    private final InstallProgressListener listener;

    /** Asked between steps whether the caller still wants the install. */
    private final DownloadCancellation cancellation;

    /** The {@code R-TOOL-05} lock, held for the whole install. */
    private final InstallLock lock;

    /** The directory this install builds in and throws away, or {@code null} when there is none. */
    private final Path staging;

    /** Where the payload is assembled, inside {@link #staging}. */
    private final Path payload;

    /** Where artefacts are fetched to; keyed by artefact, so a crash can be resumed. */
    private final Path downloads;

    /** Where the payload ends up. */
    private final Path destination;

    /** One action per step, complete by construction. */
    private final Map<InstallStep, StepAction> actions = new EnumMap<>(InstallStep.class);

    /** What has run, in order. */
    private final List<InstallStep> executed = new ArrayList<>();

    /** The downloaded main artefact, and where it is. */
    private VerifiedArtefact artefact;

    /** Each companion's download. */
    private final Map<ArtefactCompanion, VerifiedArtefact> companionArtefacts =
            new LinkedHashMap<>();

    /** What the marker will record for each installed file. */
    private final List<RecordedFile> recordedFiles = new ArrayList<>();

    /** What the probe confirmed. */
    private Set<ToolCapability> capabilities = Set.of();

    /** What the platform fix-ups changed. */
    private FixupReport fixupReport = new FixupReport(List.of(), List.of());

    /** How many files the extraction placed, excluding the marker. */
    private int payloadEntryCount;

    /** Bytes fetched so far, for progress. */
    private long bytesTransferred;

    /** The total the server declared, or negative when it declared none. */
    private long totalBytes = -1;

    /** The next step to run. */
    private int nextIndex;

    /** The answer, once there is one. */
    private Installation installation;

    /**
     * Builds a pipeline over a lock that has already been taken.
     *
     * @param record the artefact to install
     * @param cache where installed tools live
     * @param source steps 1 and 2
     * @param extractor step 3
     * @param fixups step 5
     * @param probe step 6
     * @param hashes the project's hasher
     * @param clock read once for the marker's timestamp
     * @param listener told where the install has got to
     * @param cancellation asked between steps
     * @param lock the held {@code R-TOOL-05} lock, released by {@link #close()}
     * @param staging the directory to build in, or {@code null} when the entry is already installed
     */
    InstallPipeline(
            ArtefactRecord record,
            ToolCache cache,
            VerifiedArtefactSource source,
            ArtefactExtractor extractor,
            PlatformFixups fixups,
            ToolProbe probe,
            HashService hashes,
            Clock clock,
            InstallProgressListener listener,
            DownloadCancellation cancellation,
            InstallLock lock,
            Path staging) {
        this.record = record;
        this.source = source;
        this.extractor = extractor;
        this.fixups = fixups;
        this.probe = probe;
        this.hashes = hashes;
        this.clock = clock;
        this.listener = listener;
        this.cancellation = cancellation;
        this.lock = lock;
        this.staging = staging;
        this.payload = staging == null ? null : staging.resolve("payload");
        this.downloads =
                cache.downloadDirectory(record.tool(), record.version(), record.platform());
        this.destination = cache.toolDirectory(record);
        actions.put(InstallStep.DOWNLOAD_TO_TEMPORARY_FILE, this::download);
        actions.put(InstallStep.VERIFY_SHA256, this::verifySha256);
        actions.put(InstallStep.EXTRACT_WITH_GUARDS, this::extract);
        actions.put(InstallStep.VERIFY_EXPECTED_LAYOUT, this::verifyLayout);
        actions.put(InstallStep.APPLY_PLATFORM_FIXUPS, this::applyFixups);
        actions.put(InstallStep.PROBE, this::runProbe);
        actions.put(InstallStep.MOVE_ATOMICALLY_INTO_CACHE, this::moveIntoCache);
        actions.put(InstallStep.RECORD_INSTALLATION_METADATA, this::recordMetadata);
        requireAnActionForEveryStep(actions.keySet());
    }

    /*
     * THE ENUMERATION IS THE PIPELINE.  A step with no action would otherwise be a step that
     * silently does not happen -- and R-TOOL-04's guarantee is a claim about every step, so a step
     * that does not run is a hole in the guarantee rather than a missing feature.
     *
     * It takes the implemented set rather than reading the field, so that a test can hand it an
     * incomplete one and watch it refuse.  A guard that only ever sees a complete set is a guard
     * nobody has seen work.
     */
    static void requireAnActionForEveryStep(Set<InstallStep> implemented) {
        List<InstallStep> missing = new ArrayList<>();
        for (InstallStep step : InstallStep.values()) {
            if (!implemented.contains(step)) {
                missing.add(step);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "every install step needs an action, and "
                            + missing
                            + " has none; adding a step to InstallStep means implementing it here");
        }
    }

    /**
     * Marks a pipeline finished before it started, because the entry was already installed.
     *
     * @param existing the verified entry
     */
    void completeAsAlreadyInstalled(InstallationCheck existing) {
        this.installation =
                new Installation(
                        existing.directory(),
                        existing.directory().resolve(record.executablePath()),
                        existing.requireMarker(),
                        true);
        this.nextIndex = InstallStep.values().length;
    }

    /**
     * Whether there is another step to run.
     *
     * @return {@code true} until every step has run
     */
    public boolean hasNextStep() {
        return nextIndex < InstallStep.values().length;
    }

    /**
     * The step {@link #runNextStep()} would run.
     *
     * @return the next step
     * @throws IllegalStateException if there is none
     */
    public InstallStep nextStep() {
        if (!hasNextStep()) {
            throw new IllegalStateException(
                    "every install step has run; there is no next step for " + record.describe());
        }
        return InstallStep.values()[nextIndex];
    }

    /**
     * Runs the next step.
     *
     * @return the step that ran
     * @throws InstallCancelledException if the caller asked for the install to stop
     * @throws InstallRejectedException if the step refuses the artefact
     * @throws IOException if the step fails
     * @throws IllegalStateException if there is no next step
     */
    public InstallStep runNextStep() throws IOException {
        InstallStep step = nextStep();
        if (cancellation.isCancelled()) {
            throw new InstallCancelledException(record.describe(), step);
        }
        report(step.phase());
        actions.get(step).run();
        executed.add(step);
        nextIndex++;
        return step;
    }

    /**
     * Runs every remaining step.
     *
     * @throws InstallCancelledException if the caller asked for the install to stop
     * @throws IOException if any step fails
     */
    public void runToCompletion() throws IOException {
        while (hasNextStep()) {
            runNextStep();
        }
    }

    /**
     * The steps that have run, in order.
     *
     * @return the executed steps, immutable
     */
    public List<InstallStep> executedSteps() {
        return List.copyOf(executed);
    }

    /**
     * The directory this install builds in.
     *
     * @return the staging directory, or empty when the entry was already installed
     */
    public Optional<Path> stagingDirectory() {
        return Optional.ofNullable(staging);
    }

    /**
     * What the platform fix-up step changed.
     *
     * @return the report, empty until step 5 has run
     */
    public FixupReport fixupReport() {
        return fixupReport;
    }

    /**
     * The finished install.
     *
     * @return the installation
     * @throws IllegalStateException if the pipeline has not finished
     */
    public Installation installation() {
        if (installation == null) {
            throw new IllegalStateException(
                    "the install of "
                            + record.describe()
                            + " has run "
                            + executed.size()
                            + " of "
                            + InstallStep.values().length
                            + " step(s) and has no result yet");
        }
        return installation;
    }

    /**
     * Releases the lock and, unless the install finished, deletes everything it staged.
     *
     * <p>The tool cache is therefore untouched by a failed or cancelled install: the only thing
     * that ever writes into it is step 7, and a pipeline that did not reach step 7 has written
     * nothing there.
     *
     * <p>An install that ends with the tool installed also removes its downloads -- including the
     * one that found the entry already there, which is how the artefacts a crashed attempt left
     * behind are eventually cleaned up. A failed or cancelled install keeps them, because the next
     * attempt resumes from them rather than fetching 99 MB again.
     *
     * @throws IOException if the staging directory or the lock cannot be released
     */
    @Override
    public void close() throws IOException {
        try {
            if (staging != null) {
                ToolCache.deleteRecursively(staging);
            }
            if (installation != null) {
                ToolCache.deleteRecursively(downloads);
            }
        } finally {
            lock.close();
        }
    }

    // ------------------------------------------------------------------ the eight steps --

    /*
     * STEP 1.  Into a directory named after the artefact rather than after this attempt, so that a
     * transfer a crash interrupted is resumed by the next attempt instead of fetched again.
     */
    private void download() throws IOException {
        Files.createDirectories(downloads);
        artefact =
                source.fetch(
                        record.url(),
                        downloads.resolve(downloadName("artefact", record.url())),
                        record.hashes(),
                        record.sizeBytes(),
                        this::onBytes,
                        cancellation);
        for (ArtefactCompanion companion : record.companions()) {
            companionArtefacts.put(
                    companion,
                    source.fetch(
                            companion.url(),
                            downloads.resolve(downloadName(companion.id(), companion.url())),
                            companion.hashes(),
                            companion.sizeBytes(),
                            this::onBytes,
                            cancellation));
        }
    }

    /*
     * STEP 2.  R-SEC-02 at the installer's own boundary.  The source has already refused anything
     * that does not match, so in a correct product this cannot fire -- which is the point: a defect
     * that let an unverified file through stops here instead of being extracted and executed.  The
     * MD5 is recorded and decides nothing, in either direction.
     */
    private void verifySha256() throws IOException {
        requirePinnedBytes(
                "the artefact", artefact, record.hashes(), record.sizeBytes(), record.url());
        for (Map.Entry<ArtefactCompanion, VerifiedArtefact> entry : companionArtefacts.entrySet()) {
            ArtefactCompanion companion = entry.getKey();
            requirePinnedBytes(
                    "companion \"" + companion.id() + "\"",
                    entry.getValue(),
                    companion.hashes(),
                    companion.sizeBytes(),
                    companion.url());
        }
    }

    private void requirePinnedBytes(
            String what, VerifiedArtefact downloaded, FileHashes pinned, long pinnedSize, URI url)
            throws IOException {
        long actualSize = Files.size(downloaded.file());
        if (actualSize != pinnedSize) {
            throw new InstallRejectedException(
                    InstallFailure.CHECKSUM_MISMATCH,
                    InstallStep.VERIFY_SHA256,
                    record.describe(),
                    null,
                    what
                            + " downloaded from "
                            + url
                            + " is "
                            + actualSize
                            + " bytes and the manifest pins "
                            + pinnedSize);
        }
        /*
         * THE FILE IS HASHED HERE, NOT THE DIGEST THE SOURCE REPORTED.  A step called "verify" that
         * believed another component's claim would not be one: the defect this boundary exists to
         * catch is precisely a source that hands over bytes it says are verified and are not.  The
         * cost is one pass over a file that has just arrived over a network.
         */
        FileHashes actual = hashes.hash(downloaded.file());
        if (!actual.sha256().equals(pinned.sha256())) {
            throw new InstallRejectedException(
                    InstallFailure.CHECKSUM_MISMATCH,
                    InstallStep.VERIFY_SHA256,
                    record.describe(),
                    null,
                    what
                            + " downloaded from "
                            + url
                            + " hashes to "
                            + actual.sha256()
                            + " and the manifest pins "
                            + pinned.sha256()
                            + ", so it is not executed (R-SEC-02)");
        }
    }

    /* STEP 3.  Into staging, never into the cache.  Every guard in R-SEC-05 runs here. */
    private void extract() throws IOException {
        Files.createDirectories(payload);
        extractor.extract(record, artefact.file(), payload);
        for (Map.Entry<ArtefactCompanion, VerifiedArtefact> entry : companionArtefacts.entrySet()) {
            extractor.extract(entry.getKey(), entry.getValue().file(), payload);
        }
    }

    /*
     * STEP 4.  Every path the manifest names must be there, and every path the manifest pins a
     * digest for is hashed and compared against it.  That second half is what closes the gap this
     * phase found: ArchiveMember carries a length and both digests for every member, extraction
     * records them, and until this step nothing in the product compared them with what came out.
     *
     * Where the manifest pins nothing -- a whole-archive install such as PDV, whose 222 entries are
     * covered by a length and a digest over the archive rather than over each file, or a bare
     * executable whose one file step 2 has already verified -- the digest computed here is recorded
     * and not compared.  Comparing it would be comparing a number with itself.
     */
    private void verifyLayout() throws IOException {
        Map<String, ArchiveMember> pinned = pinnedMembers();
        for (String path : record.installedPaths()) {
            Path file = payload.resolve(path);
            if (!Files.isRegularFile(file)) {
                throw new InstallRejectedException(
                        InstallFailure.LAYOUT_INCOMPLETE,
                        InstallStep.VERIFY_EXPECTED_LAYOUT,
                        record.describe(),
                        path,
                        "the manifest says \""
                                + path
                                + "\" is installed and the extraction did not produce it");
            }
            FileHashes actual = hashes.hash(file);
            long size = Files.size(file);
            ArchiveMember expected = pinned.get(path);
            if (expected != null) {
                requireMemberBytes(path, expected, size, actual);
            }
            recordedFiles.add(new RecordedFile(path, size, actual));
        }
        payloadEntryCount = ToolCache.countPayloadEntries(payload);
    }

    /*
     * The paths the manifest pins a digest for.  Three sources, and all three are real: the archive
     * member a ZIP record names; the download itself for a BARE_EXECUTABLE or a JAR, where the file
     * installed IS the file downloaded and the record's own digest describes it; and every member
     * of every companion.
     */
    private Map<String, ArchiveMember> pinnedMembers() {
        Map<String, ArchiveMember> pinned = new LinkedHashMap<>();
        record.member().ifPresent(member -> pinned.put(member.installedPath(), member));
        /*
         * NOTHING IS ADDED HERE FOR A BARE_EXECUTABLE OR A JAR.  For those kinds the installed file
         * IS the download, whose digest step 2 has already compared with the manifest's, so a
         * second comparison here would be the same number checked against itself -- and a check
         * that cannot fail is worse than no check, because it reads like one.
         */
        for (ArtefactCompanion companion : record.companions()) {
            for (ArchiveMember member : companion.members()) {
                pinned.put(member.installedPath(), member);
            }
        }
        return pinned;
    }

    private void requireMemberBytes(
            String path, ArchiveMember expected, long actualSize, FileHashes actual)
            throws IOException {
        if (actualSize != expected.sizeBytes()) {
            throw new InstallRejectedException(
                    InstallFailure.MEMBER_DIGEST_MISMATCH,
                    InstallStep.VERIFY_EXPECTED_LAYOUT,
                    record.describe(),
                    path,
                    "\""
                            + path
                            + "\" came out of the artefact "
                            + actualSize
                            + " bytes long and the manifest records "
                            + expected.sizeBytes()
                            + " for member \""
                            + expected.path()
                            + "\"");
        }
        if (!actual.sha256().equals(expected.hashes().sha256())) {
            throw new InstallRejectedException(
                    InstallFailure.MEMBER_DIGEST_MISMATCH,
                    InstallStep.VERIFY_EXPECTED_LAYOUT,
                    record.describe(),
                    path,
                    "\""
                            + path
                            + "\" came out of the artefact hashing to "
                            + actual.sha256()
                            + " and the manifest records "
                            + expected.hashes().sha256()
                            + " for member \""
                            + expected.path()
                            + "\"");
        }
    }

    /* STEP 5.  R-PLAT-05's executable bits and R-PLAT-04's quarantine removal. */
    private void applyFixups() throws IOException {
        fixupReport = fixups.apply(payload, record);
    }

    /*
     * STEP 6.  Before the move, so that a build which cannot run here never becomes a cache entry
     * that reports itself installed (R-TOOL-06).  The probe's own exception is kept as the cause,
     * because units 6 and 7 put the R-PLAT-03 loader diagnostic in it and this class has nothing
     * better to say than they do.
     */
    private void runProbe() throws IOException {
        try {
            capabilities = Set.copyOf(probe.probe(record, payload));
        } catch (IOException refused) {
            throw new InstallRejectedException(
                    InstallFailure.PROBE_FAILED,
                    InstallStep.PROBE,
                    record.describe(),
                    record.executablePath(),
                    "the installed build was not accepted by the probe: " + refused.getMessage(),
                    refused);
        }
    }

    /* STEP 7.  The one operation that touches the tool cache.  See the class documentation. */
    private void moveIntoCache() throws IOException {
        Files.createDirectories(
                Objects.requireNonNull(
                        destination.getParent(), "a tool directory has a parent directory"));
        moveAtomically(payload, destination, record.describe());
    }

    /**
     * The final move, and the whole of this installer's policy about it.
     *
     * <p>Package-private and static so that each of its three outcomes can be produced directly --
     * a rethrow that a caller must not mistake for contention is a decision, and a decision nobody
     * can watch happen is one nobody has checked.
     *
     * @param payload the staged directory
     * @param destination where it goes
     * @param artefact how the artefact is named in a diagnostic
     * @throws AtomicMoveNotSupportedException if the file system cannot rename atomically; never
     *     downgraded to a copy
     * @throws NoSuchFileException if the payload or the destination's parent has gone
     * @throws InstallRejectedException if the file system refuses the rename for any other reason,
     *     which on Linux includes a destination that already holds something
     * @throws IOException if the move fails in any other way
     */
    static void moveAtomically(Path payload, Path destination, String artefact) throws IOException {
        try {
            Files.move(payload, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException notAtomic) {
            throw notAtomic;
        } catch (NoSuchFileException gone) {
            throw gone;
        } catch (FileSystemException refused) {
            throw new InstallRejectedException(
                    InstallFailure.CACHE_CONTENDED,
                    InstallStep.MOVE_ATOMICALLY_INTO_CACHE,
                    artefact,
                    null,
                    "the file system refused to move the staged install into "
                            + destination
                            + ": "
                            + refused.getClass().getSimpleName()
                            + ", "
                            + String.valueOf(refused.getReason())
                            + ". The usual cause is another process holding a file in the tool"
                            + " cache open -- a provenance viewer, a virus scanner or a file-sync"
                            + " client; close it and install again. Nothing was written to the tool"
                            + " cache, and this installer does not retry the move or fall back to a"
                            + " copy, because a copy is not atomic",
                    refused);
        }
    }

    /*
     * STEP 8.  R-TOOL-04's "written last".  After the move, so that a directory in the cache with
     * no marker is exactly what an interrupted install looks like, and so that the marker is the
     * last thing that happens rather than the last-but-one.
     */
    private void recordMetadata() throws IOException {
        InstallationMarker marker =
                new InstallationMarker(
                        InstallationMarker.SCHEMA_VERSION,
                        record.tool(),
                        record.version(),
                        record.platform(),
                        record.releaseTag(),
                        record.url(),
                        record.sizeBytes(),
                        record.hashes(),
                        CanonicalTimestamp.utcMillis(clock.instant()),
                        record.executablePath(),
                        payloadEntryCount,
                        new ArrayList<>(new TreeSet<>(capabilities)),
                        List.copyOf(recordedFiles));
        ToolCache.writeMarker(destination, marker);
        installation =
                new Installation(
                        destination, destination.resolve(record.executablePath()), marker, false);
    }

    // ------------------------------------------------------------------------- plumbing --

    /**
     * Sends the one terminal progress report for this install, carrying the byte counts the
     * transfer ended on.
     *
     * @param phase the terminal phase
     */
    void reportTerminal(InstallPhase phase) {
        report(phase);
    }

    private void onBytes(long transferred, long total) {
        bytesTransferred = transferred;
        totalBytes = total;
        report(InstallPhase.DOWNLOADING);
    }

    private void report(InstallPhase phase) {
        listener.onInstallProgress(
                new InstallProgress(
                        record.tool(), record.version(), phase, bytesTransferred, totalBytes));
    }

    /*
     * A download's file name is derived from the URL for a person's benefit and is not trusted to
     * be one: anything outside the safe set becomes an underscore, so no upstream string can place
     * a file.  The prefix keeps an artefact and its companions apart even when upstream names two
     * of them the same thing.
     */
    static String downloadName(String prefix, URI url) {
        String path = url.getPath();
        int lastSlash = path.lastIndexOf('/');
        String tail = lastSlash < 0 ? path : path.substring(lastSlash + 1);
        return safe(prefix) + "-" + safe(tail);
    }

    static String safe(String text) {
        StringBuilder cleaned = new StringBuilder(text.length() + 1);
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            boolean ordinary =
                    (character >= 'a' && character <= 'z')
                            || (character >= 'A' && character <= 'Z')
                            || (character >= '0' && character <= '9')
                            || character == '.'
                            || character == '-'
                            || character == '_';
            cleaned.append(ordinary ? character : '_');
        }
        String result = cleaned.toString();
        return result.isEmpty() || ".".equals(result) || "..".equals(result) ? "download" : result;
    }

    /**
     * Describes the pipeline by what it is installing and how far it has got.
     *
     * @return a description for a log line or an exception message
     */
    @Override
    public String toString() {
        return "InstallPipeline["
                + record.describe()
                + ", "
                + executed.size()
                + "/"
                + InstallStep.values().length
                + " step(s) run]";
    }
}
