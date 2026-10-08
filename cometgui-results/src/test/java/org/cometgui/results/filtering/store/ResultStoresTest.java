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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.cometgui.results.filtering.FilterCounts;
import org.cometgui.results.filtering.PsmQValueFilter;
import org.cometgui.results.parser.PercolatorOutputException;
import org.cometgui.results.testing.TestHasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The factory's threshold ({@code R-RES-03}): at or below the limit the table is held in memory and
 * nothing is written or hashed; one row above it, the disk store opens. The large fixture's switch
 * at the real limit is {@link DiskStoreBudgetTest}'s.
 */
class ResultStoresTest {

    @TempDir private Path work;

    private Path table(int rows) throws IOException {
        StringBuilder text =
                new StringBuilder(
                        "PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds\n");
        for (int row = 0; row < rows; row++) {
            text.append("t_")
                    .append(row)
                    .append("_2_1\t1\t")
                    .append(row % 2 == 0 ? "0.001" : "0.5")
                    .append("\t0\tK.A.R\tp\n");
        }
        Path file = Files.createDirectories(work.resolve("outputs")).resolve("t" + rows + ".tsv");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    @DisplayName(
            "the documented limit is 100 000 rows; a table of exactly the limit is held in memory,"
                    + " one row more opens on disk")
    void switchesAboveTheLimit() throws IOException {
        assertEquals(100_000L, ResultStores.IN_MEMORY_ROW_LIMIT);
        Path index = work.resolve("results");
        TestHasher hasher = new TestHasher();
        try (ResultStore atLimit =
                ResultStores.open(table(10), TableKind.TARGET_PSMS, index, hasher, 10)) {
            assertInstanceOf(InMemoryResultStore.class, atLimit);
            assertEquals(new FilterCounts(10, 5, 5, 0), atLimit.counts(PsmQValueFilter.DEFAULT));
        }
        assertEquals(0, hasher.calls(), "nothing hashed for a table held in memory");
        assertEquals(false, Files.exists(index), "nothing written for a table held in memory");
        try (ResultStore above =
                ResultStores.open(table(11), TableKind.TARGET_PSMS, index, hasher, 10)) {
            assertInstanceOf(DiskResultStore.class, above);
            assertEquals(new FilterCounts(11, 6, 5, 0), above.counts(PsmQValueFilter.DEFAULT));
        }
        assertEquals(1, hasher.calls(), "the disk store hashes the raw table once");
        try (Stream<Path> files = Files.list(index)) {
            assertEquals(List.of(index.resolve("target-psms.index")), files.toList());
        }
        try (ResultStore empty =
                ResultStores.open(table(0), TableKind.TARGET_PSMS, index, hasher, 0)) {
            assertInstanceOf(InMemoryResultStore.class, empty);
        }
        try (ResultStore one =
                ResultStores.open(table(1), TableKind.TARGET_PSMS, index, hasher, 0)) {
            assertInstanceOf(DiskResultStore.class, one);
        }
    }

    @Test
    @DisplayName(
            "the default limit: a table of a few rows opens in memory through the public factory")
    void defaultLimit() throws IOException {
        try (ResultStore store =
                ResultStores.open(
                        table(1000), TableKind.TARGET_PSMS, work.resolve("i"), new TestHasher())) {
            assertInstanceOf(InMemoryResultStore.class, store);
        }
    }

    @Test
    @DisplayName(
            "rowsUpTo counts with the one reader and stops at its limit; a refused row within the"
                    + " limit is refused, one beyond it is not read")
    void rowsUpTo() throws IOException {
        Path ten = table(10);
        assertEquals(10, ResultStores.rowsUpTo(ten, 11));
        assertEquals(10, ResultStores.rowsUpTo(ten, 10));
        assertEquals(4, ResultStores.rowsUpTo(ten, 4));
        assertEquals(0, ResultStores.rowsUpTo(ten, 0));
        assertEquals(0, ResultStores.rowsUpTo(table(0), 5));
        Path blankAtLine4 = work.resolve("blank.tsv");
        Files.writeString(
                blankAtLine4,
                Files.readString(ten, StandardCharsets.UTF_8).replaceFirst("t_2_2_1[^\n]*", ""),
                StandardCharsets.UTF_8);
        assertEquals(2, ResultStores.rowsUpTo(blankAtLine4, 2), "line 4 is not reached");
        PercolatorOutputException refused =
                assertThrows(
                        PercolatorOutputException.class,
                        () -> ResultStores.rowsUpTo(blankAtLine4, 3));
        assertEquals(PercolatorOutputException.Problem.BLANK_LINE, refused.problem());
    }

    @Test
    @DisplayName("onDisk refuses a null argument")
    void onDiskRefusesNull() throws IOException {
        Path file = table(3);
        Path index = work.resolve("i");
        TestHasher hasher = new TestHasher();
        assertThrows(
                NullPointerException.class,
                () -> ResultStores.onDisk(null, TableKind.TARGET_PSMS, index, hasher));
        assertThrows(
                NullPointerException.class, () -> ResultStores.onDisk(file, null, index, hasher));
        assertThrows(
                NullPointerException.class,
                () -> ResultStores.onDisk(file, TableKind.TARGET_PSMS, null, hasher));
        assertThrows(
                NullPointerException.class,
                () -> ResultStores.onDisk(file, TableKind.TARGET_PSMS, index, null));
    }
}
