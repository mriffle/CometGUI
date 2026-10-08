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

package org.cometgui.ui.viewmodel.percolator;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executor;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.value.ObservableValue;
import org.cometgui.ui.viewmodel.NonNullProperty;
import org.cometgui.ui.viewmodel.params.ActiveRun;
import org.cometgui.ui.viewmodel.params.PercolatorRequest;
import org.cometgui.ui.viewmodel.params.RunNotStartedException;
import org.cometgui.ui.viewmodel.params.RunObserver;
import org.cometgui.workflow.engine.RunResult;
import org.cometgui.workflow.engine.StepTransition;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.Plan;

/**
 * The compatible-version rerun action (design decision P9-11, Phase 09 gate item 6): rerun
 * Percolator from the session's last run with the Percolator half as it is now -- typically after
 * that run used a build without {@code XML_OUTPUT} and Limelight conversion has been switched on --
 * in a new, derived run that reuses the merged PIN and runs no Comet.
 *
 * <p>Whenever the Percolator half changes, and whenever the composition root says a run ended
 * ({@link #refresh()}), the port is asked on the background executor whether such a rerun can be
 * made; the answer -- the workflow's preview of the steps the rerun executes and reuses, or the
 * workflow's own refusal -- is shown in words, and the action is enabled only for a possible rerun.
 * Starting it records the derived run and starts it, off the interface thread; its progress and its
 * outcome are stated as the Run section states a run's. An answer to an older check is dropped, so
 * the action is never enabled on a stale preview.
 */
public final class PercolatorRerunViewModel {

    /** The preview while the check is running. */
    public static final String CHECKING =
            "Checking whether Percolator can be rerun from the last run of this session.";

    /** The action's text when no rerun is offered. */
    public static final String ACTION = "Rerun Percolator";

    /** The outcome before any rerun in this session. */
    public static final String NO_RERUN_YET = "No Percolator rerun has started in this session.";

    private final PercolatorRerunPort port;

    private final ObservableValue<PercolatorRequest> request;

    private final Executor background;

    private final Executor ui;

    private final NonNullProperty<String> preview;

    private final NonNullProperty<String> actionText;

    private final ReadOnlyBooleanWrapper actionEnabled;

    private final ReadOnlyBooleanWrapper cancelEnabled;

    private final ReadOnlyBooleanWrapper running;

    private final NonNullProperty<String> outcome;

    private long generation;

    private Object attempt;

    private ActiveRun active;

    private Plan plan;

    private String description = "";

    /**
     * The rerun action, following the Percolator section's request.
     *
     * @param port the rerun, over the session's last run
     * @param request the Percolator half as the section has it, re-checked whenever it changes
     * @param background where the port is called: never the interface thread
     * @param ui where every answer is applied: the interface thread
     */
    public PercolatorRerunViewModel(
            PercolatorRerunPort port,
            ObservableValue<PercolatorRequest> request,
            Executor background,
            Executor ui) {
        this.port = Objects.requireNonNull(port, "port");
        this.request = Objects.requireNonNull(request, "request");
        this.background = Objects.requireNonNull(background, "background");
        this.ui = Objects.requireNonNull(ui, "ui");
        preview = new NonNullProperty<>(this, "preview", CHECKING);
        actionText = new NonNullProperty<>(this, "actionText", ACTION);
        actionEnabled = new ReadOnlyBooleanWrapper(this, "actionEnabled", false);
        cancelEnabled = new ReadOnlyBooleanWrapper(this, "cancelEnabled", false);
        running = new ReadOnlyBooleanWrapper(this, "running", false);
        outcome = new NonNullProperty<>(this, "outcome", NO_RERUN_YET);
        request.addListener((observable, before, after) -> refresh());
    }

    /**
     * Asks the port again, on the background executor, whether a rerun can be made. Called when the
     * request changes and by the composition root when a run ends; nothing is asked while the
     * request is {@link PercolatorRequest#pending()}. While a rerun is starting or running nothing
     * is asked: the check after it ends sees the session as it is then.
     */
    public void refresh() {
        generation++;
        if (running.get()) {
            return;
        }
        long mine = generation;
        PercolatorRequest asked = Objects.requireNonNull(request.getValue(), "request");
        preview.set(CHECKING);
        actionText.set(ACTION);
        actionEnabled.set(false);
        if (asked.pending()) {
            return;
        }
        background.execute(
                () -> {
                    RerunCheck answer;
                    try {
                        answer = port.check(asked);
                    } catch (RuntimeException failed) {
                        answer =
                                RerunCheck.refused(
                                        "Whether Percolator can be rerun could not be checked: "
                                                + failed);
                    }
                    RerunCheck checked = answer;
                    ui.execute(() -> applyCheck(mine, asked, checked));
                });
    }

    /**
     * Starts the rerun the preview describes. Returns at once; the port is called on the background
     * executor.
     *
     * @return {@code true} if a rerun is being started; {@code false} if none is offered or one is
     *     already running
     */
    public boolean start() {
        if (running.get() || !actionEnabled.get()) {
            return false;
        }
        generation++;
        Object mine = new Object();
        attempt = mine;
        active = null;
        plan = null;
        description = "";
        running.set(true);
        actionEnabled.set(false);
        outcome.set(
                "Starting the Percolator rerun: recording the new run and copying the merged PIN"
                        + " into it.");
        PercolatorRequest asked = Objects.requireNonNull(request.getValue(), "request");
        RunObserver observer = new Marshalled(this, mine);
        background.execute(
                () -> {
                    try {
                        ActiveRun started = port.start(asked, observer);
                        ui.execute(() -> guarded(() -> started(mine, started)));
                    } catch (RunNotStartedException refused) {
                        ui.execute(
                                () ->
                                        ended(
                                                mine,
                                                "The Percolator rerun did not start: "
                                                        + refused.getMessage()));
                    } catch (RuntimeException failed) {
                        ui.execute(
                                () -> ended(mine, "The Percolator rerun did not start: " + failed));
                    }
                });
        return true;
    }

    /**
     * Asks the running rerun to stop. Returns at once; the request reaches the engine on the
     * background executor.
     *
     * @return {@code true} if the request was sent
     */
    public boolean cancel() {
        ActiveRun target = active;
        if (!cancelEnabled.get() || target == null) {
            return false;
        }
        cancelEnabled.set(false);
        outcome.set("Cancelling the Percolator rerun: " + description + ".");
        background.execute(
                () -> {
                    try {
                        target.cancel();
                    } catch (RuntimeException failed) {
                        ui.execute(
                                () ->
                                        outcome.set(
                                                "The cancellation of the Percolator rerun could"
                                                        + " not be sent: "
                                                        + failed));
                    }
                });
        return true;
    }

    /**
     * The preview of the rerun, or why there is none.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> previewProperty() {
        return preview.getReadOnlyProperty();
    }

    /**
     * The action's text: which build the rerun uses.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> actionTextProperty() {
        return actionText.getReadOnlyProperty();
    }

    /**
     * Whether the action is enabled: a rerun is possible and none is running.
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty actionEnabledProperty() {
        return actionEnabled.getReadOnlyProperty();
    }

    /**
     * Whether a started rerun can still be cancelled.
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty cancelEnabledProperty() {
        return cancelEnabled.getReadOnlyProperty();
    }

    /**
     * Whether a rerun is starting or running.
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty runningProperty() {
        return running.getReadOnlyProperty();
    }

    /**
     * What the rerun is doing, or how the last one ended.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> outcomeProperty() {
        return outcome.getReadOnlyProperty();
    }

    private void applyCheck(long checked, PercolatorRequest asked, RerunCheck answer) {
        if (checked != generation || running.get()) {
            return;
        }
        if (!answer.possible()) {
            preview.set(answer.refusal().orElseThrow());
            return;
        }
        String build =
                asked.build().map(PercolatorViewModel::name).orElse("the selected Percolator");
        preview.set(
                "Rerun "
                        + build
                        + " from run "
                        + answer.sourceRun().orElseThrow()
                        + ", in a new run:\n"
                        + String.join("\n", answer.preview()));
        actionText.set("Rerun " + build);
        actionEnabled.set(true);
    }

    private void started(Object mine, ActiveRun started) {
        if (attempt != mine) {
            return;
        }
        active = started;
        cancelEnabled.set(true);
        outcome.set(
                description.isEmpty()
                        ? "Rerunning Percolator."
                        : "Rerunning Percolator: " + description + ".");
    }

    private void planned(Object mine, Plan planned, String described) {
        if (attempt != mine) {
            return;
        }
        plan = planned;
        description = described;
        outcome.set("Rerunning Percolator: " + described + ".");
    }

    private void transition(Object mine, StepTransition change) {
        if (attempt != mine) {
            return;
        }
        outcome.set(
                "Rerunning Percolator: "
                        + description
                        + ". "
                        + change.step().id()
                        + ": "
                        + change.to().name().toLowerCase(Locale.ROOT).replace('_', ' ')
                        + ".");
    }

    private void finished(Object mine, RunResult result) {
        if (attempt != mine) {
            return;
        }
        ended(mine, outcomeOf(result));
    }

    private void ended(Object mine, String words) {
        if (attempt != mine) {
            return;
        }
        attempt = null;
        active = null;
        running.set(false);
        cancelEnabled.set(false);
        outcome.set(words);
        refresh();
    }

    private String outcomeOf(RunResult result) {
        StringBuilder text = new StringBuilder();
        switch (result.outcome()) {
            case SUCCEEDED ->
                    text.append("The Percolator rerun succeeded: ").append(description).append('.');
            case CANCELLED ->
                    text.append("The Percolator rerun was cancelled: ")
                            .append(description)
                            .append(". Its logs and provenance are kept.");
            case FAILED -> text.append(failureOf(result));
            case RUNNING ->
                    text.append("The Percolator rerun ended without an outcome: ")
                            .append(description)
                            .append('.');
        }
        if (!result.finalisationErrors().isEmpty()) {
            text.append(" Its provenance could not be finalised: ")
                    .append(String.join("; ", result.finalisationErrors()));
        }
        return text.toString();
    }

    private String failureOf(RunResult result) {
        for (EngineStep step : plan == null ? List.<EngineStep>of() : plan.steps()) {
            String failure = result.failures().get(step);
            if (failure != null) {
                return "The Percolator rerun failed at step "
                        + step.id()
                        + " ("
                        + step.displayName()
                        + "): "
                        + failure
                        + ". The new run is "
                        + description
                        + '.';
            }
        }
        return "The Percolator rerun failed: " + description + '.';
    }

    /** Runs a task on the interface thread, stating any failure in the outcome. */
    private void guarded(Runnable task) {
        try {
            task.run();
        } catch (RuntimeException failed) {
            outcome.set("The Percolator rerun's progress could not be shown: " + failed);
        }
    }

    /** The observer handed to the engine: every call is applied on the interface executor. */
    private static final class Marshalled implements RunObserver {

        private final PercolatorRerunViewModel owner;

        private final Object attempt;

        Marshalled(PercolatorRerunViewModel owner, Object attempt) {
            this.owner = owner;
            this.attempt = attempt;
        }

        @Override
        public void planned(Plan plan, String description) {
            owner.ui.execute(() -> owner.guarded(() -> owner.planned(attempt, plan, description)));
        }

        @Override
        public void onTransition(StepTransition transition) {
            owner.ui.execute(() -> owner.guarded(() -> owner.transition(attempt, transition)));
        }

        @Override
        public void onRunFinished(RunResult result) {
            owner.ui.execute(() -> owner.guarded(() -> owner.finished(attempt, result)));
        }
    }
}
