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

package org.cometgui.tools.limelight;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.cometgui.tools.api.JavaRuntime;
import org.cometgui.tools.api.ToolRunOutcome;
import org.cometgui.tools.api.ToolRunner;
import org.cometgui.tools.limelight.LimelightConversionException.Failure;
import org.cometgui.tools.limelight.LimelightFixtures.Psm;
import org.cometgui.tools.limelight.LimelightFixtures.SmallRun;
import org.cometgui.tools.process.ProcessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The REAL pinned v2.8.1 converter, started on this application's Java runtime through the real
 * process service, on Phase 00's K562 inputs read in place under {@code scratch/} (each held to its
 * SHA-256). Outputs go under a {@code @TempDir} ({@code D-006}).
 *
 * <p>These fail, naming what is missing, if the mirror or an input is absent; nothing skips.
 */
@EnabledOnOs(OS.LINUX)
class LimelightConverterRealTest {

    private static final Duration TIMEOUT = Duration.ofMinutes(5);

    private static ToolRunner runner() {
        return new ToolRunner(new ProcessService(Clock.systemUTC()), TIMEOUT);
    }

    private static LimelightConverter converter(Path jar) throws IOException {
        return new LimelightConverter(
                new ProcessService(Clock.systemUTC()),
                TIMEOUT,
                JavaRuntime.ofThisApplication(),
                jar);
    }

    /** The K562 run: Comet 2026.02.2 on {@code _3} and {@code _4}, Percolator 3.07.1 {@code -X}. */
    private static LimelightConversionRequest k562(Path output, LimelightQValueCutoff q)
            throws IOException {
        PercolatorXmlPsms pout =
                PercolatorXmlPsms.read(
                        LimelightFixtures.real(
                                LimelightFixtures.POUT, LimelightFixtures.POUT_SHA256));
        PepXmlSet pepXmls =
                PepXmlSet.verify(
                        pout,
                        LimelightFixtures.realDirectory(LimelightFixtures.COMET_DIRECTORY),
                        Map.of(
                                LimelightFixtures.PEPXML_3, LimelightFixtures.PEPXML_3_SHA256,
                                LimelightFixtures.PEPXML_4, LimelightFixtures.PEPXML_4_SHA256),
                        LimelightFixtures.hashes());
        return new LimelightConversionRequest(
                LimelightFixtures.real(
                        LimelightFixtures.COMET_PARAMS, LimelightFixtures.COMET_PARAMS_SHA256),
                pout,
                pepXmls,
                LimelightFixtures.real(LimelightFixtures.FASTA, LimelightFixtures.FASTA_SHA256),
                output,
                q,
                false,
                Optional.empty(),
                false,
                false);
    }

    /** Lines of a file that contain a string, read by this test without the product's parser. */
    private static List<String> linesContaining(Path file, String text) throws IOException {
        List<String> found = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(text)) {
                    found.add(line.strip());
                }
            }
        }
        return found;
    }

    private static long count(Path file, String text) throws IOException {
        return linesContaining(file, text).size();
    }

    private static Set<String> filesIn(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            Set<String> names = new LinkedHashSet<>();
            entries.forEach(entry -> names.add(String.valueOf(entry.getFileName())));
            return names;
        }
    }

    @Test
    @DisplayName("P12-4: every option the builder can emit is in the REAL JAR's --help")
    void everyEmittedOptionIsInTheRealHelp(@TempDir Path directory) throws IOException {
        Path jar = LimelightFixtures.converterJar(directory.resolve("tool"));
        ToolRunOutcome help =
                runner().run(
                                JavaRuntime.ofThisApplication()
                                        .jarCommand(jar, List.of("--help"), Map.of()));
        String text = help.joinedOutput();
        SmallRun run =
                SmallRun.in(
                        directory.resolve("run"),
                        List.of(Psm.target("r_1_2_1"), new Psm("r_2_2_1", "true")));
        PercolatorXmlPsms read = run.read();
        LimelightConversionRequest everything =
                new LimelightConversionRequest(
                        directory.resolve("c.params"),
                        read,
                        run.verified(),
                        directory.resolve("db.fasta"),
                        directory.resolve("out.xml"),
                        new LimelightQValueCutoff(0.05),
                        true,
                        Optional.of("DECOY_"),
                        true,
                        true);
        Set<String> emitted = new LinkedHashSet<>();
        for (String argument :
                LimelightConverterCommand.arguments(everything, directory.resolve("o.xml"))) {
            if (argument.startsWith("-")) {
                emitted.add(
                        argument.contains("=")
                                ? argument.substring(0, argument.indexOf('='))
                                : argument);
            }
        }

        assertAll(
                () -> assertEquals(0, help.exitCode().orElseThrow()),
                () ->
                        assertTrue(
                                text.contains(
                                        "java -jar cometPercolator2LimelightXML.jar [-hvV]"
                                                + " [--import-decoys]"),
                                "this is the converter's help: " + text),
                () ->
                        assertEquals(
                                Set.copyOf(LimelightConverterCommand.OPTIONS),
                                emitted,
                                "the builder emits exactly the options it declares"));
        for (String option : emitted) {
            Pattern inHelp =
                    Pattern.compile(
                            "(?m)^\\s+(-\\w, )?" + Pattern.quote(option) + "(=<\\w+>)?(\\s|,|$)");
            assertTrue(
                    inHelp.matcher(text).find(),
                    option + " is not an option in the real converter's --help:\n" + text);
        }
    }

    @Test
    @DisplayName("D-007 caution 1: with NO arguments the real converter exits 0 and writes nothing")
    void noArgumentsExitZero(@TempDir Path directory) throws IOException {
        Path jar = LimelightFixtures.converterJar(directory);

        ToolRunOutcome outcome =
                runner().run(JavaRuntime.ofThisApplication().jarCommand(jar, List.of(), Map.of()));

        assertAll(
                () -> assertEquals(0, outcome.exitCode().orElseThrow()),
                () ->
                        assertTrue(
                                outcome.joinedOutput().contains("Missing required options"),
                                outcome.joinedOutput()),
                () -> assertEquals(Set.of("cometPercolator2LimelightXML.jar"), filesIn(directory)));
    }

    @Test
    @DisplayName(
            "D-007 caution 1: an UNKNOWN option among valid ones exits 0, writes nothing, and the"
                    + " judge says NO_OUTPUT")
    void anUnknownOptionExitsZero(@TempDir Path directory) throws IOException {
        Path jar = LimelightFixtures.converterJar(directory.resolve("tool"));
        Path out = Files.createDirectories(directory.resolve("out")).resolve("x.limelight.xml");
        List<String> arguments =
                new ArrayList<>(
                        LimelightConverterCommand.arguments(
                                k562(out, LimelightQValueCutoff.DEFAULT), out));
        arguments.add("--no-such-option");

        ToolRunOutcome outcome =
                runner().run(JavaRuntime.ofThisApplication().jarCommand(jar, arguments, Map.of()));

        assertAll(
                () -> assertEquals(0, outcome.exitCode().orElseThrow()),
                () ->
                        assertTrue(
                                outcome.joinedOutput().contains("--no-such-option"),
                                outcome.joinedOutput()),
                () -> assertFalse(Files.exists(out)),
                () ->
                        assertEquals(
                                Failure.NO_OUTPUT,
                                assertThrows(
                                                LimelightConversionException.class,
                                                () ->
                                                        LimelightXmlJudge.judge(
                                                                out,
                                                                LimelightSchema.fromConverterJar(
                                                                        jar)))
                                        .failure()));
    }

    @Test
    @DisplayName(
            "-q NaN, which the cutoff type refuses, is refused by the real converter with exit 0")
    void anUnparseableCutoffExitsZero(@TempDir Path directory) throws IOException {
        Path jar = LimelightFixtures.converterJar(directory.resolve("tool"));
        Path out = Files.createDirectories(directory.resolve("out")).resolve("x.limelight.xml");
        List<String> arguments =
                new ArrayList<>(
                        LimelightConverterCommand.arguments(
                                k562(out, LimelightQValueCutoff.DEFAULT), out));
        arguments.set(arguments.indexOf("-q") + 1, "NaN");

        ToolRunOutcome outcome =
                runner().run(JavaRuntime.ofThisApplication().jarCommand(jar, arguments, Map.of()));

        assertAll(
                () -> assertEquals(0, outcome.exitCode().orElseThrow()),
                () ->
                        assertTrue(
                                outcome.joinedOutput()
                                        .contains("Invalid value for option '--q-value'"),
                                outcome.joinedOutput()),
                () -> assertFalse(Files.exists(out)));
    }

    @Test
    @DisplayName(
            "GATE 1 (adapter): a REAL conversion of the K562 run at the default cutoff is kept,"
                    + " schema-valid, and matches Phase 00's counts")
    void aRealConversion(@TempDir Path directory) throws IOException {
        Path jar = LimelightFixtures.converterJar(directory.resolve("tool"));
        Path out = Files.createDirectories(directory.resolve("out")).resolve("k562.limelight.xml");

        LimelightConversion conversion =
                converter(jar).convert(k562(out, LimelightQValueCutoff.DEFAULT));

        assertAll(
                () -> assertTrue(conversion.outcome().exitedZero()),
                () -> assertEquals(out, conversion.output().file()),
                () -> assertEquals(Files.size(out), conversion.output().size()),
                () -> assertEquals(3897, conversion.output().psmCount()),
                () -> assertEquals(2985, conversion.output().reportedPeptideCount()),
                () -> assertEquals(3897L, count(out, "<psm ")),
                () -> assertEquals(Optional.of("0.01"), conversion.output().psmQValueDefault()),
                () -> assertEquals(Set.of("k562.limelight.xml"), filesIn(out.getParent())),
                () ->
                        assertEquals(
                                LimelightConverterCommand.arguments(
                                        conversion.request(), LimelightConverter.stagingPath(out)),
                                conversion.argv().subList(3, conversion.argv().size())),
                () ->
                        assertEquals(
                                Set.of("cometPercolator2LimelightXML.jar"),
                                filesIn(jar.getParent()),
                                "the converter wrote nothing beside itself"),
                () ->
                        assertEquals(
                                3897,
                                LimelightXmlJudge.judge(out, LimelightSchema.fromConverterJar(jar))
                                        .psmCount()));
    }

    @Test
    @DisplayName(
            "P12-6: -q 0.05 REACHES THE FILE -- the PSM q-value filter default reads 0.05, the"
                    + " peptide one stays 0.01, and no PSM is filtered out")
    void theCutoffReachesTheFile(@TempDir Path directory) throws IOException {
        Path jar = LimelightFixtures.converterJar(directory.resolve("tool"));
        Path out = Files.createDirectories(directory.resolve("out")).resolve("q05.limelight.xml");

        LimelightConversion conversion =
                converter(jar).convert(k562(out, new LimelightQValueCutoff(0.05)));

        assertAll(
                () -> assertEquals(Optional.of("0.05"), conversion.output().psmQValueDefault()),
                () ->
                        assertEquals(
                                List.of(
                                        "<filterable_psm_annotation_type name=\"q-value\""
                                                + " description=\"Q-value\""
                                                + " filter_direction=\"below\""
                                                + " default_filter_value=\"0.05\"/>"),
                                linesContaining(
                                        out, "<filterable_psm_annotation_type name=\"q-value\"")),
                () ->
                        assertEquals(
                                List.of(
                                        "<filterable_reported_peptide_annotation_type"
                                                + " name=\"q-value\" description=\"Q-value\""
                                                + " filter_direction=\"below\""
                                                + " default_filter_value=\"0.01\"/>"),
                                linesContaining(
                                        out,
                                        "<filterable_reported_peptide_annotation_type"
                                                + " name=\"q-value\"")),
                () -> assertEquals(3897L, count(out, "<psm ")),
                () -> assertEquals(2985L, count(out, "<reported_peptide ")),
                () ->
                        assertTrue(
                                linesContaining(out, "<conversion_program ")
                                        .get(0)
                                        .contains(" -q 0.05"),
                                "the converter records the -q it was given"));
    }

    @Test
    @DisplayName(
            "a REAL failing conversion (no FASTA there) exits 1 and leaves nothing at the output")
    void aRealFailure(@TempDir Path directory) throws IOException {
        Path jar = LimelightFixtures.converterJar(directory.resolve("tool"));
        Path out = Files.createDirectories(directory.resolve("out")).resolve("k562.limelight.xml");
        LimelightConversionRequest good = k562(out, LimelightQValueCutoff.DEFAULT);
        LimelightConversionRequest noFasta =
                new LimelightConversionRequest(
                        good.cometParams(),
                        good.percolatorXml(),
                        good.pepXmls(),
                        directory.resolve("absent.fasta"),
                        out,
                        good.qCutoff(),
                        false,
                        Optional.empty(),
                        false,
                        false);

        LimelightConversionException failed =
                assertThrows(
                        LimelightConversionException.class, () -> converter(jar).convert(noFasta));

        assertAll(
                () -> assertEquals(Failure.EXITED_NON_ZERO, failed.failure()),
                () ->
                        assertTrue(
                                failed.getMessage()
                                        .contains(
                                                "Could not find fasta file: "
                                                        + directory.resolve("absent.fasta")),
                                failed.getMessage()),
                () -> assertEquals(Set.of(), filesIn(out.getParent())));
    }

    @Test
    @DisplayName(
            "a REAL decoy import: a -Z run over a concatenated FASTA converts, its decoys imported")
    void aRealDecoyImport(@TempDir Path directory) throws IOException {
        Path jar = LimelightFixtures.converterJar(directory.resolve("tool"));
        Path out =
                Files.createDirectories(directory.resolve("out")).resolve("decoys.limelight.xml");
        PercolatorXmlPsms pout =
                PercolatorXmlPsms.read(
                        LimelightFixtures.real(
                                LimelightFixtures.CONCAT, LimelightFixtures.CONCAT_SHA256));
        LimelightConversionRequest request =
                new LimelightConversionRequest(
                        LimelightFixtures.real(
                                LimelightFixtures.CONCAT_COMET_PARAMS,
                                LimelightFixtures.CONCAT_COMET_PARAMS_SHA256),
                        pout,
                        PepXmlSet.verify(
                                pout,
                                LimelightFixtures.realDirectory(LimelightFixtures.CONCAT_DIRECTORY),
                                Map.of(
                                        LimelightFixtures.PEPXML_3,
                                        LimelightFixtures.CONCAT_PEPXML_3_SHA256,
                                        LimelightFixtures.PEPXML_4,
                                        LimelightFixtures.CONCAT_PEPXML_4_SHA256),
                                LimelightFixtures.hashes()),
                        LimelightFixtures.real(
                                LimelightFixtures.CONCAT_FASTA,
                                LimelightFixtures.CONCAT_FASTA_SHA256),
                        out,
                        LimelightQValueCutoff.DEFAULT,
                        true,
                        Optional.empty(),
                        false,
                        false);

        LimelightConversion conversion = converter(jar).convert(request);

        List<String> psms = linesContaining(out, "<psm ");
        assertAll(
                () -> assertEquals(6670, conversion.output().psmCount()),
                () -> assertEquals(6670, psms.size()),
                () ->
                        assertEquals(
                                2780L,
                                psms.stream().filter(l -> l.contains("is_decoy=\"true\"")).count()),
                () -> assertTrue(conversion.argv().contains("--import-decoys")));
    }
}
