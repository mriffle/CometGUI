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

package org.cometgui.domain.run;

import static org.cometgui.domain.testing.TestPaths.absolute;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.cometgui.domain.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Design decision P8-4's {@code -N} base names. Every expectation is typed from the rule in {@link
 * OutputBaseNames}'s documentation, not captured from the code.
 */
class OutputBaseNamesTest {

    private static List<String> bases(String... fileNames) {
        List<Path> paths = new ArrayList<>();
        for (String name : fileNames) {
            paths.add(absolute("data/in").resolve(name));
        }
        return OutputBaseNames.derive(paths).stream().map(OutputBase::base).toList();
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource(
            delimiter = '|',
            value = {
                "s.mzML|s",
                "s.MZML|s",
                "s.mzml|s",
                "s.mzXML|s",
                "s.MzXmL|s",
                "S.MGF|S",
                "s.mgf|s",
                "s.ms2|s",
                "s.MS2|s",
                "s.cms2|s",
                "s.CMS2|s",
                "s.bms2|s",
                "s.Bms2|s",
                "s.raw|s",
                "s.RAW|s",
                "notes.txt|notes.txt",
                "a.mzML.gz|a.mzML.gz",
                "a.mgf.mzML|a.mgf",
                "mzML|mzML",
                "k562_3.mzML|k562_3"
            })
    @DisplayName("a spectrum extension is stripped case-insensitively, and only that")
    void strips(String fileName, String base) {
        assertEquals(List.of(base), bases(fileName));
    }

    @Test
    @DisplayName("the brief's collision: a.mzML, A.mzXML, a.mgf -> a, A_2, a_3")
    void collisionsInInputOrder() {
        assertEquals(List.of("a", "A_2", "a_3"), bases("a.mzML", "A.mzXML", "a.mgf"));
    }

    @Test
    @DisplayName("a file genuinely named a_2 keeps its name; the collider skips to _3")
    void naturalNamesAreReserved() {
        assertAll(
                () ->
                        assertEquals(
                                List.of("a", "A_3", "a_2"), bases("a.mzML", "A.mgf", "a_2.mzML")),
                () -> assertEquals(List.of("a", "a_3", "a_2"), bases("a.mzML", "a.mgf", "a_2.ms2")),
                () ->
                        assertEquals(
                                List.of("a", "a_2", "a_3", "a_4"),
                                bases("a.mzML", "a.mgf", "a.ms2", "a.raw")));
    }

    @Test
    @DisplayName("an assigned suffix name is not handed out twice")
    void assignedNamesAreTaken() {
        // The second file cannot have a_2 (a natural name), so takes a_3; the third keeps its own
        // a_2; the fourth's A_2 then collides with it and becomes A_2_2 -- never a second a_2.
        assertEquals(
                List.of("a", "a_3", "a_2", "A_2_2"),
                bases("a.mzML", "a.mgf", "a_2.mzML", "A_2.mgf"));
    }

    @Test
    @DisplayName("the same name in two directories collides")
    void sameNameTwoDirectories() {
        List<OutputBase> derived =
                OutputBaseNames.derive(List.of(absolute("x/a.mzML"), absolute("y/a.mzML")));
        assertAll(
                () -> assertEquals("a", derived.get(0).base()),
                () -> assertEquals("a_2", derived.get(1).base()),
                () -> assertEquals(absolute("y/a.mzML"), derived.get(1).input()),
                () -> assertEquals(2, derived.get(1).position()),
                () -> assertEquals("comet-02", derived.get(1).stageId()));
    }

    @Test
    @DisplayName("names with spaces and non-ASCII are kept; case folds beyond ASCII")
    void spacesAndNonAscii() {
        // Over names, not paths: a non-ASCII Path cannot be made in a JVM whose file-name
        // encoding is not UTF-8 (Phase 04's sun.jnu.encoding finding), and the rule is the same.
        assertAll(
                () -> assertEquals(List.of("my sample 1"), bases("my sample 1.mzML")),
                () ->
                        assertEquals(
                                List.of("my sample 1", "\u00e9chantillon", "\u00c9CHANTILLON_2"),
                                OutputBaseNames.baseNames(
                                        List.of(
                                                "my sample 1.mzML",
                                                "\u00e9chantillon.mzML",
                                                "\u00c9CHANTILLON.mgf"))),
                () ->
                        assertEquals(
                                List.of("\u00b5-probe"),
                                OutputBaseNames.baseNames(List.of("\u00b5-probe.mzXML"))));
    }

    @Test
    @DisplayName("a composed and a decomposed e-acute are one name")
    void unicodeNormalisation() {
        assertEquals(
                List.of("\u00e9", "e\u0301_2"),
                OutputBaseNames.baseNames(List.of("\u00e9.mzML", "e\u0301.mzML")));
    }

    @Test
    @DisplayName("baseNames refuses an empty list, a null list and a null element by name")
    void baseNamesRefuses() {
        assertAll(
                () ->
                        assertEquals(
                                "a run needs at least one spectrum file",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> OutputBaseNames.baseNames(List.of()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "fileNames",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        OutputBaseNames.baseNames(
                                                                Nulls.of(List.class)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "fileNames[0]",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        OutputBaseNames.baseNames(
                                                                Arrays.asList(null, "a.mzML")))
                                        .getMessage()));
    }

    @Test
    @DisplayName("positions are 1-based in input order")
    void positions() {
        List<OutputBase> derived =
                OutputBaseNames.derive(
                        List.of(absolute("i/b.mzML"), absolute("i/a.mzML"), absolute("i/c.mzML")));
        assertEquals(
                List.of(
                        new OutputBase(1, absolute("i/b.mzML"), "b"),
                        new OutputBase(2, absolute("i/a.mzML"), "a"),
                        new OutputBase(3, absolute("i/c.mzML"), "c")),
                derived);
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @CsvSource(
            delimiter = '|',
            quoteCharacter = '\'',
            value = {
                "..mzML|would be \".\", which names a directory, not a file in outputs/comet",
                "...mzML|would be \"..\", which names a directory, not a file in outputs/comet",
                ".mzML|would be empty",
                ".MGF|would be empty",
                "a\\b.mzML|would contain a path separator, and so escape outputs/comet"
            })
    @DisplayName(
            "a name whose base would escape outputs/comet is refused, naming file and position")
    void escapes(String fileName, String problem) {
        IllegalArgumentException thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                OutputBaseNames.derive(
                                        List.of(
                                                absolute("in/ok.mzML"),
                                                absolute("in").resolve(fileName))));
        assertEquals(
                "spectrum file 2 (\""
                        + fileName
                        + "\") cannot name a Comet output: its base name "
                        + problem,
                thrown.getMessage());
    }

    @Test
    @DisplayName("a path whose last element is .. is refused")
    void dotDotPath() {
        assertEquals(
                "spectrum file 1 (\"..\") cannot name a Comet output: its base name would be"
                        + " \"..\", which names a directory, not a file in outputs/comet",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> OutputBaseNames.derive(List.of(absolute("in/.."))))
                        .getMessage());
    }

    @Test
    @DisplayName("a control character in a name is refused")
    void controlCharacter() {
        assertEquals(
                "spectrum file 1 (\"a\tb.mzML\") cannot name a Comet output: its base name would"
                        + " contain a control character",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> OutputBaseNames.derive(List.of(absolute("in/a\tb.mzML"))))
                        .getMessage());
    }

    @Test
    @DisplayName("a root has no file name and is refused")
    void root() {
        assertEquals(
                "spectrum file 1 (/) has no file name",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> OutputBaseNames.derive(List.of(absolute(""))))
                        .getMessage());
    }

    @Test
    @DisplayName("an empty list, a null list and a null element are refused")
    void refusesEmptyAndNull() {
        assertAll(
                () ->
                        assertEquals(
                                "a run needs at least one spectrum file",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> OutputBaseNames.derive(List.of()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "spectrumFiles",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> OutputBaseNames.derive(Nulls.of(List.class)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "spectrumFiles[1]",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        OutputBaseNames.derive(
                                                                Arrays.asList(
                                                                        absolute("a.mzML"), null)))
                                        .getMessage()));
    }

    @Test
    @DisplayName("requireSafe accepts a plain base and refuses each unsafe kind")
    void requireSafe() {
        assertAll(
                () -> assertEquals("k562 3", OutputBaseNames.requireSafe("k562 3")),
                () ->
                        assertEquals(
                                "\"\" cannot be an output base name: would be empty",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> OutputBaseNames.requireSafe(""))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "\"a/b\" cannot be an output base name: would contain a path"
                                        + " separator, and so escape outputs/comet",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> OutputBaseNames.requireSafe("a/b"))
                                        .getMessage()),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> OutputBaseNames.requireSafe(".")),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> OutputBaseNames.requireSafe("x\u0000")),
                () ->
                        assertEquals(
                                "base",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        OutputBaseNames.requireSafe(
                                                                Nulls.of(String.class)))
                                        .getMessage()));
    }

    @Test
    @DisplayName("an OutputBase refuses position 0, a null input and an unsafe base")
    void outputBaseRefuses() {
        assertAll(
                () ->
                        assertEquals(
                                "a position is 1-based, but was 0",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> new OutputBase(0, absolute("a"), "a"))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "input",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> new OutputBase(1, Nulls.of(Path.class), "a"))
                                        .getMessage()),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> new OutputBase(1, absolute("a"), "..")));
    }

    @Test
    @DisplayName("the extension list is P8-4's, in that order")
    void extensions() {
        assertEquals(
                List.of(".mzML", ".mzXML", ".mgf", ".ms2", ".cms2", ".bms2", ".raw"),
                OutputBaseNames.SPECTRUM_EXTENSIONS);
    }
}
