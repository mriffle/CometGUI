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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.run.RunIdentity;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.params.percolator.resolution.DownstreamStage;
import org.cometgui.params.percolator.resolution.PercolatorResolver;
import org.cometgui.provenance.events.ProvenanceEvent;
import org.cometgui.provenance.events.ProvenanceEventLogReader;
import org.cometgui.provenance.events.ProvenanceEventType;
import org.cometgui.provenance.manifest.ToolRecord;
import org.cometgui.results.export.ResultExporter;
import org.cometgui.results.export.TableExport;
import org.cometgui.results.filtering.PsmQValueFilter;
import org.cometgui.results.filtering.store.Category;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.tools.pdv.PdvSpectrumSupport;
import org.cometgui.workflow.engine.ReuseRefusedException;
import org.cometgui.workflow.engine.RunResult;
import org.cometgui.workflow.limelight.LimelightConversions;
import org.cometgui.workflow.limelight.LimelightConverted;
import org.cometgui.workflow.pdv.PdvMzTab;
import org.cometgui.workflow.pdv.PdvMzTabs;
import org.cometgui.workflow.storage.LimelightSettingsStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * No PSM identifier of a run holds the run directory (Phase 16 unit 3b), and a run made before that
 * was so still works -- against REAL runs through the real engine and the real process service: the
 * two K562 mzML and the proteome's first 1000 records ({@code decoy_search = 1}), each pinned Comet
 * release (2026.03.0 and 2026.02.2), then Percolator 3.07.1 with Limelight conversion enabled, so
 * the run writes Percolator's four tables and its XML.
 *
 * <h2>The runs</h2>
 *
 * <ul>
 *   <li>one run per Comet release, as the product makes it: {@code -N<base>} in the run's {@code
 *       outputs/comet};
 *   <li>one run with Comet 2026.03.0 made as the product made it before ({@link
 *       RunIdentifiers#runAsBefore}): {@code -N<run>/outputs/comet/<base>} in the run directory.
 *       Its identifiers begin with the absolute path -- asserted, so the run is what it is meant to
 *       be -- and nothing of it is rewritten.
 * </ul>
 *
 * <p>Every identifier is read here from the files the tools wrote, by {@link RunIdentifiers}: each
 * per-file PIN's and the merged PIN's {@code SpecId}s, the {@code PSMId}s of Percolator's target
 * and decoy PSM and peptide tables, and every {@code psm_id} of its XML. The earlier run's files
 * are the later run's with the directory at the start of each identifier and nothing else
 * different, line for line -- which is why no golden moves -- and the earlier run is still exported
 * for PDV and still converted by the real pinned Limelight converter, through the copy {@code
 * PoutForConverter} makes for it.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "only the linux/x86-64 Comet and Percolator binaries have ever been executed in"
                        + " this project")
class RealIdentifiersTest {

    private static final List<String> BASES = List.of("k562_3", "k562_4");

    private static final List<String> TABLES =
            List.of("psms.tsv", "peptides.tsv", "decoy-psms.tsv", "decoy-peptides.tsv");

    @TempDir private static Path scratch;

    private static RealProject project;

    private static RealProject earlierProject;

    /** A finished run and how it ended. */
    private record Ran(PreparedRun prepared, RunResult result) {

        RunLayout layout() {
            return prepared.layout();
        }

        Path percolator(String name) {
            return prepared.percolatorOutputDirectory().orElseThrow().resolve(name);
        }
    }

    private static Ran newer;

    private static Ran older;

    private static Ran earlier;

    private static Path jar;

    @BeforeAll
    static void run()
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Path root = scratch.toRealPath();
        Path inputs = Files.createDirectories(root.resolve("inputs"));
        List<Path> spectra = RealComet.spectra(inputs);
        Path fasta = RealComet.subset(inputs.resolve("subset.fasta"));
        Path percolator = RealPercolator.stage3071(root.resolve("bin/percolator-3.07.1"));
        ToolOffer offer =
                RealPercolator.offer(
                        "3.07.1",
                        ToolOrigin.MANAGED,
                        percolator,
                        RealPercolator.probe("3.07.1", percolator),
                        RealPercolator.ADVISORIES_3071);
        Set<DownstreamStage> limelight = EnumSet.of(DownstreamStage.LIMELIGHT_CONVERSION);
        PercolatorChoice choice =
                new PercolatorChoice(
                        new PercolatorSelection(offer, percolator, RealComet.sha256(percolator)),
                        PercolatorSettings.defaults(),
                        limelight,
                        PercolatorResolver.resolve(List.of(offer), limelight));
        project = RealProject.create(root.resolve("project"));
        earlierProject = RealProject.create(root.resolve("earlier project"));
        Path cometNewer = RealComet.stageComet(RealComet.NEWER, root.resolve("bin/comet"));
        Path cometOlder = RealComet.stageComet(RealComet.OLDER, root.resolve("bin/comet-older"));
        PreparedRun first =
                project.prepare(request(RealComet.NEWER, cometNewer, spectra, fasta, choice));
        newer = new Ran(first, project.run(first));
        PreparedRun second =
                project.prepare(request(RealComet.OLDER, cometOlder, spectra, fasta, choice));
        older = new Ran(second, project.run(second));
        PreparedRun before =
                earlierProject.prepare(
                        request(RealComet.NEWER, cometNewer, spectra, fasta, choice));
        earlier = new Ran(before, RunIdentifiers.runAsBefore(earlierProject, before));
        jar = RealLimelightConversionTest.stageConverter(root);
    }

    @AfterAll
    static void cleanUp() throws IOException {
        if (project != null) {
            project.close();
        }
        if (earlierProject != null) {
            earlierProject.close();
        }
    }

    private static SearchRequest request(
            String release, Path comet, List<Path> spectra, Path fasta, PercolatorChoice choice) {
        return new SearchRequest(
                        RealComet.model(release, fasta, DecoySource.COMET_INTERNAL_CONCATENATED, 4),
                        spectra,
                        RealComet.selection(release, comet),
                        IndexMode.NONE)
                .withPercolator(choice);
    }

    /** A file's lines with one prefix removed from the start of each line that has it. */
    private static List<String> withoutPrefix(Path file, String prefix) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            lines.add(line.startsWith(prefix) ? line.substring(prefix.length()) : line);
        }
        return lines;
    }

    private static void assertNoIdentifierHoldsADirectory(Ran ran) throws IOException {
        assertEquals(
                AttemptOutcome.SUCCEEDED,
                ran.result().outcome(),
                () -> ran.result().failures().toString());
        RunLayout layout = ran.layout();
        Path pout = ran.percolator("pout.xml");
        String text = Files.readString(layout.mergedPinFile(), StandardCharsets.UTF_8);
        assertAll(
                () ->
                        assertEquals(
                                3554,
                                RunIdentifiers.assertIds(
                                        layout.pinFile("k562_3"), List.of("k562_3"))),
                () ->
                        assertEquals(
                                2918,
                                RunIdentifiers.assertIds(
                                        layout.pinFile("k562_4"), List.of("k562_4"))),
                () -> assertEquals(6472, RunIdentifiers.assertIds(layout.mergedPinFile(), BASES)),
                () -> assertTrue(RunIdentifiers.assertIds(ran.percolator("psms.tsv"), BASES) > 0),
                () ->
                        assertTrue(
                                RunIdentifiers.assertIds(ran.percolator("peptides.tsv"), BASES)
                                        > 0),
                () ->
                        assertTrue(
                                RunIdentifiers.assertIds(ran.percolator("decoy-psms.tsv"), BASES)
                                        > 0),
                () ->
                        assertTrue(
                                RunIdentifiers.assertIds(
                                                ran.percolator("decoy-peptides.tsv"), BASES)
                                        > 0),
                () ->
                        assertTrue(
                                RunIdentifiers.assertIds(
                                                pout.toString(),
                                                RunIdentifiers.poutIds(pout),
                                                BASES)
                                        > RunIdentifiers.firstColumn(ran.percolator("psms.tsv"))
                                                .size(),
                                "every p:psm_id and every peptide's psm_id of pout.xml"),
                () ->
                        assertTrue(
                                !text.contains(layout.root().toString()),
                                "the merged PIN holds the run directory nowhere"));
    }

    @Test
    @DisplayName(
            "Comet 2026.03.0 and Percolator 3.07.1: every SpecId of both PINs and the merged PIN,"
                    + " every PSMId of the four tables and every psm_id of pout.xml is"
                    + " <base>_<scan>_<charge>_<rank> -- no path separator, no run directory")
    void noIdentifierHoldsADirectoryWithTheNewerComet() throws IOException {
        assertNoIdentifierHoldsADirectory(newer);
    }

    @Test
    @DisplayName(
            "Comet 2026.02.2 and Percolator 3.07.1: the same, with the other pinned Comet release")
    void noIdentifierHoldsADirectoryWithTheOlderComet() throws IOException {
        assertNoIdentifierHoldsADirectory(older);
    }

    @Test
    @DisplayName(
            "Comet's files are where the run layout says, each invocation's record states the"
                    + " argument array and the working directory actually used, and two"
                    + " invocations differ only in the input path and the -N base (R-CMT-03,"
                    + " R-CMT-04, R-PROC-04)")
    void theRecordStatesWhatRan() throws IOException {
        for (Ran ran : List.of(newer, older)) {
            RunLayout layout = ran.layout();
            assertEquals(
                    List.of("k562_3.pep.xml", "k562_3.pin", "k562_4.pep.xml", "k562_4.pin"),
                    RunEvidence.listing(layout.cometOutputDirectory()));
            List<ToolRecord> comets =
                    RunEvidence.tools(RunEvidence.manifest(layout), CometWorkflow.TOOL_NAME);
            assertEquals(2, comets.size(), "one Comet invocation per spectrum file");
            List<List<String>> recorded = new ArrayList<>();
            for (ToolRecord comet : comets) {
                List<String> argv = comet.execution().command().argv();
                recorded.add(argv);
                assertEquals(
                        layout.cometOutputDirectory(),
                        comet.execution().command().workingDirectory(),
                        "the recorded working directory is the run's outputs/comet");
                assertTrue(
                        comet.execution().command().workingDirectory().startsWith(layout.root()),
                        "which is inside the run directory");
            }
            String executable = recorded.get(0).get(0);
            assertEquals(
                    List.of(
                            executable,
                            "-P" + layout.cometParamsFile(),
                            "-Nk562_3",
                            recorded.get(0).get(3)),
                    recorded.get(0));
            assertEquals(
                    List.of(
                            executable,
                            "-P" + layout.cometParamsFile(),
                            "-Nk562_4",
                            recorded.get(1).get(3)),
                    recorded.get(1));
            assertTrue(recorded.get(0).get(3).endsWith("/inputs/k562_3.mzML"), recorded::toString);
            assertTrue(recorded.get(1).get(3).endsWith("/inputs/k562_4.mzML"), recorded::toString);
            // The process service was handed exactly what is recorded.
            List<RealProject.Launch> launches = project.runner().launchesOf(Path.of(executable));
            assertEquals(2, launches.size());
            for (int index = 0; index < 2; index++) {
                assertEquals(recorded.get(index), launches.get(index).command().argv());
                assertEquals(
                        layout.cometOutputDirectory(),
                        launches.get(index).command().workingDirectory());
            }
        }
    }

    @Test
    @DisplayName(
            "a run made before: its identifiers begin with <run>/outputs/comet/, and its PINs,"
                    + " tables and XML ids are the present run's with that directory at the start"
                    + " of each identifier and nothing else different")
    void theEarlierRunDiffersOnlyInTheDirectory() throws IOException {
        assertEquals(
                AttemptOutcome.SUCCEEDED,
                earlier.result().outcome(),
                () -> earlier.result().failures().toString());
        RunLayout was = earlier.layout();
        String directory = was.cometOutputDirectory() + "/";
        List<ToolRecord> comets =
                RunEvidence.tools(RunEvidence.manifest(was), CometWorkflow.TOOL_NAME);
        assertEquals(
                "-N" + was.cometOutputBase("k562_3"),
                comets.get(0).execution().command().argv().get(2),
                "the earlier run's record states the absolute -N it was given");
        assertEquals(was.root(), comets.get(0).execution().command().workingDirectory());
        for (String id : RunIdentifiers.firstColumn(was.mergedPinFile())) {
            assertTrue(id.startsWith(directory + "k562_"), () -> "an earlier SpecId: " + id);
        }
        for (String table : TABLES) {
            List<String> ids = RunIdentifiers.firstColumn(earlier.percolator(table));
            assertTrue(!ids.isEmpty(), table);
            for (String id : ids) {
                assertTrue(id.startsWith(directory + "k562_"), () -> table + ": " + id);
            }
        }
        List<String> earlierPout = RunIdentifiers.poutIds(earlier.percolator("pout.xml"));
        for (String id : earlierPout) {
            assertTrue(id.startsWith(directory + "k562_"), () -> "an earlier psm_id: " + id);
        }
        // Line for line: the earlier file with the directory removed IS the present file.
        assertEquals(
                Files.readAllLines(newer.layout().pinFile("k562_3"), StandardCharsets.UTF_8),
                withoutPrefix(was.pinFile("k562_3"), directory));
        assertEquals(
                Files.readAllLines(newer.layout().pinFile("k562_4"), StandardCharsets.UTF_8),
                withoutPrefix(was.pinFile("k562_4"), directory));
        assertEquals(
                Files.readAllLines(newer.layout().mergedPinFile(), StandardCharsets.UTF_8),
                withoutPrefix(was.mergedPinFile(), directory));
        for (String table : TABLES) {
            assertEquals(
                    Files.readAllLines(newer.percolator(table), StandardCharsets.UTF_8),
                    withoutPrefix(earlier.percolator(table), directory),
                    table);
        }
        List<String> stripped = new ArrayList<>();
        for (String id : earlierPout) {
            stripped.add(id.substring(directory.length()));
        }
        assertEquals(RunIdentifiers.poutIds(newer.percolator("pout.xml")), stripped);
    }

    private static PdvMzTab mzTabOf(Ran ran) throws IOException {
        RunIdentity identity = ran.prepared().identity();
        return new PdvMzTabs(
                        new org.cometgui.provenance.hashing.StreamingHashService(),
                        SecretRedactor.patternsOnly(),
                        Clock.systemUTC(),
                        Set::of)
                .prepare(ran.layout(), identity, ran.layout(), PdvSpectrumSupport.VERIFIED_VERSION);
    }

    @Test
    @DisplayName(
            "PDV's mzTab is exported from the present run and from the earlier run alike: every"
                    + " target and decoy PSM, each joined to its own spectrum file")
    void bothRunsAreExportedForPdv() throws IOException {
        long psms =
                RunIdentifiers.firstColumn(newer.percolator("psms.tsv")).size()
                        + RunIdentifiers.firstColumn(newer.percolator("decoy-psms.tsv")).size();
        PdvMzTab present = mzTabOf(newer);
        PdvMzTab before = mzTabOf(earlier);
        assertAll(
                () -> assertEquals(psms, present.psms()),
                () -> assertEquals(psms, before.psms()),
                () -> assertEquals(2, present.mgfs().size(), "one MGF per spectrum file"),
                () -> assertEquals(2, before.mgfs().size()),
                () ->
                        assertEquals(
                                RealComet.sha256(present.mgfs().get(0)),
                                RealComet.sha256(before.mgfs().get(0)),
                                "the same spectra, in the same order, from either form"),
                () ->
                        assertEquals(
                                RealComet.sha256(present.mgfs().get(1)),
                                RealComet.sha256(before.mgfs().get(1))));
    }

    @Test
    @DisplayName(
            "the earlier run is still converted by the real pinned converter, through the verified"
                    + " copy of its Percolator XML, and holds the PSMs the present run's"
                    + " conversion holds")
    void theEarlierRunIsStillConverted() throws IOException, InterruptedException {
        LimelightConverted present = convert(project, newer);
        LimelightConverted before = convert(earlierProject, earlier);
        Map<String, String> presentRecord = converted(newer.layout());
        Map<String, String> beforeRecord = converted(earlier.layout());
        long targets = RunIdentifiers.firstColumn(newer.percolator("psms.tsv")).size();
        assertAll(
                () -> assertEquals(Long.toString(targets), presentRecord.get("output.psms")),
                () -> assertEquals(Long.toString(targets), beforeRecord.get("output.psms")),
                () ->
                        assertEquals(
                                presentRecord.get("output.reported-peptides"),
                                beforeRecord.get("output.reported-peptides")),
                () -> assertEquals("pepxml", presentRecord.get("input.3.role")),
                () ->
                        assertEquals(
                                "percolator-xml-for-converter",
                                beforeRecord.get("input.3.role"),
                                "the earlier run's ids hold the directory, so the converter is"
                                        + " given the copy without it, as before"),
                () -> assertTrue(Files.isRegularFile(present.file())),
                () -> assertTrue(Files.isRegularFile(before.file())),
                () ->
                        assertEquals(
                                RunIdentifiers.poutIds(earlier.percolator("pout.xml")).size(),
                                RunIdentifiers.poutIds(newer.percolator("pout.xml")).size()),
                () ->
                        assertTrue(
                                RunIdentifiers.poutIds(earlier.percolator("pout.xml"))
                                        .get(0)
                                        .startsWith(earlier.layout().cometOutputDirectory() + "/"),
                                "the earlier run's own Percolator XML is not rewritten"));
    }

    @Test
    @DisplayName(
            "an export of the PSM table holds each PSMId as Percolator wrote it, so no exported"
                    + " row holds a path separator or the run directory")
    void anExportHoldsNoPath() throws IOException {
        RunLayout layout = older.layout();
        TableExport export =
                new ResultExporter(
                                layout,
                                older.prepared().identity().runId(),
                                "0.1.0-test",
                                Clock.systemUTC(),
                                project.hashes(),
                                SecretRedactor.patternsOnly())
                        .exportTable(
                                older.percolator("psms.tsv"),
                                TableKind.TARGET_PSMS,
                                PsmQValueFilter.DEFAULT,
                                Category.ALL);
        String text = Files.readString(export.file(), StandardCharsets.UTF_8);
        assertAll(
                () ->
                        assertEquals(
                                RunIdentifiers.firstColumn(older.percolator("psms.tsv")).size(),
                                export.rowsWritten(),
                                "every row of the table"),
                () ->
                        assertEquals(
                                export.rowsWritten(),
                                RunIdentifiers.assertIds(export.file(), BASES)),
                () ->
                        assertTrue(
                                !text.contains(layout.root().toString())
                                        && !text.contains("outputs/comet"),
                                "the exported table names the run directory nowhere"));
    }

    private static LimelightConverted convert(RealProject of, Ran ran)
            throws IOException, InterruptedException {
        LimelightConversions conversions = RealLimelightConversionTest.conversions(of, jar);
        RunLayout layout = ran.layout();
        return conversions.convert(
                layout,
                of.store().read(layout).identity(),
                LimelightSettingsStore.read(layout),
                Optional.empty());
    }

    private static Map<String, String> converted(RunLayout layout) throws IOException {
        List<ProvenanceEvent> events =
                ProvenanceEventLogReader.recover(
                                layout.eventLogFile(), SecretRedactor.patternsOnly())
                        .events()
                        .stream()
                        .filter(event -> event.type() == ProvenanceEventType.LIMELIGHT_CONVERTED)
                        .toList();
        assertEquals(1, events.size(), "one conversion of " + layout.root());
        return events.get(0).payload();
    }
}
