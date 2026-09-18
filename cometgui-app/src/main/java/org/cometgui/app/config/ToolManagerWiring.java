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

package org.cometgui.app.config;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.cometgui.domain.ports.HashService;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.install.archive.ArtefactExtractor;
import org.cometgui.install.cache.ArtefactInstaller;
import org.cometgui.install.cache.PlatformFixups;
import org.cometgui.install.cache.ToolCache;
import org.cometgui.install.download.HttpDownloader;
import org.cometgui.install.manager.LocalBinaryRegistrar;
import org.cometgui.install.manager.ManagedToolManager;
import org.cometgui.install.probe.CapabilityProber;
import org.cometgui.install.probe.HostRuntimeVersions;
import org.cometgui.install.probe.LoadabilityProbe;
import org.cometgui.install.probe.LoaderOutputClassifier;
import org.cometgui.install.probe.ManifestAlternatives;
import org.cometgui.install.probe.ProbeGatedOffers;
import org.cometgui.install.probe.StagedToolProbe;
import org.cometgui.install.probe.VersionBanner;
import org.cometgui.install.registry.ArtefactManifest;
import org.cometgui.install.registry.ArtefactManifestReader;
import org.cometgui.install.verify.ArtefactVerifier;
import org.cometgui.install.verify.VerifiedDownloader;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.tools.api.JavaToolIdentities;
import org.cometgui.tools.api.ToolRunner;
import org.cometgui.tools.comet.CometCapabilityProbe;
import org.cometgui.tools.comet.CometCompanionGates;
import org.cometgui.tools.percolator.LocalPercolatorRegistration;
import org.cometgui.tools.percolator.PercolatorCapabilityProbe;

/**
 * Where the two halves of the installer are joined.
 *
 * <p>{@code cometgui-install} declares the probe seams and {@code cometgui-tools} implements them,
 * and <strong>neither module depends on the other</strong> -- deliberately, so that the tool
 * adapters can be written without an installer and the installer without a tool. This module is the
 * only one that sees both, so the seams are satisfied here, by method references, which is the
 * route phase 05 unit 7 proved in {@code StagedJavaToolProbeTest}.
 *
 * <p>Everything here is composition and nothing here is logic. The one decision it takes is which
 * adapter answers for which tool, and that decision is written down where it is made.
 */
public final class ToolManagerWiring {

    /**
     * How long any single probe invocation may take before it is called stalled.
     *
     * <p>Two minutes, which is what unit 7's end-to-end probe of the real artefacts uses: a
     * Percolator capability probe runs the binary over a 64-plus-64-row synthetic PIN and a JVM
     * launch for the Limelight converter starts a second virtual machine.
     */
    public static final Duration PROBE_TIMEOUT = Duration.ofSeconds(120);

    private ToolManagerWiring() {
        throw new AssertionError("ToolManagerWiring is a composition helper and is never built");
    }

    /**
     * The capability stage, routed to the adapter that knows how to exercise each tool.
     *
     * <p>PDV and the Limelight converter answer with an empty set, and that is the complete answer
     * rather than a missing one: <strong>no constant of {@link ToolCapability} belongs to either
     * tool</strong>, so there is nothing about them a capability probe could observe. That is not
     * the case {@code R-TOOL-08} warns about -- a probe that failed and returned an empty set,
     * which would be positive evidence of absence drawn from no evidence at all -- because nothing
     * failed and nothing was skipped. If a capability is ever declared for one of them, this arm
     * has to grow an adapter, and {@code ToolManagerWiringTest} fails until it does.
     *
     * @param runner how one probe invocation is run and collected
     * @return the prober
     * @throws NullPointerException if {@code runner} is {@code null}
     */
    public static CapabilityProber capabilityProber(ToolRunner runner) {
        Objects.requireNonNull(runner, "runner");
        CometCapabilityProbe comet =
                new CometCapabilityProbe(runner, List.of(CometCompanionGates.thermoRawWindows()));
        PercolatorCapabilityProbe percolator = new PercolatorCapabilityProbe(runner);
        return (tool, version, platform, executable) ->
                switch (tool) {
                    case COMET -> comet.probe(tool, version, platform, executable);
                    case PERCOLATOR -> percolator.probe(tool, version, platform, executable);
                    case PDV, LIMELIGHT_CONVERTER -> Set.of();
                };
    }

    /**
     * The three-stage probe, with the two seams {@code cometgui-tools} implements bound in.
     *
     * @param manifest the shipped artefact manifest, for the alternatives a refusal names
     * @param host the machine in front of the user
     * @param versions what that machine's runtimes were established to be
     * @param processes the process seam
     * @return the probe
     * @throws IOException if this runtime cannot start a second virtual machine, which is what
     *     identifying a JAR needs
     * @throws NullPointerException if any argument is {@code null}
     */
    public static StagedToolProbe probe(
            ArtefactManifest manifest,
            HostPlatform host,
            HostRuntimeVersions versions,
            ProcessRunner processes)
            throws IOException {
        Objects.requireNonNull(manifest, "manifest");
        ToolRunner runner = new ToolRunner(processes, PROBE_TIMEOUT);
        return new StagedToolProbe(
                new LoadabilityProbe(
                        processes, new LoaderOutputClassifier(host, versions), host, PROBE_TIMEOUT),
                versions,
                VersionBanner.observedOnThisProject(),
                capabilityProber(runner),
                new ManifestAlternatives(manifest, host, versions)::forArtefact,
                Map.of(),
                JavaToolIdentities.usingThisApplicationsRuntime(runner)::identify);
    }

    /**
     * The whole Tool Manager runtime, over one cache root.
     *
     * @param host the machine in front of the user
     * @param versions what that machine's runtimes were established to be
     * @param cacheRoot where installed tools live; in production the application data directory
     * @param processes the process seam
     * @param clock the clock seam, read once per install for the completion marker
     * @param installThreads where an install runs, usually {@link #installThreads()}
     * @return the manager, behind the port the user interface sees
     * @throws IOException if the manifest cannot be read or a probe seam cannot be built
     * @throws NullPointerException if any argument is {@code null}
     */
    public static ToolManager toolManager(
            HostPlatform host,
            HostRuntimeVersions versions,
            Path cacheRoot,
            ProcessRunner processes,
            Clock clock,
            Executor installThreads)
            throws IOException {
        ArtefactManifest manifest = ArtefactManifestReader.readFromClasspath();
        HashService hashes = new StreamingHashService();
        ToolCache cache = new ToolCache(cacheRoot, hashes);
        StagedToolProbe probe = probe(manifest, host, versions, processes);
        ArtefactInstaller installer =
                new ArtefactInstaller(
                        cache,
                        new VerifiedDownloader(new HttpDownloader(), new ArtefactVerifier(hashes))
                                ::fetch,
                        new ArtefactExtractor(),
                        new PlatformFixups(host.operatingSystem()),
                        probe,
                        hashes,
                        clock);
        /*
         * The offered-set gate asks "does this installed build still start here", and the binary it
         * would run is the one in the tool directory the cache names -- so the directory comes from
         * the cache and not from the caller.  ManagedToolManager asks this only about a build whose
         * entry it has just verified, which is what makes the question answerable at all.
         */
        ProbeGatedOffers.LoadabilityCheck loadability =
                record -> probe.loadabilityOf(record, cache.toolDirectory(record));
        return new ManagedToolManager(
                manifest,
                host,
                versions,
                installer,
                loadability,
                localRegistrars(processes, hashes, host),
                installThreads);
    }

    /**
     * The tools a binary already on the machine can be registered for.
     *
     * <p>Percolator alone, because the specification's <em>Percolator installation modes</em> is
     * the only place the product offers it -- it is the documented remedy wherever no managed
     * XML-capable build exists for a platform. Comet, PDV and the converter are installed from the
     * manifest, where their checksums are pinned, and {@link ManagedToolManager} refuses a tool it
     * has no registrar for rather than pretending to probe one.
     *
     * @param processes the process seam
     * @param hashes the one hashing service
     * @param host the machine the binary would run on
     * @return the registrars, by tool
     */
    public static Map<ToolName, LocalBinaryRegistrar> localRegistrars(
            ProcessRunner processes, HashService hashes, HostPlatform host) {
        ToolRunner runner = new ToolRunner(processes, PROBE_TIMEOUT);
        LocalPercolatorRegistration percolator =
                new LocalPercolatorRegistration(
                        runner, new PercolatorCapabilityProbe(runner), hashes, host);
        return Map.of(ToolName.PERCOLATOR, executable -> percolator.register(executable).offer());
    }

    /**
     * Daemon threads for installs, one per install, named so that a thread dump says what they are.
     *
     * <p>Daemon deliberately: a download the user has walked away from must not keep the
     * application alive, and {@code R-TOOL-04} makes a half-finished install safe to abandon --
     * nothing in the cache reports itself installed until the completion marker is written last.
     *
     * @return the executor
     */
    public static Executor installThreads() {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory factory =
                runnable -> {
                    Thread thread =
                            new Thread(runnable, "cometgui-install-" + sequence.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                };
        return Executors.newCachedThreadPool(factory);
    }
}
