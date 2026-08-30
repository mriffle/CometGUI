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

package org.cometgui.ui.view;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.platform.HostBaselineOutcome;
import org.cometgui.domain.platform.HostBaselineReport;
import org.cometgui.ui.controls.StageStepper;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.controls.derived.ConsolePane;
import org.cometgui.ui.testing.FxToolkit;
import org.cometgui.ui.viewmodel.ConsoleViewModel;
import org.cometgui.ui.viewmodel.HostBaselineViewModel;
import org.cometgui.ui.viewmodel.NavigationViewModel;
import org.cometgui.ui.viewmodel.SectionId;
import org.cometgui.ui.viewmodel.StageStepperViewModel;
import org.cometgui.workflow.state.WorkflowStage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The shell: its navigation, its ten section panes, the pane the content area shows, and the
 * keyboard reachability the phase's first exit-gate item requires.
 *
 * <p>These are construction-level tests, built headless the way {@code HeadlessSceneTest} proved
 * possible on this machine. They deliberately stop short of driving a robot: the mouse and keyboard
 * tests, the accessible-name enumeration and the console flood test belong to the unit that builds
 * the {@code FxUiDriver}. What is proved here is that the scene contains what it claims to, found
 * by the stable identifiers of {@code R-TEST-04} and never by position.
 *
 * <p>The one key test below fires a real {@link KeyEvent} at the navigation container rather than
 * calling {@code selectNext()}. That is the point of it: the view-model's own tests already prove
 * the movement rule, and what is unproved until a key event travels through the filter is whether
 * the view is listening at all.
 */
class ShellViewTest {

    private NavigationViewModel navigation;

    private StageStepperViewModel stepper;

    private ShellView shell;

    private Scene scene;

    @BeforeAll
    static void startToolkit() throws InterruptedException {
        FxToolkit.start();
    }

    @BeforeEach
    void buildShell() throws InterruptedException {
        navigation = new NavigationViewModel();
        stepper = new StageStepperViewModel();
        ConsoleViewModel console = new ConsoleViewModel(new BoundedMessageLog(64));
        HostBaselineViewModel baseline =
                new HostBaselineViewModel(
                        new HostBaselineReport(
                                HostBaselineOutcome.SUPPORTED, "64-bit host, glibc 2.36."));
        FxToolkit.onFxThread(
                () -> {
                    shell = new ShellView(navigation, baseline, stepper, console);
                    scene = new Scene(shell, 1280, 800);
                    scene.getRoot().applyCss();
                    scene.getRoot().layout();
                });
    }

    @Test
    @DisplayName("every section has a pane and a navigation entry, findable by its stable id")
    void everySectionHasAPaneAndAnEntry() {
        for (SectionId section : SectionId.displayOrder()) {
            Node pane = scene.lookup("#" + UiIds.sectionPane(section));
            Node entry = scene.lookup("#" + UiIds.navigationEntry(section));
            assertAll(
                    "section " + section.id(),
                    () -> assertNotNull(pane, "no pane with id " + UiIds.sectionPane(section)),
                    () -> assertSame(shell.paneFor(section), pane),
                    () -> assertInstanceOf(SectionPane.class, pane),
                    () ->
                            assertNotNull(
                                    entry, "no entry with id " + UiIds.navigationEntry(section)),
                    () -> assertEquals(section.title(), ((ToggleButton) entry).getText()),
                    () ->
                            assertEquals(
                                    section.title() + " section",
                                    ((ToggleButton) entry).getAccessibleText()));
        }
        assertEquals(
                10,
                SectionId.displayOrder().size(),
                "the information architecture has eight primary and two secondary sections");
    }

    @Test
    @DisplayName("each pane states the section's title, its description and which phase fills it")
    void eachPaneStatesItsHeadingDescriptionAndArrivalNote() {
        for (SectionId section : SectionId.displayOrder()) {
            Label heading = (Label) scene.lookup("#" + UiIds.sectionHeading(section));
            Label description = (Label) scene.lookup("#" + UiIds.sectionDescription(section));
            Label note = (Label) scene.lookup("#" + UiIds.sectionNote(section));
            assertAll(
                    "section " + section.id(),
                    () -> assertEquals(section.title(), heading.getText()),
                    () -> assertEquals(section.description(), description.getText()),
                    () -> assertEquals(SectionArrivals.noteFor(section), note.getText()));
        }
    }

    @Test
    @DisplayName("the navigation lists the eight primary sections, a rule, then the two secondary")
    void theNavigationSeparatesTheSecondarySections() {
        Parent bar = (Parent) scene.lookup("#" + UiIds.NAVIGATION);
        List<Node> children = List.copyOf(bar.getChildrenUnmodifiable());
        assertEquals(11, children.size(), "eight primary entries, a separator, two secondary");
        for (int i = 0; i < 8; i++) {
            assertEquals(
                    UiIds.navigationEntry(SectionId.primarySections().get(i)),
                    children.get(i).getId(),
                    "primary entry " + i);
        }
        assertInstanceOf(Separator.class, children.get(8));
        assertEquals(UiIds.NAVIGATION_SEPARATOR, children.get(8).getId());
        assertEquals(UiIds.navigationEntry(SectionId.TOOL_MANAGER), children.get(9).getId());
        assertEquals(UiIds.navigationEntry(SectionId.SETTINGS), children.get(10).getId());
    }

    @Test
    @DisplayName("the content area shows exactly the selected section's pane")
    void theContentAreaShowsExactlyTheSelectedPane() throws InterruptedException {
        assertEquals(SectionId.RUN, navigation.selectedSection());
        assertShowsOnly(SectionId.RUN);
        assertEquals("Run", ((Label) scene.lookup("#" + UiIds.SHELL_SECTION_TITLE)).getText());

        FxToolkit.onFxThread(() -> navigation.select(SectionId.PROVENANCE));

        assertShowsOnly(SectionId.PROVENANCE);
        assertEquals(
                "Provenance", ((Label) scene.lookup("#" + UiIds.SHELL_SECTION_TITLE)).getText());
        assertTrue(
                shell.navigationEntryFor(SectionId.PROVENANCE).isSelected(),
                "the navigation entry follows the selection");
        assertFalse(shell.navigationEntryFor(SectionId.RUN).isSelected());
    }

    @Test
    @DisplayName("a navigation entry's action selects its section")
    void aNavigationEntryActionSelectsItsSection() throws InterruptedException {
        FxToolkit.onFxThread(() -> shell.navigationEntryFor(SectionId.LIMELIGHT).fire());

        assertEquals(SectionId.LIMELIGHT, navigation.selectedSection());
        assertShowsOnly(SectionId.LIMELIGHT);
    }

    @Test
    @DisplayName("firing the selected entry again leaves it selected rather than deselecting it")
    void firingTheSelectedEntryAgainLeavesItSelected() throws InterruptedException {
        FxToolkit.onFxThread(() -> shell.navigationEntryFor(SectionId.RUN).fire());

        assertEquals(SectionId.RUN, navigation.selectedSection());
        assertTrue(shell.navigationEntryFor(SectionId.RUN).isSelected());
        assertShowsOnly(SectionId.RUN);
    }

    @Test
    @DisplayName("arrow keys walk the selection through all ten sections and stop at the ends")
    void arrowKeysWalkThroughEverySectionIncludingTheSecondaryOnes() throws InterruptedException {
        List<SectionId> order = SectionId.displayOrder();
        List<SectionId> walked = new ArrayList<>();
        walked.add(navigation.selectedSection());
        for (int i = 1; i < order.size(); i++) {
            press(KeyCode.DOWN);
            walked.add(navigation.selectedSection());
        }
        assertEquals(order, walked, "Down must reach every section in display order");
        assertShowsOnly(SectionId.SETTINGS);

        press(KeyCode.DOWN);
        assertEquals(
                SectionId.SETTINGS,
                navigation.selectedSection(),
                "the selection does not wrap past the last section");

        press(KeyCode.UP);
        assertEquals(SectionId.TOOL_MANAGER, navigation.selectedSection());
        assertShowsOnly(SectionId.TOOL_MANAGER);

        for (int i = 0; i < order.size(); i++) {
            press(KeyCode.UP);
        }
        assertEquals(
                SectionId.RUN,
                navigation.selectedSection(),
                "the selection does not wrap past the first section");
        assertShowsOnly(SectionId.RUN);
    }

    @Test
    @DisplayName("Right and Left move the selection as Down and Up do")
    void rightAndLeftMoveTheSelectionToo() throws InterruptedException {
        press(KeyCode.RIGHT);
        assertEquals(SectionId.COMET_PARAMETERS, navigation.selectedSection());
        press(KeyCode.LEFT);
        assertEquals(SectionId.RUN, navigation.selectedSection());
    }

    @Test
    @DisplayName("a key the navigation does not use is left alone")
    void anUnusedKeyIsNotConsumed() throws InterruptedException {
        KeyEvent event = keyEvent(KeyCode.TAB);
        FxToolkit.onFxThread(() -> scene.lookup("#" + UiIds.NAVIGATION).fireEvent(event));

        assertFalse(event.isConsumed(), "Tab must still traverse focus out of the navigation");
        assertEquals(SectionId.RUN, navigation.selectedSection());
    }

    @Test
    @DisplayName("the selected entry is the only focus stop, and Tab reaches it first")
    void theSelectedEntryIsTheOnlyFocusStopInTheNavigation() throws InterruptedException {
        assertSame(
                shell.navigationEntryFor(SectionId.RUN),
                firstFocusTraversable(scene.getRoot()),
                "the first focus-traversable control in the scene must be the selected entry");
        assertEquals(1, traversableEntries(), "exactly one navigation entry is a tab stop");

        FxToolkit.onFxThread(() -> navigation.select(SectionId.RESULTS));

        assertSame(
                shell.navigationEntryFor(SectionId.RESULTS),
                firstFocusTraversable(scene.getRoot()));
        assertEquals(1, traversableEntries());
    }

    @Test
    @DisplayName("the Run pane hosts the stepper and the Console pane hosts the console")
    void theRunAndConsolePanesHostTheirControls() {
        Node stageStepper = scene.lookup("#" + UiIds.STAGE_STEPPER);
        Node consolePane = scene.lookup("#" + UiIds.CONSOLE_PANE);
        assertAll(
                () -> assertInstanceOf(StageStepper.class, stageStepper),
                () -> assertInstanceOf(ConsolePane.class, consolePane),
                () ->
                        assertTrue(
                                isDescendantOf(stageStepper, shell.paneFor(SectionId.RUN)),
                                "the stepper belongs to the Run pane"),
                () ->
                        assertTrue(
                                isDescendantOf(consolePane, shell.paneFor(SectionId.CONSOLE)),
                                "the console belongs to the Console pane"),
                () ->
                        assertNotNull(
                                scene.lookup("#" + UiIds.stepperStageState(WorkflowStage.COMET)),
                                "the stepper's stages are reachable from the shell's scene"),
                () ->
                        assertNotNull(
                                scene.lookup("#" + UiIds.CONSOLE_OUTPUT),
                                "the console's output is reachable from the shell's scene"));
    }

    @Test
    @DisplayName("a satisfied host baseline leaves the banner slot present but hidden")
    void aSatisfiedHostBaselineHidesTheBanner() {
        Label banner = (Label) scene.lookup("#" + UiIds.HOST_BASELINE_BANNER);
        assertAll(
                () -> assertNotNull(banner, "the banner slot exists whether or not it is shown"),
                () -> assertFalse(banner.isVisible()),
                () -> assertFalse(banner.isManaged()),
                () ->
                        assertEquals(
                                "Host baseline satisfied: 64-bit host, glibc 2.36.",
                                banner.getText()));
    }

    @Test
    @DisplayName("a blocking host baseline shows the banner, with the severity in words")
    void aBlockingHostBaselineShowsTheBanner() throws InterruptedException {
        HostBaselineViewModel blocked =
                new HostBaselineViewModel(
                        new HostBaselineReport(
                                HostBaselineOutcome.NOT_64_BIT,
                                "This host reports a 32-bit JVM; the managed tools are 64-bit."));
        Scene other =
                FxToolkit.callOnFxThread(
                        () ->
                                new Scene(
                                        new ShellView(
                                                new NavigationViewModel(),
                                                blocked,
                                                new StageStepperViewModel(),
                                                new ConsoleViewModel(new BoundedMessageLog(8))),
                                        800,
                                        600));
        Label banner = (Label) other.lookup("#" + UiIds.HOST_BASELINE_BANNER);
        assertAll(
                () -> assertTrue(banner.isVisible()),
                () -> assertTrue(banner.isManaged()),
                () ->
                        assertEquals(
                                "Cannot continue: This host reports a 32-bit JVM; the managed"
                                        + " tools are 64-bit.",
                                banner.getText()),
                () -> assertEquals(banner.getText(), banner.getAccessibleText()));
    }

    /** Asserts that exactly one section pane is visible and managed, and that it is this one. */
    private void assertShowsOnly(SectionId expected) {
        for (SectionId section : SectionId.displayOrder()) {
            SectionPane pane = shell.paneFor(section);
            boolean shown = section == expected;
            assertEquals(shown, pane.isVisible(), "visibility of " + section.id());
            assertEquals(shown, pane.isManaged(), "layout of " + section.id());
        }
    }

    /** Fires a real key press at the navigation container and waits for it to be handled. */
    private void press(KeyCode code) throws InterruptedException {
        FxToolkit.onFxThread(() -> scene.lookup("#" + UiIds.NAVIGATION).fireEvent(keyEvent(code)));
    }

    private static KeyEvent keyEvent(KeyCode code) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
    }

    /** How many navigation entries are focus traversable. */
    private int traversableEntries() {
        int traversable = 0;
        for (SectionId section : SectionId.displayOrder()) {
            if (shell.navigationEntryFor(section).isFocusTraversable()) {
                traversable++;
            }
        }
        return traversable;
    }

    /** The first focus-traversable control in scene-graph order, which is Tab's first stop. */
    private static Node firstFocusTraversable(Node node) {
        if (node instanceof Control control && control.isFocusTraversable()) {
            return control;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Node found = firstFocusTraversable(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static boolean isDescendantOf(Node node, Node ancestor) {
        for (Node above = node.getParent(); above != null; above = above.getParent()) {
            if (above == ancestor) {
                return true;
            }
        }
        return false;
    }
}
