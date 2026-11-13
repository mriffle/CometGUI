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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
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
import javafx.scene.input.MouseEvent;
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
import org.cometgui.ui.viewmodel.provenance.ProvenanceViewModel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Provenance section's run selector, by keyboard alone (the specification's
 * <em>Accessibility</em> principle; <em>Provenance UI</em>): moving through the list reads no run's
 * provenance, and committing a choice reads exactly that one -- the behaviour {@link
 * ResultsRunChooserUiTest} proves of the Results section's chooser, kept by the same code ({@code
 * org.cometgui.ui.controls.CommittedChoice}).
 *
 * <p>Reading a run's provenance reads and parses its record, its event log and its archived files
 * off the interface thread and empties the section while it does, so a selector that read every run
 * the arrow keys passed blanked and refilled the whole section once per run on the way to the one
 * wanted -- and the end-to-end scenarios' own way of choosing a run ({@link FxUiDriver#select}: a
 * click, the arrow keys, Enter) did exactly that.
 *
 * <h2>How the reads are counted</h2>
 *
 * <p>The application is the real one, launched with its real composition root over a project of
 * four constructed runs. Every time the Provenance section starts reading a run it says so in its
 * record line -- {@code Reading the chosen run's provenance.} -- in the same call that asks the
 * section's port for that run, and when the answer has come its summary begins {@code Run <id> of
 * project}. Listeners on both lines, added before the first key is pressed, record every text they
 * ever show, so the runs read are counted from what the section did, not from where it ended up.
 * Each step waits for the record line to say something else before the next key is pressed, so no
 * read can hide behind another: a second read started while the line already said so would not
 * change it.
 *
 * <p>No run is chosen when the section is first shown, so the first tests start from an empty
 * selector. Apart from the two tests that are about the mouse, every step is a key press delivered
 * by the robot to the focus owner.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProvenanceRunSelectorUiTest {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-09T00:00:00Z"));

    /** The four runs, oldest first: the order the selector lists them in. */
    private static final List<String> RUNS = List.of("run-a", "run-b", "run-c", "run-d");

    private static final String SELECTOR = "provenance-run";

    private static final String RECORD = "provenance-record";

    private static final String SUMMARY = "provenance-summary";

    /** How long the section may take to list the runs or read one. A bound, never a delay. */
    private static final Duration SECTION_BOUND = Duration.ofMinutes(1);

    @TempDir private static Path scratch;

    private static ParameterEditorApp app;

    private static FxUiDriver driver;

    /** Every text the record line has shown since the listener was added, in order. */
    private static final List<String> RECORD_LINE = Collections.synchronizedList(new ArrayList<>());

    /** Every text the summary has shown since the listener was added, in order. */
    private static final List<String> SUMMARIES = Collections.synchronizedList(new ArrayList<>());

    @BeforeAll
    static void launch() throws IOException {
        Path root = scratch.toRealPath();
        ProjectLayout project = ResultRuns.project(root.resolve("project"));
        Path psms = ResultRuns.fixture(ResultRuns.REAL_3071_PSMS, root.resolve("in/psms.tsv"));
        for (int index = 0; index < RUNS.size(); index++) {
            ResultRuns.run(
                    project,
                    RUNS.get(index),
                    Instant.parse("2026-10-09T05:00:00Z").plusSeconds(3600L * index),
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
        driver.clickOn("nav-provenance");
        RunSection.awaitText(
                driver,
                "provenance-runs-status",
                "This project has 4 runs."::equals,
                SECTION_BOUND);
        Labeled record = (Labeled) driver.node(RECORD);
        Labeled summary = (Labeled) driver.node(SUMMARY);
        driver.onFxThread(
                () -> {
                    record.textProperty()
                            .addListener((observable, before, after) -> RECORD_LINE.add(after));
                    summary.textProperty()
                            .addListener((observable, before, after) -> SUMMARIES.add(after));
                });
    }

    @AfterAll
    static void stop() {
        if (app != null) {
            app.stop();
        }
    }

    /** Waits until the section is not in the middle of reading a run. */
    private static void settle() {
        RunSection.awaitText(
                driver, RECORD, text -> !ProvenanceViewModel.OPENING.equals(text), SECTION_BOUND);
    }

    /** How many times the section started reading a run since the last {@link #forget()}. */
    private static int readsStarted() {
        synchronized (RECORD_LINE) {
            return Collections.frequency(RECORD_LINE, ProvenanceViewModel.OPENING);
        }
    }

    /** The runs whose provenance the section showed since the last {@link #forget()}, in order. */
    private static List<String> read() {
        List<String> runs = new ArrayList<>();
        synchronized (SUMMARIES) {
            for (String text : SUMMARIES) {
                if (text.startsWith("Run ")) {
                    runs.add(text.substring("Run ".length(), text.indexOf(" of project ")));
                }
            }
        }
        return runs;
    }

    private static void forget() {
        settle();
        RECORD_LINE.clear();
        SUMMARIES.clear();
    }

    /** The run whose provenance the section shows; empty when it shows none. */
    private static String showing() {
        String text = driver.textOf(SUMMARY);
        return text.startsWith("Run ")
                ? text.substring("Run ".length(), text.indexOf(" of project "))
                : "";
    }

    /** The run the selector shows: the first word of its text; empty when it shows none. */
    private static String selectorShows() {
        String text = ParameterEditorApp.comboText(driver, SELECTOR);
        int end = text.indexOf(" -- ");
        return end < 0 ? text : text.substring(0, end);
    }

    private static boolean listIsOpen() {
        ComboBox<?> selector = (ComboBox<?>) driver.node(SELECTOR);
        return driver.callOnFxThread(selector::isShowing);
    }

    /** Moves the keyboard focus to the run selector with Tab alone. */
    private static void focusTheSelector() {
        for (int press = 0; press < 120 && !SELECTOR.equals(driver.focusedNodeId()); press++) {
            driver.tab();
        }
        assertEquals(SELECTOR, driver.focusedNodeId(), "Tab reaches the run selector");
    }

    /** One key, and then whatever it started has finished. */
    private static void press(KeyCode key) {
        driver.press(key);
        settle();
    }

    @Test
    @Order(1)
    @DisplayName("the selector lists the four runs, oldest first, and none is chosen")
    void theFixture() {
        List<String> offered = new ArrayList<>();
        for (String item : ParameterEditorApp.comboItems(driver, SELECTOR)) {
            offered.add(item.substring(0, item.indexOf(" -- ")));
        }
        assertAll(
                () -> assertEquals(RUNS, offered),
                () -> assertEquals("", selectorShows(), "the selector shows no run"),
                () -> assertEquals(ProvenanceViewModel.NO_RUN, driver.textOf(RECORD)),
                () ->
                        assertEquals(
                                "The run whose provenance is shown",
                                driver.accessibleTextOf(SELECTOR)));
    }

    @Test
    @Order(2)
    @DisplayName("with no run chosen, Escape from a walk leaves the selector empty and reads none")
    void escapeWithNoRunChosen() {
        focusTheSelector();
        forget();
        press(KeyCode.DOWN);
        press(KeyCode.DOWN);
        assertAll(
                "after Down twice",
                () -> assertTrue(listIsOpen(), "the list is open"),
                () -> assertEquals("run-a", selectorShows(), "the first run of the list"),
                () -> assertEquals(List.of(), read(), "runs read"),
                () -> assertEquals(0, readsStarted(), "reads started"));

        press(KeyCode.ESCAPE);
        assertAll(
                "after Escape",
                () -> assertFalse(listIsOpen(), "the list is closed"),
                () -> assertEquals("", selectorShows(), "the selector shows no run again"),
                () -> assertEquals(List.of(), read(), "runs read"),
                () -> assertEquals(0, readsStarted(), "reads started"),
                () -> assertEquals(ProvenanceViewModel.NO_RUN, driver.textOf(RECORD)),
                () -> assertEquals(SELECTOR, driver.focusedNodeId()));
    }

    @Test
    @Order(3)
    @DisplayName("arrowing from the first run to the last reads none; Enter reads only the last")
    void walkingTheListReadsNothingAndEnterReadsOne() {
        focusTheSelector();
        forget();

        // Down on the closed selector opens its list and changes nothing.
        press(KeyCode.DOWN);
        assertAll(
                "after the first Down",
                () -> assertTrue(listIsOpen(), "the list is open"),
                () -> assertEquals("", selectorShows(), "the selector still shows no run"),
                () -> assertEquals(List.of(), read(), "runs read"),
                () -> assertEquals(0, readsStarted(), "reads started"));

        // Four more walk from run-a past run-b and run-c to run-d.
        List<String> shown = new ArrayList<>();
        for (int step = 0; step < 4; step++) {
            press(KeyCode.DOWN);
            shown.add(selectorShows());
        }
        assertAll(
                "after walking to the last run",
                () -> assertEquals(RUNS, shown, "the walk"),
                () -> assertTrue(listIsOpen(), "the list is still open"),
                () -> assertEquals(List.of(), read(), "runs read while walking"),
                () -> assertEquals(0, readsStarted(), "reads started while walking"),
                () -> assertEquals(ProvenanceViewModel.NO_RUN, driver.textOf(RECORD)),
                () -> assertEquals(SELECTOR, driver.focusedNodeId()));

        press(KeyCode.ENTER);
        assertAll(
                "after Enter",
                () -> assertFalse(listIsOpen(), "the list is closed"),
                () -> assertEquals(List.of("run-d"), read(), "runs read: exactly the chosen"),
                () -> assertEquals(1, readsStarted(), "reads started"),
                () -> assertEquals("run-d", showing()),
                () -> assertEquals("run-d", selectorShows()),
                () ->
                        assertEquals(
                                SELECTOR,
                                driver.focusedNodeId(),
                                "the focus stays on the selector"));
    }

    @Test
    @Order(4)
    @DisplayName("Up walks back the same way, and Escape leaves the run shown as it is")
    void escapeAbandonsTheWalk() {
        focusTheSelector();
        forget();
        assertEquals("run-d", showing(), "the run the last test chose");

        press(KeyCode.UP);
        assertTrue(listIsOpen(), "Up on the closed selector opens its list");
        press(KeyCode.UP);
        press(KeyCode.UP);
        assertEquals("run-b", selectorShows(), "two runs up from run-d");

        press(KeyCode.ESCAPE);
        assertAll(
                "after Escape",
                () -> assertFalse(listIsOpen(), "the list is closed"),
                () -> assertEquals(List.of(), read(), "runs read"),
                () -> assertEquals(0, readsStarted(), "reads started"),
                () -> assertEquals("run-d", showing(), "the run shown is unchanged"),
                () -> assertEquals("run-d", selectorShows(), "the selector is back on it"),
                () -> assertEquals(SELECTOR, driver.focusedNodeId()));
    }

    @Test
    @Order(5)
    @DisplayName("choosing the run that is already shown reads nothing")
    void theShownRunChosenAgain() {
        focusTheSelector();
        forget();
        press(KeyCode.DOWN);
        press(KeyCode.UP);
        press(KeyCode.DOWN);
        assertEquals("run-d", selectorShows(), "up one and down again");
        press(KeyCode.ENTER);
        assertAll(
                () -> assertFalse(listIsOpen(), "the list is closed"),
                () -> assertEquals(List.of(), read(), "runs read"),
                () -> assertEquals(0, readsStarted(), "reads started"),
                () -> assertEquals("run-d", showing()));
    }

    @Test
    @Order(6)
    @DisplayName("Space commits a walk as Enter does: the run stopped on is read, once")
    void spaceCommits() {
        focusTheSelector();
        forget();
        press(KeyCode.UP);
        press(KeyCode.UP);
        press(KeyCode.UP);
        assertEquals("run-b", selectorShows(), "two runs up from run-d");
        assertEquals(0, readsStarted(), "reads started while walking");

        press(KeyCode.SPACE);
        assertAll(
                () -> assertFalse(listIsOpen(), "the list is closed"),
                () -> assertEquals(List.of("run-b"), read(), "runs read"),
                () -> assertEquals(1, readsStarted(), "reads started"),
                () -> assertEquals("run-b", showing()),
                () -> assertEquals("run-b", selectorShows()));
    }

    @Test
    @Order(7)
    @DisplayName("a mouse click on a run in the open list reads that run, once")
    void aClickOnARunReadsIt() {
        forget();
        driver.clickOn(SELECTOR);
        settle();
        assertTrue(listIsOpen(), "a click on the selector opens its list");
        assertEquals(0, readsStarted(), "reads started by opening the list");

        ComboBox<?> selector = (ComboBox<?>) driver.node(SELECTOR);
        List<String> pressed = new ArrayList<>();
        driver.onFxThread(
                () -> {
                    Node list = ((ComboBoxListViewSkin<?>) selector.getSkin()).getPopupContent();
                    for (Node node : list.lookupAll(".list-cell")) {
                        ListCell<?> cell = (ListCell<?>) node;
                        if (!cell.isEmpty() && cell.getText().startsWith("run-a -- ")) {
                            pressed.add(cell.getText());
                            Bounds onScreen = cell.localToScreen(cell.getBoundsInLocal());
                            Robot robot = new Robot();
                            robot.mouseMove(onScreen.getCenterX(), onScreen.getCenterY());
                            robot.mousePress(MouseButton.PRIMARY);
                            robot.mouseRelease(MouseButton.PRIMARY);
                        }
                    }
                });
        assertEquals(1, pressed.size(), () -> "the list shows run-a once: " + pressed);
        RunSection.awaitText(
                driver, SUMMARY, text -> text.startsWith("Run run-a of project "), SECTION_BOUND);
        settle();
        assertAll(
                () -> assertFalse(listIsOpen(), "the list is closed"),
                () -> assertEquals(List.of("run-a"), read(), "runs read"),
                () -> assertEquals(1, readsStarted(), "reads started"),
                () -> assertEquals("run-a", selectorShows()));
    }

    /** Opens the list from the run shown and walks one run away from it, reading nothing. */
    private static String walkOneRunAway(String shown) {
        focusTheSelector();
        forget();
        assertEquals(shown, showing(), "the run that is shown");
        boolean last = shown.equals(RUNS.get(RUNS.size() - 1));
        press(last ? KeyCode.UP : KeyCode.DOWN);
        assertTrue(listIsOpen(), "an arrow on the closed selector opens its list");
        press(last ? KeyCode.UP : KeyCode.DOWN);
        String walkedTo = selectorShows();
        assertAll(
                "after walking one run away",
                () -> assertTrue(listIsOpen(), "the list is still open"),
                () ->
                        assertEquals(
                                RUNS.get(RUNS.indexOf(shown) + (last ? -1 : 1)),
                                walkedTo,
                                "the run the selector shows"),
                () -> assertEquals(0, readsStarted(), "reads started while walking"));
        return walkedTo;
    }

    @Test
    @Order(8)
    @DisplayName("a click elsewhere abandons a walk: the list closes and no run is read")
    void aClickElsewhereAbandonsTheWalk() {
        String shown = showing();
        String walkedTo = walkOneRunAway(shown);
        // The list drops over the lines just below the selector, so "elsewhere" is the tools list
        // further down -- and the list itself says whether the press reached it after all.
        ComboBox<?> selector = (ComboBox<?>) driver.node(SELECTOR);
        List<String> pressesInTheList = Collections.synchronizedList(new ArrayList<>());
        driver.onFxThread(
                () ->
                        ((ComboBoxListViewSkin<?>) selector.getSkin())
                                .getPopupContent()
                                .addEventFilter(
                                        MouseEvent.MOUSE_PRESSED,
                                        press ->
                                                pressesInTheList.add(
                                                        String.valueOf(press.getTarget()))));

        driver.clickOn("provenance-tools");
        settle();
        assertAll(
                "after a click on the tools list, with " + walkedTo + " shown in the run list",
                () -> assertEquals(List.of(), pressesInTheList, "presses that reached the list"),
                () -> assertFalse(listIsOpen(), "the list is closed"),
                () -> assertEquals(List.of(), read(), "runs read"),
                () -> assertEquals(0, readsStarted(), "reads started"),
                () -> assertEquals(shown, showing(), "the run shown is unchanged"),
                () -> assertEquals(shown, selectorShows(), "the selector is back on it"));
    }

    @Test
    @Order(9)
    @DisplayName("the focus leaving the selector abandons a walk: the list closes, no run is read")
    void theFocusLeavingAbandonsTheWalk() {
        String shown = showing();
        String walkedTo = walkOneRunAway(shown);

        Node elsewhere = driver.node("provenance-refresh");
        driver.onFxThread(elsewhere::requestFocus);
        settle();
        assertAll(
                "after the focus moved to Read the runs again, with "
                        + walkedTo
                        + " shown in the list",
                () -> assertEquals("provenance-refresh", driver.focusedNodeId()),
                () -> assertFalse(listIsOpen(), "the list is closed"),
                () -> assertEquals(List.of(), read(), "runs read"),
                () -> assertEquals(0, readsStarted(), "reads started"),
                () -> assertEquals(shown, showing(), "the run shown is unchanged"),
                () -> assertEquals(shown, selectorShows(), "the selector is back on it"));
    }

    @Test
    @Order(10)
    @DisplayName("the scenarios' driver chooses a run past two others and one run is read: that")
    void theDriverChoosesOneRun() {
        forget();
        // the run furthest from the one shown, so that the walk passes at least one other
        String target = RUNS.indexOf(showing()) <= 1 ? "run-d" : "run-a";
        List<String> offered = driver.itemsOf(SELECTOR);
        String label =
                offered.stream()
                        .filter(item -> item.startsWith(target + " -- "))
                        .findFirst()
                        .orElse("");
        ComboBox<?> selector = (ComboBox<?>) driver.node(SELECTOR);

        // a click, the arrow keys past the runs between, Enter: FxUiDriver.select
        driver.select(SELECTOR, label);
        settle();
        String shown = driver.selectedTextOf(SELECTOR);
        List<String> closedCell =
                driver.callOnFxThread(() -> ParameterEditorApp.cellTexts(selector));
        assertAll(
                "after the driver chose " + label,
                () -> assertEquals(List.of(target), read(), "runs read: exactly the chosen"),
                () -> assertEquals(1, readsStarted(), "reads started"),
                () -> assertEquals(target, showing()),
                () -> assertEquals(label, shown, "what the driver says the selector shows"),
                () -> assertEquals(List.of(label), closedCell, "as the selector's cell shows it"),
                () ->
                        assertEquals(
                                ParameterEditorApp.comboItems(driver, SELECTOR),
                                offered,
                                "the runs the driver says the selector offers"));
    }
}
