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
import static org.cometgui.workflow.engine.EngineAssertions.parsedStageLog;
import static org.cometgui.workflow.engine.EngineAssertions.stageEvents;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.provenance.events.ProvenanceEvent;
import org.cometgui.provenance.manifest.FileRecord;
import org.cometgui.provenance.manifest.ManifestReader;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ProvenanceStatus;
import org.cometgui.provenance.manifest.ToolRecord;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.RunState;
import org.cometgui.workflow.state.StepState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Cancellation (P8-13, gate 7's fake half, {@code AC-WF-05}, {@code AC-PRV-06}): a running step's
 * tool and the tool's own child are terminated, and the logs, the event log and the provenance
 * record are left whole.
 */
class CancellationTest {

    /** A failure bound for a process to die after cancellation; never a wait that must elapse. */
    private static final long DEATH_BOUND_SECONDS = 60;

    @TempDir private Path tmp;

    @Test
    void cancellingARunningStepKillsItsToolAndTheToolsChildAndLeavesAWholeRecord()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        List<ProcessHandle> started = new ArrayList<>();
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            Map<EngineStep, StepAction> actions = hangingComet(fixture);
            RecordingListener listener = new RecordingListener();
            CompletableFuture<String> childLine = fixture.sink().when(l -> l.startsWith("child "));
            CompletableFuture<String> parentLine = fixture.sink().when(l -> l.startsWith("pid "));
            RunHandle handle = fixture.engine().start(fixture.request(actions), listener);

            ProcessHandle child = handleOf(childLine.get(60, TimeUnit.SECONDS), "child ");
            ProcessHandle parent = handleOf(parentLine.get(60, TimeUnit.SECONDS), "pid ");
            started.add(child);
            started.add(parent);
            // Before: not vacuous. The child exists, runs, and is the tool's descendant.
            assertTrue(child.isAlive(), "the child is alive before cancelling");
            assertTrue(parent.isAlive(), "the tool is alive before cancelling");
            assertTrue(
                    parent.descendants().anyMatch(h -> h.pid() == child.pid()),
                    "the child is among the tool's descendants");
            assertEquals(StepState.RUNNING, handle.states().get(EngineStep.RUN_COMET));

            handle.cancel();

            child.onExit().get(DEATH_BOUND_SECONDS, TimeUnit.SECONDS);
            parent.onExit().get(DEATH_BOUND_SECONDS, TimeUnit.SECONDS);
            assertFalse(child.isAlive(), "the child is dead after cancelling");
            assertFalse(parent.isAlive(), "the tool is dead after cancelling");

            RunResult result = awaitResult(handle);
            handle.cancel(); // idempotent, and a no-op once finished
            assertEquals(AttemptOutcome.CANCELLED, result.outcome());
            assertEquals(RunState.CANCELLED, result.runState());
            assertEquals(RunState.CANCELLED, handle.runState());
            assertEquals(
                    List.of(
                            StepState.NOT_STARTED,
                            StepState.READY,
                            StepState.RUNNING,
                            StepState.CANCEL_REQUESTED,
                            StepState.CANCELLED),
                    listener.path(EngineStep.RUN_COMET));
            for (EngineStep pending :
                    List.of(
                            EngineStep.VALIDATE_COMET_OUTPUTS,
                            EngineStep.MERGE_PIN,
                            EngineStep.FINALISE_PROVENANCE)) {
                assertEquals(StepState.NOT_STARTED, result.states().get(pending), pending.id());
                assertEquals(List.of(), listener.path(pending), pending.id());
            }

            // The stage log parses, and says the stage was cancelled with SIGTERM's 143.
            List<String> log = parsedStageLog(fixture.layout().cometLogFile(1), "comet-01");
            String last = log.get(log.size() - 1);
            assertTrue(last.contains("ended: exit code 143 after "), last);
            assertTrue(last.endsWith(", cancellation requested"), last);
            assertTrue(
                    log.stream().anyMatch(line -> line.endsWith("[stdout] child " + child.pid())));

            // The event log parses whole.
            List<ProvenanceEvent> events = intactEvents(fixture.layout().eventLogFile());
            assertEquals(
                    List.of(
                            "stage.started ready",
                            "stage.started running",
                            "stage.started cancel-requested",
                            "stage.finished cancelled"),
                    stageEvents(events, "run-comet"));
            assertEquals(
                    "comet-01", finishedEvent(events, "run-comet").payload().get("invocations"));
            assertEquals("cancelled", events.get(events.size() - 1).payload().get("status"));

            // provenance.json reads back, finalised as cancelled, the existing output partial.
            ProvenanceManifest manifest =
                    ManifestReader.readFrom(fixture.layout().provenanceJsonFile());
            assertEquals(ProvenanceStatus.CANCELLED, manifest.run().status());
            ToolRecord tool = manifest.tools().get(0);
            assertAll(
                    () -> assertEquals(143, tool.execution().exitCode()),
                    () -> assertNotEquals(71, tool.execution().exitCode(), "71 is the watchdog"),
                    () -> assertEquals(ProvenanceStatus.CANCELLED, tool.execution().status()));
            FileRecord output = recordOf(manifest, fixture.pepXml(1));
            assertEquals(ProvenanceStatus.PARTIAL, output.status());
            assertEquals(
                    "9834a14ab9bcaa0f6a8da71073617eac8f004e596a3fa11d807b84631b825d9d",
                    output.hashes().sha256(),
                    "SHA-256 of the seven bytes \"partial\"");
            assertTrue(
                    Files.readString(fixture.layout().provenanceRstFile()).contains("cancelled"),
                    "provenance.rst is rendered from the same, cancelled, model");
            assertEquals(
                    AttemptOutcome.CANCELLED,
                    fixture.store().read(fixture.layout()).attempts().get(0).outcome());
            assertFalse(
                    fixture.store()
                            .read(fixture.layout())
                            .attempts()
                            .get(0)
                            .succeededSteps()
                            .containsKey("run-comet"));
        } finally {
            for (ProcessHandle process : started) {
                process.destroyForcibly();
            }
        }
    }

    @Test
    void withoutCancellingTheToolAndItsChildStayAlive()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        List<ProcessHandle> started = new ArrayList<>();
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            CompletableFuture<String> childLine = fixture.sink().when(l -> l.startsWith("child "));
            CompletableFuture<String> parentLine = fixture.sink().when(l -> l.startsWith("pid "));
            RunHandle handle =
                    fixture.engine()
                            .start(fixture.request(hangingComet(fixture)), new RecordingListener());
            ProcessHandle child = handleOf(childLine.get(60, TimeUnit.SECONDS), "child ");
            ProcessHandle parent = handleOf(parentLine.get(60, TimeUnit.SECONDS), "pid ");
            started.add(child);
            started.add(parent);
            assertTrue(parent.descendants().anyMatch(h -> h.pid() == child.pid()));

            // The control: nothing cancels, so neither exits within the window.
            assertThrows(
                    TimeoutException.class,
                    () ->
                            CompletableFuture.anyOf(parent.onExit(), child.onExit())
                                    .get(2, TimeUnit.SECONDS));
            assertTrue(child.isAlive(), "the child survives when nothing cancels");
            assertTrue(parent.isAlive(), "the tool survives when nothing cancels");
            assertTrue(handle.await(java.time.Duration.ZERO).isEmpty(), "the run is still going");

            handle.cancel();
            assertEquals(AttemptOutcome.CANCELLED, awaitResult(handle).outcome());
            child.onExit().get(DEATH_BOUND_SECONDS, TimeUnit.SECONDS);
        } finally {
            for (ProcessHandle process : started) {
                process.destroyForcibly();
            }
        }
    }

    @Test
    void aJavaStepThatChecksForCancellationEndsCancelled()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            CountDownLatch release = new CountDownLatch(1);
            Map<EngineStep, StepAction> actions = new EnumMap<>(fixture.standardActions());
            actions.put(
                    EngineStep.MERGE_PIN,
                    FakeStep.doing(
                            fixture.mergeDeclaration(),
                            context -> {
                                assertTrue(release.await(60, TimeUnit.SECONDS));
                                if (context.isCancellationRequested()) {
                                    throw new StepFailedException("merge stopped on request");
                                }
                            }));
            RecordingListener listener = new RecordingListener();
            RunHandle handle = fixture.engine().start(fixture.request(actions), listener);
            listener.when(EngineStep.MERGE_PIN, StepState.RUNNING).get(60, TimeUnit.SECONDS);
            handle.cancel();
            listener.when(EngineStep.MERGE_PIN, StepState.CANCEL_REQUESTED)
                    .get(60, TimeUnit.SECONDS);
            release.countDown();
            RunResult result = awaitResult(handle);
            assertEquals(StepState.CANCELLED, result.states().get(EngineStep.MERGE_PIN));
            assertEquals(
                    Map.of(EngineStep.MERGE_PIN, "merge stopped on request"), result.failures());
            assertEquals(
                    StepState.NOT_STARTED, result.states().get(EngineStep.FINALISE_PROVENANCE));
            assertEquals(RunState.CANCELLED, result.runState());
        }
    }

    @Test
    void aStepThatFinishesBeforeTheCancellationReachesItSucceedsAndTheNextStepIsCancelled()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            CountDownLatch release = new CountDownLatch(1);
            Map<EngineStep, StepAction> actions = new EnumMap<>(fixture.standardActions());
            FakeStep.Body merge = fixture.writingMergedPin();
            actions.put(
                    EngineStep.MERGE_PIN,
                    FakeStep.doing(
                            fixture.mergeDeclaration(),
                            context -> {
                                assertTrue(release.await(60, TimeUnit.SECONDS));
                                merge.run(context);
                            }));
            FakeStep finalise = FakeStep.nothing();
            actions.put(EngineStep.FINALISE_PROVENANCE, finalise);
            RecordingListener listener = new RecordingListener();
            RunHandle handle = fixture.engine().start(fixture.request(actions), listener);
            listener.when(EngineStep.MERGE_PIN, StepState.RUNNING).get(60, TimeUnit.SECONDS);
            handle.cancel();
            listener.when(EngineStep.MERGE_PIN, StepState.CANCEL_REQUESTED)
                    .get(60, TimeUnit.SECONDS);
            release.countDown();
            RunResult result = awaitResult(handle);
            assertEquals(
                    List.of(
                            StepState.NOT_STARTED,
                            StepState.READY,
                            StepState.RUNNING,
                            StepState.CANCEL_REQUESTED,
                            StepState.SUCCEEDED),
                    listener.path(EngineStep.MERGE_PIN));
            assertEquals(
                    List.of(StepState.NOT_STARTED, StepState.CANCELLED),
                    listener.path(EngineStep.FINALISE_PROVENANCE));
            assertEquals(0, finalise.executions());
            assertEquals(
                    Map.of(
                            EngineStep.FINALISE_PROVENANCE,
                            "the run was cancelled before finalise-provenance started"),
                    result.failures());
            assertEquals(RunState.CANCELLED, result.runState());
            assertEquals(AttemptOutcome.CANCELLED, result.outcome());
            ProvenanceManifest manifest =
                    ManifestReader.readFrom(fixture.layout().provenanceJsonFile());
            assertEquals(
                    ProvenanceStatus.COMPLETED,
                    recordOf(manifest, fixture.layout().mergedPinFile()).status(),
                    "the merge finished, so its output is complete");
            assertEquals(ProvenanceStatus.CANCELLED, manifest.run().status());
        }
    }

    @Test
    void cancellingAStepWhileItValidatesStopsItBeforeItsWork()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            CountDownLatch release = new CountDownLatch(1);
            Map<EngineStep, StepAction> actions = new EnumMap<>(fixture.standardActions());
            FakeStep validate =
                    new FakeStep(
                            StepDeclaration.NOTHING,
                            true,
                            context -> assertTrue(release.await(60, TimeUnit.SECONDS)),
                            context -> {});
            actions.put(EngineStep.VALIDATE_CONFIGURATION, validate);
            RecordingListener listener = new RecordingListener();
            RunHandle handle = fixture.engine().start(fixture.request(actions), listener);
            listener.when(EngineStep.VALIDATE_CONFIGURATION, StepState.VALIDATING)
                    .get(60, TimeUnit.SECONDS);
            handle.cancel();
            listener.when(EngineStep.VALIDATE_CONFIGURATION, StepState.CANCEL_REQUESTED)
                    .get(60, TimeUnit.SECONDS);
            release.countDown();
            RunResult result = awaitResult(handle);
            assertEquals(
                    List.of(
                            StepState.NOT_STARTED,
                            StepState.VALIDATING,
                            StepState.CANCEL_REQUESTED,
                            StepState.CANCELLED),
                    listener.path(EngineStep.VALIDATE_CONFIGURATION));
            assertEquals(1, validate.validations());
            assertEquals(0, validate.executions(), "a cancelled step never starts its work");
            assertEquals(
                    "the run was cancelled before validate-configuration started its work",
                    result.failures().get(EngineStep.VALIDATE_CONFIGURATION));
            assertEquals(RunState.CANCELLED, result.runState());
        }
    }

    private static Map<EngineStep, StepAction> hangingComet(EngineFixture fixture) {
        Map<EngineStep, StepAction> actions = new EnumMap<>(fixture.standardActions());
        actions.put(
                EngineStep.RUN_COMET,
                FakeStep.doing(
                        fixture.cometDeclaration(),
                        context ->
                                context.invoke(
                                        fixture.fake(
                                                "comet-01",
                                                "hang-with-child",
                                                fixture.records().toString(),
                                                "tool",
                                                fixture.pepXml(1).toString()))));
        return actions;
    }

    private static ProcessHandle handleOf(String line, String prefix) {
        long pid = Long.parseLong(line.substring(prefix.length()).trim());
        return ProcessHandle.of(pid)
                .orElseThrow(() -> new AssertionError("no process " + pid + " from " + line));
    }

    private static FileRecord recordOf(ProvenanceManifest manifest, Path path) {
        for (FileRecord file : manifest.files()) {
            if (file.path().equals(path)) {
                return file;
            }
        }
        throw new AssertionError(path + " is not recorded");
    }
}
