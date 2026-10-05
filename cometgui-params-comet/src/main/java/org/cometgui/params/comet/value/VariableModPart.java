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

package org.cometgui.params.comet.value;

import org.cometgui.params.comet.schema.VariableModField;

/**
 * One value a variable-modification editor sets on its own: a {@link VariableModField}, or one half
 * of a field the release lets hold a {@code a,b} comma pair.
 *
 * <p>The count and the neutral loss are each one field of the tuple but two controls of an editor:
 * {@code 2,4} is a minimum and a maximum, {@code 97.976896,79.966331} two losses. A part names one
 * of them, so that an editor sets the minimum count from its own text and never writes the comma
 * itself. Which parts a release has is its {@link
 * org.cometgui.params.comet.schema.VariableModLayout} ({@link VariableModSlots#parts()}): the
 * {@linkplain #optionalHalf() optional half} of a pair is a part only where the layout accepts the
 * pair.
 */
public enum VariableModPart {

    /** The mass difference. */
    MASS(VariableModField.MASS, false, "mass difference"),

    /** The residue token: residue letters and terminal codes. */
    RESIDUES(VariableModField.RESIDUES, false, "residues"),

    /** {@code 0} for a variable modification, any other number a binary group. */
    BINARY_GROUP(VariableModField.BINARY_GROUP, false, "binary group"),

    /** The minimum count per peptide, the first of a {@code min,max} pair; empty for none. */
    MINIMUM_COUNT(VariableModField.COUNT, true, "minimum count per peptide"),

    /** The maximum count per peptide. */
    MAXIMUM_COUNT(VariableModField.COUNT, false, "maximum count per peptide"),

    /** The distance from a terminus the modification is restricted to. */
    TERMINAL_DISTANCE(VariableModField.TERMINAL_DISTANCE, false, "terminal distance"),

    /** Which terminus the distance counts from. */
    TERMINUS(VariableModField.TERMINUS, false, "terminus"),

    /** Optional, required or exclusive. */
    REQUIRED(VariableModField.REQUIRED, false, "required, optional or exclusive"),

    /** The fragment neutral loss, {@code 0.0} for none. */
    NEUTRAL_LOSS(VariableModField.NEUTRAL_LOSS, false, "neutral loss"),

    /** A second fragment neutral loss, the second of a pair; empty for none. */
    SECOND_NEUTRAL_LOSS(VariableModField.NEUTRAL_LOSS, true, "second neutral loss");

    private final VariableModField field;

    private final boolean optionalHalf;

    private final String label;

    VariableModPart(VariableModField field, boolean optionalHalf, String label) {
        this.field = field;
        this.optionalHalf = optionalHalf;
        this.label = label;
    }

    /**
     * The tuple field the part is written in.
     *
     * @return the field
     */
    public VariableModField field() {
        return field;
    }

    /**
     * Whether the part is the half of a comma pair that may be left empty: the minimum count and
     * the second neutral loss. Such a part exists only where the release's layout accepts a pair in
     * its field.
     *
     * @return {@code true} for {@link #MINIMUM_COUNT} and {@link #SECOND_NEUTRAL_LOSS}
     */
    public boolean optionalHalf() {
        return optionalHalf;
    }

    /**
     * What the part is called in a label and a diagnostic.
     *
     * @return for example {@code minimum count per peptide}
     */
    public String label() {
        return label;
    }
}
