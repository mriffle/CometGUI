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

import static org.cometgui.workflow.state.StepState.CANCELLED;
import static org.cometgui.workflow.state.StepState.CANCEL_REQUESTED;
import static org.cometgui.workflow.state.StepState.FAILED;
import static org.cometgui.workflow.state.StepState.NOT_STARTED;
import static org.cometgui.workflow.state.StepState.READY;
import static org.cometgui.workflow.state.StepState.RUNNING;
import static org.cometgui.workflow.state.StepState.SKIPPED;
import static org.cometgui.workflow.state.StepState.SUCCEEDED;
import static org.cometgui.workflow.state.StepState.VALIDATING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.cometgui.workflow.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Tests for {@link StageProjection}: one test per combining rule and per boundary. */
class StageProjectionTest {

    @Nested
    @DisplayName("combining one stage's steps")
    class Combine {

        @ParameterizedTest(name = "[{index}] {0}")
        @EnumSource(StepState.class)
        @DisplayName("a single step's state is the stage's state")
        void single(StepState state) {
            assertEquals(state, StageProjection.combine(List.of(state)));
        }

        @Test
        @DisplayName("1: a cancel request outranks running and failure")
        void cancelRequested() {
            assertEquals(
                    CANCEL_REQUESTED,
                    StageProjection.combine(List.of(FAILED, RUNNING, CANCEL_REQUESTED)));
        }

        @Test
        @DisplayName("2: running outranks validating and failure")
        void running() {
            assertEquals(RUNNING, StageProjection.combine(List.of(FAILED, VALIDATING, RUNNING)));
        }

        @Test
        @DisplayName("3: validating outranks failure and waiting")
        void validating() {
            assertEquals(
                    VALIDATING, StageProjection.combine(List.of(NOT_STARTED, FAILED, VALIDATING)));
        }

        @Test
        @DisplayName("4: all ready is ready; pending but not all ready is not started")
        void pending() {
            assertEquals(READY, StageProjection.combine(List.of(READY, READY)));
            assertEquals(NOT_STARTED, StageProjection.combine(List.of(READY, NOT_STARTED)));
            assertEquals(NOT_STARTED, StageProjection.combine(List.of(NOT_STARTED, READY)));
        }

        @Test
        @DisplayName("5: failure outranks cancellation and waiting")
        void failed() {
            assertEquals(FAILED, StageProjection.combine(List.of(NOT_STARTED, CANCELLED, FAILED)));
        }

        @Test
        @DisplayName("6: cancellation outranks success and waiting")
        void cancelled() {
            assertEquals(CANCELLED, StageProjection.combine(List.of(SUCCEEDED, READY, CANCELLED)));
        }

        @Test
        @DisplayName("7: some steps done and some waiting is a stage in progress")
        void inProgress() {
            assertEquals(RUNNING, StageProjection.combine(List.of(SUCCEEDED, NOT_STARTED)));
            assertEquals(RUNNING, StageProjection.combine(List.of(SKIPPED, READY)));
        }

        @Test
        @DisplayName("8: every step skipped is a skipped stage")
        void skipped() {
            assertEquals(SKIPPED, StageProjection.combine(List.of(SKIPPED, SKIPPED)));
        }

        @Test
        @DisplayName("9: success with skips is success")
        void succeeded() {
            assertEquals(SUCCEEDED, StageProjection.combine(List.of(SKIPPED, SUCCEEDED)));
            assertEquals(SUCCEEDED, StageProjection.combine(List.of(SUCCEEDED, SKIPPED)));
        }

        @Test
        @DisplayName("an empty or null collection is refused")
        void refusals() {
            assertEquals(
                    "a stage with no steps has no state",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> StageProjection.combine(List.of()))
                            .getMessage());
            @SuppressWarnings("unchecked")
            Collection<StepState> none = Nulls.of(Collection.class);
            assertEquals(
                    "states",
                    assertThrows(NullPointerException.class, () -> StageProjection.combine(none))
                            .getMessage());
            List<StepState> withNull = new ArrayList<>();
            withNull.add(SUCCEEDED);
            withNull.add(null);
            assertEquals(
                    "states contains null",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> StageProjection.combine(withNull))
                            .getMessage());
        }
    }

    @Nested
    @DisplayName("projecting a plan onto the stepper")
    class Project {

        @Test
        @DisplayName("a phase 08 run has no Percolator stage at all, and is never 'succeeded'")
        void phase08HasNoPercolatorStage() {
            Plan plan = Scenario.phase08();
            Map<EngineStep, StepState> states = Scenario.allPlanned(plan, SUCCEEDED);
            states.put(EngineStep.MERGE_PIN, RUNNING);
            states.put(EngineStep.FINALISE_PROVENANCE, NOT_STARTED);

            Map<WorkflowStage, StepState> expected = new EnumMap<>(WorkflowStage.class);
            expected.put(WorkflowStage.INPUTS, SUCCEEDED);
            expected.put(WorkflowStage.VALIDATE, SUCCEEDED);
            expected.put(WorkflowStage.COMET, RUNNING);
            expected.put(WorkflowStage.RESULTS, NOT_STARTED);
            assertEquals(expected, StageProjection.project(plan, states));
        }

        @Test
        @DisplayName("a full plan projects onto all eight stages")
        void fullPlan() {
            Plan plan = Scenario.full();
            Map<EngineStep, StepState> states = Scenario.allPlanned(plan, SKIPPED);
            states.put(EngineStep.CONVERT_LIMELIGHT, FAILED);
            states.put(EngineStep.RUN_PERCOLATOR, SUCCEEDED);

            Map<WorkflowStage, StepState> expected = new EnumMap<>(WorkflowStage.class);
            expected.put(WorkflowStage.INPUTS, SKIPPED);
            expected.put(WorkflowStage.VALIDATE, SKIPPED);
            expected.put(WorkflowStage.COMET, SKIPPED);
            expected.put(WorkflowStage.PERCOLATOR, SUCCEEDED);
            expected.put(WorkflowStage.RESULTS, SKIPPED);
            expected.put(WorkflowStage.PDV, SKIPPED);
            expected.put(WorkflowStage.LIMELIGHT_XML, FAILED);
            expected.put(WorkflowStage.LIMELIGHT_UPLOAD, SKIPPED);
            assertEquals(expected, StageProjection.project(plan, states));
        }

        @Test
        @DisplayName("a planned step without a state is refused, naming every one in plan order")
        void missingStates() {
            Plan plan = Scenario.phase08();
            Map<EngineStep, StepState> states = Scenario.allPlanned(plan, SUCCEEDED);
            states.remove(EngineStep.MERGE_PIN);
            states.remove(EngineStep.RESOLVE_COMET);

            assertEquals(
                    "every planned step needs a state; missing: resolve-comet, merge-pin",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> StageProjection.project(plan, states))
                            .getMessage());
        }

        @Test
        @DisplayName("a state for a step outside the plan is refused, naming it")
        void unplannedStates() {
            Plan plan = Scenario.phase08();
            Map<EngineStep, StepState> states = Scenario.allPlanned(plan, SUCCEEDED);
            states.put(EngineStep.UPLOAD_LIMELIGHT, SKIPPED);
            states.put(EngineStep.RUN_PERCOLATOR, SUCCEEDED);

            assertEquals(
                    "a step outside the plan has no state, but one was given for:"
                            + " run-percolator, upload-limelight",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> StageProjection.project(plan, states))
                            .getMessage());
        }

        @Test
        @DisplayName("null arguments are refused, naming them")
        void nulls() {
            @SuppressWarnings("unchecked")
            Map<EngineStep, StepState> none = Nulls.of(Map.class);
            assertEquals(
                    "plan",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> StageProjection.project(Nulls.of(Plan.class), Map.of()))
                            .getMessage());
            assertEquals(
                    "stepStates",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> StageProjection.project(Scenario.phase08(), none))
                            .getMessage());
        }
    }
}
