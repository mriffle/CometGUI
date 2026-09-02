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

package fakes;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A badly-behaved scientific tool, on demand.
 *
 * <p><strong>This is a test fixture and nothing else.</strong> It lives under
 * {@code src/test/resources} on purpose: Maven copies it as a resource rather than compiling it,
 * and {@code org.cometgui.tools.process.fakes.FakeTools} compiles it in process, once per JVM, into
 * {@code target/fake-tools/classes}. No product logic may ever live here, and nothing in the
 * product may ever depend on it. Because it is not under a source root it is not seen by Spotless
 * or Checkstyle; it is written to their rules anyway.
 *
 * <p>It has no dependency beyond the JDK and is launched as a real external process:
 *
 * <pre>java -cp target/fake-tools/classes fakes.FakeTool &lt;scenario&gt; [args...]</pre>
 *
 * <p>An unknown scenario, a wrong argument count or an unparsable argument prints a usage message
 * naming the scenario to stderr and exits {@code 64} ({@code EX_USAGE}).
 *
 * <p><strong>Encoding.</strong> Every text stream this program writes is constructed with an
 * explicit UTF-8 charset rather than the platform default, and every file it writes is UTF-8. The
 * one scenario that must emit bytes that are not valid UTF-8 writes them to the raw stdout file
 * descriptor instead, below the encoder.
 *
 * <h2>Scenarios</h2>
 *
 * <ol>
 *   <li>{@code interleave <lines>} -- writes {@code out <n>} to stdout and {@code err <n>} to
 *       stderr alternately, {@code n} from 0, flushing after every line, and exits 0. With the two
 *       streams merged the order is {@code out 0}, {@code err 0}, {@code out 1}, ...
 *   <li>{@code exit-code <code>} -- prints {@code exiting <code>} to stdout and the same line to
 *       stderr, then exits {@code <code>}.
 *   <li>{@code write-files <file>=<text> ...} -- creates each file, parent directories included,
 *       containing exactly {@code <text>} plus one trailing newline as UTF-8; prints {@code wrote
 *       <file>} per file, with the path exactly as it was given; exits 0. The name is split at the
 *       FIRST {@code =}, so the text may contain further ones.
 *   <li>{@code missing-output <file>} -- prints {@code pretending to write <file>} and exits 0
 *       without creating anything.
 *   <li>{@code malformed-output <file>} -- writes the exact 29 bytes {@code <<<not the expected
 *       format>>>} with NO trailing newline, prints {@code wrote malformed <file>}, exits 0.
 *   <li>{@code partial-then-fail <file> <bytes> <code>} -- writes exactly {@code <bytes>} bytes of
 *       the repeating ASCII pattern {@code 0123456789} to the file, flushes and CLOSES it, prints
 *       {@code partial <bytes>}, then exits {@code <code>}. The file survives the failure.
 *   <li>{@code delayed-output <file> <preLines>} -- prints {@code working <n>} for {@code n} from 0
 *       to {@code preLines - 1}, then creates the file containing exactly {@code done} with no
 *       trailing newline, then prints {@code created <file> <bytes>} where {@code <bytes>} is read
 *       back OUT OF THE FILE, then exits 0. Reading the size back is what makes the ordering a
 *       fact rather than a comment: the line cannot be printed before the file exists. A test
 *       therefore synchronises on that line instead of sleeping.
 *   <li>{@code flood <totalBytes> <lineLength> <stream>} -- {@code <stream>} is {@code out},
 *       {@code err} or {@code both}. Emits lines of exactly {@code <lineLength>} printable ASCII
 *       characters, excluding the newline, until at least {@code <totalBytes>} bytes -- lines plus
 *       their newlines -- have been written to each selected stream. Every line starts with its
 *       zero-padded 10-digit ordinal and a space, then repeats {@code 0123456789} to the requested
 *       length, so any line can be identified on sight. For {@code out} the final line count is
 *       written as {@code lines <n>} on stderr, for {@code err} on stdout, and for {@code both}
 *       nowhere -- a summary on the stream under test would pollute it. {@code <lineLength>} must
 *       be at least 12.
 *   <li>{@code hang} -- prints {@code hanging}, flushes, then blocks forever. Ends only when
 *       killed.
 *   <li>{@code hang-with-child} -- starts a real child process, {@code fakes.FakeTool hang}, using
 *       this JVM's own executable and class path; waits for the child's {@code hanging} line;
 *       prints {@code child <pid>} with the child's real OS pid; then blocks forever. The child is
 *       an ordinary descendant process and outlives its parent unless something kills it. The
 *       child's stderr is inherited so a child failure is visible.
 *   <li>{@code hang-ignoring-term} -- installs a shutdown hook that prints {@code terminating} and
 *       then blocks forever, prints {@code hanging}, and blocks forever itself. This is the
 *       process that does NOT die on a polite {@code Process.destroy()}: the JVM cannot exit while
 *       a shutdown hook is still running, so only {@code destroyForcibly()} ends it. It exists so
 *       that the process service's escalation from terminate to kill can be proved. The {@code
 *       terminating} line is what makes that provable without a sleep -- its arrival is the event
 *       showing the signal was received and deliberately ignored.
 *   <li>{@code echo-context <envName> ...} -- prints, in this order: {@code argc <n>}; one {@code
 *       arg <i> <value>} line per argument, {@code i} from 0; {@code cwd <absolute path>} from
 *       {@code user.dir}; one {@code env <NAME> <value>} line per requested name, in the order
 *       given, with {@code -absent-} for a name that is not set; and {@code envcount <n>}, the
 *       size of the whole environment. Exits 0. This is what shows an argument array arrived
 *       intact, that the working directory is the one that was asked for, and that the environment
 *       is the one that was constructed.
 *   <li>{@code unicode <file>} -- prints the fixed non-ASCII string {@code café über
 *       日本語 ✓ αβγ} to stdout and then to stderr, writes the
 *       same string to the file as UTF-8 with no trailing newline, exits 0.
 *   <li>{@code invalid-utf8} -- writes the raw bytes {@code 41 C3 28 42 0A} to the stdout file
 *       descriptor, below any encoder: an {@code A}, an invalid two-byte UTF-8 sequence, a {@code
 *       B} and a newline. A decoder that throws on malformed input rather than replacing it kills
 *       the pump reading this.
 *   <li>{@code no-trailing-newline} -- writes {@code first\nlast-without-newline} with no
 *       terminating newline, exits 0. A pump that only emits complete lines loses the last one.
 *   <li>{@code crlf} -- writes the raw bytes of {@code one\r\ntwo\r\n}, exits 0. A pump that splits
 *       on {@code \n} alone leaves a stray carriage return on the end of each line.
 * </ol>
 */
public final class FakeTool {

    /** The fixed non-ASCII sample. Written here as the real characters; the self-test pins it as
     * {@code \\u} escapes, so neither expectation is derived from the other. */
    private static final String UNICODE_SAMPLE = "café über 日本語 ✓ αβγ";

    /** The exact text {@code malformed-output} writes. Not the expected shape of anything. */
    private static final String MALFORMED_TEXT = "<<<not the expected format>>>";

    /** The repeating payload pattern used by {@code partial-then-fail} and {@code flood}. */
    private static final String PATTERN = "0123456789";

    /** Digits in a flood line's ordinal prefix. Ten allows 10^10 lines before a line grows. */
    private static final int ORDINAL_WIDTH = 10;

    /** Flood buffer size; large enough that a 500 MB run is not a syscall benchmark. */
    private static final int FLOOD_BUFFER = 64 * 1024;

    /** The usage exit code, {@code EX_USAGE} from sysexits.h. */
    private static final int EXIT_USAGE = 64;

    /** Reported when the {@code hang-with-child} child fails to start or announce itself. */
    private static final int EXIT_CHILD_FAILED = 70;

    /** Reported when the watchdog on a hanging scenario fires. Never produced by a signal. */
    private static final int EXIT_WATCHDOG = 71;

    /** How long a hanging scenario waits to be killed before halting itself. See blockForever. */
    private static final int WATCHDOG_SECONDS = 300;

    private static final PrintStream OUT =
            new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);

    private static final PrintStream ERR =
            new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);

    private FakeTool() {
        throw new AssertionError("FakeTool is a program, not a type to instantiate");
    }

    /**
     * Runs one scenario.
     *
     * @param args the scenario name followed by that scenario's arguments
     * @throws IOException if a scenario that writes a file or a stream cannot
     */
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            usage("(none)", "no scenario was given");
            return;
        }
        String scenario = args[0];
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (scenario) {
            case "interleave" -> {
                requireArity(scenario, rest, 1);
                interleave(parseCount(scenario, rest[0]));
            }
            case "exit-code" -> {
                requireArity(scenario, rest, 1);
                exitCode(parseCount(scenario, rest[0]));
            }
            case "write-files" -> {
                requireAtLeast(scenario, rest, 1);
                writeFiles(rest);
            }
            case "missing-output" -> {
                requireArity(scenario, rest, 1);
                OUT.println("pretending to write " + rest[0]);
            }
            case "malformed-output" -> {
                requireArity(scenario, rest, 1);
                malformedOutput(rest[0]);
            }
            case "partial-then-fail" -> {
                requireArity(scenario, rest, 3);
                partialThenFail(
                        rest[0], parseLong(scenario, rest[1]), parseCount(scenario, rest[2]));
            }
            case "delayed-output" -> {
                requireArity(scenario, rest, 2);
                delayedOutput(rest[0], parseCount(scenario, rest[1]));
            }
            case "flood" -> {
                requireArity(scenario, rest, 3);
                flood(parseLong(scenario, rest[0]), parseCount(scenario, rest[1]), rest[2]);
            }
            case "hang" -> {
                requireArity(scenario, rest, 0);
                OUT.println("hanging");
                blockForever();
            }
            case "hang-with-child" -> {
                requireArity(scenario, rest, 0);
                hangWithChild();
            }
            case "hang-ignoring-term" -> {
                requireArity(scenario, rest, 0);
                hangIgnoringTerm();
            }
            case "echo-context" -> echoContext(rest);
            case "unicode" -> {
                requireArity(scenario, rest, 1);
                unicode(rest[0]);
            }
            case "invalid-utf8" -> {
                requireArity(scenario, rest, 0);
                writeRawToStdout(new byte[] {0x41, (byte) 0xC3, 0x28, 0x42, 0x0A});
            }
            case "no-trailing-newline" -> {
                requireArity(scenario, rest, 0);
                writeRawToStdout(
                        "first\nlast-without-newline".getBytes(StandardCharsets.US_ASCII));
            }
            case "crlf" -> {
                requireArity(scenario, rest, 0);
                writeRawToStdout("one\r\ntwo\r\n".getBytes(StandardCharsets.US_ASCII));
            }
            default -> usage(scenario, "unknown scenario");
        }
    }

    // ------------------------------------------------------------------ scenarios --

    private static void interleave(int lines) {
        for (int line = 0; line < lines; line++) {
            OUT.println("out " + line);
            OUT.flush();
            ERR.println("err " + line);
            ERR.flush();
        }
    }

    private static void exitCode(int code) {
        OUT.println("exiting " + code);
        ERR.println("exiting " + code);
        OUT.flush();
        ERR.flush();
        System.exit(code);
    }

    private static void writeFiles(String[] assignments) throws IOException {
        for (String assignment : assignments) {
            int separator = assignment.indexOf('=');
            if (separator < 0) {
                usage("write-files", "expected <file>=<text> but got: " + assignment);
                return;
            }
            String name = assignment.substring(0, separator);
            String text = assignment.substring(separator + 1);
            Path file = Path.of(name);
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, text + "\n", StandardCharsets.UTF_8);
            OUT.println("wrote " + name);
        }
    }

    private static void malformedOutput(String name) throws IOException {
        Files.writeString(Path.of(name), MALFORMED_TEXT, StandardCharsets.UTF_8);
        OUT.println("wrote malformed " + name);
    }

    private static void partialThenFail(String name, long bytes, int code) throws IOException {
        byte[] pattern = PATTERN.getBytes(StandardCharsets.US_ASCII);
        try (OutputStream file =
                new BufferedOutputStream(Files.newOutputStream(Path.of(name)), FLOOD_BUFFER)) {
            for (long written = 0; written < bytes; written++) {
                file.write(pattern[(int) (written % pattern.length)]);
            }
            file.flush();
        }
        OUT.println("partial " + bytes);
        OUT.flush();
        System.exit(code);
    }

    private static void delayedOutput(String name, int preLines) throws IOException {
        for (int line = 0; line < preLines; line++) {
            OUT.println("working " + line);
        }
        Path file = Path.of(name);
        Files.writeString(file, "done", StandardCharsets.UTF_8);
        /*
         * The size is read back OUT OF THE FILE rather than remembered, so the announcement
         * cannot be emitted before the file exists: reordering these two statements makes
         * Files.size throw NoSuchFileException and the scenario fail loudly.  Without that the
         * ordering is only a comment, and a test that waits for the marker and then opens the
         * file would go intermittently red for a reason no one could reproduce.  Found by the
         * phase orchestrator, by moving the announcement above the write and watching the
         * self-test stay green.
         */
        OUT.println("created " + name + " " + Files.size(file));
    }

    private static void flood(long totalBytes, int lineLength, String stream) throws IOException {
        if (lineLength < ORDINAL_WIDTH + 2) {
            usage("flood", "lineLength must be at least " + (ORDINAL_WIDTH + 2));
            return;
        }
        boolean toOut = "out".equals(stream) || "both".equals(stream);
        boolean toErr = "err".equals(stream) || "both".equals(stream);
        if (!toOut && !toErr) {
            usage("flood", "stream must be out, err or both but was: " + stream);
            return;
        }
        List<OutputStream> targets = new ArrayList<>(2);
        if (toOut) {
            targets.add(new BufferedOutputStream(new FileOutputStream(FileDescriptor.out),
                    FLOOD_BUFFER));
        }
        if (toErr) {
            targets.add(new BufferedOutputStream(new FileOutputStream(FileDescriptor.err),
                    FLOOD_BUFFER));
        }
        byte[] line = floodTemplate(lineLength);
        long ordinal = 0;
        long written = 0;
        while (written < totalBytes) {
            stampOrdinal(line, ordinal);
            for (OutputStream target : targets) {
                target.write(line);
            }
            written += line.length;
            ordinal++;
        }
        for (OutputStream target : targets) {
            target.flush();
        }
        if (toOut && !toErr) {
            ERR.println("lines " + ordinal);
        } else if (toErr && !toOut) {
            OUT.println("lines " + ordinal);
        }
    }

    /** A line of the requested length with its padding filled in and its ordinal left blank. */
    private static byte[] floodTemplate(int lineLength) {
        byte[] line = new byte[lineLength + 1];
        line[ORDINAL_WIDTH] = ' ';
        for (int position = ORDINAL_WIDTH + 1; position < lineLength; position++) {
            int offset = position - ORDINAL_WIDTH - 1;
            line[position] = (byte) PATTERN.charAt(offset % PATTERN.length());
        }
        line[lineLength] = '\n';
        return line;
    }

    private static void stampOrdinal(byte[] line, long ordinal) {
        long remaining = ordinal;
        for (int digit = ORDINAL_WIDTH - 1; digit >= 0; digit--) {
            line[digit] = (byte) ('0' + (int) (remaining % 10));
            remaining /= 10;
        }
    }

    private static void hangWithChild() throws IOException {
        /*
         * The one ProcessBuilder in the fakes, and the reason it cannot be avoided: this scenario's
         * whole purpose is to create a genuine OS descendant for the process service's cancellation
         * to have to find, and the child's stderr must be INHERITed so that a child that fails to
         * start is visible rather than silently swallowed -- which Runtime.exec cannot express.
         * The fake is a test fixture compiled outside every module, not product code: R-PROC-02's
         * ArchUnit rule governs org.cometgui.. production classes, which this is not.
         */
        List<String> argv =
                List.of(
                        javaExecutable(),
                        "-cp",
                        System.getProperty("java.class.path"),
                        "fakes.FakeTool",
                        "hang");
        ProcessBuilder builder = new ProcessBuilder(argv);
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        Process child = builder.start();
        String announcement;
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))) {
            announcement = reader.readLine();
        }
        if (!"hanging".equals(announcement)) {
            ERR.println(
                    "fakes.FakeTool hang-with-child: the child did not announce itself, read: "
                            + announcement);
            ERR.flush();
            child.destroyForcibly();
            System.exit(EXIT_CHILD_FAILED);
        }
        OUT.println("child " + child.pid());
        blockForever();
    }

    private static void hangIgnoringTerm() {
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    OUT.println("terminating");
                                    blockForever();
                                },
                                "fake-tool-shutdown-hook"));
        OUT.println("hanging");
        blockForever();
    }

    private static void echoContext(String[] names) {
        OUT.println("argc " + names.length);
        for (int index = 0; index < names.length; index++) {
            OUT.println("arg " + index + " " + names[index]);
        }
        OUT.println("cwd " + System.getProperty("user.dir"));
        for (String name : names) {
            String value = System.getenv(name);
            OUT.println("env " + name + " " + (value == null ? "-absent-" : value));
        }
        OUT.println("envcount " + System.getenv().size());
    }

    private static void unicode(String name) throws IOException {
        OUT.println(UNICODE_SAMPLE);
        ERR.println(UNICODE_SAMPLE);
        Files.writeString(Path.of(name), UNICODE_SAMPLE, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------- plumbing --

    /** Writes bytes to stdout below the encoder, for output that must not be re-encoded. */
    private static void writeRawToStdout(byte[] bytes) throws IOException {
        OutputStream raw = new FileOutputStream(FileDescriptor.out);
        raw.write(bytes);
        raw.flush();
    }

    /**
     * Blocks until this program is killed, or until the watchdog gives up on it.
     *
     * <p><strong>Why a watchdog and not a real forever.</strong> PIT runs the whole test suite once
     * per mutation in a minion JVM it kills at its own timeout. A mutant that breaks cancellation
     * makes a hanging-scenario test block; the minion is killed mid-test, the test's {@code
     * finally} never runs, and this process is reparented to PID 1 -- which, in this container, is
     * not an init that reaps anything. Two mutation runs during phase 03 unit 2 left eight such
     * processes alive for ever. {@code scripts/build.sh} runs that goal in its gates stage, so
     * every full build would do it again.
     *
     * <p><strong>Why it cannot make a cancellation test pass by accident,</strong> which is the
     * only reason a watchdog would be a bad idea here. The bound is {@value #WATCHDOG_SECONDS}
     * seconds, one to two orders of magnitude longer than any timeout in this phase's tests, and
     * the exit is {@link #EXIT_WATCHDOG}, a code no signal produces -- a cancelled process exits
     * 143 for {@code SIGTERM} or 137 for {@code SIGKILL}, and the cancellation tests assert those
     * numbers exactly. A test that only passed because the watchdog fired would see 71 and fail.
     *
     * <p>{@link Runtime#halt} rather than {@link System#exit}, because {@code hang-ignoring-term}
     * deliberately installs a shutdown hook that never returns, and {@code exit} would wait for it.
     */
    private static void blockForever() {
        try {
            if (!new CountDownLatch(1).await(WATCHDOG_SECONDS, TimeUnit.SECONDS)) {
                ERR.println(
                        "fakes.FakeTool: watchdog fired after "
                                + WATCHDOG_SECONDS
                                + "s; nothing killed this process, so it is halting itself");
                ERR.flush();
                Runtime.getRuntime().halt(EXIT_WATCHDOG);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static String javaExecutable() {
        Optional<String> command = ProcessHandle.current().info().command();
        if (command.isPresent()) {
            return command.get();
        }
        String suffix =
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                        ? ".exe"
                        : "";
        return Path.of(System.getProperty("java.home"), "bin", "java" + suffix).toString();
    }

    private static void requireArity(String scenario, String[] arguments, int expected) {
        if (arguments.length != expected) {
            usage(
                    scenario,
                    "expects " + expected + " argument(s) but got " + arguments.length);
        }
    }

    private static void requireAtLeast(String scenario, String[] arguments, int minimum) {
        if (arguments.length < minimum) {
            usage(
                    scenario,
                    "expects at least " + minimum + " argument(s) but got " + arguments.length);
        }
    }

    private static int parseCount(String scenario, String argument) {
        try {
            return Integer.parseInt(argument);
        } catch (NumberFormatException notANumber) {
            usage(scenario, "expected an integer but got: " + argument);
            return 0;
        }
    }

    private static long parseLong(String scenario, String argument) {
        try {
            return Long.parseLong(argument);
        } catch (NumberFormatException notANumber) {
            usage(scenario, "expected an integer but got: " + argument);
            return 0L;
        }
    }

    /** Prints a usage message naming the scenario to stderr and exits {@code 64}. */
    private static void usage(String scenario, String problem) {
        ERR.println("fakes.FakeTool: scenario \"" + scenario + "\": " + problem);
        ERR.println("usage: java fakes.FakeTool <scenario> [args...]");
        ERR.println("scenarios:");
        ERR.println("  interleave <lines>");
        ERR.println("  exit-code <code>");
        ERR.println("  write-files <file>=<text> ...");
        ERR.println("  missing-output <file>");
        ERR.println("  malformed-output <file>");
        ERR.println("  partial-then-fail <file> <bytes> <code>");
        ERR.println("  delayed-output <file> <preLines>");
        ERR.println("  flood <totalBytes> <lineLength> out|err|both");
        ERR.println("  hang");
        ERR.println("  hang-with-child");
        ERR.println("  hang-ignoring-term");
        ERR.println("  echo-context <envName> ...");
        ERR.println("  unicode <file>");
        ERR.println("  invalid-utf8");
        ERR.println("  no-trailing-newline");
        ERR.println("  crlf");
        ERR.flush();
        System.exit(EXIT_USAGE);
    }
}
