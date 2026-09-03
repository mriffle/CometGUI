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

package org.cometgui.tools.api;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.ports.ToolCommand;

/**
 * Runs one short tool invocation to completion and collects what it printed.
 *
 * <p>Every probe in this module goes through here, and every probe therefore goes through {@code
 * ProcessRunner}: {@code R-PROC-02} confines {@link ProcessBuilder} to the process service and an
 * ArchUnit rule enforces it, so an adapter that wanted to start a process itself could not.
 *
 * <p><strong>Bounded, because a probe must not be a way to exhaust the heap.</strong> {@code
 * R-PROC-03} caps what may be held in memory for a running tool, and a capability probe is pointed
 * at a binary nobody has vouched for: {@link #MAX_LINES_PER_STREAM} lines of each stream are kept
 * and the rest are dropped. Every probe run in this project produces far less -- the largest
 * observed is Percolator's 3404 bytes of cross-validation chatter on a 64 plus 64 row fixture -- so
 * the cap is a guard rather than a working limit.
 *
 * <p><strong>A run that never finishes is cancelled and reported as having no exit code.</strong>
 * That is not the same fact as a non-zero exit and the callers do not treat it as one.
 */
public final class ToolRunner {

    /** How many lines of each stream are kept before the rest are dropped. */
    public static final int MAX_LINES_PER_STREAM = 500;

    private final ProcessRunner processes;
    private final Duration timeout;

    /**
     * Creates a runner.
     *
     * @param processes the process service, the only thing in this product that starts a process
     * @param timeout how long one invocation gets before it is cancelled; must be positive
     * @throws NullPointerException if either argument is {@code null}
     * @throws IllegalArgumentException if {@code timeout} is not positive
     */
    public ToolRunner(ProcessRunner processes, Duration timeout) {
        this.processes = Objects.requireNonNull(processes, "processes");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive, but was: " + timeout);
        }
    }

    /**
     * How long one invocation is given.
     *
     * @return the timeout, always positive
     */
    public Duration timeout() {
        return timeout;
    }

    /**
     * Starts the command, waits for it, and returns both streams and the exit code.
     *
     * @param command the argument array, working directory and environment
     * @return what the run produced
     * @throws IOException if the process could not be started at all
     * @throws NullPointerException if {@code command} is {@code null}
     */
    public ToolRunOutcome run(ToolCommand command) throws IOException {
        Objects.requireNonNull(command, "command");
        Collector collector = new Collector();
        RunningProcess process = processes.start(command, collector);
        if (!collector.awaitExit(timeout)) {
            process.requestCancellation();
            collector.awaitExit(timeout);
            return new ToolRunOutcome(
                    OptionalInt.empty(), collector.standardOutput(), collector.standardError());
        }
        return new ToolRunOutcome(
                OptionalInt.of(collector.exitCode()),
                collector.standardOutput(),
                collector.standardError());
    }

    /** Collects both streams and the exit code of one run, dropping anything past the cap. */
    private static final class Collector implements ProcessListener {

        private final List<String> standardOutput = Collections.synchronizedList(new ArrayList<>());
        private final List<String> standardError = Collections.synchronizedList(new ArrayList<>());
        private final AtomicInteger exitCode = new AtomicInteger();
        private final CountDownLatch finished = new CountDownLatch(1);

        @Override
        public void onStandardOutput(String line) {
            append(standardOutput, line);
        }

        @Override
        public void onStandardError(String line) {
            append(standardError, line);
        }

        /*
         * KNOWN TIMED_OUT MUTANT, recorded rather than left for someone to rediscover, and the same
         * one org.cometgui.install.probe.LoadabilityProbe's collector carries for the same reason.
         * Removing the countDown below makes every run wait out its whole timeout twice and then
         * report a timeout, so every behavioural test in this module fails -- but each of them
         * takes two full timeouts to do it, and PIT abandons the run before the first assertion is
         * reached.  It is therefore scored TIMED_OUT rather than KILLED, and scripts/build.sh
         * counts only KILLED, so it counts against this module's score rather than being credited.
         * The way to convert it into a kill is to shorten the real binaries' timeouts until two of
         * them fit inside PIT's own, which would make the suite depend on how loaded the machine
         * is; a flaky test is a worse thing to own than a scored non-kill.
         */
        @Override
        public void onExit(int code) {
            exitCode.set(code);
            finished.countDown();
        }

        private static void append(List<String> lines, String line) {
            synchronized (lines) {
                if (lines.size() < MAX_LINES_PER_STREAM) {
                    lines.add(line);
                }
            }
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
