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

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.run.StageTag;

/**
 * The handle on a stage that {@link StageRunner} has started: cancel it, wait for it, ask where its
 * log is.
 *
 * <p>Deliberately as thin as {@link RunningProcess}, and for the same reason. The workflow engine
 * runs a stage on its own thread and waits with {@link #awaitOutcome()}; the user interface thread
 * holds the same object and calls {@link #requestCancellation()} on it. Those are the two things
 * that happen, and every method here is safe to call from either thread.
 *
 * <p>There is no way to get at the underlying process, the log file's writer or the message sink
 * through this object. A handle that could hand out the process would let a caller bypass the
 * cancellation this class exists to route.
 *
 * <h2>The timeout is real time, not clock time</h2>
 *
 * <p>Everything else in this phase is timestamped from the injected {@link java.time.Clock} that
 * {@code R-PROC-01} requires, and a timeout is the one thing that cannot be: a fixed clock -- the
 * form every test here uses -- never advances, so a timeout measured by it would never fire, and a
 * timeout measured by polling the clock would need a sleeping thread, which this phase forbids.
 * {@link CompletableFuture#orTimeout} schedules the expiry on the JDK's own delayed executor, so
 * the timer costs no thread while it waits and there is no sleep anywhere.
 */
public final class RunningStage {

    private final StageRecorder recorder;
    private final RunningProcess process;

    /**
     * Assembled by {@link StageRunner} once the process is running.
     *
     * @param recorder the listener that is writing this stage's log
     * @param process the started process
     * @throws NullPointerException if either argument is null
     */
    RunningStage(StageRecorder recorder, RunningProcess process) {
        this.recorder = Objects.requireNonNull(recorder, "recorder");
        this.process = Objects.requireNonNull(process, "process");
    }

    /**
     * Which stage this is.
     *
     * @return the stage, never null
     */
    public StageTag stage() {
        return recorder.stage();
    }

    /**
     * The log file this stage is writing, available immediately rather than only at the end.
     *
     * <p>It is not necessarily {@code <stage>.log}: a re-run of a stage writes {@code
     * <stage>.1.log} rather than destroying the first attempt's file.
     *
     * @return the path, never null
     */
    public Path logFile() {
        return recorder.logFile();
    }

    /**
     * Whether the tool's process is still running.
     *
     * <p>Not the same question as "has the outcome arrived": the process can be gone while the last
     * few lines of its output are still being drained and written. {@link #outcomeIfFinished()}
     * answers the other one.
     *
     * @return true until the process ends
     */
    public boolean isAlive() {
        return process.isAlive();
    }

    /**
     * Asks the stage to stop, and returns without waiting.
     *
     * <p>The termination itself, including the descendants a scientific tool may have spawned and
     * the escalation from a polite signal to a forcible one, is the process service's; this only
     * records that a human asked, so that the outcome can tell a cancelled stage from a tool that
     * failed. Calling it more than once, or after the stage has ended, does nothing.
     */
    public void requestCancellation() {
        recorder.markCancellation(StageRecorder.Cancellation.BY_CALLER);
        process.requestCancellation();
    }

    /**
     * Waits for the stage to finish and returns everything that happened.
     *
     * <p>Returns only after the last line has been written to the log file and the file closed, so
     * a caller that has the outcome can read the log without racing the writer.
     *
     * @return the outcome, never null
     * @throws InterruptedException if the waiting thread is interrupted; the stage is left running,
     *     so a caller that gives up must also call {@link #requestCancellation()}
     */
    public StageOutcome awaitOutcome() throws InterruptedException {
        try {
            return recorder.completed().get();
        } catch (ExecutionException cannotHappen) {
            throw new IllegalStateException(
                    "a stage outcome is only ever completed with a value", cannotHappen);
        }
    }

    /**
     * The outcome if the stage has already finished, without waiting for it.
     *
     * @return the outcome, or empty while the stage is still running
     */
    public Optional<StageOutcome> outcomeIfFinished() {
        return Optional.ofNullable(recorder.completed().getNow(null));
    }

    /**
     * Arms this stage's optional timeout. Called by {@link StageRunner} only when one is
     * configured.
     *
     * <p>The timer runs on a <em>copy</em> of the outcome future. {@link
     * CompletableFuture#orTimeout} completes the future it is called on exceptionally, and the
     * outcome future must never be completed exceptionally -- {@link #awaitOutcome()} would then
     * fail instead of returning the outcome of a stage that did, in the end, produce one. The copy
     * completes when the stage does and is thrown away either way.
     *
     * <p>When the timer wins, the stage is cancelled exactly as a user's cancellation would cancel
     * it, and the reason is recorded as the timeout so that the outcome and the log footer can say
     * which of the two happened.
     *
     * @param timeoutMillis how long the stage may run, in milliseconds, at least one
     */
    void armTimeout(long timeoutMillis) {
        recorder.completed()
                .copy()
                .orTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .whenComplete(
                        (outcome, ranOutOfTime) -> {
                            if (ranOutOfTime instanceof TimeoutException) {
                                recorder.markCancellation(StageRecorder.Cancellation.BY_TIMEOUT);
                                process.requestCancellation();
                            }
                        });
    }
}
