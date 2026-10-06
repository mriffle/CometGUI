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

package enginefakes;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A stand-in for a scientific tool, for the workflow engine's tests. One Java program, launched
 * with the JDK's own {@code java} through the process service, so it needs no shell and behaves the
 * same on every platform.
 *
 * <p>Scenarios (the first argument), and what each does:
 *
 * <ul>
 *   <li>{@code succeed <out>...} -- writes {@code complete <file name>} into each output, prints
 *       {@code wrote <n>}, exits 0.
 *   <li>{@code exit <code> [<out>...]} -- writes each output completely, prints a line to standard
 *       error, exits with {@code code}.
 *   <li>{@code no-output <out>} -- exits 0 without writing {@code out}.
 *   <li>{@code partial-then-fail <out> <code>} -- writes {@code partial} (no newline) into {@code
 *       out}, exits with {@code code}.
 *   <li>{@code hang <records> <name> [<out>]} -- writes {@code partial} into {@code out} if given,
 *       records its pid in {@code <records>/<name>.pid}, prints {@code pid <pid>}, and blocks.
 *   <li>{@code hang-with-child <records> <name> [<out>]} -- the same, but first starts a real child
 *       process running {@code hang <records> <name>-child}, waits for it to announce itself, and
 *       prints {@code child <pid>} before its own {@code pid} line.
 *   <li>{@code hold <records> <name> <peers> <millis> [<out>...]} -- records its start instant in
 *       {@code <records>/<name>.start}, waits until {@code peers} holds are running at once (start
 *       records without an end record) or {@code millis} have passed, writes its outputs, records
 *       its end instant in {@code
 *       <records>/<name>.end} and exits 0. The records are the fake's own evidence of when it ran,
 *       independent of the engine's bookkeeping.
 *   <li>{@code fail-after <records> <pids> <code>} -- waits until {@code pids} pid records exist in
 *       {@code records}, then exits with {@code code}: a failure that happens only once its
 *       siblings are provably running.
 * </ul>
 *
 * <p><strong>The watchdog.</strong> Every waiting scenario gives up after {@value
 * #WATCHDOG_SECONDS} seconds and halts with exit code {@value #EXIT_WATCHDOG}, a code no signal
 * produces (a cancelled process exits 143, a killed one 137). A mutation-testing run that kills a
 * test midway would otherwise strand a blocked fake for ever under a PID 1 that reaps nothing; and
 * a cancellation test that only passed because the watchdog fired sees 71 and fails. This is the
 * convention of Phase 03's {@code fakes.FakeTool}.
 *
 * <p><strong>The one ProcessBuilder.</strong> {@code hang-with-child} must create a genuine OS
 * descendant for cancellation to find, and Java can create a process no other way. This program is
 * a test fixture compiled at test time outside every module; the architecture rule confining
 * process creation to the process service governs the product's classes, and every test launches
 * this program itself through the process service.
 */
public final class EngineFake {

    private static final int EXIT_USAGE = 64;

    private static final int EXIT_CHILD_FAILED = 70;

    private static final int EXIT_WATCHDOG = 71;

    private static final int EXIT_PEERS_NEVER_CAME = 72;

    private static final int WATCHDOG_SECONDS = 300;

    private static final long POLL_MILLIS = 5L;

    private static final PrintStream OUT =
            new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);

    private static final PrintStream ERR =
            new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);

    private EngineFake() {}

    /**
     * Runs one scenario.
     *
     * @param args the scenario and its arguments
     * @throws Exception if a scenario cannot do its I/O
     */
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            ERR.println("usage: EngineFake <scenario> <args...>");
            System.exit(EXIT_USAGE);
        }
        List<String> rest = Arrays.asList(Arrays.copyOfRange(args, 1, args.length));
        switch (args[0]) {
            case "succeed" -> {
                for (String out : rest) {
                    writeComplete(Path.of(out));
                }
                OUT.println("wrote " + rest.size());
                System.exit(0);
            }
            case "exit" -> {
                for (String out : rest.subList(1, rest.size())) {
                    writeComplete(Path.of(out));
                }
                ERR.println("failing deliberately with exit code " + rest.get(0));
                System.exit(Integer.parseInt(rest.get(0)));
            }
            case "no-output" -> {
                OUT.println("exiting 0 without writing " + rest.get(0));
                System.exit(0);
            }
            case "partial-then-fail" -> {
                Files.writeString(Path.of(rest.get(0)), "partial", StandardCharsets.UTF_8);
                ERR.println("wrote part of " + rest.get(0) + " and failed");
                System.exit(Integer.parseInt(rest.get(1)));
            }
            case "hang" -> {
                partialIfGiven(rest, 2);
                announce(Path.of(rest.get(0)), rest.get(1));
                blockForever();
            }
            case "hang-with-child" -> {
                partialIfGiven(rest, 2);
                long child = startChild(rest.get(0), rest.get(1));
                OUT.println("child " + child);
                announce(Path.of(rest.get(0)), rest.get(1));
                blockForever();
            }
            case "hold" -> hold(rest);
            case "fail-after" -> {
                Path records = Path.of(rest.get(0));
                if (!awaitRecords(records, ".pid", Integer.parseInt(rest.get(1)), deadline())) {
                    ERR.println("the pid records never appeared");
                    System.exit(EXIT_PEERS_NEVER_CAME);
                }
                ERR.println("failing after my siblings started, with " + rest.get(2));
                System.exit(Integer.parseInt(rest.get(2)));
            }
            default -> {
                ERR.println("unknown scenario " + args[0]);
                System.exit(EXIT_USAGE);
            }
        }
    }

    private static void hold(List<String> rest) throws IOException, InterruptedException {
        Path records = Path.of(rest.get(0));
        String name = rest.get(1);
        int peers = Integer.parseInt(rest.get(2));
        long millis = Long.parseLong(rest.get(3));
        writeRecord(records.resolve(name + ".start"), Instant.now().toString());
        long deadline = System.nanoTime() + millis * 1_000_000L;
        while (count(records, ".start") - count(records, ".end") < peers
                && System.nanoTime() - deadline < 0) {
            Thread.sleep(POLL_MILLIS);
        }
        for (String out : rest.subList(4, rest.size())) {
            writeComplete(Path.of(out));
        }
        writeRecord(records.resolve(name + ".end"), Instant.now().toString());
        OUT.println("held " + name);
        System.exit(0);
    }

    private static long deadline() {
        return System.nanoTime() + TimeUnit.SECONDS.toNanos(WATCHDOG_SECONDS);
    }

    private static boolean awaitRecords(Path records, String suffix, int wanted, long deadline)
            throws IOException, InterruptedException {
        while (count(records, suffix) < wanted) {
            if (System.nanoTime() - deadline >= 0) {
                return false;
            }
            Thread.sleep(POLL_MILLIS);
        }
        return true;
    }

    private static int count(Path records, String suffix) throws IOException {
        int count = 0;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(records)) {
            for (Path entry : entries) {
                if (entry.getFileName().toString().endsWith(suffix)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static void partialIfGiven(List<String> rest, int index) throws IOException {
        if (rest.size() > index) {
            Files.writeString(Path.of(rest.get(index)), "partial", StandardCharsets.UTF_8);
        }
    }

    private static void announce(Path records, String name) throws IOException {
        long pid = ProcessHandle.current().pid();
        writeRecord(records.resolve(name + ".pid"), Long.toString(pid));
        OUT.println("pid " + pid);
    }

    private static long startChild(String records, String name) throws IOException {
        List<String> argv =
                List.of(
                        ProcessHandle.current().info().command().orElseThrow(),
                        "-cp",
                        System.getProperty("java.class.path"),
                        EngineFake.class.getName(),
                        "hang",
                        records,
                        name + "-child");
        ProcessBuilder builder = new ProcessBuilder(argv);
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        Process child = builder.start();
        String announcement;
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))) {
            announcement = reader.readLine();
        }
        if (announcement == null || !announcement.equals("pid " + child.pid())) {
            ERR.println("the child did not announce itself, read: " + announcement);
            child.destroyForcibly();
            System.exit(EXIT_CHILD_FAILED);
        }
        return child.pid();
    }

    private static void writeComplete(Path out) throws IOException {
        Files.writeString(out, "complete " + out.getFileName() + "\n", StandardCharsets.UTF_8);
    }

    private static void writeRecord(Path record, String content) throws IOException {
        Path temporary = record.resolveSibling(record.getFileName() + ".tmp");
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        Files.move(temporary, record, StandardCopyOption.ATOMIC_MOVE);
    }

    private static void blockForever() {
        try {
            if (!new CountDownLatch(1).await(WATCHDOG_SECONDS, TimeUnit.SECONDS)) {
                ERR.println("watchdog fired after " + WATCHDOG_SECONDS + "s; halting");
                Runtime.getRuntime().halt(EXIT_WATCHDOG);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
