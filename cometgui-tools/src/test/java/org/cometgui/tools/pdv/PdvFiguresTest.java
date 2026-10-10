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

package org.cometgui.tools.pdv;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import java.util.function.Consumer;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.tools.api.JavaRuntime;
import org.cometgui.tools.testing.Nulls;
import org.cometgui.tools.testing.ScriptedRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The figure generator's rules, graded with a scripted process runner standing in for PDV: the
 * argument array, the format gate before any launch, and every named failure. The real PDV is run
 * by {@code PdvFiguresRealTest}.
 */
class PdvFiguresTest {

    private static final String ID = "scan=2";
    private static final Map<String, String> DISPLAY = Map.of("DISPLAY", ":7", "XAUTHORITY", "/x");

    /** Marks an entry of {@link #writes} that is a directory rather than a file. */
    private static final byte[] DIRECTORY = new byte[0];

    @TempDir private Path directory;

    private Path work;
    private Path source;
    private Path pepXml;
    private Path jar;
    private JavaRuntime java;

    @BeforeEach
    void inputs() throws IOException {
        work = directory.resolve("run/pdv");
        source =
                MzMlText.document()
                        .spectrum(MzMlText.ms1(0, "scan=1"))
                        .spectrum(MzMlText.ms2(1, ID, "500.25", "2", 100.5, 10.0))
                        .spectrum(MzMlText.ms2(2, "scan=3", "600.5", "3", 300.125, 30.0))
                        .write(directory, "run.mzML");
        pepXml = Files.writeString(directory.resolve("run.pep.xml"), "<msms_pipeline_analysis/>");
        jar = directory.resolve("PDV-2.7.0/PDV-2.7.0.jar");
        java = new JavaRuntime(directory.resolve("jdk/bin/java"));
    }

    private PdvFigureRequest request(Path spectra, String... ids) {
        return new PdvFigureRequest(
                java,
                jar,
                ToolVersion.parse("2.7.0"),
                pepXml,
                spectra,
                List.of(ids),
                work,
                DISPLAY);
    }

    private static PdvFigures figures(ScriptedRunner runner) {
        return new PdvFigures(runner, Duration.ofSeconds(30));
    }

    /** What PDV would do: write files into the directory after {@code -o}. */
    private static Consumer<ToolCommand> writes(Map<String, byte[]> files) {
        return command -> {
            List<String> argv = command.argv();
            Path out = Path.of(argv.get(argv.indexOf("-o") + 1));
            try {
                for (Map.Entry<String, byte[]> file : files.entrySet()) {
                    Path target = out.resolve(file.getKey());
                    if (file.getValue() == DIRECTORY) {
                        Files.createDirectory(target);
                    } else {
                        Files.write(target, file.getValue());
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    private static byte[] png() {
        return PngFigureTest.Png.of(PngFigureTest.image(60), 0).bytes();
    }

    private static byte[] text(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private int runs;

    /** Moves to a new, absent work directory, so one test can make several runs. */
    private PdvFiguresTest fresh() {
        work = directory.resolve("run" + ++runs + "/pdv");
        return this;
    }

    private PdvFigureException failure(ScriptedRunner runner, PdvFigureRequest request) {
        return assertThrows(PdvFigureException.class, () -> figures(runner).generate(request));
    }

    @Test
    @DisplayName(
            "the command is the argument array Phase 00 proved, in the work directory, with a"
                    + " constructed environment")
    void command() {
        PdvFigureRequest request = request(source, ID);
        Path mgf = work.resolve("spectra.mgf");
        Path ids = work.resolve("spectrum-ids.txt");
        Path out = work.resolve("figures");
        Path home = work.resolve("home");

        ToolCommand command = PdvFigures.command(request, mgf, ids, out, home);

        assertAll(
                () ->
                        assertEquals(
                                List.of(
                                        java.launcher().toString(),
                                        "-Duser.home=" + home,
                                        "-jar",
                                        jar.toString(),
                                        "-r",
                                        pepXml.toString(),
                                        "-rt",
                                        "2",
                                        "-s",
                                        mgf.toString(),
                                        "-st",
                                        "1",
                                        "-i",
                                        ids.toString(),
                                        "-k",
                                        "s",
                                        "-o",
                                        out.toString(),
                                        "-ft",
                                        "png"),
                                command.argv()),
                () -> assertEquals(work, command.workingDirectory()),
                () ->
                        assertEquals(
                                Map.of(
                                        "DISPLAY",
                                        ":7",
                                        "XAUTHORITY",
                                        "/x",
                                        "LANG",
                                        "C.UTF-8",
                                        "HOME",
                                        home.toString()),
                                command.environment()));
    }

    @Test
    @DisplayName("a drawn figure is found by scanning, verified, and attributed to its spectrum")
    void drawn() throws IOException {
        ScriptedRunner runner =
                new ScriptedRunner()
                        .thenWrites(
                                writes(
                                        Map.of(
                                                "PEPTIDE" + ID + ".png",
                                                png(),
                                                "error.txt",
                                                new byte[0],
                                                "notes.txt",
                                                text("x"),
                                                "dir.png",
                                                DIRECTORY)),
                                0,
                                List.of());

        List<PdvFigure> drawn = figures(runner).generate(request(source, ID));

        assertAll(
                () -> assertEquals(1, drawn.size()),
                () -> assertEquals(ID, drawn.get(0).spectrumId()),
                () -> assertEquals("PEPTIDE", drawn.get(0).peptide()),
                () ->
                        assertEquals(
                                work.resolve("figures").resolve("PEPTIDE" + ID + ".png"),
                                drawn.get(0).figure().file()),
                () -> assertEquals(60, drawn.get(0).figure().nonWhitePixels()),
                () -> assertEquals(1, runner.commands().size()),
                () ->
                        assertEquals(
                                PdvFigures.command(
                                        request(source, ID),
                                        work.resolve("spectra.mgf"),
                                        work.resolve("spectrum-ids.txt"),
                                        work.resolve("figures"),
                                        work.resolve("home")),
                                runner.commands().get(0)),
                () -> assertEquals(ID + "\n", Files.readString(work.resolve("spectrum-ids.txt"))),
                () -> assertTrue(Files.isDirectory(work.resolve("home"))),
                () ->
                        assertTrue(
                                Files.readString(work.resolve("spectra.mgf"))
                                        .startsWith("BEGIN IONS\nTITLE=scan=2\nPEPMASS=500.25\n")));
    }

    @Test
    @DisplayName(
            "several spectra: each figure goes to the "
                    + "longest id its name ends with, in request "
                    + "order")
    void several() throws IOException {
        ScriptedRunner runner =
                new ScriptedRunner()
                        .thenWrites(
                                writes(
                                        Map.of(
                                                "Bscan=3.PNG",
                                                png(),
                                                "Ascan=2.png",
                                                png(),
                                                "PEP11.png",
                                                png(),
                                                "PEP1.png",
                                                png(),
                                                "Xabc.png",
                                                png())),
                                0,
                                List.of());

        List<PdvFigure> drawn =
                figures(runner).generate(request(source, "11", "scan=3", "a:b/c", "1", ID));

        List<String> seen = new ArrayList<>();
        for (PdvFigure figure : drawn) {
            seen.add(figure.spectrumId() + "=" + figure.peptide());
        }
        assertEquals(List.of("11=PEP", "scan=3=B", "a:b/c=X", "1=PEP", "scan=2=A"), seen);
    }

    @Test
    @DisplayName(
            "PDV's file-name characters are stripped as "
                    + "PDV strips them, and ownership is by suffix")
    void owners() {
        assertAll(
                () -> assertEquals("abc", PdvFigures.fileStem("a\\b/c:*?\"<>|")),
                () -> assertEquals("11", PdvFigures.owner("PEP11.png", List.of("1", "11"))),
                () -> assertEquals("11", PdvFigures.owner("PEP11.png", List.of("11", "1"))),
                () -> assertEquals("1", PdvFigures.owner("PEP1.png", List.of("11", "1"))),
                () -> assertNull(PdvFigures.owner("PEP2.png", List.of("11", "1"))));
    }

    @Test
    @DisplayName(
            "every unsupported source format is refused before anything is launched or written")
    void formatGateBeforeLaunch() throws IOException {
        for (String name : List.of("a.mzXML", "a.ms2", "a.cms2", "a.bms2", "a.raw", "a.txt")) {
            Path file = Files.writeString(directory.resolve(name), "x");
            ScriptedRunner runner = new ScriptedRunner();

            UnsupportedSpectrumFileException thrown =
                    assertThrows(
                            UnsupportedSpectrumFileException.class,
                            () -> figures(runner).generate(request(file, ID)));

            assertAll(
                    () ->
                            assertTrue(
                                    thrown.getMessage()
                                            .startsWith(
                                                    "The spectrum file "
                                                            + file
                                                            + " cannot be visualised by PDV"
                                                            + " 2.7.0: "),
                                    thrown.getMessage()),
                    () -> assertEquals(0, runner.played(), "PDV was not launched for " + name),
                    () -> assertEquals(List.of(), runner.commands()),
                    () -> assertFalse(Files.exists(work), "nothing was written for " + name));
        }
    }

    @Test
    @DisplayName(
            "exit 0 and no figure is the named failure NO_FIGURE, quoting error.txt and the output")
    void noFigure() {
        ScriptedRunner withErrors =
                new ScriptedRunner()
                        .thenWrites(
                                writes(Map.of("error.txt", text(ID + "\t No result\n\n"))),
                                0,
                                List.of("err"));
        ScriptedRunner silent = new ScriptedRunner().thenPrints(0, List.of(), List.of("a", "b"));

        PdvFigureException listed = failure(withErrors, request(source, ID));
        PdvFigureException bare = failure(silent, fresh().request(source, ID));

        assertAll(
                () -> assertEquals(PdvFigureException.Failure.NO_FIGURE, listed.failure()),
                () ->
                        assertEquals(
                                "PDV exited 0 and wrote no figure for "
                                        + "\"scan=2\". Its error.txt says: scan=2\t No "
                                        + "result. Its last output: err",
                                listed.getMessage()),
                () ->
                        assertEquals(
                                "PDV exited 0 and wrote no figure for "
                                        + "\"scan=2\". It wrote no error.txt. Its last "
                                        + "output: a | b",
                                bare.getMessage()));
    }

    @Test
    @DisplayName("only the last twenty output lines are quoted")
    void outputIsBounded() {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            lines.add("line" + i);
        }
        PdvFigureException thrown =
                failure(new ScriptedRunner().thenPrints(0, List.of(), lines), request(source, ID));
        assertTrue(
                thrown.getMessage()
                        .endsWith("Its last output: " + String.join(" | ", lines.subList(5, 25))),
                thrown.getMessage());
    }

    @Test
    @DisplayName("a non-zero exit is EXITED_NON_ZERO, whether or not figures were drawn")
    void nonZero() {
        PdvFigureException missing =
                failure(
                        new ScriptedRunner().thenPrints(1, List.of("boom"), List.of()),
                        request(source, ID));
        PdvFigureException drawn =
                failure(
                        new ScriptedRunner()
                                .thenWrites(
                                        writes(
                                                Map.of(
                                                        "P" + ID + ".png",
                                                        png(),
                                                        "error.txt",
                                                        new byte[0])),
                                        3,
                                        List.of()),
                        fresh().request(source, ID));
        assertAll(
                () -> assertEquals(PdvFigureException.Failure.EXITED_NON_ZERO, missing.failure()),
                () ->
                        assertEquals(
                                "PDV exited with status 1 and drew no figure "
                                        + "for \"scan=2\". It wrote no error.txt. Its "
                                        + "last output: boom",
                                missing.getMessage()),
                () -> assertEquals(PdvFigureException.Failure.EXITED_NON_ZERO, drawn.failure()),
                () ->
                        assertEquals(
                                "PDV exited with status 3. Its error.txt is empty",
                                drawn.getMessage()));
    }

    @Test
    @DisplayName("a run past its timeout is cancelled and is TIMED_OUT")
    void timeout() {
        ScriptedRunner runner = new ScriptedRunner().thenNeverFinishes();
        PdvFigureException thrown =
                assertThrows(
                        PdvFigureException.class,
                        () ->
                                new PdvFigures(runner, Duration.ofMillis(50))
                                        .generate(request(source, ID)));
        assertAll(
                () -> assertEquals(PdvFigureException.Failure.TIMED_OUT, thrown.failure()),
                () ->
                        assertEquals(
                                "PDV did not finish within PT0.05S and was "
                                        + "stopped. It wrote no error.txt",
                                thrown.getMessage()),
                () -> assertFalse(runner.lastProcess().isAlive(), "the process was cancelled"));
    }

    @Test
    @DisplayName("an invalid figure, or one that answers no request, is INVALID_FIGURE")
    void invalidFigures() {
        PdvFigureException broken =
                failure(
                        new ScriptedRunner()
                                .thenWrites(
                                        writes(
                                                Map.of(
                                                        "P" + ID + ".png",
                                                        text("not a png"),
                                                        "error.txt",
                                                        new byte[0])),
                                        0,
                                        List.of()),
                        request(source, ID));
        Path brokenFigures = work.resolve("figures");
        PdvFigureException stray =
                failure(
                        new ScriptedRunner()
                                .thenWrites(
                                        writes(
                                                Map.of(
                                                        "P" + ID + ".png",
                                                        png(),
                                                        "Pscan=9.png",
                                                        png(),
                                                        "error.txt",
                                                        new byte[0])),
                                        0,
                                        List.of()),
                        fresh().request(source, ID));
        Path figures = work.resolve("figures");
        assertAll(
                () -> assertEquals(PdvFigureException.Failure.INVALID_FIGURE, broken.failure()),
                () ->
                        assertEquals(
                                "PDV wrote a figure that is not valid: "
                                        + brokenFigures.resolve("P" + ID + ".png")
                                        + " does not begin with the PNG signature. Its "
                                        + "error.txt is empty",
                                broken.getMessage()),
                () -> assertInstanceOf(InvalidFigureException.class, broken.getCause()),
                () -> assertEquals(PdvFigureException.Failure.INVALID_FIGURE, stray.failure()),
                () ->
                        assertEquals(
                                "PDV wrote the figure "
                                        + figures.resolve("Pscan=9.png")
                                        + ", whose name ends with none of the requested "
                                        + "spectrum ids. Its error.txt is empty",
                                stray.getMessage()));
    }

    @Test
    @DisplayName("every figure drawn but error.txt not empty is PDV_REPORTED_ERRORS")
    void reportedErrors() {
        PdvFigureException thrown =
                failure(
                        new ScriptedRunner()
                                .thenWrites(
                                        writes(
                                                Map.of(
                                                        "P" + ID + ".png",
                                                        png(),
                                                        "error.txt",
                                                        text("something\nelse\n"))),
                                        0,
                                        List.of()),
                        request(source, ID));
        assertAll(
                () ->
                        assertEquals(
                                PdvFigureException.Failure.PDV_REPORTED_ERRORS, thrown.failure()),
                () ->
                        assertEquals(
                                "PDV drew every figure asked for but reported "
                                        + "problems in its error.txt. Its error.txt "
                                        + "says: something | else",
                                thrown.getMessage()));
    }

    @Test
    @DisplayName("a work directory that is not empty is refused before PDV is launched")
    void workMustBeEmpty() throws IOException {
        Files.createDirectories(work);
        Files.writeString(work.resolve("old.png"), "x");
        ScriptedRunner runner = new ScriptedRunner();

        IOException thrown =
                assertThrows(
                        IOException.class, () -> figures(runner).generate(request(source, ID)));

        assertAll(
                () ->
                        assertEquals(
                                "the PDV work directory "
                                        + work
                                        + " is not empty, so the figures PDV writes "
                                        + "could not be told from files already there",
                                thrown.getMessage()),
                () -> assertEquals(0, runner.played()));
    }

    @Test
    @DisplayName("an existing, empty work directory is used")
    void emptyWorkIsUsed() throws IOException {
        Files.createDirectories(work);
        ScriptedRunner runner =
                new ScriptedRunner()
                        .thenWrites(writes(Map.of("P" + ID + ".png", png())), 0, List.of());
        assertEquals(1, figures(runner).generate(request(source, ID)).size());
    }

    @Test
    @DisplayName("a request is validated: absolute paths, distinct one-line ids, no HOME or LANG")
    void requestValidation() {
        ToolVersion v = ToolVersion.parse("2.7.0");
        Path relative = Path.of("relative.jar");
        assertAll(
                () ->
                        assertEquals(
                                "pdvJar must be absolute, but was: relative.jar",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new PdvFigureRequest(
                                                                java,
                                                                relative,
                                                                v,
                                                                pepXml,
                                                                source,
                                                                List.of(ID),
                                                                work,
                                                                DISPLAY))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "pepXml must be absolute, but was: relative.jar",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new PdvFigureRequest(
                                                                java,
                                                                jar,
                                                                v,
                                                                relative,
                                                                source,
                                                                List.of(ID),
                                                                work,
                                                                DISPLAY))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "spectrumFile must be absolute, but was: relative.jar",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new PdvFigureRequest(
                                                                java,
                                                                jar,
                                                                v,
                                                                pepXml,
                                                                relative,
                                                                List.of(ID),
                                                                work,
                                                                DISPLAY))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "workDirectory must be absolute, but was: relative.jar",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new PdvFigureRequest(
                                                                java,
                                                                jar,
                                                                v,
                                                                pepXml,
                                                                source,
                                                                List.of(ID),
                                                                relative,
                                                                DISPLAY))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "a figure request needs at least one spectrum id",
                                assertThrows(IllegalArgumentException.class, () -> request(source))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "a spectrum id must be one non-blank line, but was \" \"",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> request(source, " "))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "a spectrum id must be one non-blank line, but was \"a\nb\"",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> request(source, "a\nb"))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "a spectrum id must be one non-blank line, but was \"a\rb\"",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> request(source, "a\rb"))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "the spectrum ids \"ab\" and \"a:b\" would give PDV's figures the"
                                        + " same file name",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> request(source, "ab", "a:b"))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "a spectrum id must be one non-blank line, but was \"\nb\"",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> request(source, "\nb"))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "a spectrum id must be one non-blank line, but was \"\rb\"",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> request(source, "\rb"))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "the spectrum id \"x\" is repeated",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> request(source, "x", "y", "x"))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "the display environment must not set HOME; "
                                        + "the PDV command constructs it",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new PdvFigureRequest(
                                                                java,
                                                                jar,
                                                                v,
                                                                pepXml,
                                                                source,
                                                                List.of(ID),
                                                                work,
                                                                Map.of("HOME", "/h")))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "the display environment must not set LANG; "
                                        + "the PDV command constructs it",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new PdvFigureRequest(
                                                                java,
                                                                jar,
                                                                v,
                                                                pepXml,
                                                                source,
                                                                List.of(ID),
                                                                work,
                                                                Map.of("LANG", "C")))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "java",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new PdvFigureRequest(
                                                                Nulls.of(JavaRuntime.class),
                                                                jar,
                                                                v,
                                                                pepXml,
                                                                source,
                                                                List.of(ID),
                                                                work,
                                                                DISPLAY))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "pdvVersion",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new PdvFigureRequest(
                                                                java,
                                                                jar,
                                                                Nulls.of(ToolVersion.class),
                                                                pepXml,
                                                                source,
                                                                List.of(ID),
                                                                work,
                                                                DISPLAY))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "pdvJar",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new PdvFigureRequest(
                                                                java,
                                                                Nulls.of(Path.class),
                                                                v,
                                                                pepXml,
                                                                source,
                                                                List.of(ID),
                                                                work,
                                                                DISPLAY))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "spectrumIds",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new PdvFigureRequest(
                                                                java,
                                                                jar,
                                                                v,
                                                                pepXml,
                                                                source,
                                                                Nulls.of(List.class),
                                                                work,
                                                                DISPLAY))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "displayEnvironment",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new PdvFigureRequest(
                                                                java,
                                                                jar,
                                                                v,
                                                                pepXml,
                                                                source,
                                                                List.of(ID),
                                                                work,
                                                                Nulls.of(Map.class)))
                                        .getMessage()));
    }

    @Test
    @DisplayName("a request's collections are copies, and a figure's components are required")
    void copiesAndNulls() {
        List<String> ids = new ArrayList<>(List.of(ID));
        PdvFigureRequest request =
                new PdvFigureRequest(
                        java, jar, ToolVersion.parse("2.7.0"), pepXml, source, ids, work, DISPLAY);
        ids.add("other");
        PngFigure png = new PngFigure(directory, 1, 1, 1, Map.of());
        assertAll(
                () -> assertEquals(List.of(ID), request.spectrumIds()),
                () -> assertEquals(DISPLAY, request.displayEnvironment()),
                () ->
                        assertEquals(
                                "spectrumId",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new PdvFigure(
                                                                Nulls.of(String.class), "P", png))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "peptide",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new PdvFigure(
                                                                ID, Nulls.of(String.class), png))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "figure",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new PdvFigure(
                                                                ID, "P", Nulls.of(PngFigure.class)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "request",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        figures(new ScriptedRunner())
                                                                .generate(
                                                                        Nulls.of(
                                                                                PdvFigureRequest
                                                                                        .class)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "timeout must be positive, but was: PT0S",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new PdvFigures(
                                                                new ScriptedRunner(),
                                                                Duration.ZERO))
                                        .getMessage()));
    }
}
