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
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Optional;
import org.cometgui.results.parser.QValue;
import org.cometgui.results.parser.ResultRow;
import org.cometgui.results.parser.ResultTableReader;
import org.cometgui.results.parser.SpectrumReference;

/**
 * The disk-backed store's index of one raw table: one fixed-width record per row, memory-mapped
 * read-only, off the heap ({@code R-RES-03}, design decision P10-4).
 *
 * <p>The file, big-endian throughout:
 *
 * <pre>
 * header, 96 bytes
 *    0  8  magic "CGRESIDX"
 *    8  4  format version (1)
 *   12  4  record size (56)
 *   16  4  table kind (TableKind ordinal)
 *   20  4  0
 *   24  8  raw table size, bytes
 *   32  8  raw table modification time, ns since the epoch
 *   40 32  raw table SHA-256
 *   72  8  rows
 *   80  4  CRC-32C of the body
 *   84  4  CRC-32C of header bytes 0..83
 *   88  8  0
 * body: one 56-byte record per row, in file order (row i is line i + 2)
 *    0  8  byte offset of the row in the raw table
 *    8  4  its length in bytes, line terminator excluded
 *   12  1  q-value status (QValue.Status ordinal)
 *   13  3  0
 *   16  8  q-value (NaN unless known)
 *   24  8  score (NaN if not a decimal number)
 *   32  8  posterior error probability (NaN if not a decimal number)
 *   40  8  scan from the PSMId (-1 if not in SpecId shape)
 *   48  4  charge from the PSMId (-1 if not in SpecId shape)
 *   52  4  0
 * </pre>
 *
 * <p>It is built by one pass of the one reader over the raw table, written to a temporary file and
 * moved into place. It is used only when every check passes: magic, header checksum, format, table
 * kind, the raw table's size, modification time and SHA-256, the length the row count implies, and
 * the body checksum. Anything else is an {@link IndexProblem}, and the caller builds a new one.
 * Text is not indexed: rows are read back from the raw table by offset.
 */
final class DiskIndex implements Closeable {

    /** The header's size. */
    static final int HEADER_BYTES = 96;

    /** One row's record size. */
    static final int RECORD_BYTES = 56;

    /** The format version. */
    static final int VERSION = 1;

    /** The most rows an index holds: a sort file addresses rows by {@code int}. */
    static final long MAX_ROWS = Integer.MAX_VALUE;

    static final int AT_VERSION = 8;
    static final int AT_RECORD_BYTES = 12;
    static final int AT_KIND = 16;
    static final int AT_RAW_SIZE = 24;
    static final int AT_RAW_TIME = 32;
    static final int AT_RAW_SHA256 = 40;
    static final int AT_ROWS = 72;
    static final int AT_BODY_CRC = 80;

    static final int FIELD_OFFSET = 0;
    static final int FIELD_LENGTH = 8;
    static final int FIELD_STATUS = 12;
    static final int FIELD_Q = 16;
    static final int FIELD_SCORE = 24;
    static final int FIELD_PEP = 32;
    static final int FIELD_SCAN = 40;
    static final int FIELD_CHARGE = 48;

    private static final byte[] MAGIC = "CGRESIDX".getBytes(StandardCharsets.US_ASCII);
    private static final QValue.Status[] STATUSES = QValue.Status.values();

    private final Arena arena;
    private final MemorySegment segment;
    private final long rows;

    private DiskIndex(Arena arena, MemorySegment segment, long rows) {
        this.arena = arena;
        this.segment = segment;
        this.rows = rows;
    }

    /**
     * An index file, checked and mapped, or why not.
     *
     * @param index the index, when every check passed; the caller closes it
     * @param problem otherwise, the first check that failed
     */
    record Checked(Optional<DiskIndex> index, Optional<IndexProblem> problem) {}

    /**
     * Opens an index file if, and only if, every check passes.
     *
     * @param file the index file
     * @param kind the table kind it must describe
     * @param raw the raw table it must describe
     * @return the mapped index, or the first problem found
     * @throws IOException if the file exists but cannot be read
     */
    static Checked open(Path file, TableKind kind, RawIdentity raw) throws IOException {
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
            Optional<IndexProblem> problem = check(segment, size, kind, raw);
            if (problem.isPresent()) {
                return new Checked(Optional.empty(), problem);
            }
            kept = true;
            return new Checked(
                    Optional.of(
                            new DiskIndex(arena, segment, segment.get(CheckedFiles.LONG, AT_ROWS))),
                    Optional.empty());
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
            MemorySegment segment, long size, TableKind kind, RawIdentity raw) {
        byte[] magic = segment.asSlice(0, MAGIC.length).toArray(ValueLayout.JAVA_BYTE);
        if (!Arrays.equals(MAGIC, magic)) {
            return Optional.of(IndexProblem.NOT_AN_INDEX);
        }
        if (CheckedFiles.crc(segment, 0, AT_BODY_CRC + 4)
                != segment.get(CheckedFiles.INT, AT_BODY_CRC + 4)) {
            return Optional.of(IndexProblem.HEADER_DAMAGED);
        }
        if (segment.get(CheckedFiles.INT, AT_VERSION) != VERSION
                || segment.get(CheckedFiles.INT, AT_RECORD_BYTES) != RECORD_BYTES) {
            return Optional.of(IndexProblem.OTHER_FORMAT);
        }
        if (segment.get(CheckedFiles.INT, AT_KIND) != kind.ordinal()) {
            return Optional.of(IndexProblem.OTHER_TABLE);
        }
        if (segment.get(CheckedFiles.LONG, AT_RAW_SIZE) != raw.size()) {
            return Optional.of(IndexProblem.RAW_SIZE_CHANGED);
        }
        if (segment.get(CheckedFiles.LONG, AT_RAW_TIME) != raw.modifiedNanos()) {
            return Optional.of(IndexProblem.RAW_TIME_CHANGED);
        }
        byte[] sha256 = segment.asSlice(AT_RAW_SHA256, 32).toArray(ValueLayout.JAVA_BYTE);
        if (!Arrays.equals(raw.sha256Bytes(), sha256)) {
            return Optional.of(IndexProblem.RAW_CONTENT_CHANGED);
        }
        long rows = segment.get(CheckedFiles.LONG, AT_ROWS);
        if (rows < 0 || rows > MAX_ROWS || size != HEADER_BYTES + rows * RECORD_BYTES) {
            return Optional.of(IndexProblem.TRUNCATED);
        }
        if (CheckedFiles.crc(segment, HEADER_BYTES, size)
                != segment.get(CheckedFiles.INT, AT_BODY_CRC)) {
            return Optional.of(IndexProblem.BODY_DAMAGED);
        }
        return Optional.empty();
    }

    /**
     * Builds an index file by one pass of the one reader over the raw table.
     *
     * @param raw the raw table, only ever read
     * @param kind which table it is
     * @param identity the raw table's identity, read before this pass
     * @param file where the index goes; replaced atomically
     * @throws ResultIndexException if the table has too many rows, or changed during the pass
     * @throws IOException if the reader refuses the table or the index cannot be written
     */
    static void build(Path raw, TableKind kind, RawIdentity identity, Path file)
            throws IOException {
        try (CheckedFiles.Writer writer = new CheckedFiles.Writer(file, HEADER_BYTES);
                ResultTableReader reader = ResultTableReader.open(raw)) {
            long rows = 0;
            for (ResultRow row = reader.next(); row != null; row = reader.next()) {
                if (rows == MAX_ROWS) {
                    throw new ResultIndexException(
                            IndexProblem.TOO_MANY_ROWS, raw, "more than " + MAX_ROWS);
                }
                ByteBuffer out = writer.body(RECORD_BYTES);
                Optional<SpectrumReference> reference = row.spectrumReference();
                QValue q = row.qValue();
                out.putLong(reader.lastRowOffset());
                out.putInt(reader.lastRowLength());
                out.put((byte) q.status().ordinal());
                out.put((byte) 0).putShort((short) 0);
                out.putDouble(q.isKnown() ? q.value() : Double.NaN);
                out.putDouble(row.score());
                out.putDouble(row.posteriorErrorProbability());
                out.putLong(reference.map(SpectrumReference::scan).orElse(-1L));
                out.putInt(reference.map(SpectrumReference::charge).orElse(-1));
                out.putInt(0);
                rows++;
            }
            identity.requireUnchanged(raw, IndexProblem.RAW_CHANGED_WHILE_INDEXING);
            ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES);
            header.put(MAGIC)
                    .putInt(VERSION)
                    .putInt(RECORD_BYTES)
                    .putInt(kind.ordinal())
                    .putInt(0)
                    .putLong(identity.size())
                    .putLong(identity.modifiedNanos())
                    .put(identity.sha256Bytes())
                    .putLong(rows);
            writer.finish(header, AT_BODY_CRC);
        }
    }

    /**
     * How many rows the index holds.
     *
     * @return the row count
     */
    long rows() {
        return rows;
    }

    long offset(long row) {
        return segment.get(CheckedFiles.LONG, at(row) + FIELD_OFFSET);
    }

    int length(long row) {
        return segment.get(CheckedFiles.INT, at(row) + FIELD_LENGTH);
    }

    QValue.Status status(long row) {
        return STATUSES[segment.get(ValueLayout.JAVA_BYTE, at(row) + FIELD_STATUS)];
    }

    double qValue(long row) {
        return segment.get(CheckedFiles.DOUBLE, at(row) + FIELD_Q);
    }

    double score(long row) {
        return segment.get(CheckedFiles.DOUBLE, at(row) + FIELD_SCORE);
    }

    double pep(long row) {
        return segment.get(CheckedFiles.DOUBLE, at(row) + FIELD_PEP);
    }

    long scan(long row) {
        return segment.get(CheckedFiles.LONG, at(row) + FIELD_SCAN);
    }

    int charge(long row) {
        return segment.get(CheckedFiles.INT, at(row) + FIELD_CHARGE);
    }

    private static long at(long row) {
        return HEADER_BYTES + row * RECORD_BYTES;
    }

    /** Unmaps the index. */
    @Override
    public void close() {
        arena.close();
    }
}
