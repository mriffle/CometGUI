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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.cometgui.domain.project.ProjectLayout;
import org.cometgui.domain.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Design decision P8-3's run directory, every path hand-typed. */
class RunLayoutTest {

    private static final ProjectLayout PROJECT = new ProjectLayout(absolute("data/MyProject"));

    private static final RunLayout RUN =
            RunLayout.of(PROJECT, Instant.parse("2026-08-28T23:15:00.999Z"), new RunId("run-0001"));

    @Test
    @DisplayName("the run directory is runs/<UTC yyyyMMdd'T'HHmmss'Z'>-<id>")
    void root() {
        // Built here as well as in the static field, which is initialised once per JVM.
        RunLayout built =
                RunLayout.of(
                        PROJECT, Instant.parse("2026-08-28T23:15:00.999Z"), new RunId("run-0001"));
        assertAll(
                () ->
                        assertEquals(
                                absolute("data/MyProject/runs/20260828T231500Z-run-0001"),
                                built.root()),
                () -> assertEquals(RUN, built));
    }

    @Test
    @DisplayName("the directory name is UTC, to the second, whatever the default locale")
    void directoryNameIgnoresLocale() {
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG-u-nu-arab"));
            assertEquals(
                    "20260101T000000Z-r",
                    RunLayout.directoryName(Instant.parse("2026-01-01T00:00:00Z"), new RunId("r")));
        } finally {
            Locale.setDefault(before);
        }
    }

    @Test
    @DisplayName("every file of the layout is where P8-3 puts it")
    void files() {
        String r = "data/MyProject/runs/20260828T231500Z-run-0001/";
        assertAll(
                () -> assertEquals(absolute(r + "run.json"), RUN.runFile()),
                () -> assertEquals(absolute(r + "parameters"), RUN.parametersDirectory()),
                () -> assertEquals(absolute(r + "parameters/comet.params"), RUN.cometParamsFile()),
                () -> assertEquals("parameters/comet.params", RunLayout.cometParamsRelativePath()),
                () -> assertEquals(absolute(r + "inputs"), RUN.inputsDirectory()),
                () -> assertEquals(absolute(r + "inputs/pin"), RUN.pinInputsDirectory()),
                () -> assertEquals(absolute(r + "inputs/pin/merged.pin"), RUN.mergedPinFile()),
                () -> assertEquals(absolute(r + "outputs"), RUN.outputsDirectory()),
                () -> assertEquals(absolute(r + "outputs/comet"), RUN.cometOutputDirectory()),
                () ->
                        assertEquals(
                                absolute(r + "outputs/comet/k562_3"),
                                RUN.cometOutputBase("k562_3")),
                () ->
                        assertEquals(
                                absolute(r + "outputs/comet/k562_3.pep.xml"),
                                RUN.pepXmlFile("k562_3")),
                () -> assertEquals(absolute(r + "outputs/comet/k562_3.pin"), RUN.pinFile("k562_3")),
                () -> assertEquals(absolute(r + "logs"), RUN.logsDirectory()),
                () -> assertEquals(absolute(r + "logs/comet-01.log"), RUN.cometLogFile(1)),
                () -> assertEquals(absolute(r + "logs/comet-12.log"), RUN.cometLogFile(12)),
                () -> assertEquals(absolute(r + "provenance"), RUN.provenanceDirectory()),
                () ->
                        assertEquals(
                                absolute(r + "provenance/provenance.json"),
                                RUN.provenanceJsonFile()),
                () ->
                        assertEquals(
                                absolute(r + "provenance/provenance.rst"), RUN.provenanceRstFile()),
                () -> assertEquals(absolute(r + "provenance/events.log"), RUN.eventLogFile()));
    }

    @Test
    @DisplayName("directories() lists every directory once, each after its parent")
    void directories() {
        String r = "data/MyProject/runs/20260828T231500Z-run-0001";
        assertEquals(
                List.of(
                        absolute(r),
                        absolute(r + "/parameters"),
                        absolute(r + "/inputs"),
                        absolute(r + "/inputs/pin"),
                        absolute(r + "/outputs"),
                        absolute(r + "/outputs/comet"),
                        absolute(r + "/logs"),
                        absolute(r + "/provenance")),
                RUN.directories());
    }

    @ParameterizedTest(name = "[{index}] position {0} -> {1}")
    @CsvSource({"1,comet-01", "9,comet-09", "10,comet-10", "99,comet-99", "100,comet-100"})
    @DisplayName("a Comet stage id is comet- and the position in at least two digits")
    void stageIds(int position, String expected) {
        String id = RunLayout.cometStageId(position);
        assertAll(
                () -> assertEquals(expected, id),
                () -> assertTrue(id.matches("[A-Za-z0-9_-]{1,64}"), id));
    }

    @Test
    @DisplayName("the largest position still makes a legal stage id")
    void largestPosition() {
        String id = RunLayout.cometStageId(Integer.MAX_VALUE);
        assertAll(
                () -> assertEquals("comet-2147483647", id),
                () -> assertTrue(id.matches("[A-Za-z0-9_-]{1,64}"), id));
    }

    @Test
    @DisplayName("position 0 is refused for a stage id and for a log")
    void positionZero() {
        assertAll(
                () ->
                        assertEquals(
                                "a position is 1-based, but was 0",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> RunLayout.cometStageId(0))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "a position is 1-based, but was -1",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> RUN.cometLogFile(-1))
                                        .getMessage()));
    }

    @Test
    @DisplayName("an output base that would leave outputs/comet is refused for every output path")
    void escapeRefused() {
        assertAll(
                () ->
                        assertEquals(
                                "\"..\" cannot be an output base name: would be \"..\", which"
                                        + " names a directory, not a file in outputs/comet",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> RUN.cometOutputBase(".."))
                                        .getMessage()),
                () -> assertThrows(IllegalArgumentException.class, () -> RUN.pepXmlFile("../x")),
                () -> assertThrows(IllegalArgumentException.class, () -> RUN.pinFile("a/b")),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () -> RUN.cometOutputBase(Nulls.of(String.class))));
    }

    @Test
    @DisplayName("a relative run root and nulls are refused by name")
    void refuses() {
        assertAll(
                () ->
                        assertEquals(
                                "a run directory must be absolute: runs/x",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> new RunLayout(Path.of("runs/x")))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "root",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> new RunLayout(Nulls.of(Path.class)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "project",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        RunLayout.of(
                                                                Nulls.of(ProjectLayout.class),
                                                                Instant.EPOCH,
                                                                new RunId("r")))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "created",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        RunLayout.directoryName(
                                                                Nulls.of(Instant.class),
                                                                new RunId("r")))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "runId",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        RunLayout.directoryName(
                                                                Instant.EPOCH,
                                                                Nulls.of(RunId.class)))
                                        .getMessage()));
    }

    @Test
    @DisplayName("the root is normalised")
    void normalised() {
        assertEquals(absolute("a/c"), new RunLayout(absolute("a/b/../c/.")).root());
    }

    @Test
    @DisplayName("the provenance and event log file names are the pinned constants")
    void constants() {
        assertAll(
                () -> assertEquals("provenance.json", RunLayout.PROVENANCE_JSON_FILE_NAME),
                () -> assertEquals("provenance.rst", RunLayout.PROVENANCE_RST_FILE_NAME),
                () -> assertEquals("events.log", RunLayout.EVENT_LOG_FILE_NAME),
                () -> assertEquals("run.json", RunLayout.RUN_FILE_NAME));
    }
}
