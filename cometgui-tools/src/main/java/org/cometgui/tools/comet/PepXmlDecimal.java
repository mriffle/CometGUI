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

import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A decimal number exactly as a pepXML file states it: the attribute's text, which is what is
 * transcribed, and its exact value, which is what is compared.
 *
 * <p>Comet prints every mass and every retention time with C's {@code %f} family ({@code %0.6f},
 * {@code %0.1f}): an optional sign, digits, and a fractional part. Only that plain form is accepted
 * -- no exponent, no {@code nan} or {@code inf}, no surrounding space, no bare {@code .5} or {@code
 * 5.} -- so a value that is not a number Comet could have written is refused rather than
 * reinterpreted.
 *
 * @param text the attribute's text, verbatim
 */
public record PepXmlDecimal(String text) {

    private static final Pattern PLAIN = Pattern.compile("[+-]?[0-9]+(?:\\.[0-9]+)?");

    /**
     * Validates the text.
     *
     * @throws NullPointerException if the text is {@code null}
     * @throws IllegalArgumentException if the text is not a plain decimal number, quoting it
     */
    public PepXmlDecimal {
        Objects.requireNonNull(text, "text");
        if (!isPlain(text)) {
            throw new IllegalArgumentException("not a plain decimal number: \"" + text + "\"");
        }
    }

    /**
     * Whether a text is a plain decimal number: an optional sign, one or more digits, and
     * optionally a point followed by one or more digits.
     *
     * @param text the text
     * @return whether it is
     */
    public static boolean isPlain(String text) {
        return PLAIN.matcher(Objects.requireNonNull(text, "text")).matches();
    }

    /**
     * The exact value, with the text's scale: {@code 15.994915} has scale 6.
     *
     * @return the value
     */
    public BigDecimal value() {
        return new BigDecimal(text);
    }
}
