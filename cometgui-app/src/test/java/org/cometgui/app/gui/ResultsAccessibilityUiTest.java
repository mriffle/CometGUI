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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Control;
import javafx.scene.control.TableView;
import org.cometgui.app.config.RunWiring;
import org.cometgui.app.testing.InstalledComet;
import org.cometgui.app.testing.ResultRuns;
import org.cometgui.app.uidriver.FxUiDriver;
import org.cometgui.app.uidriver.TestFxUiDriver;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.project.ProjectLayout;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.ui.controls.AccessibleControls;
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
 * The Results section's controls, with a run open ({@code R-RES-01}..{@code R-RES-04}, the
 * specification's <em>Accessibility</em> principle, {@code R-TEST-04}):
 *
 * <ul>
 *   <li>every control the section creates carries a stable identifier and an accessible name of its
 *       own -- not the fallback a skin-built control gets -- including the headings the tables
 *       build only once a run with weights is open;
 *   <li>the filter fields, the category, the text filter, the table and the export actions are
 *       reached with Tab alone;
 *   <li>the run's two spectrum files are shown by name in the source-file column, which cannot be
 *       hidden; another column can;
 *   <li>a heading sorts through the store (the whole table's order, not the page's), a selection is
 *       held by row and follows its row across a sort and a category change, and Copy copies it.
 * </ul>
 *
 * <p>The run is constructed ({@link ResultRuns}) with two spectrum files, {@code sample_A.mzML} and
 * {@code sample_B.mzML}, and a target PSM table written here whose {@code PSMId}s begin with the
 * run's own Comet {@code -N} bases -- {@code <run>/outputs/comet/sample_A} -- as a real run's do:
 *
 * <pre>
 *   PSMId           score  q-value  at 0.01
 *   sample_A_100_2_1  1.5   0.001    passes
 *   sample_B_200_3_1  0.5   0.002    passes
 *   sample_A_300_2_1  2.5   0.005    passes
 *   sample_B_400_2_1 -0.3   0.008    passes
 *   sample_A_500_4_1  0.9   NaN      unknown
 *   sample_B_600_2_1  0.1   0.5      fails
 * </pre>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ResultsAccessibilityUiTest {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-09T00:00:00Z"));

    private static final String RUN = "run-accessibility";

    /** Every identifier the Results section must carry with a run with weights open. */
    private static final List<String> RESULTS_IDS =
            List.of(
                    "results-pane",
                    "results-readiness",
                    "results-run",
                    "results-refresh",
                    "results-table-choice",
                    "results-psm-filter",
                    "results-peptide-filter",
                    "results-filters-status",
                    "results-view-state",
                    "results-count-total",
                    "results-count-passing",
                    "results-count-failing",
                    "results-count-unknown",
                    "results-counts",
                    "results-category",
                    "results-text-filter",
                    "results-first-page",
                    "results-previous-page",
                    "results-next-page",
                    "results-last-page",
                    "results-page",
                    "results-table-status",
                    "results-table",
                    "results-sort-psm-id",
                    "results-sort-source-file",
                    "results-sort-scan",
                    "results-sort-charge",
                    "results-sort-peptide",
                    "results-sort-proteins",
                    "results-sort-score",
                    "results-sort-q-value",
                    "results-sort-pep",
                    "results-column-psm-id",
                    "results-column-source-file",
                    "results-column-scan",
                    "results-column-charge",
                    "results-column-peptide",
                    "results-column-proteins",
                    "results-column-score",
                    "results-column-q-value",
                    "results-column-pep",
                    "results-column-status",
                    "results-selection",
                    "results-copy",
                    "results-copy-status",
                    "results-export-table",
                    "results-export-weights",
                    "results-export-status",
                    "weights-title",
                    "weights-description",
                    "weights-status",
                    "weights-table",
                    "weights-sort-feature",
                    "weights-sort-split-1",
                    "weights-sort-split-2",
                    "weights-sort-split-3",
                    "weights-sort-mean-signed",
                    "weights-sort-mean-absolute",
                    "weights-sort-standard-deviation",
                    "weights-sort-sign-consistency",
                    "weights-sort-rank");

    @TempDir private static Path scratch;

    private static ParameterEditorApp app;

    private static FxUiDriver driver;

    private static List<Throwable> listenerFailures;

    @BeforeAll
    static void launch() throws IOException {
        Path root = scratch.toRealPath();
        ProjectLayout project = ResultRuns.project(root.resolve("project"));
        Path psms = root.resolve("in/psms.tsv");
        RunLayout run =
                ResultRuns.run(
                        project,
                        RUN,
                        Instant.parse("2026-10-09T08:00:00Z"),
                        List.of(
                                root.resolve("data/sample_A.mzML"),
                                root.resolve("data/sample_B.mzML")),
                        Map.of(),
                        Optional.of(
                                ResultRuns.fixture(
                                        ResultRuns.WEIGHTS_3071, root.resolve("in/weights.txt"))));
        String comet = run.cometOutputDirectory().toString();
        Files.createDirectories(root.resolve("in"));
        Files.writeString(
                psms,
                "PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds\n"
                        + comet
                        + "/sample_A_100_2_1\t1.5\t0.001\t0.01\tK.AAAK.R\tsp|P1|A\n"
                        + comet
                        + "/sample_B_200_3_1\t0.5\t0.002\t0.02\tK.CCCK.R\tsp|P2|B\n"
                        + comet
                        + "/sample_A_300_2_1\t2.5\t0.005\t0.001\tK.DDDK.R\tsp|P3|C\n"
                        + comet
                        + "/sample_B_400_2_1\t-0.3\t0.008\t0.3\tK.EEEK.R\tsp|P4|D\n"
                        + comet
                        + "/sample_A_500_4_1\t0.9\tNaN\t0.4\tK.FFFK.R\tsp|P5|E\n"
                        + comet
                        + "/sample_B_600_2_1\t0.1\t0.5\t0.9\tK.GGGK.R\tsp|P6|F\n",
                StandardCharsets.UTF_8);
        Path target = RunResultFiles.table(run, TableKind.TARGET_PSMS);
        Files.createDirectories(RunResultFiles.percolatorOutputDirectory(run));
        Files.copy(psms, target);
        app =
                ParameterEditorApp.launch(
                        BUILD,
                        null,
                        new BoundedMessageLog(),
                        new RunWiring.Setup(InstalledComet::nothing, root.resolve("project")));
        driver = new TestFxUiDriver(app.application());
        listenerFailures = ResultsSection.recordListenerFailures(driver);
        driver.clickOn("nav-results");
        ResultsSection.awaitOpened(driver, RUN, ResultsSection.OPEN_BOUND);
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
        RunSection.awaitText(
                driver,
                "weights-status",
                text -> text.startsWith("3 cross-validation splits"),
                ResultsSection.QUERY_BOUND);
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
            "every Results control is there by its identifier, with an accessible name of its own")
    void everyControlHasItsOwnName() {
        List<Control> controls = ParameterEditorApp.controlsUnder(driver, "results-pane");
        Set<String> seen = new LinkedHashSet<>();
        List<String> unnamed = new ArrayList<>();
        for (Control control : controls) {
            String id = driver.callOnFxThread(control::getId);
            String name = driver.callOnFxThread(control::getAccessibleText);
            if (name == null || name.isBlank()) {
                unnamed.add(
                        (id == null ? "" : "#" + id) + " " + control.getClass().getSimpleName());
            }
            if (id == null || AccessibleNameEnumerationUiTest.SKIN_IDENTIFIERS.contains(id)) {
                continue;
            }
            seen.add(id);
            if (driver.callOnFxThread(() -> AccessibleControls.hasGeneratedName(control))) {
                unnamed.add("#" + id + " has only the generated name \"" + name + "\"");
            }
        }
        List<String> missing = new ArrayList<>(RESULTS_IDS);
        missing.removeAll(seen);
        assertAll(
                () -> assertEquals(List.of(), unnamed, "controls with no name of their own"),
                () -> assertEquals(List.of(), missing, "identifiers the walk did not find"),
                () ->
                        assertEquals(
                                "Learned feature weights (Percolator SVM)",
                                driver.textOf("weights-title")),
                () ->
                        assertEquals(
                                "Sort the table by q-value",
                                driver.accessibleTextOf("results-sort-q-value")),
                () ->
                        assertEquals(
                                "Show the Source file column",
                                driver.accessibleTextOf("results-column-source-file")));
    }

    @Test
    @Order(2)
    @DisplayName("the filters, category, text filter, table and exports are reached with Tab alone")
    void keyboardReachesTheControls() {
        driver.clickOn("nav-results");
        driver.clickOn("results-psm-filter");
        assertEquals("results-psm-filter", driver.focusedNodeId());
        List<String> reached = new ArrayList<>();
        for (int press = 0; press < 80; press++) {
            driver.tab();
            reached.add(driver.focusedNodeId());
        }
        for (String wanted :
                List.of(
                        "results-peptide-filter",
                        "results-category",
                        "results-text-filter",
                        "results-table",
                        "results-copy",
                        "results-export-table",
                        "results-export-weights",
                        "weights-table")) {
            assertTrue(reached.contains(wanted), () -> wanted + " was not reached: " + reached);
        }
        assertTrue(
                reached.indexOf("results-peptide-filter") < reached.indexOf("results-table"),
                () -> "the filters come before the table: " + reached);
    }

    @Test
    @Order(3)
    @DisplayName(
            "two spectrum files are shown by name; the source-file column cannot be hidden, PEP"
                    + " can")
    void theSourceFileColumn() {
        driver.clickOn("nav-results");
        CheckBox source = (CheckBox) driver.node("results-column-source-file");
        assertAll(
                () ->
                        assertEquals(
                                List.of(
                                        "sample_A.mzML",
                                        "sample_B.mzML",
                                        "sample_A.mzML",
                                        "sample_B.mzML"),
                                ResultsSection.column(driver, 1)),
                () ->
                        assertEquals(
                                List.of("100", "200", "300", "400"),
                                ResultsSection.column(driver, 2)),
                () -> assertTrue(driver.callOnFxThread(source::isSelected)),
                () -> assertTrue(driver.callOnFxThread(source::isDisabled)),
                () ->
                        assertEquals(
                                "This column cannot be hidden: the run has several spectrum files.",
                                driver.callOnFxThread(source::getAccessibleHelp)));
        driver.clickOn("results-column-pep");
        TableView<?> table = (TableView<?>) driver.node("results-table");
        assertFalse(
                driver.callOnFxThread(() -> table.getColumns().get(8).isVisible()),
                "the PEP column is hidden");
        driver.clickOn("results-column-pep");
        assertTrue(driver.callOnFxThread(() -> table.getColumns().get(8).isVisible()));
    }

    @Test
    @Order(4)
    @DisplayName(
            "a heading sorts through the store; a selected row is held by key across a sort and a"
                    + " category change; Copy copies it")
    void sortSelectionAndCopy() {
        driver.clickOn("nav-results");
        ResultsSection.selectRow(driver, 2);
        assertEquals(
                "1 row is selected: row 3 of 4, on page 1.", driver.textOf("results-selection"));

        driver.clickOn("results-sort-score");
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
        assertAll(
                "sorted by score, ascending",
                () -> assertEquals("Score, ascending", driver.textOf("results-sort-score")),
                () ->
                        assertEquals(
                                List.of("-0.3", "0.5", "1.5", "2.5"),
                                ResultsSection.column(driver, 6)),
                () -> assertEquals(List.of(3), ResultsSection.selectedIndices(driver)),
                () ->
                        assertEquals(
                                "1 row is selected: row 4 of 4, on page 1.",
                                driver.textOf("results-selection")));

        ParameterEditorApp.choose(driver, "results-category", "All rows");
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
        assertAll(
                "every row, still by score",
                () ->
                        assertEquals(
                                List.of("-0.3", "0.1", "0.5", "0.9", "1.5", "2.5"),
                                ResultsSection.column(driver, 6)),
                () -> assertEquals(List.of(5), ResultsSection.selectedIndices(driver)));

        driver.clickOn("results-copy");
        assertEquals(
                "Copied 1 row with 9 columns as tab-separated text.",
                driver.textOf("results-copy-status"));
        ParameterEditorApp.choose(driver, "results-category", "Passing rows");
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
        driver.clickOn("results-sort-score");
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
        driver.clickOn("results-sort-score");
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
        assertEquals("Score", driver.textOf("results-sort-score"), "back to file order");
        ResultsSection.assertNoListenerFailed(listenerFailures);
    }
}
