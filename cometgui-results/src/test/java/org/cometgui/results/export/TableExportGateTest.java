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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.results.filtering.FilterCounts;
import org.cometgui.results.filtering.QValueFilter;
import org.cometgui.results.filtering.store.Category;
import org.cometgui.results.filtering.store.ResultPage;
import org.cometgui.results.filtering.store.ResultQuery;
import org.cometgui.results.filtering.store.ResultSort;
import org.cometgui.results.filtering.store.ResultStore;
import org.cometgui.results.filtering.store.ResultStores;
import org.cometgui.results.parser.ResultRow;
import org.cometgui.results.testing.IndependentCounter;
import org.cometgui.results.testing.IndependentCounts;
import org.cometgui.results.testing.MiniJson;
import org.cometgui.results.testing.ScratchFixtures;
import org.cometgui.results.testing.TestHasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Phase 10 gate items 4, 5 and 8 for the filtered table export, over every raw table {@link
 * ExportTables} lists -- the constructed, the checked-in real and the real K562 tables -- at the
 * gate's cutoffs 0, 0.005, 0.01 and 1 and in every category.
 *
 * <ul>
 *   <li><b>Gate 4</b>: each raw table's SHA-256, size and modification time are the same after
 *       every store query and every export as before.
 *   <li><b>Gate 5</b>: the sidecar, read back by the test's own {@link MiniJson}, carries the run
 *       ID, the cutoff's text, the four counts before -- equal to counts independent of the code
 *       under test -- the rows after and the CometGUI version.
 *   <li><b>Gate 8</b>: the export's bytes are exactly the header and the selected lines of the raw
 *       file as {@link ExportOracle} selects them, so every row's bytes are the raw line's, and its
 *       rows are exactly the rows the in-memory and the disk store return for the category.
 * </ul>
 */
class TableExportGateTest {

    @TempDir private Path work;

    static Stream<ExportTables.Table> tables() throws IOException {
        return ExportTables.all();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tables")
    @DisplayName("gates 4, 5, 8: every category at every gate cutoff, against the oracle")
    void everyCategoryAtEveryCutoff(ExportTables.Table table) throws IOException {
        Path raw = table.path().get();
        ExportTables.Identity before = ExportTables.Identity.of(raw);
        RunLayout run = ExportTables.newRun(work);
        ResultExporter exporter = ExportTables.exporter(run, ExportTables.ticking());
        int exports = 0;
        for (String cutoff : ExportTables.GATE_CUTOFFS) {
            IndependentCounts pinned = table.counts().get(cutoff);
            for (Category category : Category.values()) {
                String what = table + " at " + cutoff + ", " + category;
                QValueFilter filter = table.filter(cutoff);
                TableExport export = exporter.exportTable(raw, table.kind(), filter, category);
                exports++;
                ExportOracle.Expected expected = ExportOracle.expect(raw, cutoff, category.name());

                assertEquals(pinned, expected.counts(), what + ": the oracle and the pin disagree");
                assertArrayEquals(
                        expected.bytes(),
                        Files.readAllBytes(export.file()),
                        what + ": the export is not the raw header and selected lines");
                assertEquals(expected.rows(), export.rowsWritten(), what);
                assertEquals(rowsOf(category, pinned), export.rowsWritten(), what);
                assertEquals(
                        new FilterCounts(
                                pinned.total(),
                                pinned.passing(),
                                pinned.failing(),
                                pinned.unknown()),
                        export.before(),
                        what);
                sidecarCarriesTheGateFive(export, raw, before, cutoff, category, pinned, what);
            }
        }
        assertEquals(before, ExportTables.Identity.of(raw), table + ": the raw table changed");
        try (Stream<Path> files = Files.list(run.exportsDirectory())) {
            List<String> names = files.map(path -> String.valueOf(path.getFileName())).toList();
            assertEquals(2 * exports, names.size(), "an export and a sidecar each: " + names);
            assertTrue(
                    names.stream()
                            .allMatch(name -> name.endsWith(".tsv") || name.endsWith(".json")),
                    "a temporary file was left behind: " + names);
        }
    }

    private static long rowsOf(Category category, IndependentCounts counts) {
        return switch (category) {
            case PASSING -> counts.passing();
            case FAILING -> counts.failing();
            case UNKNOWN_Q_VALUE -> counts.unknown();
            case ALL -> counts.total();
        };
    }

    @SuppressWarnings("unchecked")
    private static void sidecarCarriesTheGateFive(
            TableExport export,
            Path raw,
            ExportTables.Identity source,
            String cutoff,
            Category category,
            IndependentCounts pinned,
            String what)
            throws IOException {
        Map<String, Object> sidecar =
                (Map<String, Object>)
                        MiniJson.parse(Files.readString(export.sidecar(), StandardCharsets.UTF_8));
        Map<String, Object> filter = (Map<String, Object>) sidecar.get("filter");
        Map<String, Object> counts = (Map<String, Object>) sidecar.get("countsBefore");
        Map<String, Object> file = (Map<String, Object>) sidecar.get("file");
        Map<String, Object> from = (Map<String, Object>) sidecar.get("source");
        assertEquals(new BigDecimal(1), sidecar.get("schemaVersion"), what);
        assertEquals(ExportTables.RUN_ID, sidecar.get("runId"), what);
        assertEquals(ExportTables.VERSION, sidecar.get("cometguiVersion"), what);
        assertEquals(cutoff, filter.get("cutoff"), what + ": the cutoff applied");
        assertEquals(
                export.kind().isPsms() ? "psm-q-value" : "peptide-q-value",
                filter.get("name"),
                what);
        assertEquals(
                Map.of(
                                "PASSING", "passing",
                                "FAILING", "failing",
                                "UNKNOWN_Q_VALUE", "unknown-q-value",
                                "ALL", "all")
                        .get(category.name()),
                sidecar.get("category"),
                what);
        assertEquals(BigDecimal.valueOf(pinned.total()), counts.get("total"), what);
        assertEquals(BigDecimal.valueOf(pinned.passing()), counts.get("passing"), what);
        assertEquals(BigDecimal.valueOf(pinned.failing()), counts.get("failing"), what);
        assertEquals(BigDecimal.valueOf(pinned.unknown()), counts.get("unknownQValue"), what);
        assertEquals(
                BigDecimal.valueOf(rowsOf(category, pinned)), sidecar.get("rowsWritten"), what);
        assertEquals(source.sha256(), from.get("sha256"), what + ": the source's SHA-256");
        assertEquals(BigDecimal.valueOf(source.size()), from.get("size"), what);
        assertEquals(raw.toString(), from.get("path"), what + ": outside the run, absolute");
        assertEquals(ScratchFixtures.sha256(export.file()), file.get("sha256"), what);
        assertEquals(BigDecimal.valueOf(Files.size(export.file())), file.get("size"), what);
        assertEquals(
                "exports/" + export.file().getFileName(), file.get("path"), what + ": the path");
        assertEquals(Boolean.FALSE, sidecar.get("textFilterApplied"), what);
        assertEquals(Boolean.FALSE, sidecar.get("sortApplied"), what);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tables")
    @DisplayName("gates 4, 8: each export holds exactly the rows both stores return for it")
    void exportEqualsTheStores(ExportTables.Table table) throws IOException {
        Path raw = table.path().get();
        ExportTables.Identity before = ExportTables.Identity.of(raw);
        RunLayout run = ExportTables.newRun(work);
        ResultExporter exporter = ExportTables.exporter(run, ExportTables.ticking());
        List<byte[]> lines = ExportOracle.lines(Files.readAllBytes(raw));
        Path index = Files.createDirectories(work.resolve("index"));
        for (boolean onDisk : new boolean[] {false, true}) {
            try (ResultStore store =
                    onDisk
                            ? ResultStores.onDisk(raw, table.kind(), index, new TestHasher())
                            : ResultStores.inMemory(raw, table.kind())) {
                for (String cutoff : ExportTables.GATE_CUTOFFS) {
                    QValueFilter filter = table.filter(cutoff);
                    for (Category category : Category.values()) {
                        String what =
                                table
                                        + (onDisk ? " on disk" : " in memory")
                                        + " at "
                                        + cutoff
                                        + ", "
                                        + category;
                        ByteArrayOutputStream expected = new ByteArrayOutputStream();
                        expected.write(lines.get(0));
                        for (long line : storeLines(store, filter, category)) {
                            expected.write(lines.get((int) line - 1));
                        }
                        TableExport export =
                                exporter.exportTable(raw, table.kind(), filter, category);
                        assertArrayEquals(
                                expected.toByteArray(),
                                Files.readAllBytes(export.file()),
                                what + ": the export's rows are not the store's");
                        assertEquals(store.counts(filter), export.before(), what);
                    }
                }
            }
            assertEquals(
                    before,
                    ExportTables.Identity.of(raw),
                    table + ": the raw table changed after filtering and export");
        }
    }

    /** Every row a store returns for a category, in file order, by line number. */
    private static List<Long> storeLines(ResultStore store, QValueFilter filter, Category category)
            throws IOException {
        List<Long> lines = new ArrayList<>();
        long offset = 0;
        while (true) {
            ResultPage page =
                    store.query(
                            new ResultQuery(
                                    filter,
                                    category,
                                    "",
                                    ResultSort.FILE_ORDER,
                                    offset,
                                    ResultQuery.MAX_PAGE_SIZE));
            for (ResultRow row : page.rows()) {
                lines.add(row.line());
            }
            offset += page.rows().size();
            if (offset >= page.matching()) {
                return lines;
            }
            assertTrue(!page.rows().isEmpty(), "an empty page before the end");
        }
    }

    @Test
    @DisplayName("gate 8: the unknown export holds exactly the unknown rows, every kind included")
    void theUnknownExportHoldsEveryUnknownKind() throws IOException {
        ExportTables.Table table = ExportTables.unknownQ();
        Path raw = table.path().get();
        IndependentCounter.Tally tally = IndependentCounter.count(raw, List.of("0.01"));
        RunLayout run = ExportTables.newRun(work);
        ResultExporter exporter = ExportTables.exporter(run, ExportTables.ticking());
        Map<String, Long> unknownSeen = new TreeMap<>();
        for (String cutoff : ExportTables.GATE_CUTOFFS) {
            TableExport export =
                    exporter.exportTable(
                            raw, table.kind(), table.filter(cutoff), Category.UNKNOWN_Q_VALUE);
            List<byte[]> lines = ExportOracle.lines(Files.readAllBytes(export.file()));
            int column =
                    List.of(ExportOracle.text(lines.get(0)).split("\t", -1)).indexOf("q-value");
            unknownSeen.clear();
            for (byte[] line : lines.subList(1, lines.size())) {
                String q = ExportOracle.text(line).split("\t", -1)[column];
                assertTrue(
                        IndependentCounter.reason(q) != null,
                        "a known q-value '" + q + "' in the unknown export at " + cutoff);
                unknownSeen.merge(q, 1L, Long::sum);
            }
            assertEquals(8, export.rowsWritten(), "the hand count of unknown rows at " + cutoff);
            assertEquals(new TreeMap<>(tally.unknownByText()), unknownSeen, "at " + cutoff);
        }
        Set<String> reasons = new TreeSet<>(tally.reasonByText().values());
        assertEquals(
                Set.of(
                        IndependentCounter.MISSING,
                        IndependentCounter.UNPARSABLE,
                        IndependentCounter.OUT_OF_RANGE),
                reasons,
                "the constructed table holds every kind of unknown q-value");
    }
}
