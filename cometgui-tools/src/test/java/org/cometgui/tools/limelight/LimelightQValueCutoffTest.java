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

package org.cometgui.tools.limelight;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The Limelight q-value cutoff: its default, its range, and the text the converter is given. */
class LimelightQValueCutoffTest {

    @Test
    @DisplayName("the default is 0.01 and is passed as the text 0.01")
    void theDefault() {
        assertAll(
                () -> assertEquals(0.01, LimelightQValueCutoff.DEFAULT.value()),
                () -> assertEquals("0.01", LimelightQValueCutoff.DEFAULT.argument()),
                () ->
                        assertEquals(
                                new BigDecimal("0.01"), LimelightQValueCutoff.DEFAULT.asDecimal()));
    }

    @Test
    @DisplayName("the argument is plain decimal text: no exponent, no trailing zero, any locale")
    void theArgumentText() {
        Locale before = Locale.getDefault();
        Locale.setDefault(Locale.GERMANY);
        try {
            assertAll(
                    () -> assertEquals("0.05", new LimelightQValueCutoff(0.05).argument()),
                    () -> assertEquals("0", new LimelightQValueCutoff(0.0).argument()),
                    () -> assertEquals("0", new LimelightQValueCutoff(-0.0).argument()),
                    () -> assertEquals("1", new LimelightQValueCutoff(1.0).argument()),
                    () -> assertEquals("0.001", new LimelightQValueCutoff(1e-3).argument()),
                    () -> assertEquals("0.0000000001", new LimelightQValueCutoff(1e-10).argument()),
                    () -> assertEquals("0.25", new LimelightQValueCutoff(0.25).argument()));
        } finally {
            Locale.setDefault(before);
        }
    }

    @Test
    @DisplayName("both ends of [0, 1] are accepted")
    void bothEndsAreAccepted() {
        assertAll(
                () -> assertEquals(0.0, new LimelightQValueCutoff(0.0).value()),
                () -> assertEquals(1.0, new LimelightQValueCutoff(1.0).value()));
    }

    @ParameterizedTest
    @ValueSource(
            doubles = {
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
                -0.5,
                2.0,
                -Double.MIN_VALUE,
                1.0000000000000002
            })
    @DisplayName("anything not a number from 0 to 1 is refused, though the converter would take it")
    void outOfRangeIsRefused(double value) {
        assertEquals(
                "the Limelight q-value cutoff must be a number from 0 to 1, but was "
                        + value
                        + ". The converter itself accepts any number and writes it into the file"
                        + " as the q-value filter Limelight starts from, so a value outside that"
                        + " range would not be refused by anything else",
                assertThrows(IllegalArgumentException.class, () -> new LimelightQValueCutoff(value))
                        .getMessage());
    }

    @Test
    @DisplayName("the next value above 1 and below 0 are refused: the bounds are exact")
    void theBoundsAreExact() {
        assertAll(
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> new LimelightQValueCutoff(Math.nextUp(1.0))),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> new LimelightQValueCutoff(Math.nextDown(0.0))));
    }
}
