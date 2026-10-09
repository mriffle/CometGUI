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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import org.cometgui.domain.run.RunId;
import org.cometgui.results.export.TableExport;
import org.cometgui.results.export.WeightsExport;
import org.cometgui.results.filtering.DisplayFilters;
import org.cometgui.results.filtering.QValueFilter;
import org.cometgui.results.filtering.store.Category;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.results.parser.WeightsSummary;
import org.cometgui.ui.viewmodel.NonNullProperty;
import org.cometgui.workflow.storage.ViewStateReading;

/**
 * The Results section ({@code R-RES-01}..{@code R-RES-04}, design decision P10-9): which run's
 * results are shown, which of its tables, the run's saved display filters, and the export actions.
 *
 * <h2>What it holds, and what it is given</h2>
 *
 * <p>The table and the weights view are the composition root's, passed in (as every view-model is
 * built there and handed to the views, never handed out by another view-model); this class tells
 * them what to show. The display filters are the interface's one {@link DisplayFiltersViewModel},
 * shared with the Percolator section.
 *
 * <h2>Which run</h2>
 *
 * <p>{@link #refresh()} reads the session's runs with results; by default the most recent is shown,
 * and the scientist can {@link #choose} another. Opening a run opens its tables' stores through the
 * port and <strong>closes the previous run's</strong>. Its target PSM table is shown first; the
 * decoy tables are offered only when the run has them.
 *
 * <h2>The run's view state</h2>
 *
 * <p>When a run opens, its saved filters are applied to the shared filters -- or the defaults, when
 * it saved none or its file was refused, and then the refusal is shown ({@link
 * #viewStateStatusProperty()}). After that, a change of either filter while the run is open saves
 * the run's view state through the port; applying the saved state saves nothing, a change back to
 * the value saved last saves nothing, and a run whose file was refused is never written (the store
 * would refuse it too). That write is the only one a filter change causes (design decision P10-10).
 *
 * <h2>Export</h2>
 *
 * <p>{@link #exportTable()} exports the table shown under its current filter and category -- what
 * the scientist is looking at, less the text filter and sort, which are view-only and which the
 * sidecar says were not applied; {@link #exportWeights()} the run's weights. Both are refused, with
 * the reason, while the run is executing: the workflow engine holds the run's provenance event log,
 * where every export is recorded.
 *
 * <h2>Two threads</h2>
 *
 * <p>The port is called only on the {@code background} executor and every answer is applied on the
 * {@code ui} executor; an answer to an older read or open is dropped, and results opened for a run
 * no longer chosen are closed at once.
 */
public final class ResultsViewModel {

    /** The readiness before the runs are read. */
    public static final String NOT_READ = "The runs with results have not been read yet.";

    /** The readiness while the runs are read. */
    public static final String READING_RUNS = "Reading this project's runs with results.";

    /** The readiness when no run has results. */
    public static final String NO_RESULTS =
            "No run with results yet. When a search with Percolator has finished, its PSMs,"
                    + " peptides and learned feature weights are shown here.";

    /** The view-state status when no run is open. */
    public static final String NO_VIEW_STATE = "";

    private final ResultsPort port;

    private final DisplayFiltersViewModel filters;

    private final ResultTableViewModel table;

    private final WeightsViewModel weights;

    private final Executor background;

    private final Executor ui;

    // ---- state, on the interface thread ----

    private long listGeneration;

    private long openGeneration;

    private OpenedResults opened;

    private boolean applying;

    private DisplayFilters lastSaved;

    private boolean viewStateRefused;

    private boolean exporting;

    // ---- published ----

    private final NonNullProperty<List<ResultsRun>> runs;

    private final NonNullProperty<Optional<ResultsRun>> chosenRun;

    private final NonNullProperty<List<TableKind>> tables;

    private final NonNullProperty<Optional<TableKind>> chosenTable;

    private final NonNullProperty<String> readiness;

    private final NonNullProperty<String> viewStateStatus;

    private final NonNullProperty<String> exportStatus;

    private final NonNullProperty<String> tableExportRefusal;

    private final NonNullProperty<String> weightsExportRefusal;

    private final ReadOnlyBooleanWrapper tableExportEnabled;

    private final ReadOnlyBooleanWrapper weightsExportEnabled;

    /**
     * The section, with nothing read. Nothing is read until {@link #refresh()} is called.
     *
     * @param port the session's project's results
     * @param filters the interface's one display-filter state
     * @param table the table view the section shows its tables in
     * @param weights the learned feature weights view
     * @param background where the port is called: never the interface thread
     * @param ui where every answer is applied: the interface thread
     */
    public ResultsViewModel(
            ResultsPort port,
            DisplayFiltersViewModel filters,
            ResultTableViewModel table,
            WeightsViewModel weights,
            Executor background,
            Executor ui) {
        this.port = Objects.requireNonNull(port, "port");
        this.filters = Objects.requireNonNull(filters, "filters");
        this.table = Objects.requireNonNull(table, "table");
        this.weights = Objects.requireNonNull(weights, "weights");
        this.background = Objects.requireNonNull(background, "background");
        this.ui = Objects.requireNonNull(ui, "ui");
        runs = new NonNullProperty<>(this, "runs", List.of());
        chosenRun = new NonNullProperty<>(this, "chosenRun", Optional.empty());
        tables = new NonNullProperty<>(this, "tables", List.of());
        chosenTable = new NonNullProperty<>(this, "chosenTable", Optional.empty());
        readiness = new NonNullProperty<>(this, "readiness", NOT_READ);
        viewStateStatus = new NonNullProperty<>(this, "viewStateStatus", NO_VIEW_STATE);
        exportStatus = new NonNullProperty<>(this, "exportStatus", "");
        tableExportRefusal = new NonNullProperty<>(this, "tableExportRefusal", "");
        weightsExportRefusal = new NonNullProperty<>(this, "weightsExportRefusal", "");
        tableExportEnabled = new ReadOnlyBooleanWrapper(this, "tableExportEnabled", false);
        weightsExportEnabled = new ReadOnlyBooleanWrapper(this, "weightsExportEnabled", false);
        filters.displayFiltersProperty()
                .addListener((observable, before, after) -> filtersChanged(after));
        publishExports();
    }

    // =========================================================================== actions ====

    /**
     * Reads the session's runs with results again, on the background executor. The run shown stays
     * shown if it is still listed -- with its executing state brought up to date -- and otherwise
     * the most recent run is opened. The composition root calls this at start, when the project
     * changes, and when a run starts or ends.
     */
    public void refresh() {
        listGeneration++;
        long mine = listGeneration;
        if (chosenRun.get().isEmpty()) {
            readiness.set(READING_RUNS);
        }
        background.execute(
                () -> {
                    List<ResultsRun> answer;
                    String failure = null;
                    try {
                        answer = port.runs();
                    } catch (IOException | RuntimeException failed) {
                        answer = List.of();
                        failure = describe(failed);
                    }
                    List<ResultsRun> listed = answer;
                    String why = failure;
                    ui.execute(() -> runsRead(mine, listed, why));
                });
    }

    /**
     * Shows another run's results, closing the shown run's stores.
     *
     * @param run one of {@link #runsProperty()}'s runs
     * @throws IllegalArgumentException if it is not one of them
     */
    public void choose(ResultsRun run) {
        Objects.requireNonNull(run, "run");
        ResultsRun listed =
                listed(run)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "the run selector does not offer "
                                                        + run.id().value()));
        if (chosenRun.get().map(now -> now.id().equals(listed.id())).orElse(false)) {
            return;
        }
        open(listed);
    }

    /**
     * Shows another of the open run's tables.
     *
     * @param kind one of {@link #tablesProperty()}'s kinds
     * @throws IllegalArgumentException if the open run has no such table
     */
    public void chooseTable(TableKind kind) {
        Objects.requireNonNull(kind, "kind");
        if (opened == null || !opened.stores().containsKey(kind)) {
            throw new IllegalArgumentException(
                    "the open run has no " + TableCounts.rowsName(kind) + " table");
        }
        if (chosenTable.get().equals(Optional.of(kind))) {
            return;
        }
        chosenTable.set(Optional.of(kind));
        table.show(opened.stores().get(kind), opened.sourceFiles());
    }

    /**
     * Exports the table shown: its rows of the category shown under its current filter, as a new
     * file with a sidecar and a provenance event. Refused, with the reason in {@link
     * #exportStatusProperty()}, while the run is executing or another export is being written.
     *
     * @return {@code true} if the export started
     */
    public boolean exportTable() {
        String refusal = tableExportRefusal.get();
        if (!refusal.isEmpty()) {
            exportStatus.set(refusal);
            return false;
        }
        ResultsRun run = chosenRun.get().orElseThrow();
        TableKind kind = chosenTable.get().orElseThrow();
        QValueFilter filter = kind.isPsms() ? filters.filters().psm() : filters.filters().peptide();
        Category category = table.categoryProperty().get();
        exporting = true;
        exportStatus.set(
                "Exporting the "
                        + categoryWords(category)
                        + " of the "
                        + TableCounts.rowsName(kind)
                        + " table at a q-value cutoff of "
                        + filter.text()
                        + ".");
        publishExports();
        background.execute(
                () -> {
                    String said;
                    try {
                        TableExport export = port.exportTable(run.id(), kind, filter, category);
                        said = exported(export);
                    } catch (IOException | RuntimeException failed) {
                        said = "Nothing was exported: " + describe(failed);
                    }
                    String outcome = said;
                    ui.execute(() -> exportEnded(outcome));
                });
        return true;
    }

    /**
     * Exports the open run's learned feature weights, as a new file with a sidecar and a provenance
     * event. Refused, with the reason, while the run is executing, when it has no weights, or while
     * another export is being written.
     *
     * @return {@code true} if the export started
     */
    public boolean exportWeights() {
        String refusal = weightsExportRefusal.get();
        if (!refusal.isEmpty()) {
            exportStatus.set(refusal);
            return false;
        }
        ResultsRun run = chosenRun.get().orElseThrow();
        WeightsSummary summary = opened.weights().orElseThrow();
        exporting = true;
        exportStatus.set("Exporting the learned feature weights.");
        publishExports();
        background.execute(
                () -> {
                    String said;
                    try {
                        WeightsExport export = port.exportWeights(run.id(), summary);
                        said =
                                "Exported the learned feature weights of "
                                        + export.featureCount()
                                        + " features over "
                                        + export.splitCount()
                                        + " splits to "
                                        + export.file()
                                        + ". Its metadata is in "
                                        + export.sidecar()
                                        + ".";
                    } catch (IOException | RuntimeException failed) {
                        said = "Nothing was exported: " + describe(failed);
                    }
                    String outcome = said;
                    ui.execute(() -> exportEnded(outcome));
                });
        return true;
    }

    /**
     * Shows nothing and closes the shown run's stores -- when the project closes or the application
     * ends.
     */
    public void close() {
        listGeneration++;
        openGeneration++;
        closeOpened();
        runs.set(List.of());
        chosenRun.set(Optional.empty());
        readiness.set(NOT_READ);
        publishExports();
    }

    // ========================================================================= published ====

    /**
     * The runs with results, most recent first.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<List<ResultsRun>> runsProperty() {
        return runs.getReadOnlyProperty();
    }

    /**
     * The run shown, or being opened.
     *
     * @return the read-only property; empty when none is
     */
    public ReadOnlyObjectProperty<Optional<ResultsRun>> chosenRunProperty() {
        return chosenRun.getReadOnlyProperty();
    }

    /**
     * The open run's tables: target PSMs, target peptides, then the decoy tables where the run has
     * them.
     *
     * @return the read-only property; empty when no run is open
     */
    public ReadOnlyObjectProperty<List<TableKind>> tablesProperty() {
        return tables.getReadOnlyProperty();
    }

    /**
     * The table shown.
     *
     * @return the read-only property; empty when no run is open
     */
    public ReadOnlyObjectProperty<Optional<TableKind>> chosenTableProperty() {
        return chosenTable.getReadOnlyProperty();
    }

    /**
     * What the section shows, or why it shows nothing: no run read yet, no run with results, a run
     * being opened, or a failure.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> readinessProperty() {
        return readiness.getReadOnlyProperty();
    }

    /**
     * Where the open run's filters came from, whether a change was saved, and the refusal of a view
     * state that could not be read.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> viewStateStatusProperty() {
        return viewStateStatus.getReadOnlyProperty();
    }

    /**
     * The last export's outcome -- the file written and its row count -- or why it was refused.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> exportStatusProperty() {
        return exportStatus.getReadOnlyProperty();
    }

    /**
     * Why the table cannot be exported now.
     *
     * @return the read-only property; empty text when it can
     */
    public ReadOnlyObjectProperty<String> tableExportRefusalProperty() {
        return tableExportRefusal.getReadOnlyProperty();
    }

    /**
     * Why the weights cannot be exported now.
     *
     * @return the read-only property; empty text when they can
     */
    public ReadOnlyObjectProperty<String> weightsExportRefusalProperty() {
        return weightsExportRefusal.getReadOnlyProperty();
    }

    /**
     * Whether the table can be exported now.
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty tableExportEnabledProperty() {
        return tableExportEnabled.getReadOnlyProperty();
    }

    /**
     * Whether the weights can be exported now.
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty weightsExportEnabledProperty() {
        return weightsExportEnabled.getReadOnlyProperty();
    }

    /**
     * How the table selector names a table.
     *
     * @param kind the table
     * @return for example {@code Target PSMs}
     */
    public static String tableLabel(TableKind kind) {
        String name = TableCounts.rowsName(kind);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    // ========================================================================= internals ====

    private void runsRead(long answered, List<ResultsRun> listed, String failure) {
        if (answered != listGeneration) {
            return;
        }
        if (failure != null) {
            readiness.set("The runs with results could not be read: " + failure);
            return;
        }
        List<ResultsRun> ordered = new ArrayList<>(listed);
        ordered.sort(
                Comparator.comparing(ResultsRun::created)
                        .reversed()
                        .thenComparing(run -> run.id().value()));
        runs.set(List.copyOf(ordered));
        Optional<ResultsRun> still = chosenRun.get().flatMap(this::listed);
        if (still.isPresent()) {
            chosenRun.set(still);
            publishExports();
            return;
        }
        if (ordered.isEmpty()) {
            openGeneration++;
            closeOpened();
            chosenRun.set(Optional.empty());
            readiness.set(NO_RESULTS);
            publishExports();
            return;
        }
        open(ordered.get(0));
    }

    private Optional<ResultsRun> listed(ResultsRun run) {
        for (ResultsRun candidate : runs.get()) {
            if (candidate.id().equals(run.id())) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private void open(ResultsRun run) {
        openGeneration++;
        long mine = openGeneration;
        closeOpened();
        chosenRun.set(Optional.of(run));
        readiness.set("Opening the results of run " + run.id().value() + ".");
        publishExports();
        background.execute(
                () -> {
                    OpenedResults answer = null;
                    String failure = null;
                    try {
                        answer = port.open(run.id());
                    } catch (IOException | RuntimeException failed) {
                        failure = describe(failed);
                    }
                    OpenedResults result = answer;
                    String why = failure;
                    ui.execute(() -> runOpened(mine, run, result, why));
                });
    }

    private void runOpened(long answered, ResultsRun run, OpenedResults result, String failure) {
        if (answered != openGeneration) {
            if (result != null) {
                background.execute(() -> port.close(result));
            }
            return;
        }
        if (failure != null) {
            readiness.set(
                    "The results of run " + run.id().value() + " could not be opened: " + failure);
            return;
        }
        opened = result;
        List<TableKind> offered = new ArrayList<>();
        for (TableKind kind :
                List.of(
                        TableKind.TARGET_PSMS,
                        TableKind.TARGET_PEPTIDES,
                        TableKind.DECOY_PSMS,
                        TableKind.DECOY_PEPTIDES)) {
            if (result.stores().containsKey(kind)) {
                offered.add(kind);
            }
        }
        tables.set(List.copyOf(offered));
        ViewStateReading reading = result.viewState();
        viewStateRefused = reading.source() == ViewStateReading.Source.DEFAULTS_FILE_REFUSED;
        lastSaved = reading.filters();
        applying = true;
        try {
            filters.apply(reading.filters());
        } finally {
            applying = false;
        }
        viewStateStatus.set(viewStateText(reading));
        TableKind first = offered.get(0);
        chosenTable.set(Optional.of(first));
        table.show(result.stores().get(first), result.sourceFiles());
        weights.show(result.weights());
        readiness.set(
                "Showing the results of run "
                        + run.id().value()
                        + ", created "
                        + run.created()
                        + ".");
        publishExports();
    }

    private static String viewStateText(ViewStateReading reading) {
        return switch (reading.source()) {
            case SAVED ->
                    "The filters this run was last shown with were restored: PSM "
                            + reading.filters().psm().text()
                            + ", peptide "
                            + reading.filters().peptide().text()
                            + ".";
            case DEFAULTS_NOTHING_SAVED ->
                    "This run has no saved filters, so the defaults are shown: PSM 0.01, peptide"
                            + " 0.01.";
            case DEFAULTS_FILE_REFUSED ->
                    "This run's saved filters could not be read, so the defaults are shown and"
                            + " changes are not saved for this run: "
                            + reading.refusal().orElseThrow();
        };
    }

    private void filtersChanged(DisplayFilters now) {
        if (applying || opened == null || now.equals(lastSaved)) {
            return;
        }
        if (viewStateRefused) {
            viewStateStatus.set(
                    "This filter change is not saved for this run: its saved filters could not be"
                            + " read, and they are never overwritten.");
            return;
        }
        lastSaved = now;
        long mine = openGeneration;
        RunId run = opened.run();
        background.execute(
                () -> {
                    String said;
                    try {
                        port.saveViewState(run, now);
                        said =
                                "The filters are saved for this run: PSM "
                                        + now.psm().text()
                                        + ", peptide "
                                        + now.peptide().text()
                                        + ".";
                    } catch (IOException | RuntimeException failed) {
                        said = "The filters could not be saved for this run: " + describe(failed);
                    }
                    String outcome = said;
                    ui.execute(() -> saved(mine, outcome));
                });
    }

    private void saved(long answered, String outcome) {
        if (answered == openGeneration) {
            viewStateStatus.set(outcome);
        }
    }

    private void closeOpened() {
        table.clear();
        weights.show(Optional.empty());
        tables.set(List.of());
        chosenTable.set(Optional.empty());
        viewStateStatus.set(NO_VIEW_STATE);
        viewStateRefused = false;
        lastSaved = null;
        if (opened != null) {
            OpenedResults closing = opened;
            opened = null;
            background.execute(() -> port.close(closing));
        }
    }

    private void exportEnded(String outcome) {
        exporting = false;
        exportStatus.set(outcome);
        publishExports();
    }

    private static String exported(TableExport export) {
        return "Exported "
                + export.rowsWritten()
                + (export.rowsWritten() == 1 ? " row" : " rows")
                + " -- the "
                + categoryWords(export.category())
                + " of the "
                + TableCounts.rowsName(export.kind())
                + " table at a q-value cutoff of "
                + export.filter().text()
                + ", of "
                + export.before().total()
                + " in total -- to "
                + export.file()
                + ". Its metadata is in "
                + export.sidecar()
                + ".";
    }

    private static String categoryWords(Category category) {
        return switch (category) {
            case PASSING -> "passing rows";
            case UNKNOWN_Q_VALUE -> "rows with an unknown q-value";
            case FAILING -> "failing rows";
            case ALL -> "rows, all of them";
        };
    }

    private void publishExports() {
        String common = exportRefusal();
        tableExportRefusal.set(common);
        String weightsRefusal = common;
        if (weightsRefusal.isEmpty() && opened.weights().isEmpty()) {
            weightsRefusal = "This run has no learned feature weights to export.";
        }
        weightsExportRefusal.set(weightsRefusal);
        tableExportEnabled.set(common.isEmpty());
        weightsExportEnabled.set(weightsRefusal.isEmpty());
    }

    private String exportRefusal() {
        Optional<ResultsRun> run = chosenRun.get();
        if (run.isEmpty() || opened == null || chosenTable.get().isEmpty()) {
            return "Nothing can be exported: no run's results are open.";
        }
        if (run.get().executing()) {
            return "Nothing can be exported from run "
                    + run.get().id().value()
                    + " while it is executing: the workflow engine holds its provenance event"
                    + " log, where every export is recorded. Export when the run has ended.";
        }
        if (exporting) {
            return "An export is being written; the next can start when it has finished.";
        }
        return "";
    }

    private static String describe(Exception failed) {
        String message = failed.getMessage();
        return message == null || message.isBlank() ? failed.toString() : message;
    }
}
