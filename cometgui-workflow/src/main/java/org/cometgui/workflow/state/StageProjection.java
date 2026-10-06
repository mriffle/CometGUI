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
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Projects the states of a plan's engine steps onto the stepper's {@link WorkflowStage stages}.
 *
 * <h2>A stage with no planned step is absent</h2>
 *
 * <p>{@link #project(Plan, Map)} returns a state only for the stages that have at least one planned
 * step. A phase 08 run does not plan Percolator, so its projection has no {@link
 * WorkflowStage#PERCOLATOR} entry at all: the stepper draws that stage as not planned. It is never
 * reported as {@link StepState#SUCCEEDED} or {@link StepState#SKIPPED}, which would claim work that
 * never happened. For the same reason the projection must not be handed to {@link
 * RunState#deriveFrom(Map)}, which requires every core stage and will refuse it; the run state of a
 * plan is {@link RunState#deriveFrom(Plan, Map)}.
 *
 * <h2>Combining several steps into one stage</h2>
 *
 * <p>{@link #combine(Collection)} reduces the states of a stage's planned steps to one, applying
 * these rules in order and returning on the first match. They follow {@link RunState}'s precedence,
 * so the stepper and the run state never tell different stories:
 *
 * <ol>
 *   <li>any {@link StepState#CANCEL_REQUESTED} -- {@code CANCEL_REQUESTED};
 *   <li>any {@link StepState#RUNNING} -- {@code RUNNING};
 *   <li>any {@link StepState#VALIDATING} -- {@code VALIDATING};
 *   <li>every step {@link StepState#isPending() pending}: {@link StepState#READY} if every one is
 *       {@code READY}, otherwise {@link StepState#NOT_STARTED};
 *   <li>any {@link StepState#FAILED} -- {@code FAILED};
 *   <li>any {@link StepState#CANCELLED} -- {@code CANCELLED};
 *   <li>any step not yet {@link StepState#isTerminal() terminal} -- {@code RUNNING}: some of the
 *       stage's steps are done and others are waiting, so the stage is in progress;
 *   <li>every step {@link StepState#SKIPPED} -- {@code SKIPPED};
 *   <li>otherwise -- {@link StepState#SUCCEEDED}: every step succeeded or was skipped, and at least
 *       one actually ran.
 * </ol>
 */
public final class StageProjection {

    private StageProjection() {}

    /**
     * The state of each stage that has a planned step.
     *
     * @param plan the plan
     * @param stepStates a state for every planned step, and for no other
     * @return an immutable map in {@link WorkflowStage} order, with no entry for a stage that has
     *     no planned step
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException as {@link RunState#deriveFrom(Plan, Map)}: a planned step
     *     with no state, or a state for a step outside the plan
     */
    public static Map<WorkflowStage, StepState> project(
            Plan plan, Map<EngineStep, StepState> stepStates) {
        checkStates(plan, stepStates);
        Map<WorkflowStage, StepState> stages = new EnumMap<>(WorkflowStage.class);
        for (WorkflowStage stage : WorkflowStage.values()) {
            List<StepState> states = new ArrayList<>();
            for (EngineStep step : plan.stepsIn(stage)) {
                states.add(stepStates.get(step));
            }
            if (!states.isEmpty()) {
                stages.put(stage, combine(states));
            }
        }
        return Collections.unmodifiableMap(stages);
    }

    /**
     * Combines the states of one stage's steps, by the rules on this type.
     *
     * @param states the states; not empty
     * @return the stage's state
     * @throws NullPointerException if {@code states} or an element is {@code null}
     * @throws IllegalArgumentException if {@code states} is empty
     */
    public static StepState combine(Collection<StepState> states) {
        Objects.requireNonNull(states, "states");
        if (states.isEmpty()) {
            throw new IllegalArgumentException("a stage with no steps has no state");
        }
        for (StepState state : states) {
            Objects.requireNonNull(state, "states contains null");
        }
        if (states.contains(StepState.CANCEL_REQUESTED)) {
            return StepState.CANCEL_REQUESTED;
        }
        if (states.contains(StepState.RUNNING)) {
            return StepState.RUNNING;
        }
        if (states.contains(StepState.VALIDATING)) {
            return StepState.VALIDATING;
        }
        if (all(states, StepState.READY)) {
            return StepState.READY;
        }
        if (allPending(states)) {
            return StepState.NOT_STARTED;
        }
        if (states.contains(StepState.FAILED)) {
            return StepState.FAILED;
        }
        if (states.contains(StepState.CANCELLED)) {
            return StepState.CANCELLED;
        }
        if (!allTerminal(states)) {
            return StepState.RUNNING;
        }
        if (all(states, StepState.SKIPPED)) {
            return StepState.SKIPPED;
        }
        return StepState.SUCCEEDED;
    }

    /**
     * Requires exactly the planned steps to have states. Shared with {@link RunState}.
     *
     * @param plan the plan
     * @param stepStates the states
     */
    static void checkStates(Plan plan, Map<EngineStep, StepState> stepStates) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(stepStates, "stepStates");
        List<String> missing = new ArrayList<>();
        for (EngineStep step : plan.steps()) {
            if (stepStates.get(step) == null) {
                missing.add(step.id());
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "every planned step needs a state; missing: " + String.join(", ", missing));
        }
        List<String> unplanned = new ArrayList<>();
        for (EngineStep step : EngineStep.values()) {
            if (stepStates.containsKey(step) && !plan.contains(step)) {
                unplanned.add(step.id());
            }
        }
        if (!unplanned.isEmpty()) {
            throw new IllegalArgumentException(
                    "a step outside the plan has no state, but one was given for: "
                            + String.join(", ", unplanned));
        }
    }

    private static boolean all(Collection<StepState> states, StepState wanted) {
        for (StepState state : states) {
            if (state != wanted) {
                return false;
            }
        }
        return true;
    }

    private static boolean allPending(Collection<StepState> states) {
        for (StepState state : states) {
            if (!state.isPending()) {
                return false;
            }
        }
        return true;
    }

    private static boolean allTerminal(Collection<StepState> states) {
        for (StepState state : states) {
            if (!state.isTerminal()) {
                return false;
            }
        }
        return true;
    }
}
