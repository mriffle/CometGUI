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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** {@link PepXmlDecimal}: the plain decimal forms Comet prints, and nothing else. */
class PepXmlDecimalTest {

    @ParameterizedTest
    @ValueSource(strings = {"0", "15.994915", "-0.984016", "+1.0", "4741.0", "007.50", "12"})
    @DisplayName("a plain decimal keeps its text and has its exact value")
    void plain(String text) {
        assertTrue(PepXmlDecimal.isPlain(text));
        PepXmlDecimal decimal = new PepXmlDecimal(text);
        assertEquals(text, decimal.text());
        assertEquals(new BigDecimal(text), decimal.value());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "", " 1.0", "1.0 ", "1.", ".5", "1e3", "1.0E-5", "NaN", "inf", "-", "1,5", "0x1",
                "1.0.0", "--1", "١٢"
            })
    @DisplayName("anything else is refused, quoting it")
    void notPlain(String text) {
        assertFalse(PepXmlDecimal.isPlain(text));
        IllegalArgumentException refused =
                assertThrows(IllegalArgumentException.class, () -> new PepXmlDecimal(text));
        assertEquals("not a plain decimal number: \"" + text + "\"", refused.getMessage());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("the value keeps the text's scale, so 160.030649 and 160.03064900 differ in text")
    void scale() {
        assertEquals(6, new PepXmlDecimal("160.030649").value().scale());
        assertEquals(
                0,
                new PepXmlDecimal("160.030649").value().compareTo(new BigDecimal("160.03064900")));
    }
}
