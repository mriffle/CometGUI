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

package org.cometgui.ui.viewmodel.params;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.validation.Finding;
import org.cometgui.ui.viewmodel.NonNullProperty;
import org.cometgui.ui.viewmodel.StageStepperViewModel;
import org.cometgui.workflow.engine.RunResult;
import org.cometgui.workflow.engine.StepTransition;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.InputKind;
import org.cometgui.workflow.state.Plan;
import org.cometgui.workflow.state.RerunDecision;
import org.cometgui.workflow.state.RerunReason;
import org.cometgui.workflow.state.StageProjection;
import org.cometgui.workflow.state.StepState;
import org.cometgui.workflow.state.StepVerdict;
import org.cometgui.workflow.state.WorkflowStage;
import org.cometgui.workflow.steps.PreRunReport;

/**
 * The Run section's engine half: the pre-run check, Run, Cancel, the stage stepper following the
 * run, the outcome in words and the rerun preview (decision P8-16, {@code R-RUN-01}).
 *
 * <h2>Two threads, and which does what</h2>
 *
 * <p>Everything the engine does reads files or starts processes, so none of it happens on the
 * thread that calls this class (the JavaFX application thread, in the application). The {@link
 * RunEnginePort} is called only from the {@code background} executor; every answer -- the check's
 * result, the run's plan, each step transition from an engine thread, the outcome -- is handed to
 * the {@code ui} executor and applied there. This class never touches the toolkit itself: the
 * composition root passes {@code Platform::runLater} as {@code ui}, a test passes an executor it
 * drains.
 *
 * <h2>Never enabled on a stale answer</h2>
 *
 * <p>Every change of the parameters or of the spectrum files starts a new check, and until it
 * answers the engine's half of {@link RunReadinessViewModel} says the check is running ({@link
 * #CHECKING}), so Run is disabled. Each check carries a generation number; an answer to an older
 * generation is dropped, so a slow check of an earlier configuration can never enable Run for the
 * current one. While a run is starting or running the engine's half says so ({@link #RUN_ACTIVE}).
 *
 * <h2>Nothing is swallowed</h2>
 *
 * <p>JavaFX swallows an exception thrown in a listener or a {@code runLater} task (Phase 07,
 * <em>Surprises</em>). So every task this class runs catches what it did not expect and states it
 * in words: a check that failed is an engine reason, a run that could not start or whose progress
 * could not be shown is the outcome.
 */
public final class RunViewModel {

    /** The engine's reason while the pre-run check is running. */
    public static final String CHECKING =
            "The pre-run check is running: the selected Comet, the spectrum files and the"
                    + " database are being checked. Run is available when it has finished.";

    /** The engine's reason while a run is starting or running. */
    public static final String RUN_ACTIVE =
            "A run is in progress. Run is available again when it has ended.";

    /** The outcome before any run in this session. */
    public static final String NO_RUN_YET = "No run has started in this session.";

    /** The rerun preview while the check is running. */
    public static final String PREVIEW_CHECKING =
            "The rerun preview is shown when the pre-run check has finished.";

    /** The rerun preview before any run in this session. */
    public static final String PREVIEW_FIRST_RUN =
            "No earlier run in this session: Run starts a new run, and every step executes.";

    /** The rerun preview while something blocks the run. */
    public static final String PREVIEW_BLOCKED =
            "No rerun preview: the run cannot start until the reasons above are resolved.";

    /** The rerun preview while a run is starting or running. */
    public static final String PREVIEW_RUNNING =
            "The rerun preview is shown again when the current run has ended.";

    private final ParameterSession session;

    private final SpectrumInputsViewModel inputs;

    private final RunReadinessViewModel readiness;

    private final StageStepperViewModel stepper;

    private final RunEnginePort port;

    private final Executor background;

    private final Executor ui;

    private final ReadOnlyBooleanWrapper running;

    private final ReadOnlyBooleanWrapper cancelEnabled;

    private final NonNullProperty<String> outcome;

    private final NonNullProperty<String> preview;

    /** The generation of the newest check; an answer to an older one is dropped. */
    private long generation;

    /** The run being started or run, identified by object; {@code null} when none is. */
    private Object attempt;

    /** The running attempt, once the port has returned it; {@code null} otherwise. */
    private ActiveRun active;

    /** The planned steps' states, in plan order; {@code null} before a run is planned. */
    private Plan plan;

    private Map<EngineStep, StepState> stepStates;

    private String description = "";

    /**
     * The Run section's engine half.
     *
     * @param session the parameters being edited
     * @param inputs the spectrum files over the same session
     * @param readiness the one run readiness, whose engine half this class keeps
     * @param stepper the stage stepper the run drives
     * @param port the workflow engine
     * @param background where the engine is called: never the interface thread
     * @param ui where every answer is applied: the interface thread
     */
    public RunViewModel(
            ParameterSession session,
            SpectrumInputsViewModel inputs,
            RunReadinessViewModel readiness,
            StageStepperViewModel stepper,
            RunEnginePort port,
            Executor background,
            Executor ui) {
        this.session = Objects.requireNonNull(session, "session");
        this.inputs = Objects.requireNonNull(inputs, "inputs");
        this.readiness = Objects.requireNonNull(readiness, "readiness");
        this.stepper = Objects.requireNonNull(stepper, "stepper");
        this.port = Objects.requireNonNull(port, "port");
        this.background = Objects.requireNonNull(background, "background");
        this.ui = Objects.requireNonNull(ui, "ui");
        this.running = new ReadOnlyBooleanWrapper(this, "running", false);
        this.cancelEnabled = new ReadOnlyBooleanWrapper(this, "cancelEnabled", false);
        this.outcome = new NonNullProperty<>(this, "outcome", NO_RUN_YET);
        this.preview = new NonNullProperty<>(this, "preview", PREVIEW_CHECKING);
        session.modelProperty().addListener((observable, before, after) -> recheck());
        inputs.spectraProperty().addListener((observable, before, after) -> recheck());
    }

    /**
     * Whether a run is starting or running.
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty runningProperty() {
        return running.getReadOnlyProperty();
    }

    /**
     * Whether a run is starting or running.
     *
     * @return {@code true} from Run until the outcome is known
     */
    public boolean running() {
        return running.get();
    }

    /**
     * Whether Cancel is enabled: a run has started and has not been asked to stop.
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty cancelEnabledProperty() {
        return cancelEnabled.getReadOnlyProperty();
    }

    /**
     * Whether Cancel is enabled.
     *
     * @return {@code true} while a started run can still be cancelled
     */
    public boolean cancelEnabled() {
        return cancelEnabled.get();
    }

    /**
     * The last run's outcome, or what the current one is doing, in words.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> outcomeProperty() {
        return outcome.getReadOnlyProperty();
    }

    /**
     * The last run's outcome in words.
     *
     * @return the outcome
     */
    public String outcome() {
        return outcome.get();
    }

    /**
     * The rerun preview, in words: which steps the next Run executes and which it reuses, and why.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> previewProperty() {
        return preview.getReadOnlyProperty();
    }

    /**
     * The rerun preview in words.
     *
     * @return the preview
     */
    public String preview() {
        return preview.get();
    }

    /**
     * Starts a new pre-run check of the configuration as it is now, on the background executor.
     * Until it answers, Run is disabled with {@link #CHECKING}. Called whenever the parameters or
     * the spectrum files change, and by the composition root when the installed tools may have.
     * While a run is in progress, nothing is checked: the check after the run ends sees the
     * configuration as it is then.
     */
    public void recheck() {
        generation++;
        if (running.get()) {
            return;
        }
        long mine = generation;
        readiness.showEngineReasons(List.of(CHECKING));
        preview.set(PREVIEW_CHECKING);
        CometParameters model = session.model();
        List<Path> spectra = inputs.spectrumPaths();
        background.execute(
                () -> {
                    EngineCheck answer;
                    try {
                        answer = port.check(model, spectra);
                    } catch (RuntimeException failed) {
                        answer =
                                EngineCheck.unavailable(
                                        "The pre-run check failed, so the run cannot be judged: "
                                                + failed);
                    }
                    EngineCheck checked = answer;
                    ui.execute(() -> applyCheck(mine, checked));
                });
    }

    /**
     * Starts a run of the configuration as it is now, if the readiness allows it. Returns at once;
     * the engine is called on the background executor.
     *
     * @return {@code true} if a run is being started; {@code false} if Run is not enabled or a run
     *     is already in progress
     */
    public boolean start() {
        if (running.get() || !readiness.runEnabled()) {
            return false;
        }
        generation++;
        Object mine = new Object();
        attempt = mine;
        active = null;
        running.set(true);
        cancelEnabled.set(false);
        readiness.showEngineReasons(List.of(RUN_ACTIVE));
        preview.set(PREVIEW_RUNNING);
        outcome.set("Starting the run: recording it and writing its parameter file.");
        CometParameters model = session.model();
        List<Path> spectra = inputs.spectrumPaths();
        RunObserver observer = new Marshalled(this, mine);
        background.execute(
                () -> {
                    try {
                        ActiveRun started = port.start(model, spectra, observer);
                        ui.execute(() -> guarded(() -> started(mine, started)));
                    } catch (RunNotStartedException refused) {
                        ui.execute(
                                () ->
                                        notStarted(
                                                mine,
                                                "The run did not start: " + refused.getMessage()));
                    } catch (RuntimeException failed) {
                        ui.execute(() -> notStarted(mine, "The run did not start: " + failed));
                    }
                });
        return true;
    }

    /**
     * Asks the running run to stop. Returns at once; the request reaches the engine on the
     * background executor, and the outcome arrives as the run ends.
     *
     * @return {@code true} if the request was sent; {@code false} if no started run can be
     *     cancelled
     */
    public boolean cancel() {
        ActiveRun target = active;
        if (!cancelEnabled.get() || target == null) {
            return false;
        }
        cancelEnabled.set(false);
        outcome.set("Cancelling the run: " + description + ".");
        background.execute(
                () -> {
                    try {
                        target.cancel();
                    } catch (RuntimeException failed) {
                        ui.execute(
                                () -> outcome.set("The cancellation could not be sent: " + failed));
                    }
                });
        return true;
    }

    private void applyCheck(long checked, EngineCheck answer) {
        if (checked != generation || running.get()) {
            return;
        }
        try {
            showCheck(answer);
        } catch (RuntimeException unshown) {
            readiness.showEngineReasons(
                    List.of("The pre-run check's answer could not be shown: " + unshown));
            preview.set(PREVIEW_BLOCKED);
        }
    }

    private void showCheck(EngineCheck answer) {
        List<String> reasons = new ArrayList<>(answer.reasons());
        answer.report().ifPresent(report -> reasons.addAll(reportReasons(report)));
        Optional<RerunOutlook> outlook = answer.outlook();
        if (outlook.isPresent() && nothingToRun(outlook.get())) {
            reasons.add(
                    "Nothing would run: every step of run "
                            + outlook.get().runId()
                            + " succeeded and its recorded results still match. Change a"
                            + " parameter or an input file to search again.");
        }
        readiness.showEngineReasons(reasons);
        if (outlook.isPresent()) {
            preview.set(describe(outlook.get()));
        } else {
            preview.set(reasons.isEmpty() ? PREVIEW_FIRST_RUN : PREVIEW_BLOCKED);
        }
    }

    /**
     * What the pre-run report adds to the parameters' own reasons: each problem with the files,
     * then each validator error the session's report does not already list (an error of the
     * parameters alone is shown once, in the parameters' half).
     */
    private List<String> reportReasons(PreRunReport report) {
        List<String> reasons = new ArrayList<>(report.problems());
        List<Finding> known = session.report().errors();
        for (Finding error : report.validation().errors()) {
            if (!known.contains(error)) {
                reasons.add(error.message());
            }
        }
        return reasons;
    }

    private static boolean nothingToRun(RerunOutlook outlook) {
        return outlook.retry()
                && outlook.refusal().isEmpty()
                && outlook.preview().executed().isEmpty();
    }

    /** The rerun preview in words. */
    static String describe(RerunOutlook outlook) {
        StringBuilder text =
                new StringBuilder("Rerun preview against run ")
                        .append(outlook.runId())
                        .append(":\n");
        if (outlook.retry()) {
            text.append("nothing the steps read has changed, so Run retries run ")
                    .append(outlook.runId())
                    .append(" and runs exactly the steps marked below.");
        } else {
            List<String> changed = new ArrayList<>();
            for (InputKind kind : outlook.changed()) {
                changed.add(kind.id());
            }
            text.append(String.join(", ", changed))
                    .append(" changed, so Run starts a new run -- a run's configuration never")
                    .append(" changes once it starts -- and every step executes in it.");
        }
        for (StepVerdict verdict : outlook.preview().verdicts()) {
            text.append("\n- ").append(verdict.step().id()).append(": ");
            if (verdict.executes()) {
                text.append(
                                verdict.decision() == RerunDecision.RE_EXECUTE
                                        ? "re-executes"
                                        : "runs again, as a prerequisite")
                        .append(" (")
                        .append(reasonsOf(verdict))
                        .append(')');
            } else if (outlook.retry()) {
                text.append(
                        verdict.decision() == RerunDecision.REUSE
                                ? "reused from run " + outlook.runId()
                                : "not needed");
            } else {
                text.append("executes in the new run (unchanged since run ")
                        .append(outlook.runId())
                        .append(", but a new run records its own results)");
            }
        }
        outlook.refusal()
                .ifPresent(
                        refusal ->
                                text.append("\nRun ")
                                        .append(outlook.runId())
                                        .append("'s ")
                                        .append(refusal));
        return text.toString();
    }

    private static String reasonsOf(StepVerdict verdict) {
        List<String> words = new ArrayList<>();
        for (RerunReason reason : verdict.reasons()) {
            words.add(reason.describe());
        }
        return String.join("; ", words);
    }

    private void started(Object mine, ActiveRun started) {
        if (attempt != mine) {
            return;
        }
        active = started;
        cancelEnabled.set(true);
        outcome.set(description.isEmpty() ? "Running." : "Running: " + description + ".");
    }

    private void notStarted(Object mine, String why) {
        if (attempt != mine) {
            return;
        }
        endAttempt();
        outcome.set(why);
        recheck();
    }

    private void planned(Object mine, Plan planned, String described) {
        if (attempt != mine) {
            return;
        }
        plan = planned;
        description = described;
        stepStates = new EnumMap<>(EngineStep.class);
        for (EngineStep step : planned.steps()) {
            stepStates.put(step, StepState.NOT_STARTED);
        }
        for (WorkflowStage stage : WorkflowStage.values()) {
            stepper.setState(stage, StepState.NOT_STARTED);
        }
        project();
        outcome.set("Starting: " + described + ".");
    }

    private void transition(Object mine, StepTransition change) {
        if (attempt != mine || stepStates == null) {
            return;
        }
        stepStates.put(change.step(), change.to());
        project();
    }

    private void finished(Object mine, RunResult result) {
        if (attempt != mine) {
            return;
        }
        String words = outcomeOf(result);
        try {
            if (plan != null) {
                stepStates = new EnumMap<>(result.states());
                project();
            }
        } catch (RuntimeException unshown) {
            words += " The stage stepper could not show the run's final states: " + unshown;
        }
        endAttempt();
        outcome.set(words);
        recheck();
    }

    private void project() {
        for (Map.Entry<WorkflowStage, StepState> stage :
                StageProjection.project(plan, stepStates).entrySet()) {
            stepper.setState(stage.getKey(), stage.getValue());
        }
    }

    private void endAttempt() {
        attempt = null;
        active = null;
        running.set(false);
        cancelEnabled.set(false);
    }

    private String outcomeOf(RunResult result) {
        StringBuilder text = new StringBuilder();
        switch (result.outcome()) {
            case SUCCEEDED -> text.append("The run succeeded: ").append(description).append('.');
            case CANCELLED ->
                    text.append("The run was cancelled: ")
                            .append(description)
                            .append(". Its logs and provenance are kept, and the outputs it")
                            .append(" left are recorded as partial.");
            case FAILED -> text.append(failureOf(result));
            case RUNNING ->
                    text.append("The run ended without an outcome: ")
                            .append(description)
                            .append('.');
        }
        if (!result.finalisationErrors().isEmpty()) {
            text.append(" Its provenance could not be finalised: ")
                    .append(String.join("; ", result.finalisationErrors()));
        }
        if (result.listenerFailures() > 0) {
            text.append(" (")
                    .append(result.listenerFailures())
                    .append(" progress updates could not be delivered to this window.)");
        }
        return text.toString();
    }

    private String failureOf(RunResult result) {
        for (EngineStep step : plan == null ? List.<EngineStep>of() : plan.steps()) {
            String failure = result.failures().get(step);
            if (failure != null) {
                return "The run failed at step "
                        + step.id()
                        + " ("
                        + step.displayName()
                        + "): "
                        + failure
                        + ". "
                        + capitalised(description)
                        + '.';
            }
        }
        return "The run failed: " + description + '.';
    }

    private static String capitalised(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /** Runs a task on the interface thread, stating any failure in the outcome. */
    private void guarded(Runnable task) {
        try {
            task.run();
        } catch (RuntimeException failed) {
            outcome.set("The run's progress could not be shown: " + failed);
        }
    }

    /**
     * The observer handed to the engine: every call, from whichever thread, is handed to the
     * interface executor and applied there, for the attempt it was made for.
     */
    private static final class Marshalled implements RunObserver {

        private final RunViewModel owner;

        private final Object attempt;

        Marshalled(RunViewModel owner, Object attempt) {
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
