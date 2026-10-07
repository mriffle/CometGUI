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

package org.cometgui.results.parser;

import java.util.regex.Pattern;

/**
 * The one way this package reads a number Percolator wrote: a plain decimal in {@code Locale.ROOT}
 * form -- optional sign, digits with a {@code .} separator, optional exponent -- and nothing else.
 *
 * <p>{@link Double#parseDouble} alone is not strict enough. It accepts {@code "NaN"}, {@code
 * "Infinity"}, hexadecimal ({@code "0x1p3"}), type suffixes ({@code "1d"}) and surrounding white
 * space, none of which Percolator writes, so a value spelt that way is not a value Percolator
 * produced. Percolator's own {@code nan} and {@code inf} spellings are rejected for the same
 * reason. A comma decimal separator ({@code "0,5"}) is rejected rather than guessed at.
 */
final class DecimalText {

    private static final Pattern DECIMAL =
            Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");

    private DecimalText() {}

    /**
     * Reads a decimal number.
     *
     * @param text the text as written
     * @return its value, or {@link Double#NaN} if the text is not a decimal number in the form
     *     above or its value is not finite (an exponent too large for a {@code double})
     */
    static double parse(String text) {
        if (!DECIMAL.matcher(text).matches()) {
            return Double.NaN;
        }
        double value = Double.parseDouble(text);
        return Double.isFinite(value) ? value : Double.NaN;
    }
}
