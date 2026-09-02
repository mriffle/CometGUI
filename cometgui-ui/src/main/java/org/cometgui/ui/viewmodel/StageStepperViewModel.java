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

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import org.cometgui.workflow.state.RunState;
import org.cometgui.workflow.state.StepState;
import org.cometgui.workflow.state.WorkflowStage;

/**
 * The model behind the Run screen's stage stepper: one state per stage, and the run state derived
 * from them.
 *
 * <p>The specification's information architecture draws the stepper as
 *
 * <pre>
 *     Inputs -&gt; Validate -&gt; Comet -&gt; Percolator -&gt; Results
 *
 *     Results -&gt; PDV
 *     Results -&gt; Limelight XML -&gt; Limelight Upload
 * </pre>
 *
 * <p>and that diagram is written down exactly once, in {@link WorkflowStage}. {@link #coreStages()}
 * and {@link #downstreamBranches()} here read it from there rather than repeating it, so a view
 * cannot draw a stepper that disagrees with the model the engine will drive.
 *
 * <h2>The run state is derived, never assigned</h2>
 *
 * <p>{@link #runStateProperty()} is recomputed by {@link RunState#deriveFrom(Map)} -- the workflow
 * module's derivation, called rather than reimplemented. The specification requires the overall run
 * state to be derived from the step states, and a second implementation here would eventually
 * contradict the one the engine and the provenance record use, which is the failure the
 * specification is guarding against. In particular the decision that a failed <em>optional
 * downstream</em> stage does not fail the run belongs to {@link RunState}, is documented there, and
 * is not repeated or overridden here.
 *
 * <h2>Nothing runs</h2>
 *
 * <p>There is no engine behind this, no process, no timer and no thread. It is a state holder the
 * Run screen renders; phase 08's workflow engine is what will call {@link #setState(WorkflowStage,
 * StepState)}. As with every view-model in this package there is no {@code Platform.runLater}
 * either: a caller on the JavaFX application thread writes it, and the view observes.
 */
public final class StageStepperViewModel {

    private final ObservableMap<WorkflowStage, StepState> states;

    private final ReadOnlyObjectWrapper<RunState> runState;

    /** A stepper with every stage {@link StepState#NOT_STARTED}. */
    public StageStepperViewModel() {
        Map<WorkflowStage, StepState> initial = new EnumMap<>(WorkflowStage.class);
        for (WorkflowStage stage : WorkflowStage.values()) {
            initial.put(stage, StepState.NOT_STARTED);
        }
        states = FXCollections.observableMap(initial);
        runState = new ReadOnlyObjectWrapper<>(this, "runState", RunState.deriveFrom(states));
    }

    /**
     * The core path, in the order the stepper draws it.
     *
     * @return {@link WorkflowStage#coreStages()}: Inputs, Validate, Comet, Percolator, Results
     */
    public List<WorkflowStage> coreStages() {
        return WorkflowStage.coreStages();
    }

    /**
     * The optional branches hanging off Results, each in the order it is drawn.
     *
     * @return {@link WorkflowStage#downstreamBranches()}: {@code [[PDV], [LIMELIGHT_XML,
     *     LIMELIGHT_UPLOAD]]}
     */
    public List<List<WorkflowStage>> downstreamBranches() {
        return WorkflowStage.downstreamBranches();
    }

    /**
     * Every stage the stepper draws, flattened: the core path first, then each branch in order.
     *
     * <p>Convenience for a view that iterates the whole stepper once -- to give every stage node a
     * stable identifier and an accessible name, for instance -- built from {@link #coreStages()}
     * and {@link #downstreamBranches()} so that it cannot list a stage the stepper does not draw.
     *
     * @return an immutable list of all eight stages, core path first
     */
    public List<WorkflowStage> stagesInDrawOrder() {
        List<WorkflowStage> ordered = new ArrayList<>(coreStages());
        for (List<WorkflowStage> branch : downstreamBranches()) {
            ordered.addAll(branch);
        }
        return List.copyOf(ordered);
    }

    /**
     * The state of every stage, as an observable map.
     *
     * <p>Unmodifiable: {@link #setState(WorkflowStage, StepState)} is the only way in, so that the
     * derived run state cannot be left behind by a write that went round it. A view observes this
     * map to repaint one stage without repainting the stepper. The wrapper is built here rather
     * than stored, so that no caller ever holds a reference to the backing map.
     *
     * @return an unmodifiable observable map holding a state for all eight stages
     */
    public ObservableMap<WorkflowStage, StepState> stageStates() {
        return FXCollections.unmodifiableObservableMap(states);
    }

    /**
     * The state of one stage.
     *
     * @param stage the stage to read
     * @return its state, never {@code null}
     * @throws NullPointerException if {@code stage} is {@code null}
     */
    public StepState stateOf(WorkflowStage stage) {
        return states.get(Objects.requireNonNull(stage, "stage"));
    }

    /**
     * Sets the state of one stage and recomputes the derived run state.
     *
     * <p>The recomputation is done here rather than from a map listener registered in the
     * constructor: the map is only reachable through this method, so a listener would add an escape
     * of {@code this} during construction and buy nothing. Observers of {@link #stageStates()} and
     * of {@link #runStateProperty()} are both notified, the map first.
     *
     * @param stage the stage to update
     * @param state its new state
     * @throws NullPointerException if either argument is {@code null}
     */
    public void setState(WorkflowStage stage, StepState state) {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(state, "state");
        states.put(stage, state);
        runState.set(RunState.deriveFrom(states));
    }

    /**
     * The run state derived from the stage states, as an observable value.
     *
     * <p>Read-only by design: there is no setter anywhere, because a run state that could be
     * assigned would eventually contradict the stepper the user is looking at.
     *
     * @return the property, updated by every {@link #setState(WorkflowStage, StepState)}
     */
    public ReadOnlyObjectProperty<RunState> runStateProperty() {
        return runState.getReadOnlyProperty();
    }

    /**
     * The run state derived from the stage states.
     *
     * @return the derived state, never {@code null}
     */
    public RunState runState() {
        return runState.get();
    }
}
