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

import org.cometgui.results.parser.ResultRow;

/**
 * A row's stable identity within one table: its line number in the raw file, counting the header as
 * line 1, so the first row is line 2.
 *
 * <p>It does not change when the filter, category, text or sort changes, nor between two stores
 * opened on the same file, so a view holds its selection by it (design decision P10-9).
 *
 * @param line the row's line number, at least 2
 */
public record RowKey(long line) implements Comparable<RowKey> {

    /** The line of a table's first row: line 1 is the header. */
    public static final long FIRST_ROW_LINE = 2;

    /**
     * A key.
     *
     * @throws IllegalArgumentException if {@code line} is below {@link #FIRST_ROW_LINE}
     */
    public RowKey {
        if (line < FIRST_ROW_LINE) {
            throw new IllegalArgumentException(
                    "a row's line is at least "
                            + FIRST_ROW_LINE
                            + " (line 1 is the header): "
                            + line);
        }
    }

    /**
     * A row's key.
     *
     * @param row the row
     * @return the key of its line
     */
    public static RowKey of(ResultRow row) {
        return new RowKey(row.line());
    }

    @Override
    public int compareTo(RowKey other) {
        return Long.compare(line, other.line);
    }
}
