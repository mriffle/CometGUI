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
import org.cometgui.params.comet.schema.ParameterCategory;
import org.cometgui.params.comet.schema.TerminalCode;
import org.cometgui.params.comet.value.VariableModPart;
import org.cometgui.ui.viewmodel.SectionId;
import org.cometgui.ui.viewmodel.params.EssentialsSection;
import org.cometgui.ui.viewmodel.params.SearchFilter;

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
 * <p><strong>Which is exactly why every identifier is also pinned as a literal.</strong> Deriving
 * keeps the views and the tests consistent with each other; it does not make an identifier
 * <em>stable</em>, and {@code R-TEST-04} asks for stability. Because a test that looks a control up
 * through this class follows a rename silently, a rename here was once made at sign-off and left
 * the whole build green. {@code StableIdentifierPinTest} closes that: it writes out every
 * identifier this class hands out as a hand-typed literal and fails naming the one that moved. So
 * <strong>changing any value in this class means changing that literal too, deliberately, in the
 * same commit</strong> -- and checking what looks the control up.
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

    /** The Tool Manager section's content, below the section's own heading and note. */
    public static final String TOOL_MANAGER_PANE = "tool-manager-pane";

    /** The Tool Manager's one-line statement of what the list below it holds. */
    public static final String TOOL_MANAGER_SUMMARY = "tool-manager-summary";

    /** The container holding one row per tool build the port offered. */
    public static final String TOOL_MANAGER_ROWS = "tool-manager-rows";

    /** The Comet parameter editor's root, in the Comet Parameters section. */
    public static final String PARAM_EDITOR = "param-editor";

    /** The Comet release selector. */
    public static final String PARAM_RELEASE = "param-release";

    /** What the last release choice did, in words, or why it was refused. */
    public static final String PARAM_RELEASE_STATUS = "param-release-status";

    /** The switch to the Essentials level. */
    public static final String PARAM_MODE_ESSENTIALS = "param-mode-essentials";

    /** The switch to the Advanced level. */
    public static final String PARAM_MODE_ADVANCED = "param-mode-advanced";

    /** The switch to the Expert level. */
    public static final String PARAM_MODE_EXPERT = "param-mode-expert";

    /** The validation summary at the top of the editor. */
    public static final String PARAM_SUMMARY = "param-summary";

    /** The validation summary's headline: the counts in words. */
    public static final String PARAM_SUMMARY_HEADLINE = "param-summary-headline";

    /** The scrolling body below the summary, holding the three levels. */
    public static final String PARAM_BODY = "param-body";

    /** The Essentials level. */
    public static final String PARAM_ESSENTIALS = "param-essentials";

    /** The Advanced level. */
    public static final String PARAM_ADVANCED = "param-advanced";

    /** The Expert level's region. */
    public static final String PARAM_EXPERT = "param-expert";

    /** The Expert level's canonical raw text of the configuration, read-only. */
    public static final String EXPERT_CANONICAL = "param-expert-canonical";

    /** Why the writer refuses the configuration, or that it writes it. */
    public static final String EXPERT_CANONICAL_STATUS = "param-expert-canonical-status";

    /** The Expert level's editable draft. */
    public static final String EXPERT_DRAFT = "param-expert-draft";

    /** The draft's lines, each with its kind and diagnostics in words beside its colours. */
    public static final String EXPERT_LINES = "param-expert-lines";

    /** How many diagnostics the draft has, in words. */
    public static final String EXPERT_DIAGNOSTICS_HEADLINE = "param-expert-diagnostics-headline";

    /** The action that checks the draft and asks to confirm what applying it changes. */
    public static final String EXPERT_APPLY = "param-expert-apply";

    /** The action that throws the draft away. */
    public static final String EXPERT_REVERT = "param-expert-revert";

    /** The last apply's outcome in words: the errors with their lines, or what awaits. */
    public static final String EXPERT_APPLY_STATUS = "param-expert-apply-status";

    /** The lines the last failed apply's errors name, with their text. */
    public static final String EXPERT_OFFENDING = "param-expert-offending";

    /** The confirmation step: what applying the checked draft would change. */
    public static final String EXPERT_CONFIRMATION = "param-expert-confirmation";

    /** The changes, warnings and enforced values of the checked draft, in words. */
    public static final String EXPERT_CHANGES = "param-expert-changes";

    /** The confirmation that applies the checked draft to the typed configuration. */
    public static final String EXPERT_CONFIRM = "param-expert-confirm";

    /** The cancellation of the confirmation step. */
    public static final String EXPERT_CANCEL = "param-expert-cancel";

    /** The choice of what to compare the configuration with: the defaults or a preset. */
    public static final String EXPERT_COMPARE = "param-expert-compare";

    /** The comparison with the chosen defaults or preset, in words. */
    public static final String EXPERT_COMPARE_ROWS = "param-expert-compare-rows";

    /** The comparison with the last saved configuration, in words. */
    public static final String EXPERT_SAVED_ROWS = "param-expert-saved-rows";

    /** How many unknown parameters the configuration keeps, in words. */
    public static final String EXPERT_UNKNOWN_HEADLINE = "param-expert-unknown-headline";

    /** The action that saves the configuration as a new parameter file. */
    public static final String PARAM_SAVE = "param-save";

    /** The last save's outcome, in words. */
    public static final String PARAM_SAVE_STATUS = "param-save-status";

    /** The action that starts again from the selected release's defaults (asks to confirm). */
    public static final String PARAM_RESET_ALL = "param-reset-all";

    /** The confirmation of a reset of the whole configuration. */
    public static final String PARAM_RESET_ALL_CONFIRM = "param-reset-all-confirm";

    /** The cancellation of a reset of the whole configuration. */
    public static final String PARAM_RESET_ALL_CANCEL = "param-reset-all-cancel";

    /** The action that imports a parameter file through the file chooser. */
    public static final String PARAM_IMPORT = "param-import";

    /** The last import's outcome in words. */
    public static final String PARAM_IMPORT_STATUS = "param-import-status";

    /** The choice an import of a file for another release waits for. */
    public static final String PARAM_IMPORT_OFFER = "param-import-offer";

    /** The waiting import's question in words, naming both releases. */
    public static final String PARAM_IMPORT_QUESTION = "param-import-question";

    /** Migrate the waiting file to the selected release. */
    public static final String PARAM_IMPORT_MIGRATE = "param-import-migrate";

    /** Read the waiting file as its own release, switching the editor to it. */
    public static final String PARAM_IMPORT_OWN = "param-import-own";

    /** Read the waiting file as the selected release, with the mismatch warning. */
    public static final String PARAM_IMPORT_SELECTED = "param-import-selected";

    /** Import nothing. */
    public static final String PARAM_IMPORT_DISMISS = "param-import-dismiss";

    /** The global parameter search's query field. */
    public static final String PARAM_SEARCH = "param-search";

    /** How many parameters the search found, in words. */
    public static final String PARAM_SEARCH_HEADLINE = "param-search-headline";

    /** The search's results. */
    public static final String PARAM_SEARCH_RESULTS = "param-search-results";

    /** The review of the migration of the configuration, while one is under review. */
    public static final String PARAM_MIGRATION = "param-migration";

    /** The migration review's headline: releases, counts, and what needs a decision. */
    public static final String PARAM_MIGRATION_HEADLINE = "param-migration-headline";

    /** The last review action's outcome in words. */
    public static final String PARAM_MIGRATION_STATUS = "param-migration-status";

    /** The Essentials search/acquisition preset choice. */
    public static final String PRESET_CHOICE = "ess-preset-choice";

    /** The action that shows what the chosen preset would change. Nothing changes. */
    public static final String PRESET_PREVIEW = "ess-preset-preview";

    /** The preset preview: the diff rows with their check boxes, and the three actions. */
    public static final String PRESET_REVIEW = "ess-preset-review";

    /** Which release the previewed preset was made for, in words. */
    public static final String PRESET_MADE_FOR = "ess-preset-made-for";

    /** The previewed preset's compatibility problems and conversions, in words. */
    public static final String PRESET_PROBLEMS = "ess-preset-problems";

    /** Apply every row of the preview that can be applied. */
    public static final String PRESET_APPLY_ALL = "ess-preset-apply-all";

    /** Apply exactly the selected rows of the preview. */
    public static final String PRESET_APPLY_SELECTED = "ess-preset-apply-selected";

    /** Drop the preview; nothing changes. */
    public static final String PRESET_CANCEL = "ess-preset-cancel";

    /** The last preset action's outcome in words. */
    public static final String PRESET_STATUS = "ess-preset-status";

    /** The custom-enzyme editor's number field for a new row. */
    public static final String ENZYME_NEW_NUMBER = "adv-enzyme-new-number";

    /** The custom-enzyme editor's name field for a new row. */
    public static final String ENZYME_NEW_NAME = "adv-enzyme-new-name";

    /** The custom-enzyme editor's sense choice for a new row. */
    public static final String ENZYME_NEW_SENSE = "adv-enzyme-new-sense";

    /** The custom-enzyme editor's cut-residues field for a new row. */
    public static final String ENZYME_NEW_CUT = "adv-enzyme-new-cut";

    /** The custom-enzyme editor's no-cut-residues field for a new row. */
    public static final String ENZYME_NEW_NO_CUT = "adv-enzyme-new-nocut";

    /** The action that adds the new row to the configuration's enzyme table. */
    public static final String ENZYME_ADD = "adv-enzyme-add";

    /** The custom-enzyme editor's last outcome in words. */
    public static final String ENZYME_STATUS = "adv-enzyme-status";

    /** The action that adds spectrum files through the file chooser. */
    public static final String SPECTRA_ADD = "ess-spectra-add";

    /** The list of chosen spectrum files. */
    public static final String SPECTRA_LIST = "ess-spectra-list";

    /** How many spectrum files are chosen, in words. */
    public static final String SPECTRA_SUMMARY = "ess-spectra-summary";

    /** What the file system says about the database, with its full path. */
    public static final String DATABASE_STATUS = "ess-database-status";

    /** The whole precursor setting in words. */
    public static final String PRECURSOR_SUMMARY = "ess-precursor-summary";

    /** The fragment settings' instrument choice. */
    public static final String FRAGMENT_SETTING = "ess-fragment-setting";

    /** The fragment settings in words. */
    public static final String FRAGMENT_SETTING_WORDS = "ess-fragment-setting-words";

    /** The Run section's Run control. */
    public static final String RUN_START = "run-start";

    /** Why the parameters block a run, or that they do not. */
    public static final String RUN_PARAMETERS = "run-parameters";

    /** Why the workflow engine cannot run, or that it can. */
    public static final String RUN_ENGINE = "run-engine";

    /**
     * The two surfaces a parameter's control appears on. A parameter shown in Essentials is shown
     * in Advanced too, so the surface is part of every parameter identifier and the two controls
     * never share one.
     */
    public enum Surface {

        /** The curated Essentials level: identifiers begin {@code ess-}. */
        ESSENTIALS("ess"),

        /** The Advanced level: identifiers begin {@code adv-}. */
        ADVANCED("adv");

        private final String prefix;

        Surface(String prefix) {
            this.prefix = prefix;
        }

        /**
         * The identifier prefix.
         *
         * @return {@code ess} or {@code adv}
         */
        public String prefix() {
            return prefix;
        }
    }

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

    /*
     * ONE TOOL BUILD IS ONE ROW, AND A ROW IS NOT NAMED AFTER ITS TOOL AND VERSION.  On Apple
     * silicon Comet 2026.02.2 is two published builds and org.cometgui.domain.tools.ToolManager
     * offers both, so an identifier built from the tool and the version alone would name two
     * controls with one string and Scene.lookup would return whichever it reached first.  The key
     * these methods take is therefore produced by the view-model, which is the one place that can
     * see the whole offered list and can tell the second row of a release from the first.
     *
     * The key's shape is checked here rather than trusted, because these identifiers are looked up
     * as CSS selectors: a dot in a key would be read as a style class and the lookup would
     * silently find nothing, which is exactly the shape of failure this project's pinned
     * identifiers exist to stop.
     */

    /**
     * The identifier of one tool build's row.
     *
     * @param rowKey the view-model's key for that row
     * @return {@code "tool-row-"} followed by the key
     * @throws NullPointerException if {@code rowKey} is {@code null}
     * @throws IllegalArgumentException if the key is not lower-case words joined by single hyphens
     *     or underscores, with a message quoting the rejected value
     */
    public static String toolRow(String rowKey) {
        Objects.requireNonNull(rowKey, "rowKey");
        if (!isToolRowKey(rowKey)) {
            throw new IllegalArgumentException(
                    "not a usable tool row key: \""
                            + rowKey
                            + "\" (expected lower-case words joined by single hyphens or"
                            + " underscores, such as percolator-3_07_1-1; a dot would be read as a"
                            + " style class by Scene.lookup and the control would never be found)");
        }
        return "tool-row-" + rowKey;
    }

    /**
     * The identifier of the label naming the tool and version a row is for.
     *
     * @param rowKey the view-model's key for that row
     * @return the row identifier with {@code "-name"} appended
     * @throws NullPointerException if {@code rowKey} is {@code null}
     * @throws IllegalArgumentException if the key is not of the required shape
     */
    public static String toolRowName(String rowKey) {
        return toolRow(rowKey) + "-name";
    }

    /**
     * The identifier of the label stating a row's install state in words.
     *
     * @param rowKey the view-model's key for that row
     * @return the row identifier with {@code "-state"} appended
     * @throws NullPointerException if {@code rowKey} is {@code null}
     * @throws IllegalArgumentException if the key is not of the required shape
     */
    public static String toolRowState(String rowKey) {
        return toolRow(rowKey) + "-state";
    }

    /**
     * The identifier of the label listing what a build can do, each claim with its evidence.
     *
     * @param rowKey the view-model's key for that row
     * @return the row identifier with {@code "-capabilities"} appended
     * @throws NullPointerException if {@code rowKey} is {@code null}
     * @throws IllegalArgumentException if the key is not of the required shape
     */
    public static String toolRowCapabilities(String rowKey) {
        return toolRow(rowKey) + "-capabilities";
    }

    /**
     * The identifier of the label carrying a build's version advisories ({@code R-PERC-11}).
     *
     * @param rowKey the view-model's key for that row
     * @return the row identifier with {@code "-advisories"} appended
     * @throws NullPointerException if {@code rowKey} is {@code null}
     * @throws IllegalArgumentException if the key is not of the required shape
     */
    public static String toolRowAdvisories(String rowKey) {
        return toolRow(rowKey) + "-advisories";
    }

    /**
     * The identifier of the label saying how many bytes installing a build would transfer.
     *
     * @param rowKey the view-model's key for that row
     * @return the row identifier with {@code "-download"} appended
     * @throws NullPointerException if {@code rowKey} is {@code null}
     * @throws IllegalArgumentException if the key is not of the required shape
     */
    public static String toolRowDownload(String rowKey) {
        return toolRow(rowKey) + "-download";
    }

    /**
     * The identifier of the label carrying a row's {@code R-PLAT-03} loader diagnostic.
     *
     * @param rowKey the view-model's key for that row
     * @return the row identifier with {@code "-diagnostic"} appended
     * @throws NullPointerException if {@code rowKey} is {@code null}
     * @throws IllegalArgumentException if the key is not of the required shape
     */
    public static String toolRowDiagnostic(String rowKey) {
        return toolRow(rowKey) + "-diagnostic";
    }

    /**
     * The identifier of the label naming where an installed build lives.
     *
     * @param rowKey the view-model's key for that row
     * @return the row identifier with {@code "-path"} appended
     * @throws NullPointerException if {@code rowKey} is {@code null}
     * @throws IllegalArgumentException if the key is not of the required shape
     */
    public static String toolRowPath(String rowKey) {
        return toolRow(rowKey) + "-path";
    }

    /**
     * The identifier of the label reporting where a running install has got to.
     *
     * @param rowKey the view-model's key for that row
     * @return the row identifier with {@code "-progress"} appended
     * @throws NullPointerException if {@code rowKey} is {@code null}
     * @throws IllegalArgumentException if the key is not of the required shape
     */
    public static String toolRowProgress(String rowKey) {
        return toolRow(rowKey) + "-progress";
    }

    /**
     * The identifier of a row's Install action.
     *
     * @param rowKey the view-model's key for that row
     * @return the row identifier with {@code "-install"} appended
     * @throws NullPointerException if {@code rowKey} is {@code null}
     * @throws IllegalArgumentException if the key is not of the required shape
     */
    public static String toolRowInstall(String rowKey) {
        return toolRow(rowKey) + "-install";
    }

    /**
     * The identifier of a row's Cancel action.
     *
     * @param rowKey the view-model's key for that row
     * @return the row identifier with {@code "-cancel"} appended
     * @throws NullPointerException if {@code rowKey} is {@code null}
     * @throws IllegalArgumentException if the key is not of the required shape
     */
    public static String toolRowCancel(String rowKey) {
        return toolRow(rowKey) + "-cancel";
    }

    /**
     * Whether a key has the shape a tool row key must have: lower-case words ({@code a}-{@code z},
     * {@code 0}-{@code 9}) joined by single hyphens or underscores. See the note above {@link
     * #toolRow(String)} for why a dot is rejected.
     *
     * <p>Written out rather than as a regular expression since Phase 07, whose architecture rule
     * keeps {@code java.util.regex} out of {@code org.cometgui.ui} altogether: the UI reads a
     * parameter value only through the parameter model, and a rule with an exemption for "this one
     * pattern is not about parameters" would be a rule nobody could rely on.
     *
     * @param key the candidate key
     * @return {@code true} if it is non-empty, starts and ends with a word character, and has no
     *     two joiners in a row
     */
    private static boolean isToolRowKey(String key) {
        boolean expectingWord = true;
        for (int index = 0; index < key.length(); index++) {
            char c = key.charAt(index);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                expectingWord = false;
            } else if ((c == '-' || c == '_') && !expectingWord) {
                expectingWord = true;
            } else {
                return false;
            }
        }
        return !expectingWord;
    }

    /*
     * THE PARAMETER EDITOR'S IDENTIFIERS ARE BUILT FROM THE PARAMETER'S OWN NAME, exactly as Comet
     * spells it (database_name, use_A_ions), behind the surface's prefix: ess-database_name,
     * adv-use_A_ions.  A name is checked rather than trusted, for the reason toolRow gives: these
     * are looked up as CSS selectors, so a character other than a letter, a digit or an underscore
     * would make the lookup find nothing.
     */

    /**
     * The identifier of a parameter's input control on a surface: the check box, combo box or text
     * field that edits it (the first of a range's two fields; a variable-modification slot's
     * heading).
     *
     * @param surface where the control is
     * @param name the parameter name, as Comet spells it
     * @return the surface prefix, a hyphen and the name, for example {@code ess-fragment_bin_tol}
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if the name is not letters, digits and underscores
     */
    public static String parameterControl(Surface surface, String name) {
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(name, "name");
        if (!isParameterName(name)) {
            throw new IllegalArgumentException(
                    "not a usable parameter name for an identifier: \""
                            + name
                            + "\" (expected letters, digits and underscores, as Comet spells a"
                            + " parameter)");
        }
        return surface.prefix() + "-" + name;
    }

    /**
     * The identifier of the label naming a parameter's control.
     *
     * @param surface where the control is
     * @param name the parameter name
     * @return the control identifier with {@code "-label"} appended
     */
    public static String parameterLabel(Surface surface, String name) {
        return parameterControl(surface, name) + "-label";
    }

    /**
     * The identifier of a parameter's reset action.
     *
     * @param surface where the control is
     * @param name the parameter name
     * @return the control identifier with {@code "-reset"} appended
     */
    public static String parameterReset(Surface surface, String name) {
        return parameterControl(surface, name) + "-reset";
    }

    /**
     * The identifier of the label saying where a parameter's value came from.
     *
     * @param surface where the control is
     * @param name the parameter name
     * @return the control identifier with {@code "-origin"} appended
     */
    public static String parameterOrigin(Surface surface, String name) {
        return parameterControl(surface, name) + "-origin";
    }

    /**
     * The identifier of the label stating a parameter's validation state in words.
     *
     * @param surface where the control is
     * @param name the parameter name
     * @return the control identifier with {@code "-state"} appended
     */
    public static String parameterState(Surface surface, String name) {
        return parameterControl(surface, name) + "-state";
    }

    /**
     * The identifier of the label saying why a parameter is locked.
     *
     * @param surface where the control is
     * @param name the parameter name
     * @return the control identifier with {@code "-lock"} appended
     */
    public static String parameterLock(Surface surface, String name) {
        return parameterControl(surface, name) + "-lock";
    }

    /**
     * The identifier of a range parameter's second field.
     *
     * @param surface where the control is
     * @param name the parameter name
     * @return the control identifier with {@code "-second"} appended
     */
    public static String parameterSecond(Surface surface, String name) {
        return parameterControl(surface, name) + "-second";
    }

    /**
     * The identifier of a file-path parameter's chooser action.
     *
     * @param surface where the control is
     * @param name the parameter name
     * @return the control identifier with {@code "-choose"} appended
     */
    public static String parameterChoose(Surface surface, String name) {
        return parameterControl(surface, name) + "-choose";
    }

    /**
     * The identifier of the label showing a variable-modification slot's serialised value.
     *
     * @param surface where the editor is
     * @param slot the slot's parameter name, such as {@code variable_mod01}
     * @return the slot's identifier with {@code "-serialised"} appended
     */
    public static String variableModSerialised(Surface surface, String slot) {
        return parameterControl(surface, slot) + "-serialised";
    }

    /**
     * The identifier of the control editing one part of a variable-modification slot's tuple.
     *
     * @param surface where the editor is
     * @param slot the slot's parameter name
     * @param part the part
     * @return the slot's identifier, {@code "-part-"}, and the part's constant name in lower case
     *     with hyphens, for example {@code ess-variable_mod01-part-maximum-count}
     */
    public static String variableModPart(Surface surface, String slot, VariableModPart part) {
        Objects.requireNonNull(part, "part");
        return parameterControl(surface, slot) + "-part-" + hyphenated(part.name());
    }

    /**
     * The identifier of the check box selecting one residue letter of a slot's residue token.
     *
     * @param surface where the editor is
     * @param slot the slot's parameter name
     * @param letter an upper-case residue letter
     * @return the slot's identifier, {@code "-residue-"} and the letter
     * @throws IllegalArgumentException if {@code letter} is not {@code A} to {@code Z}
     */
    public static String variableModResidue(Surface surface, String slot, char letter) {
        if (letter < 'A' || letter > 'Z') {
            throw new IllegalArgumentException("a residue letter is A to Z, not '" + letter + "'");
        }
        return parameterControl(surface, slot) + "-residue-" + letter;
    }

    /**
     * The identifier of the check box selecting one terminal code of a slot's residue token. Named
     * after the code's meaning, never its character: {@code ^} and {@code $} cannot appear in a CSS
     * identifier.
     *
     * @param surface where the editor is
     * @param slot the slot's parameter name
     * @param code the terminal code
     * @return the slot's identifier, {@code "-terminus-"} and the code's constant name in lower
     *     case with hyphens, for example {@code ess-variable_mod01-terminus-protein-n}
     */
    public static String variableModTerminus(Surface surface, String slot, TerminalCode code) {
        Objects.requireNonNull(code, "code");
        return parameterControl(surface, slot) + "-terminus-" + hyphenated(code.name());
    }

    /**
     * The identifier of the action moving a slot's modification one slot up.
     *
     * @param surface where the editor is
     * @param slot the slot's parameter name
     * @return the slot's identifier with {@code "-up"} appended
     */
    public static String variableModUp(Surface surface, String slot) {
        return parameterControl(surface, slot) + "-up";
    }

    /**
     * The identifier of the action moving a slot's modification one slot down.
     *
     * @param surface where the editor is
     * @param slot the slot's parameter name
     * @return the slot's identifier with {@code "-down"} appended
     */
    public static String variableModDown(Surface surface, String slot) {
        return parameterControl(surface, slot) + "-down";
    }

    /**
     * The identifier of the action removing a slot's modification.
     *
     * @param surface where the editor is
     * @param slot the slot's parameter name
     * @return the slot's identifier with {@code "-remove"} appended
     */
    public static String variableModRemove(Surface surface, String slot) {
        return parameterControl(surface, slot) + "-remove";
    }

    /**
     * The identifier of the variable-modification editor's common-modification choice.
     *
     * @param surface where the editor is
     * @return the surface prefix and {@code "-varmod-preset"}
     */
    public static String variableModPreset(Surface surface) {
        return Objects.requireNonNull(surface, "surface").prefix() + "-varmod-preset";
    }

    /**
     * The identifier of the action adding the chosen common modification to the first free slot.
     *
     * @param surface where the editor is
     * @return the surface prefix and {@code "-varmod-add"}
     */
    public static String variableModAdd(Surface surface) {
        return Objects.requireNonNull(surface, "surface").prefix() + "-varmod-add";
    }

    /**
     * The identifier of the label carrying the slots' cross-validation against the limit and the
     * requirement ({@code R-PARAM-10}), in words.
     *
     * @param surface where the editor is
     * @return the surface prefix and {@code "-varmod-cross"}
     */
    public static String variableModCross(Surface surface) {
        return Objects.requireNonNull(surface, "surface").prefix() + "-varmod-cross";
    }

    /**
     * The identifier of the last add's or move's outcome in words.
     *
     * @param surface where the editor is
     * @return the surface prefix and {@code "-varmod-status"}
     */
    public static String variableModStatus(Surface surface) {
        return Objects.requireNonNull(surface, "surface").prefix() + "-varmod-status";
    }

    /**
     * The identifier of one Essentials group.
     *
     * @param section the group's section
     * @return {@code "ess-group-"} and the constant name in lower case with hyphens, for example
     *     {@code ess-group-variable-modifications}
     */
    public static String essentialsGroup(EssentialsSection section) {
        return "ess-group-" + hyphenated(Objects.requireNonNull(section, "section").name());
    }

    /**
     * The identifier of one Advanced category's container.
     *
     * @param category the category
     * @return {@code "adv-category-"} and the category's own identifier, for example {@code
     *     adv-category-database_peff}
     */
    public static String advancedCategory(ParameterCategory category) {
        return "adv-category-" + Objects.requireNonNull(category, "category").id();
    }

    /**
     * The identifier of the switch that shows or hides one Advanced category's parameters.
     *
     * @param category the category
     * @return the category identifier with {@code "-toggle"} appended
     */
    public static String advancedCategoryToggle(ParameterCategory category) {
        return advancedCategory(category) + "-toggle";
    }

    /**
     * The identifier of one Advanced category's reset action (asks to confirm).
     *
     * @param category the category
     * @return the category identifier with {@code "-reset"} appended
     */
    public static String advancedCategoryReset(ParameterCategory category) {
        return advancedCategory(category) + "-reset";
    }

    /**
     * The identifier of the confirmation of one Advanced category's reset.
     *
     * @param category the category
     * @return the category identifier with {@code "-reset-confirm"} appended
     */
    public static String advancedCategoryResetConfirm(ParameterCategory category) {
        return advancedCategory(category) + "-reset-confirm";
    }

    /**
     * The identifier of the cancellation of one Advanced category's reset.
     *
     * @param category the category
     * @return the category identifier with {@code "-reset-cancel"} appended
     */
    public static String advancedCategoryResetCancel(ParameterCategory category) {
        return advancedCategory(category) + "-reset-cancel";
    }

    /**
     * The identifier of one entry of the validation summary, by its place in the summary.
     *
     * @param index the entry's 0-based position
     * @return {@code "param-summary-entry-"} and the position
     * @throws IllegalArgumentException if {@code index} is negative
     */
    public static String summaryEntry(int index) {
        return "param-summary-entry-" + nonNegative(index);
    }

    /**
     * The identifier of one chosen spectrum file's row, by its place in the list.
     *
     * @param index the row's 0-based position
     * @return {@code "ess-spectrum-"} and the position
     * @throws IllegalArgumentException if {@code index} is negative
     */
    public static String spectrum(int index) {
        return "ess-spectrum-" + nonNegative(index);
    }

    /**
     * The identifier of the action removing one chosen spectrum file.
     *
     * @param index the row's 0-based position
     * @return the row's identifier with {@code "-remove"} appended
     * @throws IllegalArgumentException if {@code index} is negative
     */
    public static String spectrumRemove(int index) {
        return spectrum(index) + "-remove";
    }

    /**
     * The identifier of one filter of the global parameter search.
     *
     * @param filter the filter
     * @return {@code "param-search-filter-"} and the constant name in lower case with hyphens, for
     *     example {@code param-search-filter-modified}
     */
    public static String searchFilter(SearchFilter filter) {
        return "param-search-filter-" + hyphenated(Objects.requireNonNull(filter, "filter").name());
    }

    /**
     * The identifier of one result of the global parameter search, by its place in the results.
     *
     * @param index the result's 0-based position
     * @return {@code "param-search-result-"} and the position
     * @throws IllegalArgumentException if {@code index} is negative
     */
    public static String searchResult(int index) {
        return "param-search-result-" + nonNegative(index);
    }

    /**
     * The identifier of one row's check box in the preset preview, by its place in the preview.
     *
     * @param index the row's 0-based position
     * @return {@code "ess-preset-row-"} and the position
     * @throws IllegalArgumentException if {@code index} is negative
     */
    public static String presetRow(int index) {
        return "ess-preset-row-" + nonNegative(index);
    }

    /**
     * The identifier of one preview row's current value.
     *
     * @param index the row's 0-based position
     * @return the row's identifier with {@code "-current"} appended
     */
    public static String presetRowCurrent(int index) {
        return presetRow(index) + "-current";
    }

    /**
     * The identifier of one preview row's preset value.
     *
     * @param index the row's 0-based position
     * @return the row's identifier with {@code "-preset"} appended
     */
    public static String presetRowPreset(int index) {
        return presetRow(index) + "-preset";
    }

    /**
     * The identifier of one line of the Expert draft, by its line number.
     *
     * @param number the 1-based line number
     * @return {@code "param-expert-line-"} and the number
     * @throws IllegalArgumentException if {@code number} is negative
     */
    public static String expertLine(int number) {
        return "param-expert-line-" + nonNegative(number);
    }

    /**
     * The identifier of one diagnostic of the Expert draft, by its place in the list.
     *
     * @param index the diagnostic's 0-based position
     * @return {@code "param-expert-diagnostic-"} and the position
     * @throws IllegalArgumentException if {@code index} is negative
     */
    public static String expertDiagnostic(int index) {
        return "param-expert-diagnostic-" + nonNegative(index);
    }

    /**
     * The identifier of one unknown parameter in the Expert list, by its place in the list.
     *
     * @param index the parameter's 0-based position
     * @return {@code "param-expert-unknown-"} and the position
     * @throws IllegalArgumentException if {@code index} is negative
     */
    public static String expertUnknown(int index) {
        return "param-expert-unknown-" + nonNegative(index);
    }

    /**
     * The identifier of the action removing one unknown parameter.
     *
     * @param index the parameter's 0-based position
     * @return the parameter's identifier with {@code "-remove"} appended
     */
    public static String expertUnknownRemove(int index) {
        return expertUnknown(index) + "-remove";
    }

    /**
     * The identifier of one change of the migration under review, by its place in the review.
     *
     * @param index the change's 0-based position
     * @return {@code "param-migration-row-"} and the position
     * @throws IllegalArgumentException if {@code index} is negative
     */
    public static String migrationRow(int index) {
        return "param-migration-row-" + nonNegative(index);
    }

    /**
     * The identifier of one migration change's state in words.
     *
     * @param index the change's 0-based position
     * @return the row's identifier with {@code "-state"} appended
     */
    public static String migrationRowState(int index) {
        return migrationRow(index) + "-state";
    }

    /**
     * The identifier of the action accepting the value a migration change needing a decision put in
     * place.
     *
     * @param index the change's 0-based position
     * @return the row's identifier with {@code "-accept"} appended
     */
    public static String migrationRowAccept(int index) {
        return migrationRow(index) + "-accept";
    }

    /**
     * The identifier of the action moving the focus to a migration change's field.
     *
     * @param index the change's 0-based position
     * @return the row's identifier with {@code "-goto"} appended
     */
    public static String migrationRowGoTo(int index) {
        return migrationRow(index) + "-goto";
    }

    /**
     * The identifier of one row of the configuration's enzyme table in the custom-enzyme editor.
     *
     * @param index the row's 0-based position in the table
     * @return {@code "adv-enzyme-row-"} and the position
     * @throws IllegalArgumentException if {@code index} is negative
     */
    public static String enzymeRow(int index) {
        return "adv-enzyme-row-" + nonNegative(index);
    }

    /**
     * The identifier of the action removing one row of the enzyme table.
     *
     * @param index the row's 0-based position in the table
     * @return the row's identifier with {@code "-remove"} appended
     */
    public static String enzymeRowRemove(int index) {
        return enzymeRow(index) + "-remove";
    }

    private static int nonNegative(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("a position is 0 or more, not " + index);
        }
        return index;
    }

    /**
     * A constant name in lower case with hyphens: {@code MAXIMUM_COUNT} is {@code maximum-count}.
     */
    private static String hyphenated(String constantName) {
        return constantName.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /**
     * Whether a parameter name can be part of an identifier: non-empty, letters, digits and
     * underscores only.
     */
    private static boolean isParameterName(String name) {
        if (name.isEmpty()) {
            return false;
        }
        for (int index = 0; index < name.length(); index++) {
            char c = name.charAt(index);
            boolean usable =
                    (c >= 'a' && c <= 'z')
                            || (c >= 'A' && c <= 'Z')
                            || (c >= '0' && c <= '9')
                            || c == '_';
            if (!usable) {
                return false;
            }
        }
        return true;
    }
}
