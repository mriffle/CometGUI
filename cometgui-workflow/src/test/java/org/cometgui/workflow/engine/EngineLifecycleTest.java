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
import static org.cometgui.workflow.engine.EngineAssertions.sha256;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.provenance.manifest.FileDirection;
import org.cometgui.provenance.manifest.FileRecord;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.RunState;
import org.cometgui.workflow.state.StepState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** How a run starts and ends: its threads, a run with nothing to do, and odd actions. */
class EngineLifecycleTest {

    /** A failure bound for an engine thread to end once its run is over. */
    private static final long THREAD_END_MILLIS = 30_000L;

    @TempDir private Path tmp;

    @Test
    void aRetryWithNothingToDoFinishesAtOnceWithEveryStepSkipped()
            throws IOException, InterruptedException, ReuseRefusedException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            RunRequest request = fixture.request(fixture.standardActions());
            assertEquals(
                    AttemptOutcome.SUCCEEDED,
                    fixture.engine().start(request, new RecordingListener()).await().outcome());
            RecordingListener listener = new RecordingListener();
            RunHandle second = fixture.engine().start(request, listener);
            RunResult result = second.await();
            assertEquals(2, result.attempt());
            assertEquals(AttemptOutcome.SUCCEEDED, result.outcome());
            assertEquals(RunState.SUCCEEDED, result.runState());
            for (EngineStep step : EngineFixture.PLAN.steps()) {
                assertEquals(StepState.SKIPPED, result.states().get(step), step.id());
                assertEquals(
                        List.of(StepState.NOT_STARTED, StepState.SKIPPED),
                        listener.path(step),
                        step.id());
            }
            assertEquals(
                    Set.of(),
                    fixture.store()
                            .read(fixture.layout())
                            .attempts()
                            .get(1)
                            .succeededSteps()
                            .keySet(),
                    "a skipped step records no new success");
        }
    }

    @Test
    void theEnginesThreadsAreDaemonsAndEndWithTheRun()
            throws IOException, InterruptedException, ReuseRefusedException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            List<Boolean> workerDaemons = new ArrayList<>();
            Map<EngineStep, StepAction> actions = new EnumMap<>(fixture.standardActions());
            actions.put(
                    EngineStep.RESOLVE_COMET,
                    FakeStep.doing(
                            StepDeclaration.NOTHING,
                            context -> {
                                synchronized (workerDaemons) {
                                    workerDaemons.add(Thread.currentThread().isDaemon());
                                }
                            }));
            List<Boolean> listenerDaemons = new ArrayList<>();
            StepStateListener listener =
                    transition -> {
                        synchronized (listenerDaemons) {
                            listenerDaemons.add(Thread.currentThread().isDaemon());
                        }
                    };
            awaitResult(fixture.engine().start(fixture.request(actions), listener));
            assertEquals(List.of(true), workerDaemons, "steps run on daemon threads");
            synchronized (listenerDaemons) {
                assertFalse(listenerDaemons.isEmpty());
                assertFalse(listenerDaemons.contains(false), "callbacks arrive on a daemon thread");
            }
            List<Thread> engineThreads = new ArrayList<>();
            for (Thread thread : Thread.getAllStackTraces().keySet()) {
                String name = thread.getName();
                if (name.startsWith("cometgui-step-") || name.startsWith("cometgui-run-events-")) {
                    engineThreads.add(thread);
                }
            }
            for (Thread thread : engineThreads) {
                thread.join(THREAD_END_MILLIS);
                assertFalse(thread.isAlive(), thread.getName() + " outlived its run");
            }
        }
    }

    @Test
    void anActionThatDoesNotSayItValidatesSkipsValidating()
            throws IOException, InterruptedException, ReuseRefusedException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            StepAction plain =
                    new StepAction() {
                        @Override
                        public StepDeclaration declaration() {
                            return StepDeclaration.NOTHING;
                        }

                        @Override
                        public void execute(StepContext context) {
                            // nothing to do
                        }
                    };
            assertFalse(plain.validates());
            Map<EngineStep, StepAction> actions = new EnumMap<>(fixture.standardActions());
            actions.put(EngineStep.VALIDATE_CONFIGURATION, plain);
            RecordingListener listener = new RecordingListener();
            awaitResult(fixture.engine().start(fixture.request(actions), listener));
            assertEquals(
                    List.of(
                            StepState.NOT_STARTED,
                            StepState.READY,
                            StepState.RUNNING,
                            StepState.SUCCEEDED),
                    listener.path(EngineStep.VALIDATE_CONFIGURATION));
        }
    }

    @Test
    void anInterruptedActionFailsItsStepAndTheStepIsStillRecorded()
            throws IOException, InterruptedException, ReuseRefusedException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            Map<EngineStep, StepAction> actions = new EnumMap<>(fixture.standardActions());
            actions.put(
                    EngineStep.MERGE_PIN,
                    FakeStep.doing(
                            fixture.mergeDeclaration(),
                            context -> {
                                Thread.currentThread().interrupt();
                                throw new InterruptedException("stop");
                            }));
            RunResult result =
                    awaitResult(
                            fixture.engine()
                                    .start(fixture.request(actions), new RecordingListener()));
            assertEquals(
                    Map.of(EngineStep.MERGE_PIN, "merge-pin was interrupted"), result.failures());
            assertEquals(
                    List.of(),
                    result.finalisationErrors(),
                    "the worker could still hash the step's files and record the run");
            assertTrue(
                    WorkflowEngineRunTest.describe(result.manifest().files())
                            .contains("input pin " + fixture.pin(1) + " completed"));
        }
    }

    @Test
    void anOutputIsRecordedByItsBytesNeverByACachedDigest()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    ExecutionException,
                    TimeoutException {
        StaleableHasher delegate = new StaleableHasher();
        CachingHashService cache = new CachingHashService(delegate);
        try (EngineFixture fixture = EngineFixture.create(tmp, 1, cache)) {
            Path params = fixture.layout().cometParamsFile();
            EngineAssertions.awaitSettled(params);
            FileHashes stale = new FileHashes("a".repeat(32), "b".repeat(64));
            delegate.lieAbout(params, stale);
            assertEquals(stale, cache.hash(params));
            delegate.stopLying();
            long hits = cache.hitCount();
            assertEquals(stale, cache.hash(params), "the cache holds a stale entry");
            assertEquals(hits + 1, cache.hitCount());

            RunResult result =
                    awaitResult(
                            fixture.engine()
                                    .start(
                                            fixture.request(fixture.standardActions()),
                                            new RecordingListener()));
            FileRecord output = null;
            for (FileRecord file : result.manifest().files()) {
                if (file.direction() == FileDirection.OUTPUT && file.path().equals(params)) {
                    output = file;
                }
            }
            assertEquals(sha256(params), output.hashes().sha256());
            assertNotEquals(stale.sha256(), output.hashes().sha256());
        }
    }
}
