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

package org.cometgui.ui.viewmodel.results;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.results.filtering.store.Category;
import org.cometgui.results.filtering.store.ResultQuery;
import org.cometgui.results.filtering.store.ResultStores;
import org.cometgui.results.filtering.store.RowKey;
import org.cometgui.results.filtering.store.TableKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The paged table over the two scratch fixtures, which fail rather than skip when absent: Phase
 * 00's real K562 tables (counts pinned from {@code awk}, cometgui-results'
 * real-k562/PROVENANCE.txt) and the 1 000 000-row large fixture, opened through {@code
 * ResultStores.open} -- above the in-memory limit, so on disk -- with counts pinned from its
 * generator's manifest. On the large fixture the table never holds more than one page.
 */
class ResultTableScratchFixturesTest {

    @TempDir private Path index;

    private final TestExecutor background = new TestExecutor("background");

    private final TestExecutor ui = new TestExecutor("ui");

    private final DisplayFiltersViewModel filters = new DisplayFiltersViewModel();

    private final ResultTableViewModel table = new ResultTableViewModel(filters, background, ui);

    private void settle() {
        TestExecutor.settle(background, ui);
    }

    private List<String> counts() {
        return List.of(
                table.counts().total(),
                table.counts().passing(),
                table.counts().failing(),
                table.counts().unknownQValue());
    }

    @Test
    @DisplayName("the real K562 PSM and peptide tables: counts at 0, 0.005, 0.01 and 1 as awk's")
    void realK562() throws IOException {
        Path psmFile = ResultsFixtures.k562Psms();
        Path peptideFile = ResultsFixtures.k562Peptides();
        ResultTableViewModel peptides = new ResultTableViewModel(filters, background, ui);
        try (CountingStore psmStore =
                        new CountingStore(
                                ResultStores.open(
                                        psmFile,
                                        TableKind.TARGET_PSMS,
                                        index,
                                        new StreamingHashService()));
                CountingStore peptideStore =
                        new CountingStore(
                                ResultStores.open(
                                        peptideFile,
                                        TableKind.TARGET_PEPTIDES,
                                        index,
                                        new StreamingHashService()))) {
            table.show(
                    psmStore,
                    Map.of(
                            "20100614_Velos1_TaGe_SA_K562_3", "20100614_Velos1_TaGe_SA_K562_3.mzML",
                            "20100614_Velos1_TaGe_SA_K562_4",
                                    "20100614_Velos1_TaGe_SA_K562_4.mzML"));
            peptides.show(peptideStore, Map.of());
            settle();
            String[][] expected = {
                {"0", "3897", "0", "3897", "2985", "0", "2985"},
                {"0.005", "3897", "982", "2915", "2985", "567", "2418"},
                {"0.01", "3897", "1026", "2871", "2985", "603", "2382"},
                {"1", "3897", "3897", "0", "2985", "2985", "0"},
            };
            for (String[] row : expected) {
                filters.editPsmFilter(row[0]);
                filters.editPeptideFilter(row[0]);
                settle();
                assertEquals(List.of(row[1], row[2], row[3], "0"), counts(), "PSMs at " + row[0]);
                assertEquals(
                        List.of(row[4], row[5], row[6], "0"),
                        List.of(
                                peptides.counts().total(),
                                peptides.counts().passing(),
                                peptides.counts().failing(),
                                peptides.counts().unknownQValue()),
                        "peptides at " + row[0]);
                assertEquals(
                        IndependentCounts.of(psmFile, row[0]).passing(), table.page().matching());
            }
            assertTrue(table.page().rows().size() <= ResultTableViewModel.PAGE_SIZE);
            assertEquals(200, psmStore.mostRowsReturned(), "3897 rows, 200 at a time");
            ResultRowView first = table.page().rows().get(0);
            assertTrue(
                    first.sourceFile().startsWith("20100614_Velos1_TaGe_SA_K562_"),
                    first::sourceFile);
            assertFalse(first.scan().isEmpty());
            assertFalse(
                    table.columnsProperty().get().get(1).hideable(),
                    "two spectrum files: the source file column is mandatory");
        }
    }

    @Test
    @DisplayName(
            "the 1 000 000-row large fixture on disk: one page at a time, counts as its manifest,"
                    + " the selection followed")
    void largeFixture() throws IOException {
        Path file = ResultsFixtures.largePsms();
        try (CountingStore store =
                new CountingStore(
                        ResultStores.open(
                                file, TableKind.TARGET_PSMS, index, new StreamingHashService()))) {
            table.show(store, Map.of());
            settle();
            assertAll(
                    () -> assertEquals(ResultTableViewModel.PAGE_SIZE, table.page().rows().size()),
                    () -> assertEquals(219_777, table.page().matching()),
                    () -> assertEquals(List.of("1000000", "219777", "778745", "1478"), counts()),
                    () -> assertEquals(1099, table.page().pageCount()));
            String[][] manifest = {
                {"0", "5003", "993519"},
                {"0.005", "175162", "823360"},
                {"1", "998522", "0"},
                {"0.01", "219777", "778745"},
            };
            for (String[] row : manifest) {
                filters.editPsmFilter(row[0]);
                settle();
                assertEquals(List.of("1000000", row[1], row[2], "1478"), counts(), "at " + row[0]);
                assertEquals(Long.parseLong(row[1]), table.page().matching());
                assertTrue(table.page().rows().size() <= ResultTableViewModel.PAGE_SIZE);
            }
            assertFalse(filters.editPsmFilter("1.0001"));
            assertEquals(0, background.pending(), "refused: nothing asked");
            table.lastPage();
            settle();
            assertEquals(219_777 % 200, table.page().rows().size());
            assertEquals(
                    "Rows 219601 to 219777 of 219777 (page 1099 of 1099)", table.page().text());
            RowKey chosen = table.page().rows().get(100).key();
            table.select(chosen);
            table.sortBy(ResultsColumn.SCORE);
            table.sortBy(ResultsColumn.SCORE);
            settle();
            assertTrue(
                    table.page().rows().stream().anyMatch(row -> row.key().equals(chosen)),
                    "the view moved to the selected row's page under the new sort");
            assertTrue(
                    table.selectionStatusProperty().get().startsWith("1 row is selected: row "),
                    table.selectionStatusProperty()::get);
            table.setCategory(Category.UNKNOWN_Q_VALUE);
            settle();
            assertEquals(1478, table.page().matching());
            assertEquals("1478", table.counts().unknownQValue());
            assertTrue(table.selectionStatusProperty().get().contains("is not shown"));
            assertEquals(List.of(chosen), table.selectedProperty().get());
            assertTrue(
                    store.queries().stream()
                            .allMatch(q -> q.limit() == ResultTableViewModel.PAGE_SIZE),
                    "every query asks for one page");
            assertTrue(store.mostRowsReturned() <= ResultTableViewModel.PAGE_SIZE);
            assertTrue(ResultTableViewModel.PAGE_SIZE < ResultQuery.MAX_PAGE_SIZE);
        }
    }
}
