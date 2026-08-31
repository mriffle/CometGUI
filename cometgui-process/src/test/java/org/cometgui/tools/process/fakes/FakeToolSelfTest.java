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

package org.cometgui.tools.process.fakes;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.cometgui.domain.ports.ToolCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves every scenario of {@code fakes.FakeTool} behaves exactly as its documentation says, with
 * no process service in the way.
 *
 * <p>This is the ONLY file in the whole phase permitted to use java.lang.ProcessBuilder from test
 * sources, because it must launch the fake without the code under test. Every other test in this
 * phase launches through the process service; if a second file ever needs a ProcessBuilder, that is
 * a sign the service is missing a capability, not that this exception should be widened.
 *
 * <p>There is no fixed sleep anywhere in this file, and there must never be one. Everything waits
 * on a real event: a process exiting, or a marker line arriving on its stdout. The timeouts are
 * failure bounds, so that a broken build fails instead of hanging forever, and never the mechanism
 * by which a test is "synchronised".
 *
 * <p>Every expected value below is a hand-typed literal or is derived from something other than the
 * fake. Nothing here compares the fake's output with the fake's output.
 */
class FakeToolSelfTest {

    /** A failure bound for a scenario that is supposed to finish on its own. */
    private static final int COMPLETION_SECONDS = 60;

    /** A failure bound for a marker line that a hanging scenario is supposed to print. */
    private static final int MARKER_SECONDS = 30;

    /**
     * How long {@code hang-ignoring-term} is watched after a polite terminate. This is a negative
     * proof, and it is short on purpose: the {@code terminating} marker has already established,
     * deterministically, that the signal arrived and that a shutdown hook is blocking, so a longer
     * bound would only make the suite slower without making the assertion stronger.
     */
    private static final int POLITE_TERMINATE_SECONDS = 5;

    /** The exact non-ASCII sample the {@code unicode} scenario emits. */
    private static final String UNICODE_SAMPLE =
            "caf\u00e9 \u00fcber \u65e5\u672c\u8a9e \u2713 \u03b1\u03b2\u03b3";

    /** Its UTF-8 length, pinned independently of anything the fake reports. */
    private static final int UNICODE_SAMPLE_UTF8_BYTES = 32;

    /** The 89 padding characters of a 100-character flood line. */
    private static final String FLOOD_PADDING =
            "01234567890123456789012345678901234567890123456789012345678901234567890123456789"
                    + "012345678";

    /** A directory name with a space and characters outside ASCII. */
    private static final String AWKWARD_DIRECTORY = "r\u00e9pertoire de travail \u2713";

    // ------------------------------------------------------------------ scenario 1 --

    @Test
    @DisplayName("interleave writes the same number of numbered lines to each stream")
    void interleaveWritesNumberedLinesToBothStreams(@TempDir Path tmp) throws IOException {
        Completed completed = run(tmp, "interleave", "3");

        assertEquals(0, completed.exitCode());
        assertEquals("out 0\nout 1\nout 2\n", completed.stdout());
        assertEquals("err 0\nerr 1\nerr 2\n", completed.stderr());
    }

    @Test
    @DisplayName("interleave really alternates: with the streams merged the order is out, err, out")
    void interleaveAlternatesWhenTheStreamsAreMerged(@TempDir Path tmp) throws IOException {
        Completed completed = runMerged(tmp, "interleave", "3");

        assertEquals(0, completed.exitCode());
        assertEquals(
                "out 0\nerr 0\nout 1\nerr 1\nout 2\nerr 2\n",
                completed.stdout(),
                "the fake flushes after every line, so the merged order is fixed");
    }

    // ------------------------------------------------------------------ scenario 2 --

    @Test
    @DisplayName("exit-code exits with the code it was given and names it on both streams")
    void exitCodeReturnsTheRequestedNonZeroCode(@TempDir Path tmp) throws IOException {
        Completed completed = run(tmp, "exit-code", "7");

        assertEquals(7, completed.exitCode());
        assertEquals("exiting 7\n", completed.stdout());
        assertEquals("exiting 7\n", completed.stderr());
    }

    @Test
    @DisplayName("exit-code 0 is the success case, with output on both streams")
    void exitCodeReturnsZeroWithOutputs(@TempDir Path tmp) throws IOException {
        Completed completed = run(tmp, "exit-code", "0");

        assertEquals(0, completed.exitCode());
        assertEquals("exiting 0\n", completed.stdout());
        assertEquals("exiting 0\n", completed.stderr());
    }

    // ------------------------------------------------------------------ scenario 3 --

    @Test
    @DisplayName("write-files creates every file, with parents, containing exactly its text")
    void writeFilesCreatesEveryFileWithExactlyItsText(@TempDir Path tmp) throws IOException {
        Completed completed =
                run(tmp, "write-files", "first.txt=alpha", "nested/second.txt=beta gamma");

        assertEquals(0, completed.exitCode());
        assertEquals("wrote first.txt\nwrote nested/second.txt\n", completed.stdout());
        assertEquals("", completed.stderr());
        Path work = workingDirectory(tmp);
        assertArrayEquals(
                "alpha\n".getBytes(StandardCharsets.UTF_8),
                Files.readAllBytes(work.resolve("first.txt")));
        assertArrayEquals(
                "beta gamma\n".getBytes(StandardCharsets.UTF_8),
                Files.readAllBytes(work.resolve("nested").resolve("second.txt")),
                "an argument containing a space is one argument, not two");
    }

    // ------------------------------------------------------------------ scenario 4 --

    @Test
    @DisplayName("missing-output exits 0 and creates nothing at all")
    void missingOutputExitsZeroWithoutCreatingTheFile(@TempDir Path tmp) throws IOException {
        Completed completed = run(tmp, "missing-output", "expected.txt");

        assertEquals(0, completed.exitCode());
        assertEquals("pretending to write expected.txt\n", completed.stdout());
        assertFalse(
                Files.exists(workingDirectory(tmp).resolve("expected.txt")),
                "the whole point of this scenario is the file that is not there");
    }

    // ------------------------------------------------------------------ scenario 5 --

    @Test
    @DisplayName("malformed-output writes the wrong shape, with no trailing newline")
    void malformedOutputWritesTheWrongShape(@TempDir Path tmp) throws IOException {
        Completed completed = run(tmp, "malformed-output", "result.txt");

        assertEquals(0, completed.exitCode());
        assertEquals("wrote malformed result.txt\n", completed.stdout());
        byte[] written = Files.readAllBytes(workingDirectory(tmp).resolve("result.txt"));
        assertArrayEquals(
                "<<<not the expected format>>>".getBytes(StandardCharsets.UTF_8), written);
        assertEquals(29, written.length, "29 bytes, and not one of them a newline");
    }

    // ------------------------------------------------------------------ scenario 6 --

    @Test
    @DisplayName("partial-then-fail leaves the bytes it wrote behind when it fails")
    void partialThenFailLeavesItsPartialFileBehind(@TempDir Path tmp) throws IOException {
        Completed completed = run(tmp, "partial-then-fail", "half.bin", "25", "3");

        assertEquals(3, completed.exitCode());
        assertEquals("partial 25\n", completed.stdout());
        byte[] written = Files.readAllBytes(workingDirectory(tmp).resolve("half.bin"));
        assertEquals(25, written.length);
        assertArrayEquals(
                "0123456789012345678901234".getBytes(StandardCharsets.US_ASCII),
                written,
                "the file survives the failure, and it is exactly as long as it claimed");
    }

    // ------------------------------------------------------------------ scenario 7 --

    @Test
    @DisplayName("delayed-output creates the file before it announces it")
    void delayedOutputAnnouncesTheFileOnlyAfterCreatingIt(@TempDir Path tmp) throws IOException {
        Completed completed = run(tmp, "delayed-output", "late.txt", "4");

        assertEquals(0, completed.exitCode());
        assertEquals(
                String.join(
                                "\n",
                                "working 0",
                                "working 1",
                                "working 2",
                                "working 3",
                                "created late.txt 4")
                        + "\n",
                completed.stdout());
        assertArrayEquals(
                "done".getBytes(StandardCharsets.UTF_8),
                Files.readAllBytes(workingDirectory(tmp).resolve("late.txt")),
                "the created line carries the size read back out of the file, so it"
                        + " cannot be printed before the file exists");
    }

    // ------------------------------------------------------------------ scenario 8 --

    @Test
    @DisplayName("flood out writes exactly the lines it says, each one identifiable")
    void floodOnStdoutWritesIdentifiableFixedLengthLines(@TempDir Path tmp) throws IOException {
        // 101 bytes per line including the newline; 101000 / 101 is exactly 1000 lines.
        Completed completed = run(tmp, "flood", "101000", "100", "out");

        assertEquals(0, completed.exitCode());
        List<String> lines = completed.stdoutLines();
        assertEquals(1000, lines.size());
        for (int index = 0; index < lines.size(); index++) {
            assertEquals(100, lines.get(index).length(), "line " + index + " is the wrong length");
        }
        assertEquals("0000000000 " + FLOOD_PADDING, lines.get(0));
        assertEquals("0000000999 " + FLOOD_PADDING, lines.get(999));
        assertEquals(
                "lines 1000\n",
                completed.stderr(),
                "the count goes on the other stream so it cannot pollute the one under test");
    }

    @Test
    @DisplayName("flood err floods stderr and puts its summary on stdout")
    void floodOnStderrPutsItsSummaryOnStdout(@TempDir Path tmp) throws IOException {
        // 20200 / 101 is exactly 200 lines.
        Completed completed = run(tmp, "flood", "20200", "100", "err");

        assertEquals(0, completed.exitCode());
        assertEquals(200, completed.stderrLines().size());
        assertEquals("0000000000 " + FLOOD_PADDING, completed.stderrLines().get(0));
        assertEquals("0000000199 " + FLOOD_PADDING, completed.stderrLines().get(199));
        assertEquals("lines 200\n", completed.stdout());
    }

    @Test
    @DisplayName("flood both writes the same flood to each stream and no summary anywhere")
    void floodOnBothStreamsWritesNoSummary(@TempDir Path tmp) throws IOException {
        // 5050 / 101 is exactly 50 lines, on each stream.
        Completed completed = run(tmp, "flood", "5050", "100", "both");

        assertEquals(0, completed.exitCode());
        assertEquals(50, completed.stdoutLines().size());
        assertEquals(50, completed.stderrLines().size());
        assertEquals("0000000049 " + FLOOD_PADDING, completed.stdoutLines().get(49));
        assertArrayEquals(
                completed.stdoutBytes(), completed.stderrBytes(), "both means both, byte for byte");
        assertFalse(completed.stdout().contains("lines "), "no summary on either stream");
        assertFalse(completed.stderr().contains("lines "), "no summary on either stream");
    }

    // ------------------------------------------------------------------ scenario 9 --

    @Test
    @DisplayName("hang announces itself, stays alive, and ends only when it is destroyed")
    void hangStaysAliveUntilItIsDestroyed(@TempDir Path tmp) throws IOException {
        Process process = launchPiped(tmp, "hang");
        try (BufferedReader reader = readerFor(process)) {
            assertEquals("hanging", readLineWithin(reader, "its hanging marker"));
            assertTrue(process.isAlive(), "it blocks forever, so it is still running");

            process.destroy();

            Process exited = awaitExit(process);
            assertFalse(exited.isAlive());
            assertNotEquals(0, exited.exitValue(), "a destroyed process does not exit 0");
        } finally {
            process.destroyForcibly();
        }
    }

    // ----------------------------------------------------------------- scenario 10 --

    @Test
    @DisplayName("hang-with-child starts a real, live OS descendant and reports its pid")
    void hangWithChildStartsARealDescendant(@TempDir Path tmp) throws IOException {
        Process parent = launchPiped(tmp, "hang-with-child");
        List<ProcessHandle> descendants = List.of();
        try (BufferedReader reader = readerFor(parent)) {
            String announcement = readLineWithin(reader, "its child announcement");
            assertTrue(
                    announcement != null && announcement.startsWith("child "),
                    () -> "expected a child announcement, got: " + announcement);
            long childPid = Long.parseLong(announcement.substring("child ".length()));

            ProcessHandle child =
                    ProcessHandle.of(childPid)
                            .orElseThrow(
                                    () ->
                                            new AssertionError(
                                                    "pid " + childPid + " is not a live process"));
            assertTrue(child.isAlive(), "the announced child must still be running");

            /*
             * Snapshot the descendants BEFORE anything is destroyed: once the parent dies its
             * children are reparented and descendants() returns nothing.
             */
            descendants = parent.descendants().toList();
            List<Long> pids = descendants.stream().map(ProcessHandle::pid).toList();
            assertTrue(
                    pids.contains(childPid),
                    () -> "child " + childPid + " is not among the parent's descendants " + pids);

            /*
             * Kill the DESCENDANT FIRST, and only then its parent. Killing the parent first
             * orphans the child, and on a machine whose PID 1 does not reap orphans -- this
             * container's does not -- the killed child stays a zombie forever, /proc/<pid> still
             * exists, and ProcessHandle.isAlive() reports it alive for good. Killed while its
             * parent JVM is still running, the child is reaped by that JVM's process reaper and
             * really does disappear. Registering onExit() before the signal is the same
             * discipline: the watcher is attached while the process is unambiguously alive.
             */
            CompletableFuture<ProcessHandle> childExit = child.onExit();
            child.destroyForcibly();
            awaitWithin(childExit, COMPLETION_SECONDS, "the descendant to exit");
            assertFalse(child.isAlive(), "the descendant must be gone");

            parent.destroyForcibly();
            assertFalse(awaitExit(parent).isAlive(), "and so must the parent be");
        } finally {
            descendants.forEach(ProcessHandle::destroyForcibly);
            parent.destroyForcibly();
        }
    }

    // ----------------------------------------------------------------- scenario 11 --

    @Test
    @DisplayName("hang-ignoring-term survives a polite terminate and dies on a forcible kill")
    void hangIgnoringTermSurvivesTerminateButNotKill(@TempDir Path tmp) throws IOException {
        Process process = launchPiped(tmp, "hang-ignoring-term");
        try (BufferedReader reader = readerFor(process)) {
            assertEquals("hanging", readLineWithin(reader, "its hanging marker"));

            /*
             * The polite terminate is sent through the ProcessHandle, not through
             * Process.destroy(). It is the same SIGTERM, but on Linux Process.destroy() also
             * CLOSES this process's stdin, stdout and stderr, so the marker the shutdown hook is
             * about to print would be unreadable and the next read would fail with "Stream
             * closed". That is worth knowing for the process service itself: its pumps must
             * survive their pipes being closed underneath them at cancellation.
             */
            process.toHandle().destroy();

            assertEquals(
                    "terminating",
                    readLineWithin(reader, "its shutdown-hook marker"),
                    "the terminate did reach the process: its shutdown hook ran");
            assertTrue(
                    process.isAlive(),
                    "the hook blocks forever, so the JVM cannot finish shutting down");
            assertThrows(
                    TimeoutException.class,
                    () -> process.onExit().get(POLITE_TERMINATE_SECONDS, TimeUnit.SECONDS),
                    "a polite terminate must not end this process");

            process.destroyForcibly();

            Process exited = awaitExit(process);
            assertFalse(exited.isAlive());
            assertNotEquals(0, exited.exitValue(), "a killed process does not exit 0");
        } finally {
            process.destroyForcibly();
        }
    }

    // ----------------------------------------------------------------- scenario 12 --

    @Test
    @DisplayName("echo-context reports the argv, working directory and environment it was given")
    void echoContextReportsTheContextItWasGiven(@TempDir Path tmp) throws IOException {
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("COMETGUI_FAKE_ALPHA", "one");
        environment.put("COMETGUI_FAKE_BETA", "two");

        Completed completed =
                runWithEnvironment(
                        tmp,
                        environment,
                        "echo-context",
                        "COMETGUI_FAKE_ALPHA",
                        "COMETGUI_FAKE_MISSING",
                        "COMETGUI_FAKE_BETA");

        assertEquals(0, completed.exitCode());
        String expected =
                String.join(
                                "\n",
                                "argc 3",
                                "arg 0 COMETGUI_FAKE_ALPHA",
                                "arg 1 COMETGUI_FAKE_MISSING",
                                "arg 2 COMETGUI_FAKE_BETA",
                                "cwd " + workingDirectory(tmp).toRealPath(),
                                "env COMETGUI_FAKE_ALPHA one",
                                "env COMETGUI_FAKE_MISSING -absent-",
                                "env COMETGUI_FAKE_BETA two",
                                "envcount 2")
                        + "\n";
        assertEquals(expected, completed.stdout());
    }

    // ----------------------------------------------------------------- scenario 13 --

    @Test
    @DisplayName("unicode emits the sample on both streams and writes it as UTF-8")
    void unicodeEmitsAndWritesTheSample(@TempDir Path tmp) throws IOException {
        Completed completed = run(tmp, "unicode", "sample.txt");

        assertEquals(0, completed.exitCode());
        assertArrayEquals(
                (UNICODE_SAMPLE + "\n").getBytes(StandardCharsets.UTF_8), completed.stdoutBytes());
        assertArrayEquals(
                (UNICODE_SAMPLE + "\n").getBytes(StandardCharsets.UTF_8), completed.stderrBytes());
        byte[] written = Files.readAllBytes(workingDirectory(tmp).resolve("sample.txt"));
        assertArrayEquals(UNICODE_SAMPLE.getBytes(StandardCharsets.UTF_8), written);
        assertEquals(
                UNICODE_SAMPLE_UTF8_BYTES,
                written.length,
                "19 characters, 32 UTF-8 bytes: the encoding is not the platform default");
    }

    // ----------------------------------------------------------------- scenario 14 --

    @Test
    @DisplayName("invalid-utf8 emits exactly the malformed bytes, below any encoder")
    void invalidUtf8EmitsExactlyTheMalformedBytes(@TempDir Path tmp) throws IOException {
        Completed completed = run(tmp, "invalid-utf8");

        assertEquals(0, completed.exitCode());
        assertArrayEquals(
                new byte[] {0x41, (byte) 0xC3, 0x28, 0x42, 0x0A},
                completed.stdoutBytes(),
                "a decoder that throws instead of replacing would kill the pump reading this");
    }

    // ----------------------------------------------------------------- scenario 15 --

    @Test
    @DisplayName("no-trailing-newline leaves its last line unterminated")
    void noTrailingNewlineLeavesItsLastLineUnterminated(@TempDir Path tmp) throws IOException {
        Completed completed = run(tmp, "no-trailing-newline");

        assertEquals(0, completed.exitCode());
        assertArrayEquals(
                "first\nlast-without-newline".getBytes(StandardCharsets.US_ASCII),
                completed.stdoutBytes());
    }

    // ----------------------------------------------------------------- scenario 16 --

    @Test
    @DisplayName("crlf emits carriage returns that a naive line splitter would leave behind")
    void crlfEmitsCarriageReturns(@TempDir Path tmp) throws IOException {
        Completed completed = run(tmp, "crlf");

        assertEquals(0, completed.exitCode());
        assertArrayEquals(
                "one\r\ntwo\r\n".getBytes(StandardCharsets.US_ASCII), completed.stdoutBytes());
    }

    // ---------------------------------------------------------------------- usage --

    @Test
    @DisplayName("an unknown scenario exits 64 and names the scenario on stderr")
    void unknownScenarioExitsSixtyFour(@TempDir Path tmp) throws IOException {
        Completed completed = run(tmp, "definitely-not-a-scenario");

        assertEquals(64, completed.exitCode());
        assertEquals(
                "fakes.FakeTool: scenario \"definitely-not-a-scenario\": unknown scenario",
                completed.stderrLines().get(0));
        assertEquals("", completed.stdout(), "a usage failure says nothing on stdout");
    }

    @Test
    @DisplayName("the wrong number of arguments exits 64 and names the scenario on stderr")
    void wrongArityExitsSixtyFour(@TempDir Path tmp) throws IOException {
        Completed completed = run(tmp, "interleave");

        assertEquals(64, completed.exitCode());
        assertEquals(
                "fakes.FakeTool: scenario \"interleave\": expects 1 argument(s) but got 0",
                completed.stderrLines().get(0));
    }

    // ------------------------------------------------------------------- the harness --

    @Test
    @DisplayName("the fake really is compiled to a non-empty class file")
    void theFakeIsCompiledToARealClassFile() throws IOException {
        Path classes = FakeTools.classesDirectory();
        Path classFile = classes.resolve("fakes").resolve("FakeTool.class");

        assertTrue(Files.isRegularFile(classFile), () -> classFile + " is not a regular file");
        assertTrue(Files.size(classFile) > 0, () -> classFile + " is empty");
        assertTrue(
                classes.endsWith(Path.of("fake-tools", "classes")),
                () -> "expected target/fake-tools/classes, got " + classes);
        byte[] magic = new byte[4];
        System.arraycopy(Files.readAllBytes(classFile), 0, magic, 0, 4);
        assertArrayEquals(
                new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE},
                magic,
                "a class file starts with 0xCAFEBABE, whatever javac said about it");
    }

    @Test
    @DisplayName("argv names this JVM, the compiled class path and the scenario, in that order")
    void argvNamesTheRunningJvmAndTheCompiledClassPath() {
        List<String> argv = FakeTools.argv("interleave", "2");

        assertEquals(6, argv.size());
        assertEquals(
                ProcessHandle.current().info().command().orElseThrow(),
                argv.get(0),
                "the fake runs on the same JVM as the test, not on whatever is first on PATH");
        assertEquals("-cp", argv.get(1));
        assertTrue(
                Path.of(argv.get(2)).endsWith(Path.of("fake-tools", "classes")),
                () -> "expected the compiled classes directory, got " + argv.get(2));
        assertEquals("fakes.FakeTool", argv.get(3));
        assertEquals("interleave", argv.get(4));
        assertEquals("2", argv.get(5));
        assertTrue(Files.isExecutable(FakeTools.javaExecutable()));
    }

    @Test
    @DisplayName("command builds a ToolCommand with the working directory and environment given")
    void commandCarriesTheWorkingDirectoryAndEnvironment(@TempDir Path tmp) {
        ToolCommand withoutEnvironment = FakeTools.command(tmp, "exit-code", "0");
        ToolCommand withEnvironment =
                FakeTools.command(tmp, Map.of("COMETGUI_FAKE_KEY", "value"), "hang");

        assertEquals(tmp, withoutEnvironment.workingDirectory());
        assertEquals(Map.of(), withoutEnvironment.environment());
        assertEquals(6, withoutEnvironment.argv().size());
        assertEquals("exit-code", withoutEnvironment.argv().get(4));
        assertEquals("0", withoutEnvironment.argv().get(5));

        assertEquals(Map.of("COMETGUI_FAKE_KEY", "value"), withEnvironment.environment());
        assertEquals(5, withEnvironment.argv().size());
        assertEquals("hang", withEnvironment.argv().get(4));
    }

    @Test
    @DisplayName("the compiled fake runs from a copied class path whose name contains a space")
    void theFakeRunsFromACopiedClassPathContainingASpace(@TempDir Path tmp) throws IOException {
        Path destination = tmp.resolve("class path with spaces").resolve("classes");

        Path copied = FakeTools.classesDirectoryCopiedTo(destination);

        assertEquals(destination, copied);
        assertTrue(Files.isRegularFile(copied.resolve("fakes").resolve("FakeTool.class")));
        Completed completed = launch(tmp, false, null, FakeTools.argv(copied, "exit-code", "5"));
        assertEquals(5, completed.exitCode());
        assertEquals("exiting 5\n", completed.stdout());
    }

    @Test
    @DisplayName("the compiled fake runs from a copied class path whose name is not ASCII")
    void theFakeRunsFromACopiedClassPathContainingNonAscii(@TempDir Path tmp) throws IOException {
        assumeTrue(
                thisJvmCanNameNonAsciiFiles(),
                "this JVM cannot represent a non-ASCII file name: sun.jnu.encoding is "
                        + System.getProperty("sun.jnu.encoding")
                        + " because the OS locale is not a UTF-8 one. It is fixed by running the"
                        + " build with LANG=C.UTF-8, not by changing this test.");
        Path destination = tmp.resolve(AWKWARD_DIRECTORY).resolve("classes");

        Path copied = FakeTools.classesDirectoryCopiedTo(destination);

        assertEquals(destination, copied);
        assertTrue(Files.isRegularFile(copied.resolve("fakes").resolve("FakeTool.class")));
        Completed completed = launch(tmp, false, null, FakeTools.argv(copied, "exit-code", "5"));
        assertEquals(5, completed.exitCode());
        assertEquals("exiting 5\n", completed.stdout());
    }

    // ------------------------------------------------------------------- plumbing --

    /** What one finished run of the fake left behind. */
    private record Completed(int exitCode, Path stdoutFile, Path stderrFile) {

        private byte[] stdoutBytes() throws IOException {
            return Files.readAllBytes(stdoutFile);
        }

        private byte[] stderrBytes() throws IOException {
            return Files.readAllBytes(stderrFile);
        }

        private String stdout() throws IOException {
            return new String(stdoutBytes(), StandardCharsets.UTF_8);
        }

        private String stderr() throws IOException {
            return new String(stderrBytes(), StandardCharsets.UTF_8);
        }

        private List<String> stdoutLines() throws IOException {
            return Files.readAllLines(stdoutFile, StandardCharsets.UTF_8);
        }

        private List<String> stderrLines() throws IOException {
            return Files.readAllLines(stderrFile, StandardCharsets.UTF_8);
        }
    }

    private static Completed run(Path tmp, String scenario, String... args) throws IOException {
        return launch(tmp, false, null, FakeTools.argv(scenario, args));
    }

    private static Completed runMerged(Path tmp, String scenario, String... args)
            throws IOException {
        return launch(tmp, true, null, FakeTools.argv(scenario, args));
    }

    private static Completed runWithEnvironment(
            Path tmp, Map<String, String> environment, String scenario, String... args)
            throws IOException {
        return launch(tmp, false, environment, FakeTools.argv(scenario, args));
    }

    /**
     * Launches the fake and waits for it to finish, capturing both streams as raw bytes in files so
     * that nothing has to pump a pipe and nothing decodes anything before the assertion sees it.
     */
    private static Completed launch(
            Path tmp, boolean merged, Map<String, String> exactEnvironment, List<String> argv)
            throws IOException {
        Path capture = Files.createDirectories(tmp.resolve("capture"));
        Path out = capture.resolve("stdout.bin");
        Path err = capture.resolve("stderr.bin");
        Files.write(err, new byte[0]);
        ProcessBuilder builder = new ProcessBuilder(argv);
        builder.directory(workingDirectory(tmp).toFile());
        if (exactEnvironment != null) {
            builder.environment().clear();
            builder.environment().putAll(exactEnvironment);
        }
        builder.redirectOutput(out.toFile());
        if (merged) {
            builder.redirectErrorStream(true);
        } else {
            builder.redirectError(err.toFile());
        }
        Process process = builder.start();
        try {
            awaitWithin(process.onExit(), COMPLETION_SECONDS, argv + " to finish");
        } catch (AssertionError stuck) {
            process.destroyForcibly();
            throw stuck;
        }
        return new Completed(process.exitValue(), out, err);
    }

    /** Launches a scenario that never ends on its own, with its streams merged onto one pipe. */
    private static Process launchPiped(Path tmp, String scenario) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(FakeTools.argv(scenario));
        builder.directory(workingDirectory(tmp).toFile());
        builder.redirectErrorStream(true);
        return builder.start();
    }

    private static BufferedReader readerFor(Process process) {
        return new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
    }

    /**
     * Waits for one line to arrive on a stream that a hanging scenario keeps open. The arrival of
     * the line is the synchronisation; the read happens on another thread only so that the wait can
     * carry a failure bound.
     */
    private static String readLineWithin(BufferedReader reader, String what) {
        return awaitWithin(
                CompletableFuture.supplyAsync(() -> readLine(reader)), MARKER_SECONDS, what);
    }

    /**
     * Waits for something a real event will produce. The event is the synchronisation; the timeout
     * exists only so a broken build fails instead of hanging forever, and is never the mechanism by
     * which anything here is timed.
     */
    private static <T> T awaitWithin(Future<T> future, int seconds, String what) {
        try {
            return future.get(seconds, TimeUnit.SECONDS);
        } catch (TimeoutException stuck) {
            throw new AssertionError(
                    "waited " + seconds + " seconds for " + what + " and it never happened", stuck);
        } catch (ExecutionException failed) {
            throw new AssertionError("failed while waiting for " + what, failed.getCause());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for " + what, interrupted);
        }
    }

    private static String readLine(BufferedReader reader) {
        try {
            return reader.readLine();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static Process awaitExit(Process process) {
        return awaitWithin(process.onExit(), COMPLETION_SECONDS, "the process to exit");
    }

    private static Path workingDirectory(Path tmp) throws IOException {
        return Files.createDirectories(tmp.resolve("work"));
    }

    private static boolean thisJvmCanNameNonAsciiFiles() {
        try {
            Path.of(AWKWARD_DIRECTORY);
            return true;
        } catch (InvalidPathException unrepresentable) {
            return false;
        }
    }
}
