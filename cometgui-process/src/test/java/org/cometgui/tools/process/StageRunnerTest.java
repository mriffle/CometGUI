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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.cometgui.domain.log.MessageSeverity;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.run.StageTag;
import org.cometgui.domain.secrets.SecretRegistry;
import org.cometgui.tools.process.fakes.FakeTools;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives the whole stage layer against the real process service and the real fake tool, as a real
 * external process.
 *
 * <p><strong>Every expected value is hand-typed.</strong> The one thing taken from the running
 * system is a temporary directory's path, which is an operating-system fact rather than a computed
 * expectation, and the {@code java} binary's own path inside a rendered command, which is asserted
 * by its ends rather than in full for the same reason.
 *
 * <p><strong>There is no fixed sleep in this file and there must never be one</strong> (PHASE-03
 * exit gate item 6). Everything waits on a real event: a marker line arriving at the sink, the sink
 * gate being reached, or the outcome future completing. The timeouts are failure bounds so that a
 * broken build fails instead of hanging, and are never the mechanism. The one place a duration is
 * load-bearing is the per-stage timeout under test, where the expiry <em>is</em> the behaviour.
 *
 * <p>Every test that starts a hanging fake cancels it in a {@code finally} and waits for it to be
 * gone, so that no orphan survives the run.
 */
class StageRunnerTest {

    /** A failure bound for something that is supposed to happen on its own. Never a delay. */
    private static final int FAILURE_BOUND_SECONDS = 60;

    /** The instant every fixed-clock test sees, so a whole log file can be written out by hand. */
    private static final Instant AT = Instant.parse("2026-08-31T19:04:51.250Z");

    /** Its rendering in a log line. Typed by hand. */
    private static final String AT_TEXT = "2026-08-31T19:04:51.250Z";

    /** A clock that never moves: every timestamp in a log file is then known in advance. */
    private static final Clock FIXED = Clock.fixed(AT, ZoneOffset.UTC);

    /** {@code SIGTERM}: a Unix process killed by signal N reports 128 + N. */
    private static final int EXIT_SIGTERM = 143;

    /**
     * The per-stage timeout used where one has to actually fire: 1000 ms.
     *
     * <p>Five consecutive launches of the fake on this machine took 273 ms in total, so this is
     * roughly eighteen times the cost of starting it. That margin is why the timeout tests can
     * assert that the tool's first line reached the log before the kill: a bound too close to the
     * startup cost would make that intermittent.
     */
    private static final long TIMEOUT_MILLIS = 1000L;

    private static final StageTag COMET = TestStage.named("comet");

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

    /** A runner over the real process service, a fixed clock and no registered secret. */
    private static StageRunner runner(Path logs, RunMessageSink sink) {
        return runner(logs, sink, FIXED, SecretRegistry.empty());
    }

    private static StageRunner runner(
            Path logs, RunMessageSink sink, Clock clock, SecretRegistry secrets) {
        return new StageRunner(
                new ProcessService(Clock.systemUTC()),
                clock,
                ProcessRedactor.with(secrets),
                sink,
                logs);
    }

    private static void awaitMarker(CountDownLatch latch, String what) throws InterruptedException {
        assertTrue(
                latch.await(FAILURE_BOUND_SECONDS, TimeUnit.SECONDS),
                "the stage never produced " + what + " within the failure bound");
    }

    /**
     * Cancels a stage and waits for it to be gone. Used in a {@code finally}, where a failure of
     * its own would hide the assertion that actually failed.
     *
     * @param stage the stage to stop
     */
    private static void stopAndWait(RunningStage stage) {
        stage.requestCancellation();
        try {
            stage.awaitOutcome();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static List<String> linesOf(Path file) throws IOException {
        return Files.readAllLines(file, StandardCharsets.UTF_8);
    }

    private static List<String> tagged(List<String> lines, String tag) {
        String prefix = AT_TEXT + " [" + tag + "] ";
        return lines.stream().filter(line -> line.startsWith(prefix)).toList();
    }

    /**
     * The open file descriptors of this JVM that point at {@code file}.
     *
     * <p>Linux-specific, like the 143 and 137 exit codes this phase already pins, and deliberately
     * not skipped anywhere else: the reference platform is Linux and an unverifiable gate item is
     * not a passed one. It exists because "the log file was closed" has no other observable
     * consequence -- the content is identical either way -- and a run of a dozen stages that leaked
     * a descriptor per stage would look perfectly healthy.
     *
     * @param file the file to look for
     * @return a description of each descriptor still pointing at it, empty when there is none
     * @throws IOException if {@code /proc/self/fd} cannot be listed
     */
    private static List<String> openDescriptorsFor(Path file) throws IOException {
        Path descriptorDirectory = Path.of("/proc/self/fd");
        assertTrue(
                Files.isDirectory(descriptorDirectory),
                "this assertion reads /proc/self/fd, which the reference platform has");
        Path real = file.toRealPath();
        List<String> open = new ArrayList<>();
        try (Stream<Path> descriptors = Files.list(descriptorDirectory)) {
            for (Path descriptor : descriptors.toList()) {
                if (real.equals(targetOf(descriptor))) {
                    open.add(descriptor.getFileName() + " -> " + real);
                }
            }
        }
        return open;
    }

    private static Path targetOf(Path descriptor) {
        try {
            return Files.readSymbolicLink(descriptor);
        } catch (IOException ignored) {
            /* The descriptor was closed while this loop was reading the directory. */
            return null;
        }
    }

    // ============================================================== construction ==

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("rejects a null dependency, naming it")
        void rejectsNulls(@TempDir Path tmp) {
            ProcessRunner processes = new ProcessService(Clock.systemUTC());
            ProcessRedactor redactor = ProcessRedactor.with(SecretRegistry.empty());
            RecordingSink sink = new RecordingSink();

            assertEquals(
                    "processes",
                    assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            new StageRunner(
                                                    deliberateNull(), FIXED, redactor, sink, tmp))
                            .getMessage());
            assertEquals(
                    "clock",
                    assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            new StageRunner(
                                                    processes,
                                                    deliberateNull(),
                                                    redactor,
                                                    sink,
                                                    tmp))
                            .getMessage());
            assertEquals(
                    "redactor",
                    assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            new StageRunner(
                                                    processes, FIXED, deliberateNull(), sink, tmp))
                            .getMessage());
            assertEquals(
                    "sink",
                    assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            new StageRunner(
                                                    processes,
                                                    FIXED,
                                                    redactor,
                                                    deliberateNull(),
                                                    tmp))
                            .getMessage());
            assertEquals(
                    "logDirectory",
                    assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            new StageRunner(
                                                    processes,
                                                    FIXED,
                                                    redactor,
                                                    sink,
                                                    deliberateNull()))
                            .getMessage());
        }

        @Test
        @DisplayName("constructing a runner creates nothing: the log directory appears at start")
        void constructionTouchesNothing(@TempDir Path tmp) {
            Path logs = tmp.resolve("logs");

            runner(logs, new RecordingSink());

            assertFalse(Files.exists(logs));
        }

        @Test
        @DisplayName("start rejects a null stage, command or timeout, naming it")
        void startRejectsNulls(@TempDir Path tmp) {
            StageRunner runner = runner(tmp.resolve("logs"), new RecordingSink());
            ToolCommand command = FakeTools.command(tmp, "exit-code", "0");

            assertEquals(
                    "stage",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> runner.start(deliberateNull(), command))
                            .getMessage());
            assertEquals(
                    "command",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> runner.start(COMET, deliberateNull()))
                            .getMessage());
            assertEquals(
                    "timeout",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> runner.start(COMET, command, deliberateNull()))
                            .getMessage());
        }

        @Test
        @DisplayName("a stage identifier that is not a safe file name never reaches the disk")
        void refusesADangerousStageIdentifier(@TempDir Path tmp) {
            Path logs = tmp.resolve("logs");
            StageRunner runner = runner(logs, new RecordingSink());
            ToolCommand command = FakeTools.command(tmp, "exit-code", "0");

            assertThrows(
                    IllegalArgumentException.class,
                    () -> runner.start(new TestStage("../escaped", "Escaped"), command));

            assertFalse(
                    Files.exists(logs),
                    "the identifier is checked before anything is created or launched");
        }
    }

    // ================================================================== outcomes ==

    @Nested
    @DisplayName("a stage that runs and ends")
    class Outcomes {

        @Test
        @DisplayName("reports the exit code, the streams, the log file and the command")
        void theOutcome(@TempDir Path tmp) throws IOException, InterruptedException {
            RecordingSink sink = new RecordingSink();
            Path logs = tmp.resolve("logs");

            RunningStage stage =
                    runner(logs, sink).start(COMET, FakeTools.command(tmp, "exit-code", "3"));
            StageOutcome outcome = stage.awaitOutcome();

            assertEquals(3, outcome.exitCode(), "not normalised to 1");
            assertEquals(COMET, outcome.stage());
            assertEquals(logs.resolve("comet.log"), outcome.logFile());
            assertEquals(1L, outcome.standardOutputLines());
            assertEquals(1L, outcome.standardErrorLines());
            assertFalse(outcome.cancellationRequested());
            assertFalse(outcome.timedOut());
            assertEquals(0L, outcome.logWriteFailures());
            assertEquals(Instant.parse("2026-08-31T19:04:51.250Z"), outcome.startedAt());
            assertEquals(Instant.parse("2026-08-31T19:04:51.250Z"), outcome.endedAt());
            assertTrue(
                    outcome.redactedDisplayCommand()
                            .endsWith("\"fakes.FakeTool\", \"exit-code\", \"3\"]"),
                    "the rendered command, which was: " + outcome.redactedDisplayCommand());
            assertTrue(outcome.redactedDisplayCommand().startsWith("[\""));
        }

        @Test
        @DisplayName("the handle answers before and after the stage ends")
        void theHandle(@TempDir Path tmp) throws IOException, InterruptedException {
            RecordingSink sink = new RecordingSink();
            Path logs = tmp.resolve("logs");

            RunningStage stage =
                    runner(logs, sink).start(COMET, FakeTools.command(tmp, "exit-code", "0"));

            assertEquals(COMET, stage.stage());
            assertEquals(logs.resolve("comet.log"), stage.logFile());

            StageOutcome outcome = stage.awaitOutcome();

            assertFalse(stage.isAlive(), "the process is gone once the outcome has arrived");
            assertEquals(outcome, stage.outcomeIfFinished().orElseThrow());
            assertEquals(outcome, stage.awaitOutcome(), "waiting again returns the same outcome");
        }

        @Test
        @DisplayName("the two line counts are not the same counter")
        void theCountsAreIndependent(@TempDir Path tmp) throws IOException, InterruptedException {
            RecordingSink sink = new RecordingSink();
            Path logs = tmp.resolve("logs");

            /* flood <totalBytes> <lineLength> err: 21 bytes per line, so 100 bytes is five lines
             * on standard error, and the "lines 5" summary goes to standard output. */
            RunningStage stage =
                    runner(logs, sink)
                            .start(COMET, FakeTools.command(tmp, "flood", "100", "20", "err"));
            StageOutcome outcome = stage.awaitOutcome();

            assertEquals(1L, outcome.standardOutputLines());
            assertEquals(5L, outcome.standardErrorLines());
            assertEquals(
                    List.of(AT_TEXT + " [stdout] lines 5"),
                    tagged(linesOf(outcome.logFile()), "stdout"));
            assertEquals(5, tagged(linesOf(outcome.logFile()), "stderr").size());
        }

        @Test
        @DisplayName("the duration comes from the injected clock, exactly")
        void theDuration(@TempDir Path tmp) throws IOException, InterruptedException {
            RecordingSink sink = new RecordingSink();
            SteppingClock clock = new SteppingClock(AT, Duration.ofSeconds(1));

            RunningStage stage =
                    runner(tmp.resolve("logs"), sink, clock, SecretRegistry.empty())
                            .start(COMET, FakeTools.command(tmp, "exit-code", "0"));
            StageOutcome outcome = stage.awaitOutcome();

            /* Four reads, in this order and no other: the start, one per output line -- there are
             * exactly two, one on each stream -- and the exit.  Which stream gets which of the two
             * middle reads is nondeterministic; how many there are is not. */
            assertEquals(Instant.parse("2026-08-31T19:04:51.250Z"), outcome.startedAt());
            assertEquals(Instant.parse("2026-08-31T19:04:54.250Z"), outcome.endedAt());
            assertEquals(Duration.ofSeconds(3), outcome.duration());
            assertEquals(4L, clock.reads());
        }

        @Test
        @DisplayName("one runner runs several stages, each into its own file")
        void severalStages(@TempDir Path tmp) throws IOException, InterruptedException {
            RecordingSink sink = new RecordingSink();
            Path logs = tmp.resolve("logs");
            StageRunner runner = runner(logs, sink);

            StageOutcome comet =
                    runner.start(COMET, FakeTools.command(tmp, "exit-code", "0")).awaitOutcome();
            StageOutcome percolator =
                    runner.start(
                                    TestStage.named("percolator"),
                                    FakeTools.command(tmp, "exit-code", "0"))
                            .awaitOutcome();

            assertEquals(logs.resolve("comet.log"), comet.logFile());
            assertEquals(logs.resolve("percolator.log"), percolator.logFile());
            assertEquals(
                    AT_TEXT + " [cometgui] stage percolator ended: exit code 0 after PT0S",
                    linesOf(percolator.logFile()).get(4));
        }
    }

    // ================================================================= the log ==

    @Nested
    @DisplayName("the per-stage log file")
    class TheLogFile {

        @Test
        @DisplayName("holds the header, both streams and the footer, in the pinned format")
        void theWholeFile(@TempDir Path tmp) throws IOException, InterruptedException {
            RecordingSink sink = new RecordingSink();
            Path logs = tmp.resolve("logs");

            StageOutcome outcome =
                    runner(logs, sink)
                            .start(COMET, FakeTools.command(tmp, "exit-code", "3"))
                            .awaitOutcome();

            List<String> lines = linesOf(outcome.logFile());
            assertEquals(5, lines.size(), "two header lines, two output lines, one footer");
            assertEquals(AT_TEXT + " [cometgui] stage comet started in " + tmp, lines.get(0));
            assertTrue(
                    lines.get(1).startsWith(AT_TEXT + " [cometgui] command [\""),
                    "the second line was: " + lines.get(1));
            assertTrue(lines.get(1).endsWith("\"fakes.FakeTool\", \"exit-code\", \"3\"]"));
            assertEquals(
                    List.of(AT_TEXT + " [stderr] exiting 3", AT_TEXT + " [stdout] exiting 3"),
                    lines.subList(2, 4).stream().sorted().toList(),
                    "sorted, because the order BETWEEN the two streams is nondeterministic and"
                            + " asserting it would be a flaky test");
            assertEquals(
                    AT_TEXT + " [cometgui] stage comet ended: exit code 3 after PT0S",
                    lines.get(4));
        }

        @Test
        @DisplayName("both streams interleave into one file with every line intact and in order")
        void bothStreamsInOneFile(@TempDir Path tmp) throws IOException, InterruptedException {
            RecordingSink sink = new RecordingSink();
            Path logs = tmp.resolve("logs");

            StageOutcome outcome =
                    runner(logs, sink)
                            .start(COMET, FakeTools.command(tmp, "interleave", "200"))
                            .awaitOutcome();

            List<String> lines = linesOf(outcome.logFile());
            assertEquals(
                    403, lines.size(), "two header lines, four hundred output lines, a footer");
            assertEquals(200L, outcome.standardOutputLines());
            assertEquals(200L, outcome.standardErrorLines());
            assertEquals(
                    IntStream.range(0, 200)
                            .mapToObj(line -> AT_TEXT + " [stdout] out " + line)
                            .toList(),
                    tagged(lines, "stdout"),
                    "standard output's own lines, complete, in order and uncorrupted");
            assertEquals(
                    IntStream.range(0, 200)
                            .mapToObj(line -> AT_TEXT + " [stderr] err " + line)
                            .toList(),
                    tagged(lines, "stderr"));
            assertEquals(
                    "2026-08-31T19:04:51.250Z [stdout] out 42", tagged(lines, "stdout").get(42));
            assertEquals(
                    "2026-08-31T19:04:51.250Z [stderr] err 199", tagged(lines, "stderr").get(199));
        }

        @Test
        @DisplayName("re-running a stage writes a new file and keeps the first attempt's")
        void aRerunKeepsTheFirstAttempt(@TempDir Path tmp)
                throws IOException, InterruptedException {
            RecordingSink sink = new RecordingSink();
            Path logs = tmp.resolve("logs");
            StageRunner runner = runner(logs, sink);

            StageOutcome first =
                    runner.start(COMET, FakeTools.command(tmp, "exit-code", "1")).awaitOutcome();
            StageOutcome second =
                    runner.start(COMET, FakeTools.command(tmp, "exit-code", "0")).awaitOutcome();

            assertEquals(logs.resolve("comet.log"), first.logFile());
            assertEquals(logs.resolve("comet.1.log"), second.logFile());
            assertEquals(
                    AT_TEXT + " [cometgui] stage comet ended: exit code 1 after PT0S",
                    linesOf(logs.resolve("comet.log")).get(4),
                    "the failed first attempt's log is the one that explains the retry");
            assertEquals(
                    AT_TEXT + " [cometgui] stage comet ended: exit code 0 after PT0S",
                    linesOf(logs.resolve("comet.1.log")).get(4));
        }

        @Test
        @DisplayName("the file is closed when the stage ends, and open while it runs")
        void theFileIsClosedAtTheEnd(@TempDir Path tmp) throws IOException, InterruptedException {
            RecordingSink sink = new RecordingSink();
            CountDownLatch hanging = sink.expect("hanging");
            RunningStage stage =
                    runner(tmp.resolve("logs"), sink).start(COMET, FakeTools.command(tmp, "hang"));
            try {
                awaitMarker(hanging, "the hang scenario's announcement");

                assertEquals(
                        1,
                        openDescriptorsFor(stage.logFile()).size(),
                        "while the stage runs the log is open -- without this the assertion below"
                                + " could pass by looking in the wrong place");

                stage.requestCancellation();
                StageOutcome outcome = stage.awaitOutcome();

                assertEquals(
                        List.of(),
                        openDescriptorsFor(outcome.logFile()),
                        "a run of a dozen stages must not leak a descriptor per stage");
            } finally {
                stopAndWait(stage);
            }
        }
    }

    // =========================================================== as it arrives ==

    @Nested
    @DisplayName("as it arrives")
    class AsItArrives {

        /**
         * Lines the fake writes before it announces its file.
         *
         * <p>Large enough that the tool cannot possibly have finished while the sink holds the
         * hundred-and-first line: with the pump parked, at most one read buffer plus a pipe
         * capacity -- about 72 KB on Linux -- can have left the tool, and 20,000 lines is around
         * 240 KB. The tool is therefore blocked writing, which is what makes {@code isAlive()}
         * below a fact rather than a race. If that assumption were ever wrong the assertion would
         * fail loudly rather than pass for the wrong reason.
         */
        private static final int PRE_LINES = 20_000;

        @Test
        @DisplayName("earlier lines are on disk WHILE the tool is still running, not at the end")
        void linesAreOnDiskWhileTheToolRuns(@TempDir Path tmp)
                throws IOException, InterruptedException {
            RecordingSink sink = new RecordingSink();
            CountDownLatch atLine100 = sink.gateOn("working 100");
            RunningStage stage =
                    runner(tmp.resolve("logs"), sink)
                            .start(
                                    COMET,
                                    FakeTools.command(
                                            tmp,
                                            "delayed-output",
                                            "late.txt",
                                            Integer.toString(PRE_LINES)));
            try {
                awaitMarker(atLine100, "its hundred-and-first line of output");

                assertTrue(
                        stage.isAlive(),
                        "the tool is blocked writing to a pipe nobody is draining, so it is still"
                                + " running -- which is the whole point of this test");
                assertTrue(stage.outcomeIfFinished().isEmpty(), "and it has not finished");

                List<String> whileRunning = linesOf(stage.logFile());

                assertEquals(
                        103,
                        whileRunning.size(),
                        "two header lines and the 101 output lines up to the one the sink is"
                                + " holding: buffered output would give 2, and a flush per line"
                                + " gives exactly this");
                assertEquals(AT_TEXT + " [stdout] working 0", whileRunning.get(2));
                assertEquals(AT_TEXT + " [stdout] working 1", whileRunning.get(3));
                assertEquals(AT_TEXT + " [stdout] working 50", whileRunning.get(52));
                assertEquals(AT_TEXT + " [stdout] working 99", whileRunning.get(101));
                assertEquals(AT_TEXT + " [stdout] working 100", whileRunning.get(102));
            } finally {
                sink.releaseGate();
            }

            StageOutcome outcome = stage.awaitOutcome();

            assertEquals(0, outcome.exitCode());
            assertEquals(PRE_LINES + 1L, outcome.standardOutputLines());
            List<String> complete = linesOf(outcome.logFile());
            assertEquals(PRE_LINES + 4, complete.size());
            assertEquals(
                    AT_TEXT + " [stdout] created late.txt 4",
                    complete.get(PRE_LINES + 2),
                    "the fake reads that size back out of the file, so the line cannot exist"
                            + " before the file does");
            assertEquals(
                    AT_TEXT + " [cometgui] stage comet ended: exit code 0 after PT0S",
                    complete.get(PRE_LINES + 3));
        }
    }

    // ================================================================ redaction ==

    @Nested
    @DisplayName("redaction")
    class Redaction {

        /** A registered credential. Forty characters, which is a real token's shape. */
        private static final String SECRET = "7a3f9c2e8b4d6a1f0c5e7b9d3a2f8c4e6b1d0a5f";

        @Test
        @DisplayName("a registered secret reaches neither the log file nor the console")
        void nothingOfTheSecretSurvives(@TempDir Path tmp)
                throws IOException, InterruptedException {
            RecordingSink sink = new RecordingSink();
            Path logs = tmp.resolve("logs");

            StageOutcome outcome =
                    runner(logs, sink, FIXED, SecretRegistry.of(SECRET))
                            .start(COMET, FakeTools.command(tmp, "echo-context", SECRET))
                            .awaitOutcome();

            List<String> lines = linesOf(outcome.logFile());
            assertEquals(
                    List.of(
                            AT_TEXT + " [stdout] argc 1",
                            AT_TEXT + " [stdout] arg 0 [REDACTED]",
                            AT_TEXT + " [stdout] cwd " + tmp,
                            AT_TEXT + " [stdout] env [REDACTED] -absent-",
                            AT_TEXT + " [stdout] envcount 0"),
                    tagged(lines, "stdout"),
                    "the tool printed the credential twice and neither copy is in the log");
            assertTrue(
                    lines.get(1).endsWith("\"echo-context\", \"[REDACTED]\"]"),
                    "and the rendered command in the header is redacted too, which is what"
                            + " R-SEC-03 names: "
                            + lines.get(1));
            assertTrue(outcome.redactedDisplayCommand().endsWith("\"[REDACTED]\"]"));
            assertEquals(
                    List.of(
                            "argc 1",
                            "arg 0 [REDACTED]",
                            "cwd " + tmp,
                            "env [REDACTED] -absent-",
                            "envcount 0"),
                    sink.textsOf(MessageSeverity.INFO));

            SecretScan.assertNothingOfTheSecretSurvives(
                    SECRET,
                    Files.readString(outcome.logFile(), StandardCharsets.UTF_8),
                    "the stage log file");
            SecretScan.assertNothingOfTheSecretSurvives(
                    SECRET, String.join("\n", sink.texts()), "the console");
            SecretScan.assertNothingOfTheSecretSurvives(
                    SECRET, outcome.toString(), "the outcome value");
        }

        @Test
        @DisplayName("the marker is the shared one, hand-typed here rather than referenced")
        void theMarker() {
            assertEquals(
                    "[REDACTED]",
                    org.cometgui.domain.secrets.SecretRedactor.REDACTION_MARKER,
                    "one marker across the console log and the provenance record");
        }
    }

    // ===================================================== cancellation, timeout ==

    @Nested
    @DisplayName("cancellation and the optional timeout")
    class Cancellation {

        @Test
        @DisplayName("a cancelled stage says it was cancelled, and did not time out")
        void aCancelledStage(@TempDir Path tmp) throws IOException, InterruptedException {
            RecordingSink sink = new RecordingSink();
            CountDownLatch hanging = sink.expect("hanging");
            RunningStage stage =
                    runner(tmp.resolve("logs"), sink).start(COMET, FakeTools.command(tmp, "hang"));
            try {
                awaitMarker(hanging, "the hang scenario's announcement");
                assertTrue(stage.isAlive());

                stage.requestCancellation();
                StageOutcome outcome = stage.awaitOutcome();

                assertEquals(EXIT_SIGTERM, outcome.exitCode());
                assertTrue(outcome.cancellationRequested());
                assertFalse(outcome.timedOut(), "a person cancelled it; nothing timed it out");
                assertEquals(1L, outcome.standardOutputLines());
                assertEquals(0L, outcome.standardErrorLines());

                List<String> lines = linesOf(outcome.logFile());
                assertEquals(4, lines.size());
                assertEquals(AT_TEXT + " [stdout] hanging", lines.get(2));
                assertEquals(
                        AT_TEXT
                                + " [cometgui] stage comet ended: exit code 143 after PT0S,"
                                + " cancellation requested",
                        lines.get(3));
            } finally {
                stopAndWait(stage);
            }
        }

        @Test
        @DisplayName("a stage that runs out of time is killed, says so, and keeps its log")
        void aStageThatTimesOut(@TempDir Path tmp) throws IOException, InterruptedException {
            RecordingSink sink = new RecordingSink();
            RunningStage stage =
                    runner(tmp.resolve("logs"), sink)
                            .start(
                                    COMET,
                                    FakeTools.command(tmp, "hang"),
                                    Duration.ofMillis(TIMEOUT_MILLIS));
            try {
                StageOutcome outcome = stage.awaitOutcome();

                assertTrue(outcome.timedOut());
                assertTrue(
                        outcome.cancellationRequested(),
                        "the timeout cancels the stage, so both are true and timedOut is what"
                                + " tells them apart");
                assertEquals(EXIT_SIGTERM, outcome.exitCode());

                List<String> lines = linesOf(outcome.logFile());
                assertEquals(4, lines.size(), "the log of what the tool said before it was killed");
                assertEquals(AT_TEXT + " [stdout] hanging", lines.get(2));
                assertEquals(
                        AT_TEXT
                                + " [cometgui] stage comet ended: exit code 143 after PT0S,"
                                + " cancellation requested, timed out",
                        lines.get(3));
            } finally {
                stopAndWait(stage);
            }
        }

        @Test
        @DisplayName("a stage with NO timeout is not killed, however long it runs")
        void aStageWithNoTimeoutIsNotKilled(@TempDir Path tmp)
                throws IOException, InterruptedException {
            RecordingSink untimedSink = new RecordingSink();
            CountDownLatch hanging = untimedSink.expect("hanging");
            Path logs = tmp.resolve("logs");
            RunningStage untimed =
                    runner(logs, untimedSink)
                            .start(TestStage.named("untimed"), FakeTools.command(tmp, "hang"));
            try {
                awaitMarker(hanging, "the untimed stage's announcement");

                /*
                 * The clock this test reads is another stage, started LATER and configured with a
                 * timeout.  When its outcome arrives, that timeout has demonstrably expired -- so
                 * the untimed stage, which started first, has been running at least as long and is
                 * still there.  A sleep would prove the same thing less honestly.
                 */
                RunningStage timed =
                        runner(logs, new RecordingSink())
                                .start(
                                        TestStage.named("timed"),
                                        FakeTools.command(tmp, "hang"),
                                        Duration.ofMillis(TIMEOUT_MILLIS));
                try {
                    assertTrue(
                            timed.awaitOutcome().timedOut(),
                            "the other stage's timeout is this test's clock");
                } finally {
                    stopAndWait(timed);
                }

                assertTrue(
                        untimed.isAlive(),
                        "it started before a stage whose 1000 ms timeout has already fired, and"
                                + " nothing has killed it");
                assertTrue(untimed.outcomeIfFinished().isEmpty());

                untimed.requestCancellation();
                StageOutcome outcome = untimed.awaitOutcome();

                assertTrue(outcome.cancellationRequested());
                assertFalse(outcome.timedOut(), "it had no timeout, so nothing could time it out");
                assertEquals(EXIT_SIGTERM, outcome.exitCode());
            } finally {
                stopAndWait(untimed);
            }
        }

        @Test
        @DisplayName("a timeout of one millisecond is accepted and fires")
        void theSmallestTimeout(@TempDir Path tmp) throws IOException, InterruptedException {
            RecordingSink sink = new RecordingSink();
            RunningStage stage =
                    runner(tmp.resolve("logs"), sink)
                            .start(COMET, FakeTools.command(tmp, "hang"), Duration.ofMillis(1));
            try {
                StageOutcome outcome = stage.awaitOutcome();

                assertTrue(outcome.timedOut());
                assertTrue(outcome.cancellationRequested());
            } finally {
                stopAndWait(stage);
            }
        }

        @Test
        @DisplayName("a timeout shorter than a millisecond, zero or negative is refused")
        void refusesAnUnusableTimeout(@TempDir Path tmp) {
            StageRunner runner = runner(tmp.resolve("logs"), new RecordingSink());
            ToolCommand command = FakeTools.command(tmp, "hang");

            assertEquals(
                    "a stage timeout must be at least one millisecond, but was: PT0S",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> runner.start(COMET, command, Duration.ZERO))
                            .getMessage());
            assertEquals(
                    "a stage timeout must be at least one millisecond, but was: PT-0.005S",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> runner.start(COMET, command, Duration.ofMillis(-5)))
                            .getMessage());
            assertEquals(
                    "a stage timeout must be at least one millisecond, but was: PT0.000999999S",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> runner.start(COMET, command, Duration.ofNanos(999_999)))
                            .getMessage());
            assertFalse(
                    Files.exists(tmp.resolve("logs")),
                    "a refused timeout must not have opened a log file or started a tool");
        }
    }

    // ================================================================= failures ==

    @Nested
    @DisplayName("when something goes wrong")
    class Failures {

        @Test
        @DisplayName("a tool that cannot be started leaves a log saying what and why")
        void aToolThatCannotBeStarted(@TempDir Path tmp) throws IOException {
            RecordingSink sink = new RecordingSink();
            Path logs = tmp.resolve("logs");
            Path missing = tmp.resolve("missing");
            ToolCommand command =
                    new ToolCommand(FakeTools.argv("exit-code", "0"), missing, Map.of());

            IOException notStarted =
                    assertThrows(IOException.class, () -> runner(logs, sink).start(COMET, command));

            assertTrue(
                    notStarted.getMessage().contains("the working directory does not exist"),
                    "the process service's own diagnostic, which was: " + notStarted.getMessage());
            List<String> lines = linesOf(logs.resolve("comet.log"));
            assertEquals(3, lines.size(), "two header lines and the reason nothing followed");
            assertEquals(AT_TEXT + " [cometgui] stage comet started in " + missing, lines.get(0));
            assertTrue(
                    lines.get(2)
                            .startsWith(
                                    AT_TEXT
                                            + " [cometgui] stage comet could not be started:"
                                            + " java.io.IOException: the working directory does"
                                            + " not exist"),
                    "the third line was: " + lines.get(2));
            assertEquals(
                    List.of(),
                    openDescriptorsFor(logs.resolve("comet.log")),
                    "and the file is closed rather than left open with two lines in it");
        }

        @Test
        @DisplayName("an unchecked failure from the process runner closes the log too")
        void anUncheckedFailure(@TempDir Path tmp) throws IOException {
            Path logs = tmp.resolve("logs");
            ProcessRunner exploding =
                    (command, listener) -> {
                        throw new IllegalStateException("the launcher exploded");
                    };
            StageRunner runner =
                    new StageRunner(
                            exploding,
                            FIXED,
                            ProcessRedactor.with(SecretRegistry.empty()),
                            new RecordingSink(),
                            logs);

            assertThrows(
                    IllegalStateException.class,
                    () -> runner.start(COMET, FakeTools.command(tmp, "exit-code", "0")));

            assertEquals(
                    AT_TEXT
                            + " [cometgui] stage comet could not be started:"
                            + " java.lang.IllegalStateException: the launcher exploded",
                    linesOf(logs.resolve("comet.log")).get(2));
            assertEquals(List.of(), openDescriptorsFor(logs.resolve("comet.log")));
        }

        @Test
        @DisplayName("a console that throws on every line costs the log file nothing")
        void aThrowingSink(@TempDir Path tmp) throws IOException, InterruptedException {
            Path logs = tmp.resolve("logs");
            RunMessageSink throwing =
                    message -> {
                        throw new IllegalStateException("the console is broken");
                    };
            StageRunner runner =
                    new StageRunner(
                            new ProcessService(Clock.systemUTC()),
                            FIXED,
                            ProcessRedactor.with(SecretRegistry.empty()),
                            throwing,
                            logs);

            StageOutcome outcome =
                    runner.start(COMET, FakeTools.command(tmp, "interleave", "5")).awaitOutcome();

            assertEquals(0, outcome.exitCode());
            assertEquals(5L, outcome.standardOutputLines());
            assertEquals(5L, outcome.standardErrorLines());
            assertEquals(0L, outcome.logWriteFailures());
            assertEquals(
                    13,
                    linesOf(outcome.logFile()).size(),
                    "the disk is the run's record and a broken console must not cost it a line");
            assertEquals(
                    IntStream.range(0, 5)
                            .mapToObj(line -> AT_TEXT + " [stdout] out " + line)
                            .toList(),
                    tagged(linesOf(outcome.logFile()), "stdout"));
        }
    }

    /**
     * A clock that moves by a fixed step on every read, and counts the reads.
     *
     * <p>Deterministic where a system clock is not: the instants a test asserts are then a function
     * of how many times the code under test read the clock, which is exactly the property {@code
     * R-PROC-01} exists to give a test.
     */
    private static final class SteppingClock extends Clock {

        private final Instant base;
        private final Duration step;
        private final AtomicLong reads = new AtomicLong();

        private SteppingClock(Instant base, Duration step) {
            this.base = base;
            this.step = step;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return base.plus(step.multipliedBy(reads.getAndIncrement()));
        }

        private long reads() {
            return reads.get();
        }
    }
}
