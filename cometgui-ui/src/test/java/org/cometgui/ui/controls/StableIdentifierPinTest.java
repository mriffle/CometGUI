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

package org.cometgui.ui.controls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.cometgui.domain.log.MessageSeverity;
import org.cometgui.ui.viewmodel.SectionId;
import org.cometgui.workflow.state.WorkflowStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every stable identifier the user interface sets, written out here as a hand-typed literal.
 *
 * <h2>What this class is for</h2>
 *
 * <p>{@code R-TEST-04} says controls required by automated tests "shall have
 * <strong>stable</strong> semantic identifiers". Stability is a property of the identifier over
 * time, and no test can observe it while the value it expects is computed from the code it is
 * checking. This class is the one place in the tree where the expected value is not computed at
 * all: it is typed out.
 *
 * <h2>The defect that made it necessary</h2>
 *
 * <p><strong>Found by injection at sign-off, twice.</strong> The main orchestrator renamed {@code
 * UiIds.sectionPane(RESULTS)} to {@code "section-results-pane"} in production code and the entire
 * build stayed green -- {@code SectionNavigationUiTest} 4/0, {@code KeyboardOnlyNavigationUiTest}
 * passed, {@code UiIdsTest} 5/0, all eleven build stages OK. The phase orchestrator reproduced it
 * independently on {@code PERCOLATOR} with the same result. Two causes:
 *
 * <ol>
 *   <li>the GUI tests in {@code cometgui-app} compute the identifier they look up by calling {@link
 *       UiIds} -- which is correct for them, because they are proving that the application uses the
 *       same identifiers it publishes -- so a rename moves the expectation and the actual value
 *       together and no assertion can see it;
 *   <li>{@link UiIdsTest} pinned literals only as a <em>sample</em>: two sections out of ten, one
 *       stage out of eight, one stage filter, one severity. Everything else could be renamed with
 *       nothing noticing.
 * </ol>
 *
 * <p>A self-consistent test proves the identifier exists and that navigation works. It proves
 * nothing about stability, which is exactly what phase 07's parameter-editor tests and phase 14's
 * GUI suite will rest on. A test that cannot fail on the defect it exists to catch is not a test
 * ({@code CONTRIBUTING.rst}, <em>Gate conventions</em>).
 *
 * <h2>The rule for anyone changing an identifier</h2>
 *
 * <p><strong>If a change here makes this class fail, that is the class working.</strong> Changing
 * an identifier means changing the literal below <em>deliberately</em>, in the same commit, having
 * checked every test and every view that looks the control up. That deliberate second edit is the
 * whole point: it is what makes the identifiers a contract rather than an implementation detail.
 *
 * <h2>The rules this class obeys, so that it keeps working</h2>
 *
 * <ul>
 *   <li><strong>Nothing on the expected side is computed.</strong> No pinned value comes from
 *       {@link UiIds}, from concatenating something that came from {@link UiIds}, or from {@link
 *       SectionId#id()}, {@link WorkflowStage#id()} or {@link MessageSeverity#name()}. {@code
 *       "section-results-heading"} is written out in full; {@code SECTION_PANE.get(RESULTS) +
 *       "-heading"} would rebuild the self-referential test this class replaces. The model's own
 *       methods are used only to <em>enumerate</em> what has to be pinned and to build the actual
 *       value, never to produce an expectation.
 *   <li><strong>Adding fails too, not only renaming.</strong> A pinning table a new constant can
 *       quietly bypass rebuilds the same hole one enum constant later, so the tables' key sets are
 *       asserted to cover {@link SectionId#values()}, {@link WorkflowStage#values()} and {@link
 *       MessageSeverity#values()} in full, and {@link UiIds}'s {@code public static final String}
 *       constants are enumerated reflectively and required to appear here.
 * </ul>
 *
 * <p><strong>How the uniqueness check here differs from {@link UiIdsTest}'s.</strong> {@code
 * UiIdsTest.noTwoIdentifiersCollide} is over the values {@link UiIds} <em>generates</em>; this one
 * is over the literals pinned <em>here</em>. Together they are stronger than either alone: every
 * pinned literal is proved equal to the generated value, so a duplicate among the literals is a
 * duplicate among the generated identifiers -- and it is caught in the one place that holds the
 * complete list. Neither test replaces the other, and neither may be deleted.
 */
class StableIdentifierPinTest {

    /** Where a contributor is sent when an identifier moves. */
    private static final String THIS_FILE =
            "cometgui-ui/src/test/java/org/cometgui/ui/controls/StableIdentifierPinTest.java";

    /**
     * How many identifiers are pinned: 22 constants, 50 section identifiers, 24 stepper stage
     * identifiers, 8 console stage filters, 7 stepper arrows, 4 branch identifiers and 4 severity
     * filters. Stated so that deleting a whole category of pins is a failure rather than a smaller
     * test.
     */
    private static final int PINNED_IDENTIFIER_COUNT = 119;

    // -----------------------------------------------------------------------------------------
    // The pinned table. Every string below is typed out. Nothing here is derived from anything.
    // -----------------------------------------------------------------------------------------

    /** Each {@code public static final String} of {@link UiIds}, by field name. */
    private static final Map<String, String> CONSTANTS =
            Map.ofEntries(
                    Map.entry("SHELL_ROOT", "shell-root"),
                    Map.entry("SHELL_HEADER", "shell-header"),
                    Map.entry("SHELL_TITLE", "shell-title"),
                    Map.entry("SHELL_SECTION_TITLE", "shell-section-title"),
                    Map.entry("HOST_BASELINE_BANNER", "host-baseline-banner"),
                    Map.entry("NAVIGATION", "navigation"),
                    Map.entry("NAVIGATION_SEPARATOR", "navigation-separator"),
                    Map.entry("CONTENT", "content"),
                    Map.entry("STAGE_STEPPER", "stage-stepper"),
                    Map.entry("STAGE_STEPPER_CORE", "stage-stepper-core"),
                    Map.entry("STAGE_STEPPER_BRANCHES", "stage-stepper-branches"),
                    Map.entry("STAGE_STEPPER_RUN_STATE", "stage-stepper-run-state"),
                    Map.entry("CONSOLE_PANE", "console-pane"),
                    Map.entry("CONSOLE_TITLE", "console-title"),
                    Map.entry("CONSOLE_OUTPUT", "console-output"),
                    Map.entry("CONSOLE_SUMMARY", "console-summary"),
                    Map.entry("CONSOLE_FILTERS", "console-filters"),
                    Map.entry("CONSOLE_STAGE_FILTER", "console-stage-filter"),
                    Map.entry("CONSOLE_STAGE_FILTER_ALL", "console-stage-filter-all"),
                    Map.entry("CONSOLE_SEVERITY_FILTER", "console-severity-filter"),
                    Map.entry("CONSOLE_CLEAR", "console-clear"),
                    Map.entry("CONSOLE_COPY", "console-copy"));

    /** Each section's pane, as {@code UiIds.sectionPane} must spell it. */
    private static final Map<SectionId, String> SECTION_PANE =
            Map.ofEntries(
                    Map.entry(SectionId.RUN, "section-run"),
                    Map.entry(SectionId.COMET_PARAMETERS, "section-comet-parameters"),
                    Map.entry(SectionId.PERCOLATOR, "section-percolator"),
                    Map.entry(SectionId.RESULTS, "section-results"),
                    Map.entry(SectionId.VISUALISATION, "section-visualisation"),
                    Map.entry(SectionId.LIMELIGHT, "section-limelight"),
                    Map.entry(SectionId.PROVENANCE, "section-provenance"),
                    Map.entry(SectionId.CONSOLE, "section-console"),
                    Map.entry(SectionId.TOOL_MANAGER, "section-tool-manager"),
                    Map.entry(SectionId.SETTINGS, "section-settings"));

    /** Each section pane's heading. */
    private static final Map<SectionId, String> SECTION_HEADING =
            Map.ofEntries(
                    Map.entry(SectionId.RUN, "section-run-heading"),
                    Map.entry(SectionId.COMET_PARAMETERS, "section-comet-parameters-heading"),
                    Map.entry(SectionId.PERCOLATOR, "section-percolator-heading"),
                    Map.entry(SectionId.RESULTS, "section-results-heading"),
                    Map.entry(SectionId.VISUALISATION, "section-visualisation-heading"),
                    Map.entry(SectionId.LIMELIGHT, "section-limelight-heading"),
                    Map.entry(SectionId.PROVENANCE, "section-provenance-heading"),
                    Map.entry(SectionId.CONSOLE, "section-console-heading"),
                    Map.entry(SectionId.TOOL_MANAGER, "section-tool-manager-heading"),
                    Map.entry(SectionId.SETTINGS, "section-settings-heading"));

    /** Each section pane's description. */
    private static final Map<SectionId, String> SECTION_DESCRIPTION =
            Map.ofEntries(
                    Map.entry(SectionId.RUN, "section-run-description"),
                    Map.entry(SectionId.COMET_PARAMETERS, "section-comet-parameters-description"),
                    Map.entry(SectionId.PERCOLATOR, "section-percolator-description"),
                    Map.entry(SectionId.RESULTS, "section-results-description"),
                    Map.entry(SectionId.VISUALISATION, "section-visualisation-description"),
                    Map.entry(SectionId.LIMELIGHT, "section-limelight-description"),
                    Map.entry(SectionId.PROVENANCE, "section-provenance-description"),
                    Map.entry(SectionId.CONSOLE, "section-console-description"),
                    Map.entry(SectionId.TOOL_MANAGER, "section-tool-manager-description"),
                    Map.entry(SectionId.SETTINGS, "section-settings-description"));

    /** Each section pane's "this arrives in phase NN" note. */
    private static final Map<SectionId, String> SECTION_NOTE =
            Map.ofEntries(
                    Map.entry(SectionId.RUN, "section-run-note"),
                    Map.entry(SectionId.COMET_PARAMETERS, "section-comet-parameters-note"),
                    Map.entry(SectionId.PERCOLATOR, "section-percolator-note"),
                    Map.entry(SectionId.RESULTS, "section-results-note"),
                    Map.entry(SectionId.VISUALISATION, "section-visualisation-note"),
                    Map.entry(SectionId.LIMELIGHT, "section-limelight-note"),
                    Map.entry(SectionId.PROVENANCE, "section-provenance-note"),
                    Map.entry(SectionId.CONSOLE, "section-console-note"),
                    Map.entry(SectionId.TOOL_MANAGER, "section-tool-manager-note"),
                    Map.entry(SectionId.SETTINGS, "section-settings-note"));

    /** Each section's navigation entry. */
    private static final Map<SectionId, String> SECTION_NAVIGATION_ENTRY =
            Map.ofEntries(
                    Map.entry(SectionId.RUN, "nav-run"),
                    Map.entry(SectionId.COMET_PARAMETERS, "nav-comet-parameters"),
                    Map.entry(SectionId.PERCOLATOR, "nav-percolator"),
                    Map.entry(SectionId.RESULTS, "nav-results"),
                    Map.entry(SectionId.VISUALISATION, "nav-visualisation"),
                    Map.entry(SectionId.LIMELIGHT, "nav-limelight"),
                    Map.entry(SectionId.PROVENANCE, "nav-provenance"),
                    Map.entry(SectionId.CONSOLE, "nav-console"),
                    Map.entry(SectionId.TOOL_MANAGER, "nav-tool-manager"),
                    Map.entry(SectionId.SETTINGS, "nav-settings"));

    /** Each stage's box in the stage stepper. */
    private static final Map<WorkflowStage, String> STAGE_BOX =
            Map.ofEntries(
                    Map.entry(WorkflowStage.INPUTS, "stage-inputs"),
                    Map.entry(WorkflowStage.VALIDATE, "stage-validate"),
                    Map.entry(WorkflowStage.COMET, "stage-comet"),
                    Map.entry(WorkflowStage.PERCOLATOR, "stage-percolator"),
                    Map.entry(WorkflowStage.RESULTS, "stage-results"),
                    Map.entry(WorkflowStage.PDV, "stage-pdv"),
                    Map.entry(WorkflowStage.LIMELIGHT_XML, "stage-limelight-xml"),
                    Map.entry(WorkflowStage.LIMELIGHT_UPLOAD, "stage-limelight-upload"));

    /** The label naming each stage. */
    private static final Map<WorkflowStage, String> STAGE_NAME =
            Map.ofEntries(
                    Map.entry(WorkflowStage.INPUTS, "stage-inputs-name"),
                    Map.entry(WorkflowStage.VALIDATE, "stage-validate-name"),
                    Map.entry(WorkflowStage.COMET, "stage-comet-name"),
                    Map.entry(WorkflowStage.PERCOLATOR, "stage-percolator-name"),
                    Map.entry(WorkflowStage.RESULTS, "stage-results-name"),
                    Map.entry(WorkflowStage.PDV, "stage-pdv-name"),
                    Map.entry(WorkflowStage.LIMELIGHT_XML, "stage-limelight-xml-name"),
                    Map.entry(WorkflowStage.LIMELIGHT_UPLOAD, "stage-limelight-upload-name"));

    /** The label stating each stage's state in words. */
    private static final Map<WorkflowStage, String> STAGE_STATE =
            Map.ofEntries(
                    Map.entry(WorkflowStage.INPUTS, "stage-inputs-state"),
                    Map.entry(WorkflowStage.VALIDATE, "stage-validate-state"),
                    Map.entry(WorkflowStage.COMET, "stage-comet-state"),
                    Map.entry(WorkflowStage.PERCOLATOR, "stage-percolator-state"),
                    Map.entry(WorkflowStage.RESULTS, "stage-results-state"),
                    Map.entry(WorkflowStage.PDV, "stage-pdv-state"),
                    Map.entry(WorkflowStage.LIMELIGHT_XML, "stage-limelight-xml-state"),
                    Map.entry(WorkflowStage.LIMELIGHT_UPLOAD, "stage-limelight-upload-state"));

    /** The console's stage-filter button for each stage. The "every stage" one is a constant. */
    private static final Map<WorkflowStage, String> CONSOLE_STAGE_FILTER =
            Map.ofEntries(
                    Map.entry(WorkflowStage.INPUTS, "console-stage-filter-inputs"),
                    Map.entry(WorkflowStage.VALIDATE, "console-stage-filter-validate"),
                    Map.entry(WorkflowStage.COMET, "console-stage-filter-comet"),
                    Map.entry(WorkflowStage.PERCOLATOR, "console-stage-filter-percolator"),
                    Map.entry(WorkflowStage.RESULTS, "console-stage-filter-results"),
                    Map.entry(WorkflowStage.PDV, "console-stage-filter-pdv"),
                    Map.entry(WorkflowStage.LIMELIGHT_XML, "console-stage-filter-limelight-xml"),
                    Map.entry(
                            WorkflowStage.LIMELIGHT_UPLOAD,
                            "console-stage-filter-limelight-upload"));

    /**
     * The arrows the stepper draws <em>into</em> each stage, one per predecessor, in the order the
     * predecessors are declared. {@code INPUTS} starts the diagram and has none, which is pinned as
     * an empty list rather than by leaving the constant out -- so that a new stage still has to
     * appear here even if nothing points at it.
     */
    private static final Map<WorkflowStage, List<String>> ARROWS_INTO_STAGE =
            Map.ofEntries(
                    Map.entry(WorkflowStage.INPUTS, List.of()),
                    Map.entry(WorkflowStage.VALIDATE, List.of("stage-arrow-inputs-validate")),
                    Map.entry(WorkflowStage.COMET, List.of("stage-arrow-validate-comet")),
                    Map.entry(WorkflowStage.PERCOLATOR, List.of("stage-arrow-comet-percolator")),
                    Map.entry(WorkflowStage.RESULTS, List.of("stage-arrow-percolator-results")),
                    Map.entry(WorkflowStage.PDV, List.of("stage-arrow-results-pdv")),
                    Map.entry(
                            WorkflowStage.LIMELIGHT_XML,
                            List.of("stage-arrow-results-limelight-xml")),
                    Map.entry(
                            WorkflowStage.LIMELIGHT_UPLOAD,
                            List.of("stage-arrow-limelight-xml-limelight-upload")));

    /**
     * For each stage that starts an optional downstream branch, the branch row and the row's
     * lead-in label, in that order. The six stages that start no branch are pinned as empty lists,
     * and {@link #stepperBranchIdentifiersAreExactlyTheseLiterals()} asserts that the two non-empty
     * entries are exactly the branches the stepper draws.
     */
    private static final Map<WorkflowStage, List<String>> STAGE_BRANCH =
            Map.ofEntries(
                    Map.entry(WorkflowStage.INPUTS, List.of()),
                    Map.entry(WorkflowStage.VALIDATE, List.of()),
                    Map.entry(WorkflowStage.COMET, List.of()),
                    Map.entry(WorkflowStage.PERCOLATOR, List.of()),
                    Map.entry(WorkflowStage.RESULTS, List.of()),
                    Map.entry(
                            WorkflowStage.PDV,
                            List.of("stage-branch-pdv", "stage-branch-pdv-from")),
                    Map.entry(
                            WorkflowStage.LIMELIGHT_XML,
                            List.of(
                                    "stage-branch-limelight-xml",
                                    "stage-branch-limelight-xml-from")),
                    Map.entry(WorkflowStage.LIMELIGHT_UPLOAD, List.of()));

    /** The console's minimum-severity button for each severity. */
    private static final Map<MessageSeverity, String> SEVERITY_FILTER =
            Map.ofEntries(
                    Map.entry(MessageSeverity.INFO, "console-severity-filter-info"),
                    Map.entry(MessageSeverity.STDERR, "console-severity-filter-stderr"),
                    Map.entry(MessageSeverity.WARNING, "console-severity-filter-warning"),
                    Map.entry(MessageSeverity.ERROR, "console-severity-filter-error"));

    // -----------------------------------------------------------------------------------------
    // The identifiers themselves: each pinned literal against what UiIds produces today.
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("every UiIds constant still has its pinned spelling")
    void constantIdentifiersAreExactlyTheseLiterals() {
        for (Field field : stableStringConstantsOfUiIds()) {
            String pinned = CONSTANTS.get(field.getName());
            assertTrue(
                    pinned != null,
                    () ->
                            "UiIds."
                                    + field.getName()
                                    + " is not pinned. "
                                    + adviceFor("UiIds." + field.getName()));
            assertPinned(pinned, valueOf(field), "UiIds." + field.getName());
        }
    }

    @Test
    @DisplayName("every section's five identifiers still have their pinned spelling")
    void sectionIdentifiersAreExactlyTheseLiterals() {
        for (SectionId section : SectionId.values()) {
            String where = "section " + section.name();
            assertPinned(
                    pinned(SECTION_PANE, section, where + " pane"),
                    UiIds.sectionPane(section),
                    where + " pane (UiIds.sectionPane)");
            assertPinned(
                    pinned(SECTION_HEADING, section, where + " heading"),
                    UiIds.sectionHeading(section),
                    where + " heading (UiIds.sectionHeading)");
            assertPinned(
                    pinned(SECTION_DESCRIPTION, section, where + " description"),
                    UiIds.sectionDescription(section),
                    where + " description (UiIds.sectionDescription)");
            assertPinned(
                    pinned(SECTION_NOTE, section, where + " note"),
                    UiIds.sectionNote(section),
                    where + " note (UiIds.sectionNote)");
            assertPinned(
                    pinned(SECTION_NAVIGATION_ENTRY, section, where + " navigation entry"),
                    UiIds.navigationEntry(section),
                    where + " navigation entry (UiIds.navigationEntry)");
        }
    }

    @Test
    @DisplayName("every stepper stage's identifiers still have their pinned spelling")
    void stageIdentifiersAreExactlyTheseLiterals() {
        for (WorkflowStage stage : WorkflowStage.values()) {
            String where = "stage " + stage.name();
            assertPinned(
                    pinned(STAGE_BOX, stage, where + " box"),
                    UiIds.stepperStage(stage),
                    where + " box (UiIds.stepperStage)");
            assertPinned(
                    pinned(STAGE_NAME, stage, where + " name label"),
                    UiIds.stepperStageName(stage),
                    where + " name label (UiIds.stepperStageName)");
            assertPinned(
                    pinned(STAGE_STATE, stage, where + " state label"),
                    UiIds.stepperStageState(stage),
                    where + " state label (UiIds.stepperStageState)");
        }
    }

    @Test
    @DisplayName("every stepper arrow the diagram draws still has its pinned spelling")
    void stepperArrowIdentifiersAreExactlyTheseLiterals() {
        for (WorkflowStage stage : WorkflowStage.values()) {
            // The stepper draws one arrow into a stage from each predecessor
            // (StageStepper.appendChain), so enumerating the predecessors enumerates the arrows.
            // The predecessors decide WHICH arrows exist; the literals decide what they are called.
            List<String> actual = new ArrayList<>();
            for (WorkflowStage predecessor : stage.predecessors()) {
                actual.add(UiIds.stepperArrow(predecessor, stage));
            }
            assertPinned(
                    pinned(ARROWS_INTO_STAGE, stage, "arrows into stage " + stage.name()),
                    actual,
                    "the arrows into stage " + stage.name() + " (UiIds.stepperArrow)");
        }
    }

    @Test
    @DisplayName("every branch row and lead-in label still has its pinned spelling")
    void stepperBranchIdentifiersAreExactlyTheseLiterals() {
        Set<WorkflowStage> drawn = new LinkedHashSet<>();
        for (List<WorkflowStage> branch : WorkflowStage.downstreamBranches()) {
            WorkflowStage first = branch.get(0);
            drawn.add(first);
            assertPinned(
                    pinned(STAGE_BRANCH, first, "branch starting at " + first.name()),
                    List.of(UiIds.stepperBranch(first), UiIds.stepperBranchOrigin(first)),
                    "the branch starting at stage "
                            + first.name()
                            + " (UiIds.stepperBranch, UiIds.stepperBranchOrigin)");
        }
        Set<WorkflowStage> pinnedStarts = new LinkedHashSet<>();
        for (Map.Entry<WorkflowStage, List<String>> entry : STAGE_BRANCH.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                pinnedStarts.add(entry.getKey());
            }
        }
        assertEquals(
                names(drawn),
                names(pinnedStarts),
                "the stepper draws a branch this table does not pin, or pins one it does not draw. "
                        + adviceFor("the branch rows"));
    }

    @Test
    @DisplayName("every console filter identifier still has its pinned spelling")
    void consoleFilterIdentifiersAreExactlyTheseLiterals() {
        for (WorkflowStage stage : WorkflowStage.values()) {
            assertPinned(
                    pinned(CONSOLE_STAGE_FILTER, stage, "console stage filter " + stage.name()),
                    UiIds.consoleStageFilter(stage),
                    "the console's stage filter for " + stage.name() + " (consoleStageFilter)");
        }
        for (MessageSeverity severity : MessageSeverity.values()) {
            assertPinned(
                    pinned(SEVERITY_FILTER, severity, "console severity filter " + severity.name()),
                    UiIds.consoleSeverityFilter(severity),
                    "the console's severity filter for "
                            + severity.name()
                            + " (consoleSeverityFilter)");
        }
        // The "every stage" button is a constant rather than a per-stage identifier, and is pinned
        // with the other constants; asserted here as well because it belongs to this filter bar.
        assertPinned(
                CONSTANTS.get("CONSOLE_STAGE_FILTER_ALL"),
                UiIds.CONSOLE_STAGE_FILTER_ALL,
                "UiIds.CONSOLE_STAGE_FILTER_ALL");
    }

    // -----------------------------------------------------------------------------------------
    // Exhaustiveness: adding an identifier must fail here too, not only renaming one.
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("a new UiIds constant fails until it is pinned")
    void everyUiIdsConstantIsPinned() {
        Set<String> declared = new TreeSet<>();
        for (Field field : stableStringConstantsOfUiIds()) {
            declared.add(field.getName());
        }
        Set<String> unpinned = new TreeSet<>(declared);
        unpinned.removeAll(CONSTANTS.keySet());
        assertTrue(
                unpinned.isEmpty(),
                () ->
                        "UiIds declares constants this table does not pin: "
                                + String.join(", ", unpinned)
                                + ". "
                                + adviceFor("each new constant"));
        Set<String> stale = new TreeSet<>(CONSTANTS.keySet());
        stale.removeAll(declared);
        assertTrue(
                stale.isEmpty(),
                () ->
                        "this table pins constants UiIds no longer declares: "
                                + String.join(", ", stale)
                                + ". A pin for a control that no longer exists hides how"
                                + " much of the surface is really covered.");
    }

    @Test
    @DisplayName("a new section, stage or severity fails until it is pinned")
    void everyEnumConstantIsPinned() {
        // An enum-keyed map cannot hold a key for a constant that no longer exists -- that would
        // not compile -- so only the missing direction has to be asserted.
        assertEveryConstantIsPinned("SECTION_PANE", SECTION_PANE.keySet(), SectionId.values());
        assertEveryConstantIsPinned(
                "SECTION_HEADING", SECTION_HEADING.keySet(), SectionId.values());
        assertEveryConstantIsPinned(
                "SECTION_DESCRIPTION", SECTION_DESCRIPTION.keySet(), SectionId.values());
        assertEveryConstantIsPinned("SECTION_NOTE", SECTION_NOTE.keySet(), SectionId.values());
        assertEveryConstantIsPinned(
                "SECTION_NAVIGATION_ENTRY", SECTION_NAVIGATION_ENTRY.keySet(), SectionId.values());
        assertEveryConstantIsPinned("STAGE_BOX", STAGE_BOX.keySet(), WorkflowStage.values());
        assertEveryConstantIsPinned("STAGE_NAME", STAGE_NAME.keySet(), WorkflowStage.values());
        assertEveryConstantIsPinned("STAGE_STATE", STAGE_STATE.keySet(), WorkflowStage.values());
        assertEveryConstantIsPinned(
                "CONSOLE_STAGE_FILTER", CONSOLE_STAGE_FILTER.keySet(), WorkflowStage.values());
        assertEveryConstantIsPinned(
                "ARROWS_INTO_STAGE", ARROWS_INTO_STAGE.keySet(), WorkflowStage.values());
        assertEveryConstantIsPinned("STAGE_BRANCH", STAGE_BRANCH.keySet(), WorkflowStage.values());
        assertEveryConstantIsPinned(
                "SEVERITY_FILTER", SEVERITY_FILTER.keySet(), MessageSeverity.values());
    }

    // -----------------------------------------------------------------------------------------
    // Uniqueness, over the pinned literals.
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("no two pinned identifiers are the same string")
    void noTwoPinnedIdentifiersCollide() {
        Map<String, String> firstOwnerOf = new LinkedHashMap<>();
        List<Pin> pins = everyPin();
        for (Pin pin : pins) {
            String previousOwner = firstOwnerOf.putIfAbsent(pin.id(), pin.owner());
            assertNull(
                    previousOwner,
                    () ->
                            "two controls are pinned to the identifier \""
                                    + pin.id()
                                    + "\": "
                                    + previousOwner
                                    + " and "
                                    + pin.owner()
                                    + ". Scene.lookup returns whichever node it reaches first, so"
                                    + " one of the two tests would silently assert against the"
                                    + " wrong control.");
        }
        assertEquals(
                PINNED_IDENTIFIER_COUNT,
                pins.size(),
                "the number of pinned identifiers changed. If a control was genuinely added or"
                        + " removed, update PINNED_IDENTIFIER_COUNT in "
                        + THIS_FILE
                        + " deliberately; this count is what stops a whole category of pins being"
                        + " quietly dropped.");
    }

    // -----------------------------------------------------------------------------------------
    // Helpers. None of these produces an expected value: they only look pins up and report.
    // -----------------------------------------------------------------------------------------

    /** One pinned identifier and the control it belongs to, for the uniqueness check. */
    private record Pin(String owner, String id) {}

    /** Every pinned identifier, with an owner label, in one list. */
    private static List<Pin> everyPin() {
        List<Pin> pins = new ArrayList<>();
        for (Map.Entry<String, String> constant : CONSTANTS.entrySet()) {
            pins.add(new Pin("UiIds." + constant.getKey(), constant.getValue()));
        }
        addPins(pins, "pane of section ", SECTION_PANE);
        addPins(pins, "heading of section ", SECTION_HEADING);
        addPins(pins, "description of section ", SECTION_DESCRIPTION);
        addPins(pins, "note of section ", SECTION_NOTE);
        addPins(pins, "navigation entry of section ", SECTION_NAVIGATION_ENTRY);
        addPins(pins, "box of stage ", STAGE_BOX);
        addPins(pins, "name label of stage ", STAGE_NAME);
        addPins(pins, "state label of stage ", STAGE_STATE);
        addPins(pins, "console stage filter for ", CONSOLE_STAGE_FILTER);
        addPins(pins, "console severity filter for ", SEVERITY_FILTER);
        addListPins(pins, "arrow into stage ", ARROWS_INTO_STAGE);
        addListPins(pins, "branch row of stage ", STAGE_BRANCH);
        return List.copyOf(pins);
    }

    private static <E extends Enum<E>> void addPins(
            List<Pin> pins, String role, Map<E, String> table) {
        for (Map.Entry<E, String> entry : table.entrySet()) {
            pins.add(new Pin(role + entry.getKey().name(), entry.getValue()));
        }
    }

    private static <E extends Enum<E>> void addListPins(
            List<Pin> pins, String role, Map<E, List<String>> table) {
        for (Map.Entry<E, List<String>> entry : table.entrySet()) {
            for (String id : entry.getValue()) {
                pins.add(new Pin(role + entry.getKey().name(), id));
            }
        }
    }

    /**
     * The pinned value for one key, or a failure naming what is missing -- so that a table with a
     * hole reports the hole rather than a {@link NullPointerException}.
     */
    private static <K, V> V pinned(Map<K, V> table, K key, String what) {
        V value = table.get(key);
        assertTrue(
                value != null,
                () -> "no identifier is pinned for the " + what + ". " + adviceFor(what));
        return value;
    }

    private static void assertPinned(Object pinnedValue, Object actual, String owner) {
        assertEquals(
                pinnedValue,
                actual,
                () ->
                        "the stable identifier of the "
                                + owner
                                + " is no longer the pinned "
                                + pinnedValue
                                + ". "
                                + adviceFor(owner));
    }

    private static String adviceFor(String what) {
        return "R-TEST-04 requires the identifier of "
                + what
                + " to be STABLE: the GUI tests, and phases 07 and 14 after them, look controls up"
                + " by it. If the change is deliberate, edit the literal pinned in "
                + THIS_FILE
                + " in the same commit, and check every view and test that uses it.";
    }

    private static void assertEveryConstantIsPinned(
            String table, Set<? extends Enum<?>> pinnedKeys, Enum<?>[] constants) {
        List<String> missing = new ArrayList<>();
        for (Enum<?> constant : constants) {
            if (!pinnedKeys.contains(constant)) {
                missing.add(constant.name());
            }
        }
        assertTrue(
                missing.isEmpty(),
                () ->
                        table
                                + " pins no identifier for: "
                                + String.join(", ", missing)
                                + ". A pinning table a new constant can bypass rebuilds the hole"
                                + " this class exists to close, so pin the literal in "
                                + THIS_FILE
                                + " before adding the constant.");
    }

    /**
     * {@link UiIds}'s {@code public static final String} fields, found reflectively so that a
     * constant added without a pin is a failure rather than an omission nobody notices.
     */
    private static List<Field> stableStringConstantsOfUiIds() {
        List<Field> constants = new ArrayList<>();
        for (Field field : UiIds.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (!field.isSynthetic()
                    && field.getType() == String.class
                    && Modifier.isPublic(modifiers)
                    && Modifier.isStatic(modifiers)
                    && Modifier.isFinal(modifiers)) {
                constants.add(field);
            }
        }
        assertTrue(
                constants.size() >= CONSTANTS.size(),
                "reflection found "
                        + constants.size()
                        + " public static final String fields on UiIds but "
                        + CONSTANTS.size()
                        + " are pinned: the reflective enumeration itself has stopped working, and"
                        + " an enumeration that finds nothing would pass every check below.");
        return constants;
    }

    private static String valueOf(Field field) {
        try {
            return (String) field.get(null);
        } catch (IllegalAccessException cannotRead) {
            throw new AssertionError("cannot read UiIds." + field.getName(), cannotRead);
        }
    }

    /** Enum constants as their names, so a set comparison reports something readable. */
    private static Set<String> names(Set<? extends Enum<?>> constants) {
        Set<String> asNames = new TreeSet<>();
        for (Enum<?> constant : constants) {
            asNames.add(constant.name());
        }
        return asNames;
    }
}
