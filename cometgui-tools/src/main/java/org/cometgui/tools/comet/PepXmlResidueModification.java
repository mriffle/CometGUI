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
import java.util.Optional;

/**
 * One {@code mod_aminoacid_mass} of a search hit: a modified residue, as Comet states it.
 *
 * <p>Comet writes one for every residue that carries a static modification, a variable one, or
 * both, and for no other: {@code mass} is the residue's modified mass, {@code static} the static
 * delta when the residue has one, {@code variable} the variable delta when it has one, and {@code
 * source} ({@code "param"} for a modification declared in the parameter file, {@code "peff"} with
 * an {@code id} for one read from a PEFF database) exactly when it has a variable one. Measured on
 * Comet 2026.03.0 with a variable oxidation on cysteine over the static carbamidomethyl: {@code
 * position="2" mass="176.025564" static="57.021464" variable="15.994915" source="param"}.
 *
 * @param position the residue's 1-based position in the peptide
 * @param residue the residue at that position, from the hit's {@code peptide}
 * @param mass the modified residue's mass, {@code mass}
 * @param staticDelta the static delta, {@code static}, when present
 * @param variableDelta the variable delta, {@code variable}, when present
 * @param source {@code source}, when present: {@code "param"} or {@code "peff"}
 * @param peffId {@code id}, present exactly when the source is {@code "peff"}
 */
public record PepXmlResidueModification(
        int position,
        char residue,
        PepXmlDecimal mass,
        Optional<PepXmlDecimal> staticDelta,
        Optional<PepXmlDecimal> variableDelta,
        Optional<String> source,
        Optional<String> peffId) {

    /**
     * Validates the modification.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public PepXmlResidueModification {
        Objects.requireNonNull(mass, "mass");
        Objects.requireNonNull(staticDelta, "staticDelta");
        Objects.requireNonNull(variableDelta, "variableDelta");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(peffId, "peffId");
    }
}
