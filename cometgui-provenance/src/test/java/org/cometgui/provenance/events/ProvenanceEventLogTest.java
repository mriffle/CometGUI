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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.provenance.manifest.ProvenanceStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link ProvenanceEventLog}.
 *
 * <p><strong>Where the expected values came from.</strong> The seven log lines below were typed out
 * by hand. The seven file sizes were computed independently on the command line -- {@code python3}
 * over the same seven strings, {@code wc -c} over the file they make -- and transcribed as
 * literals. Nothing in this file asks the log what it wrote and then asserts that it wrote it.
 *
 * <p>That rule matters most for the two properties no inspection of a finished file can see. The
 * first is that <em>every record was forced to the device before the append returned</em>: a forced
 * log and an unforced log are byte-identical and differ only when the power goes out, so the
 * assertion is made against a {@link RecordingSync} that writes down the file's length at the
 * moment of each force. Comparing that list against hand-typed cumulative sizes pins the count of
 * forces, their order relative to the writes, and the fact that the record was complete when each
 * one happened. The second is that the production sync really is {@code force(true)} and not {@code
 * force(false)} or nothing at all, which is asserted against a {@link RecordingFileChannel}.
 */
class ProvenanceEventLogTest {

    /** The run this fixture log describes, one event of every type, in order. Hand-typed. */
    private static final List<String> EXPECTED_LINES =
            List.of(
                    "{\"seq\":1,\"time\":\"2026-08-31T09:15:00.000Z\",\"type\":\"run.started\","
                            + "\"payload\":{\"run.id\":\"R-2026-08-31-01\"}}",
                    "{\"seq\":2,\"time\":\"2026-08-31T09:15:01.000Z\",\"type\":\"stage.started\","
                            + "\"payload\":{\"stage\":\"comet\"}}",
                    "{\"seq\":3,\"time\":\"2026-08-31T09:15:02.000Z\",\"type\":\"tool.invoked\","
                            + "\"payload\":{\"tool\":\"comet\","
                            + "\"tool.version\":\"2026.02.2\"}}",
                    "{\"seq\":4,\"time\":\"2026-08-31T09:15:03.000Z\",\"type\":\"file.hashed\","
                            + "\"payload\":{\"file.md5\":\"d41d8cd98f00b204e9800998ecf8427e\","
                            + "\"file.path\":\"/data/HeLa.mzML\"}}",
                    "{\"seq\":5,\"time\":\"2026-08-31T09:15:04.000Z\",\"type\":\"warning.raised\","
                            + "\"payload\":{\"message\":\"Percolator 3.09 cannot write XML\"}}",
                    "{\"seq\":6,\"time\":\"2026-08-31T09:15:05.000Z\",\"type\":\"stage.finished\","
                            + "\"payload\":{\"stage\":\"comet\",\"status\":\"completed\"}}",
                    "{\"seq\":7,\"time\":\"2026-08-31T09:15:06.000Z\",\"type\":\"run.finished\","
                            + "\"payload\":{\"status\":\"completed\"}}");

    /**
     * The file's length after each of the seven appends, counted independently.
     *
     * <p>{@code python3 -c} over the seven literals above, cross-checked with {@code wc -c} on the
     * file they make: 104, 199, 319, 472, 597, 714, 813. These are the numbers a force is expected
     * to see, which is what makes "the bytes were written before the force" an assertion rather
     * than a claim.
     */
    private static final List<Long> EXPECTED_SIZES_AT_FORCE =
            List.of(104L, 199L, 319L, 472L, 597L, 714L, 813L);

    @TempDir private Path directory;

    @Test
    @DisplayName("a run of seven events is exactly these bytes on disk")
    void theFileIsExactlyTheseBytes() throws IOException {
        Path log = directory.resolve("events.log");

        try (ProvenanceEventLog events = openAt(log, new RecordingSync())) {
            appendTheWholeRun(events);
        }

        String expected = String.join("\n", EXPECTED_LINES) + "\n";
        assertAll(
                () -> assertEquals(expected, Files.readString(log, UTF_8)),
                () -> assertArrayEquals(expected.getBytes(UTF_8), Files.readAllBytes(log)),
                () -> assertEquals(813L, Files.size(log)),
                () ->
                        assertFalse(
                                Files.readString(log, UTF_8).contains("\r"),
                                "the log carries a carriage return, so it is not \\n on every"
                                        + " platform"));
    }

    @Test
    @DisplayName("each record is on the device before its append returns, and only once")
    void everyRecordIsForcedAfterItIsWritten() throws IOException {
        Path log = directory.resolve("events.log");
        RecordingSync sync = new RecordingSync();

        try (ProvenanceEventLog events = openAt(log, sync)) {
            appendTheWholeRun(events);
        }

        assertEquals(EXPECTED_SIZES_AT_FORCE, sync.sizesAtForce());
    }

    @Test
    @DisplayName("a record is readable from the file before the next append, with nothing buffered")
    void nothingIsHeldBackInABuffer() throws IOException {
        Path log = directory.resolve("events.log");
        List<Long> sizesAfterEachAppend = new ArrayList<>();

        try (ProvenanceEventLog events = openAt(log, new RecordingSync())) {
            appendTheWholeRun(events, sizesAfterEachAppend);
        }

        assertEquals(EXPECTED_SIZES_AT_FORCE, sizesAfterEachAppend);
    }

    @Test
    @DisplayName("the production sync is force(true): data and metadata, not data alone")
    void theProductionSyncForcesMetadataToo() throws IOException {
        RecordingFileChannel channel = new RecordingFileChannel();

        EventLogSync.TO_DEVICE.force(channel);

        assertEquals(List.of("force(true)"), channel.operations());
    }

    @Test
    @DisplayName("the log assigns sequence numbers; a caller cannot choose one")
    void sequenceNumbersAreTheLogsToAssign() throws IOException {
        Path log = directory.resolve("events.log");
        List<Long> assigned = new ArrayList<>();

        try (ProvenanceEventLog events = openAt(log, new RecordingSync())) {
            assertEquals(1L, events.nextSequence());
            for (int event = 0; event < 4; event++) {
                assigned.add(
                        events.append(ProvenanceEventType.WARNING_RAISED, Map.of("i", "x"))
                                .sequence());
            }
            assertEquals(5L, events.nextSequence());
        }

        assertEquals(List.of(1L, 2L, 3L, 4L), assigned);
    }

    @Test
    @DisplayName("append returns the event as written, with the payload already redacted")
    void appendReturnsWhatTheFileSays() throws IOException {
        Path log = directory.resolve("events.log");
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("upload.token", "glpat-Z1x9QeR7sVbN3mK0pLtY");
        payload.put("endpoint", "https://limelight.example.org/api");

        ProvenanceEvent written;
        try (ProvenanceEventLog events = openAt(log, new RecordingSync())) {
            written = events.append(ProvenanceEventType.TOOL_INVOKED, payload);
        }

        assertAll(
                () -> assertEquals("[REDACTED]", written.payload().get("upload.token")),
                () ->
                        assertEquals(
                                "https://limelight.example.org/api",
                                written.payload().get("endpoint")),
                () ->
                        assertEquals(
                                "{\"seq\":1,\"time\":\"2026-08-31T09:15:00.000Z\","
                                        + "\"type\":\"tool.invoked\",\"payload\":"
                                        + "{\"endpoint\":\"https://limelight.example.org/api\","
                                        + "\"upload.token\":\"[REDACTED]\"}}\n",
                                Files.readString(log, UTF_8)));
    }

    @Test
    @DisplayName("reopening a closed log continues its numbering instead of starting again")
    void reopeningContinuesTheSequence() throws IOException {
        Path log = directory.resolve("events.log");
        try (ProvenanceEventLog events = openAt(log, new RecordingSync())) {
            events.append(ProvenanceEventType.RUN_STARTED, Map.of("run.id", "R-1"));
            events.append(ProvenanceEventType.STAGE_STARTED, Map.of("stage", "comet"));
        }

        try (ProvenanceEventLog resumed = openAt(log, new RecordingSync())) {
            assertEquals(3L, resumed.nextSequence());
            assertEquals(
                    3L,
                    resumed.append(ProvenanceEventType.STAGE_FINISHED, Map.of("stage", "comet"))
                            .sequence());
        }

        RecoveredEventLog recovered = ProvenanceEventLogReader.recover(log);
        assertAll(
                () -> assertEquals(List.of(1L, 2L, 3L), sequencesOf(recovered)),
                () -> assertTrue(recovered.intact(), "reopening left the log damaged"));
    }

    @Test
    @DisplayName("the newline that heals a torn record is itself forced to the device")
    void theHealingNewlineIsForced() throws IOException {
        Path log = directory.resolve("events.log");
        // 27 bytes with no terminator, counted with python3: what a process killed mid-append
        // leaves behind.  The heal must therefore see a file of 28 bytes.
        Files.write(log, "{\"seq\":1,\"time\":\"2026-08-31".getBytes(UTF_8));
        RecordingSync sync = new RecordingSync();

        try (ProvenanceEventLog resumed = openAt(log, sync)) {
            // Asserted before any append, so the only force that can have happened is the heal.
            // A heal that was written but not forced would leave the next process facing the same
            // merged line, and the file would look identical either way.
            assertEquals(List.of(28L), sync.sizesAtForce());
            assertEquals(1L, resumed.nextSequence());
        }

        assertEquals("{\"seq\":1,\"time\":\"2026-08-31\n", Files.readString(log, UTF_8));
    }

    @Test
    @DisplayName("an existing empty file is not a torn log and gets no leading newline")
    void anEmptyFileIsNotDamage() throws IOException {
        Path log = directory.resolve("events.log");
        Files.write(log, new byte[0]);

        try (ProvenanceEventLog events = openAt(log, new RecordingSync())) {
            assertEquals(1L, events.nextSequence());
            events.append(ProvenanceEventType.RUN_STARTED, Map.of("run.id", "R-1"));
        }

        assertEquals(
                "{\"seq\":1,\"time\":\"2026-08-31T09:15:00.000Z\",\"type\":\"run.started\","
                        + "\"payload\":{\"run.id\":\"R-1\"}}\n",
                Files.readString(log, UTF_8));
    }

    @Test
    @DisplayName("an append whose force fails spends its number and does not merge into the next")
    void aFailedForceDoesNotCorruptTheFollowingRecord() throws IOException {
        Path log = directory.resolve("events.log");
        RecordingSync sync = new RecordingSync();

        try (ProvenanceEventLog events = openAt(log, sync)) {
            events.append(ProvenanceEventType.RUN_STARTED, Map.of("run.id", "R-1"));
            sync.failNextForceWith(new IOException("the device went away"));
            IOException failed =
                    assertThrows(
                            IOException.class,
                            () ->
                                    events.append(
                                            ProvenanceEventType.STAGE_STARTED,
                                            Map.of("stage", "comet")));
            assertEquals("the device went away", failed.getMessage());
            events.append(ProvenanceEventType.STAGE_FINISHED, Map.of("stage", "comet"));
        }

        RecoveredEventLog recovered = ProvenanceEventLogReader.recover(log);
        assertAll(
                () -> assertEquals(List.of(1L, 2L, 3L), sequencesOf(recovered)),
                () ->
                        assertEquals(
                                List.of(),
                                recovered.defects(),
                                "the recovery after a failed force found damage"));
    }

    @Test
    @DisplayName("a half-written record is closed off, so the next append is not swallowed by it")
    void aPartlyWrittenRecordIsTerminatedByTheNextAppend() throws IOException {
        Path log = directory.resolve("events.log");
        RecordingSync sync = new RecordingSync();

        try (ProvenanceEventLog events = openAt(log, sync)) {
            events.append(ProvenanceEventType.RUN_STARTED, Map.of("run.id", "R-1"));
            sync.failNextForceAfterLosing(20, new IOException("the device went away"));
            assertThrows(
                    IOException.class,
                    () ->
                            events.append(
                                    ProvenanceEventType.STAGE_STARTED, Map.of("stage", "comet")));
            events.append(ProvenanceEventType.STAGE_FINISHED, Map.of("stage", "comet"));
        }

        RecoveredEventLog recovered = ProvenanceEventLogReader.recover(log);
        assertAll(
                // The record written after the failure survives in full.  Without the terminating
                // newline it would have been concatenated onto the remnant and both would be one
                // unreadable line -- the failed append would have taken a later record with it.
                () -> assertEquals(List.of(1L, 3L), sequencesOf(recovered)),
                () ->
                        assertEquals(
                                new ProvenanceEvent(
                                        3L,
                                        Instant.parse("2026-08-31T09:15:02.000Z"),
                                        ProvenanceEventType.STAGE_FINISHED,
                                        Map.of("stage", "comet")),
                                recovered.events().get(1)),
                // Two defects and both are true: the remnant is not a record, and number 2 is
                // missing from the history because its append spent it and then failed.
                () -> assertEquals(2, recovered.defects().size()),
                () ->
                        assertEquals(
                                EventLogDefectKind.MALFORMED_LINE,
                                recovered.defects().get(0).kind()),
                () ->
                        assertEquals(
                                EventLogDefectKind.SEQUENCE_GAP, recovered.defects().get(1).kind()),
                () ->
                        assertEquals(
                                "expected sequence 2, but this line carries 3",
                                recovered.defects().get(1).detail()));
    }

    @Test
    @DisplayName("several threads may share one log, and the numbering survives it")
    void concurrentAppendsAreSerialised() throws IOException, InterruptedException {
        Path log = directory.resolve("events.log");
        int threads = 4;
        int perThread = 25;
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> workers = new ArrayList<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        try (ProvenanceEventLog events =
                ProvenanceEventLog.openAppend(
                        log,
                        SecretRedactor.patternsOnly(),
                        Clock.fixed(Instant.parse("2026-08-31T09:15:00Z"), ZoneOffset.UTC))) {
            for (int worker = 0; worker < threads; worker++) {
                Thread thread =
                        new Thread(
                                () -> {
                                    try {
                                        start.await();
                                        for (int event = 0; event < perThread; event++) {
                                            events.append(
                                                    ProvenanceEventType.FILE_HASHED,
                                                    Map.of(
                                                            ProvenanceEvent.FILE_PATH_KEY,
                                                            "/data/spectra.mzML"));
                                        }
                                    } catch (Throwable thrown) {
                                        failure.compareAndSet(null, thrown);
                                    }
                                });
                workers.add(thread);
                thread.start();
            }
            start.countDown();
            for (Thread thread : workers) {
                thread.join(TimeUnit.SECONDS.toMillis(30));
            }
        }

        RecoveredEventLog recovered = ProvenanceEventLogReader.recover(log);
        List<Long> expected = new ArrayList<>();
        long appended = (long) threads * perThread;
        for (long sequence = 1; sequence <= appended; sequence++) {
            expected.add(sequence);
        }
        assertAll(
                () -> assertNull(failure.get(), "an appending thread failed"),
                () -> assertEquals(expected, sequencesOf(recovered)),
                () -> assertTrue(recovered.intact(), "concurrent appends damaged the log"));
    }

    @Test
    @DisplayName("the log knows its own absolute path and says so without disclosing its contents")
    void pathAndToString() throws IOException {
        Path log = directory.resolve("events.log");

        try (ProvenanceEventLog events = openAt(log, new RecordingSync())) {
            events.append(ProvenanceEventType.RUN_STARTED, Map.of("run.id", "R-1"));

            assertAll(
                    () -> assertEquals(log.toAbsolutePath(), events.path()),
                    () -> assertTrue(events.path().isAbsolute()),
                    () ->
                            assertEquals(
                                    "ProvenanceEventLog[path="
                                            + log.toAbsolutePath()
                                            + ", nextSequence=2]",
                                    events.toString()));
        }
    }

    @Test
    @DisplayName("closing twice is allowed; appending after closing is not")
    void closingIsFinal() throws IOException {
        Path log = directory.resolve("events.log");
        ProvenanceEventLog events = openAt(log, new RecordingSync());
        events.append(ProvenanceEventType.RUN_STARTED, Map.of("run.id", "R-1"));
        events.close();
        events.close();

        assertThrows(
                ClosedChannelException.class,
                () -> events.append(ProvenanceEventType.WARNING_RAISED, Map.of("m", "x")));
    }

    @Test
    @DisplayName("a log in a directory that does not exist fails rather than being invented")
    void aMissingDirectoryIsAFailure() {
        Path log = directory.resolve("no-such-directory").resolve("events.log");

        assertThrows(
                NoSuchFileException.class,
                () -> ProvenanceEventLog.openAppend(log, SecretRedactor.patternsOnly()));
    }

    @Test
    @DisplayName("the system-clock overload stamps events with a plausible present")
    void theDefaultClockIsTheSystemClock() throws IOException {
        Path log = directory.resolve("events.log");
        Instant before = Instant.now().minusSeconds(2);

        ProvenanceEvent event;
        try (ProvenanceEventLog events =
                ProvenanceEventLog.openAppend(log, SecretRedactor.patternsOnly())) {
            event = events.append(ProvenanceEventType.RUN_STARTED, Map.of("run.id", "R-1"));
        }
        Instant after = Instant.now().plusSeconds(2);

        assertAll(
                () -> assertTrue(event.timestamp().isAfter(before), "the timestamp is in the past"),
                () ->
                        assertTrue(
                                event.timestamp().isBefore(after),
                                "the timestamp is in the future"));
    }

    @Test
    @DisplayName("null arguments are rejected by name")
    void nullArgumentsAreRejected() throws IOException {
        Path log = directory.resolve("events.log");

        try (ProvenanceEventLog events = openAt(log, new RecordingSync())) {
            assertAll(
                    () ->
                            assertEquals(
                                    "path",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            ProvenanceEventLog.openAppend(
                                                                    deliberateNull(),
                                                                    SecretRedactor.patternsOnly()))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "redactor",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            ProvenanceEventLog.openAppend(
                                                                    log, deliberateNull()))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "clock",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            ProvenanceEventLog.openAppend(
                                                                    log,
                                                                    SecretRedactor.patternsOnly(),
                                                                    deliberateNull()))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "sync",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            ProvenanceEventLog.openAppend(
                                                                    log,
                                                                    SecretRedactor.patternsOnly(),
                                                                    Clock.systemUTC(),
                                                                    deliberateNull()))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "type",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () -> events.append(deliberateNull(), Map.of()))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "payload",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            events.append(
                                                                    ProvenanceEventType.RUN_STARTED,
                                                                    deliberateNull()))
                                            .getMessage()));
        }
    }

    @Test
    @DisplayName("a rejected event is not written at all")
    void aRejectedEventLeavesNothingBehind() throws IOException {
        Path log = directory.resolve("events.log");

        try (ProvenanceEventLog events = openAt(log, new RecordingSync())) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            events.append(
                                    ProvenanceEventType.RUN_FINISHED, Map.of("stage", "comet")));
            assertEquals(1L, events.nextSequence());
        }

        assertEquals(0L, Files.size(log));
    }

    // -------------------------------------------------------------------------------------
    // Fixtures.
    // -------------------------------------------------------------------------------------

    private ProvenanceEventLog openAt(Path log, EventLogSync sync) throws IOException {
        return ProvenanceEventLog.openAppend(
                log,
                SecretRedactor.patternsOnly(),
                new StepClock(Instant.parse("2026-08-31T09:15:00Z"), Duration.ofSeconds(1)),
                sync);
    }

    private static void appendTheWholeRun(ProvenanceEventLog events) throws IOException {
        appendTheWholeRun(events, new ArrayList<>());
    }

    /**
     * Appends one event of every type, recording the file's size on disk after each one.
     *
     * @param events the log
     * @param sizesOnDisk filled with {@link Files#size} after each append
     * @throws IOException if an append fails
     */
    private static void appendTheWholeRun(ProvenanceEventLog events, List<Long> sizesOnDisk)
            throws IOException {
        // Built through the pinned key constants, so that a rename of one of them fails against
        // the hand-typed lines above instead of silently changing the format.
        Map<String, String> tool = new LinkedHashMap<>();
        tool.put(ProvenanceEvent.TOOL_VERSION_KEY, "2026.02.2");
        tool.put(ProvenanceEvent.TOOL_KEY, "comet");
        Map<String, String> hashed = new LinkedHashMap<>();
        hashed.put(ProvenanceEvent.FILE_PATH_KEY, "/data/HeLa.mzML");
        hashed.put(ProvenanceEvent.FILE_MD5_KEY, "d41d8cd98f00b204e9800998ecf8427e");
        Map<String, String> stageFinished = new LinkedHashMap<>();
        stageFinished.put(ProvenanceEvent.STATUS_KEY, ProvenanceStatus.COMPLETED.wireName());
        stageFinished.put(ProvenanceEvent.STAGE_KEY, "comet");

        events.append(
                ProvenanceEventType.RUN_STARTED,
                Map.of(ProvenanceEvent.RUN_ID_KEY, "R-2026-08-31-01"));
        sizesOnDisk.add(Files.size(events.path()));
        events.append(
                ProvenanceEventType.STAGE_STARTED, Map.of(ProvenanceEvent.STAGE_KEY, "comet"));
        sizesOnDisk.add(Files.size(events.path()));
        events.append(ProvenanceEventType.TOOL_INVOKED, tool);
        sizesOnDisk.add(Files.size(events.path()));
        events.append(ProvenanceEventType.FILE_HASHED, hashed);
        sizesOnDisk.add(Files.size(events.path()));
        events.append(
                ProvenanceEventType.WARNING_RAISED,
                Map.of(ProvenanceEvent.MESSAGE_KEY, "Percolator 3.09 cannot write XML"));
        sizesOnDisk.add(Files.size(events.path()));
        events.append(ProvenanceEventType.STAGE_FINISHED, stageFinished);
        sizesOnDisk.add(Files.size(events.path()));
        events.append(
                ProvenanceEventType.RUN_FINISHED,
                Map.of(ProvenanceEvent.STATUS_KEY, ProvenanceStatus.COMPLETED.wireName()));
        sizesOnDisk.add(Files.size(events.path()));
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

    /** A clock that advances by a fixed step every time it is read, so that lines are pinnable. */
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

    /**
     * The real force, plus a note of the file's length at the moment it happened.
     *
     * <p>Delegating rather than pretending: the production behaviour still runs in every test that
     * uses this, and what is added is the observation. The length is the assertion that matters --
     * it is what proves the record's bytes were already in the file when the force was asked for,
     * which no inspection of the finished file can show.
     */
    private static final class RecordingSync implements EventLogSync {

        private final List<Long> sizesAtForce = new ArrayList<>();

        private IOException nextFailure;

        private int loseTrailingBytes;

        List<Long> sizesAtForce() {
            return List.copyOf(sizesAtForce);
        }

        void failNextForceWith(IOException failure) {
            nextFailure = failure;
        }

        /**
         * Fails the next force, having first thrown away the last {@code bytes} bytes of the file.
         *
         * <p>This is the harsher half of a failed append: not "the record is written but was not
         * forced" but "only part of the record reached the file at all", which is what a process
         * killed inside {@code write} leaves. It is injected here because a short write cannot be
         * provoked on a real channel.
         *
         * @param bytes how much of the record never made it
         * @param failure what the append fails with
         */
        void failNextForceAfterLosing(int bytes, IOException failure) {
            loseTrailingBytes = bytes;
            nextFailure = failure;
        }

        @Override
        public void force(FileChannel channel) throws IOException {
            sizesAtForce.add(channel.size());
            if (loseTrailingBytes > 0) {
                channel.truncate(channel.size() - loseTrailingBytes);
                loseTrailingBytes = 0;
            }
            if (nextFailure != null) {
                IOException failure = nextFailure;
                nextFailure = null;
                throw failure;
            }
            channel.force(true);
        }
    }

    /**
     * A {@link FileChannel} that records the one call the sync seam is allowed to make.
     *
     * <p>Everything else refuses, deliberately: if a future edit made the seam read, write or seek,
     * that would be a change in what durability means here and it should fail loudly rather than
     * pass quietly.
     */
    private static final class RecordingFileChannel extends FileChannel {

        private final List<String> operations = new ArrayList<>();

        List<String> operations() {
            return List.copyOf(operations);
        }

        @Override
        public void force(boolean metaData) {
            operations.add("force(" + metaData + ")");
        }

        @Override
        protected void implCloseChannel() {
            operations.add("close");
        }

        @Override
        public int read(ByteBuffer destination) {
            throw new UnsupportedOperationException("read");
        }

        @Override
        public long read(ByteBuffer[] destinations, int offset, int length) {
            throw new UnsupportedOperationException("read");
        }

        @Override
        public int write(ByteBuffer source) {
            throw new UnsupportedOperationException("write");
        }

        @Override
        public long write(ByteBuffer[] sources, int offset, int length) {
            throw new UnsupportedOperationException("write");
        }

        @Override
        public long position() {
            throw new UnsupportedOperationException("position");
        }

        @Override
        public FileChannel position(long newPosition) {
            throw new UnsupportedOperationException("position");
        }

        @Override
        public long size() {
            throw new UnsupportedOperationException("size");
        }

        @Override
        public FileChannel truncate(long size) {
            throw new UnsupportedOperationException("truncate");
        }

        @Override
        public long transferTo(long position, long count, WritableByteChannel destination) {
            throw new UnsupportedOperationException("transferTo");
        }

        @Override
        public long transferFrom(ReadableByteChannel source, long position, long count) {
            throw new UnsupportedOperationException("transferFrom");
        }

        @Override
        public int read(ByteBuffer destination, long position) {
            throw new UnsupportedOperationException("read");
        }

        @Override
        public int write(ByteBuffer source, long position) {
            throw new UnsupportedOperationException("write");
        }

        @Override
        public MappedByteBuffer map(MapMode mode, long position, long size) {
            throw new UnsupportedOperationException("map");
        }

        @Override
        public FileLock lock(long position, long size, boolean shared) {
            throw new UnsupportedOperationException("lock");
        }

        @Override
        public FileLock tryLock(long position, long size, boolean shared) {
            throw new UnsupportedOperationException("tryLock");
        }
    }
}
