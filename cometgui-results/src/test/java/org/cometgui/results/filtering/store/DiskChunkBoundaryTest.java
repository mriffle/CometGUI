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

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.cometgui.results.filtering.FilterCounts;
import org.cometgui.results.filtering.PsmQValueFilter;
import org.cometgui.results.parser.ResultRow;
import org.cometgui.results.testing.IndependentCounter;
import org.cometgui.results.testing.IndependentCounts;
import org.cometgui.results.testing.TestHasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A table just large enough that the index (56 bytes a row) and a sort file (4 bytes a row) both
 * fill more than one of their writers' 1 MiB chunks -- 270 000 rows -- so the chunk boundary is
 * crossed in-process, in seconds, rather than only by the large fixture: counts against {@link
 * IndependentCounter}, orders against {@link StoreOracle}, file sizes against the documented
 * formats. Generated under this test's own directory; about 3 s.
 */
class DiskChunkBoundaryTest {

    private static final int ROWS = 270_000;

    @TempDir private Path work;

    @Test
    @DisplayName(
            "270 000 rows: the index and sort files cross their 1 MiB write chunks and still hold"
                    + " every row -- counts equal the independent counter's, orders the oracle's")
    void crossesTheWriteChunks() throws IOException {
        Path raw = Files.createDirectories(work.resolve("outputs")).resolve("psms.tsv");
        try (BufferedWriter out = Files.newBufferedWriter(raw, StandardCharsets.UTF_8)) {
            out.write("PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds\n");
            for (int row = 0; row < ROWS; row++) {
                long mixed = (row * 2_654_435_761L) & 0xffff_ffffL;
                out.write(
                        String.format(
                                Locale.ROOT,
                                "s_%d_%d_1\t%d.%03d\t%s\t0.%d\tK.%s.R\tp%d\n",
                                mixed % 50_000,
                                2 + row % 3,
                                mixed % 7,
                                row % 1000,
                                row % 997 == 0
                                        ? "NaN"
                                        : "0." + String.format(Locale.ROOT, "%04d", mixed % 10_000),
                                row % 10,
                                Long.toString(mixed, 36).toUpperCase(Locale.ROOT),
                                mixed % 300));
            }
        }
        Path index = work.resolve("results");
        List<String> cutoffs = List.of("0", "0.01", "1");
        IndependentCounter.Tally tally = IndependentCounter.count(raw, cutoffs);
        StoreOracle oracle = StoreOracle.read(raw);
        try (ResultStore store =
                ResultStores.onDisk(raw, TableKind.TARGET_PSMS, index, new TestHasher())) {
            assertEquals(ROWS, store.rowCount());
            for (String cutoff : cutoffs) {
                IndependentCounts expected = tally.at(cutoff);
                assertEquals(
                        new FilterCounts(
                                expected.total(),
                                expected.passing(),
                                expected.failing(),
                                expected.unknown()),
                        store.counts(PsmQValueFilter.parse(cutoff)),
                        "at " + cutoff);
            }
            ResultQuery all =
                    ResultQuery.firstPage(PsmQValueFilter.DEFAULT).withCategory(Category.ALL);
            for (ResultSort sort :
                    List.of(
                            ResultSort.descending(ResultSort.Column.SCORE),
                            ResultSort.ascending(ResultSort.Column.PEPTIDE))) {
                boolean descending = sort.direction() == ResultSort.Direction.DESCENDING;
                String column = sort.column().name();
                assertEquals(
                        oracle.expectEnd("0.01", "ALL", "", column, descending, 100, false),
                        lines(store.query(all.withSort(sort).withPage(0, 100))),
                        sort + " first page");
                assertEquals(
                        oracle.expectEnd("0.01", "ALL", "", column, descending, 100, true),
                        lines(store.query(all.withSort(sort).withPage(ROWS - 100, 100))),
                        sort + " last page");
            }
            assertEquals(
                    oracle.expect("0.01", "ALL", "", "FILE_ORDER", false).subList(200_000, 200_050),
                    lines(store.query(all.withPage(200_000, 50))),
                    "file order deep in the table");
        }
        assertEquals(96 + 56L * ROWS, Files.size(index.resolve("target-psms.index")));
        assertEquals(
                72 + 4L * ROWS, Files.size(index.resolve("target-psms.sort-score-descending")));
        assertEquals(
                72 + 4L * ROWS, Files.size(index.resolve("target-psms.sort-peptide-ascending")));
    }

    private static List<Long> lines(ResultPage page) {
        return page.rows().stream().map(ResultRow::line).toList();
    }
}
