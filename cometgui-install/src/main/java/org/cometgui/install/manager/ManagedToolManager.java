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

package org.cometgui.install.manager;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.cometgui.domain.tools.CapabilityEvidence;
import org.cometgui.domain.tools.DeclaredCapability;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.InstallHandle;
import org.cometgui.domain.tools.InstallProgressListener;
import org.cometgui.domain.tools.LoaderDiagnostic;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolRegistrationException;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.cache.ArtefactInstaller;
import org.cometgui.install.cache.InstallCancelledException;
import org.cometgui.install.cache.InstallationCheck;
import org.cometgui.install.cache.InstallationMarker;
import org.cometgui.install.cache.ToolCache;
import org.cometgui.install.download.DownloadCancellation;
import org.cometgui.install.probe.HostRequirementCheck;
import org.cometgui.install.probe.HostRuntimeVersions;
import org.cometgui.install.probe.LoaderOutputClassifier;
import org.cometgui.install.probe.ManifestAlternatives;
import org.cometgui.install.probe.ProbeGatedOffers;
import org.cometgui.install.registry.ArtefactManifest;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.install.registry.ArtefactSelection;

/**
 * The Tool Manager's runtime: the manifest, the cache and the installer composed into the rows a
 * scientist is shown and the install started on their behalf.
 *
 * <p>The only implementation of {@link ToolManager}, and the only class in {@code
 * org.cometgui.install} the user interface reaches -- through the domain port, because the
 * architecture rules do not let {@code org.cometgui.ui..} name this package.
 *
 * <h2>Where each answer comes from</h2>
 *
 * <ul>
 *   <li><strong>Which builds exist here</strong> -- {@link ArtefactManifest#select}, never {@link
 *       ArtefactManifest#artefacts()}: a platform-independent artefact such as PDV's zip is five
 *       rows in the raw list and one offer here, and on Apple silicon a {@code macos-x86-64}
 *       Percolator is a real offer that runs under Rosetta 2 ({@code D-004}).
 *   <li><strong>Which releases exist at all</strong> -- the whole manifest, because that is a
 *       question about releases rather than about this host's rows. Percolator 3.09 publishes
 *       nothing for Linux; it is <em>shown</em> as {@link
 *       ToolInstallState#UNAVAILABLE_ON_THIS_PLATFORM} rather than omitted, since {@code R-PERC-01}
 *       forbids promising a build that cannot run and not the admission that it exists.
 *   <li><strong>Whether this host meets a build's floors</strong> -- {@link HostRequirementCheck},
 *       in advance ({@code R-TOOL-03}), so the refusal arrives before a download rather than after
 *       a probe.
 *   <li><strong>Whether a build starts here at all</strong> -- {@link ProbeGatedOffers#decide},
 *       whose last word is {@code R-TOOL-06}'s: a tool that fails loadability is never offered.
 *   <li><strong>Whether a build is installed</strong> -- {@link ToolCache#verify} and nothing else,
 *       so {@code INSTALLED} means a completion marker whose recorded checksums still match, never
 *       a directory that exists.
 * </ul>
 *
 * <h2>Shown, and offered, are different lists</h2>
 *
 * <p>A build refused by the advance check is <em>shown</em>, with {@link
 * ToolInstallState#HOST_REQUIREMENTS_NOT_MET} and the {@code R-PLAT-03} diagnostic naming the
 * required version, this host's version and the alternatives. A build that was <em>run</em> here
 * and would not start is not shown at all. Both refusals come out of {@link
 * ProbeGatedOffers#decide} as one kind of thing, so this class asks {@link HostRequirementCheck}
 * again to tell them apart -- the same pure question over the same two inputs, which cannot answer
 * differently the second time.
 *
 * <h2>Threads</h2>
 *
 * <p>Safe to call from the JavaFX application thread, as the port requires. {@link #offers()} reads
 * the manifest and the cache and runs no download; {@link #install} hands the work to an {@link
 * Executor} and returns the handle at once. The only mutable state is the map of installs this
 * process has started, which is a {@link ConcurrentHashMap} because it is written from the install
 * threads and read from the thread that draws the rows.
 */
public final class ManagedToolManager implements ToolManager {

    /** Every artefact this build of CometGUI knows how to install. */
    private final ArtefactManifest manifest;

    /** The machine in front of the user. */
    private final HostPlatform host;

    /** What that machine's C and C++ runtimes were established to be. */
    private final HostRuntimeVersions versions;

    /** Where installed tools live, and the one authority on whether one is installed. */
    private final ToolCache cache;

    /** What runs the eight steps of an atomic install. */
    private final ArtefactInstaller installer;

    /** {@code R-TOOL-06}'s gate over the manifest's selection for this host. */
    private final ProbeGatedOffers gate;

    /** Whether an installed build's binary still starts on this host. */
    private final ProbeGatedOffers.LoadabilityCheck loadability;

    /** The tools a binary already on the machine can be registered for. */
    private final Map<ToolName, LocalBinaryRegistrar> registrars;

    /** Where an install runs, so that {@link #install} returns immediately. */
    private final Executor installThreads;

    /**
     * What this process has started, keyed the way the cache keys an entry.
     *
     * <p>Holds {@link ToolInstallState#INSTALLING} while an install runs and {@link
     * ToolInstallState#FAILED} after one that did not succeed; an entry is removed when the install
     * finished or was cancelled, because in both of those cases {@link ToolCache#verify} is then
     * the whole truth.
     */
    private final Map<String, ToolInstallState> attempts = new ConcurrentHashMap<>();

    /**
     * Composes the runtime.
     *
     * <p>The cache is taken from the installer rather than passed separately: two of them would be
     * two answers to "where are the tools", and the one that decided whether a build is installed
     * could then be a different directory from the one an install writes into.
     *
     * @param manifest the shipped artefact manifest
     * @param host the machine in front of the user
     * @param versions what that machine's runtimes were established to be
     * @param installer what runs an install, and the source of the tool cache
     * @param loadability whether an installed build's binary starts here, usually {@code
     *     StagedToolProbe::loadabilityOf} bound to the installed directory
     * @param registrars the local-binary registrar for each tool that has one
     * @param installThreads where an install runs; must not run the task on the calling thread in
     *     production, because {@link #install} promises to return as soon as the install has
     *     started
     * @throws NullPointerException if any argument is {@code null}
     */
    public ManagedToolManager(
            ArtefactManifest manifest,
            HostPlatform host,
            HostRuntimeVersions versions,
            ArtefactInstaller installer,
            ProbeGatedOffers.LoadabilityCheck loadability,
            Map<ToolName, LocalBinaryRegistrar> registrars,
            Executor installThreads) {
        this.manifest = Objects.requireNonNull(manifest, "manifest");
        this.host = Objects.requireNonNull(host, "host");
        this.versions = Objects.requireNonNull(versions, "versions");
        this.installer = Objects.requireNonNull(installer, "installer");
        this.cache = installer.cache();
        this.loadability = Objects.requireNonNull(loadability, "loadability");
        this.registrars = Map.copyOf(Objects.requireNonNull(registrars, "registrars"));
        this.installThreads = Objects.requireNonNull(installThreads, "installThreads");
        this.gate =
                new ProbeGatedOffers(
                        versions,
                        new LoaderOutputClassifier(host, versions),
                        new ManifestAlternatives(manifest, host, versions)::forArtefact);
    }

    @Override
    public List<ToolOffer> offers() {
        List<ToolOffer> offers = new ArrayList<>();
        /*
         * The enumeration's own order, which is the order the workflow runs the tools in and the
         * order the manifest declares them: search, rescore, view, convert.  A Tool Manager whose
         * rows moved about between two readings of the same manifest would be harder to use than
         * one whose order is arbitrary but fixed.
         */
        for (ToolName tool : ToolName.values()) {
            offers.addAll(offersFor(tool));
        }
        return List.copyOf(offers);
    }

    @Override
    public InstallHandle install(
            ToolName tool, ToolVersion version, InstallProgressListener listener) {
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(listener, "listener");
        ArtefactRecord record = installableRecord(tool, version);
        Cancellation cancellation = new Cancellation();
        String entry = keyOf(record);
        attempts.put(entry, ToolInstallState.INSTALLING);
        installThreads.execute(() -> runInstall(record, listener, cancellation, entry));
        return cancellation;
    }

    @Override
    public ToolOffer registerLocalBinary(ToolName tool, Path executable)
            throws ToolRegistrationException {
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(executable, "executable");
        if (!executable.isAbsolute()) {
            throw new IllegalArgumentException(
                    "executable must be an absolute path, because the registration is recorded in"
                            + " a provenance record that is read on another machine, but was: "
                            + executable);
        }
        LocalBinaryRegistrar registrar = registrars.get(tool);
        if (registrar == null) {
            throw new ToolRegistrationException(
                    "CometGUI cannot register a local "
                            + tool.id()
                            + " binary. It registers a local binary for "
                            + registrableTools()
                            + "; every other tool is installed from the artefact manifest, where"
                            + " its checksum is pinned.");
        }
        return checkedRegistration(tool, registrar.register(executable));
    }

    /*
     * THE ADAPTER'S ANSWER IS CHECKED AT THIS BOUNDARY, for the reason InstallStep.VERIFY_SHA256
     * gives about its own: in a correct product this cannot fire, which is exactly why it is here.
     * The port promises the caller a LOCAL offer for the tool it asked about, and an adapter that
     * returned a MANAGED one -- or one for another tool -- would put a row in the Tool Manager
     * saying CometGUI downloaded and verified bytes that it did not.
     */
    private static ToolOffer checkedRegistration(ToolName tool, ToolOffer registered) {
        Objects.requireNonNull(registered, "registered");
        if (registered.tool() != tool) {
            throw new IllegalStateException(
                    "the registrar for "
                            + tool.id()
                            + " answered with an offer for "
                            + registered.tool().id());
        }
        if (registered.origin() != ToolOrigin.LOCAL) {
            throw new IllegalStateException(
                    "the registrar for "
                            + tool.id()
                            + " answered with a "
                            + registered.origin()
                            + " offer, and a binary the user pointed at is "
                            + ToolOrigin.LOCAL);
        }
        return registered;
    }

    private String registrableTools() {
        List<String> names = new ArrayList<>();
        for (ToolName tool : ToolName.values()) {
            if (registrars.containsKey(tool)) {
                names.add(tool.id());
            }
        }
        if (names.isEmpty()) {
            return "no tool at all in this build";
        }
        return String.join(", ", names);
    }

    // --------------------------------------------------------------------- the offered rows --

    private List<ToolOffer> offersFor(ToolName tool) {
        List<ArtefactSelection> runnableHere = manifest.select(host, tool);
        Map<URI, InstallationCheck> installed = installedEntries(runnableHere);
        ProbeGatedOffers.Decision decision =
                gate.decide(runnableHere, record -> refusalFor(record, installed));
        Set<URI> offered = new LinkedHashSet<>();
        for (ArtefactRecord record : decision.offered()) {
            offered.add(record.url());
        }
        Map<URI, LoaderDiagnostic> refused = new LinkedHashMap<>();
        for (ProbeGatedOffers.Refusal refusal : decision.refused()) {
            refused.put(refusal.artefact().url(), refusal.diagnostic());
        }

        List<ToolOffer> offers = new ArrayList<>();
        for (Map.Entry<ToolVersion, ArtefactRecord> release : releasesOf(tool).entrySet()) {
            List<ArtefactSelection> rows = rowsOf(runnableHere, release.getKey());
            if (rows.isEmpty()) {
                offers.add(unavailableHere(release.getValue()));
                continue;
            }
            for (ArtefactSelection row : rows) {
                ArtefactRecord record = row.artefact();
                if (offered.contains(record.url())) {
                    offers.add(offerFor(record, installed.get(record.url())));
                } else if (beyondThisHost(record)) {
                    offers.add(shownButNotRunnable(record, refused.get(record.url())));
                }
                /*
                 * Otherwise the binary was run on this machine and did not start.  R-TOOL-06's
                 * last sentence is literal -- "a tool that fails loadability shall never be
                 * offered for selection" -- and unlike the two refusals above it is a fact about
                 * this artefact on this host that no user action can change, so there is no row to
                 * draw and no button to offer.
                 */
            }
        }
        return offers;
    }

    /*
     * Asked again here, although ProbeGatedOffers asked it inside decide().  A Decision says that a
     * candidate was refused and not WHICH of the two refusals fired, and that distinction is the
     * one that decides whether the build is shown or withheld.  Asking twice is safe because the
     * check is a pure function of the record's declared floors and the versions established for
     * this host, both of which are fields: it cannot answer differently the second time, and
     * re-deriving it here is cheaper than a second return value on a signed-off type.
     */
    private boolean beyondThisHost(ArtefactRecord record) {
        return HostRequirementCheck.check(record.minimumHostRequirements(), versions).isRefusal();
    }

    /*
     * LOADABILITY IS ASKED OF AN INSTALLED BUILD AND OF NOTHING ELSE.  There is no binary to run
     * until one has been downloaded, verified and extracted, and R-PLAT-02 makes execution the
     * authority on compatibility -- so an artefact nobody has fetched has not failed to start, it
     * has not been asked.  Answering "no refusal" for it is not an approval either: the install
     * itself probes at step 6, before the move into the cache, so a build that cannot run here
     * never becomes an entry that reports itself installed.
     */
    private Optional<LoaderDiagnostic> refusalFor(
            ArtefactRecord record, Map<URI, InstallationCheck> installed) throws IOException {
        if (!installed.containsKey(record.url())) {
            return Optional.empty();
        }
        return loadability.refusalFor(record);
    }

    private Map<URI, InstallationCheck> installedEntries(List<ArtefactSelection> rows) {
        Map<URI, InstallationCheck> installed = new LinkedHashMap<>();
        for (ArtefactSelection row : rows) {
            ArtefactRecord record = row.artefact();
            try {
                InstallationCheck check = cache.verify(record);
                if (check.installed()) {
                    installed.put(record.url(), check);
                }
            } catch (IOException unreadable) {
                /*
                 * AN ENTRY THAT CANNOT BE READ IS NOT ONE THAT REPORTS ITSELF INSTALLED, which is
                 * R-TOOL-04 read literally rather than a failure swallowed: the rule makes
                 * "installed" mean a marker present AND its recorded checksums verified, and
                 * neither can be established through an I/O failure.  The row therefore offers the
                 * install, which is the one action that repairs both this case and a genuinely
                 * absent entry -- ArtefactInstaller discards any directory that does not verify
                 * before it builds a new one.  Propagating instead would blank the whole Tool
                 * Manager over one unreadable directory, which is the trade ProbeGatedOffers
                 * already refused to make for an unreachable binary.
                 */
            }
        }
        return installed;
    }

    /*
     * The releases this build of CometGUI knows of for one tool, newest first, each with the first
     * row that names it.  THE WHOLE MANIFEST, not select(): "which releases exist" is a question
     * about releases, and asking it of this host's rows would delete Percolator 3.09 from a Linux
     * Tool Manager entirely rather than showing it as unavailable here.  The row is kept only for
     * the release's identity -- upstream's own spelling of the version -- and the first in manifest
     * order is taken, so the spelling shown is the one the manifest declares first.
     */
    private Map<ToolVersion, ArtefactRecord> releasesOf(ToolName tool) {
        Map<ToolVersion, ArtefactRecord> firstRow = new LinkedHashMap<>();
        for (ArtefactRecord record : manifest.artefacts()) {
            if (record.tool() == tool) {
                firstRow.putIfAbsent(record.version(), record);
            }
        }
        List<ToolVersion> newestFirst = new ArrayList<>(firstRow.keySet());
        newestFirst.sort(Comparator.reverseOrder());
        Map<ToolVersion, ArtefactRecord> ordered = new LinkedHashMap<>();
        for (ToolVersion version : newestFirst) {
            ordered.put(version, firstRow.get(version));
        }
        return ordered;
    }

    private static List<ArtefactSelection> rowsOf(
            List<ArtefactSelection> selections, ToolVersion version) {
        List<ArtefactSelection> rows = new ArrayList<>();
        for (ArtefactSelection selection : selections) {
            if (selection.artefact().version().equals(version)) {
                rows.add(selection);
            }
        }
        return rows;
    }

    /*
     * NO CAPABILITIES AND NO ADVISORIES ON A BUILD THAT IS NOT PUBLISHED HERE.  Both are properties
     * of a row rather than of a release -- the rows that do exist for Percolator 3.09 carry "runs
     * under Rosetta 2" and "needs the Visual C++ runtime", which are false statements about a Linux
     * machine -- and R-TOOL-08's principle is that absent positive evidence a capability is absent.
     * The state is the whole message: upstream publishes nothing of this release for this platform.
     */
    private static ToolOffer unavailableHere(ArtefactRecord elsewhere) {
        return new ToolOffer(
                elsewhere.tool(),
                elsewhere.version(),
                ToolOrigin.MANAGED,
                ToolInstallState.UNAVAILABLE_ON_THIS_PLATFORM,
                List.of(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                OptionalLong.empty());
    }

    private static ToolOffer shownButNotRunnable(
            ArtefactRecord record, LoaderDiagnostic diagnostic) {
        return managedOffer(
                record,
                ToolInstallState.HOST_REQUIREMENTS_NOT_MET,
                record.capabilities(),
                Optional.of(diagnostic),
                Optional.empty());
    }

    private ToolOffer offerFor(ArtefactRecord record, InstallationCheck verified) {
        ToolInstallState attempt = attempts.get(keyOf(record));
        if (attempt == ToolInstallState.INSTALLING) {
            return managedOffer(
                    record,
                    ToolInstallState.INSTALLING,
                    record.capabilities(),
                    Optional.empty(),
                    Optional.empty());
        }
        if (verified != null) {
            InstallationMarker marker = verified.requireMarker();
            return managedOffer(
                    record,
                    ToolInstallState.INSTALLED,
                    probed(marker),
                    Optional.empty(),
                    Optional.of(verified.directory().resolve(record.executablePath())));
        }
        if (attempt == ToolInstallState.FAILED) {
            return managedOffer(
                    record,
                    ToolInstallState.FAILED,
                    record.capabilities(),
                    Optional.empty(),
                    Optional.empty());
        }
        return managedOffer(
                record,
                ToolInstallState.NOT_INSTALLED,
                record.capabilities(),
                Optional.empty(),
                Optional.empty());
    }

    private static ToolOffer managedOffer(
            ArtefactRecord record,
            ToolInstallState state,
            List<DeclaredCapability> capabilities,
            Optional<LoaderDiagnostic> diagnostic,
            Optional<Path> installedPath) {
        return new ToolOffer(
                record.tool(),
                record.version(),
                ToolOrigin.MANAGED,
                state,
                capabilities,
                record.advisories(),
                diagnostic,
                installedPath,
                /*
                 * The length of the artefact itself, as the manifest pins it.  This is the number a
                 * scientist is shown before starting a download -- PDV is 103 407 417 bytes -- and
                 * it comes from the row rather than from the file on disk, so it is the same before
                 * the download as after it.
                 */
                OptionalLong.of(record.sizeBytes()));
    }

    /*
     * R-TOOL-07: WHERE MANIFEST AND PROBE DISAGREE, THE PROBE WINS.  An installed build's
     * capabilities are the ones step 6 confirmed and the completion marker recorded -- and the
     * marker was just verified, digest by digest, by the ToolCache.verify call that produced this
     * check, so the claim rests on bytes that are still the bytes that were probed.  A build that
     * is not installed can only offer what the manifest declares, evidence value and all.
     */
    private List<DeclaredCapability> probed(InstallationMarker marker) {
        List<DeclaredCapability> declared = new ArrayList<>();
        for (ToolCapability capability : marker.capabilities()) {
            declared.add(
                    new DeclaredCapability(
                            capability,
                            CapabilityEvidence.OBSERVED_BY_EXECUTION,
                            "probed by execution on "
                                    + host.id()
                                    + " when "
                                    + marker.describe()
                                    + " was installed, and recorded in the completion marker whose"
                                    + " checksums still match (R-TOOL-07)"));
        }
        return declared;
    }

    // ------------------------------------------------------------------------- the install --

    /*
     * WHICH ROW AN INSTALL IS FOR.  select(host, tool, version) is asked, not the raw manifest, so
     * a request for a release this platform does not publish is refused instead of fetching another
     * platform's bytes; and the OFFERED set decides, so R-PERC-01's rule that the product never
     * presents a build it cannot run is enforced at the one place a user can act on it.
     *
     * The first offered row is taken, and select() orders native before translated, so on Apple
     * silicon a Comet install takes the aarch64 build rather than the x86-64 one that would run
     * under Rosetta 2.  THAT CHOICE IS THE PORT'S TO MAKE ONLY BECAUSE THE PORT CANNOT EXPRESS THE
     * OTHER ONE: install(tool, version) names a release, and Comet 2026.02.2 is two macOS rows, so
     * a user on that machine cannot ask for the translated one.  Reported upward rather than
     * papered over; on every platform this project can execute today the question does not arise.
     */
    private ArtefactRecord installableRecord(ToolName tool, ToolVersion version) {
        List<ArtefactSelection> rows = manifest.select(host, tool, version);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException(
                    "no offer names "
                            + tool.id()
                            + " "
                            + version.text()
                            + " on "
                            + host.id()
                            + ": the manifest publishes no artefact of that release for this"
                            + " platform, which is what UNAVAILABLE_ON_THIS_PLATFORM means on the"
                            + " row");
        }
        Map<URI, InstallationCheck> installed = installedEntries(rows);
        ProbeGatedOffers.Decision decision =
                gate.decide(rows, record -> refusalFor(record, installed));
        if (!decision.offered().isEmpty()) {
            return decision.offered().get(0);
        }
        ProbeGatedOffers.Refusal refusal = decision.refused().get(0);
        throw new IllegalArgumentException(
                "no offer names "
                        + tool.id()
                        + " "
                        + version.text()
                        + " on "
                        + host.id()
                        + " as something this host can install: "
                        + refusal.diagnostic().message());
    }

    private void runInstall(
            ArtefactRecord record,
            InstallProgressListener listener,
            Cancellation cancellation,
            String entry) {
        try {
            installer.install(record, listener, cancellation);
            attempts.remove(entry);
        } catch (InstallCancelledException cancelled) {
            /*
             * CANCELLING IS NOT FAILING, and the row must not say it is.  Nothing was written to
             * the tool cache, so the honest state is the one the build was in before the user
             * pressed the button, and removing the entry is what makes ToolCache.verify say so.
             */
            attempts.remove(entry);
        } catch (IOException | RuntimeException failed) {
            attempts.put(entry, ToolInstallState.FAILED);
        }
    }

    /*
     * THE CACHE'S OWN KEY, not the download URL.  "Which row is this?" is answered by the URL
     * everywhere else in this class, but "which entry is being installed?" is a question about a
     * directory, and the tool directory, the download directory and the R-TOOL-05 lock file are all
     * named from the tool, the version and the platform.  A second key for the same entry would be
     * a second answer to the question of what is installed.
     */
    private static String keyOf(ArtefactRecord record) {
        return ToolCache.key(record.tool(), record.version(), record.platform());
    }

    /**
     * Describes the manager by the host it answers for and the cache it installs into.
     *
     * @return a description for a log line or an assertion message
     */
    @Override
    public String toString() {
        return "ManagedToolManager[" + host.id() + ", " + cache.root() + "]";
    }

    /**
     * One install's stop button, and the question the transfer loop asks between chunks.
     *
     * <p>One object for both because they are one fact. A handle that set a flag some other object
     * read would be two places the same answer lives, and they would disagree on the first thread
     * that read one without the other.
     */
    private static final class Cancellation implements InstallHandle, DownloadCancellation {

        private final AtomicBoolean cancelled = new AtomicBoolean();

        @Override
        public void cancel() {
            cancelled.set(true);
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }
    }
}
