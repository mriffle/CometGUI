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

package org.cometgui.results.parser;

/**
 * How many rows a result table holds, split by whether their q-value is known ({@code R-RES-02}).
 *
 * @param rows every row read, header excluded
 * @param knownQValues rows whose q-value is a number a filter can compare
 * @param unknownQValues rows whose q-value is missing, unparsable or out of range
 */
public record ResultTableCounts(long rows, long knownQValues, long unknownQValues) {

    /**
     * Counts that add up.
     *
     * @throws IllegalArgumentException if a count is negative or {@code rows} is not the sum of the
     *     other two: a row is in exactly one category
     */
    public ResultTableCounts {
        if (knownQValues < 0 || unknownQValues < 0 || rows != knownQValues + unknownQValues) {
            throw new IllegalArgumentException(
                    "every row has a known or an unknown q-value, never both or neither: rows "
                            + rows
                            + ", known "
                            + knownQValues
                            + ", unknown "
                            + unknownQValues);
        }
    }
}
