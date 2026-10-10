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

package org.cometgui.tools.pdv;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A spectrum's retention time exactly as its file states it: the text and the unit.
 *
 * @param text the value's text, verbatim from the file
 * @param unit the unit the file states it in
 */
public record RetentionTime(String text, Unit unit) {

    /** The units a source file states a retention time in. */
    public enum Unit {
        /** Seconds: the Unit Ontology's {@code UO:0000010}, and MGF's {@code RTINSECONDS}. */
        SECOND,
        /** Minutes: the Unit Ontology's {@code UO:0000031}. */
        MINUTE
    }

    private static final BigDecimal SECONDS_PER_MINUTE = BigDecimal.valueOf(60);

    /**
     * Validates the value.
     *
     * @throws NullPointerException if either component is {@code null}
     * @throws IllegalArgumentException if the text is not a decimal number, quoting it
     */
    public RetentionTime {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(unit, "unit");
        decimal(text);
    }

    private static BigDecimal decimal(String text) {
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException(
                    "a retention time must be a decimal number, but was \"" + text + "\"",
                    notANumber);
        }
    }

    /**
     * The value in seconds, as decimal text, by exact decimal arithmetic.
     *
     * <p>Seconds are the file's own text, verbatim. Minutes are multiplied by 60 as a {@link
     * BigDecimal}, which is exact, so nothing is rounded: {@code 79.015933333333} minutes is {@code
     * 4740.95599999998} seconds, not the {@code 4740.956} a {@code double} printed to three places
     * would give.
     *
     * @return the seconds, never in exponent notation when converted
     */
    public String secondsText() {
        if (unit == Unit.SECOND) {
            return text;
        }
        return decimal(text).multiply(SECONDS_PER_MINUTE).stripTrailingZeros().toPlainString();
    }
}
