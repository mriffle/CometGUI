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
 * What one static-modification parameter adds its mass to: a residue, or a peptide or protein
 * terminus -- the row key of a residue/terminus-oriented static modification table.
 *
 * <p>Comet names each parameter after its target, and this reads that convention so an editor does
 * not: {@code add_C_cysteine} is the residue {@code C} (cysteine), {@code add_Nterm_peptide} the
 * peptide N-terminus ({@code add_X_name} and {@code add_Nterm_peptide} in Comet's -q output for
 * every curated release).
 *
 * @param parameter the parameter, such as {@code add_C_cysteine}
 * @param residue the residue letter, or empty for a terminus
 * @param words the target in words, such as {@code cysteine (C)} or {@code peptide N-terminus}
 */
public record StaticModTarget(String parameter, Optional<Character> residue, String words) {

    private static final String PREFIX = "add_";

    private static final String TERM = "term_";

    /** Validates presence. */
    public StaticModTarget {
        Objects.requireNonNull(parameter, "parameter");
        Objects.requireNonNull(residue, "residue");
        Objects.requireNonNull(words, "words");
    }

    /**
     * The target of a static-modification parameter.
     *
     * @param definition a parameter definition
     * @return the target, or empty if the parameter is not a static modification: not in {@link
     *     ParameterCategory#STATIC_MODS}, not a decimal, or not named {@code add_<X>_<name>} or
     *     {@code add_<N|C>term_<peptide|protein>}
     */
    public static Optional<StaticModTarget> of(ParameterDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        String name = definition.name();
        if (definition.category() != ParameterCategory.STATIC_MODS
                || definition.kind() != ValueKind.DECIMAL
                || !name.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String rest = name.substring(PREFIX.length());
        Optional<StaticModTarget> terminus = terminus(name, rest);
        if (terminus.isPresent()) {
            return terminus;
        }
        if (rest.length() < 3 || rest.charAt(1) != '_') {
            return Optional.empty();
        }
        char letter = rest.charAt(0);
        if (letter < 'A' || letter > 'Z') {
            return Optional.empty();
        }
        String residueName = rest.substring(2).replace('_', ' ');
        return Optional.of(
                new StaticModTarget(name, Optional.of(letter), residueName + " (" + letter + ")"));
    }

    private static Optional<StaticModTarget> terminus(String name, String rest) {
        if (!rest.startsWith(TERM, 1)) {
            return Optional.empty();
        }
        char end = rest.charAt(0);
        String of = rest.substring(1 + TERM.length());
        if ((end != 'N' && end != 'C') || !("peptide".equals(of) || "protein".equals(of))) {
            return Optional.empty();
        }
        return Optional.of(
                new StaticModTarget(name, Optional.empty(), of + " " + end + "-terminus"));
    }

    /**
     * Whether the target is a terminus rather than a residue.
     *
     * @return {@code true} for {@code add_Nterm_peptide} and its three siblings
     */
    public boolean isTerminus() {
        return residue.isEmpty();
    }
}
