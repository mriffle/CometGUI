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
 * How a parameter's value is written after {@code name = }.
 *
 * <p>The specification lists the <em>empty-valued parameter</em> among the structural kinds. It is
 * modelled here, as {@link #EMPTY_ALLOWED}, rather than as a {@link ValueKind}, because it is
 * orthogonal to the kind: three of Comet 2026.02.2's four empty-by-default parameters ({@code
 * peff_obo}, {@code compoundmods_file}, {@code protein_modslist_file}) are also paths, and the
 * fourth ({@code pinfile_protein_delimiter}) is free text. What the rule records is the part that
 * matters to a parser and a writer: an empty value is a value, written as {@code name =}, and must
 * round-trip as empty rather than as absent.
 */
public enum SerializationRule {

    /** One non-empty token or text. */
    SINGLE_VALUE,

    /** One text that may be empty; empty is meaningful and is written as {@code name =}. */
    EMPTY_ALLOWED,

    /** Two whitespace-separated values on one line, as in {@code peptide_length_range = 5 50}. */
    TWO_VALUES,

    /** Zero or more whitespace-separated values, as in {@code mass_offsets}. */
    VALUE_LIST,

    /**
     * A fixed-layout tuple whose fields the version's schema defines, as in {@code variable_mod01}.
     * The codec belongs to the structured value types, not to this package.
     */
    TUPLE;

    /**
     * Whether an empty value is legal under this rule.
     *
     * @return {@code true} for {@link #EMPTY_ALLOWED} and {@link #VALUE_LIST}
     */
    public boolean allowsEmpty() {
        return this == EMPTY_ALLOWED || this == VALUE_LIST;
    }
}
