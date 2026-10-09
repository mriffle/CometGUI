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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.cometgui.results.filtering.FilterCounts;
import org.cometgui.results.filtering.store.Category;
import org.cometgui.results.filtering.store.ResultQuery;
import org.cometgui.results.filtering.store.ResultSort;
import org.cometgui.results.filtering.store.ResultStore;
import org.cometgui.results.filtering.store.ResultStores;
import org.cometgui.results.filtering.store.RowKey;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.ui.testing.Nulls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The paged table view-model over real stores opened on real and constructed Percolator tables:
 * what a page holds, counts equal to counts made independently in this file (no results class), the
 * categories, sort, the text filter, the selection kept by key across every change, copy, column
 * visibility, paging and stale answers. Every expected text is typed here.
 */
class ResultTableViewModelTest {

    /** The bases of {@code psms-shuffled.tsv} and the display names its run gives them. */
    static final Map<String, String> SHUFFLED_SOURCES =
            Map.of(
                    "/runs/r1/outputs/comet/sample_A", "sample_A.mzML",
                    "/runs/r1/outputs/comet/2026_10_08_run_2_1", "2026_10_08_run_2_1.mzXML",
                    "/runs/r1/outputs/comet/sample D", "sample D.mgf");

    private final TestExecutor background = new TestExecutor("background");

    private final TestExecutor ui = new TestExecutor("ui");

    private final DisplayFiltersViewModel filters = new DisplayFiltersViewModel();

    private final ResultTableViewModel table = new ResultTableViewModel(filters, background, ui);

    private final List<ResultStore> open = new ArrayList<>();

    @TempDir private Path work;

    @AfterEach
    void closeStores() throws IOException {
        for (ResultStore store : open) {
            store.close();
        }
    }

    private CountingStore store(Path file, TableKind kind) throws IOException {
        CountingStore store = new CountingStore(ResultStores.inMemory(file, kind));
        open.add(store);
        return store;
    }

    private void settle() {
        TestExecutor.settle(background, ui);
    }

    private List<Long> lines() {
        List<Long> lines = new ArrayList<>();
        for (ResultRowView row : table.page().rows()) {
            lines.add(row.key().line());
        }
        return lines;
    }

    private static FilterCounts counts(IndependentCounts expected) {
        return new FilterCounts(
                expected.total(), expected.passing(), expected.failing(), expected.unknown());
    }

    private CountingStore shuffled() throws IOException {
        CountingStore store =
                store(ResultsFixtures.copy("psms-shuffled.tsv"), TableKind.TARGET_PSMS);
        table.show(store, SHUFFLED_SOURCES);
        settle();
        return store;
    }

    /**
     * A constructed table of {@code rows} PSMs in one source file: row {@code i} (line {@code i +
     * 1}) has score {@code i} and q-value {@code (i mod 100) / 100}.
     */
    private Path numbered(int rows) throws IOException {
        StringBuilder text =
                new StringBuilder(
                        "PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds\n");
        for (int i = 1; i <= rows; i++) {
            text.append("/r/outputs/comet/run_A_")
                    .append(i)
                    .append("_2_1\t")
                    .append(i)
                    .append('\t')
                    .append(BigDecimal.valueOf(i % 100, 2).toPlainString())
                    .append("\t0.5\tK.PEP")
                    .append(i)
                    .append("K.R\tsp|P")
                    .append(i)
                    .append("|T\n");
        }
        Path file = work.resolve("numbered-" + rows + ".tsv");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file;
    }

    @Nested
    @DisplayName("what a page holds")
    class Page {

        @Test
        @DisplayName("the passing rows in file order, every cell as Percolator wrote it")
        void firstPage() throws IOException {
            assertEquals(TablePage.NONE, table.page());
            assertEquals("No table is open.", table.statusProperty().get());
            Path file = ResultsFixtures.copy("psms-shuffled.tsv");
            shuffled();
            assertAll(
                    () -> assertEquals(IndependentCounts.lines(file, "0.01", "passing"), lines()),
                    () -> assertEquals(List.of(3L, 4L, 5L, 8L, 11L, 13L, 14L, 20L, 21L), lines()),
                    () -> assertEquals("Rows 1 to 9 of 9 (page 1 of 1)", table.page().text()),
                    () -> assertEquals(1, table.page().pageNumber()),
                    () -> assertEquals(1, table.page().pageCount()),
                    () -> assertEquals(Category.PASSING, table.categoryProperty().get()),
                    () -> assertEquals(ResultSort.FILE_ORDER, table.sortProperty().get()),
                    () -> assertEquals("", table.statusProperty().get()),
                    () -> assertFalse(table.busyProperty().get()));
            ResultRowView line3 = table.page().rows().get(0);
            assertEquals(
                    new ResultRowView(
                            new RowKey(3),
                            "/runs/r1/outputs/comet/2026_10_08_run_2_1_15_3_1",
                            "2026_10_08_run_2_1.mzXML",
                            "15",
                            "3",
                            "R.MMAAK.L",
                            "sp|P10002|BETA_HUMAN, sp|P10003|GAMMA_HUMAN",
                            "3.1",
                            "0",
                            "1e-07"),
                    line3);
            ResultRowView line13 = table.page().rows().get(5);
            assertEquals(
                    List.of("sample_A.mzML", "60", "2", "-0.0", "1e-3", "0.001"),
                    List.of(
                            line13.sourceFile(),
                            line13.scan(),
                            line13.charge(),
                            line13.score(),
                            line13.qValue(),
                            line13.pep()),
                    "a q-value of 1e-3 and a score of -0.0 are shown as written");
            ResultRowView odd = table.page().rows().get(6);
            assertEquals(
                    List.of("odd_x_2_1", "", "", "0.01"),
                    List.of(odd.psmId(), odd.sourceFile(), odd.scan(), odd.qValue()),
                    "a PSMId not in SpecId shape has no source file, scan or charge");
            assertEquals("nan", table.page().rows().get(3).score());
            for (ResultsColumn column : ResultsColumn.values()) {
                assertFalse(line3.cell(column).isEmpty(), column::label);
            }
            assertEquals("1e-07", line3.cell(ResultsColumn.PEP));
            assertEquals("15", line3.cell(ResultsColumn.SCAN));
        }

        @Test
        @DisplayName("a base the run's inputs do not name is shown as the base itself")
        void unnamedBase() throws IOException {
            table.show(
                    store(ResultsFixtures.copy("psms-shuffled.tsv"), TableKind.TARGET_PSMS),
                    Map.of());
            settle();
            assertEquals(
                    "/runs/r1/outputs/comet/2026_10_08_run_2_1",
                    table.page().rows().get(0).sourceFile());
        }

        @Test
        @DisplayName(
                "never more than a page: 950 rows come 200 at a time, and no query asks for more")
        void paging() throws IOException {
            CountingStore store = store(numbered(950), TableKind.TARGET_PSMS);
            table.show(store, Map.of("/r/outputs/comet/run_A", "run_A.mzML"));
            settle();
            table.setCategory(Category.ALL);
            settle();
            List<Integer> sizes = new ArrayList<>();
            List<String> texts = new ArrayList<>();
            for (int page = 1; page <= 5; page++) {
                sizes.add(table.page().rows().size());
                texts.add(table.page().text());
                assertEquals(
                        page,
                        table.page().pageNumber(),
                        "the page shown, with the table's status: " + table.statusProperty().get());
                table.nextPage();
                settle();
            }
            assertAll(
                    () -> assertEquals(List.of(200, 200, 200, 200, 150), sizes),
                    () ->
                            assertEquals(
                                    List.of(
                                            "Rows 1 to 200 of 950 (page 1 of 5)",
                                            "Rows 201 to 400 of 950 (page 2 of 5)",
                                            "Rows 401 to 600 of 950 (page 3 of 5)",
                                            "Rows 601 to 800 of 950 (page 4 of 5)",
                                            "Rows 801 to 950 of 950 (page 5 of 5)"),
                                    texts),
                    () -> assertEquals(951L, table.page().rows().get(149).key().line()),
                    () -> assertEquals(200, store.mostRowsReturned()),
                    () ->
                            assertTrue(
                                    store.queries().stream()
                                            .allMatch(
                                                    q ->
                                                            q.limit()
                                                                    == ResultTableViewModel
                                                                            .PAGE_SIZE),
                                    "every query asks for one page"));
            int asked = store.queries().size();
            table.nextPage();
            assertEquals(0, background.pending(), "no page after the last");
            table.goToPage(99);
            assertEquals(0, background.pending(), "a page beyond the last is the last");
            table.previousPage();
            settle();
            assertEquals(4, table.page().pageNumber());
            table.firstPage();
            settle();
            assertEquals(1, table.page().pageNumber());
            table.previousPage();
            assertEquals(0, background.pending(), "no page before the first");
            table.lastPage();
            settle();
            assertEquals(5, table.page().pageNumber());
            table.goToPage(-3);
            settle();
            assertEquals(1, table.page().pageNumber());
            assertEquals(asked + 4, store.queries().size());
            assertEquals(200, ResultTableViewModel.PAGE_SIZE);
        }

        @Test
        @DisplayName("nothing matching is said so, with no page")
        void nothingMatches() throws IOException {
            shuffled();
            table.editText("no such protein");
            table.applyText();
            settle();
            assertEquals(
                    new TablePage(
                            List.of(),
                            0,
                            0,
                            0,
                            0,
                            "No row matches the q-value filter, the category and the text"
                                    + " filter."),
                    table.page());
            table.nextPage();
            table.lastPage();
            assertEquals(0, background.pending());
        }

        @Test
        @DisplayName("a page may not hold more than the page size")
        void pageBound() {
            List<ResultRowView> many = new ArrayList<>();
            for (int i = 0; i <= ResultTableViewModel.PAGE_SIZE; i++) {
                many.add(
                        new ResultRowView(
                                new RowKey(i + 2), "p", "", "", "", "K", "", "0", "0", "0"));
            }
            IllegalArgumentException refused =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> new TablePage(many, 0, 1000, 1, 5, "x"));
            assertEquals("a page holds at most 200 rows, not 201", refused.getMessage());
        }
    }

    @Nested
    @DisplayName("counts, filters and categories")
    class Counts {

        @Test
        @DisplayName(
                "counts equal independent counts at 0, 0.005, 0.01 and 1 after each filter edit,"
                        + " over constructed and real tables")
        void independentCounts() throws IOException {
            for (String name :
                    List.of("psms-shuffled.tsv", "psms-unknown-q.tsv", "real-3.07.1-psms.tsv")) {
                Path file = ResultsFixtures.copy(name);
                table.show(store(file, TableKind.TARGET_PSMS), Map.of());
                settle();
                for (String cutoff : List.of("0", "0.005", "0.01", "1", "0.01")) {
                    assertTrue(filters.editPsmFilter(cutoff));
                    settle();
                    IndependentCounts expected = IndependentCounts.of(file, cutoff);
                    assertEquals(
                            counts(expected),
                            table.counts().counts().orElseThrow(),
                            name + " at " + cutoff);
                    assertEquals(
                            expected.passing(), table.page().matching(), name + " at " + cutoff);
                    assertEquals(
                            List.of(
                                    Long.toString(expected.total()),
                                    Long.toString(expected.passing()),
                                    Long.toString(expected.failing()),
                                    Long.toString(expected.unknown())),
                            List.of(
                                    table.counts().total(),
                                    table.counts().passing(),
                                    table.counts().failing(),
                                    table.counts().unknownQValue()));
                }
            }
        }

        @Test
        @DisplayName("the hand counts of the constructed tables, typed, and the summary sentence")
        void handCounts() throws IOException {
            shuffled();
            assertEquals(
                    "23 target PSMs in total. At a q-value cutoff of 0.01 (a q-value equal to the"
                            + " cutoff passing): 9 passing, 7 failing, and 7 with an unknown"
                            + " q-value, which neither pass nor fail.",
                    table.counts().summary());
            for (String[] expected :
                    new String[][] {
                        {"0", "2", "14"},
                        {"0.005", "6", "10"},
                        {"0.01", "9", "7"},
                        {"0.05", "12", "4"},
                        {"1", "16", "0"}
                    }) {
                filters.editPsmFilter(expected[0]);
                settle();
                assertEquals(
                        List.of("23", expected[1], expected[2], "7"),
                        List.of(
                                table.counts().total(),
                                table.counts().passing(),
                                table.counts().failing(),
                                table.counts().unknownQValue()),
                        "CONSTRUCTED.txt's hand count at " + expected[0]);
            }
        }

        @Test
        @DisplayName("inclusive at exactly 0.01: both 0.01 rows pass, 0.0100001 does not")
        void inclusive() throws IOException {
            shuffled();
            assertTrue(lines().containsAll(List.of(4L, 14L)), () -> "lines " + lines());
            assertFalse(lines().contains(7L), "0.0100001 fails 0.01");
            assertTrue(lines().contains(8L), "0.00999999 passes 0.01");
            filters.editPsmFilter("0.00999999");
            settle();
            assertFalse(lines().contains(4L) || lines().contains(14L), () -> "lines " + lines());
            assertTrue(lines().contains(8L), "0.00999999 passes itself");
            filters.editPsmFilter("0.0100001");
            settle();
            assertTrue(lines().containsAll(List.of(4L, 7L, 8L, 14L)), () -> "lines " + lines());
        }

        @Test
        @DisplayName("refused text asks the store nothing and changes no count")
        void refused() throws IOException {
            CountingStore store = shuffled();
            int asked = store.queries().size();
            TableCounts before = table.counts();
            TablePage page = table.page();
            assertFalse(filters.editPsmFilter("1.5"));
            assertFalse(filters.editPsmFilter("abc"));
            assertFalse(filters.editPsmFilter("-0.01"));
            assertEquals(0, background.pending(), "nothing is asked");
            assertEquals(asked, store.queries().size());
            assertEquals(before, table.counts());
            assertEquals(page, table.page());
            assertTrue(filters.editPsmFilter("0.010"), "the same value");
            assertEquals(0, background.pending(), "the same value asks nothing either");
        }

        @Test
        @DisplayName(
                "the PSM filter re-queries PSM tables only, the peptide filter peptide tables"
                        + " only (AC-RES-03)")
        void independentFilters() throws IOException {
            CountingStore psms =
                    store(ResultsFixtures.copy("psms-shuffled.tsv"), TableKind.TARGET_PSMS);
            CountingStore peptides =
                    store(
                            ResultsFixtures.copy("real-3.07.1-peptides.tsv"),
                            TableKind.TARGET_PEPTIDES);
            ResultTableViewModel peptideTable = new ResultTableViewModel(filters, background, ui);
            table.show(psms, SHUFFLED_SOURCES);
            peptideTable.show(peptides, Map.of());
            settle();
            int psmAsked = psms.queries().size();
            int peptideAsked = peptides.queries().size();
            filters.editPeptideFilter("1");
            settle();
            assertEquals(psmAsked, psms.queries().size(), "the PSM table is not asked");
            assertEquals(peptideAsked + 1, peptides.queries().size());
            assertEquals("64", peptideTable.counts().passing());
            assertEquals("9", table.counts().passing());
            filters.editPsmFilter("0");
            settle();
            assertEquals(peptideAsked + 1, peptides.queries().size(), "the peptide table is not");
            assertEquals("2", table.counts().passing());
            assertEquals("64", peptideTable.counts().passing());
            assertEquals(
                    IndependentCounts.of(ResultsFixtures.copy("real-3.07.1-peptides.tsv"), "1")
                            .passing(),
                    peptideTable.page().matching());
            assertEquals(
                    List.of(
                            ResultsColumn.PEPTIDE,
                            ResultsColumn.PROTEINS,
                            ResultsColumn.SCORE,
                            ResultsColumn.Q_VALUE,
                            ResultsColumn.PEP),
                    peptideTable.columnsProperty().get().stream()
                            .map(ColumnState::column)
                            .toList());
            assertThrows(
                    IllegalArgumentException.class, () -> peptideTable.sortBy(ResultsColumn.SCAN));
        }

        @Test
        @DisplayName("the unknown category: its own count and rows, q-values as written")
        void unknownCategory() throws IOException {
            Path file = ResultsFixtures.copy("psms-shuffled.tsv");
            shuffled();
            table.setCategory(Category.UNKNOWN_Q_VALUE);
            settle();
            assertAll(
                    () -> assertEquals(IndependentCounts.lines(file, "0.01", "unknown"), lines()),
                    () -> assertEquals(7, table.page().matching()),
                    () -> assertEquals("7", table.counts().unknownQValue()),
                    () ->
                            assertEquals(
                                    List.of("NaN", "", "1.5", "-nan", "0,01", "-0.1", "Infinity"),
                                    table.page().rows().stream()
                                            .map(ResultRowView::qValue)
                                            .toList()),
                    () -> assertEquals(Category.UNKNOWN_Q_VALUE, table.categoryProperty().get()));
            filters.editPsmFilter("1");
            settle();
            assertEquals(7, table.page().matching(), "no cutoff makes an unknown q-value pass");
            table.setCategory(Category.FAILING);
            settle();
            assertEquals(0, table.page().matching());
            filters.editPsmFilter("0.01");
            settle();
            assertEquals(IndependentCounts.lines(file, "0.01", "failing"), lines());
            table.setCategory(Category.ALL);
            settle();
            assertEquals(23, table.page().rows().size());
            int pending = background.pending();
            table.setCategory(Category.ALL);
            assertEquals(pending, background.pending(), "the same category asks nothing");
        }
    }

    @Nested
    @DisplayName("sort and text")
    class SortAndText {

        @Test
        @DisplayName("a heading cycles ascending, descending, file order; ties in file order")
        void sort() throws IOException {
            shuffled();
            table.sortBy(ResultsColumn.SCORE);
            settle();
            assertEquals(ResultSort.ascending(ResultSort.Column.SCORE), table.sortProperty().get());
            assertEquals(List.of(13L, 20L, 21L, 4L, 5L, 14L, 11L, 3L, 8L), lines());
            table.sortBy(ResultsColumn.SCORE);
            settle();
            assertEquals(
                    ResultSort.descending(ResultSort.Column.SCORE), table.sortProperty().get());
            assertEquals(List.of(3L, 11L, 14L, 4L, 5L, 21L, 20L, 13L, 8L), lines());
            table.sortBy(ResultsColumn.SCORE);
            settle();
            assertEquals(ResultSort.FILE_ORDER, table.sortProperty().get());
            assertEquals(List.of(3L, 4L, 5L, 8L, 11L, 13L, 14L, 20L, 21L), lines());
            table.sortBy(ResultsColumn.Q_VALUE);
            table.sortBy(ResultsColumn.PEP);
            settle();
            assertEquals(ResultSort.ascending(ResultSort.Column.PEP), table.sortProperty().get());
        }

        @Test
        @DisplayName("typing asks nothing; the text applies on request, ignoring case")
        void text() throws IOException {
            CountingStore store = shuffled();
            int asked = store.queries().size();
            table.editText("b");
            table.editText("be");
            table.editText("beta");
            assertEquals(0, background.pending(), "no query per keystroke");
            assertEquals(asked, store.queries().size());
            assertTrue(table.textPendingProperty().get());
            assertEquals("beta", table.textDraftProperty().get());
            assertEquals("", table.appliedTextProperty().get());
            assertTrue(table.applyText());
            settle();
            assertEquals(List.of(3L), lines(), "BETA_HUMAN on a passing row");
            assertEquals("beta", table.appliedTextProperty().get());
            assertFalse(table.textPendingProperty().get());
            assertFalse(table.applyText(), "the same text again asks nothing");
            table.editText(" beta ");
            assertFalse(table.textPendingProperty().get(), "surrounding space is not text");
            table.setCategory(Category.ALL);
            settle();
            assertEquals(List.of(3L, 24L), lines());
            assertTrue(table.clearText());
            settle();
            assertEquals(23, table.page().matching());
            assertFalse(table.clearText(), "already clear");
        }
    }

    @Nested
    @DisplayName("selection, by key")
    class Selection {

        @Test
        @DisplayName("kept across filter, sort and category; hidden and said so; shown again")
        void keptAcrossChanges() throws IOException {
            shuffled();
            table.select(new RowKey(21));
            assertEquals(List.of(new RowKey(21)), table.selectedProperty().get());
            assertEquals(
                    "1 row is selected: row 9 of 9, on page 1.",
                    table.selectionStatusProperty().get());
            filters.editPsmFilter("0.005");
            settle();
            assertEquals(
                    "1 row is selected: row 6 of 6, on page 1.",
                    table.selectionStatusProperty().get());
            table.sortBy(ResultsColumn.SCORE);
            table.sortBy(ResultsColumn.SCORE);
            settle();
            assertEquals(List.of(new RowKey(21)), table.selectedProperty().get());
            assertEquals(
                    "1 row is selected: row " + (lines().indexOf(21L) + 1) + " of 6, on page 1.",
                    table.selectionStatusProperty().get());
            table.setCategory(Category.FAILING);
            settle();
            assertFalse(lines().contains(21L));
            assertEquals(List.of(new RowKey(21)), table.selectedProperty().get(), "still held");
            assertEquals(
                    "1 row is selected. The most recently selected, line 21 of the table file,"
                            + " does not match the current q-value filter, category or text"
                            + " filter, so it is not shown. It stays selected, and is shown again"
                            + " when it matches.",
                    table.selectionStatusProperty().get());
            table.setCategory(Category.ALL);
            settle();
            assertTrue(lines().contains(21L));
            assertEquals(
                    "1 row is selected: row " + (lines().indexOf(21L) + 1) + " of 23, on page 1.",
                    table.selectionStatusProperty().get());
            table.editText("PSI_HUMAN");
            table.applyText();
            settle();
            assertEquals(List.of(21L), lines());
            assertEquals(
                    "1 row is selected: row 1 of 1, on page 1.",
                    table.selectionStatusProperty().get());
        }

        @Test
        @DisplayName("the view moves to the page holding the selected row, under each change")
        void followsItsPage() throws IOException {
            CountingStore store = store(numbered(950), TableKind.TARGET_PSMS);
            table.show(store, Map.of());
            settle();
            table.setCategory(Category.ALL);
            settle();
            table.select(new RowKey(121)); // row i = 120: score 120, q 0.20
            assertEquals(
                    "1 row is selected: row 120 of 950, on page 1.",
                    table.selectionStatusProperty().get());
            table.sortBy(ResultsColumn.SCORE);
            table.sortBy(ResultsColumn.SCORE);
            settle();
            // descending score: rows 950..121 come first, so i = 120 is at position 950 - 120
            assertEquals(5, table.page().pageNumber());
            assertEquals(800, table.page().offset());
            assertTrue(lines().contains(121L));
            assertEquals(
                    "1 row is selected: row 831 of 950, on page 5.",
                    table.selectionStatusProperty().get());
            table.firstPage();
            settle();
            assertEquals(1, table.page().pageNumber(), "paging moves only the page");
            assertEquals(
                    "1 row is selected: row 831 of 950, on page 5.",
                    table.selectionStatusProperty().get());
            table.setCategory(Category.PASSING); // at 0.01, q 0.20 fails
            settle();
            assertEquals(1, table.page().pageNumber());
            assertTrue(table.selectionStatusProperty().get().contains("is not shown"));
            filters.editPsmFilter("0.2"); // inclusive: q 0.20 passes
            settle();
            long before = 0;
            long passing = 0;
            for (int i = 1; i <= 950; i++) {
                if (i % 100 <= 20) {
                    passing++;
                    if (i > 120) {
                        before++;
                    }
                }
            }
            assertEquals(passing, table.page().matching());
            assertEquals(before / 200 + 1, table.page().pageNumber());
            assertTrue(lines().contains(121L));
            assertEquals(
                    "1 row is selected: row "
                            + (before + 1)
                            + " of "
                            + passing
                            + ", on page "
                            + (before / 200 + 1)
                            + ".",
                    table.selectionStatusProperty().get());
            assertEquals(List.of(new RowKey(121)), store.positionLookups().subList(0, 1));
        }

        @Test
        @DisplayName("several rows by key; toggling; the anchor; nothing selected")
        void several() throws IOException {
            shuffled();
            table.select(new RowKey(3));
            table.toggle(new RowKey(5));
            table.toggle(new RowKey(13));
            assertEquals(
                    List.of(new RowKey(3), new RowKey(5), new RowKey(13)),
                    table.selectedProperty().get());
            assertEquals(
                    "3 rows are selected; the most recently selected is row 6 of 9, on page 1.",
                    table.selectionStatusProperty().get());
            table.toggle(new RowKey(13));
            assertEquals(
                    "2 rows are selected; the most recently selected is row 3 of 9, on page 1.",
                    table.selectionStatusProperty().get());
            table.toggle(new RowKey(3));
            assertEquals(List.of(new RowKey(5)), table.selectedProperty().get());
            table.toggle(new RowKey(5));
            assertEquals(List.of(), table.selectedProperty().get());
            assertEquals("No row is selected.", table.selectionStatusProperty().get());
            table.select(new RowKey(3));
            table.toggle(new RowKey(4));
            table.select(new RowKey(5));
            assertEquals(
                    List.of(new RowKey(5)),
                    table.selectedProperty().get(),
                    "select replaces the selection");
            table.clearSelection();
            assertEquals("No row is selected.", table.selectionStatusProperty().get());
            IllegalArgumentException refused =
                    assertThrows(IllegalArgumentException.class, () -> table.select(new RowKey(2)));
            assertEquals(
                    "no row of the page shown is on line 2 of the table file",
                    refused.getMessage());
            assertThrows(IllegalArgumentException.class, () -> table.toggle(new RowKey(99)));
        }

        @Test
        @DisplayName("an anchor deselected while the next is on another page: count only")
        void anchorElsewhere() throws IOException {
            table.show(store(numbered(950), TableKind.TARGET_PSMS), Map.of());
            settle();
            table.setCategory(Category.ALL);
            settle();
            table.select(new RowKey(2));
            table.nextPage();
            settle();
            table.toggle(new RowKey(300));
            table.toggle(new RowKey(300));
            assertEquals("1 row is selected.", table.selectionStatusProperty().get());
            table.sortBy(ResultsColumn.PSM_ID);
            settle();
            assertTrue(table.selectionStatusProperty().get().startsWith("1 row is selected: row "));
        }
    }

    @Nested
    @DisplayName("copy and columns")
    class CopyAndColumns {

        @Test
        @DisplayName("the selected rows on the page as tab-separated text, shown columns only")
        void copy() throws IOException {
            shuffled();
            assertEquals(
                    new CopyOutcome("", 0, "Nothing was copied: no row is selected."),
                    table.copy());
            table.select(new RowKey(13));
            table.toggle(new RowKey(3));
            CopyOutcome all = table.copy();
            assertEquals(
                    "PSMId\tSource file\tScan\tCharge\tPeptide\tProteins\tScore\tq-value\tPEP\n"
                            + "/runs/r1/outputs/comet/2026_10_08_run_2_1_15_3_1"
                            + "\t2026_10_08_run_2_1.mzXML\t15\t3\tR.MMAAK.L"
                            + "\tsp|P10002|BETA_HUMAN, sp|P10003|GAMMA_HUMAN\t3.1\t0\t1e-07\n"
                            + "/runs/r1/outputs/comet/sample_A_60_2_1\tsample_A.mzML\t60\t2"
                            + "\tK.M[15.9949]KK.R\tsp|P10013|NU_HUMAN\t-0.0\t1e-3\t0.001\n",
                    all.text(),
                    "page order, cells as shown");
            assertEquals(2, all.rowsCopied());
            assertEquals("Copied 2 rows with 9 columns as tab-separated text.", all.message());
            assertTrue(table.setColumnVisible(ResultsColumn.PROTEINS, false));
            assertTrue(table.setColumnVisible(ResultsColumn.PSM_ID, false));
            table.setCategory(Category.FAILING);
            settle();
            CopyOutcome none = table.copy();
            assertEquals(
                    new CopyOutcome(
                            "",
                            0,
                            "Nothing was copied: no selected row is shown on this page (2 selected"
                                    + " rows are elsewhere)."),
                    none);
            table.setCategory(Category.ALL);
            settle();
            table.toggle(new RowKey(2));
            filters.editPsmFilter("0.01");
            table.setCategory(Category.PASSING);
            settle();
            CopyOutcome some = table.copy();
            assertEquals(
                    "Source file\tScan\tCharge\tPeptide\tScore\tq-value\tPEP\n"
                            + "2026_10_08_run_2_1.mzXML\t15\t3\tR.MMAAK.L\t3.1\t0\t1e-07\n"
                            + "sample_A.mzML\t60\t2\tK.M[15.9949]KK.R\t-0.0\t1e-3\t0.001\n",
                    some.text());
            assertEquals(
                    "Copied 2 rows with 7 columns as tab-separated text. 1 selected row is not on"
                            + " this page and was not copied.",
                    some.message());
        }

        @Test
        @DisplayName(
                "the source-file column is mandatory with several spectrum files, hideable with"
                        + " one")
        void columns() throws IOException {
            shuffled();
            ColumnState source = table.columnsProperty().get().get(1);
            assertEquals(new ColumnState(ResultsColumn.SOURCE_FILE, true, false), source);
            assertEquals("Source file", source.label());
            assertFalse(table.setColumnVisible(ResultsColumn.SOURCE_FILE, false));
            assertEquals(
                    "The source file column cannot be hidden: this run has 3 spectrum files, and"
                            + " without it a PSM's spectrum file is not shown.",
                    table.columnStatusProperty().get());
            assertTrue(table.columnsProperty().get().get(1).visible());
            assertTrue(table.setColumnVisible(ResultsColumn.SCAN, false));
            assertEquals("", table.columnStatusProperty().get());
            assertEquals(
                    new ColumnState(ResultsColumn.SCAN, false, true),
                    table.columnsProperty().get().get(2));
            assertTrue(table.setColumnVisible(ResultsColumn.SCAN, true));
            assertTrue(table.columnsProperty().get().get(2).visible());
            assertEquals(0, background.pending(), "visibility asks the store nothing");

            table.show(
                    store(ResultsFixtures.copy("psms-shuffled.tsv"), TableKind.TARGET_PSMS),
                    Map.of("/runs/r1/outputs/comet/sample_A", "only.mzML"));
            settle();
            assertEquals(
                    new ColumnState(ResultsColumn.SOURCE_FILE, true, true),
                    table.columnsProperty().get().get(1));
            assertTrue(table.setColumnVisible(ResultsColumn.SOURCE_FILE, false));
            assertFalse(table.columnsProperty().get().get(1).visible());

            // Two spectrum files -- the smallest multi-file run, and the real K562 run's shape --
            // already make the column mandatory (orchestrator repair: a "> 2" rule stayed green).
            table.show(
                    store(ResultsFixtures.copy("psms-shuffled.tsv"), TableKind.TARGET_PSMS),
                    Map.of(
                            "/runs/r1/outputs/comet/sample_A", "a.mzML",
                            "/runs/r1/outputs/comet/sample_B", "b.mzML"));
            settle();
            assertEquals(
                    new ColumnState(ResultsColumn.SOURCE_FILE, true, false),
                    table.columnsProperty().get().get(1));
            assertFalse(table.setColumnVisible(ResultsColumn.SOURCE_FILE, false));
            assertTrue(table.columnsProperty().get().get(1).visible());
        }
    }

    @Nested
    @DisplayName("threads and lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("the store is asked only on the background executor, answers applied on ui")
        void threads() throws IOException {
            CountingStore store =
                    store(ResultsFixtures.copy("psms-shuffled.tsv"), TableKind.TARGET_PSMS);
            table.show(store, SHUFFLED_SOURCES);
            assertEquals(List.of(), store.queries(), "nothing asked on the calling thread");
            assertTrue(table.busyProperty().get());
            assertEquals("Reading the table.", table.statusProperty().get());
            background.drain();
            assertEquals(1, store.queries().size());
            assertEquals(TablePage.NONE, table.page(), "nothing applied before ui runs");
            ui.drain();
            assertEquals(9, table.pageProperty().get().rows().size());
            assertEquals("9", table.countsProperty().get().passing());
            assertFalse(table.busyProperty().get());
            ResultQuery asked = store.queries().get(0);
            assertEquals(
                    new ResultQuery(
                            filters.filters().psm(),
                            Category.PASSING,
                            "",
                            ResultSort.FILE_ORDER,
                            0,
                            200),
                    asked);
        }

        @Test
        @DisplayName("an older answer arriving after a newer one is dropped")
        void staleDropped() throws IOException {
            shuffled();
            table.setCategory(Category.FAILING);
            table.setCategory(Category.ALL);
            assertEquals(2, background.pending());
            background.runLast(); // the newer question, ALL, answers first
            ui.drain();
            assertEquals(23, table.page().rows().size());
            background.runFirst(); // then the older, FAILING
            ui.drain();
            assertEquals(23, table.page().rows().size(), "the FAILING answer was dropped");
            assertEquals(Category.ALL, table.categoryProperty().get());
            filters.editPsmFilter("0");
            table.show(
                    store(ResultsFixtures.copy("psms-unknown-q.tsv"), TableKind.TARGET_PSMS),
                    Map.of());
            background.runLast();
            ui.drain();
            settle();
            assertEquals("15", table.counts().total(), "the old table's answer was dropped");
            assertEquals(List.of(9L), lines(), "c08, q-value 0, passes 0");
        }

        @Test
        @DisplayName("showing another table starts it afresh: category, sort, text, selection")
        void showResets() throws IOException {
            shuffled();
            table.setCategory(Category.FAILING);
            table.sortBy(ResultsColumn.SCORE);
            table.editText("beta");
            table.applyText();
            settle();
            table.select(table.page().rows().get(0).key());
            table.editText("gamma");
            table.setColumnVisible(ResultsColumn.SOURCE_FILE, false);
            assertFalse(table.columnStatusProperty().get().isEmpty());
            table.show(
                    store(ResultsFixtures.copy("psms-unknown-q.tsv"), TableKind.TARGET_PSMS),
                    Map.of());
            assertAll(
                    () -> assertEquals(Category.PASSING, table.categoryProperty().get()),
                    () -> assertEquals(ResultSort.FILE_ORDER, table.sortProperty().get()),
                    () -> assertEquals("", table.textDraftProperty().get()),
                    () -> assertEquals("", table.appliedTextProperty().get()),
                    () -> assertEquals("", table.columnStatusProperty().get()),
                    () -> assertEquals(List.of(), table.selectedProperty().get()),
                    () -> assertEquals(TablePage.NONE, table.page(), "nothing before it answers"),
                    () -> assertEquals(TableCounts.NONE, table.counts()),
                    () -> assertTrue(table.busyProperty().get()));
            settle();
            assertEquals(List.of(2L, 3L, 9L, 13L, 14L), lines(), "c01 c02 c08 c12 c13");
        }

        @Test
        @DisplayName("an answer for a table shown before a clear is dropped after it")
        void staleAcrossClear() throws IOException {
            table.show(
                    store(ResultsFixtures.copy("psms-shuffled.tsv"), TableKind.TARGET_PSMS),
                    SHUFFLED_SOURCES);
            table.clear();
            table.show(
                    store(ResultsFixtures.copy("psms-unknown-q.tsv"), TableKind.TARGET_PSMS),
                    Map.of());
            background.runLast();
            ui.drain();
            background.runFirst();
            ui.drain();
            assertEquals("15", table.counts().total(), "the first table's answer was dropped");
        }

        @Test
        @DisplayName("399 rows fill two pages, 400 two, 401 three")
        void pageCount() throws IOException {
            for (int[] expected : new int[][] {{399, 2}, {400, 2}, {401, 3}, {199, 1}}) {
                table.show(store(numbered(expected[0]), TableKind.TARGET_PSMS), Map.of());
                settle();
                table.setCategory(Category.ALL);
                settle();
                assertEquals(expected[1], table.page().pageCount(), expected[0] + " rows");
            }
            assertEquals("Rows 1 to 199 of 199 (page 1 of 1)", table.page().text());
        }

        @Test
        @DisplayName("a store that cannot answer is said so")
        void failure() throws IOException {
            CountingStore store = shuffled();
            table.select(new RowKey(3));
            store.fail(new IOException("the index is damaged"));
            table.setCategory(Category.ALL);
            settle();
            assertAll(
                    () ->
                            assertEquals(
                                    "The table could not be read: the index is damaged",
                                    table.statusProperty().get()),
                    () -> assertEquals(TablePage.NONE, table.page()),
                    () ->
                            assertEquals(
                                    "The counts could not be read: the index is damaged",
                                    table.counts().summary()),
                    () -> assertFalse(table.busyProperty().get()),
                    () ->
                            assertEquals(
                                    "1 row is selected.", table.selectionStatusProperty().get()));
            store.fail(null);
            ResultStore closed =
                    ResultStores.inMemory(
                            ResultsFixtures.copy("psms-unknown-q.tsv"), TableKind.TARGET_PSMS);
            table.show(closed, Map.of());
            closed.close(); // closed while its first page is being asked for
            settle();
            assertTrue(
                    table.statusProperty().get().startsWith("The table could not be read: "),
                    table.statusProperty()::get);
        }

        @Test
        @DisplayName("a page above the page size is refused and said so, never shown")
        void oversizedPage() throws IOException {
            CountingStore store = store(numbered(950), TableKind.TARGET_PSMS);
            store.widen();
            table.show(store, Map.of());
            table.setCategory(Category.ALL);
            settle();
            assertEquals(TablePage.NONE, table.page());
            assertEquals(
                    "The table could not be read: a page holds at most 200 rows, not 950",
                    table.statusProperty().get());
        }

        @Test
        @DisplayName("clear shows nothing and drops what was pending")
        void clear() throws IOException {
            shuffled();
            table.select(new RowKey(3));
            table.editText("beta");
            table.applyText();
            table.sortBy(ResultsColumn.SCORE);
            table.setColumnVisible(ResultsColumn.SOURCE_FILE, false);
            settle();
            table.editText("gamma");
            table.setCategory(Category.ALL);
            assertTrue(table.busyProperty().get());
            table.clear();
            settle();
            assertAll(
                    () -> assertEquals(TablePage.NONE, table.page()),
                    () -> assertEquals(TableCounts.NONE, table.counts()),
                    () -> assertEquals(List.of(), table.columnsProperty().get()),
                    () -> assertEquals(List.of(), table.selectedProperty().get()),
                    () -> assertEquals(Category.PASSING, table.categoryProperty().get()),
                    () -> assertEquals("No table is open.", table.statusProperty().get()),
                    () -> assertEquals("", table.textDraftProperty().get()),
                    () -> assertEquals("", table.appliedTextProperty().get()),
                    () -> assertFalse(table.textPendingProperty().get()),
                    () -> assertEquals(ResultSort.FILE_ORDER, table.sortProperty().get()),
                    () -> assertEquals("", table.columnStatusProperty().get()),
                    () -> assertFalse(table.busyProperty().get()),
                    () ->
                            assertEquals(
                                    "No row is selected.", table.selectionStatusProperty().get()));
            table.setCategory(Category.ALL);
            table.sortBy(ResultsColumn.SCORE);
            table.nextPage();
            filters.editPsmFilter("0.5");
            assertFalse(table.applyText());
            assertEquals(0, background.pending(), "nothing to ask with no table");
            assertThrows(
                    NullPointerException.class,
                    () -> table.show(Nulls.of(ResultStore.class), Map.of()));
        }
    }
}
