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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.TableView;
import org.cometgui.app.uidriver.FxUiDriver;

/**
 * Driving the Results section, whose answers arrive from its store thread: every wait is on an
 * observable fact -- a label's text -- and has a bound. Every identifier is a hand-typed literal
 * (P7-5: a GUI test never calls {@code UiIds}).
 */
final class ResultsSection {

    /** How long opening a constructed run's small tables may take. A bound, never a delay. */
    static final Duration OPEN_BOUND = Duration.ofMinutes(1);

    /** How long one query of a small table may take. */
    static final Duration QUERY_BOUND = Duration.ofSeconds(30);

    /** The page size the table's view-model asks the store for. */
    static final int PAGE_SIZE = 200;

    private ResultsSection() {}

    /**
     * Waits until the Results section shows a run.
     *
     * @param driver the driver
     * @param runId the run
     * @param bound how long to wait at most
     * @return the readiness text then
     */
    static String awaitOpened(FxUiDriver driver, String runId, Duration bound) {
        return RunSection.awaitText(
                driver,
                "results-readiness",
                text -> text.startsWith("Showing the results of run " + runId + ","),
                bound);
    }

    /**
     * Waits until the table has its answer: its status is empty again.
     *
     * @param driver the driver
     * @param bound how long to wait at most
     */
    static void settle(FxUiDriver driver, Duration bound) {
        RunSection.awaitText(driver, "results-table-status", String::isEmpty, bound);
    }

    /**
     * The four counts shown: total, passing, failing, unknown q-value.
     *
     * @param driver the driver
     * @return the four texts
     */
    static List<String> counts(FxUiDriver driver) {
        return List.of(
                driver.textOf("results-count-total"),
                driver.textOf("results-count-passing"),
                driver.textOf("results-count-failing"),
                driver.textOf("results-count-unknown"));
    }

    /**
     * The counts sentence the section shows for a table at a cutoff -- the format typed out here.
     *
     * @param rows what the table's rows are called, for example {@code target PSMs}
     * @param cutoff the cutoff as the filter writes it
     * @param counts total, passing, failing, unknown
     * @return the sentence
     */
    static String summary(String rows, String cutoff, long... counts) {
        return counts[0]
                + " "
                + rows
                + " in total. At a q-value cutoff of "
                + cutoff
                + " (a q-value equal to the cutoff passing): "
                + counts[1]
                + " passing, "
                + counts[2]
                + " failing, and "
                + counts[3]
                + " with an unknown q-value, which neither pass nor fail.";
    }

    /**
     * How many items the results table holds: the rows of the page it shows.
     *
     * @param driver the driver
     * @return the size of the {@code TableView}'s items
     */
    static int items(FxUiDriver driver) {
        return table(driver).size();
    }

    /**
     * The results table's rows as the {@code TableView} holds them: one column's cells.
     *
     * @param driver the driver
     * @param column the column's position among all nine, from 0 (0 PSMId, 1 source file, 2 scan, 3
     *     charge, 4 peptide, 5 proteins, 6 score, 7 q-value, 8 PEP)
     * @return each item's cell, in order
     */
    static List<String> column(FxUiDriver driver, int column) {
        TableView<?> table = tableView(driver);
        return driver.callOnFxThread(
                () -> {
                    List<String> cells = new ArrayList<>();
                    for (int row = 0; row < table.getItems().size(); row++) {
                        Object cell = table.getColumns().get(column).getCellData(row);
                        cells.add(cell == null ? null : cell.toString());
                    }
                    return cells;
                });
    }

    /**
     * The indices of the rows selected in the table.
     *
     * @param driver the driver
     * @return the indices, ascending
     */
    static List<Integer> selectedIndices(FxUiDriver driver) {
        TableView<?> table = tableView(driver);
        return driver.callOnFxThread(
                () -> {
                    List<Integer> indices =
                            new ArrayList<>(table.getSelectionModel().getSelectedIndices());
                    Collections.sort(indices);
                    return indices;
                });
    }

    /**
     * Selects one row of the table the way a click does: through its selection model.
     *
     * @param driver the driver
     * @param index the row's position on the page
     */
    static void selectRow(FxUiDriver driver, int index) {
        TableView<?> table = tableView(driver);
        driver.onFxThread(() -> table.getSelectionModel().clearAndSelect(index));
    }

    private static List<?> table(FxUiDriver driver) {
        TableView<?> table = tableView(driver);
        return driver.callOnFxThread(() -> List.copyOf(table.getItems()));
    }

    private static TableView<?> tableView(FxUiDriver driver) {
        Node node = driver.node("results-table");
        if (!(node instanceof TableView<?> table)) {
            return fail("#results-table is a " + node.getClass().getName() + ", not a table");
        }
        return table;
    }

    /**
     * Starts recording what the JavaFX application thread's listeners throw, which JavaFX would
     * otherwise swallow (Phase 07's lesson).
     *
     * @param driver the driver
     * @return the list the throwables are added to
     */
    static List<Throwable> recordListenerFailures(FxUiDriver driver) {
        List<Throwable> thrown = Collections.synchronizedList(new ArrayList<>());
        driver.onFxThread(
                () -> Thread.currentThread().setUncaughtExceptionHandler((t, e) -> thrown.add(e)));
        return thrown;
    }

    /**
     * Asserts no listener threw.
     *
     * @param thrown what {@link #recordListenerFailures} recorded
     */
    static void assertNoListenerFailed(List<Throwable> thrown) {
        List<String> described = new ArrayList<>();
        synchronized (thrown) {
            for (Throwable failure : thrown) {
                described.add(failure.toString());
            }
        }
        assertEquals(
                List.of(),
                described,
                "an exception thrown in a JavaFX listener on the application thread");
    }
}
