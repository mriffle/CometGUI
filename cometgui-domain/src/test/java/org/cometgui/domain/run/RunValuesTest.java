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

import static org.cometgui.domain.run.RunFixtures.HASHES_A;
import static org.cometgui.domain.testing.TestPaths.absolute;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The run record's small value types: recorded and archived files, and the three enums. */
class RunValuesTest {

    private static final Instant MODIFIED = Instant.parse("2026-08-01T10:00:00.123456Z");

    @Test
    @DisplayName("a recorded input keeps its values and truncates its time to milliseconds")
    void recordedInput() {
        RecordedInput input = new RecordedInput(absolute("data/a.mzML"), 0, MODIFIED, HASHES_A);
        assertAll(
                () -> assertEquals(absolute("data/a.mzML"), input.path()),
                () -> assertEquals(0, input.size()),
                () -> assertEquals(Instant.parse("2026-08-01T10:00:00.123Z"), input.modified()),
                () -> assertEquals(HASHES_A, input.hashes()));
    }

    @Test
    @DisplayName("a recorded input refuses a relative or unnormalised path and a negative size")
    void recordedInputRefuses() {
        assertAll(
                () ->
                        assertEquals(
                                "an input's recorded path must be absolute and normalised: a.mzML",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new RecordedInput(
                                                                Path.of("a.mzML"),
                                                                1,
                                                                MODIFIED,
                                                                HASHES_A))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "an input's recorded path must be absolute and normalised:"
                                        + " /data/../a.mzML",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new RecordedInput(
                                                                absolute("data/../a.mzML"),
                                                                1,
                                                                MODIFIED,
                                                                HASHES_A))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "a file size cannot be negative, but was -1",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new RecordedInput(
                                                                absolute("a"),
                                                                -1,
                                                                MODIFIED,
                                                                HASHES_A))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "path",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new RecordedInput(
                                                                Nulls.of(Path.class),
                                                                1,
                                                                MODIFIED,
                                                                HASHES_A))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "modified",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new RecordedInput(
                                                                absolute("a"),
                                                                1,
                                                                Nulls.of(Instant.class),
                                                                HASHES_A))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "hashes",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new RecordedInput(
                                                                absolute("a"),
                                                                1,
                                                                MODIFIED,
                                                                Nulls.of(FileHashes.class)))
                                        .getMessage()));
    }

    @Test
    @DisplayName("an archived file keeps a relative /-separated path and its size")
    void archivedFile() {
        ArchivedFile file = new ArchivedFile("parameters/comet.params", 0, HASHES_A);
        assertAll(
                () -> assertEquals("parameters/comet.params", file.path()),
                () -> assertEquals(0, file.size()),
                () -> assertEquals(HASHES_A, file.hashes()),
                () -> assertEquals("x", new ArchivedFile("x", 1, HASHES_A).path()));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"", "/etc/passwd", "parameters/", "a\\b", "\\a", "C:x", "c:/x", ":a"})
    @DisplayName("an archived path that is absolute, Windows-shaped or empty is refused")
    void archivedFileRefusesShape(String path) {
        assertEquals(
                "an archived file's path must be relative to the run and use '/': \"" + path + "\"",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new ArchivedFile(path, 1, HASHES_A))
                        .getMessage());
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"..", "parameters/../../x", "a//b", "./a", "a/."})
    @DisplayName("an archived path with an empty, . or .. segment is refused")
    void archivedFileRefusesSegments(String path) {
        assertEquals(
                "an archived file's path must not contain an empty, '.' or '..' segment: \""
                        + path
                        + "\"",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new ArchivedFile(path, 1, HASHES_A))
                        .getMessage());
    }

    @Test
    @DisplayName("an archived file refuses a negative size and nulls")
    void archivedFileRefusesOthers() {
        assertAll(
                () ->
                        assertEquals(
                                "a file size cannot be negative, but was -3",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> new ArchivedFile("a", -3, HASHES_A))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "path",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new ArchivedFile(
                                                                Nulls.of(String.class),
                                                                1,
                                                                HASHES_A))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "hashes",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new ArchivedFile(
                                                                "a", 1, Nulls.of(FileHashes.class)))
                                        .getMessage()));
    }

    @Test
    @DisplayName("index modes: wire names, Comet build flags, and lookup")
    void indexModes() {
        assertAll(
                () -> assertEquals("none", IndexMode.NONE.wireName()),
                () -> assertEquals("fragment-ion", IndexMode.FRAGMENT_ION.wireName()),
                () -> assertEquals("peptide", IndexMode.PEPTIDE.wireName()),
                () -> assertEquals(Optional.empty(), IndexMode.NONE.buildFlag()),
                () -> assertEquals(Optional.of("-i"), IndexMode.FRAGMENT_ION.buildFlag()),
                () -> assertEquals(Optional.of("-j"), IndexMode.PEPTIDE.buildFlag()),
                () -> assertEquals(IndexMode.NONE, IndexMode.fromWireName("none")),
                () -> assertEquals(IndexMode.FRAGMENT_ION, IndexMode.fromWireName("fragment-ion")),
                () -> assertEquals(IndexMode.PEPTIDE, IndexMode.fromWireName("peptide")),
                () ->
                        assertEquals(
                                "not an index mode: \"Peptide\"",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> IndexMode.fromWireName("Peptide"))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "wireName",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        IndexMode.fromWireName(
                                                                Nulls.of(String.class)))
                                        .getMessage()));
    }

    @Test
    @DisplayName("database delivery: wire names and lookup")
    void databaseDelivery() {
        assertAll(
                () -> assertEquals("parameter-file", DatabaseDelivery.PARAMETER_FILE.wireName()),
                () -> assertEquals("command-line", DatabaseDelivery.COMMAND_LINE.wireName()),
                () ->
                        assertEquals(
                                DatabaseDelivery.PARAMETER_FILE,
                                DatabaseDelivery.fromWireName("parameter-file")),
                () ->
                        assertEquals(
                                DatabaseDelivery.COMMAND_LINE,
                                DatabaseDelivery.fromWireName("command-line")),
                () ->
                        assertEquals(
                                "not a database delivery: \"-D\"",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> DatabaseDelivery.fromWireName("-D"))
                                        .getMessage()),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () -> DatabaseDelivery.fromWireName(Nulls.of(String.class))));
    }

    @Test
    @DisplayName("attempt outcomes: wire names, terminality and lookup")
    void outcomes() {
        assertAll(
                () -> assertEquals("running", AttemptOutcome.RUNNING.wireName()),
                () -> assertEquals("succeeded", AttemptOutcome.SUCCEEDED.wireName()),
                () -> assertEquals("failed", AttemptOutcome.FAILED.wireName()),
                () -> assertEquals("cancelled", AttemptOutcome.CANCELLED.wireName()),
                () -> assertFalse(AttemptOutcome.RUNNING.isTerminal()),
                () -> assertTrue(AttemptOutcome.SUCCEEDED.isTerminal()),
                () -> assertTrue(AttemptOutcome.FAILED.isTerminal()),
                () -> assertTrue(AttemptOutcome.CANCELLED.isTerminal()),
                () -> assertEquals(AttemptOutcome.RUNNING, AttemptOutcome.fromWireName("running")),
                () ->
                        assertEquals(
                                AttemptOutcome.CANCELLED, AttemptOutcome.fromWireName("cancelled")),
                () ->
                        assertEquals(
                                "not an attempt outcome: \"canceled\"",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> AttemptOutcome.fromWireName("canceled"))
                                        .getMessage()),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () -> AttemptOutcome.fromWireName(Nulls.of(String.class))));
    }

    @Test
    @DisplayName("a spectrum input keeps its values, names its stage, and refuses bad members")
    void spectrumInput() {
        RecordedInput file = new RecordedInput(absolute("d/a.mzML"), 1, MODIFIED, HASHES_A);
        SpectrumInput input = new SpectrumInput(3, file, "a");
        assertAll(
                () -> assertEquals(3, input.position()),
                () -> assertEquals(file, input.file()),
                () -> assertEquals("a", input.base()),
                () -> assertEquals("comet-03", input.stageId()),
                () ->
                        assertEquals(
                                "a position is 1-based, but was 0",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> new SpectrumInput(0, file, "a"))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "file",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new SpectrumInput(
                                                                1,
                                                                Nulls.of(RecordedInput.class),
                                                                "a"))
                                        .getMessage()),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> new SpectrumInput(1, file, "..")));
    }
}
