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

import java.util.List;
import java.util.Objects;
import org.cometgui.results.filtering.FilterCounts;
import org.cometgui.results.parser.ResultRow;

/**
 * One page of a {@link ResultQuery}'s answer.
 *
 * @param rows the page's rows, in the query's order: at most its limit
 * @param offset the position of the first of them among all matching rows, from 0
 * @param matching how many rows the whole query matches -- category and text applied, offset and
 *     limit not
 * @param counts the query's filter over the whole table: total, passing, failing and unknown,
 *     whatever the category and text ({@code R-RES-01}, {@code R-RES-02})
 */
public record ResultPage(List<ResultRow> rows, long offset, long matching, FilterCounts counts) {

    /**
     * A page.
     *
     * @throws IllegalArgumentException if a number is negative or the page claims more rows than
     *     match after its offset
     * @throws NullPointerException if a reference is {@code null}
     */
    public ResultPage {
        rows = List.copyOf(rows);
        Objects.requireNonNull(counts, "counts");
        if (offset < 0 || matching < 0 || rows.size() > Math.max(0, matching - offset)) {
            throw new IllegalArgumentException(
                    rows.size()
                            + " rows at offset "
                            + offset
                            + " cannot be a page of "
                            + matching
                            + " matching rows");
        }
    }
}
