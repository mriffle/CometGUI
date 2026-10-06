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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.params.comet.writer.CanonicalParamsWriter;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.tools.api.ToolRunOutcome;
import org.cometgui.tools.api.ToolRunner;
import org.cometgui.tools.comet.CometIndexCommand;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.workflow.engine.ReuseRefusedException;
import org.cometgui.workflow.engine.RunRequest;
import org.cometgui.workflow.engine.RunResult;
import org.cometgui.workflow.engine.StepAction;
import org.cometgui.workflow.engine.StepContext;
import org.cometgui.workflow.engine.StepDeclaration;
import org.cometgui.workflow.engine.StepFailedException;
import org.cometgui.workflow.engine.StepStateListener;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.StepState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The search steps against the real binaries, beyond the gate items: separate decoys ({@code
 * decoy_search = 2}) and their decoy pepXML; a PIN with no decoy row refused naming the decoy
 * configuration ({@code R-DEC-04}); a retry that re-runs the search and replaces its outputs; and
 * {@code finalise-provenance} refusing a parameter file changed during the run.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "only the linux/x86-64 Comet binary has ever been executed in this project")
class RealStepTest {

    private record Staged(Path root, Path comet, List<Path> spectra, Path fasta) {}

    private static Staged stage(Path scratch) throws IOException {
        Path root = scratch.toRealPath();
        Path comet = RealComet.stageComet(RealComet.NEWER, root.resolve("bin/comet"));
        Path inputs = Files.createDirectories(root.resolve("inputs"));
        return new Staged(
                root,
                comet,
                RealComet.spectra(inputs),
                RealComet.subset(inputs.resolve("subset.fasta")));
    }

    private static SearchRequest request(Staged staged, DecoySource decoys) {
        return new SearchRequest(
                RealComet.model(RealComet.NEWER, staged.fasta(), decoys, 4),
                staged.spectra(),
                RealComet.selection(RealComet.NEWER, staged.comet()),
                IndexMode.NONE);
    }

    @Test
    @DisplayName(
            "decoy_search = 2: each file's separate decoy pepXML is written, declared and"
                    + " validated")
    void separateDecoys(@TempDir Path scratch)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Staged staged = stage(scratch);
        try (RealProject project = RealProject.create(staged.root().resolve("project"))) {
            PreparedRun prepared =
                    project.prepare(request(staged, DecoySource.COMET_INTERNAL_SEPARATE));
            RunResult result = project.run(prepared);
            assertEquals(
                    AttemptOutcome.SUCCEEDED, result.outcome(), () -> result.failures().toString());
            assertEquals(
                    List.of(
                            "k562_3.decoy.pep.xml",
                            "k562_3.pep.xml",
                            "k562_3.pin",
                            "k562_4.decoy.pep.xml",
                            "k562_4.pep.xml",
                            "k562_4.pin"),
                    RunEvidence.listing(prepared.layout().cometOutputDirectory()));
            Map<String, String> validated =
                    RunEvidence.finished(prepared.layout(), EngineStep.VALIDATE_COMET_OUTPUTS);
            RunEvidence.assertDetail(validated, "outputs.comet-01.targets", "3492");
            RunEvidence.assertDetail(validated, "outputs.comet-01.decoys", "3497");
            RunEvidence.assertDetail(validated, "outputs.comet-01.decoy-spectrum-queries", "728");
            RunEvidence.assertDetail(validated, "outputs.comet-02.targets", "2829");
            RunEvidence.assertDetail(validated, "outputs.comet-02.decoys", "2850");
            RunEvidence.assertDetail(validated, "outputs.comet-02.decoy-spectrum-queries", "607");
            long decoyRecords =
                    RunEvidence.manifest(prepared.layout()).files().stream()
                            .filter(file -> file.role().equals("decoy-pepxml"))
                            .count();
            assertEquals(
                    4,
                    decoyRecords,
                    "each decoy pepXML written by run-comet and read by validation");
        }
    }

    /** The real search, then every decoy row removed from the first PIN. */
    private record DropDecoys(StepAction search, Path pin) implements StepAction {

        @Override
        public StepDeclaration declaration() {
            return search.declaration();
        }

        @Override
        public void execute(StepContext context)
                throws StepFailedException, IOException, InterruptedException {
            search.execute(context);
            List<String> kept =
                    Files.readAllLines(pin, java.nio.charset.StandardCharsets.ISO_8859_1).stream()
                            .filter(line -> !line.split("\t", -1)[1].equals("-1"))
                            .toList();
            Files.write(
                    pin,
                    (String.join("\n", kept) + "\n")
                            .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        }
    }

    @Test
    @DisplayName(
            "R-DEC-04: a PIN with no decoy row fails validate-comet-outputs, naming the decoy"
                    + " configuration")
    void aPinWithoutDecoysFailsValidation(@TempDir Path scratch)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Staged staged = stage(scratch);
        try (RealProject project = RealProject.create(staged.root().resolve("project"))) {
            PreparedRun prepared =
                    project.prepare(request(staged, DecoySource.COMET_INTERNAL_CONCATENATED));
            Path pin = prepared.layout().pinFile("k562_3");
            Map<EngineStep, StepAction> actions = new EnumMap<>(prepared.actions());
            actions.put(
                    EngineStep.RUN_COMET, new DropDecoys(actions.get(EngineStep.RUN_COMET), pin));
            RunResult result = start(project, prepared, actions);
            assertEquals(AttemptOutcome.FAILED, result.outcome());
            assertEquals(StepState.FAILED, result.states().get(EngineStep.VALIDATE_COMET_OUTPUTS));
            assertEquals(StepState.NOT_STARTED, result.states().get(EngineStep.MERGE_PIN));
            assertEquals(
                    "the PIN file "
                            + pin
                            + " holds 1807 target rows and no decoy row (Label -1), so Percolator"
                            + " would have no negative examples; the decoy configuration was"
                            + " decoy_search = 1 (Comet's internal decoys, concatenated),"
                            + " decoy_prefix = \"DECOY_\"",
                    result.failures().get(EngineStep.VALIDATE_COMET_OUTPUTS));
        }
    }

    /** The real merge, then the archived parameter file changed. */
    private record MergeThenChangeParams(StepAction merge, Path params) implements StepAction {

        @Override
        public StepDeclaration declaration() {
            return merge.declaration();
        }

        @Override
        public void execute(StepContext context)
                throws StepFailedException, IOException, InterruptedException {
            merge.execute(context);
            Files.writeString(params, "# changed during the run\n", StandardOpenOption.APPEND);
        }
    }

    @Test
    @DisplayName("finalise-provenance: a parameter file changed during the run fails the run")
    void finaliseRefusesAParameterFileChangedDuringTheRun(@TempDir Path scratch)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Staged staged = stage(scratch);
        try (RealProject project = RealProject.create(staged.root().resolve("project"))) {
            PreparedRun prepared =
                    project.prepare(request(staged, DecoySource.COMET_INTERNAL_CONCATENATED));
            Path params = prepared.layout().cometParamsFile();
            Map<EngineStep, StepAction> actions = new EnumMap<>(prepared.actions());
            actions.put(
                    EngineStep.MERGE_PIN,
                    new MergeThenChangeParams(actions.get(EngineStep.MERGE_PIN), params));
            RunResult result = start(project, prepared, actions);
            assertEquals(AttemptOutcome.FAILED, result.outcome());
            assertEquals(StepState.SUCCEEDED, result.states().get(EngineStep.MERGE_PIN));
            assertEquals(StepState.FAILED, result.states().get(EngineStep.FINALISE_PROVENANCE));
            assertEquals(
                    "the run's archived parameter file "
                            + params
                            + " has SHA-256 "
                            + RealComet.sha256(params)
                            + " ("
                            + Files.size(params)
                            + " bytes), but the run recorded "
                            + prepared.parameters().hashes().sha256()
                            + " ("
                            + prepared.parameters().size()
                            + " bytes) when it was written; a run's parameters are written once,"
                            + " so this run cannot continue",
                    result.failures().get(EngineStep.FINALISE_PROVENANCE));
        }
    }

    @Test
    @DisplayName("a retry that re-runs the search replaces its outputs and the merged PIN")
    void aRetryReplacesTheSearchsOutputs(@TempDir Path scratch)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Staged staged = stage(scratch);
        try (RealProject project = RealProject.create(staged.root().resolve("project"))) {
            PreparedRun prepared =
                    project.prepare(request(staged, DecoySource.COMET_INTERNAL_CONCATENATED));
            assertEquals(AttemptOutcome.SUCCEEDED, project.run(prepared).outcome());
            Path merged = prepared.layout().mergedPinFile();
            String mergedBefore = RealComet.sha256(merged);
            Path pin = prepared.layout().pinFile("k562_3");
            String pinBefore = RealComet.sha256(pin);
            Files.writeString(merged, "an earlier attempt's merged file\n");
            Files.writeString(pin, "an earlier attempt's PIN\n");

            RunResult retry =
                    project.engine()
                            .start(
                                    prepared.request().withForced(Set.of(EngineStep.RUN_COMET)),
                                    StepStateListener.NONE)
                            .await(RealProject.RUN_BOUND)
                            .orElseThrow();
            assertEquals(
                    AttemptOutcome.SUCCEEDED, retry.outcome(), () -> retry.failures().toString());
            assertEquals(StepState.SKIPPED, retry.states().get(EngineStep.SERIALISE_COMET_PARAMS));
            assertEquals(StepState.SUCCEEDED, retry.states().get(EngineStep.RUN_COMET));
            assertEquals(StepState.SUCCEEDED, retry.states().get(EngineStep.MERGE_PIN));
            assertEquals(4, project.runner().launchesOf(staged.comet()).size());
            assertEquals(6472, RunEvidence.pinRows(merged));
            assertEquals(1, RunEvidence.headerLines(merged));
            assertEquals(mergedBefore, RealComet.sha256(merged), "merged again, identically");
            assertEquals(pinBefore, RealComet.sha256(pin), "searched again, identically");
            assertEquals(2, project.store().read(prepared.layout()).attempts().size());
        }
    }

    @Test
    @DisplayName(
            "Comet 2026.02.2's fragment-ion v4 index search writes no decoy row, and R-DEC-04"
                    + " refuses it")
    void anOlderFragmentIndexSearchWithoutDecoysIsRefused(@TempDir Path scratch)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Path root = scratch.toRealPath();
        Path older = RealComet.stageComet(RealComet.OLDER, root.resolve("bin/comet"));
        Path inputs = Files.createDirectories(root.resolve("inputs"));
        List<Path> spectra = RealComet.spectra(inputs);
        Path fasta = RealComet.subset(inputs.resolve("subset.fasta"));
        Path prebuilt = Files.createDirectories(root.resolve("prebuilt"));
        new CanonicalParamsWriter(RealComet.BUILD)
                .writeOnce(
                        RealComet.model(
                                RealComet.OLDER, fasta, DecoySource.COMET_INTERNAL_CONCATENATED, 4),
                        prebuilt.resolve("comet.params"),
                        new StreamingHashService());
        CometIndexCommand build =
                new CometIndexCommand(
                        older,
                        prebuilt.resolve("comet.params"),
                        IndexMode.FRAGMENT_ION,
                        prebuilt,
                        fasta);
        build.linkDatabase();
        ToolRunOutcome built =
                new ToolRunner(new ProcessService(Clock.systemUTC()), Duration.ofMinutes(5))
                        .run(build.command());
        assertTrue(built.exitedZero(), built.joinedOutput());
        try (RealProject project = RealProject.create(root.resolve("project"))) {
            PreparedRun prepared =
                    project.prepare(
                            new SearchRequest(
                                    RealComet.model(
                                            RealComet.OLDER,
                                            build.indexFile(),
                                            DecoySource.COMET_INTERNAL_CONCATENATED,
                                            4),
                                    spectra,
                                    RealComet.selection(RealComet.OLDER, older),
                                    IndexMode.NONE));
            RunResult result = project.run(prepared);
            assertEquals(AttemptOutcome.FAILED, result.outcome());
            assertEquals(
                    "the PIN file "
                            + prepared.layout().pinFile("k562_3")
                            + " holds 118 target rows and no decoy row (Label -1), so Percolator"
                            + " would have no negative examples; the decoy configuration was"
                            + " decoy_search = 1 (Comet's internal decoys, concatenated),"
                            + " decoy_prefix = \"DECOY_\"",
                    result.failures().get(EngineStep.VALIDATE_COMET_OUTPUTS));
        }
    }

    private static RunResult start(
            RealProject project, PreparedRun prepared, Map<EngineStep, StepAction> actions)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        RunRequest request =
                new RunRequest(
                        project.store(),
                        project.lock(),
                        prepared.layout(),
                        prepared.plan(),
                        prepared.inputs(),
                        Set.of(),
                        actions,
                        RealProject.application(),
                        prepared.settings());
        return project.engine()
                .start(request, StepStateListener.NONE)
                .await(RealProject.RUN_BOUND)
                .orElseThrow();
    }
}
