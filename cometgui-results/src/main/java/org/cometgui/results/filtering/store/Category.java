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

package org.cometgui.results.filtering.store;

import java.util.Objects;
import org.cometgui.results.filtering.Visibility;

/**
 * Which rows a table view shows, by where they fall under its q-value filter (design decision
 * P10-5, {@code R-RES-02}). Every row is in exactly one {@link Visibility}; a category selects one
 * of them, or all three.
 */
public enum Category {

    /** Rows whose q-value is known and at or below the cutoff: the default. */
    PASSING,

    /** Rows whose q-value is missing, unparsable or out of range: never passing, never failing. */
    UNKNOWN_Q_VALUE,

    /** Rows whose q-value is known and above the cutoff. */
    FAILING,

    /** Every row, whatever its q-value. */
    ALL;

    /**
     * Whether a row with this visibility is in the category.
     *
     * @param visibility where the row falls under the filter
     * @return {@code true} if the category shows it
     * @throws NullPointerException if {@code visibility} is {@code null}
     */
    public boolean includes(Visibility visibility) {
        Objects.requireNonNull(visibility, "visibility");
        return switch (this) {
            case PASSING -> visibility == Visibility.PASSES;
            case UNKNOWN_Q_VALUE -> visibility == Visibility.UNKNOWN_Q_VALUE;
            case FAILING -> visibility == Visibility.FAILS;
            case ALL -> true;
        };
    }
}
