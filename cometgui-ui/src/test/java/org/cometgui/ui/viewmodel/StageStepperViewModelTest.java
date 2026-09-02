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

package org.cometgui.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javafx.beans.property.Property;
import javafx.collections.MapChangeListener;
import org.cometgui.ui.testing.Nulls;
import org.cometgui.workflow.state.RunState;
import org.cometgui.workflow.state.StepState;
import org.cometgui.workflow.state.WorkflowStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The stage stepper's state holder.
 *
 * <p>The run-state assertions deliberately compare against {@link RunState#deriveFrom(Map)} applied
 * to the same stage states as well as against the expected constant. A view-model that had grown
 * its own copy of the derivation would satisfy the constant and diverge from the workflow module
 * the moment either changed; comparing with both says which of the two a failure is about.
 */
class StageStepperViewModelTest {

    private final StageStepperViewModel stepper = new StageStepperViewModel();

    private static Map<WorkflowStage, StepState> statesOf(StageStepperViewModel model) {
        Map<WorkflowStage, StepState> states = new EnumMap<>(WorkflowStage.class);
        for (WorkflowStage stage : WorkflowStage.values()) {
            states.put(stage, model.stateOf(stage));
        }
        return states;
    }

    private void assertRunStateIs(RunState expected) {
        assertEquals(expected, stepper.runState());
        assertEquals(
                RunState.deriveFrom(statesOf(stepper)),
                stepper.runState(),
                "the view-model must call the workflow module's derivation, not its own");
    }

    @Nested
    @DisplayName("the initial state")
    class InitialState {

        @ParameterizedTest
        @EnumSource(WorkflowStage.class)
        @DisplayName("is NOT_STARTED for every stage")
        void everyStageStartsNotStarted(WorkflowStage stage) {
            assertEquals(StepState.NOT_STARTED, stepper.stateOf(stage));
        }

        @Test
        @DisplayName("holds a state for all eight stages")
        void everyStageHasAState() {
            assertEquals(WorkflowStage.values().length, stepper.stageStates().size());
            assertEquals(8, stepper.stageStates().size());
        }

        @Test
        @DisplayName("derives a NOT_STARTED run")
        void theRunHasNotStarted() {
            assertRunStateIs(RunState.NOT_STARTED);
        }
    }

    @Nested
    @DisplayName("the stepper's shape")
    class Shape {

        @Test
        @DisplayName("is the core path from WorkflowStage, not a copy of the diagram")
        void coreStagesComeFromWorkflowStage() {
            assertEquals(WorkflowStage.coreStages(), stepper.coreStages());
            assertEquals(
                    List.of(
                            WorkflowStage.INPUTS,
                            WorkflowStage.VALIDATE,
                            WorkflowStage.COMET,
                            WorkflowStage.PERCOLATOR,
                            WorkflowStage.RESULTS),
                    stepper.coreStages());
        }

        @Test
        @DisplayName("is the downstream branches from WorkflowStage, in drawing order")
        void downstreamBranchesComeFromWorkflowStage() {
            assertEquals(WorkflowStage.downstreamBranches(), stepper.downstreamBranches());
            assertEquals(
                    List.of(
                            List.of(WorkflowStage.PDV),
                            List.of(WorkflowStage.LIMELIGHT_XML, WorkflowStage.LIMELIGHT_UPLOAD)),
                    stepper.downstreamBranches());
        }

        @Test
        @DisplayName("flattens to all eight stages, core path first")
        void drawOrderIsCoreThenBranches() {
            assertEquals(
                    List.of(
                            WorkflowStage.INPUTS,
                            WorkflowStage.VALIDATE,
                            WorkflowStage.COMET,
                            WorkflowStage.PERCOLATOR,
                            WorkflowStage.RESULTS,
                            WorkflowStage.PDV,
                            WorkflowStage.LIMELIGHT_XML,
                            WorkflowStage.LIMELIGHT_UPLOAD),
                    stepper.stagesInDrawOrder());
        }

        @Test
        @DisplayName("draws every stage exactly once")
        void drawOrderCoversEveryStageOnce() {
            List<WorkflowStage> drawn = stepper.stagesInDrawOrder();
            assertEquals(WorkflowStage.values().length, drawn.size());
            assertEquals(List.of(WorkflowStage.values()).size(), List.copyOf(drawn).size());
            for (WorkflowStage stage : WorkflowStage.values()) {
                assertEquals(
                        1,
                        java.util.Collections.frequency(drawn, stage),
                        stage
                                + " is drawn "
                                + java.util.Collections.frequency(drawn, stage)
                                + " times");
            }
        }
    }

    @Nested
    @DisplayName("setting a stage state")
    class SettingState {

        @Test
        @DisplayName("changes that stage and leaves the others alone")
        void changesOnlyThatStage() {
            stepper.setState(WorkflowStage.COMET, StepState.RUNNING);
            assertEquals(StepState.RUNNING, stepper.stateOf(WorkflowStage.COMET));
            assertEquals(StepState.NOT_STARTED, stepper.stateOf(WorkflowStage.PERCOLATOR));
            assertEquals(StepState.NOT_STARTED, stepper.stateOf(WorkflowStage.INPUTS));
        }

        @Test
        @DisplayName("is observable on the stage map, with the old and new state")
        void isObservableOnTheMap() {
            List<String> changes = new ArrayList<>();
            stepper.stageStates()
                    .addListener(
                            (MapChangeListener<WorkflowStage, StepState>)
                                    change ->
                                            changes.add(
                                                    change.getKey()
                                                            + ": "
                                                            + change.getValueRemoved()
                                                            + "->"
                                                            + change.getValueAdded()));
            stepper.setState(WorkflowStage.VALIDATE, StepState.VALIDATING);
            stepper.setState(WorkflowStage.VALIDATE, StepState.SUCCEEDED);
            assertEquals(
                    List.of("VALIDATE: NOT_STARTED->VALIDATING", "VALIDATE: VALIDATING->SUCCEEDED"),
                    changes);
        }

        @Test
        @DisplayName("rejects a null stage, naming the argument")
        void rejectsANullStage() {
            NullPointerException thrown =
                    assertThrows(
                            NullPointerException.class,
                            () ->
                                    stepper.setState(
                                            Nulls.of(WorkflowStage.class), StepState.RUNNING));
            assertEquals("stage", thrown.getMessage());
        }

        @Test
        @DisplayName("rejects a null state, naming the argument, and changes nothing")
        void rejectsANullState() {
            NullPointerException thrown =
                    assertThrows(
                            NullPointerException.class,
                            () -> stepper.setState(WorkflowStage.COMET, Nulls.of(StepState.class)));
            assertEquals("state", thrown.getMessage());
            assertEquals(StepState.NOT_STARTED, stepper.stateOf(WorkflowStage.COMET));
        }

        @Test
        @DisplayName("rejects reading a null stage, naming the argument")
        void rejectsReadingANullStage() {
            NullPointerException thrown =
                    assertThrows(
                            NullPointerException.class,
                            () -> stepper.stateOf(Nulls.of(WorkflowStage.class)));
            assertEquals("stage", thrown.getMessage());
        }
    }

    @Nested
    @DisplayName("the derived run state")
    class DerivedRunState {

        private void succeedCorePath() {
            for (WorkflowStage stage : WorkflowStage.coreStages()) {
                stepper.setState(stage, StepState.SUCCEEDED);
            }
        }

        @Test
        @DisplayName("is RUNNING while a stage is running")
        void runsWhileAStageRuns() {
            stepper.setState(WorkflowStage.INPUTS, StepState.SUCCEEDED);
            stepper.setState(WorkflowStage.COMET, StepState.RUNNING);
            assertRunStateIs(RunState.RUNNING);
        }

        @Test
        @DisplayName("is CANCEL_REQUESTED as soon as a cancellation is asked for")
        void reportsACancellationRequest() {
            stepper.setState(WorkflowStage.COMET, StepState.RUNNING);
            stepper.setState(WorkflowStage.COMET, StepState.CANCEL_REQUESTED);
            assertRunStateIs(RunState.CANCEL_REQUESTED);
        }

        @Test
        @DisplayName("is FAILED when a core stage fails")
        void failsWhenACoreStageFails() {
            stepper.setState(WorkflowStage.INPUTS, StepState.SUCCEEDED);
            stepper.setState(WorkflowStage.VALIDATE, StepState.SUCCEEDED);
            stepper.setState(WorkflowStage.COMET, StepState.FAILED);
            assertRunStateIs(RunState.FAILED);
        }

        @Test
        @DisplayName("is CANCELLED when a core stage was cancelled and none failed")
        void reportsACancelledRun() {
            stepper.setState(WorkflowStage.INPUTS, StepState.SUCCEEDED);
            stepper.setState(WorkflowStage.COMET, StepState.CANCELLED);
            assertRunStateIs(RunState.CANCELLED);
        }

        @Test
        @DisplayName("is SUCCEEDED when every core stage succeeded")
        void succeedsWhenTheCorePathSucceeds() {
            succeedCorePath();
            assertRunStateIs(RunState.SUCCEEDED);
        }

        @Test
        @DisplayName("stays SUCCEEDED when an optional downstream stage fails")
        void anOptionalDownstreamFailureDoesNotFailTheRun() {
            succeedCorePath();
            stepper.setState(WorkflowStage.LIMELIGHT_UPLOAD, StepState.FAILED);
            assertRunStateIs(RunState.SUCCEEDED);
            assertEquals(
                    StepState.FAILED,
                    stepper.stateOf(WorkflowStage.LIMELIGHT_UPLOAD),
                    "the stage itself still shows the failure the stepper draws");
        }

        @Test
        @DisplayName("notifies observers with the state moved from and the state moved to")
        void isObservable() {
            List<String> events = new ArrayList<>();
            stepper.runStateProperty()
                    .addListener((observable, was, now) -> events.add(was + "->" + now));
            stepper.setState(WorkflowStage.COMET, StepState.RUNNING);
            stepper.setState(WorkflowStage.COMET, StepState.FAILED);
            assertEquals(List.of("NOT_STARTED->RUNNING", "RUNNING->FAILED"), events);
        }

        @Test
        @DisplayName("fires nothing when a stage change leaves the run state alone")
        void isSilentWhenTheRunStateDoesNotChange() {
            stepper.setState(WorkflowStage.COMET, StepState.RUNNING);
            List<String> events = new ArrayList<>();
            stepper.runStateProperty()
                    .addListener((observable, was, now) -> events.add(was + "->" + now));
            stepper.setState(WorkflowStage.INPUTS, StepState.SUCCEEDED);
            assertEquals(List.of(), events);
            assertEquals(RunState.RUNNING, stepper.runState());
        }
    }

    @Nested
    @DisplayName("the published state")
    class PublishedState {

        @Test
        @DisplayName("cannot be written through the stage map")
        void theStageMapIsUnmodifiable() {
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> stepper.stageStates().put(WorkflowStage.COMET, StepState.SUCCEEDED));
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> stepper.stageStates().remove(WorkflowStage.COMET));
        }

        @Test
        @DisplayName("keeps the run state read-only, because it is derived")
        void theRunStatePropertyIsNotWritable() {
            assertFalse(
                    stepper.runStateProperty() instanceof Property,
                    "a run state that could be assigned would contradict the stage states");
        }
    }
}
