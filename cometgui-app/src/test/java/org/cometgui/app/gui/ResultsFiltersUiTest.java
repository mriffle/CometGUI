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

import static org.cometgui.app.gui.ParameterEditorApp.enter;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.cometgui.app.config.RunWiring;
import org.cometgui.app.testing.InstalledComet;
import org.cometgui.app.testing.ResultRuns;
import org.cometgui.app.uidriver.FxUiDriver;
import org.cometgui.app.uidriver.TestFxUiDriver;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.project.ProjectLayout;
import org.cometgui.results.filtering.store.TableKind;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

/**
 * Phase 10 exit-gate item 1 on screen ({@code R-RES-01}, {@code AC-RES-01}..{@code 03}): both
 * filters show 0.01 by default; a row at exactly the cutoff passes; text outside {@code [0, 1]} or
 * not a number is refused with the model's own message and changes no count; and the two filters
 * are independent.
 *
 * <p>The run is constructed ({@link ResultRuns}): its target PSM table is {@code
 * psms-unknown-q.tsv} and its target peptide table {@code psms-shuffled.tsv}. Every count below is
 * worked out by hand from those files, row by row, and typed out -- never computed by the code
 * under test:
 *
 * <ul>
 *   <li><strong>PSMs</strong>, 15 rows, q-values {@code c01 0.001, c02 0.01, c03 0.0100001, c04
 *       NaN, c05 (empty), c06 inf, c07 0,005, c08 0, c09 1, c10 1.5, c11 -0.01, c12 1e-3, c13
 *       0.010, c14 Infinity, c15 nan}. Unknown (missing, unparsable or out of range): c04, c05,
 *       c06, c07, c10, c11, c14, c15 = 8 at every cutoff. Passing at 0.0099999: c01, c08, c12 = 3
 *       (failing c02, c03, c09, c13 = 4); at 0.01: those and c02, c13 = 5 (failing c03, c09 = 2);
 *       at 0.0100001: and c03 = 6 (failing c09 = 1); at 0.5: 6 (failing c09 = 1).
 *   <li><strong>Peptides</strong>, 23 rows, q-values {@code 0.5, 0, 0.01, 0.005, 1, 0.0100001,
 *       0.00999999, NaN, (empty), 0.005, 1.5, 1e-3, 0.01, -nan, 0.2, 0,01, 0.3, -0.1, 0.004999, 0,
 *       0.05, Infinity, 0.02}. Unknown: NaN, empty, 1.5, -nan, 0,01, -0.1, Infinity = 7. Passing at
 *       0.01: 0, 0.01, 0.005, 0.00999999, 0.005, 1e-3, 0.01, 0.004999, 0 = 9 (failing 7); at 0.005:
 *       0, 0.005, 0.005, 1e-3, 0.004999, 0 = 6 (failing 10).
 * </ul>
 *
 * <p>The text is typed and committed with Enter, by the robot, into the Results section's own
 * fields; the Percolator section's fields, which show the same one filter state, are read back.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ResultsFiltersUiTest {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-09T00:00:00Z"));

    private static final String RUN = "run-gate-one";

    /** The display filters' explanation, typed out: the model's own words. */
    private static final String EXPLANATION =
            "The PSM and the peptide q-value filters (each 0.01 by default, from 0 to 1, a q-value"
                    + " equal to the cutoff passing) change only which PSMs and peptides are"
                    + " displayed and exported. Changing them never reruns Percolator or any other"
                    + " tool, and they are not Percolator's testFDR or trainFDR, the learning"
                    + " thresholds under Advanced settings.";

    @TempDir private static Path scratch;

    private static ParameterEditorApp app;

    private static FxUiDriver driver;

    private static List<Throwable> listenerFailures;

    @BeforeAll
    static void launch() throws IOException {
        Path root = scratch.toRealPath();
        ProjectLayout project = ResultRuns.project(root.resolve("project"));
        ResultRuns.run(
                project,
                RUN,
                Instant.parse("2026-10-09T08:00:00Z"),
                List.of(root.resolve("data/sample_A.mzML")),
                Map.of(
                        TableKind.TARGET_PSMS,
                        ResultRuns.fixture(ResultRuns.PSMS_UNKNOWN_Q, root.resolve("in/psms.tsv")),
                        TableKind.TARGET_PEPTIDES,
                        ResultRuns.fixture(
                                ResultRuns.PSMS_SHUFFLED, root.resolve("in/peptides.tsv"))),
                Optional.of(
                        ResultRuns.fixture(
                                ResultRuns.WEIGHTS_3071, root.resolve("in/weights.txt"))));
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
    }

    @AfterAll
    static void stop() {
        if (app != null) {
            app.stop();
        }
    }

    @Test
    @Order(1)
    @DisplayName("gate 1: both filters show 0.01 by default, here and in the Percolator section")
    void defaults() {
        driver.clickOn("nav-results");
        assertAll(
                () -> assertEquals("0.01", driver.textOf("results-psm-filter")),
                () -> assertEquals("0.01", driver.textOf("results-peptide-filter")),
                () -> assertEquals(EXPLANATION, driver.textOf("results-filters-status")),
                () ->
                        assertEquals(
                                "Target PSMs",
                                ParameterEditorApp.comboText(driver, "results-table-choice")),
                () -> assertEquals(List.of("15", "5", "2", "8"), ResultsSection.counts(driver)),
                () ->
                        assertEquals(
                                ResultsSection.summary("target PSMs", "0.01", 15, 5, 2, 8),
                                driver.textOf("results-counts")));
        driver.clickOn("nav-percolator");
        assertAll(
                () -> assertEquals("0.01", driver.textOf("percolator-psm-filter")),
                () -> assertEquals("0.01", driver.textOf("percolator-peptide-filter")));
    }

    @Test
    @Order(2)
    @DisplayName(
            "gate 1: a row at exactly the cutoff passes -- 0.0099999, then 0.01, then 0.0100001")
    void inclusiveAtTheCutoff() {
        driver.clickOn("nav-results");
        assertEquals(List.of("15", "3", "4", "8"), psmCountsAt("0.0099999"), "just below 0.01");
        assertEquals(
                List.of("15", "5", "2", "8"),
                psmCountsAt("0.01"),
                "exactly 0.01: the rows at 0.01 and 0.010 pass");
        assertEquals(
                List.of("15", "6", "1", "8"),
                psmCountsAt("0.0100001"),
                "exactly 0.0100001: the row at 0.0100001 passes too");
        assertEquals(
                ResultsSection.summary("target PSMs", "0.0100001", 15, 6, 1, 8),
                driver.textOf("results-counts"));
    }

    @Test
    @Order(3)
    @DisplayName(
            "gate 1: 1.5, -0.1, abc and 0,01 are refused on screen with the model's message, and"
                    + " no count changes")
    void outOfRangeAndNonNumbersAreRefused() {
        driver.clickOn("nav-results");
        enter(driver, "results-psm-filter", "0.01");
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
        List<String> before = ResultsSection.counts(driver);
        assertEquals(List.of("15", "5", "2", "8"), before);
        Map<String, String> refusals =
                Map.of(
                        "1.5",
                        "The PSM q-value filter must be between 0 and 1 inclusive, but was 1.5",
                        "-0.1",
                        "The PSM q-value filter must be between 0 and 1 inclusive, but was -0.1",
                        "abc",
                        "The PSM q-value filter must be a number between 0 and 1 inclusive,"
                                + " written with a '.' decimal point, but was 'abc'",
                        "0,01",
                        "The PSM q-value filter must be a number between 0 and 1 inclusive,"
                                + " written with a '.' decimal point, but was '0,01'");
        for (String refused : List.of("1.5", "-0.1", "abc", "0,01")) {
            enter(driver, "results-psm-filter", refused);
            ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
            String status =
                    EXPLANATION + "\nThe PSM filter's text is not used: " + refusals.get(refused);
            assertAll(
                    "PSM filter " + refused,
                    () -> assertEquals(refused, driver.textOf("results-psm-filter")),
                    () -> assertEquals(status, driver.textOf("results-filters-status")),
                    () ->
                            assertEquals(
                                    status,
                                    driver.callOnFxThread(
                                            () ->
                                                    driver.node("results-psm-filter")
                                                            .getAccessibleHelp()),
                                    "the refusal is in the field's accessible help"),
                    () -> assertEquals(before, ResultsSection.counts(driver), "no count changed"));
        }
        enter(driver, "results-peptide-filter", "1.5");
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
        assertAll(
                "peptide filter 1.5",
                () ->
                        assertEquals(
                                EXPLANATION
                                        + "\nThe PSM filter's text is not used: "
                                        + refusals.get("0,01")
                                        + "\nThe peptide filter's text is not used: The peptide"
                                        + " q-value filter must be between 0 and 1 inclusive, but"
                                        + " was 1.5",
                                driver.textOf("results-filters-status")),
                () -> assertEquals(before, ResultsSection.counts(driver), "no count changed"));
        enter(driver, "results-peptide-filter", "0.01");
        enter(driver, "results-psm-filter", "0.01");
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
        assertEquals(EXPLANATION, driver.textOf("results-filters-status"), "accepted again");
    }

    @Test
    @Order(4)
    @DisplayName("gate 1: the PSM filter does not change the peptide counts, and vice versa")
    void theFiltersAreIndependent() {
        driver.clickOn("nav-results");
        ParameterEditorApp.choose(driver, "results-table-choice", "Target peptides");
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
        assertEquals(
                List.of("23", "9", "7", "7"), ResultsSection.counts(driver), "peptides at 0.01");

        enter(driver, "results-psm-filter", "0.5");
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
        assertAll(
                "the PSM filter changed",
                () -> assertEquals("0.5", driver.textOf("results-psm-filter")),
                () -> assertEquals("0.01", driver.textOf("results-peptide-filter")),
                () ->
                        assertEquals(
                                List.of("23", "9", "7", "7"),
                                ResultsSection.counts(driver),
                                "the peptide counts did not move"));

        enter(driver, "results-peptide-filter", "0.005");
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
        assertAll(
                "the peptide filter changed",
                () -> assertEquals("0.5", driver.textOf("results-psm-filter")),
                () -> assertEquals(List.of("23", "6", "10", "7"), ResultsSection.counts(driver)),
                () ->
                        assertEquals(
                                ResultsSection.summary("target peptides", "0.005", 23, 6, 10, 7),
                                driver.textOf("results-counts")));

        ParameterEditorApp.choose(driver, "results-table-choice", "Target PSMs");
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
        assertEquals(
                List.of("15", "6", "1", "8"),
                ResultsSection.counts(driver),
                "the PSMs at the PSM filter 0.5, whatever the peptide filter");

        driver.clickOn("nav-percolator");
        assertAll(
                "the one filter state, shown in the Percolator section",
                () -> assertEquals("0.5", driver.textOf("percolator-psm-filter")),
                () -> assertEquals("0.005", driver.textOf("percolator-peptide-filter")));
    }

    @Test
    @Order(5)
    @DisplayName("no JavaFX listener threw while the filters were driven")
    void noListenerThrew() {
        ResultsSection.assertNoListenerFailed(listenerFailures);
    }

    /** Types a PSM cutoff, commits it with Enter, waits for the answer, and reads the counts. */
    private static List<String> psmCountsAt(String cutoff) {
        enter(driver, "results-psm-filter", cutoff);
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
        assertEquals(cutoff, driver.textOf("results-psm-filter"));
        return ResultsSection.counts(driver);
    }
}
