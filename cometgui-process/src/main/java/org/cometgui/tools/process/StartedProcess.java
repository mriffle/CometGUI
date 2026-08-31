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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.ports.ToolCommand;

/**
 * A process {@link ProcessService} has started: the port's handle, plus the facts a run record
 * needs.
 *
 * <p>{@link ProcessService#start} declares this type rather than {@link RunningProcess} -- a
 * covariant return, which still satisfies the port exactly -- because a caller that has just
 * started a process is the one place where the start instant, the pid and the command are known,
 * and forcing the workflow engine to re-derive them would be inventing them.
 *
 * <h2>Ordering guarantees</h2>
 *
 * <ul>
 *   <li>{@code ProcessListener.onExit(int)} is called <strong>exactly once</strong>, and
 *       <strong>after</strong> the last line of both streams. A completion thread waits for the
 *       process, then joins both pump threads -- which end only at end of stream -- and only then
 *       reports the exit.
 *   <li>{@link #waitForExit()} returns only <strong>after</strong> that call has finished, so a
 *       caller that waits is guaranteed the listener has already seen everything.
 * </ul>
 *
 * <h2>Where the timestamps come from</h2>
 *
 * <p>{@link #startedAt()} and {@link #endedAt()} come from the injected {@link Clock}, not from
 * {@link System#nanoTime()}. The trade-off is real and is stated rather than hidden: a wall clock
 * can be stepped by NTP or by a daylight-saving change, so a reported duration is not a guaranteed
 * monotonic measurement. {@code R-PROC-01} requires the clock to be an injectable seam, and a test
 * that cannot assert an exact duration is not a test -- so the seam wins, and a run whose recorded
 * duration looks impossible is a clock that moved, not a process that did.
 *
 * <p>Thread safe.
 */
public final class StartedProcess implements RunningProcess {

    private final Process process;
    private final ToolCommand command;
    private final Clock clock;
    private final Instant startedAt;
    private final Duration terminationGrace;
    private final GuardedListener listener;
    private final Thread standardOutputPump;
    private final Thread standardErrorPump;
    private final AtomicBoolean cancellationRequested;
    private final CountDownLatch finished = new CountDownLatch(1);

    private volatile Instant endedAt;
    private volatile int exitCode;

    /**
     * Assembled by {@link ProcessService}, which owns every argument.
     *
     * @param process the started process
     * @param command what was started
     * @param clock the injected clock, read once more when the process ends
     * @param startedAt the instant the process was started, already read from that clock
     * @param terminationGrace how long a cancelled process has to end before it is killed
     * @param listener the guarded listener both pumps and the completion thread call
     * @param standardOutputPump the stdout pump thread, already constructed
     * @param standardErrorPump the stderr pump thread, already constructed
     * @param cancellationRequested shared with both pumps, so a dead pipe after cancellation is
     *     recognised as expected rather than reported as a fault
     */
    StartedProcess(
            Process process,
            ToolCommand command,
            Clock clock,
            Instant startedAt,
            Duration terminationGrace,
            GuardedListener listener,
            Thread standardOutputPump,
            Thread standardErrorPump,
            AtomicBoolean cancellationRequested) {
        this.process = Objects.requireNonNull(process, "process");
        this.command = Objects.requireNonNull(command, "command");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.terminationGrace = Objects.requireNonNull(terminationGrace, "terminationGrace");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.standardOutputPump = Objects.requireNonNull(standardOutputPump, "standardOutputPump");
        this.standardErrorPump = Objects.requireNonNull(standardErrorPump, "standardErrorPump");
        this.cancellationRequested =
                Objects.requireNonNull(cancellationRequested, "cancellationRequested");
    }

    @Override
    public boolean isAlive() {
        return process.isAlive();
    }

    @Override
    public int waitForExit() throws InterruptedException {
        finished.await();
        return exitCode;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns without waiting, as the port requires, and is idempotent: the second call, and any
     * call after the process has ended, does nothing.
     *
     * <p>The descendants are snapshotted and killed before the process itself, through {@link
     * ProcessHandle} rather than {@link Process}; {@link ProcessTree} documents why both of those
     * are load-bearing. Anything still alive after the termination grace is killed forcibly, and
     * the waiting for that grace is done with {@link java.util.concurrent.CompletableFuture} timers
     * driven by {@link ProcessHandle#onExit()}, never by a sleeping thread.
     */
    @Override
    public void requestCancellation() {
        if (!cancellationRequested.compareAndSet(false, true)) {
            return;
        }
        List<ProcessHandle> ordered = ProcessTree.terminationOrder(process.toHandle());
        ProcessTree.destroyAll(ordered);
        ProcessTree.whenAllExited(ordered)
                .orTimeout(terminationGrace.toMillis(), TimeUnit.MILLISECONDS)
                .whenComplete(
                        (allExited, stillRunning) -> {
                            if (stillRunning != null) {
                                ProcessTree.destroyAllForcibly(ordered);
                            }
                        });
    }

    /**
     * The command this process was started from.
     *
     * @return the command, never null
     */
    public ToolCommand command() {
        return command;
    }

    /**
     * The operating system process id.
     *
     * @return the pid, valid whether or not the process is still running
     */
    public long pid() {
        return process.pid();
    }

    /**
     * When the process was started, read from the injected clock.
     *
     * @return the start instant, never null
     */
    public Instant startedAt() {
        return startedAt;
    }

    /**
     * When the process ended, read from the injected clock.
     *
     * @return the end instant, or empty while the process is still running
     */
    public Optional<Instant> endedAt() {
        return Optional.ofNullable(endedAt);
    }

    /**
     * How long the process ran, from the injected clock.
     *
     * <p>Measured from the start of the process to its exit, and deliberately not to the moment the
     * last line was drained: that would make a run's duration depend on how fast the listener is.
     *
     * @return the duration, or empty while the process is still running
     */
    public Optional<Duration> duration() {
        Instant ended = endedAt;
        return ended == null ? Optional.empty() : Optional.of(Duration.between(startedAt, ended));
    }

    /**
     * How many times the caller's listener threw out of a callback.
     *
     * <p>Non-zero means a defect in the listener, not in the service: the service caught it and
     * carried on so the rest of the output survived, and counts it here so that the defect is
     * visible rather than absorbed.
     *
     * @return the number of failed callbacks, zero for a well-behaved listener
     */
    public long listenerFailureCount() {
        return listener.failureCount();
    }

    /**
     * The first listener failure, rendered as text.
     *
     * @return the first failure's {@code toString()}, or empty if the listener has never thrown
     */
    public Optional<String> firstListenerFailure() {
        return listener.firstFailureDescription();
    }

    /**
     * Whether cancellation has been requested for this process.
     *
     * @return true once {@link #requestCancellation()} has been called
     */
    public boolean isCancellationRequested() {
        return cancellationRequested.get();
    }

    /**
     * Run on the completion thread: wait for the process, drain both pumps, report the exit once.
     *
     * <p>The order is the contract. Joining the pumps before calling {@code onExit} is what makes
     * "after the last output line of both streams" true; counting the latch down afterwards is what
     * makes {@link #waitForExit()} return only once the listener has been told.
     */
    void awaitCompletionAndNotify() {
        int code = waitUninterruptibly();
        endedAt = clock.instant();
        joinUninterruptibly(standardOutputPump);
        joinUninterruptibly(standardErrorPump);
        exitCode = code;
        listener.onExit(code);
        finished.countDown();
    }

    /**
     * Waits for the process, ignoring interruption.
     *
     * <p>Giving up here would leave {@link #waitForExit()} blocked for ever and the listener never
     * told, so the interrupt is remembered and re-asserted instead of being obeyed.
     *
     * @return the exit code
     */
    private int waitUninterruptibly() {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    return process.waitFor();
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

    private static void joinUninterruptibly(Thread pump) {
        boolean interrupted = false;
        try {
            while (pump.isAlive()) {
                try {
                    pump.join();
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
