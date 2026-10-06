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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.workflow.state.EngineStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every way a recorded result can fail revalidation, each produced on disk by a real attempt, and
 * the exact words each one is reported with.
 */
class ReuseCheckTest {

    @TempDir private Path tmp;

    @Test
    void aRecordedOutputThatNoLongerExistsIsRefused()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            Map<EngineStep, StepAction> actions = failingFirstMerge(fixture);
            awaitResult(fixture.engine().start(fixture.request(actions), new RecordingListener()));
            String recorded = sha256(fixture.pepXml(1));
            Files.delete(fixture.pepXml(1));
            ReuseCheck check = fixture.engine().checkReuse(fixture.request(actions));
            assertEquals(
                    List.of(
                            fixture.pepXml(1)
                                    + " (output file, role pepxml, of step run-comet) no longer"
                                    + " exists; recorded SHA-256 "
                                    + recorded),
                    descriptions(check));
            assertEquals(Optional.of(recorded), check.mismatches().get(0).recordedSha256());
            assertEquals(Optional.empty(), check.mismatches().get(0).currentSha256());
        }
    }

    @Test
    void aDeclaredFileTheManifestDoesNotRecordIsRefused()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            Map<EngineStep, StepAction> actions = failingFirstMerge(fixture);
            awaitResult(fixture.engine().start(fixture.request(actions), new RecordingListener()));
            Path extra = fixture.layout().cometOutputDirectory().resolve("extra.txt");
            Files.writeString(extra, "extra", StandardCharsets.UTF_8);
            List<DeclaredFile> files = new ArrayList<>(fixture.cometDeclaration().files());
            files.add(DeclaredFile.output("extra", extra));
            List<String> ids = new ArrayList<>(fixture.cometDeclaration().invocationIds());
            ids.add("comet-09");
            actions.put(
                    EngineStep.RUN_COMET,
                    FakeStep.doing(new StepDeclaration(files, ids), context -> {}));
            ReuseCheck check = fixture.engine().checkReuse(fixture.request(actions));
            assertEquals(
                    List.of(
                            extra
                                    + " (output file, role extra, of step run-comet) is not in the"
                                    + " recorded manifest",
                            "invocation comet-09 of step run-comet is not recorded as completed"),
                    descriptions(check));
            assertEquals(Set.of(EngineStep.RUN_COMET), check.offeredForced());
        }
    }

    @Test
    void aStepWhoseReExecutionFailedIsNotReusedFromItsEarlierSuccess()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            // Attempt 1 succeeds; attempt 2 forces merge-pin, which fails having written part of
            // its output. Attempt 1's fingerprint for merge-pin is still on record, so the preview
            // alone would reuse it; the record of attempt 2 must stop that.
            AtomicBoolean failMerge = new AtomicBoolean(false);
            Map<EngineStep, StepAction> actions = new EnumMap<>(fixture.standardActions());
            FakeStep.Body merge = fixture.writingMergedPin();
            actions.put(
                    EngineStep.MERGE_PIN,
                    FakeStep.doing(
                            fixture.mergeDeclaration(),
                            context -> {
                                if (failMerge.get()) {
                                    Files.writeString(
                                            fixture.layout().mergedPinFile(),
                                            "torn",
                                            StandardCharsets.UTF_8);
                                    throw new StepFailedException("the merge broke half way");
                                }
                                merge.run(context);
                            }));
            assertEquals(
                    AttemptOutcome.SUCCEEDED,
                    awaitResult(
                                    fixture.engine()
                                            .start(
                                                    fixture.request(actions),
                                                    new RecordingListener()))
                            .outcome());
            failMerge.set(true);
            assertEquals(
                    AttemptOutcome.FAILED,
                    awaitResult(
                                    fixture.engine()
                                            .start(
                                                    fixture.request(actions)
                                                            .withForced(
                                                                    Set.of(EngineStep.MERGE_PIN)),
                                                    new RecordingListener()))
                            .outcome());
            ReuseCheck check = fixture.engine().checkReuse(fixture.request(actions));
            // Every result step has a recorded success from attempt 1, so the preview alone would
            // reuse them all.
            assertEquals(
                    Set.of(
                            EngineStep.SERIALISE_COMET_PARAMS,
                            EngineStep.RUN_COMET,
                            EngineStep.VALIDATE_COMET_OUTPUTS,
                            EngineStep.MERGE_PIN,
                            EngineStep.FINALISE_PROVENANCE),
                    check.preview().reused());
            assertEquals(
                    List.of(
                            fixture.layout().mergedPinFile()
                                    + " (output file, role merged-pin, of step merge-pin) is"
                                    + " recorded as incomplete"),
                    descriptions(check));
            assertEquals(
                    Set.of(EngineStep.MERGE_PIN, EngineStep.FINALISE_PROVENANCE),
                    check.offered().orElseThrow().reExecuted());
        }
    }

    @Test
    void withoutAReadableManifestNothingCanBeReused()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            Map<EngineStep, StepAction> actions = failingFirstMerge(fixture);
            awaitResult(fixture.engine().start(fixture.request(actions), new RecordingListener()));
            Path manifest = fixture.layout().provenanceJsonFile();
            Files.writeString(manifest, "{ not a manifest", StandardCharsets.UTF_8);
            List<String> expected =
                    List.of(
                            "step serialise-comet-params cannot be checked: there is no readable"
                                    + " recorded manifest at "
                                    + manifest,
                            "step run-comet cannot be checked: there is no readable recorded"
                                    + " manifest at "
                                    + manifest,
                            "step validate-comet-outputs cannot be checked: there is no readable"
                                    + " recorded manifest at "
                                    + manifest);
            assertEquals(
                    expected, descriptions(fixture.engine().checkReuse(fixture.request(actions))));
            Files.delete(manifest);
            ReuseCheck missing = fixture.engine().checkReuse(fixture.request(actions));
            assertEquals(expected, descriptions(missing));
            assertFalse(missing.accepted());
            assertTrue(missing.verifiedFiles().isEmpty());
            assertTrue(missing.carriedTools().isEmpty());
        }
    }

    @Test
    void aFirstAttemptReusesNothingAndSaysSo()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            ReuseCheck check =
                    fixture.engine().checkReuse(fixture.request(fixture.standardActions()));
            assertTrue(check.accepted());
            assertEquals("nothing recorded is reused", check.message());
            assertEquals(Set.of(), check.preview().reused());
            assertFalse(Files.exists(fixture.layout().provenanceJsonFile()));
        }
    }

    private static Map<EngineStep, StepAction> failingFirstMerge(EngineFixture fixture) {
        AtomicBoolean first = new AtomicBoolean(true);
        FakeStep.Body merge = fixture.writingMergedPin();
        Map<EngineStep, StepAction> actions = new EnumMap<>(fixture.standardActions());
        actions.put(
                EngineStep.MERGE_PIN,
                FakeStep.doing(
                        fixture.mergeDeclaration(),
                        context -> {
                            if (first.getAndSet(false)) {
                                throw new StepFailedException("the first merge fails");
                            }
                            merge.run(context);
                        }));
        return actions;
    }

    private static List<String> descriptions(ReuseCheck check) {
        List<String> described = new ArrayList<>();
        for (ReuseMismatch mismatch : check.mismatches()) {
            described.add(mismatch.describe());
        }
        return described;
    }
}
