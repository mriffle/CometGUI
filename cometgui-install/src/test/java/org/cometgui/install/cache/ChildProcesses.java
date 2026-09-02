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

package org.cometgui.install.cache;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.tools.process.ProcessService;

/**
 * Runs a real process, and runs a real second JVM.
 *
 * <p><strong>Through {@link ProcessService}, because that is the only place in this product allowed
 * to build a {@link ProcessBuilder}</strong> -- {@code R-PROC-02}, enforced by an ArchUnit rule.
 * Two things in this package need a real process: proving that the installed executable actually
 * runs ({@code R-PLAT-05}), and proving that two <em>processes</em> installing the same artefact
 * serialise ({@code R-TOOL-05}).
 *
 * <p><strong>Two threads are not two processes.</strong> {@link java.nio.channels.FileLock} is held
 * by the JVM: a second lock attempt inside one JVM raises {@link
 * java.nio.channels.OverlappingFileLockException} rather than waiting, which is a different code
 * path from the one a second CometGUI hits. So the lock tests launch a second JVM here, with this
 * one's class path, rather than a second thread.
 *
 * <p>Every process is waited for and every one is destroyed if it outstays its timeout: a leaked
 * JVM or a held lock would make the next agent's tests flaky.
 */
final class ChildProcesses {

    /** What one run did. */
    record Result(int exitCode, List<String> standardOutput, List<String> standardError) {

        /**
         * Everything the process printed, for an assertion message.
         *
         * @return the two streams, labelled
         */
        String describe() {
            return "exit=" + exitCode + " stdout=" + standardOutput + " stderr=" + standardError;
        }
    }

    private ChildProcesses() {}

    /**
     * The {@code java} of the JVM running this test.
     *
     * @return the launcher's path
     */
    static String javaExecutable() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    /**
     * The class path a child JVM is given, built from where the classes it needs actually live.
     *
     * <p><strong>Not {@code java.class.path}.</strong> That property describes whatever launched
     * this JVM, and under two of this project's own runners it is not the test class path: Surefire
     * hands the JVM a manifest-only jar, and PIT's coverage minion hands it the minion's own. A
     * child started with the wrong class path fails with {@code ClassNotFoundException} before it
     * writes anything, which the parent then sees as a sixty-second wait for a journal line that is
     * never coming -- and the whole PIT stage of {@code scripts/build.sh} fails with "tests did not
     * pass without mutation". Measured, not guessed: this is what happened the first time the
     * mutation gate was pointed at this package.
     *
     * <p>Asking each class where its own code came from answers the question directly, in every
     * runner.
     *
     * @return the class path, the directories or jars holding the classes a child needs
     */
    static String classPath() {
        Set<String> entries = new LinkedHashSet<>();
        for (Class<?> anchor :
                List.of(
                        InstallHaltChild.class,
                        ArtefactInstaller.class,
                        org.cometgui.domain.tools.ToolName.class,
                        org.cometgui.provenance.json.JsonReader.class,
                        org.cometgui.tools.process.ProcessService.class)) {
            entries.add(codeSourceOf(anchor));
        }
        return String.join(File.pathSeparator, entries);
    }

    private static String codeSourceOf(Class<?> anchor) {
        CodeSource source = anchor.getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null) {
            throw new AssertionError(
                    "no code source for "
                            + anchor.getName()
                            + ", so a child JVM cannot be told where to find it");
        }
        try {
            return Path.of(source.getLocation().toURI()).toString();
        } catch (URISyntaxException notAPath) {
            throw new AssertionError(
                    "the code source of " + anchor.getName() + " is not a path", notAPath);
        }
    }

    /**
     * Starts a second JVM running a main class from this test tree.
     *
     * @param mainClass the class to run
     * @param workingDirectory an existing absolute directory
     * @param arguments the arguments after the class name
     * @return the running process and the listener collecting its output
     * @throws IOException if it cannot be started
     */
    static Child startJava(Class<?> mainClass, Path workingDirectory, List<String> arguments)
            throws IOException {
        List<String> argv = new ArrayList<>();
        argv.add(javaExecutable());
        argv.add("-cp");
        argv.add(classPath());
        argv.add(mainClass.getName());
        argv.addAll(arguments);
        return start(argv, workingDirectory);
    }

    /**
     * Runs a second JVM to completion.
     *
     * @param mainClass the class to run
     * @param workingDirectory an existing absolute directory
     * @param arguments the arguments after the class name
     * @param timeout how long to wait before killing it
     * @return what it did
     * @throws IOException if it cannot be started
     * @throws InterruptedException if the wait is interrupted
     */
    static Result runJava(
            Class<?> mainClass, Path workingDirectory, List<String> arguments, Duration timeout)
            throws IOException, InterruptedException {
        return startJava(mainClass, workingDirectory, arguments).await(timeout);
    }

    /**
     * Starts a process.
     *
     * @param argv the executable and its arguments
     * @param workingDirectory an existing absolute directory
     * @return the running process and the listener collecting its output
     * @throws IOException if it cannot be started
     */
    static Child start(List<String> argv, Path workingDirectory) throws IOException {
        if (!Files.isDirectory(workingDirectory)) {
            throw new IOException("the working directory does not exist: " + workingDirectory);
        }
        Collector collector = new Collector();
        RunningProcess process =
                new ProcessService(Clock.systemUTC())
                        .start(
                                new ToolCommand(argv, workingDirectory.toAbsolutePath(), Map.of()),
                                collector);
        return new Child(process, collector);
    }

    /**
     * Runs a process to completion.
     *
     * @param argv the executable and its arguments
     * @param workingDirectory an existing absolute directory
     * @param timeout how long to wait before killing it
     * @return what it did
     * @throws IOException if it cannot be started
     * @throws InterruptedException if the wait is interrupted
     */
    static Result run(List<String> argv, Path workingDirectory, Duration timeout)
            throws IOException, InterruptedException {
        Child child = start(argv, workingDirectory);
        return child.await(timeout);
    }

    /** A started process and the output it is producing. */
    static final class Child {

        private final RunningProcess process;
        private final Collector collector;

        Child(RunningProcess process, Collector collector) {
            this.process = process;
            this.collector = collector;
        }

        /**
         * Waits for the process to end, killing it if it outstays the timeout.
         *
         * @param timeout how long to wait
         * @return what it did
         * @throws InterruptedException if the wait is interrupted
         */
        Result await(Duration timeout) throws InterruptedException {
            if (!collector.finished.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.requestCancellation();
                boolean died = collector.finished.await(5, TimeUnit.SECONDS);
                throw new AssertionError(
                        (died ? "" : "it did not die when killed either; ")
                                + "a child process did not finish within "
                                + timeout
                                + " and was killed; it had printed "
                                + collector.result().describe());
            }
            return collector.result();
        }

        /**
         * Whether the process is still running.
         *
         * @return {@code true} until it has ended
         */
        boolean isAlive() {
            return process.isAlive();
        }

        /**
         * What it has printed so far.
         *
         * @return the output collected up to now
         */
        Result soFar() {
            return collector.result();
        }

        /**
         * Kills the process if it is still running.
         *
         * @throws InterruptedException if the wait is interrupted
         */
        void stop() throws InterruptedException {
            if (process.isAlive()) {
                process.requestCancellation();
                if (!collector.finished.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError(
                            "a child process outlived its cancellation; a leaked JVM makes the next"
                                    + " agent's tests flaky");
                }
            }
        }
    }

    /** Collects a process's two streams and its exit code. */
    static final class Collector implements ProcessListener {

        private final List<String> standardOutput = Collections.synchronizedList(new ArrayList<>());
        private final List<String> standardError = Collections.synchronizedList(new ArrayList<>());
        private final AtomicInteger exitCode = new AtomicInteger(Integer.MIN_VALUE);
        private final CountDownLatch finished = new CountDownLatch(1);

        @Override
        public void onStandardOutput(String line) {
            standardOutput.add(line);
        }

        @Override
        public void onStandardError(String line) {
            standardError.add(line);
        }

        @Override
        public void onExit(int code) {
            exitCode.set(code);
            finished.countDown();
        }

        Result result() {
            return new Result(
                    exitCode.get(), List.copyOf(standardOutput), List.copyOf(standardError));
        }
    }
}
