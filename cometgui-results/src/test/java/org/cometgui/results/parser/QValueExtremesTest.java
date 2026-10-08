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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Decimal q-values beyond a {@code double}'s range, decided as exact decimal arithmetic decides
 * them (the independent counter's policy): a positive value too small for a {@code double} is not
 * zero, and a value too large or a negative one too small is out of range, not unparsable. Phase 10
 * unit 1 found both; neither occurs in Percolator output or any fixture.
 */
class QValueExtremesTest {

    @ParameterizedTest
    @ValueSource(strings = {"1e-400", "1E-400", "0.0000001e-330", "+5e-324000"})
    @DisplayName("a positive decimal closer to zero than any double is known, and above zero")
    void tinyPositive(String text) {
        QValue q = QValue.of(text);
        assertEquals(QValue.Status.KNOWN, q.status());
        assertEquals(Double.MIN_VALUE, q.value());
        assertTrue(q.value() > 0.0);
        assertEquals(text, q.text());
    }

    @ParameterizedTest
    @CsvSource({
        "1e400, OUT_OF_RANGE",
        "-1e400, OUT_OF_RANGE",
        "1E+309, OUT_OF_RANGE",
        "-1e-400, OUT_OF_RANGE",
        "-0.0000001e-330, OUT_OF_RANGE"
    })
    @DisplayName("too large for a double, or negative and too small: out of range, not unparsable")
    void outOfRange(String text, QValue.Status status) {
        assertEquals(status, QValue.of(text).status());
    }

    @ParameterizedTest
    @CsvSource({"0e-400, 0.0", "0.000e999, 0.0", "-0e-400, -0.0", "4.9e-324, 4.9e-324"})
    @DisplayName("an exact zero in any spelling is zero; the smallest double is itself")
    void zeros(String text, double value) {
        QValue q = QValue.of(text);
        assertEquals(QValue.Status.KNOWN, q.status());
        assertEquals(value, q.value());
    }

    @Test
    @DisplayName("the significand test reads only the digits before the exponent")
    void significand() {
        assertTrue(DecimalText.hasNonZeroSignificand("0.0001"));
        assertTrue(DecimalText.hasNonZeroSignificand("-9"));
        assertTrue(DecimalText.hasNonZeroSignificand(".5e-999"));
        assertFalse(DecimalText.hasNonZeroSignificand("0.000"));
        assertFalse(DecimalText.hasNonZeroSignificand("0e5"));
        assertFalse(DecimalText.hasNonZeroSignificand("-0E9"));
        assertFalse(DecimalText.hasNonZeroSignificand("0"));
        assertTrue(DecimalText.isDecimal("1e400"));
        assertFalse(DecimalText.isDecimal("nan"));
        assertTrue(Double.isNaN(DecimalText.parse("1e400")), "the weights' rule is unchanged");
    }
}
