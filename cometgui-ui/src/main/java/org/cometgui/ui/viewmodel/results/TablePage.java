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

import java.util.List;
import java.util.Objects;

/**
 * The one page of rows a results table holds ({@code R-RES-03}): at most {@link
 * ResultTableViewModel#PAGE_SIZE} rows, never every row, and where they stand among the rows the
 * query matches.
 *
 * @param rows the page's rows, in the query's order
 * @param offset the position of the first of them among the matching rows, from 0
 * @param matching how many rows the filter, category and text match
 * @param pageNumber the page's number, from 1; 0 when nothing matches
 * @param pageCount how many pages the matching rows fill; 0 when nothing matches
 * @param text where the page stands, in words
 */
public record TablePage(
        List<ResultRowView> rows,
        long offset,
        long matching,
        long pageNumber,
        long pageCount,
        String text) {

    /** The page of a table that is not open. */
    public static final TablePage NONE = new TablePage(List.of(), 0, 0, 0, 0, "No table is open.");

    /**
     * A page.
     *
     * @throws IllegalArgumentException if it holds more rows than a page may
     * @throws NullPointerException if a reference is {@code null}
     */
    public TablePage {
        rows = List.copyOf(rows);
        Objects.requireNonNull(text, "text");
        if (rows.size() > ResultTableViewModel.PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "a page holds at most "
                            + ResultTableViewModel.PAGE_SIZE
                            + " rows, not "
                            + rows.size());
        }
    }

    /**
     * A page of a query's answer.
     *
     * @param rows the rows
     * @param offset where the first stands
     * @param matching how many rows match
     * @return the page, with its number and its position in words
     */
    static TablePage of(List<ResultRowView> rows, long offset, long matching) {
        int size = ResultTableViewModel.PAGE_SIZE;
        if (matching == 0) {
            return new TablePage(
                    rows,
                    offset,
                    0,
                    0,
                    0,
                    "No row matches the q-value filter, the category and the text filter.");
        }
        long count = (matching + size - 1) / size;
        long number = offset / size + 1;
        String text =
                "Rows "
                        + (offset + 1)
                        + " to "
                        + (offset + rows.size())
                        + " of "
                        + matching
                        + " (page "
                        + number
                        + " of "
                        + count
                        + ")";
        return new TablePage(rows, offset, matching, number, count, text);
    }
}
