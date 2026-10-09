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
import java.nio.file.attribute.PosixFilePermission;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.params.comet.writer.CanonicalParamsWriter;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.params.percolator.resolution.DownstreamStage;
import org.cometgui.params.percolator.resolution.PercolatorResolver;
import org.cometgui.provenance.events.ProvenanceEventType;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.provenance.manifest.FileDirection;
import org.cometgui.provenance.manifest.FileRecord;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ProvenanceStatus;
import org.cometgui.provenance.manifest.ToolRecord;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Phase 09 exit gate items 1, 2, 4 (provenance half), 5, 7 and 9, and {@code AC-RES-06}, {@code
 * AC-RES-07} and {@code AC-PRV-10}, against REAL runs through the real engine and the real process
 * service: Comet 2026.03.0 on the two K562 mzML and the proteome's first 1000 records ({@code
 * decoy_search = 1}, {@code num_threads = 4}), then Percolator.
 *
 * <p>Four runs share one project: 3.07.1 with Limelight conversion enabled (the resolved default
 * over {3.09 local, 3.07.1 managed}); 3.09 with Limelight disabled (the resolved default then);
 * 3.09 chosen by the user with Limelight enabled; and 3.07.1 chosen by the user with Limelight
 * disabled. Every build's capabilities are the real probe's verdict, run here. The zero-decoy case
 * uses Comet 2026.02.2's fragment-ion index ({@link #gate5TheRealZeroDecoyPin}).
 *
 * <p>Everything asserted is read back from disk -- the run directory, the event log, and {@code
 * provenance.json} through {@code ManifestReader} -- and every expected value is typed out here or
 * counted independently from the files.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "only the linux/x86-64 Comet and Percolator binaries have ever been executed in"
                        + " this project")
class RealPercolatorRunTest {

    @TempDir private static Path scratch;

    private static Path root;

    private static Path comet;

    private static List<Path> spectra;

    private static Path fasta;

    private static Path percolator3071;

    private static Path percolator309;

    private static Set<ToolCapability> probed3071;

    private static Set<ToolCapability> probed309;

    private static ToolOffer offer3071;

    private static ToolOffer offer309;

    private static RealProject project;

    private static Ran withLimelight3071;

    private static Ran withoutLimelight309;

    private static Ran chosen309WithLimelight;

    private static Ran chosen3071WithoutLimelight;

    /**
     * One run, and the raw outputs' SHA-256 just before and just after parsing, and just before and
     * just after {@code finalise-results}.
     */
    private record Ran(
            PreparedRun prepared,
            RunResult result,
            Map<String, String> beforeParse,
            Map<String, String> afterParse,
            Map<String, String> beforeFinalise,
            Map<String, String> afterFinalise) {

        Path out() {
            return prepared.percolatorOutputDirectory().orElseThrow();
        }

        ProvenanceManifest manifest() throws IOException {
            return RunEvidence.manifest(prepared.layout());
        }

        Map<String, String> settings() throws IOException {
            return manifest().settings();
        }

        ToolRecord percolatorTool() throws IOException {
            List<ToolRecord> tools =
                    RunEvidence.tools(manifest(), CometWorkflow.PERCOLATOR_TOOL_NAME);
            assertEquals(1, tools.size(), "one Percolator invocation");
            return tools.get(0);
        }

        List<String> recordedArgv() throws IOException {
            return percolatorTool().execution().command().argv();
        }
    }

    /** Parsing, with every raw output hashed independently just before and just after. */
    private record HashingAround(
            StepAction parse,
            List<Path> files,
            Map<String, String> before,
            Map<String, String> after)
            implements StepAction {

        @Override
        public StepDeclaration declaration() {
            return parse.declaration();
        }

        @Override
        public void execute(StepContext context)
                throws StepFailedException, IOException, InterruptedException {
            for (Path file : files) {
                before.put(String.valueOf(file.getFileName()), RealComet.sha256(file));
            }
            parse.execute(context);
            for (Path file : files) {
                after.put(String.valueOf(file.getFileName()), RealComet.sha256(file));
            }
        }
    }

    @BeforeAll
    static void searchProbeAndRun()
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        root = scratch.toRealPath();
        comet = RealComet.stageComet(RealComet.NEWER, root.resolve("bin/comet"));
        Path inputs = Files.createDirectories(root.resolve("inputs"));
        spectra = RealComet.spectra(inputs);
        fasta = RealComet.subset(inputs.resolve("subset.fasta"));
        percolator3071 = RealPercolator.stage3071(root.resolve("bin/percolator-3.07.1"));
        percolator309 = RealPercolator.wrapper309();
        probed3071 = RealPercolator.probe("3.07.1", percolator3071);
        probed309 = RealPercolator.probe("3.09", percolator309);
        offer3071 =
                RealPercolator.offer(
                        "3.07.1",
                        ToolOrigin.MANAGED,
                        percolator3071,
                        probed3071,
                        RealPercolator.ADVISORIES_3071);
        offer309 =
                RealPercolator.offer("3.09", ToolOrigin.LOCAL, percolator309, probed309, List.of());
        project = RealProject.create(root.resolve("project"));
        Set<DownstreamStage> limelight = EnumSet.of(DownstreamStage.LIMELIGHT_CONVERSION);
        Set<DownstreamStage> none = EnumSet.noneOf(DownstreamStage.class);
        withLimelight3071 = run(choice(offer3071, limelight));
        withoutLimelight309 = run(choice(offer309, none));
        chosen309WithLimelight = run(choice(offer309, limelight));
        chosen3071WithoutLimelight = run(choice(offer3071, none));
    }

    @AfterAll
    static void cleanUp() throws IOException {
        if (project != null) {
            project.close();
        }
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

    private static SearchRequest search(String release, Path database, Path cometBinary) {
        return new SearchRequest(
                RealComet.model(release, database, DecoySource.COMET_INTERNAL_CONCATENATED, 4),
                spectra,
                RealComet.selection(release, cometBinary),
                IndexMode.NONE);
    }

    private static Ran run(PercolatorChoice choice)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        PreparedRun prepared =
                project.prepare(search(RealComet.NEWER, fasta, comet).withPercolator(choice));
        Path out = prepared.percolatorOutputDirectory().orElseThrow();
        List<Path> files = new ArrayList<>();
        for (String name :
                List.of(
                        "psms.tsv",
                        "peptides.tsv",
                        "decoy-psms.tsv",
                        "decoy-peptides.tsv",
                        "weights.txt",
                        "pout.xml")) {
            if (prepared.percolatorArgv().orElseThrow().contains(out.resolve(name).toString())) {
                files.add(out.resolve(name));
            }
        }
        Map<String, String> before = new TreeMap<>();
        Map<String, String> after = new TreeMap<>();
        Map<EngineStep, StepAction> actions = new EnumMap<>(prepared.actions());
        actions.put(
                EngineStep.PARSE_PERCOLATOR,
                new HashingAround(actions.get(EngineStep.PARSE_PERCOLATOR), files, before, after));
        Map<String, String> beforeFinalise = new TreeMap<>();
        Map<String, String> afterFinalise = new TreeMap<>();
        actions.put(
                EngineStep.FINALISE_RESULTS,
                new RawHashingStep(
                        actions.get(EngineStep.FINALISE_RESULTS),
                        out,
                        beforeFinalise,
                        afterFinalise));
        RunResult result = start(prepared, actions);
        return new Ran(prepared, result, before, after, beforeFinalise, afterFinalise);
    }

    private static RunResult start(PreparedRun prepared, Map<EngineStep, StepAction> actions)
            throws IOException, InterruptedException, ReuseRefusedException {
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

    /** The Percolator launches of one run: its merged PIN is the argv's last element. */
    private static List<RealProject.Launch> percolatorLaunches(PreparedRun prepared) {
        List<RealProject.Launch> launches = new ArrayList<>();
        for (RealProject.Launch launch : project.runner().launches()) {
            List<String> argv = launch.command().argv();
            boolean percolator =
                    argv.get(0).equals(percolator3071.toString())
                            || argv.get(0).equals(percolator309.toString());
            if (percolator
                    && argv.get(argv.size() - 1)
                            .equals(prepared.layout().mergedPinFile().toString())) {
                launches.add(launch);
            }
        }
        return launches;
    }

    /** The rows of a PIN with one label, counted here. */
    private static long pinRows(Path pin, String label) throws IOException {
        long rows = 0;
        List<String> lines = Files.readAllLines(pin, StandardCharsets.ISO_8859_1);
        for (String line : lines.subList(1, lines.size())) {
            if (line.split("\t", -1)[1].equals(label)) {
                rows++;
            }
        }
        return rows;
    }

    /** A table's data rows, and how many have a q-value in [0, 1], counted here. */
    private static long[] tableCounts(Path table) throws IOException {
        List<String> lines = Files.readAllLines(table, StandardCharsets.UTF_8);
        long known = 0;
        for (String line : lines.subList(1, lines.size())) {
            try {
                double q = Double.parseDouble(line.split("\t", -1)[2]);
                if (q >= 0.0 && q <= 1.0) {
                    known++;
                }
            } catch (NumberFormatException unparsable) {
                // counted as unknown
            }
        }
        return new long[] {lines.size() - 1L, known};
    }

    private static List<String> xmlFilesUnder(Path directory) throws IOException {
        try (Stream<Path> walked = Files.walk(directory)) {
            return walked.map(Path::toString).filter(name -> name.endsWith(".xml")).toList();
        }
    }

    private static Map<String, String> outputHashes(ProvenanceManifest manifest, Path out) {
        Map<String, String> hashes = new TreeMap<>();
        for (FileRecord file : manifest.files()) {
            if (file.direction() == FileDirection.OUTPUT && file.path().startsWith(out)) {
                hashes.put(String.valueOf(file.path().getFileName()), file.hashes().sha256());
            }
        }
        return hashes;
    }

    private static Map<String, String> independentHashes(Path out) throws IOException {
        Map<String, String> hashes = new TreeMap<>();
        for (String name : RunEvidence.listing(out)) {
            hashes.put(name, RealComet.sha256(out.resolve(name)));
        }
        return hashes;
    }

    @Test
    @DisplayName(
            "the real probe's verdicts the runs are given: 3.07.1 every capability, 3.09 no XML")
    void theProbedSets() {
        Set<ToolCapability> every = EnumSet.copyOf(FakePercolator.EVERY);
        Set<ToolCapability> withoutXml = EnumSet.copyOf(every);
        withoutXml.remove(ToolCapability.XML_OUTPUT);
        withoutXml.remove(ToolCapability.XML_DECOY_OUTPUT);
        assertEquals(every, probed3071);
        assertEquals(withoutXml, probed309);
    }

    @Test
    @DisplayName(
            "gate 1, gate 2, AC-RES-06: 3.07.1 with Limelight enabled writes PSM, peptide, decoy,"
                    + " weights and pout XML; each parses, counted against the merged PIN and each"
                    + " other; the recorded argv carries -X and the pout.xml path")
    void gate1And2WithAnXmlCapableBuild() throws IOException {
        Ran ran = withLimelight3071;
        RunResult result = ran.result();
        assertEquals(
                AttemptOutcome.SUCCEEDED, result.outcome(), () -> result.failures().toString());
        Map<EngineStep, StepState> succeeded = new EnumMap<>(EngineStep.class);
        for (EngineStep step :
                List.of(
                        EngineStep.VALIDATE_CONFIGURATION,
                        EngineStep.RESOLVE_COMET,
                        EngineStep.RESOLVE_PERCOLATOR,
                        EngineStep.SERIALISE_COMET_PARAMS,
                        EngineStep.HASH_INPUTS,
                        EngineStep.RUN_COMET,
                        EngineStep.VALIDATE_COMET_OUTPUTS,
                        EngineStep.MERGE_PIN,
                        EngineStep.RUN_PERCOLATOR,
                        EngineStep.PARSE_PERCOLATOR,
                        EngineStep.FINALISE_RESULTS,
                        EngineStep.FINALISE_PROVENANCE)) {
            succeeded.put(step, StepState.SUCCEEDED);
        }
        assertEquals(succeeded, result.states());

        Path out = ran.out();
        assertEquals(
                List.of(
                        "decoy-peptides.tsv",
                        "decoy-psms.tsv",
                        "peptides.tsv",
                        "pout.xml",
                        "psms.tsv",
                        "weights.txt"),
                RunEvidence.listing(out));
        assertEquals(
                List.of("comet.params", "percolator-settings.json"),
                RunEvidence.listing(ran.prepared().layout().parametersDirectory()));
        assertEquals(
                List.of("comet-01.log", "comet-02.log", "percolator.log"),
                RunEvidence.listing(ran.prepared().layout().logsDirectory()));
        RunEvidence.stageLog(ran.prepared().layout().logsDirectory().resolve("percolator.log"));

        Path merged = ran.prepared().layout().mergedPinFile();
        long targets = pinRows(merged, "1");
        long decoys = pinRows(merged, "-1");
        assertEquals(3285, targets, "1807 + 1478 target rows in the merged PIN");
        assertEquals(3187, decoys, "1747 + 1440 decoy rows in the merged PIN");
        long[] psms = tableCounts(out.resolve("psms.tsv"));
        long[] peptides = tableCounts(out.resolve("peptides.tsv"));
        long[] decoyPsms = tableCounts(out.resolve("decoy-psms.tsv"));
        long[] decoyPeptides = tableCounts(out.resolve("decoy-peptides.tsv"));
        assertEquals(targets, psms[0], "one PSM row per target PIN row");
        assertEquals(decoys, decoyPsms[0], "one decoy PSM row per decoy PIN row");
        assertEquals(2482, peptides[0]);
        assertEquals(2399, decoyPeptides[0]);
        Map<String, String> parsed =
                RunEvidence.finished(ran.prepared().layout(), EngineStep.PARSE_PERCOLATOR);
        RunEvidence.assertDetail(parsed, "tables.percolator-psms.rows", Long.toString(psms[0]));
        RunEvidence.assertDetail(parsed, "tables.percolator-psms.known-q", Long.toString(psms[1]));
        RunEvidence.assertDetail(
                parsed, "tables.percolator-psms.unknown-q", Long.toString(psms[0] - psms[1]));
        RunEvidence.assertDetail(
                parsed, "tables.percolator-peptides.rows", Long.toString(peptides[0]));
        RunEvidence.assertDetail(
                parsed, "tables.percolator-decoy-psms.rows", Long.toString(decoyPsms[0]));
        RunEvidence.assertDetail(
                parsed, "tables.percolator-decoy-peptides.rows", Long.toString(decoyPeptides[0]));
        List<String> weights = Files.readAllLines(out.resolve("weights.txt"));
        long headers = weights.stream().filter(line -> line.endsWith("\tm0")).count();
        assertEquals(3, headers, "three cross-validation splits in the file, counted here");
        RunEvidence.assertDetail(parsed, "weights.splits", "3");
        RunEvidence.assertDetail(parsed, "weights.features", "22");
        RunEvidence.assertDetail(parsed, "pout.psms", Long.toString(psms[0]));
        RunEvidence.assertDetail(parsed, "pout.peptides", Long.toString(peptides[0]));
        RunEvidence.assertDetail(
                parsed, "pout.namespace", "http://per-colator.com/percolator_out/15");
        Map<String, String> ran2 =
                RunEvidence.finished(ran.prepared().layout(), EngineStep.RUN_PERCOLATOR);
        RunEvidence.assertDetail(ran2, "pin.targets", "3285");
        RunEvidence.assertDetail(ran2, "pin.decoys", "3187");

        List<String> argv = ran.recordedArgv();
        int x = argv.indexOf("-X");
        assertTrue(x > 0, () -> "no -X in the recorded argv " + argv);
        assertEquals(out.resolve("pout.xml").toString(), argv.get(x + 1));
        assertFalse(argv.contains("-Z"));
        List<RealProject.Launch> launches = percolatorLaunches(ran.prepared());
        assertEquals(1, launches.size());
        assertEquals(argv, launches.get(0).command().argv(), "recorded = launched");
        ToolRecord tool = ran.percolatorTool();
        assertEquals(RealPercolator.SHA256_3071, tool.hashes().sha256());
        assertEquals("3.07.1", tool.version());
        assertEquals(java.util.Optional.of("percolator"), tool.stageId());
        assertEquals(0, tool.execution().exitCode());
        TreeSet<String> ids = new TreeSet<>();
        probed3071.forEach(capability -> ids.add(capability.id()));
        assertEquals(ids, tool.capabilities());
        assertEquals(
                List.of(
                        RealPercolator.ADVISORIES_3071.get(0).text(),
                        RealPercolator.ADVISORIES_3071.get(1).text()),
                tool.warnings());
        System.out.printf(
                Locale.ROOT,
                "RealPercolatorRunTest 3.07.1 recorded argv %s%n  outputs/percolator %s%n",
                argv,
                sizes(out));
    }

    private static List<String> sizes(Path directory) throws IOException {
        List<String> sized = new ArrayList<>();
        for (String name : RunEvidence.listing(directory)) {
            sized.add(name + " " + Files.size(directory.resolve(name)));
        }
        return sized;
    }

    @Test
    @DisplayName(
            "gate 4 (provenance): over {3.09 local, 3.07.1 managed} with Limelight enabled,"
                    + " provenance records 3.07.1 selected, why, its advisories, and 3.09 skipped"
                    + " for XML_OUTPUT")
    void gate4TheSkippedVersionIsRecorded() throws IOException {
        Map<String, String> settings = withLimelight3071.settings();
        assertEquals("3.07.1", settings.get(PercolatorProvenance.VERSION));
        assertEquals("managed", settings.get(PercolatorProvenance.ORIGIN));
        assertEquals(RealPercolator.SHA256_3071, settings.get(PercolatorProvenance.BINARY_SHA256));
        assertEquals("resolved-default", settings.get(PercolatorProvenance.SELECTION));
        assertEquals("3.07.1 managed", settings.get(PercolatorProvenance.RESOLVED_DEFAULT));
        assertEquals("3.09 local", settings.get("percolator.skipped.01.version"));
        assertEquals("XML_OUTPUT", settings.get("percolator.skipped.01.missing"));
        assertFalse(settings.containsKey("percolator.skipped.02.version"));
        String reason = settings.get("percolator.skipped.01.reason");
        assertEquals(
                "Using Percolator 3.07.1 rather than 3.09 (registered local binary) because 3.09"
                        + " (registered local binary) lacks XML_OUTPUT, which Limelight conversion"
                        + " needs (the Limelight converter reads the Percolator XML that XML_OUTPUT"
                        + " writes).",
                reason,
                "gate 4: the recorded reason names the skipped version and the capability it"
                        + " lacks, in the resolver's own words");
        assertEquals(reason, settings.get(PercolatorProvenance.SELECTION_REASON));
        assertEquals(
                RealPercolator.ADVISORIES_3071.get(0).text(),
                settings.get(
                        "percolator.advisory.percolator.3-07-1-predates-i-spline-pep-regressor"));
        assertEquals(
                RealPercolator.ADVISORIES_3071.get(1).text(),
                settings.get("percolator.advisory.percolator.3-07-1-predates-pep-above-one-fix"));
        assertEquals(
                "DECOY_OUTPUT MAX_ITERATIONS_OPTION NO_ANALYTICS_OPTION PEPTIDE_TSV_OUTPUT"
                        + " PSM_TSV_OUTPUT SEED_OPTION TEST_FDR_OPTION THREAD_OPTION"
                        + " TRAIN_FDR_OPTION WEIGHTS_OUTPUT XML_DECOY_OUTPUT XML_OUTPUT",
                settings.get(PercolatorProvenance.CAPABILITIES));
        assertEquals("limelight-conversion", settings.get(PercolatorProvenance.DOWNSTREAM_STAGES));
        assertEquals("DECOY_", settings.get(PercolatorProvenance.DECOY_PREFIX));
        assertEquals("0.01", settings.get("percolator.test-fdr"));
        assertEquals("0.01", settings.get("percolator.train-fdr"));
        assertEquals("1", settings.get("percolator.random-seed"));
        assertEquals("10", settings.get("percolator.maximum-iterations"));
        assertEquals("3", settings.get("percolator.thread-count"));
        assertFalse(
                settings.keySet().stream()
                        .anyMatch(key -> key.startsWith("percolator.not-emitted.")));
        for (String key : settings.keySet()) {
            assertTrue(
                    key.matches(
                            org.cometgui.provenance.manifest.ProvenanceSchema.SETTINGS_KEY_PATTERN),
                    key);
        }
        System.out.printf(Locale.ROOT, "RealPercolatorRunTest 3.07.1 settings %s%n", settings);
    }

    @Test
    @DisplayName(
            "gate 2, AC-RES-07: 3.09 with Limelight disabled -- no XML option in the RECORDED"
                    + " argv, no .xml written, the run succeeds and the tables parse")
    void gate2ThreeNineWithoutLimelight() throws IOException {
        Ran ran = withoutLimelight309;
        assertEquals(
                AttemptOutcome.SUCCEEDED,
                ran.result().outcome(),
                () -> ran.result().failures().toString());
        List<String> argv = ran.recordedArgv();
        assertFalse(argv.contains("-X"), argv::toString);
        assertFalse(argv.contains("-Z"), argv::toString);
        assertFalse(argv.contains("--xmloutput"), argv::toString);
        assertFalse(argv.stream().anyMatch(element -> element.endsWith(".xml")), argv::toString);
        assertEquals(List.of(), xmlFilesUnder(ran.out()));
        assertEquals(
                List.of(
                        "decoy-peptides.tsv",
                        "decoy-psms.tsv",
                        "peptides.tsv",
                        "psms.tsv",
                        "weights.txt"),
                RunEvidence.listing(ran.out()));
        assertEquals(RealPercolator.SHA256_WRAPPER_309, ran.percolatorTool().hashes().sha256());
        assertFalse(ran.percolatorTool().managed());
        Map<String, String> settings = ran.settings();
        assertEquals("3.09", settings.get(PercolatorProvenance.VERSION));
        assertEquals("local", settings.get(PercolatorProvenance.ORIGIN));
        assertEquals("resolved-default", settings.get(PercolatorProvenance.SELECTION));
        assertEquals("none", settings.get(PercolatorProvenance.DOWNSTREAM_STAGES));
        assertEquals(
                "not requested: no enabled downstream stage needs pout XML, so none is written"
                        + " and none is expected",
                settings.get(PercolatorProvenance.POUT_XML));
        Map<String, String> parsed =
                RunEvidence.finished(ran.prepared().layout(), EngineStep.PARSE_PERCOLATOR);
        RunEvidence.assertDetail(parsed, "tables.percolator-psms.rows", "3285");
        RunEvidence.assertDetail(parsed, "tables.percolator-decoy-psms.rows", "3187");
        RunEvidence.assertDetail(parsed, "weights.splits", "3");
        RunEvidence.assertDetail(parsed, "pout.status", "not requested");
        System.out.printf(Locale.ROOT, "RealPercolatorRunTest 3.09 recorded argv %s%n", argv);
    }

    @Test
    @DisplayName(
            "gate 2: 3.09 chosen by the user with Limelight ENABLED -- still no XML option in the"
                    + " recorded argv, no .xml, success, and provenance says why: XML_OUTPUT"
                    + " absent")
    void gate2ThreeNineChosenWithLimelight() throws IOException {
        Ran ran = chosen309WithLimelight;
        assertEquals(
                AttemptOutcome.SUCCEEDED,
                ran.result().outcome(),
                () -> ran.result().failures().toString());
        List<String> argv = ran.recordedArgv();
        assertFalse(argv.contains("-X"), argv::toString);
        assertFalse(argv.stream().anyMatch(element -> element.endsWith(".xml")), argv::toString);
        assertEquals(List.of(), xmlFilesUnder(ran.out()));
        Map<String, String> settings = ran.settings();
        assertEquals("user-choice", settings.get(PercolatorProvenance.SELECTION));
        assertEquals("3.07.1 managed", settings.get(PercolatorProvenance.RESOLVED_DEFAULT));
        assertEquals("-X", settings.get("percolator.not-emitted.01.option"));
        String reason = settings.get("percolator.not-emitted.01.reason");
        assertTrue(reason.contains("XML_OUTPUT"), reason);
        assertEquals("not requested: " + reason, settings.get(PercolatorProvenance.POUT_XML));
        assertFalse(settings.containsKey("percolator.not-emitted.02.option"));
    }

    @Test
    @DisplayName(
            "3.07.1 chosen with Limelight disabled: XML-capable, but no stage needs XML, so no -X"
                    + " is recorded and none is written -- the XML decision reads the stages, not"
                    + " the build")
    void anXmlCapableBuildWithoutLimelightWritesNoXml() throws IOException {
        Ran ran = chosen3071WithoutLimelight;
        assertEquals(
                AttemptOutcome.SUCCEEDED,
                ran.result().outcome(),
                () -> ran.result().failures().toString());
        assertTrue(probed3071.contains(ToolCapability.XML_OUTPUT));
        List<String> argv = ran.recordedArgv();
        assertFalse(argv.contains("-X"), argv::toString);
        assertEquals(List.of(), xmlFilesUnder(ran.out()));
        Map<String, String> settings = ran.settings();
        assertEquals("user-choice", settings.get(PercolatorProvenance.SELECTION));
        assertEquals("3.09 local", settings.get(PercolatorProvenance.RESOLVED_DEFAULT));
        assertFalse(settings.containsKey("percolator.not-emitted.01.option"));
        assertEquals(
                "not requested: no enabled downstream stage needs pout XML, so none is written"
                        + " and none is expected",
                settings.get(PercolatorProvenance.POUT_XML));
    }

    @Test
    @DisplayName(
            "gate 7, AC-PRV-10: the effective seed and the JVM locale are in provenance.json of"
                    + " every run")
    void gate7TheSeedOfEveryRun() throws IOException {
        for (Ran ran :
                List.of(
                        withLimelight3071,
                        withoutLimelight309,
                        chosen309WithLimelight,
                        chosen3071WithoutLimelight)) {
            ProvenanceManifest manifest = ran.manifest();
            assertEquals("1", manifest.settings().get(PercolatorProvenance.SEED));
            assertEquals(Locale.getDefault(), manifest.application().locale());
            List<String> argv = ran.recordedArgv();
            assertEquals("1", argv.get(argv.indexOf("--seed") + 1), "the seed recorded is passed");
        }
    }

    @Test
    @DisplayName(
            "D-013: the argv recorded in provenance.json of every real run carries --no-analytics"
                    + " exactly once, last before the merged PIN, and is the argv launched")
    void everyRealRunPassesNoAnalytics() throws IOException {
        for (Ran ran :
                List.of(
                        withLimelight3071,
                        withoutLimelight309,
                        chosen309WithLimelight,
                        chosen3071WithoutLimelight)) {
            List<String> argv = ran.recordedArgv();
            assertEquals(
                    1,
                    argv.stream().filter("--no-analytics"::equals).count(),
                    () -> "--no-analytics once in the recorded argv " + argv);
            assertEquals(
                    List.of("--no-analytics", ran.prepared().layout().mergedPinFile().toString()),
                    argv.subList(argv.size() - 2, argv.size()),
                    argv::toString);
            List<RealProject.Launch> launches = percolatorLaunches(ran.prepared());
            assertEquals(
                    List.of(argv),
                    launches.stream().map(launch -> launch.command().argv()).toList(),
                    "recorded = launched");
            Set<String> recorded = ran.percolatorTool().capabilities();
            assertTrue(
                    recorded.contains("NO_ANALYTICS_OPTION"),
                    () -> "the real probe observed it: " + recorded);
            assertFalse(
                    ran.settings().containsValue("--no-analytics"),
                    "and no not-emitted entry names it");
        }
    }

    /**
     * {@code finalise-results} in the four real runs. Measured on 2026-10-09: this search -- the
     * two K562 files against only the proteome's first 1000 records -- leaves no row of any table
     * at or below 0.01 (the smallest q-value of the target PSMs is 0.018648, of the target peptides
     * 0.0274), so at the default filters every real table counts 0 passing and every row failing.
     * The comparison with the independent count still catches swapped categories and a cutoff of
     * 0.0187 or above, but it cannot tell 0.01 from a smaller cutoff; {@code FinaliseResultsTest}'s
     * hand-typed table, with rows at 0, 0.005, 0.01 and 0.0100001, is what does.
     */
    @Test
    @DisplayName(
            "P10-6: finalise-results SUCCEEDED in every real run, ordered after parse-percolator"
                    + " and before finalise-provenance in the event log; its counts at 0.01"
                    + " equal an independent split + BigDecimal count of each raw table; the"
                    + " weights' split and feature counts; every raw output byte-identical across"
                    + " it; nothing under outputs/ but Percolator's own files")
    void finaliseResultsInEveryRealRun() throws IOException {
        BigDecimal cutoff = new BigDecimal("0.01");
        for (Ran ran :
                List.of(
                        withLimelight3071,
                        withoutLimelight309,
                        chosen309WithLimelight,
                        chosen3071WithoutLimelight)) {
            RunLayout layout = ran.prepared().layout();
            assertEquals(
                    StepState.SUCCEEDED, ran.result().states().get(EngineStep.FINALISE_RESULTS));
            long parsed =
                    RunEvidence.sequenceOf(
                            layout,
                            ProvenanceEventType.STAGE_FINISHED,
                            EngineStep.PARSE_PERCOLATOR);
            long started =
                    RunEvidence.sequenceOf(
                            layout, ProvenanceEventType.STAGE_STARTED, EngineStep.FINALISE_RESULTS);
            long finished =
                    RunEvidence.sequenceOf(
                            layout,
                            ProvenanceEventType.STAGE_FINISHED,
                            EngineStep.FINALISE_RESULTS);
            long provenance =
                    RunEvidence.sequenceOf(
                            layout,
                            ProvenanceEventType.STAGE_STARTED,
                            EngineStep.FINALISE_PROVENANCE);
            assertTrue(parsed < started, () -> parsed + " then " + started);
            assertTrue(finished < provenance, () -> finished + " then " + provenance);

            Map<String, String> details = RunEvidence.finished(layout, EngineStep.FINALISE_RESULTS);
            RunEvidence.assertDetail(details, "filters.psm", "0.01");
            RunEvidence.assertDetail(details, "filters.peptide", "0.01");
            Map<String, String> tables = new TreeMap<>();
            tables.put("percolator-psms", "psms.tsv");
            tables.put("percolator-peptides", "peptides.tsv");
            tables.put("percolator-decoy-psms", "decoy-psms.tsv");
            tables.put("percolator-decoy-peptides", "decoy-peptides.tsv");
            for (Map.Entry<String, String> table : tables.entrySet()) {
                IndependentTableCount expected =
                        IndependentTableCount.at(ran.out().resolve(table.getValue()), cutoff);
                String prefix = "tables." + table.getKey() + ".";
                RunEvidence.assertDetail(details, prefix + "rows", Long.toString(expected.total()));
                RunEvidence.assertDetail(
                        details, prefix + "total", Long.toString(expected.total()));
                RunEvidence.assertDetail(
                        details, prefix + "passing", Long.toString(expected.passing()));
                RunEvidence.assertDetail(
                        details, prefix + "failing", Long.toString(expected.failing()));
                RunEvidence.assertDetail(
                        details, prefix + "unknown-q", Long.toString(expected.unknown()));
                RunEvidence.assertDetail(details, prefix + "store", "memory");
            }
            RunEvidence.assertDetail(details, "weights.splits", "3");
            RunEvidence.assertDetail(details, "weights.features", "22");
            assertFalse(
                    Files.exists(layout.resultsDirectory()),
                    "every real table is below the in-memory row limit, so nothing is indexed");
            assertEquals(
                    List.of("comet", "percolator"), RunEvidence.listing(layout.outputsDirectory()));

            assertEquals(RunEvidence.listing(ran.out()).size(), ran.beforeFinalise().size());
            assertEquals(
                    ran.beforeFinalise(), ran.afterFinalise(), "finalise-results changed a byte");
            assertEquals(independentHashes(ran.out()), ran.afterFinalise());
        }
        IndependentTableCount psms =
                IndependentTableCount.at(withLimelight3071.out().resolve("psms.tsv"), cutoff);
        System.out.printf(
                Locale.ROOT,
                "RealPercolatorRunTest finalise-results 3.07.1 target PSMs at 0.01: %s;"
                        + " details %s%n",
                psms,
                RunEvidence.finished(
                        withLimelight3071.prepared().layout(), EngineStep.FINALISE_RESULTS));
    }

    @Test
    @DisplayName(
            "gate 9: every raw output's SHA-256 is equal before parsing, after parsing, before and"
                    + " after finalise-results, after provenance finalisation and in provenance;"
                    + " each is read-only after success")
    void gate9RawOutputsAreUnchangedAndReadOnly() throws IOException {
        for (Ran ran :
                List.of(
                        withLimelight3071,
                        withoutLimelight309,
                        chosen309WithLimelight,
                        chosen3071WithoutLimelight)) {
            Path out = ran.out();
            Map<String, String> now = independentHashes(out);
            assertEquals(RunEvidence.listing(out).size(), ran.beforeParse().size());
            assertEquals(ran.beforeParse(), ran.afterParse(), "parsing changed a byte");
            assertEquals(
                    ran.beforeParse(),
                    ran.beforeFinalise(),
                    "something between parsing and finalise-results changed a byte");
            assertEquals(
                    ran.beforeFinalise(), ran.afterFinalise(), "finalise-results changed a byte");
            assertEquals(ran.beforeParse(), now, "finalisation changed a byte");
            assertEquals(now, outputHashes(ran.manifest(), out), "provenance records the bytes");
            for (String name : RunEvidence.listing(out)) {
                Path file = out.resolve(name);
                assertFalse(Files.isWritable(file), name + " is writable");
                Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file);
                assertFalse(permissions.contains(PosixFilePermission.OWNER_WRITE), name);
                assertFalse(permissions.contains(PosixFilePermission.GROUP_WRITE), name);
                assertFalse(permissions.contains(PosixFilePermission.OTHERS_WRITE), name);
            }
        }
    }

    /**
     * The rerun preview after a real run. Phase 09 pinned {@code finalise-provenance} as reused
     * after a Percolator change -- harmless then, because {@code finalise-results} was not planned
     * and no planned edge carried the change into core provenance. Phase 10 (design decision P10-6)
     * plans {@code finalise-results} whenever Percolator is planned, so a Percolator change
     * re-executes it and, through the declared edge {@code finalise-results ->
     * finalise-provenance}, core provenance after it: the specification's order, restored.
     */
    @Test
    @DisplayName(
            "the rerun preview after the real 3.07.1 run: nothing for the same configuration;"
                    + " Percolator, the results and core provenance (Comet reused) for a changed"
                    + " seed or another build")
    void rerunPreviewAfterARealPercolatorRun() throws IOException {
        Set<DownstreamStage> limelight = EnumSet.of(DownstreamStage.LIMELIGHT_CONVERSION);
        PercolatorChoice same = choice(offer3071, limelight);
        SearchRequest unchanged = search(RealComet.NEWER, fasta, comet).withPercolator(same);
        int launches = project.runner().launches().size();
        org.cometgui.workflow.engine.ReuseCheck nothing =
                project.workflow()
                        .preview(project.engine(), withLimelight3071.prepared(), unchanged);
        assertTrue(nothing.accepted(), nothing::message);
        assertEquals(Set.of(), nothing.preview().executed());
        assertEquals(
                Set.of(
                        EngineStep.SERIALISE_COMET_PARAMS,
                        EngineStep.RUN_COMET,
                        EngineStep.VALIDATE_COMET_OUTPUTS,
                        EngineStep.MERGE_PIN,
                        EngineStep.RUN_PERCOLATOR,
                        EngineStep.PARSE_PERCOLATOR,
                        EngineStep.FINALISE_RESULTS,
                        EngineStep.FINALISE_PROVENANCE),
                nothing.preview().reused());

        PercolatorChoice reseeded =
                new PercolatorChoice(
                        same.selection(),
                        same.settings().withRandomSeed(2),
                        same.enabledStages(),
                        same.resolution());
        for (PercolatorChoice changed : List.of(reseeded, choice(offer309, limelight))) {
            org.cometgui.workflow.state.RerunPreview preview =
                    project.workflow()
                            .preview(
                                    project.engine(),
                                    withLimelight3071.prepared(),
                                    search(RealComet.NEWER, fasta, comet).withPercolator(changed))
                            .preview();
            // The order Phase 10 restored (P10-6): finalise-results is planned with Percolator, and
            // the if-planned edge finalise-results -> finalise-provenance carries a Percolator
            // change into core provenance. Phase 09 pinned finalise-provenance as reused here,
            // because without finalise-results no planned edge reached it.
            assertEquals(
                    Set.of(
                            EngineStep.RUN_PERCOLATOR,
                            EngineStep.PARSE_PERCOLATOR,
                            EngineStep.FINALISE_RESULTS,
                            EngineStep.FINALISE_PROVENANCE),
                    preview.reExecuted());
            assertEquals(
                    Set.of(EngineStep.VALIDATE_CONFIGURATION, EngineStep.RESOLVE_PERCOLATOR),
                    preview.prepared());
            assertEquals(
                    Set.of(
                            EngineStep.SERIALISE_COMET_PARAMS,
                            EngineStep.RUN_COMET,
                            EngineStep.VALIDATE_COMET_OUTPUTS,
                            EngineStep.MERGE_PIN),
                    preview.reused());
        }
        assertEquals(launches, project.runner().launches().size(), "a preview launches nothing");
    }

    @Test
    @DisplayName(
            "gate 5: the REAL zero-decoy PIN (Comet 2026.02.2, fragment-ion index): the full run"
                    + " stops at Comet validation; driven past it, run-percolator refuses the"
                    + " merged PIN naming the decoy configuration; zero Percolator launches;"
                    + " the seed recorded in both")
    void gate5TheRealZeroDecoyPin(@TempDir Path elsewhere)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Path older = RealComet.stageComet(RealComet.OLDER, root.resolve("bin/comet-older"));
        Path prebuilt = Files.createDirectories(elsewhere.toRealPath().resolve("prebuilt"));
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
        PercolatorChoice choice =
                choice(offer3071, EnumSet.of(DownstreamStage.LIMELIGHT_CONVERSION));
        SearchRequest request =
                search(RealComet.OLDER, build.indexFile(), older).withPercolator(choice);

        PreparedRun full = project.prepare(request);
        RunResult stopped = start(full, full.actions());
        assertEquals(AttemptOutcome.FAILED, stopped.outcome());
        assertEquals(StepState.FAILED, stopped.states().get(EngineStep.VALIDATE_COMET_OUTPUTS));
        assertEquals(StepState.NOT_STARTED, stopped.states().get(EngineStep.RUN_PERCOLATOR));
        assertEquals(List.of(), percolatorLaunches(full));
        assertEquals(
                "1", RunEvidence.manifest(full.layout()).settings().get(PercolatorProvenance.SEED));

        // The rerun case: the merged PIN is used without Comet validation, so the Percolator
        // stage's
        // own check is the one that must refuse it.
        PreparedRun past = project.prepare(request);
        Map<EngineStep, StepAction> actions = new EnumMap<>(past.actions());
        actions.put(
                EngineStep.VALIDATE_COMET_OUTPUTS,
                new StepAction() {
                    @Override
                    public StepDeclaration declaration() {
                        return StepDeclaration.NOTHING;
                    }

                    @Override
                    public void execute(StepContext context) {}
                });
        RunResult refused = start(past, actions);
        assertEquals(AttemptOutcome.FAILED, refused.outcome());
        assertEquals(StepState.SUCCEEDED, refused.states().get(EngineStep.MERGE_PIN));
        assertEquals(StepState.FAILED, refused.states().get(EngineStep.RUN_PERCOLATOR));
        Path merged = past.layout().mergedPinFile();
        assertEquals(0, pinRows(merged, "-1"));
        assertEquals(198, pinRows(merged, "1"));
        assertEquals(
                "Percolator was not started: the PIN file "
                        + merged
                        + " holds 198 target rows and no decoy row (Label -1), so Percolator"
                        + " would have no negative examples; the decoy configuration was"
                        + " decoy_search = 1 (Comet's internal decoys, concatenated),"
                        + " decoy_prefix = \"DECOY_\"",
                refused.failures().get(EngineStep.RUN_PERCOLATOR));
        assertEquals(List.of(), percolatorLaunches(past), "Percolator was never launched");
        ProvenanceManifest manifest = RunEvidence.manifest(past.layout());
        assertEquals(ProvenanceStatus.FAILED, manifest.run().status());
        assertEquals("1", manifest.settings().get(PercolatorProvenance.SEED));
        assertEquals(Locale.getDefault(), manifest.application().locale());
    }
}
