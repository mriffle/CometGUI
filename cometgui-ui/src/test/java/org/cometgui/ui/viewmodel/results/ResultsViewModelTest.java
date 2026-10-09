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

package org.cometgui.ui.viewmodel.results;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.RunId;
import org.cometgui.results.filtering.DisplayFilters;
import org.cometgui.results.filtering.PeptideQValueFilter;
import org.cometgui.results.filtering.PsmQValueFilter;
import org.cometgui.results.filtering.store.Category;
import org.cometgui.results.filtering.store.ResultStore;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.workflow.storage.ViewStateReading;
import org.cometgui.workflow.storage.ViewStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Results section over a port of real stores, real view-state files and the real exporter:
 * which run is shown, its tables, its view state applied and saved, the exports and their refusal
 * while the run executes, and what is closed when. Every expected text is typed here.
 */
class ResultsViewModelTest {

    private static final Instant OLD = Instant.parse("2026-10-01T09:00:00Z");

    private static final Instant NEW = Instant.parse("2026-10-05T09:00:00Z");

    @TempDir private Path project;

    private final TestExecutor background = new TestExecutor("background");

    private final TestExecutor ui = new TestExecutor("ui");

    private final DisplayFiltersViewModel filters = new DisplayFiltersViewModel();

    private final ResultTableViewModel table = new ResultTableViewModel(filters, background, ui);

    private final WeightsViewModel weights = new WeightsViewModel();

    private FakeResultsPort port;

    private ResultsViewModel results;

    @BeforeEach
    void setUp() {
        port = new FakeResultsPort(project);
        results = new ResultsViewModel(port, filters, table, weights, background, ui);
    }

    private void settle() {
        TestExecutor.settle(background, ui);
    }

    private void addOld() {
        port.add(
                "r-old",
                OLD,
                false,
                Map.of(
                        TableKind.TARGET_PSMS,
                        ResultsFixtures.copy("real-3.07.1-psms.tsv"),
                        TableKind.TARGET_PEPTIDES,
                        ResultsFixtures.copy("real-3.07.1-peptides.tsv")),
                Optional.empty(),
                Map.of());
    }

    private void addNew(boolean executing) {
        port.add(
                "r-new",
                NEW,
                executing,
                Map.of(
                        TableKind.TARGET_PSMS,
                        ResultsFixtures.copy("psms-shuffled.tsv"),
                        TableKind.TARGET_PEPTIDES,
                        ResultsFixtures.copy("real-3.07.1-peptides.tsv"),
                        TableKind.DECOY_PSMS,
                        ResultsFixtures.copy("real-3.07.1-decoy-psms.tsv")),
                Optional.of(ResultsFixtures.copy("real-3.07.1-weights.txt")),
                ResultTableViewModelTest.SHUFFLED_SOURCES);
    }

    private ResultsRun listed(String id) {
        return results.runsProperty().get().stream()
                .filter(run -> run.id().value().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static DisplayFilters filtersOf(String psm, String peptide) {
        return new DisplayFilters(
                new PsmQValueFilter(new BigDecimal(psm)),
                new PeptideQValueFilter(new BigDecimal(peptide)));
    }

    @Nested
    @DisplayName("which run")
    class WhichRun {

        @Test
        @DisplayName("nothing is read until refresh; then no run with results is said so")
        void noResults() {
            assertEquals(
                    "The runs with results have not been read yet.",
                    results.readinessProperty().get());
            assertEquals(List.of(), port.calls());
            results.refresh();
            assertEquals(
                    "Reading this project's runs with results.", results.readinessProperty().get());
            assertEquals(List.of(), port.calls(), "the port is called on the background only");
            settle();
            assertAll(
                    () -> assertEquals(List.of("runs"), port.calls()),
                    () ->
                            assertEquals(
                                    "No run with results yet. When a search with Percolator has"
                                            + " finished, its PSMs, peptides and learned feature"
                                            + " weights are shown here.",
                                    results.readinessProperty().get()),
                    () -> assertEquals(Optional.empty(), results.chosenRunProperty().get()),
                    () -> assertFalse(results.tableExportEnabledProperty().get()),
                    () ->
                            assertEquals(
                                    "Nothing can be exported: no run's results are open.",
                                    results.tableExportRefusalProperty().get()));
        }

        @Test
        @DisplayName("the most recent run opens by default, with its tables and weights")
        void mostRecent() {
            addOld();
            addNew(false);
            results.refresh();
            settle();
            assertAll(
                    () -> assertEquals(List.of("runs", "open r-new"), port.calls()),
                    () ->
                            assertEquals(
                                    List.of("r-new", "r-old"),
                                    results.runsProperty().get().stream()
                                            .map(run -> run.id().value())
                                            .toList()),
                    () ->
                            assertEquals(
                                    "r-new -- created 2026-10-05T09:00:00Z, succeeded",
                                    results.runsProperty().get().get(0).label()),
                    () ->
                            assertEquals(
                                    "r-new",
                                    results.chosenRunProperty().get().orElseThrow().id().value()),
                    () ->
                            assertEquals(
                                    List.of(
                                            TableKind.TARGET_PSMS,
                                            TableKind.TARGET_PEPTIDES,
                                            TableKind.DECOY_PSMS),
                                    results.tablesProperty().get(),
                                    "decoy PSMs offered, decoy peptides absent"),
                    () ->
                            assertEquals(
                                    Optional.of(TableKind.TARGET_PSMS),
                                    results.chosenTableProperty().get()),
                    () -> assertEquals("23", table.counts().total()),
                    () ->
                            assertEquals(
                                    "Showing the results of run r-new, created"
                                            + " 2026-10-05T09:00:00Z.",
                                    results.readinessProperty().get()),
                    () -> assertEquals(4, weights.rowsProperty().get().size()),
                    () -> assertTrue(results.tableExportEnabledProperty().get()),
                    () -> assertTrue(results.weightsExportEnabledProperty().get()));
        }

        @Test
        @DisplayName("choosing another run closes the shown run's stores and opens the other's")
        void choose() {
            addOld();
            addNew(false);
            results.refresh();
            settle();
            List<ResultStore> first = List.copyOf(port.openedStores());
            results.choose(listed("r-old"));
            assertEquals("Opening the results of run r-old.", results.readinessProperty().get());
            assertEquals(TablePage.NONE, table.page(), "the old table is cleared at once");
            settle();
            assertEquals(List.of("runs", "open r-new", "close r-new", "open r-old"), port.calls());
            for (ResultStore store : first) {
                assertThrows(IllegalStateException.class, store::rowCount, "closed");
            }
            assertEquals(
                    List.of(TableKind.TARGET_PSMS, TableKind.TARGET_PEPTIDES),
                    results.tablesProperty().get());
            assertEquals(
                    "No learned feature weights: no run is open, or the open run has no weights"
                            + " artefact.",
                    weights.statusProperty().get());
            assertEquals(
                    "This run has no learned feature weights to export.",
                    results.weightsExportRefusalProperty().get());
            assertFalse(results.weightsExportEnabledProperty().get());
            assertFalse(results.exportWeights());
            assertEquals(
                    "This run has no learned feature weights to export.",
                    results.exportStatusProperty().get());
            results.choose(listed("r-old"));
            assertEquals(0, background.pending(), "the run shown again opens nothing");
            ResultsRun stranger =
                    new ResultsRun(
                            new RunId("r-x"),
                            OLD,
                            AttemptOutcome.SUCCEEDED,
                            java.util.Set.of(TableKind.TARGET_PSMS),
                            false);
            assertThrows(IllegalArgumentException.class, () -> results.choose(stranger));
        }

        @Test
        @DisplayName("refresh keeps the run shown, brings its state up to date, reopens nothing")
        void refreshKeeps() {
            addOld();
            addNew(false);
            results.refresh();
            settle();
            results.choose(listed("r-old"));
            settle();
            port.executing("r-old", true);
            results.refresh();
            settle();
            assertEquals(
                    List.of("runs", "open r-new", "close r-new", "open r-old", "runs"),
                    port.calls());
            assertTrue(results.chosenRunProperty().get().orElseThrow().executing());
            assertFalse(results.tableExportEnabledProperty().get());
            port.remove("r-old");
            results.refresh();
            settle();
            assertEquals(
                    List.of(
                            "runs",
                            "open r-new",
                            "close r-new",
                            "open r-old",
                            "runs",
                            "runs",
                            "close r-old",
                            "open r-new"),
                    port.calls(),
                    "a run no longer listed is closed and the most recent opened");
            port.remove("r-new");
            results.refresh();
            settle();
            assertEquals("close r-new", port.calls().get(port.calls().size() - 1));
            assertEquals(ResultsViewModel.NO_RESULTS, results.readinessProperty().get());
            assertEquals(List.of(), results.tablesProperty().get());
        }

        @Test
        @DisplayName("another table of the run; a table it does not have is refused")
        void chooseTable() {
            addNew(false);
            results.refresh();
            settle();
            results.chooseTable(TableKind.TARGET_PEPTIDES);
            settle();
            assertEquals("64", table.counts().total());
            assertEquals(5, table.columnsProperty().get().size());
            results.chooseTable(TableKind.TARGET_PEPTIDES);
            assertEquals(0, background.pending(), "the table shown again asks nothing");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> results.chooseTable(TableKind.DECOY_PEPTIDES));
            assertEquals("Decoy peptides", ResultsViewModel.tableLabel(TableKind.DECOY_PEPTIDES));
            assertEquals("Target PSMs", ResultsViewModel.tableLabel(TableKind.TARGET_PSMS));
        }

        @Test
        @DisplayName("results opened for a run no longer chosen are closed at once")
        void staleOpen() {
            addOld();
            addNew(false);
            results.refresh();
            background.drain();
            ui.drain(); // runs read: r-new is being opened
            results.choose(listed("r-old"));
            settle();
            assertEquals(List.of("runs", "open r-new", "open r-old", "close r-new"), port.calls());
            assertEquals("r-old", results.chosenRunProperty().get().orElseThrow().id().value());
            assertEquals("64", table.counts().total(), "r-old's PSMs are shown");
            for (ResultStore store : port.openedStores().subList(0, 3)) {
                assertThrows(IllegalStateException.class, store::rowCount);
            }
        }

        @Test
        @DisplayName("an older list arriving after a newer one is dropped")
        void staleList() {
            addNew(false);
            results.refresh();
            results.refresh();
            background.runLast();
            ui.drain();
            background.runFirst();
            ui.drain();
            settle();
            assertEquals(List.of("runs", "runs", "open r-new"), port.calls());
        }

        @Test
        @DisplayName("failures to list or to open are said, nothing is left open")
        void failures() {
            addNew(false);
            port.failRuns(new IOException("the project is gone"));
            results.refresh();
            settle();
            assertEquals(
                    "The runs with results could not be read: the project is gone",
                    results.readinessProperty().get());
            port.failOpen(new IOException("psms.tsv is damaged"));
            results.refresh();
            settle();
            assertEquals(
                    "The results of run r-new could not be opened: psms.tsv is damaged",
                    results.readinessProperty().get());
            assertFalse(results.tableExportEnabledProperty().get());
            assertFalse(results.exportTable());
            assertEquals(
                    "Nothing can be exported: no run's results are open.",
                    results.exportStatusProperty().get());
        }

        @Test
        @DisplayName("close shows nothing and closes the stores")
        void close() {
            addNew(false);
            results.refresh();
            settle();
            results.close();
            settle();
            assertEquals("close r-new", port.calls().get(port.calls().size() - 1));
            assertEquals(List.of(), results.runsProperty().get());
            assertEquals(TablePage.NONE, table.page());
            assertEquals(List.of(), weights.rowsProperty().get());
            assertEquals(ResultsViewModel.NOT_READ, results.readinessProperty().get());
        }
    }

    @Nested
    @DisplayName("the run's view state")
    class ViewState {

        @Test
        @DisplayName("saved filters are applied on opening, and applying them saves nothing")
        void applied() throws IOException {
            addNew(false);
            ViewStateStore.write(port.run("r-new").layout(), filtersOf("0.005", "0.05"));
            results.refresh();
            settle();
            assertAll(
                    () -> assertEquals(filtersOf("0.005", "0.05"), filters.filters()),
                    () -> assertEquals("0.005", filters.psmFilterTextProperty().get()),
                    () -> assertEquals("6", table.counts().passing(), "the table under 0.005"),
                    () ->
                            assertEquals(
                                    "The filters this run was last shown with were restored: PSM"
                                            + " 0.005, peptide 0.05.",
                                    results.viewStateStatusProperty().get()),
                    () -> assertEquals(List.of("runs", "open r-new"), port.calls()));
        }

        @Test
        @DisplayName("a run with nothing saved shows the defaults, even after another run's")
        void defaults() throws IOException {
            addOld();
            addNew(false);
            ViewStateStore.write(port.run("r-new").layout(), filtersOf("0.5", "0.5"));
            results.refresh();
            settle();
            results.choose(listed("r-old"));
            settle();
            assertEquals(DisplayFilters.DEFAULTS, filters.filters());
            assertEquals(
                    "This run has no saved filters, so the defaults are shown: PSM 0.01, peptide"
                            + " 0.01.",
                    results.viewStateStatusProperty().get());
            assertTrue(port.calls().stream().noneMatch(call -> call.startsWith("save")));
        }

        @Test
        @DisplayName("a filter change saves the run's view state once; refused text saves nothing")
        void saved() throws IOException {
            addNew(false);
            results.refresh();
            settle();
            filters.editPsmFilter("0.02");
            settle();
            filters.editPsmFilter("0.020");
            filters.editPsmFilter("2");
            filters.editPeptideFilter("nope");
            settle();
            assertEquals(List.of("runs", "open r-new", "save r-new 0.02/0.01"), port.calls());
            assertEquals(
                    new ViewStateReading(
                            filtersOf("0.02", "0.01"),
                            ViewStateReading.Source.SAVED,
                            Optional.empty()),
                    ViewStateStore.read(port.run("r-new").layout()));
            assertEquals(
                    "The filters are saved for this run: PSM 0.02, peptide 0.01.",
                    results.viewStateStatusProperty().get());
            filters.editPeptideFilter("0.2");
            filters.editPsmFilter("0.01");
            settle();
            assertEquals(
                    List.of(
                            "runs",
                            "open r-new",
                            "save r-new 0.02/0.01",
                            "save r-new 0.02/0.2",
                            "save r-new 0.01/0.2"),
                    port.calls(),
                    "no process, no rerun: the view state is all a filter change writes");
        }

        @Test
        @DisplayName("a refused view-state file is shown, never written, and changes are not saved")
        void refused() throws IOException {
            addNew(false);
            Path file = port.run("r-new").layout().viewStateFile();
            Files.createDirectories(port.run("r-new").layout().resultsDirectory());
            Files.writeString(file, "{\"schemaVersion\": 9}", StandardCharsets.UTF_8);
            results.refresh();
            settle();
            assertEquals(DisplayFilters.DEFAULTS, filters.filters());
            String status = results.viewStateStatusProperty().get();
            assertTrue(
                    status.startsWith(
                            "This run's saved filters could not be read, so the defaults are"
                                    + " shown and changes are not saved for this run: "),
                    status);
            assertTrue(status.contains(file.toString()), status);
            filters.editPsmFilter("0.05");
            settle();
            assertEquals(
                    "This filter change is not saved for this run: its saved filters could not"
                            + " be read, and they are never overwritten.",
                    results.viewStateStatusProperty().get());
            assertTrue(port.calls().stream().noneMatch(call -> call.startsWith("save")));
            assertEquals("{\"schemaVersion\": 9}", Files.readString(file, StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("a save that fails is said so, and the next change tries again")
        void saveFails() throws IOException {
            addNew(false);
            results.refresh();
            settle();
            Path resultsDirectory = port.run("r-new").layout().resultsDirectory();
            Files.writeString(resultsDirectory, "a file where the directory belongs");
            filters.editPsmFilter("0.03");
            settle();
            String status = results.viewStateStatusProperty().get();
            assertTrue(status.startsWith("The filters could not be saved for this run: "), status);
            Files.delete(resultsDirectory);
            filters.editPsmFilter("0.04");
            settle();
            assertEquals(
                    "The filters are saved for this run: PSM 0.04, peptide 0.01.",
                    results.viewStateStatusProperty().get());
            assertEquals(
                    filtersOf("0.04", "0.01"),
                    ViewStateStore.read(port.run("r-new").layout()).filters());
        }

        @Test
        @DisplayName("no filter change saves anything while no run is open")
        void noRun() {
            filters.editPsmFilter("0.3");
            assertEquals(0, background.pending());
            assertEquals(List.of(), port.calls());
        }
    }

    @Nested
    @DisplayName("export")
    class Export {

        @Test
        @DisplayName("refused while the run executes, with the reason; allowed once it has ended")
        void refusedWhileExecuting() throws IOException {
            addNew(true);
            results.refresh();
            settle();
            String reason =
                    "Nothing can be exported from run r-new while it is executing: the workflow"
                            + " engine holds its provenance event log, where every export is"
                            + " recorded. Export when the run has ended.";
            assertAll(
                    () -> assertFalse(results.tableExportEnabledProperty().get()),
                    () -> assertFalse(results.weightsExportEnabledProperty().get()),
                    () -> assertEquals(reason, results.tableExportRefusalProperty().get()),
                    () -> assertEquals(reason, results.weightsExportRefusalProperty().get()),
                    () ->
                            assertEquals(
                                    "r-new -- created 2026-10-05T09:00:00Z, executing now",
                                    results.runsProperty().get().get(0).label()));
            assertFalse(results.exportTable());
            assertFalse(results.exportWeights());
            assertEquals(reason, results.exportStatusProperty().get());
            assertEquals(0, background.pending());
            assertTrue(port.calls().stream().noneMatch(call -> call.startsWith("export")));
            assertFalse(Files.exists(port.run("r-new").layout().exportsDirectory()));

            port.executing("r-new", false);
            results.refresh();
            settle();
            assertTrue(results.tableExportEnabledProperty().get());
        }

        @Test
        @DisplayName("the table shown, its filter and category; the file and its rows reported")
        void table() throws IOException {
            addNew(false);
            Path raw = ResultsFixtures.copy("psms-shuffled.tsv");
            String rawSha = ResultsFixtures.sha256(raw);
            results.refresh();
            settle();
            filters.editPsmFilter("0.005");
            table.setCategory(Category.UNKNOWN_Q_VALUE);
            table.editText("sample");
            table.applyText();
            settle();
            assertTrue(results.exportTable());
            assertFalse(results.tableExportEnabledProperty().get(), "one export at a time");
            assertEquals(
                    "Exporting the rows with an unknown q-value of the target PSMs table at a"
                            + " q-value cutoff of 0.005.",
                    results.exportStatusProperty().get());
            assertFalse(results.exportTable());
            assertEquals(
                    "An export is being written; the next can start when it has finished.",
                    results.exportStatusProperty().get());
            settle();
            assertEquals(
                    "export r-new TARGET_PSMS 0.005 UNKNOWN_Q_VALUE",
                    port.calls().get(port.calls().size() - 1));
            Path exports = port.run("r-new").layout().exportsDirectory();
            List<Path> written;
            try (Stream<Path> listing = Files.list(exports)) {
                written = listing.filter(p -> p.toString().endsWith(".tsv")).toList();
            }
            assertEquals(1, written.size());
            Path file = written.get(0);
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            assertEquals(
                    1 + IndependentCounts.lines(raw, "0.005", "unknown").size(),
                    lines.size(),
                    "the category's rows, the text filter not applied");
            assertEquals(
                    "Exported 7 rows -- the rows with an unknown q-value of the target PSMs table"
                            + " at a q-value cutoff of 0.005, of 23 in total -- to "
                            + file
                            + ". Its metadata is in "
                            + file
                            + ".json.",
                    results.exportStatusProperty().get());
            assertTrue(Files.isRegularFile(Path.of(file + ".json")));
            assertEquals(rawSha, ResultsFixtures.sha256(raw), "the raw table is unchanged");
            assertTrue(results.tableExportEnabledProperty().get());

            results.chooseTable(TableKind.TARGET_PEPTIDES);
            settle();
            filters.editPeptideFilter("1");
            settle();
            assertTrue(results.exportTable());
            settle();
            assertEquals(
                    "export r-new TARGET_PEPTIDES 1 PASSING",
                    port.calls().get(port.calls().size() - 1),
                    "the peptide filter for the peptide table");
            String status = results.exportStatusProperty().get();
            assertTrue(
                    status.startsWith(
                            "Exported 64 rows -- the passing rows of the target peptides table at"
                                    + " a q-value cutoff of 1, of 64 in total -- to "),
                    status);
        }

        @Test
        @DisplayName("the weights; a failed export is said so")
        void weightsAndFailure() throws IOException {
            addNew(false);
            results.refresh();
            settle();
            assertTrue(results.exportWeights());
            settle();
            String status = results.exportStatusProperty().get();
            assertTrue(
                    status.startsWith(
                            "Exported the learned feature weights of 4 features over 3 splits to "),
                    status);
            assertEquals("export weights r-new", port.calls().get(port.calls().size() - 1));
            Files.delete(port.run("r-new").layout().eventLogFile());
            Files.delete(port.run("r-new").layout().provenanceDirectory());
            Files.writeString(port.run("r-new").layout().provenanceDirectory(), "not a directory");
            assertTrue(results.exportTable());
            settle();
            assertTrue(
                    results.exportStatusProperty().get().startsWith("Nothing was exported: "),
                    results.exportStatusProperty()::get);
            assertTrue(results.tableExportEnabledProperty().get());
        }
    }
}
