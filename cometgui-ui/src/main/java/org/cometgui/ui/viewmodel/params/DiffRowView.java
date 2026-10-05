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

import java.util.Objects;
import java.util.Optional;
import org.cometgui.params.comet.presets.DiffRow;

/**
 * One row of a reviewable diff as the editor shows it: the model's {@link DiffRow} -- what differs,
 * the current text and the other side's -- with the parameter's display name in the selected
 * release where it has one.
 *
 * @param row the model's row
 * @param displayName the parameter's display name, for a row about a parameter the selected release
 *     models; empty for an enzyme row or an unknown parameter
 */
public record DiffRowView(DiffRow row, Optional<String> displayName) {

    /** What a side shows when it does not have the thing at all (an empty value is shown empty). */
    public static final String ABSENT = "(not present)";

    /** Validates presence. */
    public DiffRowView {
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(displayName, "displayName");
    }

    /**
     * A row with the display name the session's release gives its parameter.
     *
     * @param row the model's row
     * @param session the session
     * @return the row
     */
    static DiffRowView of(DiffRow row, ParameterSession session) {
        Optional<String> name =
                row.kind() == DiffRow.Kind.PARAMETER
                        ? session.displayNameOf(row.key())
                        : Optional.empty();
        return new DiffRowView(row, name);
    }

    /**
     * The first column.
     *
     * @return {@code Display name (name)} for a parameter, the model's label otherwise (an enzyme
     *     row's {@code [COMET_ENZYME_INFO] row N}, an unknown parameter's name)
     */
    public String label() {
        return displayName.map(name -> name + " (" + row.key() + ")").orElse(row.label());
    }

    /**
     * The current side's text.
     *
     * @return the text, or {@link #ABSENT}
     */
    public String current() {
        return row.current().orElse(ABSENT);
    }

    /**
     * The other side's text.
     *
     * @return the text, or {@link #ABSENT}
     */
    public String other() {
        return row.other().orElse(ABSENT);
    }
}
