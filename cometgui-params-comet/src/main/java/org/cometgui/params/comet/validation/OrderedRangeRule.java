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

import java.math.BigDecimal;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ParameterEntry;
import org.cometgui.params.comet.model.ParameterValue;

/**
 * Validator {@code ordered_range}, the generic ordering rule: the first of a two-value range is not
 * greater than the second. <strong>Never</strong> applied to the signed precursor tolerance pair,
 * which has its own rule ({@link TolerancePairRule}, {@code R-PARAM-04}); the metadata loader
 * refuses a pair member that names this validator.
 *
 * <p>Comet 2026.02.2 does not refuse a reversed range: for {@code peptide_length_range}, {@code
 * digest_mass_range}, {@code clear_mz_range} and {@code precursor_charge} it applies the range only
 * if the end is not below the start, and otherwise keeps its built-in default without a word
 * ({@code CometSearchManager.cpp} lines 606-613, 1009-1022, 1054-1061 and 1093-1100 at {@code
 * v2026.02.2}). So a reversed range is an error here.
 *
 * <p>Two ranges give zero a meaning of its own, which this rule respects:
 *
 * <ul>
 *   <li>{@code scan_range}: {@code 0} as the second value means "to the last scan" -- Comet takes
 *       each end independently, {@code 0} meaning unset ({@code Comet.cpp} lines 768-787; {@code
 *       CometPreprocess.cpp} lines 383-387), and refuses only an end below the start when the end
 *       is not {@code 0} ({@code CometSearchManager.cpp} lines 304-316). {@code 500 0} is legal.
 *   <li>{@code precursor_charge}: {@code 0} as the first value switches the range off ({@code -q}'s
 *       own comment, "0 as 1st entry ignores parameter", {@code Comet.cpp} line 1055; applied only
 *       when the start is above 0, {@code CometSearchManager.cpp} lines 1054-1061). {@code 0 0} is
 *       the default; {@code 0 4} is legal but its {@code 4} does nothing, which is a warning.
 * </ul>
 */
final class OrderedRangeRule {

    /** Second value 0 means "to the last scan". */
    static final String SCAN_RANGE = "scan_range";

    /** First value 0 switches the range off. */
    static final String PRECURSOR_CHARGE = "precursor_charge";

    private OrderedRangeRule() {}

    static void check(CometParameters model, ParameterEntry entry, Findings findings) {
        String name = entry.name();
        BigDecimal first;
        BigDecimal second;
        switch (entry.value()) {
            case ParameterValue.WholeRange range -> {
                first = BigDecimal.valueOf(range.range().first());
                second = BigDecimal.valueOf(range.range().second());
            }
            case ParameterValue.DecimalPair range -> {
                first = range.range().first();
                second = range.range().second();
            }
            default ->
                    throw new IllegalStateException(
                            name
                                    + " names the ordered_range validator but is of kind "
                                    + entry.definition().kind()
                                    + ", not a two-value range");
        }
        String shown = name + " = " + model.text(name);
        if (name.equals(PRECURSOR_CHARGE) && first.signum() == 0) {
            if (second.signum() != 0) {
                findings.add(
                        Rule.RANGE_SECOND_IGNORED,
                        name,
                        shown
                                + ": a first value of 0 switches the charge range off, so the"
                                + " second value has no effect; use 0 0 for no range, or a first"
                                + " value of 1 or more");
            }
            return;
        }
        if (name.equals(SCAN_RANGE) && second.signum() == 0) {
            return;
        }
        if (first.compareTo(second) > 0) {
            findings.add(
                    Rule.RANGE_REVERSED,
                    name,
                    shown
                            + ": the first value is greater than the second, and Comet would"
                            + " silently ignore the range; give the smaller value first");
        }
    }
}
