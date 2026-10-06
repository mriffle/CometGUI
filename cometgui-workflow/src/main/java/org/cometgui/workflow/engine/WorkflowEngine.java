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

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.cometgui.workflow.state.EngineStep;

/**
 * The workflow engine: runs a plan of steps over the declared graph, and is the one place a run's
 * state, provenance, cancellation and reuse are decided ({@code R-RUN-01}, {@code R-RUN-02}, {@code
 * R-CMT-05}).
 *
 * <p>It knows nothing about any particular tool. A step is a {@link StepAction}; the engine runs it
 * when every planned step upstream of it has succeeded or been reused, alongside every other step
 * whose upstream steps are done -- so hashing the inputs runs while a tool is being resolved -- and
 * gives it a {@link StepContext} through which it runs its tools.
 *
 * <h2>An attempt, from start to finish</h2>
 *
 * <ol>
 *   <li>{@link #checkReuse} -- also run by {@link #start}, so it cannot be skipped -- computes the
 *       rerun preview against the fingerprints {@code run.json} records for steps that succeeded,
 *       and re-hashes every file of every step it would reuse. A changed file refuses the start
 *       with {@link ReuseRefusedException}, naming the file and offering the plan that runs its
 *       producer again.
 *   <li>The attempt is added to {@code run.json} and {@code run.started} is appended to the run's
 *       event log. Steps the preview reuses or does not need move to {@code SKIPPED}.
 *   <li>Steps run as their upstream steps finish. Every state change goes to the listener and to
 *       the event log ({@code stage.started} for a step entering {@code VALIDATING}, {@code READY},
 *       {@code RUNNING} or {@code CANCEL_REQUESTED}; {@code stage.finished} for a terminal state),
 *       with the state's wire name. Each step that succeeds has its fingerprint recorded in {@code
 *       run.json}. A step that fails stops new steps from starting; steps already running finish.
 *   <li>When nothing is running, the attempt is finalised, whether it succeeded, failed or was
 *       cancelled: {@code provenance.json} is written atomically by the redacting manifest writer,
 *       {@code provenance.rst} is rendered from the same model, {@code run.finished} carries the
 *       status, and the attempt's outcome is recorded in {@code run.json}.
 * </ol>
 *
 * <p>The run's state is never stored: it is derived from the step states ({@code
 * RunState.deriveFrom}) wherever it is needed.
 */
public final class WorkflowEngine {

    /** The provenance settings key the engine records the attempt number under. */
    public static final String ATTEMPT_SETTING = "workflow.attempt";

    /** The provenance settings key the engine records the planned step identifiers under. */
    public static final String PLAN_SETTING = "workflow.plan";

    private final EngineServices services;

    /**
     * Creates the engine.
     *
     * @param services its collaborators
     */
    public WorkflowEngine(EngineServices services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    /**
     * The preview of a run, with every result it would reuse revalidated: what the user sees before
     * starting it.
     *
     * @param request the run as it would be started
     * @return the check; {@link ReuseCheck#accepted()} when {@link #start} would start it
     * @throws IOException if {@code run.json} or a reused file cannot be read
     * @throws IllegalArgumentException if a planned step has no implementation, naming it
     */
    public ReuseCheck checkReuse(RunRequest request) throws IOException {
        declarationsOf(request);
        return ReuseValidator.check(request, services.hashes());
    }

    /**
     * Starts an attempt. Returns once the attempt is recorded and its first steps are dispatched.
     *
     * @param request the run
     * @param listener observes every state change, on an engine thread
     * @return the running attempt
     * @throws IOException if {@code run.json} or the event log cannot be read or written, or a
     *     reused file cannot be re-hashed; nothing has started
     * @throws ReuseRefusedException if a result the run would reuse no longer matches its record;
     *     nothing has started
     * @throws IllegalArgumentException if a planned step has no implementation, naming it, or a
     *     step's declaration is invalid
     */
    public RunHandle start(RunRequest request, StepStateListener listener)
            throws IOException, ReuseRefusedException {
        Objects.requireNonNull(listener, "listener");
        Map<EngineStep, StepDeclaration> declarations = declarationsOf(request);
        ReuseCheck check = ReuseValidator.check(request, services.hashes());
        if (!check.accepted()) {
            throw new ReuseRefusedException(check);
        }
        return new RunHandle(RunExecution.begin(services, request, declarations, check, listener));
    }

    /**
     * Refuses a plan with a step that has no implementation (P8-9: such a step is never recorded as
     * succeeded or skipped), and reads every planned step's declaration once.
     */
    private static Map<EngineStep, StepDeclaration> declarationsOf(RunRequest request) {
        Objects.requireNonNull(request, "request");
        List<String> missing = new ArrayList<>();
        for (EngineStep step : request.plan().steps()) {
            if (!request.actions().containsKey(step)) {
                missing.add(step.id());
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "the plan includes steps with no implementation in this build: "
                            + String.join(", ", missing)
                            + "; a run cannot include them, and they are never recorded as"
                            + " succeeded or skipped");
        }
        Map<EngineStep, StepDeclaration> declarations = new EnumMap<>(EngineStep.class);
        for (EngineStep step : request.plan().steps()) {
            declarations.put(
                    step,
                    Objects.requireNonNull(
                            request.actions().get(step).declaration(),
                            "the declaration of " + step.id()));
        }
        return declarations;
    }
}
