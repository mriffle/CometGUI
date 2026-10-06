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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.tools.comet.CometIndexHeaderReader;
import org.cometgui.workflow.engine.ReuseRefusedException;
import org.cometgui.workflow.engine.RunRequest;
import org.cometgui.workflow.engine.RunResult;
import org.cometgui.workflow.engine.StepAction;
import org.cometgui.workflow.engine.StepStateListener;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.StepState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The index cache against the real Comet 2026.03.0: a cached index is re-hashed against its marker
 * and judged before it is searched, an entry is complete or absent, and an entry completed by
 * another run in the meantime is reused rather than rebuilt. In every refusal the search is never
 * launched.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "only the linux/x86-64 Comet binary has ever been executed in this project")
class RealIndexCacheTest {

    private record Built(
            RealProject project, SearchRequest request, Path comet, Path fasta, Path index) {}

    /** A project whose cache holds a complete fragment-ion index, built by a first run. */
    private static Built built(Path scratch)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Path root = scratch.toRealPath();
        Path comet = RealComet.stageComet(RealComet.NEWER, root.resolve("bin/comet"));
        Path inputs = Files.createDirectories(root.resolve("inputs"));
        List<Path> spectra = RealComet.spectra(inputs);
        Path fasta = RealComet.subset(inputs.resolve("subset.fasta"));
        RealProject project = RealProject.create(root.resolve("project"));
        SearchRequest request =
                new SearchRequest(
                        RealComet.model(
                                RealComet.NEWER, fasta, DecoySource.COMET_INTERNAL_CONCATENATED, 4),
                        spectra,
                        RealComet.selection(RealComet.NEWER, comet),
                        IndexMode.FRAGMENT_ION);
        PreparedRun first = project.prepare(request);
        RunResult result = project.run(first);
        assertEquals(
                AttemptOutcome.SUCCEEDED, result.outcome(), () -> result.failures().toString());
        assertEquals(3, project.runner().launchesOf(comet).size());
        return new Built(project, request, comet, fasta, first.indexFile().orElseThrow());
    }

    /** Runs a run prepared to reuse the cache after a change, which must fail in the index step. */
    private static String failsInTheIndexStep(Built built, PreparedRun prepared)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        RunResult result = built.project().run(prepared);
        assertEquals(AttemptOutcome.FAILED, result.outcome());
        assertEquals(StepState.FAILED, result.states().get(EngineStep.BUILD_COMET_INDEX));
        assertEquals(StepState.NOT_STARTED, result.states().get(EngineStep.RUN_COMET));
        assertEquals(3, built.project().runner().launchesOf(built.comet()).size(), "no search");
        return result.failures().get(EngineStep.BUILD_COMET_INDEX);
    }

    @Test
    @DisplayName("a cached index changed after it was built is refused, naming both digests")
    void aChangedCachedIndexIsRefused(@TempDir Path scratch)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Built built = built(scratch);
        try (RealProject project = built.project()) {
            Path marker = built.index().resolveSibling("index.complete");
            String recorded =
                    IndexCacheEntry.Completion.parse(Files.readString(marker))
                            .orElseThrow()
                            .sha256();
            long size = Files.size(built.index());
            PreparedRun second = project.prepare(built.request());
            assertFalse(second.buildsIndex());
            Files.write(built.index(), new byte[] {0}, StandardOpenOption.APPEND);
            assertEquals(
                    "the cached index "
                            + built.index()
                            + " has SHA-256 "
                            + RealComet.sha256(built.index())
                            + " ("
                            + (size + 1)
                            + " bytes), but its cache entry records "
                            + recorded
                            + " ("
                            + size
                            + " bytes); it changed after it was built and is not searched",
                    failsInTheIndexStep(built, second));
        }
    }

    @Test
    @DisplayName("an entry that was complete when the run was prepared and is not now is refused")
    void anEntryNoLongerCompleteIsRefused(@TempDir Path scratch)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Built built = built(scratch);
        try (RealProject project = built.project()) {
            PreparedRun second = project.prepare(built.request());
            Files.delete(built.index().resolveSibling("index.complete"));
            assertEquals(
                    "the cached index "
                            + built.index()
                            + " was complete when the run was prepared and is not now; the run"
                            + " declared no index build, so prepare a new run to rebuild it",
                    failsInTheIndexStep(built, second));
        }
    }

    @Test
    @DisplayName(
            "a cached index is judged before it is searched: a v4 index put in the entry after"
                    + " validation is refused by the index step")
    void aCachedIndexIsJudged(@TempDir Path scratch)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Built built = built(scratch);
        try (RealProject project = built.project()) {
            PreparedRun second = project.prepare(built.request());
            Map<EngineStep, StepAction> actions = new EnumMap<>(second.actions());
            actions.put(
                    EngineStep.VALIDATE_CONFIGURATION,
                    new StepFailureTest.ValidateThenTamper(
                            actions.get(EngineStep.VALIDATE_CONFIGURATION),
                            () -> {
                                IndexHeaders.write(
                                        built.index(), IndexHeaders.v4(built.fasta().toString()));
                                Files.writeString(
                                        built.index().resolveSibling("index.complete"),
                                        "cometgui-index-cache 1\nsha256 "
                                                + RealComet.sha256(built.index())
                                                + "\nsize "
                                                + Files.size(built.index())
                                                + "\n");
                            }));
            RunResult result =
                    project.engine()
                            .start(
                                    new RunRequest(
                                            project.store(),
                                            project.lock(),
                                            second.layout(),
                                            second.plan(),
                                            second.inputs(),
                                            Set.of(),
                                            actions,
                                            RealProject.application(),
                                            second.settings()),
                                    StepStateListener.NONE)
                            .await(RealProject.RUN_BOUND)
                            .orElseThrow();
            assertEquals(
                    StepState.SUCCEEDED, result.states().get(EngineStep.VALIDATE_CONFIGURATION));
            assertEquals(StepState.FAILED, result.states().get(EngineStep.BUILD_COMET_INDEX));
            assertEquals(StepState.NOT_STARTED, result.states().get(EngineStep.RUN_COMET));
            assertEquals(3, project.runner().launchesOf(built.comet()).size(), "no search");
            assertEquals(
                    "the index "
                            + built.index()
                            + " cannot be searched with these parameters:\n"
                            + "- [index.format_unreadable] the index "
                            + built.index()
                            + " is in format v4 (written by Comet 2026.02 rev. 2 (6edec91)), and"
                            + " Comet 2026.03.0 reads index format v5, so it would stop before"
                            + " searching; rebuild the index from its FASTA with Comet 2026.03.0",
                    result.failures().get(EngineStep.BUILD_COMET_INDEX));

            // The same cache entry, met by the next prepare: refused before a run exists.
            RunBlockedException refused =
                    org.junit.jupiter.api.Assertions.assertThrows(
                            RunBlockedException.class, () -> project.prepare(built.request()));
            assertTrue(
                    refused.getMessage()
                            .contains("- [index.format_unreadable] the index " + built.index()),
                    refused::getMessage);
            assertEquals(3, project.runner().launchesOf(built.comet()).size(), "still no search");
        }
    }

    @Test
    @DisplayName(
            "an entry completed by another run after this one was prepared is reused, not rebuilt")
    void anEntryCompletedMeanwhileIsReused(@TempDir Path scratch)
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
                                    4),
                            spectra,
                            RealComet.selection(RealComet.NEWER, comet),
                            IndexMode.FRAGMENT_ION);
            PreparedRun first = project.prepare(request);
            PreparedRun second = project.prepare(request);
            assertTrue(first.buildsIndex());
            assertTrue(second.buildsIndex());
            Path index = first.indexFile().orElseThrow();
            // A build that never finished left an index without a marker: cleared, not trusted.
            IndexHeaders.write(index, IndexHeaders.v5(fasta.toString()));
            assertEquals(AttemptOutcome.SUCCEEDED, project.run(first).outcome());
            assertEquals(5, CometIndexHeaderReader.read(index).formatVersion());
            assertTrue(Files.size(index) > 1_000_000, "the real index replaced the leftover");
            RunResult reused = project.run(second);
            assertEquals(
                    AttemptOutcome.SUCCEEDED, reused.outcome(), () -> reused.failures().toString());
            assertEquals(5, project.runner().launchesOf(comet).size(), "one build, four searches");
            RunEvidence.assertDetail(
                    RunEvidence.finished(second.layout(), EngineStep.BUILD_COMET_INDEX),
                    "index.cache",
                    "reused");
            RunEvidence.assertDetail(
                    RunEvidence.finished(second.layout(), EngineStep.BUILD_COMET_INDEX),
                    "index.sha256",
                    RealComet.sha256(index));
        }
    }
}
