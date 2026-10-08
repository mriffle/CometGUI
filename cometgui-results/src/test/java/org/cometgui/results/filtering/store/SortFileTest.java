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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The external merge sort behind every sort file, with a chunk so small that the keys are spilled
 * to many runs and merged -- the path the large fixture takes -- against the order a plain {@code
 * List.sort} by the rules restated here gives, and against the single-chunk path. The large
 * fixture's spilled sorts are checked against independent orders in {@link DiskLargeFixtureTest}.
 */
class SortFileTest {

    private static final RawIdentity RAW = new RawIdentity(1, 2, "ab".repeat(32));

    @TempDir private Path work;

    /** 300 keys with ties, missing values and shared prefixes, in file order. */
    private static List<SortFile.Key> keys(SortFile.KeyType type) {
        List<SortFile.Key> keys = new ArrayList<>();
        for (int row = 0; row < 300; row++) {
            int value = (row * 37) % 23;
            if (row % 11 == 0) {
                keys.add(SortFile.Key.missing(row));
                continue;
            }
            keys.add(
                    switch (type) {
                        case WHOLE -> SortFile.Key.whole(row, value);
                        case REAL -> SortFile.Key.real(row, value == 5 ? -0.0 : value / 7.0);
                        case TEXT ->
                                row % 3 == 0
                                        ? SortFile.Key.text(row, "p" + value)
                                        : SortFile.Key.text(row, "p" + value, "q" + (row % 4));
                    });
        }
        return keys;
    }

    /**
     * The rules of ResultSort restated: missing last both ways; values in the direction asked
     * ({@code -0.0} equal to {@code 0.0}; texts joined by a character none contains); ties by row.
     */
    private static List<Integer> expected(
            SortFile.KeyType type, List<SortFile.Key> keys, boolean descending) {
        List<SortFile.Key> sorted = new ArrayList<>(keys);
        sorted.sort(
                (a, b) -> {
                    if (a.missing() || b.missing()) {
                        return a.missing() == b.missing()
                                ? Integer.compare(a.row(), b.row())
                                : a.missing() ? 1 : -1;
                    }
                    int byValue =
                            switch (type) {
                                case WHOLE -> Long.compare(a.whole(), b.whole());
                                case REAL -> Double.compare(a.real() + 0.0, b.real() + 0.0);
                                case TEXT ->
                                        String.join("\u0000", a.texts())
                                                .compareTo(String.join("\u0000", b.texts()));
                            };
                    if (descending) {
                        byValue = -byValue;
                    }
                    return byValue != 0 ? byValue : Integer.compare(a.row(), b.row());
                });
        return sorted.stream().map(SortFile.Key::row).toList();
    }

    /** Builds both files of a column and reads both orders back: ascending, then descending. */
    private List<List<Integer>> build(
            SortFile.KeyType type, List<SortFile.Key> keys, long chunkBytes) throws IOException {
        ResultSort.Column column = ResultSort.Column.SCORE;
        Path ascending = work.resolve("sort-" + type + "-" + chunkBytes + "-up");
        Path descending = work.resolve("sort-" + type + "-" + chunkBytes + "-down");
        SortFile.build(
                ascending,
                descending,
                TableKind.TARGET_PSMS,
                column,
                type,
                keys.size(),
                RAW,
                sink -> {
                    for (SortFile.Key key : keys) {
                        sink.accept(key);
                    }
                },
                chunkBytes);
        List<List<Integer>> orders = new ArrayList<>();
        for (ResultSort sort :
                List.of(ResultSort.ascending(column), ResultSort.descending(column))) {
            Path file = sort.direction() == ResultSort.Direction.ASCENDING ? ascending : descending;
            SortFile.Checked checked =
                    SortFile.open(file, TableKind.TARGET_PSMS, sort, keys.size(), RAW);
            assertEquals(java.util.Optional.empty(), checked.problem(), sort.toString());
            try (SortFile sortFile = checked.sortFile().orElseThrow()) {
                List<Integer> rows = new ArrayList<>();
                for (int position = 0; position < keys.size(); position++) {
                    rows.add(sortFile.rowAt(position));
                }
                orders.add(rows);
            }
        }
        return orders;
    }

    @ParameterizedTest
    @ValueSource(strings = {"WHOLE", "REAL", "TEXT"})
    @DisplayName(
            "spilled to many runs and merged, or sorted in one chunk: the restated rules' order,"
                    + " ascending and the derived descending; no run file left behind")
    void spilledEqualsInMemory(String typeName) throws IOException {
        SortFile.KeyType type = SortFile.KeyType.valueOf(typeName);
        List<SortFile.Key> keys = keys(type);
        List<List<Integer>> expected =
                List.of(expected(type, keys, false), expected(type, keys, true));
        assertEquals(expected, build(type, keys, SortFile.CHUNK_BYTES), "one chunk");
        assertEquals(expected, build(type, keys, 1000), "spilled, about 10 a run");
        assertEquals(expected, build(type, keys, 1), "spilled, one key a run");
        try (Stream<Path> files = Files.list(work)) {
            for (Path file : files.toList()) {
                String name = String.valueOf(file.getFileName());
                assertTrue(name.startsWith("sort-") && !name.endsWith(".run"), name);
                assertTrue(!name.endsWith(".tmp"), name);
            }
        }
    }

    @Test
    @DisplayName(
            "descending derived from ascending: all keys equal (file order both ways), all missing,"
                    + " one row, no rows")
    void descendingEdges() throws IOException {
        List<SortFile.Key> equal = new ArrayList<>();
        List<SortFile.Key> missing = new ArrayList<>();
        for (int row = 0; row < 5; row++) {
            equal.add(SortFile.Key.real(row, row % 2 == 0 ? 0.0 : -0.0));
            missing.add(SortFile.Key.missing(row));
        }
        List<Integer> fileOrder = List.of(0, 1, 2, 3, 4);
        assertEquals(List.of(fileOrder, fileOrder), build(SortFile.KeyType.REAL, equal, 1));
        assertEquals(List.of(fileOrder, fileOrder), build(SortFile.KeyType.WHOLE, missing, 1));
        assertEquals(
                List.of(List.of(0), List.of(0)),
                build(SortFile.KeyType.TEXT, List.of(SortFile.Key.text(0, "a")), 1));
        assertEquals(List.of(List.of(), List.of()), build(SortFile.KeyType.TEXT, List.of(), 1));
        List<SortFile.Key> mixed =
                List.of(
                        SortFile.Key.whole(0, 2),
                        SortFile.Key.missing(1),
                        SortFile.Key.whole(2, 1),
                        SortFile.Key.whole(3, 2),
                        SortFile.Key.missing(4),
                        SortFile.Key.whole(5, 1));
        assertEquals(
                List.of(List.of(2, 5, 0, 3, 1, 4), List.of(0, 3, 2, 5, 1, 4)),
                build(SortFile.KeyType.WHOLE, mixed, 1));
    }

    @Test
    @DisplayName(
            "keys out of file order, or too few, are refused and leave no file; texts that are a"
                    + " prefix of one another sort shorter first")
    void refusesBadKeys() throws IOException {
        Path file = work.resolve("bad");
        ResultSort sort = ResultSort.ascending(ResultSort.Column.PEPTIDE);
        IOException outOfOrder =
                assertThrows(
                        IOException.class,
                        () ->
                                SortFile.build(
                                        file,
                                        work.resolve("bad-down"),
                                        TableKind.TARGET_PSMS,
                                        sort.column(),
                                        SortFile.KeyType.TEXT,
                                        2,
                                        RAW,
                                        sink -> {
                                            sink.accept(SortFile.Key.text(1, "a"));
                                            sink.accept(SortFile.Key.text(0, "b"));
                                        }));
        assertTrue(outOfOrder.getMessage().contains("out of file order"), outOfOrder.getMessage());
        IOException tooFew =
                assertThrows(
                        IOException.class,
                        () ->
                                SortFile.build(
                                        file,
                                        work.resolve("bad-down"),
                                        TableKind.TARGET_PSMS,
                                        sort.column(),
                                        SortFile.KeyType.TEXT,
                                        3,
                                        RAW,
                                        sink -> sink.accept(SortFile.Key.text(0, "a"))));
        assertTrue(tooFew.getMessage().contains("cover 1 rows"), tooFew.getMessage());
        try (Stream<Path> files = Files.list(work)) {
            assertEquals(List.of(), files.toList());
        }
        List<SortFile.Key> prefixes =
                List.of(
                        SortFile.Key.text(0, "ab", "c"),
                        SortFile.Key.text(1, "ab"),
                        SortFile.Key.text(2, "a"),
                        SortFile.Key.text(3, "ab", "b"));
        assertEquals(
                List.of(List.of(2, 1, 3, 0), List.of(0, 3, 1, 2)),
                build(SortFile.KeyType.TEXT, prefixes, 1));
    }
}
