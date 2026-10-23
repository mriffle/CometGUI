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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.cometgui.domain.run.RunLayout;
import org.cometgui.provenance.events.ProvenanceEvent;
import org.cometgui.provenance.events.ProvenanceEventType;
import org.cometgui.provenance.manifest.FileDirection;
import org.cometgui.provenance.manifest.FileRecord;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ProvenanceStatus;
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
 * The specification's failure path <strong>"the output or project directory becoming
 * unwritable"</strong>, through the real workflow and engine (Phase 14, gate item 4): write
 * permission is taken away from part of a run's directory <em>while a stage is running</em> -- a
 * stand-in tool that has written its files and waits ({@link StandInTools}) -- and the stage is
 * then let finish.
 *
 * <ol>
 *   <li><strong>The run's own directory</strong>, while Percolator runs: Percolator ends normally,
 *       but {@code run.json} can no longer be replaced. The run fails at {@code run-percolator}
 *       naming the file it could not write; nothing after it starts; the end of the attempt cannot
 *       be recorded either, and the result says so, naming the file; {@code run.json} is left
 *       byte-identical to its last good state and still parses; no temporary file is left behind;
 *       {@code provenance.json} -- in {@code provenance/}, which is still writable -- is sealed as
 *       failed, Percolator's outputs partial.
 *   <li><strong>The run's {@code logs/}</strong>, while Comet runs: Percolator's stage log cannot
 *       be created, so the run fails at {@code run-percolator} naming the log, and Percolator is
 *       never launched; {@code run.json} and {@code provenance.json} both record the failure.
 * </ol>
 *
 * <p>Neither may hang (every wait is bounded) or end in anything that reads as success. The
 * directory is proved unwritable by a refused write before the stage is released, so a test run as
 * root -- which can write anywhere -- <strong>fails</strong> rather than passing vacuously ({@link
 * RealComet#makeReadOnly}). Permission is given back afterwards so the temporary directory can be
 * removed. No file outside this module is read.
 *
 * <p>Not tested here, because the product's behaviour is a defect reported by Phase 14 unit 8b
 * rather than a behaviour to pin: {@code provenance/} becoming unwritable leaves a run recorded as
 * succeeded with no {@code provenance.json}.
 */
@EnabledOnOs(
        value = {OS.LINUX, OS.MAC},
        disabledReason = "the stand-in tools are POSIX shell scripts and modes are POSIX")
class UnwritableRunDirectoryTest {

    private static final String DENIED = "java.nio.file.AccessDeniedException: ";

    @Test
    @DisplayName(
            "the run's directory unwritable while Percolator runs: failed at run-percolator naming"
                    + " run.json, the attempt's end not recordable and said so, run.json intact,"
                    + " provenance sealed as failed")
    void theRunDirectoryWhilePercolatorRuns(@TempDir Path directory)
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
                        fake.root().resolve("bin/percolator"), StandInTools.Ending.RELEASABLE);
        try (RealProject project = RealProject.create(fake.project())) {
            PreparedRun prepared = project.prepare(StandInTools.request(fake, percolator));
            RunLayout layout = prepared.layout();
            CompletableFuture<String> childLine =
                    project.sink().when(text -> text.startsWith("child "));
            StandInTools.Watching watching = new StandInTools.Watching();
            RunHandle handle = project.workflow().start(project.engine(), prepared, watching);
            ProcessHandle child =
                    StandInTools.processOf(StandInTools.awaitLine(childLine, watching), "child ");
            assertEquals(StepState.RUNNING, handle.states().get(EngineStep.RUN_PERCOLATOR));
            byte[] lastGood = Files.readAllBytes(layout.runFile());
            List<String> before = RunEvidence.listing(layout.root());
            RunResult result;
            try {
                RealComet.makeReadOnly(layout.root());
                child.destroy();
                result = awaitEnd(handle);
            } finally {
                RealComet.makeWritable(layout.root());
            }
            String temporary = layout.runFile() + ".tmp-";
            String failure = result.failures().get(EngineStep.RUN_PERCOLATOR);
            RunAttempt attempt = project.store().read(layout).attempts().get(0);
            ProvenanceManifest manifest = RunEvidence.manifest(layout);
            List<ProvenanceEvent> events = RunEvidence.events(layout);
            ProvenanceEvent last = events.get(events.size() - 1);
            assertAll(
                    "the run's directory became unwritable",
                    () -> assertEquals(AttemptOutcome.FAILED, result.outcome()),
                    () -> assertEquals(RunState.FAILED, result.runState()),
                    () ->
                            assertEquals(
                                    StandInTools.statesUpTo(
                                            EngineStep.RUN_PERCOLATOR, StepState.FAILED),
                                    new TreeMap<>(result.states())),
                    () ->
                            assertEquals(
                                    List.of(EngineStep.RUN_PERCOLATOR),
                                    List.copyOf(result.failures().keySet())),
                    () ->
                            assertTrue(
                                    failure.startsWith(
                                            "could not record that run-percolator succeeded:"
                                                    + " java.nio.file.AccessDeniedException: "
                                                    + temporary),
                                    () ->
                                            "the failure names the file it could not write: "
                                                    + failure),
                    () -> assertEquals(1, result.finalisationErrors().size()),
                    () ->
                            assertTrue(
                                    result.finalisationErrors()
                                            .get(0)
                                            .startsWith(
                                                    "could not record the attempt's end: "
                                                            + DENIED
                                                            + temporary),
                                    () ->
                                            "the unrecordable end is stated: "
                                                    + result.finalisationErrors()),
                    () ->
                            assertArrayEquals(
                                    lastGood,
                                    Files.readAllBytes(layout.runFile()),
                                    "run.json is exactly its last good state, never torn"),
                    () ->
                            assertEquals(
                                    AttemptOutcome.RUNNING,
                                    attempt.outcome(),
                                    "which is the attempt still running, as last recorded"),
                    () ->
                            assertEquals(
                                    before,
                                    RunEvidence.listing(layout.root()),
                                    "no temporary file is left in the run's directory"),
                    () -> assertEquals(ProvenanceStatus.FAILED, manifest.run().status()),
                    () -> assertEquals(ProvenanceEventType.RUN_FINISHED, last.type()),
                    () -> assertEquals("failed", last.payload().get(ProvenanceEvent.STATUS_KEY)),
                    () ->
                            assertEquals(
                                    StandInTools.percolatorOutputs("partial"),
                                    percolatorOutputsOf(manifest),
                                    "Percolator's outputs are recorded partial"));
        }
    }

    @Test
    @DisplayName(
            "the run's logs/ unwritable while Comet runs: failed at run-percolator naming the log,"
                    + " Percolator never launched, run.json and provenance both say failed")
    void theLogsDirectoryWhileCometRuns(@TempDir Path directory)
            throws IOException,
                    InterruptedException,
                    RunBlockedException,
                    ReuseRefusedException,
                    ExecutionException,
                    TimeoutException {
        FakeSearch fake = FakeSearch.create(directory);
        StandInTools.comet(fake.executable(), true);
        Path percolator =
                FakePercolator.write(
                        fake.root().resolve("bin/percolator"), FakePercolator.Behaviour.normal());
        try (RealProject project = RealProject.create(fake.project())) {
            PreparedRun prepared = project.prepare(StandInTools.request(fake, percolator));
            RunLayout layout = prepared.layout();
            CompletableFuture<String> childLine =
                    project.sink().when(text -> text.startsWith("child "));
            StandInTools.Watching watching = new StandInTools.Watching();
            RunHandle handle = project.workflow().start(project.engine(), prepared, watching);
            ProcessHandle child =
                    StandInTools.processOf(StandInTools.awaitLine(childLine, watching), "child ");
            assertEquals(StepState.RUNNING, handle.states().get(EngineStep.RUN_COMET));
            RunResult result;
            try {
                RealComet.makeReadOnly(layout.logsDirectory());
                child.destroy();
                result = awaitEnd(handle);
            } finally {
                RealComet.makeWritable(layout.logsDirectory());
            }
            RunAttempt attempt = project.store().read(layout).attempts().get(0);
            ProvenanceManifest manifest = RunEvidence.manifest(layout);
            assertAll(
                    "the run's logs/ became unwritable",
                    () -> assertEquals(AttemptOutcome.FAILED, result.outcome()),
                    () ->
                            assertEquals(
                                    StandInTools.statesUpTo(
                                            EngineStep.RUN_PERCOLATOR, StepState.FAILED),
                                    new TreeMap<>(result.states())),
                    () ->
                            assertEquals(
                                    Map.of(
                                            EngineStep.RUN_PERCOLATOR,
                                            "could not start percolator:"
                                                    + " java.nio.file.AccessDeniedException: "
                                                    + layout.logsDirectory()
                                                            .resolve("percolator.log")),
                                    result.failures(),
                                    "the failure names the log that could not be created"),
                    () -> assertEquals(List.of(), result.finalisationErrors()),
                    () ->
                            assertEquals(
                                    List.of(),
                                    project.runner().launchesOf(percolator),
                                    "Percolator was never launched"),
                    () ->
                            assertEquals(
                                    List.of("comet-01.log"),
                                    RunEvidence.listing(layout.logsDirectory()),
                                    "no Percolator log, and Comet's kept"),
                    () -> assertEquals(AttemptOutcome.FAILED, attempt.outcome()),
                    () -> assertEquals(ProvenanceStatus.FAILED, manifest.run().status()));
        }
    }

    private static RunResult awaitEnd(RunHandle handle) throws InterruptedException {
        return handle.await(RealProject.RUN_BOUND)
                .orElseThrow(
                        () -> {
                            handle.cancel();
                            return new AssertionError(
                                    "the run did not end within " + RealProject.RUN_BOUND);
                        });
    }

    /** Percolator's outputs in the record, as file name to role and status. */
    private static Map<String, String> percolatorOutputsOf(ProvenanceManifest manifest) {
        Map<String, String> outputs = new TreeMap<>();
        for (FileRecord file : manifest.files()) {
            if (file.direction() == FileDirection.OUTPUT && file.role().startsWith("percolator-")) {
                outputs.put(
                        String.valueOf(file.path().getFileName()),
                        file.role() + " " + file.status().wireName());
            }
        }
        return outputs;
    }
}
