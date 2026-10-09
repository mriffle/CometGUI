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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.controls.params.Texts;
import org.cometgui.ui.viewmodel.results.WeightsColumn;
import org.cometgui.ui.viewmodel.results.WeightsRowView;
import org.cometgui.ui.viewmodel.results.WeightsSort;
import org.cometgui.ui.viewmodel.results.WeightsViewModel;

/**
 * The learned feature weights view ({@code R-PERC-08}, {@code R-PERC-09}, design decision P10-8):
 * its specified title, the description of what the values are and are not, and a sortable table --
 * the source of truth -- with one column per cross-validation split the file holds.
 *
 * <p>Each heading is a button that sorts through {@link WeightsViewModel#sortBy} or {@link
 * WeightsViewModel#sortBySplit}, which sort on the summary's numbers; the {@link TableView} itself
 * never sorts, because it would sort the text shown.
 *
 * <p><strong>No chart is drawn.</strong> The specification makes the bar chart optional and the
 * table the accessibility and export source of truth. A chart would need each mean absolute weight
 * as a number, and the view-model gives the view text only, as it must: reading the numbers back
 * out of their text here would be number parsing in a view. So the chart is not built.
 */
public final class WeightsPane extends VBox {

    private final WeightsViewModel weights;

    private final TableView<WeightsRowView> table = new TableView<>();

    private final ObservableList<WeightsRowView> shown = FXCollections.observableArrayList();

    /** The fixed columns' heading buttons, by column; the split headings are rebuilt. */
    private final Map<WeightsColumn, Button> headings = new LinkedHashMap<>();

    private final List<Button> splitHeadings = new ArrayList<>();

    private final TableColumn<WeightsRowView, String> feature;

    private final List<TableColumn<WeightsRowView, String>> statistics = new ArrayList<>();

    /**
     * The view.
     *
     * @param weights the learned feature weights view-model
     */
    public WeightsPane(WeightsViewModel weights) {
        this.weights = Objects.requireNonNull(weights, "weights");
        setSpacing(6);

        Label title = new Label(WeightsViewModel.TITLE);
        title.setId(UiIds.WEIGHTS_TITLE);
        named(title, WeightsViewModel.TITLE);
        Label description = new Label(WeightsViewModel.DESCRIPTION);
        description.setId(UiIds.WEIGHTS_DESCRIPTION);
        description.setWrapText(true);
        named(description, WeightsViewModel.DESCRIPTION);
        Label status =
                Texts.label(
                        UiIds.WEIGHTS_STATUS,
                        weights.statusProperty().get(),
                        "the learned feature weights shown");
        weights.statusProperty().addListener((observable, before, after) -> status.setText(after));

        table.setId(UiIds.WEIGHTS_TABLE);
        named(table, WeightsViewModel.TITLE + ": one row per feature");
        table.setItems(shown);
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        table.setPrefHeight(320);
        table.setPlaceholder(new Label(WeightsViewModel.NO_WEIGHTS));

        feature =
                column(
                        WeightsColumn.FEATURE,
                        "Feature",
                        row ->
                                row.note().isEmpty()
                                        ? row.feature()
                                        : row.feature() + " (" + row.note() + ")");
        statistics.add(
                column(WeightsColumn.MEAN_SIGNED, "Mean signed", WeightsRowView::meanSigned));
        statistics.add(
                column(WeightsColumn.MEAN_ABSOLUTE, "Mean absolute", WeightsRowView::meanAbsolute));
        statistics.add(
                column(
                        WeightsColumn.STANDARD_DEVIATION,
                        "Standard deviation",
                        WeightsRowView::standardDeviation));
        statistics.add(
                column(
                        WeightsColumn.SIGN_CONSISTENCY,
                        "Sign consistency",
                        WeightsRowView::signConsistency));
        statistics.add(column(WeightsColumn.RANK, "Rank", WeightsRowView::rank));

        weights.splitHeadingsProperty()
                .addListener((observable, before, after) -> showColumns(after));
        weights.rowsProperty().addListener((observable, before, after) -> shown.setAll(after));
        weights.sortProperty().addListener((observable, before, after) -> showSort(after));
        showColumns(weights.splitHeadingsProperty().get());
        shown.setAll(weights.rowsProperty().get());
        showSort(weights.sortProperty().get());

        getChildren().addAll(title, description, status, table);
    }

    private TableColumn<WeightsRowView, String> column(
            WeightsColumn sortedBy, String label, Function<WeightsRowView, String> cell) {
        TableColumn<WeightsRowView, String> column = new TableColumn<>();
        column.setSortable(false);
        column.setReorderable(false);
        column.setPrefWidth(sortedBy == WeightsColumn.FEATURE ? 220 : 120);
        column.setCellValueFactory(
                value -> new ReadOnlyStringWrapper(cell.apply(value.getValue())));
        Button heading = new Button(label);
        heading.setId(UiIds.weightsSort(sortedBy));
        named(heading, "Sort the learned feature weights by " + label);
        heading.setUserData(label);
        heading.setOnAction(event -> weights.sortBy(sortedBy));
        column.setGraphic(heading);
        headings.put(sortedBy, heading);
        return column;
    }

    private void showColumns(List<String> splits) {
        splitHeadings.clear();
        List<TableColumn<WeightsRowView, String>> all = new ArrayList<>();
        all.add(feature);
        for (int index = 0; index < splits.size(); index++) {
            int split = index;
            String label = splits.get(index);
            TableColumn<WeightsRowView, String> column = new TableColumn<>();
            column.setSortable(false);
            column.setReorderable(false);
            column.setPrefWidth(90);
            column.setCellValueFactory(
                    value -> new ReadOnlyStringWrapper(value.getValue().splits().get(split)));
            Button heading = new Button(label);
            heading.setId(UiIds.weightsSortSplit(split));
            named(heading, "Sort the learned feature weights by the weight of " + label);
            heading.setUserData(label);
            heading.setOnAction(event -> weights.sortBySplit(split));
            column.setGraphic(heading);
            splitHeadings.add(heading);
            all.add(column);
        }
        all.addAll(statistics);
        table.getColumns().setAll(all);
        showSort(weights.sortProperty().get());
    }

    private void showSort(WeightsSort sort) {
        for (Map.Entry<WeightsColumn, Button> entry : headings.entrySet()) {
            mark(entry.getValue(), sort.column() == entry.getKey(), sort.descending());
        }
        for (int split = 0; split < splitHeadings.size(); split++) {
            mark(
                    splitHeadings.get(split),
                    sort.column() == WeightsColumn.SPLIT && sort.split() == split,
                    sort.descending());
        }
    }

    private static void mark(Button heading, boolean sorted, boolean descending) {
        String label = (String) heading.getUserData();
        String order = !sorted ? "" : descending ? ", descending" : ", ascending";
        heading.setText(label + order);
        heading.setAccessibleHelp(
                order.isEmpty()
                        ? "Not sorted by this column. Press to sort ascending."
                        : "Sorted by this column" + order + ". Press to change the order.");
    }
}
