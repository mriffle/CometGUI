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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.cometgui.domain.secrets.SecretRedactor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Phase 04's exit gate item 4: <b>a crash simulated mid-run leaves a parsable event log with usable
 * history</b>.
 *
 * <p><strong>The crash is performed, not argued.</strong> Every test below writes a real log with
 * the real writer and then damages the file the way a dying process damages it -- {@link
 * FileChannel#truncate} in the middle of the last record, a block of {@code NUL} bytes where a
 * filesystem left one after a power loss, a writer that is simply never closed because its JVM went
 * away. Nothing here reasons from "appends are atomic"; the file is torn and then read.
 *
 * <p><strong>And the recovered history is asserted by content.</strong> "Three events came back" is
 * satisfied by three wrong events. Each test compares the recovered list against hand-typed {@link
 * ProvenanceEvent}s -- sequence, timestamp, type and payload -- so a reader that recovered the
 * right number of records with the wrong fields would fail. The four log lines and the four file
 * offsets below were typed by hand and counted independently with {@code python3}.
 *
 * <p>The tear is applied at many offsets rather than one. A reader can be correct at the boundary
 * and wrong one byte in either side of it, so {@link #aTearAtEveryOffsetRecoversExactlyThePrefix()}
 * walks every byte position in the file and asserts the exact prefix each one must leave.
 */
class EventLogCrashRecoveryTest {

    /** The four lines the fixture run writes, hand-typed. Lengths: 91, 94, 92, 98. */
    private static final List<String> LINES =
            List.of(
                    "{\"seq\":1,\"time\":\"2026-08-31T09:15:00.000Z\",\"type\":\"run.started\","
                            + "\"payload\":{\"run.id\":\"R-1\"}}",
                    "{\"seq\":2,\"time\":\"2026-08-31T09:15:01.000Z\",\"type\":\"stage.started\","
                            + "\"payload\":{\"stage\":\"comet\"}}",
                    "{\"seq\":3,\"time\":\"2026-08-31T09:15:02.000Z\",\"type\":\"tool.invoked\","
                            + "\"payload\":{\"tool\":\"comet\"}}",
                    "{\"seq\":4,\"time\":\"2026-08-31T09:15:03.000Z\",\"type\":\"file.hashed\","
                            + "\"payload\":{\"path\":\"/data/a.mzML\"}}");

    /** Where each line ends, counted independently: 92, 187, 280, 379. */
    private static final long END_OF_LINE_ONE = 92L;

    private static final long END_OF_LINE_TWO = 187L;

    private static final long END_OF_LINE_THREE = 280L;

    private static final long END_OF_LINE_FOUR = 379L;

    /** The first three events, as they must come back from every torn variant. Hand-typed. */
    private static final List<ProvenanceEvent> FIRST_THREE =
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
                            ProvenanceEventType.TOOL_INVOKED,
                            Map.of("tool", "comet")));

    @TempDir private Path directory;

    @Test
    @DisplayName("the fixture run really does write these four lines and 379 bytes")
    void theFixtureIsWhatThisFileSaysItIs() throws IOException {
        Path log = writeTheRun();

        assertAll(
                () -> assertEquals(String.join("\n", LINES) + "\n", Files.readString(log, UTF_8)),
                () -> assertEquals(END_OF_LINE_FOUR, Files.size(log)));
    }

    @Test
    @DisplayName("a tear one byte into the last record leaves the first three events, by content")
    void aTearOneByteIntoTheLastRecord() throws IOException {
        Path log = writeTheRun();
        truncateTo(log, END_OF_LINE_THREE + 1);

        RecoveredEventLog recovered = ProvenanceEventLogReader.recover(log);

        assertAll(
                () -> assertEquals(FIRST_THREE, recovered.events()),
                () ->
                        assertEquals(
                                List.of(
                                        new EventLogDefect(
                                                EventLogDefectKind.TORN_FINAL_LINE,
                                                4L,
                                                END_OF_LINE_THREE,
                                                "the record starting here has no terminating"
                                                        + " newline; its length on disk is 1")),
                                recovered.defects()),
                () -> assertFalse(recovered.intact()),
                () -> assertEquals(3L, recovered.highestSequence()));
    }

    @Test
    @DisplayName("a tear one byte before the last newline is still every earlier event, by content")
    void aTearOneByteBeforeTheFinalNewline() throws IOException {
        Path log = writeTheRun();
        truncateTo(log, END_OF_LINE_FOUR - 1);

        RecoveredEventLog recovered = ProvenanceEventLogReader.recover(log);

        assertAll(
                () -> assertEquals(FIRST_THREE, recovered.events()),
                () ->
                        assertEquals(
                                List.of(
                                        new EventLogDefect(
                                                EventLogDefectKind.TORN_FINAL_LINE,
                                                4L,
                                                END_OF_LINE_THREE,
                                                "the record starting here has no terminating"
                                                        + " newline; its length on disk is 98")),
                                recovered.defects()),
                () -> assertEquals(3, recovered.events().size()));
    }

    @Test
    @DisplayName("a tear in the middle of the last record is the same three events again")
    void aTearInTheMiddleOfTheLastRecord() throws IOException {
        Path log = writeTheRun();
        truncateTo(log, END_OF_LINE_THREE + 47);

        RecoveredEventLog recovered = ProvenanceEventLogReader.recover(log);

        assertAll(
                () -> assertEquals(FIRST_THREE, recovered.events()),
                () -> assertEquals(1, recovered.defects().size()),
                () ->
                        assertEquals(
                                EventLogDefectKind.TORN_FINAL_LINE,
                                recovered.defects().get(0).kind()),
                () -> assertEquals(END_OF_LINE_THREE, recovered.defects().get(0).byteOffset()));
    }

    @Test
    @DisplayName("a crash exactly on a record boundary loses nothing and reports nothing")
    void aTearOnARecordBoundaryIsNotDamage() throws IOException {
        Path log = writeTheRun();
        truncateTo(log, END_OF_LINE_THREE);

        RecoveredEventLog recovered = ProvenanceEventLogReader.recover(log);

        assertAll(
                () -> assertEquals(FIRST_THREE, recovered.events()),
                () -> assertEquals(List.of(), recovered.defects()),
                () -> assertTrue(recovered.intact()));
    }

    @Test
    @DisplayName("every byte offset in the file recovers exactly the records complete before it")
    void aTearAtEveryOffsetRecoversExactlyThePrefix() throws IOException {
        Path original = writeTheRun();
        byte[] whole = Files.readAllBytes(original);
        List<Long> boundaries =
                List.of(END_OF_LINE_ONE, END_OF_LINE_TWO, END_OF_LINE_THREE, END_OF_LINE_FOUR);

        for (int size = 0; size <= whole.length; size++) {
            Path torn = directory.resolve("torn-" + size + ".log");
            Files.write(torn, Arrays.copyOf(whole, size));

            RecoveredEventLog recovered = ProvenanceEventLogReader.recover(torn);

            int completeRecords = 0;
            for (long boundary : boundaries) {
                if (boundary <= size) {
                    completeRecords++;
                }
            }
            List<Long> expectedSequences = new ArrayList<>();
            for (long sequence = 1; sequence <= completeRecords; sequence++) {
                expectedSequences.add(sequence);
            }
            boolean tornTail = size > 0 && !boundaries.contains((long) size);

            int expectedDefects = tornTail ? 1 : 0;
            assertEquals(
                    expectedSequences,
                    sequencesOf(recovered),
                    "a log truncated to " + size + " bytes recovered the wrong events");
            assertEquals(
                    expectedDefects,
                    recovered.defects().size(),
                    "a log truncated to " + size + " bytes reported the wrong damage");
            if (tornTail) {
                assertEquals(
                        EventLogDefectKind.TORN_FINAL_LINE,
                        recovered.defects().get(0).kind(),
                        "a log truncated to " + size + " bytes misnamed its damage");
            }
        }
    }

    @Test
    @DisplayName("trailing NUL bytes with no newline are a torn tail, not a record")
    void trailingNulBytesWithoutANewline() throws IOException {
        Path log = writeTheRun();
        truncateTo(log, END_OF_LINE_THREE);
        appendBytes(log, new byte[4096]);

        RecoveredEventLog recovered = ProvenanceEventLogReader.recover(log);

        assertAll(
                () -> assertEquals(FIRST_THREE, recovered.events()),
                () ->
                        assertEquals(
                                List.of(
                                        new EventLogDefect(
                                                EventLogDefectKind.TORN_FINAL_LINE,
                                                4L,
                                                END_OF_LINE_THREE,
                                                "the record starting here has no terminating"
                                                        + " newline; its length on disk is 4096")),
                                recovered.defects()));
    }

    @Test
    @DisplayName("a block of NUL bytes that does end in a newline is a malformed line")
    void trailingNulBytesTerminatedByANewline() throws IOException {
        Path log = writeTheRun();
        truncateTo(log, END_OF_LINE_THREE);
        byte[] zeroesThenNewline = new byte[512];
        zeroesThenNewline[511] = '\n';
        appendBytes(log, zeroesThenNewline);

        RecoveredEventLog recovered = ProvenanceEventLogReader.recover(log);

        assertAll(
                () -> assertEquals(FIRST_THREE, recovered.events()),
                () ->
                        assertEquals(
                                List.of(
                                        new EventLogDefect(
                                                EventLogDefectKind.MALFORMED_LINE,
                                                4L,
                                                END_OF_LINE_THREE,
                                                "expected {\"seq\": at offset 0")),
                                recovered.defects()));
    }

    @Test
    @DisplayName("NUL bytes written over the tail of a complete record are damage, not content")
    void nulBytesOverwritingTheTailOfARecord() throws IOException {
        Path log = writeTheRun();
        byte[] whole = Files.readAllBytes(log);
        for (int index = 320; index < 378; index++) {
            whole[index] = 0;
        }
        Files.write(log, whole);

        RecoveredEventLog recovered = ProvenanceEventLogReader.recover(log);

        assertAll(
                () -> assertEquals(FIRST_THREE, recovered.events()),
                () -> assertEquals(1, recovered.defects().size()),
                () ->
                        assertEquals(
                                EventLogDefectKind.MALFORMED_LINE,
                                recovered.defects().get(0).kind()),
                () ->
                        assertEquals(
                                "an unescaped control character at offset 40",
                                recovered.defects().get(0).detail()));
    }

    @Test
    @DisplayName("a writer whose JVM died without closing has every record on disk already")
    void anAbandonedWriterLosesNothing() throws IOException {
        Path log = directory.resolve("events.log");
        // Deliberately not closed and deliberately not in a try-with-resources: this is what a
        // process that was killed leaves behind.  Every assertion below runs while the writer is
        // still open, which is the whole point -- nothing may depend on close() having happened.
        ProvenanceEventLog abandoned =
                ProvenanceEventLog.openAppend(log, SecretRedactor.patternsOnly(), fixtureClock());
        abandoned.append(ProvenanceEventType.RUN_STARTED, Map.of("run.id", "R-1"));
        abandoned.append(ProvenanceEventType.STAGE_STARTED, Map.of("stage", "comet"));
        abandoned.append(ProvenanceEventType.TOOL_INVOKED, Map.of("tool", "comet"));

        RecoveredEventLog recovered = ProvenanceEventLogReader.recover(log);

        try {
            assertAll(
                    () -> assertEquals(FIRST_THREE, recovered.events()),
                    () -> assertTrue(recovered.intact()),
                    () -> assertEquals(END_OF_LINE_THREE, Files.size(log)));
        } finally {
            abandoned.close();
        }
    }

    @Test
    @DisplayName("a run resumed after a crash heals the tear and keeps the numbering gap-free")
    void aResumedRunHealsTheTear() throws IOException {
        Path log = writeTheRun();
        truncateTo(log, END_OF_LINE_THREE + 30);

        try (ProvenanceEventLog resumed =
                ProvenanceEventLog.openAppend(
                        log,
                        SecretRedactor.patternsOnly(),
                        Clock.fixed(Instant.parse("2026-08-31T09:20:00Z"), ZoneOffset.UTC))) {
            assertEquals(4L, resumed.nextSequence());
            resumed.append(ProvenanceEventType.WARNING_RAISED, Map.of("message", "resumed"));
        }

        RecoveredEventLog recovered = ProvenanceEventLogReader.recover(log);
        assertAll(
                // The three events from before the crash, plus the one written after it, and the
                // number 4 is used exactly once even though the crash happened while writing it.
                () -> assertEquals(List.of(1L, 2L, 3L, 4L), sequencesOf(recovered)),
                () ->
                        assertEquals(
                                new ProvenanceEvent(
                                        4L,
                                        Instant.parse("2026-08-31T09:20:00.000Z"),
                                        ProvenanceEventType.WARNING_RAISED,
                                        Map.of("message", "resumed")),
                                recovered.events().get(3)),
                // The healed remnant is one malformed line and nothing else: the damage from the
                // old crash did not swallow the record written after it.
                () -> assertEquals(1, recovered.defects().size()),
                () ->
                        assertEquals(
                                EventLogDefectKind.MALFORMED_LINE,
                                recovered.defects().get(0).kind()),
                () -> assertEquals(END_OF_LINE_THREE, recovered.defects().get(0).byteOffset()),
                () ->
                        assertTrue(
                                Files.readString(log, UTF_8)
                                        .startsWith(String.join("\n", LINES.subList(0, 3)) + "\n"),
                                "healing a tear rewrote the records before it"));
    }

    @Test
    @DisplayName("the events recovered from a torn log are equal to those the writer returned")
    void whatTheWriterReturnedIsWhatSurvives() throws IOException {
        Path log = directory.resolve("events.log");
        List<ProvenanceEvent> asWritten = new ArrayList<>();
        try (ProvenanceEventLog events =
                ProvenanceEventLog.openAppend(log, SecretRedactor.patternsOnly(), fixtureClock())) {
            asWritten.add(events.append(ProvenanceEventType.RUN_STARTED, Map.of("run.id", "R-1")));
            asWritten.add(
                    events.append(ProvenanceEventType.STAGE_STARTED, Map.of("stage", "comet")));
            asWritten.add(events.append(ProvenanceEventType.TOOL_INVOKED, Map.of("tool", "comet")));
            asWritten.add(
                    events.append(ProvenanceEventType.FILE_HASHED, Map.of("path", "/data/a.mzML")));
        }
        truncateTo(log, END_OF_LINE_THREE + 5);

        RecoveredEventLog recovered = ProvenanceEventLogReader.recover(log);

        assertAll(
                () -> assertEquals(FIRST_THREE, asWritten.subList(0, 3)),
                () -> assertEquals(asWritten.subList(0, 3), recovered.events()));
    }

    // -------------------------------------------------------------------------------------
    // Fixtures.
    // -------------------------------------------------------------------------------------

    private Path writeTheRun() throws IOException {
        Path log = directory.resolve("events.log");
        try (ProvenanceEventLog events =
                ProvenanceEventLog.openAppend(log, SecretRedactor.patternsOnly(), fixtureClock())) {
            events.append(ProvenanceEventType.RUN_STARTED, Map.of("run.id", "R-1"));
            events.append(ProvenanceEventType.STAGE_STARTED, Map.of("stage", "comet"));
            events.append(ProvenanceEventType.TOOL_INVOKED, Map.of("tool", "comet"));
            events.append(ProvenanceEventType.FILE_HASHED, Map.of("path", "/data/a.mzML"));
        }
        return log;
    }

    /** Cuts the file off at a byte offset, which is what a process dying mid-write leaves. */
    private static void truncateTo(Path log, long size) throws IOException {
        try (FileChannel channel = FileChannel.open(log, StandardOpenOption.WRITE)) {
            channel.truncate(size);
        }
    }

    private static void appendBytes(Path log, byte[] bytes) throws IOException {
        try (FileChannel channel =
                FileChannel.open(log, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
        }
    }

    private static Clock fixtureClock() {
        return new StepClock(Instant.parse("2026-08-31T09:15:00Z"), Duration.ofSeconds(1));
    }

    private static List<Long> sequencesOf(RecoveredEventLog recovered) {
        List<Long> sequences = new ArrayList<>();
        for (ProvenanceEvent event : recovered.events()) {
            sequences.add(event.sequence());
        }
        return sequences;
    }

    /** A clock that advances by a fixed step every time it is read. */
    private static final class StepClock extends Clock {

        private final Duration step;

        private Instant next;

        StepClock(Instant first, Duration step) {
            this.next = first;
            this.step = step;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException("withZone");
        }

        @Override
        public Instant instant() {
            Instant now = next;
            next = next.plus(step);
            return now;
        }
    }
}
