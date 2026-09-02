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
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import org.cometgui.domain.ports.HashService;
import org.cometgui.domain.tools.InstallPhase;
import org.cometgui.domain.tools.InstallProgress;
import org.cometgui.domain.tools.InstallProgressListener;
import org.cometgui.install.archive.ArtefactExtractor;
import org.cometgui.install.download.DownloadCancellation;
import org.cometgui.install.registry.ArtefactRecord;

/**
 * Installs one managed artefact, atomically, with the {@code R-TOOL-05} lock held.
 *
 * <p>The specification's eight steps are {@link InstallPipeline}'s; this class is what goes around
 * them: take the lock, decide what the cache already holds, run the steps, and report a terminal
 * phase exactly once.
 *
 * <h2>What happens before step 1</h2>
 *
 * <p>Under the lock, and in this order:
 *
 * <ol>
 *   <li><strong>A complete entry is returned as it is.</strong> {@code R-TOOL-05} permits
 *       concurrent installation to be "serialised by a lock file <em>or</em> made idempotent", and
 *       doing both is what makes the second of two racing processes useful rather than merely
 *       harmless: it waits, finds the tool installed, and returns it without fetching 99 MB again.
 *       {@link Installation#alreadyInstalled()} says which of the two happened.
 *   <li><strong>An incomplete entry is discarded whole.</strong> A directory with no marker is an
 *       install something interrupted; a directory whose recorded checksums no longer match is an
 *       entry that has been corrupted or swapped. Neither is repaired in place, because a directory
 *       that half matches its marker is a directory nobody can say anything true about.
 * </ol>
 *
 * <h2>Exactly one terminal report</h2>
 *
 * <p>{@link InstallProgressListener} promises that exactly one report carries a terminal {@link
 * InstallPhase} and that it is the last one. {@link #install} is where that promise is kept: {@link
 * InstallPhase#DONE} when the install finished, {@link InstallPhase#CANCELLED} when the caller
 * asked it to stop, and {@link InstallPhase#FAILED} for everything else. Cancelling is not failing
 * -- a user who cancelled a large download has not encountered an error -- and the two phases exist
 * so that the difference survives.
 *
 * <p>Thread safe in the sense that matters: two threads may call {@link #install} for the same
 * artefact at once and will serialise on {@link InstallLock}, which holds a JVM-wide monitor as
 * well as a file lock for exactly that reason.
 */
public final class ArtefactInstaller {

    /** Where installed tools live, and the authority on whether one is installed. */
    private final ToolCache cache;

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

    /** Read once per install, for the marker's timestamp. */
    private final Clock clock;

    /**
     * Composes the collaborators the eight steps need.
     *
     * @param cache where installed tools live
     * @param source steps 1 and 2, in production {@code
     *     org.cometgui.install.verify.VerifiedDownloader::fetch}
     * @param extractor step 3
     * @param fixups step 5, built for the host this application is running on
     * @param probe step 6, implemented by phase 05 units 6 and 7
     * @param hashes the project's one hasher
     * @param clock read once per install for the marker's timestamp
     * @throws NullPointerException if any argument is {@code null}
     */
    public ArtefactInstaller(
            ToolCache cache,
            VerifiedArtefactSource source,
            ArtefactExtractor extractor,
            PlatformFixups fixups,
            ToolProbe probe,
            HashService hashes,
            Clock clock) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.source = Objects.requireNonNull(source, "source");
        this.extractor = Objects.requireNonNull(extractor, "extractor");
        this.fixups = Objects.requireNonNull(fixups, "fixups");
        this.probe = Objects.requireNonNull(probe, "probe");
        this.hashes = Objects.requireNonNull(hashes, "hashes");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * The cache this installer installs into.
     *
     * @return the cache
     */
    public ToolCache cache() {
        return cache;
    }

    /**
     * Installs one artefact, or returns the one that is already installed.
     *
     * @param record the artefact to install
     * @param listener told where the install has got to; use a no-op rather than {@code null}
     * @param cancellation asked between steps whether the caller still wants the install
     * @return the installation
     * @throws InstallCancelledException if the caller asked for the install to stop
     * @throws InstallRejectedException if a step refuses the artefact
     * @throws IOException if the install fails
     * @throws NullPointerException if any argument is {@code null}
     */
    public Installation install(
            ArtefactRecord record,
            InstallProgressListener listener,
            DownloadCancellation cancellation)
            throws IOException {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(listener, "listener");
        /*
         * EXACTLY ONE TERMINAL REPORT, ON EVERY PATH.  The phase is decided in the body and sent
         * from the finally block rather than from each branch, because sending it from the branches
         * lets a close() that throws after a successful install produce two -- a DONE and then a
         * FAILED -- which is precisely the contract InstallProgressListener states.
         */
        InstallPipeline started = null;
        InstallPhase terminal = InstallPhase.FAILED;
        try {
            started = begin(record, listener, cancellation);
            try (InstallPipeline running = started) {
                running.runToCompletion();
                Installation done = running.installation();
                terminal = InstallPhase.DONE;
                return done;
            }
        } catch (InstallCancelledException cancelled) {
            terminal = InstallPhase.CANCELLED;
            throw cancelled;
        } catch (IOException | RuntimeException failed) {
            terminal = InstallPhase.FAILED;
            throw failed;
        } finally {
            if (started == null) {
                report(listener, record, terminal);
            } else {
                started.reportTerminal(terminal);
            }
        }
    }

    /**
     * Takes the lock, decides what the cache already holds, and returns the install ready to run.
     *
     * <p>The ordinary way in is {@link #install}, which runs the loop and reports the terminal
     * phase. This method exists because there is more than one way to drive eight steps: an install
     * can be advanced a step at a time, and a caller that stops part way -- or a process that stops
     * part way, which is the case {@code R-TOOL-04} is about -- must leave nothing that reports
     * itself installed. Both go through the same object and the same actions.
     *
     * <p>The returned pipeline holds the lock. <strong>Close it.</strong>
     *
     * @param record the artefact to install
     * @param listener told where the install has got to
     * @param cancellation asked between steps
     * @return the pipeline, holding the lock; already finished when the entry was installed
     * @throws IOException if the lock cannot be taken or an incomplete entry cannot be discarded
     * @throws NullPointerException if any argument is {@code null}
     */
    public InstallPipeline begin(
            ArtefactRecord record,
            InstallProgressListener listener,
            DownloadCancellation cancellation)
            throws IOException {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(listener, "listener");
        Objects.requireNonNull(cancellation, "cancellation");
        InstallLock lock =
                InstallLock.acquire(
                        cache.lockFile(record.tool(), record.version(), record.platform()));
        try {
            /*
             * SWEEP WHAT A CRASHED ATTEMPT LEFT, BEFORE ANYTHING ELSE.  A process that died between
             * steps left its staging directory on disk, and nothing else would ever remove it -- a
             * 99 MB archive unpacked once per crash.  It is safe here and only here: this
             * artefact's lock is held, so no other process can be building it.  Before the
             * already-installed check rather than after, because the crash that leaves litter
             * behind after the marker was written would otherwise never be cleaned up at all.
             */
            cache.discard(cache.stagingRoot(record.tool(), record.version(), record.platform()));
            InstallationCheck existing = cache.verify(record);
            if (existing.installed()) {
                InstallPipeline pipeline = pipeline(record, listener, cancellation, lock, null);
                pipeline.completeAsAlreadyInstalled(existing);
                return pipeline;
            }
            if (existing.state() != InstallationState.NOT_PRESENT) {
                cache.discard(existing.directory());
            }
            Path staging =
                    cache.createStagingDirectory(
                            record.tool(), record.version(), record.platform());
            return pipeline(record, listener, cancellation, lock, staging);
        } catch (IOException | RuntimeException | Error failed) {
            lock.close();
            throw failed;
        }
    }

    private InstallPipeline pipeline(
            ArtefactRecord record,
            InstallProgressListener listener,
            DownloadCancellation cancellation,
            InstallLock lock,
            Path staging) {
        return new InstallPipeline(
                record,
                cache,
                source,
                extractor,
                fixups,
                probe,
                hashes,
                clock,
                listener,
                cancellation,
                lock,
                staging);
    }

    private static void report(
            InstallProgressListener listener, ArtefactRecord record, InstallPhase phase) {
        listener.onInstallProgress(
                new InstallProgress(record.tool(), record.version(), phase, 0L, -1L));
    }

    /**
     * Describes the installer by the cache it installs into.
     *
     * @return a description for a log line or an exception message
     */
    @Override
    public String toString() {
        return "ArtefactInstaller[" + cache + ", fixups=" + fixups + "]";
    }
}
