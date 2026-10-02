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

package org.cometgui.params.comet.schema;

import java.util.EnumSet;
import java.util.Set;

/**
 * The structural kind of a Comet parameter's value, as the specification's <em>Structured parameter
 * kinds</em> names them.
 *
 * <p>Each kind needs its own parser, writer, validator and control, which is why the kinds are
 * distinct even where two serialise alike: a {@link #TOLERANCE_PAIR_MEMBER} is a decimal, but it is
 * validated by {@code R-PARAM-04}'s signed-pair rule and never by the generic ordering rule. The
 * codecs for {@link #VARIABLE_MOD_TUPLE}, the enzyme table behind {@link #ENZYME_REFERENCE} and the
 * tolerance pair belong to the structured value types; this enum only names the kinds. Whether an
 * empty value is legal is a {@link SerializationRule}, not a kind.
 */
public enum ValueKind {

    /** A whole number, such as {@code allowed_missed_cleavage = 2}. */
    INTEGER(EnumSet.of(SerializationRule.SINGLE_VALUE)),

    /** A decimal number, such as {@code fragment_bin_tol = 0.02}. */
    DECIMAL(EnumSet.of(SerializationRule.SINGLE_VALUE)),

    /** Free text, such as {@code decoy_prefix = DECOY_}. */
    STRING(EnumSet.of(SerializationRule.SINGLE_VALUE, SerializationRule.EMPTY_ALLOWED)),

    /** {@code 0} or {@code 1} meaning off or on, such as {@code clip_nterm_methionine}. */
    BOOLEAN_FLAG(EnumSet.of(SerializationRule.SINGLE_VALUE)),

    /** An integer code with labelled meanings, such as {@code num_enzyme_termini = 2}. */
    INTEGER_ENUM(EnumSet.of(SerializationRule.SINGLE_VALUE)),

    /** A token from a fixed set, such as {@code activation_method = ALL}. */
    STRING_ENUM(EnumSet.of(SerializationRule.SINGLE_VALUE)),

    /** A file-system path, such as {@code database_name}. */
    FILE_PATH(EnumSet.of(SerializationRule.SINGLE_VALUE, SerializationRule.EMPTY_ALLOWED)),

    /** Two whole numbers on one line, such as {@code peptide_length_range = 5 50}. */
    INTEGER_RANGE(EnumSet.of(SerializationRule.TWO_VALUES)),

    /** Two decimals on one line, such as {@code digest_mass_range = 600.0 5000.0}. */
    DECIMAL_RANGE(EnumSet.of(SerializationRule.TWO_VALUES)),

    /** Zero or more decimals on one line, such as {@code mass_offsets}. */
    DECIMAL_LIST(EnumSet.of(SerializationRule.VALUE_LIST)),

    /**
     * One member of the signed precursor tolerance pair, {@code peptide_mass_tolerance_lower} or
     * {@code _upper}; the lower bound is normally negative ({@code R-PARAM-04}).
     */
    TOLERANCE_PAIR_MEMBER(EnumSet.of(SerializationRule.SINGLE_VALUE)),

    /** A variable-modification tuple, {@code variable_mod01} to {@code variable_mod15}. */
    VARIABLE_MOD_TUPLE(EnumSet.of(SerializationRule.TUPLE)),

    /** The number of a row in the {@code [COMET_ENZYME_INFO]} table. */
    ENZYME_REFERENCE(EnumSet.of(SerializationRule.SINGLE_VALUE)),

    /** One member of the ion-series family {@code use_A_ions} .. {@code use_Z1_ions}. */
    ION_SERIES_FLAG(EnumSet.of(SerializationRule.SINGLE_VALUE));

    private final Set<SerializationRule> serializations;

    ValueKind(Set<SerializationRule> serializations) {
        this.serializations = serializations;
    }

    /**
     * Whether a parameter of this kind may be written under a rule.
     *
     * @param rule the rule
     * @return {@code true} if the combination is meaningful
     */
    public boolean allows(SerializationRule rule) {
        return serializations.contains(rule);
    }

    /**
     * Whether the value is one of a labelled set of choices.
     *
     * @return {@code true} for {@link #INTEGER_ENUM} and {@link #STRING_ENUM}
     */
    public boolean isEnumeration() {
        return this == INTEGER_ENUM || this == STRING_ENUM;
    }

    /**
     * Whether the value is made of numbers that a minimum and maximum can bound.
     *
     * @return {@code true} for the integer, decimal, range, list and tolerance kinds
     */
    public boolean isBoundedNumeric() {
        return switch (this) {
            case INTEGER,
                    DECIMAL,
                    INTEGER_RANGE,
                    DECIMAL_RANGE,
                    DECIMAL_LIST,
                    TOLERANCE_PAIR_MEMBER ->
                    true;
            default -> false;
        };
    }

    /**
     * Whether every number in the value must be whole.
     *
     * @return {@code true} for {@link #INTEGER} and {@link #INTEGER_RANGE}
     */
    public boolean isWholeNumbers() {
        return this == INTEGER || this == INTEGER_RANGE;
    }
}
