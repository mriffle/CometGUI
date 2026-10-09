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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.HashService;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.domain.secrets.SecretRegistry;
import org.cometgui.provenance.events.ProvenanceEventLog;
import org.cometgui.provenance.events.ProvenanceEventLogReader;
import org.cometgui.provenance.events.ProvenanceEventType;
import org.cometgui.provenance.events.RecoveredEventLog;
import org.cometgui.results.filtering.FilterCounts;
import org.cometgui.results.filtering.PeptideQValueFilter;
import org.cometgui.results.filtering.PsmQValueFilter;
import org.cometgui.results.filtering.store.Category;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.results.parser.PercolatorOutputException;
import org.cometgui.results.parser.WeightsSummary;
import org.cometgui.results.testing.MiniJson;
import org.cometgui.results.testing.ScratchFixtures;
import org.cometgui.results.testing.TestHasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The filtered table export's behaviour beyond the gate items: its name, never overwriting, the
 * atomic write and what a failure leaves, the provenance event, line terminators, secrets, and
 * exports made at once.
 */
class TableExportBehaviourTest {

    private static final String HEADER_LINE =
            "PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds";

    @TempDir private Path work;

    private Path shuffled() {
        return ExportTables.shuffled().path().get();
    }

    private static List<String> names(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.map(path -> String.valueOf(path.getFileName())).sorted().toList();
        }
    }

    /** A {@code null} SpotBugs cannot constant-fold, as {@code StoreValuesTest} has it. */
    private static <T> T nothing(Class<T> type) {
        return type.cast(null);
    }

    @Test
    @DisplayName(
            "the file name says the table, cutoff, category and time; the sidecar is beside it")
    void theNameIsPinned() throws IOException {
        RunLayout run = ExportTables.newRun(work);
        ResultExporter exporter = ExportTables.exporter(run, ExportTables.fixed());

        TableExport passing =
                exporter.exportTable(
                        shuffled(),
                        TableKind.TARGET_PSMS,
                        PsmQValueFilter.DEFAULT,
                        Category.PASSING);
        TableExport unknown =
                exporter.exportTable(
                        ExportTables.checkedIn("real/percolator-3.09/decoy-peptides.tsv")
                                .path()
                                .get(),
                        TableKind.DECOY_PEPTIDES,
                        PeptideQValueFilter.parse("0.005"),
                        Category.UNKNOWN_Q_VALUE);

        assertEquals(
                run.exportsDirectory()
                        .resolve("target-psms_q0.01_passing_20261009T120000.123Z.tsv"),
                passing.file());
        assertEquals(
                run.exportsDirectory()
                        .resolve("target-psms_q0.01_passing_20261009T120000.123Z.tsv.json"),
                passing.sidecar());
        assertEquals(
                "decoy-peptides_q0.005_unknown-q-value_20261009T120000.123Z.tsv",
                String.valueOf(unknown.file().getFileName()));
        assertEquals(ExportTables.START, passing.created());
        assertEquals(
                List.of(
                        "decoy-peptides_q0.005_unknown-q-value_20261009T120000.123Z.tsv",
                        "decoy-peptides_q0.005_unknown-q-value_20261009T120000.123Z.tsv.json",
                        "target-psms_q0.01_passing_20261009T120000.123Z.tsv",
                        "target-psms_q0.01_passing_20261009T120000.123Z.tsv.json"),
                names(run.exportsDirectory()),
                "an export and its sidecar each, and no temporary file");
    }

    @Test
    @DisplayName("an existing file is never overwritten: a taken name gets the next free suffix")
    void neverOverwrites() throws IOException {
        RunLayout run = ExportTables.newRun(work);
        Path exports = Files.createDirectories(run.exportsDirectory());
        String stem = "target-psms_q0.01_passing_20261009T120000.123Z";
        Path precious = exports.resolve(stem + ".tsv");
        byte[] preciousBytes = "precious, not an export\n".getBytes(StandardCharsets.UTF_8);
        Files.write(precious, preciousBytes);
        Files.writeString(exports.resolve(stem + "-3.tsv.json"), "a sidecar with no export");
        Files.createDirectory(exports.resolve(stem + "-4.tsv"));
        ResultExporter exporter = ExportTables.exporter(run, ExportTables.fixed());

        List<String> made = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            made.add(
                    String.valueOf(
                            exporter.exportTable(
                                            shuffled(),
                                            TableKind.TARGET_PSMS,
                                            PsmQValueFilter.DEFAULT,
                                            Category.PASSING)
                                    .file()
                                    .getFileName()));
        }

        assertEquals(List.of(stem + "-2.tsv", stem + "-5.tsv", stem + "-6.tsv"), made);
        assertArrayEquals(preciousBytes, Files.readAllBytes(precious), "the existing file changed");
        assertEquals(
                "a sidecar with no export",
                Files.readString(exports.resolve(stem + "-3.tsv.json")),
                "the existing sidecar changed");
        assertTrue(Files.isDirectory(exports.resolve(stem + "-4.tsv")));
        assertFalse(Files.exists(exports.resolve(stem + "-3.tsv")));
    }

    @Test
    @DisplayName(
            "a table refused part-way leaves no file under the export's name, and no temporary")
    void aRefusedTableLeavesNothing() throws IOException {
        Path raw = work.resolve("broken.tsv");
        Files.writeString(
                raw,
                HEADER_LINE
                        + "\nr1\t2.0\t0.001\t0.01\tK.AAK.R\tP1\nr2\t1.0\t0.5\n"
                        + "r3\t0.5\t0.002\t0.1\tK.CCK.R\tP2\n",
                StandardCharsets.UTF_8);
        RunLayout run = ExportTables.newRun(work);
        ResultExporter exporter = ExportTables.exporter(run, ExportTables.fixed());

        PercolatorOutputException refused =
                assertThrows(
                        PercolatorOutputException.class,
                        () ->
                                exporter.exportTable(
                                        raw,
                                        TableKind.TARGET_PSMS,
                                        PsmQValueFilter.DEFAULT,
                                        Category.ALL));

        assertTrue(refused.getMessage().contains("Line 3"), refused.getMessage());
        assertEquals(List.of(), names(run.exportsDirectory()), "something was left in exports/");
        assertFalse(Files.exists(run.eventLogFile()), "an event was recorded for no export");
    }

    @Test
    @DisplayName("an event that cannot be recorded removes the export and its sidecar")
    void anUnrecordedExportIsRemoved() throws IOException {
        RunLayout run = ExportTables.newRun(work);
        ResultExporter exporter =
                new ResultExporter(
                        run,
                        new RunId(ExportTables.RUN_ID),
                        ExportTables.VERSION,
                        ExportTables.fixed(),
                        new TestHasher(),
                        SecretRedactor.patternsOnly(),
                        payload -> {
                            assertTrue(
                                    Files.exists(
                                            run.exportsDirectory()
                                                    .resolve(
                                                            "target-psms_q0.01_passing"
                                                                    + "_20261009T120000.123Z.tsv"
                                                                    + ".json")),
                                    "the sidecar is written before the event");
                            throw new IOException("the disk is full");
                        });

        IOException failed =
                assertThrows(
                        IOException.class,
                        () ->
                                exporter.exportTable(
                                        shuffled(),
                                        TableKind.TARGET_PSMS,
                                        PsmQValueFilter.DEFAULT,
                                        Category.PASSING));

        assertEquals(
                "The export target-psms_q0.01_passing_20261009T120000.123Z.tsv could not be"
                        + " completed and has been removed: the disk is full",
                failed.getMessage());
        assertEquals(List.of(), names(run.exportsDirectory()));
    }

    @Test
    @DisplayName("a run without its provenance directory gets no export, since no event can land")
    void noEventLogNoExport() throws IOException {
        RunLayout run = new RunLayout(work.resolve("bare-run"));
        ResultExporter exporter = ExportTables.exporter(run, ExportTables.fixed());

        IOException failed =
                assertThrows(
                        IOException.class,
                        () ->
                                exporter.exportTable(
                                        shuffled(),
                                        TableKind.TARGET_PSMS,
                                        PsmQValueFilter.DEFAULT,
                                        Category.ALL));

        assertTrue(failed.getMessage().contains("has been removed"), failed.getMessage());
        assertEquals(List.of(), names(run.exportsDirectory()));
    }

    @Test
    @DisplayName("a table that changes while it is exported is not exported")
    void aChangingTableIsNotExported() throws IOException {
        Path raw = Files.copy(shuffled(), work.resolve("psms.tsv"));
        TestHasher real = new TestHasher();
        HashService appending =
                path -> {
                    FileHashes hashes = real.hash(path);
                    if (path.equals(raw.toAbsolutePath().normalize())) {
                        Files.writeString(
                                raw,
                                "late\t0\t0.5\t0.5\tK.LATE.R\tP9\n",
                                StandardOpenOption.APPEND);
                    }
                    return hashes;
                };
        RunLayout run = ExportTables.newRun(work);
        ResultExporter exporter =
                new ResultExporter(
                        run,
                        new RunId(ExportTables.RUN_ID),
                        ExportTables.VERSION,
                        ExportTables.fixed(),
                        appending,
                        SecretRedactor.patternsOnly());

        IOException failed =
                assertThrows(
                        IOException.class,
                        () ->
                                exporter.exportTable(
                                        raw,
                                        TableKind.TARGET_PSMS,
                                        PsmQValueFilter.DEFAULT,
                                        Category.ALL));

        assertTrue(
                failed.getMessage().contains("changed while it was being copied for export"),
                failed.getMessage());
        assertEquals(List.of(), names(run.exportsDirectory()));
    }

    @Test
    @DisplayName("each export appends one export.written event, read back from the run's log")
    @SuppressWarnings("unchecked")
    void oneEventPerExport() throws IOException {
        RunLayout run = ExportTables.newRun(work);
        Clock clock = ExportTables.fixed();
        try (ProvenanceEventLog log =
                ProvenanceEventLog.openAppend(
                        run.eventLogFile(), SecretRedactor.patternsOnly(), clock)) {
            log.append(ProvenanceEventType.RUN_STARTED, Map.of("run.id", ExportTables.RUN_ID));
            log.append(ProvenanceEventType.RUN_FINISHED, Map.of("status", "completed"));
        }
        ResultExporter exporter = ExportTables.exporter(run, clock);
        Path raw = ExportTables.unknownQ().path().get();

        TableExport export =
                exporter.exportTable(
                        raw,
                        TableKind.TARGET_PSMS,
                        PsmQValueFilter.parse("0.005"),
                        Category.FAILING);

        List<String> lines = Files.readAllLines(run.eventLogFile(), StandardCharsets.UTF_8);
        assertEquals(3, lines.size(), "exactly one event appended: " + lines);
        Map<String, Object> event = (Map<String, Object>) MiniJson.parse(lines.get(2));
        assertEquals(new BigDecimal(3), event.get("seq"));
        assertEquals("2026-10-09T12:00:00.123Z", event.get("time"));
        assertEquals("export.written", event.get("type"));
        Map<String, Object> expected = new TreeMap<>();
        expected.put("run.id", ExportTables.RUN_ID);
        expected.put("export.kind", "filtered-table");
        expected.put("file.path", "exports/" + export.file().getFileName());
        expected.put("file.md5", export.exportHashes().md5());
        expected.put("file.sha256", ScratchFixtures.sha256(export.file()));
        expected.put("export.sidecar", "exports/" + export.sidecar().getFileName());
        expected.put("export.table", "target-psms");
        expected.put("export.category", "failing");
        expected.put("filter.name", "psm-q-value");
        expected.put("filter.cutoff", "0.005");
        expected.put("source.path", raw.toString());
        expected.put("source.md5", export.sourceHashes().md5());
        expected.put("source.sha256", ExportTables.UNKNOWN_Q_SHA256);
        expected.put("counts.total", "15");
        expected.put("counts.passing", "3");
        expected.put("counts.failing", "4");
        expected.put("counts.unknown-q-value", "8");
        expected.put("rows.written", "4");
        assertEquals(expected, new TreeMap<>((Map<String, Object>) event.get("payload")));
        assertEquals(3, export.eventSequence());
        assertEquals(new FilterCounts(15, 3, 4, 8), export.before());
        RecoveredEventLog recovered = ProvenanceEventLogReader.recover(run.eventLogFile());
        assertTrue(recovered.intact(), () -> "defects: " + recovered.defects());
    }

    @Test
    @DisplayName("a table inside the run is named relative to it; outputs/ is only read")
    void aSourceInsideTheRunIsRelativeAndOnlyRead() throws IOException {
        RunLayout run = ExportTables.newRun(work);
        Path outputs = Files.createDirectories(run.outputsDirectory().resolve("percolator"));
        Path raw =
                Files.copy(
                        shuffled(),
                        outputs.resolve("psms.tsv"),
                        StandardCopyOption.COPY_ATTRIBUTES);
        assertTrue(raw.toFile().setWritable(false, false), "could not make the table read-only");
        ExportTables.Identity before = ExportTables.Identity.of(raw);
        List<String> outputsBefore = names(outputs);
        try {
            ResultExporter exporter = ExportTables.exporter(run, ExportTables.ticking());
            for (Category category : Category.values()) {
                TableExport export =
                        exporter.exportTable(
                                raw, TableKind.TARGET_PSMS, PsmQValueFilter.DEFAULT, category);
                String sidecar = Files.readString(export.sidecar(), StandardCharsets.UTF_8);
                assertTrue(sidecar.contains("\"path\": \"outputs/percolator/psms.tsv\""), sidecar);
                assertTrue(export.file().startsWith(run.exportsDirectory()));
            }
            assertEquals(before, ExportTables.Identity.of(raw));
            assertEquals(outputsBefore, names(outputs), "something was written under outputs/");
            assertEquals(List.of("percolator"), names(run.outputsDirectory()));
            String log = Files.readString(run.eventLogFile(), StandardCharsets.UTF_8);
            assertTrue(log.contains("\"source.path\":\"outputs/percolator/psms.tsv\""), log);
        } finally {
            assertTrue(raw.toFile().setWritable(true, true), "could not make the table writable");
        }
    }

    @Test
    @DisplayName("CR LF, a lone CR and a last row with no terminator are copied byte for byte")
    void terminatorsAreCopiedVerbatim() throws IOException {
        Path raw = work.resolve("mixed.tsv");
        String text =
                HEADER_LINE
                        + "\r\n"
                        + "a\t3.0\t0.001\t0.01\tK.AAK.R\tP1\tP2\r\n"
                        + "b\t2.0\t0.5\t0.2\tK.BBK.R\tP3\r"
                        + "c\t1.5\tNaN\t0.3\tK.CCK.R\tP4\n"
                        + "d\t1.0\t0.01\t0.4\tK.DDK.R\tP5\r\n"
                        + "e\t0.5\t0.002\t0.5\tK.EEK.R\tP6";
        Files.writeString(raw, text, StandardCharsets.UTF_8);
        RunLayout run = ExportTables.newRun(work);
        ResultExporter exporter = ExportTables.exporter(run, ExportTables.ticking());
        for (Category category : Category.values()) {
            TableExport export =
                    exporter.exportTable(
                            raw, TableKind.TARGET_PSMS, PsmQValueFilter.DEFAULT, category);
            assertArrayEquals(
                    ExportOracle.expect(raw, "0.01", category.name()).bytes(),
                    Files.readAllBytes(export.file()),
                    category.name());
        }
        TableExport passing =
                exporter.exportTable(
                        raw, TableKind.TARGET_PSMS, PsmQValueFilter.DEFAULT, Category.PASSING);
        assertEquals(
                HEADER_LINE
                        + "\r\na\t3.0\t0.001\t0.01\tK.AAK.R\tP1\tP2\r\n"
                        + "d\t1.0\t0.01\t0.4\tK.DDK.R\tP5\r\n"
                        + "e\t0.5\t0.002\t0.5\tK.EEK.R\tP6",
                Files.readString(passing.file(), StandardCharsets.UTF_8));
        assertEquals(new FilterCounts(5, 3, 1, 1), passing.before());
    }

    @Test
    @DisplayName("a table with no rows exports its header alone, with or without a terminator")
    void aHeaderOnlyTable() throws IOException {
        RunLayout run = ExportTables.newRun(work);
        ResultExporter exporter = ExportTables.exporter(run, ExportTables.ticking());
        for (String header : List.of(HEADER_LINE + "\n", HEADER_LINE)) {
            Path raw = work.resolve("empty-" + header.length() + ".tsv");
            Files.writeString(raw, header, StandardCharsets.UTF_8);
            TableExport export =
                    exporter.exportTable(
                            raw,
                            TableKind.TARGET_PEPTIDES,
                            PeptideQValueFilter.DEFAULT,
                            Category.ALL);
            assertEquals(header, Files.readString(export.file(), StandardCharsets.UTF_8));
            assertEquals(new FilterCounts(0, 0, 0, 0), export.before());
            assertEquals(0, export.rowsWritten());
        }
    }

    @Test
    @DisplayName("a PSM table is refused a peptide filter, and nothing is written")
    void theOtherTablesFilterIsRefused() throws IOException {
        RunLayout run = ExportTables.newRun(work);
        ResultExporter exporter = ExportTables.exporter(run, ExportTables.fixed());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        exporter.exportTable(
                                shuffled(),
                                TableKind.TARGET_PSMS,
                                PeptideQValueFilter.DEFAULT,
                                Category.PASSING));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        exporter.exportTable(
                                shuffled(),
                                TableKind.DECOY_PEPTIDES,
                                PsmQValueFilter.DEFAULT,
                                Category.PASSING));
        assertEquals(List.of(), names(run.exportsDirectory()));
    }

    @Test
    @DisplayName(
            "a registered secret in the source's path reaches neither the sidecar nor the event")
    void noSecretInTheMetadata() throws IOException {
        String secret = "Sup3rS3cretValue";
        Path directory = Files.createDirectories(work.resolve(secret));
        Path raw = Files.copy(shuffled(), directory.resolve("psms.tsv"));
        RunLayout run = ExportTables.newRun(work);
        ResultExporter exporter =
                new ResultExporter(
                        run,
                        new RunId(ExportTables.RUN_ID),
                        ExportTables.VERSION,
                        ExportTables.fixed(),
                        new TestHasher(),
                        SecretRedactor.with(SecretRegistry.of(secret)));

        TableExport export =
                exporter.exportTable(
                        raw, TableKind.TARGET_PSMS, PsmQValueFilter.DEFAULT, Category.ALL);

        String sidecar = Files.readString(export.sidecar(), StandardCharsets.UTF_8);
        String log = Files.readString(run.eventLogFile(), StandardCharsets.UTF_8);
        assertFalse(sidecar.contains(secret), sidecar);
        assertFalse(log.contains(secret), log);
        assertTrue(sidecar.contains("[REDACTED]"), sidecar);
        assertTrue(log.contains("[REDACTED]"), log);
    }

    @Test
    @DisplayName("exports made at once choose distinct names and number their events without a gap")
    void concurrentExports() throws IOException, InterruptedException, ExecutionException {
        RunLayout run = ExportTables.newRun(work);
        ResultExporter exporter = ExportTables.exporter(run, ExportTables.fixed());
        Path raw = shuffled();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<TableExport>> tasks = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                tasks.add(
                        () ->
                                exporter.exportTable(
                                        raw,
                                        TableKind.TARGET_PSMS,
                                        PsmQValueFilter.DEFAULT,
                                        Category.ALL));
            }
            Set<Path> files = new HashSet<>();
            Set<Long> sequences = new HashSet<>();
            for (Future<TableExport> done : pool.invokeAll(tasks)) {
                files.add(done.get().file());
                sequences.add(done.get().eventSequence());
            }
            assertEquals(threads, files.size());
            assertEquals(Set.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L), sequences);
        } finally {
            pool.shutdownNow();
        }
        RecoveredEventLog recovered = ProvenanceEventLogReader.recover(run.eventLogFile());
        assertTrue(recovered.intact(), () -> "defects: " + recovered.defects());
        assertEquals(threads, recovered.events().size());
        assertEquals(2 * threads, names(run.exportsDirectory()).size());
    }

    @Test
    @DisplayName("the exporter refuses a blank version and missing arguments")
    void arguments() throws IOException {
        RunLayout run = ExportTables.newRun(work);
        RunId id = new RunId(ExportTables.RUN_ID);
        Clock clock = ExportTables.fixed();
        TestHasher hasher = new TestHasher();
        SecretRedactor redactor = SecretRedactor.patternsOnly();
        IllegalArgumentException blank =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new ResultExporter(run, id, " ", clock, hasher, redactor));
        assertEquals("the CometGUI version must not be blank", blank.getMessage());
        assertThrows(
                NullPointerException.class,
                () -> new ResultExporter(null, id, "1", clock, hasher, redactor));
        assertThrows(
                NullPointerException.class,
                () -> new ResultExporter(run, null, "1", clock, hasher, redactor));
        assertThrows(
                NullPointerException.class,
                () -> new ResultExporter(run, id, null, clock, hasher, redactor));
        assertThrows(
                NullPointerException.class,
                () -> new ResultExporter(run, id, "1", null, hasher, redactor));
        assertThrows(
                NullPointerException.class,
                () -> new ResultExporter(run, id, "1", clock, null, redactor));
        assertThrows(
                NullPointerException.class,
                () -> new ResultExporter(run, id, "1", clock, hasher, null));
        ResultExporter exporter = ExportTables.exporter(run, clock);
        assertThrows(
                NullPointerException.class,
                () ->
                        exporter.exportTable(
                                nothing(Path.class),
                                TableKind.TARGET_PSMS,
                                PsmQValueFilter.DEFAULT,
                                Category.ALL));
        assertThrows(
                NullPointerException.class,
                () ->
                        exporter.exportTable(
                                shuffled(),
                                nothing(TableKind.class),
                                PsmQValueFilter.DEFAULT,
                                Category.ALL));
        assertThrows(
                NullPointerException.class,
                () ->
                        exporter.exportTable(
                                shuffled(),
                                TableKind.TARGET_PSMS,
                                nothing(PsmQValueFilter.class),
                                Category.ALL));
        assertThrows(
                NullPointerException.class,
                () ->
                        exporter.exportTable(
                                shuffled(),
                                TableKind.TARGET_PSMS,
                                PsmQValueFilter.DEFAULT,
                                nothing(Category.class)));
        assertThrows(
                NullPointerException.class,
                () -> exporter.exportWeights(nothing(WeightsSummary.class)));
        assertEquals(List.of(), names(run.exportsDirectory()));
    }
}
