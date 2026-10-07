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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.cometgui.domain.log.MessageSeverity;
import org.cometgui.params.comet.schema.ParameterCategory;
import org.cometgui.params.comet.schema.TerminalCode;
import org.cometgui.params.comet.schema.ValueKind;
import org.cometgui.params.comet.value.VariableModPart;
import org.cometgui.ui.viewmodel.SectionId;
import org.cometgui.ui.viewmodel.params.EssentialsSection;
import org.cometgui.ui.viewmodel.params.SearchFilter;
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
     * How many identifiers are pinned: 25 constants, 45 section identifiers, 24 stepper stage
     * identifiers, 11 Tool Manager row identifiers, 8 console stage filters, 7 stepper arrows, 4
     * branch identifiers and 4 severity filters. Stated so that deleting a whole category of pins
     * is a failure rather than a smaller test.
     *
     * <p>Raised from 119 by phase 05 unit 9, which added the Tool Manager section's three
     * containers and the eleven identifiers one tool build's row carries. Adding an entry because
     * the thing it describes was added is maintenance; removing one, or lowering this number to
     * make a build pass, is not.
     *
     * <p>Lowered from 133 to 128 by phase 07 unit 2, which removed the Settings section from
     * navigation (tier-1 decision, {@code STATUS.rst}, <em>The Settings section</em>): its five
     * identifiers -- {@code section-settings}, {@code -heading}, {@code -description}, {@code
     * -note} and {@code nav-settings} -- went with the {@code SectionId} constant. That is the
     * removal of the thing described, not of a pin for something that still exists.
     *
     * <p>Raised from 128 to 317 by phase 07 unit 6, which added the Comet parameter editor and the
     * Run control: 29 constants; the per-parameter scheme pinned for one parameter of each of the
     * 14 value kinds on both surfaces (28) and for each of the 12 per-parameter methods on both
     * surfaces (24); the 4 per-surface methods on both surfaces (8); the 10 tuple parts and the 4
     * terminal codes of one slot; the 10 Essentials groups; the 14 Advanced categories times five
     * (70); and 6 identifiers built from a position or a character.
     *
     * <p>Raised from 317 to 382 by phase 07 unit 7, which filled the editor's two placeholders and
     * added its remaining parts: the two placeholder constants removed with the placeholders they
     * named; 47 constants added (the Expert level 17, import 8, search 3, migration review 3,
     * preset preview 9, custom-enzyme editor 7); the 5 search filters; and 15 identifiers built
     * from a position, one per new method that builds one (two for the search result).
     *
     * <p>Raised from 382 to 396 by phase 07 unit 10, which routed the Essentials fragment
     * instrument choice through a preview of its own and built the static-modification table: 7
     * constants (the fragment preview's container, made-for, problems, three actions and status); 3
     * identifiers built from a position (one per new fragment-row method); the table's container on
     * both surfaces (2); and a row's name cell on both surfaces (2).
     *
     * <p>Raised from 396 to 399 by phase 08 unit 7, which made the Run section run the workflow
     * engine: 3 constants (Cancel, the outcome and the rerun preview).
     */
    private static final int PINNED_IDENTIFIER_COUNT = 399;

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
                    Map.entry("CONSOLE_COPY", "console-copy"),
                    Map.entry("TOOL_MANAGER_PANE", "tool-manager-pane"),
                    Map.entry("TOOL_MANAGER_SUMMARY", "tool-manager-summary"),
                    Map.entry("TOOL_MANAGER_ROWS", "tool-manager-rows"),
                    Map.entry("PARAM_EDITOR", "param-editor"),
                    Map.entry("PARAM_RELEASE", "param-release"),
                    Map.entry("PARAM_RELEASE_STATUS", "param-release-status"),
                    Map.entry("PARAM_MODE_ESSENTIALS", "param-mode-essentials"),
                    Map.entry("PARAM_MODE_ADVANCED", "param-mode-advanced"),
                    Map.entry("PARAM_MODE_EXPERT", "param-mode-expert"),
                    Map.entry("PARAM_SUMMARY", "param-summary"),
                    Map.entry("PARAM_SUMMARY_HEADLINE", "param-summary-headline"),
                    Map.entry("PARAM_BODY", "param-body"),
                    Map.entry("PARAM_ESSENTIALS", "param-essentials"),
                    Map.entry("PARAM_ADVANCED", "param-advanced"),
                    Map.entry("PARAM_EXPERT", "param-expert"),
                    Map.entry("PARAM_SAVE", "param-save"),
                    Map.entry("PARAM_SAVE_STATUS", "param-save-status"),
                    Map.entry("PARAM_RESET_ALL", "param-reset-all"),
                    Map.entry("PARAM_RESET_ALL_CONFIRM", "param-reset-all-confirm"),
                    Map.entry("PARAM_RESET_ALL_CANCEL", "param-reset-all-cancel"),
                    Map.entry("EXPERT_CANONICAL", "param-expert-canonical"),
                    Map.entry("EXPERT_CANONICAL_STATUS", "param-expert-canonical-status"),
                    Map.entry("EXPERT_DRAFT", "param-expert-draft"),
                    Map.entry("EXPERT_LINES", "param-expert-lines"),
                    Map.entry("EXPERT_DIAGNOSTICS_HEADLINE", "param-expert-diagnostics-headline"),
                    Map.entry("EXPERT_APPLY", "param-expert-apply"),
                    Map.entry("EXPERT_REVERT", "param-expert-revert"),
                    Map.entry("EXPERT_APPLY_STATUS", "param-expert-apply-status"),
                    Map.entry("EXPERT_OFFENDING", "param-expert-offending"),
                    Map.entry("EXPERT_CONFIRMATION", "param-expert-confirmation"),
                    Map.entry("EXPERT_CHANGES", "param-expert-changes"),
                    Map.entry("EXPERT_CONFIRM", "param-expert-confirm"),
                    Map.entry("EXPERT_CANCEL", "param-expert-cancel"),
                    Map.entry("EXPERT_COMPARE", "param-expert-compare"),
                    Map.entry("EXPERT_COMPARE_ROWS", "param-expert-compare-rows"),
                    Map.entry("EXPERT_SAVED_ROWS", "param-expert-saved-rows"),
                    Map.entry("EXPERT_UNKNOWN_HEADLINE", "param-expert-unknown-headline"),
                    Map.entry("PARAM_IMPORT", "param-import"),
                    Map.entry("PARAM_IMPORT_STATUS", "param-import-status"),
                    Map.entry("PARAM_IMPORT_OFFER", "param-import-offer"),
                    Map.entry("PARAM_IMPORT_QUESTION", "param-import-question"),
                    Map.entry("PARAM_IMPORT_MIGRATE", "param-import-migrate"),
                    Map.entry("PARAM_IMPORT_OWN", "param-import-own"),
                    Map.entry("PARAM_IMPORT_SELECTED", "param-import-selected"),
                    Map.entry("PARAM_IMPORT_DISMISS", "param-import-dismiss"),
                    Map.entry("PARAM_SEARCH", "param-search"),
                    Map.entry("PARAM_SEARCH_HEADLINE", "param-search-headline"),
                    Map.entry("PARAM_SEARCH_RESULTS", "param-search-results"),
                    Map.entry("PARAM_MIGRATION", "param-migration"),
                    Map.entry("PARAM_MIGRATION_HEADLINE", "param-migration-headline"),
                    Map.entry("PARAM_MIGRATION_STATUS", "param-migration-status"),
                    Map.entry("PRESET_CHOICE", "ess-preset-choice"),
                    Map.entry("PRESET_PREVIEW", "ess-preset-preview"),
                    Map.entry("PRESET_REVIEW", "ess-preset-review"),
                    Map.entry("PRESET_MADE_FOR", "ess-preset-made-for"),
                    Map.entry("PRESET_PROBLEMS", "ess-preset-problems"),
                    Map.entry("PRESET_APPLY_ALL", "ess-preset-apply-all"),
                    Map.entry("PRESET_APPLY_SELECTED", "ess-preset-apply-selected"),
                    Map.entry("PRESET_CANCEL", "ess-preset-cancel"),
                    Map.entry("PRESET_STATUS", "ess-preset-status"),
                    Map.entry("ENZYME_NEW_NUMBER", "adv-enzyme-new-number"),
                    Map.entry("ENZYME_NEW_NAME", "adv-enzyme-new-name"),
                    Map.entry("ENZYME_NEW_SENSE", "adv-enzyme-new-sense"),
                    Map.entry("ENZYME_NEW_CUT", "adv-enzyme-new-cut"),
                    Map.entry("ENZYME_NEW_NO_CUT", "adv-enzyme-new-nocut"),
                    Map.entry("ENZYME_ADD", "adv-enzyme-add"),
                    Map.entry("ENZYME_STATUS", "adv-enzyme-status"),
                    Map.entry("SPECTRA_ADD", "ess-spectra-add"),
                    Map.entry("SPECTRA_LIST", "ess-spectra-list"),
                    Map.entry("SPECTRA_SUMMARY", "ess-spectra-summary"),
                    Map.entry("DATABASE_STATUS", "ess-database-status"),
                    Map.entry("PRECURSOR_SUMMARY", "ess-precursor-summary"),
                    Map.entry("FRAGMENT_SETTING", "ess-fragment-setting"),
                    Map.entry("FRAGMENT_SETTING_WORDS", "ess-fragment-setting-words"),
                    Map.entry("FRAGMENT_REVIEW", "ess-fragment-review"),
                    Map.entry("FRAGMENT_MADE_FOR", "ess-fragment-made-for"),
                    Map.entry("FRAGMENT_PROBLEMS", "ess-fragment-problems"),
                    Map.entry("FRAGMENT_APPLY_ALL", "ess-fragment-apply-all"),
                    Map.entry("FRAGMENT_APPLY_SELECTED", "ess-fragment-apply-selected"),
                    Map.entry("FRAGMENT_CANCEL", "ess-fragment-cancel"),
                    Map.entry("FRAGMENT_STATUS", "ess-fragment-status"),
                    Map.entry("RUN_START", "run-start"),
                    Map.entry("RUN_PARAMETERS", "run-parameters"),
                    Map.entry("RUN_ENGINE", "run-engine"),
                    Map.entry("RUN_CANCEL", "run-cancel"),
                    Map.entry("RUN_OUTCOME", "run-outcome"),
                    Map.entry("RUN_PREVIEW", "run-preview"));

    // -----------------------------------------------------------------------------------------
    // The parameter editor (Phase 07). A parameter's identifiers are built from its own name, so
    // they cannot all be listed; the SCHEME is pinned instead, for a representative parameter of
    // every value kind on both surfaces and for every method that builds one, each identifier
    // typed out in full. Nothing below is produced by UiIds, ValueKind or the metadata.
    // -----------------------------------------------------------------------------------------

    /**
     * For one parameter of every {@link ValueKind}: its name, then its control's identifier on
     * Essentials and on Advanced. A kind whose parameters Essentials does not show is pinned on
     * both all the same: the scheme does not depend on the surface showing the parameter.
     */
    private static final Map<ValueKind, List<String>> PARAMETER_OF_EACH_KIND =
            Map.ofEntries(
                    Map.entry(
                            ValueKind.INTEGER,
                            List.of(
                                    "allowed_missed_cleavage",
                                    "ess-allowed_missed_cleavage",
                                    "adv-allowed_missed_cleavage")),
                    Map.entry(
                            ValueKind.DECIMAL,
                            List.of(
                                    "fragment_bin_tol",
                                    "ess-fragment_bin_tol",
                                    "adv-fragment_bin_tol")),
                    Map.entry(
                            ValueKind.STRING,
                            List.of("decoy_prefix", "ess-decoy_prefix", "adv-decoy_prefix")),
                    Map.entry(
                            ValueKind.BOOLEAN_FLAG,
                            List.of(
                                    "require_variable_mod",
                                    "ess-require_variable_mod",
                                    "adv-require_variable_mod")),
                    Map.entry(
                            ValueKind.INTEGER_ENUM,
                            List.of(
                                    "peptide_mass_units",
                                    "ess-peptide_mass_units",
                                    "adv-peptide_mass_units")),
                    Map.entry(
                            ValueKind.STRING_ENUM,
                            List.of(
                                    "activation_method",
                                    "ess-activation_method",
                                    "adv-activation_method")),
                    Map.entry(
                            ValueKind.FILE_PATH,
                            List.of("database_name", "ess-database_name", "adv-database_name")),
                    Map.entry(
                            ValueKind.INTEGER_RANGE,
                            List.of(
                                    "peptide_length_range",
                                    "ess-peptide_length_range",
                                    "adv-peptide_length_range")),
                    Map.entry(
                            ValueKind.DECIMAL_RANGE,
                            List.of(
                                    "digest_mass_range",
                                    "ess-digest_mass_range",
                                    "adv-digest_mass_range")),
                    Map.entry(
                            ValueKind.DECIMAL_LIST,
                            List.of("mass_offsets", "ess-mass_offsets", "adv-mass_offsets")),
                    Map.entry(
                            ValueKind.TOLERANCE_PAIR_MEMBER,
                            List.of(
                                    "peptide_mass_tolerance_lower",
                                    "ess-peptide_mass_tolerance_lower",
                                    "adv-peptide_mass_tolerance_lower")),
                    Map.entry(
                            ValueKind.VARIABLE_MOD_TUPLE,
                            List.of("variable_mod01", "ess-variable_mod01", "adv-variable_mod01")),
                    Map.entry(
                            ValueKind.ENZYME_REFERENCE,
                            List.of(
                                    "search_enzyme_number",
                                    "ess-search_enzyme_number",
                                    "adv-search_enzyme_number")),
                    Map.entry(
                            ValueKind.ION_SERIES_FLAG,
                            List.of("use_A_ions", "ess-use_A_ions", "adv-use_A_ions")));

    /**
     * Every UiIds method taking a surface and a parameter name, by method name: the parameter it is
     * pinned for, then the identifier on Essentials and on Advanced. {@code parameterControl} is
     * pinned here for a parameter of its own as well as by kind above.
     */
    private static final Map<String, List<String>> PER_PARAMETER =
            Map.ofEntries(
                    Map.entry(
                            "parameterControl",
                            List.of(
                                    "max_variable_mods_in_peptide",
                                    "ess-max_variable_mods_in_peptide",
                                    "adv-max_variable_mods_in_peptide")),
                    Map.entry(
                            "parameterLabel",
                            List.of(
                                    "max_variable_mods_in_peptide",
                                    "ess-max_variable_mods_in_peptide-label",
                                    "adv-max_variable_mods_in_peptide-label")),
                    Map.entry(
                            "parameterReset",
                            List.of(
                                    "max_variable_mods_in_peptide",
                                    "ess-max_variable_mods_in_peptide-reset",
                                    "adv-max_variable_mods_in_peptide-reset")),
                    Map.entry(
                            "parameterOrigin",
                            List.of(
                                    "max_variable_mods_in_peptide",
                                    "ess-max_variable_mods_in_peptide-origin",
                                    "adv-max_variable_mods_in_peptide-origin")),
                    Map.entry(
                            "parameterState",
                            List.of(
                                    "max_variable_mods_in_peptide",
                                    "ess-max_variable_mods_in_peptide-state",
                                    "adv-max_variable_mods_in_peptide-state")),
                    Map.entry(
                            "parameterLock",
                            List.of(
                                    "output_percolatorfile",
                                    "ess-output_percolatorfile-lock",
                                    "adv-output_percolatorfile-lock")),
                    Map.entry(
                            "parameterSecond",
                            List.of(
                                    "precursor_charge",
                                    "ess-precursor_charge-second",
                                    "adv-precursor_charge-second")),
                    Map.entry(
                            "parameterChoose",
                            List.of("peff_obo", "ess-peff_obo-choose", "adv-peff_obo-choose")),
                    Map.entry(
                            "variableModSerialised",
                            List.of(
                                    "variable_mod15",
                                    "ess-variable_mod15-serialised",
                                    "adv-variable_mod15-serialised")),
                    Map.entry(
                            "variableModUp",
                            List.of(
                                    "variable_mod15",
                                    "ess-variable_mod15-up",
                                    "adv-variable_mod15-up")),
                    Map.entry(
                            "variableModDown",
                            List.of(
                                    "variable_mod15",
                                    "ess-variable_mod15-down",
                                    "adv-variable_mod15-down")),
                    Map.entry(
                            "variableModRemove",
                            List.of(
                                    "variable_mod15",
                                    "ess-variable_mod15-remove",
                                    "adv-variable_mod15-remove")),
                    Map.entry(
                            "staticModName",
                            List.of(
                                    "add_K_lysine",
                                    "ess-add_K_lysine-name",
                                    "adv-add_K_lysine-name")));

    /** Every UiIds method taking a surface alone: Essentials, then Advanced. */
    private static final Map<String, List<String>> PER_SURFACE =
            Map.ofEntries(
                    Map.entry(
                            "variableModPreset", List.of("ess-varmod-preset", "adv-varmod-preset")),
                    Map.entry("variableModAdd", List.of("ess-varmod-add", "adv-varmod-add")),
                    Map.entry("variableModCross", List.of("ess-varmod-cross", "adv-varmod-cross")),
                    Map.entry(
                            "variableModStatus", List.of("ess-varmod-status", "adv-varmod-status")),
                    Map.entry("staticModTable", List.of("ess-static-mods", "adv-static-mods")));

    /** Each tuple part's control of {@code variable_mod01} on Essentials. */
    private static final Map<VariableModPart, String> VARIABLE_MOD_PART =
            Map.ofEntries(
                    Map.entry(VariableModPart.MASS, "ess-variable_mod01-part-mass"),
                    Map.entry(VariableModPart.RESIDUES, "ess-variable_mod01-part-residues"),
                    Map.entry(VariableModPart.BINARY_GROUP, "ess-variable_mod01-part-binary-group"),
                    Map.entry(
                            VariableModPart.MINIMUM_COUNT, "ess-variable_mod01-part-minimum-count"),
                    Map.entry(
                            VariableModPart.MAXIMUM_COUNT, "ess-variable_mod01-part-maximum-count"),
                    Map.entry(
                            VariableModPart.TERMINAL_DISTANCE,
                            "ess-variable_mod01-part-terminal-distance"),
                    Map.entry(VariableModPart.TERMINUS, "ess-variable_mod01-part-terminus"),
                    Map.entry(VariableModPart.REQUIRED, "ess-variable_mod01-part-required"),
                    Map.entry(VariableModPart.NEUTRAL_LOSS, "ess-variable_mod01-part-neutral-loss"),
                    Map.entry(
                            VariableModPart.SECOND_NEUTRAL_LOSS,
                            "ess-variable_mod01-part-second-neutral-loss"));

    /** Each terminal code's check box of {@code variable_mod01} on Essentials. */
    private static final Map<TerminalCode, String> VARIABLE_MOD_TERMINUS =
            Map.ofEntries(
                    Map.entry(TerminalCode.PEPTIDE_N, "ess-variable_mod01-terminus-peptide-n"),
                    Map.entry(TerminalCode.PEPTIDE_C, "ess-variable_mod01-terminus-peptide-c"),
                    Map.entry(TerminalCode.PROTEIN_N, "ess-variable_mod01-terminus-protein-n"),
                    Map.entry(TerminalCode.PROTEIN_C, "ess-variable_mod01-terminus-protein-c"));

    /** Each Essentials group's container. */
    private static final Map<EssentialsSection, String> ESSENTIALS_GROUP =
            Map.ofEntries(
                    Map.entry(EssentialsSection.INPUTS, "ess-group-inputs"),
                    Map.entry(EssentialsSection.SEARCH_PRESET, "ess-group-search-preset"),
                    Map.entry(EssentialsSection.PRECURSOR, "ess-group-precursor"),
                    Map.entry(EssentialsSection.FRAGMENT, "ess-group-fragment"),
                    Map.entry(EssentialsSection.DIGESTION, "ess-group-digestion"),
                    Map.entry(
                            EssentialsSection.STATIC_MODIFICATIONS,
                            "ess-group-static-modifications"),
                    Map.entry(
                            EssentialsSection.VARIABLE_MODIFICATIONS,
                            "ess-group-variable-modifications"),
                    Map.entry(EssentialsSection.DECOYS, "ess-group-decoys"),
                    Map.entry(EssentialsSection.EXECUTION, "ess-group-execution"),
                    Map.entry(EssentialsSection.OUTPUTS, "ess-group-outputs"));

    /** Each filter of the global parameter search, by its constant. */
    private static final Map<SearchFilter, String> SEARCH_FILTER =
            Map.ofEntries(
                    Map.entry(SearchFilter.MODIFIED, "param-search-filter-modified"),
                    Map.entry(SearchFilter.ERRORS, "param-search-filter-errors"),
                    Map.entry(SearchFilter.WARNINGS, "param-search-filter-warnings"),
                    Map.entry(SearchFilter.EXPERT, "param-search-filter-expert"),
                    Map.entry(SearchFilter.UNSUPPORTED, "param-search-filter-unsupported"));

    /**
     * Each Advanced category's five identifiers: the container, the switch, the reset, its
     * confirmation and its cancellation.
     */
    private static final Map<ParameterCategory, List<String>> ADVANCED_CATEGORY =
            Map.ofEntries(
                    Map.entry(
                            ParameterCategory.DATABASE_PEFF,
                            List.of(
                                    "adv-category-database_peff",
                                    "adv-category-database_peff-toggle",
                                    "adv-category-database_peff-reset",
                                    "adv-category-database_peff-reset-confirm",
                                    "adv-category-database_peff-reset-cancel")),
                    Map.entry(
                            ParameterCategory.CPU_EXECUTION,
                            List.of(
                                    "adv-category-cpu_execution",
                                    "adv-category-cpu_execution-toggle",
                                    "adv-category-cpu_execution-reset",
                                    "adv-category-cpu_execution-reset-confirm",
                                    "adv-category-cpu_execution-reset-cancel")),
                    Map.entry(
                            ParameterCategory.PRECURSOR_MASS,
                            List.of(
                                    "adv-category-precursor_mass",
                                    "adv-category-precursor_mass-toggle",
                                    "adv-category-precursor_mass-reset",
                                    "adv-category-precursor_mass-reset-confirm",
                                    "adv-category-precursor_mass-reset-cancel")),
                    Map.entry(
                            ParameterCategory.DIGESTION_ENZYMES,
                            List.of(
                                    "adv-category-digestion_enzymes",
                                    "adv-category-digestion_enzymes-toggle",
                                    "adv-category-digestion_enzymes-reset",
                                    "adv-category-digestion_enzymes-reset-confirm",
                                    "adv-category-digestion_enzymes-reset-cancel")),
                    Map.entry(
                            ParameterCategory.FRAGMENT_SCORING,
                            List.of(
                                    "adv-category-fragment_scoring",
                                    "adv-category-fragment_scoring-toggle",
                                    "adv-category-fragment_scoring-reset",
                                    "adv-category-fragment_scoring-reset-confirm",
                                    "adv-category-fragment_scoring-reset-cancel")),
                    Map.entry(
                            ParameterCategory.FRAGMENT_INDEX,
                            List.of(
                                    "adv-category-fragment_index",
                                    "adv-category-fragment_index-toggle",
                                    "adv-category-fragment_index-reset",
                                    "adv-category-fragment_index-reset-confirm",
                                    "adv-category-fragment_index-reset-cancel")),
                    Map.entry(
                            ParameterCategory.SPECTRUM_FILTERS,
                            List.of(
                                    "adv-category-spectrum_filters",
                                    "adv-category-spectrum_filters-toggle",
                                    "adv-category-spectrum_filters-reset",
                                    "adv-category-spectrum_filters-reset-confirm",
                                    "adv-category-spectrum_filters-reset-cancel")),
                    Map.entry(
                            ParameterCategory.SPECTRAL_PROCESSING,
                            List.of(
                                    "adv-category-spectral_processing",
                                    "adv-category-spectral_processing-toggle",
                                    "adv-category-spectral_processing-reset",
                                    "adv-category-spectral_processing-reset-confirm",
                                    "adv-category-spectral_processing-reset-cancel")),
                    Map.entry(
                            ParameterCategory.SEARCH_RANGES,
                            List.of(
                                    "adv-category-search_ranges",
                                    "adv-category-search_ranges-toggle",
                                    "adv-category-search_ranges-reset",
                                    "adv-category-search_ranges-reset-confirm",
                                    "adv-category-search_ranges-reset-cancel")),
                    Map.entry(
                            ParameterCategory.OUTPUT,
                            List.of(
                                    "adv-category-output",
                                    "adv-category-output-toggle",
                                    "adv-category-output-reset",
                                    "adv-category-output-reset-confirm",
                                    "adv-category-output-reset-cancel")),
                    Map.entry(
                            ParameterCategory.MS1_REALTIME,
                            List.of(
                                    "adv-category-ms1_realtime",
                                    "adv-category-ms1_realtime-toggle",
                                    "adv-category-ms1_realtime-reset",
                                    "adv-category-ms1_realtime-reset-confirm",
                                    "adv-category-ms1_realtime-reset-cancel")),
                    Map.entry(
                            ParameterCategory.STATIC_MODS,
                            List.of(
                                    "adv-category-static_mods",
                                    "adv-category-static_mods-toggle",
                                    "adv-category-static_mods-reset",
                                    "adv-category-static_mods-reset-confirm",
                                    "adv-category-static_mods-reset-cancel")),
                    Map.entry(
                            ParameterCategory.VARIABLE_MODS,
                            List.of(
                                    "adv-category-variable_mods",
                                    "adv-category-variable_mods-toggle",
                                    "adv-category-variable_mods-reset",
                                    "adv-category-variable_mods-reset-confirm",
                                    "adv-category-variable_mods-reset-cancel")),
                    Map.entry(
                            ParameterCategory.MISC,
                            List.of(
                                    "adv-category-misc",
                                    "adv-category-misc-toggle",
                                    "adv-category-misc-reset",
                                    "adv-category-misc-reset-confirm",
                                    "adv-category-misc-reset-cancel")));

    /**
     * The identifiers built from a position or a single character, each pinned for the argument
     * named in its key.
     */
    private static final Map<String, String> BY_POSITION_OR_CHARACTER =
            Map.ofEntries(
                    Map.entry("summaryEntry(0)", "param-summary-entry-0"),
                    Map.entry("summaryEntry(12)", "param-summary-entry-12"),
                    Map.entry("spectrum(0)", "ess-spectrum-0"),
                    Map.entry("spectrumRemove(3)", "ess-spectrum-3-remove"),
                    Map.entry(
                            "variableModResidue(ESSENTIALS, variable_mod01, M)",
                            "ess-variable_mod01-residue-M"),
                    Map.entry(
                            "variableModResidue(ADVANCED, variable_mod07, Y)",
                            "adv-variable_mod07-residue-Y"),
                    Map.entry("searchResult(0)", "param-search-result-0"),
                    Map.entry("searchResult(4)", "param-search-result-4"),
                    Map.entry("presetRow(0)", "ess-preset-row-0"),
                    Map.entry("presetRowCurrent(5)", "ess-preset-row-5-current"),
                    Map.entry("presetRowPreset(7)", "ess-preset-row-7-preset"),
                    Map.entry("fragmentRow(0)", "ess-fragment-row-0"),
                    Map.entry("fragmentRowCurrent(2)", "ess-fragment-row-2-current"),
                    Map.entry("fragmentRowPreset(1)", "ess-fragment-row-1-preset"),
                    Map.entry("expertLine(8)", "param-expert-line-8"),
                    Map.entry("expertDiagnostic(0)", "param-expert-diagnostic-0"),
                    Map.entry("expertUnknown(1)", "param-expert-unknown-1"),
                    Map.entry("expertUnknownRemove(1)", "param-expert-unknown-1-remove"),
                    Map.entry("migrationRow(2)", "param-migration-row-2"),
                    Map.entry("migrationRowState(2)", "param-migration-row-2-state"),
                    Map.entry("migrationRowAccept(2)", "param-migration-row-2-accept"),
                    Map.entry("migrationRowGoTo(2)", "param-migration-row-2-goto"),
                    Map.entry("enzymeRow(12)", "adv-enzyme-row-12"),
                    Map.entry("enzymeRowRemove(12)", "adv-enzyme-row-12-remove"));

    /**
     * Every {@code public static String} method of {@link UiIds}, by name, that the tables above or
     * the earlier ones pin. A method missing from this set fails {@link
     * #everyIdentifierMethodIsPinned()}: add its literals to a table, then its name here.
     */
    private static final Set<String> PINNED_METHODS =
            Set.of(
                    "sectionPane",
                    "sectionHeading",
                    "sectionDescription",
                    "sectionNote",
                    "navigationEntry",
                    "stepperStage",
                    "stepperStageName",
                    "stepperStageState",
                    "stepperArrow",
                    "stepperBranch",
                    "stepperBranchOrigin",
                    "consoleStageFilter",
                    "consoleSeverityFilter",
                    "toolRow",
                    "toolRowName",
                    "toolRowState",
                    "toolRowCapabilities",
                    "toolRowAdvisories",
                    "toolRowDownload",
                    "toolRowDiagnostic",
                    "toolRowPath",
                    "toolRowProgress",
                    "toolRowInstall",
                    "toolRowCancel",
                    "parameterControl",
                    "parameterLabel",
                    "parameterReset",
                    "parameterOrigin",
                    "parameterState",
                    "parameterLock",
                    "parameterSecond",
                    "parameterChoose",
                    "variableModSerialised",
                    "variableModUp",
                    "variableModDown",
                    "variableModRemove",
                    "variableModPreset",
                    "variableModAdd",
                    "variableModCross",
                    "variableModStatus",
                    "variableModPart",
                    "variableModTerminus",
                    "variableModResidue",
                    "essentialsGroup",
                    "advancedCategory",
                    "advancedCategoryToggle",
                    "advancedCategoryReset",
                    "advancedCategoryResetConfirm",
                    "advancedCategoryResetCancel",
                    "summaryEntry",
                    "spectrum",
                    "spectrumRemove",
                    "searchFilter",
                    "searchResult",
                    "presetRow",
                    "presetRowCurrent",
                    "presetRowPreset",
                    "fragmentRow",
                    "fragmentRowCurrent",
                    "fragmentRowPreset",
                    "staticModTable",
                    "staticModName",
                    "expertLine",
                    "expertDiagnostic",
                    "expertUnknown",
                    "expertUnknownRemove",
                    "migrationRow",
                    "migrationRowState",
                    "migrationRowAccept",
                    "migrationRowGoTo",
                    "enzymeRow",
                    "enzymeRowRemove");

    /**
     * The row key the Tool Manager's per-row identifiers are pinned for.
     *
     * <p>Typed out, and deliberately a real one: {@code ToolManagerViewModel} builds this key for
     * Percolator 3.07.1's first offered build, with the version's dots written as underscores
     * because a dot in an identifier is read by {@code Scene.lookup} as a style class. The ordinal
     * is there because two offers can legitimately share a tool and a version.
     */
    private static final String PINNED_TOOL_ROW_KEY = "percolator-3_07_1-1";

    /**
     * Every identifier one Tool Manager row carries, by the {@link UiIds} method that produces it.
     *
     * <p>The keys of this map are method names and are used to enumerate what has to be pinned; the
     * values are the identifiers and are typed out in full. {@code toolRowName} is written as
     * {@code "tool-row-percolator-3_07_1-1-name"} rather than as the row identifier with a suffix
     * appended, for the reason stated on this class: an expectation assembled from another
     * expectation is not a pin.
     */
    private static final Map<String, String> TOOL_ROW =
            Map.ofEntries(
                    Map.entry("toolRow", "tool-row-percolator-3_07_1-1"),
                    Map.entry("toolRowName", "tool-row-percolator-3_07_1-1-name"),
                    Map.entry("toolRowState", "tool-row-percolator-3_07_1-1-state"),
                    Map.entry("toolRowCapabilities", "tool-row-percolator-3_07_1-1-capabilities"),
                    Map.entry("toolRowAdvisories", "tool-row-percolator-3_07_1-1-advisories"),
                    Map.entry("toolRowDownload", "tool-row-percolator-3_07_1-1-download"),
                    Map.entry("toolRowDiagnostic", "tool-row-percolator-3_07_1-1-diagnostic"),
                    Map.entry("toolRowPath", "tool-row-percolator-3_07_1-1-path"),
                    Map.entry("toolRowProgress", "tool-row-percolator-3_07_1-1-progress"),
                    Map.entry("toolRowInstall", "tool-row-percolator-3_07_1-1-install"),
                    Map.entry("toolRowCancel", "tool-row-percolator-3_07_1-1-cancel"));

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
                    Map.entry(SectionId.TOOL_MANAGER, "section-tool-manager"));

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
                    Map.entry(SectionId.TOOL_MANAGER, "section-tool-manager-heading"));

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
                    Map.entry(SectionId.TOOL_MANAGER, "section-tool-manager-description"));

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
                    Map.entry(SectionId.TOOL_MANAGER, "section-tool-manager-note"));

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
                    Map.entry(SectionId.TOOL_MANAGER, "nav-tool-manager"));

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

    @Test
    @DisplayName("every identifier a Tool Manager row carries still has its pinned spelling")
    void toolRowIdentifiersAreExactlyTheseLiterals() throws ReflectiveOperationException {
        for (Method method : toolRowIdentifierMethodsOfUiIds()) {
            String pinned = TOOL_ROW.get(method.getName());
            assertTrue(
                    pinned != null,
                    () ->
                            "UiIds."
                                    + method.getName()
                                    + " is not pinned. "
                                    + adviceFor("UiIds." + method.getName()));
            assertPinned(
                    pinned,
                    method.invoke(null, PINNED_TOOL_ROW_KEY),
                    "UiIds." + method.getName() + "(\"" + PINNED_TOOL_ROW_KEY + "\")");
        }
    }

    @Test
    @DisplayName("a new Tool Manager row identifier fails until it is pinned")
    void everyToolRowIdentifierIsPinned() {
        Set<String> declared = new TreeSet<>();
        for (Method method : toolRowIdentifierMethodsOfUiIds()) {
            declared.add(method.getName());
        }
        Set<String> unpinned = new TreeSet<>(declared);
        unpinned.removeAll(TOOL_ROW.keySet());
        assertTrue(
                unpinned.isEmpty(),
                () ->
                        "UiIds builds Tool Manager row identifiers this table does not pin: "
                                + String.join(", ", unpinned)
                                + ". "
                                + adviceFor("each new row identifier"));
        Set<String> stale = new TreeSet<>(TOOL_ROW.keySet());
        stale.removeAll(declared);
        assertTrue(
                stale.isEmpty(),
                () ->
                        "this table pins row identifiers UiIds no longer builds: "
                                + String.join(", ", stale)
                                + ". A pin for a control that no longer exists hides how much of"
                                + " the surface is really covered.");
    }

    @Test
    @DisplayName(
            "a parameter of every value kind has its pinned control identifier on both surfaces")
    void parameterControlSchemeIsPinnedForEveryKind() {
        assertEquals(
                EnumSet.allOf(ValueKind.class),
                EnumSet.copyOf(PARAMETER_OF_EACH_KIND.keySet()),
                "every value kind is pinned. " + adviceFor("a new value kind's control"));
        for (Map.Entry<ValueKind, List<String>> pinned : PARAMETER_OF_EACH_KIND.entrySet()) {
            String name = pinned.getValue().get(0);
            assertPinned(
                    pinned.getValue().subList(1, 3),
                    List.of(
                            UiIds.parameterControl(UiIds.Surface.ESSENTIALS, name),
                            UiIds.parameterControl(UiIds.Surface.ADVANCED, name)),
                    "control of the " + pinned.getKey() + " parameter " + name);
        }
    }

    @Test
    @DisplayName("every per-parameter identifier still has its pinned spelling on both surfaces")
    void perParameterIdentifiersAreExactlyTheseLiterals() throws ReflectiveOperationException {
        for (Map.Entry<String, List<String>> pinned : PER_PARAMETER.entrySet()) {
            Method method =
                    UiIds.class.getMethod(pinned.getKey(), UiIds.Surface.class, String.class);
            String name = pinned.getValue().get(0);
            assertPinned(
                    pinned.getValue().subList(1, 3),
                    List.of(
                            method.invoke(null, UiIds.Surface.ESSENTIALS, name),
                            method.invoke(null, UiIds.Surface.ADVANCED, name)),
                    "UiIds." + pinned.getKey() + "(surface, \"" + name + "\")");
        }
        for (Map.Entry<String, List<String>> pinned : PER_SURFACE.entrySet()) {
            Method method = UiIds.class.getMethod(pinned.getKey(), UiIds.Surface.class);
            assertPinned(
                    pinned.getValue(),
                    List.of(
                            method.invoke(null, UiIds.Surface.ESSENTIALS),
                            method.invoke(null, UiIds.Surface.ADVANCED)),
                    "UiIds." + pinned.getKey() + "(surface)");
        }
        for (VariableModPart part : VariableModPart.values()) {
            assertPinned(
                    pinned(VARIABLE_MOD_PART, part, "variable-modification part " + part.name()),
                    UiIds.variableModPart(UiIds.Surface.ESSENTIALS, "variable_mod01", part),
                    "UiIds.variableModPart(ESSENTIALS, variable_mod01, " + part.name() + ")");
        }
        for (TerminalCode code : TerminalCode.values()) {
            assertPinned(
                    pinned(VARIABLE_MOD_TERMINUS, code, "terminal code " + code.name()),
                    UiIds.variableModTerminus(UiIds.Surface.ESSENTIALS, "variable_mod01", code),
                    "UiIds.variableModTerminus(ESSENTIALS, variable_mod01, " + code.name() + ")");
        }
    }

    @Test
    @DisplayName("every Essentials group and Advanced category identifier has its pinned spelling")
    void groupAndCategoryIdentifiersAreExactlyTheseLiterals() {
        for (EssentialsSection section : EssentialsSection.values()) {
            assertPinned(
                    pinned(ESSENTIALS_GROUP, section, "Essentials group " + section.name()),
                    UiIds.essentialsGroup(section),
                    "UiIds.essentialsGroup(" + section.name() + ")");
        }
        for (SearchFilter filter : SearchFilter.values()) {
            assertPinned(
                    pinned(SEARCH_FILTER, filter, "search filter " + filter.name()),
                    UiIds.searchFilter(filter),
                    "UiIds.searchFilter(" + filter.name() + ")");
        }
        for (ParameterCategory category : ParameterCategory.values()) {
            assertPinned(
                    pinned(ADVANCED_CATEGORY, category, "Advanced category " + category.name()),
                    List.of(
                            UiIds.advancedCategory(category),
                            UiIds.advancedCategoryToggle(category),
                            UiIds.advancedCategoryReset(category),
                            UiIds.advancedCategoryResetConfirm(category),
                            UiIds.advancedCategoryResetCancel(category)),
                    "the identifiers of Advanced category " + category.name());
        }
    }

    @Test
    @DisplayName("identifiers built from a position or a character have their pinned spelling")
    void positionAndCharacterIdentifiersAreExactlyTheseLiterals() {
        Map<String, String> actual = new LinkedHashMap<>();
        actual.put("summaryEntry(0)", UiIds.summaryEntry(0));
        actual.put("summaryEntry(12)", UiIds.summaryEntry(12));
        actual.put("spectrum(0)", UiIds.spectrum(0));
        actual.put("spectrumRemove(3)", UiIds.spectrumRemove(3));
        actual.put(
                "variableModResidue(ESSENTIALS, variable_mod01, M)",
                UiIds.variableModResidue(UiIds.Surface.ESSENTIALS, "variable_mod01", 'M'));
        actual.put(
                "variableModResidue(ADVANCED, variable_mod07, Y)",
                UiIds.variableModResidue(UiIds.Surface.ADVANCED, "variable_mod07", 'Y'));
        actual.put("searchResult(0)", UiIds.searchResult(0));
        actual.put("searchResult(4)", UiIds.searchResult(4));
        actual.put("presetRow(0)", UiIds.presetRow(0));
        actual.put("presetRowCurrent(5)", UiIds.presetRowCurrent(5));
        actual.put("presetRowPreset(7)", UiIds.presetRowPreset(7));
        actual.put("fragmentRow(0)", UiIds.fragmentRow(0));
        actual.put("fragmentRowCurrent(2)", UiIds.fragmentRowCurrent(2));
        actual.put("fragmentRowPreset(1)", UiIds.fragmentRowPreset(1));
        actual.put("expertLine(8)", UiIds.expertLine(8));
        actual.put("expertDiagnostic(0)", UiIds.expertDiagnostic(0));
        actual.put("expertUnknown(1)", UiIds.expertUnknown(1));
        actual.put("expertUnknownRemove(1)", UiIds.expertUnknownRemove(1));
        actual.put("migrationRow(2)", UiIds.migrationRow(2));
        actual.put("migrationRowState(2)", UiIds.migrationRowState(2));
        actual.put("migrationRowAccept(2)", UiIds.migrationRowAccept(2));
        actual.put("migrationRowGoTo(2)", UiIds.migrationRowGoTo(2));
        actual.put("enzymeRow(12)", UiIds.enzymeRow(12));
        actual.put("enzymeRowRemove(12)", UiIds.enzymeRowRemove(12));
        assertEquals(BY_POSITION_OR_CHARACTER.keySet(), actual.keySet());
        for (Map.Entry<String, String> entry : actual.entrySet()) {
            assertPinned(
                    BY_POSITION_OR_CHARACTER.get(entry.getKey()),
                    entry.getValue(),
                    "UiIds." + entry.getKey());
        }
    }

    @Test
    @DisplayName("a new UiIds identifier method fails until its literals are pinned")
    void everyIdentifierMethodIsPinned() {
        Set<String> declared = new TreeSet<>();
        for (Method method : UiIds.class.getDeclaredMethods()) {
            int modifiers = method.getModifiers();
            if (!method.isSynthetic()
                    && method.getReturnType() == String.class
                    && Modifier.isPublic(modifiers)
                    && Modifier.isStatic(modifiers)) {
                declared.add(method.getName());
            }
        }
        assertTrue(
                declared.size() >= PINNED_METHODS.size(),
                "reflection found "
                        + declared.size()
                        + " identifier methods; it has stopped working");
        Set<String> unpinned = new TreeSet<>(declared);
        unpinned.removeAll(PINNED_METHODS);
        assertTrue(
                unpinned.isEmpty(),
                () ->
                        "UiIds builds identifiers with methods no table here pins: "
                                + String.join(", ", unpinned)
                                + ". "
                                + adviceFor("each new identifier method"));
        Set<String> stale = new TreeSet<>(PINNED_METHODS);
        stale.removeAll(declared);
        assertTrue(
                stale.isEmpty(),
                () ->
                        "this class pins methods UiIds no longer declares: "
                                + String.join(", ", stale));
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
        assertEveryConstantIsPinned(
                "VARIABLE_MOD_PART", VARIABLE_MOD_PART.keySet(), VariableModPart.values());
        assertEveryConstantIsPinned(
                "VARIABLE_MOD_TERMINUS", VARIABLE_MOD_TERMINUS.keySet(), TerminalCode.values());
        assertEveryConstantIsPinned(
                "ESSENTIALS_GROUP", ESSENTIALS_GROUP.keySet(), EssentialsSection.values());
        assertEveryConstantIsPinned("SEARCH_FILTER", SEARCH_FILTER.keySet(), SearchFilter.values());
        assertEveryConstantIsPinned(
                "ADVANCED_CATEGORY", ADVANCED_CATEGORY.keySet(), ParameterCategory.values());
        assertEveryConstantIsPinned(
                "PARAMETER_OF_EACH_KIND", PARAMETER_OF_EACH_KIND.keySet(), ValueKind.values());
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
        for (Map.Entry<String, String> rowIdentifier : TOOL_ROW.entrySet()) {
            pins.add(new Pin("UiIds." + rowIdentifier.getKey(), rowIdentifier.getValue()));
        }
        for (Map.Entry<ValueKind, List<String>> kind : PARAMETER_OF_EACH_KIND.entrySet()) {
            for (String id : kind.getValue().subList(1, 3)) {
                pins.add(new Pin("control of the " + kind.getKey().name() + " parameter", id));
            }
        }
        for (Map.Entry<String, List<String>> method : PER_PARAMETER.entrySet()) {
            for (String id : method.getValue().subList(1, 3)) {
                pins.add(new Pin("UiIds." + method.getKey(), id));
            }
        }
        for (Map.Entry<String, List<String>> method : PER_SURFACE.entrySet()) {
            for (String id : method.getValue()) {
                pins.add(new Pin("UiIds." + method.getKey(), id));
            }
        }
        addPins(pins, "variable-modification part ", VARIABLE_MOD_PART);
        addPins(pins, "variable-modification terminal code ", VARIABLE_MOD_TERMINUS);
        addPins(pins, "Essentials group ", ESSENTIALS_GROUP);
        addPins(pins, "search filter ", SEARCH_FILTER);
        addListPins(pins, "Advanced category ", ADVANCED_CATEGORY);
        for (Map.Entry<String, String> entry : BY_POSITION_OR_CHARACTER.entrySet()) {
            pins.add(new Pin("UiIds." + entry.getKey(), entry.getValue()));
        }
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
     * The {@link UiIds} methods that build one Tool Manager row's identifiers.
     *
     * <p>Enumerated reflectively, for the same reason the constants are: a table a new identifier
     * can bypass rebuilds the hole this class exists to close, one method later.
     *
     * @return every public static {@code String} method taking one {@code String} whose name begins
     *     {@code toolRow}
     */
    private static List<Method> toolRowIdentifierMethodsOfUiIds() {
        List<Method> methods = new ArrayList<>();
        for (Method method : UiIds.class.getDeclaredMethods()) {
            int modifiers = method.getModifiers();
            if (!method.isSynthetic()
                    && method.getName().startsWith("toolRow")
                    && method.getReturnType() == String.class
                    && Modifier.isPublic(modifiers)
                    && Modifier.isStatic(modifiers)
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == String.class) {
                methods.add(method);
            }
        }
        assertTrue(
                methods.size() >= TOOL_ROW.size(),
                "reflection found "
                        + methods.size()
                        + " Tool Manager row identifier methods on UiIds but "
                        + TOOL_ROW.size()
                        + " are pinned: the reflective enumeration itself has stopped working, and"
                        + " an enumeration that finds nothing would pass every check above.");
        return methods;
    }

    /**
     * {@link UiIds}'s {@code public static final String} fields, found reflectively so that a
     * constant added without a pin is a failure rather than an omission nobody notices.
     *
     * @return every public static final {@code String} field it declares
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
