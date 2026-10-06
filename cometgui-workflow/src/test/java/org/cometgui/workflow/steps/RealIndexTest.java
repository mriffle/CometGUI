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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.cometgui.domain.params.CometIndexDescription;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.DatabaseDelivery;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.run.RunIdentity;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.writer.CanonicalParamsWriter;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.provenance.manifest.FileDirection;
import org.cometgui.provenance.manifest.FileRecord;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ToolRecord;
import org.cometgui.tools.api.ToolRunOutcome;
import org.cometgui.tools.api.ToolRunner;
import org.cometgui.tools.comet.CometIndexCommand;
import org.cometgui.tools.comet.CometIndexHeaderReader;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.workflow.engine.ReuseRefusedException;
import org.cometgui.workflow.engine.RunResult;
import org.cometgui.workflow.state.EngineStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The index-compatibility assignment (tier 1, 2026-10-06) and the index step (P8-8), against the
 * real binaries.
 *
 * <ul>
 *   <li>A fragment-ion run builds its index in the PROJECT's cache -- the FASTA's own directory is
 *       read-only and stays as it was -- searches it with {@code -D}, records the mechanism, and a
 *       second run reuses the cache without building.
 *   <li>In one test: a format-4 index built by Comet 2026.02.2 and chosen as {@code database_name}
 *       is refused before Comet starts for a Comet 2026.03.0 run (no launch, no run directory), and
 *       the very same file is accepted, and searched, by a Comet 2026.02.2 run. A check blind to
 *       the release fails one half or the other.
 * </ul>
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "only the linux/x86-64 Comet binary has ever been executed in this project")
class RealIndexTest {

    @Test
    @DisplayName(
            "index check: a fragment-ion run builds its index in the project's cache, searches it"
                    + " with -D, and a second run reuses the cache")
    void indexBuiltIntoTheProjectCacheAndReused(@TempDir Path scratch)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Path root = scratch.toRealPath();
        Path comet = RealComet.stageComet(RealComet.NEWER, root.resolve("bin/comet"));
        Path inputs = Files.createDirectories(root.resolve("read-only inputs"));
        List<Path> spectra = RealComet.spectra(inputs);
        Path fasta = RealComet.subset(inputs.resolve("subset.fasta"));
        RealComet.makeReadOnly(inputs);
        try (RealProject project = RealProject.create(root.resolve("project"))) {
            Map<String, String> inputsBefore = RealComet.snapshot(inputs);
            SearchRequest request =
                    new SearchRequest(
                            RealComet.model(
                                    RealComet.NEWER,
                                    fasta,
                                    DecoySource.COMET_INTERNAL_CONCATENATED,
                                    4),
                            spectra,
                            RealComet.selection(RealComet.NEWER, comet),
                            IndexMode.FRAGMENT_ION);

            PreparedRun first = project.prepare(request);
            assertTrue(first.buildsIndex());
            Path index = first.indexFile().orElseThrow();
            Path cache = RealComet.parentOf(index);
            assertEquals(project.project().indexCacheDirectory(), RealComet.parentOf(cache));
            assertEquals(
                    first.settings().get(CometWorkflow.INDEX_KEY_SETTING),
                    String.valueOf(cache.getFileName()));
            RunResult built = project.run(first);
            assertEquals(
                    AttemptOutcome.SUCCEEDED, built.outcome(), () -> built.failures().toString());

            assertEquals(inputsBefore, RealComet.snapshot(inputs), "nothing beside the FASTA");
            assertEquals(
                    List.of("index.complete", "subset.fasta", "subset.fasta.idx"),
                    RunEvidence.listing(cache));
            assertEquals(fasta, Files.readSymbolicLink(cache.resolve("subset.fasta")));
            CometIndexDescription header = CometIndexHeaderReader.read(index);
            assertEquals(5, header.formatVersion());
            assertEquals(IndexMode.FRAGMENT_ION, header.type());
            String indexSha256 = RealComet.sha256(index);
            assertEquals(
                    "cometgui-index-cache 1\nsha256 "
                            + indexSha256
                            + "\nsize "
                            + Files.size(index)
                            + "\n",
                    Files.readString(cache.resolve("index.complete")));

            List<RealProject.Launch> launches = project.runner().launchesOf(comet);
            assertEquals(3, launches.size(), "one index build and one search per file");
            String run1 = first.layout().root().toString();
            assertEquals(
                    List.of(
                            comet.toString(),
                            "-P" + run1 + "/parameters/comet.params",
                            "-i",
                            "-D" + cache + "/subset.fasta"),
                    launches.get(0).command().argv());
            assertEquals(cache, launches.get(0).command().workingDirectory());
            assertEquals(
                    List.of(
                            comet.toString(),
                            "-P" + run1 + "/parameters/comet.params",
                            "-D" + index,
                            "-N" + run1 + "/outputs/comet/k562_3",
                            inputs + "/k562_3.mzML"),
                    launches.get(1).command().argv());
            RunIdentity identity = project.store().read(first.layout()).identity();
            assertEquals(IndexMode.FRAGMENT_ION, identity.indexMode());
            assertEquals(DatabaseDelivery.COMMAND_LINE, identity.databaseDelivery());
            RunEvidence.assertDetail(
                    RunEvidence.finished(first.layout(), EngineStep.BUILD_COMET_INDEX),
                    "index.cache",
                    "built");
            RunEvidence.assertDetail(
                    RunEvidence.finished(first.layout(), EngineStep.BUILD_COMET_INDEX),
                    "index.sha256",
                    indexSha256);
            // Measured: a fragment-ion index search finds far fewer PSMs than the FASTA search.
            Map<String, String> validated =
                    RunEvidence.finished(first.layout(), EngineStep.VALIDATE_COMET_OUTPUTS);
            RunEvidence.assertDetail(validated, "outputs.comet-01.targets", "118");
            RunEvidence.assertDetail(validated, "outputs.comet-01.decoys", "96");
            RunEvidence.assertDetail(validated, "outputs.comet-02.targets", "80");
            RunEvidence.assertDetail(validated, "outputs.comet-02.decoys", "48");
            ProvenanceManifest manifest = RunEvidence.manifest(first.layout());
            List<ToolRecord> tools = RunEvidence.tools(manifest, CometWorkflow.TOOL_NAME);
            assertEquals(
                    List.of(
                            Optional.of("comet-index"),
                            Optional.of("comet-01"),
                            Optional.of("comet-02")),
                    tools.stream().map(ToolRecord::stageId).toList());
            FileRecord indexRecord =
                    manifest.files().stream()
                            .filter(file -> file.direction() == FileDirection.OUTPUT)
                            .filter(file -> file.role().equals("comet-index"))
                            .findFirst()
                            .orElseThrow();
            assertEquals(index, indexRecord.path());
            assertEquals(indexSha256, indexRecord.hashes().sha256());
            assertEquals(
                    RealComet.sha256(first.layout().mergedPinFile()),
                    RunEvidence.finished(first.layout(), EngineStep.MERGE_PIN).get("merge.sha256"));

            PreparedRun second = project.prepare(request);
            assertFalse(second.buildsIndex());
            assertEquals(Optional.of(index), second.indexFile());
            RunResult reused = project.run(second);
            assertEquals(
                    AttemptOutcome.SUCCEEDED, reused.outcome(), () -> reused.failures().toString());
            List<RealProject.Launch> after = project.runner().launchesOf(comet);
            assertEquals(5, after.size(), "the second run searches twice and builds nothing");
            for (RealProject.Launch launch : after.subList(3, 5)) {
                assertTrue(
                        launch.command().argv().contains("-D" + index),
                        launch.command().argv()::toString);
                assertFalse(launch.command().argv().contains("-i"));
            }
            RunEvidence.assertDetail(
                    RunEvidence.finished(second.layout(), EngineStep.BUILD_COMET_INDEX),
                    "index.cache",
                    "reused");
            assertEquals(
                    List.of(Optional.of("comet-01"), Optional.of("comet-02")),
                    RunEvidence.tools(
                                    RunEvidence.manifest(second.layout()), CometWorkflow.TOOL_NAME)
                            .stream()
                            .map(ToolRecord::stageId)
                            .toList());
            assertEquals(indexSha256, RealComet.sha256(index), "the reused index is unchanged");
        } finally {
            RealComet.makeWritable(inputs);
        }
    }

    @Test
    @DisplayName(
            "index check: a v4 index built by 2026.02.2 is refused before Comet starts for a"
                    + " 2026.03.0 run, and the same file is accepted and searched by a 2026.02.2"
                    + " run")
    void aVersion4IndexIsRefusedForTheNewerReleaseAndAcceptedForTheOlder(@TempDir Path scratch)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Path root = scratch.toRealPath();
        Path newer = RealComet.stageComet(RealComet.NEWER, root.resolve("bin-newer/comet"));
        Path older = RealComet.stageComet(RealComet.OLDER, root.resolve("bin-older/comet"));
        Path inputs = Files.createDirectories(root.resolve("inputs"));
        List<Path> spectra = RealComet.spectra(inputs);
        Path fasta = RealComet.subset(inputs.resolve("subset.fasta"));

        // A pre-existing index, built by Comet 2026.02.2 outside any project.
        Path prebuilt = Files.createDirectories(root.resolve("prebuilt"));
        // A peptide index, so index_search_type = 0: Comet 2026.02.2's default 1 asks for a
        // fragment-ion index, which the index rules rightly call a contradiction. (A fragment-ion
        // v4 index built with decoy_search = 1 is no use here: measured, Comet 2026.02.2's
        // search of it writes 118 target rows and no decoy row, which R-DEC-04 refuses.)
        CometParameters olderModel =
                RealComet.model(RealComet.OLDER, fasta, DecoySource.COMET_INTERNAL_CONCATENATED, 4)
                        .withText("index_search_type", "0", ValueOrigin.USER);
        new CanonicalParamsWriter(RealComet.BUILD)
                .writeOnce(
                        olderModel, prebuilt.resolve("comet.params"), new StreamingHashService());
        CometIndexCommand build =
                new CometIndexCommand(
                        older,
                        prebuilt.resolve("comet.params"),
                        IndexMode.PEPTIDE,
                        prebuilt,
                        fasta);
        build.linkDatabase();
        ToolRunOutcome built =
                new ToolRunner(new ProcessService(Clock.systemUTC()), Duration.ofMinutes(5))
                        .run(build.command());
        assertTrue(built.exitedZero(), built.joinedOutput());
        Path v4 = build.indexFile();
        CometIndexDescription header = CometIndexHeaderReader.read(v4);
        assertEquals(4, header.formatVersion());
        assertEquals(IndexMode.PEPTIDE, header.type());

        try (RealProject project = RealProject.create(root.resolve("project"))) {
            SearchRequest forNewer =
                    new SearchRequest(
                            RealComet.model(
                                    RealComet.NEWER,
                                    v4,
                                    DecoySource.COMET_INTERNAL_CONCATENATED,
                                    4),
                            spectra,
                            RealComet.selection(RealComet.NEWER, newer),
                            IndexMode.NONE);
            RunBlockedException refused =
                    assertThrows(RunBlockedException.class, () -> project.prepare(forNewer));
            assertEquals(
                    "the run cannot start:\n- [index.format_unreadable] the index "
                            + v4
                            + " is in format v4 (written by Comet "
                            + header.cometVersion()
                            + "), and Comet 2026.03.0 reads index format v5, so it would stop"
                            + " before searching; rebuild the index from its FASTA with Comet"
                            + " 2026.03.0",
                    refused.getMessage());
            assertEquals(List.of(), project.runner().launches(), "nothing launched");
            assertEquals(List.of(), project.runDirectories(), "no run directory");

            SearchRequest forOlder =
                    new SearchRequest(
                            RealComet.model(
                                            RealComet.OLDER,
                                            v4,
                                            DecoySource.COMET_INTERNAL_CONCATENATED,
                                            4)
                                    .withText("index_search_type", "0", ValueOrigin.USER),
                            spectra,
                            RealComet.selection(RealComet.OLDER, older),
                            IndexMode.NONE);
            PreRunReport readiness = project.workflow().check(project.project(), forOlder);
            assertFalse(readiness.blocked(), readiness::message);
            PreparedRun accepted = project.prepare(forOlder);
            RunResult searched = project.run(accepted);
            assertEquals(
                    AttemptOutcome.SUCCEEDED,
                    searched.outcome(),
                    () -> searched.failures().toString());
            List<RealProject.Launch> launches = project.runner().launchesOf(older);
            assertEquals(2, launches.size());
            String run = accepted.layout().root().toString();
            assertEquals(
                    List.of(
                            older.toString(),
                            "-P" + run + "/parameters/comet.params",
                            "-N" + run + "/outputs/comet/k562_3",
                            inputs + "/k562_3.mzML"),
                    launches.get(0).command().argv());
            assertEquals(List.of(), project.runner().launchesOf(newer));
            RunIdentity identity = project.store().read(accepted.layout()).identity();
            assertEquals(v4, identity.fasta().path());
            assertEquals(DatabaseDelivery.PARAMETER_FILE, identity.databaseDelivery());
            Map<String, String> validated =
                    RunEvidence.finished(accepted.layout(), EngineStep.VALIDATE_COMET_OUTPUTS);
            RunEvidence.assertDetail(validated, "outputs.comet-01.targets", "1807");
            RunEvidence.assertDetail(validated, "outputs.comet-01.decoys", "1747");
        }
    }

    @Test
    @DisplayName(
            "index_search_type contradicting the index mode: only a warning before the build, then"
                    + " the built index is judged and the run fails before Comet searches")
    void anIndexSearchTypeContradictingTheModeFailsAfterTheBuild(@TempDir Path scratch)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Path root = scratch.toRealPath();
        Path comet = RealComet.stageComet(RealComet.NEWER, root.resolve("bin/comet"));
        Path inputs = Files.createDirectories(root.resolve("inputs"));
        List<Path> spectra = RealComet.spectra(inputs);
        Path fasta = RealComet.subset(inputs.resolve("subset.fasta"));
        try (RealProject project = RealProject.create(root.resolve("project"))) {
            SearchRequest request =
                    new SearchRequest(
                            RealComet.model(
                                            RealComet.NEWER,
                                            fasta,
                                            DecoySource.COMET_INTERNAL_CONCATENATED,
                                            4)
                                    .withText("index_search_type", "0", ValueOrigin.USER),
                            spectra,
                            RealComet.selection(RealComet.NEWER, comet),
                            IndexMode.FRAGMENT_ION);
            PreRunReport readiness = project.workflow().check(project.project(), request);
            assertFalse(readiness.blocked(), readiness::message);
            assertEquals(
                    List.of("index_search_type.ignored_without_idx"),
                    readiness.validation().warnings().stream()
                            .map(finding -> finding.rule().id())
                            .toList());
            PreparedRun prepared = project.prepare(request);
            RunResult result = project.run(prepared);
            assertEquals(AttemptOutcome.FAILED, result.outcome());
            assertEquals(
                    org.cometgui.workflow.state.StepState.FAILED,
                    result.states().get(EngineStep.BUILD_COMET_INDEX));
            String message = result.failures().get(EngineStep.BUILD_COMET_INDEX);
            Path index = prepared.indexFile().orElseThrow();
            assertEquals(
                    "the index "
                            + index
                            + " cannot be searched with these parameters:\n-"
                            + " [index.contradicts_search]"
                            + " the index "
                            + index
                            + " records \"IndexSearchType: fragment ion index\", and Comet"
                            + " searches an"
                            + " existing index with what it records, so index_search_type = 0 would"
                            + " be silently ignored; set index_search_type to 1, or rebuild the"
                            + " index"
                            + " from its FASTA with this search's settings",
                    message);
            assertEquals(1, project.runner().launchesOf(comet).size(), "the build, no search");
            assertFalse(
                    Files.exists(
                            RealComet.parentOf(prepared.indexFile().orElseThrow())
                                    .resolve("index.complete")),
                    "a judged-out index is never marked complete");
        }
    }
}
