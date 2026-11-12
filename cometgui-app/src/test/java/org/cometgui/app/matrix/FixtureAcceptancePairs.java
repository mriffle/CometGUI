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

package org.cometgui.app.matrix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.cometgui.app.config.ApplicationServices;
import org.cometgui.app.config.ProjectSession;
import org.cometgui.app.config.RunWiring;
import org.cometgui.app.config.SessionEngine;
import org.cometgui.app.matrix.MatrixSupport.Row;
import org.cometgui.app.matrix.VersionMatrixPairs.Fixture;
import org.cometgui.app.matrix.VersionMatrixPairs.Run;
import org.cometgui.app.testing.LimelightFiles;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.results.filtering.PeptideQValueFilter;
import org.cometgui.results.filtering.PsmQValueFilter;
import org.cometgui.results.parser.LearnedWeights;
import org.cometgui.results.parser.PercolatorOutputException;
import org.cometgui.results.parser.ResultTable;
import org.cometgui.results.parser.ResultTableReader;
import org.cometgui.results.parser.WeightsReader;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.ui.viewmodel.limelight.LimelightPort;
import org.cometgui.ui.viewmodel.limelight.LimelightResult;
import org.cometgui.ui.viewmodel.limelight.LimelightRunState;
import org.cometgui.ui.viewmodel.results.ResultsRun;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * The runs behind the fixture acceptance check (Phase 16 unit 2; PHASE-16, <em>The CI fixture</em>,
 * step 5): for every (Comet, Percolator) pair the product offers on this host, the whole Comet ->
 * Percolator run over the named fixture through the product's run port -- {@link
 * VersionMatrixPairs#runOnce}, the one way the version matrix, the determinism suite and the
 * performance suite run it, with the matrix's own Comet thread count and Percolator settings.
 *
 * <h2>Two configurations for each pair</h2>
 *
 * <ul>
 *   <li>{@value #MATRIX}: exactly the version matrix's pair run -- the release's own defaults,
 *       which report Comet's top five results for each spectrum. This is the run the reviewed
 *       goldens, the determinism suite and the performance suite are taken from.
 *   <li>{@value #TOP_ONE}: the same with {@code num_output_lines = 1}, and then the product's own
 *       Limelight conversion of that run ({@link RunWiring#limelight}). One result per spectrum is
 *       what the end-to-end scenario sets before it converts (Phase 14, E5): with the top five,
 *       Comet can report one spectrum twice with the same modified peptide, and the pinned
 *       converter refuses such a run whatever the FASTA. A Percolator build whose probe observed no
 *       {@code XML_OUTPUT} cannot feed the converter; its row says so and converts nothing.
 * </ul>
 *
 * <h2>What this class decides, and what it leaves to the check</h2>
 *
 * <p>It asserts only what is exact: the attempt succeeded; the product's readers parse every
 * Percolator table and the weights; the conversion was not refused, and the file it wrote is valid
 * against the schema inside the installed converter JAR, validated here with the JDK's own
 * validator, and holds one {@code psm} element per target PSM row. It records what the product
 * counted. <strong>It applies no floor.</strong> The floors, and the judgement of whether a FASTA
 * is fit to be the fixture, are {@code scripts/fixtures/fixture_acceptance.py}'s, which also counts
 * every figure again from the files, with its own reader.
 *
 * <p>No golden is read or written: a fixture with a new FASTA has a new digest and therefore no
 * reviewed golden yet ({@code R-TEST-07}), and this check must be able to judge it before one
 * exists.
 *
 * <h2>Every run stays on disk</h2>
 *
 * <p>Each run has its own fresh project, {@code <report>/runs/<pair>/<configuration>/project},
 * which is left there: the check reads the PIN files, the Percolator tables, the weights,
 * Percolator's own log and the Limelight XML from it. The report directory is the caller's, under
 * {@code _build/} or {@code scratch/}; nothing in it is ever committed.
 *
 * <p>The fixture is the version matrix's one named input ({@value MatrixSupport#FIXTURE}); without
 * it this class FAILS, saying so. Opt-in, like the matrix's classes (P15-5): it is not named {@code
 * *Test}, so a plain build never selects it, and it is disabled unless {@value
 * MatrixSupport#ENABLE}{@code =true}. {@code scripts/fixtures/check-fixture.sh} runs it.
 */
@EnabledIfSystemProperty(
        named = MatrixSupport.ENABLE,
        matches = "true",
        disabledReason =
                "fixture acceptance (Phase 16 unit 2): opt-in, run by"
                        + " scripts/fixtures/check-fixture.sh with -Dcometgui.matrix=true; it"
                        + " installs every offered Comet and Percolator from its upstream URL")
class FixtureAcceptancePairs {

    /** The configuration that is the version matrix's pair run, unchanged. */
    static final String MATRIX = "matrix";

    /** The configuration that reports one result per spectrum and is then converted. */
    static final String TOP_ONE = "top1";

    private static final BuildIdentity BUILD =
            BuildIdentity.of(
                    "0.0.0-fixture-acceptance", "unknown", Instant.parse("2026-11-12T00:00:00Z"));

    private static final String INSTALLED = "every tool is installed by the fixture acceptance run";

    @TempDir private static Path temporary;

    @AfterAll
    static void restore() {
        MatrixSupport.restoreGuard();
    }

    @Test
    @DisplayName(
            "every (Comet, Percolator) pair offered here: the whole workflow over the fixture in"
                    + " both configurations, and the product's Limelight conversion")
    void everyPair() throws IOException, InterruptedException {
        String named = System.getProperty(MatrixSupport.FIXTURE, "");
        assertFalse(
                named.isBlank(),
                "REFUSED: the fixture acceptance run needs its one named input, a directory of"
                        + " *.mzML and one *.fasta (scripts/fixtures/check-fixture.sh DIR). None"
                        + " was named, so nothing was searched and this is not a pass.");
        Fixture fixture = VersionMatrixPairs.fixture(Path.of(named), temporary.resolve("inputs"));

        MatrixSupport.openGuardForUpstream();
        Clock clock = Clock.systemUTC();
        ProcessService processes = new ProcessService(clock);
        ToolManager manager = MatrixSupport.toolManager(temporary.resolve("cache"), processes);
        List<ToolOffer> comets = new ArrayList<>();
        List<ToolOffer> percolators = new ArrayList<>();
        for (ToolOffer offer : manager.offers()) {
            if (offer.origin() != ToolOrigin.MANAGED
                    || offer.state() == ToolInstallState.UNAVAILABLE_ON_THIS_PLATFORM) {
                continue;
            }
            if (offer.tool() == ToolName.COMET) {
                comets.add(MatrixSupport.install(manager, offer.tool(), offer.version()));
            } else if (offer.tool() == ToolName.PERCOLATOR) {
                percolators.add(MatrixSupport.install(manager, offer.tool(), offer.version()));
            } else if (offer.tool() == ToolName.LIMELIGHT_CONVERTER) {
                MatrixSupport.install(manager, offer.tool(), offer.version());
            }
        }
        assertFalse(comets.isEmpty(), "no Comet is offered here");
        assertFalse(percolators.isEmpty(), "no Percolator is offered here");

        Map<String, Map<String, String>> configurations = new LinkedHashMap<>();
        configurations.put(MATRIX, Map.of());
        configurations.put(TOP_ONE, Map.of("num_output_lines", "1"));
        String host = MatrixSupport.host().id();
        List<String> failed = new ArrayList<>();
        for (ToolOffer comet : comets) {
            for (ToolOffer percolator : percolators) {
                for (Map.Entry<String, Map<String, String>> configuration :
                        configurations.entrySet()) {
                    String c = comet.version().text();
                    String p = percolator.version().text();
                    String key = "comet-" + c + "_percolator-" + p;
                    Row row =
                            Row.start(
                                    "acceptance:"
                                            + configuration.getKey()
                                            + ":comet-"
                                            + c
                                            + ":percolator-"
                                            + p
                                            + ":"
                                            + host);
                    row.observe("pair", key);
                    row.observe("configuration", configuration.getKey());
                    row.observe("cometEdits", new LinkedHashMap<>(configuration.getValue()));
                    row.observe("cometVersion", c);
                    row.observe("percolatorVersion", p);
                    row.observe("fixtureDigest", fixture.digest());
                    row.observe("fixtureFiles", fixture.files());
                    run(
                            row,
                            key,
                            configuration.getKey(),
                            configuration.getValue(),
                            new Tools(clock, processes, manager, comet, percolator, percolators),
                            fixture);
                    try {
                        row.finish();
                    } catch (AssertionError red) {
                        failed.add(red.getMessage());
                    }
                }
            }
        }
        assertEquals(List.of(), failed, "runs that failed");
    }

    /** What one run is made with. */
    private record Tools(
            Clock clock,
            ProcessService processes,
            ToolManager manager,
            ToolOffer comet,
            ToolOffer percolator,
            List<ToolOffer> every) {}

    /** One run in its own fresh project, which stays on disk. */
    private static void run(
            Row row,
            String key,
            String configuration,
            Map<String, String> edits,
            Tools tools,
            Fixture fixture)
            throws IOException {
        Path report = MatrixSupport.report();
        Path home = report.resolve("runs").resolve(key).resolve(configuration);
        Path project = home.resolve("project");
        assertFalse(
                Files.exists(project), project + " exists: every run starts in a fresh project");
        Files.createDirectories(home);
        ApplicationServices services =
                VersionMatrixPairs.services(tools.clock(), tools.processes());
        CachingHashService hashes = RunWiring.hasher();
        BoundedMessageLog console = new BoundedMessageLog();
        try (ProjectSession session =
                new ProjectSession(project, tools.clock(), services.runIds())) {
            SessionEngine engine =
                    RunWiring.port(
                            services,
                            console,
                            Optional.of(tools.manager()),
                            INSTALLED,
                            session,
                            BUILD,
                            hashes);
            Run[] ran = new Run[1];
            boolean succeeded =
                    row.check(
                            "the whole workflow ran through the product's run port and succeeded",
                            () -> {
                                Run run =
                                        VersionMatrixPairs.runOnce(
                                                engine,
                                                project,
                                                fixture,
                                                tools.comet(),
                                                tools.percolator(),
                                                tools.every(),
                                                VersionMatrixPairs.COMET_THREADS,
                                                PercolatorSettings.defaults(),
                                                edits);
                                ran[0] = run;
                                row.observe("runSeconds", run.seconds());
                                row.observe(
                                        "runDirectory",
                                        report.relativize(run.directory()).toString());
                                assertEquals(
                                        AttemptOutcome.SUCCEEDED,
                                        run.result().outcome(),
                                        () -> "the attempt: " + run.result().failures());
                            });
            if (!succeeded) {
                return;
            }
            Run run = ran[0];
            row.check(
                    "Percolator wrote its tables and its weights, and the product reads them",
                    () -> row.observe("product", counted(run.directory())));
            boolean xml =
                    tools.percolator().capabilities().stream()
                            .anyMatch(
                                    declared -> declared.capability() == ToolCapability.XML_OUTPUT);
            row.observe("xmlCapable", xml);
            row.observe("converts", TOP_ONE.equals(configuration) && xml);
            if (!TOP_ONE.equals(configuration) || !xml) {
                return;
            }
            row.check(
                    "the product's Limelight conversion wrote a file that is valid against the"
                            + " converter's own schema and holds one psm per target PSM row",
                    () ->
                            convert(
                                    row,
                                    report,
                                    run,
                                    services,
                                    session,
                                    hashes,
                                    console,
                                    engine,
                                    tools.manager()));
        }
    }

    /** The product's conversion of one finished run, and this test's own reading of the file. */
    private static void convert(
            Row row,
            Path report,
            Run run,
            ApplicationServices services,
            ProjectSession session,
            CachingHashService hashes,
            BoundedMessageLog console,
            SessionEngine engine,
            ToolManager manager)
            throws IOException {
        String id = run.result().manifest().run().runId().value();
        ResultsRun listed =
                RunWiring.results(services, session, hashes, BUILD, engine).runs().stream()
                        .filter(candidate -> candidate.id().value().equals(id))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new AssertionError(
                                                "the Results section does not list run " + id));
        LimelightPort limelight =
                RunWiring.limelight(
                        services,
                        console,
                        session,
                        hashes,
                        engine,
                        Optional.of(manager),
                        INSTALLED);
        LimelightRunState state = limelight.check(listed, "");
        assertEquals(
                List.of(),
                state.readiness().refusals(),
                "what the product refuses before converting");
        Path jar =
                state.readiness()
                        .converter()
                        .orElseThrow(() -> new AssertionError("no converter is installed"))
                        .jar();
        LimelightResult converted = limelight.convert(listed, state.saved(), "", line -> {});
        Map<String, Object> observed = new LinkedHashMap<>();
        row.observe("limelight", observed);
        observed.put("file", report.relativize(converted.file()).toString());
        observed.put("bytes", Files.size(converted.file()));
        observed.put("sha256", converted.sha256());
        observed.put("psms", converted.psms());
        observed.put("reportedPeptides", converted.reportedPeptides());
        observed.put("qCutoff", converted.qCutoff());
        observed.put("converterJarSha256", MatrixSupport.sha256(jar));
        LimelightFiles.assertValid(converted.file(), jar);
        observed.put("validAgainstConverterSchema", true);
        LimelightFiles.Read read = LimelightFiles.read(converted.file());
        observed.put("psmElementsCountedHere", read.psms());
        assertEquals(
                MatrixSupport.sha256(converted.file()),
                converted.sha256(),
                "the SHA-256 the product recorded for the file");
        assertEquals(
                converted.psms(),
                read.psms(),
                "the psm elements the product counted against this test's count");
        assertEquals(
                LimelightFiles.targetPsmRows(run.directory()),
                read.psms(),
                "one psm element per target PSM row");
        assertTrue(read.psms() > 0, "the Limelight XML holds no psm element");
    }

    /** What the product's own readers and default filters count in a run's Percolator outputs. */
    private static Map<String, Object> counted(Path run)
            throws IOException, PercolatorOutputException {
        Map<String, Object> counted = new LinkedHashMap<>();
        for (String name :
                List.of("psms.tsv", "peptides.tsv", "decoy-psms.tsv", "decoy-peptides.tsv")) {
            ResultTable table = ResultTableReader.readAll(VersionMatrixPairs.find(run, name));
            boolean peptides = name.contains("peptides");
            long passing =
                    table.rows().stream()
                            .filter(
                                    r ->
                                            peptides
                                                    ? PeptideQValueFilter.DEFAULT.passes(r)
                                                    : PsmQValueFilter.DEFAULT.passes(r))
                            .count();
            counted.put(name + ".rows", table.rows().size());
            counted.put(name + ".passingDefaultCutoff", passing);
        }
        LearnedWeights weights = WeightsReader.read(VersionMatrixPairs.find(run, "weights.txt"));
        counted.put("weights.splits", weights.splitCount());
        counted.put("weights.features", weights.featureNames().size());
        return counted;
    }
}
