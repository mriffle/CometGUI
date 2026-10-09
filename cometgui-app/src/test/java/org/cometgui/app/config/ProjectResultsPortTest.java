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

package org.cometgui.app.config;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.cometgui.app.testing.ResultRuns;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.project.ProjectId;
import org.cometgui.domain.project.ProjectLayout;
import org.cometgui.domain.run.ArchivedFile;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.DatabaseDelivery;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.run.RecordedInput;
import org.cometgui.domain.run.RunDerivation;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.RunIdentity;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.results.export.TableExport;
import org.cometgui.results.filtering.DisplayFilters;
import org.cometgui.results.filtering.PeptideQValueFilter;
import org.cometgui.results.filtering.PsmQValueFilter;
import org.cometgui.results.filtering.store.Category;
import org.cometgui.results.filtering.store.ResultStore;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.ui.viewmodel.results.OpenedResults;
import org.cometgui.ui.viewmodel.results.ResultsRun;
import org.cometgui.workflow.steps.RunResultFiles;
import org.cometgui.workflow.storage.ProjectLock;
import org.cometgui.workflow.storage.ReservedRun;
import org.cometgui.workflow.storage.RunStore;
import org.cometgui.workflow.storage.ViewStateReading;
import org.cometgui.workflow.storage.ViewStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Results section's port over a real project directory ({@link ProjectResultsPort}): which runs
 * it lists, what it opens, the spectrum files' names it maps, the view state it saves, and the
 * exports it makes or refuses. The runs are constructed through the run store ({@link ResultRuns});
 * the counts are the fixtures' hand counts (see {@code ResultsFiltersUiTest}).
 */
class ProjectResultsPortTest {

    private static final Instant CREATED = Instant.parse("2026-10-09T08:00:00Z");

    @TempDir private Path scratch;

    private Path root;

    private ProjectSession session;

    private final Set<RunId> executing = new HashSet<>();

    private ProjectResultsPort port;

    @BeforeEach
    void setUp() throws IOException {
        root = scratch.toRealPath();
        session =
                new ProjectSession(
                        root.resolve("project"),
                        Clock.systemUTC(),
                        ApplicationServices.forThisHost().runIds());
        port =
                new ProjectResultsPort(
                        session,
                        new StreamingHashService(),
                        "0.0.0-port-test",
                        Clock.fixed(Instant.parse("2026-10-09T12:00:00Z"), ZoneOffset.UTC),
                        () -> Set.copyOf(executing));
    }

    private RunLayout run(
            String id, Instant created, Map<TableKind, String> tables, boolean weights)
            throws IOException {
        ProjectLayout project =
                Files.isRegularFile(root.resolve("project/project.json"))
                        ? new ProjectLayout(root.resolve("project"))
                        : ResultRuns.project(root.resolve("project"));
        Map<TableKind, Path> copies = new java.util.EnumMap<>(TableKind.class);
        for (Map.Entry<TableKind, String> table : tables.entrySet()) {
            copies.put(
                    table.getKey(),
                    ResultRuns.fixture(
                            table.getValue(), root.resolve("in/" + id + "/" + table.getKey())));
        }
        return ResultRuns.run(
                project,
                id,
                created,
                List.of(root.resolve("data/a/sample_A.mzML"), root.resolve("data/a/sample_B.mzML")),
                copies,
                weights
                        ? Optional.of(
                                ResultRuns.fixture(
                                        ResultRuns.WEIGHTS_3071,
                                        root.resolve("in/" + id + "/weights.txt")))
                        : Optional.empty());
    }

    @Test
    @DisplayName("no project: no run, and nothing is created or locked")
    void noProject() throws IOException {
        assertEquals(List.of(), port.runs());
        assertFalse(Files.exists(root.resolve("project")), "nothing was created");
        assertFalse(session.isOpen(), "no lock was taken");
    }

    @Test
    @DisplayName(
            "runs: those with at least one table, with their tables, outcome and executing state;"
                    + " a run without tables and a directory without run.json are not listed")
    void runs() throws IOException {
        run(
                "run-both",
                CREATED,
                Map.of(
                        TableKind.TARGET_PSMS, ResultRuns.PSMS_UNKNOWN_Q,
                        TableKind.DECOY_PEPTIDES, ResultRuns.PSMS_SHUFFLED),
                true);
        run("run-none", CREATED.plusSeconds(60), Map.of(), false);
        Path stray =
                Files.createDirectories(
                        root.resolve("project/runs/20261009T090000Z-stray/outputs/percolator"));
        Files.writeString(stray.resolve("psms.tsv"), "PSMId\n", StandardCharsets.UTF_8);
        executing.add(new RunId("run-both"));

        List<ResultsRun> runs = port.runs();
        assertEquals(1, runs.size(), runs::toString);
        ResultsRun listed = runs.get(0);
        assertAll(
                () -> assertEquals(new RunId("run-both"), listed.id()),
                () -> assertEquals(CREATED, listed.created()),
                () -> assertEquals(AttemptOutcome.SUCCEEDED, listed.outcome()),
                () ->
                        assertEquals(
                                EnumSet.of(TableKind.TARGET_PSMS, TableKind.DECOY_PEPTIDES),
                                listed.tables()),
                () -> assertTrue(listed.executing()),
                () -> assertFalse(session.isOpen(), "reading takes no lock"));
        executing.clear();
        assertFalse(port.runs().get(0).executing());
    }

    @Test
    @DisplayName(
            "open: each table through the store factory, the weights, the spectrum files by the"
                    + " -N base a PSMId begins with, the view state; close closes every store")
    void open() throws IOException {
        RunLayout layout =
                run(
                        "run-open",
                        CREATED,
                        Map.of(
                                TableKind.TARGET_PSMS, ResultRuns.PSMS_UNKNOWN_Q,
                                TableKind.TARGET_PEPTIDES, ResultRuns.PSMS_SHUFFLED),
                        true);
        port.runs();
        OpenedResults opened = port.open(new RunId("run-open"));
        ResultStore psms = opened.stores().get(TableKind.TARGET_PSMS);
        assertAll(
                () ->
                        assertEquals(
                                EnumSet.of(TableKind.TARGET_PSMS, TableKind.TARGET_PEPTIDES),
                                EnumSet.copyOf(opened.stores().keySet())),
                () -> assertEquals(15, psms.rowCount()),
                () -> assertEquals(23, opened.stores().get(TableKind.TARGET_PEPTIDES).rowCount()),
                () -> assertEquals(5, psms.counts(PsmQValueFilter.DEFAULT).passing(), "at 0.01"),
                () -> assertEquals(3, opened.weights().orElseThrow().splitCount()),
                () ->
                        assertEquals(
                                Map.of(
                                        root.resolve(
                                                        "project/runs/20261009T080000Z-run-open/"
                                                                + "outputs/comet/sample_A")
                                                .toString(),
                                        "sample_A.mzML",
                                        root.resolve(
                                                        "project/runs/20261009T080000Z-run-open/"
                                                                + "outputs/comet/sample_B")
                                                .toString(),
                                        "sample_B.mzML"),
                                opened.sourceFiles()),
                () ->
                        assertEquals(
                                ViewStateReading.Source.DEFAULTS_NOTHING_SAVED,
                                opened.viewState().source()),
                () ->
                        assertEquals(
                                layout.root(),
                                root.resolve("project/runs/20261009T080000Z-run-open")));
        port.close(opened);
        assertThrows(IllegalStateException.class, psms::rowCount, "closed");
        port.close(opened);
    }

    @Test
    @DisplayName("a run not listed is refused when opened, naming it")
    void unknownRun() {
        IOException refused = assertThrows(IOException.class, () -> port.open(new RunId("nope")));
        assertEquals(
                "run nope is not a run with results in " + root.resolve("project"),
                refused.getMessage());
    }

    @Test
    @DisplayName("a run gone since it was listed is not offered from the old list")
    void aRunGoneSinceListed() throws IOException {
        RunLayout layout =
                run(
                        "run-gone",
                        CREATED,
                        Map.of(TableKind.TARGET_PSMS, ResultRuns.PSMS_UNKNOWN_Q),
                        false);
        assertEquals(1, port.runs().size());
        Files.delete(RunResultFiles.table(layout, TableKind.TARGET_PSMS));
        assertEquals(List.of(), port.runs());
        IOException refused =
                assertThrows(IOException.class, () -> port.open(new RunId("run-gone")));
        assertEquals(
                "run run-gone is not a run with results in " + root.resolve("project"),
                refused.getMessage());
    }

    @Test
    @DisplayName("a table the reader refuses fails the open, naming the file")
    void aRefusedTable() throws IOException {
        RunLayout layout =
                run(
                        "run-broken",
                        CREATED,
                        Map.of(TableKind.TARGET_PSMS, ResultRuns.PSMS_UNKNOWN_Q),
                        false);
        Path peptides = RunResultFiles.table(layout, TableKind.TARGET_PEPTIDES);
        Files.writeString(peptides, "not a Percolator table\n", StandardCharsets.UTF_8);
        port.runs();
        IOException refused =
                assertThrows(IOException.class, () -> port.open(new RunId("run-broken")));
        assertTrue(
                refused.getMessage().contains(peptides.toString()),
                () -> "the refusal names the table: " + refused.getMessage());
    }

    @Test
    @DisplayName("two spectrum files of one name: each is shown by its whole path")
    void sharedNames() throws IOException {
        ProjectLayout project = ResultRuns.project(root.resolve("project"));
        ResultRuns.run(
                project,
                "run-shared",
                CREATED,
                List.of(root.resolve("data/a/sample.mzML"), root.resolve("data/b/sample.mzML")),
                Map.of(
                        TableKind.TARGET_PSMS,
                        ResultRuns.fixture(ResultRuns.PSMS_UNKNOWN_Q, root.resolve("in/p.tsv"))),
                Optional.empty());
        port.runs();
        OpenedResults opened = port.open(new RunId("run-shared"));
        try {
            assertEquals(
                    Set.of(
                            root.resolve("data/a/sample.mzML").toString(),
                            root.resolve("data/b/sample.mzML").toString()),
                    Set.copyOf(opened.sourceFiles().values()));
            assertEquals(Optional.empty(), opened.weights());
        } finally {
            port.close(opened);
        }
    }

    @Test
    @DisplayName(
            "a derived run maps the source run's -N bases, which its rescored merged PIN carries")
    void derivedRun() throws IOException {
        ProjectLayout project = ResultRuns.project(root.resolve("project"));
        Instant sourceCreated = Instant.parse("2026-10-09T07:00:00Z");
        Clock clock = Clock.fixed(CREATED, ZoneOffset.UTC);
        RunLayout layout;
        try (ProjectLock lock = ProjectLock.acquire(project, clock)) {
            RunStore store = new RunStore(project, clock, () -> new RunId("run-derived"));
            ReservedRun reserved = store.reserve(lock);
            layout = reserved.layout();
            Files.writeString(layout.cometParamsFile(), "# constructed\n");
            FileHashes zero = new FileHashes("0".repeat(32), "0".repeat(64));
            RecordedInput spectrum =
                    new RecordedInput(root.resolve("data/x.mzML"), 1, CREATED, zero);
            store.record(
                    lock,
                    new RunIdentity(
                            reserved.runId(),
                            new ProjectId(ResultRuns.PROJECT_ID),
                            reserved.created(),
                            "2026.03.0",
                            RunIdentity.spectraOf(List.of(spectrum)),
                            new RecordedInput(root.resolve("data/x.fasta"), 1, CREATED, zero),
                            new ArchivedFile(RunLayout.cometParamsRelativePath(), 14, zero),
                            IndexMode.NONE,
                            DatabaseDelivery.PARAMETER_FILE,
                            Optional.of(
                                    new RunDerivation(
                                            new RunId("run-source"),
                                            sourceCreated,
                                            new ArchivedFile(
                                                    RunLayout.provenanceJsonRelativePath(),
                                                    1,
                                                    zero),
                                            new ArchivedFile(
                                                    RunLayout.mergedPinRelativePath(), 1, zero)))));
        }
        Path table = RunResultFiles.table(layout, TableKind.TARGET_PSMS);
        Files.createDirectories(RunResultFiles.percolatorOutputDirectory(layout));
        ResultRuns.fixture(ResultRuns.PSMS_UNKNOWN_Q, table);
        List<ResultsRun> runs = port.runs();
        assertEquals(AttemptOutcome.RUNNING, runs.get(0).outcome(), "no attempt recorded");
        OpenedResults opened = port.open(new RunId("run-derived"));
        try {
            assertEquals(
                    Map.of(
                            root.resolve("project/runs/20261009T070000Z-run-source/outputs/comet/x")
                                    .toString(),
                            "x.mzML"),
                    opened.sourceFiles());
        } finally {
            port.close(opened);
        }
    }

    @Test
    @DisplayName("saving the view state writes results/view-state.json and takes the project lock")
    void saveViewState() throws IOException {
        RunLayout layout =
                run(
                        "run-view",
                        CREATED,
                        Map.of(TableKind.TARGET_PSMS, ResultRuns.PSMS_UNKNOWN_Q),
                        false);
        DisplayFilters filters =
                DisplayFilters.DEFAULTS
                        .withPsm(PsmQValueFilter.parse("0.05"))
                        .withPeptide(PeptideQValueFilter.parse("0.02"));
        port.saveViewState(new RunId("run-view"), filters);
        ViewStateReading saved = ViewStateStore.read(layout);
        assertAll(
                () -> assertEquals(ViewStateReading.Source.SAVED, saved.source()),
                () -> assertEquals(filters, saved.filters()),
                () -> assertTrue(session.isOpen(), "a write takes the project's lock"));
        session.close();
    }

    @Test
    @DisplayName(
            "an executing run's table and weights are refused, naming why, and nothing is"
                    + " written; once it has ended the export is made")
    void exports() throws IOException {
        RunLayout layout =
                run(
                        "run-export",
                        CREATED,
                        Map.of(TableKind.TARGET_PSMS, ResultRuns.PSMS_UNKNOWN_Q),
                        true);
        port.runs();
        OpenedResults opened = port.open(new RunId("run-export"));
        port.close(opened);
        executing.add(new RunId("run-export"));
        IOException table =
                assertThrows(
                        IOException.class,
                        () ->
                                port.exportTable(
                                        new RunId("run-export"),
                                        TableKind.TARGET_PSMS,
                                        PsmQValueFilter.DEFAULT,
                                        Category.PASSING));
        IOException weights =
                assertThrows(
                        IOException.class,
                        () ->
                                port.exportWeights(
                                        new RunId("run-export"), opened.weights().orElseThrow()));
        String why =
                "run run-export is executing: the workflow engine holds its provenance event log,"
                        + " where every export is recorded. Export when the run has ended.";
        assertAll(
                () -> assertEquals(why, table.getMessage()),
                () -> assertEquals(why, weights.getMessage()),
                () -> assertFalse(Files.exists(layout.exportsDirectory()), "nothing exported"),
                () -> assertFalse(session.isOpen(), "no lock taken for a refusal"));

        executing.clear();
        TableExport made =
                port.exportTable(
                        new RunId("run-export"),
                        TableKind.TARGET_PSMS,
                        PsmQValueFilter.DEFAULT,
                        Category.PASSING);
        try (Stream<Path> exported = Files.list(layout.exportsDirectory())) {
            assertAll(
                    () -> assertEquals(5, made.rowsWritten(), "the 5 rows passing at 0.01"),
                    () -> assertEquals(2, exported.count(), "the export and its sidecar"),
                    () -> assertTrue(session.isOpen(), "an export takes the project's lock"));
        }
        assertEquals(
                3,
                port.exportWeights(new RunId("run-export"), opened.weights().orElseThrow())
                        .splitCount());
        session.close();
    }
}
