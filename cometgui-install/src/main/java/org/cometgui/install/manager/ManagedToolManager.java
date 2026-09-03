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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
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
import org.cometgui.install.probe.LoaderOutputClassifier;
import org.cometgui.install.probe.ProbeContext;
import org.cometgui.install.probe.ProbeGatedOffers;
import org.cometgui.install.registry.ArtefactManifest;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.install.registry.ArtefactSelection;

/**
 * The Tool Manager's runtime: what this machine may be shown, and what happens when a scientist
 * presses Install.
 *
 * <p>This is the implementation of {@link ToolManager}, the one seam the user interface is allowed
 * to see. Everything behind it -- the manifest and its selection rules, the host-requirement check,
 * the offered-set gate, the tool cache and its completion markers, the eight-step atomic install --
 * is composed here and is invisible from a view.
 *
 * <h2>What an offer costs to produce</h2>
 *
 * <p>{@link #offers()} is documented as answering "from what is already known", and it is written
 * to keep that promise: it <strong>starts no process</strong>. Three things decide a row, and none
 * of them launches anything:
 *
 * <ol>
 *   <li>{@link ArtefactManifest#select} -- what upstream publishes that this host could run at all,
 *       newest first, native before translated, one row per download;
 *   <li>{@link ProbeGatedOffers} -- of those, which may be presented for selection. It refuses a
 *       build whose declared floor this host provably fails ({@code R-TOOL-03}: say so
 *       <em>before</em> the download rather than after), and one whose binary this process has
 *       already watched fail to load ({@link ProbeRefusalLog});
 *   <li>{@code ToolCache.verify} -- {@code R-TOOL-04}'s question, answered by re-reading the
 *       completion marker and re-hashing every file it records.
 * </ol>
 *
 * <p>The third is the only one that touches a file: it re-hashes every file the completion marker
 * records, every time. That is cheaper than it sounds and the reason is worth knowing -- the marker
 * records the executable and the companion members the manifest pins, not the whole payload, so for
 * PDV it is a 1343276-byte JAR rather than the 103407417-byte archive it came out of. The cost is
 * deliberate and is unit 5's decision, not this class's: a cached answer would be a claim that
 * nothing has touched the cache since, which is the claim {@code R-TOOL-04} exists to stop anybody
 * making.
 *
 * <h2>A row that cannot be installed here is still a row</h2>
 *
 * <p>{@code R-PERC-01} forbids <em>promising</em> a build that cannot run. It is not a reason to
 * pretend the build does not exist, and an empty list is worse than an explanation: Percolator 3.09
 * publishes no Linux artefact at all, and a Linux user who simply never sees 3.09 cannot tell that
 * from a bug in CometGUI. So {@link #offers()} carries three kinds of row that are not offers to
 * install:
 *
 * <ul>
 *   <li>{@link ToolInstallState#UNAVAILABLE_ON_THIS_PLATFORM} -- the manifest knows this version
 *       and publishes nothing this host can run. It carries <strong>no capabilities and no
 *       advisories</strong>, because every capability and every advisory in the manifest belongs to
 *       a <em>row</em>, and this host has no row: taking them from another platform's row is how a
 *       Rosetta 2 advisory reaches a Linux user.
 *   <li>{@link ToolInstallState#HOST_REQUIREMENTS_NOT_MET} -- an artefact exists and this machine
 *       does not meet its declared floor, with the {@code R-PLAT-03} diagnostic naming the required
 *       version, this host's version and the alternatives.
 *   <li>{@link ToolInstallState#FAILED} -- an install was attempted and did not succeed. Where it
 *       was the loadability probe that refused the build, the row also carries that diagnostic and
 *       the build stops being installable, which is {@code R-TOOL-06}'s last sentence.
 * </ul>
 *
 * <h2>Installing, and the order things settle in</h2>
 *
 * <p>{@link #install} returns as soon as the install is under way; everything after that arrives
 * through the caller's {@link InstallProgressListener}, including how it ended. Two orderings
 * matter and both are deliberate.
 *
 * <p><strong>The row stops saying INSTALLING before the terminal report is forwarded.</strong> This
 * class wraps the caller's listener, and on a terminal phase it settles the in-flight bookkeeping
 * first and forwards second -- so a listener that asks for {@link #offers()} the instant it is told
 * the install finished never sees a finished install still described as running.
 *
 * <p><strong>A loadability refusal is recorded inside the probe, not in a catch block.</strong>
 * {@code ArtefactInstaller} reports the terminal phase from a {@code finally}, so any code catching
 * the failure runs <em>after</em> the caller has already been told. {@link ProbeRefusalLog} wraps
 * install step 6 instead, which puts the refusal in the log before the terminal report exists.
 * {@code R-TOOL-06} says a build that fails to load is never offered, and "never" cannot have a
 * window in it.
 *
 * <p>What does settle after the terminal report is the weaker half: whether the row reads {@link
 * ToolInstallState#FAILED} or {@link ToolInstallState#NOT_INSTALLED}. Both are true of a build that
 * is not installed, the difference is only whether an attempt is remembered, and it corrects itself
 * on the next call. It is written down here rather than left for somebody to find.
 *
 * <p>Safe for use from the JavaFX application thread, and safe for an install running on another:
 * the three pieces of mutable state are concurrent collections and the install itself is handed to
 * an {@link Executor} the caller supplies.
 */
public final class ManagedToolManager implements ToolManager {

    /**
     * What an installed build's capability claims say about where they came from.
     *
     * <p>{@code R-TOOL-07}: where the manifest and the probe disagree, the probe wins. So an
     * installed row's capabilities are the set the probe watched the binary do at install time --
     * not the manifest's claims about that build -- and the note says so, because {@code
     * DeclaredCapability} refuses evidence with no provenance.
     */
    static final String PROBED_NOTE =
            "probed by execution at install time, on this host, and recorded in the completion"
                    + " marker; R-TOOL-07 makes the probe the authority over the manifest's claim"
                    + " for the host: ";

    private final ArtefactManifest manifest;
    private final HostPlatform host;
    private final ProbeGatedOffers gate;
    private final LoaderOutputClassifier classifier;
    private final Function<ArtefactRecord, List<String>> alternatives;
    private final ProbeRefusalLog probeRefusals;
    private final ArtefactInstaller installer;
    private final LocalBinaryRegistrar localBinaries;
    private final Executor installs;

    /** Downloads with an install running, by how many installs of it are running. */
    private final Map<URI, Integer> inFlight = new ConcurrentHashMap<>();

    /** Downloads whose last install attempt ended in {@code FAILED}. */
    private final Set<URI> failedInstalls = ConcurrentHashMap.newKeySet();

    /** Binaries the user pointed at, in the order they were registered. */
    private final List<ToolOffer> localOffers = new CopyOnWriteArrayList<>();

    /**
     * Composes the Tool Manager over one manifest, one host and one cache.
     *
     * @param manifest the shipped artefact manifest
     * @param host the machine in front of the user
     * @param gate {@code R-TOOL-06}'s offered-set rule
     * @param classifier what turns a remembered probe failure back into an {@code R-PLAT-03}
     *     diagnostic; the same instance the gate was built with, so one failure cannot be worded
     *     two ways
     * @param alternatives what to name instead of a refused build, usually {@code
     *     ManifestAlternatives::forArtefact}
     * @param probeRefusals what this process has watched fail to load; the same instance that wraps
     *     install step 6
     * @param installer the eight-step atomic install, and the authority on what is in the cache
     * @param localBinaries {@code R-TOOL-08}'s local registration, implemented in {@code
     *     org.cometgui.tools} and adapted in {@code cometgui-app}
     * @param installs where an install runs; {@link #install} hands it the work and returns
     * @throws NullPointerException if any argument is {@code null}
     */
    public ManagedToolManager(
            ArtefactManifest manifest,
            HostPlatform host,
            ProbeGatedOffers gate,
            LoaderOutputClassifier classifier,
            Function<ArtefactRecord, List<String>> alternatives,
            ProbeRefusalLog probeRefusals,
            ArtefactInstaller installer,
            LocalBinaryRegistrar localBinaries,
            Executor installs) {
        this.manifest = Objects.requireNonNull(manifest, "manifest");
        this.host = Objects.requireNonNull(host, "host");
        this.gate = Objects.requireNonNull(gate, "gate");
        this.classifier = Objects.requireNonNull(classifier, "classifier");
        this.alternatives = Objects.requireNonNull(alternatives, "alternatives");
        this.probeRefusals = Objects.requireNonNull(probeRefusals, "probeRefusals");
        this.installer = Objects.requireNonNull(installer, "installer");
        this.localBinaries = Objects.requireNonNull(localBinaries, "localBinaries");
        this.installs = Objects.requireNonNull(installs, "installs");
    }

    // ------------------------------------------------------------------------------ offers --

    @Override
    public List<ToolOffer> offers() {
        List<ToolOffer> offers = new ArrayList<>();
        for (ToolName tool : ToolName.values()) {
            List<ArtefactSelection> selected = manifest.select(host, tool);
            Map<URI, LoaderDiagnostic> refused = refusalsAmong(selected);
            for (ArtefactSelection selection : selected) {
                ArtefactRecord record = selection.artefact();
                LoaderDiagnostic diagnostic = refused.get(record.url());
                offers.add(
                        diagnostic == null ? offeredRow(record) : refusedRow(record, diagnostic));
            }
            for (ToolVersion version : versionsWithNoArtefactHere(tool, selected)) {
                offers.add(unavailableRow(tool, version));
            }
            for (ToolOffer local : localOffers) {
                if (local.tool() == tool) {
                    offers.add(local);
                }
            }
        }
        return List.copyOf(offers);
    }

    /*
     * The decision is keyed back onto the selection order rather than used as it comes.  The gate
     * answers with two lists and this class renders one, and a Tool Manager that listed everything
     * it could install and then everything it could not would have thrown away the manifest's
     * newest-first ordering -- the order R-PERC-02's "latest compatible" walks -- for no reason
     * beyond the shape of the return value.
     */
    private Map<URI, LoaderDiagnostic> refusalsAmong(List<ArtefactSelection> selected) {
        Map<URI, LoaderDiagnostic> refused = new LinkedHashMap<>();
        for (ProbeGatedOffers.Refusal refusal :
                gate.decide(selected, this::rememberedRefusal).refused()) {
            refused.put(refusal.artefact().url(), refusal.diagnostic());
        }
        return refused;
    }

    /*
     * The LoadabilityCheck this class hands the gate does not run anything: offers() is called from
     * the JavaFX application thread and a list of rows is not worth four process launches.  What it
     * answers with is what the install probe already watched happen, which is the only evidence
     * R-PERC-01's "its post-install runtime probe has passed" can be read from anyway.
     */
    private Optional<LoaderDiagnostic> rememberedRefusal(ArtefactRecord record) {
        return probeRefusals
                .refusalFor(record)
                .map(kind -> classifier.of(kind, contextFor(record)));
    }

    private ProbeContext contextFor(ArtefactRecord record) {
        return new ProbeContext(
                ProbeContext.subjectOf(record.executablePath()),
                record.minimumHostRequirements().requiredHostLibraries(),
                alternatives.apply(record));
    }

    private ToolOffer offeredRow(ArtefactRecord record) {
        if (inFlight.containsKey(record.url())) {
            return row(record, ToolInstallState.INSTALLING, record.capabilities(), null, null);
        }
        Optional<InstallationCheck> installed = installedEntry(record);
        if (installed.isPresent()) {
            InstallationCheck check = installed.get();
            return row(
                    record,
                    ToolInstallState.INSTALLED,
                    probedCapabilities(check),
                    null,
                    check.directory().resolve(record.executablePath()));
        }
        ToolInstallState state =
                failedInstalls.contains(record.url())
                        ? ToolInstallState.FAILED
                        : ToolInstallState.NOT_INSTALLED;
        return row(record, state, record.capabilities(), null, null);
    }

    private ToolOffer refusedRow(ArtefactRecord record, LoaderDiagnostic diagnostic) {
        /*
         * WHICH REFUSAL THIS WAS decides the state, and the two are different sentences to a
         * scientist.  ProbeGatedOffers asks the advance check first and only then the loadability
         * check, so a build in the refusal log got past the floors and was refused by its own
         * binary -- which is an install that was attempted and did not succeed, FAILED's own
         * words.  Everything else here is a floor this machine does not meet, which is knowable
         * before anything is downloaded and is what R-TOOL-03 exists for.
         */
        ToolInstallState state =
                probeRefusals.refusalFor(record).isPresent()
                        ? ToolInstallState.FAILED
                        : ToolInstallState.HOST_REQUIREMENTS_NOT_MET;
        return row(record, state, record.capabilities(), diagnostic, null);
    }

    private ToolOffer unavailableRow(ToolName tool, ToolVersion version) {
        return new ToolOffer(
                tool,
                version,
                ToolOrigin.MANAGED,
                ToolInstallState.UNAVAILABLE_ON_THIS_PLATFORM,
                OptionalLong.empty(),
                List.of(),
                List.of(),
                Optional.empty(),
                Optional.empty());
    }

    private ToolOffer row(
            ArtefactRecord record,
            ToolInstallState state,
            List<DeclaredCapability> capabilities,
            LoaderDiagnostic diagnostic,
            Path installedPath) {
        return new ToolOffer(
                record.tool(),
                record.version(),
                ToolOrigin.MANAGED,
                state,
                OptionalLong.of(record.sizeBytes()),
                capabilities,
                record.advisories(),
                Optional.ofNullable(diagnostic),
                Optional.ofNullable(installedPath));
    }

    private List<DeclaredCapability> probedCapabilities(InstallationCheck check) {
        List<DeclaredCapability> probed = new ArrayList<>();
        for (ToolCapability capability : check.requireMarker().capabilities()) {
            probed.add(
                    new DeclaredCapability(
                            capability,
                            CapabilityEvidence.OBSERVED_BY_EXECUTION,
                            PROBED_NOTE + check.directory()));
        }
        return probed;
    }

    /*
     * R-TOOL-04 makes an entry installed only when the marker written last is present AND its
     * recorded checksums match.  A directory that cannot be read is not such an entry, so the
     * honest answer is "not installed" -- and it is also the useful one, because installing again
     * is exactly what discards a directory nobody can say anything true about and rebuilds it.
     * The failure is confined to the one row: a cache entry somebody has made unreadable must not
     * blank the whole Tool Manager, which is the same rule ProbeGatedOffers holds for a binary that
     * cannot be reached.
     */
    private Optional<InstallationCheck> installedEntry(ArtefactRecord record) {
        try {
            InstallationCheck check = installer.cache().verify(record);
            return check.installed() ? Optional.of(check) : Optional.empty();
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }

    /*
     * A version the manifest knows and this host cannot run any build of.  Read out of the raw
     * artefact list rather than computed from anything else, because that list IS what upstream
     * publishes: R-PERC-12's "absent is honest" is a statement about rows that are not there, and
     * the only way to say "3.09 exists and not for you" is to look at the rows for other platforms.
     */
    private List<ToolVersion> versionsWithNoArtefactHere(
            ToolName tool, List<ArtefactSelection> selected) {
        Set<ToolVersion> runnable = new LinkedHashSet<>();
        for (ArtefactSelection selection : selected) {
            runnable.add(selection.artefact().version());
        }
        Set<ToolVersion> absent = new LinkedHashSet<>();
        for (ArtefactRecord record : manifest.artefacts()) {
            if (record.tool() == tool && !runnable.contains(record.version())) {
                absent.add(record.version());
            }
        }
        List<ToolVersion> ordered = new ArrayList<>(absent);
        ordered.sort(Comparator.reverseOrder());
        return ordered;
    }

    // ----------------------------------------------------------------------------- installing --

    @Override
    public InstallHandle install(
            ToolName tool, ToolVersion version, InstallProgressListener listener) {
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(listener, "listener");
        ArtefactRecord record = installableRecord(tool, version);
        AtomicBoolean cancelled = new AtomicBoolean();
        enterFlight(record.url());
        /*
         * An executor that refuses the work has not started an install, and the row must not be
         * left saying INSTALLING: nothing would ever report a terminal phase for it, so it would
         * say so for ever.  Written as a flag and a finally rather than as a catch-and-rethrow,
         * because rethrowing a caught RuntimeException is what SpotBugs reports as
         * THROWS_METHOD_THROWS_RUNTIMEEXCEPTION and the refusal belongs to the caller unchanged.
         */
        boolean submitted = false;
        try {
            installs.execute(() -> runInstall(record, listener, cancelled));
            submitted = true;
        } finally {
            if (!submitted) {
                leaveFlight(record.url());
            }
        }
        return () -> cancelled.set(true);
    }

    /*
     * The record is taken from the gate's OFFERED list and not merely from the manifest, so that
     * "must be one offers() named" means the same thing here as it does there.  A build this host
     * fails the floor of, or one whose binary has been watched refuse to start, is not installable
     * by asking for it directly either -- otherwise R-TOOL-06's rule would hold for the list and
     * not for the button.
     */
    private ArtefactRecord installableRecord(ToolName tool, ToolVersion version) {
        List<ArtefactRecord> offered =
                gate.decide(manifest.select(host, tool, version), this::rememberedRefusal)
                        .offered();
        if (offered.isEmpty()) {
            throw new IllegalArgumentException(
                    "no offer on this host names "
                            + tool.id()
                            + " "
                            + version.text()
                            + "; "
                            + host.id()
                            + " is offered "
                            + describeInstallable(tool));
        }
        return offered.get(0);
    }

    private String describeInstallable(ToolName tool) {
        List<String> describable = new ArrayList<>();
        for (ArtefactRecord record :
                gate.decide(manifest.select(host, tool), this::rememberedRefusal).offered()) {
            describable.add(record.describe());
        }
        return describable.isEmpty() ? "no build of that tool at all" : describable.toString();
    }

    /*
     * THREE MUTANTS HERE AND IN settlingListener SCORE TIMED_OUT RATHER THAN KILLED, and the reason
     * is worth writing down rather than leaving for somebody to rediscover.  Each of them --
     * dropping the call to runInstall, dropping the forwarded terminal report, returning a null
     * listener -- stops the install from ever reporting that it finished, and a test waiting for
     * that report blocks.  PIT gives a mutant its own timeout, so a blocking test ends the mutant's
     * evaluation before a test that would fail fast is reached, and scripts/build.sh counts only
     * KILLED.
     *
     * They are all covered and all fatal: ManagedToolManagerLifecycleTest runs the same installs
     * through Runnable::run, where every one of the three fails an assertion with no waiting at
     * all.  Making them count would mean bounding every wait at under four seconds, which would
     * trade three accounted timeouts for a suite that goes red under load -- and a flaky test is
     * worse than a mutant with an argument beside it.
     */
    private void runInstall(
            ArtefactRecord record, InstallProgressListener listener, AtomicBoolean cancelled) {
        try {
            installer.install(record, settlingListener(record, listener), cancelled::get);
            failedInstalls.remove(record.url());
        } catch (IOException | RuntimeException stopped) {
            /*
             * A CANCELLED install is not a FAILED one and must not be remembered as one: a user who
             * cancelled a 99 MB download has not encountered an error, and a row that read "failed"
             * afterwards would tell them they had.  It equally does not CLEAR an earlier failure --
             * cancelling an install says nothing about whether the last one worked.
             */
            if (!(stopped instanceof InstallCancelledException)) {
                failedInstalls.add(record.url());
            }
        }
    }

    /*
     * The in-flight bookkeeping is settled on the way THROUGH the terminal report rather than after
     * it.  ArtefactInstaller promises exactly one terminal phase and that it is the last thing the
     * listener sees, so a user interface refreshing its rows from that callback is the ordinary
     * case, not an exotic one -- and if the row were settled afterwards, that refresh would show a
     * finished install still running.
     */
    private InstallProgressListener settlingListener(
            ArtefactRecord record, InstallProgressListener listener) {
        return progress -> {
            if (progress.phase().isTerminal()) {
                leaveFlight(record.url());
            }
            listener.onInstallProgress(progress);
        };
    }

    private void enterFlight(URI url) {
        inFlight.merge(url, 1, Integer::sum);
    }

    private void leaveFlight(URI url) {
        inFlight.computeIfPresent(url, (key, running) -> running == 1 ? null : running - 1);
    }

    // ------------------------------------------------------------------- local registration --

    @Override
    public ToolOffer registerLocalBinary(ToolName tool, Path executable)
            throws ToolRegistrationException {
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(executable, "executable");
        if (!executable.isAbsolute()) {
            throw new IllegalArgumentException(
                    "executable must be an absolute path, because a relative one means a different"
                            + " file depending on where this process was started, but was: "
                            + executable);
        }
        ToolOffer registered =
                Objects.requireNonNull(
                        localBinaries.register(tool, executable),
                        "a registrar answers with an offer or throws; it must not return null");
        requireRegistrationOf(tool, executable, registered);
        /*
         * Registering the same file twice REPLACES the earlier row rather than adding a second.
         * The point of registering again is that the file has changed -- R-TOOL-07 re-confirms the
         * capability set when the executable's checksum does -- and two rows naming one path would
         * make the Tool Manager show a stale capability set beside a fresh one with nothing to
         * tell them apart.
         */
        localOffers.removeIf(
                existing -> existing.installedPath().equals(registered.installedPath()));
        localOffers.add(registered);
        return registered;
    }

    private static void requireRegistrationOf(ToolName tool, Path executable, ToolOffer registered)
            throws ToolRegistrationException {
        if (registered.tool() != tool) {
            throw new ToolRegistrationException(
                    "The file at "
                            + executable
                            + " was offered for registration as "
                            + tool.id()
                            + " and came back registered as "
                            + registered.tool().id()
                            + "; CometGUI will not file one tool's binary under another's name.");
        }
        if (registered.origin() != ToolOrigin.LOCAL) {
            throw new ToolRegistrationException(
                    "The file at "
                            + executable
                            + " came back recorded as "
                            + registered.origin()
                            + " and a binary CometGUI did not download is "
                            + ToolOrigin.LOCAL
                            + "; its provenance is whatever you know about where it came from, and"
                            + " recording it as managed would claim a pinned checksum that does not"
                            + " exist.");
        }
    }

    /**
     * Describes the manager by the host it answers for and what it is holding.
     *
     * @return a description for a log line or an exception message
     */
    @Override
    public String toString() {
        return "ManagedToolManager["
                + host.id()
                + ", "
                + manifest.artefacts().size()
                + " artefact(s), "
                + localOffers.size()
                + " local binary/binaries, "
                + inFlight.size()
                + " install(s) running]";
    }
}
