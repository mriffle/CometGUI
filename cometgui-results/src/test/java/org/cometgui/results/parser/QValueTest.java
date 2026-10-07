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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The q-value policy (R-RES-02) and the one decimal reader it rests on. */
class QValueTest {

    @ParameterizedTest
    @CsvSource({
        "0, 0.0",
        "1, 1.0",
        "0.01, 0.01",
        "0.0588235, 0.0588235",
        "1e-3, 0.001",
        "1E-03, 0.001",
        "+0.5, 0.5",
        ".5, 0.5",
        "5., 5.0",
        "-0.0, -0.0",
        "3.70705e-11, 3.70705e-11"
    })
    @DisplayName("decimals in Locale.ROOT form are read")
    void decimals(String text, double expected) {
        assertEquals(expected, DecimalText.parse(text));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                " 0.1",
                "0.1 ",
                "NaN",
                "nan",
                "-nan",
                "inf",
                "-inf",
                "Infinity",
                "0,5",
                "0x1p3",
                "1d",
                "1f",
                "e5",
                ".",
                "1e",
                "1e+",
                "--1",
                "abc",
                "1.2.3",
                "1e400"
            })
    @DisplayName("everything else, and a value too large to be finite, is not a number")
    void notDecimals(String text) {
        assertTrue(Double.isNaN(DecimalText.parse(text)), text);
    }

    @Test
    @DisplayName("parsing does not depend on the default locale")
    void localeIndependent() {
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertEquals(0.01, DecimalText.parse("0.01"));
            assertTrue(Double.isNaN(DecimalText.parse("0,01")));
        } finally {
            Locale.setDefault(before);
        }
    }

    @ParameterizedTest
    @CsvSource({"0, 0.0", "1, 1.0", "0.01, 0.01", "1e-3, 0.001", "-0, -0.0"})
    @DisplayName("a finite number within [0, 1] is KNOWN, its text kept")
    void known(String text, double value) {
        QValue q = QValue.of(text);
        assertEquals(QValue.Status.KNOWN, q.status());
        assertTrue(q.isKnown());
        assertEquals(value, q.value());
        assertEquals(text, q.text());
        assertEquals(text, q.toString());
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                "'', MISSING",
                "NaN, UNPARSABLE",
                "nan, UNPARSABLE",
                "inf, UNPARSABLE",
                "Infinity, UNPARSABLE",
                "'0,005', UNPARSABLE",
                "' 0.01', UNPARSABLE",
                "1.0000001, OUT_OF_RANGE",
                "1.5, OUT_OF_RANGE",
                "-0.01, OUT_OF_RANGE",
                "-1e-9, OUT_OF_RANGE"
            },
            delimiter = ',',
            quoteCharacter = '\'')
    @DisplayName("missing, unparsable and out-of-range q-values are not known, and have no value")
    void unknown(String text, QValue.Status status) {
        QValue q = QValue.of(text);
        assertEquals(status, q.status());
        assertFalse(q.isKnown());
        assertEquals(text, q.text());
        IllegalStateException refused = assertThrows(IllegalStateException.class, q::value);
        assertEquals(
                "the q-value '" + text + "' is " + status + ", so it has no value to compare",
                refused.getMessage());
        assertEquals(status + "('" + text + "')", q.toString());
    }

    @Test
    @DisplayName("equal by text: 0.01 and 0.010 are distinct as written")
    void equality() {
        assertEquals(QValue.of("0.01"), QValue.of("0.01"));
        assertEquals(QValue.of("0.01").hashCode(), QValue.of("0.01").hashCode());
        assertNotEquals(QValue.of("0.01"), QValue.of("0.010"));
        assertNotEquals(QValue.of("0.01").hashCode(), QValue.of("0.02").hashCode());
        assertNotEquals(QValue.of("NaN"), QValue.of("nan"));
        assertFalse(QValue.of("0.01").equals("0.01"));
        assertThrows(NullPointerException.class, () -> QValue.of(null));
    }
}
