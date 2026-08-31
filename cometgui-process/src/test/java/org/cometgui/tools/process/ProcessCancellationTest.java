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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.tools.process.fakes.FakeTools;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * PHASE-03 exit gate item 2, and everything that stops it passing for the wrong reason.
 *
 * <p>The gate item is one sentence: <em>"A hanging process with a child is cancelled and neither
 * process survives; the test asserts on process liveness, not on the absence of an exception."</em>
 * Four things have to be true before an assertion about a dead child means anything, and each of
 * them is a separate test below.
 *
 * <ol>
 *   <li><strong>The child was alive, and it was really the child.</strong> {@link GateItemTwo}
 *       looks the announced pid up through {@link ProcessHandle}, proves that handle is alive, and
 *       proves it is in the parent's {@code descendants()} set -- so what is about to be killed is
 *       a genuine descendant rather than an unrelated or already-dead pid.
 *   <li><strong>It would not have died on its own.</strong> The negative control launches the same
 *       scenario, does <em>not</em> cancel, and requires both processes to still be running when a
 *       bounded observation window closes. Without it, a child that exited by itself would make the
 *       cancellation test pass.
 *   <li><strong>It died of the cancellation and of nothing else.</strong> Every exit code is a
 *       hand-typed number. 143 is {@code SIGTERM} and 137 is {@code SIGKILL}; {@value
 *       #EXIT_WATCHDOG} is the fake's own watchdog, which fires five minutes after nothing killed
 *       it, so a test that accepted {@value #EXIT_WATCHDOG} would be proving the opposite of what
 *       it claims. Every exit assertion below therefore also states what the code is not.
 *   <li><strong>Cancelling did not throw the tool's output away.</strong> {@link
 *       OutputSurvivesCancellation} cancels a process mid-flood and requires the delivered lines to
 *       be a truncation of what the tool wrote -- never a corruption of it, and never nothing.
 * </ol>
 *
 * <p><strong>There is no fixed sleep here and there must never be one</strong> (exit gate item 6).
 * Every wait is a real event: a marker line arriving, {@link ProcessHandle#onExit()}, {@link
 * StartedProcess#waitForExit()}. Every timeout is a failure bound, never the mechanism. The one
 * place a duration is <em>asserted</em> rather than waited on is {@link
 * ContractEdges#cancellationReturnsWithoutWaiting}, which measures with {@link System#nanoTime()};
 * a measurement is not a synchronisation.
 *
 * <p><strong>A negative window is an assertion, not a sleep.</strong> Requiring {@code
 * handle.onExit().get(2, SECONDS)} to throw {@link TimeoutException} states that something did
 * <em>not</em> happen, which is the only way to show a process is still running for a reason
 * instead of by luck.
 *
 * <p>Every expected value is hand-typed. The only values taken from the running system are process
 * ids, which are operating-system facts rather than computed expectations.
 */
class ProcessCancellationTest {

    /** A failure bound for something that is supposed to happen on its own. Never a delay. */
    private static final int FAILURE_BOUND_SECONDS = 60;

    /**
     * How long something that must NOT happen is watched for.
     *
     * <p>Short on purpose: the processes it watches block for ever, so a longer window would make
     * the suite slower without making the assertion stronger.
     */
    private static final int NEGATIVE_WINDOW_SECONDS = 2;

    /** {@code SIGTERM}: a Unix process killed by signal N reports 128 + N. */
    private static final int EXIT_SIGTERM = 143;

    /** {@code SIGKILL}, which is what the escalation from terminate to kill sends. */
    private static final int EXIT_SIGKILL = 137;

    /**
     * The fake's own watchdog code, asserted against rather than for.
     *
     * <p>{@code fakes.FakeTool} halts a hanging scenario with this after five minutes, so that a
     * PIT minion killed mid-test cannot leave a process behind for ever. No signal produces it. A
     * cancellation test that saw it would be one whose process timed itself out instead of being
     * cancelled.
     */
    private static final int EXIT_WATCHDOG = 71;

    /** The prefix of the line {@code hang-with-child} announces its child's real pid on. */
    private static final String CHILD_PREFIX = "child ";

    /** What a line the service wrote itself, rather than the tool, begins with. */
    private static final String FAULT_PREFIX = "[cometgui] ";

    /** The characters of one flood line, excluding its newline. */
    private static final int FLOOD_LINE_LENGTH = 100;

    /**
     * The 89 padding characters that follow the ordinal and its space on a 100-character flood
     * line.
     *
     * <p>Hand-typed, exactly as {@code FakeToolSelfTest} types it, so neither expectation is
     * derived from the other and neither is derived from the code under test.
     */
    private static final String FLOOD_PADDING =
            "01234567890123456789012345678901234567890123456789012345678901234567890123456789"
                    + "012345678";

    /**
     * The flood ordinal whose arrival is the signal to cancel.
     *
     * <p>Far enough in that a great deal of output is already flowing, early enough that the fake
     * is certainly still writing.
     */
    private static final int CANCEL_AT_ORDINAL = 500;

    /**
     * A flood total large enough that the fake cannot finish before it is cancelled.
     *
     * <p>Two hundred million bytes is about two million lines. Nothing accumulates them: {@link
     * FloodChecker} checks each line as it arrives and keeps counts, so the size of this number
     * costs no heap.
     */
    private static final String LARGE_FLOOD_TOTAL = "200000000";

    /**
     * The floor on how many flood lines must survive a cancellation taken while the stdout pump is
     * deliberately held.
     *
     * <p>Where the number comes from, because a floor pulled out of the air proves nothing. When
     * the gate closes, the pump has already read one 8192-character chunk out of the stream, which
     * is 81 whole lines of 101 bytes, and the splitter re-delivers those from its own buffer
     * without touching the pipe again. So an implementation that closed the pipe at cancellation
     * still delivers about 81 lines and could pass a floor of, say, 50. A Linux pipe holds 65536
     * bytes -- 649 more lines -- and the JDK's own buffer holds another 8192, so an implementation
     * that leaves the pipe alone and drains it to end of stream delivers several hundred more. This
     * floor sits between the two, and the injection recorded in the phase log confirmed both ends.
     */
    private static final int LINES_THAT_MUST_SURVIVE_A_HELD_PUMP = 300;

    /**
     * The bound on how long {@code requestCancellation()} itself may take.
     *
     * <p>Compared against a termination grace of {@link #LONG_GRACE}: the port says the call
     * returns without waiting, so the two numbers are six times apart and the assertion can neither
     * pass by accident on a fast machine nor fail by accident on a slow one.
     */
    private static final long RETURNS_WITHIN_MILLIS = 5_000L;

    /**
     * A termination grace long enough that a process which ignores {@code SIGTERM} stays alive for
     * the whole test, so its liveness can be asserted without racing the escalation.
     */
    private static final Duration LONG_GRACE = Duration.ofSeconds(30);

    /**
     * A termination grace short enough that the escalation from terminate to kill happens while the
     * test is watching. It is production configuration, not a sleep: the process does the waiting.
     */
    private static final Duration SHORT_GRACE = Duration.ofSeconds(1);

    private static ProcessService service() {
        return new ProcessService(Clock.systemUTC());
    }

    private static ProcessService serviceWithGrace(Duration grace) {
        return new ProcessService(Clock.systemUTC(), StandardCharsets.UTF_8, grace);
    }

    private static void awaitMarker(CountDownLatch latch, String what) throws InterruptedException {
        assertTrue(
                latch.await(FAILURE_BOUND_SECONDS, TimeUnit.SECONDS),
                "the fake never produced " + what + " within the failure bound");
    }

    /**
     * The handle on a freshly started fake.
     *
     * <p>Taken while the process is certainly alive, and kept, because a {@link ProcessHandle}
     * obtained then refuses to signal a recycled pid: destroying through it checks the process's
     * start time. A handle looked up later from the same number carries no such protection, so the
     * clean-up below could kill an unrelated process.
     *
     * @param started the process the service has just returned
     * @return its handle
     */
    private static ProcessHandle handleOf(StartedProcess started) {
        return ProcessHandle.of(started.pid())
                .orElseThrow(
                        () ->
                                new AssertionError(
                                        "the process the service just started, pid "
                                                + started.pid()
                                                + ", has no handle"));
    }

    /**
     * Kills a fake and everything under it, descendants first, and waits for the service to notice.
     *
     * <p><strong>Descendants first is not a style choice.</strong> PID 1 in this container does not
     * reap orphans, so a child killed after its parent has gone becomes a permanent zombie: {@code
     * /proc} still holds it, {@code isAlive()} stays true for ever and {@code onExit()} never
     * completes. Phase 03 unit 1 found that the hard way. Every test in this file therefore ends
     * here, including the ones where the service has already done the job and every {@code
     * destroyForcibly} is a no-op.
     *
     * @param parent the handle taken when the process was started
     * @param alsoKill descendants the test learned about, killed before the parent
     * @param started the service's handle, waited on so the test does not outrun its own clean-up
     */
    private static void stopTree(
            ProcessHandle parent, List<ProcessHandle> alsoKill, StartedProcess started) {
        for (ProcessHandle descendant : alsoKill) {
            descendant.destroyForcibly();
        }
        parent.descendants().forEach(ProcessHandle::destroyForcibly);
        parent.destroyForcibly();
        try {
            started.waitForExit();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * The exact text of one flood line of {@link #FLOOD_LINE_LENGTH} characters.
     *
     * <p>Built from the format {@code fakes.FakeTool} documents and from nothing the service
     * produced. {@code OutputSurvivesCancellation.theExpectedFloodLineIsTheOneTheFakeWrites} pins
     * two of its results against fully hand-typed literals, so the builder is checked rather than
     * trusted.
     *
     * @param ordinal the line's zero-based number
     * @return the line, without its newline
     */
    private static String floodLine(long ordinal) {
        return String.format(Locale.ROOT, "%010d", ordinal) + " " + FLOOD_PADDING;
    }

    // ============================================================== gate item 2 ==

    @Nested
    @DisplayName("gate item 2: a hanging process with a child")
    class GateItemTwo {

        @Test
        @DisplayName("cancelling kills the parent AND its real descendant: both handles go dead")
        void neitherTheParentNorItsChildSurvivesCancellation(@TempDir Path tmp)
                throws IOException, InterruptedException, ExecutionException, TimeoutException {
            RecordingListener listener = new RecordingListener();
            CountDownLatch announced = listener.expectPrefix(CHILD_PREFIX);

            StartedProcess started =
                    service().start(FakeTools.command(tmp, "hang-with-child"), listener);
            ProcessHandle parentHandle = handleOf(started);
            List<ProcessHandle> child = List.of();
            try {
                awaitMarker(announced, "the line \"child <pid>\"");
                long childPid = announcedChildPid(listener);
                ProcessHandle childHandle =
                        ProcessHandle.of(childPid)
                                .orElseThrow(
                                        () ->
                                                new AssertionError(
                                                        "the fake announced child pid "
                                                                + childPid
                                                                + " but no such process exists"));
                child = List.of(childHandle);

                /* THIS TEST IS NOT VACUOUS.  Before anything is cancelled: the announced child
                 * exists, it is running, and it is a genuine descendant of the process about to be
                 * cancelled rather than an unrelated pid. */
                assertTrue(childHandle.isAlive(), "the announced child is running");
                assertTrue(started.isAlive(), "the parent is running");
                assertTrue(
                        parentHandle.descendants().anyMatch(each -> each.pid() == childPid),
                        () ->
                                "the announced child "
                                        + childPid
                                        + " is not among the descendants of the parent "
                                        + started.pid()
                                        + ", which are "
                                        + parentHandle
                                                .descendants()
                                                .map(ProcessHandle::pid)
                                                .toList());
                assertFalse(started.isCancellationRequested());

                started.requestCancellation();

                /* LIVENESS, not the absence of an exception.  The bound is a failure bound: a
                 * process that was cancelled ends on its own, and one that was not never will. */
                parentHandle.onExit().get(FAILURE_BOUND_SECONDS, TimeUnit.SECONDS);
                childHandle.onExit().get(FAILURE_BOUND_SECONDS, TimeUnit.SECONDS);
                assertFalse(parentHandle.isAlive(), "the parent survived the cancellation");
                assertFalse(childHandle.isAlive(), "the child survived the cancellation");
                assertFalse(started.isAlive());

                /* AND IT DIED OF THE CANCELLATION.  143 is SIGTERM.  71 would mean the fake's own
                 * watchdog gave up waiting to be killed -- this test passing for the wrong
                 * reason -- and 137 would mean the polite terminate never worked. */
                int exitCode = started.waitForExit();
                assertEquals(EXIT_SIGTERM, exitCode);
                assertNotEquals(
                        EXIT_WATCHDOG, exitCode, "the fake timed itself out; nothing killed it");
                assertNotEquals(EXIT_SIGKILL, exitCode, "the polite terminate did not work");
                assertEquals(0L, listener.exited().getCount(), "waitForExit returned after onExit");
                assertEquals(1L, listener.exitReports());
                assertEquals("exit:143", lastEvent(listener));
                assertTrue(started.isCancellationRequested());
            } finally {
                stopTree(parentHandle, child, started);
            }
        }

        @Test
        @DisplayName("NEGATIVE CONTROL: without cancelling, neither process dies on its own")
        void withoutCancellationNeitherProcessDies(@TempDir Path tmp)
                throws IOException, InterruptedException {
            RecordingListener listener = new RecordingListener();
            CountDownLatch announced = listener.expectPrefix(CHILD_PREFIX);

            StartedProcess started =
                    service().start(FakeTools.command(tmp, "hang-with-child"), listener);
            ProcessHandle parentHandle = handleOf(started);
            List<ProcessHandle> child = List.of();
            try {
                awaitMarker(announced, "the line \"child <pid>\"");
                long childPid = announcedChildPid(listener);
                ProcessHandle childHandle =
                        ProcessHandle.of(childPid).orElseThrow(AssertionError::new);
                child = List.of(childHandle);

                /* NOTHING IS CANCELLED HERE.  Both processes must still be running when the window
                 * closes: a child that ended of its own accord would make the test above pass
                 * without the service having done anything at all. */
                assertThrows(
                        TimeoutException.class,
                        () -> childHandle.onExit().get(NEGATIVE_WINDOW_SECONDS, TimeUnit.SECONDS),
                        "the child ended although nothing cancelled it");
                assertThrows(
                        TimeoutException.class,
                        () -> parentHandle.onExit().get(NEGATIVE_WINDOW_SECONDS, TimeUnit.SECONDS),
                        "the parent ended although nothing cancelled it");
                assertTrue(childHandle.isAlive(), "the child is still running");
                assertTrue(started.isAlive(), "the parent is still running");
                assertFalse(started.isCancellationRequested());
                assertEquals(0L, listener.exitReports(), "nothing exited, so nothing was told");
            } finally {
                stopTree(parentHandle, child, started);
            }
        }

        private long announcedChildPid(RecordingListener listener) {
            String line =
                    listener.firstLineStartingWith(CHILD_PREFIX)
                            .orElseThrow(
                                    () ->
                                            new AssertionError(
                                                    "the latch fired but no line began with \""
                                                            + CHILD_PREFIX
                                                            + "\"; the lines were "
                                                            + listener.standardOutput()));
            return Long.parseLong(line.substring(CHILD_PREFIX.length()));
        }
    }

    // =========================================================== the escalation ==

    @Nested
    @DisplayName("escalation from terminate to kill")
    class Escalation {

        @Test
        @DisplayName("a process that ignores SIGTERM is STILL ALIVE after the polite terminate")
        void thePoliteTerminateArrivesAndIsSurvived(@TempDir Path tmp)
                throws IOException, InterruptedException {
            RecordingListener listener = new RecordingListener();
            CountDownLatch hanging = listener.expect("hanging");
            CountDownLatch terminating = listener.expect("terminating");

            StartedProcess started =
                    serviceWithGrace(LONG_GRACE)
                            .start(FakeTools.command(tmp, "hang-ignoring-term"), listener);
            ProcessHandle parentHandle = handleOf(started);
            try {
                awaitMarker(hanging, "the line \"hanging\"");

                started.requestCancellation();

                /* "terminating" is printed from INSIDE the shutdown hook, so its arrival is the
                 * proof that SIGTERM was delivered.  The hook never returns, so the JVM cannot
                 * exit, and the grace here is long enough that the escalation is nowhere near. */
                awaitMarker(terminating, "the line \"terminating\" from inside its shutdown hook");
                assertTrue(started.isAlive(), "SIGTERM arrived and the process ignored it");
                assertThrows(
                        TimeoutException.class,
                        () -> parentHandle.onExit().get(NEGATIVE_WINDOW_SECONDS, TimeUnit.SECONDS),
                        "the polite terminate ended it, so no escalation would ever be needed");
                assertTrue(parentHandle.isAlive());
                assertEquals(0L, listener.exitReports(), "it has not exited, so nothing was told");
            } finally {
                stopTree(parentHandle, List.of(), started);
            }
        }

        @Test
        @DisplayName("after the grace it is KILLED: the exit code is 137, not 143 and not 71")
        void afterTheGraceItIsKilled(@TempDir Path tmp) throws IOException, InterruptedException {
            RecordingListener listener = new RecordingListener();
            CountDownLatch hanging = listener.expect("hanging");

            StartedProcess started =
                    serviceWithGrace(SHORT_GRACE)
                            .start(FakeTools.command(tmp, "hang-ignoring-term"), listener);
            ProcessHandle parentHandle = handleOf(started);
            try {
                awaitMarker(hanging, "the line \"hanging\"");

                started.requestCancellation();

                int exitCode = started.waitForExit();
                assertEquals(EXIT_SIGKILL, exitCode);
                assertNotEquals(
                        EXIT_SIGTERM, exitCode, "SIGTERM cannot end a process whose hook blocks");
                assertNotEquals(
                        EXIT_WATCHDOG, exitCode, "the fake timed itself out; nothing killed it");
                assertFalse(started.isAlive());
                assertFalse(parentHandle.isAlive());
                assertEquals(
                        List.of("out:hanging", "out:terminating", "exit:137"), listener.events());
                assertEquals(1L, listener.exitReports());
            } finally {
                stopTree(parentHandle, List.of(), started);
            }
        }
    }

    // ================================================= output survives the kill ==

    @Nested
    @DisplayName("cancellation truncates output, it never corrupts or discards it")
    class OutputSurvivesCancellation {

        @Test
        @DisplayName("the expected flood line is the one the fake writes, character for character")
        void theExpectedFloodLineIsTheOneTheFakeWrites() {
            assertEquals(
                    "0000000000 01234567890123456789012345678901234567890123456789"
                            + "012345678901234567890123456789012345678",
                    floodLine(0));
            assertEquals(
                    "0000000500 01234567890123456789012345678901234567890123456789"
                            + "012345678901234567890123456789012345678",
                    floodLine(CANCEL_AT_ORDINAL));
            assertEquals(FLOOD_LINE_LENGTH, floodLine(0).length());
            assertEquals(FLOOD_LINE_LENGTH, floodLine(CANCEL_AT_ORDINAL).length());
        }

        @Test
        @DisplayName(
                "cancelled mid-flood: every line is intact and the ordinals run from 0 with no gap")
        void aCancelledFloodIsTruncatedButNeverCorrupted(@TempDir Path tmp)
                throws IOException, InterruptedException {
            FloodChecker checker = new FloodChecker(CANCEL_AT_ORDINAL);

            StartedProcess started = service().start(floodCommand(tmp), checker);
            ProcessHandle parentHandle = handleOf(started);
            try {
                awaitMarker(checker.reachedTarget(), "flood line " + CANCEL_AT_ORDINAL);

                started.requestCancellation();

                int exitCode = started.waitForExit();
                assertEquals(EXIT_SIGTERM, exitCode);
                assertNotEquals(
                        EXIT_WATCHDOG, exitCode, "the flood finished; it was not cancelled");
                checker.assertNothingWasCorrupted();
                assertTrue(
                        checker.lineCount() > CANCEL_AT_ORDINAL,
                        () ->
                                "the flood was cancelled after line "
                                        + CANCEL_AT_ORDINAL
                                        + " arrived, so more than that many lines must have been"
                                        + " delivered, but only "
                                        + checker.lineCount()
                                        + " were");
                assertEquals(0L, checker.faultLines(), "cancellation is not a stream fault");
                assertEquals(1L, checker.exitReports());
                assertEquals(
                        checker.lineCount(),
                        checker.lineCountWhenExitWasReported(),
                        "onExit came after the last line");
            } finally {
                stopTree(parentHandle, List.of(), started);
            }
        }

        @Test
        @DisplayName("output already written is not thrown away: a held pump still drains the pipe")
        void outputAlreadyWrittenSurvivesTheCancellation(@TempDir Path tmp)
                throws IOException, InterruptedException {
            FloodChecker checker = new FloodChecker(0);
            checker.holdThePumpFromLine(0);

            StartedProcess started = service().start(floodCommand(tmp), checker);
            ProcessHandle parentHandle = handleOf(started);
            try {
                /* The pump stops taking lines at line 0, so the operating system's pipe fills and
                 * the fake blocks writing into it.  Everything it has written is now in flight: in
                 * the pipe, in the JDK's buffer and in the splitter's.  A cancellation that closed
                 * the pipe -- which is what Process.destroy() does on Linux, and why the service
                 * cancels through ProcessHandle instead -- would throw all of it away. */
                awaitMarker(checker.reachedTarget(), "flood line 0");
                assertEquals(1L, checker.lineCount(), "the pump is held after exactly one line");

                started.requestCancellation();
                checker.releaseThePump();

                int exitCode = started.waitForExit();
                assertEquals(EXIT_SIGTERM, exitCode);
                assertNotEquals(
                        EXIT_WATCHDOG, exitCode, "the flood finished; it was not cancelled");
                assertFalse(checker.wasHeldTooLong(), "the pump was never released");
                checker.assertNothingWasCorrupted();
                assertTrue(
                        checker.lineCount() >= LINES_THAT_MUST_SURVIVE_A_HELD_PUMP,
                        () ->
                                "cancellation discarded output the tool had already written: only "
                                        + checker.lineCount()
                                        + " lines were delivered, and at least "
                                        + LINES_THAT_MUST_SURVIVE_A_HELD_PUMP
                                        + " were in flight when it was cancelled");
                assertEquals(0L, checker.faultLines(), "cancellation is not a stream fault");
                assertEquals(1L, checker.exitReports());
                assertEquals(
                        checker.lineCount(),
                        checker.lineCountWhenExitWasReported(),
                        "onExit came after the last line");
            } finally {
                checker.releaseThePump();
                stopTree(parentHandle, List.of(), started);
            }
        }

        private ToolCommand floodCommand(Path tmp) {
            return FakeTools.command(
                    tmp, "flood", LARGE_FLOOD_TOTAL, Integer.toString(FLOOD_LINE_LENGTH), "out");
        }
    }

    // ======================================================= the contract edges ==

    @Nested
    @DisplayName("the edges of the RunningProcess contract")
    class ContractEdges {

        @Test
        @DisplayName("requestCancellation RETURNS WITHOUT WAITING, with the process still alive")
        void cancellationReturnsWithoutWaiting(@TempDir Path tmp)
                throws IOException, InterruptedException {
            RecordingListener listener = new RecordingListener();
            CountDownLatch hanging = listener.expect("hanging");

            StartedProcess started =
                    serviceWithGrace(LONG_GRACE)
                            .start(FakeTools.command(tmp, "hang-ignoring-term"), listener);
            ProcessHandle parentHandle = handleOf(started);
            try {
                awaitMarker(hanging, "the line \"hanging\"");

                /* A MEASUREMENT, NOT A SYNCHRONISATION: nothing waits on this number. */
                long before = System.nanoTime();
                started.requestCancellation();
                long elapsedMillis = (System.nanoTime() - before) / 1_000_000L;

                assertTrue(
                        elapsedMillis < RETURNS_WITHIN_MILLIS,
                        () ->
                                "requestCancellation blocked for "
                                        + elapsedMillis
                                        + "ms against a termination grace of "
                                        + LONG_GRACE.toMillis()
                                        + "ms; the port says it returns without waiting");
                assertTrue(
                        started.isAlive(),
                        "it returned before the process it cancelled had ended: the point");
                assertTrue(started.isCancellationRequested());
                assertEquals(0L, listener.exitReports());
            } finally {
                stopTree(parentHandle, List.of(), started);
            }
        }

        @Test
        @DisplayName(
                "cancelling a process with a child twice, and again after its exit, is a no-op")
        void cancellationIsIdempotentAroundTheExit(@TempDir Path tmp)
                throws IOException, InterruptedException {
            RecordingListener listener = new RecordingListener();
            CountDownLatch announced = listener.expectPrefix(CHILD_PREFIX);

            StartedProcess started =
                    service().start(FakeTools.command(tmp, "hang-with-child"), listener);
            ProcessHandle parentHandle = handleOf(started);
            try {
                awaitMarker(announced, "the line \"child <pid>\"");

                started.requestCancellation();
                started.requestCancellation();
                assertEquals(EXIT_SIGTERM, started.waitForExit());
                started.requestCancellation();
                started.requestCancellation();

                assertEquals(EXIT_SIGTERM, started.waitForExit());
                assertEquals(1L, listener.exitReports(), "the exit is reported exactly once");
                assertEquals("exit:143", lastEvent(listener));
                assertFalse(started.isAlive());
            } finally {
                stopTree(parentHandle, List.of(), started);
            }
        }

        @Test
        @DisplayName("cancelling a process that already exited normally leaves its exit code alone")
        void cancellingAnExitedProcessDoesNotChangeItsExitCode(@TempDir Path tmp)
                throws IOException, InterruptedException {
            RecordingListener listener = new RecordingListener();

            StartedProcess started =
                    service().start(FakeTools.command(tmp, "exit-code", "3"), listener);
            ProcessHandle parentHandle = handleOf(started);
            try {
                assertEquals(3, started.waitForExit());
                assertFalse(started.isCancellationRequested());

                started.requestCancellation();

                assertEquals(3, started.waitForExit(), "the exit code is the one the tool chose");
                assertNotEquals(EXIT_SIGTERM, started.waitForExit());
                assertNotEquals(EXIT_SIGKILL, started.waitForExit());
                assertEquals(1L, listener.exitReports());
                assertEquals(List.of("exiting 3"), listener.standardOutput());
                assertEquals(List.of("exiting 3"), listener.standardError());
                assertEquals("exit:3", lastEvent(listener));
                assertTrue(started.isCancellationRequested());
            } finally {
                stopTree(parentHandle, List.of(), started);
            }
        }
    }

    // ============================= the order requestCancellation actually uses ==

    @Nested
    @DisplayName("the order requestCancellation actually uses")
    class TerminationOrderInUse {

        /**
         * THE ONE THE REAL PROCESSES CANNOT PROVE, AND WHY IT IS HERE.
         *
         * <p>Taking the descendants snapshot <em>after</em> destroying the parent is the classic
         * bug this whole design exists to avoid: {@code descendants()} is a snapshot, and a dead
         * parent has none, so the children become invisible and survive. It was injected into
         * {@code StartedProcess.requestCancellation} during this unit and <strong>the entire suite
         * stayed green</strong> -- the 128 tests that existed then, this file's real-process gate
         * item 2 test included. The reason is that {@code destroy()} only sends {@code SIGTERM}:
         * the next statement runs microseconds later, long before the parent JVM has died, so the
         * child is still listed and still gets killed. The bug is a race that this machine wins
         * every time, and a test that waited for it to be lost would be a test synchronised by a
         * sleep.
         *
         * <p>So the order is asserted where it is a fact rather than a race: against a process
         * whose handle records what was asked of it. Injecting that reordering now produces {@code
         * [destroy:100, descendants, destroy:200, destroy:100]} against the expectation below.
         */
        @Test
        @DisplayName("the descendants are snapshotted BEFORE anything is destroyed, root last")
        void theDescendantsAreSnapshottedBeforeAnythingIsDestroyed(@TempDir Path tmp) {
            List<String> calls = new ArrayList<>();
            RecordingHandle root = new RecordingHandle(100L, null, calls);
            RecordingHandle child = new RecordingHandle(200L, root, calls);
            root.setDescendants(List.of(child));

            startedProcessFor(root, tmp, LONG_GRACE).requestCancellation();

            assertEquals(List.of("descendants", "destroy:200", "destroy:100"), snapshotOf(calls));
        }

        @Test
        @DisplayName("what is still running when the grace expires is killed in the same order")
        void theEscalationUsesTheSameOrder(@TempDir Path tmp) throws InterruptedException {
            List<String> calls = new ArrayList<>();
            RecordingHandle root = new RecordingHandle(100L, null, calls);
            RecordingHandle child = new RecordingHandle(200L, root, calls);
            root.setDescendants(List.of(child));
            CountDownLatch killed = new CountDownLatch(2);
            root.countDownWhenKilled(killed);
            child.countDownWhenKilled(killed);

            /* Neither handle's onExit is ever completed, so the grace expires and the escalation
             * fires.  The waiting is the production timer's, not the test's. */
            startedProcessFor(root, tmp, SHORT_GRACE).requestCancellation();

            assertTrue(
                    killed.await(FAILURE_BOUND_SECONDS, TimeUnit.SECONDS),
                    "the termination grace expired and nothing was killed");
            assertEquals(
                    List.of(
                            "descendants",
                            "destroy:200",
                            "destroy:100",
                            "destroyForcibly:200",
                            "destroyForcibly:100"),
                    snapshotOf(calls));
        }

        private StartedProcess startedProcessFor(
                RecordingHandle root, Path workingDirectory, Duration grace) {
            return new StartedProcess(
                    new RecordingProcess(root),
                    FakeTools.command(workingDirectory, "hang"),
                    Clock.systemUTC(),
                    Instant.EPOCH,
                    grace,
                    new GuardedListener(new RecordingListener()),
                    new Thread(() -> {}, "not-started-stdout-pump"),
                    new Thread(() -> {}, "not-started-stderr-pump"),
                    new AtomicBoolean());
        }

        private List<String> snapshotOf(List<String> calls) {
            synchronized (calls) {
                return List.copyOf(calls);
            }
        }
    }

    private static String lastEvent(RecordingListener listener) {
        List<String> events = listener.events();
        assertFalse(events.isEmpty(), "the listener recorded nothing at all");
        return events.get(events.size() - 1);
    }

    // ================================================================== helpers ==

    /**
     * Checks a flood line by line as it arrives, and keeps counts rather than lines.
     *
     * <p>A cancelled 200 MB flood delivers more output than a test can hold, so nothing is
     * accumulated. Each line is compared with the exact text {@code fakes.FakeTool} documents for
     * its position, and what survives the run is a count, the first line that did not match, and
     * where the exit was reported relative to the last line.
     *
     * <p><strong>What "not corrupted" means here.</strong> The arrival index <em>is</em> the
     * expected ordinal, so comparing line <em>n</em> with {@code floodLine(n)} asserts the ordinal,
     * the length, the payload and the absence of any gap in one operation. Exactly one line may be
     * short: the last one, if the tool was killed in the middle of the kernel's copy into the pipe.
     * That is a truncation, and it must still be a prefix of the line it was going to be. Anything
     * else is corruption and fails.
     *
     * <p>Thread safe: the two pump threads and the completion thread all call it.
     */
    private static final class FloodChecker implements ProcessListener {

        private final long targetOrdinal;
        private final CountDownLatch reachedTarget = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicLong lines = new AtomicLong();
        private final AtomicLong faults = new AtomicLong();
        private final AtomicLong exits = new AtomicLong();
        private final AtomicLong linesAtExit = new AtomicLong(-1L);
        private final AtomicLong firstMismatchIndex = new AtomicLong(-1L);
        private final AtomicReference<String> firstMismatch = new AtomicReference<>();
        private final AtomicBoolean heldTooLong = new AtomicBoolean();

        private volatile long holdFromLine = -1L;

        FloodChecker(long targetOrdinal) {
            this.targetOrdinal = targetOrdinal;
        }

        /**
         * Holds the pump inside the callback from this line onwards, until it is released.
         *
         * @param ordinal the first line to hold on
         */
        void holdThePumpFromLine(long ordinal) {
            holdFromLine = ordinal;
        }

        /** Lets a held pump carry on. Safe to call more than once and safe to call when idle. */
        void releaseThePump() {
            release.countDown();
        }

        /**
         * A latch counted down when the line the test is waiting for has arrived.
         *
         * @return the latch
         */
        CountDownLatch reachedTarget() {
            return reachedTarget;
        }

        long lineCount() {
            return lines.get();
        }

        long faultLines() {
            return faults.get();
        }

        long exitReports() {
            return exits.get();
        }

        long lineCountWhenExitWasReported() {
            return linesAtExit.get();
        }

        /**
         * Whether the pump gave up waiting to be released.
         *
         * @return true if the failure bound expired while the pump was held
         */
        boolean wasHeldTooLong() {
            return heldTooLong.get();
        }

        /** Fails unless the delivered lines are an undamaged prefix of what the fake wrote. */
        void assertNothingWasCorrupted() {
            String mismatch = firstMismatch.get();
            if (mismatch == null) {
                return;
            }
            long index = firstMismatchIndex.get();
            assertEquals(
                    lines.get() - 1L,
                    index,
                    () ->
                            "flood line "
                                    + index
                                    + " is not what the fake wrote and it is not the last line"
                                    + " delivered, so the output was corrupted rather than"
                                    + " truncated: expected \""
                                    + floodLine(index)
                                    + "\" but got \""
                                    + mismatch
                                    + "\"");
            assertTrue(
                    !mismatch.isEmpty() && floodLine(index).startsWith(mismatch),
                    () ->
                            "the last flood line was truncated to something that is not a prefix"
                                    + " of the line the fake wrote: expected a prefix of \""
                                    + floodLine(index)
                                    + "\" but got \""
                                    + mismatch
                                    + "\"");
        }

        @Override
        public void onStandardOutput(String line) {
            if (line.startsWith(FAULT_PREFIX)) {
                faults.incrementAndGet();
                return;
            }
            long index = lines.getAndIncrement();
            if (!floodLine(index).equals(line) && firstMismatchIndex.compareAndSet(-1L, index)) {
                firstMismatch.set(line);
            }
            if (index >= targetOrdinal) {
                reachedTarget.countDown();
            }
            long hold = holdFromLine;
            if (hold >= 0L && index >= hold) {
                awaitRelease();
            }
        }

        @Override
        public void onStandardError(String line) {
            if (line.startsWith(FAULT_PREFIX)) {
                faults.incrementAndGet();
            }
        }

        @Override
        public void onExit(int exitCode) {
            exits.incrementAndGet();
            linesAtExit.set(lines.get());
        }

        /**
         * Waits to be released.
         *
         * <p>An assertion here would be swallowed: the service wraps a caller's listener in a
         * {@code GuardedListener} that catches everything, exactly so a listener cannot kill a
         * pump. So the failure is recorded in a flag and asserted on the test's own thread.
         */
        private void awaitRelease() {
            boolean interrupted = false;
            try {
                while (true) {
                    try {
                        if (!release.await(FAILURE_BOUND_SECONDS, TimeUnit.SECONDS)) {
                            heldTooLong.set(true);
                        }
                        return;
                    } catch (InterruptedException retry) {
                        interrupted = true;
                    }
                }
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * A process handle that exists only in this test's head, and remembers what was asked of it.
     *
     * <p>It is what makes "the descendants were listed before anything was signalled" a fact. A
     * real process tree cannot say when it was asked for its children, and by the time a test can
     * see the consequence the race has already been won or lost.
     *
     * <p>Thread safe: the escalation runs on a {@link CompletableFuture} timer thread.
     */
    private static final class RecordingHandle implements ProcessHandle {

        private final long pid;
        private final ProcessHandle parent;
        private final List<String> calls;
        private final CompletableFuture<ProcessHandle> exit = new CompletableFuture<>();
        private final AtomicReference<CountDownLatch> killed = new AtomicReference<>();

        private volatile List<ProcessHandle> descendants = List.of();

        RecordingHandle(long pid, ProcessHandle parent, List<String> calls) {
            this.pid = pid;
            this.parent = parent;
            this.calls = calls;
        }

        void setDescendants(List<ProcessHandle> tree) {
            descendants = List.copyOf(tree);
        }

        void countDownWhenKilled(CountDownLatch latch) {
            killed.set(latch);
        }

        @Override
        public long pid() {
            return pid;
        }

        @Override
        public Optional<ProcessHandle> parent() {
            return Optional.ofNullable(parent);
        }

        @Override
        public Stream<ProcessHandle> children() {
            return descendants.stream();
        }

        @Override
        public Stream<ProcessHandle> descendants() {
            record("descendants");
            return descendants.stream();
        }

        @Override
        public Info info() {
            throw new UnsupportedOperationException(
                    "a recording handle has no process information");
        }

        @Override
        public CompletableFuture<ProcessHandle> onExit() {
            return exit.copy();
        }

        @Override
        public boolean supportsNormalTermination() {
            return true;
        }

        @Override
        public boolean destroy() {
            record("destroy:" + pid);
            return true;
        }

        @Override
        public boolean destroyForcibly() {
            record("destroyForcibly:" + pid);
            CountDownLatch latch = killed.get();
            if (latch != null) {
                latch.countDown();
            }
            return true;
        }

        @Override
        public boolean isAlive() {
            return !exit.isDone();
        }

        @Override
        public int compareTo(ProcessHandle other) {
            return Long.compare(pid, other.pid());
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof RecordingHandle handle && handle.pid == pid;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(pid);
        }

        private void record(String call) {
            synchronized (calls) {
                calls.add(call);
            }
        }
    }

    /** A process that exists only to hand {@link StartedProcess} a {@link RecordingHandle}. */
    private static final class RecordingProcess extends Process {

        private final ProcessHandle handle;

        RecordingProcess(ProcessHandle handle) {
            this.handle = handle;
        }

        @Override
        public ProcessHandle toHandle() {
            return handle;
        }

        @Override
        public long pid() {
            return handle.pid();
        }

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public InputStream getErrorStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public boolean isAlive() {
            return handle.isAlive();
        }

        @Override
        public int waitFor() {
            throw new UnsupportedOperationException("nothing waits for a recording process");
        }

        @Override
        public int exitValue() {
            throw new IllegalThreadStateException("a recording process never ends");
        }

        @Override
        public void destroy() {
            throw new UnsupportedOperationException(
                    "the service must cancel through ProcessHandle, never through Process");
        }
    }
}
