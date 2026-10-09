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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.params.percolator.resolution.DownstreamStage;
import org.cometgui.provenance.events.ProvenanceEventType;
import org.cometgui.tools.percolator.SyntheticPin;
import org.cometgui.workflow.engine.DeclaredFile;
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
 * {@code finalise-results} (design decision P10-6) through the real engine and the real process
 * service, with the stand-in Percolator ({@link FakePercolator}) writing a hand-typed table and the
 * Comet steps replaced, as {@link PercolatorStepTest} does.
 *
 * <p>The table holds ten rows whose q-values are chosen so that every count is typed out here: at
 * the default cutoff 0.01, {@code 0}, {@code 0.005} and {@code 0.01} pass (the boundary is
 * inclusive); {@code 0.0100001}, {@code 0.5} and {@code 1} fail; an empty field, {@code NaN},
 * {@code 1.5} (out of range) and {@code 0,01} (a comma) are unknown. So 10 rows, 3 passing, 3
 * failing, 4 unknown -- in each of the four tables, since the stand-in writes one text to all four.
 *
 * <p>POSIX only: the stand-in is a shell script.
 */
@EnabledOnOs(
        value = {OS.LINUX, OS.MAC},
        disabledReason = "the stand-in Percolator is a POSIX shell script")
class FinaliseResultsTest {

    private static final String PREFIX = SyntheticPin.DECOY_PROTEIN_PREFIX;

    /** The hand-typed table, a printf format for the stand-in. */
    private static final String TABLE =
            "PSMId\\tscore\\tq-value\\tposterior_error_prob\\tpeptide\\tproteinIds\\n"
                    + "r1\\t9.0\\t0\\t0.0001\\tK.AAAK.R\\tsp|P1|A\\n"
                    + "r2\\t8.0\\t0.005\\t0.001\\tK.CCCK.R\\tsp|P2|B\\n"
                    + "r3\\t7.0\\t0.01\\t0.01\\tK.DDDK.R\\tsp|P3|C\\n"
                    + "r4\\t6.0\\t0.0100001\\t0.02\\tK.EEEK.R\\tsp|P4|D\\n"
                    + "r5\\t5.0\\t0.5\\t0.4\\tK.FFFK.R\\tsp|P5|E\\n"
                    + "r6\\t4.0\\t1\\t0.9\\tK.GGGK.R\\tsp|P6|F\\n"
                    + "r7\\t3.0\\t\\t0.9\\tK.HHHK.R\\tsp|P7|G\\n"
                    + "r8\\t2.0\\tNaN\\t0.9\\tK.IIIK.R\\tsp|P8|H\\n"
                    + "r9\\t1.0\\t1.5\\t0.9\\tK.KKKK.R\\tsp|P9|I\\n"
                    + "r10\\t0.5\\t0,01\\t0.9\\tK.LLLK.R\\tsp|P10|J\\n";

    /** The four tables' roles, as provenance and the step's details name them. */
    private static final List<String> TABLES =
            List.of(
                    "percolator-psms",
                    "percolator-peptides",
                    "percolator-decoy-psms",
                    "percolator-decoy-peptides");

    /** Stands in for a Comet step. */
    private record Nothing() implements StepAction {

        @Override
        public StepDeclaration declaration() {
            return StepDeclaration.NOTHING;
        }

        @Override
        public void execute(StepContext context) {}
    }

    /** Stands in for {@code merge-pin}: writes the synthetic 64 + 64 PIN. */
    private record WriteMergedPin(Path merged) implements StepAction {

        @Override
        public StepDeclaration declaration() {
            return new StepDeclaration(
                    List.of(DeclaredFile.output(RunDeclarations.MERGED_PIN, merged)), List.of());
        }

        @Override
        public void execute(StepContext context) throws IOException {
            Files.writeString(
                    merged,
                    SyntheticPin.of(64, SyntheticPin.PROBE_SEED),
                    StandardCharsets.US_ASCII);
        }
    }

    /** Parsing, then the target PSM table replaced by something no reader accepts. */
    private record ParseThenCorrupt(StepAction parse, Path table) implements StepAction {

        @Override
        public StepDeclaration declaration() {
            return parse.declaration();
        }

        @Override
        public void execute(StepContext context)
                throws StepFailedException, IOException, InterruptedException {
            parse.execute(context);
            Files.setPosixFilePermissions(table, PosixFilePermissions.fromString("rw-------"));
            Files.writeString(table, "this is not a Percolator table\n", StandardCharsets.UTF_8);
        }
    }

    private record Staged(FakeSearch search, Path percolator, RealProject project) {}

    private static Staged stage(Path directory) throws IOException {
        FakeSearch search = FakeSearch.create(directory);
        Path percolator =
                FakePercolator.write(
                        search.root().resolve("bin/percolator"),
                        FakePercolator.Behaviour.normal().withTable(TABLE));
        return new Staged(search, percolator, RealProject.create(search.project()));
    }

    private static PreparedRun prepare(Staged staged, Set<ToolCapability> capabilities)
            throws IOException, RunBlockedException {
        PercolatorChoice choice =
                FakePercolator.choice(
                        FakePercolator.offer(
                                staged.percolator(),
                                "3.07.1",
                                ToolOrigin.MANAGED,
                                capabilities,
                                List.of()),
                        List.of(
                                FakePercolator.offer(
                                        staged.percolator(),
                                        "3.07.1",
                                        ToolOrigin.MANAGED,
                                        capabilities,
                                        List.of())),
                        EnumSet.noneOf(DownstreamStage.class),
                        PercolatorSettings.defaults());
        return staged.project()
                .prepare(
                        new SearchRequest(
                                staged.search()
                                        .model(DecoySource.COMET_INTERNAL_CONCATENATED)
                                        .withText("decoy_prefix", PREFIX, ValueOrigin.USER),
                                staged.search().spectra(),
                                staged.search().selection(),
                                IndexMode.NONE,
                                Optional.of(choice)));
    }

    private static RunResult run(
            RealProject project, PreparedRun prepared, Map<EngineStep, StepAction> overrides)
            throws IOException, InterruptedException, ReuseRefusedException {
        Map<EngineStep, StepAction> actions = new EnumMap<>(prepared.actions());
        actions.put(EngineStep.RUN_COMET, new Nothing());
        actions.put(EngineStep.VALIDATE_COMET_OUTPUTS, new Nothing());
        actions.put(EngineStep.MERGE_PIN, new WriteMergedPin(prepared.layout().mergedPinFile()));
        actions.putAll(overrides);
        return project.engine()
                .start(
                        new RunRequest(
                                project.store(),
                                project.lock(),
                                prepared.layout(),
                                prepared.plan(),
                                prepared.inputs(),
                                Set.of(),
                                actions,
                                RealProject.application(),
                                prepared.settings()),
                        StepStateListener.NONE)
                .await(RealProject.RUN_BOUND)
                .orElseThrow();
    }

    private static Path out(PreparedRun prepared) {
        return prepared.percolatorOutputDirectory().orElseThrow();
    }

    /** The details every table carries: hand-typed above, the same in all four. */
    private static void assertTableCounts(Map<String, String> finished, String role, String store) {
        String prefix = "tables." + role + ".";
        RunEvidence.assertDetail(finished, prefix + "rows", "10");
        RunEvidence.assertDetail(finished, prefix + "store", store);
        RunEvidence.assertDetail(finished, prefix + "total", "10");
        RunEvidence.assertDetail(finished, prefix + "passing", "3");
        RunEvidence.assertDetail(finished, prefix + "failing", "3");
        RunEvidence.assertDetail(finished, prefix + "unknown-q", "4");
    }

    @Test
    @DisplayName(
            "every table counted at 0.01/0.01 equals the hand-typed counts; the weights summarised;"
                    + " in memory, so no index and no results/ directory; ordered between"
                    + " parse-percolator and finalise-provenance")
    void countsAtTheDefaultFilters(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Staged staged = stage(directory);
        try (RealProject project = staged.project()) {
            PreparedRun prepared = prepare(staged, FakePercolator.EVERY);
            RunResult result = run(project, prepared, Map.of());

            assertEquals(
                    AttemptOutcome.SUCCEEDED, result.outcome(), () -> result.failures().toString());
            assertEquals(StepState.SUCCEEDED, result.states().get(EngineStep.FINALISE_RESULTS));
            Map<String, String> finished =
                    RunEvidence.finished(prepared.layout(), EngineStep.FINALISE_RESULTS);
            RunEvidence.assertDetail(finished, "filters.psm", "0.01");
            RunEvidence.assertDetail(finished, "filters.peptide", "0.01");
            for (String role : TABLES) {
                assertTableCounts(finished, role, "memory");
            }
            RunEvidence.assertDetail(finished, "weights.splits", "2");
            RunEvidence.assertDetail(finished, "weights.features", "3");
            for (String table :
                    List.of("psms.tsv", "peptides.tsv", "decoy-psms.tsv", "decoy-peptides.tsv")) {
                assertEquals(
                        new IndependentTableCount(10, 3, 3, 4),
                        IndependentTableCount.at(
                                out(prepared).resolve(table), new BigDecimal("0.01")),
                        "the independent counter agrees with the hand-typed counts");
            }
            assertFalse(
                    Files.exists(prepared.layout().resultsDirectory()),
                    "no table is above the row limit, so nothing is indexed on disk");

            long parsed =
                    RunEvidence.sequenceOf(
                            prepared.layout(),
                            ProvenanceEventType.STAGE_FINISHED,
                            EngineStep.PARSE_PERCOLATOR);
            long started =
                    RunEvidence.sequenceOf(
                            prepared.layout(),
                            ProvenanceEventType.STAGE_STARTED,
                            EngineStep.FINALISE_RESULTS);
            long finalised =
                    RunEvidence.sequenceOf(
                            prepared.layout(),
                            ProvenanceEventType.STAGE_FINISHED,
                            EngineStep.FINALISE_RESULTS);
            long provenance =
                    RunEvidence.sequenceOf(
                            prepared.layout(),
                            ProvenanceEventType.STAGE_STARTED,
                            EngineStep.FINALISE_PROVENANCE);
            assertTrue(parsed < started, "finalise-results starts after parse-percolator ends");
            assertTrue(
                    finalised < provenance,
                    () ->
                            "finalise-provenance starts after finalise-results ends: "
                                    + finalised
                                    + " then "
                                    + provenance);
        }
    }

    @Test
    @DisplayName(
            "with the row limit lowered to 2, every table is indexed on disk under results/index/"
                    + " and nowhere else; the counts are the same; nothing new under outputs/;"
                    + " every raw output byte-identical across the step")
    void aTableAboveTheLimitIsIndexedUnderResults(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Staged staged = stage(directory);
        try (RealProject project = staged.project()) {
            PreparedRun prepared = prepare(staged, FakePercolator.EVERY);
            PercolatorRun half = prepared.percolatorRun().orElseThrow();
            Map<String, String> before = new TreeMap<>();
            Map<String, String> after = new TreeMap<>();
            RunResult result =
                    run(
                            project,
                            prepared,
                            Map.of(
                                    EngineStep.FINALISE_RESULTS,
                                    new RawHashingStep(
                                            new ResultSteps.FinaliseResults(half, 2),
                                            out(prepared),
                                            before,
                                            after)));

            assertEquals(
                    AttemptOutcome.SUCCEEDED, result.outcome(), () -> result.failures().toString());
            Map<String, String> finished =
                    RunEvidence.finished(prepared.layout(), EngineStep.FINALISE_RESULTS);
            for (String role : TABLES) {
                assertTableCounts(finished, role, "disk");
            }
            assertEquals(
                    List.of("index"), RunEvidence.listing(prepared.layout().resultsDirectory()));
            assertEquals(
                    List.of(
                            "decoy-peptides.index",
                            "decoy-psms.index",
                            "target-peptides.index",
                            "target-psms.index"),
                    RunEvidence.listing(prepared.layout().resultIndexDirectory()));
            assertEquals(
                    List.of(
                            "decoy-peptides.tsv",
                            "decoy-psms.tsv",
                            "peptides.tsv",
                            "psms.tsv",
                            "weights.txt"),
                    RunEvidence.listing(out(prepared)));
            assertEquals(
                    List.of("comet", "percolator"),
                    RunEvidence.listing(prepared.layout().outputsDirectory()));
            assertEquals(5, before.size());
            assertEquals(before, after, "finalise-results changed a raw output's bytes");
            for (String name : before.keySet()) {
                assertFalse(Files.isWritable(out(prepared).resolve(name)), name);
            }
        }
    }

    @Test
    @DisplayName(
            "a build without DECOY_OUTPUT and WEIGHTS_OUTPUT: the decoy tables and weights are not"
                    + " there to open, and that is not an error")
    void missingOptionalTablesAreNotAnError(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Staged staged = stage(directory);
        Set<ToolCapability> lacking = EnumSet.copyOf(FakePercolator.EVERY);
        lacking.removeAll(
                List.of(
                        ToolCapability.DECOY_OUTPUT,
                        ToolCapability.WEIGHTS_OUTPUT,
                        ToolCapability.XML_DECOY_OUTPUT));
        try (RealProject project = staged.project()) {
            PreparedRun prepared = prepare(staged, lacking);
            RunResult result = run(project, prepared, Map.of());

            assertEquals(
                    AttemptOutcome.SUCCEEDED, result.outcome(), () -> result.failures().toString());
            assertEquals(List.of("peptides.tsv", "psms.tsv"), RunEvidence.listing(out(prepared)));
            Map<String, String> finished =
                    RunEvidence.finished(prepared.layout(), EngineStep.FINALISE_RESULTS);
            assertTableCounts(finished, "percolator-psms", "memory");
            assertTableCounts(finished, "percolator-peptides", "memory");
            for (String key : finished.keySet()) {
                assertFalse(key.contains("decoy"), key);
                assertFalse(key.startsWith("weights."), key);
            }
        }
    }

    @Test
    @DisplayName(
            "a table the reader refuses fails finalise-results naming the file and its role, and"
                    + " core provenance is not finalised after it")
    void aRefusedTableFailsTheStep(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Staged staged = stage(directory);
        try (RealProject project = staged.project()) {
            PreparedRun prepared = prepare(staged, FakePercolator.EVERY);
            Path psms = out(prepared).resolve("psms.tsv");
            RunResult result =
                    run(
                            project,
                            prepared,
                            Map.of(
                                    EngineStep.PARSE_PERCOLATOR,
                                    new ParseThenCorrupt(
                                            prepared.actions().get(EngineStep.PARSE_PERCOLATOR),
                                            psms)));

            assertEquals(AttemptOutcome.FAILED, result.outcome());
            assertEquals(StepState.FAILED, result.states().get(EngineStep.FINALISE_RESULTS));
            assertEquals(
                    StepState.NOT_STARTED, result.states().get(EngineStep.FINALISE_PROVENANCE));
            String failure = result.failures().get(EngineStep.FINALISE_RESULTS);
            assertTrue(
                    failure.startsWith(
                            "the percolator-psms file "
                                    + psms
                                    + " cannot be finalised for the results: "),
                    failure);
        }
    }

    @Test
    @DisplayName(
            "the step declares the tables and the weights as inputs and nothing else: no pout XML,"
                    + " no index file, no output")
    void declaration(@TempDir Path directory) throws IOException, RunBlockedException {
        Staged staged = stage(directory);
        try (RealProject project = staged.project()) {
            Set<ToolCapability> every = EnumSet.copyOf(FakePercolator.EVERY);
            PercolatorChoice choice =
                    FakePercolator.choice(
                            FakePercolator.offer(
                                    staged.percolator(),
                                    "3.07.1",
                                    ToolOrigin.MANAGED,
                                    every,
                                    List.of()),
                            List.of(),
                            EnumSet.of(DownstreamStage.LIMELIGHT_CONVERSION),
                            PercolatorSettings.defaults());
            PreparedRun prepared =
                    project.prepare(
                            new SearchRequest(
                                    staged.search()
                                            .model(DecoySource.COMET_INTERNAL_CONCATENATED)
                                            .withText("decoy_prefix", PREFIX, ValueOrigin.USER),
                                    staged.search().spectra(),
                                    staged.search().selection(),
                                    IndexMode.NONE,
                                    Optional.of(choice)));
            Path out = out(prepared);
            assertTrue(prepared.percolatorArgv().orElseThrow().contains("-X"));
            assertEquals(
                    new StepDeclaration(
                            List.of(
                                    DeclaredFile.input("percolator-psms", out.resolve("psms.tsv")),
                                    DeclaredFile.input(
                                            "percolator-peptides", out.resolve("peptides.tsv")),
                                    DeclaredFile.input(
                                            "percolator-decoy-psms", out.resolve("decoy-psms.tsv")),
                                    DeclaredFile.input(
                                            "percolator-decoy-peptides",
                                            out.resolve("decoy-peptides.tsv")),
                                    DeclaredFile.input(
                                            "percolator-weights", out.resolve("weights.txt"))),
                            List.of()),
                    prepared.actions().get(EngineStep.FINALISE_RESULTS).declaration());
        }
    }
}
