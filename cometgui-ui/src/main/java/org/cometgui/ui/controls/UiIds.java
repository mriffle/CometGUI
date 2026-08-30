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

import java.util.Locale;
import java.util.Objects;
import org.cometgui.domain.log.MessageSeverity;
import org.cometgui.domain.run.StageTag;
import org.cometgui.ui.viewmodel.SectionId;

/**
 * Every stable identifier the user interface sets with {@code setId(...)}, in one place.
 *
 * <p><strong>{@code R-TEST-04}.</strong> "Controls required by automated tests shall have stable
 * semantic identifiers ... Tests shall not locate important controls by pixel coordinates or
 * brittle CSS ancestry." A test therefore finds a control with {@code scene.lookup("#" +
 * UiIds.CONSOLE_OUTPUT)} and never by walking children or measuring anything.
 *
 * <p><strong>Why one class rather than a string literal at each call site.</strong> The identifier
 * is a contract between a view that sets it and a test that looks it up, and those two live in
 * different source trees written by different agents. Ten views each spelling {@code
 * "section-comet-parameters"} from memory is a contract that drifts silently: the view compiles,
 * the test compiles, and the lookup returns {@code null} at run time. Here it cannot drift, because
 * both sides call the same method.
 *
 * <p><strong>Derived, not repeated.</strong> Everything that has an identity in the model --
 * sections, workflow stages, severities -- builds its identifier from that model's own stable
 * identifier ({@link SectionId#id()}, {@link StageTag#id()}, {@link MessageSeverity}'s constant
 * name) rather than from a second copy of the string. Renaming a section is then a change in one
 * enum, and every view and every test follows it.
 *
 * <p><strong>Why this class lives in {@code controls} and not in {@code view}.</strong> Both
 * packages need it: the shell and the section panes set section and navigation identifiers, and the
 * stage stepper and the console pane set their own. Putting it here makes the dependency run one
 * way -- {@code view} composes {@code controls} -- instead of making the two packages point at each
 * other.
 */
public final class UiIds {

    /** The shell's root pane. */
    public static final String SHELL_ROOT = "shell-root";

    /** The shell's header area, above the navigation and the content. */
    public static final String SHELL_HEADER = "shell-header";

    /** The application title in the header. */
    public static final String SHELL_TITLE = "shell-title";

    /** The header's echo of the selected section's title. */
    public static final String SHELL_SECTION_TITLE = "shell-section-title";

    /** The host-baseline banner slot, present in the scene whether or not it is visible. */
    public static final String HOST_BASELINE_BANNER = "host-baseline-banner";

    /** The left navigation container. */
    public static final String NAVIGATION = "navigation";

    /** The rule between the primary navigation entries and the secondary ones. */
    public static final String NAVIGATION_SEPARATOR = "navigation-separator";

    /** The content area, which holds exactly the selected section's pane. */
    public static final String CONTENT = "content";

    /** The stage stepper's root. */
    public static final String STAGE_STEPPER = "stage-stepper";

    /** The stage stepper's core row: Inputs, Validate, Comet, Percolator, Results. */
    public static final String STAGE_STEPPER_CORE = "stage-stepper-core";

    /** The stage stepper's optional downstream branches, one row each. */
    public static final String STAGE_STEPPER_BRANCHES = "stage-stepper-branches";

    /** The stage stepper's statement of the derived run state, in words. */
    public static final String STAGE_STEPPER_RUN_STATE = "stage-stepper-run-state";

    /** The console pane's root. */
    public static final String CONSOLE_PANE = "console-pane";

    /** The console pane's heading. */
    public static final String CONSOLE_TITLE = "console-title";

    /** The console's text view. */
    public static final String CONSOLE_OUTPUT = "console-output";

    /** The console's summary line: what was discarded, and how much is being shown. */
    public static final String CONSOLE_SUMMARY = "console-summary";

    /** The console's filter bar. */
    public static final String CONSOLE_FILTERS = "console-filters";

    /** The row of stage-filter buttons. */
    public static final String CONSOLE_STAGE_FILTER = "console-stage-filter";

    /** The stage filter's "every stage" button. */
    public static final String CONSOLE_STAGE_FILTER_ALL = "console-stage-filter-all";

    /** The row of minimum-severity buttons. */
    public static final String CONSOLE_SEVERITY_FILTER = "console-severity-filter";

    /** The console's "clear" action. */
    public static final String CONSOLE_CLEAR = "console-clear";

    /** The console's "copy" action. */
    public static final String CONSOLE_COPY = "console-copy";

    private UiIds() {}

    /**
     * The identifier of one section's pane.
     *
     * @param section the section
     * @return {@code "section-"} followed by {@link SectionId#id()}
     * @throws NullPointerException if {@code section} is {@code null}
     */
    public static String sectionPane(SectionId section) {
        return "section-" + Objects.requireNonNull(section, "section").id();
    }

    /**
     * The identifier of one section pane's heading.
     *
     * @param section the section
     * @return the pane identifier with {@code "-heading"} appended
     * @throws NullPointerException if {@code section} is {@code null}
     */
    public static String sectionHeading(SectionId section) {
        return sectionPane(section) + "-heading";
    }

    /**
     * The identifier of one section pane's description.
     *
     * @param section the section
     * @return the pane identifier with {@code "-description"} appended
     * @throws NullPointerException if {@code section} is {@code null}
     */
    public static String sectionDescription(SectionId section) {
        return sectionPane(section) + "-description";
    }

    /**
     * The identifier of one section pane's "this arrives in phase NN" note.
     *
     * @param section the section
     * @return the pane identifier with {@code "-note"} appended
     * @throws NullPointerException if {@code section} is {@code null}
     */
    public static String sectionNote(SectionId section) {
        return sectionPane(section) + "-note";
    }

    /**
     * The identifier of one section's navigation entry.
     *
     * @param section the section
     * @return {@code "nav-"} followed by {@link SectionId#id()}
     * @throws NullPointerException if {@code section} is {@code null}
     */
    public static String navigationEntry(SectionId section) {
        return "nav-" + Objects.requireNonNull(section, "section").id();
    }

    /**
     * The identifier of one stage's box in the stage stepper.
     *
     * @param stage the workflow stage
     * @return {@code "stage-"} followed by {@link StageTag#id()}
     * @throws NullPointerException if {@code stage} is {@code null}
     */
    public static String stepperStage(StageTag stage) {
        return "stage-" + Objects.requireNonNull(stage, "stage").id();
    }

    /**
     * The identifier of the label naming one stage in the stage stepper.
     *
     * @param stage the workflow stage
     * @return the stage identifier with {@code "-name"} appended
     * @throws NullPointerException if {@code stage} is {@code null}
     */
    public static String stepperStageName(StageTag stage) {
        return stepperStage(stage) + "-name";
    }

    /**
     * The identifier of the label stating one stage's state in words.
     *
     * @param stage the workflow stage
     * @return the stage identifier with {@code "-state"} appended
     * @throws NullPointerException if {@code stage} is {@code null}
     */
    public static String stepperStageState(StageTag stage) {
        return stepperStage(stage) + "-state";
    }

    /**
     * The identifier of the arrow the stepper draws between two stages.
     *
     * @param from the earlier stage
     * @param to the later stage
     * @return {@code "stage-arrow-<from>-<to>"}
     * @throws NullPointerException if either stage is {@code null}
     */
    public static String stepperArrow(StageTag from, StageTag to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        return "stage-arrow-" + from.id() + "-" + to.id();
    }

    /**
     * The identifier of one optional downstream branch row, named for the stage it starts with.
     *
     * @param firstStage the branch's first stage
     * @return {@code "stage-branch-"} followed by that stage's identifier
     * @throws NullPointerException if {@code firstStage} is {@code null}
     */
    public static String stepperBranch(StageTag firstStage) {
        return "stage-branch-" + Objects.requireNonNull(firstStage, "firstStage").id();
    }

    /**
     * The identifier of a branch row's lead-in label, which names the stage the branch hangs off.
     *
     * @param firstStage the branch's first stage
     * @return the branch identifier with {@code "-from"} appended
     * @throws NullPointerException if {@code firstStage} is {@code null}
     */
    public static String stepperBranchOrigin(StageTag firstStage) {
        return stepperBranch(firstStage) + "-from";
    }

    /**
     * The identifier of the console's filter button for one stage.
     *
     * @param stage the stage the button filters to
     * @return {@link #CONSOLE_STAGE_FILTER} with {@code "-"} and the stage identifier appended
     * @throws NullPointerException if {@code stage} is {@code null}
     */
    public static String consoleStageFilter(StageTag stage) {
        return CONSOLE_STAGE_FILTER + "-" + Objects.requireNonNull(stage, "stage").id();
    }

    /**
     * The identifier of the console's minimum-severity button for one severity.
     *
     * @param severity the severity the button selects as the minimum
     * @return {@link #CONSOLE_SEVERITY_FILTER} with {@code "-"} and the lower-cased constant name
     *     appended, for example {@code console-severity-filter-warning}
     * @throws NullPointerException if {@code severity} is {@code null}
     */
    public static String consoleSeverityFilter(MessageSeverity severity) {
        Objects.requireNonNull(severity, "severity");
        return CONSOLE_SEVERITY_FILTER + "-" + severity.name().toLowerCase(Locale.ROOT);
    }
}
