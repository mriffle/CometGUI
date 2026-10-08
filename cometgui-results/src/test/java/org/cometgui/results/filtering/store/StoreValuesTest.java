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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.cometgui.results.filtering.FilterCounts;
import org.cometgui.results.filtering.PeptideQValueFilter;
import org.cometgui.results.filtering.PsmQValueFilter;
import org.cometgui.results.filtering.Visibility;
import org.cometgui.results.parser.QValue;
import org.cometgui.results.parser.ResultRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The store's value types: queries, pages, keys, categories, kinds, sorts and the text filter. */
class StoreValuesTest {

    private static final FilterCounts NONE = new FilterCounts(0, 0, 0, 0);

    /**
     * A {@code null} SpotBugs cannot constant-fold, for the tests that prove a method rejects one
     * (the project fixes {@code NP_NULL_PARAM_DEREF_ALL_TARGETS_DANGEROUS} at the call site, as
     * {@code org.cometgui.domain.testing.Nulls} does, rather than excluding it).
     */
    private static <T> T nothing(Class<T> type) {
        return type.cast(null);
    }

    /** Consumes a value a refusal test computes only to be refused. */
    private static void use(Object value) {
        assertTrue(value != null);
    }

    private static ResultRow row(String psmId, String peptide, String... proteins) {
        return new ResultRow(
                2, psmId, "1", 1.0, QValue.of("0.01"), "0", 0.0, peptide, List.of(proteins));
    }

    @Test
    @DisplayName("the first page: passing, no text, file order, offset 0, 200 rows")
    void firstPage() {
        ResultQuery query = ResultQuery.firstPage(PsmQValueFilter.DEFAULT);
        assertEquals(
                new ResultQuery(
                        PsmQValueFilter.DEFAULT,
                        Category.PASSING,
                        "",
                        new ResultSort(
                                ResultSort.Column.FILE_ORDER, ResultSort.Direction.ASCENDING),
                        0,
                        200),
                query);
        assertEquals(200, ResultQuery.DEFAULT_PAGE_SIZE);
        assertEquals(5000, ResultQuery.MAX_PAGE_SIZE);
    }

    @Test
    @DisplayName("each wither changes its own field and nothing else")
    void withers() {
        ResultQuery query = ResultQuery.firstPage(PsmQValueFilter.DEFAULT);
        ResultSort sort = ResultSort.descending(ResultSort.Column.PEP);
        PsmQValueFilter other = PsmQValueFilter.parse("0.05");
        assertEquals(
                new ResultQuery(other, Category.PASSING, "", ResultSort.FILE_ORDER, 0, 200),
                query.withFilter(other));
        assertEquals(
                new ResultQuery(
                        PsmQValueFilter.DEFAULT, Category.ALL, "", ResultSort.FILE_ORDER, 0, 200),
                query.withCategory(Category.ALL));
        assertEquals(
                new ResultQuery(
                        PsmQValueFilter.DEFAULT,
                        Category.PASSING,
                        "abc",
                        ResultSort.FILE_ORDER,
                        0,
                        200),
                query.withText(" abc\t"));
        assertEquals(
                new ResultQuery(PsmQValueFilter.DEFAULT, Category.PASSING, "", sort, 0, 200),
                query.withSort(sort));
        assertEquals(
                new ResultQuery(
                        PsmQValueFilter.DEFAULT, Category.PASSING, "", ResultSort.FILE_ORDER, 7, 9),
                query.withPage(7, 9));
    }

    @Test
    @DisplayName("a query's bounds: offset >= 0, limit 0..5000, text stripped, nothing null")
    void queryBounds() {
        ResultQuery query = ResultQuery.firstPage(PsmQValueFilter.DEFAULT);
        assertEquals(0, query.withPage(0, 0).limit());
        assertEquals(5000, query.withPage(0, 5000).limit());
        IllegalArgumentException tooMany =
                assertThrows(IllegalArgumentException.class, () -> use(query.withPage(0, 5001)));
        assertEquals(
                "a page holds from 0 to 5000 rows, but 5001 were asked for", tooMany.getMessage());
        assertThrows(IllegalArgumentException.class, () -> use(query.withPage(0, -1)));
        IllegalArgumentException negative =
                assertThrows(IllegalArgumentException.class, () -> use(query.withPage(-1, 10)));
        assertEquals("a page's offset is at least 0, but was -1", negative.getMessage());
        assertEquals("", query.withText("  \t ").text());
        assertThrows(NullPointerException.class, () -> use(query.withText(nothing(String.class))));
        assertThrows(
                NullPointerException.class, () -> use(query.withSort(nothing(ResultSort.class))));
        assertThrows(
                NullPointerException.class, () -> use(query.withCategory(nothing(Category.class))));
        assertThrows(
                NullPointerException.class,
                () -> use(query.withFilter(nothing(PsmQValueFilter.class))));
    }

    @Test
    @DisplayName("a page cannot claim more rows than match after its offset")
    void pageBounds() {
        ResultRow one = row("a", "K.A.R", "p");
        assertEquals(1, new ResultPage(List.of(one), 4, 5, NONE).rows().size());
        assertEquals(0, new ResultPage(List.of(), 9, 5, NONE).rows().size());
        assertThrows(
                IllegalArgumentException.class, () -> new ResultPage(List.of(one), 5, 5, NONE));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResultPage(List.of(one, one), 4, 5, NONE));
        assertThrows(IllegalArgumentException.class, () -> new ResultPage(List.of(), -1, 5, NONE));
        assertThrows(IllegalArgumentException.class, () -> new ResultPage(List.of(), 0, -1, NONE));
        assertThrows(NullPointerException.class, () -> new ResultPage(List.of(), 0, 0, null));
        IllegalArgumentException message =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new ResultPage(List.of(one), 0, 0, NONE));
        assertEquals(
                "1 rows at offset 0 cannot be a page of 0 matching rows", message.getMessage());
    }

    @Test
    @DisplayName("a row key is its line, at least 2, ordered by line")
    void rowKeys() {
        assertEquals(new RowKey(2), RowKey.of(row("a", "p")));
        assertEquals(2, RowKey.FIRST_ROW_LINE);
        IllegalArgumentException header =
                assertThrows(IllegalArgumentException.class, () -> new RowKey(1));
        assertEquals("a row's line is at least 2 (line 1 is the header): 1", header.getMessage());
        assertTrue(new RowKey(2).compareTo(new RowKey(3)) < 0);
        assertTrue(new RowKey(3).compareTo(new RowKey(2)) > 0);
        assertEquals(0, new RowKey(3).compareTo(new RowKey(3)));
    }

    @ParameterizedTest
    @CsvSource({
        "PASSING, true, false, false",
        "FAILING, false, true, false",
        "UNKNOWN_Q_VALUE, false, false, true",
        "ALL, true, true, true"
    })
    @DisplayName("each category includes exactly its own visibility; ALL includes all three")
    void categories(Category category, boolean passes, boolean fails, boolean unknown) {
        assertEquals(passes, category.includes(Visibility.PASSES));
        assertEquals(fails, category.includes(Visibility.FAILS));
        assertEquals(unknown, category.includes(Visibility.UNKNOWN_Q_VALUE));
        assertThrows(
                NullPointerException.class, () -> category.includes(nothing(Visibility.class)));
    }

    @Test
    @DisplayName("table kinds: PSM tables take the PSM filter, peptide tables the peptide filter")
    void tableKinds() {
        assertTrue(TableKind.TARGET_PSMS.isPsms() && !TableKind.TARGET_PSMS.isDecoy());
        assertTrue(TableKind.DECOY_PSMS.isPsms() && TableKind.DECOY_PSMS.isDecoy());
        assertTrue(!TableKind.TARGET_PEPTIDES.isPsms() && !TableKind.TARGET_PEPTIDES.isDecoy());
        assertTrue(!TableKind.DECOY_PEPTIDES.isPsms() && TableKind.DECOY_PEPTIDES.isDecoy());
        for (TableKind kind : TableKind.values()) {
            if (kind.isPsms()) {
                assertSame(PsmQValueFilter.DEFAULT, kind.check(PsmQValueFilter.DEFAULT));
                IllegalArgumentException refused =
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> kind.check(PeptideQValueFilter.DEFAULT));
                assertEquals(
                        "the "
                                + kind
                                + " table is filtered by the PSM q-value filter, not by"
                                + " PeptideQValueFilter(q <= 0.01)",
                        refused.getMessage());
            } else {
                assertSame(PeptideQValueFilter.DEFAULT, kind.check(PeptideQValueFilter.DEFAULT));
                IllegalArgumentException refused =
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> kind.check(PsmQValueFilter.DEFAULT));
                assertEquals(
                        "the "
                                + kind
                                + " table is filtered by the peptide q-value filter, not by"
                                + " PsmQValueFilter(q <= 0.01)",
                        refused.getMessage());
            }
            assertThrows(
                    NullPointerException.class, () -> kind.check(nothing(PsmQValueFilter.class)));
        }
    }

    @Test
    @DisplayName("sorts: ascending, descending, file order; nothing null")
    void sorts() {
        assertEquals(
                new ResultSort(ResultSort.Column.SCORE, ResultSort.Direction.ASCENDING),
                ResultSort.ascending(ResultSort.Column.SCORE));
        assertEquals(
                new ResultSort(ResultSort.Column.SCORE, ResultSort.Direction.DESCENDING),
                ResultSort.descending(ResultSort.Column.SCORE));
        assertEquals(ResultSort.ascending(ResultSort.Column.FILE_ORDER), ResultSort.FILE_ORDER);
        assertThrows(
                NullPointerException.class,
                () -> new ResultSort(null, ResultSort.Direction.ASCENDING));
        assertThrows(NullPointerException.class, () -> new ResultSort(ResultSort.Column.PEP, null));
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                "'', 'anything', true",
                "'', '', true",
                "'abc', 'abc', true",
                "'ABC', 'xabcx', true",
                "'abc', 'xAbCx', true",
                "'abcd', 'abc', false",
                "'bd', 'abcd', false",
                "'cd', 'abcd', true",
                "'ab', 'abcd', true",
                "'sample d', '/x/Sample D_1_2_1', true"
            },
            quoteCharacter = '\'')
    @DisplayName("contains, ignoring case: every position tried, ends included")
    void contains(String text, String field, boolean expected) {
        assertEquals(expected, TextFilter.contains(field, text));
    }

    @Test
    @DisplayName("a row matches on its PSMId, its peptide or any protein -- not on its numbers")
    void textMatchesFields() {
        ResultRow row = row("/runs/Sample_A_12_2_1", "K.PEPTIDEK.R", "sp|P1|ALPHA", "sp|P2|Beta");
        assertTrue(TextFilter.matches("sample_a", row));
        assertTrue(TextFilter.matches("peptidek", row));
        assertTrue(TextFilter.matches("alpha", row));
        assertTrue(TextFilter.matches("BETA", row), "the second protein too");
        assertTrue(TextFilter.matches("", row));
        assertFalse(TextFilter.matches("gamma", row));
        assertFalse(TextFilter.matches("0.01", row), "the q-value is not text");
        assertThrows(NullPointerException.class, () -> TextFilter.matches(null, row));
    }
}
