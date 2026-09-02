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

import static org.cometgui.ui.controls.AccessibleControls.named;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.cometgui.ui.viewmodel.StageStepperViewModel;
import org.cometgui.workflow.state.RunState;
import org.cometgui.workflow.state.StepState;
import org.cometgui.workflow.state.WorkflowStage;

/**
 * The Run screen's stage stepper: the specification's workflow diagram, drawn, with every stage's
 * state written out in words.
 *
 * <p>The specification's <em>Information Architecture</em> draws it as
 *
 * <pre>
 *     Inputs -&gt; Validate -&gt; Comet -&gt; Percolator -&gt; Results
 *
 *     Results -&gt; PDV
 *     Results -&gt; Limelight XML -&gt; Limelight Upload
 * </pre>
 *
 * <p>and this control draws exactly that: the five core stages in one row, then each optional
 * downstream branch on a row of its own, led in by the name of the stage it hangs off. Neither the
 * order nor the edges are written here. The rows come from {@link
 * StageStepperViewModel#coreStages()} and {@link StageStepperViewModel#downstreamBranches()}, and
 * every arrow is drawn from {@link WorkflowStage#predecessors()}, so a stepper that disagreed with
 * the model the workflow engine drives would have to be a change to the model.
 *
 * <h2>State is text, never colour alone</h2>
 *
 * <p>The specification's <em>Accessibility</em> principle is explicit: "validation errors must be
 * conveyed in text, not by colour alone". Every stage therefore carries a second label holding its
 * {@link StepState} in words -- "Not started", "Running", "Cancel requested" -- and that label's
 * accessible text is rewritten with it, so a screen reader hears the state rather than the stage
 * name twice. A later phase may add colour; it may not remove the words. The derived {@link
 * RunState} is stated the same way, once, below the diagram.
 *
 * <p>The words come from the constant name -- underscores to spaces, first letter capitalised --
 * rather than from a hand-written table. A table has to be extended when a constant is added and
 * silently renders the wrong thing (or nothing) when it is not; the derivation cannot miss a
 * constant, and {@code StageStepperTest} pins the exact string for all nine states.
 *
 * <h2>Nothing runs here</h2>
 *
 * <p>No engine, no thread, no timer, no animation. The control observes the view-model and rewrites
 * two labels; phase 08's workflow engine is what will move the states.
 */
public final class StageStepper extends VBox {

    /**
     * The view-model's map of stage states, held in a field on purpose.
     *
     * <p>{@link StageStepperViewModel#stageStates()} builds a fresh unmodifiable wrapper on each
     * call, and that wrapper observes the backing map <em>weakly</em>. A listener registered on a
     * wrapper nobody kept would therefore stop firing at the next garbage collection, and the
     * stepper would quietly stop updating -- intermittently, and only under memory pressure, which
     * is the worst way to find a bug. Keeping the wrapper alive for the control's lifetime is what
     * makes the subscription reliable, and reading through it below is what keeps it honest.
     */
    private final ObservableMap<WorkflowStage, StepState> stageStates;

    private final Map<WorkflowStage, Label> stateLabels = new EnumMap<>(WorkflowStage.class);

    private final Label runStateLabel;

    /**
     * The arrow the diagram draws between two stages, as a Unicode escape to keep this file ASCII.
     */
    private static final String ARROW = "\u2192";

    /**
     * A stepper driven by the given view-model.
     *
     * @param viewModel the stage states and the run state derived from them
     * @throws NullPointerException if {@code viewModel} is {@code null}
     */
    public StageStepper(StageStepperViewModel viewModel) {
        Objects.requireNonNull(viewModel, "viewModel");
        this.stageStates = viewModel.stageStates();
        setId(UiIds.STAGE_STEPPER);
        setSpacing(8);
        setPadding(new Insets(8));

        HBox core = new HBox(8);
        core.setId(UiIds.STAGE_STEPPER_CORE);
        core.setAlignment(Pos.CENTER_LEFT);
        appendChain(core, viewModel.coreStages(), false);

        VBox branches = new VBox(6);
        branches.setId(UiIds.STAGE_STEPPER_BRANCHES);
        for (List<WorkflowStage> branch : viewModel.downstreamBranches()) {
            branches.getChildren().add(branchRow(branch));
        }

        runStateLabel = new Label();
        runStateLabel.setId(UiIds.STAGE_STEPPER_RUN_STATE);
        named(runStateLabel, "run state");
        showRunState(viewModel.runState());

        getChildren().addAll(core, branches, runStateLabel);

        stageStates.addListener(
                (MapChangeListener<WorkflowStage, StepState>) change -> showStage(change.getKey()));
        viewModel.runStateProperty().addListener((property, was, now) -> showRunState(now));
    }

    /**
     * One optional branch, led in by the name of the stage it hangs off.
     *
     * @param branch the branch's stages, in the order they are drawn, never empty
     * @return the row
     */
    private HBox branchRow(List<WorkflowStage> branch) {
        WorkflowStage first = branch.get(0);
        WorkflowStage origin = first.predecessors().get(0);
        HBox row = new HBox(8);
        row.setId(UiIds.stepperBranch(first));
        row.setAlignment(Pos.CENTER_LEFT);
        Label from = new Label(origin.displayName());
        from.setId(UiIds.stepperBranchOrigin(first));
        named(from, "branch from the " + origin.displayName() + " stage");
        row.getChildren().add(from);
        appendChain(row, branch, true);
        return row;
    }

    /**
     * Appends a chain of stages to a row, with an arrow before each one that follows something
     * already drawn.
     *
     * @param row the row to append to
     * @param stages the stages, in draw order
     * @param arrowBeforeFirst whether the first stage needs an arrow, which is true for a branch
     *     (its lead-in label is already in the row) and false for the core path
     */
    private void appendChain(HBox row, List<WorkflowStage> stages, boolean arrowBeforeFirst) {
        for (int i = 0; i < stages.size(); i++) {
            WorkflowStage stage = stages.get(i);
            if (i > 0 || arrowBeforeFirst) {
                row.getChildren().add(arrow(stage.predecessors().get(0), stage));
            }
            row.getChildren().add(stageBox(stage));
        }
    }

    /**
     * The arrow the diagram draws between two stages.
     *
     * @param from the earlier stage
     * @param to the later stage
     * @return the arrow label
     */
    private static Label arrow(WorkflowStage from, WorkflowStage to) {
        Label label = new Label(ARROW);
        label.setId(UiIds.stepperArrow(from, to));
        named(label, "then");
        return label;
    }

    /**
     * One stage: its name, and its state in words underneath.
     *
     * @param stage the stage to draw
     * @return the stage's box, whose state label is registered for updates
     */
    private VBox stageBox(WorkflowStage stage) {
        Label name = new Label(stage.displayName());
        name.setId(UiIds.stepperStageName(stage));
        named(name, stage.displayName() + " stage");

        Label state = new Label();
        state.setId(UiIds.stepperStageState(stage));
        named(state, stage.displayName() + " stage state");
        stateLabels.put(stage, state);

        VBox box = new VBox(2, name, state);
        box.setId(UiIds.stepperStage(stage));
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(4, 8, 4, 8));
        showStage(stage);
        return box;
    }

    /**
     * Rewrites one stage's state label and its accessible text from the view-model.
     *
     * @param stage the stage whose state changed
     */
    private void showStage(WorkflowStage stage) {
        Label label = stateLabels.get(stage);
        if (label == null) {
            return;
        }
        String words = inWords(stageStates.get(stage).name());
        label.setText(words);
        label.setAccessibleText(stage.displayName() + " stage: " + words);
    }

    /**
     * Rewrites the derived run state, in words.
     *
     * @param runState the run state the view-model derived
     */
    private void showRunState(RunState runState) {
        String words = inWords(runState.name());
        runStateLabel.setText("Run state: " + words);
        runStateLabel.setAccessibleText("run state: " + words);
    }

    /**
     * A constant name as a phrase: {@code CANCEL_REQUESTED} becomes {@code Cancel requested}.
     *
     * @param constantName an enum constant name, upper case with underscores
     * @return the phrase, never blank
     */
    private static String inWords(String constantName) {
        String lower = constantName.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
