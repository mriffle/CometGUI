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

package org.cometgui.tools.percolator;

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
import org.cometgui.tools.api.ToolRunner;
import org.cometgui.tools.testing.ScriptedRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The probe's rules, graded over shapes of answer no real binary here produces.
 *
 * <p>The real binaries are run by {@code PercolatorRealBinaryTest}; this is what makes it
 * affordable to vary the answer -- a valid document, a short one, a zero-byte one, one in the wrong
 * namespace, no banner, no answer at all -- and it is those variations, rather than the happy path,
 * that decide whether the rule can go red.
 */
class PercolatorCapabilityProbeTest {

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);
    private static final ToolVersion V3071 = ToolVersion.parse("3.07.1");
    private static final String BANNER =
            "Percolator version 3.07.1, Build Date Jun 20 2024 13:20:18";

    private static String document(int psms, boolean decoys) {
        StringBuilder written =
                new StringBuilder(
                        "<percolator_output xmlns=\"http://per-colator.com/percolator_out/15\""
                                + " xmlns:p=\"http://per-colator.com/percolator_out/15\"><psms>");
        for (int index = 0; index < psms; index++) {
            written.append("<psm");
            if (decoys) {
                written.append(" p:decoy=\"").append(index % 2 == 0 ? "false" : "true").append('"');
            }
            written.append("/>");
        }
        return written.append("</psms></percolator_output>").toString();
    }

    /** Writes what a run of the real binary would have written, at the path it was told to. */
    private static Consumer<ToolCommand> writes(String content) {
        return command -> {
            try {
                Files.writeString(Path.of(command.argv().get(2)), content, StandardCharsets.UTF_8);
            } catch (IOException notWritten) {
                throw new UncheckedIOException(notWritten);
            }
        };
    }

    private static PercolatorCapabilityProbe probe(ScriptedRunner runner) {
        return new PercolatorCapabilityProbe(new ToolRunner(runner, Duration.ofSeconds(5)), 64);
    }

    private static Path binary(Path directory) throws IOException {
        return Files.writeString(directory.resolve("percolator"), "ELF");
    }

    @Test
    @DisplayName("a run that writes both documents is observed to have both XML capabilities")
    void bothCapabilities(@TempDir Path directory) throws IOException {
        ScriptedRunner runner =
                new ScriptedRunner()
                        .thenWrites(writes(document(64, false)), 0, List.of(BANNER))
                        .thenWrites(writes(document(128, true)), 0, List.of(BANNER));

        Set<ToolCapability> observed =
                probe(runner).probe(ToolName.PERCOLATOR, V3071, LINUX, binary(directory));

        assertAll(
                () ->
                        assertEquals(
                                Set.of(ToolCapability.XML_OUTPUT, ToolCapability.XML_DECOY_OUTPUT),
                                observed),
                () -> assertEquals(2, runner.played(), "each capability gets its own run"));
    }

    @Test
    @DisplayName("the two runs are -X and -X -Z, in that order, over one fixture")
    void theArgumentArrays(@TempDir Path directory) throws IOException {
        ScriptedRunner runner =
                new ScriptedRunner()
                        .thenWrites(writes(document(64, false)), 0, List.of(BANNER))
                        .thenWrites(writes(document(128, true)), 0, List.of(BANNER));
        Path executable = binary(directory);

        probe(runner).probe(ToolName.PERCOLATOR, V3071, LINUX, executable);

        List<String> targets = runner.commands().get(0).argv();
        List<String> decoys = runner.commands().get(1).argv();
        assertAll(
                () -> assertEquals(executable.toString(), targets.get(0)),
                () -> assertEquals("-X", targets.get(1)),
                () -> assertTrue(targets.get(2).endsWith("targets.pout.xml"), targets.toString()),
                () -> assertTrue(targets.get(3).endsWith("probe.pin"), targets.toString()),
                () -> assertEquals(4, targets.size()),
                () -> assertEquals("-X", decoys.get(1)),
                () -> assertTrue(decoys.get(2).endsWith("decoys.pout.xml"), decoys.toString()),
                () -> assertEquals("-Z", decoys.get(3)),
                () -> assertEquals(targets.get(3), decoys.get(4), "one fixture, two runs"),
                () -> assertEquals(5, decoys.size()));
    }

    @Test
    @DisplayName("a ZERO-BYTE output file is not success, even when the run exits 0")
    void aZeroByteFileIsNotSuccess(@TempDir Path directory) throws IOException {
        ScriptedRunner runner =
                new ScriptedRunner()
                        .thenWrites(writes(""), 0, List.of(BANNER))
                        .thenWrites(writes(""), 0, List.of(BANNER));

        Set<ToolCapability> observed =
                probe(runner).probe(ToolName.PERCOLATOR, V3071, LINUX, binary(directory));

        assertEquals(Set.of(), observed);
    }

    @Test
    @DisplayName("a run that wrote nothing at all claims nothing")
    void nothingWrittenIsNothingClaimed(@TempDir Path directory) throws IOException {
        ScriptedRunner runner =
                new ScriptedRunner()
                        .thenPrints(1, List.of(BANNER, "-X is not supported"), List.of())
                        .thenPrints(1, List.of(BANNER, "-X is not supported"), List.of());

        assertEquals(
                Set.of(),
                probe(runner).probe(ToolName.PERCOLATOR, V3071, LINUX, binary(directory)),
                "this is Percolator 3.09's shape: it starts, prints its banner and refuses -X, so"
                        + " the absence really was observed");
    }

    @ParameterizedTest(name = "[{index}] {0} psm elements instead of 64")
    @ValueSource(ints = {0, 1, 63, 65, 128})
    @DisplayName("a document with the wrong psm count is not the fixture's output")
    void theWrongPsmCount(int psms, @TempDir Path directory) throws IOException {
        ScriptedRunner runner =
                new ScriptedRunner()
                        .thenWrites(writes(document(psms, false)), 0, List.of(BANNER))
                        .thenWrites(writes(document(128, true)), 0, List.of(BANNER));

        assertEquals(
                Set.of(ToolCapability.XML_DECOY_OUTPUT),
                probe(runner).probe(ToolName.PERCOLATOR, V3071, LINUX, binary(directory)),
                "64 target rows in and "
                        + psms
                        + " psm out is not the same run, so only the second capability -- whose"
                        + " own document is the right shape -- survives");
    }

    @Test
    @DisplayName("a document in the wrong namespace is not Percolator output")
    void theWrongNamespace(@TempDir Path directory) throws IOException {
        String wrong = document(64, false).replace("percolator_out/15", "percolator_out/");
        ScriptedRunner runner =
                new ScriptedRunner()
                        .thenWrites(writes(wrong), 0, List.of(BANNER))
                        .thenWrites(writes(wrong), 0, List.of(BANNER));

        assertEquals(
                Set.of(),
                probe(runner).probe(ToolName.PERCOLATOR, V3071, LINUX, binary(directory)));
    }

    @Test
    @DisplayName("XML_DECOY_OUTPUT needs both decoy values, not merely 128 rows")
    void decoysNeedBothValues(@TempDir Path directory) throws IOException {
        ScriptedRunner runner =
                new ScriptedRunner()
                        .thenWrites(writes(document(64, false)), 0, List.of(BANNER))
                        .thenWrites(writes(document(128, false)), 0, List.of(BANNER));

        assertEquals(
                Set.of(ToolCapability.XML_OUTPUT),
                probe(runner).probe(ToolName.PERCOLATOR, V3071, LINUX, binary(directory)),
                "a build that answered -Z by writing every psm twice would not be writing decoys,"
                        + " and 3.09 is proof that one XML feature can go while another stays");
    }

    @Test
    @DisplayName("the two capabilities are independent: decoys without targets is possible here")
    void theCapabilitiesAreIndependent(@TempDir Path directory) throws IOException {
        ScriptedRunner runner =
                new ScriptedRunner()
                        .thenWrites(writes(""), 0, List.of(BANNER))
                        .thenWrites(writes(document(128, true)), 0, List.of(BANNER));

        assertEquals(
                Set.of(ToolCapability.XML_DECOY_OUTPUT),
                probe(runner).probe(ToolName.PERCOLATOR, V3071, LINUX, binary(directory)),
                "the second run is made whatever the first said, because a release that kept one"
                        + " XML feature and dropped the other must not read as fully capable or as"
                        + " fully incapable");
    }

    @Test
    @DisplayName("no banner is a loadability failure and never an empty capability set")
    void noBannerIsNotAnEmptySet(@TempDir Path directory) throws IOException {
        Path executable = binary(directory);
        ScriptedRunner runner =
                new ScriptedRunner()
                        .thenPrints(
                                127,
                                List.of(
                                        "percolator: error while loading shared libraries:"
                                                + " libboost_filesystem.so.1.83.0: cannot open"
                                                + " shared object file: No such file or directory"),
                                List.of());

        assertEquals(
                "Percolator 3.07.1 at "
                        + executable
                        + " never printed its version banner, so it did not run far enough to be"
                        + " asked what it can do; this is a loadability failure and must not be"
                        + " reported as a missing capability. It exited 127 saying: percolator:"
                        + " error while loading shared libraries: libboost_filesystem.so.1.83.0:"
                        + " cannot open shared object file: No such file or directory",
                assertThrows(
                                IOException.class,
                                () ->
                                        probe(runner)
                                                .probe(
                                                        ToolName.PERCOLATOR,
                                                        V3071,
                                                        LINUX,
                                                        executable))
                        .getMessage());
    }

    @Test
    @DisplayName("a run that never answers is a refusal, not an absence")
    void aRunThatNeverAnswers(@TempDir Path directory) throws IOException {
        Path executable = binary(directory);
        ScriptedRunner runner = new ScriptedRunner().thenNeverFinishes();
        PercolatorCapabilityProbe probe =
                new PercolatorCapabilityProbe(new ToolRunner(runner, Duration.ofMillis(50)), 64);

        assertEquals(
                "Percolator 3.07.1 at "
                        + executable
                        + " did not finish within PT0.05S, so this probe established nothing about"
                        + " it; a probe that got no answer has not established that a capability is"
                        + " absent",
                assertThrows(
                                IOException.class,
                                () -> probe.probe(ToolName.PERCOLATOR, V3071, LINUX, executable))
                        .getMessage());
    }

    @Test
    @DisplayName("a process that will not start propagates rather than becoming an absence")
    void aProcessThatWillNotStart(@TempDir Path directory) throws IOException {
        Path executable = binary(directory);
        ScriptedRunner runner = new ScriptedRunner().thenFailsToStart("Permission denied");

        assertEquals(
                "Permission denied",
                assertThrows(
                                IOException.class,
                                () ->
                                        probe(runner)
                                                .probe(
                                                        ToolName.PERCOLATOR,
                                                        V3071,
                                                        LINUX,
                                                        executable))
                        .getMessage());
    }

    @Test
    @DisplayName("another tool's binary is refused, because a capability belongs to one tool")
    void anotherTool(@TempDir Path directory) throws IOException {
        Path executable = binary(directory);
        PercolatorCapabilityProbe probe = probe(new ScriptedRunner());

        assertAll(
                () ->
                        assertEquals(
                                "this probe runs Percolator and was asked to probe comet; a"
                                        + " capability of one tool means nothing said of another",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        probe.probe(
                                                                ToolName.COMET,
                                                                V3071,
                                                                LINUX,
                                                                executable))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "this probe runs Percolator and was asked to probe pdv; a"
                                        + " capability of one tool means nothing said of another",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        probe.probe(
                                                                ToolName.PDV,
                                                                V3071,
                                                                LINUX,
                                                                executable))
                                        .getMessage()));
    }

    @Test
    @DisplayName("the product's own probe uses 64 target rows, and a smaller one is asked for")
    void theDefaultFixtureSize() {
        ToolRunner runner = new ToolRunner(new ScriptedRunner(), Duration.ofSeconds(1));

        assertAll(
                () -> assertEquals(64, new PercolatorCapabilityProbe(runner).targetRows()),
                () -> assertEquals(8, new PercolatorCapabilityProbe(runner, 8).targetRows()),
                () ->
                        assertEquals(
                                "a synthetic PIN needs at least one target row, but was asked for"
                                        + " 0",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> new PercolatorCapabilityProbe(runner, 0))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "runner",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> new PercolatorCapabilityProbe(null))
                                        .getMessage()));
    }

    @Test
    @DisplayName("every argument is required")
    void everyArgumentIsRequired(@TempDir Path directory) throws IOException {
        Path executable = binary(directory);
        PercolatorCapabilityProbe probe = probe(new ScriptedRunner());

        assertAll(
                () ->
                        assertEquals(
                                "tool",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> probe.probe(null, V3071, LINUX, executable))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "version",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        probe.probe(
                                                                ToolName.PERCOLATOR,
                                                                null,
                                                                LINUX,
                                                                executable))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "platform",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        probe.probe(
                                                                ToolName.PERCOLATOR,
                                                                V3071,
                                                                null,
                                                                executable))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "executable",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        probe.probe(
                                                                ToolName.PERCOLATOR,
                                                                V3071,
                                                                LINUX,
                                                                null))
                                        .getMessage()));
    }

    @Test
    @DisplayName("no temporary workspace is left behind, whether the probe answers or refuses")
    void theWorkspaceIsCleanedUp(@TempDir Path directory) throws IOException {
        Path temporary = Path.of(System.getProperty("java.io.tmpdir"));
        long before = countProbeWorkspaces(temporary);
        ScriptedRunner good =
                new ScriptedRunner()
                        .thenWrites(writes(document(64, false)), 0, List.of(BANNER))
                        .thenWrites(writes(document(128, true)), 0, List.of(BANNER));
        probe(good).probe(ToolName.PERCOLATOR, V3071, LINUX, binary(directory));
        ScriptedRunner bad = new ScriptedRunner().thenPrints(127, List.of("no banner"), List.of());
        assertThrows(
                IOException.class,
                () -> probe(bad).probe(ToolName.PERCOLATOR, V3071, LINUX, binary(directory)));

        assertEquals(before, countProbeWorkspaces(temporary));
    }

    private static long countProbeWorkspaces(Path temporary) throws IOException {
        try (Stream<Path> entries = Files.list(temporary)) {
            return entries.filter(
                            entry ->
                                    entry.getFileName()
                                            .toString()
                                            .startsWith("cometgui-percolator-probe-"))
                    .count();
        }
    }
}
