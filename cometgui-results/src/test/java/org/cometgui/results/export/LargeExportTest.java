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

package org.cometgui.results.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.results.filtering.FilterCounts;
import org.cometgui.results.filtering.PsmQValueFilter;
import org.cometgui.results.filtering.store.Category;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.results.testing.IndependentCounts;
import org.cometgui.results.testing.LargeFixture;
import org.cometgui.results.testing.ScratchFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The export on the 1 000 000-row large fixture (design decision P10-2): the counts and rows equal
 * the generator's manifest, every exported line is the raw line of a row in the category, and -- in
 * a child JVM with {@code -Xmx}{@value #HEAP} -- the whole 147 MB table exports without being held
 * in memory, its bytes equal to the raw file's.
 *
 * <p>Measured on the phase 10 host (2026-10-09; see the unit 6 report): passing at 0.01 in this
 * JVM, and every row in the child at {@value #HEAP}. The time budget is {@value #BUDGET_MS} ms per
 * export, several times the measurement.
 */
class LargeExportTest {

    /** The child JVM's heap. */
    static final String HEAP = "32m";

    /** The time budget for one export of the large table, hashing included. */
    static final long BUDGET_MS = 30_000;

    private static final long CHILD_TIMEOUT_SECONDS = 180;

    @TempDir private Path work;

    @Test
    @DisplayName("passing at 0.01: 219 777 rows, the manifest's counts, each line the raw line")
    void passingAtTheDefault() throws IOException {
        LargeFixture fixture = LargeFixture.locate();
        Path raw = fixture.psms();
        IndependentCounts manifest = fixture.expected(LargeFixture.PSMS, "0.01");
        RunLayout run = ExportTables.newRun(work);
        ResultExporter exporter = ExportTables.exporter(run, ExportTables.fixed());
        Runtime runtime = Runtime.getRuntime();
        System.gc();
        long usedBefore = runtime.totalMemory() - runtime.freeMemory();
        long start = System.nanoTime();

        TableExport export =
                exporter.exportTable(
                        raw, TableKind.TARGET_PSMS, PsmQValueFilter.DEFAULT, Category.PASSING);

        long millis = (System.nanoTime() - start) / 1_000_000;
        long usedAfter = runtime.totalMemory() - runtime.freeMemory();
        System.out.println(
                "LargeExportTest passing at 0.01: "
                        + millis
                        + " ms, heap used before "
                        + usedBefore / (1 << 20)
                        + " MB, after "
                        + usedAfter / (1 << 20)
                        + " MB, export "
                        + Files.size(export.file())
                        + " bytes");
        assertEquals(219_777, export.rowsWritten(), "the manifest's passing count at 0.01");
        assertEquals(manifest.passing(), export.rowsWritten());
        assertEquals(
                new FilterCounts(
                        manifest.total(),
                        manifest.passing(),
                        manifest.failing(),
                        manifest.unknown()),
                export.before());
        assertTrue(millis < BUDGET_MS, "took " + millis + " ms");
        assertEquals(manifest.passing(), matchRawInOrder(raw, export.file(), "PASSING", "0.01"));
    }

    /**
     * Walks the raw table and the export together, one line at a time: every export line must be
     * the next raw line in the category, and no raw line in it may be skipped.
     *
     * @return the rows matched
     */
    private static long matchRawInOrder(Path raw, Path export, String category, String cutoff)
            throws IOException {
        BigDecimal limit = new BigDecimal(cutoff);
        long matched = 0;
        try (BufferedReader rawLines = Files.newBufferedReader(raw, StandardCharsets.UTF_8);
                BufferedReader exportLines =
                        Files.newBufferedReader(export, StandardCharsets.UTF_8)) {
            String header = rawLines.readLine();
            assertEquals(header, exportLines.readLine(), "the header");
            int column = List.of(header.split("\t", -1)).indexOf("q-value");
            for (String line = rawLines.readLine(); line != null; line = rawLines.readLine()) {
                String where = ExportOracle.visibility(line.split("\t", -1)[column], limit);
                if (where.equals(category)) {
                    assertEquals(line, exportLines.readLine(), "row " + (matched + 1));
                    matched++;
                }
            }
            assertEquals(null, exportLines.readLine(), "the export has a row too many");
        }
        return matched;
    }

    @Test
    @DisplayName("the unknown export holds exactly the manifest's unknown rows, kind by kind")
    void unknownRowsByKind() throws IOException {
        LargeFixture fixture = LargeFixture.locate();
        Path raw = fixture.psms();
        RunLayout run = ExportTables.newRun(work);
        TableExport export =
                ExportTables.exporter(run, ExportTables.fixed())
                        .exportTable(
                                raw,
                                TableKind.TARGET_PSMS,
                                PsmQValueFilter.DEFAULT,
                                Category.UNKNOWN_Q_VALUE);

        Map<String, Long> expected = new TreeMap<>();
        for (LargeFixture.UnknownKind kind : fixture.unknownKinds(LargeFixture.PSMS)) {
            expected.merge(kind.text(), kind.rows(), Long::sum);
        }
        Map<String, Long> seen = new TreeMap<>();
        try (BufferedReader lines =
                Files.newBufferedReader(export.file(), StandardCharsets.UTF_8)) {
            String header = lines.readLine();
            assertTrue(header != null, "the export has no header");
            int column = List.of(header.split("\t", -1)).indexOf("q-value");
            for (String line = lines.readLine(); line != null; line = lines.readLine()) {
                seen.merge(line.split("\t", -1)[column], 1L, Long::sum);
            }
        }
        assertEquals(expected, seen);
        assertEquals(1478, export.rowsWritten());
        assertEquals(fixture.expected(LargeFixture.PSMS, "0.01").unknown(), export.rowsWritten());
        assertEquals(
                fixture.expected(LargeFixture.PSMS, "0.01").unknown(),
                matchRawInOrder(raw, export.file(), "UNKNOWN_Q_VALUE", "0.01"));
    }

    @Test
    @DisplayName("in a " + HEAP + " heap, every row exports and the export equals the raw file")
    void everyRowInASmallHeap() throws IOException, InterruptedException {
        LargeFixture fixture = LargeFixture.locate();
        Map<String, String> all = child(fixture.psms(), "1", "ALL");
        System.out.println("LargeExportTest child -Xmx" + HEAP + ", ALL: " + all);
        assertEquals("1000000", all.get("rows"));
        assertEquals(
                fixture.recordedSha256(LargeFixture.PSMS),
                all.get("sha256"),
                "exporting every row of an LF table reproduces the raw file byte for byte");
        assertTrue(Long.parseLong(all.get("ms")) < BUDGET_MS, "took " + all.get("ms") + " ms");
        assertEquals(
                ScratchFixtures.sha256(Path.of(all.get("file"))),
                all.get("sha256"),
                "the sidecar's hash is the file's");

        Map<String, String> passing = child(fixture.psms(), "0.01", "PASSING");
        System.out.println("LargeExportTest child -Xmx" + HEAP + ", PASSING 0.01: " + passing);
        assertEquals("219777", passing.get("rows"));
        assertEquals("1478", passing.get("unknown"));
        assertTrue(Long.parseLong(passing.get("ms")) < BUDGET_MS);
    }

    private Map<String, String> child(Path table, String cutoff, String category)
            throws IOException, InterruptedException {
        Path directory = Files.createDirectories(work.resolve("child-" + category));
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-Xmx" + HEAP);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(ExportProbe.class.getName());
        command.add(table.toString());
        command.add(directory.toString());
        command.add(cutoff);
        command.add(category);
        Path log = work.resolve("child-" + category + ".log");
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
        assertEquals(0, child.exitValue(), "the child failed: " + output);
        for (String line : output) {
            if (line.startsWith(ExportProbe.RESULT + " ")) {
                Map<String, String> fields = new LinkedHashMap<>();
                for (String field : line.substring(ExportProbe.RESULT.length() + 1).split(" ")) {
                    int equals = field.indexOf('=');
                    fields.put(field.substring(0, equals), field.substring(equals + 1));
                }
                return fields;
            }
        }
        throw new AssertionError("the child printed no result: " + output);
    }
}
