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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.cometgui.results.filtering.FilterCounts;
import org.cometgui.results.filtering.PsmQValueFilter;
import org.cometgui.results.filtering.QValueFilter;
import org.cometgui.results.parser.ResultRow;
import org.cometgui.results.testing.IndependentCounts;
import org.cometgui.results.testing.LargeFixture;
import org.cometgui.results.testing.ScratchFixtures;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The large fixture's 1 000 000-row PSM table (design decision P10-2) through the in-memory store,
 * opened once: counts equal the generator's manifest at all eight cutoffs, and every unknown kind
 * is in the unknown category in the generator's own numbers. Measured on the phase 10 host
 * (2026-10-08): opening takes about 2 s and holds about 670 MB of heap; a count about 0.03 s. Sorts
 * and text at this scale, and the heap budget, are the disk-backed store's tests (unit 3). Fails,
 * never skips, when the fixture is absent.
 */
class InMemoryLargeFixtureTest {

    private static LargeFixture fixture;
    private static ResultStore store;
    private static String sha256Before;

    @BeforeAll
    static void open() throws IOException {
        fixture = LargeFixture.locate();
        sha256Before = ScratchFixtures.sha256(fixture.psms());
        store = ResultStores.inMemory(fixture.psms(), TableKind.TARGET_PSMS);
    }

    @AfterAll
    static void close() throws IOException {
        if (store != null) {
            store.close();
        }
    }

    @Test
    @DisplayName("gate 3 at scale: counts equal the manifest at 0, 0.001, 0.005, 0.01 ... 1")
    void countsEqualManifest() throws IOException {
        assertEquals(1_000_000L, store.rowCount());
        assertEquals(8, fixture.cutoffs().size());
        for (String cutoff : fixture.cutoffs()) {
            IndependentCounts expected = fixture.expected(LargeFixture.PSMS, cutoff);
            assertEquals(
                    new FilterCounts(
                            expected.total(),
                            expected.passing(),
                            expected.failing(),
                            expected.unknown()),
                    store.counts(PsmQValueFilter.parse(cutoff)),
                    "at " + cutoff);
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
            long offset = 0;
            ResultPage page;
            do {
                page = store.query(unknown.withPage(offset, ResultQuery.MAX_PAGE_SIZE));
                for (ResultRow row : page.rows()) {
                    found.merge(row.qValue().text(), 1L, Long::sum);
                }
                offset += page.rows().size();
            } while (offset < page.matching());
            assertEquals(expected, found, "at " + cutoff);
            assertEquals(1478, page.matching());
        }
    }

    @Test
    @DisplayName("pages at scale: never above the limit, and the raw file unchanged")
    void pagesAndRawFile() throws IOException {
        ResultPage page =
                store.query(
                        ResultQuery.firstPage(PsmQValueFilter.DEFAULT)
                                .withCategory(Category.ALL)
                                .withPage(999_000, ResultQuery.MAX_PAGE_SIZE));
        assertEquals(1000, page.rows().size(), "the last thousand rows");
        assertEquals(1_000_001L, page.rows().get(999).line());
        assertTrue(page.matching() == 1_000_000L);
        assertEquals(sha256Before, ScratchFixtures.sha256(fixture.psms()));
    }
}
