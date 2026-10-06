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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.provenance.manifest.ManifestReader;
import org.cometgui.provenance.manifest.ProvenanceStatus;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.StepState;
import org.cometgui.workflow.testing.Nulls;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the engine does when recording itself fails: a step whose success cannot be recorded is not
 * a success, and a run whose record cannot be finished still finishes and says what is missing.
 */
class EngineEdgeTest {

    @TempDir private Path tmp;

    @Test
    void aStepWhoseSuccessCannotBeRecordedFailsAndTheMissingEndIsReported()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            Map<EngineStep, StepAction> actions = new EnumMap<>(fixture.standardActions());
            // Releasing the project lock mid-run makes every later run.json write refuse.
            actions.put(
                    EngineStep.FINALISE_PROVENANCE,
                    FakeStep.doing(StepDeclaration.NOTHING, context -> fixture.lock().close()));
            RunResult result =
                    awaitResult(
                            fixture.engine()
                                    .start(fixture.request(actions), new RecordingListener()));
            assertEquals(StepState.FAILED, result.states().get(EngineStep.FINALISE_PROVENANCE));
            assertTrue(
                    result.failures()
                            .get(EngineStep.FINALISE_PROVENANCE)
                            .startsWith(
                                    "could not record that finalise-provenance succeeded:"
                                            + " java.lang.IllegalStateException"),
                    result.failures().toString());
            assertEquals(
                    1, result.finalisationErrors().size(), result.finalisationErrors()::toString);
            assertTrue(
                    result.finalisationErrors()
                            .get(0)
                            .startsWith("could not record the attempt's end:"));
            assertEquals(AttemptOutcome.FAILED, result.outcome());
            assertEquals(
                    ProvenanceStatus.FAILED,
                    ManifestReader.readFrom(fixture.layout().provenanceJsonFile()).run().status());
        }
    }

    @Test
    void aDeclaredFileThatCannotBeHashedFailsTheStep()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        Path[] refused = new Path[1];
        CachingHashService hashes =
                new CachingHashService(
                        path -> {
                            if (path.equals(refused[0])) {
                                throw new IOException("refused");
                            }
                            return new StreamingHashService().hash(path);
                        });
        try (EngineFixture fixture = EngineFixture.create(tmp, 1, hashes)) {
            refused[0] = fixture.layout().mergedPinFile();
            RunResult result =
                    awaitResult(
                            fixture.engine()
                                    .start(
                                            fixture.request(fixture.standardActions()),
                                            new RecordingListener()));
            String expected =
                    "could not hash "
                            + fixture.layout().mergedPinFile()
                            + ": java.io.IOException: refused";
            assertEquals(Map.of(EngineStep.MERGE_PIN, expected), result.failures());
            assertEquals(List.of(expected), result.finalisationErrors());
            assertFalse(
                    WorkflowEngineRunTest.describe(result.manifest().files()).stream()
                            .anyMatch(line -> line.contains("merged-pin")),
                    "a file that could not be hashed is not recorded");
        }
    }

    @Test
    void aFileThatVanishesAfterHashingIsReportedRatherThanRecorded()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        Path[] vanishing = new Path[1];
        CachingHashService hashes =
                new CachingHashService(
                        path -> {
                            var digests = new StreamingHashService().hash(path);
                            if (path.equals(vanishing[0])) {
                                Files.delete(path);
                            }
                            return digests;
                        });
        try (EngineFixture fixture = EngineFixture.create(tmp, 1, hashes)) {
            vanishing[0] = fixture.fasta();
            Map<EngineStep, StepAction> actions = new EnumMap<>(fixture.standardActions());
            actions.put(
                    EngineStep.HASH_INPUTS,
                    FakeStep.doing(
                            new StepDeclaration(
                                    List.of(DeclaredFile.input("fasta", fixture.fasta())),
                                    List.of()),
                            context -> {}));
            RunResult result =
                    awaitResult(
                            fixture.engine()
                                    .start(fixture.request(actions), new RecordingListener()));
            assertEquals(StepState.SUCCEEDED, result.states().get(EngineStep.HASH_INPUTS));
            assertEquals(1, result.finalisationErrors().size());
            assertTrue(
                    result.finalisationErrors()
                            .get(0)
                            .startsWith("could not describe " + fixture.fasta() + ": "),
                    result.finalisationErrors()::toString);
        }
    }

    @Test
    void anUnwritableManifestOrReportIsReportedAndTheRunStillFinishes()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            Files.createDirectory(fixture.layout().provenanceJsonFile());
            Files.createDirectory(fixture.layout().provenanceRstFile());
            RunResult result =
                    awaitResult(
                            fixture.engine()
                                    .start(
                                            fixture.request(fixture.standardActions()),
                                            new RecordingListener()));
            assertEquals(AttemptOutcome.SUCCEEDED, result.outcome());
            assertEquals(
                    2, result.finalisationErrors().size(), result.finalisationErrors()::toString);
            assertTrue(
                    result.finalisationErrors()
                            .get(0)
                            .startsWith("could not write provenance.json: "));
            assertTrue(
                    result.finalisationErrors()
                            .get(1)
                            .startsWith("could not write provenance.rst: "));
            assertEquals(
                    AttemptOutcome.SUCCEEDED,
                    fixture.store().read(fixture.layout()).attempts().get(0).outcome());
        }
    }

    @Test
    void anEventLogThatCannotBeOpenedStartsNothing()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            Files.createDirectory(fixture.layout().eventLogFile());
            FakeStep first = new FakeStep(StepDeclaration.NOTHING, true, c -> {}, c -> {});
            Map<EngineStep, StepAction> actions = new EnumMap<>(fixture.standardActions());
            actions.put(EngineStep.VALIDATE_CONFIGURATION, first);
            assertThrows(
                    IOException.class,
                    () ->
                            fixture.engine()
                                    .start(fixture.request(actions), new RecordingListener()));
            assertEquals(List.of(), fixture.store().read(fixture.layout()).attempts());
            assertEquals(0, first.validations());
        }
    }

    @Test
    void anAttemptThatCannotBeRecordedStartsNothing()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            fixture.lock().close();
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            fixture.engine()
                                    .start(
                                            fixture.request(fixture.standardActions()),
                                            new RecordingListener()));
            assertEquals(List.of(), fixture.store().read(fixture.layout()).attempts());
        }
    }

    @Test
    void aRunWhoseRecordCannotBeBuiltCompletesExceptionallyAndSaysSo()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            // The first reading is the attempt's start; every later one is an hour earlier, so
            // the run record would end before it began and cannot be built.
            Clock backwards = new BackwardsClock();
            EngineServices services =
                    new EngineServices(
                            new ProcessService(backwards),
                            backwards,
                            SecretRedactor.patternsOnly(),
                            fixture.sink(),
                            fixture.hashes(),
                            EngineFixture.CORES,
                            EngineFixture.CAP);
            RecordingListener listener = new RecordingListener();
            RunHandle handle =
                    new WorkflowEngine(services)
                            .start(fixture.request(fixture.standardActions()), listener);
            IllegalStateException broken = assertThrows(IllegalStateException.class, handle::await);
            assertEquals("the run could not be finalised", broken.getMessage());
            assertInstanceOf(IllegalArgumentException.class, broken.getCause());
            IllegalStateException again =
                    assertThrows(
                            IllegalStateException.class,
                            () -> handle.await(Duration.ofSeconds(60)));
            assertEquals("the run could not be finalised", again.getMessage());
            assertFalse(listener.finished().isDone(), "no result is delivered");
        }
    }

    @Test
    void theEngineRequiresAListenerAndADeclarationForEveryStep()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            assertEquals(
                    "listener",
                    assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            fixture.engine()
                                                    .start(
                                                            fixture.request(
                                                                    fixture.standardActions()),
                                                            Nulls.of(StepStateListener.class)))
                            .getMessage());
            Map<EngineStep, StepAction> actions = new EnumMap<>(fixture.standardActions());
            actions.put(EngineStep.MERGE_PIN, FakeStep.doing(null, context -> {}));
            assertEquals(
                    "the declaration of merge-pin",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> fixture.engine().checkReuse(fixture.request(actions)))
                            .getMessage());
            assertEquals(
                    "request",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> fixture.engine().checkReuse(Nulls.of(RunRequest.class)))
                            .getMessage());
        }
    }

    @Test
    void theHandleReportsTheAttemptAndPlan()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            RunHandle handle =
                    fixture.engine()
                            .start(
                                    fixture.request(fixture.standardActions()),
                                    new RecordingListener());
            assertEquals(1, handle.attempt());
            assertEquals(EngineFixture.PLAN, handle.plan());
            awaitResult(handle);
            assertEquals(EngineFixture.PLAN.steps(), List.copyOf(handle.states().keySet()));
            assertThrows(NullPointerException.class, () -> handle.await(Nulls.of(Duration.class)));
        }
    }

    /** Reads once at noon, then always an hour earlier. */
    private static final class BackwardsClock extends Clock {

        private final AtomicBoolean first = new AtomicBoolean(true);

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return first.getAndSet(false)
                    ? Instant.parse("2026-10-06T12:00:00Z")
                    : Instant.parse("2026-10-06T11:00:00Z");
        }
    }
}
