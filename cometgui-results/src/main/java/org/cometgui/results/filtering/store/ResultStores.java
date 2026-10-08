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
import java.nio.file.Path;
import java.util.Objects;
import org.cometgui.domain.ports.HashService;
import org.cometgui.results.parser.PercolatorOutputException;
import org.cometgui.results.parser.ResultTableReader;

/**
 * Opens {@link ResultStore}s: in memory at or below {@link #IN_MEMORY_ROW_LIMIT} rows, on disk
 * above it ({@code R-RES-03}, design decision P10-4).
 *
 * <p>{@link #open} decides by counting the table's rows with the one reader, stopping as soon as it
 * has seen one row more than the limit, so deciding costs at most a parse of {@code limit + 1} rows
 * -- about 0.1 s at the limit on the phase 10 host, 0.3 s in a fresh JVM -- whatever the table's
 * size.
 */
public final class ResultStores {

    /**
     * The most rows a table may have to be held in memory: {@value}. Above it, {@link #open} opens
     * the disk-backed store.
     *
     * <p>Chosen from measurement on the phase 10 host (2026-10-08). The in-memory store holds every
     * row as parsed objects, about 670 bytes of heap a row (the 1 000 000-row large fixture: about
     * 670 MB, 1.9 s to open), and a run opens up to four tables -- target and decoy PSMs and
     * peptides -- at once. At 100 000 rows a table costs about 67 MB and 0.2 s, so four stay near
     * 270 MB, within the default heap of a desktop JVM; the real K562 search's largest table has
     * 3897 rows. Above the limit the disk store's costs are small beside what the heap would hold:
     * at 1 000 000 rows a first opening (hash, one indexing pass) takes about 2.2 s, a later one
     * (hash, check) about 0.7 s, a count 0.01 s, a numeric sort's first use 0.7 s, a text sort's
     * 2.7 s and a new text filter 1.5 s, while its heap stays flat: it runs that whole sequence in
     * a 16 MB heap, and its budget test holds it to 64 MB, where reading the table into memory
     * fails ({@code DiskStoreBudgetTest}).
     */
    public static final long IN_MEMORY_ROW_LIMIT = 100_000;

    private ResultStores() {}

    /**
     * Opens a table in the store that suits its size.
     *
     * @param file the raw Percolator table, only ever read
     * @param kind which of the four tables it is
     * @param indexDirectory where the disk store keeps its index if the table needs one; made if
     *     absent, never the raw table's own directory; untouched for a table held in memory
     * @param hasher the one hasher, for the disk store's check of the raw table
     * @return the open store; the caller closes it
     * @throws PercolatorOutputException if the reader refuses the file
     * @throws IOException if the disk store cannot build or read its index
     * @throws NullPointerException if any argument is {@code null}
     */
    public static ResultStore open(
            Path file, TableKind kind, Path indexDirectory, HashService hasher) throws IOException {
        return open(file, kind, indexDirectory, hasher, IN_MEMORY_ROW_LIMIT);
    }

    /**
     * As {@link #open(Path, TableKind, Path, HashService)}, with another limit: for tests.
     *
     * @param limit the most rows held in memory
     */
    static ResultStore open(
            Path file, TableKind kind, Path indexDirectory, HashService hasher, long limit)
            throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(indexDirectory, "indexDirectory");
        Objects.requireNonNull(hasher, "hasher");
        return rowsUpTo(file, limit + 1) <= limit
                ? inMemory(file, kind)
                : onDisk(file, kind, indexDirectory, hasher);
    }

    /**
     * Opens a table in memory, whatever its size.
     *
     * @param file the raw Percolator table, only ever read
     * @param kind which of the four tables it is
     * @return the open store; the caller closes it
     * @throws PercolatorOutputException if the reader refuses the file
     * @throws NullPointerException if either is {@code null}
     */
    public static ResultStore inMemory(Path file, TableKind kind) throws PercolatorOutputException {
        Objects.requireNonNull(file, "file");
        return InMemoryResultStore.open(file, kind);
    }

    /**
     * Opens a table on disk, whatever its size.
     *
     * @param file the raw Percolator table, only ever read
     * @param kind which of the four tables it is
     * @param indexDirectory where the index lives; made if absent; never the raw table's own
     *     directory
     * @param hasher the one hasher
     * @return the open store; the caller closes it
     * @throws PercolatorOutputException if the reader refuses the file
     * @throws ResultIndexException if the index cannot be trusted even when freshly built
     * @throws IOException if a file cannot be read or written
     * @throws NullPointerException if any argument is {@code null}
     */
    public static ResultStore onDisk(
            Path file, TableKind kind, Path indexDirectory, HashService hasher) throws IOException {
        return DiskResultStore.open(file, kind, indexDirectory, hasher);
    }

    /**
     * Counts a table's rows with the one reader, but no further than a limit.
     *
     * @param file the table
     * @param limit where to stop counting
     * @return the row count, or {@code limit} if there are at least that many
     * @throws PercolatorOutputException if the reader refuses the file within the rows counted
     */
    static long rowsUpTo(Path file, long limit) throws PercolatorOutputException {
        long rows = 0;
        try (ResultTableReader reader = ResultTableReader.open(file)) {
            while (rows < limit && reader.next() != null) {
                rows++;
            }
        }
        return rows;
    }
}
