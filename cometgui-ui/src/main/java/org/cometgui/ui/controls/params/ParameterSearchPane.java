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

package org.cometgui.ui.controls.params;

import static org.cometgui.ui.controls.AccessibleControls.named;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.viewmodel.params.ParameterSearchViewModel;
import org.cometgui.ui.viewmodel.params.SearchFilter;
import org.cometgui.ui.viewmodel.params.SearchHit;

/**
 * The global parameter search (<em>Global parameter search</em>, exit gate item 8): a query field,
 * the five filters as check boxes, and one result per parameter found, each saying in text why it
 * was found -- its name, display name, help text, category or an alias. Activating a result (Enter,
 * Space or a click) moves to the parameter's field.
 *
 * <p>The matching is the {@link ParameterSearchViewModel}'s. The results are listed while there is
 * a query or a filter; an empty query with no filter would list every parameter, which is what the
 * Advanced level already is.
 */
public final class ParameterSearchPane extends VBox {

    private final ParameterSearchViewModel search;

    private final Consumer<SearchHit> open;

    private final TextField query = new TextField();

    private final Label headline;

    private final VBox results = new VBox(2);

    private final ScrollPane scroller = new ScrollPane(results);

    /**
     * The search.
     *
     * @param search the search's view-model
     * @param open what activating a result does with it
     */
    public ParameterSearchPane(ParameterSearchViewModel search, Consumer<SearchHit> open) {
        this.search = Objects.requireNonNull(search, "search");
        this.open = Objects.requireNonNull(open, "open");
        setSpacing(4);

        Label label = new Label("Find a parameter");
        named(label, "Find a parameter");
        query.setId(UiIds.PARAM_SEARCH);
        named(query, "Find a parameter by name, display name, help text, category or alias");
        query.setPromptText("name, display name, help text, category or alias");
        query.setPrefColumnCount(30);
        label.setLabelFor(query);
        query.textProperty().addListener((observable, before, after) -> search.setQuery(after));
        HBox line = new HBox(8, label, query);
        line.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(query, Priority.SOMETIMES);

        FlowPane filters = new FlowPane(12, 4);
        for (SearchFilter filter : SearchFilter.values()) {
            CheckBox box = new CheckBox(filter.words());
            box.setId(UiIds.searchFilter(filter));
            named(box, "Search filter: " + filter.words());
            box.setOnAction(event -> search.setFilter(filter, box.isSelected()));
            filters.getChildren().add(box);
        }

        headline = Texts.label(UiIds.PARAM_SEARCH_HEADLINE, "", "search results");
        results.setId(UiIds.PARAM_SEARCH_RESULTS);
        scroller.setFitToWidth(true);
        scroller.setMaxHeight(220);
        named(scroller, "Search results");

        getChildren().addAll(line, filters, headline, scroller);
        search.resultsProperty().addListener((observable, before, after) -> refresh());
        search.filtersProperty().addListener((observable, before, after) -> refresh());
        search.queryProperty().addListener((observable, before, after) -> refresh());
        refresh();
    }

    private void refresh() {
        results.getChildren().clear();
        boolean searching = !search.queryProperty().get().isBlank() || !search.filters().isEmpty();
        scroller.setVisible(searching);
        scroller.setManaged(searching);
        if (!searching) {
            headline.setText("Type to find a parameter, or tick a filter.");
            return;
        }
        headline.setText(search.headline() + ".");
        List<SearchHit> hits = search.results();
        for (int index = 0; index < hits.size(); index++) {
            SearchHit hit = hits.get(index);
            String text = hit.label() + " -- " + hit.why();
            Button result = Texts.button(UiIds.searchResult(index), text, text);
            result.setMaxWidth(Double.MAX_VALUE);
            result.setAccessibleHelp("Activate to move to this parameter's field.");
            result.setOnAction(event -> open.accept(hit));
            result.setOnKeyPressed(
                    event -> {
                        if (event.getCode() == KeyCode.ENTER) {
                            event.consume();
                            result.fire();
                        }
                    });
            results.getChildren().add(result);
        }
    }
}
