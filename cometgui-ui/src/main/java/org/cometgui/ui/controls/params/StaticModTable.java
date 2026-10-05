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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.controls.UiIds.Surface;
import org.cometgui.ui.viewmodel.params.FieldViewModel;
import org.cometgui.ui.viewmodel.params.ParameterSession;
import org.cometgui.ui.viewmodel.params.StaticModRow;
import org.cometgui.ui.viewmodel.params.StaticModsViewModel;

/**
 * The static modifications as the specification's residue/terminus-oriented table (<em>Typed
 * control requirements</em>, <em>Static modifications</em>): one row per {@code add_*} parameter
 * shown, with the residue or terminus in words, the mass added (edited through the model), the
 * modification's name, its default state and where its value came from, a reset, and its validation
 * state -- a refused mass's own message from the model, in text, at the row.
 *
 * <p>Comet has no name for a static modification -- a static modification is only a mass per
 * residue or terminus -- so the name column shows the parameter's display name, such as {@code
 * Static modification: lysine (K)}. Every row is the {@link StaticModsViewModel}'s; this class
 * reads its rows and hands it the text typed, and judges nothing.
 *
 * <p>Each row's cells carry the per-parameter identifiers of its parameter, so the mass field is
 * found, named and checked exactly as any other parameter control: the mass field {@link
 * UiIds#parameterControl}, the residue or terminus {@link UiIds#parameterLabel} (the field's
 * label), the default state {@link UiIds#parameterOrigin}, the reset {@link UiIds#parameterReset},
 * the validation state {@link UiIds#parameterState}, the name {@link UiIds#staticModName}.
 */
public final class StaticModTable extends VBox {

    private final StaticModsViewModel table;

    private final Map<String, Row> rows = new LinkedHashMap<>();

    /**
     * The table of some static modifications.
     *
     * @param table the static modifications' view-model
     * @param fields the parameters to show, in order; each must be a row of {@code table}
     * @param surface where the table is shown, which its identifiers name
     * @param session the session, whose configuration the table follows
     * @param subscriptions where this build's listeners are registered
     * @throws IllegalArgumentException if a field is not a static modification of the release
     */
    public StaticModTable(
            StaticModsViewModel table,
            List<FieldViewModel> fields,
            Surface surface,
            ParameterSession session,
            Subscriptions subscriptions) {
        this.table = Objects.requireNonNull(table, "table");
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(session, "session");
        setId(UiIds.staticModTable(surface));
        setSpacing(4);
        Map<String, StaticModRow> current = current();
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(4);
        List<String> headings =
                List.of(
                        "Residue or terminus",
                        "Mass added",
                        "Modification",
                        "Default state",
                        "",
                        "Validation");
        for (int column = 0; column < headings.size(); column++) {
            if (!headings.get(column).isEmpty()) {
                Label heading = new Label(headings.get(column));
                heading.setStyle("-fx-font-weight: bold;");
                named(heading, "Column: " + headings.get(column));
                grid.add(heading, column, 0);
            }
        }
        int line = 1;
        for (FieldViewModel field : fields) {
            if (!current.containsKey(field.name())) {
                throw new IllegalArgumentException(
                        field.name() + " is not a static modification of this release");
            }
            Row row = new Row(field, surface, current.get(field.name()));
            rows.put(field.name(), row);
            row.place(grid, line++);
            subscriptions.onChange(field.stateTextProperty(), this::refresh);
            subscriptions.onChange(field.textProperty(), this::refresh);
        }
        Label note =
                new Label(
                        "Comet has no name for a static modification: the name shown is the"
                                + " setting's own.");
        note.setWrapText(true);
        named(note, note.getText());
        getChildren().addAll(grid, note);
        subscriptions.onChange(session.modelProperty(), this::refresh);
        refresh();
    }

    /**
     * Whether the table shows a parameter.
     *
     * @param name the parameter
     * @return {@code true} if it is a row of this table
     */
    public boolean shows(String name) {
        return rows.containsKey(name);
    }

    /**
     * Moves the keyboard focus to a row's mass field.
     *
     * @param name the row's parameter
     * @throws IllegalArgumentException if the table does not show it
     */
    public void focus(String name) {
        Row row = rows.get(name);
        if (row == null) {
            throw new IllegalArgumentException(name + " is not a row of this table");
        }
        row.mass.requestFocus();
    }

    private Map<String, StaticModRow> current() {
        Map<String, StaticModRow> byName = new LinkedHashMap<>();
        for (StaticModRow row : table.rows()) {
            byName.put(row.parameter(), row);
        }
        return byName;
    }

    /** Puts every row in step with the configuration. */
    private void refresh() {
        Map<String, StaticModRow> current = current();
        for (Row row : rows.values()) {
            row.refresh(current.get(row.field.name()));
        }
    }

    /** One row's cells. */
    private final class Row {

        private final FieldViewModel field;

        private final Label target;

        private final TextField mass = new TextField();

        private final Label name;

        private final Label origin;

        private final Button reset;

        private final Label state;

        private boolean updating;

        private boolean dirty;

        Row(FieldViewModel field, Surface surface, StaticModRow first) {
            this.field = field;
            String parameter = field.name();
            target = new Label(first.words());
            target.setId(UiIds.parameterLabel(surface, parameter));
            named(target, "Residue or terminus: " + first.words());
            target.setMinWidth(170);
            target.setLabelFor(mass);

            mass.setId(UiIds.parameterControl(surface, parameter));
            named(mass, field.displayName());
            mass.setPrefColumnCount(1);
            mass.setPrefWidth(120);
            mass.textProperty()
                    .addListener(
                            (observable, before, after) -> {
                                if (!updating) {
                                    dirty = true;
                                }
                            });
            mass.setOnAction(event -> commit());
            mass.focusedProperty()
                    .addListener(
                            (observable, wasFocused, focused) -> {
                                if (!focused && dirty) {
                                    commit();
                                }
                            });

            name =
                    Texts.label(
                            UiIds.staticModName(surface, parameter),
                            field.displayName(),
                            "name of the modification");
            origin =
                    Texts.label(
                            UiIds.parameterOrigin(surface, parameter),
                            "",
                            "default state of " + field.displayName());
            reset =
                    Texts.button(
                            UiIds.parameterReset(surface, parameter),
                            "Reset",
                            "Reset "
                                    + field.displayName()
                                    + " to the Comet "
                                    + field.release().text()
                                    + " default");
            reset.setOnAction(
                    event -> {
                        table.reset(table.row(parameter).orElseThrow());
                        StaticModTable.this.refresh();
                    });
            state =
                    Texts.label(
                            UiIds.parameterState(surface, parameter),
                            field.stateText(),
                            "validation state of " + field.displayName());
        }

        void place(GridPane grid, int line) {
            grid.add(target, 0, line);
            grid.add(mass, 1, line);
            grid.add(name, 2, line);
            grid.add(origin, 3, line);
            grid.add(reset, 4, line);
            grid.add(state, 5, line);
            target.setAlignment(Pos.CENTER_LEFT);
        }

        /** Hands the typed mass to the table's view-model, which hands it to the model. */
        private void commit() {
            dirty = false;
            table.setMass(table.row(field.name()).orElseThrow(), mass.getText());
            // Shown again explicitly: a refused edit leaves the model untouched, and a property
            // that ends equal to what it was fires nothing.
            StaticModTable.this.refresh();
        }

        void refresh(StaticModRow row) {
            updating = true;
            try {
                if (!dirty) {
                    mass.setText(row.massText());
                }
                origin.setText(row.stateText());
                state.setText(field.stateText());
                String help = help(row);
                mass.setAccessibleHelp(help);
                Tooltip tooltip = mass.getTooltip();
                if (tooltip == null) {
                    mass.setTooltip(new Tooltip(help));
                } else {
                    tooltip.setText(help);
                }
            } finally {
                updating = false;
            }
        }

        /** What a tooltip and a screen reader say about the row's mass. */
        private String help(StaticModRow row) {
            List<String> lines = new ArrayList<>();
            lines.add(
                    field.displayName()
                            + " ("
                            + field.name()
                            + "): the mass added to every "
                            + row.words()
                            + ". "
                            + field.shortHelp());
            lines.add("Comet " + field.release().text() + " default: " + row.defaultText() + ".");
            lines.add(row.stateText() + ".");
            lines.add("Validation: " + field.stateText());
            lines.add("Documentation: " + field.helpUrl());
            return String.join("\n", lines);
        }
    }
}
