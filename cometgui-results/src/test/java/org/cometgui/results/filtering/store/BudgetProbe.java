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
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.cometgui.results.filtering.FilterCounts;
import org.cometgui.results.filtering.PsmQValueFilter;
import org.cometgui.results.parser.ResultTable;
import org.cometgui.results.parser.ResultTableReader;
import org.cometgui.results.testing.TestHasher;

/**
 * The program {@code DiskStoreBudgetTest} runs in a child JVM with a fixed {@code -Xmx}, so that
 * the large fixture's heap budget is enforced by the JVM rather than estimated (design decision
 * P10-11, gate 6). It prints its measurements as one line beginning {@link #RESULT} and exits 0,
 * or, if the heap runs out, prints a line beginning {@link #OUT_OF_MEMORY} and exits {@link
 * #OUT_OF_MEMORY_EXIT}.
 *
 * <p>{@code store <psms.tsv> <index directory> <text>}: opens the table through {@link
 * ResultStores#open} -- the factory decides memory or disk, by its threshold -- with an empty index
 * directory (a cold open: hash, index), closes it and opens it again (a warm open: hash, check);
 * counts at every cutoff given on the command line; reads the first, middle and last pages of all
 * rows by score descending (a numeric sort, built on first use) and by peptide ascending (a text
 * sort, an external merge sort on first use); and counts the rows a text filter matches.
 *
 * <p>{@code readAll <psms.tsv>}: the negative control, {@link ResultTableReader#readAll}, which
 * holds every row.
 */
public final class BudgetProbe {

    /** The first word of the result line. */
    static final String RESULT = "BUDGET";

    /** The first word of the line printed when the heap runs out. */
    static final String OUT_OF_MEMORY = "OUT_OF_MEMORY";

    /** The exit status when the heap runs out. */
    static final int OUT_OF_MEMORY_EXIT = 3;

    private BudgetProbe() {}

    /**
     * Runs one mode.
     *
     * @param arguments the mode and its arguments, as above, then the cutoffs
     * @throws IOException if the store fails
     */
    public static void main(String[] arguments) throws IOException {
        try {
            if ("readAll".equals(arguments[0])) {
                ResultTable table = ResultTableReader.readAll(Path.of(arguments[1]));
                System.out.println(RESULT + " store=readAll rows=" + table.rows().size());
                return;
            }
            store(
                    Path.of(arguments[1]),
                    Path.of(arguments[2]),
                    arguments[3],
                    List.of(arguments).subList(4, arguments.length));
        } catch (OutOfMemoryError exhausted) {
            System.out.println(OUT_OF_MEMORY + " " + exhausted.getMessage());
            System.out.flush();
            Runtime.getRuntime().halt(OUT_OF_MEMORY_EXIT);
        }
    }

    private static void store(Path table, Path indexDirectory, String text, List<String> cutoffs)
            throws IOException {
        TestHasher hasher = new TestHasher();
        Map<String, Object> out = new LinkedHashMap<>();
        long start = System.nanoTime();
        try (ResultStore cold =
                ResultStores.open(table, TableKind.TARGET_PSMS, indexDirectory, hasher)) {
            out.put("store", cold.getClass().getSimpleName());
            out.put("openColdMs", millis(start));
        }
        start = System.nanoTime();
        try (ResultStore store =
                ResultStores.open(table, TableKind.TARGET_PSMS, indexDirectory, hasher)) {
            out.put("openWarmMs", millis(start));
            out.put("rows", store.rowCount());
            start = System.nanoTime();
            List<String> counted = new ArrayList<>();
            for (String cutoff : cutoffs) {
                FilterCounts counts = store.counts(PsmQValueFilter.parse(cutoff));
                counted.add(
                        cutoff
                                + ":"
                                + counts.total()
                                + "/"
                                + counts.passing()
                                + "/"
                                + counts.failing()
                                + "/"
                                + counts.unknownQValue());
            }
            out.put("countsMs", millis(start));
            out.put("counts", String.join(",", counted));
            ResultQuery all =
                    ResultQuery.firstPage(PsmQValueFilter.DEFAULT).withCategory(Category.ALL);
            start = System.nanoTime();
            out.put(
                    "scorePages",
                    threePages(
                            store, all.withSort(ResultSort.descending(ResultSort.Column.SCORE))));
            out.put("numericSortMs", millis(start));
            start = System.nanoTime();
            out.put(
                    "peptidePages",
                    threePages(
                            store, all.withSort(ResultSort.ascending(ResultSort.Column.PEPTIDE))));
            out.put("textSortMs", millis(start));
            start = System.nanoTime();
            ResultPage matched = store.query(all.withText(text));
            out.put("textFilterMs", millis(start));
            out.put("textMatches", matched.matching());
            out.put("textFirstLine", matched.rows().isEmpty() ? -1 : matched.rows().get(0).line());
        }
        out.put("hashes", hasher.calls());
        out.put("heapPoolPeaksMb", heapPoolPeaksMegabytes());
        out.put("maxHeapMb", Runtime.getRuntime().maxMemory() >> 20);
        StringBuilder line = new StringBuilder(RESULT);
        out.forEach((key, value) -> line.append(' ').append(key).append('=').append(value));
        System.out.println(line);
    }

    /** The first lines of the first, middle and last pages, the pages themselves fetched. */
    private static String threePages(ResultStore store, ResultQuery query) throws IOException {
        ResultPage first = store.query(query.withPage(0, ResultQuery.DEFAULT_PAGE_SIZE));
        long middleOffset = first.matching() / 2;
        ResultPage middle =
                store.query(query.withPage(middleOffset, ResultQuery.DEFAULT_PAGE_SIZE));
        long lastOffset = first.matching() - ResultQuery.DEFAULT_PAGE_SIZE;
        ResultPage last = store.query(query.withPage(lastOffset, ResultQuery.DEFAULT_PAGE_SIZE));
        return first.rows().get(0).line()
                + "/"
                + middle.rows().get(0).line()
                + "/"
                + last.rows().get(last.rows().size() - 1).line()
                + "/"
                + (first.rows().size() + middle.rows().size() + last.rows().size());
    }

    private static long millis(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }

    /**
     * The sum of every heap pool's peak use: an upper bound, the pools peaking at different times.
     */
    private static long heapPoolPeaksMegabytes() {
        long peak = 0;
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.HEAP) {
                peak += pool.getPeakUsage().getUsed();
            }
        }
        return peak >> 20;
    }
}
