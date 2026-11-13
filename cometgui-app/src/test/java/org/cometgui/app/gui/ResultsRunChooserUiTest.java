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
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListCell;
import javafx.scene.control.skin.ComboBoxListViewSkin;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.robot.Robot;
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
 * The Results section's run chooser, by keyboard alone ({@code R-RES-03}, the specification's
 * <em>Accessibility</em> principle): moving through the list opens no run, and committing a choice
 * opens exactly that one.
 *
 * <p>Opening a run hashes, indexes and maps its tables -- seconds for a large run -- so a chooser
 * that opened every run the arrow keys passed made a keyboard user pay a cold open per run on the
 * way to the one they wanted (Phase 15 unit 8's finding).
 *
 * <h2>How the opens are counted</h2>
 *
 * <p>The fifth and sixth tests are the other two ways a choice is committed: Space, and the mouse
 * (a click by the toolkit's own robot on a run's cell in the open list). The last two are the other
 * two ways a walk is abandoned: a click somewhere else in the window, and the keyboard focus
 * leaving the chooser. Each closes the list, opens nothing and puts the chooser back on the open
 * run, as Escape does.
 *
 * <p>The last test holds the chooser to what the end-to-end scenarios need of it: their driver
 * ({@link FxUiDriver#selectedTextOf} and {@link FxUiDriver#itemsOf}) reads a combo box through its
 * converter and refuses one that has a cell factory of its own, because such a box can show
 * something else. The chooser has none, and the test proves the reading against the cells
 * themselves: the text of the cell in the closed chooser and of every cell in its open list.
 *
 * <p>The application is the real one, launched with its real composition root over a project of
 * four constructed runs. Every time the Results section starts opening a run it says so in its
 * readiness line -- {@code Opening the results of run <id>.} -- in the same call that asks the
 * session's results port for that run's stores, and when the stores have been opened it says {@code
 * Showing the results of run <id>, ...}. A listener on that line, added before the first key is
 * pressed, records every text it ever shows, so the runs opened are counted from what the section
 * did, not from where it ended up. No mouse is used after the section is shown: every step is a key
 * press delivered by the robot to the focus owner.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ResultsRunChooserUiTest {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-09T00:00:00Z"));

    /** The four runs, most recent first: the order the chooser lists them in. */
    private static final List<String> RUNS = List.of("run-d", "run-c", "run-b", "run-a");

    private static final String OPENING = "Opening the results of run ";

    private static final String SHOWING = "Showing the results of run ";

    @TempDir private static Path scratch;

    private static ParameterEditorApp app;

    private static FxUiDriver driver;

    private static List<Throwable> listenerFailures;

    /** Every text the readiness line has shown since the listener was added, in order. */
    private static final List<String> READINESS = Collections.synchronizedList(new ArrayList<>());

    @BeforeAll
    static void launch() throws IOException {
        Path root = scratch.toRealPath();
        ProjectLayout project = ResultRuns.project(root.resolve("project"));
        Path psms = ResultRuns.fixture(ResultRuns.REAL_3071_PSMS, root.resolve("in/psms.tsv"));
        for (int index = 0; index < RUNS.size(); index++) {
            ResultRuns.run(
                    project,
                    RUNS.get(index),
                    Instant.parse("2026-10-09T08:00:00Z").minusSeconds(3600L * index),
                    List.of(root.resolve("data/sample.mzML")),
                    Map.of(TableKind.TARGET_PSMS, psms),
                    Optional.empty());
        }
        app =
                ParameterEditorApp.launch(
                        BUILD,
                        null,
                        new BoundedMessageLog(),
                        new RunWiring.Setup(InstalledComet::nothing, root.resolve("project")));
        driver = new TestFxUiDriver(app.application());
        listenerFailures = ResultsSection.recordListenerFailures(driver);
        driver.clickOn("nav-results");
        // the most recent run is opened first
        ResultsSection.awaitOpened(driver, RUNS.get(0), ResultsSection.OPEN_BOUND);
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
        Labeled readiness = (Labeled) driver.node("results-readiness");
        driver.onFxThread(
                () ->
                        readiness
                                .textProperty()
                                .addListener((observable, before, after) -> READINESS.add(after)));
    }

    @AfterAll
    static void stop() {
        if (app != null) {
            app.stop();
        }
    }

    /** The runs the section started opening since the last {@link #forget()}, in order. */
    private static List<String> opened() {
        List<String> runs = new ArrayList<>();
        synchronized (READINESS) {
            for (String text : READINESS) {
                if (text.startsWith(OPENING)) {
                    runs.add(text.substring(OPENING.length(), text.length() - 1));
                }
            }
        }
        return runs;
    }

    private static void forget() {
        READINESS.clear();
    }

    /** The run whose results the readiness line says are shown. */
    private static String showing() {
        String text = driver.textOf("results-readiness");
        assertTrue(text.startsWith(SHOWING), () -> "the readiness line: " + text);
        return text.substring(SHOWING.length(), text.indexOf(',', SHOWING.length()));
    }

    /** The run the chooser shows: the first word of its text. */
    private static String chooserShows() {
        String text = ParameterEditorApp.comboText(driver, "results-run");
        int end = text.indexOf(" -- ");
        return end < 0 ? text : text.substring(0, end);
    }

    private static boolean listIsOpen() {
        ComboBox<?> chooser = (ComboBox<?>) driver.node("results-run");
        return driver.callOnFxThread(chooser::isShowing);
    }

    /** Moves the keyboard focus to the run chooser with Tab alone. */
    private static void focusTheChooser() {
        for (int press = 0; press < 120 && !"results-run".equals(driver.focusedNodeId()); press++) {
            driver.tab();
        }
        assertEquals("results-run", driver.focusedNodeId(), "Tab reaches the run chooser");
    }

    @Test
    @Order(1)
    @DisplayName("the chooser lists the four runs, most recent first, and the most recent is open")
    void theFixture() {
        List<String> offered = new ArrayList<>();
        for (String item : ParameterEditorApp.comboItems(driver, "results-run")) {
            offered.add(item.substring(0, item.indexOf(" -- ")));
        }
        assertAll(
                () -> assertEquals(RUNS, offered),
                () -> assertEquals("run-d", showing()),
                () -> assertEquals("run-d", chooserShows()),
                () ->
                        assertEquals(
                                "The run whose results are shown",
                                driver.accessibleTextOf("results-run")));
    }

    @Test
    @Order(2)
    @DisplayName("arrowing from the first run to the last opens none; Enter opens only the last")
    void walkingTheListOpensNothingAndEnterOpensOne() {
        focusTheChooser();
        forget();

        // Down on the closed chooser opens its list and changes nothing.
        driver.press(KeyCode.DOWN);
        assertAll(
                "after the first Down",
                () -> assertTrue(listIsOpen(), "the list is open"),
                () -> assertEquals("run-d", chooserShows()),
                () -> assertEquals(List.of(), opened(), "runs opened"));

        // Three more walk past run-c and run-b to run-a.
        List<String> shown = new ArrayList<>();
        for (int press = 0; press < 3; press++) {
            driver.press(KeyCode.DOWN);
            shown.add(chooserShows());
        }
        assertAll(
                "after walking to the last run",
                () -> assertEquals(List.of("run-c", "run-b", "run-a"), shown, "the walk"),
                () -> assertTrue(listIsOpen(), "the list is still open"),
                () -> assertEquals(List.of(), opened(), "runs opened while walking"),
                () -> assertEquals("run-d", showing(), "the run shown is still the first"),
                () -> assertEquals("results-run", driver.focusedNodeId()));

        driver.press(KeyCode.ENTER);
        ResultsSection.awaitOpened(driver, "run-a", ResultsSection.OPEN_BOUND);
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
        assertAll(
                "after Enter",
                () -> assertFalse(listIsOpen(), "the list is closed"),
                () -> assertEquals(List.of("run-a"), opened(), "runs opened: exactly the chosen"),
                () -> assertEquals("run-a", showing()),
                () -> assertEquals("run-a", chooserShows()),
                () ->
                        assertEquals(
                                "results-run",
                                driver.focusedNodeId(),
                                "the focus stays on the chooser"));
    }

    @Test
    @Order(3)
    @DisplayName("Up walks back the same way, and Escape leaves the open run as it is")
    void escapeAbandonsTheWalk() {
        focusTheChooser();
        forget();
        assertEquals("run-a", showing(), "the run the last test opened");

        driver.press(KeyCode.UP);
        assertTrue(listIsOpen(), "Up on the closed chooser opens its list");
        driver.press(KeyCode.UP);
        driver.press(KeyCode.UP);
        assertEquals("run-c", chooserShows(), "two runs up from run-a");

        driver.press(KeyCode.ESCAPE);
        assertAll(
                "after Escape",
                () -> assertFalse(listIsOpen(), "the list is closed"),
                () -> assertEquals(List.of(), opened(), "runs opened"),
                () -> assertEquals("run-a", showing(), "the open run is unchanged"),
                () -> assertEquals("run-a", chooserShows(), "the chooser is back on it"),
                () -> assertEquals("results-run", driver.focusedNodeId()));
    }

    @Test
    @Order(4)
    @DisplayName("choosing the run that is already open opens nothing")
    void theOpenRunChosenAgain() {
        focusTheChooser();
        forget();
        driver.press(KeyCode.DOWN);
        driver.press(KeyCode.UP);
        driver.press(KeyCode.DOWN);
        assertEquals("run-a", chooserShows(), "up one and down again");
        driver.press(KeyCode.ENTER);
        assertAll(
                () -> assertFalse(listIsOpen(), "the list is closed"),
                () -> assertEquals(List.of(), opened(), "runs opened"),
                () -> assertEquals("run-a", showing()));
    }

    @Test
    @Order(5)
    @DisplayName("a walk committed two runs up opens that run once; no listener ever failed")
    void aSecondChoice() {
        focusTheChooser();
        forget();
        driver.press(KeyCode.UP);
        driver.press(KeyCode.UP);
        driver.press(KeyCode.UP);
        assertEquals("run-c", chooserShows());
        driver.press(KeyCode.ENTER);
        ResultsSection.awaitOpened(driver, "run-c", ResultsSection.OPEN_BOUND);
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
        assertAll(
                () -> assertEquals(List.of("run-c"), opened(), "runs opened"),
                () -> assertEquals("run-c", showing()),
                () -> assertEquals(List.of(), listenerFailures, "listener failures"));
    }

    @Test
    @Order(6)
    @DisplayName("Space commits a walk as Enter does: the run stopped on is opened, once")
    void spaceCommits() {
        focusTheChooser();
        forget();
        assertEquals("run-c", showing(), "the run the last test opened");
        driver.press(KeyCode.DOWN);
        driver.press(KeyCode.DOWN);
        assertEquals("run-b", chooserShows(), "one run down from run-c");
        assertEquals(List.of(), opened(), "runs opened while walking");

        driver.press(KeyCode.SPACE);
        ResultsSection.awaitOpened(driver, "run-b", ResultsSection.OPEN_BOUND);
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
        assertAll(
                () -> assertFalse(listIsOpen(), "the list is closed"),
                () -> assertEquals(List.of("run-b"), opened(), "runs opened"),
                () -> assertEquals("run-b", chooserShows()));
    }

    @Test
    @Order(7)
    @DisplayName("a mouse click on a run in the open list opens that run, once")
    void aClickOnARunOpensIt() {
        forget();
        driver.clickOn("results-run");
        assertTrue(listIsOpen(), "a click on the chooser opens its list");
        assertEquals(List.of(), opened(), "runs opened by opening the list");

        ComboBox<?> chooser = (ComboBox<?>) driver.node("results-run");
        List<String> pressed = new ArrayList<>();
        driver.onFxThread(
                () -> {
                    Node list = ((ComboBoxListViewSkin<?>) chooser.getSkin()).getPopupContent();
                    for (Node node : list.lookupAll(".list-cell")) {
                        ListCell<?> cell = (ListCell<?>) node;
                        if (!cell.isEmpty() && cell.getText().startsWith("run-d -- ")) {
                            pressed.add(cell.getText());
                            Bounds onScreen = cell.localToScreen(cell.getBoundsInLocal());
                            Robot robot = new Robot();
                            robot.mouseMove(onScreen.getCenterX(), onScreen.getCenterY());
                            robot.mousePress(MouseButton.PRIMARY);
                            robot.mouseRelease(MouseButton.PRIMARY);
                        }
                    }
                });
        assertEquals(1, pressed.size(), () -> "the list shows run-d once: " + pressed);
        ResultsSection.awaitOpened(driver, "run-d", ResultsSection.OPEN_BOUND);
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
        assertAll(
                () -> assertFalse(listIsOpen(), "the list is closed"),
                () -> assertEquals(List.of("run-d"), opened(), "runs opened"),
                () -> assertEquals("run-d", chooserShows()),
                () -> assertEquals(List.of(), listenerFailures, "listener failures"));
    }

    /** Opens the list from the open run and walks one run away from it, opening nothing. */
    private static String walkOneRunAway(String open) {
        focusTheChooser();
        forget();
        assertEquals(open, showing(), "the run that is open");
        boolean last = open.equals(RUNS.get(RUNS.size() - 1));
        driver.press(last ? KeyCode.UP : KeyCode.DOWN);
        assertTrue(listIsOpen(), "an arrow on the closed chooser opens its list");
        driver.press(last ? KeyCode.UP : KeyCode.DOWN);
        String walkedTo = chooserShows();
        assertAll(
                "after walking one run away",
                () -> assertTrue(listIsOpen(), "the list is still open"),
                () ->
                        assertEquals(
                                RUNS.get(RUNS.indexOf(open) + (last ? -1 : 1)),
                                walkedTo,
                                "the run the chooser shows"),
                () -> assertEquals(List.of(), opened(), "runs opened while walking"));
        return walkedTo;
    }

    @Test
    @Order(8)
    @DisplayName("a click elsewhere abandons a walk: the list closes and no run is opened")
    void aClickElsewhereAbandonsTheWalk() {
        String open = showing();
        String walkedTo = walkOneRunAway(open);

        driver.clickOn("results-readiness");
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
        assertAll(
                "after a click on the readiness line, with " + walkedTo + " shown in the list",
                () -> assertFalse(listIsOpen(), "the list is closed"),
                () -> assertEquals(List.of(), opened(), "runs opened"),
                () -> assertEquals(open, showing(), "the open run is unchanged"),
                () -> assertEquals(open, chooserShows(), "the chooser is back on it"),
                () -> assertEquals(List.of(), listenerFailures, "listener failures"));
    }

    @Test
    @Order(9)
    @DisplayName("the focus leaving the chooser abandons a walk: the list closes and no run opens")
    void theFocusLeavingAbandonsTheWalk() {
        String open = showing();
        String walkedTo = walkOneRunAway(open);

        Node elsewhere = driver.node("results-table-choice");
        driver.onFxThread(elsewhere::requestFocus);
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
        assertAll(
                "after the focus moved to the table choice, with "
                        + walkedTo
                        + " shown in the list",
                () -> assertEquals("results-table-choice", driver.focusedNodeId()),
                () -> assertFalse(listIsOpen(), "the list is closed"),
                () -> assertEquals(List.of(), opened(), "runs opened"),
                () -> assertEquals(open, showing(), "the open run is unchanged"),
                () -> assertEquals(open, chooserShows(), "the chooser is back on it"),
                () -> assertEquals(List.of(), listenerFailures, "listener failures"));
    }

    /** The text of every cell under a node that holds an item, in the order they are laid out. */
    private static List<String> cellTexts(Node within) {
        List<ListCell<?>> cells = new ArrayList<>();
        for (Node node : within.lookupAll(".list-cell")) {
            if (node instanceof ListCell<?> cell && !cell.isEmpty()) {
                cells.add(cell);
            }
        }
        cells.sort(Comparator.comparingInt(ListCell::getIndex));
        List<String> texts = new ArrayList<>();
        for (ListCell<?> cell : cells) {
            texts.add(cell.getText());
        }
        return texts;
    }

    @Test
    @Order(10)
    @DisplayName("the scenarios' driver reads the chooser, and reads what its cells really show")
    void theDriverReadsWhatTheCellsShow() {
        String open = showing();
        List<String> offered = driver.itemsOf("results-run");
        String shown = driver.selectedTextOf("results-run");
        ComboBox<?> chooser = (ComboBox<?>) driver.node("results-run");
        List<String> closedCell = driver.callOnFxThread(() -> cellTexts(chooser));

        driver.clickOn("results-run");
        assertTrue(listIsOpen(), "a click on the chooser opens its list");
        List<String> listCells =
                driver.callOnFxThread(
                        () ->
                                cellTexts(
                                        ((ComboBoxListViewSkin<?>) chooser.getSkin())
                                                .getPopupContent()));
        driver.press(KeyCode.ESCAPE);

        List<String> runs = new ArrayList<>();
        for (String item : offered) {
            runs.add(item.substring(0, item.indexOf(" -- ")));
        }
        assertAll(
                () -> assertEquals(RUNS, runs, "the runs the driver says the chooser offers"),
                () -> assertEquals(listCells, offered, "as the cells of the open list show them"),
                () ->
                        assertTrue(
                                shown.startsWith(open + " -- "), () -> "the driver reads " + shown),
                () ->
                        assertEquals(
                                List.of(shown), closedCell, "as the closed chooser's cell shows"),
                () -> assertFalse(listIsOpen(), "the list is closed again"),
                () -> assertEquals(open, showing(), "the open run is unchanged"));
    }
}
