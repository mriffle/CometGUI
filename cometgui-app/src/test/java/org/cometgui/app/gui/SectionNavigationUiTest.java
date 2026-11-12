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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import javafx.scene.Node;
import javafx.scene.control.ToggleButton;
import org.cometgui.app.uidriver.FxUiDriver;
import org.cometgui.app.uidriver.RobotFxUiDriver;
import org.cometgui.app.uidriver.RunningApplication;
import org.cometgui.app.uidriver.TestFxUiDriver;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.view.SectionPane;
import org.cometgui.ui.viewmodel.SectionId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Phase 02 exit-gate items 1 (by mouse) and 2, against the real application, headless.
 *
 * <p>Item 2: "A headless GUI test navigates all sections and asserts each is present by stable
 * identifier." Item 1, first half: "The application starts, and every primary section is reachable
 * by mouse". Both are the same walk, so they are one test: click each navigation entry with a robot
 * and assert that exactly that section's pane is showing, found by {@code UiIds}, never by position
 * or by CSS ancestry ({@code R-TEST-04}).
 *
 * <p><strong>The click is synthetic.</strong> {@link FxUiDriver#clickOn(String)} moves a pointer to
 * the entry's own screen bounds and presses a button; it does not fire an {@code ActionEvent} and
 * does not call {@code NavigationViewModel.select}. {@code ShellViewTest} in cometgui-ui already
 * proves the action handler works when fired; what is unproved until a robot clicks is whether the
 * control is reachable by a mouse at all.
 *
 * <p><strong>Both drivers, same test.</strong> The parameterised test runs the whole walk through
 * TestFX and again through plain {@code javafx.scene.robot.Robot}. That is what makes the {@code
 * FxUiDriver} abstraction worth having rather than a layer of indirection: the fallback phase 00
 * named is exercised on every build, so it cannot rot.
 *
 * <p><strong>A fresh application, over a data directory of this test's own.</strong> The
 * application is the real one -- its own Tool Manager and default project -- started over an empty
 * application-data directory in this test's temporary directory ({@link
 * ParameterEditorApp#launchOver}), not through {@code main}, which reads this machine's
 * application-data directory and so makes whatever is installed there part of the walk (and,
 * finding a Comet there, creates and locks a project in it). {@link #theApplicationIsAFreshOne}
 * states the assumption as a value. That {@code main} itself starts the application is {@code
 * CometGuiApplicationStartupTest}'s to show.
 */
class SectionNavigationUiTest {

    /** The nine section identifiers, written out so that the walk below cannot be vacuous. */
    private static final List<String> EXPECTED_SECTION_IDS =
            List.of(
                    "run",
                    "comet-parameters",
                    "percolator",
                    "results",
                    "visualisation",
                    "limelight",
                    "provenance",
                    "console",
                    "tool-manager");

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-11-12T00:00:00Z"));

    @TempDir private static Path scratch;

    private static Path applicationData;

    private static RunningApplication application;

    @BeforeAll
    static void launchTheApplication() throws IOException {
        applicationData = scratch.toRealPath().resolve("application-data");
        application = ParameterEditorApp.launchOver(BUILD, applicationData).application();
    }

    @AfterAll
    static void stopTheApplication() {
        if (application != null) {
            application.stop();
        }
    }

    /**
     * The drivers the navigation walk is run through.
     *
     * @return TestFX first, then the TestFX-free fallback
     */
    static Stream<FxUiDriver> drivers() {
        assertNotNull(application, "the application must be running before a driver is built");
        return Stream.of(new TestFxUiDriver(application), new RobotFxUiDriver(application));
    }

    @Test
    @DisplayName(
            "the application walked is a fresh one over this test's own application-data"
                    + " directory: nothing is listed as installed, and nothing was created there")
    void theApplicationIsAFreshOne() {
        FreshApplicationData.assertFresh(
                application, new TestFxUiDriver(application), applicationData);
    }

    @Test
    @DisplayName("the application shows one window, titled CometGUI, whose root is the shell")
    void theApplicationIsRunning() {
        FxUiDriver driver = new TestFxUiDriver(application);
        Node root = driver.node(UiIds.SHELL_ROOT);
        assertAll(
                () -> assertEquals("CometGUI", application.title()),
                () -> assertTrue(application.isShowing(), "the stage is not showing"),
                () ->
                        assertEquals(
                                UiIds.SHELL_ROOT,
                                driver.callOnFxThread(root::getId),
                                "the shell is in the scene under its own stable identifier"),
                () ->
                        assertEquals(
                                "ShellView",
                                driver.callOnFxThread(() -> root.getClass().getSimpleName()),
                                "the root found by identifier is the real shell"));
    }

    @Test
    @DisplayName(
            "the nine sections the walk visits are the nine the information architecture names")
    void theSectionsAreTheOnesTheArchitectureNames() {
        assertEquals(
                EXPECTED_SECTION_IDS,
                SectionId.displayOrder().stream().map(SectionId::id).toList(),
                "the walk below iterates SectionId.displayOrder(); if that list changed, the"
                        + " coverage of this test changed with it");
    }

    /**
     * The running application has no Settings navigation entry and no Settings pane.
     *
     * <p>Phase 07 removed the section (tier-1 decision, {@code STATUS.rst}, <em>The Settings
     * section</em>) because nothing the specification requires is configurable at application
     * level. The identifiers it used to carry are typed by hand, so this asserts the absence in the
     * real scene rather than in a list the test computed.
     */
    @Test
    @DisplayName("the running application has no Settings entry and no Settings pane")
    void theApplicationHasNoSettingsSection() {
        FxUiDriver driver = new TestFxUiDriver(application);
        for (String removed : List.of("nav-settings", "section-settings")) {
            AssertionError thrown = assertThrows(AssertionError.class, () -> driver.node(removed));
            assertTrue(
                    thrown.getMessage()
                            .startsWith("no node with the stable identifier #" + removed + " "),
                    () ->
                            "the lookup failure does not name "
                                    + removed
                                    + ": "
                                    + thrown.getMessage());
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("drivers")
    @DisplayName("a robot click on every navigation entry shows exactly that section's pane")
    void clickingEveryNavigationEntryShowsExactlyThatSection(FxUiDriver driver) {
        for (SectionId section : SectionId.displayOrder()) {
            driver.clickOn(UiIds.navigationEntry(section));

            Node pane = driver.node(UiIds.sectionPane(section));
            ToggleButton entry = (ToggleButton) driver.node(UiIds.navigationEntry(section));
            assertAll(
                    "after clicking #" + UiIds.navigationEntry(section) + " with " + driver,
                    () ->
                            assertInstanceOf(
                                    SectionPane.class,
                                    pane,
                                    "#" + UiIds.sectionPane(section) + " is not a section pane"),
                    () ->
                            assertEquals(
                                    section,
                                    driver.callOnFxThread(() -> ((SectionPane) pane).section()),
                                    "the pane found by identifier belongs to another section"),
                    () ->
                            assertEquals(
                                    section.title(),
                                    driver.textOf(UiIds.sectionHeading(section)),
                                    "the shown pane's heading"),
                    () ->
                            assertEquals(
                                    section.title(),
                                    driver.textOf(UiIds.SHELL_SECTION_TITLE),
                                    "the header's echo of the selected section"),
                    () ->
                            assertEquals(
                                    "selected section: " + section.title(),
                                    driver.accessibleTextOf(UiIds.SHELL_SECTION_TITLE),
                                    "what a screen reader would announce after the click"),
                    () ->
                            assertTrue(
                                    driver.callOnFxThread(entry::isSelected),
                                    "the clicked navigation entry is not selected"),
                    () -> assertShowsOnly(driver, section));
        }
    }

    /**
     * Exactly one section pane is showing, and it is this one.
     *
     * @param driver the driver to ask
     * @param expected the section whose pane must be the only one showing
     */
    private static void assertShowsOnly(FxUiDriver driver, SectionId expected) {
        int showing = 0;
        for (SectionId section : SectionId.displayOrder()) {
            boolean visible = driver.isVisible(UiIds.sectionPane(section));
            assertEquals(
                    section == expected,
                    visible,
                    "#"
                            + UiIds.sectionPane(section)
                            + (section == expected
                                    ? " should be showing after selecting it"
                                    : " should not be showing while "
                                            + expected.id()
                                            + " is selected"));
            if (visible) {
                showing++;
            }
        }
        assertEquals(1, showing, "exactly one section pane may be showing at a time");
    }
}
