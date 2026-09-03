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

package org.cometgui.tools.comet;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.tools.api.CompanionGate;
import org.cometgui.tools.api.ToolRunner;
import org.cometgui.tools.testing.ScriptedRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Comet's capability rules, including both halves of {@code R-TOOL-02}'s companion rule.
 *
 * <p>The Thermo half cannot be graded against a real run: {@code comet.win64.exe} is a Windows
 * executable and no Windows binary has ever been executed anywhere in this project. What is graded
 * here is the rule -- a Windows install with the three libraries beside it advertises the
 * capability, one missing any of them does not, and a Linux install never does -- with the run
 * itself scripted, and the run graded for real in {@code CometRealBinaryTest}.
 */
class CometCapabilityProbeTest {

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);
    private static final HostPlatform WINDOWS =
            new HostPlatform(HostOperatingSystem.WINDOWS, HostArchitecture.X86_64);
    private static final ToolVersion VERSION = ToolVersion.parse("2026.02.2");
    private static final String BANNER = " Comet version \"2026.02 rev. 2 (6edec91)\"";

    private static final List<String> THERMO_DLLS =
            List.of(
                    "CometWrapper.dll",
                    "ThermoFisher.CommonCore.Data.dll",
                    "ThermoFisher.CommonCore.RawFileReader.dll");

    /** The parameter names the real {@code comet -p} declares, shortened but real. */
    private static final List<String> DEFAULT_PARAMETERS =
            List.of(
                    "database_name = x",
                    "output_pepxmlfile = 1",
                    "output_percolatorfile = 0",
                    "output_txtfile = 0");

    /** The real {@code comet -q} declares everything {@code -p} does and 22 more. */
    private static final List<String> COMPLETE_PARAMETERS = withExtra(DEFAULT_PARAMETERS, 22);

    private static List<String> withExtra(List<String> base, int extra) {
        List<String> lines = new ArrayList<>(base);
        for (int index = 0; index < extra; index++) {
            lines.add("extra_parameter_" + index + " = 0");
        }
        return List.copyOf(lines);
    }

    private static Consumer<ToolCommand> writesParameters(List<String> lines) {
        return command -> {
            try {
                Files.write(
                        command.workingDirectory()
                                .resolve(CometCapabilityProbe.WRITTEN_PARAMETERS_FILE),
                        lines,
                        StandardCharsets.UTF_8);
            } catch (IOException notWritten) {
                throw new UncheckedIOException(notWritten);
            }
        };
    }

    private static ScriptedRunner cometThatAnswersBoth() {
        return new ScriptedRunner()
                .thenWrites(writesParameters(DEFAULT_PARAMETERS), 0, List.of(BANNER))
                .thenWrites(writesParameters(COMPLETE_PARAMETERS), 0, List.of(BANNER));
    }

    private static CometCapabilityProbe probe(ScriptedRunner runner, List<CompanionGate> gates) {
        return new CometCapabilityProbe(new ToolRunner(runner, Duration.ofSeconds(5)), gates);
    }

    private static Path executable(Path directory, List<String> companions) throws IOException {
        Path bin = Files.createDirectories(directory.resolve("bin"));
        for (String companion : companions) {
            Files.writeString(bin.resolve(companion), "MZ");
        }
        return Files.writeString(bin.resolve("comet.exe"), "MZ");
    }

    @Test
    @DisplayName("the complete query declaring more than the default is COMPLETE_PARAMS_QUERY")
    void theCompleteQuery(@TempDir Path directory) throws IOException {
        Set<ToolCapability> observed =
                probe(cometThatAnswersBoth(), List.of())
                        .probe(ToolName.COMET, VERSION, LINUX, executable(directory, List.of()));

        assertEquals(
                Set.of(
                        ToolCapability.PEPXML_OUTPUT,
                        ToolCapability.PIN_OUTPUT,
                        ToolCapability.COMPLETE_PARAMS_QUERY),
                observed);
    }

    @Test
    @DisplayName("a -q that declares no more than -p is not a complete parameter query")
    void aCompleteQueryThatIsNotComplete(@TempDir Path directory) throws IOException {
        ScriptedRunner runner =
                new ScriptedRunner()
                        .thenWrites(writesParameters(DEFAULT_PARAMETERS), 0, List.of(BANNER))
                        .thenWrites(writesParameters(DEFAULT_PARAMETERS), 0, List.of(BANNER));

        Set<ToolCapability> observed =
                probe(runner, List.of())
                        .probe(ToolName.COMET, VERSION, LINUX, executable(directory, List.of()));

        assertEquals(
                Set.of(ToolCapability.PEPXML_OUTPUT, ToolCapability.PIN_OUTPUT),
                observed,
                "the capability is the DIFFERENCE between the two files -- 96 against 118 on the"
                        + " real binary -- so a build whose -q behaved like -p must not claim it");
    }

    @Test
    @DisplayName(
            "the two runs are -p then -q, in separate directories, or they read one file twice")
    void theTwoRuns(@TempDir Path directory) throws IOException {
        ScriptedRunner runner = cometThatAnswersBoth();
        Path binary = executable(directory, List.of());

        probe(runner, List.of()).probe(ToolName.COMET, VERSION, LINUX, binary);

        ToolCommand first = runner.commands().get(0);
        ToolCommand second = runner.commands().get(1);
        assertAll(
                () -> assertEquals(List.of(binary.toString(), "-p"), first.argv()),
                () -> assertEquals(List.of(binary.toString(), "-q"), second.argv()),
                () ->
                        assertTrue(
                                !first.workingDirectory().equals(second.workingDirectory()),
                                "Comet writes comet.params.new into the working directory under"
                                        + " both options, so a shared directory would compare the"
                                        + " second run against the first run's file -- a"
                                        + " comparison that can only ever say \"the same\""));
    }

    @ParameterizedTest(name = "[{index}] without {0}")
    @ValueSource(
            strings = {
                "output_pepxmlfile",
                "output_percolatorfile",
            })
    @DisplayName("a parameter the binary does not declare is a capability it does not have")
    void anUndeclaredParameter(String missing, @TempDir Path directory) throws IOException {
        List<String> complete =
                COMPLETE_PARAMETERS.stream()
                        .filter(line -> !line.startsWith(missing + " "))
                        .toList();
        ScriptedRunner runner =
                new ScriptedRunner()
                        .thenWrites(writesParameters(DEFAULT_PARAMETERS), 0, List.of(BANNER))
                        .thenWrites(writesParameters(complete), 0, List.of(BANNER));

        Set<ToolCapability> observed =
                probe(runner, List.of())
                        .probe(ToolName.COMET, VERSION, LINUX, executable(directory, List.of()));

        assertAll(
                () -> assertTrue(observed.contains(ToolCapability.COMPLETE_PARAMS_QUERY)),
                () ->
                        assertEquals(
                                "output_pepxmlfile".equals(missing),
                                !observed.contains(ToolCapability.PEPXML_OUTPUT)),
                () ->
                        assertEquals(
                                "output_percolatorfile".equals(missing),
                                !observed.contains(ToolCapability.PIN_OUTPUT)));
    }

    @Test
    @DisplayName("a Windows install WITH the three Thermo libraries advertises THERMO_RAW_WINDOWS")
    void withTheThermoLibraries(@TempDir Path directory) throws IOException {
        Set<ToolCapability> observed =
                probe(cometThatAnswersBoth(), List.of(CometCompanionGates.thermoRawWindows()))
                        .probe(
                                ToolName.COMET,
                                VERSION,
                                WINDOWS,
                                executable(directory, THERMO_DLLS));

        assertTrue(observed.contains(ToolCapability.THERMO_RAW_WINDOWS), observed.toString());
    }

    @ParameterizedTest(name = "[{index}] missing {0}")
    @ValueSource(
            strings = {
                "CometWrapper.dll",
                "ThermoFisher.CommonCore.Data.dll",
                "ThermoFisher.CommonCore.RawFileReader.dll"
            })
    @DisplayName("a Windows install missing ANY ONE of them does not advertise it")
    void withoutOneThermoLibrary(String missing, @TempDir Path directory) throws IOException {
        List<String> present = THERMO_DLLS.stream().filter(name -> !name.equals(missing)).toList();

        Set<ToolCapability> observed =
                probe(cometThatAnswersBoth(), List.of(CometCompanionGates.thermoRawWindows()))
                        .probe(ToolName.COMET, VERSION, WINDOWS, executable(directory, present));

        assertAll(
                () ->
                        assertTrue(
                                !observed.contains(ToolCapability.THERMO_RAW_WINDOWS),
                                "R-TOOL-02: an install missing them shall not advertise it"),
                () ->
                        assertTrue(
                                observed.contains(ToolCapability.PEPXML_OUTPUT),
                                "the rest of the capability set is unaffected: " + observed));
    }

    @Test
    @DisplayName("a Windows install with NONE of them does not advertise it")
    void withNoThermoLibraries(@TempDir Path directory) throws IOException {
        Set<ToolCapability> observed =
                probe(cometThatAnswersBoth(), List.of(CometCompanionGates.thermoRawWindows()))
                        .probe(ToolName.COMET, VERSION, WINDOWS, executable(directory, List.of()));

        assertTrue(!observed.contains(ToolCapability.THERMO_RAW_WINDOWS), observed.toString());
    }

    @Test
    @DisplayName(
            "a Linux install never advertises it, even with three files of those names beside it")
    void notOnLinux(@TempDir Path directory) throws IOException {
        Set<ToolCapability> observed =
                probe(cometThatAnswersBoth(), List.of(CometCompanionGates.thermoRawWindows()))
                        .probe(ToolName.COMET, VERSION, LINUX, executable(directory, THERMO_DLLS));

        assertTrue(
                !observed.contains(ToolCapability.THERMO_RAW_WINDOWS),
                "THERMO_RAW_WINDOWS is a fact about a Windows install; the gate carries the"
                        + " operating system of the manifest row its companions hang off");
    }

    @Test
    @DisplayName("with no gate configured at all the capability is simply absent")
    void withNoGateConfigured(@TempDir Path directory) throws IOException {
        Set<ToolCapability> observed =
                probe(cometThatAnswersBoth(), List.of())
                        .probe(
                                ToolName.COMET,
                                VERSION,
                                WINDOWS,
                                executable(directory, THERMO_DLLS));

        assertTrue(
                !observed.contains(ToolCapability.THERMO_RAW_WINDOWS),
                "the manifest's Linux and macOS rows declare no companions, so a probe built from"
                        + " one has no gate and grants nothing: "
                        + observed);
    }

    @Test
    @DisplayName("the companion rule is answerable on its own, without running anything")
    void theCompanionRuleAlone(@TempDir Path directory) throws IOException {
        CometCapabilityProbe probe =
                probe(new ScriptedRunner(), List.of(CometCompanionGates.thermoRawWindows()));

        assertAll(
                () ->
                        assertEquals(
                                Set.of(ToolCapability.THERMO_RAW_WINDOWS),
                                probe.gatedByCompanions(
                                        WINDOWS, executable(directory.resolve("a"), THERMO_DLLS))),
                () ->
                        assertEquals(
                                Set.of(),
                                probe.gatedByCompanions(
                                        WINDOWS, executable(directory.resolve("b"), List.of()))),
                () ->
                        assertEquals(
                                "platform",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> probe.gatedByCompanions(null, directory))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "executable",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> probe.gatedByCompanions(WINDOWS, null))
                                        .getMessage()));
    }

    @Test
    @DisplayName("a binary that prints no banner is a refusal, never an empty capability set")
    void noBannerIsARefusal(@TempDir Path directory) throws IOException {
        Path binary = executable(directory, List.of());
        ScriptedRunner runner =
                new ScriptedRunner()
                        .thenPrints(
                                126,
                                List.of(
                                        "bash: comet: cannot execute binary file: Exec format"
                                                + " error"),
                                List.of());

        assertEquals(
                "Comet 2026.02.2 at "
                        + binary
                        + " never printed its version banner in answer to -p, so it did not run far"
                        + " enough to be asked what it can do; this is a loadability failure and"
                        + " must not be reported as a missing capability. It exited 126 saying:"
                        + " bash: comet: cannot execute binary file: Exec format error",
                assertThrows(
                                IOException.class,
                                () ->
                                        probe(runner, List.of())
                                                .probe(ToolName.COMET, VERSION, LINUX, binary))
                        .getMessage());
    }

    @Test
    @DisplayName("a run that never answers is a refusal, naming the option it was asked")
    void aRunThatNeverAnswers(@TempDir Path directory) throws IOException {
        Path binary = executable(directory, List.of());
        ScriptedRunner runner = new ScriptedRunner().thenNeverFinishes();
        CometCapabilityProbe probe =
                new CometCapabilityProbe(new ToolRunner(runner, Duration.ofMillis(50)), List.of());

        assertEquals(
                "Comet 2026.02.2 at "
                        + binary
                        + " did not answer -p within PT0.05S, so this probe established nothing"
                        + " about it",
                assertThrows(
                                IOException.class,
                                () -> probe.probe(ToolName.COMET, VERSION, LINUX, binary))
                        .getMessage());
    }

    @Test
    @DisplayName("a binary that ran but wrote no parameter file claims nothing from it")
    void aRunThatWroteNoFile(@TempDir Path directory) throws IOException {
        ScriptedRunner runner =
                new ScriptedRunner()
                        .thenPrints(0, List.of(BANNER), List.of())
                        .thenPrints(0, List.of(BANNER), List.of());

        assertEquals(
                Set.of(),
                probe(runner, List.of())
                        .probe(ToolName.COMET, VERSION, LINUX, executable(directory, List.of())),
                "it started and said its name, so the absence was observed rather than assumed");
    }

    @Test
    @DisplayName("no temporary workspace is left behind, whether the probe answers or refuses")
    void theWorkspaceIsCleanedUp(@TempDir Path directory) throws IOException {
        Path temporary = Path.of(System.getProperty("java.io.tmpdir"));
        long before = countProbeWorkspaces(temporary);
        probe(cometThatAnswersBoth(), List.of())
                .probe(
                        ToolName.COMET,
                        VERSION,
                        LINUX,
                        executable(directory.resolve("good"), List.of()));
        ScriptedRunner bad = new ScriptedRunner().thenPrints(126, List.of("no banner"), List.of());
        assertThrows(
                IOException.class,
                () ->
                        probe(bad, List.of())
                                .probe(
                                        ToolName.COMET,
                                        VERSION,
                                        LINUX,
                                        executable(directory.resolve("bad"), List.of())));

        assertEquals(before, countProbeWorkspaces(temporary));
    }

    private static long countProbeWorkspaces(Path temporary) throws IOException {
        try (Stream<Path> entries = Files.list(temporary)) {
            return entries.filter(
                            entry ->
                                    entry.getFileName()
                                            .toString()
                                            .startsWith("cometgui-comet-probe-"))
                    .count();
        }
    }

    @Test
    @DisplayName("another tool's binary is refused, and every argument is required")
    void refusalsAndNulls(@TempDir Path directory) throws IOException {
        Path binary = executable(directory, List.of());
        CometCapabilityProbe probe = probe(new ScriptedRunner(), List.of());

        assertAll(
                () ->
                        assertEquals(
                                "this probe runs Comet and was asked to probe percolator; a"
                                        + " capability of one tool means nothing said of another",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        probe.probe(
                                                                ToolName.PERCOLATOR,
                                                                VERSION,
                                                                LINUX,
                                                                binary))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "tool",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> probe.probe(null, VERSION, LINUX, binary))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "version",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        probe.probe(
                                                                ToolName.COMET,
                                                                null,
                                                                LINUX,
                                                                binary))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "platform",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        probe.probe(
                                                                ToolName.COMET,
                                                                VERSION,
                                                                null,
                                                                binary))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "executable",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        probe.probe(
                                                                ToolName.COMET,
                                                                VERSION,
                                                                LINUX,
                                                                null))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "runner",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> new CometCapabilityProbe(null, List.of()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "gates",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new CometCapabilityProbe(
                                                                new ToolRunner(
                                                                        new ScriptedRunner(),
                                                                        Duration.ofSeconds(1)),
                                                                null))
                                        .getMessage()));
    }
}
