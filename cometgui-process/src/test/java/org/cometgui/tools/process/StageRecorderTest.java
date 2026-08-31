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

package org.cometgui.tools.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.cometgui.domain.log.LogMessage;
import org.cometgui.domain.log.MessageSeverity;
import org.cometgui.domain.secrets.SecretRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The listener that turns a stage's output into a log file, a console and an outcome.
 *
 * <p>Most of it is proved end to end against a real process in {@code StageRunnerTest}. What is
 * here is the one path a real run must never take and a test therefore has to construct: a log file
 * that cannot be written. A full disk part-way through a two-hour search is the case where quiet
 * behaviour would be worst, so it is the case that is pinned hardest.
 */
class StageRecorderTest {

    private static final Instant AT = Instant.parse("2026-08-31T19:04:51.250Z");

    private static final Clock FIXED = Clock.fixed(AT, ZoneOffset.UTC);

    /**
     * A null the static analyser cannot see through; see {@code StageLogFormatTest} for why.
     *
     * @param <T> whatever the call site needs
     * @return null
     */
    private static <T> T deliberateNull() {
        List<T> holder = new ArrayList<>(1);
        holder.add(null);
        return holder.get(0);
    }

    private static StageRecorder recorderWriting(StageLogFile log, RecordingSink sink) {
        return new StageRecorder(
                TestStage.named("comet"),
                "comet",
                "[\"/opt/comet\"]",
                log,
                ProcessRedactor.with(SecretRegistry.empty()),
                sink,
                FIXED,
                AT);
    }

    @Test
    @DisplayName("a log file that cannot be written is announced to the console exactly once")
    void oneWarningAndOnlyOne(@TempDir Path logs) {
        RecordingSink sink = new RecordingSink();
        StageLogFile broken = new StageLogFile(logs.resolve("comet.log"), new AlwaysFailing());
        StageRecorder recorder = recorderWriting(broken, sink);

        recorder.onStandardOutput("first");
        recorder.onStandardOutput("second");
        recorder.onStandardError("third");

        assertEquals(
                List.of(
                        "[cometgui] the stage log "
                                + logs.resolve("comet.log")
                                + " could not be written: java.io.IOException: No space left on"
                                + " device"),
                sink.textsOf(MessageSeverity.WARNING),
                "one warning, however many lines fail: a warning per line would bury the run");
        assertEquals(List.of("first", "second"), sink.textsOf(MessageSeverity.INFO));
        assertEquals(List.of("third"), sink.textsOf(MessageSeverity.STDERR));
    }

    @Test
    @DisplayName("the console still gets every line when the disk has stopped taking them")
    void theConsoleSurvivesTheDisk(@TempDir Path logs) {
        RecordingSink sink = new RecordingSink();
        StageLogFile broken = new StageLogFile(logs.resolve("comet.log"), new AlwaysFailing());
        StageRecorder recorder = recorderWriting(broken, sink);

        recorder.onStandardOutput("Comet version 2024.01");
        recorder.onStandardError("Search 12% complete");

        assertEquals(
                List.of("Comet version 2024.01", "Search 12% complete"),
                sink.texts().stream().filter(text -> !text.startsWith("[cometgui] ")).toList());
    }

    @Test
    @DisplayName("the failures are counted into the outcome, so an incomplete log says so")
    void theOutcomeCarriesTheFailureCount(@TempDir Path logs)
            throws InterruptedException, ExecutionException {
        RecordingSink sink = new RecordingSink();
        StageLogFile broken = new StageLogFile(logs.resolve("comet.log"), new AlwaysFailing());
        StageRecorder recorder = recorderWriting(broken, sink);

        recorder.onStandardOutput("one");
        recorder.onStandardOutput("two");
        recorder.onExit(0);

        StageOutcome outcome = recorder.completed().get();
        assertEquals(
                4L,
                outcome.logWriteFailures(),
                "two lines, the footer and the close: the close counts, because the last bytes"
                        + " may not have landed");
        assertEquals(2L, outcome.standardOutputLines(), "the count is lines the tool emitted");
        assertEquals(0L, outcome.standardErrorLines());
    }

    @Test
    @DisplayName("a healthy stage announces nothing of its own to the console")
    void aHealthyStageIsQuiet(@TempDir Path logs) throws IOException {
        RecordingSink sink = new RecordingSink();
        try (StageLogFile log = StageLogFile.create(logs, "comet")) {
            StageRecorder recorder = recorderWriting(log, sink);

            recorder.onStandardOutput("Comet version 2024.01");

            assertEquals(List.of(), sink.textsOf(MessageSeverity.WARNING));
            assertEquals(List.of("Comet version 2024.01"), sink.texts());
        }
    }

    @Test
    @DisplayName("the console message carries the stage and the run's instant")
    void theConsoleMessageIsTagged(@TempDir Path logs) throws IOException {
        RecordingSink sink = new RecordingSink();
        try (StageLogFile log = StageLogFile.create(logs, "comet")) {
            StageRecorder recorder = recorderWriting(log, sink);

            recorder.onStandardError("Search 12% complete");

            LogMessage message = sink.messages().get(0);
            assertEquals(Instant.parse("2026-08-31T19:04:51.250Z"), message.timestamp());
            assertEquals("comet", message.stage().orElseThrow().id());
            assertEquals(MessageSeverity.STDERR, message.severity());
            assertEquals("Search 12% complete", message.text());
        }
    }

    @Test
    @DisplayName("the first cancellation reason wins")
    void theFirstReasonWins(@TempDir Path logs)
            throws IOException, InterruptedException, ExecutionException {
        RecordingSink sink = new RecordingSink();
        try (StageLogFile log = StageLogFile.create(logs, "comet")) {
            StageRecorder recorder = recorderWriting(log, sink);

            recorder.markCancellation(StageRecorder.Cancellation.BY_CALLER);
            recorder.markCancellation(StageRecorder.Cancellation.BY_TIMEOUT);
            recorder.onExit(143);

            StageOutcome outcome = recorder.completed().get();
            assertTrue(outcome.cancellationRequested());
            assertEquals(
                    false,
                    outcome.timedOut(),
                    "the user cancelled first; a timeout that fired afterwards did not kill it");
        }
        assertEquals(
                List.of(
                        "2026-08-31T19:04:51.250Z [cometgui] stage comet ended: exit code 143 after"
                                + " PT0S, cancellation requested"),
                Files.readAllLines(logs.resolve("comet.log"), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("rejects a null constructor argument, naming it")
    void rejectsNulls(@TempDir Path logs) throws IOException {
        RecordingSink sink = new RecordingSink();
        try (StageLogFile log = StageLogFile.create(logs, "comet")) {
            ProcessRedactor redactor = ProcessRedactor.with(SecretRegistry.empty());
            assertEquals(
                    "stage",
                    assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            new StageRecorder(
                                                    deliberateNull(),
                                                    "comet",
                                                    "[]",
                                                    log,
                                                    redactor,
                                                    sink,
                                                    FIXED,
                                                    AT))
                            .getMessage());
            assertEquals(
                    "stageId",
                    assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            new StageRecorder(
                                                    TestStage.named("comet"),
                                                    deliberateNull(),
                                                    "[]",
                                                    log,
                                                    redactor,
                                                    sink,
                                                    FIXED,
                                                    AT))
                            .getMessage());
            assertEquals(
                    "redactedDisplayCommand",
                    assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            new StageRecorder(
                                                    TestStage.named("comet"),
                                                    "comet",
                                                    deliberateNull(),
                                                    log,
                                                    redactor,
                                                    sink,
                                                    FIXED,
                                                    AT))
                            .getMessage());
            assertEquals(
                    "log",
                    assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            new StageRecorder(
                                                    TestStage.named("comet"),
                                                    "comet",
                                                    "[]",
                                                    deliberateNull(),
                                                    redactor,
                                                    sink,
                                                    FIXED,
                                                    AT))
                            .getMessage());
            assertEquals(
                    "redactor",
                    assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            new StageRecorder(
                                                    TestStage.named("comet"),
                                                    "comet",
                                                    "[]",
                                                    log,
                                                    deliberateNull(),
                                                    sink,
                                                    FIXED,
                                                    AT))
                            .getMessage());
            assertEquals(
                    "sink",
                    assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            new StageRecorder(
                                                    TestStage.named("comet"),
                                                    "comet",
                                                    "[]",
                                                    log,
                                                    redactor,
                                                    deliberateNull(),
                                                    FIXED,
                                                    AT))
                            .getMessage());
            assertEquals(
                    "clock",
                    assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            new StageRecorder(
                                                    TestStage.named("comet"),
                                                    "comet",
                                                    "[]",
                                                    log,
                                                    redactor,
                                                    sink,
                                                    deliberateNull(),
                                                    AT))
                            .getMessage());
            assertEquals(
                    "startedAt",
                    assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            new StageRecorder(
                                                    TestStage.named("comet"),
                                                    "comet",
                                                    "[]",
                                                    log,
                                                    redactor,
                                                    sink,
                                                    FIXED,
                                                    deliberateNull()))
                            .getMessage());
        }
    }

    /** A writer whose every operation fails, the way a full disk does. */
    private static final class AlwaysFailing extends Writer {

        @Override
        public void write(char[] buffer, int offset, int length) throws IOException {
            throw new IOException("No space left on device");
        }

        @Override
        public void flush() throws IOException {
            throw new IOException("No space left on device");
        }

        @Override
        public void close() throws IOException {
            throw new IOException("No space left on device");
        }
    }
}
