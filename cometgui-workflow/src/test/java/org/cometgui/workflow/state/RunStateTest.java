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

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.cometgui.workflow.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Tests for {@link RunState#deriveFrom(Map)}.
 *
 * <p>One test per precedence rule, and one per boundary between two rules -- a state of the map
 * that satisfies both, asserting which of the two wins. The boundaries are the part worth testing:
 * any single rule is obvious in isolation, and every real disagreement about a derived run state is
 * a disagreement about which rule should have fired first.
 */
class RunStateTest {

    @Test
    @DisplayName("the six run states are the derived ones and nothing else")
    void constantsAreTheSixDerivedStates() {
        assertEquals(
                List.of(
                        RunState.NOT_STARTED,
                        RunState.RUNNING,
                        RunState.CANCEL_REQUESTED,
                        RunState.SUCCEEDED,
                        RunState.FAILED,
                        RunState.CANCELLED),
                List.of(RunState.values()));
    }

    @Nested
    @DisplayName("an incomplete map is rejected")
    class Rejection {

        @Test
        @DisplayName("a null map is rejected, naming the parameter")
        void nullMapIsRejected() {
            @SuppressWarnings("unchecked")
            Map<WorkflowStage, StepState> noStates = Nulls.of(Map.class);

            NullPointerException thrown =
                    assertThrows(NullPointerException.class, () -> RunState.deriveFrom(noStates));
            assertEquals("stageStates", thrown.getMessage());
        }

        @ParameterizedTest(name = "[{index}] without {0}")
        @EnumSource(
                value = WorkflowStage.class,
                names = {"INPUTS", "VALIDATE", "COMET", "PERCOLATOR", "RESULTS"})
        @DisplayName("a map missing one core stage is rejected, naming that stage")
        void aMissingCoreStageIsRejected(WorkflowStage missing) {
            Map<WorkflowStage, StepState> states = allCore(StepState.SUCCEEDED);
            states.remove(missing);

            IllegalArgumentException thrown =
                    assertThrows(IllegalArgumentException.class, () -> RunState.deriveFrom(states));
            assertEquals(
                    "a run state cannot be derived without a state for every core stage; missing: "
                            + missing.id(),
                    thrown.getMessage());
        }

        @Test
        @DisplayName("several missing core stages are all named, in core-path order")
        void severalMissingCoreStagesAreAllNamed() {
            Map<WorkflowStage, StepState> states = allCore(StepState.SUCCEEDED);
            states.remove(WorkflowStage.PERCOLATOR);
            states.remove(WorkflowStage.VALIDATE);

            IllegalArgumentException thrown =
                    assertThrows(IllegalArgumentException.class, () -> RunState.deriveFrom(states));
            assertEquals(
                    "a run state cannot be derived without a state for every core stage; missing: "
                            + "validate, percolator",
                    thrown.getMessage());
        }

        @Test
        @DisplayName("an empty map is rejected, naming all five core stages")
        void anEmptyMapIsRejected() {
            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class, () -> RunState.deriveFrom(Map.of()));
            assertEquals(
                    "a run state cannot be derived without a state for every core stage; missing: "
                            + "inputs, validate, comet, percolator, results",
                    thrown.getMessage());
        }

        @Test
        @DisplayName("a core stage mapped explicitly to null is rejected like an absent one")
        void aCoreStageMappedToNullIsRejected() {
            Map<WorkflowStage, StepState> states = new HashMap<>(allCore(StepState.SUCCEEDED));
            states.put(WorkflowStage.COMET, null);

            IllegalArgumentException thrown =
                    assertThrows(IllegalArgumentException.class, () -> RunState.deriveFrom(states));
            assertEquals(
                    "a run state cannot be derived without a state for every core stage; missing: "
                            + "comet",
                    thrown.getMessage());
        }

        @Test
        @DisplayName("the three downstream stages may be absent: a core-only map derives")
        void absentDownstreamStagesAreReadAsNotStarted() {
            assertEquals(RunState.SUCCEEDED, RunState.deriveFrom(allCore(StepState.SUCCEEDED)));
        }
    }

    @Nested
    @DisplayName("rule 1 -- CANCEL_REQUESTED anywhere outranks everything")
    class CancelRequestedRule {

        @ParameterizedTest(name = "[{index}] on {0}")
        @EnumSource(WorkflowStage.class)
        @DisplayName(
                "a cancel request on any stage, core or downstream, makes the run "
                        + "CANCEL_REQUESTED")
        void aCancelRequestOnAnyStageWins(WorkflowStage stage) {
            Map<WorkflowStage, StepState> states = allCore(StepState.SUCCEEDED);
            states.put(stage, StepState.CANCEL_REQUESTED);

            assertEquals(RunState.CANCEL_REQUESTED, RunState.deriveFrom(states));
        }

        @Test
        @DisplayName("it outranks a running stage: the Cancel button must stop being offered")
        void itOutranksRunning() {
            Map<WorkflowStage, StepState> states = allCore(StepState.SUCCEEDED);
            states.put(WorkflowStage.COMET, StepState.RUNNING);
            states.put(WorkflowStage.PERCOLATOR, StepState.CANCEL_REQUESTED);

            assertEquals(RunState.CANCEL_REQUESTED, RunState.deriveFrom(states));
        }

        @Test
        @DisplayName("it outranks a failed core stage while the cancellation is still in flight")
        void itOutranksAFailedCoreStage() {
            Map<WorkflowStage, StepState> states = allCore(StepState.SUCCEEDED);
            states.put(WorkflowStage.COMET, StepState.FAILED);
            states.put(WorkflowStage.PERCOLATOR, StepState.CANCEL_REQUESTED);

            assertEquals(RunState.CANCEL_REQUESTED, RunState.deriveFrom(states));
        }
    }

    @Nested
    @DisplayName("rule 2 -- an active stage makes the run RUNNING")
    class RunningRule {

        @ParameterizedTest(name = "[{index}] {0} on COMET")
        @EnumSource(
                value = StepState.class,
                names = {"VALIDATING", "RUNNING"})
        @DisplayName("any active state on a core stage makes the run RUNNING")
        void anActiveCoreStageMakesTheRunRunning(StepState active) {
            Map<WorkflowStage, StepState> states = allCore(StepState.NOT_STARTED);
            states.put(WorkflowStage.COMET, active);

            assertEquals(RunState.RUNNING, RunState.deriveFrom(states));
        }

        @Test
        @DisplayName("a downstream stage still working keeps the whole run RUNNING")
        void aRunningDownstreamStageKeepsTheRunRunning() {
            Map<WorkflowStage, StepState> states = allCore(StepState.SUCCEEDED);
            states.put(WorkflowStage.LIMELIGHT_XML, StepState.RUNNING);

            assertEquals(RunState.RUNNING, RunState.deriveFrom(states));
        }

        @Test
        @DisplayName("it outranks a failed core stage: a run with a live process is not finished")
        void itOutranksAFailedCoreStage() {
            Map<WorkflowStage, StepState> states = allCore(StepState.NOT_STARTED);
            states.put(WorkflowStage.COMET, StepState.FAILED);
            states.put(WorkflowStage.PERCOLATOR, StepState.RUNNING);

            assertEquals(RunState.RUNNING, RunState.deriveFrom(states));
        }

        @Test
        @DisplayName("it outranks a cancelled core stage while another stage is still running")
        void itOutranksACancelledCoreStage() {
            Map<WorkflowStage, StepState> states = allCore(StepState.NOT_STARTED);
            states.put(WorkflowStage.COMET, StepState.CANCELLED);
            states.put(WorkflowStage.PERCOLATOR, StepState.RUNNING);

            assertEquals(RunState.RUNNING, RunState.deriveFrom(states));
        }
    }

    @Nested
    @DisplayName("rule 3 -- nothing has happened yet")
    class NotStartedRule {

        @Test
        @DisplayName("every stage NOT_STARTED makes the run NOT_STARTED")
        void everyStageNotStarted() {
            assertEquals(
                    RunState.NOT_STARTED, RunState.deriveFrom(allStages(StepState.NOT_STARTED)));
        }

        @Test
        @DisplayName("READY is still pending: a fully validated but unstarted run is NOT_STARTED")
        void everyStageReady() {
            assertEquals(RunState.NOT_STARTED, RunState.deriveFrom(allStages(StepState.READY)));
        }

        @Test
        @DisplayName("a mixture of NOT_STARTED and READY is still NOT_STARTED")
        void aMixtureOfPendingStates() {
            Map<WorkflowStage, StepState> states = allStages(StepState.NOT_STARTED);
            states.put(WorkflowStage.INPUTS, StepState.READY);
            states.put(WorkflowStage.VALIDATE, StepState.READY);

            assertEquals(RunState.NOT_STARTED, RunState.deriveFrom(states));
        }

        @Test
        @DisplayName("one finished core stage is enough to leave NOT_STARTED behind")
        void oneFinishedStageEndsNotStarted() {
            Map<WorkflowStage, StepState> states = allCore(StepState.NOT_STARTED);
            states.put(WorkflowStage.INPUTS, StepState.SUCCEEDED);

            assertEquals(RunState.RUNNING, RunState.deriveFrom(states));
        }

        @Test
        @DisplayName("a downstream stage that has finished also leaves NOT_STARTED behind")
        void aFinishedDownstreamStageEndsNotStarted() {
            Map<WorkflowStage, StepState> states = allStages(StepState.NOT_STARTED);
            states.put(WorkflowStage.PDV, StepState.SUCCEEDED);

            assertEquals(RunState.RUNNING, RunState.deriveFrom(states));
        }
    }

    @Nested
    @DisplayName("rules 4 and 5 -- failure and cancellation, from the core stages only")
    class FailureAndCancellationRules {

        @ParameterizedTest(name = "[{index}] {0} failed")
        @EnumSource(
                value = WorkflowStage.class,
                names = {"INPUTS", "VALIDATE", "COMET", "PERCOLATOR", "RESULTS"})
        @DisplayName("any failed core stage makes the run FAILED")
        void aFailedCoreStageFailsTheRun(WorkflowStage stage) {
            Map<WorkflowStage, StepState> states = allCore(StepState.SUCCEEDED);
            states.put(stage, StepState.FAILED);

            assertEquals(RunState.FAILED, RunState.deriveFrom(states));
        }

        @ParameterizedTest(name = "[{index}] {0} cancelled")
        @EnumSource(
                value = WorkflowStage.class,
                names = {"INPUTS", "VALIDATE", "COMET", "PERCOLATOR", "RESULTS"})
        @DisplayName("any cancelled core stage makes the run CANCELLED")
        void aCancelledCoreStageCancelsTheRun(WorkflowStage stage) {
            Map<WorkflowStage, StepState> states = allCore(StepState.SUCCEEDED);
            states.put(stage, StepState.CANCELLED);

            assertEquals(RunState.CANCELLED, RunState.deriveFrom(states));
        }

        @Test
        @DisplayName("FAILED outranks CANCELLED: the user already knows they cancelled")
        void failureOutranksCancellation() {
            Map<WorkflowStage, StepState> states = allCore(StepState.SUCCEEDED);
            states.put(WorkflowStage.COMET, StepState.FAILED);
            states.put(WorkflowStage.PERCOLATOR, StepState.CANCELLED);

            assertEquals(RunState.FAILED, RunState.deriveFrom(states));
        }

        @Test
        @DisplayName("a failed core stage fails the run even with the rest not started")
        void aFailedCoreStageFailsAnUnfinishedRun() {
            Map<WorkflowStage, StepState> states = allCore(StepState.NOT_STARTED);
            states.put(WorkflowStage.VALIDATE, StepState.FAILED);

            assertEquals(RunState.FAILED, RunState.deriveFrom(states));
        }
    }

    @Nested
    @DisplayName("the decision -- a failed optional downstream stage does not fail the run")
    class OptionalDownstreamStages {

        @ParameterizedTest(name = "[{index}] {0} failed")
        @EnumSource(
                value = WorkflowStage.class,
                names = {"PDV", "LIMELIGHT_XML", "LIMELIGHT_UPLOAD"})
        @DisplayName("a failed downstream stage leaves a completed search SUCCEEDED")
        void aFailedDownstreamStageDoesNotFailTheRun(WorkflowStage stage) {
            Map<WorkflowStage, StepState> states = allCore(StepState.SUCCEEDED);
            states.put(stage, StepState.FAILED);

            assertEquals(RunState.SUCCEEDED, RunState.deriveFrom(states));
        }

        @ParameterizedTest(name = "[{index}] {0} cancelled")
        @EnumSource(
                value = WorkflowStage.class,
                names = {"PDV", "LIMELIGHT_XML", "LIMELIGHT_UPLOAD"})
        @DisplayName("a cancelled downstream stage leaves a completed search SUCCEEDED")
        void aCancelledDownstreamStageDoesNotCancelTheRun(WorkflowStage stage) {
            Map<WorkflowStage, StepState> states = allCore(StepState.SUCCEEDED);
            states.put(stage, StepState.CANCELLED);

            assertEquals(RunState.SUCCEEDED, RunState.deriveFrom(states));
        }

        @Test
        @DisplayName("a failed downstream stage does not rescue a failed core stage either")
        void aFailedDownstreamStageChangesNothingAboutAFailedCoreStage() {
            Map<WorkflowStage, StepState> states = allCore(StepState.SUCCEEDED);
            states.put(WorkflowStage.COMET, StepState.FAILED);
            states.put(WorkflowStage.PDV, StepState.FAILED);

            assertEquals(RunState.FAILED, RunState.deriveFrom(states));
        }
    }

    @Nested
    @DisplayName("rules 6 and 7 -- between stages, and finished")
    class ProgressAndSuccessRules {

        @Test
        @DisplayName("core stages part done and part pending, nothing active, is RUNNING")
        void betweenTwoStagesTheRunIsRunning() {
            Map<WorkflowStage, StepState> states = allCore(StepState.NOT_STARTED);
            states.put(WorkflowStage.INPUTS, StepState.SUCCEEDED);
            states.put(WorkflowStage.VALIDATE, StepState.SUCCEEDED);
            states.put(WorkflowStage.COMET, StepState.SUCCEEDED);
            states.put(WorkflowStage.PERCOLATOR, StepState.READY);

            assertEquals(RunState.RUNNING, RunState.deriveFrom(states));
        }

        @Test
        @DisplayName("every core stage SUCCEEDED is SUCCEEDED")
        void everyCoreStageSucceeded() {
            assertEquals(RunState.SUCCEEDED, RunState.deriveFrom(allCore(StepState.SUCCEEDED)));
        }

        @Test
        @DisplayName("every core stage SKIPPED is SUCCEEDED: skipped work is done work")
        void everyCoreStageSkipped() {
            assertEquals(RunState.SUCCEEDED, RunState.deriveFrom(allCore(StepState.SKIPPED)));
        }

        @Test
        @DisplayName("a rerun that skips Comet and reruns Percolator is SUCCEEDED")
        void aRerunThatSkipsCometSucceeds() {
            Map<WorkflowStage, StepState> states = allCore(StepState.SUCCEEDED);
            states.put(WorkflowStage.COMET, StepState.SKIPPED);
            states.put(WorkflowStage.INPUTS, StepState.SKIPPED);

            assertEquals(RunState.SUCCEEDED, RunState.deriveFrom(states));
        }

        @Test
        @DisplayName("a completed search with its downstream stages never started is SUCCEEDED")
        void downstreamStagesNeverStartedDoNotHoldTheRunOpen() {
            Map<WorkflowStage, StepState> states = allStages(StepState.NOT_STARTED);
            for (WorkflowStage stage : WorkflowStage.coreStages()) {
                states.put(stage, StepState.SUCCEEDED);
            }

            assertEquals(RunState.SUCCEEDED, RunState.deriveFrom(states));
        }

        @Test
        @DisplayName("one core stage left NOT_STARTED keeps a nearly finished run RUNNING")
        void oneUnfinishedCoreStageKeepsTheRunRunning() {
            Map<WorkflowStage, StepState> states = allCore(StepState.SUCCEEDED);
            states.put(WorkflowStage.RESULTS, StepState.NOT_STARTED);

            assertEquals(RunState.RUNNING, RunState.deriveFrom(states));
        }
    }

    private static Map<WorkflowStage, StepState> allCore(StepState state) {
        Map<WorkflowStage, StepState> states = new EnumMap<>(WorkflowStage.class);
        for (WorkflowStage stage : WorkflowStage.coreStages()) {
            states.put(stage, state);
        }
        return states;
    }

    private static Map<WorkflowStage, StepState> allStages(StepState state) {
        Map<WorkflowStage, StepState> states = new EnumMap<>(WorkflowStage.class);
        for (WorkflowStage stage : WorkflowStage.values()) {
            states.put(stage, state);
        }
        return states;
    }
}
