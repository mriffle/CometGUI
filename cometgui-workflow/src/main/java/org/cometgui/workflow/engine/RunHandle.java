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

package org.cometgui.workflow.engine;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.Plan;
import org.cometgui.workflow.state.RunState;
import org.cometgui.workflow.state.StepState;

/**
 * A run in progress: what the user interface holds to cancel it, ask where it is, and wait for it.
 *
 * <p>Every method is safe from any thread, the JavaFX application thread included: {@link
 * #cancel()} returns at once, and the two {@code await} methods are the only ones that block.
 */
public final class RunHandle {

    private final RunExecution execution;

    RunHandle(RunExecution execution) {
        this.execution = Objects.requireNonNull(execution, "execution");
    }

    /**
     * Asks the run to stop. Running steps move to {@code CANCEL_REQUESTED} and their tools -- with
     * every descendant process -- are asked to terminate through the process service; steps not yet
     * started are never started. Idempotent, and a no-op once the run is finishing. Returns without
     * waiting; the outcome arrives through {@link #await()} and the listener.
     */
    public void cancel() {
        execution.cancel();
    }

    /**
     * Waits for the run to finish and its provenance to be final.
     *
     * @return how it ended
     * @throws InterruptedException if the wait is interrupted
     */
    public RunResult await() throws InterruptedException {
        return execution.await();
    }

    /**
     * Waits for the run to finish, for at most a given time.
     *
     * @param timeout how long to wait
     * @return how it ended, or empty if it has not ended within {@code timeout}
     * @throws InterruptedException if the wait is interrupted
     */
    public Optional<RunResult> await(Duration timeout) throws InterruptedException {
        return execution.await(Objects.requireNonNull(timeout, "timeout"));
    }

    /**
     * The attempt's number in {@code run.json}.
     *
     * @return the attempt number, starting at one
     */
    public int attempt() {
        return execution.attempt();
    }

    /**
     * The plan being run.
     *
     * @return the plan
     */
    public Plan plan() {
        return execution.plan();
    }

    /**
     * A snapshot of every planned step's state.
     *
     * @return an immutable map in plan order
     */
    public Map<EngineStep, StepState> states() {
        return execution.states();
    }

    /**
     * The run state, derived from the step states now ({@code RunState.deriveFrom}); never stored.
     *
     * @return the derived state
     */
    public RunState runState() {
        return RunState.deriveFrom(execution.plan(), execution.states());
    }
}
