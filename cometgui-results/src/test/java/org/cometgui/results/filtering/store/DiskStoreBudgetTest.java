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

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.cometgui.results.testing.IndependentCounts;
import org.cometgui.results.testing.LargeFixture;
import org.cometgui.results.testing.ScratchFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Gate 6, {@code AC-RES-10}: the large fixture loads, filters, sorts and pages within a fixed heap
 * and documented times (design decision P10-11). The heap is enforced, not estimated: {@link
 * BudgetProbe} runs in a <strong>child JVM with {@code -Xmx}{@value #HEAP}</strong>, opening the 1
 * 000 000-row table through {@link ResultStores#open} so that the factory's threshold picks the
 * store. The negative control runs {@code ResultTableReader.readAll} over the same file in the same
 * heap and must die of {@link OutOfMemoryError}, so the fixture provably crosses what the budget
 * can hold and the budget is not slack.
 *
 * <p>Measured on the phase 10 host (2026-10-08, 64 cores, Linux, JDK 25, warm page cache), in the
 * child at {@code -Xmx64m}: cold open (hash, index) 2.2 s; warm open (hash, check) 0.7 s; counts at
 * eight cutoffs 0.1 s; three pages by score, the column's sort files built on first use, 0.7 s;
 * three pages by peptide, one external merge sort building both directions on first use, 2.7 s; a
 * new text filter's first page and match count 1.5 s. The disk store completes the same run at
 * {@code -Xmx16m}; {@code readAll} fails at 64, 256 and 512 MB and completes only at 1 GB. Each
 * time budget below is three to four times its measurement: this is a local gate on one host, not
 * the specification's nightly-runner thresholds (<em>Performance and resource tests</em>).
 *
 * <p>Process launching here is test code; product code launches processes only through the process
 * service ({@code R-PROC-02}). Cost: about 12 s for the class.
 */
class DiskStoreBudgetTest {

    /** The child JVM's heap: {@value}. */
    static final String HEAP = "64m";

    /** Budget for a cold open -- hash, one indexing pass, mapping: {@value} ms. */
    static final long OPEN_COLD_MS = 8_000;

    /** Budget for a warm open -- hash, index check, mapping: {@value} ms. */
    static final long OPEN_WARM_MS = 3_000;

    /** Budget for the counts at all eight manifest cutoffs: {@value} ms. */
    static final long COUNTS_MS = 1_000;

    /** Budget for three pages by a numeric sort, its sort file built on first use: {@value} ms. */
    static final long NUMERIC_SORT_MS = 3_000;

    /** Budget for three pages by a text sort, its external merge sort on first use: {@value} ms. */
    static final long TEXT_SORT_MS = 10_000;

    /** Budget for a new text filter's first page and match count: {@value} ms. */
    static final long TEXT_FILTER_MS = 6_000;

    /** The text the probe filters by. */
    static final String TEXT = "fx00123";

    private static final long CHILD_TIMEOUT_SECONDS = 180;

    @TempDir private Path work;

    private record Child(int exit, List<String> output) {}

    private Child run(String... arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-Xmx" + HEAP);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(BudgetProbe.class.getName());
        command.addAll(List.of(arguments));
        Path log = work.resolve("child-" + arguments[0] + ".log");
        Process child =
                new ProcessBuilder(command)
                        .redirectErrorStream(true)
                        .redirectOutput(log.toFile())
                        .start();
        boolean finished = child.waitFor(CHILD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            child.destroyForcibly().waitFor();
        }
        List<String> output = Files.readAllLines(log, StandardCharsets.UTF_8);
        assertTrue(finished, "the child ran past " + CHILD_TIMEOUT_SECONDS + " s: " + output);
        return new Child(child.exitValue(), output);
    }

    private static Map<String, String> result(Child child) {
        for (String line : child.output()) {
            if (line.startsWith(BudgetProbe.RESULT + " ")) {
                Map<String, String> fields = new LinkedHashMap<>();
                for (String field : line.substring(BudgetProbe.RESULT.length() + 1).split(" ")) {
                    int equals = field.indexOf('=');
                    fields.put(field.substring(0, equals), field.substring(equals + 1));
                }
                return fields;
            }
        }
        throw new AssertionError("the child printed no result line: " + child.output());
    }

    @Test
    @DisplayName(
            "gate 6: in a -Xmx64m child the factory picks the disk store for the 1 000 000-row"
                    + " fixture; counts at all eight cutoffs equal the manifest's; three pages by"
                    + " score and by peptide, and a text filter, all within the documented times")
    void largeFixtureWithinBudget() throws IOException, InterruptedException {
        LargeFixture fixture = LargeFixture.locate();
        String sha256 = ScratchFixtures.sha256(fixture.psms());
        List<String> arguments =
                new ArrayList<>(
                        List.of(
                                "store",
                                fixture.psms().toString(),
                                work.resolve("index").toString(),
                                TEXT));
        arguments.addAll(fixture.cutoffs());
        Child child = run(arguments.toArray(String[]::new));
        assertEquals(0, child.exit(), "the child failed: " + child.output());
        Map<String, String> measured = result(child);
        System.out.println("budget child at -Xmx" + HEAP + ": " + measured);
        assertEquals(
                "DiskResultStore", measured.get("store"), "the threshold picked the disk store");
        assertEquals("1000000", measured.get("rows"));
        assertEquals(String.valueOf(64L), measured.get("maxHeapMb"), "the child's heap");
        List<String> expectedCounts = new ArrayList<>();
        for (String cutoff : fixture.cutoffs()) {
            IndependentCounts counts = fixture.expected(LargeFixture.PSMS, cutoff);
            expectedCounts.add(
                    cutoff
                            + ":"
                            + counts.total()
                            + "/"
                            + counts.passing()
                            + "/"
                            + counts.failing()
                            + "/"
                            + counts.unknown());
        }
        assertEquals(String.join(",", expectedCounts), measured.get("counts"));
        assertEquals(
                String.valueOf(textMatches(fixture.psms(), TEXT)), measured.get("textMatches"));
        assertTrue(measured.get("scorePages").endsWith("/600"), measured.get("scorePages"));
        assertTrue(measured.get("peptidePages").endsWith("/600"), measured.get("peptidePages"));
        assertEquals("2", measured.get("hashes"), "each open hashed the raw table once");
        assertWithin(measured, "openColdMs", OPEN_COLD_MS);
        assertWithin(measured, "openWarmMs", OPEN_WARM_MS);
        assertWithin(measured, "countsMs", COUNTS_MS);
        assertWithin(measured, "numericSortMs", NUMERIC_SORT_MS);
        assertWithin(measured, "textSortMs", TEXT_SORT_MS);
        assertWithin(measured, "textFilterMs", TEXT_FILTER_MS);
        assertEquals(sha256, ScratchFixtures.sha256(fixture.psms()), "gate 4: the raw table");
    }

    @Test
    @DisplayName(
            "negative control: in the same -Xmx64m, ResultTableReader.readAll over the same table"
                    + " dies of OutOfMemoryError -- the fixture crosses what the budget can hold")
    void readAllDoesNotFit() throws IOException, InterruptedException {
        LargeFixture fixture = LargeFixture.locate();
        Child child = run("readAll", fixture.psms().toString());
        assertEquals(
                BudgetProbe.OUT_OF_MEMORY_EXIT,
                child.exit(),
                "readAll must run out of heap: " + child.output());
        assertTrue(
                child.output().stream()
                        .anyMatch(line -> line.startsWith(BudgetProbe.OUT_OF_MEMORY + " ")),
                child.output().toString());
    }

    private static void assertWithin(Map<String, String> measured, String key, long budget) {
        long millis = Long.parseLong(measured.get(key));
        assertTrue(
                millis <= budget,
                String.format(
                        Locale.ROOT,
                        "%s took %d ms, over its budget of %d ms",
                        key,
                        millis,
                        budget));
    }

    /**
     * Rows whose {@code PSMId}, peptide or a protein contains the text ignoring case: the raw table
     * read with {@code split}, no production class.
     */
    private static long textMatches(Path table, String text) throws IOException {
        String needle = text.toLowerCase(Locale.ROOT);
        long matches = 0;
        try (BufferedReader in = Files.newBufferedReader(table, StandardCharsets.UTF_8)) {
            String first = in.readLine();
            assertTrue(first != null, table + " has no header");
            List<String> header = List.of(first.split("\t", -1));
            int id = header.indexOf("PSMId");
            int peptide = header.indexOf("peptide");
            int proteins = header.indexOf("proteinIds");
            String line;
            while ((line = in.readLine()) != null) {
                String[] fields = line.split("\t", -1);
                boolean found =
                        fields[id].toLowerCase(Locale.ROOT).contains(needle)
                                || fields[peptide].toLowerCase(Locale.ROOT).contains(needle);
                for (int at = proteins; !found && at < fields.length; at++) {
                    found = fields[at].toLowerCase(Locale.ROOT).contains(needle);
                }
                if (found) {
                    matches++;
                }
            }
        }
        return matches;
    }
}
