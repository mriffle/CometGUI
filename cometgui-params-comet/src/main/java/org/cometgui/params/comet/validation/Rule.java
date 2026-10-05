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

package org.cometgui.params.comet.validation;

import java.util.Objects;
import java.util.Optional;
import org.cometgui.params.comet.schema.ValidatorId;

/**
 * Every rule the validators apply, each with a stable identifier and a severity.
 *
 * <p>Most rules have a fixed severity. A <em>version-scoped</em> rule does not: whether what it
 * checks is an error, a warning or nothing at all differs between Comet releases, so each release's
 * version record states it ({@link org.cometgui.params.comet.schema.CometVersionRecord
 * #ruleSeverities()}, decision C-2 of the Comet 2026.03.0 intake), and a finding takes the severity
 * of the version its model carries. Every curated release must state every version-scoped rule; the
 * validator refuses a record that does not, or that states one for a rule it does not have or whose
 * severity is fixed.
 *
 * <p>The identifier is what a test, the editor and the documentation's rule catalogue refer to; it
 * never changes once published. A rule that belongs to a metadata {@link ValidatorId} names it as
 * its family, so that "the generic ordering rule never fires for the precursor pair" is a question
 * a test can ask of a report. {@code docs/developer/comet_parameter_schema.rst} lists every rule
 * with what it checks and the Comet source it encodes.
 */
public enum Rule {

    /** The value is not one of the parameter's labelled choices. */
    CHOICE_NOT_LISTED("choice.not_listed", Severity.ERROR, ValidatorId.CHOICE),

    /** A number is below the parameter's curated minimum. */
    BELOW_MINIMUM("bounds.below_minimum", Severity.ERROR, null),

    /** A number is above the parameter's curated maximum. */
    ABOVE_MAXIMUM("bounds.above_maximum", Severity.ERROR, null),

    /** A path the metadata does not allow to be empty is empty. */
    PATH_EMPTY("path.empty", Severity.ERROR, ValidatorId.PATH),

    /** A path holds a NUL character, which ends a C string. */
    PATH_NUL("path.nul_character", Severity.ERROR, ValidatorId.PATH),

    /** A path is longer than Comet's file-name buffer. */
    PATH_TOO_LONG("path.too_long", Severity.ERROR, ValidatorId.PATH),

    /** A text Comet reads as one white-space delimited token holds white space. */
    TEXT_NOT_ONE_TOKEN("text.not_one_token", Severity.ERROR, null),

    /** A text Comet reads as one token is longer than Comet reads. */
    TEXT_TOO_LONG("text.too_long", Severity.ERROR, null),

    /** The generic ordering rule: the first of two values is greater than the second. */
    RANGE_REVERSED("ordered_range.reversed", Severity.ERROR, ValidatorId.ORDERED_RANGE),

    /** A two-value range is switched off by its first value, so its second has no effect. */
    RANGE_SECOND_IGNORED(
            "ordered_range.second_ignored", Severity.WARNING, ValidatorId.ORDERED_RANGE),

    /** {@code R-PARAM-04}: the precursor tolerance's lower bound is above its upper bound. */
    PAIR_REVERSED(
            "signed_tolerance_pair.reversed", Severity.ERROR, ValidatorId.SIGNED_TOLERANCE_PAIR),

    /** {@code R-PARAM-04}: the precursor window does not contain zero. */
    PAIR_SAME_SIGNED(
            "signed_tolerance_pair.same_signed",
            Severity.WARNING,
            ValidatorId.SIGNED_TOLERANCE_PAIR),

    /** {@code R-PARAM-04}: the precursor window contains zero but is not symmetric about it. */
    PAIR_ASYMMETRIC(
            "signed_tolerance_pair.asymmetric",
            Severity.WARNING,
            ValidatorId.SIGNED_TOLERANCE_PAIR),

    /** An enzyme number names no row of the {@code [COMET_ENZYME_INFO]} table. */
    ENZYME_NOT_IN_TABLE("enzyme_in_table.missing", Severity.ERROR, ValidatorId.ENZYME_IN_TABLE),

    /** A referenced enzyme row has a field longer than Comet reads, so Comet misreads it. */
    ENZYME_ROW_UNREADABLE("enzyme_table.row_unreadable", Severity.ERROR, null),

    /** An enzyme row nothing references has a field longer than Comet reads. */
    ENZYME_ROW_UNREADABLE_UNUSED("enzyme_table.unused_row_unreadable", Severity.WARNING, null),

    /** The enzyme rows are not numbered 0, 1, 2 ... as Comet's documentation asks. */
    ENZYME_NUMBERING("enzyme_table.numbering", Severity.WARNING, null),

    /** A variable-modification residue token is longer than Comet reads. */
    VARMOD_RESIDUES_TOO_LONG(
            "variable_mod_tuple.residues_too_long", Severity.ERROR, ValidatorId.VARIABLE_MOD_TUPLE),

    /** An active slot's count is negative. */
    VARMOD_COUNT_NEGATIVE(
            "variable_mod_tuple.count_negative", Severity.ERROR, ValidatorId.VARIABLE_MOD_TUPLE),

    /** An active slot's minimum count is above its maximum. */
    VARMOD_COUNT_REVERSED(
            "variable_mod_tuple.count_reversed", Severity.ERROR, ValidatorId.VARIABLE_MOD_TUPLE),

    /** An active slot's maximum count is zero, so the modification is never placed. */
    VARMOD_COUNT_ZERO(
            "variable_mod_tuple.count_zero", Severity.WARNING, ValidatorId.VARIABLE_MOD_TUPLE),

    /**
     * A residue token holds a character the model's Comet release does not accept, such as the
     * protein-terminus codes {@code ^} and {@code $} in a release older than 2026.03.0.
     */
    VARMOD_RESIDUE_NOT_IN_RELEASE(
            "variable_mod_tuple.residue_not_in_release",
            Severity.ERROR,
            ValidatorId.VARIABLE_MOD_TUPLE),

    /**
     * An active slot's terminal distance is below -2, which Comet does not document.
     * Version-scoped: Comet 2026.03.0 refuses it, earlier releases treat it as -1.
     */
    VARMOD_DISTANCE_UNDOCUMENTED(
            "variable_mod_tuple.distance_undocumented", ValidatorId.VARIABLE_MOD_TUPLE),

    /** An active slot constrains a distance from a terminus Comet does not know. */
    VARMOD_TERMINUS_UNDOCUMENTED(
            "variable_mod_tuple.terminus_undocumented",
            Severity.ERROR,
            ValidatorId.VARIABLE_MOD_TUPLE),

    /** An active slot's requirement is a value Comet does not document. */
    VARMOD_REQUIREMENT_UNDOCUMENTED(
            "variable_mod_tuple.requirement_undocumented",
            Severity.WARNING,
            ValidatorId.VARIABLE_MOD_TUPLE),

    /** An active slot's binary group is negative, which Comet does not document. */
    VARMOD_BINARY_GROUP_NEGATIVE(
            "variable_mod_tuple.binary_group_negative",
            Severity.WARNING,
            ValidatorId.VARIABLE_MOD_TUPLE),

    /** {@code R-PARAM-10}: a variable modification is required and no slot is active. */
    VARMODS_REQUIRED_WITHOUT_SLOT("variable_mods.required_without_slot", Severity.ERROR, null),

    /** {@code R-PARAM-10}: a slot's minimum count exceeds the per-peptide limit. */
    VARMODS_MINIMUM_ABOVE_LIMIT("variable_mods.minimum_above_limit", Severity.ERROR, null),

    /** {@code R-PARAM-10}: a modification is required while the per-peptide limit is zero. */
    VARMODS_REQUIRED_BUT_NONE_ALLOWED(
            "variable_mods.required_but_none_allowed", Severity.ERROR, null),

    /** {@code R-PARAM-10}: slots are active while the per-peptide limit is zero. */
    VARMODS_NONE_ALLOWED("variable_mods.none_allowed", Severity.WARNING, null),

    /**
     * AScorePro is on while a slot above {@code variable_mod09} is active after Comet merges slots
     * identical to a lower one: AScorePro cannot tell a two-digit slot number from two one-digit
     * ones.
     */
    VARMODS_ASCOREPRO_SLOT("variable_mods.ascorepro_slot_unsupported", Severity.ERROR, null),

    /**
     * {@code index_search_type} asks for an index type while {@code database_name} does not name an
     * {@code .idx} file, so nothing uses the value. Version-scoped: Comet 2026.03.0 warns,
     * 2026.02.2 is silent.
     */
    INDEX_SEARCH_TYPE_IGNORED("index_search_type.ignored_without_idx", null),

    /** {@code R-CMT-01}: an output a downstream stage needs is switched off. */
    WORKFLOW_OUTPUT_OFF(
            "workflow_enforced.output_off", Severity.ERROR, ValidatorId.WORKFLOW_ENFORCED),

    /** {@code R-DEC-01}: the decoy prefix is empty. */
    DECOY_PREFIX_EMPTY("decoy.prefix_empty", Severity.ERROR, null),

    /**
     * {@code R-PARAM-07}: an imported parameter the schema does not model, kept and written back.
     */
    UNKNOWN_PARAMETER("unknown_parameter.imported", Severity.WARNING, null),

    /** A parameter the schema models for other Comet versions but not the selected one. */
    UNAVAILABLE_IN_VERSION("version.parameter_unavailable", Severity.ERROR, null),

    /**
     * A warning of the parse that produced the model, such as a {@code # comet_version} marker
     * naming another version ({@code R-PARAM-06}), carried into the report unchanged.
     */
    IMPORT_DIAGNOSTIC("import.diagnostic", Severity.WARNING, null),

    /**
     * {@code R-PARAM-13}: a schema migration could not carry a value with its meaning, substituted
     * the target release's default, and the scientist has not yet resolved the entry. {@link
     * CometValidator} never reports it, because it needs the migration's report: {@code
     * org.cometgui.params.comet.migration.MigrationReview#validate} adds it to the validator's
     * findings, the one place the two meet.
     */
    MIGRATION_NEEDS_ATTENTION("migration.needs_attention", Severity.ERROR, null);

    private final String id;

    private final Severity severity;

    private final ValidatorId family;

    /** A rule with a fixed severity. */
    Rule(String id, Severity severity, ValidatorId family) {
        this.id = id;
        this.severity = Objects.requireNonNull(severity, "severity");
        this.family = family;
    }

    /** A version-scoped rule: each Comet release's version record states its severity. */
    Rule(String id, ValidatorId family) {
        this.id = id;
        this.severity = null;
        this.family = family;
    }

    /**
     * The stable identifier.
     *
     * @return for example {@code signed_tolerance_pair.asymmetric}
     */
    public String id() {
        return id;
    }

    /**
     * The severity every finding of this rule has, whatever the Comet version.
     *
     * @return the severity, or empty for a version-scoped rule
     */
    public Optional<Severity> fixedSeverity() {
        return Optional.ofNullable(severity);
    }

    /**
     * Whether each Comet release's version record states this rule's severity.
     *
     * @return {@code true} for a version-scoped rule
     */
    public boolean isVersionScoped() {
        return severity == null;
    }

    /**
     * The rule with a stable identifier.
     *
     * @param id the identifier
     * @return the rule, or empty if no rule has it
     */
    public static Optional<Rule> byId(String id) {
        for (Rule rule : values()) {
            if (rule.id.equals(id)) {
                return Optional.of(rule);
            }
        }
        return Optional.empty();
    }

    /**
     * The metadata validator this rule implements, if it implements one.
     *
     * @return the validator, or empty for the bounds and cross-field rules
     */
    public Optional<ValidatorId> family() {
        return Optional.ofNullable(family);
    }
}
