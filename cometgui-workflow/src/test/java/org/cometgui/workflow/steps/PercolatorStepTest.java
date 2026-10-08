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
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.tools.ToolAdvisory;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.params.percolator.resolution.DownstreamStage;
import org.cometgui.provenance.manifest.FileDirection;
import org.cometgui.provenance.manifest.FileRecord;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ProvenanceStatus;
import org.cometgui.provenance.manifest.ToolRecord;
import org.cometgui.tools.percolator.SyntheticPin;
import org.cometgui.workflow.engine.DeclaredFile;
import org.cometgui.workflow.engine.ReuseRefusedException;
import org.cometgui.workflow.engine.RunRequest;
import org.cometgui.workflow.engine.RunResult;
import org.cometgui.workflow.engine.StepAction;
import org.cometgui.workflow.engine.StepContext;
import org.cometgui.workflow.engine.StepDeclaration;
import org.cometgui.workflow.engine.StepStateListener;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.StepState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The three Percolator steps through the real engine and the real process service, with a stand-in
 * Percolator ({@link FakePercolator}) and the Comet steps replaced: the merged PIN is CometGUI's
 * own synthetic 64 + 64 PIN ({@link SyntheticPin}, decoy proteins prefixed {@code decoy_}), written
 * where {@code merge-pin} writes it. The real-binary tests prove the happy paths on a real merged
 * PIN; these prove each refusal, each recorded fact and each capability-driven omission, quickly.
 * Every expected value is typed out here.
 *
 * <p>POSIX only: the stand-in is a shell script, and read-only is asserted on permission bits.
 */
@EnabledOnOs(
        value = {OS.LINUX, OS.MAC},
        disabledReason = "the stand-in Percolator is a POSIX shell script")
class PercolatorStepTest {

    /** The run's decoy prefix: the synthetic PIN's. */
    private static final String PREFIX = SyntheticPin.DECOY_PROTEIN_PREFIX;

    private static final ToolAdvisory ADVISORY =
            new ToolAdvisory(
                    "percolator.test-advisory", "A stand-in advisory, shown and recorded.");

    /** Does nothing and declares nothing: stands in for a Comet step. */
    private record Nothing() implements StepAction {

        @Override
        public StepDeclaration declaration() {
            return StepDeclaration.NOTHING;
        }

        @Override
        public void execute(StepContext context) {}
    }

    /** Stands in for {@code merge-pin}: writes the merged PIN, declared as its output. */
    private record WriteMergedPin(Path merged, String text) implements StepAction {

        @Override
        public StepDeclaration declaration() {
            return new StepDeclaration(
                    List.of(DeclaredFile.output(RunDeclarations.MERGED_PIN, merged)), List.of());
        }

        @Override
        public void execute(StepContext context) throws IOException {
            Files.writeString(merged, text, StandardCharsets.US_ASCII);
        }
    }

    /** The validate step, then a change -- after the pre-run check, before Percolator. */
    private record ValidateThen(StepAction validate, StepFailureTest.Tamper tamper)
            implements StepAction {

        @Override
        public StepDeclaration declaration() {
            return validate.declaration();
        }

        @Override
        public boolean validates() {
            return true;
        }

        @Override
        public void validate(StepContext context)
                throws org.cometgui.workflow.engine.StepFailedException,
                        IOException,
                        InterruptedException {
            validate.validate(context);
            tamper.apply();
        }

        @Override
        public void execute(StepContext context) {}
    }

    private record Staged(FakeSearch search, Path percolator, RealProject project) {}

    private static Staged stage(Path directory, FakePercolator.Behaviour behaviour)
            throws IOException {
        FakeSearch search = FakeSearch.create(directory);
        Path percolator = FakePercolator.write(search.root().resolve("bin/percolator"), behaviour);
        return new Staged(search, percolator, RealProject.create(search.project()));
    }

    private static ToolOffer offer(Path percolator, Set<ToolCapability> capabilities) {
        return FakePercolator.offer(
                percolator, "3.07.1", ToolOrigin.MANAGED, capabilities, List.of(ADVISORY));
    }

    private static PercolatorChoice choice(
            Path percolator, Set<ToolCapability> capabilities, Set<DownstreamStage> stages)
            throws IOException {
        ToolOffer offer = offer(percolator, capabilities);
        return FakePercolator.choice(offer, List.of(offer), stages, PercolatorSettings.defaults());
    }

    private static SearchRequest request(FakeSearch search, PercolatorChoice choice)
            throws IOException {
        return new SearchRequest(
                search.model(DecoySource.COMET_INTERNAL_CONCATENATED)
                        .withText("decoy_prefix", PREFIX, ValueOrigin.USER),
                search.spectra(),
                search.selection(),
                IndexMode.NONE,
                Optional.of(choice));
    }

    private static RunResult run(
            RealProject project,
            PreparedRun prepared,
            String mergedPin,
            Map<EngineStep, StepAction> overrides)
            throws IOException, InterruptedException, ReuseRefusedException {
        Map<EngineStep, StepAction> actions = new EnumMap<>(prepared.actions());
        actions.put(EngineStep.RUN_COMET, new Nothing());
        actions.put(EngineStep.VALIDATE_COMET_OUTPUTS, new Nothing());
        actions.put(
                EngineStep.MERGE_PIN,
                new WriteMergedPin(prepared.layout().mergedPinFile(), mergedPin));
        actions.putAll(overrides);
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

    private static RunResult run(RealProject project, PreparedRun prepared)
            throws IOException, InterruptedException, ReuseRefusedException {
        return run(project, prepared, pin(), Map.of());
    }

    /** The synthetic 64 + 64 PIN. */
    private static String pin() {
        return SyntheticPin.of(64, SyntheticPin.PROBE_SEED);
    }

    private static Path out(PreparedRun prepared) {
        return prepared.percolatorOutputDirectory().orElseThrow();
    }

    private static List<String> expectedArgv(
            Path percolator, PreparedRun prepared, boolean xml, boolean weights, boolean seed) {
        return expectedArgv(percolator, prepared, xml, weights, seed, true);
    }

    private static List<String> expectedArgv(
            Path percolator,
            PreparedRun prepared,
            boolean xml,
            boolean weights,
            boolean seed,
            boolean noAnalytics) {
        Path out = out(prepared);
        List<String> argv =
                new ArrayList<>(
                        List.of(
                                percolator.toString(),
                                "--results-psms",
                                out + "/psms.tsv",
                                "--results-peptides",
                                out + "/peptides.tsv",
                                "--decoy-results-psms",
                                out + "/decoy-psms.tsv",
                                "--decoy-results-peptides",
                                out + "/decoy-peptides.tsv"));
        if (weights) {
            argv.addAll(List.of("--weights", out + "/weights.txt"));
        }
        if (xml) {
            argv.addAll(List.of("-X", out + "/pout.xml"));
        }
        if (seed) {
            argv.addAll(List.of("--seed", "1"));
        }
        argv.addAll(
                List.of(
                        "--num-threads",
                        "3",
                        "--testFDR",
                        "0.01",
                        "--trainFDR",
                        "0.01",
                        "--maxiter",
                        "10"));
        if (noAnalytics) {
            argv.add("--no-analytics");
        }
        argv.add(prepared.layout().mergedPinFile().toString());
        return argv;
    }

    private static Map<String, String> filesByRole(
            ProvenanceManifest manifest, FileDirection direction) {
        Map<String, String> files = new TreeMap<>();
        for (FileRecord file : manifest.files()) {
            if (file.direction() == direction && file.role().startsWith("percolator")) {
                files.put(file.role(), file.path() + " " + file.status().wireName());
            }
        }
        return files;
    }

    @Test
    @DisplayName(
            "every capability, Limelight enabled: the hand-typed argv with -X, six artefacts"
                    + " written, read-only, parsed, and recorded with their roles")
    void aRunWritesParsesAndRecords(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Staged staged = stage(directory, FakePercolator.Behaviour.normal());
        try (RealProject project = staged.project()) {
            PreparedRun prepared =
                    project.prepare(
                            request(
                                    staged.search(),
                                    choice(
                                            staged.percolator(),
                                            FakePercolator.EVERY,
                                            EnumSet.of(DownstreamStage.LIMELIGHT_CONVERSION))));
            RunResult result = run(project, prepared);

            assertEquals(
                    AttemptOutcome.SUCCEEDED, result.outcome(), () -> result.failures().toString());
            for (EngineStep step :
                    List.of(
                            EngineStep.RESOLVE_PERCOLATOR,
                            EngineStep.RUN_PERCOLATOR,
                            EngineStep.PARSE_PERCOLATOR)) {
                assertEquals(StepState.SUCCEEDED, result.states().get(step), step.id());
            }
            Path out = out(prepared);
            assertEquals(
                    List.of(
                            "decoy-peptides.tsv",
                            "decoy-psms.tsv",
                            "peptides.tsv",
                            "pout.xml",
                            "psms.tsv",
                            "weights.txt"),
                    RunEvidence.listing(out));
            for (String name : RunEvidence.listing(out)) {
                Set<PosixFilePermission> permissions =
                        Files.getPosixFilePermissions(out.resolve(name));
                assertFalse(permissions.contains(PosixFilePermission.OWNER_WRITE), name);
                assertFalse(permissions.contains(PosixFilePermission.GROUP_WRITE), name);
                assertFalse(permissions.contains(PosixFilePermission.OTHERS_WRITE), name);
                assertTrue(permissions.contains(PosixFilePermission.OWNER_READ), name);
            }
            List<RealProject.Launch> launches = project.runner().launchesOf(staged.percolator());
            assertEquals(1, launches.size());
            List<String> argv = expectedArgv(staged.percolator(), prepared, true, true, true);
            assertEquals(argv, launches.get(0).command().argv());
            assertEquals(out, launches.get(0).command().workingDirectory());
            assertEquals(Map.of("LANG", "C.UTF-8"), launches.get(0).command().environment());
            assertEquals(Optional.of(argv), prepared.percolatorArgv());
            assertTrue(
                    Files.isRegularFile(
                            prepared.layout().logsDirectory().resolve("percolator.log")));

            Map<String, String> ran =
                    RunEvidence.finished(prepared.layout(), EngineStep.RUN_PERCOLATOR);
            RunEvidence.assertDetail(ran, "pin.targets", "64");
            RunEvidence.assertDetail(ran, "pin.decoys", "64");
            // ExpMass, CalcMass, feat1, feat2, feat3: the header's columns between ScanNr and
            // Peptide.
            RunEvidence.assertDetail(ran, "pin.features", "5");
            RunEvidence.assertDetail(ran, "outputs.count", "6");
            RunEvidence.assertDetail(ran, "outputs.read-only", "true");
            RunEvidence.assertDetail(ran, "invocations", "percolator");
            Map<String, String> parsed =
                    RunEvidence.finished(prepared.layout(), EngineStep.PARSE_PERCOLATOR);
            for (String table :
                    List.of(
                            "percolator-psms",
                            "percolator-peptides",
                            "percolator-decoy-psms",
                            "percolator-decoy-peptides")) {
                RunEvidence.assertDetail(parsed, "tables." + table + ".rows", "2");
                RunEvidence.assertDetail(parsed, "tables." + table + ".known-q", "1");
                RunEvidence.assertDetail(parsed, "tables." + table + ".unknown-q", "1");
            }
            RunEvidence.assertDetail(parsed, "weights.splits", "2");
            RunEvidence.assertDetail(parsed, "weights.features", "3");
            RunEvidence.assertDetail(parsed, "pout.psms", "2");
            RunEvidence.assertDetail(parsed, "pout.peptides", "1");
            RunEvidence.assertDetail(
                    parsed, "pout.namespace", "http://per-colator.com/percolator_out/15");

            ProvenanceManifest manifest = RunEvidence.manifest(prepared.layout());
            List<ToolRecord> tools =
                    RunEvidence.tools(manifest, CometWorkflow.PERCOLATOR_TOOL_NAME);
            assertEquals(1, tools.size());
            ToolRecord tool = tools.get(0);
            assertEquals(argv, tool.execution().command().argv());
            assertEquals(Optional.of("percolator"), tool.stageId());
            assertEquals("3.07.1", tool.version());
            assertTrue(tool.managed());
            assertEquals(RealComet.sha256(staged.percolator()), tool.hashes().sha256());
            assertEquals(
                    new java.util.TreeSet<>(
                            List.of(
                                    "DECOY_OUTPUT",
                                    "MAX_ITERATIONS_OPTION",
                                    "NO_ANALYTICS_OPTION",
                                    "PEPTIDE_TSV_OUTPUT",
                                    "PSM_TSV_OUTPUT",
                                    "SEED_OPTION",
                                    "TEST_FDR_OPTION",
                                    "THREAD_OPTION",
                                    "TRAIN_FDR_OPTION",
                                    "WEIGHTS_OUTPUT",
                                    "XML_DECOY_OUTPUT",
                                    "XML_OUTPUT")),
                    tool.capabilities());
            assertEquals(List.of("A stand-in advisory, shown and recorded."), tool.warnings());

            Path settingsFile = prepared.percolatorSettingsFile().orElseThrow();
            Map<String, String> inputs = new TreeMap<>();
            inputs.put("percolator-settings", settingsFile + " completed");
            inputs.put("percolator-psms", out.resolve("psms.tsv") + " completed");
            inputs.put("percolator-peptides", out.resolve("peptides.tsv") + " completed");
            inputs.put("percolator-decoy-psms", out.resolve("decoy-psms.tsv") + " completed");
            inputs.put(
                    "percolator-decoy-peptides", out.resolve("decoy-peptides.tsv") + " completed");
            inputs.put("percolator-weights", out.resolve("weights.txt") + " completed");
            inputs.put("percolator-pout-xml", out.resolve("pout.xml") + " completed");
            assertEquals(
                    inputs,
                    filesByRole(manifest, FileDirection.INPUT),
                    "the settings read by run-percolator, every artefact read by parse-percolator");
            Map<String, String> outputs = new TreeMap<>();
            outputs.put("percolator-psms", out.resolve("psms.tsv") + " completed");
            outputs.put("percolator-peptides", out.resolve("peptides.tsv") + " completed");
            outputs.put("percolator-decoy-psms", out.resolve("decoy-psms.tsv") + " completed");
            outputs.put(
                    "percolator-decoy-peptides", out.resolve("decoy-peptides.tsv") + " completed");
            outputs.put("percolator-weights", out.resolve("weights.txt") + " completed");
            outputs.put("percolator-pout-xml", out.resolve("pout.xml") + " completed");
            assertEquals(outputs, filesByRole(manifest, FileDirection.OUTPUT));
            for (FileRecord file : manifest.files()) {
                assertEquals(RealComet.sha256(file.path()), file.hashes().sha256(), file.role());
            }
            long mergedPinInputs =
                    manifest.files().stream()
                            .filter(
                                    file ->
                                            file.role().equals("merged-pin")
                                                    && file.direction() == FileDirection.INPUT)
                            .count();
            assertEquals(1, mergedPinInputs, "the merged PIN, read by run-percolator");

            assertEquals(
                    "{\n"
                            + "  \"schemaVersion\": 1,\n"
                            + "  \"settings\": {\n"
                            + "    \"test-fdr\": \"0.01\",\n"
                            + "    \"train-fdr\": \"0.01\",\n"
                            + "    \"random-seed\": \"1\",\n"
                            + "    \"maximum-iterations\": \"10\",\n"
                            + "    \"thread-count\": \"3\"\n"
                            + "  },\n"
                            + "  \"downstreamStages\": [\n"
                            + "    \"limelight-conversion\"\n"
                            + "  ]\n"
                            + "}\n",
                    Files.readString(settingsFile, StandardCharsets.UTF_8));
            assertEquals(
                    RealComet.sha256(settingsFile),
                    manifest.settings().get(PercolatorProvenance.SETTINGS_SHA256));
            assertEquals(
                    RealComet.sha256(settingsFile),
                    prepared.inputs()
                            .get(org.cometgui.workflow.state.InputKind.PERCOLATOR_SETTINGS)
                            .orElseThrow()
                            .digest(),
                    "the settings input's digest is the archived file's SHA-256");
            assertEquals("1", manifest.settings().get(PercolatorProvenance.SEED));
            assertEquals(
                    "requested (-X "
                            + out.resolve("pout.xml")
                            + "), because an enabled downstream stage needs it:"
                            + " limelight-conversion",
                    manifest.settings().get(PercolatorProvenance.POUT_XML));
            assertEquals(
                    "A stand-in advisory, shown and recorded.",
                    manifest.settings().get("percolator.advisory.percolator.test-advisory"));
            assertEquals(PREFIX, manifest.settings().get(PercolatorProvenance.DECOY_PREFIX));
            assertFalse(manifest.settings().containsKey(PercolatorProvenance.WEIGHTS_WARNING));
        }
    }

    @Test
    @DisplayName(
            "a merged PIN with no decoy row fails run-percolator before Percolator is launched,"
                    + " naming the decoy configuration, and the seed is still recorded")
    void aPinWithoutDecoysIsRefusedBeforeLaunch(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Staged staged = stage(directory, FakePercolator.Behaviour.normal());
        try (RealProject project = staged.project()) {
            PreparedRun prepared =
                    project.prepare(
                            request(
                                    staged.search(),
                                    choice(staged.percolator(), FakePercolator.EVERY, Set.of())));
            // CONSTRUCTED edge case (P9-13): the synthetic PIN with every decoy row removed.
            List<String> kept = new ArrayList<>();
            for (String line : pin().split("\n", -1)) {
                if (!line.isEmpty() && !line.split("\t", -1)[1].equals("-1")) {
                    kept.add(line);
                }
            }
            RunResult result = run(project, prepared, String.join("\n", kept) + "\n", Map.of());

            assertEquals(AttemptOutcome.FAILED, result.outcome());
            assertEquals(StepState.FAILED, result.states().get(EngineStep.RUN_PERCOLATOR));
            assertEquals(StepState.NOT_STARTED, result.states().get(EngineStep.PARSE_PERCOLATOR));
            assertEquals(
                    "Percolator was not started: the PIN file "
                            + prepared.layout().mergedPinFile()
                            + " holds 64 target rows and no decoy row (Label -1), so Percolator"
                            + " would have no negative examples; the decoy configuration was"
                            + " decoy_search = 1 (Comet's internal decoys, concatenated),"
                            + " decoy_prefix = \"decoy_\"",
                    result.failures().get(EngineStep.RUN_PERCOLATOR));
            assertEquals(List.of(), project.runner().launches(), "nothing was launched");
            ProvenanceManifest manifest = RunEvidence.manifest(prepared.layout());
            assertEquals(ProvenanceStatus.FAILED, manifest.run().status());
            assertEquals("1", manifest.settings().get(PercolatorProvenance.SEED));
            assertEquals(List.of(), RunEvidence.tools(manifest, "percolator"));
        }
    }

    @Test
    @DisplayName(
            "an archived percolator-settings.json changed after it was written fails"
                    + " run-percolator, naming the file and both digests, before any launch")
    void aChangedSettingsFileIsRefused(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Staged staged = stage(directory, FakePercolator.Behaviour.normal());
        try (RealProject project = staged.project()) {
            PreparedRun prepared =
                    project.prepare(
                            request(
                                    staged.search(),
                                    choice(staged.percolator(), FakePercolator.EVERY, Set.of())));
            Path settings = prepared.percolatorSettingsFile().orElseThrow();
            String recorded = RealComet.sha256(settings);
            long size = Files.size(settings);
            StepAction validate = prepared.actions().get(EngineStep.VALIDATE_CONFIGURATION);
            RunResult result =
                    run(
                            project,
                            prepared,
                            pin(),
                            Map.of(
                                    EngineStep.VALIDATE_CONFIGURATION,
                                    new ValidateThen(
                                            validate,
                                            () ->
                                                    Files.writeString(
                                                            settings,
                                                            " ",
                                                            StandardOpenOption.APPEND))));
            assertEquals(StepState.FAILED, result.states().get(EngineStep.RUN_PERCOLATOR));
            assertEquals(
                    "the run's archived Percolator settings file "
                            + settings
                            + " has SHA-256 "
                            + RealComet.sha256(settings)
                            + " ("
                            + (size + 1)
                            + " bytes), but the run recorded "
                            + recorded
                            + " ("
                            + size
                            + " bytes) when it was written; a run's settings are written once,"
                            + " so Percolator is not run",
                    result.failures().get(EngineStep.RUN_PERCOLATOR));
            assertEquals(List.of(), project.runner().launches());
        }
    }

    @Test
    @DisplayName(
            "resolve-percolator: an executable replaced after it was selected is refused, naming"
                    + " both digests; a non-executable one is refused too")
    void resolveRefusesAReplacedExecutable(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Staged staged = stage(directory, FakePercolator.Behaviour.normal());
        try (RealProject project = staged.project()) {
            PreparedRun prepared =
                    project.prepare(
                            request(
                                    staged.search(),
                                    choice(staged.percolator(), FakePercolator.EVERY, Set.of())));
            String selected = RealComet.sha256(staged.percolator());
            StepAction validate = prepared.actions().get(EngineStep.VALIDATE_CONFIGURATION);
            RunResult replaced =
                    run(
                            project,
                            prepared,
                            pin(),
                            Map.of(
                                    EngineStep.VALIDATE_CONFIGURATION,
                                    new ValidateThen(
                                            validate,
                                            () ->
                                                    Files.writeString(
                                                            staged.percolator(),
                                                            "# replaced\n",
                                                            StandardOpenOption.APPEND))));
            assertEquals(StepState.FAILED, replaced.states().get(EngineStep.RESOLVE_PERCOLATOR));
            assertEquals(
                    "the Percolator executable "
                            + staged.percolator()
                            + " has SHA-256 "
                            + RealComet.sha256(staged.percolator())
                            + ", but Percolator 3.07.1 was selected at "
                            + selected
                            + "; it was replaced after it was selected, and is not run",
                    replaced.failures().get(EngineStep.RESOLVE_PERCOLATOR));
            assertEquals(StepState.NOT_STARTED, replaced.states().get(EngineStep.RUN_PERCOLATOR));
            assertEquals(List.of(), project.runner().launches());
        }
    }

    @Test
    @DisplayName("resolve-percolator: an executable that can no longer be executed is refused")
    void resolveRefusesANonExecutable(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Staged staged = stage(directory, FakePercolator.Behaviour.normal());
        try (RealProject project = staged.project()) {
            PreparedRun prepared =
                    project.prepare(
                            request(
                                    staged.search(),
                                    choice(staged.percolator(), FakePercolator.EVERY, Set.of())));
            StepAction validate = prepared.actions().get(EngineStep.VALIDATE_CONFIGURATION);
            RunResult result =
                    run(
                            project,
                            prepared,
                            pin(),
                            Map.of(
                                    EngineStep.VALIDATE_CONFIGURATION,
                                    new ValidateThen(
                                            validate,
                                            () ->
                                                    Files.setPosixFilePermissions(
                                                            staged.percolator(),
                                                            Set.of(
                                                                    PosixFilePermission
                                                                            .OWNER_READ)))));
            assertEquals(
                    "the selected Percolator executable "
                            + staged.percolator()
                            + " does not exist or cannot be executed",
                    result.failures().get(EngineStep.RESOLVE_PERCOLATOR));
        }
    }

    private static RunResult failing(
            Path directory, FakePercolator.Behaviour behaviour, Set<DownstreamStage> stages)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Staged staged = stage(directory, behaviour);
        try (RealProject project = staged.project()) {
            PreparedRun prepared =
                    project.prepare(
                            request(
                                    staged.search(),
                                    choice(staged.percolator(), FakePercolator.EVERY, stages)));
            RunResult result = run(project, prepared);
            assertEquals(AttemptOutcome.FAILED, result.outcome());
            assertEquals(1, project.runner().launchesOf(staged.percolator()).size());
            return result;
        }
    }

    @Test
    @DisplayName("an artefact the command asked for and Percolator did not write fails the step")
    void aMissingArtefactFails(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        RunResult result =
                failing(
                        directory,
                        FakePercolator.Behaviour.normal().skipping("--weights"),
                        Set.of());
        String message = result.failures().get(EngineStep.RUN_PERCOLATOR);
        assertTrue(
                message.startsWith(
                                "Percolator exited 0 but its percolator-weights file "
                                        + directory.toRealPath().resolve("project/runs"))
                        && message.endsWith("/outputs/percolator/weights.txt was not written"),
                message);
    }

    @Test
    @DisplayName("an artefact Percolator wrote empty fails the step")
    void anEmptyArtefactFails(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        RunResult result =
                failing(
                        directory,
                        FakePercolator.Behaviour.normal().emptying("--results-psms"),
                        Set.of());
        String message = result.failures().get(EngineStep.RUN_PERCOLATOR);
        assertTrue(
                message.startsWith("Percolator exited 0 but its percolator-psms file ")
                        && message.endsWith("/outputs/percolator/psms.tsv is empty"),
                message);
    }

    @Test
    @DisplayName(
            "a file the command did not ask for -- XML when none was requested -- fails the step")
    void anUnexpectedXmlFails(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        RunResult result =
                failing(
                        directory,
                        FakePercolator.Behaviour.normal().alsoWriting("stray.xml"),
                        Set.of());
        String message = result.failures().get(EngineStep.RUN_PERCOLATOR);
        assertTrue(
                message.startsWith("Percolator wrote stray.xml in ")
                        && message.endsWith(
                                "/outputs/percolator, which its command did not ask for; this run"
                                        + " requested no pout XML and expects none"),
                message);
    }

    @Test
    @DisplayName("an unexpected file beside a requested pout XML fails without the no-XML clause")
    void anUnexpectedFileBesideXmlFails(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        RunResult result =
                failing(
                        directory,
                        FakePercolator.Behaviour.normal().alsoWriting("stray.txt"),
                        EnumSet.of(DownstreamStage.LIMELIGHT_CONVERSION));
        String message = result.failures().get(EngineStep.RUN_PERCOLATOR);
        assertTrue(
                message.startsWith("Percolator wrote stray.txt in ")
                        && message.endsWith(
                                "/outputs/percolator, which its command did not ask for"),
                message);
    }

    @Test
    @DisplayName("Percolator exiting non-zero fails the step, its outputs recorded partial")
    void aNonZeroExitFails(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Staged staged = stage(directory, FakePercolator.Behaviour.normal().exiting(3));
        try (RealProject project = staged.project()) {
            PreparedRun prepared =
                    project.prepare(
                            request(
                                    staged.search(),
                                    choice(staged.percolator(), FakePercolator.EVERY, Set.of())));
            RunResult result = run(project, prepared);
            assertEquals(
                    "invocation percolator exited with code 3; its log is "
                            + prepared.layout().logsDirectory().resolve("percolator.log"),
                    result.failures().get(EngineStep.RUN_PERCOLATOR));
            ProvenanceManifest manifest = RunEvidence.manifest(prepared.layout());
            assertEquals(
                    "partial",
                    filesByRole(manifest, FileDirection.OUTPUT)
                            .get("percolator-psms")
                            .split(" ")[1]);
            assertEquals("1", manifest.settings().get(PercolatorProvenance.SEED));
            assertEquals(
                    3, RunEvidence.tools(manifest, "percolator").get(0).execution().exitCode());
            assertTrue(
                    Files.getPosixFilePermissions(out(prepared).resolve("psms.tsv"))
                            .contains(PosixFilePermission.OWNER_WRITE),
                    "a failed run's outputs are partial, not made immutable");
        }
    }

    @Test
    @DisplayName("a table the parser refuses fails parse-percolator with the parser's message")
    void aMalformedTableFailsParsing(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Staged staged =
                stage(
                        directory,
                        FakePercolator.Behaviour.normal()
                                .withTable(
                                        "PSMId\\tscore\\tpeptide\\tproteinIds\\n"
                                                + "p1\\t2.5\\tK.P.R\\tA\\n"));
        try (RealProject project = staged.project()) {
            PreparedRun prepared =
                    project.prepare(
                            request(
                                    staged.search(),
                                    choice(staged.percolator(), FakePercolator.EVERY, Set.of())));
            RunResult result = run(project, prepared);
            assertEquals(StepState.SUCCEEDED, result.states().get(EngineStep.RUN_PERCOLATOR));
            assertEquals(StepState.FAILED, result.states().get(EngineStep.PARSE_PERCOLATOR));
            String message = result.failures().get(EngineStep.PARSE_PERCOLATOR);
            assertTrue(message.contains("psms.tsv"), message);
            assertTrue(message.contains("q-value"), message);
        }
    }

    @Test
    @DisplayName(
            "a build without WEIGHTS_OUTPUT, SEED_OPTION and XML_OUTPUT, Limelight enabled: none"
                    + " is passed, each omission and R-PERC-08's warning are recorded, the seed is"
                    + " recorded as not passed, and the run succeeds")
    void omissionsAreRecorded(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Staged staged = stage(directory, FakePercolator.Behaviour.normal());
        Set<ToolCapability> lacking = EnumSet.copyOf(FakePercolator.EVERY);
        lacking.removeAll(
                List.of(
                        ToolCapability.WEIGHTS_OUTPUT,
                        ToolCapability.SEED_OPTION,
                        ToolCapability.XML_OUTPUT,
                        ToolCapability.XML_DECOY_OUTPUT));
        try (RealProject project = staged.project()) {
            PreparedRun prepared =
                    project.prepare(
                            request(
                                    staged.search(),
                                    choice(
                                            staged.percolator(),
                                            lacking,
                                            EnumSet.of(DownstreamStage.LIMELIGHT_CONVERSION))));
            RunResult result = run(project, prepared);
            assertEquals(
                    AttemptOutcome.SUCCEEDED, result.outcome(), () -> result.failures().toString());
            List<String> argv = expectedArgv(staged.percolator(), prepared, false, false, false);
            assertEquals(
                    argv, project.runner().launchesOf(staged.percolator()).get(0).command().argv());
            assertEquals(
                    List.of("decoy-peptides.tsv", "decoy-psms.tsv", "peptides.tsv", "psms.tsv"),
                    RunEvidence.listing(out(prepared)));

            ProvenanceManifest manifest = RunEvidence.manifest(prepared.layout());
            Map<String, String> settings = manifest.settings();
            assertEquals("not-passed", settings.get(PercolatorProvenance.SEED));
            assertEquals(
                    "No random seed was passed: this Percolator build has no observed SEED_OPTION,"
                            + " so it ran with its own default seed, and the configured seed 1 was"
                            + " not used.",
                    settings.get(PercolatorProvenance.SEED_EXPLANATION));
            assertEquals("--weights", settings.get("percolator.not-emitted.01.option"));
            assertEquals("-X", settings.get("percolator.not-emitted.02.option"));
            assertEquals("--seed", settings.get("percolator.not-emitted.03.option"));
            assertFalse(settings.containsKey("percolator.not-emitted.04.option"));
            assertTrue(
                    settings.get("percolator.not-emitted.02.reason")
                            .contains("do not include XML_OUTPUT"),
                    settings.get("percolator.not-emitted.02.reason"));
            assertEquals(
                    "not requested: " + settings.get("percolator.not-emitted.02.reason"),
                    settings.get(PercolatorProvenance.POUT_XML));
            String warning = settings.get(PercolatorProvenance.WEIGHTS_WARNING);
            assertEquals(
                    "R-PERC-08: no learned weights file was written, so the weights are not"
                            + " available from a file: "
                            + settings.get("percolator.not-emitted.01.reason"),
                    warning);
            ToolRecord tool = RunEvidence.tools(manifest, "percolator").get(0);
            assertEquals(
                    List.of("A stand-in advisory, shown and recorded.", warning), tool.warnings());
            Map<String, String> parsed =
                    RunEvidence.finished(prepared.layout(), EngineStep.PARSE_PERCOLATOR);
            RunEvidence.assertDetail(parsed, "weights.status", warning);
            RunEvidence.assertDetail(parsed, "pout.status", "not requested");
            assertEquals(
                    Map.of(
                            "percolator-psms",
                            out(prepared).resolve("psms.tsv") + " completed",
                            "percolator-peptides",
                            out(prepared).resolve("peptides.tsv") + " completed",
                            "percolator-decoy-psms",
                            out(prepared).resolve("decoy-psms.tsv") + " completed",
                            "percolator-decoy-peptides",
                            out(prepared).resolve("decoy-peptides.tsv") + " completed"),
                    filesByRole(manifest, FileDirection.OUTPUT));
        }
    }

    @Test
    @DisplayName(
            "D-013: a build whose probe did NOT observe --no-analytics -- a stand-in that refuses"
                    + " it -- is run without it, succeeds, and provenance says analytics could not"
                    + " be switched off")
    void aBuildWithoutNoAnalyticsRunsAndSaysSo(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Staged staged =
                stage(directory, FakePercolator.Behaviour.normal().rejecting("--no-analytics"));
        Set<ToolCapability> lacking = EnumSet.copyOf(FakePercolator.EVERY);
        lacking.remove(ToolCapability.NO_ANALYTICS_OPTION);
        try (RealProject project = staged.project()) {
            PreparedRun prepared =
                    project.prepare(
                            request(
                                    staged.search(),
                                    choice(staged.percolator(), lacking, Set.of())));
            RunResult result = run(project, prepared);

            assertEquals(
                    AttemptOutcome.SUCCEEDED, result.outcome(), () -> result.failures().toString());
            List<String> argv =
                    expectedArgv(staged.percolator(), prepared, false, true, true, false);
            assertEquals(
                    argv, project.runner().launchesOf(staged.percolator()).get(0).command().argv());
            ProvenanceManifest manifest = RunEvidence.manifest(prepared.layout());
            ToolRecord tool = RunEvidence.tools(manifest, "percolator").get(0);
            assertEquals(argv, tool.execution().command().argv(), "recorded = launched");
            Map<String, String> settings = manifest.settings();
            assertEquals("--no-analytics", settings.get("percolator.not-emitted.01.option"));
            assertEquals(
                    "--no-analytics was not passed, so this Percolator may post usage analytics"
                            + " while it runs: the build's probed capabilities do not include"
                            + " NO_ANALYTICS_OPTION, and an option the build was not observed to"
                            + " accept is never passed (R-PERC-06)",
                    settings.get("percolator.not-emitted.01.reason"));
            assertFalse(settings.containsKey("percolator.not-emitted.02.option"));
        }
    }

    @Test
    @DisplayName(
            "prepare refuses, with no run directory, a Percolator that cannot write the target"
                    + " tables, or whose executable changed since it was selected")
    void prepareRefusesAnUnusablePercolator(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Staged staged = stage(directory, FakePercolator.Behaviour.normal());
        Set<ToolCapability> noTables = EnumSet.copyOf(FakePercolator.EVERY);
        noTables.remove(ToolCapability.PSM_TSV_OUTPUT);
        try (RealProject project = staged.project()) {
            RunBlockedException tables =
                    assertThrows(
                            RunBlockedException.class,
                            () ->
                                    project.prepare(
                                            request(
                                                    staged.search(),
                                                    choice(
                                                            staged.percolator(),
                                                            noTables,
                                                            Set.of()))));
            assertEquals(
                    "the run cannot start:\n- Percolator was not started: the build at "
                            + staged.percolator()
                            + " cannot write the target results this run reads -- its probed"
                            + " capabilities do not include PSM_TSV_OUTPUT (--results-psms)."
                            + " Choose a Percolator build whose probe observed both"
                            + " PSM_TSV_OUTPUT and PEPTIDE_TSV_OUTPUT",
                    tables.getMessage());

            PercolatorChoice choice = choice(staged.percolator(), FakePercolator.EVERY, Set.of());
            String selected = RealComet.sha256(staged.percolator());
            Files.writeString(staged.percolator(), "# changed\n", StandardOpenOption.APPEND);
            RunBlockedException changed =
                    assertThrows(
                            RunBlockedException.class,
                            () -> project.prepare(request(staged.search(), choice)));
            assertEquals(
                    "the run cannot start:\n- the Percolator executable "
                            + staged.percolator()
                            + " has SHA-256 "
                            + RealComet.sha256(staged.percolator())
                            + ", but Percolator 3.07.1 was selected at "
                            + selected
                            + "; it has changed since it was selected",
                    changed.getMessage());

            Files.setPosixFilePermissions(
                    staged.percolator(), Set.of(PosixFilePermission.OWNER_READ));
            assertEquals(
                    List.of(
                            "the selected Percolator executable "
                                    + staged.percolator()
                                    + " does not exist or cannot be executed"),
                    project.workflow()
                            .check(project.project(), request(staged.search(), choice))
                            .problems());
            assertEquals(List.of(), project.runDirectories());
            assertEquals(List.of(), project.runner().launches());
        }
    }
}
