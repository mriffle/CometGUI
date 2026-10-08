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

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.TreeMap;
import org.cometgui.results.filtering.FilterCounts;
import org.cometgui.results.filtering.PsmQValueFilter;
import org.cometgui.results.filtering.QValueFilter;
import org.cometgui.results.parser.ResultRow;
import org.cometgui.results.testing.IndependentCounts;
import org.cometgui.results.testing.LargeFixture;
import org.cometgui.results.testing.ScratchFixtures;
import org.cometgui.results.testing.TestHasher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The large fixture's 1 000 000-row PSM table (design decision P10-2) through the disk-backed
 * store, opened by the factory -- whose threshold picks the disk store for it -- once for the
 * class: counts equal the generator's manifest at all eight cutoffs, every unknown kind is in the
 * unknown category in the generator's numbers, every sort column's first page in both directions
 * and the q-value and peptide orders' middle and last pages equal {@link StoreOracle}'s (test code:
 * {@code split}, {@code BigDecimal}, no production class), and a text filter matches what the
 * oracle finds. Fails, never skips, when the fixture is absent.
 *
 * <p>Cost, measured on the phase 10 host (2026-10-08): about 35 s for the class, most of it the
 * oracle reading and ordering a million rows and the store building 18 sort files.
 */
class DiskLargeFixtureTest {

    @TempDir private static Path indexDirectory;

    private static LargeFixture fixture;
    private static ResultStore store;
    private static StoreOracle oracle;
    private static String sha256Before;

    @BeforeAll
    static void open() throws IOException {
        fixture = LargeFixture.locate();
        sha256Before = ScratchFixtures.sha256(fixture.psms());
        store =
                ResultStores.open(
                        fixture.psms(), TableKind.TARGET_PSMS, indexDirectory, new TestHasher());
        oracle = StoreOracle.read(fixture.psms());
    }

    @AfterAll
    static void close() throws IOException {
        if (store != null) {
            store.close();
        }
        if (fixture != null) {
            assertEquals(
                    sha256Before,
                    ScratchFixtures.sha256(fixture.psms()),
                    "gate 4: the raw table is byte-identical after indexing, every query and"
                            + " close");
        }
    }

    @Test
    @DisplayName(
            "the factory opens the disk store for the large fixture; gate 3 at scale: counts equal"
                    + " the manifest at 0, 0.001, 0.005, 0.01, 0.05, 0.1, 0.5 and 1")
    void countsEqualManifest() throws IOException {
        assertInstanceOf(DiskResultStore.class, store);
        assertEquals(1_000_000L, store.rowCount());
        assertEquals(8, fixture.cutoffs().size());
        for (String cutoff : fixture.cutoffs()) {
            IndependentCounts expected = fixture.expected(LargeFixture.PSMS, cutoff);
            FilterCounts counts =
                    new FilterCounts(
                            expected.total(),
                            expected.passing(),
                            expected.failing(),
                            expected.unknown());
            assertEquals(counts, store.counts(PsmQValueFilter.parse(cutoff)), "at " + cutoff);
            assertEquals(
                    counts.passing(),
                    store.query(ResultQuery.firstPage(PsmQValueFilter.parse(cutoff))).matching(),
                    "passing matches at " + cutoff);
        }
    }

    @Test
    @DisplayName(
            "gate 8 at scale: the unknown category holds exactly the manifest's unknown kinds, in"
                    + " its numbers, at 0 and at 1")
    void unknownKindsInTheirCategory() throws IOException {
        Map<String, Long> expected = new TreeMap<>();
        for (LargeFixture.UnknownKind kind : fixture.unknownKinds(LargeFixture.PSMS)) {
            expected.put(kind.text(), kind.rows());
        }
        for (String cutoff : List.of("0", "1")) {
            QValueFilter filter = PsmQValueFilter.parse(cutoff);
            ResultQuery unknown =
                    ResultQuery.firstPage(filter).withCategory(Category.UNKNOWN_Q_VALUE);
            Map<String, Long> found = new TreeMap<>();
            for (long line : ResultStoreContract.allLines(store, unknown, 500)) {
                ResultRow row = store.row(new RowKey(line)).orElseThrow();
                found.merge(row.qValue().text(), 1L, Long::sum);
            }
            assertEquals(expected, found, "at " + cutoff);
        }
    }

    @Test
    @DisplayName(
            "every column, both directions: the first page equals the oracle's; by q-value and by"
                    + " peptide, both directions, the middle and last pages too")
    void sortsAgainstTheOracle() throws IOException {
        int size = ResultQuery.DEFAULT_PAGE_SIZE;
        QValueFilter filter = PsmQValueFilter.DEFAULT;
        for (ResultSort.Column column : ResultSort.Column.values()) {
            for (ResultSort.Direction direction : ResultSort.Direction.values()) {
                boolean descending = direction == ResultSort.Direction.DESCENDING;
                String where = column + " " + direction;
                ResultQuery all =
                        ResultQuery.firstPage(filter)
                                .withCategory(Category.ALL)
                                .withSort(new ResultSort(column, direction));
                assertEquals(
                        oracle.expectEnd("0.01", "ALL", "", column.name(), descending, size, false),
                        lines(store.query(all.withPage(0, size))),
                        where + ", first page");
                if (column == ResultSort.Column.Q_VALUE || column == ResultSort.Column.PEPTIDE) {
                    List<Long> order =
                            oracle.expect("0.01", "PASSING", "", column.name(), descending);
                    ResultQuery passing = all.withCategory(Category.PASSING);
                    int middle = order.size() / 2;
                    assertEquals(
                            order.subList(middle, middle + size),
                            lines(store.query(passing.withPage(middle, size))),
                            where + ", middle passing page");
                    assertEquals(
                            order.subList(order.size() - 7, order.size()),
                            lines(store.query(passing.withPage(order.size() - 7, size))),
                            where + ", last passing page");
                    assertEquals(
                            oracle.expectEnd(
                                    "0.01", "ALL", "", column.name(), descending, size, true),
                            lines(store.query(all.withPage(1_000_000 - size, size))),
                            where + ", last page of all");
                    long line = order.get(middle + 3);
                    assertEquals(
                            OptionalLong.of(middle + 3),
                            store.positionOf(new RowKey(line), passing),
                            where + ", position of line " + line);
                }
            }
        }
    }

    @Test
    @DisplayName(
            "a text filter at scale: as many matches as the oracle finds, the first page its first"
                    + " rows, and counts still over the whole table")
    void textFilter() throws IOException {
        String text = "fx00123";
        List<Long> expected = oracle.expect("1", "ALL", text, "FILE_ORDER", false);
        ResultPage page =
                store.query(
                        ResultQuery.firstPage(PsmQValueFilter.parse("1"))
                                .withCategory(Category.ALL)
                                .withText(text));
        assertEquals(expected.size(), page.matching());
        assertEquals(expected.subList(0, ResultQuery.DEFAULT_PAGE_SIZE), lines(page));
        assertEquals(1_000_000L, page.counts().total());
        List<Long> passingByScore =
                oracle.expectEnd("0.01", "PASSING", text, "SCORE", true, 50, false);
        assertEquals(
                passingByScore,
                lines(
                        store.query(
                                ResultQuery.firstPage(PsmQValueFilter.DEFAULT)
                                        .withText(text)
                                        .withSort(ResultSort.descending(ResultSort.Column.SCORE))
                                        .withPage(0, 50))));
    }

    private static List<Long> lines(ResultPage page) {
        return page.rows().stream().map(ResultRow::line).toList();
    }
}
