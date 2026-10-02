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

import java.util.Objects;
import java.util.Optional;

/**
 * The named rules the metadata attaches to a parameter, beyond what its {@link ValueKind}, bounds
 * and choices already imply. The rules themselves are implemented by the validation package; this
 * enum is the closed vocabulary the metadata may use, so that a misspelt rule is a load failure
 * rather than a rule that silently never runs.
 */
public enum ValidatorId {

    /** The value is one of the parameter's choices. */
    CHOICE("choice"),

    /** A path has a legal form and, where required, exists and is readable. */
    PATH("path"),

    /** The first of two values is not greater than the second. */
    ORDERED_RANGE("ordered_range"),

    /**
     * {@code R-PARAM-04}: {@code lower <= 0 <= upper}, a warning and not an error for a deliberate
     * asymmetric or same-signed window. Never combined with {@link #ORDERED_RANGE}.
     */
    SIGNED_TOLERANCE_PAIR("signed_tolerance_pair"),

    /** The enzyme number exists in the table the file carries. */
    ENZYME_IN_TABLE("enzyme_in_table"),

    /** A variable-modification tuple matches the version's field layout. */
    VARIABLE_MOD_TUPLE("variable_mod_tuple"),

    /** The workflow forces this parameter's value ({@code R-CMT-01}). */
    WORKFLOW_ENFORCED("workflow_enforced");

    private final String id;

    ValidatorId(String id) {
        this.id = id;
    }

    /**
     * The identifier the metadata file uses.
     *
     * @return the identifier
     */
    public String id() {
        return id;
    }

    /**
     * Resolves a metadata identifier, exactly.
     *
     * @param id the identifier
     * @return the validator, or empty if none has that identifier
     */
    public static Optional<ValidatorId> fromId(String id) {
        Objects.requireNonNull(id, "id");
        for (ValidatorId validator : values()) {
            if (validator.id.equals(id)) {
                return Optional.of(validator);
            }
        }
        return Optional.empty();
    }
}
