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
import org.cometgui.domain.tools.InstallPhase;
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
import org.cometgui.install.cache.InstallationCheck;
import org.cometgui.install.cache.InstallationMarker;
import org.cometgui.install.cache.ToolCache;
import org.cometgui.install.download.DownloadCancellation;
import org.cometgui.install.probe.HostRequirementCheck;
import org.cometgui.install.probe.HostRuntimeVersions;
import org.cometgui.install.probe.LoaderOutputClassifier;
import org.cometgui.install.probe.ManifestAlternatives;
import org.cometgui.install.probe.ProbeGatedOffers;
import org.cometgui.install.registry.ArtefactCompanion;
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
     * The binaries the user pointed CometGUI at, by the path they named.
     *
     * <h2>Why they are held at all</h2>
     *
     * <p>{@code phases/PHASE-05-tool-registry.rst} puts "installed, available,
     * unavailable-on-this-platform <em>and local</em> tools" in the Tool Manager's scope, and the
     * specification's <em>Percolator installation modes</em> calls registering a local binary the
     * documented remedy wherever no managed XML-capable build exists for a platform. A remedy whose
     * result the Tool Manager cannot show is not a remedy, so {@link #registerLocalBinary} keeps
     * what it registered and {@link #offers()} lists it.
     *
     * <h2>Keyed on the path, not on the tool and the version</h2>
     *
     * <p>The key is the absolute path the caller named, normalised. Registering the <strong>same
     * path</strong> again replaces the row rather than adding one: the point of asking again is
     * that the file may have changed, and {@link LinkedHashMap} keeps a replaced entry where it was
     * so the row does not jump about while a user re-probes it. Registering a <strong>second,
     * different path</strong> for the same tool adds a second row, because a user may hold several
     * builds and the product's business is to show what exists rather than to choose between them.
     *
     * <p>It is <strong>not</strong> keyed on the tool and the version. Two different files can
     * report the same version -- a rebuild, a copy kept beside an older one -- and collapsing them
     * would hide one binary behind another, which is the same mistake the Tool Manager's own row
     * identifiers exist to avoid. Two paths that happen to resolve to one file through a symbolic
     * link are two rows; the path is what the user chose and what the row shows.
     *
     * <h2>This session only, and that is a stated boundary</h2>
     *
     * <p><strong>A registration does not survive a restart.</strong> Persisting one needs a store
     * this phase does not own -- no phase has built an application preference store, and the empty
     * Settings section Phase 02 left for one was removed from navigation in Phase 07 because no
     * specified preference needed it -- so after a restart the Tool Manager shows the managed rows
     * and the user registers again. Written down here so that it is a limit somebody decided rather
     * than one nobody noticed.
     */
    private final Map<Path, ToolOffer> registeredBinaries = new LinkedHashMap<>();

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
        ToolOffer registered = checkedRegistration(tool, registrar.register(executable));
        synchronized (registeredBinaries) {
            registeredBinaries.put(executable.normalize(), registered);
        }
        return registered;
    }

    /**
     * The binaries registered for one tool, in the order they were registered.
     *
     * @param tool the tool
     * @return the offers, immutable and usually empty
     */
    private List<ToolOffer> registeredFor(ToolName tool) {
        List<ToolOffer> theirs = new ArrayList<>();
        synchronized (registeredBinaries) {
            for (ToolOffer offer : registeredBinaries.values()) {
                if (offer.tool() == tool) {
                    theirs.add(offer);
                }
            }
        }
        return List.copyOf(theirs);
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
        /*
         * A LOCAL BINARY IS STILL THIS TOOL, SO IT SITS WITH THIS TOOL -- after the releases
         * CometGUI can install, in the order the user registered it.
         *
         * With the tool, because a reader looking for "what Percolator does this machine have"
         * looks in one place and expects to find all of them; a group of managed rows followed by a
         * separate group of local ones would make them hunt.
         *
         * After them, and not merged into the version ordering, because a registered binary is not
         * a release the manifest knows about.  Its version comes from probing the user's own file
         * and can be anything at all -- including a number the manifest also names -- so placing it
         * among the managed rows by version would read as though CometGUI offered it, which is the
         * one thing R-PERC-01 forbids the interface to imply.
         */
        offers.addAll(registeredFor(tool));
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
                OptionalLong.of(wholeDownloadOf(record)));
    }

    /*
     * HOW MANY BYTES WILL MOVE, which is the question a user asks before pressing Install -- and it
     * is a question about step 1, not about one file.  Step 1 fetches the artefact AND every
     * companion the record names, in one go, so quoting the artefact alone would describe
     * Percolator 3.07.1 on Linux as a 946 303-byte download when the install really transfers
     * 2 798 963: the .deb the two XSDs come out of is 1 852 660 of those bytes.  A number three
     * times smaller than the transfer it names is a value that misstates what it reports, and
     * unit 9 renders it under a label that makes it a claim to the user.
     *
     * Every length comes from the manifest rather than from a file on disk, so the figure is the
     * same before the download as after it, and it is deliberately NOT the size of what ends up
     * installed: an archive is unpacked and a payload is taken out of a package, so that is a
     * different number and this record does not carry it.
     */
    private static long wholeDownloadOf(ArtefactRecord record) {
        long bytes = record.sizeBytes();
        for (ArtefactCompanion companion : record.companions()) {
            bytes += companion.sizeBytes();
        }
        return bytes;
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

    /*
     * THE ROW AGREES WITH THE TERMINAL REPORT BEFORE THE LISTENER SEES IT.
     *
     * ArtefactInstaller sends exactly one terminal report, from a finally block, and then returns
     * or lets the failure propagate.  This method used to write the attempt map afterwards, so a
     * listener that re-read offers() the moment it saw DONE was told INSTALLING -- the install had
     * finished and the port had not caught up.  With a real interface thread the listener's work is
     * queued and this method wins the race before anything looks, which is exactly what made the
     * defect invisible: it was hidden by scheduling latency, not by correctness, and a defect that
     * only shows under one executor is this project's signature shape wearing a different coat.
     *
     * So the settle happens INSIDE the listener this class hands to the installer, before the
     * caller's listener is called at all.  It is graded with the executor that loses -- an install
     * run on the calling thread, with a listener that asks offers() synchronously.
     */
    private void runInstall(
            ArtefactRecord record,
            InstallProgressListener listener,
            Cancellation cancellation,
            String entry) {
        try {
            installer.install(record, settlingFirst(listener, entry), cancellation);
        } catch (IOException | RuntimeException stopped) {
            /*
             * NOTHING LEFT TO DO HERE, AND DELIBERATELY NO SECOND PLACE THAT WRITES THE STATE.
             * Every way out of ArtefactInstaller.install -- done, cancelled, failed -- reports its
             * terminal phase before it returns or throws, and the listener above has already
             * written the attempt map from it.  Writing it again here would be a second answer to
             * "what state is this entry in", and the second answer would be on a branch no test can
             * reach: a mutation no test can kill.  The throw is swallowed because this runs on an
             * install thread, where an escape would kill the thread with nobody to catch it, and
             * the row already says what happened.
             */
        }
    }

    /**
     * The caller's listener, with this manager's own state settled before every terminal report.
     *
     * @param listener the listener the caller gave to {@link #install}
     * @param entry the cache key of the build being installed
     * @return a listener that settles first and forwards second
     */
    private InstallProgressListener settlingFirst(InstallProgressListener listener, String entry) {
        return progress -> {
            if (progress.phase().isTerminal()) {
                settle(entry, progress.phase());
            }
            listener.onInstallProgress(progress);
        };
    }

    /*
     * CANCELLING IS NOT FAILING, and the row must not say it is.  Nothing was written to the tool
     * cache, so the honest state is the one the build was in before the user pressed the button,
     * and removing the entry is what makes ToolCache.verify the whole truth again.  A finished
     * install is removed for the same reason: the marker it wrote is now the answer.
     */
    private void settle(String entry, InstallPhase terminal) {
        if (terminal == InstallPhase.FAILED) {
            attempts.put(entry, ToolInstallState.FAILED);
        } else {
            attempts.remove(entry);
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
