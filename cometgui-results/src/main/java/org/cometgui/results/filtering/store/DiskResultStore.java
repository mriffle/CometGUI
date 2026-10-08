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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.cometgui.domain.ports.HashService;
import org.cometgui.results.filtering.FilterCounts;
import org.cometgui.results.filtering.QValueFilter;
import org.cometgui.results.filtering.Visibility;
import org.cometgui.results.parser.QValue;
import org.cometgui.results.parser.ResultRow;
import org.cometgui.results.parser.ResultTableHeader;
import org.cometgui.results.parser.ResultTableReader;
import org.cometgui.results.parser.SpectrumReference;

/**
 * A {@link ResultStore} that keeps no row in heap: for a table above the row threshold ({@code
 * R-RES-03}, design decision P10-4).
 *
 * <p>Opening hashes the raw table through the injected {@link HashService} and checks the {@link
 * DiskIndex} in the index directory against it; an index that is absent or fails any check is
 * rebuilt by one pass of the one reader, and {@link #indexReport} says which and why. The index is
 * memory-mapped, off the heap, and so is each {@link SortFile}: a column's ascending and descending
 * files are built together, on the first use of either, by one external merge sort, and kept beside
 * the index for the next opening. Nothing holds an object per row for the whole table: a query
 * walks the mapped order, classifies each row by its indexed q-value status and value through
 * {@link QValueFilter#classify(QValue.Status, double)} -- the one predicate -- and reads back only
 * the page's rows, by offset, from the raw table, which it opened read-only and reads by position.
 * A text filter is a bit per row, made by one streaming pass of the one reader through {@link
 * TextFilter} and kept for the last few texts.
 *
 * <p>Every row read back is checked against its index record (q-value status and value, score and
 * PEP); a mismatch, or a raw table whose size or modification time has changed since opening, is an
 * {@link IndexProblem#RAW_CHANGED_SINCE_OPENED} refusal rather than a wrong row.
 *
 * <p>Queries are safe from any number of threads. {@link #close} waits for queries running, then
 * unmaps every file and closes the raw table.
 *
 * <p>Files, in the index directory, named by table kind ({@code target-psms} and so on): {@code
 * <kind>.index}, {@code <kind>.sort-<column>-<direction>}, and, while being written, temporary
 * files beside them. One index directory can hold all four tables of a run; it must not be the raw
 * table's own directory.
 */
final class DiskResultStore implements ResultStore {

    /** How many text filters' matches are kept: each is one bit per row. */
    static final int TEXT_CACHE_SIZE = 8;

    /** How many filters' counts are kept. */
    static final int COUNTS_CACHE_SIZE = 64;

    private final Path file;
    private final TableKind kind;
    private final ResultTableHeader header;
    private final RawIdentity raw;
    private final Path indexDirectory;
    private final DiskIndex index;
    private final IndexReport indexReport;
    private final FileChannel channel;
    private final long rows;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<ResultSort, SortFile> sorts = new ConcurrentHashMap<>();
    private final Map<ResultSort, IndexReport> sortReports = new ConcurrentHashMap<>();
    private final Map<ResultSort.Column, Object> sortLocks = new ConcurrentHashMap<>();
    private final Map<String, long[]> textMatches = lru(TEXT_CACHE_SIZE);
    private final Map<QValueFilter, FilterCounts> counts = lru(COUNTS_CACHE_SIZE);
    private boolean closed;

    /**
     * What opening found of an index or sort file.
     *
     * @param built whether the file was built rather than reused
     * @param problem why it was built: {@link IndexProblem#ABSENT} when there was none, empty when
     *     it was reused
     */
    record IndexReport(boolean built, Optional<IndexProblem> problem) {}

    private DiskResultStore(
            Path file,
            TableKind kind,
            ResultTableHeader header,
            RawIdentity raw,
            Path indexDirectory,
            DiskIndex index,
            IndexReport indexReport,
            FileChannel channel) {
        this.file = file;
        this.kind = kind;
        this.header = header;
        this.raw = raw;
        this.indexDirectory = indexDirectory;
        this.index = index;
        this.indexReport = indexReport;
        this.channel = channel;
        this.rows = index.rows();
    }

    /**
     * Opens a table, building or rebuilding its index as needed.
     *
     * @param file the raw table, only ever read
     * @param kind which table it is
     * @param indexDirectory where the index lives; made if absent; never the raw table's directory
     * @param hasher the one hasher
     * @return the store
     * @throws ResultIndexException if a freshly built index fails its own checks, or the raw table
     *     changes while it is indexed, or holds too many rows
     * @throws org.cometgui.results.parser.PercolatorOutputException if the reader refuses the table
     * @throws IOException if a file cannot be read or written
     * @throws IllegalArgumentException if the index directory is the raw table's directory
     */
    static DiskResultStore open(Path file, TableKind kind, Path indexDirectory, HashService hasher)
            throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(indexDirectory, "indexDirectory");
        Objects.requireNonNull(hasher, "hasher");
        ResultTableHeader header;
        try (ResultTableReader reader = ResultTableReader.open(file)) {
            header = reader.header();
        }
        Path rawDirectory = file.toAbsolutePath().normalize().getParent();
        Path directory = indexDirectory.toAbsolutePath().normalize();
        if (directory.equals(rawDirectory)) {
            throw new IllegalArgumentException(
                    "the index directory must not be the raw table's own directory: " + directory);
        }
        RawIdentity raw = RawIdentity.of(file, hasher);
        Files.createDirectories(directory);
        Path indexFile = directory.resolve(name(kind) + ".index");
        DiskIndex.Checked found = DiskIndex.open(indexFile, kind, raw);
        IndexReport report;
        DiskIndex index;
        if (found.index().isPresent()) {
            index = found.index().get();
            report = new IndexReport(false, Optional.empty());
        } else {
            report = new IndexReport(true, found.problem());
            deleteSortFiles(directory, kind);
            DiskIndex.build(file, kind, raw, indexFile);
            DiskIndex.Checked built = DiskIndex.open(indexFile, kind, raw);
            if (built.index().isEmpty()) {
                IndexProblem problem = built.problem().orElseThrow();
                throw new ResultIndexException(problem, file, "in the index just built");
            }
            index = built.index().get();
        }
        try {
            FileChannel channel = FileChannel.open(file, StandardOpenOption.READ);
            return new DiskResultStore(file, kind, header, raw, directory, index, report, channel);
        } catch (IOException | RuntimeException failed) {
            index.close();
            throw failed;
        }
    }

    /**
     * What opening found of the index: reused, or built and why.
     *
     * @return the report
     */
    IndexReport indexReport() {
        return indexReport;
    }

    /**
     * What the first use of a sort found of its sort file, in this store.
     *
     * @param sort the sort
     * @return the report, or empty if the sort has not been used or is file order, which needs none
     */
    Optional<IndexReport> sortReport(ResultSort sort) {
        return Optional.ofNullable(sortReports.get(sort));
    }

    @Override
    public Path file() {
        lock.readLock().lock();
        try {
            requireOpen();
            return file;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public TableKind kind() {
        lock.readLock().lock();
        try {
            requireOpen();
            return kind;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public ResultTableHeader header() {
        lock.readLock().lock();
        try {
            requireOpen();
            return header;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public long rowCount() {
        lock.readLock().lock();
        try {
            requireOpen();
            return rows;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public FilterCounts counts(QValueFilter filter) {
        lock.readLock().lock();
        try {
            requireOpen();
            return countsUnder(kind.check(filter));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public ResultPage query(ResultQuery query) throws IOException {
        lock.readLock().lock();
        try {
            requireOpen();
            QValueFilter filter = kind.check(query.filter());
            raw.requireUnchanged(file, IndexProblem.RAW_CHANGED_SINCE_OPENED);
            FilterCounts whole = countsUnder(filter);
            Order order = order(query.sort());
            long[] text = query.text().isEmpty() ? null : textMatches(query.text());
            long known = text == null ? inCategory(whole, query.category()) : -1;
            List<Long> page = new ArrayList<>(Math.min(query.limit(), (int) Math.min(rows, 1024)));
            long end = query.offset() + query.limit();
            long matching = 0;
            for (long position = 0; position < rows; position++) {
                int row = order.rowAt(position);
                if (matches(row, filter, query.category(), text)) {
                    if (matching >= query.offset() && matching < end) {
                        page.add((long) row);
                    }
                    matching++;
                    if (known >= 0 && matching >= end) {
                        matching = known;
                        break;
                    }
                }
            }
            List<ResultRow> found = new ArrayList<>(page.size());
            for (long row : page) {
                found.add(readRow(row));
            }
            return new ResultPage(found, query.offset(), matching, whole);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<ResultRow> row(RowKey key) throws IOException {
        Objects.requireNonNull(key, "key");
        lock.readLock().lock();
        try {
            requireOpen();
            raw.requireUnchanged(file, IndexProblem.RAW_CHANGED_SINCE_OPENED);
            long row = key.line() - RowKey.FIRST_ROW_LINE;
            if (row >= rows) {
                return Optional.empty();
            }
            return Optional.of(readRow(row));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public OptionalLong positionOf(RowKey key, ResultQuery query) throws IOException {
        Objects.requireNonNull(key, "key");
        lock.readLock().lock();
        try {
            requireOpen();
            QValueFilter filter = kind.check(query.filter());
            raw.requireUnchanged(file, IndexProblem.RAW_CHANGED_SINCE_OPENED);
            long target = key.line() - RowKey.FIRST_ROW_LINE;
            if (target >= rows) {
                return OptionalLong.empty();
            }
            long[] text = query.text().isEmpty() ? null : textMatches(query.text());
            if (!matches((int) target, filter, query.category(), text)) {
                return OptionalLong.empty();
            }
            Order order = order(query.sort());
            long matching = 0;
            for (long position = 0; position < rows; position++) {
                int row = order.rowAt(position);
                if (row == target) {
                    return OptionalLong.of(matching);
                }
                if (matches(row, filter, query.category(), text)) {
                    matching++;
                }
            }
            throw new ResultIndexException(
                    IndexProblem.NOT_A_PERMUTATION, file, "row " + target + " is in no position");
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void close() {
        lock.writeLock().lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            try {
                channel.close();
            } catch (IOException ignored) {
                // Read-only: nothing was written that closing could lose.
            }
            for (SortFile sort : sorts.values()) {
                sort.close();
            }
            sorts.clear();
            index.close();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /* ------------------------------------------------------------------ internals */

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("the result store for " + file + " is closed");
        }
    }

    private boolean matches(int row, QValueFilter filter, Category category, long[] text) {
        if (!category.includes(classify(filter, row))) {
            return false;
        }
        return text == null || (text[row >>> 6] & (1L << row)) != 0;
    }

    private Visibility classify(QValueFilter filter, long row) {
        return filter.classify(index.status(row), index.qValue(row));
    }

    private static long inCategory(FilterCounts counts, Category category) {
        return switch (category) {
            case PASSING -> counts.passing();
            case FAILING -> counts.failing();
            case UNKNOWN_Q_VALUE -> counts.unknownQValue();
            case ALL -> counts.total();
        };
    }

    private FilterCounts countsUnder(QValueFilter filter) {
        synchronized (counts) {
            FilterCounts cached = counts.get(filter);
            if (cached != null) {
                return cached;
            }
        }
        long passing = 0;
        long failing = 0;
        long unknown = 0;
        for (long row = 0; row < rows; row++) {
            switch (classify(filter, row)) {
                case PASSES -> passing++;
                case FAILS -> failing++;
                case UNKNOWN_Q_VALUE -> unknown++;
            }
        }
        FilterCounts computed =
                new FilterCounts(passing + failing + unknown, passing, failing, unknown);
        synchronized (counts) {
            counts.put(filter, computed);
        }
        return computed;
    }

    /** Reads one row back from the raw table by its offset, and checks it against the index. */
    private ResultRow readRow(long row) throws IOException {
        long offset = index.offset(row);
        int length = index.length(row);
        ByteBuffer bytes = ByteBuffer.allocate(length);
        long at = offset;
        while (bytes.hasRemaining()) {
            int read = channel.read(bytes, at);
            if (read < 0) {
                throw new ResultIndexException(
                        IndexProblem.RAW_CHANGED_SINCE_OPENED,
                        file,
                        "row " + (row + RowKey.FIRST_ROW_LINE) + " lies past the end of the file");
            }
            at += read;
        }
        long line = row + RowKey.FIRST_ROW_LINE;
        ResultRow parsed = ResultTableReader.row(file, header, line, bytes.array());
        QValue q = parsed.qValue();
        boolean same =
                q.status() == index.status(row)
                        && (!q.isKnown() || Double.compare(q.value(), index.qValue(row)) == 0)
                        && Double.compare(parsed.score(), index.score(row)) == 0
                        && Double.compare(parsed.posteriorErrorProbability(), index.pep(row)) == 0;
        if (!same) {
            throw new ResultIndexException(
                    IndexProblem.RAW_CHANGED_SINCE_OPENED,
                    file,
                    "line " + line + " no longer holds the row its index recorded");
        }
        return parsed;
    }

    /** The positions of a sort: file order needs no file. */
    @FunctionalInterface
    private interface Order {
        int rowAt(long position);
    }

    private Order order(ResultSort sort) throws IOException {
        if (sort.column() == ResultSort.Column.FILE_ORDER) {
            return sort.direction() == ResultSort.Direction.ASCENDING
                    ? position -> (int) position
                    : position -> (int) (rows - 1 - position);
        }
        SortFile cached = sorts.get(sort);
        if (cached != null) {
            return cached::rowAt;
        }
        synchronized (sortLocks.computeIfAbsent(sort.column(), unused -> new Object())) {
            SortFile again = sorts.get(sort);
            if (again != null) {
                return again::rowAt;
            }
            SortFile.Checked found = SortFile.open(sortFile(sort), kind, sort, rows, raw);
            SortFile opened;
            if (found.sortFile().isPresent()) {
                opened = found.sortFile().get();
                sortReports.put(sort, new IndexReport(false, Optional.empty()));
            } else {
                sortReports.put(sort, new IndexReport(true, found.problem()));
                ResultSort ascending = ResultSort.ascending(sort.column());
                ResultSort descending = ResultSort.descending(sort.column());
                SortFile.build(
                        sortFile(ascending),
                        sortFile(descending),
                        kind,
                        sort.column(),
                        keyType(sort.column()),
                        rows,
                        raw,
                        keys(sort.column()));
                SortFile.Checked built = SortFile.open(sortFile(sort), kind, sort, rows, raw);
                if (built.sortFile().isEmpty()) {
                    throw new ResultIndexException(
                            built.problem().orElseThrow(), file, "in the sort file just built");
                }
                opened = built.sortFile().get();
            }
            sorts.put(sort, opened);
            return opened::rowAt;
        }
    }

    private Path sortFile(ResultSort sort) {
        return indexDirectory.resolve(name(kind) + ".sort-" + name(sort));
    }

    private static SortFile.KeyType keyType(ResultSort.Column column) {
        return switch (column) {
            case SCAN, CHARGE -> SortFile.KeyType.WHOLE;
            case SCORE, Q_VALUE, PEP -> SortFile.KeyType.REAL;
            case FILE_ORDER, PSM_ID, SOURCE_FILE, PEPTIDE, PROTEINS -> SortFile.KeyType.TEXT;
        };
    }

    /** Every row's key for a column, in file order: numbers from the index, text from the table. */
    private SortFile.KeySource keys(ResultSort.Column column) {
        return switch (column) {
            case SCAN -> sink -> forEachIndexed(row -> wholeKey(row, index.scan(row)), sink);
            case CHARGE -> sink -> forEachIndexed(row -> wholeKey(row, index.charge(row)), sink);
            case SCORE -> sink -> forEachIndexed(row -> realKey(row, index.score(row)), sink);
            case Q_VALUE -> sink -> forEachIndexed(row -> realKey(row, index.qValue(row)), sink);
            case PEP -> sink -> forEachIndexed(row -> realKey(row, index.pep(row)), sink);
            case PSM_ID ->
                    sink -> forEachRow((row, value) -> sink.accept(textKey(row, value.psmId())));
            case PEPTIDE ->
                    sink -> forEachRow((row, value) -> sink.accept(textKey(row, value.peptide())));
            case PROTEINS ->
                    sink -> forEachRow((row, value) -> sink.accept(proteinsKey(row, value)));
            case SOURCE_FILE ->
                    sink -> forEachRow((row, value) -> sink.accept(sourceKey(row, value)));
            case FILE_ORDER -> throw new IllegalArgumentException("file order needs no sort file");
        };
    }

    private static SortFile.Key wholeKey(int row, long value) {
        return value < 0 ? SortFile.Key.missing(row) : SortFile.Key.whole(row, value);
    }

    private static SortFile.Key realKey(int row, double value) {
        return Double.isNaN(value) ? SortFile.Key.missing(row) : SortFile.Key.real(row, value);
    }

    private static SortFile.Key proteinsKey(int row, ResultRow value) {
        return SortFile.Key.text(row, value.proteinIds().toArray(String[]::new));
    }

    private static SortFile.Key sourceKey(int row, ResultRow value) {
        Optional<SpectrumReference> reference = value.spectrumReference();
        return reference.isPresent()
                ? textKey(row, reference.get().base())
                : SortFile.Key.missing(row);
    }

    private static SortFile.Key textKey(int row, String value) {
        return SortFile.Key.text(row, value);
    }

    @FunctionalInterface
    private interface IndexedKey {
        SortFile.Key of(int row);
    }

    private void forEachIndexed(IndexedKey key, SortFile.KeySink sink) throws IOException {
        for (int row = 0; row < rows; row++) {
            sink.accept(key.of(row));
        }
    }

    @FunctionalInterface
    private interface RowVisitor {
        void visit(int row, ResultRow value) throws IOException;
    }

    /**
     * Streams every row through the one reader, checking each against its index record's offset, so
     * that a raw table that changed since opening is refused rather than mis-sorted.
     */
    private void forEachRow(RowVisitor visitor) throws IOException {
        try (ResultTableReader reader = ResultTableReader.open(file)) {
            int row = 0;
            for (ResultRow value = reader.next(); value != null; value = reader.next()) {
                if (row >= rows
                        || reader.lastRowOffset() != index.offset(row)
                        || reader.lastRowLength() != index.length(row)) {
                    throw new ResultIndexException(
                            IndexProblem.RAW_CHANGED_SINCE_OPENED,
                            file,
                            "line " + value.line() + " is not where the index recorded it");
                }
                visitor.visit(row, value);
                row++;
            }
            if (row != rows) {
                throw new ResultIndexException(
                        IndexProblem.RAW_CHANGED_SINCE_OPENED,
                        file,
                        row + " rows read, " + rows + " indexed");
            }
        }
    }

    /** One bit per row, set where the row matches the text: by one streaming pass. */
    private long[] textMatches(String text) throws IOException {
        synchronized (textMatches) {
            long[] cached = textMatches.get(text);
            if (cached != null) {
                return cached;
            }
            long[] bits = new long[words(rows)];
            forEachRow(
                    (row, value) -> {
                        if (TextFilter.matches(text, value)) {
                            bits[row >>> 6] |= 1L << row;
                        }
                    });
            textMatches.put(text, bits);
            return bits;
        }
    }

    /**
     * How many {@code long}s hold one bit per row.
     *
     * @param rows the rows
     * @return {@code ceil(rows / 64)}
     */
    static int words(long rows) {
        return Math.toIntExact((rows + 63) >>> 6);
    }

    private static <K, V> Map<K, V> lru(int size) {
        return new LinkedHashMap<>(16, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > size;
            }
        };
    }

    private static String name(TableKind kind) {
        return kind.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String name(ResultSort sort) {
        return (sort.column().name() + "-" + sort.direction().name())
                .toLowerCase(Locale.ROOT)
                .replace('_', '-');
    }

    /** A rebuilt index makes every sort file of the table stale: they are removed with it. */
    private static void deleteSortFiles(Path directory, TableKind kind) throws IOException {
        String prefix = name(kind) + ".sort-";
        try (var files = Files.list(directory)) {
            for (Path candidate : files.toList()) {
                if (String.valueOf(candidate.getFileName()).startsWith(prefix)) {
                    Files.deleteIfExists(candidate);
                }
            }
        }
    }
}
