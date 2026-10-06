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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.DatabaseDelivery;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.provenance.manifest.FileDirection;
import org.cometgui.provenance.manifest.FileRecord;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ProvenanceStatus;
import org.cometgui.provenance.manifest.ToolRecord;
import org.cometgui.workflow.engine.ReuseCheck;
import org.cometgui.workflow.engine.ReuseRefusedException;
import org.cometgui.workflow.engine.RunResult;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.RerunPreview;
import org.cometgui.workflow.state.RunState;
import org.cometgui.workflow.state.StepState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Phase 08 exit gate items 1, 2, 3 (rows), 6 (integration half) and 9, against ONE real Comet
 * 2026.03.0 run through the engine: the two K562 spectrum files from an input directory proven
 * read-only, the proteome's first 1000 records, {@code decoy_search = 1}, {@code num_threads = 4}.
 *
 * <p>Everything asserted is read back from disk -- the run directory, {@code events.log} through
 * the event-log reader, {@code provenance.json} through {@code ManifestReader} -- and every
 * expected value is typed out here.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "only the linux/x86-64 Comet binary has ever been executed in this project")
class RealCometRunTest {

    @TempDir private static Path scratch;

    private static Path root;

    private static Path comet;

    private static Path inputs;

    private static List<Path> spectra;

    private static Path fasta;

    private static RealProject project;

    private static SearchRequest request;

    private static PreparedRun prepared;

    private static RunResult result;

    private static Map<String, String> before;

    private static Map<String, String> after;

    private static long searchMillis;

    @BeforeAll
    static void runTheRealSearch()
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        root = scratch.toRealPath();
        comet = RealComet.stageComet(RealComet.NEWER, root.resolve("bin/comet"));
        inputs = Files.createDirectories(root.resolve("read-only inputs"));
        spectra = RealComet.spectra(inputs);
        fasta = RealComet.subset(inputs.resolve("subset.fasta"));
        RealComet.makeReadOnly(inputs);
        project = RealProject.create(root.resolve("project"));
        request =
                new SearchRequest(
                        RealComet.model(
                                RealComet.NEWER, fasta, DecoySource.COMET_INTERNAL_CONCATENATED, 4),
                        spectra,
                        RealComet.selection(RealComet.NEWER, comet),
                        IndexMode.NONE);
        before = RealComet.snapshot(root);
        long started = System.nanoTime();
        prepared = project.prepare(request);
        result = project.run(prepared);
        searchMillis = (System.nanoTime() - started) / 1_000_000;
        after = RealComet.snapshot(root);
    }

    @AfterAll
    static void cleanUp() throws IOException {
        if (inputs != null) {
            RealComet.makeWritable(inputs);
        }
        if (project != null) {
            project.close();
        }
    }

    private static RunLayout layout() {
        return prepared.layout();
    }

    @Test
    @DisplayName(
            "gate 1: two files from a read-only directory give two pepXML and two PIN files in the"
                    + " run, with distinct -N bases, each validated, and the merged PIN")
    void gate1TwoFilesFromAReadOnlyDirectory() throws IOException {
        assertEquals(
                AttemptOutcome.SUCCEEDED, result.outcome(), () -> result.failures().toString());
        assertEquals(RunState.SUCCEEDED, result.runState());
        Map<EngineStep, StepState> succeeded = new EnumMap<>(EngineStep.class);
        for (EngineStep step :
                List.of(
                        EngineStep.VALIDATE_CONFIGURATION,
                        EngineStep.RESOLVE_COMET,
                        EngineStep.SERIALISE_COMET_PARAMS,
                        EngineStep.HASH_INPUTS,
                        EngineStep.RUN_COMET,
                        EngineStep.VALIDATE_COMET_OUTPUTS,
                        EngineStep.MERGE_PIN,
                        EngineStep.FINALISE_PROVENANCE)) {
            succeeded.put(step, StepState.SUCCEEDED);
        }
        assertEquals(succeeded, result.states());

        assertEquals(
                List.of("k562_3.pep.xml", "k562_3.pin", "k562_4.pep.xml", "k562_4.pin"),
                RunEvidence.listing(layout().cometOutputDirectory()));
        assertEquals(
                List.of("k562_3.mzML", "k562_4.mzML", "subset.fasta"), RunEvidence.listing(inputs));

        List<RealProject.Launch> launches = project.runner().launchesOf(comet);
        assertEquals(2, launches.size(), "one Comet invocation per spectrum file");
        String run = root + "/project/runs/" + layout().root().getFileName();
        assertEquals(
                List.of(
                        comet.toString(),
                        "-P" + run + "/parameters/comet.params",
                        "-N" + run + "/outputs/comet/k562_3",
                        root + "/read-only inputs/k562_3.mzML"),
                launches.get(0).command().argv());
        assertEquals(
                List.of(
                        comet.toString(),
                        "-P" + run + "/parameters/comet.params",
                        "-N" + run + "/outputs/comet/k562_4",
                        root + "/read-only inputs/k562_4.mzML"),
                launches.get(1).command().argv());
        assertEquals(Path.of(run), launches.get(0).command().workingDirectory());

        Map<String, String> validated =
                RunEvidence.finished(layout(), EngineStep.VALIDATE_COMET_OUTPUTS);
        RunEvidence.assertDetail(validated, "state", "succeeded");
        RunEvidence.assertDetail(validated, "outputs.comet-01.spectrum-queries", "728");
        RunEvidence.assertDetail(validated, "outputs.comet-01.targets", "1807");
        RunEvidence.assertDetail(validated, "outputs.comet-01.decoys", "1747");
        RunEvidence.assertDetail(validated, "outputs.comet-02.spectrum-queries", "607");
        RunEvidence.assertDetail(validated, "outputs.comet-02.targets", "1478");
        RunEvidence.assertDetail(validated, "outputs.comet-02.decoys", "1440");
        assertTrue(Files.isRegularFile(layout().mergedPinFile()));
        assertEquals(List.of("comet.params"), RunEvidence.listing(layout().parametersDirectory()));
        assertEquals(
                List.of("comet-01.log", "comet-02.log"),
                RunEvidence.listing(layout().logsDirectory()));
        assertEquals(
                DatabaseDelivery.PARAMETER_FILE,
                project.store().read(layout()).identity().databaseDelivery());
        System.out.printf(
                java.util.Locale.ROOT,
                "RealCometRunTest: prepare + two real searches + validate + merge + provenance"
                        + " took %d ms%n",
                searchMillis);
    }

    @Test
    @DisplayName(
            "gate 2: the input tree is identical before and after, and nothing outside the run"
                    + " directory changed but the runs/ directory that gained it")
    void gate2NothingWrittenOutsideTheRunDirectory() {
        String runPrefix = "project/runs/" + layout().root().getFileName();
        Map<String, String> inputsBefore = new java.util.TreeMap<>();
        Map<String, String> inputsAfter = new java.util.TreeMap<>();
        before.forEach(
                (name, entry) -> {
                    if (name.startsWith("read-only inputs")) {
                        inputsBefore.put(name, entry);
                    }
                });
        after.forEach(
                (name, entry) -> {
                    if (name.startsWith("read-only inputs")) {
                        inputsAfter.put(name, entry);
                    }
                });
        assertEquals(4, inputsBefore.size(), () -> "the input tree: " + inputsBefore);
        assertEquals(inputsBefore, inputsAfter);

        List<String> outside = new ArrayList<>();
        for (String changed : RealComet.differences(before, after)) {
            if (!changed.equals(runPrefix) && !changed.startsWith(runPrefix + "/")) {
                outside.add(changed);
            }
        }
        // The one change outside the run: runs/ gained the run directory, so its mtime moved.
        assertEquals(List.of("project/runs"), outside);
        assertTrue(after.containsKey(runPrefix + "/outputs/comet/k562_3.pin"));
    }

    @Test
    @DisplayName(
            "gate 3: the merged real PIN has exactly one header and 3554 + 2918 = 6472 data rows,"
                    + " and the merge is recorded in its stage.finished event")
    void gate3MergedPinHasOneHeaderAndTheSummedRows() throws IOException {
        Path merged = layout().mergedPinFile();
        assertEquals(1, RunEvidence.headerLines(merged));
        assertEquals(3554, RunEvidence.pinRows(layout().pinFile("k562_3")));
        assertEquals(2918, RunEvidence.pinRows(layout().pinFile("k562_4")));
        assertEquals(6472, RunEvidence.pinRows(merged));
        assertEquals(
                Files.readAllLines(layout().pinFile("k562_3"), StandardCharsets.ISO_8859_1).get(0),
                Files.readAllLines(merged, StandardCharsets.ISO_8859_1).get(0));

        Map<String, String> merge = RunEvidence.finished(layout(), EngineStep.MERGE_PIN);
        RunEvidence.assertDetail(merge, "state", "succeeded");
        RunEvidence.assertDetail(
                merge, "merge.input-01.file", layout().pinFile("k562_3").toString());
        RunEvidence.assertDetail(merge, "merge.input-01.rows", "3554");
        RunEvidence.assertDetail(
                merge, "merge.input-02.file", layout().pinFile("k562_4").toString());
        RunEvidence.assertDetail(merge, "merge.input-02.rows", "2918");
        RunEvidence.assertDetail(merge, "merge.total-rows", "6472");
        RunEvidence.assertDetail(merge, "merge.output", merged.toString());
        RunEvidence.assertDetail(merge, "merge.sha256", RealComet.sha256(merged));
    }

    @Test
    @DisplayName(
            "gate 9: one tool record per spectrum file, argvs differing only in -N and input; the"
                    + " archived comet.params hash equals the executed -P file, writeOnce's hash"
                    + " and an independent re-hash")
    void gate9ArgvPerFileAndArchivedParamsHash() throws IOException {
        ProvenanceManifest manifest = RunEvidence.manifest(layout());
        assertEquals(ProvenanceStatus.COMPLETED, manifest.run().status());
        List<ToolRecord> tools = RunEvidence.tools(manifest, CometWorkflow.TOOL_NAME);
        assertEquals(2, tools.size(), () -> manifest.tools().toString());
        assertEquals(java.util.Optional.of("comet-01"), tools.get(0).stageId());
        assertEquals(java.util.Optional.of("comet-02"), tools.get(1).stageId());
        List<String> first = tools.get(0).execution().command().argv();
        List<String> second = tools.get(1).execution().command().argv();
        assertEquals(4, first.size());
        assertEquals(4, second.size());
        List<Integer> differing = new ArrayList<>();
        for (int index = 0; index < first.size(); index++) {
            if (!first.get(index).equals(second.get(index))) {
                differing.add(index);
            }
        }
        assertEquals(List.of(2, 3), differing, "the argvs differ only in -N and the input");
        assertTrue(first.get(2).startsWith("-N") && second.get(2).startsWith("-N"));
        assertEquals(root + "/read-only inputs/k562_3.mzML", first.get(3));
        assertEquals(root + "/read-only inputs/k562_4.mzML", second.get(3));
        assertEquals(RealComet.NEWER_SHA256, tools.get(0).hashes().sha256());
        assertEquals(0, tools.get(0).execution().exitCode());

        String executedParams = first.get(1);
        assertTrue(executedParams.startsWith("-P"));
        Path executed = Path.of(executedParams.substring(2));
        assertEquals(executedParams, second.get(1));
        assertEquals(layout().cometParamsFile(), executed);
        String independent = RealComet.sha256(executed);
        List<FileRecord> params = new ArrayList<>();
        for (FileRecord file : manifest.files()) {
            if (file.role().equals("comet-params") && file.direction() == FileDirection.OUTPUT) {
                params.add(file);
            }
        }
        assertEquals(1, params.size(), () -> manifest.files().toString());
        assertEquals(executed, params.get(0).path());
        assertEquals(independent, params.get(0).hashes().sha256());
        assertEquals(independent, prepared.parameters().hashes().sha256());
        assertEquals(
                independent,
                project.store().read(layout()).identity().parameters().hashes().sha256());
        assertEquals(independent, manifest.settings().get(CometWorkflow.PARAMS_SHA256_SETTING));
        RunEvidence.assertDetail(
                RunEvidence.finished(layout(), EngineStep.FINALISE_PROVENANCE),
                "comet.params-sha256",
                independent);
    }

    @Test
    @DisplayName(
            "gate 6: after the real run, an unchanged configuration re-executes nothing; changed"
                    + " Comet parameters re-execute Comet and everything downstream; a changed"
                    + " spectrum file re-executes Comet and downstream but reuses the parameters")
    void gate6RerunPreviewAfterARealRun(@TempDir Path elsewhere) throws IOException {
        ReuseCheck unchanged = project.workflow().preview(project.engine(), prepared, request);
        assertTrue(unchanged.accepted(), unchanged::message);
        RerunPreview same = unchanged.preview();
        assertEquals(Set.of(), same.executed());
        assertEquals(
                Set.of(
                        EngineStep.SERIALISE_COMET_PARAMS,
                        EngineStep.RUN_COMET,
                        EngineStep.VALIDATE_COMET_OUTPUTS,
                        EngineStep.MERGE_PIN,
                        EngineStep.FINALISE_PROVENANCE),
                same.reused());

        SearchRequest changedParameters =
                new SearchRequest(
                        request.model().withText("fragment_bin_tol", "1.0005", ValueOrigin.USER),
                        spectra,
                        request.comet(),
                        IndexMode.NONE);
        RerunPreview parameters =
                project.workflow().preview(project.engine(), prepared, changedParameters).preview();
        assertEquals(
                Set.of(
                        EngineStep.SERIALISE_COMET_PARAMS,
                        EngineStep.RUN_COMET,
                        EngineStep.VALIDATE_COMET_OUTPUTS,
                        EngineStep.MERGE_PIN,
                        EngineStep.FINALISE_PROVENANCE),
                parameters.reExecuted());
        assertEquals(
                Set.of(
                        EngineStep.VALIDATE_CONFIGURATION,
                        EngineStep.RESOLVE_COMET,
                        EngineStep.HASH_INPUTS),
                parameters.prepared());
        assertEquals(Set.of(), parameters.reused());

        Path changed = Files.createDirectories(elsewhere.toRealPath().resolve("changed"));
        Path k4 = changed.resolve("k562_4.mzML");
        Files.copy(spectra.get(1), k4);
        Files.write(
                k4,
                "\n".getBytes(StandardCharsets.US_ASCII),
                java.nio.file.StandardOpenOption.APPEND);
        SearchRequest changedSpectrum =
                new SearchRequest(
                        request.model(),
                        List.of(spectra.get(0), k4),
                        request.comet(),
                        IndexMode.NONE);
        ReuseCheck spectrum =
                project.workflow().preview(project.engine(), prepared, changedSpectrum);
        assertTrue(spectrum.accepted(), spectrum::message);
        assertEquals(
                Set.of(
                        EngineStep.RUN_COMET,
                        EngineStep.VALIDATE_COMET_OUTPUTS,
                        EngineStep.MERGE_PIN,
                        EngineStep.FINALISE_PROVENANCE),
                spectrum.preview().reExecuted());
        assertEquals(
                Set.of(
                        EngineStep.VALIDATE_CONFIGURATION,
                        EngineStep.RESOLVE_COMET,
                        EngineStep.HASH_INPUTS),
                spectrum.preview().prepared());
        assertEquals(Set.of(EngineStep.SERIALISE_COMET_PARAMS), spectrum.preview().reused());
        assertEquals(2, project.runner().launchesOf(comet).size(), "a preview launches nothing");
    }
}
