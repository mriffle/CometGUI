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
import java.nio.file.Files;
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
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.tools.process.fakes.FakeTools;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives the real process service against the real fake tool, as a real external process.
 *
 * <p><strong>Every expected value below is a hand-typed literal.</strong> Nothing asserts that the
 * service's output equals something the service produced, and no expectation is derived by calling
 * the code that is under test. The one value taken from the running system is a process id, which
 * is an operating-system fact rather than a computed expectation.
 *
 * <p><strong>There is no fixed sleep in this file and there must never be one</strong> (PHASE-03
 * exit gate item 6). Everything waits on a real event: {@link StartedProcess#waitForExit()}, or a
 * marker line arriving through {@link RecordingListener#expect(String)}. The timeouts are failure
 * bounds so a broken build fails instead of hanging, and are never the mechanism.
 *
 * <p>The adversarial cancellation proofs -- a hanging process with a descendant, the 500 MB flood,
 * awkward paths -- belong to later units of this phase. What is here is the core service's own
 * contract.
 */
class ProcessServiceTest {

    /** A failure bound for something that is supposed to happen on its own. Never a delay. */
    private static final int FAILURE_BOUND_SECONDS = 60;

    /** The exact non-ASCII sample the {@code unicode} scenario emits, hand-typed. */
    private static final String UNICODE_SAMPLE = "café über 日本語 ✓ αβγ";

    /**
     * How long the exit is watched for while a line is deliberately held back.
     *
     * <p>A NEGATIVE proof, and short on purpose: the correct implementation cannot report the exit
     * during this window at all, because it is blocked joining a pump that the test is holding, so
     * a longer window would only make the suite slower without making the assertion stronger. It is
     * not a synchronisation: the synchronisation is the marker line and the gate latch.
     */
    private static final int EARLY_EXIT_WATCH_SECONDS = 2;

    /** {@code SIGTERM}: a Unix process killed by signal N reports 128 + N. */
    private static final int EXIT_SIGTERM = 143;

    /** {@code SIGKILL}, which is what the escalation sends. */
    private static final int EXIT_SIGKILL = 137;

    private static ProcessService service() {
        return new ProcessService(Clock.systemUTC());
    }

    private static void stopAndWait(StartedProcess started) {
        started.requestCancellation();
        try {
            started.waitForExit();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * A null the static analyser cannot see through.
     *
     * <p>Proving that {@code start} rejects null means passing it null, and SpotBugs reports
     * exactly that as {@code NP_NULL_PARAM_DEREF_ALL_TARGETS_DANGEROUS}. Routing the null through a
     * collection keeps the test -- deleting it to quieten the analyser would be deleting the check
     * that the guard exists -- without adding an exclusion to the project's SpotBugs filter.
     *
     * @param <T> whatever the call site needs
     * @return null
     */
    private static <T> T opaqueNull() {
        List<T> holder = new ArrayList<>(1);
        holder.add(null);
        return holder.get(0);
    }

    private static void awaitMarker(CountDownLatch latch, String what) throws InterruptedException {
        assertTrue(
                latch.await(FAILURE_BOUND_SECONDS, TimeUnit.SECONDS),
                "the fake never produced " + what + " within the failure bound");
    }

    // =============================================================== streaming ==

    @Nested
    @DisplayName("streaming the two outputs")
    class Streaming {

        @Test
        @DisplayName("every stdout line and every stderr line arrives, each stream in its order")
        void bothStreamsArriveCompleteAndInOrder(@TempDir Path tmp)
                throws IOException, InterruptedException {
            RecordingListener listener = new RecordingListener();

            StartedProcess started =
                    service().start(FakeTools.command(tmp, "interleave", "3"), listener);

            assertEquals(0, started.waitForExit());
            assertEquals(List.of("out 0", "out 1", "out 2"), listener.standardOutput());
            assertEquals(List.of("err 0", "err 1", "err 2"), listener.standardError());
        }

        @Test
        @DisplayName("the streams are never merged: nothing written to stderr appears on stdout")
        void theStreamsAreNeverMerged(@TempDir Path tmp) throws IOException, InterruptedException {
            RecordingListener listener = new RecordingListener();

            StartedProcess started =
                    service().start(FakeTools.command(tmp, "exit-code", "0"), listener);

            assertEquals(0, started.waitForExit());
            assertEquals(List.of("exiting 0"), listener.standardOutput());
            assertEquals(List.of("exiting 0"), listener.standardError());
            assertEquals(
                    List.of("err:exiting 0", "exit:0", "out:exiting 0"),
                    sorted(listener),
                    "exactly three events, sorted so the nondeterministic stream order cannot"
                            + " make this flaky");
        }

        @Test
        @DisplayName("a zero exit code is reported exactly")
        void zeroExitCode(@TempDir Path tmp) throws IOException, InterruptedException {
            RecordingListener listener = new RecordingListener();

            StartedProcess started =
                    service().start(FakeTools.command(tmp, "exit-code", "0"), listener);

            assertEquals(0, started.waitForExit());
            assertEquals(1L, listener.exitReports());
            assertTrue(listener.events().contains("exit:0"));
        }

        @Test
        @DisplayName("a non-zero exit code is reported exactly, not normalised to 1")
        void nonZeroExitCode(@TempDir Path tmp) throws IOException, InterruptedException {
            RecordingListener listener = new RecordingListener();

            StartedProcess started =
                    service().start(FakeTools.command(tmp, "exit-code", "7"), listener);

            assertEquals(7, started.waitForExit());
            assertEquals(List.of("exiting 7"), listener.standardOutput());
            assertEquals(List.of("exiting 7"), listener.standardError());
            assertTrue(listener.events().contains("exit:7"));
        }

        @Test
        @DisplayName("onExit is called EXACTLY ONCE and AFTER the last line of both streams")
        void onExitComesOnceAndLast(@TempDir Path tmp) throws IOException, InterruptedException {
            RecordingListener listener = new RecordingListener();

            StartedProcess started =
                    service().start(FakeTools.command(tmp, "interleave", "100"), listener);
            assertEquals(0, started.waitForExit());

            List<String> events = listener.events();
            assertEquals(201, events.size(), "100 stdout lines, 100 stderr lines and one exit");
            assertEquals("exit:0", events.get(200), "the exit must be the very last event");
            assertEquals(1L, listener.exitReports());
            assertEquals("out 99", listener.standardOutput().get(99));
            assertEquals("err 99", listener.standardError().get(99));
            assertEquals(100, listener.standardOutput().size());
            assertEquals(100, listener.standardError().size());
        }

        @Test
        @DisplayName("waitForExit returns only after the listener has been told")
        void waitForExitReturnsAfterTheListenerWasTold(@TempDir Path tmp)
                throws IOException, InterruptedException {
            RecordingListener listener = new RecordingListener();

            StartedProcess started =
                    service().start(FakeTools.command(tmp, "interleave", "2"), listener);
            int code = started.waitForExit();

            /* Read once, immediately: if the exit were reported after the latch, this snapshot
             * would not contain it, and if a line could still be in flight the size would be
             * short. */
            List<String> events = listener.events();
            assertEquals(0, code);
            assertEquals(5, events.size());
            assertEquals("exit:0", events.get(4));
            assertEquals(0L, started.listenerFailureCount());
        }

        @Test
        @DisplayName("onExit waits for the STDOUT pump: not reported while a line is still held")
        void theExitWaitsForTheStandardOutputPump(@TempDir Path tmp)
                throws IOException, InterruptedException {
            GatedListener listener = new GatedListener("out 1");
            CountDownLatch lastErrorLine = listener.recorded().expect("err 1");

            StartedProcess started =
                    service().start(FakeTools.command(tmp, "interleave", "2"), listener);
            try {
                awaitMarker(lastErrorLine, "the line \"err 1\"");
                /* Standard error has drained and the fake has finished writing, so the process is
                 * ending; the stdout pump is parked inside the listener still holding "out 1".  A
                 * completion thread that did not join the pumps would report the exit now, ahead
                 * of that line, which is exactly what the port forbids. */
                assertFalse(
                        listener.recorded()
                                .exited()
                                .await(EARLY_EXIT_WATCH_SECONDS, TimeUnit.SECONDS),
                        "onExit was reported while a standard output line was still undelivered");
            } finally {
                listener.release();
            }

            assertEquals(0, started.waitForExit());
            List<String> events = listener.recorded().events();
            assertEquals(5, events.size());
            assertEquals("exit:0", events.get(4), "the exit must still be the very last event");
            assertEquals(List.of("out 0", "out 1"), listener.recorded().standardOutput());
            assertFalse(
                    listener.gateExpired(),
                    "the held line must have been released by the test, not by a failure bound");
        }

        @Test
        @DisplayName("onExit waits for the STDERR pump: not reported while a line is still held")
        void theExitWaitsForTheStandardErrorPump(@TempDir Path tmp)
                throws IOException, InterruptedException {
            GatedListener listener = new GatedListener("err 1");
            CountDownLatch lastOutputLine = listener.recorded().expect("out 1");

            StartedProcess started =
                    service().start(FakeTools.command(tmp, "interleave", "2"), listener);
            try {
                awaitMarker(lastOutputLine, "the line \"out 1\"");
                assertFalse(
                        listener.recorded()
                                .exited()
                                .await(EARLY_EXIT_WATCH_SECONDS, TimeUnit.SECONDS),
                        "onExit was reported while a standard error line was still undelivered");
            } finally {
                listener.release();
            }

            assertEquals(0, started.waitForExit());
            List<String> events = listener.recorded().events();
            assertEquals(5, events.size());
            assertEquals("exit:0", events.get(4), "the exit must still be the very last event");
            assertEquals(List.of("err 0", "err 1"), listener.recorded().standardError());
            assertFalse(
                    listener.gateExpired(),
                    "the held line must have been released by the test, not by a failure bound");
        }

        @Test
        @DisplayName("all three threads per process are daemons, so a hung tool cannot pin the JVM")
        void theServiceThreadsAreDaemons(@TempDir Path tmp)
                throws IOException, InterruptedException {
            RecordingListener listener = new RecordingListener();
            CountDownLatch hanging = listener.expect("hanging");

            StartedProcess started = service().start(FakeTools.command(tmp, "hang"), listener);
            try {
                awaitMarker(hanging, "the line \"hanging\"");

                long pid = started.pid();
                assertEquals(
                        Map.of(
                                "cometgui-process-" + pid + "-stdout", Boolean.TRUE,
                                "cometgui-process-" + pid + "-stderr", Boolean.TRUE,
                                "cometgui-process-" + pid + "-exit", Boolean.TRUE),
                        liveThreadsNamed("cometgui-process-" + pid + "-"),
                        "a non-daemon thread here would keep the application alive after the user"
                                + " closed it, for as long as the tool kept running");
            } finally {
                stopAndWait(started);
            }
        }

        @Test
        @DisplayName("callbacks arrive on the service's own named threads, never on the caller's")
        void callbacksArriveOnThePumpThreads(@TempDir Path tmp)
                throws IOException, InterruptedException {
            ThreadNamingListener listener = new ThreadNamingListener();
            String testThread = Thread.currentThread().getName();

            StartedProcess started =
                    service().start(FakeTools.command(tmp, "exit-code", "3"), listener);
            assertEquals(3, started.waitForExit());

            long pid = started.pid();
            assertEquals("cometgui-process-" + pid + "-stdout", listener.standardOutputThread());
            assertEquals("cometgui-process-" + pid + "-stderr", listener.standardErrorThread());
            assertEquals("cometgui-process-" + pid + "-exit", listener.exitThread());
            assertNotEquals(testThread, listener.standardOutputThread());
            assertNotEquals(testThread, listener.standardErrorThread());
            assertNotEquals(testThread, listener.exitThread());
        }

        private List<String> sorted(RecordingListener listener) {
            List<String> events = new ArrayList<>(listener.events());
            events.sort(String::compareTo);
            return events;
        }
    }

    // ============================================================== line rules ==

    @Nested
    @DisplayName("what counts as a line")
    class Lines {

        @Test
        @DisplayName("unicode: the exact sample arrives on both streams")
        void unicodeArrivesIntactOnBothStreams(@TempDir Path tmp)
                throws IOException, InterruptedException {
            RecordingListener listener = new RecordingListener();

            StartedProcess started =
                    service().start(FakeTools.command(tmp, "unicode", "sample.txt"), listener);

            assertEquals(0, started.waitForExit());
            assertEquals(List.of("café über 日本語 ✓ αβγ"), listener.standardOutput());
            assertEquals(List.of("café über 日本語 ✓ αβγ"), listener.standardError());
            assertEquals(
                    19,
                    UNICODE_SAMPLE.length(),
                    "4 + 1 + 4 + 1 + 3 + 1 + 1 + 1 + 3 characters, every one of them decoded"
                            + " from UTF-8 by the pump");
        }

        @Test
        @DisplayName("invalid-utf8: 41 C3 28 42 0A becomes one line reading A, U+FFFD, ( and B")
        void malformedBytesBecomeTheReplacementCharacter(@TempDir Path tmp)
                throws IOException, InterruptedException {
            RecordingListener listener = new RecordingListener();

            StartedProcess started =
                    service().start(FakeTools.command(tmp, "invalid-utf8"), listener);

            assertEquals(0, started.waitForExit());
            assertEquals(List.of("A�(B"), listener.standardOutput());
            assertEquals(List.of(), listener.standardError());
        }

        @Test
        @DisplayName("no-trailing-newline: the final unterminated line is delivered")
        void theFinalUnterminatedLineIsDelivered(@TempDir Path tmp)
                throws IOException, InterruptedException {
            RecordingListener listener = new RecordingListener();

            StartedProcess started =
                    service().start(FakeTools.command(tmp, "no-trailing-newline"), listener);

            assertEquals(0, started.waitForExit());
            assertEquals(List.of("first", "last-without-newline"), listener.standardOutput());
        }

        @Test
        @DisplayName("crlf: exactly one and two, with no carriage return left on either")
        void carriageReturnLineFeedLeavesNoStrayCharacter(@TempDir Path tmp)
                throws IOException, InterruptedException {
            RecordingListener listener = new RecordingListener();

            StartedProcess started = service().start(FakeTools.command(tmp, "crlf"), listener);

            assertEquals(0, started.waitForExit());
            assertEquals(List.of("one", "two"), listener.standardOutput());
            assertEquals(3, listener.standardOutput().get(0).length());
            assertEquals(3, listener.standardOutput().get(1).length());
        }

        @Test
        @DisplayName("flood: lines far longer than the cap are split at exactly 65536 characters")
        void longLinesAreSplitAtTheCap(@TempDir Path tmp) throws IOException, InterruptedException {
            RecordingListener listener = new RecordingListener();

            StartedProcess started =
                    service()
                            .start(
                                    FakeTools.command(tmp, "flood", "1000000", "200000", "out"),
                                    listener);

            assertEquals(0, started.waitForExit());
            List<String> out = listener.standardOutput();
            /* The fake writes lines of 200000 characters plus a newline until it has written at
             * least 1000000 bytes, which is 5 lines of 200001 bytes.  The cap splits each of them
             * into 65536 + 65536 + 65536 + 3392, so 20 lines arrive and not one character is
             * lost. */
            assertEquals(20, out.size());
            assertEquals(List.of("lines 5"), listener.standardError());
            long characters = 0;
            for (int group = 0; group < 5; group++) {
                assertEquals(65_536, out.get(group * 4).length());
                assertEquals(65_536, out.get(group * 4 + 1).length());
                assertEquals(65_536, out.get(group * 4 + 2).length());
                assertEquals(3_392, out.get(group * 4 + 3).length());
                characters +=
                        out.get(group * 4).length()
                                + out.get(group * 4 + 1).length()
                                + out.get(group * 4 + 2).length()
                                + out.get(group * 4 + 3).length();
            }
            assertEquals(1_000_000L, characters);
            assertTrue(out.get(0).startsWith("0000000000 0123456789"), out.get(0).substring(0, 21));
            assertTrue(
                    out.get(16).startsWith("0000000004 0123456789"), out.get(16).substring(0, 21));
        }
    }

    // ================================================================= launch ==

    @Nested
    @DisplayName("how the process is launched")
    class Launch {

        @Test
        @DisplayName("the process runs in the working directory it was given, spaces and all")
        void theWorkingDirectoryIsTheOneAskedFor(@TempDir Path tmp)
                throws IOException, InterruptedException {
            Path work = Files.createDirectories(tmp.resolve("work dir"));
            RecordingListener listener = new RecordingListener();

            StartedProcess started =
                    service().start(FakeTools.command(work, "echo-context"), listener);

            assertEquals(0, started.waitForExit());
            assertEquals(
                    List.of("argc 0", "cwd " + work.toRealPath(), "envcount 0"),
                    listener.standardOutput());
        }

        @Test
        @DisplayName(
                "with an empty environment the tool sees NOTHING: envcount 0, no PATH, no HOME")
        void anEmptyEnvironmentIsTrulyEmpty(@TempDir Path tmp)
                throws IOException, InterruptedException {
            RecordingListener listener = new RecordingListener();

            StartedProcess started =
                    service()
                            .start(
                                    FakeTools.command(tmp, "echo-context", "PATH", "HOME", "LANG"),
                                    listener);

            assertEquals(0, started.waitForExit());
            assertEquals(
                    List.of(
                            "argc 3",
                            "arg 0 PATH",
                            "arg 1 HOME",
                            "arg 2 LANG",
                            "cwd " + tmp.toRealPath(),
                            "env PATH -absent-",
                            "env HOME -absent-",
                            "env LANG -absent-",
                            "envcount 0"),
                    listener.standardOutput());
        }

        @Test
        @DisplayName("exactly the two variables given are set, with their exact values")
        void theEnvironmentIsExactlyWhatWasConstructed(@TempDir Path tmp)
                throws IOException, InterruptedException {
            Map<String, String> environment = new LinkedHashMap<>();
            environment.put("COMETGUI_ALPHA", "one value");
            environment.put("COMETGUI_BETA", "two");
            RecordingListener listener = new RecordingListener();

            StartedProcess started =
                    service()
                            .start(
                                    FakeTools.command(
                                            tmp,
                                            environment,
                                            "echo-context",
                                            "COMETGUI_ALPHA",
                                            "COMETGUI_BETA",
                                            "PATH"),
                                    listener);

            assertEquals(0, started.waitForExit());
            assertEquals(
                    List.of(
                            "argc 3",
                            "arg 0 COMETGUI_ALPHA",
                            "arg 1 COMETGUI_BETA",
                            "arg 2 PATH",
                            "cwd " + tmp.toRealPath(),
                            "env COMETGUI_ALPHA one value",
                            "env COMETGUI_BETA two",
                            "env PATH -absent-",
                            "envcount 2"),
                    listener.standardOutput());
        }

        @Test
        @DisplayName("the argument array survives spaces, quotes, semicolons, $(..), backticks, *")
        void theArgumentArrayArrivesIntact(@TempDir Path tmp)
                throws IOException, InterruptedException {
            RecordingListener listener = new RecordingListener();

            StartedProcess started =
                    service()
                            .start(
                                    FakeTools.command(
                                            tmp,
                                            "echo-context",
                                            "a b",
                                            "he said \"hi\"",
                                            "x;y",
                                            "$(id)",
                                            "`id`",
                                            "*"),
                                    listener);

            assertEquals(0, started.waitForExit());
            assertEquals(
                    List.of(
                            "argc 6",
                            "arg 0 a b",
                            "arg 1 he said \"hi\"",
                            "arg 2 x;y",
                            "arg 3 $(id)",
                            "arg 4 `id`",
                            "arg 5 *",
                            "cwd " + tmp.toRealPath(),
                            "env a b -absent-",
                            "env he said \"hi\" -absent-",
                            "env x;y -absent-",
                            "env $(id) -absent-",
                            "env `id` -absent-",
                            "env * -absent-",
                            "envcount 0"),
                    listener.standardOutput());
        }

        @Test
        @DisplayName("a missing executable fails with an IOException naming it")
        void aMissingExecutableIsNamed(@TempDir Path tmp) {
            Path missing = tmp.resolve("no-such-tool");
            ToolCommand command = new ToolCommand(List.of(missing.toString()), tmp, Map.of());

            IOException failure =
                    assertThrows(
                            IOException.class,
                            () -> service().start(command, new RecordingListener()));

            assertTrue(
                    failure.getMessage().startsWith("could not start ToolCommand[argv="),
                    failure.getMessage());
            assertTrue(failure.getMessage().contains(missing.toString()), failure.getMessage());
        }

        @Test
        @DisplayName("a working directory that does not exist fails with an IOException naming it")
        void aMissingWorkingDirectoryIsNamed(@TempDir Path tmp) {
            Path missing = tmp.resolve("not-there");
            ToolCommand command = FakeTools.command(missing, "exit-code", "0");

            IOException failure =
                    assertThrows(
                            IOException.class,
                            () -> service().start(command, new RecordingListener()));

            assertTrue(
                    failure.getMessage()
                            .startsWith(
                                    "the working directory does not exist or is not a directory: "
                                            + missing),
                    failure.getMessage());
        }

        @Test
        @DisplayName(
                "a working directory that is a file, not a directory, is rejected the same way")
        void aWorkingDirectoryThatIsAFileIsRejected(@TempDir Path tmp) throws IOException {
            Path file = Files.writeString(tmp.resolve("a-file.txt"), "not a directory\n");
            ToolCommand command = FakeTools.command(file, "exit-code", "0");

            IOException failure =
                    assertThrows(
                            IOException.class,
                            () -> service().start(command, new RecordingListener()));

            assertTrue(
                    failure.getMessage()
                            .startsWith(
                                    "the working directory does not exist or is not a directory: "
                                            + file),
                    failure.getMessage());
        }

        @Test
        @DisplayName("null arguments are rejected before anything is launched")
        void nullArgumentsAreRejected(@TempDir Path tmp) {
            ToolCommand command = FakeTools.command(tmp, "exit-code", "0");
            ToolCommand noCommand = opaqueNull();
            ProcessListener noListener = opaqueNull();

            assertTrue(
                    assertThrows(
                                    NullPointerException.class,
                                    () -> service().start(noCommand, new RecordingListener()))
                            .getMessage()
                            .contains("command"));
            assertTrue(
                    assertThrows(
                                    NullPointerException.class,
                                    () -> service().start(command, noListener))
                            .getMessage()
                            .contains("listener"));
        }

        @Test
        @DisplayName("standard input is closed, so a tool that reads it sees end of file at once")
        void standardInputIsClosed() throws IOException {
            FakeProcess process = new FakeProcess(null);

            ProcessService.closeStandardInput(process);

            assertTrue(
                    process.standardInputWasClosed(),
                    "the pipe to the tool must be closed at start, or a tool that reads standard"
                            + " input blocks for ever on a terminal that is not there");
        }

        @Test
        @DisplayName("a standard input pipe that cannot be closed is reported, not ignored")
        void aPipeThatWillNotCloseIsReported() {
            FakeProcess process = new FakeProcess(new IOException("pipe stuck"));

            IOException failure =
                    assertThrows(
                            IOException.class, () -> ProcessService.closeStandardInput(process));

            assertEquals("pipe stuck", failure.getMessage());
            assertTrue(process.standardInputWasClosed());
        }
    }

    // ============================================================== the clock ==

    @Nested
    @DisplayName("timestamps and duration")
    class Timing {

        private static final Instant START = Instant.parse("2026-08-31T09:15:00Z");
        private static final Instant END = Instant.parse("2026-08-31T09:15:07.500Z");

        /** Never returned if the service reads the clock exactly twice, which it must. */
        private static final Instant TRAP = Instant.parse("1999-12-31T23:59:59Z");

        @Test
        @DisplayName("the duration comes from the injected clock and is exact")
        void theDurationComesFromTheInjectedClock(@TempDir Path tmp)
                throws IOException, InterruptedException {
            SteppingClock clock = new SteppingClock(List.of(START, END, TRAP));
            ProcessService service =
                    new ProcessService(clock, StandardCharsets.UTF_8, Duration.ofSeconds(5));
            RecordingListener listener = new RecordingListener();

            StartedProcess started =
                    service.start(FakeTools.command(tmp, "exit-code", "0"), listener);
            assertEquals(0, started.waitForExit());

            assertEquals(START, started.startedAt());
            assertEquals(Optional.of(END), started.endedAt());
            assertEquals(Optional.of(Duration.ofMillis(7500)), started.duration());
            assertEquals(
                    2,
                    clock.reads(),
                    "the clock must be read exactly twice: once at start, once at exit");
        }

        @Test
        @DisplayName("while the process runs there is no end instant and no duration")
        void nothingIsReportedWhileTheProcessRuns(@TempDir Path tmp)
                throws IOException, InterruptedException {
            SteppingClock clock = new SteppingClock(List.of(START, END, TRAP));
            ProcessService service =
                    new ProcessService(clock, StandardCharsets.UTF_8, Duration.ofSeconds(5));
            RecordingListener listener = new RecordingListener();
            CountDownLatch hanging = listener.expect("hanging");

            StartedProcess started = service.start(FakeTools.command(tmp, "hang"), listener);
            try {
                awaitMarker(hanging, "the line \"hanging\"");

                assertEquals(START, started.startedAt());
                assertEquals(Optional.empty(), started.endedAt());
                assertEquals(Optional.empty(), started.duration());
                assertTrue(started.isAlive());
            } finally {
                stopAndWait(started);
            }
            assertEquals(Optional.of(END), started.endedAt());
            assertFalse(started.isAlive());
        }

        @Test
        @DisplayName("the command and the pid are carried on the handle")
        void theHandleCarriesTheCommandAndThePid(@TempDir Path tmp)
                throws IOException, InterruptedException {
            ToolCommand command = FakeTools.command(tmp, "exit-code", "0");
            RecordingListener listener = new RecordingListener();

            StartedProcess started = service().start(command, listener);

            assertEquals(command, started.command());
            assertTrue(started.pid() > 0, "a real process has a real pid");
            assertEquals(0, started.waitForExit());
        }
    }

    // =========================================================== cancellation ==

    @Nested
    @DisplayName("cancellation")
    class Cancellation {

        @Test
        @DisplayName("a hanging process is terminated, and its output up to that point survives")
        void aHangingProcessIsTerminated(@TempDir Path tmp)
                throws IOException, InterruptedException {
            RecordingListener listener = new RecordingListener();
            CountDownLatch hanging = listener.expect("hanging");

            StartedProcess started = service().start(FakeTools.command(tmp, "hang"), listener);
            try {
                awaitMarker(hanging, "the line \"hanging\"");
                assertFalse(started.isCancellationRequested());

                started.requestCancellation();

                assertEquals(EXIT_SIGTERM, started.waitForExit());
                assertFalse(started.isAlive());
                assertTrue(started.isCancellationRequested());
                assertEquals(List.of("out:hanging", "exit:143"), listener.events());
                assertEquals(1L, listener.exitReports());
            } finally {
                stopAndWait(started);
            }
        }

        @Test
        @DisplayName("cancelling twice, and cancelling after the exit, does nothing more")
        void cancellationIsIdempotent(@TempDir Path tmp) throws IOException, InterruptedException {
            RecordingListener listener = new RecordingListener();
            CountDownLatch hanging = listener.expect("hanging");

            StartedProcess started = service().start(FakeTools.command(tmp, "hang"), listener);
            awaitMarker(hanging, "the line \"hanging\"");

            started.requestCancellation();
            started.requestCancellation();
            assertEquals(EXIT_SIGTERM, started.waitForExit());
            started.requestCancellation();
            started.requestCancellation();

            assertEquals(EXIT_SIGTERM, started.waitForExit());
            assertEquals(List.of("out:hanging", "exit:143"), listener.events());
            assertEquals(1L, listener.exitReports());
        }

        @Test
        @DisplayName("a process that ignores SIGTERM is killed once the grace has passed")
        void aProcessThatIgnoresTerminationIsKilled(@TempDir Path tmp)
                throws IOException, InterruptedException {
            ProcessService impatient =
                    new ProcessService(
                            Clock.systemUTC(), StandardCharsets.UTF_8, Duration.ofSeconds(1));
            RecordingListener listener = new RecordingListener();
            CountDownLatch hanging = listener.expect("hanging");
            CountDownLatch terminating = listener.expect("terminating");

            StartedProcess started =
                    impatient.start(FakeTools.command(tmp, "hang-ignoring-term"), listener);
            try {
                awaitMarker(hanging, "the line \"hanging\"");

                started.requestCancellation();

                awaitMarker(
                        terminating,
                        "the line \"terminating\", which proves SIGTERM arrived and was ignored");
                assertEquals(EXIT_SIGKILL, started.waitForExit());
                assertFalse(started.isAlive());
                assertEquals(
                        List.of("out:hanging", "out:terminating", "exit:137"), listener.events());
                assertEquals(1L, listener.exitReports());
            } finally {
                stopAndWait(started);
            }
        }
    }

    // ======================================================== listener faults ==

    @Nested
    @DisplayName("a listener that misbehaves")
    class ListenerFaults {

        @Test
        @DisplayName("a listener that throws on every callback loses no output and is counted")
        void aThrowingListenerLosesNothing(@TempDir Path tmp)
                throws IOException, InterruptedException {
            AlwaysThrowingListener listener = new AlwaysThrowingListener();

            StartedProcess started =
                    service().start(FakeTools.command(tmp, "interleave", "3"), listener);

            assertEquals(0, started.waitForExit());
            assertEquals(List.of("out 0", "out 1", "out 2"), listener.recorded().standardOutput());
            assertEquals(List.of("err 0", "err 1", "err 2"), listener.recorded().standardError());
            assertEquals(1L, listener.recorded().exitReports());
            assertEquals(
                    7L,
                    started.listenerFailureCount(),
                    "three stdout lines, three stderr lines and the exit");
            assertEquals(
                    Optional.of("java.lang.IllegalStateException: the console is broken"),
                    started.firstListenerFailure());
        }
    }

    // ============================================================ construction ==

    @Nested
    @DisplayName("constructing the service")
    class Construction {

        @Test
        @DisplayName("a null clock, charset or grace is rejected, naming the argument")
        void nullArgumentsAreRejected() {
            assertTrue(
                    assertThrows(NullPointerException.class, () -> new ProcessService(null))
                            .getMessage()
                            .contains("clock"));
            assertTrue(
                    assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            new ProcessService(
                                                    Clock.systemUTC(), null, Duration.ofSeconds(1)))
                            .getMessage()
                            .contains("charset"));
            assertTrue(
                    assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            new ProcessService(
                                                    Clock.systemUTC(),
                                                    StandardCharsets.UTF_8,
                                                    null))
                            .getMessage()
                            .contains("terminationGrace"));
        }

        @Test
        @DisplayName("a zero or negative termination grace is rejected, naming the value")
        void aNonPositiveGraceIsRejected() {
            assertEquals(
                    "terminationGrace must be positive, but was: PT0S",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            new ProcessService(
                                                    Clock.systemUTC(),
                                                    StandardCharsets.UTF_8,
                                                    Duration.ZERO))
                            .getMessage());
            assertEquals(
                    "terminationGrace must be positive, but was: PT-2S",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            new ProcessService(
                                                    Clock.systemUTC(),
                                                    StandardCharsets.UTF_8,
                                                    Duration.ofSeconds(-2)))
                            .getMessage());
        }
    }

    // ================================================================ helpers ==

    /**
     * The live threads whose names begin with {@code prefix}, and whether each is a daemon.
     *
     * @param prefix the name prefix to look for
     * @return name to daemon flag, sorted by name
     */
    private static Map<String, Boolean> liveThreadsNamed(String prefix) {
        Map<String, Boolean> found = new TreeMap<>();
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.getName().startsWith(prefix)) {
                found.put(thread.getName(), thread.isDaemon());
            }
        }
        return found;
    }

    /**
     * Parks one stream's pump inside a callback, before the line is recorded, until released.
     *
     * <p>That is what turns "the exit is reported after the last line" from a statement that
     * happens to hold into one that is proved: with a pump held, an implementation that did not
     * join the pumps before reporting would report the exit early, and this listener is what makes
     * that visible.
     */
    private static final class GatedListener implements ProcessListener {

        private final RecordingListener recorder = new RecordingListener();
        private final CountDownLatch gate = new CountDownLatch(1);
        private final AtomicBoolean gateExpired = new AtomicBoolean();
        private final String heldLine;

        private GatedListener(String heldLine) {
            this.heldLine = heldLine;
        }

        private RecordingListener recorded() {
            return recorder;
        }

        private void release() {
            gate.countDown();
        }

        /**
         * Whether the held line was let through by a timeout rather than by the test.
         *
         * @return true if the failure bound expired, which makes the run's ordering meaningless
         */
        private boolean gateExpired() {
            return gateExpired.get();
        }

        @Override
        public void onStandardOutput(String line) {
            hold(line);
            recorder.onStandardOutput(line);
        }

        @Override
        public void onStandardError(String line) {
            hold(line);
            recorder.onStandardError(line);
        }

        @Override
        public void onExit(int exitCode) {
            recorder.onExit(exitCode);
        }

        private void hold(String line) {
            if (!heldLine.equals(line)) {
                return;
            }
            try {
                if (!gate.await(FAILURE_BOUND_SECONDS, TimeUnit.SECONDS)) {
                    gateExpired.set(true);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Remembers which thread each kind of callback arrived on. */
    private static final class ThreadNamingListener implements ProcessListener {

        private volatile String standardOutputThread;
        private volatile String standardErrorThread;
        private volatile String exitThread;

        @Override
        public void onStandardOutput(String line) {
            standardOutputThread = Thread.currentThread().getName();
        }

        @Override
        public void onStandardError(String line) {
            standardErrorThread = Thread.currentThread().getName();
        }

        @Override
        public void onExit(int exitCode) {
            exitThread = Thread.currentThread().getName();
        }

        private String standardOutputThread() {
            return standardOutputThread;
        }

        private String standardErrorThread() {
            return standardErrorThread;
        }

        private String exitThread() {
            return exitThread;
        }
    }

    /** Records everything and then throws, the way a broken console pane would. */
    private static final class AlwaysThrowingListener implements ProcessListener {

        private final RecordingListener recorder = new RecordingListener();

        @Override
        public void onStandardOutput(String line) {
            recorder.onStandardOutput(line);
            throw new IllegalStateException("the console is broken");
        }

        @Override
        public void onStandardError(String line) {
            recorder.onStandardError(line);
            throw new IllegalStateException("the console is broken");
        }

        @Override
        public void onExit(int exitCode) {
            recorder.onExit(exitCode);
            throw new IllegalStateException("the console is broken");
        }

        private RecordingListener recorded() {
            return recorder;
        }
    }

    /** A clock handing out a fixed sequence, so a duration can be asserted exactly. */
    private static final class SteppingClock extends Clock {

        private final List<Instant> instants;
        private final AtomicInteger reads = new AtomicInteger();

        private SteppingClock(List<Instant> instants) {
            this.instants = List.copyOf(instants);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException("a stepping clock has no zone to change");
        }

        @Override
        public Instant instant() {
            int index = reads.getAndIncrement();
            return instants.get(Math.min(index, instants.size() - 1));
        }

        private int reads() {
            return reads.get();
        }
    }

    /** A process that exists only so that closing its standard input can be observed. */
    private static final class FakeProcess extends Process {

        private final IOException refusal;
        private final AtomicBoolean closed = new AtomicBoolean();

        private FakeProcess(IOException refusal) {
            this.refusal = refusal;
        }

        private boolean standardInputWasClosed() {
            return closed.get();
        }

        /**
         * A fresh stream per call, exactly as a real {@link Process} hands one out, and never the
         * same object twice: a test double that published a field would be a different shape from
         * the thing it stands in for.
         */
        @Override
        public OutputStream getOutputStream() {
            return new OutputStream() {

                @Override
                public void write(int oneByte) throws IOException {
                    throw new IOException("nothing writes to a tool's standard input");
                }

                @Override
                public void close() throws IOException {
                    closed.set(true);
                    if (refusal != null) {
                        throw refusal;
                    }
                }
            };
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
        public int waitFor() {
            return 0;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
            throw new UnsupportedOperationException("a fake process cannot be destroyed");
        }
    }
}
