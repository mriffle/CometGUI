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

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import org.cometgui.results.filtering.FilterCounts;
import org.cometgui.results.filtering.QValueFilter;
import org.cometgui.results.parser.ResultRow;
import org.cometgui.results.parser.ResultTableHeader;

/**
 * One Percolator PSM or peptide table, opened once and then asked for one page at a time, so that a
 * view never holds every row ({@code R-RES-03}). {@link ResultStores} opens one.
 *
 * <p>The contract every implementation keeps, and {@code ResultStoreContract} tests:
 *
 * <ul>
 *   <li>Every row of the file is in the store, read by the one {@code ResultTableReader}; a row
 *       with an unknown q-value is kept ({@code R-RES-02}).
 *   <li>Where a row falls is decided by {@link QValueFilter#classify}, the one q-value predicate,
 *       and by nothing else (design decision P10-1).
 *   <li>Counts are over the whole table, whatever the category, text and page.
 *   <li>A query's pages, read in turn, are exactly its matching rows in its order: none twice, none
 *       missed. Sort, missing-value and tie rules are {@link ResultSort}'s; text matching is {@link
 *       TextFilter}'s.
 *   <li>The raw file is never written, moved or locked: it is byte-identical after opening, any
 *       number of queries, and closing.
 *   <li>Once open, the store answers queries from any number of threads at once.
 *   <li>After {@link #close}, every other method throws {@link IllegalStateException}; closing
 *       twice is harmless.
 * </ul>
 */
public interface ResultStore extends Closeable {

    /**
     * The raw table the store was opened on.
     *
     * @return its path
     */
    Path file();

    /**
     * Which table it is.
     *
     * @return the kind the store was opened as
     */
    TableKind kind();

    /**
     * The table's header.
     *
     * @return the header, as the reader read it
     */
    ResultTableHeader header();

    /**
     * How many rows the table holds, header excluded.
     *
     * @return the row count
     */
    long rowCount();

    /**
     * Where every row falls under a filter.
     *
     * @param filter the PSM filter for a PSM table, the peptide filter for a peptide table
     * @return total, passing, failing and unknown over the whole table
     * @throws IllegalArgumentException if the filter is the other table kind's ({@link
     *     TableKind#check})
     * @throws IOException if the store cannot read what it holds
     */
    FilterCounts counts(QValueFilter filter) throws IOException;

    /**
     * One page of rows.
     *
     * @param query the query
     * @return the page, the number of rows the query matches, and the filter's counts
     * @throws IllegalArgumentException if the query's filter is the other table kind's
     * @throws IOException if the store cannot read what it holds
     */
    ResultPage query(ResultQuery query) throws IOException;

    /**
     * One row by its key, whatever the filter.
     *
     * @param key the key
     * @return the row, or empty if the table has no row on that line
     * @throws IOException if the store cannot read what it holds
     */
    Optional<ResultRow> row(RowKey key) throws IOException;
}
