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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import org.cometgui.app.uidriver.FxUiDriver;
import org.cometgui.app.uidriver.RobotFxUiDriver;
import org.cometgui.app.uidriver.RunningApplication;
import org.cometgui.app.uidriver.TestFxUiDriver;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.viewmodel.SectionId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Phase 02 exit-gate item 1, second half: "every primary section is reachable ... by keyboard
 * alone."
 *
 * <p><strong>No mouse is used anywhere in this class, and none can be.</strong> The application is
 * launched in this JVM ({@code reuseForks=false}), no test here calls {@link
 * FxUiDriver#clickOn(String)}, and nothing calls a view-model. Every section is reached with real
 * key presses delivered by a robot to whatever has the keyboard focus, which is the only way to
 * find out whether a keyboard user can get there.
 *
 * <h2>What the shell's keyboard design is, and how it is asserted</h2>
 *
 * <p>It is a roving tab stop. {@code #navigation} is not focus traversable and exactly one
 * navigation entry -- the selected one -- is, so Tab moves into and out of the navigation in one
 * press rather than ten, and the arrow keys move within it. Up and Left go back, Down and Right go
 * forward, an event filter on the container makes the arrows win over JavaFX's own directional
 * traversal, and the ends do not wrap. Each of those is a value this test reads back: the
 * identifier of the focus owner, which entries are traversable, and which pane is showing.
 *
 * <h2>Order matters here, so it is declared</h2>
 *
 * <p>The first test is about a <em>fresh</em> application -- what Tab does when nothing has touched
 * the window yet -- so it must run before any test has moved the selection. {@link
 * MethodOrderer.OrderAnnotation} makes that explicit rather than leaving it to JUnit's default
 * ordering. The walk that follows leaves the selection back on {@link SectionId#RUN} so that the
 * second driver's run starts where the first one did.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KeyboardOnlyNavigationUiTest {

    private static RunningApplication application;

    @BeforeAll
    static void launchTheApplication() {
        application = RunningApplication.launchedByMain();
    }

    @AfterAll
    static void stopTheApplication() {
        if (application != null) {
            application.stop();
        }
    }

    /**
     * The drivers the keyboard walk is run through.
     *
     * @return TestFX first, then the TestFX-free fallback
     */
    static Stream<FxUiDriver> drivers() {
        assertNotNull(application, "the application must be running before a driver is built");
        return Stream.of(new TestFxUiDriver(application), new RobotFxUiDriver(application));
    }

    @Test
    @Order(1)
    @DisplayName("from a fresh application, Tab lands on the selected entry -- the only tab stop")
    void tabFromAFreshApplicationLandsOnTheSelectedNavigationEntry() {
        FxUiDriver driver = new TestFxUiDriver(application);

        assertAll(
                "a fresh application",
                () -> assertTrue(driver.isVisible(UiIds.sectionPane(SectionId.RUN))),
                () ->
                        assertEquals(
                                SectionId.RUN.title(), driver.textOf(UiIds.SHELL_SECTION_TITLE)));

        driver.tab();

        String entryId = UiIds.navigationEntry(SectionId.RUN);
        ToggleButton entry = (ToggleButton) driver.node(entryId);
        assertAll(
                "after one Tab",
                () ->
                        assertEquals(
                                entryId,
                                driver.focusedNodeId(),
                                "Tab from the top of the scene must reach the selected navigation"
                                        + " entry; the header holds only labels, which are not"
                                        + " traversable"),
                () ->
                        assertTrue(
                                driver.callOnFxThread(entry::isFocused),
                                "the entry the focus owner names must actually be focused"),
                () ->
                        assertTrue(
                                driver.callOnFxThread(entry::isFocusTraversable),
                                "the focused entry must be a tab stop"),
                () ->
                        assertTrue(
                                driver.isVisible(entryId),
                                "focus must be somewhere the user can see"),
                () ->
                        assertEquals(
                                List.of(entryId),
                                traversableEntries(driver),
                                "exactly one navigation entry is a tab stop: the selected one"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("drivers")
    @Order(2)
    @DisplayName("every section is reached with key presses alone, and the ends do not wrap")
    void everySectionIsReachableByKeyboardAlone(FxUiDriver driver) {
        List<SectionId> order = SectionId.displayOrder();
        assertEquals(
                8,
                SectionId.primarySections().size(),
                "eight primary sections, per the architecture");

        driver.tab();
        assertEquals(
                UiIds.navigationEntry(SectionId.RUN),
                driver.focusedNodeId(),
                "the walk starts with the focus on the first section's entry");

        List<SectionId> reached = new ArrayList<>();
        reached.add(SectionId.RUN);
        assertSelected(driver, SectionId.RUN);

        for (int i = 1; i < order.size(); i++) {
            driver.press(KeyCode.DOWN);
            SectionId expected = order.get(i);
            assertSelected(driver, expected);
            reached.add(expected);
        }
        assertEquals(
                order,
                reached,
                "Down must walk the selection through every section in display order");
        assertTrue(
                reached.containsAll(SectionId.primarySections()),
                "every primary section was reached by keyboard alone");

        driver.press(KeyCode.DOWN);
        assertSelected(driver, order.get(order.size() - 1));

        driver.press(KeyCode.UP);
        assertSelected(driver, order.get(order.size() - 2));

        driver.press(KeyCode.RIGHT);
        assertSelected(driver, order.get(order.size() - 1));

        driver.press(KeyCode.LEFT);
        assertSelected(driver, order.get(order.size() - 2));

        for (int i = 0; i < order.size(); i++) {
            driver.press(KeyCode.UP);
        }
        assertSelected(driver, SectionId.RUN);
    }

    @Test
    @Order(3)
    @DisplayName("Tab leaves the navigation for the console's controls, and Shift+Tab comes back")
    void tabLeavesTheNavigationAndShiftTabReturns() {
        FxUiDriver driver = new TestFxUiDriver(application);

        driver.tab();
        assertEquals(UiIds.navigationEntry(SectionId.RUN), driver.focusedNodeId());

        int stepsToConsole = SectionId.displayOrder().indexOf(SectionId.CONSOLE);
        for (int i = 0; i < stepsToConsole; i++) {
            driver.press(KeyCode.DOWN);
        }
        assertSelected(driver, SectionId.CONSOLE);

        driver.tab();
        assertEquals(
                UiIds.CONSOLE_STAGE_FILTER_ALL,
                driver.focusedNodeId(),
                "Tab must leave the navigation for the first control of the shown section");

        driver.shiftTab();
        assertEquals(
                UiIds.navigationEntry(SectionId.CONSOLE),
                driver.focusedNodeId(),
                "Shift+Tab must come back to the navigation's single tab stop");
        assertSelected(driver, SectionId.CONSOLE);

        for (int i = 0; i < SectionId.displayOrder().size(); i++) {
            driver.press(KeyCode.UP);
        }
        assertSelected(driver, SectionId.RUN);
    }

    /**
     * The section is selected: its pane is the only one showing, the header says so, and the focus
     * has moved with the selection.
     *
     * @param driver the driver to ask
     * @param expected the section that must be selected
     */
    private static void assertSelected(FxUiDriver driver, SectionId expected) {
        int showing = 0;
        for (SectionId section : SectionId.displayOrder()) {
            boolean visible = driver.isVisible(UiIds.sectionPane(section));
            assertEquals(
                    section == expected,
                    visible,
                    "#"
                            + UiIds.sectionPane(section)
                            + " showing, with "
                            + expected.id()
                            + " chosen");
            if (visible) {
                showing++;
            }
        }
        assertEquals(1, showing, "exactly one section pane may be showing at a time");
        assertEquals(
                expected.title(),
                driver.textOf(UiIds.SHELL_SECTION_TITLE),
                "the header's echo of the selected section");
        assertEquals(
                UiIds.navigationEntry(expected),
                driver.focusedNodeId(),
                "the roving tab stop must follow the selection");
        assertEquals(
                List.of(UiIds.navigationEntry(expected)),
                traversableEntries(driver),
                "the selected entry must be the only traversable one");
    }

    /**
     * The identifiers of the navigation entries that are focus traversable.
     *
     * @param driver the driver to ask
     * @return the identifiers, in display order; the roving tab stop makes this exactly one
     */
    private static List<String> traversableEntries(FxUiDriver driver) {
        List<String> traversable = new ArrayList<>();
        for (SectionId section : SectionId.displayOrder()) {
            String id = UiIds.navigationEntry(section);
            ToggleButton entry = (ToggleButton) driver.node(id);
            if (Boolean.TRUE.equals(driver.callOnFxThread(entry::isFocusTraversable))) {
                traversable.add(id);
            }
        }
        return traversable;
    }
}
