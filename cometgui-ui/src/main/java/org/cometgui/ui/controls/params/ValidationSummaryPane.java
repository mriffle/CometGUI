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

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.viewmodel.params.SummaryEntry;
import org.cometgui.ui.viewmodel.params.ValidationSummaryViewModel;

/**
 * The validation summary at the top of the editor: the counts in words, then one focusable entry
 * per finding, each saying its kind, its parameter, its category and the message in text.
 * Activating an entry -- Enter or Space on the focused entry, or a click -- asks the editor to move
 * the keyboard focus to the entry's field ("Errors shall be ... summarised at the top of the
 * editor, and reachable by keyboard").
 */
public final class ValidationSummaryPane extends VBox {

    private final ValidationSummaryViewModel summary;

    private final Consumer<String> focusField;

    private final Label headline;

    private final VBox entries = new VBox(2);

    /**
     * The summary.
     *
     * @param summary the view-model
     * @param focusField what activating an entry does with its parameter's name
     */
    public ValidationSummaryPane(ValidationSummaryViewModel summary, Consumer<String> focusField) {
        this.summary = Objects.requireNonNull(summary, "summary");
        this.focusField = Objects.requireNonNull(focusField, "focusField");
        setId(UiIds.PARAM_SUMMARY);
        setSpacing(4);
        headline =
                Texts.label(UiIds.PARAM_SUMMARY_HEADLINE, summary.headline(), "validation summary");
        getChildren().addAll(headline, entries);
        summary.entriesProperty().addListener((observable, before, after) -> refresh());
        summary.headlineProperty().addListener((observable, before, after) -> refresh());
        refresh();
    }

    private void refresh() {
        headline.setText("Validation: " + summary.headline());
        entries.getChildren().clear();
        List<SummaryEntry> shown = summary.entries();
        for (int index = 0; index < shown.size(); index++) {
            SummaryEntry entry = shown.get(index);
            Button button = Texts.button(UiIds.summaryEntry(index), entry.text(), entry.text());
            button.setWrapText(true);
            button.setMaxWidth(Double.MAX_VALUE);
            entry.focusTarget()
                    .ifPresentOrElse(
                            name -> {
                                button.setAccessibleHelp(
                                        "Activate to move the focus to the field it names.");
                                button.setOnAction(event -> focusField.accept(name));
                                button.setOnKeyPressed(
                                        event -> {
                                            if (event.getCode() == KeyCode.ENTER) {
                                                event.consume();
                                                button.fire();
                                            }
                                        });
                            },
                            () ->
                                    button.setAccessibleHelp(
                                            "This finding is not attached to one field."));
            entries.getChildren().add(button);
        }
    }
}
