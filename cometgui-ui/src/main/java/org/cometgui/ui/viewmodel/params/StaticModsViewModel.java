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

package org.cometgui.ui.viewmodel.params;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.schema.StaticModTarget;

/**
 * The static-modification table: one row per {@code add_*} parameter of the selected release, keyed
 * by the residue or terminus the model says it modifies ({@code StaticModTarget}), the termini
 * first as the release lists them. Every edit and reset is the field's, through the model.
 */
public final class StaticModsViewModel {

    private final ParameterSession session;

    /**
     * The table of a session.
     *
     * @param session the session
     */
    public StaticModsViewModel(ParameterSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    /**
     * The rows for the configuration as it is now, in the release's order.
     *
     * @return the rows
     */
    public List<StaticModRow> rows() {
        CometParameters model = session.model();
        List<StaticModRow> rows = new ArrayList<>();
        for (FieldViewModel field : session.fields()) {
            Optional<StaticModTarget> target = StaticModTarget.of(field.definition());
            if (target.isPresent()) {
                String name = field.name();
                boolean atDefault =
                        model.value(name).equals(model.resetToDefault(name).value(name));
                rows.add(
                        new StaticModRow(
                                target.get(),
                                field.text(),
                                field.defaultText(),
                                field.originText(),
                                atDefault));
            }
        }
        return List.copyOf(rows);
    }

    /**
     * A row's field: help, findings, pending refusal and state in words.
     *
     * @param row a row
     * @return the field
     */
    public FieldViewModel field(StaticModRow row) {
        return session.field(row.parameter());
    }

    /**
     * Sets a row's mass from the text the scientist entered.
     *
     * @param row a row
     * @param text the mass
     * @return whether the configuration now holds it, or the model's refusal
     */
    public EditOutcome setMass(StaticModRow row, String text) {
        return field(row).setText(text);
    }

    /**
     * Puts a row's mass back to the release's default.
     *
     * @param row a row
     * @return the outcome
     */
    public EditOutcome reset(StaticModRow row) {
        return field(row).reset();
    }

    /**
     * The row of a residue.
     *
     * @param residue the residue letter
     * @return the row, or empty if the release has no static modification for it
     */
    public Optional<StaticModRow> residue(char residue) {
        return rows().stream()
                .filter(row -> row.target().residue().equals(Optional.of(residue)))
                .findFirst();
    }
}
