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
import java.util.Set;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Control;
import org.cometgui.app.uidriver.FxUiDriver;
import org.cometgui.app.uidriver.RunningApplication;
import org.cometgui.app.uidriver.TestFxUiDriver;
import org.cometgui.domain.log.MessageSeverity;
import org.cometgui.ui.controls.AccessibleControls;
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
 * adds a control and forgets to add it to the list, which is the only day the gate matters. All
 * nine section panes are children of the content area at all times, so one walk sees the whole
 * interface rather than only the selected section.
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
 * the nine navigation entries, the console's text area, its filters -- were among them. The floor
 * is a floor and not an exact number on purpose: adding a control to the interface must not break
 * this test, only failing to name one must.
 */
class AccessibleNameEnumerationUiTest {

    /**
     * The fewest controls a shell with nine section panes, nine navigation entries, a stage stepper
     * and a console can possibly contain.
     *
     * <p>Derived rather than guessed, and deliberately an underestimate:
     *
     * <ul>
     *   <li>navigation: 9 entries + 1 separator = 10
     *   <li>header: the application title, the selected-section echo and the baseline banner = 3
     *   <li>section panes: 9 x (heading, description, arrival note) = 27
     *   <li>console: title, output, summary, clear, copy, "all stages" and 4 severity filters = 10
     *   <li>stage stepper: 5 core stages x (name, state) + the run-state line = 11
     *   <li>stage stepper arrows, each a named {@code Label}: 4 on the core path + 3 on the
     *       branches = 7
     * </ul>
     *
     * <p>68 in total, before the stepper's branch rows, the console's per-stage filters, the Tool
     * Manager's content and everything the skins build. The failure message prints the number
     * actually seen.
     *
     * <p>Plus, since phase 10 unit 8, the Results section with no run open -- which is what this
     * walk sees: the application is launched by its own main and finds no project -- 49 identified
     * controls ({@link #RESULTS_IDENTIFIED}): its own 39 (the pane, readiness, run selector and
     * refresh, table selector, two filter fields and their status, view state, four counts and
     * their sentence, category, text filter, four page actions and the page's position, table
     * status, the table, nine column switches, column status, selection, copy and its status, two
     * exports and their status) and the learned feature weights' 10 (title, description, status,
     * table and the six headings it has before any split is known). The results table's nine
     * sorting headings are not counted: a table with no run open shows no column, so their headers
     * are not built. 68 + 49 = 117.
     *
     * <p><strong>History of this floor.</strong> Phase 02 set it at 65 for ten sections and a
     * derivation that left the arrows out (its walk found 91). Phase 07 unit 2 removed the Settings
     * section from navigation (tier-1 decision, {@code STATUS.rst}, <em>The Settings section</em>),
     * which takes one navigation entry and three pane labels out of the derivation (65 - 4 = 61).
     * Rather than lower the floor to 61, the arrows -- which were always there and always counted
     * by the walk -- were added to the derivation, and the floor was raised to 68. Phase 10 unit 8
     * raised it to 117 for the Results section, as derived above.
     */
    private static final int MINIMUM_CONTROLS = 117;

    /**
     * The Results section's identified controls with no run open, typed out (P7-5). Every one must
     * be reached by the walk and carry a name of its own; {@code ResultsAccessibilityUiTest} walks
     * the same section with a run open, headings included.
     */
    static final List<String> RESULTS_IDENTIFIED =
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
                    "weights-sort-mean-signed",
                    "weights-sort-mean-absolute",
                    "weights-sort-standard-deviation",
                    "weights-sort-sign-consistency",
                    "weights-sort-rank");

    /**
     * The identifiers JavaFX's own skins give the controls they build. Every control this project
     * creates carries a stable identifier and skin-built ones carry none -- except this one: {@code
     * ComboBoxListViewSkin} identifies the list it builds for a combo box's choices as {@code
     * list-view}. Observed, not assumed: it is the only identified control the walk found with a
     * generated name before any of this project's controls were checked.
     */
    static final Set<String> SKIN_IDENTIFIERS = Set.of("list-view");

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

    /**
     * A control this project created carries a name the code gave it, never the fallback {@code
     * AccessibleControls} generates for controls a skin builds. Every control this project creates
     * carries a stable identifier and no skin-built one does, so the identifier is what tells them
     * apart. Without this, a control whose {@code named(...)} call was removed would still pass the
     * test above on its generated "... within ..." name: Phase 07's sign-off showed exactly that.
     */
    @Test
    @DisplayName("no control this project created carries a generated name")
    void noProjectControlCarriesAGeneratedName() {
        List<String> generated = new ArrayList<>();
        int identified = 0;
        for (Control control : controls) {
            String id = driver.callOnFxThread(control::getId);
            if (id != null && !id.isBlank() && !SKIN_IDENTIFIERS.contains(id)) {
                identified++;
                if (driver.callOnFxThread(() -> AccessibleControls.hasGeneratedName(control))) {
                    generated.add(
                            describe(control)
                                    + " is named only by the fallback: \""
                                    + driver.callOnFxThread(control::getAccessibleText)
                                    + "\"");
                }
            }
        }
        assertTrue(
                identified >= MINIMUM_CONTROLS,
                "the walk found " + identified + " identified controls");
        assertEquals(
                List.of(),
                generated,
                () ->
                        generated.size()
                                + " controls this project created have no name of their own: "
                                + String.join("; ", generated));
    }

    @Test
    @DisplayName("the Results section's 49 identified controls with no run open, each named")
    void theResultsSectionIsEnumerated() {
        assertEquals(49, RESULTS_IDENTIFIED.size(), "the derivation of MINIMUM_CONTROLS");
        assertEquals(
                RESULTS_IDENTIFIED.size(),
                Set.copyOf(RESULTS_IDENTIFIED).size(),
                "no identifier is listed twice");
        List<String> resultsIds =
                controls.stream()
                        .map(control -> driver.callOnFxThread(control::getId))
                        .filter(
                                id ->
                                        id != null
                                                && (id.startsWith("results-")
                                                        || id.startsWith("weights-")))
                        .toList();
        assertEquals(
                Set.copyOf(RESULTS_IDENTIFIED),
                Set.copyOf(resultsIds),
                "the Results section's identified controls with no run open");
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
        expected.addAll(RESULTS_IDENTIFIED);

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
