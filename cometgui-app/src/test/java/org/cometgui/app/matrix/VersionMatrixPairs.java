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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.cometgui.app.config.ApplicationServices;
import org.cometgui.app.config.ClockRunIdSource;
import org.cometgui.app.config.FfmGlibcVersionSource;
import org.cometgui.app.config.PlatformFileSystemAccess;
import org.cometgui.app.config.ProjectSession;
import org.cometgui.app.config.RunWiring;
import org.cometgui.app.config.SessionEngine;
import org.cometgui.app.config.SystemEnvironmentReader;
import org.cometgui.app.matrix.MatrixSupport.Row;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.ports.EnvironmentReader;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.parser.ReleaseDefaults;
import org.cometgui.params.comet.schema.MetadataLoader;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.params.percolator.resolution.DownstreamStage;
import org.cometgui.params.percolator.resolution.PercolatorResolution;
import org.cometgui.params.percolator.resolution.PercolatorResolver;
import org.cometgui.provenance.manifest.FileDirection;
import org.cometgui.provenance.manifest.FileRecord;
import org.cometgui.provenance.manifest.ManifestReader;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ToolRecord;
import org.cometgui.results.filtering.PeptideQValueFilter;
import org.cometgui.results.filtering.PsmQValueFilter;
import org.cometgui.results.parser.LearnedWeights;
import org.cometgui.results.parser.ResultRow;
import org.cometgui.results.parser.ResultTable;
import org.cometgui.results.parser.ResultTableReader;
import org.cometgui.results.parser.WeightsReader;
import org.cometgui.tools.percolator.PoutDocument;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.ui.viewmodel.params.PercolatorRequest;
import org.cometgui.ui.viewmodel.params.RunNotStartedException;
import org.cometgui.ui.viewmodel.params.RunObserver;
import org.cometgui.workflow.engine.RunResult;
import org.cometgui.workflow.engine.StepTransition;
import org.cometgui.workflow.state.Plan;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * The fixture half of the version matrix (gate item 1; P15-4): the whole Comet -> Percolator run,
 * once for every (Comet, Percolator) pair the product offers on this host, through the product's
 * own run port ({@link RunWiring#port}: {@code WorkflowRunPort}, {@code CometWorkflow}, the
 * engine), with every tool installed from its pinned upstream URL into an empty cache by the
 * product's installer.
 *
 * <h2>The fixture is ONE named input, and without it this class fails</h2>
 *
 * <p>{@value MatrixSupport#FIXTURE} names a directory (the runner sets it from {@code
 * COMETGUI_MATRIX_FIXTURE}); its {@code *.mzML} files are the spectra and its one {@code *.fasta}
 * the database; nothing else in it is read. Its digest -- the SHA-256 of the sorted lines "{@code
 * <sha256> <name>}" of those files as found -- keys the goldens, so a {@code D-006} CI fixture adds
 * goldens beside the local fixture's rather than replacing them. An mzML that arrived with CR bytes
 * (the transit corruption {@code D-006} records) is searched as an LF copy, and both digests are
 * recorded. <strong>Run without the property, this class FAILS</strong>, saying so; it never passes
 * having searched nothing.
 *
 * <h2>What a pair's row holds</h2>
 *
 * <p>Exact invariants are asserted here: the attempt succeeded; one Comet invocation per spectrum
 * file and one Percolator invocation, each recorded with the installed executable's path and
 * SHA-256 and exit 0; {@code -X} recorded exactly when the build's probe observed {@code
 * XML_OUTPUT} (Limelight is enabled, so a stage needs XML); every output present; every recorded
 * file digest equal to this test's own; the merged PIN holding exactly the per-file PIN rows; and
 * the product's default filters agreeing with this test's own count. The golden values -- counts at
 * the default cut-off, decoy counts, the weights split count and a digest of the sorted selected
 * PSM identifiers -- are recorded in {@code metrics} and compared with the reviewed goldens by the
 * matrix ({@code R-TEST-07}): derived results only, never fixture content.
 */
@EnabledIfSystemProperty(
        named = MatrixSupport.ENABLE,
        matches = "true",
        disabledReason = MatrixSupport.DISABLED)
class VersionMatrixPairs {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-matrix", "unknown", Instant.parse("2026-11-06T00:00:00Z"));

    /** How long one whole run may take. A bound, never a delay. */
    private static final long RUN_MINUTES = 30;

    /** Comet's threads in every pair run, so the configuration is the same on every host. */
    static final String COMET_THREADS = "4";

    @TempDir private static Path temporary;

    @AfterAll
    static void restore() {
        MatrixSupport.restoreGuard();
    }

    /** The fixture as the runs see it. */
    record Fixture(String digest, List<Path> spectra, Path fasta, Map<String, Object> files) {}

    @Test
    @DisplayName(
            "every (Comet, Percolator) pair offered here runs the whole workflow over the fixture")
    void everyPair() throws IOException, InterruptedException {
        String named = System.getProperty(MatrixSupport.FIXTURE, "");
        assertFalse(
                named.isBlank(),
                "REFUSED: the fixture part of the version matrix needs its one named input,"
                        + " COMETGUI_MATRIX_FIXTURE (a directory of *.mzML and one *.fasta). It is"
                        + " not set, so nothing was searched and this is not a pass. D-006's CI"
                        + " fixture is OPEN; locally, scratch/fixture.");
        Fixture fixture = fixture(Path.of(named), temporary.resolve("inputs"));

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
            }
        }
        assertFalse(comets.isEmpty(), "no Comet is offered here");
        assertFalse(percolators.isEmpty(), "no Percolator is offered here");

        ApplicationServices services = services(clock, processes);
        Path projectDirectory = temporary.resolve("project");
        List<String> failed = new ArrayList<>();
        try (ProjectSession session =
                new ProjectSession(projectDirectory, clock, services.runIds())) {
            SessionEngine engine =
                    RunWiring.port(
                            services,
                            new BoundedMessageLog(),
                            Optional.of(manager),
                            "every tool is installed by the version matrix",
                            session,
                            BUILD);
            String host = MatrixSupport.host().id();
            for (ToolOffer comet : comets) {
                for (ToolOffer percolator : percolators) {
                    String c = comet.version().text();
                    String p = percolator.version().text();
                    Row row = Row.start("pair:comet-" + c + ":percolator-" + p + ":" + host);
                    row.observe("goldenKey", "comet-" + c + "_percolator-" + p);
                    row.observe("fixtureDigest", fixture.digest());
                    row.observe("fixtureFiles", fixture.files());
                    pair(row, engine, projectDirectory, fixture, comet, percolator, percolators);
                    try {
                        row.finish();
                    } catch (AssertionError red) {
                        failed.add(red.getMessage());
                    }
                }
            }
        }
        assertEquals(List.of(), failed, "pairs that failed");
    }

    // ------------------------------------------------------------------------------------------
    // The fixture
    // ------------------------------------------------------------------------------------------

    /** Reads the named directory, digests it, and stages the searched copies. */
    static Fixture fixture(Path directory, Path staging) throws IOException {
        assertTrue(
                Files.isDirectory(directory),
                "COMETGUI_MATRIX_FIXTURE " + directory + " is not a directory");
        List<Path> spectra = new ArrayList<>();
        List<Path> fastas = new ArrayList<>();
        try (Stream<Path> listed = Files.list(directory)) {
            for (Path file : listed.sorted().toList()) {
                String name = String.valueOf(file.getFileName());
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                if (name.endsWith(".mzML")) {
                    spectra.add(file);
                } else if (name.endsWith(".fasta")) {
                    fastas.add(file);
                }
            }
        }
        assertFalse(spectra.isEmpty(), directory + " holds no *.mzML");
        assertEquals(
                1, fastas.size(), () -> directory + " must hold exactly one *.fasta: " + fastas);
        List<Path> read = new ArrayList<>(spectra);
        read.addAll(fastas);
        read.sort(
                (left, right) ->
                        String.valueOf(left.getFileName())
                                .compareTo(String.valueOf(right.getFileName())));
        StringBuilder lines = new StringBuilder();
        Map<String, Object> files = new LinkedHashMap<>();
        for (Path file : read) {
            String sha = MatrixSupport.sha256(file);
            lines.append(sha).append("  ").append(file.getFileName()).append('\n');
            files.put(String.valueOf(file.getFileName()), sha);
        }
        String digest = sha256(lines.toString().getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(staging);
        List<Path> staged = new ArrayList<>();
        for (Path spectrum : spectra) {
            byte[] bytes = Files.readAllBytes(spectrum);
            Path copy = staging.resolve(String.valueOf(spectrum.getFileName()));
            if (containsCarriageReturn(bytes)) {
                ByteArrayOutputStream repaired = new ByteArrayOutputStream(bytes.length);
                for (byte value : bytes) {
                    if (value != '\r') {
                        repaired.write(value);
                    }
                }
                Files.write(copy, repaired.toByteArray());
                files.put(
                        copy.getFileName() + " (searched, CR removed)", MatrixSupport.sha256(copy));
            } else {
                Files.write(copy, bytes);
            }
            staged.add(copy);
        }
        Path fasta = staging.resolve(String.valueOf(fastas.get(0).getFileName()));
        Files.copy(fastas.get(0), fasta);
        return new Fixture(digest, List.copyOf(staged), fasta, files);
    }

    private static boolean containsCarriageReturn(byte[] bytes) {
        for (byte value : bytes) {
            if (value == '\r') {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------
    // One pair
    // ------------------------------------------------------------------------------------------

    private static void pair(
            Row row,
            SessionEngine engine,
            Path projectDirectory,
            Fixture fixture,
            ToolOffer comet,
            ToolOffer percolator,
            List<ToolOffer> every)
            throws IOException {
        Map<String, Object> metrics = new LinkedHashMap<>();
        row.observe("metrics", metrics);
        Path[] directory = new Path[1];
        boolean ran =
                row.check(
                        "the whole workflow ran through the product's run port and succeeded",
                        () -> {
                            Run run =
                                    runOnce(
                                            engine,
                                            projectDirectory,
                                            fixture,
                                            comet,
                                            percolator,
                                            every,
                                            COMET_THREADS,
                                            PercolatorSettings.defaults());
                            row.observe("runSeconds", run.seconds());
                            row.observe("runDirectory", run.name());
                            directory[0] = run.directory();
                            assertEquals(
                                    AttemptOutcome.SUCCEEDED,
                                    run.result().outcome(),
                                    () -> "the attempt: " + run.result().failures());
                        });
        if (!ran) {
            return;
        }
        Path run = directory[0];
        row.check(
                "provenance: one Comet invocation per spectrum file and one Percolator, each the"
                        + " installed executable at its digest, exit 0; -X exactly when XML_OUTPUT"
                        + " was observed",
                () -> {
                    ProvenanceManifest manifest =
                            ManifestReader.readFrom(new RunLayout(run).provenanceJsonFile());
                    List<ToolRecord> comets = tools(manifest, "comet");
                    List<ToolRecord> percolators = tools(manifest, "percolator");
                    assertEquals(fixture.spectra().size(), comets.size(), "Comet invocations");
                    assertEquals(1, percolators.size(), "Percolator invocations");
                    Path cometPath = comet.installedPath().orElseThrow();
                    for (ToolRecord record : comets) {
                        assertEquals(comet.version().text(), record.version());
                        assertEquals(cometPath, record.executablePath());
                        assertEquals(MatrixSupport.sha256(cometPath), record.hashes().sha256());
                        assertEquals(0, record.execution().exitCode());
                        assertEquals(
                                cometPath.toString(), record.execution().command().argv().get(0));
                    }
                    for (Path spectrum : fixture.spectra()) {
                        assertEquals(
                                1,
                                comets.stream()
                                        .filter(
                                                r ->
                                                        r.execution()
                                                                .command()
                                                                .argv()
                                                                .contains(spectrum.toString()))
                                        .count(),
                                "one Comet argv names " + spectrum);
                    }
                    ToolRecord record = percolators.get(0);
                    Path percolatorPath = percolator.installedPath().orElseThrow();
                    assertEquals(percolator.version().text(), record.version());
                    assertEquals(percolatorPath, record.executablePath());
                    assertEquals(MatrixSupport.sha256(percolatorPath), record.hashes().sha256());
                    assertEquals(0, record.execution().exitCode());
                    List<String> argv = record.execution().command().argv();
                    boolean xml =
                            percolator.capabilities().stream()
                                    .anyMatch(
                                            declared ->
                                                    declared.capability()
                                                            == ToolCapability.XML_OUTPUT);
                    row.observe("xmlCapable", xml);
                    assertEquals(xml, argv.contains("-X"), "-X against the probe: " + argv);
                    assertTrue(argv.contains("--seed"), "the seed is passed: " + argv);
                    row.observe(
                            "percolatorOptions",
                            argv.stream().filter(a -> a.startsWith("-")).toList());
                });
        row.check(
                "every output present, and every recorded file digest equal to this test's own",
                () -> {
                    ProvenanceManifest manifest =
                            ManifestReader.readFrom(new RunLayout(run).provenanceJsonFile());
                    long outputs = 0;
                    for (FileRecord file : manifest.files()) {
                        if (Files.isRegularFile(file.path())) {
                            assertEquals(
                                    MatrixSupport.sha256(file.path()),
                                    file.hashes().sha256(),
                                    "the recorded SHA-256 of " + file.path());
                        }
                        if (file.direction() == FileDirection.OUTPUT) {
                            outputs++;
                        }
                    }
                    assertTrue(outputs > 0, "provenance records no output");
                    for (String name :
                            List.of(
                                    "psms.tsv",
                                    "peptides.tsv",
                                    "decoy-psms.tsv",
                                    "decoy-peptides.tsv",
                                    "weights.txt")) {
                        Path file = find(run, name);
                        assertTrue(Files.size(file) > 0, name + " is empty");
                        assertTrue(
                                manifest.files().stream()
                                        .anyMatch(record -> record.path().equals(file)),
                                name + " is not in provenance");
                    }
                    assertEquals(
                            fixture.spectra().size(),
                            files(run, ".pep.xml").size(),
                            "one pepXML per spectrum file");
                });
        row.check(
                "the merged PIN holds exactly the rows of the per-file PINs",
                () -> {
                    Path merged = find(run, "merged.pin");
                    List<Path> perFile =
                            files(run, ".pin").stream()
                                    .filter(path -> !path.equals(merged))
                                    .toList();
                    assertEquals(
                            fixture.spectra().size(),
                            perFile.size(),
                            "one PIN per spectrum file: " + perFile);
                    long[] summed = new long[3];
                    for (Path pin : perFile) {
                        long[] counted = pinRows(pin);
                        for (int index = 0; index < 3; index++) {
                            summed[index] += counted[index];
                        }
                    }
                    long[] mergedRows = pinRows(merged);
                    assertEquals(
                            summed[0], mergedRows[0], "merged PIN rows against the per-file PINs");
                    assertEquals(summed[1], mergedRows[1], "merged PIN target rows");
                    assertEquals(summed[2], mergedRows[2], "merged PIN decoy rows");
                    metrics.put("pin.rows", mergedRows[0]);
                    metrics.put("pin.targetRows", mergedRows[1]);
                    metrics.put("pin.decoyRows", mergedRows[2]);
                });
        row.check(
                "results parsed by the product, its default filters agreeing with this test's own"
                        + " count",
                () -> {
                    for (String name :
                            List.of(
                                    "psms.tsv",
                                    "peptides.tsv",
                                    "decoy-psms.tsv",
                                    "decoy-peptides.tsv")) {
                        Path file = find(run, name);
                        ResultTable table = ResultTableReader.readAll(file);
                        long lines = Files.readAllLines(file, StandardCharsets.UTF_8).size() - 1L;
                        assertEquals(lines, table.rows().size(), name + ": rows against lines - 1");
                        boolean peptides = name.contains("peptides");
                        List<ResultRow> passing =
                                table.rows().stream()
                                        .filter(
                                                r ->
                                                        peptides
                                                                ? PeptideQValueFilter.DEFAULT
                                                                        .passes(r)
                                                                : PsmQValueFilter.DEFAULT.passes(r))
                                        .toList();
                        assertEquals(
                                VersionMatrixTools.independentAtOrBelow(table, "0.01"),
                                passing.size(),
                                name + ": the product's default filter against this test's count");
                        metrics.put(name + ".rows", table.rows().size());
                        metrics.put(name + ".passingDefaultCutoff", passing.size());
                        List<String> identifiers =
                                passing.stream()
                                        .map(
                                                r ->
                                                        peptides
                                                                ? r.peptide()
                                                                : withoutDirectory(r.psmId()))
                                        .sorted()
                                        .toList();
                        metrics.put(
                                name + ".selectedIdentifiersSha256",
                                sha256(
                                        (String.join("\n", identifiers) + "\n")
                                                .getBytes(StandardCharsets.UTF_8)));
                        row.observe(
                                name + ".selectedSample",
                                identifiers.subList(0, Math.min(3, identifiers.size())));
                    }
                    LearnedWeights weights = WeightsReader.read(find(run, "weights.txt"));
                    metrics.put("weights.splits", weights.splitCount());
                    metrics.put("weights.features", weights.featureNames().size());
                    Optional<Path> pout = optionalFind(run, "pout.xml");
                    if (pout.isPresent()) {
                        PoutDocument document = PoutDocument.read(pout.get());
                        assertEquals(PoutDocument.NAMESPACE, document.namespace());
                        metrics.put("pout.psms", document.psmCount());
                        metrics.put("pout.decoyPsms", document.decoyPsmCount());
                        metrics.put("pout.peptides", document.peptideCount());
                    }
                });
    }

    /**
     * One finished run of the whole workflow.
     *
     * @param directory the run's directory
     * @param name the run directory's name
     * @param seconds the wall clock it took
     * @param result the engine's result
     */
    record Run(Path directory, String name, long seconds, RunResult result) {}

    /**
     * Runs the whole workflow once through the product's run port: the release's own defaults with
     * the fixture's FASTA, decoys searched, the given Comet thread count and Percolator settings,
     * and Limelight enabled so that a stage needs XML. Shared by the matrix and the determinism
     * suite ({@link DeterminismPairs}).
     *
     * @param engine the session's run port
     * @param projectDirectory the session's project directory
     * @param fixture the fixture
     * @param comet the Comet build
     * @param percolator the Percolator build
     * @param every every Percolator build offered, for the product's resolution
     * @param cometThreads the {@code num_threads} text
     * @param settings the Percolator settings
     * @return the run, which exactly one new run directory holds
     * @throws IOException if the project cannot be read
     * @throws InterruptedException if interrupted while waiting
     * @throws ExecutionException if the observer's result failed
     * @throws TimeoutException if the run did not finish in time
     * @throws RunNotStartedException if the product refused to start it
     */
    static Run runOnce(
            SessionEngine engine,
            Path projectDirectory,
            Fixture fixture,
            ToolOffer comet,
            ToolOffer percolator,
            List<ToolOffer> every,
            String cometThreads,
            PercolatorSettings settings)
            throws IOException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException,
                    RunNotStartedException {
        return runOnce(
                engine,
                projectDirectory,
                fixture,
                comet,
                percolator,
                every,
                cometThreads,
                settings,
                Map.of());
    }

    /**
     * {@link #runOnce(SessionEngine, Path, Fixture, ToolOffer, ToolOffer, List, String,
     * PercolatorSettings)} with further Comet parameters set as a scientist would set them, after
     * the three every run sets: the fixture acceptance run ({@link FixtureAcceptancePairs}) reports
     * one result per spectrum for the run it converts to Limelight XML.
     *
     * @param engine the session's run port
     * @param projectDirectory the session's project directory
     * @param fixture the fixture
     * @param comet the Comet build
     * @param percolator the Percolator build
     * @param every every Percolator build offered, for the product's resolution
     * @param cometThreads the {@code num_threads} text
     * @param settings the Percolator settings
     * @param edits further Comet parameters, name to text, applied in iteration order
     * @return the run, which exactly one new run directory holds
     * @throws IOException if the project cannot be read
     * @throws InterruptedException if interrupted while waiting
     * @throws ExecutionException if the observer's result failed
     * @throws TimeoutException if the run did not finish in time
     * @throws RunNotStartedException if the product refused to start it
     */
    static Run runOnce(
            SessionEngine engine,
            Path projectDirectory,
            Fixture fixture,
            ToolOffer comet,
            ToolOffer percolator,
            List<ToolOffer> every,
            String cometThreads,
            PercolatorSettings settings,
            Map<String, String> edits)
            throws IOException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException,
                    RunNotStartedException {
        CometParameters edited =
                ReleaseDefaults.load(MetadataLoader.loadBundled(), comet.version())
                        .withText("database_name", fixture.fasta().toString(), ValueOrigin.USER)
                        .withText("decoy_search", "1", ValueOrigin.USER)
                        .withText("num_threads", cometThreads, ValueOrigin.USER);
        for (Map.Entry<String, String> edit : edits.entrySet()) {
            edited = edited.withText(edit.getKey(), edit.getValue(), ValueOrigin.USER);
        }
        CometParameters model = edited.withWorkflowEnforcedOutputs();
        PercolatorResolution resolution =
                PercolatorResolver.resolve(every, EnumSet.of(DownstreamStage.LIMELIGHT_CONVERSION));
        PercolatorRequest request =
                new PercolatorRequest(
                        Optional.of(percolator),
                        resolution.selected().map(percolator::equals).orElse(false),
                        Optional.of(resolution),
                        Optional.of(settings),
                        List.of(),
                        false);
        List<String> before = runDirectories(projectDirectory);
        CompletableFuture<RunResult> finished = new CompletableFuture<>();
        long started = System.nanoTime();
        engine.start(
                model,
                List.of(),
                fixture.spectra(),
                request,
                new RunObserver() {
                    @Override
                    public void planned(Plan plan, String description) {}

                    @Override
                    public void onTransition(StepTransition transition) {}

                    @Override
                    public void onRunFinished(RunResult result) {
                        finished.complete(result);
                    }
                });
        RunResult result = finished.get(RUN_MINUTES, TimeUnit.MINUTES);
        long seconds = (System.nanoTime() - started) / 1_000_000_000L;
        List<String> after = new ArrayList<>(runDirectories(projectDirectory));
        after.removeAll(before);
        assertEquals(1, after.size(), "one new run directory: " + after);
        return new Run(
                projectDirectory.resolve("runs").resolve(after.get(0)),
                after.get(0),
                seconds,
                result);
    }

    /**
     * The application's services over the real environment, without a session bus.
     *
     * @param clock the clock
     * @param processes the process service
     * @return the services
     */
    static ApplicationServices services(Clock clock, ProcessService processes) {
        EnvironmentReader environment = withoutSessionBus(new SystemEnvironmentReader());
        return new ApplicationServices(
                clock,
                environment,
                new PlatformFileSystemAccess(environment),
                new ClockRunIdSource(clock),
                new FfmGlibcVersionSource(),
                processes,
                null,
                null);
    }

    /**
     * A PSM identifier without the directory Comet puts in front of it. Comet builds the identifier
     * from its {@code -N} value, which the product passes as the absolute output base inside the
     * run directory, so every identifier begins with that run's own path (measured: {@code
     * /tmp/.../runs/<run>/outputs/comet/<base>_<scan>_<charge>_<rank>}). The path differs from run
     * to run and machine to machine; what identifies the PSM is the rest.
     */
    static String withoutDirectory(String psmId) {
        int slash = Math.max(psmId.lastIndexOf('/'), psmId.lastIndexOf('\\'));
        return psmId.substring(slash + 1);
    }

    private static List<ToolRecord> tools(ProvenanceManifest manifest, String name) {
        return manifest.tools().stream().filter(tool -> tool.name().equals(name)).toList();
    }

    /** Rows, target rows and decoy rows of a PIN, counted here from its Label column. */
    private static long[] pinRows(Path pin) throws IOException {
        List<String> lines = Files.readAllLines(pin, StandardCharsets.ISO_8859_1);
        long rows = 0;
        long targets = 0;
        long decoys = 0;
        for (String line : lines.subList(1, lines.size())) {
            if (line.isEmpty()) {
                continue;
            }
            rows++;
            String label = line.split("\t", -1)[1];
            if ("1".equals(label)) {
                targets++;
            } else if ("-1".equals(label)) {
                decoys++;
            }
        }
        return new long[] {rows, targets, decoys};
    }

    static Path find(Path run, String name) throws IOException {
        return optionalFind(run, name)
                .orElseThrow(() -> new AssertionError("no " + name + " under " + run));
    }

    static Optional<Path> optionalFind(Path run, String name) throws IOException {
        List<Path> found;
        try (Stream<Path> walked = Files.walk(run)) {
            found =
                    walked.filter(Files::isRegularFile)
                            .filter(path -> String.valueOf(path.getFileName()).equals(name))
                            .toList();
        }
        assertTrue(found.size() <= 1, () -> "more than one " + name + ": " + found);
        return found.stream().findFirst();
    }

    private static List<Path> files(Path run, String suffix) throws IOException {
        try (Stream<Path> walked = Files.walk(run.resolve("outputs"))) {
            return walked.filter(Files::isRegularFile)
                    .filter(
                            path ->
                                    String.valueOf(path.getFileName())
                                            .toLowerCase(Locale.ROOT)
                                            .endsWith(suffix))
                    .sorted()
                    .toList();
        }
    }

    private static List<String> runDirectories(Path project) throws IOException {
        Path runs = project.resolve("runs");
        if (!Files.isDirectory(runs)) {
            return List.of();
        }
        try (Stream<Path> listed = Files.list(runs)) {
            return listed.map(path -> String.valueOf(path.getFileName())).sorted().toList();
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError("every Java runtime provides SHA-256", impossible);
        }
    }

    /** The real environment, without a session bus: no desktop keyring can be reached. */
    private static EnvironmentReader withoutSessionBus(EnvironmentReader real) {
        return new EnvironmentReader() {
            @Override
            public Optional<String> environmentVariable(String name) {
                return "DBUS_SESSION_BUS_ADDRESS".equals(name)
                        ? Optional.empty()
                        : real.environmentVariable(name);
            }

            @Override
            public Optional<String> systemProperty(String name) {
                return real.systemProperty(name);
            }
        };
    }
}
