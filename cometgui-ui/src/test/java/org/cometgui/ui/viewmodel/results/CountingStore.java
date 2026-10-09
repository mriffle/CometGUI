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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.cometgui.results.filtering.FilterCounts;
import org.cometgui.results.filtering.QValueFilter;
import org.cometgui.results.filtering.store.ResultPage;
import org.cometgui.results.filtering.store.ResultQuery;
import org.cometgui.results.filtering.store.ResultStore;
import org.cometgui.results.filtering.store.RowKey;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.results.parser.ResultRow;
import org.cometgui.results.parser.ResultTableHeader;

/**
 * A real store, every question to it recorded: what the table view-model asked for, how many rows
 * it was given at most, and whether it asked at all.
 */
final class CountingStore implements ResultStore {

    private final ResultStore real;

    /** Every page query, in order. */
    private final List<ResultQuery> queries = new ArrayList<>();

    /** Every key looked up by position, in order. */
    private final List<RowKey> positionLookups = new ArrayList<>();

    /** The most rows one answer held. */
    private int mostRowsReturned;

    /** When set, every question fails with it. */
    private IOException failure;

    /** When set, every page is asked for with the largest limit a query allows. */
    private boolean widened;

    CountingStore(ResultStore real) {
        this.real = real;
    }

    List<ResultQuery> queries() {
        return queries;
    }

    List<RowKey> positionLookups() {
        return positionLookups;
    }

    int mostRowsReturned() {
        return mostRowsReturned;
    }

    void fail(IOException next) {
        failure = next;
    }

    void widen() {
        widened = true;
    }

    @Override
    public Path file() {
        return real.file();
    }

    @Override
    public TableKind kind() {
        return real.kind();
    }

    @Override
    public ResultTableHeader header() {
        return real.header();
    }

    @Override
    public long rowCount() {
        return real.rowCount();
    }

    @Override
    public FilterCounts counts(QValueFilter filter) throws IOException {
        throw new AssertionError("the table view-model takes its counts from the page's answer");
    }

    @Override
    public ResultPage query(ResultQuery query) throws IOException {
        queries.add(query);
        if (failure != null) {
            throw failure;
        }
        ResultPage page =
                real.query(widened ? query.withPage(0, ResultQuery.MAX_PAGE_SIZE) : query);
        mostRowsReturned = Math.max(mostRowsReturned, page.rows().size());
        return page;
    }

    @Override
    public Optional<ResultRow> row(RowKey key) throws IOException {
        throw new AssertionError("the table view-model reads rows only by page");
    }

    @Override
    public OptionalLong positionOf(RowKey key, ResultQuery query) throws IOException {
        positionLookups.add(key);
        if (failure != null) {
            throw failure;
        }
        return real.positionOf(key, query);
    }

    @Override
    public void close() throws IOException {
        real.close();
    }
}
