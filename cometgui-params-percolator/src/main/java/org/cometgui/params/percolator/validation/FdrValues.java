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

package org.cometgui.params.percolator.validation;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The one range rule shared by {@link TestFdr} and {@link TrainFdr}: a false discovery rate above 0
 * and at most 1.
 *
 * <p>Shared as a rule and nowhere else: the two thresholds stay two types, so that neither can be
 * handed where the other is expected, and neither is the display q-value filter ({@code
 * AC-RES-05}).
 *
 * <p><strong>Why 0 is refused although Percolator accepts it.</strong> All three managed builds
 * (3.06.5, 3.07.1, 3.09) accept any value in {@code [0, 1]} and reject anything outside it with
 * "option requires a float between 0 and 1" -- measured on 2026-10-07. But {@code --trainFDR 0} is
 * a sentinel, "use testFDR" (both builds' help text), so a recorded {@code 0} would not be the
 * value Percolator trained at, and {@code --testFDR 0} asks for results at a false discovery rate
 * no real search attains. Refusing 0 keeps every recorded threshold the threshold that was used.
 */
final class FdrValues {

    private FdrValues() {}

    /**
     * Checks a threshold and returns it in its canonical form.
     *
     * @param name the option's name for the message, {@code testFDR} or {@code trainFDR}
     * @param value the threshold
     * @return the threshold without trailing zeros, so that {@code 0.010} and {@code 0.01} are one
     *     value and render one way
     */
    static BigDecimal checked(String name, BigDecimal value) {
        Objects.requireNonNull(value, name);
        if (value.signum() <= 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(
                    name
                            + " must be greater than 0 and at most 1, but was "
                            + value.toPlainString());
        }
        return value.stripTrailingZeros();
    }

    /**
     * Reads a threshold as a user typed it.
     *
     * @param name the option's name for the message
     * @param text the text; surrounding whitespace is ignored, and only a full stop is a decimal
     *     separator whatever the locale
     * @return the threshold, checked
     */
    static BigDecimal parse(String name, String text) {
        Objects.requireNonNull(text, "text");
        String stripped = text.strip();
        BigDecimal value;
        try {
            value = new BigDecimal(stripped);
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException(
                    name
                            + " must be a number greater than 0 and at most 1, written with a full"
                            + " stop as the decimal separator (for example 0.01), but was \""
                            + stripped
                            + "\"",
                    notANumber);
        }
        return checked(name, value);
    }
}
