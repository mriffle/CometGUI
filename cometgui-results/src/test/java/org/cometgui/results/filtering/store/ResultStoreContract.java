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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.cometgui.results.filtering.FilterCounts;
import org.cometgui.results.filtering.PeptideQValueFilter;
import org.cometgui.results.filtering.PsmQValueFilter;
import org.cometgui.results.filtering.QValueFilter;
import org.cometgui.results.parser.PercolatorOutputException;
import org.cometgui.results.parser.ResultRow;
import org.cometgui.results.testing.Fixtures;
import org.cometgui.results.testing.IndependentCounter;
import org.cometgui.results.testing.IndependentCounts;
import org.cometgui.results.testing.RealK562;
import org.cometgui.results.testing.ScratchFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The {@link ResultStore} contract, for every implementation: subclass it, implement {@link #open},
 * and every test here runs against that store. Phase 10 unit 2 runs it on the in-memory store; unit
 * 3 runs it, unchanged, on the disk-backed one.
 *
 * <p>Every expected number and order comes from outside the code under test: {@code awk} counts
 * pinned in {@link RealK562} for the real K562 output, hand counts typed from {@code
 * constructed/CONSTRUCTED.txt}, {@link IndependentCounter} (itself checked against {@code awk}) for
 * the checked-in Percolator tables, and {@link StoreOracle} for orders and text matches. Fixtures:
 * the twelve checked-in real tables (Percolator 3.06.5, 3.07.1, 3.09), the eight K562 tables (fail,
 * never skip, when absent), the constructed unknown-q-value table and the constructed shuffled
 * table, whose rows are deliberately in neither score nor q-value order.
 *
 * <p>Gate items served at the model level: 1 (inclusive at exactly the cutoff), 3 (counts equal to
 * independent counts at 0, 0.005, 0.01 and 1), 4 (raw files byte-identical) and 8 (every unknown
 * q-value in its own category, never passing or failing).
 */
abstract class ResultStoreContract {

    /** The cutoffs every fixture is counted at: the gate's four and more. */
    static final List<String> GATE_CUTOFFS = List.of("0", "0.005", "0.01", "1");

    static final String SHUFFLED = "constructed/psms-shuffled.tsv";
    static final String SHUFFLED_SHA256 =
            "3754547ea1ca3ff35b67913a8249b98831e924c0a84029c2a9769fc581a6f5d4";
    static final String UNKNOWN_Q = "constructed/psms-unknown-q.tsv";
    static final String UNKNOWN_Q_SHA256 =
            "ea58b3b63f9031bf53b5675d117b1e7203137a0ebd2399c9def824334267b6c7";

    /** The checked-in real tables and their SHA-256s, as {@code real/PROVENANCE.txt} records. */
    static final Map<String, String> CHECKED_IN = new LinkedHashMap<>();

    static {
        String v3071Targets = "6e782dd7bbba9f16bce1c6556bc86032e7a9804b6494694db61573d88340ed53";
        String v3071Decoys = "3f9557b82119a4f9900e5964c762dd9ce504de508afdb5be67dfdcca465259e4";
        String v3065Targets = "848e26e570a2c5f736be9e00377adef43db1677796b9efa9dfc2221bb9430852";
        String v3065Decoys = "2f60bf17a274b6e9f95860a8c635540938bd17fcf492e159fedd13722b52ec43";
        String v309Targets = "44aa04692c21aa47742f406d3da23adfc032d5fba363d07aecc9aa1f2379d07b";
        String v309Decoys = "9074109fa81a2ea2362de7d69894f0b660a0204baefc9f0f13172fac29d0b36c";
        for (String[] version :
                new String[][] {
                    {"percolator-3.07.1", v3071Targets, v3071Decoys},
                    {"percolator-3.06.5", v3065Targets, v3065Decoys},
                    {"percolator-3.09", v309Targets, v309Decoys}
                }) {
            CHECKED_IN.put("real/" + version[0] + "/psms.tsv", version[1]);
            CHECKED_IN.put("real/" + version[0] + "/peptides.tsv", version[1]);
            CHECKED_IN.put("real/" + version[0] + "/decoy-psms.tsv", version[2]);
            CHECKED_IN.put("real/" + version[0] + "/decoy-peptides.tsv", version[2]);
        }
    }

    @TempDir private Path work;

    /**
     * This test's scratch directory, emptied by JUnit after each test.
     *
     * @return the directory
     */
    protected Path work() {
        return work;
    }

    /**
     * Opens a store on a raw table.
     *
     * @param table the raw table
     * @param kind which table it is
     * @param workDirectory an empty directory the store may write into (an index, for instance);
     *     never the raw table's
     * @return the open store
     * @throws IOException if the store refuses the table
     */
    protected abstract ResultStore open(Path table, TableKind kind, Path workDirectory)
            throws IOException;

    private ResultStore open(Fixture fixture) throws IOException {
        return open(fixture.path().get(), fixture.kind(), work);
    }

    /* ---------------------------------------------------------------- fixtures */

    /**
     * One table under test and its independent counts.
     *
     * @param name a readable name
     * @param path verifies the file's SHA-256 and returns it
     * @param kind which table it is
     * @param expected independent counts by cutoff text
     */
    record Fixture(
            String name,
            Supplier<Path> path,
            TableKind kind,
            Map<String, IndependentCounts> expected) {

        @Override
        public String toString() {
            return name;
        }

        QValueFilter filter(String cutoff) {
            return kind.isPsms()
                    ? PsmQValueFilter.parse(cutoff)
                    : PeptideQValueFilter.parse(cutoff);
        }
    }

    static Fixture shuffled() {
        Map<String, IndependentCounts> hand = new LinkedHashMap<>();
        hand.put("0", new IndependentCounts(23, 2, 14, 7));
        hand.put("0.005", new IndependentCounts(23, 6, 10, 7));
        hand.put("0.01", new IndependentCounts(23, 9, 7, 7));
        hand.put("0.05", new IndependentCounts(23, 12, 4, 7));
        hand.put("1", new IndependentCounts(23, 16, 0, 7));
        return new Fixture(
                SHUFFLED,
                () -> Fixtures.verified(SHUFFLED, SHUFFLED_SHA256),
                TableKind.TARGET_PSMS,
                hand);
    }

    static Fixture unknownQ() {
        Map<String, IndependentCounts> hand = new LinkedHashMap<>();
        hand.put("0", new IndependentCounts(15, 1, 6, 8));
        hand.put("0.005", new IndependentCounts(15, 3, 4, 8));
        hand.put("0.01", new IndependentCounts(15, 5, 2, 8));
        hand.put("0.05", new IndependentCounts(15, 6, 1, 8));
        hand.put("1", new IndependentCounts(15, 7, 0, 8));
        return new Fixture(
                UNKNOWN_Q,
                () -> Fixtures.verified(UNKNOWN_Q, UNKNOWN_Q_SHA256),
                TableKind.TARGET_PSMS,
                hand);
    }

    static Fixture k562(RealK562.Table table) {
        String name = table.name();
        TableKind kind =
                name.contains("DECOY")
                        ? name.contains("PSMS") ? TableKind.DECOY_PSMS : TableKind.DECOY_PEPTIDES
                        : name.contains("PSMS") ? TableKind.TARGET_PSMS : TableKind.TARGET_PEPTIDES;
        return new Fixture("k562/" + table.relative(), table::path, kind, table.awkCounts());
    }

    static Fixture checkedIn(String relative) throws IOException {
        TableKind kind =
                relative.contains("decoy-psms")
                        ? TableKind.DECOY_PSMS
                        : relative.contains("decoy-peptides")
                                ? TableKind.DECOY_PEPTIDES
                                : relative.contains("psms")
                                        ? TableKind.TARGET_PSMS
                                        : TableKind.TARGET_PEPTIDES;
        Path path = Fixtures.verified(relative, CHECKED_IN.get(relative));
        List<String> cutoffs = List.of("0", "0.005", "0.01", "0.0588235", "0.166667", "1");
        IndependentCounter.Tally tally = IndependentCounter.count(path, cutoffs);
        return new Fixture(relative, () -> path, kind, tally.byCutoff());
    }

    static Stream<Fixture> fixtures() throws IOException {
        List<Fixture> all = new ArrayList<>();
        all.add(shuffled());
        all.add(unknownQ());
        for (String relative : CHECKED_IN.keySet()) {
            all.add(checkedIn(relative));
        }
        for (RealK562.Table table : RealK562.Table.values()) {
            all.add(k562(table));
        }
        return all.stream();
    }

    static Stream<Arguments> fixturesAndCutoffs() throws IOException {
        return fixtures()
                .flatMap(
                        fixture ->
                                fixture.expected().keySet().stream()
                                        .map(cutoff -> Arguments.of(fixture, cutoff)));
    }

    static Stream<Arguments> sortsOnShuffledAndK562() {
        List<Arguments> all = new ArrayList<>();
        for (Fixture fixture :
                List.of(
                        shuffled(),
                        unknownQ(),
                        k562(RealK562.Table.V3071_TARGET_PSMS),
                        k562(RealK562.Table.V309_DECOY_PEPTIDES))) {
            for (ResultSort.Column column : ResultSort.Column.values()) {
                for (ResultSort.Direction direction : ResultSort.Direction.values()) {
                    all.add(Arguments.of(fixture, new ResultSort(column, direction)));
                }
            }
        }
        return all.stream();
    }

    /* ---------------------------------------------------------------- helpers */

    static FilterCounts asFilterCounts(IndependentCounts counts) {
        return new FilterCounts(
                counts.total(), counts.passing(), counts.failing(), counts.unknown());
    }

    /** Every matching row's line, page by page, checking each page's own promises on the way. */
    static List<Long> allLines(ResultStore store, ResultQuery query, int pageSize)
            throws IOException {
        List<Long> lines = new ArrayList<>();
        long offset = 0;
        while (true) {
            ResultPage page = store.query(query.withPage(offset, pageSize));
            assertEquals(offset, page.offset(), "the page says where it starts");
            assertTrue(page.rows().size() <= pageSize, "a page never holds more than its limit");
            for (ResultRow row : page.rows()) {
                lines.add(row.line());
            }
            assertFalse(
                    page.rows().isEmpty() && offset < page.matching(),
                    "a page before the end holds rows (offset " + offset + ")");
            offset += page.rows().size();
            if (offset >= page.matching()) {
                assertEquals(page.matching(), lines.size(), "the pages hold every matching row");
                return lines;
            }
            assertEquals(pageSize, page.rows().size(), "only the last page is short");
        }
    }

    static ResultQuery all(QValueFilter filter) {
        return ResultQuery.firstPage(filter).withCategory(Category.ALL);
    }

    /* ---------------------------------------------------------------- gate 3: counts */

    @ParameterizedTest(name = "{0} at {1}")
    @MethodSource("fixturesAndCutoffs")
    @DisplayName(
            "gate 3: counts equal the independent counts (awk, hand, IndependentCounter) at every"
                    + " cutoff, in counts() and in every page")
    void countsEqualIndependentCounts(Fixture fixture, String cutoff) throws IOException {
        FilterCounts expected = asFilterCounts(fixture.expected().get(cutoff));
        try (ResultStore store = open(fixture)) {
            assertEquals(expected.total(), store.rowCount(), "row count");
            QValueFilter filter = fixture.filter(cutoff);
            assertEquals(expected, store.counts(filter), "counts()");
            for (Category category : Category.values()) {
                ResultPage page = store.query(ResultQuery.firstPage(filter).withCategory(category));
                assertEquals(expected, page.counts(), "page counts under " + category);
                long inCategory =
                        switch (category) {
                            case PASSING -> expected.passing();
                            case FAILING -> expected.failing();
                            case UNKNOWN_Q_VALUE -> expected.unknownQValue();
                            case ALL -> expected.total();
                        };
                assertEquals(inCategory, page.matching(), category + " matches");
            }
        }
    }

    @Test
    @DisplayName("the gate's own cutoffs 0, 0.005, 0.01 and 1 are counted on every fixture")
    void gateCutoffsCovered() throws IOException {
        for (Fixture fixture : fixtures().toList()) {
            assertTrue(
                    fixture.expected().keySet().containsAll(GATE_CUTOFFS),
                    fixture + " counts " + fixture.expected().keySet());
        }
        assertEquals(22, fixtures().count(), "2 constructed + 12 checked-in + 8 K562 tables");
    }

    /* ---------------------------------------------------------------- categories */

    @ParameterizedTest(name = "{0} at {1}")
    @MethodSource("fixturesAndCutoffs")
    @DisplayName(
            "categories: passing, failing and unknown partition the table, each exactly the rows"
                    + " the oracle puts there, in file order")
    void categoriesPartition(Fixture fixture, String cutoff) throws IOException {
        StoreOracle oracle = StoreOracle.read(fixture.path().get());
        try (ResultStore store = open(fixture)) {
            QValueFilter filter = fixture.filter(cutoff);
            Set<Long> seen = new HashSet<>();
            for (Category category : Category.values()) {
                List<Long> lines =
                        allLines(
                                store,
                                ResultQuery.firstPage(filter).withCategory(category),
                                ResultQuery.MAX_PAGE_SIZE);
                assertEquals(
                        oracle.expect(cutoff, category.name(), "", "FILE_ORDER", false),
                        lines,
                        category.name());
                if (category != Category.ALL) {
                    for (Long line : lines) {
                        assertTrue(seen.add(line), "line " + line + " in two categories");
                    }
                }
            }
            assertEquals(oracle.rows().size(), seen.size(), "every row in one category");
        }
    }

    /* ---------------------------------------------------------------- gate 1 and gate 8 */

    @Test
    @DisplayName(
            "gate 1: inclusive at exactly the cutoff -- 0.01 passes 0.01 and 0.0100001 fails it;"
                    + " 0 passes 0; the q-value 1 passes 1")
    void inclusiveAtTheCutoff() throws IOException {
        try (ResultStore store = open(shuffled())) {
            assertEquals(
                    Set.of("0", "1e-3", "0.004999", "0.005", "0.00999999", "0.01"),
                    qTexts(store, PsmQValueFilter.parse("0.01"), Category.PASSING));
            assertTrue(
                    qTexts(store, PsmQValueFilter.parse("0.01"), Category.FAILING)
                            .contains("0.0100001"));
            assertEquals(Set.of("0"), qTexts(store, PsmQValueFilter.parse("0"), Category.PASSING));
            assertTrue(qTexts(store, PsmQValueFilter.parse("1"), Category.PASSING).contains("1"));
            assertEquals(Set.of(), qTexts(store, PsmQValueFilter.parse("1"), Category.FAILING));
            // The two rows at exactly 0.01, and the two at exactly 0.005, pass their own cutoff.
            assertEquals(
                    List.of(4L, 14L),
                    linesWithQ(store, PsmQValueFilter.parse("0.01"), Category.PASSING, "0.01"));
            assertEquals(
                    List.of(5L, 11L),
                    linesWithQ(store, PsmQValueFilter.parse("0.005"), Category.PASSING, "0.005"));
        }
        try (ResultStore store = open(unknownQ())) {
            assertEquals(
                    Set.of("0", "0.001", "1e-3", "0.01", "0.010"),
                    qTexts(store, PsmQValueFilter.DEFAULT, Category.PASSING));
            assertEquals(
                    Set.of("0.0100001", "1"),
                    qTexts(store, PsmQValueFilter.DEFAULT, Category.FAILING));
        }
    }

    @Test
    @DisplayName(
            "gate 8: every unknown spelling is in the unknown category at 0, 0.01 and 1, and never"
                    + " passing or failing")
    void unknownKindsInTheirOwnCategory() throws IOException {
        Map<Fixture, Set<String>> spellings =
                Map.of(
                        shuffled(),
                        Set.of("NaN", "", "1.5", "-nan", "0,01", "-0.1", "Infinity"),
                        unknownQ(),
                        Set.of("NaN", "", "inf", "0,005", "1.5", "-0.01", "Infinity", "nan"));
        for (Map.Entry<Fixture, Set<String>> entry : spellings.entrySet()) {
            try (ResultStore store = open(entry.getKey())) {
                for (String cutoff : List.of("0", "0.01", "1")) {
                    QValueFilter filter = PsmQValueFilter.parse(cutoff);
                    assertEquals(
                            entry.getValue(),
                            qTexts(store, filter, Category.UNKNOWN_Q_VALUE),
                            entry.getKey() + " at " + cutoff);
                    Set<String> shown = qTexts(store, filter, Category.PASSING);
                    shown.addAll(qTexts(store, filter, Category.FAILING));
                    for (String unknown : entry.getValue()) {
                        assertFalse(shown.contains(unknown), unknown + " passed or failed");
                    }
                }
            }
        }
    }

    @Test
    @DisplayName(
            "q-values beyond a double's range: 1e-400 is known and fails 0, 1e400 and -1e-400 are"
                    + " unknown -- as the independent counter says")
    void beyondDoubleRange() throws IOException {
        Path table = work.resolve("extremes.tsv");
        Files.writeString(
                table,
                "PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds\n"
                        + "a_1_2_1\t1\t1e-400\t0\tK.A.R\tp\n"
                        + "a_2_2_1\t1\t1e400\t0\tK.A.R\tp\n"
                        + "a_3_2_1\t1\t-1e-400\t0\tK.A.R\tp\n"
                        + "a_4_2_1\t1\t0\t0\tK.A.R\tp\n"
                        + "a_5_2_1\t1\t0e-400\t0\tK.A.R\tp\n",
                StandardCharsets.UTF_8);
        IndependentCounter.Tally tally = IndependentCounter.count(table, List.of("0", "1"));
        assertEquals(new IndependentCounts(5, 2, 1, 2), tally.at("0"));
        try (ResultStore store = open(table, TableKind.TARGET_PSMS, work.resolve("index"))) {
            for (String cutoff : List.of("0", "1")) {
                assertEquals(
                        asFilterCounts(tally.at(cutoff)),
                        store.counts(PsmQValueFilter.parse(cutoff)),
                        "at " + cutoff);
            }
            assertEquals(
                    List.of(5L, 6L, 2L, 3L, 4L),
                    allLines(
                            store,
                            all(PsmQValueFilter.DEFAULT)
                                    .withSort(ResultSort.ascending(ResultSort.Column.Q_VALUE)),
                            10),
                    "0 and 0e-400 tie in file order, 1e-400 after them, the unknown last");
        }
    }

    private static Set<String> qTexts(ResultStore store, QValueFilter filter, Category category)
            throws IOException {
        ResultPage page =
                store.query(
                        ResultQuery.firstPage(filter)
                                .withCategory(category)
                                .withPage(0, ResultQuery.MAX_PAGE_SIZE));
        return page.rows().stream()
                .map(row -> row.qValue().text())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static List<Long> linesWithQ(
            ResultStore store, QValueFilter filter, Category category, String q)
            throws IOException {
        return store
                .query(
                        ResultQuery.firstPage(filter)
                                .withCategory(category)
                                .withPage(0, ResultQuery.MAX_PAGE_SIZE))
                .rows()
                .stream()
                .filter(row -> row.qValue().text().equals(q))
                .map(ResultRow::line)
                .toList();
    }

    /* ---------------------------------------------------------------- text filter */

    @ParameterizedTest(name = "text ''{0}''")
    @org.junit.jupiter.params.provider.ValueSource(
            strings = {
                "",
                "   ",
                "sample d",
                "SAMPLE_A",
                "  alpha  ",
                "delta_human",
                "PEPTIDEK",
                "p10003",
                "psm84",
                "2026_10_08_run_2_1_15",
                "m[15.9949]",
                "_2_1",
                "decoy_",
                "no such text"
            })
    @DisplayName(
            "text filter: case-insensitive substring of PSMId, peptide or any protein, white space"
                    + " stripped, in every category, as the oracle finds it")
    void textFilter(String text) throws IOException {
        Fixture fixture = shuffled();
        StoreOracle oracle = StoreOracle.read(fixture.path().get());
        try (ResultStore store = open(fixture)) {
            for (Category category : Category.values()) {
                for (String cutoff : List.of("0", "0.01", "1")) {
                    ResultQuery query =
                            ResultQuery.firstPage(PsmQValueFilter.parse(cutoff))
                                    .withCategory(category)
                                    .withText(text);
                    assertEquals(
                            oracle.expect(cutoff, category.name(), text, "FILE_ORDER", false),
                            allLines(store, query, 4),
                            category + " at " + cutoff);
                }
            }
        }
    }

    @Test
    @DisplayName("text filter by hand: which rows each text finds, and counts stay whole-table")
    void textFilterByHand() throws IOException {
        try (ResultStore store = open(shuffled())) {
            QValueFilter one = PsmQValueFilter.parse("1");
            assertEquals(
                    List.of(4L, 9L, 12L, 17L, 20L, 23L),
                    allLines(store, all(one).withText("sample d"), 10));
            assertEquals(
                    List.of(2L, 8L, 24L), allLines(store, all(one).withText("ALPHA_human"), 10));
            assertEquals(List.of(4L, 23L), allLines(store, all(one).withText("peptidek"), 10));
            ResultPage none = store.query(all(one).withText("no such text"));
            assertEquals(0, none.matching());
            assertEquals(List.of(), none.rows());
            assertEquals(new FilterCounts(23, 16, 0, 7), none.counts(), "counts ignore the text");
        }
    }

    /* ---------------------------------------------------------------- sorting */

    @ParameterizedTest(name = "{0} by {1}")
    @MethodSource("sortsOnShuffledAndK562")
    @DisplayName(
            "every column, both directions: the oracle's order -- missing values last both ways,"
                    + " ties in file order both ways -- in every category")
    void everySort(Fixture fixture, ResultSort sort) throws IOException {
        StoreOracle oracle = StoreOracle.read(fixture.path().get());
        boolean descending = sort.direction() == ResultSort.Direction.DESCENDING;
        try (ResultStore store = open(fixture)) {
            for (Category category : List.of(Category.ALL, Category.PASSING, Category.FAILING)) {
                ResultQuery query =
                        ResultQuery.firstPage(fixture.filter("0.01"))
                                .withCategory(category)
                                .withSort(sort);
                assertEquals(
                        oracle.expect(
                                "0.01", category.name(), "", sort.column().name(), descending),
                        allLines(store, query, 97),
                        category.name());
            }
        }
    }

    @Test
    @DisplayName(
            "sort rules by hand on the shuffled table: missing last, ties in file order, -0 = 0")
    void sortRulesByHand() throws IOException {
        try (ResultStore store = open(shuffled())) {
            QValueFilter filter = PsmQValueFilter.DEFAULT;
            List<Long> unknownInFileOrder = List.of(9L, 10L, 12L, 15L, 17L, 19L, 23L);
            List<Long> qAscending =
                    allLines(
                            store,
                            all(filter).withSort(ResultSort.ascending(ResultSort.Column.Q_VALUE)),
                            5);
            assertEquals(List.of(3L, 21L, 13L, 20L, 5L, 11L), qAscending.subList(0, 6));
            assertEquals(unknownInFileOrder, qAscending.subList(16, 23));
            List<Long> qDescending =
                    allLines(
                            store,
                            all(filter).withSort(ResultSort.descending(ResultSort.Column.Q_VALUE)),
                            5);
            assertEquals(List.of(6L, 2L, 18L, 16L), qDescending.subList(0, 4));
            assertEquals(List.of(3L, 21L), qDescending.subList(14, 16), "tied 0s in file order");
            assertEquals(unknownInFileOrder, qDescending.subList(16, 23));
            List<Long> scoreAscending =
                    allLines(
                            store,
                            all(filter).withSort(ResultSort.ascending(ResultSort.Column.SCORE)),
                            5);
            assertEquals(List.of(9L, 12L, 13L), scoreAscending.subList(0, 3), "-0.0 ties 0.0");
            assertEquals(8L, scoreAscending.get(22), "the nan score last");
            List<Long> scoreDescending =
                    allLines(
                            store,
                            all(filter).withSort(ResultSort.descending(ResultSort.Column.SCORE)),
                            5);
            assertEquals(List.of(3L, 11L, 16L, 6L), scoreDescending.subList(0, 4));
            assertEquals(8L, scoreDescending.get(22), "the nan score last descending too");
            assertEquals(
                    List.of(12L, 13L),
                    scoreDescending.subList(19, 21),
                    "0.0 and -0.0 tied, in file order");
            List<Long> scanAscending =
                    allLines(
                            store,
                            all(filter).withSort(ResultSort.ascending(ResultSort.Column.SCAN)),
                            5);
            assertEquals(List.of(6L, 14L), scanAscending.subList(21, 23), "psm84 and odd_x last");
            List<Long> scanDescending =
                    allLines(
                            store,
                            all(filter).withSort(ResultSort.descending(ResultSort.Column.SCAN)),
                            5);
            assertEquals(List.of(2L, 10L, 21L), scanDescending.subList(0, 3), "scan 1200 x3");
            assertEquals(List.of(6L, 14L), scanDescending.subList(21, 23));
            List<Long> pepDescending =
                    allLines(
                            store,
                            all(filter).withSort(ResultSort.descending(ResultSort.Column.PEP)),
                            5);
            assertEquals(
                    List.of(11L, 18L), pepDescending.subList(21, 23), "empty and inf PEP last");
            List<Long> fileDescending =
                    allLines(
                            store,
                            all(filter)
                                    .withSort(ResultSort.descending(ResultSort.Column.FILE_ORDER)),
                            5);
            assertEquals(24L, fileDescending.get(0));
            assertEquals(2L, fileDescending.get(22));
        }
    }

    @Test
    @DisplayName(
            "scan 0 and charge 0 are values, not missing: they sort first ascending, before the"
                    + " PSMId that has neither")
    void zeroScanAndCharge() throws IOException {
        Path table = work.resolve("zeros.tsv");
        Files.writeString(
                table,
                "PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds\n"
                        + "psm1\t1\t0.001\t0\tK.A.R\tp\n"
                        + "a_5_2_1\t1\t0.001\t0\tK.A.R\tp\n"
                        + "a_0_0_1\t1\t0.001\t0\tK.A.R\tp\n",
                StandardCharsets.UTF_8);
        try (ResultStore store = open(table, TableKind.TARGET_PSMS, work.resolve("index"))) {
            for (ResultSort.Column column :
                    List.of(ResultSort.Column.SCAN, ResultSort.Column.CHARGE)) {
                assertEquals(
                        List.of(4L, 3L, 2L),
                        allLines(
                                store,
                                all(PsmQValueFilter.DEFAULT).withSort(ResultSort.ascending(column)),
                                10),
                        column + " ascending");
                assertEquals(
                        List.of(3L, 4L, 2L),
                        allLines(
                                store,
                                all(PsmQValueFilter.DEFAULT)
                                        .withSort(ResultSort.descending(column)),
                                10),
                        column + " descending");
            }
        }
    }

    @Test
    @DisplayName(
            "scans and charges of 0, among others and among missing ones, in a shuffled table:"
                    + " the oracle's order both ways")
    void zeroScansAndChargesShuffled() throws IOException {
        StringBuilder text =
                new StringBuilder(
                        "PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds\n");
        for (int i = 0; i < 30; i++) {
            int scan = i * 7 % 5;
            int charge = i * 3 % 4;
            String id = scan == 4 ? "psm" + i : "b_" + scan + "_" + charge + "_1";
            text.append(id).append("\t1\t0.001\t0\tK.A.R\tp\n");
        }
        Path table = work.resolve("zeros-shuffled.tsv");
        Files.writeString(table, text, StandardCharsets.UTF_8);
        StoreOracle oracle = StoreOracle.read(table);
        try (ResultStore store = open(table, TableKind.TARGET_PSMS, work.resolve("index"))) {
            for (ResultSort.Column column :
                    List.of(ResultSort.Column.SCAN, ResultSort.Column.CHARGE)) {
                for (ResultSort.Direction direction : ResultSort.Direction.values()) {
                    assertEquals(
                            oracle.expect(
                                    "0.01",
                                    "ALL",
                                    "",
                                    column.name(),
                                    direction == ResultSort.Direction.DESCENDING),
                            allLines(
                                    store,
                                    all(PsmQValueFilter.DEFAULT)
                                            .withSort(new ResultSort(column, direction)),
                                    7),
                            column + " " + direction);
                }
            }
        }
    }

    /* ---------------------------------------------------------------- paging */

    @ParameterizedTest(name = "page size {0}")
    @org.junit.jupiter.params.provider.ValueSource(ints = {1, 2, 7, 100, ResultQuery.MAX_PAGE_SIZE})
    @DisplayName(
            "paging: pages concatenate to exactly the matching rows, none twice, none missing, at"
                    + " any page size")
    void pagesConcatenate(int pageSize) throws IOException {
        Fixture fixture = k562(RealK562.Table.V3071_TARGET_PSMS);
        StoreOracle oracle = StoreOracle.read(fixture.path().get());
        try (ResultStore store = open(fixture)) {
            for (Category category : Category.values()) {
                ResultQuery query =
                        ResultQuery.firstPage(PsmQValueFilter.DEFAULT)
                                .withCategory(category)
                                .withSort(ResultSort.descending(ResultSort.Column.SCORE));
                List<Long> lines = allLines(store, query, pageSize);
                assertEquals(lines.size(), new HashSet<>(lines).size(), "no row twice");
                assertEquals(
                        oracle.expect("0.01", category.name(), "", "SCORE", true),
                        lines,
                        category.name());
            }
        }
    }

    @Test
    @DisplayName(
            "paging edges: an offset at or past the end gives no rows and the true match count;"
                    + " a limit of 0 gives counts only")
    void pagingEdges() throws IOException {
        try (ResultStore store = open(shuffled())) {
            ResultQuery passing = ResultQuery.firstPage(PsmQValueFilter.DEFAULT);
            ResultPage last = store.query(passing.withPage(8, 5));
            assertEquals(1, last.rows().size());
            assertEquals(9, last.matching());
            ResultPage atEnd = store.query(passing.withPage(9, 5));
            assertEquals(List.of(), atEnd.rows());
            assertEquals(9, atEnd.matching());
            assertEquals(9, atEnd.offset());
            ResultPage past = store.query(passing.withPage(1_000_000, 5));
            assertEquals(List.of(), past.rows());
            assertEquals(9, past.matching());
            ResultPage countsOnly = store.query(passing.withPage(0, 0));
            assertEquals(List.of(), countsOnly.rows());
            assertEquals(9, countsOnly.matching());
            assertEquals(new FilterCounts(23, 9, 7, 7), countsOnly.counts());
        }
    }

    @Test
    @DisplayName(
            "page size bound: no page above MAX_PAGE_SIZE, even when every row of a larger table"
                    + " matches; a larger limit is refused")
    void pageSizeBound() throws IOException {
        int rows = ResultQuery.MAX_PAGE_SIZE + 1234;
        StringBuilder text =
                new StringBuilder(
                        "PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds\n");
        for (int i = 0; i < rows; i++) {
            text.append("big_").append(i).append("_2_1\t1\t0.001\t0\tK.A.R\tp\n");
        }
        Path table = work.resolve("big.tsv");
        Files.writeString(table, text, StandardCharsets.UTF_8);
        try (ResultStore store = open(table, TableKind.TARGET_PSMS, work.resolve("index"))) {
            ResultPage page =
                    store.query(
                            ResultQuery.firstPage(PsmQValueFilter.DEFAULT)
                                    .withPage(0, ResultQuery.MAX_PAGE_SIZE));
            assertEquals(ResultQuery.MAX_PAGE_SIZE, page.rows().size());
            assertEquals(rows, page.matching());
            assertEquals(
                    ResultQuery.DEFAULT_PAGE_SIZE,
                    store.query(ResultQuery.firstPage(PsmQValueFilter.DEFAULT)).rows().size());
        }
        ResultQuery valid = ResultQuery.firstPage(PsmQValueFilter.DEFAULT);
        IllegalArgumentException refused =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> assertNotNull(valid.withPage(0, ResultQuery.MAX_PAGE_SIZE + 1)));
        assertTrue(refused.getMessage().contains("5001"), refused.getMessage());
    }

    /* ---------------------------------------------------------------- independence */

    @Test
    @DisplayName(
            "independence: PSM and peptide stores open together, each filtered by its own filter;"
                    + " changing one leaves the other's counts and rows as they were")
    void psmAndPeptideFiltersIndependent() throws IOException {
        Fixture psms = k562(RealK562.Table.V3071_TARGET_PSMS);
        Fixture peptides = k562(RealK562.Table.V3071_TARGET_PEPTIDES);
        try (ResultStore psmStore = open(psms);
                ResultStore peptideStore =
                        open(peptides.path().get(), peptides.kind(), work.resolve("peptides"))) {
            PeptideQValueFilter peptideFilter = PeptideQValueFilter.parse("0.05");
            ResultPage peptidesBefore =
                    peptideStore.query(ResultQuery.firstPage(peptideFilter).withPage(0, 1000));
            assertEquals(asFilterCounts(peptides.expected().get("0.05")), peptidesBefore.counts());
            for (String cutoff : List.of("0", "0.005", "0.01", "1")) {
                ResultPage psmPage =
                        psmStore.query(ResultQuery.firstPage(PsmQValueFilter.parse(cutoff)));
                assertEquals(asFilterCounts(psms.expected().get(cutoff)), psmPage.counts());
                ResultPage peptidesAfter =
                        peptideStore.query(ResultQuery.firstPage(peptideFilter).withPage(0, 1000));
                assertEquals(peptidesBefore.counts(), peptidesAfter.counts(), "at PSM " + cutoff);
                assertEquals(peptidesBefore.rows(), peptidesAfter.rows(), "at PSM " + cutoff);
            }
            IllegalArgumentException crossed =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> psmStore.counts(PeptideQValueFilter.DEFAULT));
            assertTrue(crossed.getMessage().contains("PSM q-value filter"), crossed.getMessage());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> peptideStore.query(ResultQuery.firstPage(PsmQValueFilter.DEFAULT)));
            assertEquals(TableKind.TARGET_PSMS, psmStore.kind());
            assertEquals(TableKind.TARGET_PEPTIDES, peptideStore.kind());
        }
    }

    /* ---------------------------------------------------------------- gate 4: raw files */

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    @DisplayName(
            "gate 4: the raw file's SHA-256, size and time are equal before and after opening,"
                    + " every category, sort and text, and closing")
    void rawFileUntouched(Fixture fixture) throws IOException {
        Path file = fixture.path().get();
        String before = ScratchFixtures.sha256(file);
        long size = Files.size(file);
        FileTime time = Files.getLastModifiedTime(file);
        try (ResultStore store = open(fixture)) {
            assertEquals(file, store.file());
            for (String cutoff : fixture.expected().keySet()) {
                store.counts(fixture.filter(cutoff));
                for (Category category : Category.values()) {
                    for (ResultSort.Column column : ResultSort.Column.values()) {
                        store.query(
                                ResultQuery.firstPage(fixture.filter(cutoff))
                                        .withCategory(category)
                                        .withSort(ResultSort.descending(column))
                                        .withText("K"));
                    }
                }
            }
            store.row(new RowKey(2));
        }
        assertEquals(before, ScratchFixtures.sha256(file), "SHA-256");
        assertEquals(size, Files.size(file), "size");
        assertEquals(time, Files.getLastModifiedTime(file), "modification time");
        try (Stream<Path> siblings = Files.list(Objects.requireNonNull(file.getParent()))) {
            for (Path sibling : siblings.toList()) {
                assertFalse(
                        String.valueOf(sibling.getFileName()).startsWith(file.getFileName() + "."),
                        "nothing written beside the raw file: " + sibling);
            }
        }
    }

    /* ---------------------------------------------------------------- row keys */

    @Test
    @DisplayName(
            "row keys: every row found by its line, equal to the row in any page; the key survives"
                    + " filter, category, text and sort changes; an absent line is empty")
    void rowKeys() throws IOException {
        StoreOracle oracle = StoreOracle.read(shuffled().path().get());
        try (ResultStore store = open(shuffled())) {
            for (StoreOracle.Row expected : oracle.rows()) {
                Optional<ResultRow> row = store.row(new RowKey(expected.line()));
                assertTrue(row.isPresent(), "line " + expected.line());
                assertEquals(expected.psmId(), row.get().psmId());
                assertEquals(expected.qText(), row.get().qValue().text());
                assertEquals(expected.proteins(), row.get().proteinIds());
            }
            assertEquals(Optional.empty(), store.row(new RowKey(25)));
            assertEquals(Optional.empty(), store.row(new RowKey(Long.MAX_VALUE)));
            RowKey selected = new RowKey(4);
            ResultRow byKey = store.row(selected).orElseThrow();
            for (String cutoff : List.of("0.01", "0.05", "1")) {
                for (ResultSort.Column column : ResultSort.Column.values()) {
                    ResultPage page =
                            store.query(
                                    ResultQuery.firstPage(PsmQValueFilter.parse(cutoff))
                                            .withSort(ResultSort.descending(column))
                                            .withText("sample")
                                            .withPage(0, 50));
                    List<ResultRow> found =
                            page.rows().stream()
                                    .filter(row -> RowKey.of(row).equals(selected))
                                    .toList();
                    assertEquals(List.of(byKey), found, cutoff + " " + column);
                }
            }
        }
    }

    /* ---------------------------------------------------------------- lifecycle */

    @Test
    @DisplayName(
            "header and kind as opened; a closed store refuses every call; closing twice is fine")
    void lifecycle() throws IOException {
        Fixture fixture = shuffled();
        ResultStore store = open(fixture);
        assertEquals(
                List.of(
                        "PSMId",
                        "score",
                        "q-value",
                        "posterior_error_prob",
                        "peptide",
                        "proteinIds"),
                store.header().columns());
        assertEquals(TableKind.TARGET_PSMS, store.kind());
        assertEquals(23, store.rowCount());
        store.close();
        store.close();
        assertThrows(IllegalStateException.class, store::rowCount);
        assertThrows(IllegalStateException.class, store::header);
        assertThrows(IllegalStateException.class, store::kind);
        assertThrows(IllegalStateException.class, store::file);
        assertThrows(IllegalStateException.class, () -> store.counts(PsmQValueFilter.DEFAULT));
        assertThrows(
                IllegalStateException.class,
                () -> store.query(ResultQuery.firstPage(PsmQValueFilter.DEFAULT)));
        IllegalStateException refused =
                assertThrows(IllegalStateException.class, () -> store.row(new RowKey(2)));
        assertTrue(refused.getMessage().contains("closed"), refused.getMessage());
    }

    @Test
    @DisplayName("a missing or malformed table is refused by the one reader, with its own problem")
    void refusedTables() throws IOException {
        PercolatorOutputException missing =
                assertThrows(
                        PercolatorOutputException.class,
                        () -> open(work.resolve("absent.tsv"), TableKind.TARGET_PSMS, work));
        assertEquals(PercolatorOutputException.Problem.MISSING_FILE, missing.problem());
        Path blank = work.resolve("blank.tsv");
        Files.writeString(
                blank,
                "PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds\n\n",
                StandardCharsets.UTF_8);
        PercolatorOutputException refused =
                assertThrows(
                        PercolatorOutputException.class,
                        () -> open(blank, TableKind.TARGET_PSMS, work.resolve("index")));
        assertEquals(PercolatorOutputException.Problem.BLANK_LINE, refused.problem());
    }

    @Test
    @DisplayName("an empty table: no rows, all counts 0, every query empty")
    void emptyTable() throws IOException {
        Path table = work.resolve("empty.tsv");
        Files.writeString(
                table,
                "PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds\n",
                StandardCharsets.UTF_8);
        try (ResultStore store = open(table, TableKind.DECOY_PEPTIDES, work.resolve("index"))) {
            assertEquals(0, store.rowCount());
            ResultPage page =
                    store.query(
                            all(PeptideQValueFilter.DEFAULT)
                                    .withSort(ResultSort.descending(ResultSort.Column.PROTEINS)));
            assertEquals(List.of(), page.rows());
            assertEquals(0, page.matching());
            assertEquals(new FilterCounts(0, 0, 0, 0), page.counts());
            assertEquals(Optional.empty(), store.row(new RowKey(2)));
        }
    }

    /* ---------------------------------------------------------------- concurrency */

    @Test
    @DisplayName(
            "thread safety: 8 threads x 40 mixed queries at once give exactly the answers one"
                    + " thread gives")
    void concurrentQueries() throws IOException, InterruptedException, ExecutionException {
        Fixture fixture = k562(RealK562.Table.V3071_TARGET_PSMS);
        List<ResultQuery> queries = new ArrayList<>();
        String[] cutoffs = {"0", "0.005", "0.01", "0.05", "1"};
        String[] texts = {"", "K562_3", "sp|", "R.", "zz"};
        ResultSort.Column[] columns = ResultSort.Column.values();
        for (int i = 0; i < 40; i++) {
            queries.add(
                    new ResultQuery(
                            PsmQValueFilter.parse(cutoffs[i % cutoffs.length]),
                            Category.values()[i % Category.values().length],
                            texts[i % texts.length],
                            new ResultSort(
                                    columns[i % columns.length],
                                    ResultSort.Direction.values()[i % 2]),
                            (i * 37L) % 300,
                            1 + (i * 53) % 400));
        }
        try (ResultStore sequential = open(fixture);
                ResultStore shared =
                        open(fixture.path().get(), fixture.kind(), work.resolve("shared"))) {
            List<ResultPage> expected = new ArrayList<>();
            for (ResultQuery query : queries) {
                expected.add(sequential.query(query));
            }
            ExecutorService threads = Executors.newFixedThreadPool(8);
            try {
                List<Future<List<ResultPage>>> results = new ArrayList<>();
                for (int t = 0; t < 8; t++) {
                    int start = t;
                    results.add(
                            threads.submit(
                                    () -> {
                                        List<ResultPage> pages = new ArrayList<>();
                                        for (int i = 0; i < queries.size(); i++) {
                                            int index = (start * 5 + i) % queries.size();
                                            pages.add(shared.query(queries.get(index)));
                                        }
                                        return pages;
                                    }));
                }
                for (int t = 0; t < 8; t++) {
                    List<ResultPage> pages = results.get(t).get();
                    for (int i = 0; i < queries.size(); i++) {
                        int index = (t * 5 + i) % queries.size();
                        assertEquals(
                                expected.get(index),
                                pages.get(i),
                                "thread " + t + " query " + index);
                    }
                }
            } finally {
                threads.shutdownNow();
            }
        }
    }

    /* ---------------------------------------------------------------- spectrum references */

    @ParameterizedTest
    @EnumSource(
            value = RealK562.Table.class,
            names = {"V3071_TARGET_PSMS", "V309_DECOY_PEPTIDES"})
    @DisplayName(
            "real K562 rows: every PSMId reads as a spectrum reference whose base is one of the two"
                    + " K562 files, and the oracle's regex agrees on base, scan and charge")
    void k562SpectrumReferences(RealK562.Table table) throws IOException {
        StoreOracle oracle = StoreOracle.read(table.path());
        Map<Long, StoreOracle.Row> byLine = new LinkedHashMap<>();
        oracle.rows().forEach(row -> byLine.put(row.line(), row));
        try (ResultStore store = open(k562(table))) {
            ResultQuery everything = all(PsmQValueFilter.DEFAULT);
            if (!store.kind().isPsms()) {
                everything = all(PeptideQValueFilter.DEFAULT);
            }
            Set<String> bases = new TreeSet<>();
            long offset = 0;
            while (true) {
                ResultPage page =
                        store.query(everything.withPage(offset, ResultQuery.MAX_PAGE_SIZE));
                for (ResultRow row : page.rows()) {
                    var reference = row.spectrumReference().orElseThrow();
                    StoreOracle.Row expected = byLine.get(row.line());
                    assertNotNull(expected);
                    assertEquals(expected.base(), reference.base());
                    assertEquals(expected.scan(), BigDecimal.valueOf(reference.scan()));
                    assertEquals(expected.charge(), BigDecimal.valueOf(reference.charge()));
                    bases.add(reference.base());
                }
                assertFalse(
                        page.rows().isEmpty() && offset < page.matching(),
                        "a page before the end holds rows (offset " + offset + ")");
                offset += page.rows().size();
                if (offset >= page.matching()) {
                    break;
                }
            }
            assertEquals(
                    Set.of("20100614_Velos1_TaGe_SA_K562_3", "20100614_Velos1_TaGe_SA_K562_4"),
                    bases);
        }
    }
}
