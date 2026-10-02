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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The one number reader and writer every codec shares. */
class NumbersTest {

    @Test
    @DisplayName("digits and scale survive; only the notation is canonical")
    void digitsAndScaleSurvive() {
        assertEquals("15.994915", Numbers.text(Numbers.decimal("s", "f", "15.994915")));
        assertEquals("15.9949", Numbers.text(Numbers.decimal("s", "f", "15.9949")));
        assertEquals("0.0", Numbers.text(Numbers.decimal("s", "f", "0.0")));
        assertEquals("0.0000", Numbers.text(Numbers.decimal("s", "f", "0.0000")));
        assertEquals("-17.026549", Numbers.text(Numbers.decimal("s", "f", "-17.026549")));
        assertEquals("15.99", Numbers.text(Numbers.decimal("s", "f", "+15.99")));
        assertEquals("150", Numbers.text(Numbers.decimal("s", "f", "1.5e2")));
        assertEquals("0.0015", Numbers.text(Numbers.decimal("s", "f", "1.5E-3")));
        assertEquals("0.5", Numbers.text(Numbers.decimal("s", "f", ".5")));
        assertEquals("15", Numbers.text(Numbers.decimal("s", "f", "15.")));
        assertEquals("15", Numbers.text(Numbers.decimal("s", "f", "15")));
    }

    @Test
    @DisplayName("what C's %lf would not read as a plain number is refused")
    void nonNumbersAreRefused() {
        for (String bad :
                List.of(
                        "", ".", "-", "1.2.3", "1e", "1e1000", "nan", "inf", "0x10", "1,5", "1 2",
                        "--1")) {
            ValueSyntaxException failure =
                    assertThrows(
                            ValueSyntaxException.class, () -> Numbers.decimal("s", "f", bad), bad);
            assertEquals("s, f: \"" + bad + "\" is not a number", failure.getMessage());
        }
        assertEquals(new BigDecimal("1E+999"), Numbers.decimal("s", "f", "1e999"));
    }

    @Test
    @DisplayName("whole numbers are read and range-checked")
    void wholeNumbers() {
        assertEquals(-1, Numbers.whole("s", "f", "-1"));
        assertEquals(3, Numbers.whole("s", "f", "+3"));
        assertEquals(Integer.MAX_VALUE, Numbers.whole("s", "f", "2147483647"));
        assertEquals(
                "s, f: \"2147483648\" is too large for Comet's integer",
                assertThrows(
                                ValueSyntaxException.class,
                                () -> Numbers.whole("s", "f", "2147483648"))
                        .getMessage());
        for (String bad : List.of("", "1.0", "1e2", "a", "1,2", " 1")) {
            assertEquals(
                    "s, f: \"" + bad + "\" is not a whole number",
                    assertThrows(
                                    ValueSyntaxException.class,
                                    () -> Numbers.whole("s", "f", bad),
                                    bad)
                            .getMessage());
        }
    }

    @Test
    @DisplayName("tokens split on any run of white space; blank text has none")
    void tokens() {
        assertArrayEquals(new String[] {"a", "b", "c"}, Numbers.tokens("  a \t b\r\nc  "));
        assertArrayEquals(new String[0], Numbers.tokens(""));
        assertArrayEquals(new String[0], Numbers.tokens(" \t "));
        assertArrayEquals(new String[] {"a"}, Numbers.tokens("a"));
    }
}
