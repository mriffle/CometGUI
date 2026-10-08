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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.cometgui.results.filtering.FilterCounts;
import org.cometgui.results.filtering.QValueFilter;
import org.cometgui.results.parser.PercolatorOutputException;
import org.cometgui.results.parser.ResultRow;
import org.cometgui.results.parser.ResultTable;
import org.cometgui.results.parser.ResultTableHeader;
import org.cometgui.results.parser.ResultTableReader;
import org.cometgui.results.parser.SpectrumReference;

/**
 * A {@link ResultStore} holding every row in memory: for a table at or below the row threshold.
 *
 * <p>Rows are read once, by {@link ResultTableReader#readAll}. The values a sort needs are held in
 * arrays beside them, and each sort's order is computed on first use and kept, so a query is one
 * pass over the rows in that order. Everything is fixed once the constructor returns; the order
 * cache is a concurrent map, so queries are safe from any number of threads.
 */
final class InMemoryResultStore implements ResultStore {

    private final Path file;
    private final TableKind kind;
    private final ResultTableHeader header;
    private final List<ResultRow> rows;
    private final String[] base;
    private final long[] scan;
    private final int[] charge;
    private final double[] score;
    private final double[] qValue;
    private final double[] pep;
    private final Map<ResultSort, int[]> orders = new ConcurrentHashMap<>();
    private volatile boolean closed;

    private InMemoryResultStore(Path file, TableKind kind, ResultTable table) {
        this.file = file;
        this.kind = kind;
        this.header = table.header();
        this.rows = table.rows();
        int size = rows.size();
        base = new String[size];
        scan = new long[size];
        charge = new int[size];
        score = new double[size];
        qValue = new double[size];
        pep = new double[size];
        for (int index = 0; index < size; index++) {
            ResultRow row = rows.get(index);
            Optional<SpectrumReference> reference = row.spectrumReference();
            base[index] = reference.map(SpectrumReference::base).orElse(null);
            scan[index] = reference.map(SpectrumReference::scan).orElse(-1L);
            charge[index] = reference.map(SpectrumReference::charge).orElse(-1);
            score[index] = row.score();
            qValue[index] = row.qValue().isKnown() ? row.qValue().value() : Double.NaN;
            pep[index] = row.posteriorErrorProbability();
        }
    }

    /**
     * Reads a whole table.
     *
     * @param file the raw table
     * @param kind which table it is
     * @return the store
     * @throws PercolatorOutputException if the reader refuses the file
     */
    static InMemoryResultStore open(Path file, TableKind kind) throws PercolatorOutputException {
        Objects.requireNonNull(kind, "kind");
        return new InMemoryResultStore(file, kind, ResultTableReader.readAll(file));
    }

    @Override
    public Path file() {
        open();
        return file;
    }

    @Override
    public TableKind kind() {
        open();
        return kind;
    }

    @Override
    public ResultTableHeader header() {
        open();
        return header;
    }

    @Override
    public long rowCount() {
        open();
        return rows.size();
    }

    @Override
    public FilterCounts counts(QValueFilter filter) {
        open();
        return kind.check(filter).count(rows);
    }

    @Override
    public ResultPage query(ResultQuery query) {
        open();
        QValueFilter filter = kind.check(query.filter());
        int[] order = orders.computeIfAbsent(query.sort(), this::order);
        List<ResultRow> page = new ArrayList<>(Math.min(query.limit(), rows.size()));
        long matching = 0;
        for (int index : order) {
            ResultRow row = rows.get(index);
            if (query.category().includes(filter.classify(row))
                    && TextFilter.matches(query.text(), row)) {
                if (matching >= query.offset() && page.size() < query.limit()) {
                    page.add(row);
                }
                matching++;
            }
        }
        return new ResultPage(page, query.offset(), matching, filter.count(rows));
    }

    @Override
    public Optional<ResultRow> row(RowKey key) {
        open();
        Objects.requireNonNull(key, "key");
        int low = 0;
        int high = rows.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            long line = rows.get(middle).line();
            if (line < key.line()) {
                low = middle + 1;
            } else if (line > key.line()) {
                high = middle - 1;
            } else {
                return Optional.of(rows.get(middle));
            }
        }
        return Optional.empty();
    }

    @Override
    public void close() {
        closed = true;
    }

    private void open() {
        if (closed) {
            throw new IllegalStateException("the result store for " + file + " is closed");
        }
    }

    /** Every row's index, in a sort's order; ties, and rows with no value, in file order. */
    private int[] order(ResultSort sort) {
        Integer[] indices = new Integer[rows.size()];
        Arrays.setAll(indices, index -> index);
        boolean descending = sort.direction() == ResultSort.Direction.DESCENDING;
        Arrays.sort(
                indices,
                (a, b) -> {
                    int byValue = compare(sort.column(), a, b, descending);
                    return byValue != 0 ? byValue : Integer.compare(a, b);
                });
        return Arrays.stream(indices).mapToInt(Integer::intValue).toArray();
    }

    private int compare(ResultSort.Column column, int a, int b, boolean descending) {
        return switch (column) {
            case FILE_ORDER -> directed(Integer.compare(a, b), descending);
            case PSM_ID -> directed(rows.get(a).psmId().compareTo(rows.get(b).psmId()), descending);
            case SOURCE_FILE -> texts(base[a], base[b], descending);
            case SCAN -> numbers(scan[a] < 0, scan[b] < 0, scan[a], scan[b], descending);
            case CHARGE -> numbers(charge[a] < 0, charge[b] < 0, charge[a], charge[b], descending);
            case PEPTIDE ->
                    directed(rows.get(a).peptide().compareTo(rows.get(b).peptide()), descending);
            case PROTEINS ->
                    directed(
                            proteins(rows.get(a).proteinIds(), rows.get(b).proteinIds()),
                            descending);
            case SCORE -> doubles(score[a], score[b], descending);
            case Q_VALUE -> doubles(qValue[a], qValue[b], descending);
            case PEP -> doubles(pep[a], pep[b], descending);
        };
    }

    private static int directed(int comparison, boolean descending) {
        return descending ? -comparison : comparison;
    }

    /** Missing last in either direction; otherwise by value, in the direction asked. */
    private static int numbers(
            boolean aMissing, boolean bMissing, double a, double b, boolean descending) {
        if (aMissing || bMissing) {
            return Boolean.compare(aMissing, bMissing);
        }
        return directed(a < b ? -1 : a > b ? 1 : 0, descending);
    }

    private static int doubles(double a, double b, boolean descending) {
        return numbers(Double.isNaN(a), Double.isNaN(b), a, b, descending);
    }

    private static int texts(String a, String b, boolean descending) {
        if (a == null || b == null) {
            return Boolean.compare(a == null, b == null);
        }
        return directed(a.compareTo(b), descending);
    }

    private static int proteins(List<String> a, List<String> b) {
        int shared = Math.min(a.size(), b.size());
        for (int index = 0; index < shared; index++) {
            int comparison = a.get(index).compareTo(b.get(index));
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(a.size(), b.size());
    }
}
