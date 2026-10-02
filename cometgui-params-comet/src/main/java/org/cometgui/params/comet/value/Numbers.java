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
import java.util.regex.Pattern;

/**
 * How every codec in this package reads and writes a number, in one place.
 *
 * <p>A decimal is read into a {@link BigDecimal}, which keeps every digit and the scale the user
 * wrote: {@code 15.9949} and {@code 15.994915} stay different values, and {@code 0.0} stays {@code
 * 0.0} rather than becoming {@code 0}. It is written back with {@link BigDecimal#toPlainString()},
 * so the digits survive exactly; only the notation is canonical -- a leading {@code +} is dropped
 * and an exponent is written out ({@code 1.5e2} becomes {@code 150}). A whole number is read with
 * {@link Integer#parseInt(String)} and written with {@link Integer#toString(int)}. None of these
 * consults the default locale, so a JVM running under a comma-decimal locale reads and writes the
 * same text ({@code R-PARAM-11}).
 *
 * <p>What is accepted is what C's {@code %lf} and {@code %d}, which Comet's reader uses, accept as
 * a plain number -- minus {@code inf} and {@code nan}, which no Comet parameter means.
 *
 * <p>Public since Phase 06 unit 4, unchanged otherwise: the typed model reads and writes its scalar
 * values (a single decimal, a single whole number) through this class, so that the whole parameter
 * file has one number reader and one number writer.
 */
public final class Numbers {

    private static final Pattern DECIMAL =
            Pattern.compile("[+-]?([0-9]+[.]?[0-9]*|[.][0-9]+)([eE][+-]?[0-9]{1,3})?");

    private static final Pattern WHOLE = Pattern.compile("[+-]?[0-9]+");

    private Numbers() {}

    /**
     * Reads a decimal.
     *
     * @param subject what is being read, for the diagnostic
     * @param field the field, for the diagnostic
     * @param text one token
     * @return its value, with the scale written
     * @throws ValueSyntaxException if the token is not a number
     */
    public static BigDecimal decimal(String subject, String field, String text) {
        if (!DECIMAL.matcher(text).matches()) {
            throw new ValueSyntaxException(subject, field, "\"" + text + "\" is not a number");
        }
        return new BigDecimal(text);
    }

    /**
     * Reads a whole number.
     *
     * @param subject what is being read, for the diagnostic
     * @param field the field, for the diagnostic
     * @param text one token
     * @return its value
     * @throws ValueSyntaxException if the token is not a whole number an {@code int} can hold
     */
    public static int whole(String subject, String field, String text) {
        if (!WHOLE.matcher(text).matches()) {
            throw new ValueSyntaxException(
                    subject, field, "\"" + text + "\" is not a whole number");
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException tooLarge) {
            throw new ValueSyntaxException(
                    subject, field, "\"" + text + "\" is too large for Comet's integer");
        }
    }

    /**
     * Writes a decimal as its digits, with the scale it was read with.
     *
     * @param value the value
     * @return its canonical text
     */
    public static String text(BigDecimal value) {
        return value.toPlainString();
    }

    /**
     * Splits a value into its whitespace-separated tokens.
     *
     * @param text the value text
     * @return the tokens; none for blank text
     */
    public static String[] tokens(String text) {
        String stripped = text.strip();
        return stripped.isEmpty() ? new String[0] : stripped.split("\\s+");
    }
}
