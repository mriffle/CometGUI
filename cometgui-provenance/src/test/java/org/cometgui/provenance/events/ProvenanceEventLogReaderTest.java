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

package org.cometgui.provenance.events;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link ProvenanceEventLogReader}, against files this test wrote by hand.
 *
 * <p><strong>Not one log in this file was produced by {@link ProvenanceEventLog}.</strong> Every
 * one is a string typed out here and written straight to disk, and every recovered event is
 * asserted against hand-typed components. A reader tested only against its own writer's output
 * proves that the two agree; it proves nothing about whether either of them is right, and it cannot
 * be given the doctored files -- a repeated sequence number, a line of invalid UTF-8 -- that a
 * writer will not produce and a damaged disk will.
 *
 * <p>The byte offsets in the expected defects were counted independently, from the lengths of the
 * literals above them.
 */
class ProvenanceEventLogReaderTest {

    private static final String LINE_ONE =
            "{\"seq\":1,\"time\":\"2026-08-31T09:15:00.000Z\",\"type\":\"run.started\","
                    + "\"payload\":{\"run.id\":\"R-1\"}}";

    private static final String LINE_TWO =
            "{\"seq\":2,\"time\":\"2026-08-31T09:15:01.000Z\",\"type\":\"stage.started\","
                    + "\"payload\":{\"stage\":\"comet\"}}";

    private static final String LINE_THREE =
            "{\"seq\":3,\"time\":\"2026-08-31T09:15:02.000Z\",\"type\":\"stage.finished\","
                    + "\"payload\":{\"stage\":\"comet\",\"status\":\"completed\"}}";

    /** {@code LINE_ONE} is 91 characters; with its newline the next line starts at 92. */
    private static final long LINE_TWO_STARTS_AT = 92L;

    @TempDir private Path directory;

    @Test
    @DisplayName("a clean log yields exactly these three events and reports nothing wrong")
    void aCleanLogRecoversEverything() throws IOException {
        Path log = write(LINE_ONE, LINE_TWO, LINE_THREE);

        RecoveredEventLog recovered = ProvenanceEventLogReader.recover(log);

        assertAll(
                () ->
                        assertEquals(
                                List.of(
                                        new ProvenanceEvent(
                                                1L,
                                                Instant.parse("2026-08-31T09:15:00.000Z"),
                                                ProvenanceEventType.RUN_STARTED,
                                                Map.of("run.id", "R-1")),
                                        new ProvenanceEvent(
                                                2L,
                                                Instant.parse("2026-08-31T09:15:01.000Z"),
                                                ProvenanceEventType.STAGE_STARTED,
                                                Map.of("stage", "comet")),
                                        new ProvenanceEvent(
                                                3L,
                                                Instant.parse("2026-08-31T09:15:02.000Z"),
                                                ProvenanceEventType.STAGE_FINISHED,
                                                Map.of("stage", "comet", "status", "completed"))),
                                recovered.events()),
                () -> assertEquals(List.of(), recovered.defects()),
                () -> assertTrue(recovered.intact()),
                () -> assertEquals(3L, recovered.highestSequence()));
    }

    @Test
    @DisplayName("an empty log is intact: a run can die before its first append")
    void anEmptyLogIsIntact() throws IOException {
        Path log = directory.resolve("events.log");
        Files.write(log, new byte[0]);

        RecoveredEventLog recovered = ProvenanceEventLogReader.recover(log);

        assertAll(
                () -> assertEquals(List.of(), recovered.events()),
                () -> assertTrue(recovered.intact()),
                () -> assertEquals(0L, recovered.highestSequence()),
                () ->
                        assertEquals(
                                "RecoveredEventLog[events=0, highestSequence=0, defects=0]",
                                recovered.toString()));
    }

    @Test
    @DisplayName("a missing file is an IOException: that is not damage, it is absence")
    void aMissingLogThrows() {
        assertThrows(
                NoSuchFileException.class,
                () -> ProvenanceEventLogReader.recover(directory.resolve("nothing.log")));
    }

    @Test
    @DisplayName("an empty line is reported, and the records around it survive")
    void anEmptyLineIsReported() throws IOException {
        Path log = write(LINE_ONE, "", LINE_TWO);

        RecoveredEventLog recovered = ProvenanceEventLogReader.recover(log);

        assertAll(
                () -> assertEquals(List.of(1L, 2L), sequencesOf(recovered)),
                () ->
                        assertEquals(
                                List.of(
                                        new EventLogDefect(
                                                EventLogDefectKind.MALFORMED_LINE,
                                                2L,
                                                LINE_TWO_STARTS_AT,
                                                "the line is empty")),
                                recovered.defects()));
    }

    @Test
    @DisplayName("a line that is not valid UTF-8 is damage, not a string full of replacements")
    void invalidUtf8IsDamage() throws IOException {
        Path log = directory.resolve("events.log");
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.write(LINE_ONE.getBytes(UTF_8));
        file.write('\n');
        // A lone continuation byte: valid in no UTF-8 sequence, and silently replaced by
        // U+FFFD if the line were decoded with String(byte[], UTF_8).
        file.write(0x7b);
        file.write(0xbf);
        file.write('\n');
        file.write(LINE_TWO.getBytes(UTF_8));
        file.write('\n');
        Files.write(log, file.toByteArray());

        RecoveredEventLog recovered = ProvenanceEventLogReader.recover(log);

        assertAll(
                () -> assertEquals(List.of(1L, 2L), sequencesOf(recovered)),
                () ->
                        assertEquals(
                                List.of(
                                        new EventLogDefect(
                                                EventLogDefectKind.MALFORMED_LINE,
                                                2L,
                                                LINE_TWO_STARTS_AT,
                                                "the line is not valid UTF-8")),
                                recovered.defects()));
    }

    @Test
    @DisplayName("a missing record leaves no trace in the bytes, so the gap is what reports it")
    void aSequenceGapIsReported() throws IOException {
        String lineFive =
                "{\"seq\":5,\"time\":\"2026-08-31T09:15:04.000Z\",\"type\":\"file.hashed\","
                        + "\"payload\":{\"path\":\"/data/a.mzML\"}}";
        Path log = write(LINE_ONE, LINE_TWO, lineFive);

        RecoveredEventLog recovered = ProvenanceEventLogReader.recover(log);

        assertAll(
                () -> assertEquals(List.of(1L, 2L, 5L), sequencesOf(recovered)),
                () -> assertEquals(1, recovered.defects().size()),
                () ->
                        assertEquals(
                                new EventLogDefect(
                                        EventLogDefectKind.SEQUENCE_GAP,
                                        3L,
                                        187L,
                                        "expected sequence 3, but this line carries 5"),
                                recovered.defects().get(0)),
                () -> assertEquals(5L, recovered.highestSequence()),
                () -> assertFalse(recovered.intact()));
    }

    @Test
    @DisplayName("one hole produces one defect, not one for every line after it")
    void oneGapIsReportedOnce() throws IOException {
        String lineFive =
                "{\"seq\":5,\"time\":\"2026-08-31T09:15:04.000Z\",\"type\":\"file.hashed\","
                        + "\"payload\":{\"path\":\"/data/a.mzML\"}}";
        String lineSix =
                "{\"seq\":6,\"time\":\"2026-08-31T09:15:05.000Z\",\"type\":\"file.hashed\","
                        + "\"payload\":{\"path\":\"/data/b.mzML\"}}";
        Path log = write(LINE_ONE, LINE_TWO, lineFive, lineSix);

        RecoveredEventLog recovered = ProvenanceEventLogReader.recover(log);

        assertAll(
                () -> assertEquals(List.of(1L, 2L, 5L, 6L), sequencesOf(recovered)),
                () -> assertEquals(1, recovered.defects().size()),
                () ->
                        assertEquals(
                                EventLogDefectKind.SEQUENCE_GAP,
                                recovered.defects().get(0).kind()));
    }

    @Test
    @DisplayName("a repeated sequence number is a gap too: the log has two events in one position")
    void aRepeatedSequenceIsReported() throws IOException {
        Path log = write(LINE_ONE, LINE_TWO, LINE_TWO);

        RecoveredEventLog recovered = ProvenanceEventLogReader.recover(log);

        assertAll(
                () -> assertEquals(List.of(1L, 2L, 2L), sequencesOf(recovered)),
                () ->
                        assertEquals(
                                List.of(
                                        new EventLogDefect(
                                                EventLogDefectKind.SEQUENCE_GAP,
                                                3L,
                                                187L,
                                                "expected sequence 3, but this line carries 2")),
                                recovered.defects()));
    }

    @Test
    @DisplayName("a log whose first record is not number one is reported at the first line")
    void aLogThatDoesNotStartAtOneIsReported() throws IOException {
        Path log = write(LINE_TWO);

        RecoveredEventLog recovered = ProvenanceEventLogReader.recover(log);

        assertEquals(
                List.of(
                        new EventLogDefect(
                                EventLogDefectKind.SEQUENCE_GAP,
                                1L,
                                0L,
                                "expected sequence 1, but this line carries 2")),
                recovered.defects());
    }

    @Test
    @DisplayName("a malformed line carries the parser's own complaint, with its offset")
    void aMalformedLineCarriesTheParsersComplaint() throws IOException {
        Path log = write(LINE_ONE, "{\"seq\":2,\"time\":\"nonsense\"}", LINE_THREE);

        RecoveredEventLog recovered = ProvenanceEventLogReader.recover(log);

        assertAll(
                () -> assertEquals(List.of(1L, 3L), sequencesOf(recovered)),
                () -> assertEquals(2, recovered.defects().size()),
                () ->
                        assertEquals(
                                new EventLogDefect(
                                        EventLogDefectKind.MALFORMED_LINE,
                                        2L,
                                        LINE_TWO_STARTS_AT,
                                        "expected ,\"type\": at offset 26"),
                                recovered.defects().get(0)),
                // The lost record is reported twice over, and both reports are true: the line was
                // not a record, and a record is missing from the history.
                () ->
                        assertEquals(
                                EventLogDefectKind.SEQUENCE_GAP,
                                recovered.defects().get(1).kind()));
    }

    @Test
    @DisplayName("a file of pure rubbish is recovered as nothing at all, without throwing")
    void rubbishNeverThrows() throws IOException {
        Path log = write("not json", "{}", "{\"seq\":\"one\"}", "<?xml version=\"1.0\"?>");

        RecoveredEventLog recovered = ProvenanceEventLogReader.recover(log);

        assertAll(
                () -> assertEquals(List.of(), recovered.events()),
                () -> assertEquals(4, recovered.defects().size()),
                () ->
                        assertEquals(
                                List.of(1L, 2L, 3L, 4L),
                                recovered.defects().stream()
                                        .map(EventLogDefect::lineNumber)
                                        .toList()));
    }

    @Test
    @DisplayName("a null path is rejected by name")
    void nullPathIsRejected() {
        assertEquals(
                "path",
                assertThrows(
                                NullPointerException.class,
                                () -> ProvenanceEventLogReader.recover(deliberateNull()))
                        .getMessage());
    }

    @Test
    @DisplayName("the reader holds no state and refuses to be instantiated, even by reflection")
    void utilityClassIsNotInstantiable() throws ReflectiveOperationException {
        Constructor<ProvenanceEventLogReader> constructor =
                ProvenanceEventLogReader.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        InvocationTargetException wrapper =
                assertThrows(InvocationTargetException.class, constructor::newInstance);

        assertEquals(
                "ProvenanceEventLogReader is a utility class and is never instantiated",
                assertInstanceOf(AssertionError.class, wrapper.getCause()).getMessage());
    }

    private Path write(String... lines) throws IOException {
        Path log = directory.resolve("events.log");
        StringBuilder content = new StringBuilder();
        for (String line : lines) {
            content.append(line).append('\n');
        }
        Files.write(log, content.toString().getBytes(UTF_8));
        return log;
    }

    private static List<Long> sequencesOf(RecoveredEventLog recovered) {
        List<Long> sequences = new ArrayList<>();
        for (ProvenanceEvent event : recovered.events()) {
            sequences.add(event.sequence());
        }
        return sequences;
    }

    /**
     * A {@code null} that SpotBugs cannot see; see {@code ProvenanceEventTypeTest} for why.
     *
     * @param <T> whatever the call site needs
     * @return {@code null}
     */
    private static <T> T deliberateNull() {
        return null;
    }
}
