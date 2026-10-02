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

/**
 * The fields a variable-modification tuple can hold, by meaning rather than by position.
 *
 * <p>Which of these a Comet version's tuple has, and in what order, is not this enum's business: it
 * is the version's {@link VariableModLayout}, read from the curated metadata. This enum fixes only
 * what each field <em>is</em> -- the kind of text it holds and whether Comet could ever accept a
 * comma pair in it -- so that a layout naming a field can be checked for sense. Meanings are those
 * of Comet's {@code variable_mod01} documentation and of {@code LoadParameters} in {@code
 * Comet.cpp} at tag {@code v2026.02.2}; {@code docs/developer/comet_parameter_schema.rst} cites
 * each.
 */
public enum VariableModField {

    /** The modification's mass difference, a decimal such as {@code 15.9949}. */
    MASS("mass difference", Kind.DECIMAL, false),

    /**
     * The residues it applies to, as one token of residue letters, with {@code n} and {@code c} for
     * the N- and C-terminus, such as {@code STY} or {@code nK}.
     */
    RESIDUES("residues", Kind.RESIDUES, false),

    /** {@code 0} for a variable modification; any other integer names a binary group. */
    BINARY_GROUP("binary group", Kind.INTEGER, false),

    /** The maximum count per peptide, such as {@code 3}, or a {@code min,max} pair. */
    COUNT("count per peptide", Kind.INTEGER, true),

    /** The distance from the terminus the modification is restricted to; {@code -1} for none. */
    TERMINAL_DISTANCE("terminal distance", Kind.INTEGER, false),

    /** Which terminus that distance counts from: protein N or C, peptide N or C. */
    TERMINUS("terminus", Kind.INTEGER, false),

    /** {@code 0} optional, {@code 1} required, {@code -1} exclusive. */
    REQUIRED("required", Kind.INTEGER, false),

    /** The fragment neutral loss, {@code 0.0} for none, or a pair of two losses. */
    NEUTRAL_LOSS("neutral loss", Kind.DECIMAL, true);

    /** The kind of text one value of a field holds. */
    public enum Kind {

        /** A decimal number, such as {@code 15.9949} or {@code -17.026549}. */
        DECIMAL,

        /** A whole number, such as {@code 3} or {@code -1}. */
        INTEGER,

        /** A token of residue letters and terminus markers, such as {@code nK}. */
        RESIDUES
    }

    private final String label;

    private final Kind kind;

    private final boolean pairable;

    VariableModField(String label, Kind kind, boolean pairable) {
        this.label = label;
        this.kind = kind;
        this.pairable = pairable;
    }

    /**
     * What the field is called in a diagnostic.
     *
     * @return a short lower-case name, such as {@code neutral loss}
     */
    public String label() {
        return label;
    }

    /**
     * The kind of text each value of the field holds.
     *
     * @return the kind
     */
    public Kind kind() {
        return kind;
    }

    /**
     * Whether any Comet release accepts a comma pair in this field. A layout may let a pairable
     * field take a pair or not, according to its version; it may never give a pair to a field that
     * is not pairable.
     *
     * @return {@code true} for {@link #COUNT} and {@link #NEUTRAL_LOSS}
     */
    public boolean pairable() {
        return pairable;
    }
}
