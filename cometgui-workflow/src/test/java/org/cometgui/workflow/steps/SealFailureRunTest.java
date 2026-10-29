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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.run.RunAttempt;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.params.percolator.resolution.DownstreamStage;
import org.cometgui.params.percolator.resolution.PercolatorResolver;
import org.cometgui.provenance.events.ProvenanceEvent;
import org.cometgui.provenance.events.ProvenanceEventType;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.workflow.engine.ReuseRefusedException;
import org.cometgui.workflow.engine.RunResult;
import org.cometgui.workflow.engine.StepStateListener;
import org.cometgui.workflow.provenance.LoadedRun;
import org.cometgui.workflow.provenance.ProvenanceReader;
import org.cometgui.workflow.provenance.RecordState;
import org.cometgui.workflow.provenance.RunCondition;
import org.cometgui.workflow.provenance.RunListing;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.RunState;
import org.cometgui.workflow.state.StepState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Defect E12, repaired: <strong>a run whose provenance record cannot be written is never a
 * success</strong>. Each run here is let do all its work, and then -- after its last step has
 * recorded its success and before the engine seals the run ({@link SealTrigger}) -- part of its
 * directory is made unwritable, proved by a refused write first so that a test run as root fails
 * rather than passing.
 *
 * <ul>
 *   <li><strong>An ordinary run</strong>, the real Comet 2026.03.0 over the two K562 files and the
 *       real Percolator 3.07.1, whose {@code provenance/} becomes unwritable: it ends FAILED, its
 *       reason naming {@code provenance.json} and the error; {@code run.json} records it failed
 *       with that reason (schema version 3); the event log's last event says so; no {@code
 *       provenance.json} or {@code provenance.rst} exists; every step keeps the state it reached;
 *       and every output the tools wrote is still on disk, with the digest the run recorded when it
 *       hashed it and byte for byte what it was at the seal. The product's own reader then lists it
 *       as failed and unsealed, and loads it as a failed run with no sealed record.
 *   <li><strong>The Percolator compatible rerun</strong>, the second path that finalises a run: a
 *       sealed real search with Percolator 3.09 is rerun with 3.07.1 for Limelight, and the rerun's
 *       {@code provenance/} becomes unwritable. The rerun fails the same way, and the run it was
 *       derived from is untouched and still listed as completed.
 *   <li><strong>{@code run.json} unwritable too</strong> (stand-in tools; the tools are not what is
 *       tested): the run's own directory and its {@code provenance/} both become unwritable. The
 *       result says the run failed and that its end could not be recorded; {@code run.json} is left
 *       exactly as the last step left it, the attempt running; and a reopened project, read by the
 *       product's own reader, lists the run as <em>interrupted</em> -- never as completed.
 * </ul>
 *
 * <p>Files read outside this module: {@code scratch/phase05/artefacts} (both binaries), {@code
 * scratch/fixture} (both K562 mzML and the proteome), and {@code manifests/tools.json}, as {@link
 * RealComet} and {@link RealPercolator} document -- exactly as {@code RealPercolatorRerunTest}.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "only the linux/x86-64 Comet and Percolator binaries have ever been executed in"
                        + " this project")
class SealFailureRunTest {

    private static final String DENIED = "permission was denied (";

    /** The real tools, inputs and a project whose engine reads a {@link SealTrigger}. */
    private record Staged(
            Path comet,
            List<Path> spectra,
            Path fasta,
            ToolOffer offer3071,
            ToolOffer offer309,
            SealTrigger trigger,
            RealProject project) {}

    private static Staged stage(Path directory) throws IOException {
        Path root = directory.toRealPath();
        Path comet = RealComet.stageComet(RealComet.NEWER, root.resolve("bin/comet"));
        Path inputs = Files.createDirectories(root.resolve("inputs"));
        List<Path> spectra = RealComet.spectra(inputs);
        Path fasta = RealComet.subset(inputs.resolve("subset.fasta"));
        Path percolator3071 = RealPercolator.stage3071(root.resolve("bin/percolator-3.07.1"));
        Path percolator309 = RealPercolator.wrapper309();
        ToolOffer offer3071 =
                RealPercolator.offer(
                        "3.07.1",
                        ToolOrigin.MANAGED,
                        percolator3071,
                        RealPercolator.probe("3.07.1", percolator3071),
                        RealPercolator.ADVISORIES_3071);
        ToolOffer offer309 =
                RealPercolator.offer(
                        "3.09",
                        ToolOrigin.LOCAL,
                        percolator309,
                        RealPercolator.probe("3.09", percolator309),
                        List.of());
        SealTrigger trigger = new SealTrigger();
        return new Staged(
                comet,
                spectra,
                fasta,
                offer3071,
                offer309,
                trigger,
                RealProject.create(root.resolve("project"), trigger));
    }

    private static SearchRequest search(
            Staged staged, ToolOffer percolator, Set<DownstreamStage> on) throws IOException {
        return new SearchRequest(
                        RealComet.model(
                                RealComet.NEWER,
                                staged.fasta(),
                                DecoySource.COMET_INTERNAL_CONCATENATED,
                                4),
                        staged.spectra(),
                        RealComet.selection(RealComet.NEWER, staged.comet()),
                        IndexMode.NONE)
                .withPercolator(choice(staged, percolator, on));
    }

    private static PercolatorChoice choice(
            Staged staged, ToolOffer selected, Set<DownstreamStage> stages) throws IOException {
        Path executable = selected.installedPath().orElseThrow();
        return new PercolatorChoice(
                new PercolatorSelection(selected, executable, RealComet.sha256(executable)),
                PercolatorSettings.defaults(),
                stages,
                PercolatorResolver.resolve(List.of(staged.offer309(), staged.offer3071()), stages));
    }

    private static ProvenanceReader reader() {
        return new ProvenanceReader(
                SecretRedactor::patternsOnly, new CachingHashService(new StreamingHashService()));
    }

    @Test
    @DisplayName(
            "the real Comet and Percolator run, provenance/ unwritable before the seal: FAILED with"
                    + " the reason in the result, run.json and the event log; no record; every"
                    + " output intact; read back as failed and unsealed")
    void anOrdinaryRunWhoseRecordCannotBeWritten(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Staged staged = stage(directory);
        try (RealProject project = staged.project()) {
            PreparedRun prepared =
                    project.prepare(
                            search(
                                    staged,
                                    staged.offer3071(),
                                    EnumSet.noneOf(DownstreamStage.class)));
            RunLayout layout = prepared.layout();
            AtomicReference<Map<String, String>> atSeal = new AtomicReference<>();
            staged.trigger()
                    .arm(
                            layout.runFile(),
                            () -> {
                                atSeal.set(outputs(layout));
                                RealComet.makeReadOnly(layout.provenanceDirectory());
                            });
            RunResult result;
            try {
                result = project.run(prepared);
            } finally {
                RealComet.makeWritable(layout.provenanceDirectory());
            }
            assertTrue(staged.trigger().fired(), "the directory was made unwritable");
            assertEquals(Optional.empty(), staged.trigger().failure());
            assertFailedForItsRecord(layout, result, project);
            assertOutputsIntact(layout, atSeal.get());

            List<RunListing> listed = reader().list(project.project(), Set.of());
            LoadedRun loaded = reader().load(layout);
            assertAll(
                    "read back by the product's own reader",
                    () -> assertEquals(1, listed.size()),
                    () -> assertEquals(RunCondition.FAILED, listed.get(0).condition()),
                    () -> assertFalse(listed.get(0).sealedRecord()),
                    () -> assertEquals(RecordState.NO_SEALED_RECORD, loaded.state()),
                    () -> assertEquals("failed", loaded.view().summary().status()),
                    () ->
                            assertTrue(
                                    loaded.view().warnings().stream()
                                            .anyMatch(
                                                    warning ->
                                                            warning.message()
                                                                    .startsWith(
                                                                            "this run has no sealed"
                                                                                    + " provenance"
                                                                                    + " record")),
                                    () ->
                                            "the missing record is warned of: "
                                                    + loaded.view().warnings()));
        }
    }

    @Test
    @DisplayName(
            "the Percolator compatible rerun, its provenance/ unwritable before the seal: the rerun"
                    + " FAILED with the reason everywhere, the run it came from untouched and still"
                    + " completed")
    void aPercolatorRerunWhoseRecordCannotBeWritten(@TempDir Path directory)
            throws IOException,
                    InterruptedException,
                    RunBlockedException,
                    ReuseRefusedException,
                    RerunRefusedException {
        Staged staged = stage(directory);
        try (RealProject project = staged.project()) {
            PreparedRun original =
                    project.prepare(
                            search(
                                    staged,
                                    staged.offer309(),
                                    EnumSet.noneOf(DownstreamStage.class)));
            RunResult originalResult = project.run(original);
            assertEquals(
                    AttemptOutcome.SUCCEEDED,
                    originalResult.outcome(),
                    () -> "the source run must succeed: " + originalResult.failures());
            Map<String, String> sourceBefore = RealComet.snapshot(original.layout().root());

            PercolatorRerun action = new PercolatorRerun(project.hashes());
            DerivedRun rerun =
                    action.prepare(
                            project.store(),
                            project.lock(),
                            original.layout(),
                            choice(
                                    staged,
                                    staged.offer3071(),
                                    EnumSet.of(DownstreamStage.LIMELIGHT_CONVERSION)),
                            RealProject.application());
            RunLayout layout = rerun.layout();
            AtomicReference<Map<String, String>> atSeal = new AtomicReference<>();
            staged.trigger()
                    .arm(
                            layout.runFile(),
                            () -> {
                                atSeal.set(outputs(layout));
                                RealComet.makeReadOnly(layout.provenanceDirectory());
                            });
            RunResult result;
            try {
                result =
                        action.start(project.engine(), rerun, StepStateListener.NONE)
                                .await(RealProject.RUN_BOUND)
                                .orElseThrow(
                                        () ->
                                                new AssertionError(
                                                        "the rerun did not finish within "
                                                                + RealProject.RUN_BOUND));
            } finally {
                RealComet.makeWritable(layout.provenanceDirectory());
            }
            RunResult ended = result;
            assertTrue(staged.trigger().fired(), "the rerun's directory was made unwritable");
            assertEquals(Optional.empty(), staged.trigger().failure());
            assertFailedForItsRecord(layout, ended, project);
            assertOutputsIntact(layout, atSeal.get());
            List<RunListing> listed = reader().list(project.project(), Set.of());
            assertAll(
                    "the run it was derived from",
                    () ->
                            assertEquals(
                                    List.of(),
                                    RealComet.differences(
                                            sourceBefore,
                                            RealComet.snapshot(original.layout().root())),
                                    "not one byte, mtime or entry of the source changed"),
                    () ->
                            assertEquals(
                                    List.of(
                                            "run-0001 completed sealed",
                                            "run-0002 failed unsealed"),
                                    describe(listed)));
        }
    }

    @Test
    @DisplayName(
            "run.json unwritable too: the result says failed and that its end was not recorded;"
                    + " run.json is as the last step left it; reopened, the run is interrupted,"
                    + " never completed")
    void runJsonUnwritableToo(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        FakeSearch fake = FakeSearch.create(directory.toRealPath());
        StandInTools.comet(fake.executable());
        Path percolator =
                FakePercolator.write(
                        fake.root().resolve("bin/percolator"), FakePercolator.Behaviour.normal());
        SealTrigger trigger = new SealTrigger();
        RunLayout layout;
        AtomicReference<byte[]> atSeal = new AtomicReference<>();
        RunResult result;
        try (RealProject project = RealProject.create(fake.project(), trigger)) {
            PreparedRun prepared = project.prepare(StandInTools.request(fake, percolator));
            layout = prepared.layout();
            RunLayout run = layout;
            trigger.arm(
                    run.runFile(),
                    () -> {
                        atSeal.set(Files.readAllBytes(run.runFile()));
                        RealComet.makeReadOnly(run.provenanceDirectory());
                        RealComet.makeReadOnly(run.root());
                    });
            try {
                result = project.run(prepared);
            } finally {
                RealComet.makeWritable(layout.root());
                RealComet.makeWritable(layout.provenanceDirectory());
            }
        }
        assertTrue(trigger.fired());
        assertEquals(Optional.empty(), trigger.failure());
        RunResult ended = result;
        RunLayout run = layout;
        String reason = ended.provenanceFailure().orElse("");
        List<ProvenanceEvent> events = RunEvidence.events(run);
        ProvenanceEvent last = events.get(events.size() - 1);
        assertAll(
                "the run ended",
                () -> assertEquals(AttemptOutcome.FAILED, ended.outcome()),
                () -> assertEquals(RunState.FAILED, ended.runState()),
                () ->
                        assertTrue(
                                reason.startsWith(
                                        "its provenance record "
                                                + run.provenanceJsonFile()
                                                + " could not be written: "
                                                + DENIED),
                                reason),
                () -> assertEquals(1, ended.finalisationErrors().size()),
                () ->
                        assertTrue(
                                ended.finalisationErrors()
                                        .get(0)
                                        .startsWith(
                                                "could not record the attempt's end:"
                                                        + " java.nio.file.AccessDeniedException: "
                                                        + run.runFile()
                                                        + ".tmp-"),
                                () ->
                                        "the unrecorded end is stated: "
                                                + ended.finalisationErrors()),
                () ->
                        assertArrayEquals(
                                atSeal.get(),
                                Files.readAllBytes(run.runFile()),
                                "run.json is exactly as the last step left it"),
                () -> assertEquals(ProvenanceEventType.RUN_FINISHED, last.type()),
                () -> assertEquals("failed", last.payload().get(ProvenanceEvent.STATUS_KEY)),
                () -> assertEquals(reason, last.payload().get(ProvenanceEvent.MESSAGE_KEY)),
                () -> assertFalse(Files.exists(run.provenanceJsonFile())));

        try (RealProject reopened = RealProject.open(fake.project(), "after")) {
            List<RunListing> listed = reader().list(reopened.project(), Set.of());
            LoadedRun loaded = reader().load(run);
            RunAttempt attempt = reopened.store().read(run).attempts().get(0);
            assertAll(
                    "reopened",
                    () -> assertEquals(AttemptOutcome.RUNNING, attempt.outcome()),
                    () -> assertEquals(List.of("run-0001 interrupted unsealed"), describe(listed)),
                    () -> assertEquals(RecordState.NO_SEALED_RECORD, loaded.state()),
                    () ->
                            assertFalse(
                                    loaded.view().summary().status().equals("completed"),
                                    () -> "never completed: " + loaded.view().summary()));
        }
    }

    /** What every run here shows once its record could not be written. */
    private static void assertFailedForItsRecord(
            RunLayout layout, RunResult result, RealProject project) throws IOException {
        String reason = result.provenanceFailure().orElse("");
        String prefix =
                "its provenance record "
                        + layout.provenanceJsonFile()
                        + " could not be written: "
                        + DENIED
                        + layout.provenanceJsonFile()
                        + ".tmp-";
        RunAttempt attempt = project.store().read(layout).attempts().get(0);
        String runJson = Files.readString(layout.runFile());
        List<ProvenanceEvent> events = RunEvidence.events(layout);
        ProvenanceEvent last = events.get(events.size() - 1);
        assertAll(
                "a run whose record could not be written",
                () -> assertEquals(AttemptOutcome.FAILED, result.outcome()),
                () -> assertEquals(RunState.FAILED, result.runState()),
                () -> assertTrue(reason.startsWith(prefix), reason),
                () ->
                        assertTrue(
                                result.states().values().stream()
                                        .allMatch(state -> state == StepState.SUCCEEDED),
                                () -> "every step keeps what it did: " + result.states()),
                () ->
                        assertEquals(
                                StepState.SUCCEEDED,
                                result.states().get(EngineStep.RUN_PERCOLATOR)),
                () -> assertEquals(Map.of(), result.failures()),
                () -> assertEquals(List.of(), result.finalisationErrors()),
                () -> assertEquals(AttemptOutcome.FAILED, attempt.outcome()),
                () -> assertEquals(Optional.of(reason), attempt.reason()),
                () ->
                        assertTrue(
                                runJson.startsWith("{\n  \"schemaVersion\": 3,\n"),
                                "run.json is written at version 3"),
                () -> assertEquals(ProvenanceEventType.RUN_FINISHED, last.type()),
                () -> assertEquals("failed", last.payload().get(ProvenanceEvent.STATUS_KEY)),
                () -> assertEquals(reason, last.payload().get(ProvenanceEvent.MESSAGE_KEY)),
                () -> assertFalse(Files.exists(layout.provenanceJsonFile())),
                () -> assertFalse(Files.exists(layout.provenanceRstFile())),
                () ->
                        assertEquals(
                                List.of("events.log"),
                                RunEvidence.listing(layout.provenanceDirectory()),
                                "and no temporary file is left"));
    }

    /**
     * Every output the run recorded hashing is on disk with that digest, and every file under
     * {@code outputs/} is byte for byte what it was at the seal.
     */
    private static void assertOutputsIntact(RunLayout layout, Map<String, String> atSeal)
            throws IOException {
        Map<String, String> recorded = new TreeMap<>();
        for (ProvenanceEvent event : RunEvidence.events(layout)) {
            if (event.type() == ProvenanceEventType.FILE_HASHED
                    && "output".equals(event.payload().get("direction"))) {
                recorded.put(
                        event.payload().get(ProvenanceEvent.FILE_PATH_KEY),
                        event.payload().get(ProvenanceEvent.FILE_SHA256_KEY));
            }
        }
        Map<String, String> now = new TreeMap<>();
        for (String path : recorded.keySet()) {
            Path file = Path.of(path);
            now.put(path, Files.isRegularFile(file) ? RealComet.sha256(file) : "missing");
        }
        Map<String, String> after = outputs(layout);
        assertAll(
                "the tools' work",
                () -> assertTrue(recorded.size() >= 3, () -> "outputs recorded: " + recorded),
                () -> assertEquals(recorded, now, "every recorded output, with its digest"),
                () -> assertEquals(atSeal, after, "outputs/ exactly as it was at the seal"));
    }

    /** Every regular file under the run's {@code outputs/}, with its size, time and SHA-256. */
    private static Map<String, String> outputs(RunLayout layout) throws IOException {
        return RealComet.snapshot(layout.outputsDirectory());
    }

    private static List<String> describe(List<RunListing> listed) {
        return listed.stream()
                .map(
                        run ->
                                run.runId().orElse("?")
                                        + " "
                                        + run.condition().word()
                                        + " "
                                        + (run.sealedRecord() ? "sealed" : "unsealed"))
                .sorted()
                .toList();
    }
}
