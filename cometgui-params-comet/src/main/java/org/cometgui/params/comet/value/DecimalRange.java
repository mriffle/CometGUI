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
 * Two decimals on one line, such as {@code digest_mass_range = 600.0 5000.0} and {@code
 * clear_mz_range = 0.0 0.0}: the {@code DECIMAL_RANGE} kind.
 *
 * <p>Read as Comet reads it, {@code "%lf %lf"} ({@code parse_double_range} in {@code Comet.cpp} at
 * {@code v2026.02.2}), except that exactly two numbers are required. Each keeps the scale written,
 * and is written back as {@link Numbers} describes, separated by one space. Order is the validation
 * package's question.
 *
 * @param first the first number
 * @param second the second number
 */
public record DecimalRange(BigDecimal first, BigDecimal second) {

    /** Validates presence. */
    public DecimalRange {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
    }

    /**
     * Reads a range.
     *
     * @param name the parameter, for the diagnostic
     * @param text the value text
     * @return the range
     * @throws ValueSyntaxException naming the parameter and the field, if the text is not two
     *     decimals
     */
    public static DecimalRange parse(String name, String text) {
        Objects.requireNonNull(name, "name");
        String[] tokens = Ranges.two(name, text);
        return new DecimalRange(
                Numbers.decimal(name, Ranges.FIRST, tokens[0]),
                Numbers.decimal(name, Ranges.SECOND, tokens[1]));
    }

    /**
     * The value text.
     *
     * @return for example {@code 600.0 5000.0}
     */
    public String text() {
        return firstText() + " " + secondText();
    }

    /**
     * Reads a range from its two values entered separately, as a range control's two fields hold
     * them.
     *
     * @param name the parameter, for the diagnostic
     * @param firstText the first value
     * @param secondText the second value
     * @return the range
     * @throws ValueSyntaxException naming the parameter and the value at fault, if either is not
     *     one number
     */
    public static DecimalRange parse(String name, String firstText, String secondText) {
        Objects.requireNonNull(name, "name");
        return new DecimalRange(
                Numbers.decimal(name, Ranges.FIRST, Ranges.one(name, Ranges.FIRST, firstText)),
                Numbers.decimal(name, Ranges.SECOND, Ranges.one(name, Ranges.SECOND, secondText)));
    }

    /**
     * The first value's text, with the scale written.
     *
     * @return for example {@code 600.0}
     */
    public String firstText() {
        return Numbers.text(first);
    }

    /**
     * The second value's text, with the scale written.
     *
     * @return for example {@code 5000.0}
     */
    public String secondText() {
        return Numbers.text(second);
    }
}
