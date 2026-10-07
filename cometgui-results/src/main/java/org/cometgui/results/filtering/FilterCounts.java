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

package org.cometgui.results.filtering;

/**
 * How many rows fall where under one display filter: the total and passing counts the results view
 * shows ({@code R-RES-01}), and the unknown-q-value category ({@code R-RES-02}).
 *
 * @param total every row counted
 * @param passing rows whose q-value is at or below the cutoff
 * @param failing rows whose q-value is above it
 * @param unknownQValue rows whose q-value is not known
 */
public record FilterCounts(long total, long passing, long failing, long unknownQValue) {

    /**
     * Counts that add up.
     *
     * @throws IllegalArgumentException if a count is negative or {@code total} is not the sum of
     *     the other three: a row falls in exactly one category
     */
    public FilterCounts {
        if (passing < 0
                || failing < 0
                || unknownQValue < 0
                || total != passing + failing + unknownQValue) {
            throw new IllegalArgumentException(
                    "every row falls in exactly one category: total "
                            + total
                            + ", passing "
                            + passing
                            + ", failing "
                            + failing
                            + ", unknown q-value "
                            + unknownQValue);
        }
    }
}
