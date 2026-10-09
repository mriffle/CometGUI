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

package org.cometgui.app.gui;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.cometgui.app.config.RunWiring;
import org.cometgui.app.testing.IndependentCounts;
import org.cometgui.app.testing.IndependentCounts.Row;
import org.cometgui.app.testing.IndependentCounts.Where;
import org.cometgui.app.testing.InstalledComet;
import org.cometgui.app.testing.K562Outputs;
import org.cometgui.app.testing.ResultRuns;
import org.cometgui.app.testing.TestJson;
import org.cometgui.app.uidriver.FxUiDriver;
import org.cometgui.app.uidriver.TestFxUiDriver;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.project.ProjectLayout;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.workflow.steps.RunResultFiles;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

/**
 * Phase 10 exit-gate items 4, 5 and 8 through the interface's own buttons ({@code R-RES-01}, {@code
 * R-RES-02}, {@code R-RES-04}, {@code R-PERC-07}, {@code AC-RES-04}):
 *
 * <ul>
 *   <li><strong>Gate 8</strong> -- a row whose q-value is missing, unparsable or out of range is
 *       counted and shown as its own category, and exported as exactly that category: the unknown
 *       count on screen, the rows the unknown category shows on every page, the rows the export of
 *       that category holds and its sidecar's unknown count are one set; the passing export holds
 *       none of them.
 *   <li><strong>Gate 5</strong> -- an export made with the Export button at a non-default cutoff
 *       carries, in a sidecar read back here with {@link TestJson} (not the product's model), the
 *       run's ID, the cutoff as typed, the four counts before and the count after -- each equal to
 *       the independent count -- and the version of CometGUI the application was launched as; the
 *       status on screen names the file and the row count; the file holds that many rows, the very
 *       rows the independent counter puts in that category, byte for byte; and the run's {@code
 *       provenance/events.log} gained exactly one {@code export.written} event naming the file and
 *       the cutoff.
 *   <li><strong>Gate 4</strong> -- after every filter change and every export of this class (four
 *       categories, three tables, two runs, and the weights), each file under each run's {@code
 *       outputs/} has the SHA-256, size and modification time it had before; nothing new is under
 *       {@code outputs/}; and every export is under {@code exports/}.
 * </ul>
 *
 * <p><strong>The runs</strong> (constructed through the run store, {@link ResultRuns}):
 *
 * <ul>
 *   <li>{@code run-unknown}: target PSMs {@code psms-unknown-q.tsv} (15 rows, 8 unknown: {@code c04
 *       NaN}, {@code c05} empty, {@code c06 inf}, {@code c07 0,005}, {@code c10 1.5}, {@code c11
 *       -0.01}, {@code c14 Infinity}, {@code c15 nan}); target peptides {@code psms-shuffled.tsv}
 *       (23 rows, 7 unknown; at 0.05: 12 passing -- one at exactly 0.05 --, 4 failing); and decoy
 *       PSMs <em>written by this test</em>, 1203 rows of which every third (401) has an unknown
 *       q-value of one of ten kinds, so that the unknown category fills three pages of 200.
 *   <li>{@code run-k562}: the real K562 Percolator 3.07.1 output ({@link K562Outputs}, from {@code
 *       scratch/}, SHA-256 pinned; fails, never skips, without it) and its weights.
 * </ul>
 *
 * <p>Every expected count is the {@code awk}-pinned one ({@link K562Outputs#AWK}), or {@link
 * IndependentCounts} run on the run's own raw bytes, or both.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ResultsExportUiTest {

    /** A version no default could produce, so that the sidecar's can only have come from here. */
    private static final String VERSION = "9.8.7-gate5";

    private static final BuildIdentity BUILD =
            BuildIdentity.of(VERSION, "unknown", Instant.parse("2026-10-09T00:00:00Z"));

    private static final String UNKNOWN_RUN = "run-unknown";

    private static final String K562 = "run-k562";

    /** The unknown q-values the generated table cycles through. */
    private static final List<String> UNKNOWN_KINDS =
            List.of("", "NaN", "nan", "inf", "-inf", "Infinity", "0,01", "1.5", "-0.01", "abc");

    /** The known q-values it cycles through. */
    private static final List<String> KNOWN =
            List.of("0", "0.001", "0.005", "0.01", "0.0100001", "0.05", "0.5", "1");

    private static final int GENERATED_ROWS = 1203;

    @TempDir private static Path scratch;

    private static RunLayout unknownRun;

    private static RunLayout k562;

    /** Each run's {@code outputs/} before anything was done: path to size, time and SHA-256. */
    private static final Map<String, String> BEFORE = new TreeMap<>();

    /** Each run's whole tree before anything was done. */
    private static final Set<String> TREE_BEFORE = new TreeSet<>();

    /** Every export the interface reported, as the absolute paths of the file and its sidecar. */
    private static final Set<Path> EXPORTED = new HashSet<>();

    private static ParameterEditorApp app;

    private static FxUiDriver driver;

    private static List<Throwable> listenerFailures;

    @BeforeAll
    static void launch() throws IOException {
        Path root = scratch.toRealPath();
        ProjectLayout project = ResultRuns.project(root.resolve("project"));
        Path generated = root.resolve("in/generated-decoy-psms.tsv");
        Files.createDirectories(root.resolve("in"));
        Files.writeString(generated, generatedTable(), StandardCharsets.UTF_8);
        unknownRun =
                ResultRuns.run(
                        project,
                        UNKNOWN_RUN,
                        Instant.parse("2026-10-09T09:00:00Z"),
                        List.of(root.resolve("data/sample_A.mzML")),
                        Map.of(
                                TableKind.TARGET_PSMS,
                                ResultRuns.fixture(
                                        ResultRuns.PSMS_UNKNOWN_Q, root.resolve("in/psms.tsv")),
                                TableKind.TARGET_PEPTIDES,
                                ResultRuns.fixture(
                                        ResultRuns.PSMS_SHUFFLED, root.resolve("in/peptides.tsv")),
                                TableKind.DECOY_PSMS,
                                generated),
                        Optional.of(
                                ResultRuns.fixture(
                                        ResultRuns.WEIGHTS_3071, root.resolve("in/weights.txt"))));
        k562 =
                ResultRuns.run(
                        project,
                        K562,
                        Instant.parse("2026-10-09T08:00:00Z"),
                        List.of(root.resolve("data/k562_3.mzML"), root.resolve("data/k562_4.mzML")),
                        K562Outputs.tables(root.resolve("k562")),
                        Optional.of(K562Outputs.weights(root.resolve("k562-weights"))));
        for (RunLayout run : List.of(unknownRun, k562)) {
            BEFORE.putAll(outputs(run));
            TREE_BEFORE.addAll(tree(run));
        }
        assertEquals(
                9,
                BEFORE.size(),
                "the two runs' raw outputs: 3 tables + weights, 4 tables + weights");
        app =
                ParameterEditorApp.launch(
                        BUILD,
                        null,
                        new BoundedMessageLog(),
                        new RunWiring.Setup(InstalledComet::nothing, root.resolve("project")));
        driver = new TestFxUiDriver(app.application());
        listenerFailures = ResultsSection.recordListenerFailures(driver);
        driver.clickOn("nav-results");
        ResultsSection.awaitOpened(driver, UNKNOWN_RUN, ResultsSection.OPEN_BOUND);
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
    }

    @AfterAll
    static void stop() {
        if (app != null) {
            app.stop();
        }
    }

    @Test
    @Order(1)
    @DisplayName(
            "gate 8: the unknown q-value rows are counted, shown and exported as one set; the"
                    + " passing export holds none of them")
    void theUnknownCategoryOnScreenAndInTheExport() throws IOException {
        driver.clickOn("nav-results");
        ResultsSection.showRun(driver, UNKNOWN_RUN);
        ResultsSection.filters(driver, "0.01", "0.01");
        ResultsSection.showTable(driver, "Target PSMs");
        Path raw = RunResultFiles.table(unknownRun, TableKind.TARGET_PSMS);
        List<Row> rows = IndependentCounts.rows(raw, "0.01");
        List<Row> unknown = IndependentCounts.only(rows, Where.UNKNOWN);
        assertEquals(
                List.of("c04", "c05", "c06", "c07", "c10", "c11", "c14", "c15"),
                unknown.stream().map(Row::psmId).toList(),
                "the counter agrees with the hand count");
        assertEquals(
                IndependentCounts.counts(raw, "0.01"),
                ResultsSection.counts(driver),
                "the four counts shown");
        assertEquals("8", driver.textOf("results-count-unknown"));

        category("Rows with an unknown q-value");
        assertEquals(
                ids(unknown),
                ResultsSection.column(driver, 0),
                "the unknown category shows exactly the unknown rows, in file order");

        Export unknownExport =
                exportTable(
                        unknownRun,
                        UNKNOWN_RUN,
                        "target-psms",
                        "0.01",
                        "unknown-q-value",
                        raw,
                        rows,
                        Where.UNKNOWN);
        assertEquals(
                driver.textOf("results-count-unknown"),
                unknownExport.sidecarCount("countsBefore", "unknownQValue"),
                "the sidecar's unknown count is the count on screen");

        category("Passing rows");
        Export passing =
                exportTable(
                        unknownRun,
                        UNKNOWN_RUN,
                        "target-psms",
                        "0.01",
                        "passing",
                        raw,
                        rows,
                        Where.PASSING);
        Set<String> unknownIds = new HashSet<>(ids(unknown));
        for (String row : passing.rows()) {
            assertFalse(
                    unknownIds.contains(row.split("\t", 2)[0]),
                    () -> "an unknown q-value row in the passing export: " + row);
        }
        assertEquals(5, passing.rows().size(), "c01, c02, c08, c12, c13");
    }

    @Test
    @Order(2)
    @DisplayName(
            "gate 8: 401 unknown rows over three pages -- every page shown, then exported -- are"
                    + " exactly the counter's")
    void theUnknownCategoryOverSeveralPages() throws IOException {
        driver.clickOn("nav-results");
        ResultsSection.showRun(driver, UNKNOWN_RUN);
        ResultsSection.showTable(driver, "Decoy PSMs");
        Path raw = RunResultFiles.table(unknownRun, TableKind.DECOY_PSMS);
        String cutoff = driver.textOf("results-psm-filter");
        List<Row> rows = IndependentCounts.rows(raw, cutoff);
        List<Row> unknown = IndependentCounts.only(rows, Where.UNKNOWN);
        assertEquals(401, unknown.size(), "every third of 1203 rows, by construction");
        assertEquals(IndependentCounts.counts(raw, cutoff), ResultsSection.counts(driver));

        category("Rows with an unknown q-value");
        List<String> shown = new ArrayList<>();
        assertEquals("Rows 1 to 200 of 401 (page 1 of 3)", driver.textOf("results-page"));
        shown.addAll(ResultsSection.column(driver, 0));
        for (String page :
                List.of(
                        "Rows 201 to 400 of 401 (page 2 of 3)",
                        "Rows 401 to 401 of 401 (page 3 of 3)")) {
            driver.clickOn("results-next-page");
            RunSection.awaitText(driver, "results-page", page::equals, ResultsSection.QUERY_BOUND);
            ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
            shown.addAll(ResultsSection.column(driver, 0));
        }
        assertEquals(ids(unknown), shown, "the unknown rows of all three pages, in file order");

        exportTable(
                unknownRun,
                UNKNOWN_RUN,
                "decoy-psms",
                cutoff,
                "unknown-q-value",
                raw,
                rows,
                Where.UNKNOWN);
        category("Failing rows");
        exportTable(
                unknownRun, UNKNOWN_RUN, "decoy-psms", cutoff, "failing", raw, rows, Where.FAILING);
    }

    @Test
    @Order(3)
    @DisplayName(
            "gate 5: the constructed peptides exported at 0.05 -- a row at exactly 0.05 -- as their"
                    + " failing rows; the sidecar, status, file and event agree with the counter")
    void aPeptideExportAtANonDefaultCutoff() throws IOException {
        driver.clickOn("nav-results");
        ResultsSection.showRun(driver, UNKNOWN_RUN);
        ResultsSection.filters(driver, "0.01", "0.05");
        ResultsSection.showTable(driver, "Target peptides");
        Path raw = RunResultFiles.table(unknownRun, TableKind.TARGET_PEPTIDES);
        List<Row> rows = IndependentCounts.rows(raw, "0.05");
        assertEquals(
                List.of("23", "12", "4", "7"),
                IndependentCounts.counts(raw, "0.05"),
                "the hand count of psms-shuffled.tsv at 0.05");
        assertEquals(List.of("23", "12", "4", "7"), ResultsSection.counts(driver));
        category("Failing rows");
        Export export =
                exportTable(
                        unknownRun,
                        UNKNOWN_RUN,
                        "target-peptides",
                        "0.05",
                        "failing",
                        raw,
                        rows,
                        Where.FAILING);
        assertEquals("peptide-q-value", export.sidecar("filter", "name"));
    }

    @Test
    @Order(4)
    @DisplayName(
            "gate 5: the real K562 target PSMs exported at 0.05 as their 1171 passing rows, and the"
                    + " decoy peptides at 0.05 as their 2326 failing rows: the awk pins throughout")
    void realExportsAtANonDefaultCutoff() throws IOException {
        driver.clickOn("nav-results");
        ResultsSection.showRun(driver, K562);
        ResultsSection.filters(driver, "0.05", "0.05");

        ResultsSection.showTable(driver, "Target PSMs");
        Path psms = RunResultFiles.table(k562, TableKind.TARGET_PSMS);
        assertEquals(K562Outputs.awk(TableKind.TARGET_PSMS, "0.05"), ResultsSection.counts(driver));
        Export passing =
                exportTable(
                        k562,
                        K562,
                        "target-psms",
                        "0.05",
                        "passing",
                        psms,
                        IndependentCounts.rows(psms, "0.05"),
                        Where.PASSING);
        assertAll(
                "the awk pins",
                () -> assertEquals("1171", passing.sidecarCount("rowsWritten")),
                () -> assertEquals(1171, passing.rows().size()),
                () -> assertEquals("3897", passing.sidecarCount("countsBefore", "total")),
                () -> assertEquals("1171", passing.sidecarCount("countsBefore", "passing")),
                () -> assertEquals("2726", passing.sidecarCount("countsBefore", "failing")),
                () -> assertEquals("0", passing.sidecarCount("countsBefore", "unknownQValue")),
                () -> assertEquals("psm-q-value", passing.sidecar("filter", "name")));

        ResultsSection.showTable(driver, "Decoy peptides");
        Path decoys = RunResultFiles.table(k562, TableKind.DECOY_PEPTIDES);
        assertEquals(
                K562Outputs.awk(TableKind.DECOY_PEPTIDES, "0.05"), ResultsSection.counts(driver));
        category("Failing rows");
        Export failing =
                exportTable(
                        k562,
                        K562,
                        "decoy-peptides",
                        "0.05",
                        "failing",
                        decoys,
                        IndependentCounts.rows(decoys, "0.05"),
                        Where.FAILING);
        assertAll(
                "the awk pins",
                () -> assertEquals("2326", failing.sidecarCount("rowsWritten")),
                () -> assertEquals("2359", failing.sidecarCount("countsBefore", "total")),
                () -> assertEquals("33", failing.sidecarCount("countsBefore", "passing")));
    }

    @Test
    @Order(5)
    @DisplayName("the weights are exported with the button: a file, a sidecar and one event")
    void theWeightsExport() throws IOException {
        driver.clickOn("nav-results");
        ResultsSection.showRun(driver, K562);
        Set<Path> before = exports(k562);
        int events = exportEvents(k562).size();
        String status = ResultsSection.export(driver, "results-export-weights");
        List<Path> made = made(k562, before);
        Path file = made.get(0);
        Path sidecar = made.get(1);
        assertEquals(
                "Exported the learned feature weights of 22 features over 3 splits to "
                        + file
                        + ". Its metadata is in "
                        + sidecar
                        + ".",
                status);
        assertTrue(
                String.valueOf(file.getFileName())
                        .matches("learned-feature-weights_[0-9T.]+Z\\.tsv"),
                () -> "the name " + file.getFileName());
        Map<String, Object> json = TestJson.object(Files.readString(sidecar));
        assertAll(
                () -> assertEquals("learned-feature-weights", json.get("export")),
                () -> assertEquals(K562, json.get("runId")),
                () -> assertEquals(VERSION, json.get("cometguiVersion")),
                () -> assertEquals(new BigDecimal(3), json.get("splitCount")),
                () -> assertEquals(new BigDecimal(22), json.get("featureCount")),
                () ->
                        assertEquals(
                                K562Outputs.WEIGHTS_SHA256, TestJson.at(json, "source", "sha256")));
        assertEquals(events + 1, exportEvents(k562).size(), "exactly one export.written event");
        EXPORTED.add(file);
        EXPORTED.add(sidecar);
    }

    @Test
    @Order(6)
    @DisplayName(
            "gate 4: after every filter change and export, every raw output is byte-identical with"
                    + " its time unchanged; nothing new is under outputs/; every export is under"
                    + " exports/")
    void theRawOutputsAreUntouched() throws IOException {
        Map<String, String> after = new TreeMap<>();
        Set<String> treeAfter = new TreeSet<>();
        for (RunLayout run : List.of(unknownRun, k562)) {
            after.putAll(outputs(run));
            treeAfter.addAll(tree(run));
        }
        assertEquals(BEFORE, after, "every file under outputs/: SHA-256, size and time");
        assertEquals(16, EXPORTED.size(), "eight exports, each a file and its sidecar");
        List<String> outside = new ArrayList<>();
        for (String path : treeAfter) {
            if (TREE_BEFORE.contains(path)) {
                continue;
            }
            boolean derived =
                    path.matches(".*/runs/[^/]+/(exports|results)(/.*)?")
                            || path.matches(".*/runs/[^/]+/provenance(/events\\.log)?");
            if (!derived) {
                outside.add(path);
            }
            assertFalse(path.contains("/outputs/"), () -> "new under outputs/: " + path);
        }
        assertEquals(List.of(), outside, "new files outside exports/, results/ and the event log");
        for (Path exported : EXPORTED) {
            assertEquals(
                    "exports",
                    String.valueOf(Objects.requireNonNull(exported.getParent()).getFileName()),
                    () -> exported + " is under exports/");
            assertTrue(treeAfter.contains(exported.toString()), () -> exported + " exists");
        }
        ResultsSection.assertNoListenerFailed(listenerFailures);
    }

    // ===================================================================== exports ====

    /** One export the interface made: its file, sidecar and rows. */
    private record Export(
            Path file, Path sidecarFile, Map<String, Object> json, List<String> rows) {

        String sidecar(String... path) {
            return String.valueOf(TestJson.at(json, path));
        }

        String sidecarCount(String... path) {
            return ((BigDecimal) TestJson.at(json, path)).toPlainString();
        }
    }

    /**
     * Presses Export this table and checks everything about the export against the counter's rows
     * of the category: the status, the name, the file's rows byte for byte, the sidecar, and the
     * one event.
     */
    private static Export exportTable(
            RunLayout run,
            String runId,
            String table,
            String cutoff,
            String category,
            Path raw,
            List<Row> rows,
            Where where)
            throws IOException {
        String rawSha256 = ResultRuns.sha256(raw);
        Set<Path> before = exports(run);
        List<Map<String, Object>> eventsBefore = exportEvents(run);
        List<String> shownCounts = ResultsSection.counts(driver);

        String status = ResultsSection.export(driver, "results-export-table");

        List<Path> made = made(run, before);
        Path file = made.get(0);
        Path sidecar = made.get(1);
        List<Row> expected = IndependentCounts.only(rows, where);
        List<String> counts = counts(rows);
        assertEquals(counts, shownCounts, "the counts on screen when the export was made");
        String rowsName = table.replace('-', ' ').replace("psms", "PSMs");
        String categoryWords =
                switch (category) {
                    case "passing" -> "passing rows";
                    case "failing" -> "failing rows";
                    case "unknown-q-value" -> "rows with an unknown q-value";
                    default -> "rows, all of them";
                };
        assertEquals(
                "Exported "
                        + expected.size()
                        + (expected.size() == 1 ? " row" : " rows")
                        + " -- the "
                        + categoryWords
                        + " of the "
                        + rowsName
                        + " table at a q-value cutoff of "
                        + cutoff
                        + ", of "
                        + rows.size()
                        + " in total -- to "
                        + file
                        + ". Its metadata is in "
                        + sidecar
                        + ".",
                status,
                "the status names the file and the row count");
        assertTrue(
                String.valueOf(file.getFileName())
                        .matches(
                                table
                                        + "_q"
                                        + cutoff.replace(".", "\\.")
                                        + "_"
                                        + category
                                        + "_[0-9]{8}T[0-9]{6}\\.[0-9]{3}Z\\.tsv"),
                () -> "the export's name " + file.getFileName());

        String header = Files.readAllLines(raw, StandardCharsets.UTF_8).get(0) + "\n";
        StringBuilder wanted = new StringBuilder(header);
        for (Row row : expected) {
            wanted.append(row.bytes());
        }
        String written = Files.readString(file, StandardCharsets.UTF_8);
        assertEquals(
                wanted.toString(),
                written,
                "the export is the header and exactly the counter's rows of the category, verbatim,"
                        + " in file order");
        List<String> exportedRows = new ArrayList<>(List.of(written.split("\n", -1)));
        exportedRows.remove(exportedRows.size() - 1);
        exportedRows.remove(0);

        Map<String, Object> json = TestJson.object(Files.readString(sidecar));
        String relative = "exports/" + file.getFileName();
        assertAll(
                "the sidecar " + sidecar.getFileName(),
                () -> assertEquals(new BigDecimal(1), json.get("schemaVersion")),
                () -> assertEquals("filtered-table", json.get("export")),
                () -> assertEquals(runId, json.get("runId"), "the run's ID"),
                () -> assertEquals(VERSION, json.get("cometguiVersion")),
                () -> assertEquals(relative, TestJson.at(json, "file", "path")),
                () ->
                        assertEquals(
                                new BigDecimal(Files.size(file)),
                                TestJson.at(json, "file", "size")),
                () -> assertEquals(ResultRuns.sha256(file), TestJson.at(json, "file", "sha256")),
                () -> assertEquals(table, TestJson.at(json, "source", "table")),
                () ->
                        assertEquals(
                                "outputs/percolator/" + raw.getFileName(),
                                TestJson.at(json, "source", "path")),
                () -> assertEquals(rawSha256, TestJson.at(json, "source", "sha256")),
                () -> assertEquals(cutoff, TestJson.at(json, "filter", "cutoff")),
                () -> assertEquals(category, json.get("category")),
                () ->
                        assertEquals(
                                counts,
                                List.of(
                                        plain(TestJson.at(json, "countsBefore", "total")),
                                        plain(TestJson.at(json, "countsBefore", "passing")),
                                        plain(TestJson.at(json, "countsBefore", "failing")),
                                        plain(TestJson.at(json, "countsBefore", "unknownQValue"))),
                                "the four counts before"),
                () ->
                        assertEquals(
                                Integer.toString(expected.size()),
                                plain(json.get("rowsWritten")),
                                "the count after"),
                () ->
                        assertEquals(
                                expected.size(),
                                exportedRows.size(),
                                "the file's rows are the count after"),
                () -> assertEquals(Boolean.FALSE, json.get("textFilterApplied")),
                () -> assertEquals(Boolean.FALSE, json.get("sortApplied")));

        List<Map<String, Object>> eventsAfter = exportEvents(run);
        assertEquals(
                eventsBefore.size() + 1,
                eventsAfter.size(),
                "exactly one export.written event was appended");
        assertEquals(eventsBefore, eventsAfter.subList(0, eventsBefore.size()), "and nothing else");
        @SuppressWarnings("unchecked")
        Map<String, Object> payload =
                (Map<String, Object>) eventsAfter.get(eventsAfter.size() - 1).get("payload");
        assertAll(
                "the event's payload",
                () -> assertEquals(relative, payload.get("file.path")),
                () -> assertEquals(cutoff, payload.get("filter.cutoff")),
                () -> assertEquals(category, payload.get("export.category")),
                () -> assertEquals(runId, payload.get("run.id")),
                () -> assertEquals(Integer.toString(expected.size()), payload.get("rows.written")));
        EXPORTED.add(file);
        EXPORTED.add(sidecar);
        return new Export(file, sidecar, json, exportedRows);
    }

    private static String plain(Object number) {
        return ((BigDecimal) number).toPlainString();
    }

    /** The counter's four counts of its rows. */
    private static List<String> counts(List<Row> rows) {
        return List.of(
                Integer.toString(rows.size()),
                Integer.toString(IndependentCounts.only(rows, Where.PASSING).size()),
                Integer.toString(IndependentCounts.only(rows, Where.FAILING).size()),
                Integer.toString(IndependentCounts.only(rows, Where.UNKNOWN).size()));
    }

    /** The files in a run's {@code exports/}. */
    private static Set<Path> exports(RunLayout run) throws IOException {
        Path directory = run.exportsDirectory();
        if (!Files.isDirectory(directory)) {
            return Set.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return new TreeSet<>(files.toList());
        }
    }

    /** The two files an export added: the table or weights file, then its sidecar. */
    private static List<Path> made(RunLayout run, Set<Path> before) throws IOException {
        List<Path> added = new ArrayList<>(exports(run));
        added.removeAll(before);
        assertEquals(2, added.size(), () -> "one export and its sidecar were added: " + added);
        added.sort(null);
        assertEquals(
                added.get(0).getFileName() + ".json",
                String.valueOf(added.get(1).getFileName()),
                "the sidecar is beside the export");
        return added;
    }

    /** The {@code export.written} events in a run's event log, read with the test's own reader. */
    private static List<Map<String, Object>> exportEvents(RunLayout run) throws IOException {
        Path log = run.eventLogFile();
        if (!Files.exists(log)) {
            return List.of();
        }
        List<Map<String, Object>> events = new ArrayList<>();
        for (String line : Files.readAllLines(log, StandardCharsets.UTF_8)) {
            Map<String, Object> event = TestJson.object(line);
            if ("export.written".equals(event.get("type"))) {
                events.add(event);
            }
        }
        return events;
    }

    // ===================================================================== helpers ====

    private static void category(String label) {
        ParameterEditorApp.choose(driver, "results-category", label);
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
    }

    private static List<String> ids(List<Row> rows) {
        return rows.stream().map(Row::psmId).toList();
    }

    /** Every file under a run's {@code outputs/}: relative path to size, time and SHA-256. */
    private static Map<String, String> outputs(RunLayout run) throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        Path outputs = run.outputsDirectory();
        try (Stream<Path> walk = Files.walk(outputs)) {
            for (Path file : walk.filter(Files::isRegularFile).sorted().toList()) {
                files.put(
                        file.toString(),
                        Files.size(file)
                                + " "
                                + Files.getLastModifiedTime(file)
                                + " "
                                + ResultRuns.sha256(file));
            }
        }
        return files;
    }

    /** Every path in a run's directory, files and directories, absolute. */
    private static Set<String> tree(RunLayout run) throws IOException {
        try (Stream<Path> walk = Files.walk(run.root())) {
            return new TreeSet<>(walk.map(Path::toString).toList());
        }
    }

    /** The decoy PSM table this test constructs: every third row's q-value unknown. */
    private static String generatedTable() {
        StringBuilder table =
                new StringBuilder(
                        "PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds\n");
        for (int row = 1; row <= GENERATED_ROWS; row++) {
            String q =
                    row % 3 == 0
                            ? UNKNOWN_KINDS.get((row / 3) % UNKNOWN_KINDS.size())
                            : KNOWN.get(row % KNOWN.size());
            table.append(String.format(Locale.ROOT, "gen_%04d", row))
                    .append('\t')
                    .append(String.format(Locale.ROOT, "%.3f", 3.0 - row / 400.0))
                    .append('\t')
                    .append(q)
                    .append("\t0.01\tK.PEPTIDE")
                    .append(row % 26)
                    .append(".R\tdecoy_sp|P")
                    .append(row)
                    .append("|X\n");
        }
        return table.toString();
    }
}
