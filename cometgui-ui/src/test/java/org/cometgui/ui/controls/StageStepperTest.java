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

package org.cometgui.ui.controls;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import org.cometgui.ui.testing.FxToolkit;
import org.cometgui.ui.viewmodel.StageStepperViewModel;
import org.cometgui.workflow.state.StepState;
import org.cometgui.workflow.state.WorkflowStage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The stage stepper: the diagram it draws, and the rule that every state is legible as text.
 *
 * <p>The specification's <em>Accessibility</em> principle forbids conveying state by colour alone,
 * so the assertions below are on rendered strings. Asserting a style class instead would prove that
 * a colour was chosen, which is precisely what the principle says is not enough.
 */
class StageStepperTest {

    /** The word each state is rendered as. Written out, so the mapping is pinned, not derived. */
    private static final Map<StepState, String> WORDS = words();

    private StageStepperViewModel viewModel;

    private Scene scene;

    @BeforeAll
    static void startToolkit() throws InterruptedException {
        FxToolkit.start();
    }

    @BeforeEach
    void buildStepper() throws InterruptedException {
        viewModel = new StageStepperViewModel();
        FxToolkit.onFxThread(
                () -> {
                    scene = new Scene(new StageStepper(viewModel), 900, 300);
                    scene.getRoot().applyCss();
                    scene.getRoot().layout();
                });
    }

    @Test
    @DisplayName("the core row is Inputs, Validate, Comet, Percolator, Results with arrows between")
    void theCoreRowIsTheSpecificationsCorePath() {
        Parent core = (Parent) scene.lookup("#" + UiIds.STAGE_STEPPER_CORE);
        List<String> ids = childIds(core);
        assertEquals(
                List.of(
                        "stage-inputs",
                        "stage-arrow-inputs-validate",
                        "stage-validate",
                        "stage-arrow-validate-comet",
                        "stage-comet",
                        "stage-arrow-comet-percolator",
                        "stage-percolator",
                        "stage-arrow-percolator-results",
                        "stage-results"),
                ids,
                "five stages and the four arrows the diagram draws between them");
    }

    @Test
    @DisplayName("the two optional branches hang off Results the way the diagram attaches them")
    void theBranchesHangOffResults() {
        Parent branches = (Parent) scene.lookup("#" + UiIds.STAGE_STEPPER_BRANCHES);
        assertEquals(List.of("stage-branch-pdv", "stage-branch-limelight-xml"), childIds(branches));

        assertEquals(
                List.of("stage-branch-pdv-from", "stage-arrow-results-pdv", "stage-pdv"),
                childIds((Parent) scene.lookup("#stage-branch-pdv")));
        assertEquals(
                List.of(
                        "stage-branch-limelight-xml-from",
                        "stage-arrow-results-limelight-xml",
                        "stage-limelight-xml",
                        "stage-arrow-limelight-xml-limelight-upload",
                        "stage-limelight-upload"),
                childIds((Parent) scene.lookup("#stage-branch-limelight-xml")));

        assertEquals(
                "Results",
                ((Label) scene.lookup("#stage-branch-pdv-from")).getText(),
                "each branch says which stage it hangs off");
    }

    @Test
    @DisplayName("every stage names itself and starts Not started")
    void everyStageNamesItselfAndStartsNotStarted() {
        for (WorkflowStage stage : WorkflowStage.values()) {
            Label name = (Label) scene.lookup("#" + UiIds.stepperStageName(stage));
            Label state = (Label) scene.lookup("#" + UiIds.stepperStageState(stage));
            assertAll(
                    stage.id(),
                    () -> assertNotNull(name, "no name label for " + stage.id()),
                    () -> assertEquals(stage.displayName(), name.getText()),
                    () -> assertEquals("Not started", state.getText()),
                    () ->
                            assertEquals(
                                    stage.displayName() + " stage: Not started",
                                    state.getAccessibleText()));
        }
    }

    @Test
    @DisplayName("all nine step states render as text, and as accessible text")
    void allNineStepStatesRenderAsText() throws InterruptedException {
        Label state = (Label) scene.lookup("#" + UiIds.stepperStageState(WorkflowStage.COMET));
        for (StepState step : StepState.values()) {
            FxToolkit.onFxThread(() -> viewModel.setState(WorkflowStage.COMET, step));
            assertEquals(WORDS.get(step), state.getText(), "the rendering of " + step);
            assertEquals("Comet stage: " + WORDS.get(step), state.getAccessibleText());
        }
        assertEquals(9, WORDS.size(), "the specification's nine explicit step states");
    }

    @Test
    @DisplayName("the derived run state is stated in words below the diagram")
    void theDerivedRunStateIsStatedInWords() throws InterruptedException {
        Label runState = (Label) scene.lookup("#" + UiIds.STAGE_STEPPER_RUN_STATE);
        assertEquals("Run state: Not started", runState.getText());
        assertEquals("run state: Not started", runState.getAccessibleText());

        FxToolkit.onFxThread(() -> viewModel.setState(WorkflowStage.COMET, StepState.RUNNING));
        assertEquals("Run state: Running", runState.getText());

        FxToolkit.onFxThread(
                () -> {
                    for (WorkflowStage stage : WorkflowStage.coreStages()) {
                        viewModel.setState(stage, StepState.SUCCEEDED);
                    }
                });
        assertEquals("Run state: Succeeded", runState.getText());

        FxToolkit.onFxThread(() -> viewModel.setState(WorkflowStage.PERCOLATOR, StepState.FAILED));
        assertEquals("Run state: Failed", runState.getText());
    }

    @Test
    @DisplayName("the arrows carry an accessible name, since their text is a glyph")
    void theArrowsCarryAnAccessibleName() {
        Label arrow = (Label) scene.lookup("#stage-arrow-comet-percolator");
        assertEquals("\u2192", arrow.getText());
        assertEquals("then", arrow.getAccessibleText());
    }

    private static List<String> childIds(Parent parent) {
        List<String> ids = new ArrayList<>();
        for (Node child : parent.getChildrenUnmodifiable()) {
            ids.add(child.getId());
        }
        return ids;
    }

    private static Map<StepState, String> words() {
        Map<StepState, String> words = new EnumMap<>(StepState.class);
        words.put(StepState.NOT_STARTED, "Not started");
        words.put(StepState.VALIDATING, "Validating");
        words.put(StepState.READY, "Ready");
        words.put(StepState.RUNNING, "Running");
        words.put(StepState.SUCCEEDED, "Succeeded");
        words.put(StepState.FAILED, "Failed");
        words.put(StepState.CANCEL_REQUESTED, "Cancel requested");
        words.put(StepState.CANCELLED, "Cancelled");
        words.put(StepState.SKIPPED, "Skipped");
        return Map.copyOf(words);
    }
}
