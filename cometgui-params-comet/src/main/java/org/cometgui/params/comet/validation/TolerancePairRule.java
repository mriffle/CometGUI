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

package org.cometgui.params.comet.validation;

import java.util.List;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ParameterEntry;
import org.cometgui.params.comet.value.TolerancePair;

/**
 * Validator {@code signed_tolerance_pair}: {@code R-PARAM-04}'s own rule for the precursor mass
 * tolerance, {@code lower <= 0 <= upper}. It is <strong>not</strong> the generic ordering rule,
 * which would accept the normal {@code -20.0 / 20.0} for the wrong reason and grade every other
 * window wrongly.
 *
 * <ul>
 *   <li>{@code lower > upper} is an <strong>error</strong>: no mass can fall in the window, and
 *       Comet 2026.02.2 itself stops with "mass_tolerance_lower is greater than
 *       mass_tolerance_upper" ({@code CometSearchManager.cpp} lines 572-576, which negate both
 *       bounds, and 1584-1588, at {@code v2026.02.2}).
 *   <li>A window that does not contain zero ({@code 5 / 20}, {@code -20 / -5}) is a
 *       <strong>warning</strong>: legal, and a user may want it, but the theoretical mass itself is
 *       outside it.
 *   <li>A window that contains zero but is not symmetric about it ({@code -10 / 20}) is a
 *       <strong>warning</strong> too.
 * </ul>
 *
 * <p>Units ({@code peptide_mass_units}: amu, mmu or ppm) do not change the rule: each is a positive
 * multiple of a mass difference, so the signs of the bounds and their order are the same in every
 * unit. Every finding names both parameters, the lower first.
 */
final class TolerancePairRule {

    private TolerancePairRule() {}

    static void check(CometParameters model, ParameterEntry entry, Findings findings) {
        TolerancePair pair = model.tolerancePair();
        List<String> both = List.of(TolerancePair.LOWER, TolerancePair.UPPER);
        String shown =
                TolerancePair.LOWER
                        + " = "
                        + pair.lowerText()
                        + " and "
                        + TolerancePair.UPPER
                        + " = "
                        + pair.upperText();
        if (pair.lower().compareTo(pair.upper()) > 0) {
            findings.add(
                    Rule.PAIR_REVERSED,
                    both,
                    shown
                            + ": the lower bound is above the upper bound, so no precursor can"
                            + " match and Comet refuses to search; the lower bound is normally"
                            + " negative, for example -20.0 and 20.0");
            return;
        }
        if (pair.lower().signum() > 0 || pair.upper().signum() < 0) {
            findings.add(
                    Rule.PAIR_SAME_SIGNED,
                    both,
                    shown
                            + ": the window does not contain 0, so a peptide whose mass matches"
                            + " the precursor exactly is not searched; check this is deliberate"
                            + " (normally lower <= 0 <= upper)");
            return;
        }
        if (pair.lower().negate().compareTo(pair.upper()) != 0) {
            findings.add(
                    Rule.PAIR_ASYMMETRIC,
                    both,
                    shown
                            + ": the window is not symmetric about 0; check this is deliberate"
                            + " (normally the lower bound is the negative of the upper)");
        }
    }
}
