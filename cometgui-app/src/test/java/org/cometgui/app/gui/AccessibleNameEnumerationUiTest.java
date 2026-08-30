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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Control;
import org.cometgui.app.uidriver.FxUiDriver;
import org.cometgui.app.uidriver.RunningApplication;
import org.cometgui.app.uidriver.TestFxUiDriver;
import org.cometgui.domain.log.MessageSeverity;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.viewmodel.SectionId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Phase 02 exit-gate item 4: "Every control that exists has an accessible name; a test enumerates
 * them and fails on a missing one."
 *
 * <p>The specification's <em>Accessibility</em> principle is what it serves: "Every interactive
 * control requires an accessible label ... custom JavaFX controls shall expose appropriate
 * accessibility attributes."
 *
 * <h2>Every control, not every control a test remembered</h2>
 *
 * <p>The enumeration walks the whole scene graph of the launched application and collects every
 * {@link Control}. It is not a list of identifiers to check: a list would pass on the day someone
 * adds a control and forgets to add it to the list, which is the only day the gate matters. All ten
 * section panes are children of the content area at all times, so one walk sees the whole interface
 * rather than only the selected section.
 *
 * <h2>After applyCss() and layout(), because half the controls do not exist before that</h2>
 *
 * <p>A {@code TextArea} is one control in the source and four in the scene graph: its skin builds a
 * {@code ScrollPane}, which builds two {@code ScrollBar}s. None of them exists until CSS has been
 * applied and the skin built, so a walk that ran earlier would enumerate a smaller, easier
 * interface and report success about controls it never saw. {@code AccessibleControls} is what
 * gives those skin-built controls a name -- it watches the children of every control this project
 * names explicitly -- and this test is what proves the watching works.
 *
 * <h2>The count is asserted too</h2>
 *
 * <p>A walk that found three controls and named all three would pass while proving nothing. So the
 * test asserts a floor on how many controls were seen, and separately that a specific handful --
 * the ten navigation entries, the console's text area, its filters -- were among them. The floor is
 * a floor and not an exact number on purpose: adding a control to the interface must not break this
 * test, only failing to name one must.
 */
class AccessibleNameEnumerationUiTest {

    /**
     * The fewest controls a shell with ten section panes, ten navigation entries, a stage stepper
     * and a console can possibly contain.
     *
     * <p>Derived rather than guessed, and deliberately an underestimate:
     *
     * <ul>
     *   <li>navigation: 10 entries + 1 separator = 11
     *   <li>header: the application title, the selected-section echo and the baseline banner = 3
     *   <li>section panes: 10 x (heading, description, arrival note) = 30
     *   <li>console: title, output, summary, clear, copy, "all stages" and 4 severity filters = 10
     *   <li>stage stepper: 5 core stages x (name, state) + the run-state line = 11
     * </ul>
     *
     * <p>65 in total, before the stepper's arrows and branch rows, the console's per-stage filters
     * and everything the skins build. This build's walk finds 91; the floor is what must hold, and
     * the failure message prints the number actually seen.
     */
    private static final int MINIMUM_CONTROLS = 65;

    private static RunningApplication application;

    private static FxUiDriver driver;

    private static List<Control> controls;

    @BeforeAll
    static void launchTheApplicationAndWalkTheScene() {
        application = RunningApplication.launchedByMain();
        driver = new TestFxUiDriver(application);
        Parent root = (Parent) driver.node(UiIds.SHELL_ROOT);
        controls =
                driver.callOnFxThread(
                        () -> {
                            root.applyCss();
                            root.layout();
                            List<Control> found = new ArrayList<>();
                            collectControls(root, found);
                            return found;
                        });
    }

    @AfterAll
    static void stopTheApplication() {
        if (application != null) {
            application.stop();
        }
    }

    @Test
    @DisplayName("every control in the running application has a non-blank accessible name")
    void everyControlHasAnAccessibleName() {
        List<String> unnamed = new ArrayList<>();
        for (Control control : controls) {
            String accessibleText = driver.callOnFxThread(control::getAccessibleText);
            if (accessibleText == null || accessibleText.isBlank()) {
                unnamed.add(describe(control));
            }
        }
        assertEquals(
                List.of(),
                unnamed,
                () ->
                        "every control must have an accessible name (specification.rst, Design"
                                + " principles, Accessibility). "
                                + unnamed.size()
                                + " of "
                                + controls.size()
                                + " controls have none: "
                                + String.join("; ", unnamed));
    }

    @Test
    @DisplayName("the walk saw the whole interface, not a corner of it")
    void theWalkSawTheWholeInterface() {
        List<String> identified =
                controls.stream()
                        .map(control -> driver.callOnFxThread(control::getId))
                        .filter(id -> id != null && !id.isBlank())
                        .toList();

        List<String> expected = new ArrayList<>();
        for (SectionId section : SectionId.displayOrder()) {
            expected.add(UiIds.navigationEntry(section));
            expected.add(UiIds.sectionHeading(section));
            expected.add(UiIds.sectionDescription(section));
        }
        expected.add(UiIds.SHELL_TITLE);
        expected.add(UiIds.SHELL_SECTION_TITLE);
        expected.add(UiIds.HOST_BASELINE_BANNER);
        expected.add(UiIds.CONSOLE_OUTPUT);
        expected.add(UiIds.CONSOLE_SUMMARY);
        expected.add(UiIds.CONSOLE_CLEAR);
        expected.add(UiIds.CONSOLE_COPY);
        expected.add(UiIds.CONSOLE_STAGE_FILTER_ALL);
        for (MessageSeverity severity : MessageSeverity.values()) {
            expected.add(UiIds.consoleSeverityFilter(severity));
        }

        assertAll(
                () ->
                        assertTrue(
                                controls.size() >= MINIMUM_CONTROLS,
                                "the walk found only "
                                        + controls.size()
                                        + " controls, fewer than the "
                                        + MINIMUM_CONTROLS
                                        + " the shell cannot be built without; a walk that finds"
                                        + " nothing proves nothing"),
                () ->
                        assertTrue(
                                identified.containsAll(expected),
                                () -> {
                                    List<String> missing = new ArrayList<>(expected);
                                    missing.removeAll(identified);
                                    return "the walk did not reach these controls: " + missing;
                                }),
                () ->
                        assertTrue(
                                controls.size() > identified.size(),
                                "the walk must also reach the controls nobody wrote -- the skins'"
                                        + " scroll bars and their scroll pane, which carry no"
                                        + " identifier and are named by AccessibleControls"));
    }

    @Test
    @DisplayName("the controls a skin built are named after what they are and what they belong to")
    void skinBuiltControlsAreNamedByTheirOwner() {
        List<String> generated =
                controls.stream()
                        .filter(control -> driver.callOnFxThread(control::getId) == null)
                        .map(control -> driver.callOnFxThread(control::getAccessibleText))
                        .filter(name -> name != null && name.contains(" within "))
                        .toList();

        assertTrue(
                generated.stream()
                        .anyMatch(name -> name.endsWith(" within " + UiIds.CONSOLE_OUTPUT)),
                "the console's text area builds a scroll pane and two scroll bars; each must be"
                        + " named after it. Generated names seen: "
                        + generated);
    }

    /**
     * Adds every {@link Control} at or below this node to the list, depth first.
     *
     * @param node the node to walk from
     * @param found the list to add to
     */
    private static void collectControls(Node node, List<Control> found) {
        if (node instanceof Control control) {
            found.add(control);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectControls(child, found);
            }
        }
    }

    /**
     * A control a reader can find again: what it is, what identifier it carries, and what it sits
     * under.
     *
     * @param control the offending control
     * @return a description naming its class and its identifier
     */
    private static String describe(Control control) {
        String id = driver.callOnFxThread(control::getId);
        String owner =
                driver.callOnFxThread(
                        () -> {
                            for (Node above = control.getParent();
                                    above != null;
                                    above = above.getParent()) {
                                if (above.getId() != null && !above.getId().isBlank()) {
                                    return above.getId();
                                }
                            }
                            return "the scene root";
                        });
        return control.getClass().getSimpleName()
                + " with id "
                + (id == null ? "<none>" : "#" + id)
                + " under #"
                + owner;
    }
}
