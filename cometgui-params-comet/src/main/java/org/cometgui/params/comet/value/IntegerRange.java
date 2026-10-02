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

import java.util.Objects;

/**
 * Two whole numbers on one line, such as {@code peptide_length_range = 5 50}, {@code
 * precursor_charge = 0 0} and {@code scan_range = 0 0}: the {@code INTEGER_RANGE} kind.
 *
 * <p>Read as Comet reads it, {@code "%d %d"} ({@code parse_int_range} in {@code Comet.cpp} at
 * {@code v2026.02.2}), except that exactly two numbers are required where Comet would ignore a
 * third. Written as the two numbers separated by one space. The two are not called minimum and
 * maximum because they are not always that ({@code precursor_charge = 0 2} searches every charge),
 * and whether they are ordered is the validation package's question.
 *
 * @param first the first number
 * @param second the second number
 */
public record IntegerRange(int first, int second) {

    /**
     * Reads a range.
     *
     * @param name the parameter, for the diagnostic
     * @param text the value text
     * @return the range
     * @throws ValueSyntaxException naming the parameter and the field, if the text is not two whole
     *     numbers
     */
    public static IntegerRange parse(String name, String text) {
        Objects.requireNonNull(name, "name");
        String[] tokens = Ranges.two(name, text);
        return new IntegerRange(
                Numbers.whole(name, Ranges.FIRST, tokens[0]),
                Numbers.whole(name, Ranges.SECOND, tokens[1]));
    }

    /**
     * The value text.
     *
     * @return for example {@code 5 50}
     */
    public String text() {
        return first + " " + second;
    }
}
