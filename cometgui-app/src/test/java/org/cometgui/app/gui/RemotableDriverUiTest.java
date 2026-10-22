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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.cometgui.app.config.RunWiring;
import org.cometgui.app.e2e.oracle.QValueCounts;
import org.cometgui.app.testing.InstalledComet;
import org.cometgui.app.testing.ResultRuns;
import org.cometgui.app.uidriver.FxUiDriver;
import org.cometgui.app.uidriver.RobotFxUiDriver;
import org.cometgui.app.uidriver.RunningApplication;
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
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The remotable driver methods Phase 14 unit 1 added (P14-3) -- {@code exists}, {@code isDisabled},
 * {@code pressKey}, {@code pressKeyWith}, {@code select}, {@code selectedTextOf}, {@code itemsOf},
 * {@code columnsOf} and {@code rowsOf} -- proved against the real application through both drivers,
 * as the navigation tests prove the original ones.
 *
 * <p><strong>The application</strong> is the real {@code CometGuiApplication}, started as the other
 * Results GUI tests start it ({@link ParameterEditorApp}), over a constructed project with one
 * succeeded run ({@link ResultRuns}): {@code psms-unknown-q.tsv} as its target PSMs (15 rows),
 * {@code psms-shuffled.tsv} as its target peptides (23 rows) and {@code weights-two-splits.txt} as
 * its weights. Every file read is a checked-in fixture of this module; nothing outside it is read.
 *
 * <p><strong>The expected values</strong> are typed out, and each one is also recomputed by an
 * independent party where one exists: the counts and the PSM identifiers a table shows by {@link
 * QValueCounts} reading the fixture, the weights by hand from the fixture's numbers (the class
 * comment of {@code PercolatorWeightsTest} shows the arithmetic; for this file, {@code
 * WEIGHTS-SUMMARY.txt} in {@code cometgui-results} records Python's).
 *
 * <p><strong>The one thing the real application cannot show here</strong> is a {@code ListView}
 * with rows: its only lists are the Provenance section's, and a constructed run has no sealed
 * record, so they are empty. The list half of {@code rowsOf} -- a default cell and a cell factory
 * of its own -- is therefore proved on two lists the test shows in a second window of the same
 * running toolkit, last, and that window is closed again.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RemotableDriverUiTest {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-22T00:00:00Z"));

    private static final String RUN = "run-driver";

    /** The results table's nine columns, as their headings say. */
    private static final List<String> RESULT_COLUMNS =
            List.of(
                    "PSMId",
                    "Source file",
                    "Scan",
                    "Charge",
                    "Peptide",
                    "Proteins",
                    "Score",
                    "q-value",
                    "PEP");

    /**
     * The five target PSMs at q &lt;= 0.01, as the table shows them: the fixture's rows c01, c02,
     * c08, c12 and c13 (q 0.001, 0.01, 0, 1e-3, 0.010), the proteins joined with ", ", and no
     * source file, scan or charge, which a constructed PSMId does not carry.
     */
    private static final List<String> PSMS_AT_ONE_PERCENT =
            List.of(
                    "c01\t\t\t\tK.AAAAAAK.R\tsp|P1|A, sp|P2|B, sp|P3|C\t2.5\t0.001\t1e-05",
                    "c02\t\t\t\tK.CCCCCCK.R\tsp|P4|D\t2.1\t0.01\t0.002",
                    "c08\t\t\t\tK.IIIIIIK.R\tsp|P11|K\t0.8\t0\t0.05",
                    "c12\t\t\t\tK.NNNNNNK.R\tsp|P16|P\t0.2\t1e-3\t0.06",
                    "c13\t\t\t\tK.PPPPPPK.R\tsp|P17|Q\t0.15\t0.010\t0.07");

    private static final List<String> WEIGHT_COLUMNS =
            List.of(
                    "Feature",
                    "Split 1",
                    "Split 2",
                    "Mean signed",
                    "Mean absolute",
                    "Standard deviation",
                    "Sign consistency",
                    "Rank");

    /**
     * {@code weights-two-splits.txt} on screen, worked by hand: lnrSp 0.125 and 0.13 (mean 0.1275,
     * deviation 0.0025); deltLCn -0.03 and -0.025 (-0.0275, 0.0025); Xcorr 1.5 and 1.45 (1.475,
     * 0.025); Charge2 0.2 and -0.1 (mean 0.05, mean absolute 0.15, deviation 0.15); m0 -2.5 and
     * -2.4 (-2.45, 0.05). Ranked by mean absolute: Xcorr 1, Charge2 2, lnrSp 3, deltLCn 4.
     */
    private static final List<String> WEIGHT_ROWS =
            List.of(
                    "lnrSp\t0.1250\t0.1300\t0.127500\t0.127500\t0.002500"
                            + "\tall positive (2 positive, 0 negative, 0 zero)\t3",
                    "deltLCn\t-0.0300\t-0.0250\t-0.027500\t0.027500\t0.002500"
                            + "\tall negative (0 positive, 2 negative, 0 zero)\t4",
                    "Xcorr\t1.5000\t1.4500\t1.475000\t1.475000\t0.025000"
                            + "\tall positive (2 positive, 0 negative, 0 zero)\t1",
                    "Charge2\t0.2000\t-0.1000\t0.050000\t0.150000\t0.150000"
                            + "\tmixed (1 positive, 1 negative, 0 zero)\t2",
                    "m0 (bias term, not ranked)\t-2.5000\t-2.4000\t-2.450000\t2.450000\t0.050000"
                            + "\tall negative (0 positive, 2 negative, 0 zero)\t");

    @TempDir private static Path scratch;

    private static ParameterEditorApp app;

    private static Path psms;

    private static Path peptides;

    @BeforeAll
    static void launch() throws IOException {
        Path root = scratch.toRealPath();
        ProjectLayout project = ResultRuns.project(root.resolve("project"));
        psms = ResultRuns.fixture(ResultRuns.PSMS_UNKNOWN_Q, root.resolve("in/psms.tsv"));
        peptides = ResultRuns.fixture(ResultRuns.PSMS_SHUFFLED, root.resolve("in/peptides.tsv"));
        ResultRuns.run(
                project,
                RUN,
                Instant.parse("2026-10-22T08:00:00Z"),
                List.of(root.resolve("data/sample_A.mzML")),
                Map.of(TableKind.TARGET_PSMS, psms, TableKind.TARGET_PEPTIDES, peptides),
                Optional.of(
                        ResultRuns.fixture(
                                ResultRuns.WEIGHTS_TWO_SPLITS, root.resolve("in/weights.txt"))));
        app =
                ParameterEditorApp.launch(
                        BUILD,
                        null,
                        new BoundedMessageLog(),
                        new RunWiring.Setup(InstalledComet::nothing, root.resolve("project")));
        FxUiDriver driver = new TestFxUiDriver(app.application());
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

    static Stream<FxUiDriver> drivers() {
        return drivers(app.application());
    }

    private static Stream<FxUiDriver> drivers(RunningApplication application) {
        assertNotNull(application, "the application must be running before a driver is built");
        return Stream.of(new TestFxUiDriver(application), new RobotFxUiDriver(application));
    }

    /** Shows the Results section on the target PSMs at the default cutoff, whatever came before. */
    private static void atTheStart(FxUiDriver driver) {
        driver.clickOn("nav-results");
        driver.select("results-table-choice", "Target PSMs");
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
        if (!driver.textOf("results-psm-filter").equals("0.01")) {
            ParameterEditorApp.enter(driver, "results-psm-filter", "0.01");
            ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
        }
    }

    private static List<String> oracle(Path table, String cutoff) throws IOException {
        QValueCounts counts = QValueCounts.of(table, cutoff);
        return Stream.of(counts.total(), counts.passing(), counts.failing(), counts.unknown())
                .map(String::valueOf)
                .toList();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("drivers")
    @Order(1)
    @DisplayName("exists: a control in the scene is there, shown or not; a removed one is not")
    void exists(FxUiDriver driver) {
        assertAll(
                driver.toString(),
                () -> assertTrue(driver.exists("shell-root")),
                () -> assertTrue(driver.exists("results-table")),
                () -> assertTrue(driver.exists("run-start"), "in the scene, though not showing"),
                () -> assertFalse(driver.isVisible("run-start"), "while Results is shown"),
                () -> assertFalse(driver.exists("nav-settings"), "the removed Settings entry"),
                () -> assertFalse(driver.exists("no-such-control")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("drivers")
    @Order(2)
    @DisplayName(
            "isDisabled: Run is disabled with no Comet installed, the PSM filter is not -- each as"
                    + " the node itself says")
    void isDisabled(FxUiDriver driver) {
        Node run = driver.node("run-start");
        Node filter = driver.node("results-psm-filter");
        assertAll(
                driver.toString(),
                () -> assertTrue(driver.isDisabled("run-start")),
                () ->
                        assertEquals(
                                driver.callOnFxThread(run::isDisabled),
                                driver.isDisabled("run-start")),
                () -> assertFalse(driver.isDisabled("results-psm-filter")),
                () ->
                        assertEquals(
                                driver.callOnFxThread(filter::isDisabled),
                                driver.isDisabled("results-psm-filter")),
                () ->
                        assertTrue(
                                assertThrows(
                                                AssertionError.class,
                                                () -> driver.isDisabled("no-such-control"))
                                        .getMessage()
                                        .startsWith(
                                                "no node with the stable identifier"
                                                        + " #no-such-control ")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("drivers")
    @Order(3)
    @DisplayName(
            "select, selectedTextOf, itemsOf: choosing Target peptides by its text shows the"
                    + " peptide table's counts, which the oracle recomputes; and back")
    void selectByText(FxUiDriver driver) throws IOException {
        atTheStart(driver);
        assertEquals(
                List.of("Target PSMs", "Target peptides"), driver.itemsOf("results-table-choice"));
        assertEquals("Target PSMs", driver.selectedTextOf("results-table-choice"));
        assertEquals(List.of("15", "5", "2", "8"), ResultsSection.counts(driver));

        driver.select("results-table-choice", "Target peptides");
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
        assertAll(
                driver.toString(),
                () ->
                        assertEquals(
                                "Target peptides", driver.selectedTextOf("results-table-choice")),
                () -> assertEquals(List.of("23", "9", "7", "7"), ResultsSection.counts(driver)),
                () -> assertEquals(oracle(peptides, "0.01"), ResultsSection.counts(driver)),
                () ->
                        assertEquals(
                                QValueCounts.of(peptides, "0.01").passing(),
                                driver.rowsOf("results-table").size(),
                                "one table row per passing peptide"));

        driver.select("results-table-choice", "Target PSMs");
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
        assertEquals("Target PSMs", driver.selectedTextOf("results-table-choice"));
        assertEquals(oracle(psms, "0.01"), ResultsSection.counts(driver));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("drivers")
    @Order(4)
    @DisplayName(
            "select refuses an item that is not offered, naming what is, and changes nothing; a"
                    + " text field is not a combo box")
    void selectRefuses(FxUiDriver driver) {
        atTheStart(driver);
        AssertionError refused =
                assertThrows(
                        AssertionError.class,
                        () -> driver.select("results-table-choice", "Decoy PSMs"));
        assertAll(
                driver.toString(),
                () ->
                        assertEquals(
                                "#results-table-choice offers no item shown as \"Decoy PSMs\"; it"
                                        + " offers [Target PSMs, Target peptides]",
                                refused.getMessage()),
                () -> assertEquals("Target PSMs", driver.selectedTextOf("results-table-choice")),
                () ->
                        assertEquals(
                                "#results-psm-filter is a javafx.scene.control.TextField, not a"
                                        + " combo box",
                                assertThrows(
                                                AssertionError.class,
                                                () -> driver.selectedTextOf("results-psm-filter"))
                                        .getMessage()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("drivers")
    @Order(5)
    @DisplayName(
            "columnsOf and rowsOf: the results table's nine headings and its five rows at 0.01,"
                    + " the PSMs the oracle passes")
    void theResultsTable(FxUiDriver driver) throws IOException {
        atTheStart(driver);
        List<String> rows = driver.rowsOf("results-table");
        assertAll(
                driver.toString(),
                () -> assertEquals(RESULT_COLUMNS, driver.columnsOf("results-table")),
                () -> assertEquals(PSMS_AT_ONE_PERCENT, rows),
                () -> assertEquals(QValueCounts.of(psms, "0.01").passingIds(), firstColumn(rows)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("drivers")
    @Order(6)
    @DisplayName("columnsOf and rowsOf: the learned weights, every cell as worked out by hand")
    void theWeightsTable(FxUiDriver driver) {
        atTheStart(driver);
        assertAll(
                driver.toString(),
                () -> assertEquals(WEIGHT_COLUMNS, driver.columnsOf("weights-table")),
                () -> assertEquals(WEIGHT_ROWS, driver.rowsOf("weights-table")),
                () ->
                        assertEquals(
                                "#results-counts is a javafx.scene.control.Label, neither a"
                                        + " TableView nor a ListView",
                                assertThrows(
                                                AssertionError.class,
                                                () -> driver.rowsOf("results-counts"))
                                        .getMessage()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("drivers")
    @Order(7)
    @DisplayName(
            "pressKeyWith and pressKey: Ctrl+A, typing 0.005 and Enter refilter to the oracle's"
                    + " count at 0.005; an unknown key name is refused")
    void pressingKeysByName(FxUiDriver driver) throws IOException {
        atTheStart(driver);
        driver.clickOn("results-psm-filter");
        driver.pressKeyWith("CONTROL", "A");
        driver.type("0.005");
        driver.pressKey("ENTER");
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
        assertAll(
                driver.toString(),
                () -> assertEquals("0.005", driver.textOf("results-psm-filter")),
                () -> assertEquals(List.of("15", "3", "4", "8"), ResultsSection.counts(driver)),
                () -> assertEquals(oracle(psms, "0.005"), ResultsSection.counts(driver)),
                () ->
                        assertEquals(
                                "there is no KeyCode named \"NOT_A_KEY\"",
                                assertThrows(
                                                AssertionError.class,
                                                () -> driver.pressKey("NOT_A_KEY"))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "there is no KeyCode named \"CTRL\"",
                                assertThrows(
                                                AssertionError.class,
                                                () -> driver.pressKeyWith("CTRL", "A"))
                                        .getMessage()));
        driver.typeInto("results-psm-filter", "0.01");
        driver.pressKey("ENTER");
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
        assertEquals(List.of("15", "5", "2", "8"), ResultsSection.counts(driver));
    }

    @org.junit.jupiter.api.Test
    @Order(8)
    @DisplayName(
            "rowsOf on lists, through both drivers: a default cell shows the item's text, a cell"
                    + " factory of the list's own shows what its cell says")
    void rowsOfLists() {
        FxUiDriver main = new TestFxUiDriver(app.application());
        Stage second =
                main.callOnFxThread(
                        () -> {
                            ListView<String> plain =
                                    new ListView<>(
                                            FXCollections.observableArrayList(
                                                    "alpha", null, "gamma"));
                            plain.setId("probe-plain-list");
                            ListView<String> custom =
                                    new ListView<>(
                                            FXCollections.observableArrayList("alpha", "beta"));
                            custom.setId("probe-custom-list");
                            custom.setCellFactory(list -> new ShoutingCell());
                            Stage stage = new Stage();
                            stage.setScene(new Scene(new VBox(plain, custom), 300, 300));
                            stage.show();
                            return stage;
                        });
        try {
            for (FxUiDriver driver : drivers(RunningApplication.showing(second)).toList()) {
                assertAll(
                        driver.toString(),
                        () ->
                                assertEquals(
                                        List.of("alpha", "", "gamma"),
                                        driver.rowsOf("probe-plain-list")),
                        () ->
                                assertEquals(
                                        List.of("row 1: ALPHA", "row 2: BETA"),
                                        driver.rowsOf("probe-custom-list")),
                        () ->
                                assertEquals(
                                        "#probe-plain-list is a javafx.scene.control.ListView,"
                                                + " not a"
                                                + " TableView",
                                        assertThrows(
                                                        AssertionError.class,
                                                        () -> driver.columnsOf("probe-plain-list"))
                                                .getMessage()));
            }
        } finally {
            main.onFxThread(second::hide);
        }
    }

    /** A cell that shows its item upper-cased and numbered: not what {@code toString} says. */
    private static final class ShoutingCell extends ListCell<String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            setText(
                    empty || item == null
                            ? null
                            : "row "
                                    + (getIndex() + 1)
                                    + ": "
                                    + item.toUpperCase(java.util.Locale.ROOT));
        }
    }

    private static List<String> firstColumn(List<String> rows) {
        Function<String, String> first = row -> row.substring(0, row.indexOf('\t'));
        List<String> ids = new ArrayList<>();
        for (String row : rows) {
            ids.add(first.apply(row));
        }
        return ids;
    }
}
