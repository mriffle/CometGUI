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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.run.RunDerivation;
import org.cometgui.domain.run.RunDescriptor;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.params.percolator.resolution.DownstreamStage;
import org.cometgui.params.percolator.resolution.PercolatorResolver;
import org.cometgui.provenance.manifest.FileDirection;
import org.cometgui.provenance.manifest.FileRecord;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ToolRecord;
import org.cometgui.workflow.engine.ReuseRefusedException;
import org.cometgui.workflow.engine.RunResult;
import org.cometgui.workflow.engine.StepStateListener;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.StepState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Phase 09 exit gate items 6 and 9 for the compatible-version rerun ({@link PercolatorRerun}),
 * against REAL runs through the real engine and the real process service.
 *
 * <p>The scientist's case, as the specification's <em>Stage reruns</em> tells it: a real Comet
 * 2026.03.0 search of the two K562 mzML and the proteome's first 1000 records ({@code decoy_search
 * = 1}, {@code num_threads = 4}), rescored by Percolator 3.09 with Limelight conversion
 * <em>disabled</em> -- the resolved default then. Limelight is then wanted, which needs pout XML,
 * which 3.09 cannot write; the rerun with 3.07.1 and Limelight enabled must make a second run that
 * runs only Percolator, from the first run's preserved merged PIN, and leave the first run exactly
 * as it was. Every build's capabilities are the real probe's verdict, run here; every binary is
 * held to its SHA-256 first ({@link RealPercolator}, {@link RealComet}); a missing fixture fails.
 *
 * <p>Everything asserted is read back from disk -- {@code run.json} through the run store, {@code
 * provenance.json} through {@code ManifestReader}, every file hashed here -- and every expected
 * value is typed out here or counted independently.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "only the linux/x86-64 Comet and Percolator binaries have ever been executed in"
                        + " this project")
class RealPercolatorRerunTest {

    @TempDir private static Path scratch;

    private static Path comet;

    private static List<Path> spectra;

    private static Path fasta;

    private static Path percolator3071;

    private static Path percolator309;

    private static ToolOffer offer3071;

    private static ToolOffer offer309;

    private static RealProject project;

    private static PreparedRun original;

    private static RunResult originalResult;

    /** The original run's directory, every path with its size, SHA-256, time and mode, before. */
    private static Map<String, String> treeBefore;

    /** The same, after the preview, the rerun and its execution. */
    private static Map<String, String> treeAfter;

    private static String originalManifestBefore;

    private static List<ToolRecord> originalCometBefore;

    private static PercolatorRerunPreview preview;

    private static DerivedRun rerun;

    private static RunResult rerunResult;

    /** Comet's launches, counted around the real process service, before and after the rerun. */
    private static int cometBefore;

    private static int cometAfter;

    @BeforeAll
    static void searchThenRerun()
            throws IOException,
                    InterruptedException,
                    RunBlockedException,
                    ReuseRefusedException,
                    RerunRefusedException {
        Path root = scratch.toRealPath();
        comet = RealComet.stageComet(RealComet.NEWER, root.resolve("bin/comet"));
        Path inputs = Files.createDirectories(root.resolve("inputs"));
        spectra = RealComet.spectra(inputs);
        fasta = RealComet.subset(inputs.resolve("subset.fasta"));
        percolator3071 = RealPercolator.stage3071(root.resolve("bin/percolator-3.07.1"));
        percolator309 = RealPercolator.wrapper309();
        offer3071 =
                RealPercolator.offer(
                        "3.07.1",
                        ToolOrigin.MANAGED,
                        percolator3071,
                        RealPercolator.probe("3.07.1", percolator3071),
                        RealPercolator.ADVISORIES_3071);
        offer309 =
                RealPercolator.offer(
                        "3.09",
                        ToolOrigin.LOCAL,
                        percolator309,
                        RealPercolator.probe("3.09", percolator309),
                        List.of());
        project = RealProject.create(root.resolve("project"));

        original =
                project.prepare(
                        search().withPercolator(
                                        choice(offer309, EnumSet.noneOf(DownstreamStage.class))));
        originalResult = project.run(original);

        RunLayout layout = original.layout();
        treeBefore = RunEvidence.tree(layout.root());
        originalManifestBefore = RealComet.sha256(layout.provenanceJsonFile());
        originalCometBefore =
                RunEvidence.tools(RunEvidence.manifest(layout), CometWorkflow.TOOL_NAME);
        cometBefore = project.runner().launchesOf(comet).size();

        PercolatorRerun action = new PercolatorRerun(project.hashes());
        PercolatorChoice compatible =
                choice(offer3071, EnumSet.of(DownstreamStage.LIMELIGHT_CONVERSION));
        preview = action.preview(project.store(), project.project(), layout, compatible);
        rerun =
                action.prepare(
                        project.store(),
                        project.lock(),
                        layout,
                        compatible,
                        RealProject.application());
        rerunResult =
                action.start(project.engine(), rerun, StepStateListener.NONE)
                        .await(RealProject.RUN_BOUND)
                        .orElseThrow();

        cometAfter = project.runner().launchesOf(comet).size();
        treeAfter = RunEvidence.tree(layout.root());
    }

    @AfterAll
    static void cleanUp() throws IOException {
        if (project != null) {
            project.close();
        }
    }

    private static SearchRequest search() {
        return new SearchRequest(
                RealComet.model(RealComet.NEWER, fasta, DecoySource.COMET_INTERNAL_CONCATENATED, 4),
                spectra,
                RealComet.selection(RealComet.NEWER, comet),
                IndexMode.NONE);
    }

    /** One build, resolved among both offers, in the order the Tool Manager would list them. */
    private static PercolatorChoice choice(ToolOffer selected, Set<DownstreamStage> stages)
            throws IOException {
        Path executable = selected.installedPath().orElseThrow();
        return new PercolatorChoice(
                new PercolatorSelection(selected, executable, RealComet.sha256(executable)),
                PercolatorSettings.defaults(),
                stages,
                PercolatorResolver.resolve(List.of(offer309, offer3071), stages));
    }

    private static ToolRecord percolatorTool(ProvenanceManifest manifest) {
        List<ToolRecord> tools = RunEvidence.tools(manifest, CometWorkflow.PERCOLATOR_TOOL_NAME);
        assertEquals(1, tools.size(), "one Percolator invocation");
        return tools.get(0);
    }

    /** The argv a build with every non-XML capability runs, typed out; {@code -X} when asked. */
    private static List<String> argv(Path executable, RunLayout layout, boolean xml) {
        Path out = layout.outputsDirectory().resolve("percolator");
        List<String> argv =
                new ArrayList<>(
                        List.of(
                                executable.toString(),
                                "--results-psms",
                                out + "/psms.tsv",
                                "--results-peptides",
                                out + "/peptides.tsv",
                                "--decoy-results-psms",
                                out + "/decoy-psms.tsv",
                                "--decoy-results-peptides",
                                out + "/decoy-peptides.tsv",
                                "--weights",
                                out + "/weights.txt"));
        if (xml) {
            argv.addAll(List.of("-X", out + "/pout.xml"));
        }
        argv.addAll(
                List.of(
                        "--seed",
                        "1",
                        "--num-threads",
                        "3",
                        "--testFDR",
                        "0.01",
                        "--trainFDR",
                        "0.01",
                        "--maxiter",
                        "10",
                        layout.mergedPinFile().toString()));
        return argv;
    }

    @Test
    @DisplayName(
            "the original: a real Comet search rescored by 3.09 with Limelight disabled, no XML"
                    + " option in its recorded argv")
    void theOriginalRun() throws IOException {
        assertEquals(
                AttemptOutcome.SUCCEEDED,
                originalResult.outcome(),
                () -> originalResult.failures().toString());
        assertEquals(2, originalCometBefore.size(), "one Comet invocation per spectrum file");
        ToolRecord tool = percolatorTool(RunEvidence.manifest(original.layout()));
        assertEquals("3.09", tool.version());
        assertEquals(RealPercolator.SHA256_WRAPPER_309, tool.hashes().sha256());
        assertEquals(
                argv(percolator309, original.layout(), false), tool.execution().command().argv());
    }

    @Test
    @DisplayName(
            "the preview, before anything was created: the Percolator steps execute, the Comet"
                    + " results are reused from run-0001")
    void thePreview() throws IOException {
        Path merged = original.layout().mergedPinFile();
        assertEquals("run-0001", preview.source().value());
        assertEquals(
                EnumSet.of(
                        EngineStep.SERIALISE_COMET_PARAMS,
                        EngineStep.RUN_COMET,
                        EngineStep.VALIDATE_COMET_OUTPUTS,
                        EngineStep.MERGE_PIN),
                preview.reusedFromSource());
        assertEquals(
                List.of(
                        "Percolator reruns in a new run on the merged PIN of run run-0001 ("
                                + merged
                                + ", SHA-256 "
                                + RealComet.sha256(merged)
                                + ", re-hashed and unchanged); Comet is not run.",
                        "validate-configuration: prepares -- needed by resolve-percolator",
                        "resolve-percolator: prepares -- needed by run-percolator",
                        "serialise-comet-params: not executed -- its result is reused from run"
                                + " run-0001",
                        "run-comet: not executed -- its result is reused from run run-0001",
                        "validate-comet-outputs: not executed -- its result is reused from run"
                                + " run-0001",
                        "merge-pin: not executed -- its result is reused from run run-0001",
                        "run-percolator: executes -- no successful earlier execution is recorded",
                        "parse-percolator: executes -- no successful earlier execution is"
                                + " recorded; run-percolator re-executes",
                        "finalise-provenance: executes -- no successful earlier execution is"
                                + " recorded"),
                preview.lines());
        System.out.printf(
                Locale.ROOT,
                "RealPercolatorRerunTest preview%n  %s%n",
                String.join("\n  ", preview.lines()));
    }

    @Test
    @DisplayName(
            "gate 6: a second run whose Percolator record has a different version, checksum and"
                    + " argv; Comet launched zero times; the merged PIN re-hashed and equal; the"
                    + " original's Comet stage untouched")
    void gate6TheSecondExecutionRecord() throws IOException {
        assertEquals(
                AttemptOutcome.SUCCEEDED,
                rerunResult.outcome(),
                () -> rerunResult.failures().toString());
        for (StepState state : rerunResult.states().values()) {
            assertEquals(StepState.SUCCEEDED, state);
        }
        assertEquals(cometBefore, cometAfter, "Comet was launched during the rerun");
        assertEquals(2, cometBefore, "the original's two Comet invocations, and no more");

        RunLayout layout = rerun.layout();
        assertTrue(Files.isDirectory(layout.root()));
        assertTrue(String.valueOf(layout.root().getFileName()).endsWith("-run-0002"));
        ProvenanceManifest first = RunEvidence.manifest(original.layout());
        ProvenanceManifest second = RunEvidence.manifest(layout);
        ToolRecord before = percolatorTool(first);
        ToolRecord after = percolatorTool(second);
        List<String> firstArgv = before.execution().command().argv();
        List<String> secondArgv = after.execution().command().argv();

        assertEquals("3.09", before.version());
        assertEquals("3.07.1", after.version());
        assertEquals(RealPercolator.SHA256_WRAPPER_309, before.hashes().sha256());
        assertEquals(RealPercolator.SHA256_3071, after.hashes().sha256());
        assertEquals(argv(percolator309, original.layout(), false), firstArgv);
        assertEquals(argv(percolator3071, layout, true), secondArgv);
        assertFalse(firstArgv.contains("-X"));
        assertFalse(firstArgv.stream().anyMatch(element -> element.endsWith("pout.xml")));
        int x = secondArgv.indexOf("-X");
        assertEquals(
                layout.outputsDirectory().resolve("percolator/pout.xml").toString(),
                secondArgv.get(x + 1));
        assertEquals(List.of(), RunEvidence.tools(second, CometWorkflow.TOOL_NAME));
        for (String key : second.settings().keySet()) {
            assertFalse(key.startsWith("comet."), key);
        }

        String recordedPin = null;
        for (FileRecord file : first.files()) {
            if (file.direction() == FileDirection.OUTPUT && "merged-pin".equals(file.role())) {
                recordedPin = file.hashes().sha256();
            }
        }
        assertEquals(RealComet.sha256(original.layout().mergedPinFile()), recordedPin);
        assertEquals(recordedPin, RealComet.sha256(layout.mergedPinFile()));
        RunDescriptor descriptor = project.store().read(layout);
        RunDerivation from = descriptor.identity().derivedFrom().orElseThrow();
        assertEquals(2, descriptor.schemaVersion());
        assertEquals("run-0001", from.runId().value());
        assertEquals(recordedPin, from.mergedPin().hashes().sha256());
        assertEquals(originalManifestBefore, from.provenance().hashes().sha256());
        Map<String, String> settings = second.settings();
        assertEquals("run-0001", settings.get(RerunProvenance.SOURCE_RUN_ID));
        assertEquals(recordedPin, settings.get(RerunProvenance.MERGED_PIN_SHA256));
        assertEquals("3.09", settings.get(RerunProvenance.SOURCE_PERCOLATOR_VERSION));
        assertEquals("2026.03.0", settings.get(RerunProvenance.SOURCE_COMET_RELEASE));
        assertEquals(RealComet.sha256(comet), settings.get(RerunProvenance.SOURCE_COMET_SHA256));
        assertEquals(
                "serialise-comet-params run-comet validate-comet-outputs merge-pin",
                settings.get(RerunProvenance.REUSED_STEPS));

        assertEquals(
                originalManifestBefore, RealComet.sha256(original.layout().provenanceJsonFile()));
        assertEquals(
                originalCometBefore,
                RunEvidence.tools(
                        RunEvidence.manifest(original.layout()), CometWorkflow.TOOL_NAME));

        Map<String, String> parsed = RunEvidence.finished(layout, EngineStep.PARSE_PERCOLATOR);
        RunEvidence.assertDetail(
                parsed, "pout.namespace", "http://per-colator.com/percolator_out/15");
        System.out.printf(
                Locale.ROOT,
                "RealPercolatorRerunTest gate 6%n  first  %s %s argv %s%n  second %s %s argv %s%n"
                        + "  derivedFrom runId=%s created=%s provenance sha256=%s mergedPin"
                        + " sha256=%s size=%d%n  Comet launches before=%d after=%d%n"
                        + "  rerun settings %s%n",
                before.version(),
                before.hashes().sha256(),
                firstArgv,
                after.version(),
                after.hashes().sha256(),
                secondArgv,
                from.runId(),
                from.created(),
                from.provenance().hashes().sha256(),
                from.mergedPin().hashes().sha256(),
                from.mergedPin().size(),
                cometBefore,
                cometAfter,
                rerunSettings(settings));
    }

    private static Map<String, String> rerunSettings(Map<String, String> settings) {
        Map<String, String> selected = new TreeMap<>();
        settings.forEach(
                (key, value) -> {
                    if (key.startsWith("rerun.")) {
                        selected.put(key, value);
                    }
                });
        return selected;
    }

    @Test
    @DisplayName(
            "gate 9: the original run's whole tree is identical after the rerun, its raw"
                    + " Percolator outputs byte-identical and still read-only; the new run's are"
                    + " read-only too")
    void gate9TheOriginalIsUntouched() throws IOException {
        assertEquals(treeBefore, treeAfter);
        assertTrue(treeBefore.size() > 20, () -> "a real run has its files: " + treeBefore);
        Path out = original.percolatorOutputDirectory().orElseThrow();
        List<String> raw =
                List.of(
                        "decoy-peptides.tsv",
                        "decoy-psms.tsv",
                        "peptides.tsv",
                        "psms.tsv",
                        "weights.txt");
        assertEquals(raw, RunEvidence.listing(out));
        for (String name : raw) {
            String key = "outputs/percolator/" + name;
            assertEquals(treeBefore.get(key), treeAfter.get(key), key);
            assertTrue(
                    treeAfter.get(key).contains(" " + RealComet.sha256(out.resolve(name)) + " "));
            assertNotWritable(out.resolve(name));
        }
        Path newOut = rerun.percolatorOutputDirectory();
        List<String> written =
                List.of(
                        "decoy-peptides.tsv",
                        "decoy-psms.tsv",
                        "peptides.tsv",
                        "pout.xml",
                        "psms.tsv",
                        "weights.txt");
        assertEquals(written, RunEvidence.listing(newOut));
        for (String name : written) {
            assertNotWritable(newOut.resolve(name));
        }
        System.out.printf(
                Locale.ROOT,
                "RealPercolatorRerunTest gate 9: the original's tree, %d paths, identical before"
                        + " and after%n",
                treeAfter.size());
    }

    private static void assertNotWritable(Path file) throws IOException {
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file);
        assertFalse(permissions.contains(PosixFilePermission.OWNER_WRITE), file::toString);
        assertFalse(permissions.contains(PosixFilePermission.GROUP_WRITE), file::toString);
        assertFalse(permissions.contains(PosixFilePermission.OTHERS_WRITE), file::toString);
    }

    @Test
    @DisplayName(
            "refusal: a second real run whose merged PIN was altered is refused naming the file"
                    + " and both hashes, nothing launched, no run left behind")
    void anAlteredMergedPinIsRefused()
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        PreparedRun copy = project.prepare(search());
        RunResult ran = project.run(copy);
        assertEquals(AttemptOutcome.SUCCEEDED, ran.outcome(), () -> ran.failures().toString());
        Path merged = copy.layout().mergedPinFile();
        String recorded = RealComet.sha256(merged);
        Files.writeString(
                merged, "altered\n", StandardCharsets.US_ASCII, StandardOpenOption.APPEND);
        String altered = RealComet.sha256(merged);
        List<String> runs = project.runDirectories();
        int launches = project.runner().launches().size();

        PercolatorRerun action = new PercolatorRerun(project.hashes());
        PercolatorChoice compatible =
                choice(offer3071, EnumSet.of(DownstreamStage.LIMELIGHT_CONVERSION));
        RerunRefusedException refused =
                assertThrows(
                        RerunRefusedException.class,
                        () ->
                                action.prepare(
                                        project.store(),
                                        project.lock(),
                                        copy.layout(),
                                        compatible,
                                        RealProject.application()));
        String expected =
                "the file "
                        + merged
                        + " (role merged-pin) of run "
                        + copy.identity().runId()
                        + " has SHA-256 "
                        + altered
                        + ", but run "
                        + copy.identity().runId()
                        + " recorded SHA-256 "
                        + recorded
                        + "; it has changed since it was recorded, so it is not reused and"
                        + " Percolator is not rerun from it (R-RUN-02). Nothing was created.";
        assertEquals(expected, refused.getMessage());
        assertEquals(runs, project.runDirectories(), "no run directory left behind");
        assertEquals(launches, project.runner().launches().size(), "nothing launched");
        System.out.printf(Locale.ROOT, "RealPercolatorRerunTest refusal: %s%n", expected);
    }
}
