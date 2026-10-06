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
import static org.cometgui.workflow.engine.EngineAssertions.intactEvents;
import static org.cometgui.workflow.engine.EngineAssertions.stageEvents;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.HashService;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.RunDescriptor;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.provenance.manifest.FileRecord;
import org.cometgui.provenance.manifest.ManifestReader;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ToolRecord;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.StepState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Retry and prerequisite revalidation (R-RUN-02, P8-14, gate 8): an unchanged recorded result is
 * reused without being run again; a changed one refuses reuse, names the file and both hashes, and
 * offers the plan that runs its producer again; and the re-hash is proved not to come from the hash
 * cache.
 */
class RetryRevalidationTest {

    /** SHA-256 of {@code "spectra 2\n"}, the fixture's second spectrum file, by sha256sum. */
    private static final String SPECTRUM_2_SHA256 =
            "04d47d7667350ad6b622eebe691e5b7674c231b149c3fe8c078ab9bc84c3060d";

    /** MD5 of {@code "spectra 2\n"}, by md5sum. */
    private static final String SPECTRUM_2_MD5 = "29b9a3363a63935c390ecbb84aa5b8a7";

    private static final String CHANGED_CONTENT = "spectra 2 -- changed\n";

    /** SHA-256 of {@link #CHANGED_CONTENT}, by sha256sum. */
    private static final String CHANGED_SHA256 =
            "2fa06e8af0c0924f07f77f0ef62b681747cc169d7b692b0ff7aa13f86028796b";

    @TempDir private Path tmp;

    @Test
    void aRetryReusesUnchangedResultsWithoutRunningThemAgain()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 2)) {
            Attempts attempts = new Attempts(fixture);
            assertEquals(AttemptOutcome.FAILED, attempts.run().outcome());

            ReuseCheck check = fixture.engine().checkReuse(fixture.request(attempts.actions()));
            assertTrue(check.accepted(), check::message);
            assertEquals(List.of(), check.mismatches());
            assertEquals(Optional.empty(), check.offered());
            assertEquals(Set.of(), check.offeredForced());
            assertEquals(
                    Set.of(
                            EngineStep.SERIALISE_COMET_PARAMS,
                            EngineStep.RUN_COMET,
                            EngineStep.VALIDATE_COMET_OUTPUTS),
                    check.preview().reused());
            assertEquals(
                    Set.of(EngineStep.MERGE_PIN, EngineStep.FINALISE_PROVENANCE),
                    check.preview().executed());
            assertEquals(
                    "every recorded result to be reused still matches its record:"
                            + " serialise-comet-params, run-comet, validate-comet-outputs",
                    check.message());

            RecordingListener listener = new RecordingListener();
            RunResult second = attempts.run(listener);
            assertEquals(AttemptOutcome.SUCCEEDED, second.outcome(), second.failures().toString());
            assertEquals(2, second.attempt());
            assertEquals(1, attempts.comet().executions(), "run-comet was reused, not run again");
            assertEquals(
                    List.of(StepState.NOT_STARTED, StepState.SKIPPED),
                    listener.path(EngineStep.RUN_COMET));
            assertEquals(StepState.SKIPPED, second.states().get(EngineStep.HASH_INPUTS));
            assertEquals(StepState.SUCCEEDED, second.states().get(EngineStep.MERGE_PIN));

            // The reused step's invocations and files are carried into the new record.
            ProvenanceManifest manifest =
                    ManifestReader.readFrom(fixture.layout().provenanceJsonFile());
            List<Optional<String>> stages = new ArrayList<>();
            for (ToolRecord tool : manifest.tools()) {
                stages.add(tool.stageId());
            }
            assertEquals(List.of(Optional.of("comet-01"), Optional.of("comet-02")), stages);
            assertTrue(
                    WorkflowEngineRunTest.describe(manifest.files())
                            .contains("output pin " + fixture.pin(2) + " completed"));
            assertEquals("2", manifest.settings().get(WorkflowEngine.ATTEMPT_SETTING));

            RunDescriptor recorded = fixture.store().read(fixture.layout());
            assertEquals(2, recorded.attempts().size());
            assertEquals(AttemptOutcome.FAILED, recorded.attempts().get(0).outcome());
            assertEquals(AttemptOutcome.SUCCEEDED, recorded.attempts().get(1).outcome());
            assertEquals(
                    Set.of("merge-pin", "finalise-provenance"),
                    recorded.attempts().get(1).succeededSteps().keySet());
            assertEquals(
                    List.of(
                            "stage.started ready",
                            "stage.started running",
                            "stage.finished succeeded",
                            "stage.finished skipped"),
                    stageEvents(intactEvents(fixture.layout().eventLogFile()), "run-comet"),
                    "the event log holds both attempts");
        }
    }

    @Test
    void aRecordedInputThatChangedRefusesReuseNamingTheFileAndOffersToRunItsProducer()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 2)) {
            Attempts attempts = new Attempts(fixture);
            attempts.run();
            Path changed = fixture.spectra().get(1);
            Files.writeString(changed, CHANGED_CONTENT, StandardCharsets.UTF_8);

            RunRequest retry = fixture.request(attempts.actions());
            ReuseCheck check = fixture.engine().checkReuse(retry);
            assertFalse(check.accepted());
            assertEquals(
                    List.of(
                            new ReuseMismatch(
                                    EngineStep.RUN_COMET,
                                    ReuseMismatch.Kind.CHANGED,
                                    changed.toString(),
                                    "input file, role spectrum",
                                    Optional.of(SPECTRUM_2_SHA256),
                                    Optional.of(CHANGED_SHA256))),
                    check.mismatches());
            String expected =
                    "recorded results cannot be reused because they no longer match the record:\n"
                            + "- "
                            + changed
                            + " (input file, role spectrum, of step run-comet) has changed since"
                            + " it was recorded: recorded SHA-256 "
                            + SPECTRUM_2_SHA256
                            + ", now "
                            + CHANGED_SHA256
                            + "\nthese steps must run again: run-comet, validate-comet-outputs,"
                            + " merge-pin, finalise-provenance";
            assertEquals(expected, check.message());
            assertEquals(Set.of(EngineStep.RUN_COMET), check.offeredForced());
            assertEquals(
                    Set.of(
                            EngineStep.RUN_COMET,
                            EngineStep.VALIDATE_COMET_OUTPUTS,
                            EngineStep.MERGE_PIN,
                            EngineStep.FINALISE_PROVENANCE),
                    check.offered().orElseThrow().reExecuted());

            ReuseRefusedException refused =
                    assertThrows(
                            ReuseRefusedException.class,
                            () -> fixture.engine().start(retry, new RecordingListener()));
            assertEquals(expected, refused.getMessage());
            assertEquals(check.mismatches(), refused.check().mismatches());
            assertEquals(
                    1,
                    fixture.store().read(fixture.layout()).attempts().size(),
                    "a refused start records no attempt");

            // Accepting the offer re-executes the producer and records the file as it is now.
            RunResult accepted =
                    awaitResult(
                            fixture.engine()
                                    .start(
                                            retry.withForced(check.offeredForced()),
                                            new RecordingListener()));
            assertEquals(
                    AttemptOutcome.SUCCEEDED, accepted.outcome(), accepted.failures().toString());
            assertEquals(2, attempts.comet().executions(), "run-comet ran again");
            ProvenanceManifest manifest =
                    ManifestReader.readFrom(fixture.layout().provenanceJsonFile());
            FileRecord spectrum = null;
            for (FileRecord file : manifest.files()) {
                if (file.path().equals(changed)) {
                    spectrum = file;
                }
            }
            assertEquals(CHANGED_SHA256, spectrum.hashes().sha256());
        }
    }

    @Test
    void theReHashIsNotServedFromTheCacheEvenWhenTheCacheHoldsAStaleEntry()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        StaleableHasher delegate = new StaleableHasher();
        CachingHashService cache = new CachingHashService(delegate);
        try (EngineFixture fixture = EngineFixture.create(tmp, 2, cache)) {
            Attempts attempts = new Attempts(fixture);
            attempts.run();
            Path changed = fixture.spectra().get(1);
            Files.writeString(changed, CHANGED_CONTENT, StandardCharsets.UTF_8);
            awaitSettled(changed);

            // Prime the cache with the digest the file had BEFORE it changed: a stale entry for a
            // file whose every attribute the cache checks is now unchanged.
            FileHashes stale = new FileHashes(SPECTRUM_2_MD5, SPECTRUM_2_SHA256);
            delegate.lieAbout(changed, stale);
            assertEquals(stale, cache.hash(changed));
            delegate.stopLying();
            long hitsBefore = cache.hitCount();
            assertEquals(stale, cache.hash(changed), "the cache now serves the stale entry");
            assertEquals(hitsBefore + 1, cache.hitCount(), "...as a hit");

            long hitsBeforeCheck = cache.hitCount();
            ReuseCheck check = fixture.engine().checkReuse(fixture.request(attempts.actions()));
            assertEquals(
                    hitsBeforeCheck,
                    cache.hitCount(),
                    "revalidation asked the cache for nothing it could serve");
            assertFalse(
                    check.accepted(), "a stale cache entry must not make a changed file reusable");
            assertEquals(1, check.mismatches().size(), check::message);
            assertEquals(Optional.of(CHANGED_SHA256), check.mismatches().get(0).currentSha256());
            assertEquals(
                    Optional.of(SPECTRUM_2_SHA256), check.mismatches().get(0).recordedSha256());
        }
    }

    /**
     * Waits until the wall clock is past the second in which the file last changed, so that the
     * hash cache will remember it ({@code CachingHashService}'s "settled" rule). This waits on an
     * observable state -- the clock passing a computed instant -- not for a fixed time.
     */
    private static void awaitSettled(Path file) throws IOException {
        FileTime modified = Files.getLastModifiedTime(file);
        FileTime changed = (FileTime) Files.getAttribute(file, "unix:ctime");
        Instant latest =
                modified.toInstant().isAfter(changed.toInstant())
                        ? modified.toInstant()
                        : changed.toInstant();
        Instant settled = latest.truncatedTo(ChronoUnit.SECONDS).plusSeconds(1).plusMillis(20);
        while (Instant.now().isBefore(settled)) {
            LockSupport.parkUntil(settled.toEpochMilli());
        }
    }

    /** Two attempts' worth of actions: the first attempt's merge fails, every later one works. */
    private static final class Attempts {

        private final EngineFixture fixture;

        private final FakeStep comet;

        private final Map<EngineStep, StepAction> actions;

        Attempts(EngineFixture fixture) {
            this.fixture = fixture;
            this.comet = FakeStep.doing(fixture.cometDeclaration(), fixture.succeedingComet(1));
            AtomicBoolean firstMerge = new AtomicBoolean(true);
            FakeStep.Body merge = fixture.writingMergedPin();
            this.actions = new EnumMap<>(fixture.standardActions());
            actions.put(EngineStep.RUN_COMET, comet);
            actions.put(
                    EngineStep.MERGE_PIN,
                    FakeStep.doing(
                            fixture.mergeDeclaration(),
                            context -> {
                                if (firstMerge.getAndSet(false)) {
                                    throw new StepFailedException("the first merge fails");
                                }
                                merge.run(context);
                            }));
        }

        FakeStep comet() {
            return comet;
        }

        Map<EngineStep, StepAction> actions() {
            return actions;
        }

        RunResult run()
                throws IOException,
                        InterruptedException,
                        ReuseRefusedException,
                        StepFailedException,
                        ExecutionException,
                        TimeoutException {
            return run(new RecordingListener());
        }

        RunResult run(RecordingListener listener)
                throws IOException,
                        InterruptedException,
                        ReuseRefusedException,
                        StepFailedException,
                        ExecutionException,
                        TimeoutException {
            return awaitResult(fixture.engine().start(fixture.request(actions), listener));
        }
    }

    /** A hasher that can be told to return a stale digest for one file, standing in for a lie. */
    private static final class StaleableHasher implements HashService {

        private final StreamingHashService real = new StreamingHashService();

        private volatile Path liePath;

        private volatile FileHashes lie;

        void lieAbout(Path path, FileHashes hashes) {
            liePath = path;
            lie = hashes;
        }

        void stopLying() {
            lie = null;
        }

        @Override
        public FileHashes hash(Path path) throws IOException {
            FileHashes told = lie;
            if (told != null && path.equals(liePath)) {
                return told;
            }
            return real.hash(path);
        }
    }
}
