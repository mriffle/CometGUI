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

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The signed precursor mass tolerance: two parameters, {@code peptide_mass_tolerance_lower} and
 * {@code peptide_mass_tolerance_upper}, modelled as one value.
 *
 * <p>The lower bound is normally negative: Comet defines the mass error as experimental minus
 * theoretical, so {@code -20.0} admits peptides up to 20 units heavier than the measured mass. The
 * sign is kept exactly as written. Each member is read as Comet reads it, one decimal ({@code
 * parse_double} in {@code Comet.cpp} at {@code v2026.02.2}). Whether the window is sensible -- the
 * specification's {@code lower <= 0 <= upper} with a warning otherwise ({@code R-PARAM-04}) -- is
 * the validation package's rule, never the generic ordering rule.
 *
 * @param lower the lower bound, normally negative
 * @param upper the upper bound
 */
public record TolerancePair(BigDecimal lower, BigDecimal upper) {

    /** The parameter holding the lower bound. */
    public static final String LOWER = "peptide_mass_tolerance_lower";

    /** The parameter holding the upper bound. */
    public static final String UPPER = "peptide_mass_tolerance_upper";

    /** Validates presence. */
    public TolerancePair {
        Objects.requireNonNull(lower, "lower");
        Objects.requireNonNull(upper, "upper");
    }

    /**
     * Reads the pair from the two declarations' value texts.
     *
     * @param lowerText the value of {@link #LOWER}
     * @param upperText the value of {@link #UPPER}
     * @return the pair
     * @throws ValueSyntaxException naming the parameter, if either is not one decimal
     */
    public static TolerancePair parse(String lowerText, String upperText) {
        return new TolerancePair(member(LOWER, lowerText), member(UPPER, upperText));
    }

    private static BigDecimal member(String name, String text) {
        Objects.requireNonNull(text, "text");
        String[] tokens = Numbers.tokens(text);
        if (tokens.length != 1) {
            throw new ValueSyntaxException(
                    name, "value", "\"" + text.strip() + "\" is not one decimal number");
        }
        return Numbers.decimal(name, "value", tokens[0]);
    }

    /**
     * The value text of {@link #LOWER}.
     *
     * @return for example {@code -20.0}
     */
    public String lowerText() {
        return Numbers.text(lower);
    }

    /**
     * The value text of {@link #UPPER}.
     *
     * @return for example {@code 20.0}
     */
    public String upperText() {
        return Numbers.text(upper);
    }
}
