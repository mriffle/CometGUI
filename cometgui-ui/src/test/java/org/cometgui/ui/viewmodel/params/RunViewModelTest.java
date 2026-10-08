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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javafx.beans.property.SimpleObjectProperty;
import org.cometgui.domain.params.PreRunFacts;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.run.RunId;
import org.cometgui.params.comet.validation.Finding;
import org.cometgui.params.comet.validation.Rule;
import org.cometgui.params.comet.validation.Severity;
import org.cometgui.params.comet.validation.ValidationReport;
import org.cometgui.provenance.manifest.ApplicationRecord;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ProvenanceStatus;
import org.cometgui.provenance.manifest.RunRecord;
import org.cometgui.ui.testing.Editors;
import org.cometgui.ui.testing.Editors.KnownFiles;
import org.cometgui.ui.testing.Editors.ScriptedChooser;
import org.cometgui.ui.testing.Percolators;
import org.cometgui.ui.testing.ScriptedEngine;
import org.cometgui.ui.viewmodel.StageStepperViewModel;
import org.cometgui.workflow.engine.RunResult;
import org.cometgui.workflow.engine.StepTransition;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.Fingerprints;
import org.cometgui.workflow.state.InputKind;
import org.cometgui.workflow.state.InputValue;
import org.cometgui.workflow.state.Plan;
import org.cometgui.workflow.state.RerunPreview;
import org.cometgui.workflow.state.RunState;
import org.cometgui.workflow.state.StepFingerprint;
import org.cometgui.workflow.state.StepInputs;
import org.cometgui.workflow.state.StepState;
import org.cometgui.workflow.state.WorkflowStage;
import org.cometgui.workflow.steps.CometWorkflow;
import org.cometgui.workflow.steps.PreRunReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The Run section's engine half, with a scripted engine and two executors the test drains by hand:
 * where each call happens, that an old answer never enables Run, the stepper following the run, the
 * outcome in words for every ending, Cancel reaching the engine, and the rerun preview's words.
 * Every expected text is typed out here.
 */
class RunViewModelTest {

    /** The plan of a Phase 08 run with no index mode. */
    private static final Plan PLAN = CometWorkflow.planFor(IndexMode.NONE);

    private static final String DESCRIPTION = "run run-1 in /projects/p/runs/run-1";

    private final ParameterSession session = Editors.session();

    private final ScriptedChooser chooser = new ScriptedChooser();

    private final SpectrumInputsViewModel inputs =
            Editors.inputs(session, chooser, new KnownFiles());

    private final ParameterEditorViewModel editor = Editors.editor(session, inputs, chooser);

    private final StageStepperViewModel stepper = new StageStepperViewModel();

    private final ScriptedEngine engine = new ScriptedEngine(model -> clean());

    private final RunViewModel run = Editors.run(session, inputs, editor, stepper, engine);

    private final RunReadinessViewModel readiness = editor.readiness();

    /** A pre-run report with nothing against the run. */
    private static EngineCheck clean() {
        return EngineCheck.checked(
                new PreRunReport(List.of(), new ValidationReport(List.of()), PreRunFacts.none()),
                Optional.empty());
    }

    /** Checks once, settles both queues, and requires Run to be enabled. */
    private void ready() {
        engine.announce(PLAN, DESCRIPTION);
        run.recheck();
        engine.settle();
        assertTrue(readiness.runEnabled(), readiness::reasonsText);
    }

    @Nested
    @DisplayName("the pre-run check")
    class Check {

        @Test
        @DisplayName(
                "is made on the background executor, applied on the interface one, and Run"
                        + " is disabled with the reason in words until it has been applied")
        void backgroundThenInterface() {
            engine.answer(model -> EngineCheck.unavailable("Comet 2026.03.0 is not installed."));
            run.recheck();
            assertAll(
                    "requested, nothing run yet",
                    () -> assertEquals(0, engine.checks(), "the port is not called on the caller"),
                    () -> assertEquals(List.of(RunViewModel.CHECKING), readiness.engineReasons()),
                    () -> assertFalse(readiness.runEnabled()),
                    () -> assertEquals(RunViewModel.PREVIEW_CHECKING, run.preview()));

            engine.background().drain();
            assertAll(
                    "checked on the background executor, not applied yet",
                    () -> assertEquals(1, engine.checks()),
                    () -> assertEquals(List.of("main:true"), engine.callers()),
                    () -> assertEquals(List.of(RunViewModel.CHECKING), readiness.engineReasons()),
                    () -> assertEquals(1, engine.ui().pending()));

            engine.ui().drain();
            assertAll(
                    "applied",
                    () ->
                            assertEquals(
                                    List.of("Comet 2026.03.0 is not installed."),
                                    readiness.engineReasons()),
                    () -> assertFalse(readiness.runEnabled()),
                    () -> assertEquals(RunViewModel.PREVIEW_BLOCKED, run.preview()));
        }

        @Test
        @DisplayName("an answer to an older configuration is dropped: Run is never enabled on it")
        void staleAnswerDropped() {
            engine.answer(
                    model ->
                            "0.02".equals(model.text("fragment_bin_tol"))
                                    ? clean()
                                    : EngineCheck.unavailable(
                                            "checked fragment_bin_tol = "
                                                    + model.text("fragment_bin_tol")));
            run.recheck();
            engine.background().drain();

            // The configuration changes before the first answer is applied.
            session.edit("fragment_bin_tol", "1.0005");
            engine.ui().drain();
            assertAll(
                    "the clean answer of 0.02 is not applied to 1.0005",
                    () -> assertEquals(List.of(RunViewModel.CHECKING), readiness.engineReasons()),
                    () -> assertFalse(readiness.runEnabled()));

            engine.settle();
            assertEquals(List.of("checked fragment_bin_tol = 1.0005"), readiness.engineReasons());
        }

        @Test
        @DisplayName("choosing spectrum files checks again")
        void spectraCheckAgain() {
            ready();
            assertEquals(RunViewModel.PREVIEW_FIRST_RUN, run.preview());
            int before = engine.checks();
            chooser.spectra(Path.of("run1.mzML").toAbsolutePath());
            inputs.chooseSpectra();
            assertEquals(List.of(RunViewModel.CHECKING), readiness.engineReasons());
            assertEquals(
                    RunViewModel.PREVIEW_CHECKING,
                    run.preview(),
                    "the preview of the earlier answer is withdrawn while checking again");
            engine.settle();
            assertEquals(before + 1, engine.checks());
        }

        @Test
        @DisplayName(
                "the report's problems, then the validator errors the parameters do not"
                        + " already show, are the engine's reasons")
        void reportReasons() {
            session.edit("peptide_mass_tolerance_lower", "30.0");
            List<Finding> parameterErrors = session.report().errors();
            assertEquals(1, parameterErrors.size());
            Finding decoys =
                    new Finding(
                            Rule.DECOY_NONE_ANYWHERE,
                            Severity.ERROR,
                            List.of("decoy_search", "database_name"),
                            Optional.empty(),
                            "decoy_search = 0 (no internal decoys) and /db.fasta holds no decoys");
            engine.answer(
                    model ->
                            EngineCheck.checked(
                                    new PreRunReport(
                                            List.of("there is no spectrum file to search"),
                                            new ValidationReport(
                                                    List.of(parameterErrors.get(0), decoys)),
                                            PreRunFacts.none()),
                                    Optional.empty()));
            run.recheck();
            engine.settle();
            assertEquals(
                    List.of(
                            "there is no spectrum file to search",
                            "decoy_search = 0 (no internal decoys) and /db.fasta holds no decoys"),
                    readiness.engineReasons(),
                    "the precursor window's error is the parameters' reason, shown once");
            assertTrue(readiness.parametersBlockRun());
        }

        @Test
        @DisplayName("a check that throws is stated as the engine's reason, never swallowed")
        void checkThatThrows() {
            engine.answer(
                    model -> {
                        throw new IllegalStateException("the FASTA vanished");
                    });
            run.recheck();
            engine.settle();
            assertEquals(
                    List.of(
                            "The pre-run check failed, so the run cannot be judged:"
                                    + " java.lang.IllegalStateException: the FASTA vanished"),
                    readiness.engineReasons());
            assertFalse(readiness.runEnabled());
        }

        @Test
        @DisplayName(
                "an answer that cannot be shown is stated as the engine's reason, never"
                        + " swallowed")
        void unshowableAnswerStated() {
            engine.answer(
                    model ->
                            EngineCheck.checked(
                                    new PreRunReport(
                                            List.of(" "),
                                            new ValidationReport(List.of()),
                                            PreRunFacts.none()),
                                    Optional.empty()));
            run.recheck();
            engine.settle();
            assertEquals(
                    List.of(
                            "The pre-run check's answer could not be shown:"
                                    + " java.lang.IllegalArgumentException: a workflow engine that"
                                    + " cannot run has to say why: a blank reason leaves the Run"
                                    + " control disabled with no explanation"),
                    readiness.engineReasons());
            assertFalse(readiness.runEnabled());
            assertEquals(RunViewModel.PREVIEW_BLOCKED, run.preview());
        }

        @Test
        @DisplayName(
                "with no earlier run and nothing against it, the preview says a new run runs"
                        + " every step")
        void firstRunPreview() {
            ready();
            assertEquals(
                    "No earlier run in this session: Run starts a new run, and every step"
                            + " executes.",
                    run.preview());
        }
    }

    @Nested
    @DisplayName("Run, Cancel and the stepper")
    class Running {

        @Test
        @DisplayName("Run does nothing while the readiness has a reason")
        void runRefusedWhenNotReady() {
            run.recheck();
            assertFalse(run.start(), "the check has not answered");
            engine.settle();
            session.edit("peptide_mass_tolerance_lower", "30.0");
            engine.settle();
            assertFalse(run.start(), "the parameters block");
            assertEquals(List.of(), engine.started());
        }

        @Test
        @DisplayName(
                "Run starts on the background executor; the stepper follows the transitions;"
                        + " success is stated in words and the check runs again")
        void runSucceeds() {
            ready();
            int checks = engine.checks();
            assertTrue(run.start());
            assertAll(
                    "starting, before the engine has been called",
                    () -> assertTrue(run.running()),
                    () -> assertFalse(run.cancelEnabled(), "nothing to cancel yet"),
                    () -> assertEquals(List.of(RunViewModel.RUN_ACTIVE), readiness.engineReasons()),
                    () -> assertFalse(readiness.runEnabled()),
                    () -> assertEquals(List.of(), engine.started()),
                    () ->
                            assertEquals(
                                    "Starting the run: recording it and writing its parameter"
                                            + " file.",
                                    run.outcome()));
            assertFalse(run.start(), "a second Run while one is starting does nothing");

            engine.background().drain();
            assertEquals(1, engine.started().size());
            assertEquals("main:true", engine.callers().get(engine.callers().size() - 1));
            RunObserver observer = engine.started().get(0).observer();
            engine.ui().drain();
            assertAll(
                    "planned and started",
                    () -> assertTrue(run.cancelEnabled()),
                    () -> assertEquals(StepState.NOT_STARTED, stepper.stateOf(WorkflowStage.COMET)),
                    () ->
                            assertEquals(
                                    "Running: run run-1 in /projects/p/runs/run-1.",
                                    run.outcome()));

            observer.onTransition(transition(EngineStep.RUN_COMET, StepState.RUNNING));
            assertEquals(
                    StepState.NOT_STARTED,
                    stepper.stateOf(WorkflowStage.COMET),
                    "an engine transition is applied on the interface executor only");
            engine.ui().drain();
            assertEquals(StepState.RUNNING, stepper.stateOf(WorkflowStage.COMET));

            observer.onRunFinished(result(AttemptOutcome.SUCCEEDED, allSucceeded(), Map.of()));
            engine.ui().drain();
            assertAll(
                    "succeeded",
                    () -> assertFalse(run.running()),
                    () -> assertFalse(run.cancelEnabled()),
                    () -> assertEquals(StepState.SUCCEEDED, stepper.stateOf(WorkflowStage.COMET)),
                    () -> assertEquals(StepState.SUCCEEDED, stepper.stateOf(WorkflowStage.INPUTS)),
                    () ->
                            assertEquals(
                                    StepState.NOT_STARTED,
                                    stepper.stateOf(WorkflowStage.PERCOLATOR),
                                    "Percolator is not planned in Phase 08"),
                    () ->
                            assertEquals(
                                    "The run succeeded: run run-1 in /projects/p/runs/run-1.",
                                    run.outcome()),
                    () -> assertEquals(List.of(RunViewModel.CHECKING), readiness.engineReasons()));
            engine.settle();
            assertEquals(checks + 1, engine.checks(), "the check after the run");
            assertTrue(readiness.runEnabled());
            assertAll(
                    "the published properties are the values",
                    () -> assertEquals(run.outcome(), run.outcomeProperty().get()),
                    () -> assertEquals(run.preview(), run.previewProperty().get()),
                    () -> assertFalse(run.cancelEnabledProperty().get()));
        }

        @Test
        @DisplayName(
                "the plan arrives first: the outcome says the run is starting, and every stage --"
                        + " planned or not -- starts again from not started")
        void plannedResetsTheStepper() {
            stepper.setState(WorkflowStage.PERCOLATOR, StepState.FAILED);
            stepper.setState(WorkflowStage.COMET, StepState.SUCCEEDED);
            ready();
            run.start();
            engine.background().drain();
            assertEquals(2, engine.ui().pending(), "the plan, then the started run");
            engine.ui().runOne();
            assertAll(
                    "planned, not yet started",
                    () ->
                            assertEquals(
                                    "Starting: run run-1 in /projects/p/runs/run-1.",
                                    run.outcome()),
                    () -> assertFalse(run.cancelEnabled()),
                    () -> assertEquals(StepState.NOT_STARTED, stepper.stateOf(WorkflowStage.COMET)),
                    () ->
                            assertEquals(
                                    StepState.NOT_STARTED,
                                    stepper.stateOf(WorkflowStage.PERCOLATOR),
                                    "a stage this run does not plan is not left showing an"
                                            + " earlier state"));
            engine.ui().runOne();
            assertTrue(run.cancelEnabled());
        }

        @Test
        @DisplayName("a failed run with no failing step named still says it failed, and where")
        void failureWithoutAStep() {
            ready();
            run.start();
            engine.settle();
            Map<EngineStep, StepState> states = allSucceeded();
            states.put(EngineStep.FINALISE_PROVENANCE, StepState.FAILED);
            engine.started()
                    .get(0)
                    .observer()
                    .onRunFinished(result(AttemptOutcome.FAILED, states, Map.of()));
            engine.ui().drain();
            assertEquals("The run failed: run run-1 in /projects/p/runs/run-1.", run.outcome());
        }

        @Test
        @DisplayName("a cancellation the engine could not take is stated in words, not swallowed")
        void cancellationThatThrows() {
            engine.failCancellations(new IllegalStateException("the process service has stopped"));
            ready();
            run.start();
            engine.settle();
            assertTrue(run.cancel());
            engine.settle();
            assertEquals(
                    "The cancellation could not be sent: java.lang.IllegalStateException: the"
                            + " process service has stopped",
                    run.outcome());
        }

        @Test
        @DisplayName(
                "Cancel is enabled only while a started run can be cancelled, and reaches the"
                        + " engine on the background executor")
        void cancelReachesTheEngine() {
            ready();
            assertFalse(run.cancel(), "no run");
            run.start();
            assertFalse(run.cancel(), "the engine has not returned the run yet");
            engine.background().drain();
            RunObserver observer = engine.started().get(0).observer();
            engine.ui().drain();
            assertTrue(run.cancelEnabled());

            assertTrue(run.cancel());
            assertAll(
                    "requested",
                    () -> assertFalse(run.cancelEnabled(), "one request is enough"),
                    () -> assertEquals(0, engine.cancels(), "not on the caller's thread"),
                    () ->
                            assertEquals(
                                    "Cancelling the run: run run-1 in /projects/p/runs/run-1.",
                                    run.outcome()));
            engine.background().drain();
            assertEquals(1, engine.cancels());
            assertEquals("cancel:true", engine.callers().get(engine.callers().size() - 1));
            assertFalse(run.cancel());

            Map<EngineStep, StepState> states = allSucceeded();
            states.put(EngineStep.RUN_COMET, StepState.CANCELLED);
            states.put(EngineStep.VALIDATE_COMET_OUTPUTS, StepState.NOT_STARTED);
            states.put(EngineStep.MERGE_PIN, StepState.NOT_STARTED);
            states.put(EngineStep.FINALISE_PROVENANCE, StepState.NOT_STARTED);
            observer.onRunFinished(result(AttemptOutcome.CANCELLED, states, Map.of()));
            engine.ui().drain();
            assertAll(
                    "cancelled",
                    () -> assertEquals(StepState.CANCELLED, stepper.stateOf(WorkflowStage.COMET)),
                    () ->
                            assertEquals(
                                    "The run was cancelled: run run-1 in /projects/p/runs/run-1."
                                            + " Its logs and provenance are kept, and the outputs"
                                            + " it left are recorded as partial.",
                                    run.outcome()),
                    () -> assertFalse(run.running()));
        }

        @Test
        @DisplayName("a failed run names the step and the engine's message")
        void failureStated() {
            ready();
            run.start();
            engine.background().drain();
            RunObserver observer = engine.started().get(0).observer();
            Map<EngineStep, StepState> states = allSucceeded();
            states.put(EngineStep.RUN_COMET, StepState.FAILED);
            states.put(EngineStep.VALIDATE_COMET_OUTPUTS, StepState.NOT_STARTED);
            states.put(EngineStep.MERGE_PIN, StepState.NOT_STARTED);
            states.put(EngineStep.FINALISE_PROVENANCE, StepState.NOT_STARTED);
            observer.onRunFinished(
                    new RunResult(
                            1,
                            AttemptOutcome.FAILED,
                            RunState.deriveFrom(PLAN, states),
                            states,
                            Map.of(EngineStep.RUN_COMET, "comet-01 exited with status 1"),
                            manifest(),
                            List.of("provenance.rst could not be written"),
                            2));
            engine.ui().drain();
            assertEquals(
                    "The run failed at step run-comet (Run Comet once per spectrum file):"
                            + " comet-01 exited with status 1. Run run-1 in"
                            + " /projects/p/runs/run-1. Its provenance could not be finalised:"
                            + " provenance.rst could not be written (2 progress updates could not"
                            + " be delivered to this window.)",
                    run.outcome());
            assertEquals(StepState.FAILED, stepper.stateOf(WorkflowStage.COMET));
            assertFalse(run.running());
        }

        @Test
        @DisplayName("a run the engine refused is stated in words, and Run is offered again")
        void refusalStated() {
            ready();
            engine.refuseNextStart(
                    new RunNotStartedException(
                            "the run cannot start:\n- there is no spectrum file to search", null));
            run.start();
            engine.settle();
            assertAll(
                    () ->
                            assertEquals(
                                    "The run did not start: the run cannot start:\n- there is no"
                                            + " spectrum file to search",
                                    run.outcome()),
                    () -> assertFalse(run.running()),
                    () -> assertTrue(readiness.runEnabled(), "checked again"));
        }

        @Test
        @DisplayName("a start that throws is stated in words, never swallowed")
        void startThatThrows() {
            ScriptedEngine throwing = new ScriptedEngine(model -> clean());
            RunEnginePort broken =
                    new RunEnginePort() {
                        @Override
                        public EngineCheck check(
                                org.cometgui.params.comet.model.CometParameters model,
                                List<Path> spectra,
                                PercolatorRequest percolator) {
                            return clean();
                        }

                        @Override
                        public ActiveRun start(
                                org.cometgui.params.comet.model.CometParameters model,
                                List<Path> spectra,
                                PercolatorRequest percolator,
                                RunObserver observer) {
                            throw new IllegalStateException("no process service");
                        }
                    };
            RunViewModel other =
                    new RunViewModel(
                            session,
                            inputs,
                            readiness,
                            stepper,
                            new SimpleObjectProperty<>(Percolators.ready()),
                            broken,
                            throwing.background(),
                            throwing.ui());
            other.recheck();
            throwing.settle();
            assertTrue(other.start());
            throwing.settle();
            assertEquals(
                    "The run did not start: java.lang.IllegalStateException: no process service",
                    other.outcome());
            assertFalse(other.running());
        }

        @Test
        @DisplayName("progress that cannot be shown is stated in the outcome, not swallowed")
        void unshowableProgressStated() {
            ready();
            run.start();
            engine.background().drain();
            RunObserver observer = engine.started().get(0).observer();
            observer.onTransition(transition(EngineStep.RUN_PERCOLATOR, StepState.RUNNING));
            engine.ui().drain();
            assertEquals(
                    "The run's progress could not be shown: java.lang.IllegalArgumentException: a"
                            + " step outside the plan has no state, but one was given for:"
                            + " run-percolator",
                    run.outcome());
        }

        @Test
        @DisplayName("while a run is in progress nothing is checked")
        void noCheckWhileRunning() {
            ready();
            run.start();
            engine.settle();
            int checks = engine.checks();
            session.edit("fragment_bin_tol", "1.0005");
            engine.settle();
            assertEquals(checks, engine.checks());
            assertEquals(List.of(RunViewModel.RUN_ACTIVE), readiness.engineReasons());
            assertEquals(RunViewModel.PREVIEW_RUNNING, run.preview());
        }
    }

    @Nested
    @DisplayName("the rerun preview (R-RUN-01)")
    class Preview {

        @Test
        @DisplayName(
                "a retry after a failure at run-comet: exactly the steps that execute, the"
                        + " reused one, and why")
        void retryAfterFailure() {
            StepInputs recorded = inputsWith("params-a");
            RerunPreview preview =
                    RerunPreview.compute(
                            PLAN,
                            recorded,
                            Map.of(
                                    EngineStep.SERIALISE_COMET_PARAMS,
                                    fingerprint(recorded, EngineStep.SERIALISE_COMET_PARAMS)));
            assertEquals(
                    "Rerun preview against run run-1:\n"
                            + "nothing the steps read has changed, so Run retries run run-1 and"
                            + " runs exactly the steps marked below.\n"
                            + "- validate-configuration: runs again, as a prerequisite (needed by"
                            + " resolve-comet; needed by hash-inputs)\n"
                            + "- resolve-comet: runs again, as a prerequisite (needed by"
                            + " run-comet)\n"
                            + "- serialise-comet-params: reused from run run-1\n"
                            + "- hash-inputs: runs again, as a prerequisite (needed by run-comet)\n"
                            + "- run-comet: re-executes (no successful earlier execution is"
                            + " recorded)\n"
                            + "- validate-comet-outputs: re-executes (no successful earlier"
                            + " execution is recorded; run-comet re-executes)\n"
                            + "- merge-pin: re-executes (no successful earlier execution is"
                            + " recorded; validate-comet-outputs re-executes)\n"
                            + "- finalise-provenance: re-executes (no successful earlier execution"
                            + " is recorded; merge-pin re-executes)",
                    shown(new RerunOutlook("run-1", true, preview, Optional.empty(), Set.of())));
            assertTrue(readiness.runEnabled(), "something executes, so Run is offered");
        }

        @Test
        @DisplayName(
                "a changed parameter file: a new run, every step executes, the reasons from"
                        + " the comparison")
        void changedParameters() {
            StepInputs before = inputsWith("params-a");
            RerunPreview preview =
                    RerunPreview.compute(PLAN, inputsWith("params-b"), recordedAll(before));
            assertEquals(
                    "Rerun preview against run run-1:\n"
                            + "comet-parameters changed, so Run starts a new run -- a run's"
                            + " configuration never changes once it starts -- and every step"
                            + " executes in it.\n"
                            + "- validate-configuration: runs again, as a prerequisite (needed by"
                            + " resolve-comet; needed by serialise-comet-params; needed by"
                            + " hash-inputs)\n"
                            + "- resolve-comet: runs again, as a prerequisite (needed by"
                            + " run-comet)\n"
                            + "- serialise-comet-params: re-executes (comet-parameters changed)\n"
                            + "- hash-inputs: runs again, as a prerequisite (needed by run-comet)\n"
                            + "- run-comet: re-executes (comet-parameters changed;"
                            + " serialise-comet-params re-executes)\n"
                            + "- validate-comet-outputs: re-executes (run-comet re-executes)\n"
                            + "- merge-pin: re-executes (validate-comet-outputs re-executes)\n"
                            + "- finalise-provenance: re-executes (merge-pin re-executes)",
                    shown(
                            new RerunOutlook(
                                    "run-1",
                                    false,
                                    preview,
                                    Optional.empty(),
                                    EnumSet.of(InputKind.COMET_PARAMETERS))));
        }

        @Test
        @DisplayName(
                "a changed spectrum file: the unchanged step executes in the new run too, and"
                        + " the last run's refusal is shown in its words")
        void changedSpectrumWithRefusal() {
            StepInputs before = inputsWith("params-a");
            StepInputs after =
                    before.with(
                            InputKind.SPECTRUM_FILES,
                            new InputValue.Files(
                                    List.of(new InputValue.NamedFile("k562_3.mzML", hashes('e')))));
            RerunPreview preview = RerunPreview.compute(PLAN, after, recordedAll(before));
            String refusal =
                    "recorded results cannot be reused because they no longer match the record:\n"
                            + "- /data/k562_3.mzML (input file, role spectrum, of step run-comet)"
                            + " has changed since it was recorded\n"
                            + "these steps must run again: run-comet";
            String text =
                    shown(
                            new RerunOutlook(
                                    "run-1",
                                    false,
                                    preview,
                                    Optional.of(refusal),
                                    EnumSet.of(InputKind.SPECTRUM_FILES)));
            assertTrue(
                    text.contains(
                            "\n- serialise-comet-params: executes in the new run (unchanged since"
                                    + " run run-1, but a new run records its own results)\n"),
                    text);
            assertTrue(
                    text.contains("\n- run-comet: re-executes (spectrum-files changed)\n"), text);
            assertTrue(text.endsWith("\nRun run-1's " + refusal), text);
            assertTrue(
                    text.startsWith(
                            "Rerun preview against run run-1:\nspectrum-files changed, so Run"
                                    + " starts a new run"),
                    text);
        }

        @Test
        @DisplayName(
                "nothing changed after a success: nothing would run, so Run is disabled with"
                        + " that reason")
        void nothingToRun() {
            StepInputs recorded = inputsWith("params-a");
            RerunPreview preview = RerunPreview.compute(PLAN, recorded, recordedAll(recorded));
            String text =
                    shown(new RerunOutlook("run-1", true, preview, Optional.empty(), Set.of()));
            assertTrue(text.contains("\n- run-comet: reused from run run-1\n"), text);
            assertTrue(text.contains("\n- hash-inputs: not needed\n"), text);
            assertEquals(
                    List.of(
                            "Nothing would run: every step of run run-1 succeeded and its recorded"
                                    + " results still match. Change a parameter or an input file"
                                    + " to search again."),
                    readiness.engineReasons());
            assertFalse(readiness.runEnabled());
        }

        @Test
        @DisplayName("a retry whose reuse is refused offers its plan and is not 'nothing to run'")
        void refusedRetryStillRuns() {
            StepInputs recorded = inputsWith("params-a");
            RerunPreview offered =
                    RerunPreview.compute(
                            PLAN, recorded, recordedAll(recorded), Set.of(EngineStep.MERGE_PIN));
            String text =
                    shown(
                            new RerunOutlook(
                                    "run-1",
                                    true,
                                    offered,
                                    Optional.of("recorded results cannot be reused"),
                                    Set.of()));
            assertTrue(text.contains("\n- merge-pin: re-executes (re-execution was requested)"));
            assertTrue(text.endsWith("\nRun run-1's recorded results cannot be reused"), text);
            assertTrue(readiness.runEnabled());
        }

        @Test
        @DisplayName("an outlook whose retry flag disagrees with its changed inputs is refused")
        void outlookConsistency() {
            RerunPreview preview =
                    RerunPreview.compute(PLAN, inputsWith("a"), recordedAll(inputsWith("a")));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new RerunOutlook(
                                    "run-1",
                                    true,
                                    preview,
                                    Optional.empty(),
                                    Set.of(InputKind.FASTA)));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new RerunOutlook("run-1", false, preview, Optional.empty(), Set.of()));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new RerunOutlook(" ", true, preview, Optional.empty(), Set.of()));
        }

        /** Shows an outlook through a check and returns the preview's words. */
        private String shown(RerunOutlook outlook) {
            engine.answer(
                    model ->
                            EngineCheck.checked(
                                    new PreRunReport(
                                            List.of(),
                                            new ValidationReport(List.of()),
                                            PreRunFacts.none()),
                                    Optional.of(outlook)));
            run.recheck();
            engine.settle();
            return run.preview();
        }
    }

    @Test
    @DisplayName("an engine answer that says nothing is refused")
    void emptyAnswerRefused() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EngineCheck(List.of(), Optional.empty(), Optional.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new EngineCheck(List.of(" "), Optional.empty(), Optional.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    throw new RunNotStartedException(" ", null);
                });
    }

    private static StepTransition transition(EngineStep step, StepState to) {
        return new StepTransition(
                step,
                StepState.NOT_STARTED,
                to,
                Instant.parse("2026-10-07T00:00:00Z"),
                RunState.NOT_STARTED);
    }

    private static Map<EngineStep, StepState> allSucceeded() {
        Map<EngineStep, StepState> states = new EnumMap<>(EngineStep.class);
        for (EngineStep step : PLAN.steps()) {
            states.put(step, StepState.SUCCEEDED);
        }
        return states;
    }

    @Nested
    @DisplayName("the Percolator half (Phase 09)")
    class PercolatorHalf {

        private final SimpleObjectProperty<PercolatorRequest> percolator =
                new SimpleObjectProperty<>(Percolators.ready());

        private final RunViewModel withPercolator =
                Editors.run(session, inputs, editor, stepper, percolator, engine);

        @Test
        @DisplayName(
                "its problems are reasons of the engine's half, after the engine's own, and"
                        + " block Run with no preview")
        void problemsAreEngineReasons() {
            engine.answer(model -> EngineCheck.unavailable("Comet 2026.03.0 is not installed."));
            percolator.set(
                    PercolatorRequest.blocked(
                            List.of(
                                    "No Percolator can be used on this computer.",
                                    "The Percolator setting testFDR is not valid: no.")));
            engine.settle();
            withPercolator.recheck();
            engine.settle();
            assertAll(
                    () ->
                            assertEquals(
                                    List.of(
                                            "Comet 2026.03.0 is not installed.",
                                            "No Percolator can be used on this computer.",
                                            "The Percolator setting testFDR is not valid: no."),
                                    readiness.engineReasons()),
                    () -> assertFalse(readiness.runEnabled()),
                    () -> assertEquals(RunViewModel.PREVIEW_BLOCKED, withPercolator.preview()),
                    () -> assertFalse(withPercolator.start(), "Run does nothing"));
            engine.answer(model -> clean());
            percolator.set(Percolators.ready());
            engine.settle();
            assertAll(
                    "the half became runnable: checked again by itself, and Run is enabled",
                    () -> assertEquals(List.of(), readiness.engineReasons()),
                    () -> assertTrue(readiness.runEnabled()),
                    () -> assertEquals(RunViewModel.PREVIEW_FIRST_RUN, withPercolator.preview()));
        }

        @Test
        @DisplayName(
                "a half not read yet is waited for: no check is made and Run says the check is"
                        + " running, until the half changes")
        void pendingHalfIsWaitedFor() {
            engine.settle();
            int before = engine.checks();
            percolator.set(PercolatorRequest.pending("The Percolator builds are being read."));
            engine.settle();
            assertAll(
                    () -> assertEquals(before, engine.checks(), "no check on a pending half"),
                    () -> assertEquals(List.of(RunViewModel.CHECKING), readiness.engineReasons()),
                    () -> assertEquals(RunViewModel.PREVIEW_CHECKING, withPercolator.preview()),
                    () -> assertFalse(withPercolator.start()));
            percolator.set(Percolators.ready());
            engine.settle();
            assertEquals(before + 1, engine.checks());
            assertTrue(readiness.runEnabled());
        }

        @Test
        @DisplayName(
                "the port is given the half as it was when the check was asked, and a change"
                        + " asks again and drops the older answer")
        void theHalfReachesThePort() {
            engine.settle();
            int before = engine.checks();
            PercolatorRequest blocked = PercolatorRequest.blocked(List.of("Not read yet."));
            percolator.set(blocked);
            engine.background().drain();
            assertEquals(before + 1, engine.checks(), "a change of the half checks again");
            assertEquals(blocked, engine.percolatorsChecked().get(before));
            PercolatorRequest ready = Percolators.ready();
            percolator.set(ready);
            engine.settle();
            assertAll(
                    () -> assertEquals(before + 2, engine.checks()),
                    () -> assertEquals(ready, engine.percolatorsChecked().get(before + 1)),
                    () ->
                            assertEquals(
                                    List.of(),
                                    readiness.engineReasons(),
                                    "the older, blocked answer was dropped"));
        }

        @Test
        @DisplayName("Run starts with the half, and running is observable until the run ends")
        void runCarriesTheHalf() {
            engine.announce(PLAN, DESCRIPTION);
            engine.settle();
            withPercolator.recheck();
            engine.settle();
            assertFalse(withPercolator.runningProperty().get());
            assertTrue(withPercolator.start());
            assertTrue(withPercolator.runningProperty().get(), "running from the moment of Run");
            engine.settle();
            assertEquals(Percolators.ready(), engine.started().get(0).percolator());
            engine.started()
                    .get(0)
                    .observer()
                    .onRunFinished(result(AttemptOutcome.SUCCEEDED, allSucceeded(), Map.of()));
            engine.settle();
            assertFalse(withPercolator.runningProperty().get(), "not running once it ended");
        }
    }

    private static RunResult result(
            AttemptOutcome outcome,
            Map<EngineStep, StepState> states,
            Map<EngineStep, String> failures) {
        return new RunResult(
                1,
                outcome,
                RunState.deriveFrom(PLAN, states),
                states,
                failures,
                manifest(),
                List.of(),
                0);
    }

    private static ProvenanceManifest manifest() {
        Instant at = Instant.parse("2026-10-07T00:00:00Z");
        return ProvenanceManifest.current(
                new RunRecord(
                        new RunId("run-1"), "p", ProvenanceStatus.COMPLETED, at, Optional.of(at)),
                ApplicationRecord.capture("0.0.0-test", "unknown"),
                Map.of(),
                List.of(),
                List.of());
    }

    private static FileHashes hashes(char digit) {
        return new FileHashes(String.valueOf(digit).repeat(32), String.valueOf(digit).repeat(64));
    }

    /** Inputs of a run, its parameter file's bytes standing for the configuration. */
    private static StepInputs inputsWith(String parameters) {
        Map<InputKind, InputValue> values = new EnumMap<>(InputKind.class);
        values.put(
                InputKind.SPECTRUM_FILES,
                new InputValue.Files(
                        List.of(new InputValue.NamedFile("k562_3.mzML", hashes('a')))));
        values.put(
                InputKind.FASTA,
                new InputValue.Files(List.of(new InputValue.NamedFile("db.fasta", hashes('b')))));
        values.put(
                InputKind.COMET_PARAMETERS,
                new InputValue.Bytes(
                        "params-a".equals(parameters) || "a".equals(parameters)
                                ? "c".repeat(64)
                                : "d".repeat(64)));
        values.put(InputKind.COMET_INDEX_MODE, new InputValue.Text("none"));
        values.put(InputKind.COMET_TOOL, new InputValue.Tool("2026.03.0", "f".repeat(64)));
        return StepInputs.of(values);
    }

    private static StepFingerprint fingerprint(StepInputs inputs, EngineStep step) {
        return Fingerprints.compute(PLAN, inputs).get(step);
    }

    /** The fingerprints a run records when every step succeeded. */
    private static Map<EngineStep, StepFingerprint> recordedAll(StepInputs inputs) {
        Map<EngineStep, StepFingerprint> recorded = new EnumMap<>(EngineStep.class);
        Map<EngineStep, StepFingerprint> all = Fingerprints.compute(PLAN, inputs);
        for (EngineStep step : PLAN.steps()) {
            if (step.kind() == org.cometgui.workflow.state.StepKind.RESULT) {
                recorded.put(step, all.get(step));
            }
        }
        return recorded;
    }
}
