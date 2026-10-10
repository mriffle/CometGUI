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

package org.cometgui.tools.comet;

import java.util.Objects;

/**
 * One {@code search_summary/aminoacid_modification} declaration, as Comet writes it: a residue, its
 * delta and its modified mass, and whether the modification is variable.
 *
 * <p>Comet declares each variable modification once per residue it names ({@code variable="Y"},
 * {@code binary="Y"} for a binary one) and each non-zero static {@code add_<X>_...} once ({@code
 * variable="N"}); a compound-mass {@code J} declaration has the same shape.
 *
 * @param aminoAcid the residue, one character
 * @param massDiff the delta, {@code massdiff}
 * @param mass the residue's mass with the modification, {@code mass}
 * @param variable {@code variable="Y"}; {@code false} for {@code variable="N"}, a fixed
 *     modification
 * @param binary {@code binary="Y"}; {@code false} when the attribute is absent or {@code "N"}
 */
public record PepXmlAminoAcidModification(
        String aminoAcid,
        PepXmlDecimal massDiff,
        PepXmlDecimal mass,
        boolean variable,
        boolean binary) {

    /**
     * Validates the declaration.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public PepXmlAminoAcidModification {
        Objects.requireNonNull(aminoAcid, "aminoAcid");
        Objects.requireNonNull(massDiff, "massDiff");
        Objects.requireNonNull(mass, "mass");
    }
}
