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

import java.util.Objects;

/**
 * One column of the open table, and whether it is shown.
 *
 * @param column the column
 * @param visible whether it is shown
 * @param hideable whether the scientist may hide it; {@code false} for the source-file column of a
 *     run with more than one spectrum file, which is mandatory ({@code R-RES}, PSM table)
 */
public record ColumnState(ResultsColumn column, boolean visible, boolean hideable) {

    /**
     * A column's state.
     *
     * @throws NullPointerException if {@code column} is {@code null}
     */
    public ColumnState {
        Objects.requireNonNull(column, "column");
    }

    /**
     * The column's heading.
     *
     * @return the label
     */
    public String label() {
        return column.label();
    }
}
