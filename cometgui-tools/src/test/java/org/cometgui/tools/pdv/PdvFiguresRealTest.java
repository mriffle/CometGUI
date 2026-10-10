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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.tools.api.JavaRuntime;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.tools.testing.K562Run;
import org.cometgui.tools.testing.ProjectXvfb;
import org.cometgui.tools.testing.RealPdv;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * Gate item 2: the real PDV 2.7.0 command line, on the real Comet pepXML and the real spectrum file
 * Comet searched, through the product path -- format check, MGF re-encoding, the process service --
 * under the project-local Xvfb, judged by the decoded figure.
 *
 * <p>The spectrum file is the K562 {@code _3} subset mzML, on which Phase 00 showed PDV's own mzML
 * route exits 0 writing nothing: 1-based position and scan number differ for every spectrum. The
 * PSM is Phase 00's: scan 11367, {@code FNLTYVSHDGDDK}, Percolator's top target PSM.
 *
 * <p>Two PDV runs only, because PIT re-runs this class for mutants in the classes it covers;
 * everything that can be graded without PDV is graded in {@code PdvFiguresTest}. Everything this
 * writes -- the MGF, the figures -- is K562-derived ({@code D-006}) and is written under this
 * module's build directory and deleted afterwards.
 */
@EnabledOnOs(OS.LINUX)
class PdvFiguresRealTest {

    private static final String KNOWN = "controllerType=0 controllerNumber=1 scan=11367";
    private static final String ABSENT = "controllerType=0 controllerNumber=1 scan=99999999";

    /** PDV's two ion-series colours, set in {@code PDVCLIMainClass.displayResults}. */
    private static final int ORANGE = 0xFF6600;

    private static final int GREEN = 0x009900;

    private static ProjectXvfb display;
    private static Path build;
    private static Path displayDirectory;
    private Path work;

    @BeforeAll
    static void startDisplay() throws IOException {
        build = Path.of(System.getProperty("cometgui.buildDirectory", "target")).toAbsolutePath();
        Files.createDirectories(build);
        displayDirectory = Files.createTempDirectory(build, "xvfb-");
        display = ProjectXvfb.start(displayDirectory);
    }

    @AfterAll
    static void stopDisplay() throws IOException {
        if (display != null) {
            display.close();
        }
        deleteTree(displayDirectory);
    }

    @BeforeEach
    void workDirectory() throws IOException {
        work = Files.createTempDirectory(build, "pdv-figures-").resolve("pdv");
    }

    @AfterEach
    void deleteDerivedData() throws IOException {
        deleteTree(work.getParent());
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static PdvFigureRequest request(Path work, String id) throws IOException {
        return new PdvFigureRequest(
                JavaRuntime.ofThisApplication(),
                RealPdv.jar(),
                ToolVersion.parse("2.7.0"),
                K562Run.pepXml(),
                K562Run.mzml(),
                List.of(id),
                work,
                display.clientEnvironment());
    }

    private static PdvFigures figures() {
        return new PdvFigures(new ProcessService(Clock.systemUTC()), PdvFigures.DEFAULT_TIMEOUT);
    }

    @Test
    @DisplayName(
            "a known PSM on the real pepXML and subset mzML gives one valid, annotated 800 x 400"
                    + " PNG of FNLTYVSHDGDDK")
    void aKnownPsmIsDrawn() throws IOException {
        List<PdvFigure> drawn = figures().generate(request(work, KNOWN));

        assertEquals(1, drawn.size(), "one figure for one spectrum id");
        PdvFigure figure = drawn.get(0);
        PngFigure png = figure.figure();
        assertAll(
                () -> assertEquals(KNOWN, figure.spectrumId()),
                () ->
                        assertEquals(
                                "FNLTYVSHDGDDK",
                                figure.peptide(),
                                "PDV names the figure after the peptide it annotated"),
                () ->
                        assertEquals(
                                work.resolve(PdvFigures.FIGURES)
                                        .resolve("FNLTYVSHDGDDK" + KNOWN + ".png"),
                                png.file()),
                () -> assertEquals(800, png.width(), "PDV's documented default width"),
                () -> assertEquals(400, png.height(), "PDV's documented default height"),
                () ->
                        assertTrue(
                                png.nonWhitePixels() >= 5000,
                                "Phase 00's figure of this spectrum was 6918 non-white pixels;"
                                        + " this one is "
                                        + png.nonWhitePixels()),
                () ->
                        assertTrue(
                                png.pixelsOf(ORANGE) >= 300,
                                "annotated ions in PDV's orange: " + png.pixelsOf(ORANGE)),
                () ->
                        assertTrue(
                                png.pixelsOf(GREEN) >= 300,
                                "annotated ions in PDV's green: " + png.pixelsOf(GREEN)),
                () ->
                        assertEquals(
                                "",
                                Files.readString(
                                        work.resolve(PdvFigures.FIGURES)
                                                .resolve(PdvFigures.ERROR_FILE),
                                        StandardCharsets.UTF_8),
                                "PDV's error.txt is empty"),
                () ->
                        assertEquals(
                                728,
                                countTitles(work.resolve(PdvFigures.MGF)),
                                "every MS2 spectrum of the source was re-encoded"));
    }

    @Test
    @DisplayName("a spectrum id that is not in the run is the named failure NO_FIGURE, not a pass")
    void anAbsentSpectrumIsANamedFailure() throws IOException {
        PdvFigureException failure =
                assertThrows(
                        PdvFigureException.class, () -> figures().generate(request(work, ABSENT)));

        assertAll(
                () -> assertEquals(PdvFigureException.Failure.NO_FIGURE, failure.failure()),
                () ->
                        assertTrue(
                                failure.getMessage()
                                        .startsWith(
                                                "PDV exited 0 and wrote no figure for \""
                                                        + ABSENT
                                                        + "\". Its error.txt says: "
                                                        + ABSENT
                                                        + "\t No result"),
                                failure.getMessage()),
                () ->
                        assertEquals(
                                List.of(PdvFigures.ERROR_FILE),
                                fileNames(work.resolve(PdvFigures.FIGURES)),
                                "PDV wrote no figure at all"));
    }

    private static long countTitles(Path mgf) throws IOException {
        try (Stream<String> lines = Files.lines(mgf, StandardCharsets.UTF_8)) {
            return lines.filter(line -> line.startsWith("TITLE=")).count();
        }
    }

    private static List<String> fileNames(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.map(file -> String.valueOf(file.getFileName())).sorted().toList();
        }
    }
}
