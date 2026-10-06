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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.cometgui.workflow.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link RunState#deriveFrom(Plan, Map)}: the run state over a plan's engine steps, with
 * the same precedence as the stage-based derivation, which {@link RunStateTest} still covers
 * unchanged.
 */
class RunStateOverPlanTest {

    private static RunState derive(Plan plan, Map<EngineStep, StepState> states) {
        return RunState.deriveFrom(plan, states);
    }

    @Test
    @DisplayName("a phase 08 run finishes without Percolator")
    void phase08RunSucceedsWithoutPercolator() {
        Plan plan = Scenario.phase08();

        assertEquals(
                RunState.SUCCEEDED, derive(plan, Scenario.allPlanned(plan, StepState.SUCCEEDED)));
    }

    @Test
    @DisplayName("...which the stage-based derivation refuses, because Percolator has no state")
    void theStageDerivationRefusesAPhase08Projection() {
        Plan plan = Scenario.phase08();
        Map<WorkflowStage, StepState> projected =
                StageProjection.project(plan, Scenario.allPlanned(plan, StepState.SUCCEEDED));

        assertEquals(
                "a run state cannot be derived without a state for every core stage; missing:"
                        + " percolator",
                assertThrows(IllegalArgumentException.class, () -> RunState.deriveFrom(projected))
                        .getMessage());
    }

    @Test
    @DisplayName("every planned step pending is not started")
    void notStarted() {
        Plan plan = Scenario.full();
        Map<EngineStep, StepState> states = Scenario.allPlanned(plan, StepState.READY);
        states.put(EngineStep.MERGE_PIN, StepState.NOT_STARTED);

        assertEquals(RunState.NOT_STARTED, derive(plan, states));
    }

    @Test
    @DisplayName("a cancel request on an optional step outranks a running core step")
    void cancelRequestedAnywhere() {
        Plan plan = Scenario.full();
        Map<EngineStep, StepState> states = Scenario.allPlanned(plan, StepState.SUCCEEDED);
        states.put(EngineStep.RUN_COMET, StepState.RUNNING);
        states.put(EngineStep.UPLOAD_LIMELIGHT, StepState.CANCEL_REQUESTED);

        assertEquals(RunState.CANCEL_REQUESTED, derive(plan, states));
    }

    @Test
    @DisplayName("an active optional step keeps the run running, though the core failed")
    void activeOptionalStep() {
        Plan plan = Scenario.full();
        Map<EngineStep, StepState> states = Scenario.allPlanned(plan, StepState.SUCCEEDED);
        states.put(EngineStep.RUN_COMET, StepState.FAILED);
        states.put(EngineStep.LAUNCH_PDV, StepState.VALIDATING);

        assertEquals(RunState.RUNNING, derive(plan, states));
    }

    @Test
    @DisplayName("a failed core step fails the run, outranking a cancelled one")
    void coreFailure() {
        Plan plan = Scenario.full();
        Map<EngineStep, StepState> states = Scenario.allPlanned(plan, StepState.SUCCEEDED);
        states.put(EngineStep.MERGE_PIN, StepState.CANCELLED);
        states.put(EngineStep.APPEND_DOWNSTREAM_PROVENANCE, StepState.FAILED);

        assertEquals(RunState.FAILED, derive(plan, states));
    }

    @Test
    @DisplayName("a cancelled core step cancels the run")
    void coreCancellation() {
        Plan plan = Scenario.phase08();
        Map<EngineStep, StepState> states = Scenario.allPlanned(plan, StepState.SUCCEEDED);
        states.put(EngineStep.RUN_COMET, StepState.CANCELLED);
        states.put(EngineStep.VALIDATE_COMET_OUTPUTS, StepState.NOT_STARTED);

        assertEquals(RunState.CANCELLED, derive(plan, states));
    }

    @Test
    @DisplayName("a failed or cancelled optional step does not change the verdict on the search")
    void optionalFailure() {
        Plan plan = Scenario.full();
        Map<EngineStep, StepState> states = Scenario.allPlanned(plan, StepState.SUCCEEDED);
        states.put(EngineStep.CONVERT_LIMELIGHT, StepState.FAILED);
        states.put(EngineStep.UPLOAD_LIMELIGHT, StepState.CANCELLED);
        states.put(EngineStep.LAUNCH_PDV, StepState.NOT_STARTED);

        assertEquals(RunState.SUCCEEDED, derive(plan, states));
    }

    @Test
    @DisplayName("a core step still waiting keeps the run running")
    void coreUnfinished() {
        Plan plan = Scenario.phase08();
        Map<EngineStep, StepState> states = Scenario.allPlanned(plan, StepState.SKIPPED);
        states.put(EngineStep.FINALISE_PROVENANCE, StepState.READY);

        assertEquals(RunState.RUNNING, derive(plan, states));
    }

    @Test
    @DisplayName("a rerun reusing everything but conversion succeeds")
    void skippedCountsAsSatisfied() {
        Plan plan = Scenario.full();
        Map<EngineStep, StepState> states = Scenario.allPlanned(plan, StepState.SKIPPED);
        states.put(EngineStep.CONVERT_LIMELIGHT, StepState.SUCCEEDED);

        assertEquals(RunState.SUCCEEDED, derive(plan, states));
    }

    @Test
    @DisplayName("states that do not match the plan are refused, naming the steps")
    void refusals() {
        Plan plan = Scenario.phase08();
        Map<EngineStep, StepState> missing = Scenario.allPlanned(plan, StepState.SUCCEEDED);
        missing.remove(EngineStep.HASH_INPUTS);
        Map<EngineStep, StepState> extra = Scenario.allPlanned(plan, StepState.SUCCEEDED);
        extra.put(EngineStep.RUN_PERCOLATOR, StepState.SKIPPED);

        assertEquals(
                "every planned step needs a state; missing: hash-inputs",
                assertThrows(IllegalArgumentException.class, () -> derive(plan, missing))
                        .getMessage());
        assertEquals(
                "a step outside the plan has no state, but one was given for: run-percolator",
                assertThrows(IllegalArgumentException.class, () -> derive(plan, extra))
                        .getMessage());
        assertEquals(
                "plan",
                assertThrows(
                                NullPointerException.class,
                                () -> derive(Nulls.of(Plan.class), Map.of()))
                        .getMessage());
    }
}
