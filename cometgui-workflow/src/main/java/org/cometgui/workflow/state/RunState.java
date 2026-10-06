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

package org.cometgui.workflow.state;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The state of a run as a whole, derived from the state of its stages.
 *
 * <p>The specification says the overall run state "shall be derived from step states", so there is
 * no setter and no stored field anywhere: {@link #deriveFrom(Map)}, over the stepper's stages, and
 * {@link #deriveFrom(Plan, Map)}, over a plan's engine steps, are the only ways to obtain one, and
 * each is a pure function of the states it is given. Both apply the one precedence below. A run
 * state that could be assigned independently would eventually contradict the stepper the user is
 * looking at.
 *
 * <h2>The precedence, in order</h2>
 *
 * <p>{@link #deriveFrom(Map)} applies these rules in this order and returns on the first match. The
 * order is the whole content of the type, so it is written out here and every rule and every
 * boundary between two rules has a test.
 *
 * <ol>
 *   <li><strong>{@link #CANCEL_REQUESTED}</strong> -- some stage, core or downstream, is {@link
 *       StepState#CANCEL_REQUESTED}. A stop has been asked for and not yet happened. This outranks
 *       everything because the UI must stop offering Cancel and must say that the request is in
 *       flight; folding it into {@code RUNNING} would leave the user pressing a button that already
 *       did its work.
 *   <li><strong>{@link #RUNNING}</strong> -- some stage is {@link StepState#isActive() active}. A
 *       run with a live process is not finished, whatever else has happened, so this is checked
 *       before failure: declaring a run {@code FAILED} while Comet is still writing would be a lie
 *       the provenance record would keep.
 *   <li><strong>{@link #NOT_STARTED}</strong> -- every stage is {@link StepState#isPending()
 *       pending}. Nothing has run.
 *   <li><strong>{@link #FAILED}</strong> -- some <em>core</em> stage is {@link StepState#FAILED}.
 *       Ranked above cancellation because a failure is information the user did not ask for and
 *       must not lose behind "you cancelled it", which they already know.
 *   <li><strong>{@link #CANCELLED}</strong> -- some <em>core</em> stage is {@link
 *       StepState#CANCELLED}. The run stopped because it was asked to, which is not a failure and
 *       must not be reported as one.
 *   <li><strong>{@link #RUNNING}</strong> -- some core stage is not {@link StepState#isTerminal()
 *       terminal}. Nothing is active and nothing has gone wrong, but the core path is not finished:
 *       this is the gap between one stage ending and the next starting, and the run is still in
 *       progress.
 *   <li><strong>{@link #SUCCEEDED}</strong> -- everything else: every core stage reached {@link
 *       StepState#SUCCEEDED} or {@link StepState#SKIPPED}.
 * </ol>
 *
 * <p>{@link StepState#SKIPPED} counts as satisfied rather than as an absence. A skipped stage is
 * one whose work is already done and still valid -- the specification's <em>Stage reruns</em>
 * section is explicit that changing a Percolator parameter reruns Percolator and not Comet -- so a
 * rerun in which Comet is skipped is a successful run, not a partial one.
 *
 * <h2>Decision: a failed optional downstream stage does not fail the run</h2>
 *
 * <p><strong>Rules 4 and 5 look at core stages only.</strong> If every core stage succeeded and
 * {@link WorkflowStage#PDV} or {@link WorkflowStage#LIMELIGHT_UPLOAD} then failed, the run is
 * {@link #SUCCEEDED}.
 *
 * <p>This is a deliberate decision and it goes the way it does because the run state is what the
 * user reads to decide whether to run the search again. The scientific result -- Comet, Percolator
 * and the parsed results -- is complete and correct in that situation; PDV failing to launch, or a
 * Limelight server refusing an upload, says nothing about it. Reporting a multi-hour search as
 * {@code FAILED} because a viewer would not start would push the user into repeating work that does
 * not need repeating, and the specification treats these stages as optional throughout: the
 * Definition of Done asks for an upload "where the platform permits", and Percolator versions that
 * cannot feed Limelight at all are supported for everything else.
 *
 * <p>The failure is not hidden. It is still {@link StepState#FAILED} on its own stage, the stepper
 * draws it, the console holds its messages and provenance records it. What it does not do is change
 * the verdict on the search.
 *
 * <p>Rules 1 and 2 do look at every stage, downstream ones included, because those two rules are
 * about whether anything is still happening rather than about whether the run was any good. A run
 * whose Limelight upload is still in flight is still {@link #RUNNING}.
 */
public enum RunState {

    /** No stage has run. */
    NOT_STARTED,

    /** Work is in flight, or the core path is started and not finished. */
    RUNNING,

    /** A cancellation has been asked for and has not yet taken effect. */
    CANCEL_REQUESTED,

    /** Every core stage succeeded or was skipped. */
    SUCCEEDED,

    /** A core stage failed. */
    FAILED,

    /** A core stage was cancelled, and none failed. */
    CANCELLED;

    /**
     * Derives the run state from the state of each stage.
     *
     * <p>The precedence is documented on this type, and each rule is tested. The map must name a
     * state for every {@link WorkflowStage#isCore() core} stage; the three optional downstream
     * stages may be absent, and an absent one is read as {@link StepState#NOT_STARTED}, because a
     * run that was never asked to open PDV is not a run with a hole in it. A stage mapped
     * explicitly to {@code null} is treated as absent, since a {@link Map} cannot distinguish the
     * two.
     *
     * @param stageStates the state of each stage; keys not present are absent
     * @return the derived run state, never {@code null}
     * @throws NullPointerException if {@code stageStates} is {@code null}
     * @throws IllegalArgumentException if a core stage has no state, naming the missing stages by
     *     {@link WorkflowStage#id()} in core-path order
     */
    public static RunState deriveFrom(Map<WorkflowStage, StepState> stageStates) {
        Map<WorkflowStage, StepState> states = complete(stageStates);
        List<StepState> core = new ArrayList<>();
        for (WorkflowStage stage : WorkflowStage.coreStages()) {
            core.add(states.get(stage));
        }
        return derive(states.values(), core);
    }

    /**
     * Derives the run state of a {@link Plan} from the state of each planned engine step.
     *
     * <p>The same precedence as {@link #deriveFrom(Map)}, documented on this type, applied to the
     * plan's steps instead of the stepper's stages: "every stage" becomes every planned step, and
     * "core" becomes every planned step that is not {@link EngineStep#isOptional() optional}. Every
     * optional step maps to an optional downstream stage and every other step to a core stage, so
     * the two readings agree.
     *
     * <p>This is the derivation for a run that does not plan every core stage -- a phase 08 run,
     * which finishes without Percolator. {@link #deriveFrom(Map)} cannot serve it: it requires a
     * state for {@link WorkflowStage#PERCOLATOR}, and the only state an unplanned stage could be
     * given that would let the run succeed is {@code SUCCEEDED} or {@code SKIPPED}, which would be
     * false. Here a step outside the plan has no state at all, and supplying one is refused.
     *
     * @param plan the plan the run executes
     * @param stepStates a state for every planned step, and for no other
     * @return the derived run state, never {@code null}
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if a planned step has no state, naming every such step in
     *     plan order, or a step outside the plan has one, naming those
     */
    public static RunState deriveFrom(Plan plan, Map<EngineStep, StepState> stepStates) {
        StageProjection.checkStates(plan, stepStates);
        List<StepState> every = new ArrayList<>();
        List<StepState> core = new ArrayList<>();
        for (EngineStep step : plan.steps()) {
            StepState state = stepStates.get(step);
            every.add(state);
            if (!step.isOptional()) {
                core.add(state);
            }
        }
        return derive(every, core);
    }

    /**
     * The precedence documented on this type, over every state and the core states.
     *
     * @param every the state of everything the run contains
     * @param core the states of its core part
     * @return the run state
     */
    private static RunState derive(Collection<StepState> every, Collection<StepState> core) {
        if (every.contains(StepState.CANCEL_REQUESTED)) {
            return CANCEL_REQUESTED;
        }
        if (anyIsActive(every)) {
            return RUNNING;
        }
        if (everyIsPending(every)) {
            return NOT_STARTED;
        }
        if (core.contains(StepState.FAILED)) {
            return FAILED;
        }
        if (core.contains(StepState.CANCELLED)) {
            return CANCELLED;
        }
        if (anyIsUnfinished(core)) {
            return RUNNING;
        }
        return SUCCEEDED;
    }

    /**
     * Fills in the absent downstream stages and rejects a map missing a core stage.
     *
     * @param stageStates the caller's map
     * @return a complete map over every stage
     */
    private static Map<WorkflowStage, StepState> complete(
            Map<WorkflowStage, StepState> stageStates) {
        Objects.requireNonNull(stageStates, "stageStates");
        Map<WorkflowStage, StepState> states = new EnumMap<>(WorkflowStage.class);
        List<String> missing = new ArrayList<>();
        for (WorkflowStage stage : WorkflowStage.values()) {
            StepState state = stageStates.get(stage);
            if (state == null) {
                if (stage.isCore()) {
                    missing.add(stage.id());
                }
                states.put(stage, StepState.NOT_STARTED);
            } else {
                states.put(stage, state);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "a run state cannot be derived without a state for every core stage; missing: "
                            + String.join(", ", missing));
        }
        return states;
    }

    private static boolean anyIsActive(Collection<StepState> states) {
        for (StepState state : states) {
            if (state.isActive()) {
                return true;
            }
        }
        return false;
    }

    private static boolean everyIsPending(Collection<StepState> states) {
        for (StepState state : states) {
            if (!state.isPending()) {
                return false;
            }
        }
        return true;
    }

    private static boolean anyIsUnfinished(Collection<StepState> states) {
        for (StepState state : states) {
            if (!state.isTerminal()) {
                return true;
            }
        }
        return false;
    }
}
