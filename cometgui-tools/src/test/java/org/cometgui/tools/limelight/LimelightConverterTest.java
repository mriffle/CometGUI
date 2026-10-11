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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.tools.api.JavaRuntime;
import org.cometgui.tools.limelight.LimelightConversionException.Failure;
import org.cometgui.tools.limelight.LimelightFixtures.SmallRun;
import org.cometgui.tools.testing.Nulls;
import org.cometgui.tools.testing.ScriptedRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The converter's rules, graded with a recording runner that plays what the converter might do --
 * including what the real one does: exit 0 having written nothing (gate item 2). The real JAR is
 * run by {@link LimelightConverterRealTest}.
 */
class LimelightConverterTest {

    private static final Path ROOT =
            java.nio.file.FileSystems.getDefault().getRootDirectories().iterator().next();

    @TempDir private Path directory;

    private Path jar;
    private SmallRun run;
    private Path output;
    private Path staging;

    @BeforeEach
    void stage() throws IOException {
        jar = LimelightFixtures.converterJar(directory.resolve("tool"));
        run = SmallRun.in(directory.resolve("run"));
        output = Files.createDirectories(directory.resolve("out")).resolve("run.limelight.xml");
        staging = output.resolveSibling("run.limelight.xml.partial");
    }

    private LimelightConverter converter(ProcessRunner runner) throws IOException {
        return new LimelightConverter(
                runner, Duration.ofSeconds(5), JavaRuntime.ofThisApplication(), jar);
    }

    private LimelightConversionRequest request(double q) throws IOException {
        return run.request(output, new LimelightQValueCutoff(q));
    }

    private static Consumer<ToolCommand> writes(String content) {
        return command -> {
            Path out = Path.of(command.argv().get(command.argv().indexOf("-o") + 1));
            try {
                Files.writeString(out, content, StandardCharsets.UTF_8);
            } catch (IOException failed) {
                throw new UncheckedIOException(failed);
            }
        };
    }

    private static Consumer<ToolCommand> copies(Path source) {
        return command -> {
            Path out = Path.of(command.argv().get(command.argv().indexOf("-o") + 1));
            try {
                Files.copy(source, out);
            } catch (IOException failed) {
                throw new UncheckedIOException(failed);
            }
        };
    }

    private static Path known() throws IOException {
        return LimelightFixtures.real(
                LimelightFixtures.LIMELIGHT_XML, LimelightFixtures.LIMELIGHT_XML_SHA256);
    }

    private void nothingLooksComplete() {
        assertAll(
                () -> assertFalse(Files.exists(output), "nothing at the final path"),
                () -> assertFalse(Files.exists(staging), "the staging file is removed"));
    }

    @Test
    @DisplayName("GATE 2: exit 0 with NO file is a failure naming the file, and nothing is left")
    void exitZeroWithNoFile() throws IOException {
        ScriptedRunner runner =
                new ScriptedRunner()
                        .thenPrints(
                                0,
                                List.of(),
                                List.of(
                                        "Error printing runtime information.",
                                        "Missing required options"));

        LimelightConversionException failed =
                assertThrows(
                        LimelightConversionException.class,
                        () -> converter(runner).convert(request(0.01)));

        assertAll(
                () -> assertEquals(Failure.NO_OUTPUT, failed.failure()),
                () -> assertEquals(Optional.of(staging), failed.file()),
                () ->
                        assertEquals(
                                "there is no Limelight XML file at "
                                        + staging
                                        + ". The converter exited 0, which it also does when it"
                                        + " writes nothing. Nothing was written to "
                                        + output
                                        + ". Its last output: Error printing runtime information."
                                        + " | Missing required options",
                                failed.getMessage()),
                () -> assertEquals(1, runner.played()));
        nothingLooksComplete();
    }

    @Test
    @DisplayName("GATE 2: exit 0 with an EMPTY file is a failure, and the empty file is removed")
    void exitZeroWithAnEmptyFile() throws IOException {
        ScriptedRunner runner = new ScriptedRunner().thenWrites(writes(""), 0, List.of());

        LimelightConversionException failed =
                assertThrows(
                        LimelightConversionException.class,
                        () -> converter(runner).convert(request(0.01)));

        assertAll(
                () -> assertEquals(Failure.EMPTY_OUTPUT, failed.failure()),
                () -> assertEquals(Optional.of(staging), failed.file()),
                () ->
                        assertTrue(
                                failed.getMessage()
                                        .startsWith(
                                                "the Limelight XML file "
                                                        + staging
                                                        + " is empty (zero bytes). The converter"
                                                        + " exited 0")));
        nothingLooksComplete();
    }

    @Test
    @DisplayName("exit 0 with a malformed file is a failure, and the file is removed")
    void exitZeroWithAMalformedFile() throws IOException {
        ScriptedRunner runner =
                new ScriptedRunner().thenWrites(writes("<limelight_input>"), 0, List.of());

        assertEquals(
                Failure.NOT_WELL_FORMED,
                assertThrows(
                                LimelightConversionException.class,
                                () -> converter(runner).convert(request(0.01)))
                        .failure());
        nothingLooksComplete();
    }

    @Test
    @DisplayName("a valid file is moved to the output path, with the exact command that wrote it")
    void aValidFileIsKept() throws IOException {
        Path source = known();
        ScriptedRunner runner =
                new ScriptedRunner().thenWrites(copies(source), 0, List.of("Done."));
        LimelightConversionRequest request = request(0.01);

        LimelightConversion conversion = converter(runner).convert(request);

        ToolCommand command = runner.commands().get(0);
        List<String> expected = new ArrayList<>();
        expected.add(JavaRuntime.ofThisApplication().launcher().toString());
        expected.add("-jar");
        expected.add(jar.toString());
        expected.addAll(LimelightConverterCommand.arguments(request, staging));
        assertAll(
                () -> assertEquals(expected, command.argv()),
                () -> assertEquals(expected, conversion.argv()),
                () -> assertEquals(jar.getParent(), command.workingDirectory()),
                () -> assertEquals(Map.of("LANG", "C.UTF-8"), command.environment()),
                () -> assertEquals(output, conversion.output().file()),
                () -> assertEquals(3897, conversion.output().psmCount()),
                () -> assertEquals(Optional.of("0.01"), conversion.output().psmQValueDefault()),
                () -> assertEquals(request, conversion.request()),
                () -> assertEquals(List.of("Done."), conversion.outcome().standardError()),
                () ->
                        assertEquals(
                                LimelightFixtures.LIMELIGHT_XML_SHA256,
                                LimelightFixtures.digest(output, "SHA-256")),
                () -> assertFalse(Files.exists(staging)));
    }

    @Test
    @DisplayName("a non-zero exit fails even with a valid file, and the file is removed")
    void aNonZeroExitFails() throws IOException {
        ScriptedRunner runner =
                new ScriptedRunner()
                        .thenWrites(
                                copies(known()),
                                1,
                                List.of("Encountered error during conversion: boom"));

        LimelightConversionException failed =
                assertThrows(
                        LimelightConversionException.class,
                        () -> converter(runner).convert(request(0.01)));

        assertAll(
                () -> assertEquals(Failure.EXITED_NON_ZERO, failed.failure()),
                () ->
                        assertEquals(
                                "the Limelight converter exited with status 1. Nothing was written"
                                        + " to "
                                        + output
                                        + ". Its last output: Encountered error during conversion:"
                                        + " boom",
                                failed.getMessage()));
        nothingLooksComplete();
    }

    @Test
    @DisplayName("a converter that never finishes is stopped and fails as TIMED_OUT")
    void aTimeout() throws IOException {
        ScriptedRunner runner = new ScriptedRunner().thenNeverFinishes();
        LimelightConverter converter =
                new LimelightConverter(
                        runner, Duration.ofMillis(50), JavaRuntime.ofThisApplication(), jar);

        LimelightConversionException failed =
                assertThrows(
                        LimelightConversionException.class, () -> converter.convert(request(0.01)));

        assertAll(
                () -> assertEquals(Failure.TIMED_OUT, failed.failure()),
                () ->
                        assertEquals(
                                "the Limelight converter did not finish within PT0.05S and was"
                                        + " stopped. Nothing was written to "
                                        + output,
                                failed.getMessage()),
                () -> assertFalse(runner.lastProcess().isAlive(), "it was cancelled"));
        nothingLooksComplete();
    }

    @Test
    @DisplayName("a file without the cutoff asked for fails as Q_CUTOFF_NOT_APPLIED")
    void theCutoffMustReachTheFile() throws IOException {
        ScriptedRunner runner = new ScriptedRunner().thenWrites(copies(known()), 0, List.of());

        LimelightConversionException failed =
                assertThrows(
                        LimelightConversionException.class,
                        () -> converter(runner).convert(request(0.05)));

        assertAll(
                () -> assertEquals(Failure.Q_CUTOFF_NOT_APPLIED, failed.failure()),
                () ->
                        assertEquals(
                                "the Limelight XML file "
                                        + staging
                                        + " carries the Percolator PSM q-value filter 0.01, not the"
                                        + " cutoff 0.05 the conversion asked for. Nothing was"
                                        + " written to "
                                        + output,
                                failed.getMessage()));
        nothingLooksComplete();
    }

    @Test
    @DisplayName(
            "a file with no Percolator PSM q-value at all fails; one written 0.010 for 0.01 passes")
    void theCutoffIsComparedAsANumber() throws IOException {
        String text = Files.readString(known(), StandardCharsets.UTF_8);
        String line =
                "<filterable_psm_annotation_type name=\"q-value\" description=\"Q-value\""
                        + " filter_direction=\"below\" default_filter_value=\"0.01\"/>";
        String without = text.replace(line, line.replace("\"q-value\"", "\"q value\""));
        String padded = text.replace(line, line.replace("\"0.01\"", "\"0.010\""));
        ScriptedRunner runner =
                new ScriptedRunner()
                        .thenWrites(writes(without), 0, List.of())
                        .thenWrites(writes(padded), 0, List.of());
        LimelightConverter converter = converter(runner);

        LimelightConversionException failed =
                assertThrows(
                        LimelightConversionException.class, () -> converter.convert(request(0.01)));
        LimelightConversion kept = converter.convert(request(0.01));

        assertAll(
                () -> assertEquals(Failure.Q_CUTOFF_NOT_APPLIED, failed.failure()),
                () ->
                        assertTrue(
                                failed.getMessage()
                                        .contains(
                                                " carries no Percolator PSM q-value filter, not the"
                                                        + " cutoff 0.01 ")),
                () -> assertEquals(Optional.of("0.010"), kept.output().psmQValueDefault()));
    }

    @Test
    @DisplayName(
            "something already at the output path is refused before anything starts, untouched")
    void neverOverwrites() throws IOException {
        Files.writeString(output, "an earlier conversion");
        ScriptedRunner runner = new ScriptedRunner();

        LimelightConversionException refused =
                assertThrows(
                        LimelightConversionException.class,
                        () -> converter(runner).convert(request(0.01)));

        assertAll(
                () -> assertEquals(Failure.OUTPUT_EXISTS, refused.failure()),
                () ->
                        assertEquals(
                                "something is already at "
                                        + output
                                        + ", and a Limelight conversion never overwrites a file",
                                refused.getMessage()),
                () -> assertEquals(0, runner.played()),
                () -> assertEquals("an earlier conversion", Files.readString(output)));
    }

    @Test
    @DisplayName("a dangling link at the output path is still something there")
    void aDanglingLinkIsSomething() throws IOException {
        Files.createSymbolicLink(output, directory.resolve("nowhere"));
        ScriptedRunner runner = new ScriptedRunner();

        assertEquals(
                Failure.OUTPUT_EXISTS,
                assertThrows(
                                LimelightConversionException.class,
                                () -> converter(runner).convert(request(0.01)))
                        .failure());
    }

    @Test
    @DisplayName("a converter JAR with no schema is refused before anything starts")
    void noSchemaNoRun() throws IOException {
        Path empty = Files.createDirectories(directory.resolve("empty"));
        Path bare = empty.resolve("bare.jar");
        try (var zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(bare))) {
            zip.putNextEntry(new java.util.zip.ZipEntry("x"));
            zip.closeEntry();
        }
        ScriptedRunner runner = new ScriptedRunner();
        LimelightConverter converter =
                new LimelightConverter(
                        runner, Duration.ofSeconds(5), JavaRuntime.ofThisApplication(), bare);

        assertAll(
                () ->
                        assertEquals(
                                Failure.NO_SCHEMA,
                                assertThrows(
                                                LimelightConversionException.class,
                                                () -> converter.convert(request(0.01)))
                                        .failure()),
                () -> assertEquals(0, runner.played()));
    }

    @Test
    @DisplayName(
            "a stale staging file is removed before the converter runs, so it cannot be judged")
    void aStaleStagingFileIsRemoved() throws IOException {
        Files.copy(known(), staging);
        List<Boolean> presentAtRun = new ArrayList<>();
        ScriptedRunner runner =
                new ScriptedRunner()
                        .thenWrites(
                                command -> presentAtRun.add(Files.exists(staging)), 0, List.of());

        assertEquals(
                Failure.NO_OUTPUT,
                assertThrows(
                                LimelightConversionException.class,
                                () -> converter(runner).convert(request(0.01)))
                        .failure());
        assertEquals(List.of(false), presentAtRun);
        nothingLooksComplete();
    }

    @Test
    @DisplayName("a missing output directory, and a converter that cannot start, leave nothing")
    void beforeAndAtStart() throws IOException {
        LimelightConversionRequest elsewhere =
                run.request(
                        directory.resolve("absent").resolve("x.xml"),
                        LimelightQValueCutoff.DEFAULT);
        ScriptedRunner none = new ScriptedRunner();
        ScriptedRunner refuses = new ScriptedRunner().thenFailsToStart("cannot start java");

        assertAll(
                () ->
                        assertEquals(
                                "the directory of the Limelight output "
                                        + elsewhere.output()
                                        + " does not exist",
                                assertThrows(
                                                IOException.class,
                                                () -> converter(none).convert(elsewhere))
                                        .getMessage()),
                () -> assertEquals(0, none.played()),
                () ->
                        assertEquals(
                                "cannot start java",
                                assertThrows(
                                                IOException.class,
                                                () -> converter(refuses).convert(request(0.01)))
                                        .getMessage()));
        nothingLooksComplete();
    }

    private static LimelightConversionRequest noRequest() {
        return Nulls.of(LimelightConversionRequest.class);
    }

    @Test
    @DisplayName("the staging path is beside the output; the constructor checks its arguments")
    void stagingAndConstruction() throws IOException {
        JavaRuntime java = JavaRuntime.ofThisApplication();
        ScriptedRunner runner = new ScriptedRunner();
        assertAll(
                () -> assertEquals(staging, LimelightConverter.stagingPath(output)),
                () -> assertEquals(".partial", LimelightConverter.STAGING_SUFFIX),
                () -> assertEquals(Duration.ofMinutes(30), LimelightConverter.DEFAULT_TIMEOUT),
                () ->
                        assertEquals(
                                "the output path " + ROOT + " names no file",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> LimelightConverter.stagingPath(ROOT))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "converterJar must be an absolute path, but was: c.jar",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new LimelightConverter(
                                                                runner,
                                                                Duration.ofSeconds(1),
                                                                java,
                                                                Path.of("c.jar")))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "java",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new LimelightConverter(
                                                                runner,
                                                                Duration.ofSeconds(1),
                                                                Nulls.of(JavaRuntime.class),
                                                                jar))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "converterJar",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new LimelightConverter(
                                                                runner,
                                                                Duration.ofSeconds(1),
                                                                java,
                                                                Nulls.of(Path.class)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "request",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> converter(runner).convert(noRequest()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "request",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> converter(runner).command(noRequest()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "output",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        LimelightConverter.stagingPath(
                                                                Nulls.of(Path.class)))
                                        .getMessage()));
    }

    @Test
    @DisplayName("the written cutoff is compared as a number; text that is no number never matches")
    void sameNumber() {
        java.math.BigDecimal asked = new java.math.BigDecimal("0.01");
        assertAll(
                () -> assertTrue(LimelightConverter.sameNumber("0.010", asked)),
                () -> assertTrue(LimelightConverter.sameNumber("1e-2", asked)),
                () -> assertFalse(LimelightConverter.sameNumber("0.011", asked)),
                () -> assertFalse(LimelightConverter.sameNumber("abc", asked)),
                () -> assertFalse(LimelightConverter.sameNumber("", asked)));
    }
}
