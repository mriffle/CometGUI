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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.tools.api.ToolRunner;
import org.cometgui.tools.testing.FakePercolator;
import org.cometgui.tools.testing.Nulls;
import org.cometgui.tools.testing.ScriptedRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    /**
     * The first run, {@code --no-analytics}'s own, refused as real Percolator refuses an option it
     * does not know: these tests grade the XML verdicts, so the later runs carry no {@code
     * --no-analytics} and {@link #writes} finds the output path where it always was. {@code
     * NO_ANALYTICS_OPTION} itself is graded over {@link FakePercolator} below.
     */
    private static ScriptedRunner refusingNoAnalytics() {
        return new ScriptedRunner()
                .thenPrints(1, List.of("Exception caught: " + BANNER), List.of());
    }

    /**
     * The nine runs after the two XML runs, each refused as real Percolator refuses an option it
     * does not know: these tests grade the XML verdicts, and every other capability is graded over
     * {@link FakePercolator} below.
     */
    private static ScriptedRunner refusingTheRest(ScriptedRunner runner) {
        for (int run = 0; run < 9; run++) {
            runner.thenPrints(1, List.of("Exception caught: " + BANNER), List.of());
        }
        return runner;
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
                refusingTheRest(
                        refusingNoAnalytics()
                                .thenWrites(writes(document(64, false)), 0, List.of(BANNER))
                                .thenWrites(writes(document(128, true)), 0, List.of(BANNER)));

        Set<ToolCapability> observed =
                probe(runner).probe(ToolName.PERCOLATOR, V3071, LINUX, binary(directory));

        assertAll(
                () ->
                        assertEquals(
                                Set.of(ToolCapability.XML_OUTPUT, ToolCapability.XML_DECOY_OUTPUT),
                                observed),
                () -> assertEquals(12, runner.played(), "each capability gets its own run"));
    }

    @Test
    @DisplayName("the two XML runs are -X and -X -Z, in that order, over one fixture")
    void theArgumentArrays(@TempDir Path directory) throws IOException {
        ScriptedRunner runner =
                refusingTheRest(
                        refusingNoAnalytics()
                                .thenWrites(writes(document(64, false)), 0, List.of(BANNER))
                                .thenWrites(writes(document(128, true)), 0, List.of(BANNER)));
        Path executable = binary(directory);

        probe(runner).probe(ToolName.PERCOLATOR, V3071, LINUX, executable);

        List<String> targets = runner.commands().get(1).argv();
        List<String> decoys = runner.commands().get(2).argv();
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
                refusingTheRest(
                        refusingNoAnalytics()
                                .thenWrites(writes(""), 0, List.of(BANNER))
                                .thenWrites(writes(""), 0, List.of(BANNER)));

        Set<ToolCapability> observed =
                probe(runner).probe(ToolName.PERCOLATOR, V3071, LINUX, binary(directory));

        assertEquals(Set.of(), observed);
    }

    @Test
    @DisplayName("a run that wrote nothing at all claims nothing")
    void nothingWrittenIsNothingClaimed(@TempDir Path directory) throws IOException {
        ScriptedRunner runner =
                refusingTheRest(
                        refusingNoAnalytics()
                                .thenPrints(1, List.of(BANNER, "-X is not supported"), List.of())
                                .thenPrints(1, List.of(BANNER, "-X is not supported"), List.of()));

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
                refusingTheRest(
                        refusingNoAnalytics()
                                .thenWrites(writes(document(psms, false)), 0, List.of(BANNER))
                                .thenWrites(writes(document(128, true)), 0, List.of(BANNER)));

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
                refusingTheRest(
                        refusingNoAnalytics()
                                .thenWrites(writes(wrong), 0, List.of(BANNER))
                                .thenWrites(writes(wrong), 0, List.of(BANNER)));

        assertEquals(
                Set.of(),
                probe(runner).probe(ToolName.PERCOLATOR, V3071, LINUX, binary(directory)));
    }

    @Test
    @DisplayName("XML_DECOY_OUTPUT needs both decoy values, not merely 128 rows")
    void decoysNeedBothValues(@TempDir Path directory) throws IOException {
        ScriptedRunner runner =
                refusingTheRest(
                        refusingNoAnalytics()
                                .thenWrites(writes(document(64, false)), 0, List.of(BANNER))
                                .thenWrites(writes(document(128, false)), 0, List.of(BANNER)));

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
                refusingTheRest(
                        refusingNoAnalytics()
                                .thenWrites(writes(""), 0, List.of(BANNER))
                                .thenWrites(writes(document(128, true)), 0, List.of(BANNER)));

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
                                1,
                                new PercolatorCapabilityProbe(runner, 1).targetRows(),
                                "one target row is a fixture, however useless; the floor is at"
                                        + " zero"),
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
                                                () ->
                                                        new PercolatorCapabilityProbe(
                                                                Nulls.of(ToolRunner.class)))
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
                                                () ->
                                                        probe.probe(
                                                                Nulls.of(ToolName.class),
                                                                V3071,
                                                                LINUX,
                                                                executable))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "version",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        probe.probe(
                                                                ToolName.PERCOLATOR,
                                                                Nulls.of(ToolVersion.class),
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
                                                                Nulls.of(HostPlatform.class),
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
                                                                Nulls.of(Path.class)))
                                        .getMessage()));
    }

    @Test
    @DisplayName("no temporary workspace is left behind, whether the probe answers or refuses")
    void theWorkspaceIsCleanedUp(@TempDir Path directory) throws IOException {
        Path temporary = Path.of(System.getProperty("java.io.tmpdir"));
        long before = countProbeWorkspaces(temporary);
        ScriptedRunner good =
                refusingTheRest(
                        refusingNoAnalytics()
                                .thenWrites(writes(document(64, false)), 0, List.of(BANNER))
                                .thenWrites(writes(document(128, true)), 0, List.of(BANNER)));
        probe(good).probe(ToolName.PERCOLATOR, V3071, LINUX, binary(directory));
        ScriptedRunner bad = new ScriptedRunner().thenPrints(127, List.of("no banner"), List.of());
        assertThrows(
                IOException.class,
                () -> probe(bad).probe(ToolName.PERCOLATOR, V3071, LINUX, binary(directory)));

        assertEquals(before, countProbeWorkspaces(temporary));
    }

    // ------------------------------------------------------ every capability, over a fake --

    /** Every Percolator capability there is, hand-typed: what a fully capable build probes to. */
    private static final Set<ToolCapability> EVERY_CAPABILITY =
            Set.of(
                    ToolCapability.XML_OUTPUT,
                    ToolCapability.XML_DECOY_OUTPUT,
                    ToolCapability.PSM_TSV_OUTPUT,
                    ToolCapability.PEPTIDE_TSV_OUTPUT,
                    ToolCapability.DECOY_OUTPUT,
                    ToolCapability.WEIGHTS_OUTPUT,
                    ToolCapability.THREAD_OPTION,
                    ToolCapability.SEED_OPTION,
                    ToolCapability.TEST_FDR_OPTION,
                    ToolCapability.TRAIN_FDR_OPTION,
                    ToolCapability.MAX_ITERATIONS_OPTION,
                    ToolCapability.NO_ANALYTICS_OPTION);

    /** The six whose observable is a completed run's standard output, hand-typed. */
    private static final Set<ToolCapability> OPTION_CAPABILITIES =
            Set.of(
                    ToolCapability.THREAD_OPTION,
                    ToolCapability.SEED_OPTION,
                    ToolCapability.TEST_FDR_OPTION,
                    ToolCapability.TRAIN_FDR_OPTION,
                    ToolCapability.MAX_ITERATIONS_OPTION,
                    ToolCapability.NO_ANALYTICS_OPTION);

    private static Set<ToolCapability> probeOver(FakePercolator fake, Path directory)
            throws IOException {
        return new PercolatorCapabilityProbe(new ToolRunner(fake, Duration.ofSeconds(5)), 64)
                .probe(ToolName.PERCOLATOR, V3071, LINUX, binary(directory));
    }

    private static Set<ToolCapability> without(
            Set<ToolCapability> all, Set<ToolCapability> removed) {
        Set<ToolCapability> remaining = EnumSet.noneOf(ToolCapability.class);
        remaining.addAll(all);
        remaining.removeAll(removed);
        return remaining;
    }

    private static UnaryOperator<List<String>> dropLast() {
        return lines -> lines.subList(0, lines.size() - 1);
    }

    private static UnaryOperator<List<String>> appendLine(String line) {
        return lines -> {
            List<String> longer = new ArrayList<>(lines);
            longer.add(line);
            return longer;
        };
    }

    private static UnaryOperator<List<String>> replaceIn(int index, String from, String to) {
        return lines -> {
            List<String> changed = new ArrayList<>(lines);
            int at = index < 0 ? changed.size() + index : index;
            String original = changed.get(at);
            String replaced = original.replace(from, to);
            if (replaced.equals(original)) {
                throw new AssertionError("\"" + from + "\" is not in line " + at + ": " + original);
            }
            changed.set(at, replaced);
            return changed;
        };
    }

    private static UnaryOperator<List<String>> becomes(List<String> replacement) {
        return lines -> replacement;
    }

    @Test
    @DisplayName("a build that does everything the real ones do probes to every capability")
    void aFullyCapableBuild(@TempDir Path directory) throws IOException {
        FakePercolator fake = new FakePercolator();

        assertAll(
                () -> assertEquals(EVERY_CAPABILITY, probeOver(fake, directory)),
                () -> assertEquals(12, fake.commands().size(), "one run per capability"));
    }

    @Test
    @DisplayName(
            "the twelve argument arrays, in order: --no-analytics first, then one option under test"
                    + " each, --no-analytics after it, the PIN last")
    void everyArgumentArray(@TempDir Path directory) throws IOException {
        FakePercolator fake = new FakePercolator();
        Path executable = binary(directory);

        new PercolatorCapabilityProbe(new ToolRunner(fake, Duration.ofSeconds(5)), 64)
                .probe(ToolName.PERCOLATOR, V3071, LINUX, executable);

        List<ToolCommand> commands = fake.commands();
        Path workspace = commands.get(0).workingDirectory();
        String pin = workspace.resolve("probe.pin").toString();
        assertAll(
                () ->
                        assertEquals(
                                List.of(
                                        List.of("--no-analytics", pin),
                                        List.of(
                                                "-X",
                                                workspace.resolve("targets.pout.xml").toString(),
                                                "--no-analytics",
                                                pin),
                                        List.of(
                                                "-X",
                                                workspace.resolve("decoys.pout.xml").toString(),
                                                "-Z",
                                                "--no-analytics",
                                                pin),
                                        List.of(
                                                "--results-psms",
                                                workspace.resolve("psms.tsv").toString(),
                                                "--no-analytics",
                                                pin),
                                        List.of(
                                                "--results-peptides",
                                                workspace.resolve("peptides.tsv").toString(),
                                                "--no-analytics",
                                                pin),
                                        List.of(
                                                "--decoy-results-psms",
                                                workspace.resolve("decoy-psms.tsv").toString(),
                                                "--decoy-results-peptides",
                                                workspace.resolve("decoy-peptides.tsv").toString(),
                                                "--no-analytics",
                                                pin),
                                        List.of(
                                                "--weights",
                                                workspace.resolve("weights.txt").toString(),
                                                "--no-analytics",
                                                pin),
                                        List.of("--seed", "1", "--no-analytics", pin),
                                        List.of("--num-threads", "3", "--no-analytics", pin),
                                        List.of("--testFDR", "0.01", "--no-analytics", pin),
                                        List.of("--trainFDR", "0.01", "--no-analytics", pin),
                                        List.of("--maxiter", "10", "--no-analytics", pin)),
                                commands.stream()
                                        .map(
                                                command ->
                                                        command.argv()
                                                                .subList(1, command.argv().size()))
                                        .toList()),
                () ->
                        assertTrue(
                                commands.stream()
                                        .allMatch(
                                                command ->
                                                        command.argv()
                                                                        .get(0)
                                                                        .equals(
                                                                                executable
                                                                                        .toString())
                                                                && command.workingDirectory()
                                                                        .equals(workspace)
                                                                && command.environment().isEmpty()),
                                "every run is of the binary given, in one workspace, with a"
                                        + " constructed and empty environment"),
                () ->
                        assertTrue(
                                String.valueOf(workspace.getFileName())
                                        .startsWith("cometgui-percolator-probe-"),
                                workspace::toString));
    }

    @ParameterizedTest(name = "[{index}] rejecting {0} loses exactly {1}")
    @CsvSource({
        "-X, XML_OUTPUT XML_DECOY_OUTPUT",
        "-Z, XML_DECOY_OUTPUT",
        "--results-psms, PSM_TSV_OUTPUT",
        "--results-peptides, PEPTIDE_TSV_OUTPUT",
        "--decoy-results-psms, DECOY_OUTPUT",
        "--decoy-results-peptides, DECOY_OUTPUT",
        "--weights, WEIGHTS_OUTPUT",
        "--seed, SEED_OPTION",
        "--num-threads, THREAD_OPTION",
        "--testFDR, TEST_FDR_OPTION",
        "--trainFDR, TRAIN_FDR_OPTION",
        "--maxiter, MAX_ITERATIONS_OPTION",
        "--no-analytics, NO_ANALYTICS_OPTION"
    })
    @DisplayName("a build rejecting one option loses exactly that capability and keeps the rest")
    void oneRejectedOptionLosesExactlyItsCapability(
            String option, String lost, @TempDir Path directory) throws IOException {
        Set<ToolCapability> removed = EnumSet.noneOf(ToolCapability.class);
        for (String id : lost.split(" ")) {
            removed.add(ToolCapability.fromId(id));
        }

        assertEquals(
                without(EVERY_CAPABILITY, removed),
                probeOver(new FakePercolator().rejecting(option), directory),
                "-X is in both XML runs, so it is the one option whose rejection costs two");
    }

    @Test
    @DisplayName("3.09's shape -- -X and -Z refused -- keeps every tab-separated capability")
    void theShapeOfPercolator309(@TempDir Path directory) throws IOException {
        assertEquals(
                Set.of(
                        ToolCapability.PSM_TSV_OUTPUT,
                        ToolCapability.PEPTIDE_TSV_OUTPUT,
                        ToolCapability.DECOY_OUTPUT,
                        ToolCapability.WEIGHTS_OUTPUT,
                        ToolCapability.THREAD_OPTION,
                        ToolCapability.SEED_OPTION,
                        ToolCapability.TEST_FDR_OPTION,
                        ToolCapability.TRAIN_FDR_OPTION,
                        ToolCapability.MAX_ITERATIONS_OPTION,
                        ToolCapability.NO_ANALYTICS_OPTION),
                probeOver(new FakePercolator().rejecting("-X").rejecting("-Z"), directory));
    }

    @Test
    @DisplayName("a build that never prints its banner throws: no answer is not an empty set")
    void aBuildThatNeverRunsThrows(@TempDir Path directory) throws IOException {
        Path executable = binary(directory);
        FakePercolator fake = new FakePercolator().withoutBanner();

        IOException refused =
                assertThrows(
                        IOException.class,
                        () ->
                                new PercolatorCapabilityProbe(
                                                new ToolRunner(fake, Duration.ofSeconds(5)), 64)
                                        .probe(ToolName.PERCOLATOR, V3071, LINUX, executable));

        assertAll(
                () ->
                        assertEquals(
                                "Percolator 3.07.1 at "
                                        + executable
                                        + " never printed its version banner, so it did not run"
                                        + " far enough to be asked what it can do; this is a"
                                        + " loadability failure and must not be reported as a"
                                        + " missing capability. It exited 127 saying: percolator:"
                                        + " error while loading shared libraries",
                                refused.getMessage()),
                () -> assertEquals(1, fake.commands().size(), "it stopped at the first run"));
    }

    @Test
    @DisplayName(
            "a banner missing from a LATER run still throws, rather than costing one capability")
    void aLaterRunWithNoBannerThrows(@TempDir Path directory) throws IOException {
        Path executable = binary(directory);
        ScriptedRunner runner =
                new ScriptedRunner()
                        .thenPrints(1, List.of("Exception caught: " + BANNER), List.of())
                        .thenPrints(1, List.of("Exception caught: " + BANNER), List.of())
                        .thenPrints(139, List.of("Segmentation fault"), List.of());

        IOException refused =
                assertThrows(
                        IOException.class,
                        () -> probe(runner).probe(ToolName.PERCOLATOR, V3071, LINUX, executable));

        assertTrue(
                refused.getMessage().endsWith("It exited 139 saying: Segmentation fault"),
                refused.getMessage());
    }

    @Test
    @DisplayName("a run that times out after the XML runs still throws")
    void aLaterTimeoutThrows(@TempDir Path directory) throws IOException {
        Path executable = binary(directory);
        ScriptedRunner runner =
                new ScriptedRunner()
                        .thenPrints(1, List.of("Exception caught: " + BANNER), List.of())
                        .thenPrints(1, List.of("Exception caught: " + BANNER), List.of())
                        .thenPrints(1, List.of("Exception caught: " + BANNER), List.of())
                        .thenNeverFinishes();
        PercolatorCapabilityProbe probe =
                new PercolatorCapabilityProbe(new ToolRunner(runner, Duration.ofMillis(50)), 64);

        assertTrue(
                assertThrows(
                                IOException.class,
                                () -> probe.probe(ToolName.PERCOLATOR, V3071, LINUX, executable))
                        .getMessage()
                        .contains("did not finish within PT0.05S"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "one row fewer | dropLast",
                "one row more | appendTarget",
                "no q-value column | noQValue",
                "a decoy among the targets | decoyRow",
                "a row short of a field | shortRow",
                "nothing at all | empty",
                "a header only | headerOnly"
            })
    @DisplayName(
            "a PSM table that is not the fixture's targets costs PSM_TSV_OUTPUT and nothing else")
    void aDamagedPsmTable(String what, String damage, @TempDir Path directory) throws IOException {
        FakePercolator fake = new FakePercolator().altering("--results-psms", damageNamed(damage));

        assertEquals(
                without(EVERY_CAPABILITY, Set.of(ToolCapability.PSM_TSV_OUTPUT)),
                probeOver(fake, directory),
                what);
    }

    private static UnaryOperator<List<String>> damageNamed(String damage) {
        return switch (damage) {
            case "dropLast" -> dropLast();
            case "appendTarget" ->
                    appendLine("psm999\t0.5\t0.01\t0.02\tK.AAAAAAAAA.R\tsp|P99999|TEST");
            case "noQValue" -> replaceIn(0, "q-value", "qvalue");
            case "decoyRow" -> replaceIn(-1, "\tsp|", "\tdecoy_sp|");
            case "shortRow" ->
                    lines -> {
                        List<String> changed = new ArrayList<>(lines);
                        String last = changed.get(changed.size() - 1);
                        changed.set(changed.size() - 1, last.substring(0, last.lastIndexOf('\t')));
                        return changed;
                    };
            case "empty" -> becomes(List.of());
            case "headerOnly" -> becomes(List.of(FakePercolator.TABLE_HEADER));
            default -> throw new AssertionError("no damage named " + damage);
        };
    }

    @Test
    @DisplayName("an extra column is not a damaged table: the columns are found by name")
    void anExtraColumnIsAccepted(@TempDir Path directory) throws IOException {
        UnaryOperator<List<String>> widened =
                lines -> lines.stream().map(line -> "extra\t" + line).toList();
        FakePercolator fake = new FakePercolator().altering("--results-psms", widened);

        assertEquals(EVERY_CAPABILITY, probeOver(fake, directory));
    }

    @Test
    @DisplayName("a peptide table one row short costs PEPTIDE_TSV_OUTPUT and nothing else")
    void aDamagedPeptideTable(@TempDir Path directory) throws IOException {
        FakePercolator fake = new FakePercolator().altering("--results-peptides", dropLast());

        assertEquals(
                without(EVERY_CAPABILITY, Set.of(ToolCapability.PEPTIDE_TSV_OUTPUT)),
                probeOver(fake, directory));
    }

    @Test
    @DisplayName(
            "a decoy table holding a target, or a missing second decoy table, costs DECOY_OUTPUT")
    void damagedDecoyTables(@TempDir Path directory) throws IOException {
        FakePercolator targetAmongDecoys =
                new FakePercolator()
                        .altering("--decoy-results-psms", replaceIn(-1, "\tdecoy_sp|", "\tsp|"));
        FakePercolator noDecoyPeptides =
                new FakePercolator().altering("--decoy-results-peptides", becomes(List.of()));
        Set<ToolCapability> expected =
                without(EVERY_CAPABILITY, Set.of(ToolCapability.DECOY_OUTPUT));

        assertAll(
                () ->
                        assertEquals(
                                expected,
                                probeOver(
                                        targetAmongDecoys,
                                        Files.createDirectory(directory.resolve("a"))),
                                "a decoy PSM table with a target in it"),
                () ->
                        assertEquals(
                                expected,
                                probeOver(
                                        noDecoyPeptides,
                                        Files.createDirectory(directory.resolve("b"))),
                                "the decoy PSMs alone are half of one capability"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "a bin that does not name feat3 | noFeature",
                "a weight that is not a number | notANumber",
                "a weight that is NaN | nan",
                "a weight that is infinite | infinite",
                "a row narrower than its header | narrow",
                "a row wider than its header | wide",
                "a bin missing its raw row | dropLast",
                "comments and nothing else | commentsOnly",
                "nothing at all | empty"
            })
    @DisplayName("weights that are not the fixture's cost WEIGHTS_OUTPUT and nothing else")
    void damagedWeights(String what, String damage, @TempDir Path directory) throws IOException {
        UnaryOperator<List<String>> alteration =
                switch (damage) {
                    case "noFeature" -> replaceIn(-3, "feat3", "feat4");
                    case "notANumber" -> replaceIn(-1, "-0.4876", "heavy");
                    case "nan" -> replaceIn(-2, "-0.2914", "NaN");
                    case "infinite" -> replaceIn(-1, "-0.4876", "Infinity");
                    case "narrow" -> replaceIn(-1, "\t-0.4876", "");
                    case "wide" -> replaceIn(-2, "-0.2914", "-0.2914\t1.0");
                    case "dropLast" -> dropLast();
                    case "commentsOnly" -> lines -> lines.subList(0, 3);
                    case "empty" -> becomes(List.of());
                    default -> throw new AssertionError("no damage named " + damage);
                };
        FakePercolator fake = new FakePercolator().altering("--weights", alteration);

        assertEquals(
                without(EVERY_CAPABILITY, Set.of(ToolCapability.WEIGHTS_OUTPUT)),
                probeOver(fake, directory),
                what);
    }

    @Test
    @DisplayName(
            "weights with no comment lines are still weights: the comments are not the evidence")
    void weightsWithoutComments(@TempDir Path directory) throws IOException {
        FakePercolator fake =
                new FakePercolator().altering("--weights", lines -> lines.subList(3, lines.size()));

        assertEquals(EVERY_CAPABILITY, probeOver(fake, directory));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "no table on standard output | empty",
                "one row fewer | dropLast",
                "a decoy among the targets | decoyRow"
            })
    @DisplayName("an option run whose standard output is not the table costs the six options only")
    void anIncompleteStandardOutput(String what, String damage, @TempDir Path directory)
            throws IOException {
        FakePercolator fake =
                new FakePercolator().altering(FakePercolator.STANDARD_OUTPUT, damageNamed(damage));

        assertEquals(
                without(EVERY_CAPABILITY, OPTION_CAPABILITIES), probeOver(fake, directory), what);
    }

    @Test
    @DisplayName("an option run that exits 1 with a whole table is not a completed run")
    void anOptionRunMustExitZero(@TempDir Path directory) throws IOException {
        FakePercolator fake = new FakePercolator().exitingWith("--maxiter", 1);

        assertEquals(
                without(EVERY_CAPABILITY, Set.of(ToolCapability.MAX_ITERATIONS_OPTION)),
                probeOver(fake, directory));
    }

    @Test
    @DisplayName(
            "a WRITTEN table is the verdict whatever the exit code, as the document is for XML")
    void aWrittenTableIsTheVerdict(@TempDir Path directory) throws IOException {
        FakePercolator fake =
                new FakePercolator()
                        .exitingWith("--results-psms", 1)
                        .exitingWith("--weights", 2)
                        .exitingWith("--decoy-results-psms", 1);

        assertEquals(EVERY_CAPABILITY, probeOver(fake, directory));
    }

    @Test
    @DisplayName("the product's probe passes Percolator's own defaults, so only the option differs")
    void theOptionValuesAreTheDefaults(@TempDir Path directory) throws IOException {
        FakePercolator fake = new FakePercolator();
        probeOver(fake, directory);

        List<List<String>> optionRuns =
                fake.commands().subList(7, 12).stream()
                        .map(command -> command.argv().subList(1, 3))
                        .toList();
        assertEquals(
                List.of(
                        List.of("--seed", "1"),
                        List.of("--num-threads", "3"),
                        List.of("--testFDR", "0.01"),
                        List.of("--trainFDR", "0.01"),
                        List.of("--maxiter", "10")),
                optionRuns);
    }

    // ------------------------------------------------------------- --no-analytics (D-013) --

    private static long occurrences(List<String> argv, String element) {
        return argv.stream().filter(element::equals).count();
    }

    @Test
    @DisplayName(
            "D-013: the FIRST run is --no-analytics alone, and once observed EVERY later run of the"
                    + " probe carries it exactly once, just before the PIN")
    void noAnalyticsIsObservedFirstAndThenAlwaysPassed(@TempDir Path directory) throws IOException {
        FakePercolator fake = new FakePercolator();
        Path executable = binary(directory);

        Set<ToolCapability> observed =
                new PercolatorCapabilityProbe(new ToolRunner(fake, Duration.ofSeconds(5)), 64)
                        .probe(ToolName.PERCOLATOR, V3071, LINUX, executable);

        List<List<String>> launched = fake.commands().stream().map(ToolCommand::argv).toList();
        String pin = fake.commands().get(0).workingDirectory().resolve("probe.pin").toString();
        assertAll(
                () -> assertTrue(observed.contains(ToolCapability.NO_ANALYTICS_OPTION)),
                () ->
                        assertEquals(
                                List.of(executable.toString(), "--no-analytics", pin),
                                launched.get(0),
                                "the observation run is the first launch, and carries the switch"
                                        + " it observes"),
                () -> assertEquals(12, launched.size()),
                () ->
                        assertEquals(
                                List.of(),
                                launched.stream()
                                        .filter(argv -> occurrences(argv, "--no-analytics") != 1)
                                        .toList(),
                                "every launch of the probe carries --no-analytics exactly once"),
                () ->
                        assertEquals(
                                List.of(),
                                launched.stream()
                                        .filter(
                                                argv ->
                                                        !argv.get(argv.size() - 2)
                                                                .equals("--no-analytics"))
                                        .toList(),
                                "as the last option, just before the PIN"));
    }

    @Test
    @DisplayName(
            "D-013: a build REFUSING --no-analytics loses that capability only, and no later run"
                    + " is handed it")
    void aBuildRefusingNoAnalytics(@TempDir Path directory) throws IOException {
        FakePercolator fake = new FakePercolator().rejecting("--no-analytics");

        Set<ToolCapability> observed = probeOver(fake, directory);

        List<List<String>> launched = fake.commands().stream().map(ToolCommand::argv).toList();
        assertAll(
                () ->
                        assertEquals(
                                without(
                                        EVERY_CAPABILITY,
                                        Set.of(ToolCapability.NO_ANALYTICS_OPTION)),
                                observed),
                () -> assertEquals(12, launched.size(), "every other capability still probed"),
                () -> assertEquals(1, occurrences(launched.get(0), "--no-analytics")),
                () ->
                        assertEquals(
                                List.of(),
                                launched.subList(1, launched.size()).stream()
                                        .filter(argv -> argv.contains("--no-analytics"))
                                        .toList(),
                                "a refused switch is not passed again"));
    }

    @Test
    @DisplayName(
            "D-013: a --no-analytics run that exits 1 with a whole table is not observed, and is"
                    + " not passed on")
    void aNoAnalyticsRunMustExitZero(@TempDir Path directory) throws IOException {
        FakePercolator fake = new FakePercolator().exitingWith("--no-analytics", 1);

        Set<ToolCapability> observed = probeOver(fake, directory);

        assertAll(
                () ->
                        assertEquals(
                                without(
                                        EVERY_CAPABILITY,
                                        Set.of(ToolCapability.NO_ANALYTICS_OPTION)),
                                observed),
                () ->
                        assertTrue(
                                fake.commands().stream()
                                        .skip(1)
                                        .noneMatch(
                                                command ->
                                                        command.argv().contains("--no-analytics")),
                                "only the observation run carried it"));
    }

    private static long countProbeWorkspaces(Path temporary) throws IOException {
        try (Stream<Path> entries = Files.list(temporary)) {
            return entries.filter(
                            entry ->
                                    String.valueOf(entry.getFileName())
                                            .startsWith("cometgui-percolator-probe-"))
                    .count();
        }
    }
}
