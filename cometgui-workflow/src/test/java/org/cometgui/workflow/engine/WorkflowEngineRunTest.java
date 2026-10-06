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

package org.cometgui.workflow.engine;

import static org.cometgui.workflow.engine.EngineAssertions.awaitResult;
import static org.cometgui.workflow.engine.EngineAssertions.finishedEvent;
import static org.cometgui.workflow.engine.EngineAssertions.intactEvents;
import static org.cometgui.workflow.engine.EngineAssertions.sha256;
import static org.cometgui.workflow.engine.EngineAssertions.stageEvents;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.RecordedFingerprint;
import org.cometgui.domain.run.RunDescriptor;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.provenance.events.ProvenanceEvent;
import org.cometgui.provenance.events.ProvenanceEventType;
import org.cometgui.provenance.manifest.FileRecord;
import org.cometgui.provenance.manifest.ManifestReader;
import org.cometgui.provenance.manifest.ManifestWriter;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ProvenanceStatus;
import org.cometgui.provenance.manifest.ToolRecord;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.Plan;
import org.cometgui.workflow.state.RerunPreview;
import org.cometgui.workflow.state.RunState;
import org.cometgui.workflow.state.StepFingerprint;
import org.cometgui.workflow.state.StepState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Whole attempts through the real engine, process service, hasher, run store, event log and
 * manifest writer: what a successful and a failed attempt leave behind, read back from disk.
 */
class WorkflowEngineRunTest {

    private static final String PLAN_IDS =
            "validate-configuration resolve-comet serialise-comet-params hash-inputs run-comet"
                    + " validate-comet-outputs merge-pin finalise-provenance";

    @TempDir private Path tmp;

    @Test
    void aSuccessfulAttemptMovesEveryStepThroughItsStatesInOrderAndRecordsThem()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 2)) {
            RecordingListener listener = new RecordingListener();
            RunHandle handle =
                    fixture.engine().start(fixture.request(fixture.standardActions()), listener);
            RunResult result = awaitResult(handle);
            RunResult delivered = listener.finished().get(30, TimeUnit.SECONDS);

            assertEquals(result, delivered, "onRunFinished delivers the same result");
            assertEquals(AttemptOutcome.SUCCEEDED, result.outcome());
            assertEquals(RunState.SUCCEEDED, result.runState());
            assertEquals(1, result.attempt());
            assertEquals(List.of(), result.finalisationErrors());
            assertEquals(0L, result.listenerFailures());
            for (EngineStep step : EngineFixture.PLAN.steps()) {
                assertEquals(StepState.SUCCEEDED, result.states().get(step), step.id());
            }
            assertEquals(RunState.SUCCEEDED, handle.runState());

            // The documented order, step by step, as the listener saw it.
            assertEquals(
                    List.of(
                            StepState.NOT_STARTED,
                            StepState.VALIDATING,
                            StepState.READY,
                            StepState.RUNNING,
                            StepState.SUCCEEDED),
                    listener.path(EngineStep.VALIDATE_CONFIGURATION));
            for (EngineStep step : EngineFixture.PLAN.steps()) {
                if (step != EngineStep.VALIDATE_CONFIGURATION) {
                    assertEquals(
                            List.of(
                                    StepState.NOT_STARTED,
                                    StepState.READY,
                                    StepState.RUNNING,
                                    StepState.SUCCEEDED),
                            listener.path(step),
                            step.id());
                }
            }
            // No step leaves NOT_STARTED before every planned upstream step has succeeded.
            List<StepTransition> seen = listener.transitions();
            for (EngineStep step : EngineFixture.PLAN.steps()) {
                int dispatched = indexOf(seen, step, StepState.NOT_STARTED);
                for (EngineStep upstream : EngineFixture.PLAN.upstreamOf(step)) {
                    int done = indexOfInto(seen, upstream, StepState.SUCCEEDED);
                    assertTrue(
                            done < dispatched,
                            step.id() + " was dispatched before " + upstream.id() + " succeeded");
                }
            }
            // The run state each transition carries is the one derived at that moment.
            assertEquals(RunState.RUNNING, seen.get(0).runState());
            assertEquals(RunState.SUCCEEDED, seen.get(seen.size() - 1).runState());
            for (String thread : listener.threadNames()) {
                assertTrue(thread.startsWith("cometgui-run-events-"), thread);
            }

            // The same order in the event log, which must parse whole.
            List<ProvenanceEvent> events = intactEvents(fixture.layout().eventLogFile());
            ProvenanceEvent first = events.get(0);
            ProvenanceEvent last = events.get(events.size() - 1);
            assertEquals(ProvenanceEventType.RUN_STARTED, first.type());
            assertEquals(
                    Map.of("run.id", "run-0001", "attempt", "1", "plan", PLAN_IDS),
                    first.payload());
            assertEquals(ProvenanceEventType.RUN_FINISHED, last.type());
            assertEquals(
                    Map.of("run.id", "run-0001", "attempt", "1", "status", "completed"),
                    last.payload());
            assertEquals(
                    List.of(
                            "stage.started validating",
                            "stage.started ready",
                            "stage.started running",
                            "stage.finished succeeded"),
                    stageEvents(events, "validate-configuration"));
            assertEquals(
                    List.of(
                            "stage.started ready",
                            "stage.started running",
                            "stage.finished succeeded"),
                    stageEvents(events, "merge-pin"));
            assertEquals(
                    Map.of(
                            "stage", "merge-pin",
                            "state", "succeeded",
                            "attempt", "1",
                            "invocations", "",
                            "merge.rows", "2"),
                    finishedEvent(events, "merge-pin").payload());
            assertEquals(
                    "comet-01 comet-02",
                    finishedEvent(events, "run-comet").payload().get("invocations"));
            assertEquals(2, countOf(events, ProvenanceEventType.TOOL_INVOKED));
            // One file.hashed per declared file per step: 1 + 3 + 7 + 2 + 3.
            assertEquals(16, countOf(events, ProvenanceEventType.FILE_HASHED));

            // provenance.json, read back.
            ProvenanceManifest manifest =
                    ManifestReader.readFrom(fixture.layout().provenanceJsonFile());
            // In memory the instants carry microseconds; the file carries milliseconds. Rendered,
            // the two must be the same document.
            ManifestWriter writer = ManifestWriter.redactingWith(SecretRedactor.patternsOnly());
            assertEquals(
                    Files.readString(fixture.layout().provenanceJsonFile()),
                    writer.render(result.manifest()),
                    "the result carries what was written");
            assertEquals(ProvenanceStatus.COMPLETED, manifest.run().status());
            assertEquals("run-0001", manifest.run().runId().value());
            assertEquals("project-engine", manifest.run().projectId());
            assertTrue(manifest.run().end().isPresent());
            assertEquals(
                    new TreeMap<>(
                            Map.of(
                                    "comet.release", "2026.03.0",
                                    "workflow.attempt", "1",
                                    "workflow.plan", PLAN_IDS)),
                    manifest.settings());
            assertEquals(2, manifest.tools().size());
            for (int position = 1; position <= 2; position++) {
                ToolRecord tool = manifest.tools().get(position - 1);
                Path log = fixture.layout().cometLogFile(position);
                String stageId = EngineFixture.stageId(position);
                assertAll(
                        () -> assertEquals("fake-comet", tool.name()),
                        () -> assertEquals(Set.of("fake"), tool.capabilities()),
                        () -> assertEquals(Optional.of(stageId), tool.stageId()),
                        () -> assertEquals(0, tool.execution().exitCode()),
                        () -> assertEquals(ProvenanceStatus.COMPLETED, tool.execution().status()),
                        () -> assertEquals(log, tool.execution().stdout().orElseThrow().path()),
                        () -> assertEquals(tool.execution().stdout(), tool.execution().stderr()),
                        () ->
                                assertEquals(
                                        sha256(log),
                                        tool.execution().stdout().orElseThrow().hashes().sha256()));
            }
            assertEquals(expectedFiles(fixture), describe(manifest.files()));
            for (FileRecord file : manifest.files()) {
                assertEquals(sha256(file.path()), file.hashes().sha256(), file.path().toString());
                assertEquals(Files.size(file.path()), file.sizeBytes(), file.path().toString());
            }
            String report = Files.readString(fixture.layout().provenanceRstFile());
            assertTrue(report.contains("run-0001"), "the report names the run");
            assertTrue(report.contains("comet-02"), "the report names the second invocation");

            // run.json: the attempt, and the fingerprint of every step that succeeded.
            RunDescriptor recorded = fixture.store().read(fixture.layout());
            assertEquals(1, recorded.attempts().size());
            assertEquals(AttemptOutcome.SUCCEEDED, recorded.attempts().get(0).outcome());
            Map<EngineStep, StepFingerprint> expected =
                    RerunPreview.compute(EngineFixture.PLAN, fixture.inputs(), Map.of())
                            .fingerprints();
            Map<String, RecordedFingerprint> expectedRecorded = new TreeMap<>();
            for (Map.Entry<EngineStep, StepFingerprint> entry : expected.entrySet()) {
                expectedRecorded.put(
                        entry.getKey().id(), RecordedFingerprints.toRecorded(entry.getValue()));
            }
            assertEquals(expectedRecorded, recorded.attempts().get(0).succeededSteps());
        }
    }

    @Test
    void hashingTheInputsRunsAlongsideToolResolution()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            CyclicBarrier both = new CyclicBarrier(2);
            Map<EngineStep, StepAction> actions = new EnumMap<>(fixture.standardActions());
            FakeStep.Body meet =
                    context -> {
                        try {
                            both.await(60, TimeUnit.SECONDS);
                        } catch (java.util.concurrent.BrokenBarrierException
                                | java.util.concurrent.TimeoutException alone) {
                            throw new StepFailedException(
                                    context.step().id() + " ran alone: " + alone);
                        }
                    };
            actions.put(EngineStep.RESOLVE_COMET, FakeStep.doing(StepDeclaration.NOTHING, meet));
            actions.put(
                    EngineStep.HASH_INPUTS,
                    FakeStep.doing(
                            fixture.standardActions().get(EngineStep.HASH_INPUTS).declaration(),
                            meet));
            RunResult result =
                    awaitResult(
                            fixture.engine()
                                    .start(fixture.request(actions), new RecordingListener()));
            assertEquals(Map.of(), result.failures());
            assertEquals(StepState.SUCCEEDED, result.states().get(EngineStep.RESOLVE_COMET));
            assertEquals(StepState.SUCCEEDED, result.states().get(EngineStep.HASH_INPUTS));
        }
    }

    @Test
    void aFailedStepFailsTheRunAndNothingDownstreamOfItStarts()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 2)) {
            Map<EngineStep, StepAction> actions = new EnumMap<>(fixture.standardActions());
            actions.put(
                    EngineStep.MERGE_PIN,
                    FakeStep.doing(
                            fixture.mergeDeclaration(),
                            context -> {
                                throw new StepFailedException("merge refused, deliberately");
                            }));
            FakeStep finalise = FakeStep.nothing();
            actions.put(EngineStep.FINALISE_PROVENANCE, finalise);
            RecordingListener listener = new RecordingListener();
            RunHandle handle = fixture.engine().start(fixture.request(actions), listener);
            RunResult result = awaitResult(handle);

            assertEquals(AttemptOutcome.FAILED, result.outcome());
            assertEquals(RunState.FAILED, result.runState());
            assertEquals(StepState.FAILED, result.states().get(EngineStep.MERGE_PIN));
            assertEquals(
                    StepState.NOT_STARTED, result.states().get(EngineStep.FINALISE_PROVENANCE));
            assertEquals(0, finalise.executions(), "a step downstream of a failure never runs");
            assertEquals(List.of(), listener.path(EngineStep.FINALISE_PROVENANCE));
            assertEquals(
                    Map.of(EngineStep.MERGE_PIN, "merge refused, deliberately"), result.failures());
            assertEquals(
                    List.of(
                            StepState.NOT_STARTED,
                            StepState.READY,
                            StepState.RUNNING,
                            StepState.FAILED),
                    listener.path(EngineStep.MERGE_PIN));

            List<ProvenanceEvent> events = intactEvents(fixture.layout().eventLogFile());
            assertEquals(
                    "merge refused, deliberately",
                    finishedEvent(events, "merge-pin").payload().get("message"));
            assertEquals(List.of(), stageEvents(events, "finalise-provenance"));
            assertEquals("failed", events.get(events.size() - 1).payload().get("status"));

            ProvenanceManifest manifest =
                    ManifestReader.readFrom(fixture.layout().provenanceJsonFile());
            assertEquals(ProvenanceStatus.FAILED, manifest.run().status());
            assertFalse(
                    describe(manifest.files())
                            .contains(
                                    "output merged-pin "
                                            + fixture.layout().mergedPinFile()
                                            + " completed"));

            RunDescriptor recorded = fixture.store().read(fixture.layout());
            assertEquals(AttemptOutcome.FAILED, recorded.attempts().get(0).outcome());
            assertEquals(
                    Set.of(
                            "validate-configuration",
                            "resolve-comet",
                            "serialise-comet-params",
                            "hash-inputs",
                            "run-comet",
                            "validate-comet-outputs"),
                    recorded.attempts().get(0).succeededSteps().keySet());
        }
    }

    @Test
    void aPlanWithAStepThatHasNoImplementationIsRefusedByNameBeforeAnythingStarts()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            RunRequest request =
                    new RunRequest(
                            fixture.store(),
                            fixture.lock(),
                            fixture.layout(),
                            Plan.covering(Set.of(EngineStep.FINALISE_RESULTS)),
                            fixture.inputs(),
                            Set.of(),
                            fixture.standardActions(),
                            fixture.request(fixture.standardActions()).application(),
                            Map.of());
            IllegalArgumentException refused =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> fixture.engine().start(request, new RecordingListener()));
            assertEquals(
                    "the plan includes steps with no implementation in this build:"
                            + " resolve-percolator, run-percolator, parse-percolator,"
                            + " finalise-results; a run cannot include them, and they are never"
                            + " recorded as succeeded or skipped",
                    refused.getMessage());
            assertThrows(
                    IllegalArgumentException.class, () -> fixture.engine().checkReuse(request));
            assertEquals(List.of(), fixture.store().read(fixture.layout()).attempts());
            assertFalse(Files.exists(fixture.layout().eventLogFile()));
        }
    }

    @Test
    void anOutputMissingDespiteExitZeroFailsTheStepAndItsOtherOutputsArePartial()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 2)) {
            Map<EngineStep, StepAction> actions = new EnumMap<>(fixture.standardActions());
            actions.put(
                    EngineStep.RUN_COMET,
                    FakeStep.doing(
                            fixture.cometDeclaration(),
                            context ->
                                    context.invokeAll(
                                            List.of(
                                                    fixture.fake(
                                                            "comet-01",
                                                            "succeed",
                                                            fixture.pepXml(1).toString(),
                                                            fixture.pin(1).toString()),
                                                    fixture.fake(
                                                            "comet-02",
                                                            "no-output",
                                                            fixture.pepXml(2).toString())),
                                            1)));
            RunResult result =
                    awaitResult(
                            fixture.engine()
                                    .start(fixture.request(actions), new RecordingListener()));
            assertEquals(StepState.FAILED, result.states().get(EngineStep.RUN_COMET));
            assertEquals(
                    "run-comet reported success, but its declared output file, role pepxml "
                            + fixture.pepXml(2)
                            + " does not exist",
                    result.failures().get(EngineStep.RUN_COMET));
            ProvenanceManifest manifest =
                    ManifestReader.readFrom(fixture.layout().provenanceJsonFile());
            List<String> files = describe(manifest.files());
            assertTrue(
                    files.contains("output pepxml " + fixture.pepXml(1) + " partial"),
                    files::toString);
            assertTrue(
                    files.contains("output pin " + fixture.pin(1) + " partial"), files::toString);
            assertEquals(
                    List.of(ProvenanceStatus.COMPLETED, ProvenanceStatus.COMPLETED),
                    List.of(
                            manifest.tools().get(0).execution().status(),
                            manifest.tools().get(1).execution().status()));
        }
    }

    @Test
    void aFailedInvocationsPartialOutputIsRecordedPartialWithItsHash()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            Map<EngineStep, StepAction> actions = new EnumMap<>(fixture.standardActions());
            actions.put(
                    EngineStep.RUN_COMET,
                    FakeStep.doing(
                            fixture.cometDeclaration(),
                            context ->
                                    context.invoke(
                                            fixture.fake(
                                                    "comet-01",
                                                    "partial-then-fail",
                                                    fixture.pepXml(1).toString(),
                                                    "3"))));
            RunResult result =
                    awaitResult(
                            fixture.engine()
                                    .start(fixture.request(actions), new RecordingListener()));
            assertEquals(
                    "invocation comet-01 exited with code 3; its log is "
                            + fixture.layout().cometLogFile(1),
                    result.failures().get(EngineStep.RUN_COMET));
            ProvenanceManifest manifest =
                    ManifestReader.readFrom(fixture.layout().provenanceJsonFile());
            FileRecord partial = null;
            for (FileRecord file : manifest.files()) {
                if (file.path().equals(fixture.pepXml(1))) {
                    partial = file;
                }
            }
            assertEquals(ProvenanceStatus.PARTIAL, partial.status());
            assertEquals(7L, partial.sizeBytes());
            // The digests of the seven ASCII bytes "partial", computed with sha256sum and md5sum.
            assertEquals(
                    "9834a14ab9bcaa0f6a8da71073617eac8f004e596a3fa11d807b84631b825d9d",
                    partial.hashes().sha256());
            assertEquals("0e87c1212a698494dcdb198af3e0eb2f", partial.hashes().md5());
            assertEquals(3, manifest.tools().get(0).execution().exitCode());
            assertEquals(ProvenanceStatus.FAILED, manifest.tools().get(0).execution().status());
        }
    }

    @Test
    void aListenerThatThrowsNeitherStopsTheRunNorLaterCallbacks()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            List<StepTransition> delivered = new ArrayList<>();
            StepStateListener throwing =
                    transition -> {
                        synchronized (delivered) {
                            delivered.add(transition);
                        }
                        throw new IllegalStateException("a listener defect");
                    };
            RunResult result =
                    awaitResult(
                            fixture.engine()
                                    .start(fixture.request(fixture.standardActions()), throwing));
            assertEquals(AttemptOutcome.SUCCEEDED, result.outcome());
            // validate-configuration moves four times, the seven other steps three times each.
            assertEquals(25, delivered.size());
            assertEquals(25L, result.listenerFailures());
        }
    }

    private static int indexOf(List<StepTransition> seen, EngineStep step, StepState from) {
        for (int index = 0; index < seen.size(); index++) {
            if (seen.get(index).step() == step && seen.get(index).from() == from) {
                return index;
            }
        }
        throw new AssertionError(step.id() + " never left " + from);
    }

    private static int indexOfInto(List<StepTransition> seen, EngineStep step, StepState to) {
        for (int index = 0; index < seen.size(); index++) {
            if (seen.get(index).step() == step && seen.get(index).to() == to) {
                return index;
            }
        }
        throw new AssertionError(step.id() + " never reached " + to);
    }

    private static long countOf(List<ProvenanceEvent> events, ProvenanceEventType type) {
        return events.stream().filter(event -> event.type() == type).count();
    }

    static List<String> describe(List<FileRecord> files) {
        List<String> described = new ArrayList<>();
        for (FileRecord file : files) {
            described.add(
                    file.direction().wireName()
                            + " "
                            + file.role()
                            + " "
                            + file.path()
                            + " "
                            + file.status().wireName());
        }
        return described;
    }

    private static List<String> expectedFiles(EngineFixture fixture) {
        Path params = fixture.layout().cometParamsFile();
        return List.of(
                "output comet-params " + params + " completed",
                "input spectrum " + fixture.spectra().get(0) + " completed",
                "input spectrum " + fixture.spectra().get(1) + " completed",
                "input fasta " + fixture.fasta() + " completed",
                "input comet-params " + params + " completed",
                "output pepxml " + fixture.pepXml(1) + " completed",
                "output pin " + fixture.pin(1) + " completed",
                "output pepxml " + fixture.pepXml(2) + " completed",
                "output pin " + fixture.pin(2) + " completed",
                "input pin " + fixture.pin(1) + " completed",
                "input pin " + fixture.pin(2) + " completed",
                "output merged-pin " + fixture.layout().mergedPinFile() + " completed");
    }
}
