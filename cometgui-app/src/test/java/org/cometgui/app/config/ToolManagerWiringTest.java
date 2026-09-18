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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolRegistrationException;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.probe.CapabilityProber;
import org.cometgui.install.probe.HostRuntimeVersions;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.tools.api.ToolRunner;
import org.cometgui.tools.process.ProcessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The join between the installer's seams and the tool adapters, exercised in the one module that
 * sees both.
 *
 * <p>{@code cometgui-install} names {@code CapabilityProber} and {@code JavaArtefactIdentity} and
 * cannot see an implementation of either; {@code cometgui-tools} implements their shape and cannot
 * name them. Unit 7 proved that route for the probe in {@code StagedJavaToolProbeTest}; this proves
 * it for the whole Tool Manager, including the local-binary registrar the port's third method
 * needs.
 */
class ToolManagerWiringTest {

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    private static final HostRuntimeVersions DEBIAN_12 =
            new HostRuntimeVersions(
                    Optional.of(GlibcVersion.parse("2.36")),
                    Optional.of(GlibcVersion.parse("3.4.30")));

    @TempDir private Path temporary;

    /** Records what a probe tried to run and refuses to run it, which is all these tests need. */
    private static final class RecordingRunner implements ProcessRunner {

        private final List<List<String>> commands = new ArrayList<>();

        @Override
        public RunningProcess start(ToolCommand command, ProcessListener listener)
                throws IOException {
            commands.add(command.argv());
            throw new IOException("this test does not run tools; it records what was asked for");
        }

        synchronized List<List<String>> commands() {
            return List.copyOf(commands);
        }
    }

    @Test
    @DisplayName(
            "no capability belongs to PDV or the converter, so an empty set is the whole answer")
    void noCapabilityBelongsToTheJarTools() throws IOException {
        CapabilityProber prober =
                ToolManagerWiring.capabilityProber(
                        new ToolRunner(new RecordingRunner(), ToolManagerWiring.PROBE_TIMEOUT));
        Path jar = temporary.resolve("anything.jar");

        assertAll(
                () ->
                        assertEquals(
                                List.of(),
                                List.of(ToolCapability.values()).stream()
                                        .filter(ToolManagerWiringTest::belongsToAJarTool)
                                        .toList(),
                                "if a capability is ever declared for one of these, the wiring"
                                        + " needs an adapter for it and this assertion says so"),
                () ->
                        assertEquals(
                                Set.of(),
                                prober.probe(ToolName.PDV, ToolVersion.parse("2.7.0"), LINUX, jar)),
                () ->
                        assertEquals(
                                Set.of(),
                                prober.probe(
                                        ToolName.LIMELIGHT_CONVERTER,
                                        ToolVersion.parse("2.8.1"),
                                        LINUX,
                                        jar)));
    }

    @Test
    @DisplayName("Comet and Percolator each reach their own adapter, and neither refuses the tool")
    void eachNativeToolReachesItsOwnAdapter() {
        RecordingRunner runner = new RecordingRunner();
        CapabilityProber prober =
                ToolManagerWiring.capabilityProber(
                        new ToolRunner(runner, ToolManagerWiring.PROBE_TIMEOUT));
        Path comet = temporary.resolve("comet");
        Path percolator = temporary.resolve("percolator");

        assertThrows(
                IOException.class,
                () -> prober.probe(ToolName.COMET, ToolVersion.parse("2026.02.2"), LINUX, comet),
                "the Comet adapter tried to run the binary and this test's runner refuses to");
        List<List<String>> afterComet = runner.commands();
        assertThrows(
                IOException.class,
                () ->
                        prober.probe(
                                ToolName.PERCOLATOR,
                                ToolVersion.parse("3.07.1"),
                                LINUX,
                                percolator));
        List<List<String>> afterBoth = runner.commands();

        assertAll(
                () ->
                        assertEquals(
                                List.of(comet.toString(), "-p"),
                                afterComet.get(0),
                                "the Comet adapter asks for the default parameter file, which the"
                                        + " Percolator adapter never would"),
                () ->
                        assertTrue(
                                afterBoth.get(afterComet.size()).contains("-X"),
                                () ->
                                        "the Percolator adapter asks for XML output over a"
                                                + " synthetic PIN: "
                                                + afterBoth.get(afterComet.size())),
                () ->
                        assertEquals(
                                percolator.toString(),
                                afterBoth.get(afterComet.size()).get(0),
                                "and it runs the binary it was given"));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    @DisplayName("registerLocalBinary reaches unit 7's registrar through the port")
    void registerLocalBinaryReachesThePercolatorAdapter() throws IOException {
        Path notPercolator = temporary.resolve("not-percolator");
        Files.writeString(notPercolator, "#!/bin/sh\nexit 0\n");
        Files.setPosixFilePermissions(notPercolator, PosixFilePermissions.fromString("rwxr-xr-x"));
        ToolManager manager = managerOverRealProcesses();

        ToolRegistrationException refused =
                assertThrows(
                        ToolRegistrationException.class,
                        () -> manager.registerLocalBinary(ToolName.PERCOLATOR, notPercolator));

        assertTrue(
                refused.getMessage()
                        .startsWith("The file at " + notPercolator + " is not Percolator:"),
                () ->
                        "the message is unit 7's own, which is the evidence that the port reached"
                                + " the adapter in the other module: "
                                + refused.getMessage());
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    @DisplayName("the composed manager offers this host's rows from the shipped manifest")
    void theComposedManagerOffersThisHostsRows() throws IOException {
        ToolManager manager = managerOverRealProcesses();

        List<ToolOffer> offers = manager.offers();

        assertEquals(
                List.of(
                        "comet 2026.02.2 NOT_INSTALLED",
                        "percolator 3.09 UNAVAILABLE_ON_THIS_PLATFORM",
                        "percolator 3.07.1 NOT_INSTALLED",
                        "percolator 3.06.5 NOT_INSTALLED",
                        "pdv 2.7.0 NOT_INSTALLED",
                        "limelight-converter 2.8.1 NOT_INSTALLED"),
                offers.stream()
                        .map(
                                offer ->
                                        offer.tool().id()
                                                + " "
                                                + offer.version().text()
                                                + " "
                                                + offer.state())
                        .toList(),
                "the whole runtime, composed as the application composes it, over an empty cache");
    }

    private static boolean belongsToAJarTool(ToolCapability capability) {
        return capability.tool() == ToolName.PDV
                || capability.tool() == ToolName.LIMELIGHT_CONVERTER;
    }

    private ToolManager managerOverRealProcesses() throws IOException {
        return ToolManagerWiring.toolManager(
                LINUX,
                DEBIAN_12,
                temporary.resolve("cache"),
                new ProcessService(Clock.systemUTC()),
                Clock.systemUTC(),
                Runnable::run);
    }

    @Test
    @DisplayName("the local registrars are Percolator's alone, and nothing else is offered one")
    void onlyPercolatorHasALocalRegistrar() {
        assertEquals(
                Set.of(ToolName.PERCOLATOR),
                ToolManagerWiring.localRegistrars(
                                new RecordingRunner(), new StreamingHashService(), LINUX)
                        .keySet(),
                "the specification offers a registered local binary for Percolator and for no"
                        + " other tool");
    }
}
