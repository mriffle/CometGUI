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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.viewmodel.params.ChoiceOption;
import org.cometgui.ui.viewmodel.params.EditOutcome;
import org.cometgui.ui.viewmodel.params.EnzymeOption;
import org.cometgui.ui.viewmodel.params.EnzymesViewModel;
import org.cometgui.ui.viewmodel.params.ParameterSession;

/**
 * The custom-enzyme editor (<em>Enzyme definitions</em>): the configuration's {@code
 * [COMET_ENZYME_INFO]} rows, each with its rule in words and a remove action, and a form that adds
 * a custom row. The enzyme selectors offer exactly these rows, so a row added here can be selected
 * there, and a row a selector holds cannot be removed. Every decision -- reading the new row's
 * texts, the number used twice, the row in use -- is the {@link EnzymesViewModel}'s and the
 * model's; this editor shows the outcome.
 */
public final class EnzymeTableEditor extends VBox {

    private final EnzymesViewModel enzymes;

    private final VBox rows = new VBox(2);

    private final TextField number = new TextField();

    private final TextField name = new TextField();

    private final ComboBox<ChoiceOption> sense = new ComboBox<>();

    private final TextField cut = new TextField();

    private final TextField noCut = new TextField();

    private final Label status;

    /**
     * The enzyme table editor.
     *
     * @param enzymes the enzymes' view-model
     * @param session the session, whose enzyme table the editor follows
     * @param subscriptions where this build's listeners are registered
     */
    public EnzymeTableEditor(
            EnzymesViewModel enzymes, ParameterSession session, Subscriptions subscriptions) {
        this.enzymes = Objects.requireNonNull(enzymes, "enzymes");
        Objects.requireNonNull(session, "session");
        setSpacing(6);
        setPadding(new Insets(6, 0, 0, 0));

        Label title = new Label("Enzyme table ([COMET_ENZYME_INFO])");
        title.setStyle("-fx-font-weight: bold;");
        named(title, "Enzyme table");

        number.setId(UiIds.ENZYME_NEW_NUMBER);
        named(number, "New enzyme row: number");
        number.setPrefColumnCount(4);
        name.setId(UiIds.ENZYME_NEW_NAME);
        named(name, "New enzyme row: name");
        name.setPrefColumnCount(14);
        sense.setId(UiIds.ENZYME_NEW_SENSE);
        named(sense, "New enzyme row: side it cleaves on");
        sense.setConverter(
                new StringConverter<>() {
                    @Override
                    public String toString(ChoiceOption option) {
                        return option == null ? "" : option.label();
                    }

                    @Override
                    public ChoiceOption fromString(String text) {
                        return null;
                    }
                });
        List<ChoiceOption> senses = enzymes.senseChoices();
        sense.getItems().setAll(senses);
        if (!senses.isEmpty()) {
            sense.setValue(senses.get(0));
        }
        cut.setId(UiIds.ENZYME_NEW_CUT);
        named(cut, "New enzyme row: cut residues");
        cut.setPrefColumnCount(6);
        noCut.setId(UiIds.ENZYME_NEW_NO_CUT);
        named(noCut, "New enzyme row: no-cut residues (- for none)");
        noCut.setPrefColumnCount(6);
        noCut.setText("-");
        Button add =
                Texts.button(
                        UiIds.ENZYME_ADD,
                        "Add enzyme",
                        "Add the new enzyme row to the configuration's enzyme table");
        add.setOnAction(event -> add());
        HBox form =
                new HBox(
                        6,
                        caption("Number", number),
                        number,
                        caption("Name", name),
                        name,
                        caption("Cleaves", sense),
                        sense,
                        caption("Cut", cut),
                        cut,
                        caption("No cut", noCut),
                        noCut,
                        add);
        form.setAlignment(Pos.CENTER_LEFT);
        status =
                Texts.label(
                        UiIds.ENZYME_STATUS,
                        "Add a custom enzyme here; the enzyme selectors offer every row below.",
                        "enzyme table status");

        getChildren().addAll(title, rows, form, status);
        subscriptions.onChange(session.modelProperty(), this::refresh);
        refresh();
    }

    private static Label caption(String text, Control control) {
        Label label = new Label(text);
        named(label, text);
        label.setLabelFor(control);
        return label;
    }

    private void add() {
        String numberText = number.getText();
        String nameText = name.getText();
        EditOutcome outcome =
                enzymes.addCustom(
                        numberText, nameText, sense.getValue(), cut.getText(), noCut.getText());
        if (outcome.accepted()) {
            status.setText("Added enzyme " + numberText + ". " + nameText + ".");
            name.clear();
            cut.clear();
            noCut.setText("-");
            refresh();
        } else {
            status.setText("Not added: " + outcome.refusal().orElse(""));
        }
    }

    private void refresh() {
        rows.getChildren().clear();
        List<EnzymeOption> options = enzymes.options();
        for (int index = 0; index < options.size(); index++) {
            EnzymeOption option = options.get(index);
            String text = option.label() + " -- " + option.description();
            Label row = new Label(text);
            row.setId(UiIds.enzymeRow(index));
            row.setWrapText(true);
            named(row, "Enzyme row " + text);
            Button remove =
                    Texts.button(
                            UiIds.enzymeRowRemove(index),
                            "Remove",
                            "Remove enzyme row " + option.label());
            remove.setOnAction(
                    event -> {
                        EditOutcome outcome = enzymes.remove(option);
                        status.setText(
                                outcome.refusal()
                                        .map(reason -> "Not removed: " + reason)
                                        .orElse("Removed enzyme " + option.label() + "."));
                    });
            HBox line = new HBox(8, row, remove);
            line.setAlignment(Pos.CENTER_LEFT);
            rows.getChildren().add(line);
        }
        if (!number.isFocused()) {
            number.setText(enzymes.nextNumberText());
        }
    }
}
