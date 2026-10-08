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
import org.cometgui.results.filtering.QValueFilter;

/**
 * One page's worth of question to a {@link ResultStore}.
 *
 * <p>The rows a query <em>matches</em> are those whose {@link
 * org.cometgui.results.filtering.Visibility Visibility} under {@code filter} is in {@code category}
 * and which match {@code text} ({@link TextFilter}). Ordered by {@code sort}, the page is the
 * matching rows from position {@code offset} (zero-based), at most {@code limit} of them.
 *
 * <p>{@code limit} is bounded by {@link #MAX_PAGE_SIZE}, so no query can return every row of a
 * large table ({@code R-RES-03}); a limit of 0 asks only for the counts.
 *
 * @param filter the q-value filter: the PSM filter for a PSM table, the peptide filter for a
 *     peptide table ({@link TableKind#check})
 * @param category which rows, by where they fall under the filter
 * @param text the text filter, its surrounding white space stripped; empty for none
 * @param sort the order
 * @param offset how many matching rows to skip, from 0
 * @param limit the most rows to return, from 0 to {@link #MAX_PAGE_SIZE}
 */
public record ResultQuery(
        QValueFilter filter,
        Category category,
        String text,
        ResultSort sort,
        long offset,
        int limit) {

    /** The most rows one page may hold. */
    public static final int MAX_PAGE_SIZE = 5000;

    /** The page size of {@link #firstPage}. */
    public static final int DEFAULT_PAGE_SIZE = 200;

    /**
     * A query.
     *
     * @throws IllegalArgumentException if {@code offset} is negative or {@code limit} is negative
     *     or above {@link #MAX_PAGE_SIZE}
     * @throws NullPointerException if any reference is {@code null}
     */
    public ResultQuery {
        Objects.requireNonNull(filter, "filter");
        Objects.requireNonNull(category, "category");
        text = Objects.requireNonNull(text, "text").strip();
        Objects.requireNonNull(sort, "sort");
        if (offset < 0) {
            throw new IllegalArgumentException("a page's offset is at least 0, but was " + offset);
        }
        if (limit < 0 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "a page holds from 0 to "
                            + MAX_PAGE_SIZE
                            + " rows, but "
                            + limit
                            + " were asked for");
        }
    }

    /**
     * The default view of a table under a filter: passing rows, no text filter, file order, the
     * first {@link #DEFAULT_PAGE_SIZE} rows.
     *
     * @param filter the q-value filter
     * @return the query
     */
    public static ResultQuery firstPage(QValueFilter filter) {
        return new ResultQuery(
                filter, Category.PASSING, "", ResultSort.FILE_ORDER, 0, DEFAULT_PAGE_SIZE);
    }

    /**
     * The same query under another filter.
     *
     * @param newFilter the filter
     * @return the query
     */
    public ResultQuery withFilter(QValueFilter newFilter) {
        return new ResultQuery(newFilter, category, text, sort, offset, limit);
    }

    /**
     * The same query for another category.
     *
     * @param newCategory the category
     * @return the query
     */
    public ResultQuery withCategory(Category newCategory) {
        return new ResultQuery(filter, newCategory, text, sort, offset, limit);
    }

    /**
     * The same query with another text filter.
     *
     * @param newText the text; empty for none
     * @return the query
     */
    public ResultQuery withText(String newText) {
        return new ResultQuery(filter, category, newText, sort, offset, limit);
    }

    /**
     * The same query in another order.
     *
     * @param newSort the sort
     * @return the query
     */
    public ResultQuery withSort(ResultSort newSort) {
        return new ResultQuery(filter, category, text, newSort, offset, limit);
    }

    /**
     * The same query for another page.
     *
     * @param newOffset how many matching rows to skip
     * @param newLimit the most rows to return
     * @return the query
     */
    public ResultQuery withPage(long newOffset, int newLimit) {
        return new ResultQuery(filter, category, text, sort, newOffset, newLimit);
    }
}
