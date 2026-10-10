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
 * One {@code search_summary/terminal_modification} declaration, as Comet writes it.
 *
 * <p><strong>{@code mass} is the modified terminal group's mass, not the delta</strong>, and it is
 * the same quantity a search hit's {@code mod_nterm_mass} or {@code mod_cterm_mass} carries:
 * measured on Comet 2026.03.0, an N-terminal {@code massdiff="42.010565"} is declared with {@code
 * mass="43.018390"} (the delta plus a hydrogen) and the hits it applies to carry {@code
 * mod_nterm_mass="43.018390"}. A variable terminal declaration's {@code mass} includes the
 * corresponding static peptide-terminal modification; a hit on which a static <em>protein</em>
 * -terminal modification and a variable one both apply carries their sum, which matches no single
 * declaration (measured: {@code 71.049690} with declarations {@code 43.018390} and {@code
 * 29.039125}).
 *
 * @param terminus the terminus
 * @param massDiff the delta, {@code massdiff}
 * @param mass the modified terminal group's mass, {@code mass}
 * @param variable {@code variable="Y"}; {@code false} for a static one
 * @param proteinTerminus {@code protein_terminus="Y"}: the modification applies only at a protein
 *     terminus
 */
public record PepXmlTerminalModification(
        PepXmlTerminus terminus,
        PepXmlDecimal massDiff,
        PepXmlDecimal mass,
        boolean variable,
        boolean proteinTerminus) {

    /**
     * Validates the declaration.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public PepXmlTerminalModification {
        Objects.requireNonNull(terminus, "terminus");
        Objects.requireNonNull(massDiff, "massDiff");
        Objects.requireNonNull(mass, "mass");
    }
}
