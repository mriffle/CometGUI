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

package org.cometgui.workflow.steps;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.RunAttempt;
import org.cometgui.provenance.events.ProvenanceEvent;
import org.cometgui.provenance.events.ProvenanceEventType;
import org.cometgui.provenance.manifest.FileDirection;
import org.cometgui.provenance.manifest.FileRecord;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ProvenanceStatus;
import org.cometgui.provenance.manifest.ToolRecord;
import org.cometgui.workflow.engine.ReuseRefusedException;
import org.cometgui.workflow.engine.RunHandle;
import org.cometgui.workflow.engine.RunResult;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.RunState;
import org.cometgui.workflow.state.StepState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The specification's failure path <strong>"the user cancels Percolator"</strong> through the real
 * workflow and engine (Phase 14, gate item 4): a search whose Comet half has succeeded and whose
 * Percolator is running -- a stand-in that has written its files and keeps running with a child of
 * its own ({@link StandInTools}) -- is cancelled.
 *
 * <p>Proved, each by observation rather than by an exit code: the Percolator process and its child
 * are dead by pid; {@code run-percolator} went through {@code CANCEL_REQUESTED} to {@code
 * CANCELLED}, every step after it never started, every step before it succeeded; the attempt is
 * recorded cancelled in {@code run.json} with the Comet half's successes kept for a retry; the
 * stage log ends with the cancellation; the event log ends with the run cancelled; and {@code
 * provenance.json} is sealed as cancelled with the Percolator tool record cancelled and exactly
 * Percolator's outputs marked partial ({@code R-PROV-01}), the Comet outputs and the merged PIN
 * complete.
 *
 * <p>Waits are on the lines the stand-in prints and on the run's own end, never on a delay. No file
 * outside this module is read.
 */
@EnabledOnOs(
        value = {OS.LINUX, OS.MAC},
        disabledReason = "the stand-in tools are POSIX shell scripts")
class PercolatorCancellationTest {

    @Test
    @DisplayName(
            "cancel while Percolator runs: Percolator and its child dead by pid, run-percolator"
                    + " cancelled, nothing after it started, the record sealed as cancelled with"
                    + " Percolator's outputs partial")
    void cancelWhilePercolatorRuns(@TempDir Path directory)
            throws IOException,
                    InterruptedException,
                    RunBlockedException,
                    ReuseRefusedException,
                    ExecutionException,
                    TimeoutException {
        FakeSearch fake = FakeSearch.create(directory);
        StandInTools.comet(fake.executable());
        Path percolator =
                StandInTools.percolator(
                        fake.root().resolve("bin/percolator"), StandInTools.Ending.CANCELLED_ONLY);
        try (RealProject project = RealProject.create(fake.project())) {
            PreparedRun prepared = project.prepare(StandInTools.request(fake, percolator));
            CompletableFuture<String> childLine =
                    project.sink().when(text -> text.startsWith("child "));
            CompletableFuture<String> toolLine =
                    project.sink().when(text -> text.startsWith("pid "));
            StandInTools.Watching transitions = new StandInTools.Watching();
            RunHandle handle = project.workflow().start(project.engine(), prepared, transitions);

            ProcessHandle child =
                    StandInTools.processOf(
                            StandInTools.awaitLine(childLine, transitions), "child ");
            ProcessHandle tool =
                    StandInTools.processOf(StandInTools.awaitLine(toolLine, transitions), "pid ");
            List<RealProject.Launch> launched = project.runner().launchesOf(percolator);
            // Before: not vacuous. Percolator runs, as launched by the service, with its child.
            assertAll(
                    () -> assertEquals(1, launched.size(), "Percolator was launched once"),
                    () -> assertEquals(launched.get(0).process().pid(), tool.pid()),
                    () -> assertTrue(tool.isAlive(), "Percolator runs when it is cancelled"),
                    () -> assertTrue(child.isAlive(), "and so does its child"),
                    () ->
                            assertTrue(
                                    tool.descendants().anyMatch(h -> h.pid() == child.pid()),
                                    "the child is Percolator's descendant"),
                    () ->
                            assertEquals(
                                    StepState.RUNNING,
                                    handle.states().get(EngineStep.RUN_PERCOLATOR)));

            handle.cancel();
            RunResult result =
                    handle.await(RealProject.RUN_BOUND)
                            .orElseThrow(() -> new AssertionError("the cancelled run never ended"));

            ProvenanceManifest manifest = RunEvidence.manifest(prepared.layout());
            List<ToolRecord> percolators =
                    RunEvidence.tools(manifest, CometWorkflow.PERCOLATOR_TOOL_NAME);
            RunAttempt attempt = project.store().read(prepared.layout()).attempts().get(0);
            List<String> log =
                    RunEvidence.stageLog(
                            prepared.layout().logsDirectory().resolve("percolator.log"));
            List<ProvenanceEvent> events = RunEvidence.events(prepared.layout());
            ProvenanceEvent last = events.get(events.size() - 1);
            assertAll(
                    "cancelled while Percolator ran",
                    () ->
                            assertFalse(
                                    StandInTools.running(tool.pid()),
                                    "pid " + tool.pid() + " (Percolator) outlived the cancel"),
                    () ->
                            assertFalse(
                                    StandInTools.running(child.pid()),
                                    "pid " + child.pid() + " (its child) outlived the cancel"),
                    () -> assertEquals(AttemptOutcome.CANCELLED, result.outcome()),
                    () -> assertEquals(RunState.CANCELLED, result.runState()),
                    () ->
                            assertEquals(
                                    StandInTools.statesUpTo(
                                            EngineStep.RUN_PERCOLATOR, StepState.CANCELLED),
                                    new TreeMap<>(result.states())),
                    () ->
                            assertEquals(
                                    List.of(
                                            StepState.NOT_STARTED,
                                            StepState.READY,
                                            StepState.RUNNING,
                                            StepState.CANCEL_REQUESTED,
                                            StepState.CANCELLED),
                                    transitions.path(EngineStep.RUN_PERCOLATOR)),
                    () ->
                            assertEquals(
                                    AttemptOutcome.CANCELLED,
                                    attempt.outcome(),
                                    "run.json records the attempt cancelled"),
                    () ->
                            assertEquals(
                                    List.of(
                                            "hash-inputs",
                                            "merge-pin",
                                            "resolve-comet",
                                            "resolve-percolator",
                                            "run-comet",
                                            "serialise-comet-params",
                                            "validate-comet-outputs",
                                            "validate-configuration"),
                                    attempt.succeededSteps().keySet().stream().sorted().toList(),
                                    "the Comet half is kept for a retry; Percolator is not"),
                    () ->
                            assertTrue(
                                    log.get(log.size() - 1).contains("[cometgui] stage percolator"),
                                    () -> "the stage log ends with the stage: " + log),
                    () ->
                            assertTrue(
                                    log.get(log.size() - 1).endsWith(", cancellation requested"),
                                    () -> "and says it was cancelled: " + log),
                    () -> assertEquals(ProvenanceEventType.RUN_FINISHED, last.type()),
                    () -> assertEquals("cancelled", last.payload().get(ProvenanceEvent.STATUS_KEY)),
                    () -> assertEquals(ProvenanceStatus.CANCELLED, manifest.run().status()),
                    () -> assertEquals(1, percolators.size()),
                    () ->
                            assertEquals(
                                    ProvenanceStatus.CANCELLED,
                                    percolators.get(0).execution().status()),
                    () -> assertEquals(143, percolators.get(0).execution().exitCode()),
                    () ->
                            assertEquals(
                                    expectedOutputs(),
                                    outputsOf(manifest),
                                    "Percolator's outputs partial, the Comet half's complete"),
                    () ->
                            assertTrue(
                                    Files.readString(prepared.layout().provenanceRstFile())
                                            .contains("cancelled"),
                                    "provenance.rst is rendered from the cancelled model"));
        }
    }

    /**
     * The run's outputs as file name to role and status, typed out: everything the Comet half wrote
     * complete, every file the cancelled Percolator had written partial. No pout XML: no downstream
     * stage that needs it is enabled, so {@code -X} is not passed.
     */
    private static Map<String, String> expectedOutputs() {
        Map<String, String> outputs = new TreeMap<>(StandInTools.percolatorOutputs("partial"));
        outputs.put("comet.params", "comet-params completed");
        outputs.put("k562_3.pep.xml", "pepxml completed");
        outputs.put("k562_3.pin", "pin completed");
        outputs.put("merged.pin", "merged-pin completed");
        return outputs;
    }

    private static Map<String, String> outputsOf(ProvenanceManifest manifest) {
        Map<String, String> outputs = new TreeMap<>();
        for (FileRecord file : manifest.files()) {
            if (file.direction() == FileDirection.OUTPUT) {
                outputs.put(
                        String.valueOf(file.path().getFileName()),
                        file.role() + " " + file.status().wireName());
            }
        }
        return outputs;
    }
}
