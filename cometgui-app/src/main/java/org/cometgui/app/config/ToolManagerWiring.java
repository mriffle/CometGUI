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
import org.cometgui.domain.ports.HashService;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolRegistrationException;
import org.cometgui.install.archive.ArtefactExtractor;
import org.cometgui.install.cache.ArtefactInstaller;
import org.cometgui.install.cache.PlatformFixups;
import org.cometgui.install.cache.ToolCache;
import org.cometgui.install.cache.VerifiedArtefactSource;
import org.cometgui.install.manager.LocalBinaryRegistrar;
import org.cometgui.install.manager.ManagedToolManager;
import org.cometgui.install.manager.ProbeRefusalLog;
import org.cometgui.install.probe.CapabilityProber;
import org.cometgui.install.probe.HostRuntimeVersions;
import org.cometgui.install.probe.LoadabilityProbe;
import org.cometgui.install.probe.LoaderOutputClassifier;
import org.cometgui.install.probe.ManifestAlternatives;
import org.cometgui.install.probe.ProbeGatedOffers;
import org.cometgui.install.probe.StagedToolProbe;
import org.cometgui.install.probe.VersionBanner;
import org.cometgui.install.registry.ArtefactManifest;
import org.cometgui.tools.api.JavaToolIdentities;
import org.cometgui.tools.api.ToolRunner;
import org.cometgui.tools.comet.CometCapabilityProbe;
import org.cometgui.tools.comet.CometCompanionGates;
import org.cometgui.tools.percolator.LocalPercolatorRegistration;
import org.cometgui.tools.percolator.PercolatorCapabilityProbe;

/**
 * Where the installer and the tool adapters are introduced to each other.
 *
 * <p><strong>This module is the only place both are visible.</strong> {@code cometgui-install}
 * declares three ports in domain vocabulary -- {@link CapabilityProber}, {@code
 * org.cometgui.install.probe.JavaArtefactIdentity} and {@link LocalBinaryRegistrar} -- and {@code
 * cometgui-tools} supplies methods with exactly those shapes. It cannot <em>implement</em> them:
 * {@code cometgui-tools} depends on {@code cometgui-domain} and {@code cometgui-process} and not on
 * the installer, deliberately, because a dependency the other way would put {@code
 * org.cometgui.install} and {@code org.cometgui.tools} in a cycle that {@code
 * LayeringRulesTest.majorLayersAreFreeOfCycles} rejects. So the two halves are joined by method
 * references, here, and no module's dependency list changes. Phase 05 unit 7 proved that route for
 * the JAR identity seam before this class existed.
 *
 * <h2>Why this is not a method on {@link ApplicationServices}</h2>
 *
 * <p>{@link ApplicationServices} is documented as immutable, as holding no I/O resources, and as
 * doing no I/O when it is built -- and a Tool Manager is none of those things. It needs a cache
 * directory to write into, an {@link Executor} to run installs on, and a downloader that owns an
 * HTTP client with threads that have to be closed. Building one inside {@code
 * ApplicationServices.forThisHost()} would quietly turn the composition root into a lifecycle
 * object; the seams it hands out are exactly what this class asks for instead.
 *
 * <p><strong>What is deliberately still absent.</strong> {@code ApplicationServices.forThisHost()}
 * continues to answer {@code Optional.empty()} for the process runner, the hash service and the
 * downloader. Wiring the first two is phase 03's and phase 04's claim to make, not this unit's, and
 * wiring the third means owning something closeable. The Tool Manager section of the user interface
 * is where that decision belongs, and this class is written so that it is a matter of passing three
 * arguments.
 *
 * <h2>What the adapters lose, said plainly</h2>
 *
 * <p>{@link LocalPercolatorRegistration} computes the MD5 and SHA-256 of the binary it registers
 * and returns them beside the offer. {@link LocalBinaryRegistrar} carries only the offer, because
 * {@code org.cometgui.domain.tools.ToolOffer} deliberately has no checksum field -- a scientist is
 * not shown a SHA-256 in a list of tools. <strong>A provenance record of a locally registered
 * binary therefore cannot be built through {@code ToolManager}</strong> and must go to {@code
 * LocalPercolatorRegistration} directly. That is a real limit of this seam and it is written here
 * rather than discovered by whoever writes the provenance record.
 */
public final class ToolManagerWiring {

    private ToolManagerWiring() {}

    /**
     * Composes a Tool Manager over one manifest, one host and one cache directory.
     *
     * @param manifest the shipped artefact manifest
     * @param host the machine in front of the user
     * @param runtimes what that machine's C and C++ runtimes were established to be
     * @param processes the process seam ({@code R-PROC-01}), the only thing that starts a process
     * @param hashes the one hashing service in this product
     * @param artefacts steps 1 and 2 of the install, in production {@code
     *     org.cometgui.install.verify.VerifiedDownloader::fetch}
     * @param cacheRoot where installed tools and everything an install can throw away live
     * @param clock read once per install for the completion marker's timestamp
     * @param toolTimeout how long any one probe invocation gets before it is cancelled
     * @param installs where an install runs; {@code ToolManager.install} hands it the work and
     *     returns
     * @return the Tool Manager
     * @throws IOException if this runtime cannot start a second JVM, which is what identifying a
     *     JAR needs
     * @throws NullPointerException if any argument is {@code null}
     */
    public static ToolManager toolManager(
            ArtefactManifest manifest,
            HostPlatform host,
            HostRuntimeVersions runtimes,
            ProcessRunner processes,
            HashService hashes,
            VerifiedArtefactSource artefacts,
            Path cacheRoot,
            Clock clock,
            Duration toolTimeout,
            Executor installs)
            throws IOException {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(runtimes, "runtimes");
        Objects.requireNonNull(processes, "processes");
        Objects.requireNonNull(hashes, "hashes");
        Objects.requireNonNull(artefacts, "artefacts");
        Objects.requireNonNull(cacheRoot, "cacheRoot");
        Objects.requireNonNull(clock, "clock");
        ToolRunner runner =
                new ToolRunner(processes, Objects.requireNonNull(toolTimeout, "toolTimeout"));
        /*
         * ONE CLASSIFIER, ONE SOURCE OF ALTERNATIVES, shared by the probe that produces a loader
         * diagnostic during an install and by the gate that renders one afterwards.  Two instances
         * would be two chances for the same failure to be worded two ways, and R-PLAT-03's whole
         * subject is what the sentence says.
         */
        LoaderOutputClassifier classifier = new LoaderOutputClassifier(host, runtimes);
        ManifestAlternatives alternatives = new ManifestAlternatives(manifest, host, runtimes);
        StagedToolProbe probe =
                new StagedToolProbe(
                        new LoadabilityProbe(processes, classifier, host, toolTimeout),
                        runtimes,
                        VersionBanner.observedOnThisProject(),
                        capabilityProber(runner),
                        alternatives::forArtefact,
                        Map.of(),
                        JavaToolIdentities.usingThisApplicationsRuntime(runner)::identify);
        ProbeRefusalLog refusals = new ProbeRefusalLog();
        ArtefactInstaller installer =
                new ArtefactInstaller(
                        new ToolCache(cacheRoot, hashes),
                        artefacts,
                        new ArtefactExtractor(),
                        PlatformFixups.forHost(host),
                        refusals.recording(probe),
                        hashes,
                        clock);
        return new ManagedToolManager(
                manifest,
                host,
                new ProbeGatedOffers(runtimes, classifier, alternatives::forArtefact),
                classifier,
                alternatives::forArtefact,
                refusals,
                installer,
                localBinaries(runner, hashes, host),
                Objects.requireNonNull(installs, "installs"));
    }

    /**
     * {@code R-TOOL-06}'s capability stage, routed to the adapter that knows how to exercise each
     * tool.
     *
     * <p><strong>PDV and the Limelight converter answer with an empty set, and that is the honest
     * answer rather than a gap.</strong> {@code org.cometgui.domain.tools.ToolCapability} declares
     * sixteen capabilities and every one of them belongs to Comet or to Percolator: there is no
     * capability of PDV or of the converter for a probe to establish, so {@code R-TOOL-08}'s
     * "absent positive evidence, the capability is absent" has nothing to be absent about. Their
     * identity stage is where the work happens, and it runs before this one.
     *
     * @param runner how one invocation is run and collected
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
                    case PDV, LIMELIGHT_CONVERTER -> Set.<ToolCapability>of();
                };
    }

    /**
     * {@code R-TOOL-08}'s local registration, for the one tool this product knows how to register
     * by hand.
     *
     * <p>Percolator is that tool because {@code R-PERC-03} makes registering a local binary the
     * documented remedy wherever no managed XML-capable build exists for a platform. The other
     * three have no such remedy and no registrar, and being told so is better than a file chooser
     * that accepts a file and then cannot say what happened to it.
     *
     * @param runner how the identifying invocation is run
     * @param hashes the one hashing service in this product
     * @param host the machine the binary would run on
     * @return the registrar
     * @throws NullPointerException if any argument is {@code null}
     */
    public static LocalBinaryRegistrar localBinaries(
            ToolRunner runner, HashService hashes, HostPlatform host) {
        Objects.requireNonNull(runner, "runner");
        LocalPercolatorRegistration percolator =
                new LocalPercolatorRegistration(
                        runner, new PercolatorCapabilityProbe(runner), hashes, host);
        return (tool, executable) -> {
            if (tool != ToolName.PERCOLATOR) {
                throw new ToolRegistrationException(
                        "CometGUI can register a local binary for "
                                + ToolName.PERCOLATOR.id()
                                + " only, and the file at "
                                + executable
                                + " was offered as "
                                + tool.id()
                                + ". Percolator is the one tool with a documented local-binary"
                                + " remedy, because a managed XML-capable build does not exist for"
                                + " every platform.");
            }
            return percolator.register(executable).offer();
        };
    }
}
