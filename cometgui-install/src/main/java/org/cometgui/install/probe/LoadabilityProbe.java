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

package org.cometgui.install.probe;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.LoaderDiagnostic;
import org.cometgui.domain.tools.ProbeFailureKind;

/**
 * Stage 1 of {@code R-TOOL-06}: does this build start at all?
 *
 * <h2>What counts as having started</h2>
 *
 * <p>Not the exit code. The real {@code comet.linux.exe} answers {@code -h} with <strong>exit
 * 1</strong> and a correct banner, so a probe that read the exit code would refuse a working Comet;
 * the real Percolator answers {@code --help} with exit 0. So loadability fails on exactly four
 * things, and passes otherwise:
 *
 * <ol>
 *   <li>the file is built for another processor, read from its own ELF header ({@link
 *       ExecutableFormat} says why the loader cannot be asked);
 *   <li>the process could not be started at all, and the failure message is one this project
 *       recognises -- or is not, in which case it is still a loadability failure, because a start
 *       that did not happen established nothing;
 *   <li>something it printed is a loader failure {@link LoaderOutputClassifier} recognises;
 *   <li>it printed nothing whatsoever and exited non-zero, or it never finished within the timeout.
 * </ol>
 *
 * <p>The fourth is the conservative direction {@code ProbeFailureKind} documents: an ambiguous
 * outcome is recorded as "we did not establish that it starts" and never as "we established that it
 * cannot do this".
 *
 * <h2>Every process goes through the process service</h2>
 *
 * <p>{@code R-PROC-02}: {@code ProcessBuilder} is constructed in exactly one place in this product
 * and an ArchUnit rule enforces it. The environment is constructed rather than inherited, so the
 * caller states what the child gets -- which is how the two-layer loader failure is reproduced,
 * with a stub library on {@code LD_LIBRARY_PATH} and nothing else.
 */
public final class LoadabilityProbe {

    private final ProcessRunner processes;
    private final LoaderOutputClassifier classifier;
    private final HostPlatform host;
    private final Duration timeout;

    /**
     * Creates a probe.
     *
     * @param processes the process service, the only thing in this product that starts a process
     * @param classifier turns what the child printed into an {@code R-PLAT-03} diagnostic
     * @param host the machine the probe is running on
     * @param timeout how long a build gets to print its banner and exit before it is cancelled and
     *     reported as {@link ProbeFailureKind#TIMED_OUT}; must be positive
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code timeout} is not positive
     */
    public LoadabilityProbe(
            ProcessRunner processes,
            LoaderOutputClassifier classifier,
            HostPlatform host,
            Duration timeout) {
        this.processes = Objects.requireNonNull(processes, "processes");
        this.classifier = Objects.requireNonNull(classifier, "classifier");
        this.host = Objects.requireNonNull(host, "host");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive, but was: " + timeout);
        }
    }

    /**
     * The machine this probe is running on.
     *
     * @return the host platform it was created for
     */
    public HostPlatform host() {
        return host;
    }

    /**
     * Runs a staged executable and decides whether it started.
     *
     * @param executable the absolute path of the executable
     * @param arguments what to pass it, usually its own version or help option
     * @param environment the environment the child gets, which is constructed rather than inherited
     * @param context what to name in a diagnostic and what to offer instead
     * @return what was established
     * @throws IOException if the executable's own directory cannot be used as a working directory
     * @throws NullPointerException if any argument is {@code null}
     */
    public LoadabilityResult probe(
            Path executable,
            List<String> arguments,
            Map<String, String> environment,
            ProbeContext context)
            throws IOException {
        Objects.requireNonNull(executable, "executable");
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(context, "context");
        Path workingDirectory = workingDirectoryOf(executable);
        requireRegularFile(executable);
        Optional<LoaderDiagnostic> wrongArchitecture = wrongArchitecture(executable, context);
        if (wrongArchitecture.isPresent()) {
            return failed(wrongArchitecture.get());
        }
        return run(executable, workingDirectory, arguments, environment, context);
    }

    /*
     * Checked before the file is read, so that a probe pointed at a path that is not there says so
     * instead of surfacing a NoSuchFileException from the architecture reader -- a message naming
     * the same path for a different reason, which sends the reader looking for the wrong problem.
     */
    private static void requireRegularFile(Path executable) throws IOException {
        if (!Files.isRegularFile(executable)) {
            throw new IOException(
                    "the staged executable "
                            + executable
                            + " is not a regular file, so it cannot be probed");
        }
    }

    /*
     * Read before the launch, because on Linux the launch cannot answer this: when execvp gets
     * ENOEXEC glibc retries the file through /bin/sh, so a foreign-architecture ELF starts a
     * process and the shell -- not the loader -- prints the complaint.  See ExecutableFormat.
     */
    private Optional<LoaderDiagnostic> wrongArchitecture(Path executable, ProbeContext context)
            throws IOException {
        Optional<HostArchitecture> built = ExecutableFormat.architectureOf(executable);
        if (built.isPresent() && built.get() != host.architecture()) {
            return Optional.of(classifier.of(ProbeFailureKind.WRONG_ARCHITECTURE, context));
        }
        return Optional.empty();
    }

    private LoadabilityResult run(
            Path executable,
            Path workingDirectory,
            List<String> arguments,
            Map<String, String> environment,
            ProbeContext context) {
        List<String> argv = new ArrayList<>();
        argv.add(executable.toString());
        argv.addAll(arguments);
        Collector collector = new Collector();
        RunningProcess process;
        try {
            process =
                    processes.start(
                            new ToolCommand(argv, workingDirectory, environment), collector);
        } catch (IOException notStarted) {
            return failed(startFailure(notStarted, context));
        }
        return awaitAndClassify(process, collector, context);
    }

    /*
     * The staged directory, which the install pipeline created and which therefore exists.  Using
     * the executable's own directory rather than a temporary one means a tool that looks beside
     * itself for a companion file finds it, which is how Comet's Thermo DLLs and Percolator's XSDs
     * are installed.
     */
    private static Path workingDirectoryOf(Path executable) throws IOException {
        Path directory = executable.toAbsolutePath().getParent();
        if (directory == null || !Files.isDirectory(directory)) {
            throw new IOException(
                    "the staged executable "
                            + executable
                            + " has no directory to run in, so it cannot be probed");
        }
        return directory;
    }

    private LoaderDiagnostic startFailure(IOException notStarted, ProbeContext context) {
        return classifier
                .fromStartFailure(wholeChain(notStarted), context)
                .orElseGet(() -> classifier.of(ProbeFailureKind.EXECUTION_FAILED, context));
    }

    /**
     * Every message in a failure's cause chain, joined.
     *
     * <p><strong>The chain, not the top message.</strong> The process service wraps whatever the
     * runtime threw -- {@code could not start ToolCommand[...]} -- and the thing the classifier
     * needs to read, {@code Exec failed, error: 13 (Permission denied)}, is in the cause. A
     * classifier given only the top message would recognise nothing and every start failure would
     * be reported as an unexplained one.
     *
     * @param failure the failure to flatten
     * @return the messages of the failure and of every cause under it, joined by {@code " | "}
     * @throws NullPointerException if {@code failure} is {@code null}
     */
    static String wholeChain(Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        StringBuilder joined = new StringBuilder();
        for (Throwable link = failure; link != null; link = link.getCause()) {
            if (joined.length() > 0) {
                joined.append(" | ");
            }
            joined.append(Objects.toString(link.getMessage(), ""));
        }
        return joined.toString();
    }

    private LoadabilityResult awaitAndClassify(
            RunningProcess process, Collector collector, ProbeContext context) {
        if (!collector.awaitExit(timeout)) {
            process.requestCancellation();
            collector.awaitExit(timeout);
            return new LoadabilityResult(
                    Optional.of(classifier.of(ProbeFailureKind.TIMED_OUT, context)),
                    collector.standardOutput(),
                    collector.standardError(),
                    OptionalInt.empty());
        }
        List<String> standardOutput = collector.standardOutput();
        List<String> standardError = collector.standardError();
        int exitCode = collector.exitCode();
        Optional<LoaderDiagnostic> loaderFailure =
                classifier.fromOutput(
                        IdentityProbe.errorFirst(standardError, standardOutput), context);
        if (loaderFailure.isEmpty()
                && exitCode != 0
                && standardError.isEmpty()
                && standardOutput.isEmpty()) {
            loaderFailure = Optional.of(classifier.of(ProbeFailureKind.EXECUTION_FAILED, context));
        }
        return new LoadabilityResult(
                loaderFailure, standardOutput, standardError, OptionalInt.of(exitCode));
    }

    private static LoadabilityResult failed(LoaderDiagnostic diagnostic) {
        return new LoadabilityResult(
                Optional.of(diagnostic), List.of(), List.of(), OptionalInt.empty());
    }

    /** Collects both streams and the exit code of one probe run. */
    private static final class Collector implements ProcessListener {

        private final List<String> standardOutput = Collections.synchronizedList(new ArrayList<>());
        private final List<String> standardError = Collections.synchronizedList(new ArrayList<>());
        private final AtomicInteger exitCode = new AtomicInteger();
        private final CountDownLatch finished = new CountDownLatch(1);

        @Override
        public void onStandardOutput(String line) {
            standardOutput.add(line);
        }

        @Override
        public void onStandardError(String line) {
            standardError.add(line);
        }

        /*
         * KNOWN TIMED_OUT MUTANT, recorded rather than left for someone to rediscover.  Removing
         * the countDown below makes every probe wait out its whole timeout and then report
         * TIMED_OUT, so every behavioural test in this package fails -- but each of them takes two
         * full timeouts to do it, and PIT abandons the run before the first assertion is reached.
         * It is therefore scored TIMED_OUT rather than KILLED, and scripts/build.sh counts only
         * KILLED, so it counts against this package's score rather than being credited.  The way to
         * convert it into a kill is to shorten the real binaries' probe timeout until two of them
         * fit inside PIT's own, which would make the suite depend on how loaded the machine is; a
         * flaky test is a worse thing to own than a scored non-kill.
         */
        @Override
        public void onExit(int code) {
            exitCode.set(code);
            finished.countDown();
        }

        boolean awaitExit(Duration limit) {
            try {
                return finished.await(limit.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        List<String> standardOutput() {
            synchronized (standardOutput) {
                return List.copyOf(standardOutput);
            }
        }

        List<String> standardError() {
            synchronized (standardError) {
                return List.copyOf(standardError);
            }
        }

        int exitCode() {
            return exitCode.get();
        }
    }
}
