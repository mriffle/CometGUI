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

package org.cometgui.ui.controls.results;

import static org.cometgui.ui.controls.AccessibleControls.named;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.cometgui.results.filtering.store.Category;
import org.cometgui.results.filtering.store.ResultSort;
import org.cometgui.results.filtering.store.RowKey;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.controls.params.Texts;
import org.cometgui.ui.viewmodel.results.ColumnState;
import org.cometgui.ui.viewmodel.results.CopyOutcome;
import org.cometgui.ui.viewmodel.results.DisplayFiltersViewModel;
import org.cometgui.ui.viewmodel.results.ResultRowView;
import org.cometgui.ui.viewmodel.results.ResultTableViewModel;
import org.cometgui.ui.viewmodel.results.ResultsColumn;
import org.cometgui.ui.viewmodel.results.ResultsRun;
import org.cometgui.ui.viewmodel.results.ResultsViewModel;
import org.cometgui.ui.viewmodel.results.TableCounts;
import org.cometgui.ui.viewmodel.results.TablePage;
import org.cometgui.ui.viewmodel.results.WeightsViewModel;

/**
 * The Results section's content ({@code R-RES-01}..{@code R-RES-04}, design decision P10-9): the
 * run and table shown, the two q-value display filters, the four counts, the category, the text
 * filter, the page and its navigation, the table, its columns, selection, copy, the exports, and
 * the learned feature weights ({@link WeightsPane}).
 *
 * <h2>The table holds one page, never every row</h2>
 *
 * <p>The {@link TableView}'s items are the rows of {@link ResultTableViewModel#pageProperty()} --
 * at most {@link ResultTableViewModel#PAGE_SIZE} -- replaced whenever the page is; nothing here
 * holds or lists the table's rows ({@code R-RES-03}). The headings sort through {@link
 * ResultTableViewModel#sortBy}, which asks the store for the first page of the new order: every
 * column is made unsortable for the {@code TableView} itself, so it never sorts the page it holds,
 * which would order 200 rows of a million and call it sorted. Each heading is a button, so that a
 * sort can be reached and announced like any other action.
 *
 * <h2>Selection is the view-model's</h2>
 *
 * <p>A selection made in the table is forwarded by row key ({@link ResultTableViewModel#select},
 * {@link ResultTableViewModel#toggle}); whenever the page or the view-model's selection changes,
 * the rows of the page whose keys are selected are selected in the table again. So a selection
 * survives a filter change, a sort and a page move however the table is redrawn.
 *
 * <h2>A thin view</h2>
 *
 * <p>Every text shown is a view-model's, every action calls a view-model method. Nothing here reads
 * a number, compares a q-value, parses or hashes; the counts are the store's, written as text by
 * the view-model.
 */
public final class ResultsPane extends ScrollPane {

    /** How the table's text filter is described to a screen reader. */
    static final String TEXT_FILTER_NAME =
            "Text filter on PSMId, peptide and proteins, applied with Enter";

    private final ResultsViewModel results;

    private final ResultTableViewModel table;

    private final ComboBox<ResultsRun> run = new ComboBox<>();

    private final ComboBox<TableKind> tableChoice = new ComboBox<>();

    private final ComboBox<Category> category = new ComboBox<>();

    private final TextField text = new TextField();

    private final TableView<ResultRowView> rows = new TableView<>();

    private final ObservableList<ResultRowView> pageRows = FXCollections.observableArrayList();

    private final Map<ResultsColumn, TableColumn<ResultRowView, String>> columns =
            new EnumMap<>(ResultsColumn.class);

    private final Map<ResultsColumn, Button> sortButtons = new EnumMap<>(ResultsColumn.class);

    private final Map<ResultsColumn, CheckBox> columnToggles = new EnumMap<>(ResultsColumn.class);

    private final Label copyStatus;

    /** Set while the view moves a control itself, so that the move is not taken as a choice. */
    private boolean updating;

    /**
     * The section's content.
     *
     * @param results the run and table shown, the view state and the exports
     * @param table the table shown, as a page
     * @param weights the learned feature weights
     * @param filters the interface's one display-filter state, shared with the Percolator section
     */
    public ResultsPane(
            ResultsViewModel results,
            ResultTableViewModel table,
            WeightsViewModel weights,
            DisplayFiltersViewModel filters) {
        this.results = Objects.requireNonNull(results, "results");
        this.table = Objects.requireNonNull(table, "table");
        Objects.requireNonNull(weights, "weights");
        Objects.requireNonNull(filters, "filters");
        setId(UiIds.RESULTS_PANE);
        named(this, "the Results section's content");
        setFitToWidth(true);

        Label readiness =
                Texts.label(
                        UiIds.RESULTS_READINESS,
                        results.readinessProperty().get(),
                        "what the Results section shows");
        follow(readiness, results.readinessProperty());

        buildRunChoice();
        buildTableChoice();
        Button refresh =
                Texts.button(
                        UiIds.RESULTS_REFRESH,
                        "Read the runs again",
                        "Read this project's runs with results again");
        refresh.setOnAction(event -> results.refresh());

        TextField psm =
                filterField(
                        UiIds.RESULTS_PSM_FILTER,
                        "PSM q-value filter",
                        filters.psmFilterTextProperty(),
                        filters.filtersStatusProperty());
        psm.setOnAction(event -> filters.editPsmFilter(psm.getText()));
        commitOnFocusLoss(psm, () -> filters.editPsmFilter(psm.getText()));
        TextField peptide =
                filterField(
                        UiIds.RESULTS_PEPTIDE_FILTER,
                        "peptide q-value filter",
                        filters.peptideFilterTextProperty(),
                        filters.filtersStatusProperty());
        peptide.setOnAction(event -> filters.editPeptideFilter(peptide.getText()));
        commitOnFocusLoss(peptide, () -> filters.editPeptideFilter(peptide.getText()));
        Label filtersStatus =
                Texts.label(
                        UiIds.RESULTS_FILTERS_STATUS,
                        filters.filtersStatusProperty().get(),
                        "what the display filters do");
        follow(filtersStatus, filters.filtersStatusProperty());
        Label viewState =
                Texts.label(
                        UiIds.RESULTS_VIEW_STATE,
                        results.viewStateStatusProperty().get(),
                        "the open run's saved filters");
        follow(viewState, results.viewStateStatusProperty());

        Label total = count(UiIds.RESULTS_COUNT_TOTAL, "Total rows", TableCounts::total);
        Label passing = count(UiIds.RESULTS_COUNT_PASSING, "Passing", TableCounts::passing);
        Label failing = count(UiIds.RESULTS_COUNT_FAILING, "Failing", TableCounts::failing);
        Label unknown =
                count(UiIds.RESULTS_COUNT_UNKNOWN, "Unknown q-value", TableCounts::unknownQValue);
        Label summary =
                Texts.label(
                        UiIds.RESULTS_COUNTS,
                        table.counts().summary(),
                        "the table's counts under its filter");
        table.countsProperty()
                .addListener((observable, before, after) -> summary.setText(after.summary()));

        buildCategory();
        buildTextFilter();

        Button first = pageButton(UiIds.RESULTS_FIRST_PAGE, "First", "Show the first page");
        first.setOnAction(event -> table.firstPage());
        Button previous =
                pageButton(UiIds.RESULTS_PREVIOUS_PAGE, "Previous", "Show the previous page");
        previous.setOnAction(event -> table.previousPage());
        Button next = pageButton(UiIds.RESULTS_NEXT_PAGE, "Next", "Show the next page");
        next.setOnAction(event -> table.nextPage());
        Button last = pageButton(UiIds.RESULTS_LAST_PAGE, "Last", "Show the last page");
        last.setOnAction(event -> table.lastPage());
        Label pageText =
                Texts.label(UiIds.RESULTS_PAGE, table.page().text(), "where the page stands");
        table.pageProperty()
                .addListener(
                        (observable, before, after) -> {
                            pageText.setText(after.text());
                            enablePaging(first, previous, next, last, after);
                        });
        enablePaging(first, previous, next, last, table.page());

        buildTable();
        HBox columnSwitches = buildColumnToggles();
        Label columnStatus =
                Texts.label(
                        UiIds.RESULTS_COLUMN_STATUS,
                        table.columnStatusProperty().get(),
                        "no column change was refused");
        follow(columnStatus, table.columnStatusProperty());
        Label tableStatus =
                Texts.label(
                        UiIds.RESULTS_TABLE_STATUS,
                        table.statusProperty().get(),
                        "the table is shown");
        follow(tableStatus, table.statusProperty());
        Label selection =
                Texts.label(
                        UiIds.RESULTS_SELECTION,
                        table.selectionStatusProperty().get(),
                        "what is selected");
        follow(selection, table.selectionStatusProperty());

        Button copy =
                Texts.button(
                        UiIds.RESULTS_COPY,
                        "Copy selected rows",
                        "Copy the selected rows shown as tab-separated text");
        copy.setOnAction(event -> copySelection());
        copyStatus = Texts.label(UiIds.RESULTS_COPY_STATUS, "", "nothing has been copied");

        Button exportTable =
                Texts.button(
                        UiIds.RESULTS_EXPORT_TABLE,
                        "Export this table",
                        "Export the rows of the table shown, under its q-value filter and"
                                + " category");
        exportTable.setOnAction(event -> results.exportTable());
        exportTable.disableProperty().bind(results.tableExportEnabledProperty().not());
        exportTable.accessibleHelpProperty().bind(results.tableExportRefusalProperty());
        Button exportWeights =
                Texts.button(
                        UiIds.RESULTS_EXPORT_WEIGHTS,
                        "Export the learned feature weights",
                        "Export the learned feature weights");
        exportWeights.setOnAction(event -> results.exportWeights());
        exportWeights.disableProperty().bind(results.weightsExportEnabledProperty().not());
        exportWeights.accessibleHelpProperty().bind(results.weightsExportRefusalProperty());
        Label exportStatus =
                Texts.label(UiIds.RESULTS_EXPORT_STATUS, exportText(), "nothing has been exported");
        results.exportStatusProperty()
                .addListener((observable, before, after) -> exportStatus.setText(exportText()));
        results.tableExportRefusalProperty()
                .addListener((observable, before, after) -> exportStatus.setText(exportText()));

        table.pageProperty().addListener((observable, before, after) -> showPage(after));
        table.selectedProperty().addListener((observable, before, after) -> restoreSelection());
        table.columnsProperty().addListener((observable, before, after) -> showColumns(after));
        table.sortProperty().addListener((observable, before, after) -> showSort(after));
        showColumns(table.columnsProperty().get());
        showSort(table.sortProperty().get());
        showPage(table.page());

        WeightsPane weightsPane = new WeightsPane(weights);

        VBox content =
                new VBox(
                        6,
                        readiness,
                        new HBox(8, caption("Run"), run, refresh),
                        new HBox(8, caption("Table"), tableChoice),
                        caption("Result filters"),
                        new HBox(8, caption("PSM q-value filter"), psm),
                        new HBox(8, caption("Peptide q-value filter"), peptide),
                        filtersStatus,
                        viewState,
                        new HBox(16, total, passing, failing, unknown),
                        summary,
                        new HBox(8, caption("Rows shown"), category),
                        new HBox(8, caption("Text filter"), text),
                        new HBox(8, first, previous, next, last, pageText),
                        tableStatus,
                        rows,
                        columnSwitches,
                        columnStatus,
                        selection,
                        new HBox(8, copy, copyStatus),
                        new HBox(8, exportTable, exportWeights),
                        exportStatus,
                        weightsPane);
        setContent(content);
    }

    // ===================================================================== construction ====

    private void buildRunChoice() {
        run.setId(UiIds.RESULTS_RUN);
        named(run, "The run whose results are shown");
        run.setConverter(
                new StringConverter<>() {
                    @Override
                    public String toString(ResultsRun shown) {
                        return shown == null ? "" : shown.label();
                    }

                    @Override
                    public ResultsRun fromString(String typed) {
                        return null;
                    }
                });
        run.valueProperty()
                .addListener(
                        (observable, before, after) -> {
                            if (!updating && after != null) {
                                results.choose(after);
                            }
                        });
        results.runsProperty().addListener((observable, before, after) -> showRuns());
        results.chosenRunProperty().addListener((observable, before, after) -> showRuns());
        showRuns();
    }

    private void buildTableChoice() {
        tableChoice.setId(UiIds.RESULTS_TABLE_CHOICE);
        named(tableChoice, "The table shown: target or decoy PSMs or peptides");
        tableChoice.setConverter(
                new StringConverter<>() {
                    @Override
                    public String toString(TableKind kind) {
                        return kind == null ? "" : ResultsViewModel.tableLabel(kind);
                    }

                    @Override
                    public TableKind fromString(String typed) {
                        return null;
                    }
                });
        tableChoice
                .valueProperty()
                .addListener(
                        (observable, before, after) -> {
                            if (!updating && after != null) {
                                results.chooseTable(after);
                            }
                        });
        results.tablesProperty().addListener((observable, before, after) -> showTables());
        results.chosenTableProperty().addListener((observable, before, after) -> showTables());
        showTables();
    }

    private void buildCategory() {
        category.setId(UiIds.RESULTS_CATEGORY);
        named(category, "Rows shown: passing, unknown q-value, failing or all");
        category.getItems().setAll(Category.values());
        category.setConverter(
                new StringConverter<>() {
                    @Override
                    public String toString(Category shown) {
                        return shown == null ? "" : categoryLabel(shown);
                    }

                    @Override
                    public Category fromString(String typed) {
                        return null;
                    }
                });
        category.setValue(table.categoryProperty().get());
        category.valueProperty()
                .addListener(
                        (observable, before, after) -> {
                            if (!updating && after != null) {
                                table.setCategory(after);
                            }
                        });
        table.categoryProperty()
                .addListener(
                        (observable, before, after) -> {
                            updating = true;
                            try {
                                category.setValue(after);
                            } finally {
                                updating = false;
                            }
                        });
    }

    private void buildTextFilter() {
        text.setId(UiIds.RESULTS_TEXT_FILTER);
        named(text, TEXT_FILTER_NAME);
        text.setPromptText("Text to find, then Enter");
        text.textProperty()
                .addListener(
                        (observable, before, after) -> {
                            if (!updating) {
                                table.editText(after);
                            }
                        });
        text.setOnAction(event -> table.applyText());
        table.textDraftProperty()
                .addListener(
                        (observable, before, after) -> {
                            if (!text.getText().equals(after)) {
                                updating = true;
                                try {
                                    text.setText(after);
                                } finally {
                                    updating = false;
                                }
                            }
                        });
        text.accessibleHelpProperty()
                .bind(
                        Bindings.createStringBinding(
                                () ->
                                        table.textPendingProperty().get()
                                                ? "Not applied yet: press Enter to apply it."
                                                : appliedTextHelp(
                                                        table.appliedTextProperty().get()),
                                table.textPendingProperty(),
                                table.appliedTextProperty()));
    }

    private void buildTable() {
        rows.setId(UiIds.RESULTS_TABLE);
        named(rows, "Results table: one page of rows");
        rows.setItems(pageRows);
        rows.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        rows.setPrefHeight(420);
        rows.setPlaceholder(new Label("No row to show."));
        for (ResultsColumn column : ResultsColumn.values()) {
            TableColumn<ResultRowView, String> shown = new TableColumn<>();
            // the TableView never sorts the page it holds: every sort is the store's
            shown.setSortable(false);
            shown.setReorderable(false);
            shown.setPrefWidth(column == ResultsColumn.PSM_ID ? 280 : 110);
            shown.setCellValueFactory(
                    cell -> new ReadOnlyStringWrapper(cell.getValue().cell(column)));
            Button heading = new Button(column.label());
            heading.setId(UiIds.resultsSort(column));
            named(heading, "Sort the table by " + column.label());
            heading.setOnAction(event -> table.sortBy(column));
            shown.setGraphic(heading);
            columns.put(column, shown);
            sortButtons.put(column, heading);
            rows.getColumns().add(shown);
        }
        rows.getSelectionModel()
                .getSelectedItems()
                .addListener((ListChangeListener<ResultRowView>) change -> forwardSelection());
        KeyCombination copyKeys = new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN);
        rows.setOnKeyPressed(
                event -> {
                    if (copyKeys.match(event)) {
                        copySelection();
                        event.consume();
                    }
                });
    }

    private HBox buildColumnToggles() {
        HBox switches = new HBox(8, caption("Columns"));
        for (ResultsColumn column : ResultsColumn.values()) {
            CheckBox toggle = new CheckBox(column.label());
            toggle.setId(UiIds.resultsColumnToggle(column));
            named(toggle, "Show the " + column.label() + " column");
            toggle.setOnAction(
                    event -> {
                        table.setColumnVisible(column, toggle.isSelected());
                        // a refused change leaves the column as it was: show what is true
                        showColumns(table.columnsProperty().get());
                    });
            columnToggles.put(column, toggle);
            switches.getChildren().add(toggle);
        }
        return switches;
    }

    // ============================================================================ sync ====

    private void showRuns() {
        updating = true;
        try {
            List<ResultsRun> listed = results.runsProperty().get();
            if (!run.getItems().equals(listed)) {
                run.getItems().setAll(listed);
            }
            Optional<ResultsRun> chosen = results.chosenRunProperty().get();
            run.setValue(chosen.orElse(null));
            run.setDisable(listed.isEmpty());
        } finally {
            updating = false;
        }
    }

    private void showTables() {
        updating = true;
        try {
            List<TableKind> offered = results.tablesProperty().get();
            if (!tableChoice.getItems().equals(offered)) {
                tableChoice.getItems().setAll(offered);
            }
            tableChoice.setValue(results.chosenTableProperty().get().orElse(null));
            tableChoice.setDisable(offered.isEmpty());
        } finally {
            updating = false;
        }
    }

    private void showPage(TablePage page) {
        updating = true;
        try {
            pageRows.setAll(page.rows());
        } finally {
            updating = false;
        }
        restoreSelection();
    }

    /** Selects in the table exactly the page's rows whose keys the view-model holds selected. */
    private void restoreSelection() {
        Set<RowKey> selected = new HashSet<>(table.selectedProperty().get());
        List<Integer> wanted = new ArrayList<>();
        for (int index = 0; index < pageRows.size(); index++) {
            if (selected.contains(pageRows.get(index).key())) {
                wanted.add(index);
            }
        }
        List<Integer> now = new ArrayList<>(rows.getSelectionModel().getSelectedIndices());
        now.sort(null);
        if (now.equals(wanted)) {
            return;
        }
        updating = true;
        try {
            rows.getSelectionModel().clearSelection();
            for (int index : wanted) {
                rows.getSelectionModel().select(index);
            }
        } finally {
            updating = false;
        }
    }

    /** Hands a selection made in the table to the view-model, by row key. */
    private void forwardSelection() {
        if (updating) {
            return;
        }
        List<RowKey> inTable = new ArrayList<>();
        for (ResultRowView row : rows.getSelectionModel().getSelectedItems()) {
            if (row != null) {
                inTable.add(row.key());
            }
        }
        List<RowKey> held = table.selectedProperty().get();
        if (inTable.size() == 1) {
            if (!held.equals(inTable)) {
                table.select(inTable.get(0));
            }
            return;
        }
        Set<RowKey> chosen = new HashSet<>(inTable);
        Set<RowKey> selected = new HashSet<>(held);
        for (ResultRowView row : List.copyOf(pageRows)) {
            if (chosen.contains(row.key()) != selected.contains(row.key())) {
                table.toggle(row.key());
            }
        }
    }

    private void showColumns(List<ColumnState> states) {
        Map<ResultsColumn, ColumnState> byColumn = new EnumMap<>(ResultsColumn.class);
        for (ColumnState state : states) {
            byColumn.put(state.column(), state);
        }
        for (ResultsColumn column : ResultsColumn.values()) {
            ColumnState state = byColumn.get(column);
            CheckBox toggle = columnToggles.get(column);
            boolean inTable = state != null;
            columns.get(column).setVisible(inTable && state.visible());
            toggle.setVisible(inTable);
            toggle.setManaged(inTable);
            toggle.setSelected(inTable && state.visible());
            toggle.setDisable(!inTable || !state.hideable());
            toggle.setAccessibleHelp(
                    inTable && !state.hideable()
                            ? "This column cannot be hidden: the run has several spectrum files."
                            : null);
        }
    }

    private void showSort(ResultSort sort) {
        for (Map.Entry<ResultsColumn, Button> entry : sortButtons.entrySet()) {
            ResultsColumn column = entry.getKey();
            Button heading = entry.getValue();
            String order = "";
            if (sort.column() == column.sortColumn()) {
                order =
                        sort.direction() == ResultSort.Direction.ASCENDING
                                ? ", ascending"
                                : ", descending";
            }
            heading.setText(column.label() + order);
            heading.setAccessibleHelp(
                    order.isEmpty()
                            ? "Not sorted by this column. Press to sort ascending."
                            : "Sorted by this column" + order + ". Press to change the order.");
        }
    }

    private void copySelection() {
        CopyOutcome outcome = table.copy();
        String said = outcome.message();
        if (outcome.rowsCopied() > 0) {
            ClipboardContent content = new ClipboardContent();
            content.putString(outcome.text());
            try {
                Clipboard.getSystemClipboard().setContent(content);
            } catch (RuntimeException unavailable) {
                said = "The rows could not be put on the clipboard: " + unavailable.getMessage();
            }
        }
        copyStatus.setText(said);
    }

    private String exportText() {
        String status = results.exportStatusProperty().get();
        return status.isEmpty() ? results.tableExportRefusalProperty().get() : status;
    }

    private Label count(String id, String caption, Function<TableCounts, String> value) {
        Label label = new Label(value.apply(table.counts()));
        label.setId(id);
        named(label, countName(caption, value.apply(table.counts())));
        table.countsProperty()
                .addListener(
                        (observable, before, after) -> {
                            String shown = value.apply(after);
                            label.setText(shown);
                            label.setAccessibleText(countName(caption, shown));
                        });
        return label;
    }

    private static String countName(String caption, String shown) {
        return caption + ": " + (shown.isEmpty() ? "none shown" : shown);
    }

    private static void enablePaging(
            Button first, Button previous, Button next, Button last, TablePage page) {
        boolean atStart = page.pageNumber() <= 1;
        boolean atEnd = page.pageNumber() >= page.pageCount();
        first.setDisable(atStart);
        previous.setDisable(atStart);
        next.setDisable(atEnd);
        last.setDisable(atEnd);
    }

    private static Button pageButton(String id, String shown, String name) {
        return Texts.button(id, shown, name);
    }

    private static TextField filterField(
            String id,
            String name,
            ObservableValue<String> fieldText,
            ObservableValue<String> status) {
        TextField field = new TextField(fieldText.getValue());
        field.setId(id);
        named(field, name + " in the Results section, a display filter that never reruns a tool");
        field.accessibleHelpProperty().bind(status);
        fieldText.addListener(
                (observable, before, after) -> {
                    if (!field.getText().equals(after)) {
                        field.setText(after);
                    }
                });
        return field;
    }

    /** Commits a field's text when it loses the focus. */
    private static void commitOnFocusLoss(TextField field, Runnable commit) {
        field.focusedProperty()
                .addListener(
                        (observable, before, after) -> {
                            if (!after) {
                                commit.run();
                            }
                        });
    }

    private static String appliedTextHelp(String applied) {
        return applied.isEmpty() ? "No text filter is applied." : "Applied: " + applied;
    }

    /**
     * How the category selector names a category.
     *
     * @param shown the category
     * @return for example {@code Passing rows}
     */
    static String categoryLabel(Category shown) {
        return switch (shown) {
            case PASSING -> "Passing rows";
            case UNKNOWN_Q_VALUE -> "Rows with an unknown q-value";
            case FAILING -> "Failing rows";
            case ALL -> "All rows";
        };
    }

    static Label caption(String shown) {
        Label caption = new Label(shown);
        named(caption, shown);
        return caption;
    }

    static void follow(Label label, ObservableValue<String> shown) {
        shown.addListener((observable, before, after) -> label.setText(after));
    }
}
