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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;

/**
 * One sort's order over a table: the row index at each position, one big-endian {@code int} per
 * row, memory-mapped read-only ({@code R-RES-03}). A column's two files, ascending and descending,
 * are built together on the first use of either, and kept in the index directory beside the index.
 *
 * <p>The file:
 *
 * <pre>
 * header, 72 bytes
 *    0  8  magic "CGRESPRM"
 *    8  4  format version (1)
 *   12  4  table kind (TableKind ordinal)
 *   16  4  column (ResultSort.Column ordinal)
 *   20  4  direction (ResultSort.Direction ordinal)
 *   24  8  rows
 *   32 32  raw table SHA-256
 *   64  4  CRC-32C of the body
 *   68  4  CRC-32C of header bytes 0..67
 * body: rows x 4 bytes, the row index (line - 2) at each position
 * </pre>
 *
 * <p>It is built by an external merge sort, so that no key is held for every row at once: keys
 * arrive in file order, are sorted in chunks of bounded size, each chunk is spilled to a run file
 * in the index directory, and the runs are merged into the ascending file; the descending file is
 * derived from it. A table small enough for one chunk is sorted without spilling. The order is
 * {@link ResultSort}'s: a row with no value last in either direction, values in the direction
 * asked, ties by file order ascending in either direction. A sort file is used only when every
 * check passes, the last being that it holds every row exactly once.
 */
final class SortFile implements Closeable {

    /** The header's size. */
    static final int HEADER_BYTES = 72;

    /** The format version. */
    static final int VERSION = 1;

    /**
     * The most key bytes, estimated, one chunk of the external sort holds in heap before it is
     * spilled: 8 MiB, so that sorting runs well within the heap budget of {@code ResultStores}.
     */
    static final long CHUNK_BYTES = 8L << 20;

    static final int AT_VERSION = 8;
    static final int AT_KIND = 12;
    static final int AT_COLUMN = 16;
    static final int AT_DIRECTION = 20;
    static final int AT_ROWS = 24;
    static final int AT_RAW_SHA256 = 32;
    static final int AT_BODY_CRC = 64;

    private static final byte[] MAGIC = "CGRESPRM".getBytes(StandardCharsets.US_ASCII);
    private static final int RUN_BUFFER_BYTES = 1 << 16;

    private final Arena arena;
    private final MemorySegment segment;

    private SortFile(Arena arena, MemorySegment segment) {
        this.arena = arena;
        this.segment = segment;
    }

    /** What a sort key is. */
    enum KeyType {
        /** A whole number: scan, charge. */
        WHOLE,
        /** A decimal number: score, q-value, PEP. */
        REAL,
        /** Text, or a list of texts compared in order: PSMId, source file, peptide, proteins. */
        TEXT
    }

    /**
     * One row's sort key.
     *
     * @param row the row index, from 0
     * @param missing whether the row has no value, which sorts last in either direction
     * @param whole the value for {@link KeyType#WHOLE}
     * @param real the value for {@link KeyType#REAL}
     * @param texts the value for {@link KeyType#TEXT}: one text, or proteins in order
     */
    record Key(int row, boolean missing, long whole, double real, String[] texts) {

        static Key missing(int row) {
            return new Key(row, true, 0, 0, null);
        }

        static Key whole(int row, long value) {
            return new Key(row, false, value, 0, null);
        }

        static Key real(int row, double value) {
            return new Key(row, false, 0, value, null);
        }

        static Key text(int row, String... texts) {
            return new Key(row, false, 0, 0, texts);
        }

        /**
         * The heap this key is reckoned to take, for sizing a chunk: 64 bytes for the key, and for
         * text 16 for the array, 8 a reference and 48 plus 2 a character for each string.
         *
         * @return the estimate
         */
        long estimatedBytes() {
            long bytes = 64;
            if (texts != null) {
                bytes += 16 + 8L * texts.length;
                for (String text : texts) {
                    bytes += 48 + 2L * text.length();
                }
            }
            return bytes;
        }
    }

    /** Hands every row's key, in file order, to a sink. */
    @FunctionalInterface
    interface KeySource {
        /**
         * Feeds the keys.
         *
         * @param sink where they go
         * @throws IOException if they cannot be read
         */
        void feed(KeySink sink) throws IOException;
    }

    /** Receives keys. */
    @FunctionalInterface
    interface KeySink {
        /**
         * Takes one key.
         *
         * @param key the key
         * @throws IOException if it cannot be kept
         */
        void accept(Key key) throws IOException;
    }

    /**
     * The ascending order of {@link ResultSort}: missing last, values ascending, ties by row
     * ascending. Descending is derived from it ({@link #build}).
     *
     * @param type the key type
     * @return the comparator
     */
    static Comparator<Key> order(KeyType type) {
        return (a, b) -> {
            if (a.missing() || b.missing()) {
                if (a.missing() && b.missing()) {
                    return Integer.compare(a.row(), b.row());
                }
                return a.missing() ? 1 : -1;
            }
            int byValue = compareValues(type, a, b);
            return byValue != 0 ? byValue : Integer.compare(a.row(), b.row());
        };
    }

    /** Lists of text, first element first; a list that is a prefix of another sorts first. */
    private static int texts(String[] a, String[] b) {
        int shared = Math.min(a.length, b.length);
        for (int index = 0; index < shared; index++) {
            int comparison = a[index].compareTo(b[index]);
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(a.length, b.length);
    }

    /**
     * A sort file, checked and mapped, or why not.
     *
     * @param sortFile the file, when every check passed; the caller closes it
     * @param problem otherwise, the first check that failed
     */
    record Checked(Optional<SortFile> sortFile, Optional<IndexProblem> problem) {}

    /**
     * Opens a sort file if, and only if, every check passes.
     *
     * @param file the sort file
     * @param kind the table kind
     * @param sort the sort it must hold
     * @param rows how many rows it must order
     * @param raw the raw table it must describe
     * @return the mapped file, or the first problem found
     * @throws IOException if the file exists but cannot be read
     */
    static Checked open(Path file, TableKind kind, ResultSort sort, long rows, RawIdentity raw)
            throws IOException {
        if (!Files.isRegularFile(file)) {
            return refused(IndexProblem.ABSENT);
        }
        Arena arena = Arena.ofShared();
        boolean kept = false;
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            long size = channel.size();
            if (size < HEADER_BYTES) {
                return refused(IndexProblem.TRUNCATED);
            }
            MemorySegment segment = channel.map(FileChannel.MapMode.READ_ONLY, 0, size, arena);
            Optional<IndexProblem> problem = check(segment, size, kind, sort, rows, raw);
            if (problem.isPresent()) {
                return new Checked(Optional.empty(), problem);
            }
            kept = true;
            return new Checked(Optional.of(new SortFile(arena, segment)), Optional.empty());
        } finally {
            if (!kept) {
                arena.close();
            }
        }
    }

    private static Checked refused(IndexProblem problem) {
        return new Checked(Optional.empty(), Optional.of(problem));
    }

    private static Optional<IndexProblem> check(
            MemorySegment segment,
            long size,
            TableKind kind,
            ResultSort sort,
            long rows,
            RawIdentity raw) {
        byte[] magic = segment.asSlice(0, MAGIC.length).toArray(ValueLayout.JAVA_BYTE);
        if (!Arrays.equals(MAGIC, magic)) {
            return Optional.of(IndexProblem.NOT_AN_INDEX);
        }
        if (CheckedFiles.crc(segment, 0, AT_BODY_CRC + 4)
                != segment.get(CheckedFiles.INT, AT_BODY_CRC + 4)) {
            return Optional.of(IndexProblem.HEADER_DAMAGED);
        }
        if (segment.get(CheckedFiles.INT, AT_VERSION) != VERSION) {
            return Optional.of(IndexProblem.OTHER_FORMAT);
        }
        if (segment.get(CheckedFiles.INT, AT_KIND) != kind.ordinal()
                || segment.get(CheckedFiles.INT, AT_COLUMN) != sort.column().ordinal()
                || segment.get(CheckedFiles.INT, AT_DIRECTION) != sort.direction().ordinal()) {
            return Optional.of(IndexProblem.OTHER_TABLE);
        }
        byte[] sha256 = segment.asSlice(AT_RAW_SHA256, 32).toArray(ValueLayout.JAVA_BYTE);
        if (!Arrays.equals(raw.sha256Bytes(), sha256)) {
            return Optional.of(IndexProblem.RAW_CONTENT_CHANGED);
        }
        if (segment.get(CheckedFiles.LONG, AT_ROWS) != rows || size != HEADER_BYTES + rows * 4) {
            return Optional.of(IndexProblem.TRUNCATED);
        }
        if (CheckedFiles.crc(segment, HEADER_BYTES, size)
                != segment.get(CheckedFiles.INT, AT_BODY_CRC)) {
            return Optional.of(IndexProblem.BODY_DAMAGED);
        }
        BitSet seen = new BitSet((int) rows);
        for (long position = 0; position < rows; position++) {
            int row = segment.get(CheckedFiles.INT, HEADER_BYTES + position * 4);
            if (row < 0 || row >= rows || seen.get(row)) {
                return Optional.of(IndexProblem.NOT_A_PERMUTATION);
            }
            seen.set(row);
        }
        return Optional.empty();
    }

    /**
     * Builds both sort files of a column -- ascending and descending -- by one external merge sort
     * of its keys.
     *
     * <p>The merge writes the ascending order and notes, per position, whether its key equals the
     * one before. Descending is then the ascending file's groups of equal keys in reverse order,
     * each group in its own (file) order, followed by the rows with no value in file order: exactly
     * the rules of {@link ResultSort}, without a second pass over the keys.
     *
     * @param ascending where the ascending file goes; replaced atomically
     * @param descending where the descending file goes; replaced atomically
     * @param kind the table kind
     * @param column the column
     * @param type its key type
     * @param rows how many rows the keys must cover
     * @param raw the raw table's identity
     * @param keys every row's key, in file order
     * @return how many runs were spilled to disk: 0 when the keys fit one chunk
     * @throws IOException if the keys cannot be read, do not cover every row exactly once in file
     *     order, or a file cannot be written
     */
    static int build(
            Path ascending,
            Path descending,
            TableKind kind,
            ResultSort.Column column,
            KeyType type,
            long rows,
            RawIdentity raw,
            KeySource keys)
            throws IOException {
        return build(ascending, descending, kind, column, type, rows, raw, keys, CHUNK_BYTES);
    }

    /**
     * As {@link #build(Path, Path, TableKind, ResultSort.Column, KeyType, long, RawIdentity,
     * KeySource)}, with another chunk size: for tests of the spill and merge.
     *
     * @param chunkBytes the most key bytes, estimated, one chunk holds before it is spilled
     * @return how many runs were spilled to disk
     */
    static int build(
            Path ascending,
            Path descending,
            TableKind kind,
            ResultSort.Column column,
            KeyType type,
            long rows,
            RawIdentity raw,
            KeySource keys,
            long chunkBytes)
            throws IOException {
        Comparator<Key> order = order(type);
        Path directory =
                Objects.requireNonNull(
                        ascending.toAbsolutePath().getParent(), "the directory of " + ascending);
        List<Path> runs = new ArrayList<>();
        List<Integer> runSizes = new ArrayList<>();
        Ascending written;
        try (CheckedFiles.Writer writer = new CheckedFiles.Writer(ascending, HEADER_BYTES)) {
            written = new Ascending(writer, type);
            List<Key> chunk = new ArrayList<>();
            long[] held = {0};
            long[] seen = {0};
            keys.feed(
                    key -> {
                        if (key.row() != seen[0]) {
                            throw new IOException(
                                    "sort keys out of file order: row "
                                            + key.row()
                                            + " where row "
                                            + seen[0]
                                            + " was due");
                        }
                        seen[0]++;
                        chunk.add(key);
                        held[0] += key.estimatedBytes();
                        if (held[0] >= chunkBytes) {
                            runs.add(spill(directory, ascending, chunk, order));
                            runSizes.add(chunk.size());
                            chunk.clear();
                            held[0] = 0;
                        }
                    });
            if (seen[0] != rows) {
                throw new IOException(
                        "sort keys cover " + seen[0] + " rows, but the index holds " + rows);
            }
            if (runs.isEmpty()) {
                chunk.sort(order);
                for (Key key : chunk) {
                    written.accept(key);
                }
            } else {
                if (!chunk.isEmpty()) {
                    runs.add(spill(directory, ascending, chunk, order));
                    runSizes.add(chunk.size());
                    chunk.clear();
                }
                merge(runs, runSizes, order, written);
            }
            writer.finish(
                    header(kind, column, ResultSort.Direction.ASCENDING, rows, raw), AT_BODY_CRC);
        } finally {
            for (Path run : runs) {
                Files.deleteIfExists(run);
            }
        }
        writeDescending(ascending, descending, kind, column, rows, raw, written);
        return runs.size();
    }

    private static ByteBuffer header(
            TableKind kind,
            ResultSort.Column column,
            ResultSort.Direction direction,
            long rows,
            RawIdentity raw) {
        ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES);
        header.put(MAGIC)
                .putInt(VERSION)
                .putInt(kind.ordinal())
                .putInt(column.ordinal())
                .putInt(direction.ordinal())
                .putLong(rows)
                .put(raw.sha256Bytes());
        return header;
    }

    /** Writes the ascending order and notes, per position, a key equal to the one before it. */
    private static final class Ascending implements KeySink {
        private final CheckedFiles.Writer writer;
        private final KeyType type;
        private final BitSet tiesWithPrevious = new BitSet();
        private Key previous;
        private int position;
        private int missing;

        Ascending(CheckedFiles.Writer writer, KeyType type) {
            this.writer = writer;
            this.type = type;
        }

        @Override
        public void accept(Key key) throws IOException {
            writer.body(4).putInt(key.row());
            if (key.missing()) {
                missing++;
            } else if (previous != null && compareValues(type, previous, key) == 0) {
                tiesWithPrevious.set(position);
            }
            previous = key;
            position++;
        }
    }

    /** Descending: the ascending groups of equal values reversed, then the rows with none. */
    private static void writeDescending(
            Path ascending,
            Path descending,
            TableKind kind,
            ResultSort.Column column,
            long rows,
            RawIdentity raw,
            Ascending written)
            throws IOException {
        Checked checked =
                open(
                        ascending,
                        kind,
                        new ResultSort(column, ResultSort.Direction.ASCENDING),
                        rows,
                        raw);
        if (checked.sortFile().isEmpty()) {
            throw new ResultIndexException(
                    checked.problem().orElseThrow(), ascending, "in the sort file just written");
        }
        try (SortFile order = checked.sortFile().get();
                CheckedFiles.Writer writer = new CheckedFiles.Writer(descending, HEADER_BYTES)) {
            int valued = Math.toIntExact(rows - written.missing);
            int end = valued;
            while (end > 0) {
                int start = end - 1;
                while (start > 0 && written.tiesWithPrevious.get(start)) {
                    start--;
                }
                for (int position = start; position < end; position++) {
                    writer.body(4).putInt(order.rowAt(position));
                }
                end = start;
            }
            for (long position = valued; position < rows; position++) {
                writer.body(4).putInt(order.rowAt(position));
            }
            writer.finish(
                    header(kind, column, ResultSort.Direction.DESCENDING, rows, raw), AT_BODY_CRC);
        }
    }

    /** Two values compared, ascending, neither missing; 0 when they tie. */
    private static int compareValues(KeyType type, Key a, Key b) {
        return switch (type) {
            case WHOLE -> Long.compare(a.whole(), b.whole());
            case REAL -> a.real() < b.real() ? -1 : a.real() > b.real() ? 1 : 0;
            case TEXT -> texts(a.texts(), b.texts());
        };
    }

    private static Path spill(Path directory, Path file, List<Key> chunk, Comparator<Key> order)
            throws IOException {
        chunk.sort(order);
        Path run = Files.createTempFile(directory, file.getFileName() + ".", ".run");
        try (DataOutputStream out =
                new DataOutputStream(
                        new BufferedOutputStream(Files.newOutputStream(run), RUN_BUFFER_BYTES))) {
            for (Key key : chunk) {
                out.writeInt(key.row());
                out.writeBoolean(key.missing());
                out.writeLong(key.whole());
                out.writeDouble(key.real());
                String[] texts = key.texts();
                out.writeInt(texts == null ? -1 : texts.length);
                if (texts != null) {
                    for (String text : texts) {
                        byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
                        out.writeInt(utf8.length);
                        out.write(utf8);
                    }
                }
            }
        }
        return run;
    }

    private static Key read(DataInputStream in) throws IOException {
        int row = in.readInt();
        boolean missing = in.readBoolean();
        long whole = in.readLong();
        double real = in.readDouble();
        int count = in.readInt();
        String[] texts = null;
        if (count >= 0) {
            texts = new String[count];
            for (int index = 0; index < count; index++) {
                byte[] utf8 = new byte[in.readInt()];
                in.readFully(utf8);
                texts[index] = new String(utf8, StandardCharsets.UTF_8);
            }
        }
        return new Key(row, missing, whole, real, texts);
    }

    /** One run being merged: its stream, its next key and how many keys are left after it. */
    private static final class Cursor {
        private final DataInputStream in;
        private Key head;
        private int left;

        Cursor(DataInputStream in, int size) throws IOException {
            this.in = in;
            this.left = size;
            advance();
        }

        boolean advance() throws IOException {
            if (left == 0) {
                head = null;
                return false;
            }
            head = read(in);
            left--;
            return true;
        }
    }

    private static void merge(
            List<Path> runs, List<Integer> sizes, Comparator<Key> order, KeySink sink)
            throws IOException {
        List<DataInputStream> streams = new ArrayList<>();
        try {
            PriorityQueue<Cursor> heads =
                    new PriorityQueue<>(runs.size(), (a, b) -> order.compare(a.head, b.head));
            for (int index = 0; index < runs.size(); index++) {
                DataInputStream in =
                        new DataInputStream(
                                new BufferedInputStream(
                                        Files.newInputStream(runs.get(index)), RUN_BUFFER_BYTES));
                streams.add(in);
                Cursor cursor = new Cursor(in, sizes.get(index));
                if (cursor.head != null) {
                    heads.add(cursor);
                }
            }
            while (!heads.isEmpty()) {
                Cursor next = heads.poll();
                sink.accept(next.head);
                if (next.advance()) {
                    heads.add(next);
                }
            }
        } finally {
            for (DataInputStream in : streams) {
                in.close();
            }
        }
    }

    /**
     * The row at a position of the order.
     *
     * @param position from 0
     * @return the row index, from 0
     */
    int rowAt(long position) {
        return segment.get(CheckedFiles.INT, HEADER_BYTES + position * 4);
    }

    /** Unmaps the file. */
    @Override
    public void close() {
        arena.close();
    }
}
