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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.beans.property.SimpleObjectProperty;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.run.RunId;
import org.cometgui.provenance.manifest.ApplicationRecord;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ProvenanceStatus;
import org.cometgui.provenance.manifest.RunRecord;
import org.cometgui.ui.testing.Percolators;
import org.cometgui.ui.testing.ScriptedRerun;
import org.cometgui.ui.viewmodel.params.PercolatorRequest;
import org.cometgui.ui.viewmodel.params.RunNotStartedException;
import org.cometgui.workflow.engine.RunResult;
import org.cometgui.workflow.engine.StepTransition;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.Plan;
import org.cometgui.workflow.state.RunState;
import org.cometgui.workflow.state.StepState;
import org.cometgui.workflow.steps.PercolatorRerun;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The compatible-version rerun action over a scripted port and two hand-drained executors: the
 * check off the interface thread and a stale answer dropped, the preview and the action's words,
 * the start, its progress and every ending, refusals and cancellation. Every text is typed out.
 */
class PercolatorRerunViewModelTest {

    /** A derived run's plan: the Percolator steps, with the source's Comet results provided. */
    private static final Plan PLAN = PercolatorRerun.planFor(IndexMode.NONE);

    private static final String DESCRIPTION =
            "run run-2 in /p/runs/run-2, rescoring the merged PIN of run run-1";

    private static final List<String> LINES =
            List.of(
                    "Percolator reruns in a new run on the merged PIN of run run-1.",
                    "run-comet: not executed -- its result is reused from run run-1",
                    "run-percolator: executes -- percolator-build changed");

    private final SimpleObjectProperty<PercolatorRequest> request =
            new SimpleObjectProperty<>(Percolators.ready());

    private final ScriptedRerun port =
            new ScriptedRerun(asked -> RerunCheck.possible("run-1", LINES));

    private final PercolatorRerunViewModel rerun =
            new PercolatorRerunViewModel(port, request, port.background(), port.ui());

    private static final String PREVIEW =
            "Rerun Percolator 3.07.1 from run run-1, in a new run:\n"
                    + "Percolator reruns in a new run on the merged PIN of run run-1.\n"
                    + "run-comet: not executed -- its result is reused from run run-1\n"
                    + "run-percolator: executes -- percolator-build changed";

    private void offered() {
        rerun.refresh();
        port.settle();
        assertTrue(rerun.actionEnabledProperty().get(), rerun.previewProperty()::get);
        port.announce(PLAN, DESCRIPTION);
    }

    @Test
    @DisplayName("nothing is asked on construction; the action is off until a check says yes")
    void construction() {
        assertAll(
                () -> assertEquals(List.of(), port.callers()),
                () ->
                        assertEquals(
                                PercolatorRerunViewModel.CHECKING, rerun.previewProperty().get()),
                () -> assertEquals("Rerun Percolator", rerun.actionTextProperty().get()),
                () -> assertFalse(rerun.actionEnabledProperty().get()),
                () ->
                        assertEquals(
                                "No Percolator rerun has started in this session.",
                                rerun.outcomeProperty().get()),
                () -> assertFalse(rerun.start(), "nothing is offered"));
    }

    @Test
    @DisplayName(
            "the check is made on the background executor with the request as it is, and its"
                    + " preview and action applied on the interface one")
    void possible() {
        rerun.refresh();
        assertEquals(List.of(), port.callers(), "not on the caller's thread");
        port.background().drain();
        assertEquals(List.of("check:true"), port.callers());
        assertEquals(List.of(Percolators.ready()), port.checked());
        assertFalse(rerun.actionEnabledProperty().get(), "not applied yet");
        port.ui().drain();
        assertAll(
                () -> assertEquals(PREVIEW, rerun.previewProperty().get()),
                () -> assertEquals("Rerun Percolator 3.07.1", rerun.actionTextProperty().get()),
                () -> assertTrue(rerun.actionEnabledProperty().get()));
    }

    @Test
    @DisplayName("a request not read yet is not checked: the preview says the check is running")
    void pendingNotChecked() {
        request.set(PercolatorRequest.pending("reading"));
        port.settle();
        assertAll(
                () -> assertEquals(List.of(), port.checked()),
                () ->
                        assertEquals(
                                PercolatorRerunViewModel.CHECKING, rerun.previewProperty().get()),
                () -> assertFalse(rerun.actionEnabledProperty().get()));
    }

    @Test
    @DisplayName("a new check resets the preview and the action until it answers")
    void recheckResets() {
        offered();
        request.set(PercolatorRequest.blocked(List.of("changed")));
        assertAll(
                () ->
                        assertEquals(
                                PercolatorRerunViewModel.CHECKING, rerun.previewProperty().get()),
                () -> assertEquals("Rerun Percolator", rerun.actionTextProperty().get()),
                () -> assertFalse(rerun.actionEnabledProperty().get()));
    }

    @Test
    @DisplayName("the plan is shown before the start is acknowledged; no plan, no description")
    void plannedThenStarted() {
        offered();
        rerun.start();
        port.background().drain();
        port.ui().runOne();
        assertEquals(
                "Rerunning Percolator: " + DESCRIPTION + ".",
                rerun.outcomeProperty().get(),
                "the plan's description, before the start is applied");
        assertFalse(rerun.cancelEnabledProperty().get(), "not started yet");
        port.settle();
        assertTrue(rerun.cancelEnabledProperty().get());

        ScriptedRerun silent = new ScriptedRerun(asked -> RerunCheck.possible("run-1", LINES));
        PercolatorRerunViewModel quiet =
                new PercolatorRerunViewModel(silent, request, silent.background(), silent.ui());
        request.set(Percolators.ready());
        quiet.refresh();
        silent.settle();
        assertTrue(quiet.start());
        silent.settle();
        assertEquals("Rerunning Percolator.", quiet.outcomeProperty().get());
    }

    @Test
    @DisplayName("a refusal is shown in the workflow's words, and the action stays off")
    void refused() {
        port.answer(
                asked ->
                        RerunCheck.refused(
                                "run run-1 already ran Percolator 3.07.1 with these settings."));
        rerun.refresh();
        port.settle();
        assertAll(
                () ->
                        assertEquals(
                                "run run-1 already ran Percolator 3.07.1 with these settings.",
                                rerun.previewProperty().get()),
                () -> assertFalse(rerun.actionEnabledProperty().get()),
                () -> assertEquals("Rerun Percolator", rerun.actionTextProperty().get()));
    }

    @Test
    @DisplayName("a check that throws is stated, never swallowed")
    void checkThrows() {
        port.answer(
                asked -> {
                    throw new IllegalStateException("no project");
                });
        rerun.refresh();
        port.settle();
        assertEquals(
                "Whether Percolator can be rerun could not be checked:"
                        + " java.lang.IllegalStateException: no project",
                rerun.previewProperty().get());
    }

    @Test
    @DisplayName("a change of the request asks again, and the older answer is dropped")
    void staleDropped() {
        rerun.refresh();
        port.background().drain();
        port.answer(asked -> RerunCheck.refused("no run"));
        PercolatorRequest blocked = PercolatorRequest.blocked(List.of("not read"));
        request.set(blocked);
        port.settle();
        assertAll(
                () -> assertEquals(List.of(Percolators.ready(), blocked), port.checked()),
                () -> assertEquals("no run", rerun.previewProperty().get()),
                () -> assertFalse(rerun.actionEnabledProperty().get()));
        port.answer(asked -> RerunCheck.possible("run-1", LINES));
        request.set(PercolatorRequest.blocked(List.of("still not read")));
        port.settle();
        assertEquals(
                "Rerun the selected Percolator from run run-1, in a new run:\n"
                        + String.join("\n", LINES),
                rerun.previewProperty().get(),
                "a request with no build is named generically");
    }

    @Test
    @DisplayName(
            "a started rerun: off the interface thread, its plan and steps shown, its success"
                    + " stated, and the action checked again")
    void succeeds() {
        offered();
        assertTrue(rerun.start());
        assertAll(
                "starting",
                () -> assertTrue(rerun.runningProperty().get()),
                () -> assertFalse(rerun.actionEnabledProperty().get()),
                () -> assertFalse(rerun.start(), "one at a time"),
                () ->
                        assertEquals(
                                "Starting the Percolator rerun: recording the new run and copying"
                                        + " the merged PIN into it.",
                                rerun.outcomeProperty().get()));
        rerun.refresh();
        assertEquals(0, port.ui().pending(), "no check while a rerun runs");
        port.settle();
        assertAll(
                "running",
                () -> assertEquals(List.of("check:true", "start:true"), port.callers()),
                () -> assertEquals(Percolators.ready(), port.started().get(0).percolator()),
                () -> assertTrue(rerun.cancelEnabledProperty().get()),
                () ->
                        assertEquals(
                                "Rerunning Percolator: " + DESCRIPTION + ".",
                                rerun.outcomeProperty().get()));
        port.started()
                .get(0)
                .observer()
                .onTransition(
                        new StepTransition(
                                EngineStep.RUN_PERCOLATOR,
                                StepState.READY,
                                StepState.RUNNING,
                                Instant.parse("2026-10-08T00:00:00Z"),
                                RunState.RUNNING));
        port.settle();
        assertEquals(
                "Rerunning Percolator: " + DESCRIPTION + ". run-percolator: running.",
                rerun.outcomeProperty().get());
        port.started()
                .get(0)
                .observer()
                .onRunFinished(result(AttemptOutcome.SUCCEEDED, Map.of(), List.of()));
        port.settle();
        assertAll(
                "ended",
                () ->
                        assertEquals(
                                "The Percolator rerun succeeded: " + DESCRIPTION + ".",
                                rerun.outcomeProperty().get()),
                () -> assertFalse(rerun.runningProperty().get()),
                () -> assertFalse(rerun.cancelEnabledProperty().get()),
                () -> assertEquals(2, port.checked().size(), "checked again once it ended"));
    }

    @Test
    @DisplayName("a failure names its step; a cancellation and a lost record are stated")
    void endings() {
        offered();
        rerun.start();
        port.settle();
        port.started()
                .get(0)
                .observer()
                .onRunFinished(
                        result(
                                AttemptOutcome.FAILED,
                                Map.of(EngineStep.RUN_PERCOLATOR, "Percolator exited with code 1"),
                                List.of("provenance.json could not be written")));
        port.settle();
        assertEquals(
                "The Percolator rerun failed at step run-percolator (Run Percolator): Percolator"
                        + " exited with code 1. The new run is "
                        + DESCRIPTION
                        + ". Its provenance could not be finalised: provenance.json could not be"
                        + " written",
                rerun.outcomeProperty().get());

        offered();
        rerun.start();
        port.settle();
        port.started()
                .get(1)
                .observer()
                .onRunFinished(result(AttemptOutcome.CANCELLED, Map.of(), List.of()));
        port.settle();
        assertEquals(
                "The Percolator rerun was cancelled: "
                        + DESCRIPTION
                        + ". Its logs and provenance are kept.",
                rerun.outcomeProperty().get());

        offered();
        rerun.start();
        port.settle();
        port.started()
                .get(2)
                .observer()
                .onRunFinished(result(AttemptOutcome.FAILED, Map.of(), List.of()));
        port.settle();
        assertEquals(
                "The Percolator rerun failed: " + DESCRIPTION + ".", rerun.outcomeProperty().get());
    }

    @Test
    @DisplayName("a refused start and a start that throws are stated; the action is offered again")
    void notStarted() {
        offered();
        port.refuseNextStart(new RunNotStartedException("the merged PIN has changed", null));
        rerun.start();
        port.settle();
        assertAll(
                () ->
                        assertEquals(
                                "The Percolator rerun did not start: the merged PIN has changed",
                                rerun.outcomeProperty().get()),
                () -> assertFalse(rerun.runningProperty().get()),
                () -> assertTrue(rerun.actionEnabledProperty().get(), "checked again"));
        port.refuseNextStart(new IllegalStateException("no engine"));
        rerun.start();
        port.settle();
        assertEquals(
                "The Percolator rerun did not start: java.lang.IllegalStateException: no engine",
                rerun.outcomeProperty().get());
    }

    @Test
    @DisplayName("Cancel reaches the port off the interface thread; a failed cancel is stated")
    void cancel() {
        assertFalse(rerun.cancel(), "nothing to cancel");
        offered();
        rerun.start();
        port.settle();
        assertTrue(rerun.cancel());
        assertAll(
                () -> assertFalse(rerun.cancelEnabledProperty().get()),
                () ->
                        assertEquals(
                                "Cancelling the Percolator rerun: " + DESCRIPTION + ".",
                                rerun.outcomeProperty().get()),
                () -> assertEquals(0, port.cancels(), "not on the caller"));
        port.settle();
        assertEquals(1, port.cancels());
        assertTrue(port.callers().contains("cancel:true"));
        assertFalse(rerun.cancel(), "only once");

        port.started()
                .get(0)
                .observer()
                .onRunFinished(result(AttemptOutcome.CANCELLED, Map.of(), List.of()));
        port.settle();
        port.failCancellations(new IllegalStateException("gone"));
        offered();
        rerun.start();
        port.settle();
        rerun.cancel();
        port.settle();
        assertEquals(
                "The cancellation of the Percolator rerun could not be sent:"
                        + " java.lang.IllegalStateException: gone",
                rerun.outcomeProperty().get());
    }

    @Test
    @DisplayName("progress from an attempt that is no longer current is ignored")
    void staleProgress() {
        offered();
        rerun.start();
        port.settle();
        port.started()
                .get(0)
                .observer()
                .onRunFinished(result(AttemptOutcome.SUCCEEDED, Map.of(), List.of()));
        port.settle();
        String ended = rerun.outcomeProperty().get();
        port.started().get(0).observer().planned(PLAN, "an old attempt");
        port.started()
                .get(0)
                .observer()
                .onRunFinished(result(AttemptOutcome.FAILED, Map.of(), List.of()));
        port.settle();
        assertEquals(ended, rerun.outcomeProperty().get());
    }

    @Test
    @DisplayName("a check answer must be whole")
    void checkInvariants() {
        assertAll(
                () -> assertTrue(RerunCheck.possible("run-1", LINES).possible()),
                () -> assertFalse(RerunCheck.refused("no").possible()),
                () -> assertThrows(IllegalArgumentException.class, () -> RerunCheck.refused(" ")),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> RerunCheck.possible("run-1", List.of())),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> new RerunCheck(Optional.empty(), LINES, Optional.of("no"))));
    }

    private static RunResult result(
            AttemptOutcome outcome, Map<EngineStep, String> failures, List<String> unrecorded) {
        Map<EngineStep, StepState> states = new EnumMap<>(EngineStep.class);
        for (EngineStep step : PLAN.steps()) {
            states.put(step, failures.containsKey(step) ? StepState.FAILED : StepState.SUCCEEDED);
        }
        Instant at = Instant.parse("2026-10-08T00:00:00Z");
        return new RunResult(
                1,
                outcome,
                RunState.deriveFrom(PLAN, states),
                states,
                failures,
                ProvenanceManifest.current(
                        new RunRecord(
                                new RunId("run-2"),
                                "p",
                                ProvenanceStatus.COMPLETED,
                                at,
                                Optional.of(at)),
                        ApplicationRecord.capture("0.0.0-test", "unknown"),
                        Map.of(),
                        List.of(),
                        List.of()),
                unrecorded,
                0);
    }
}
