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

package org.cometgui.results.filtering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.cometgui.results.parser.QValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * {@link QValueFilter#classify(QValue.Status, double)}: the one predicate given a status and a
 * value, as the disk-backed store holds a q-value in its index (Phase 10 unit 3). It is the one
 * comparison; {@link QValueFilter#classify(QValue)} delegates to it, so the two agree on every
 * q-value by construction, and this test proves it on the edge texts as well.
 */
class QValueFilterStatusTest {

    private static final List<String> TEXTS =
            List.of(
                    "0",
                    "0.0",
                    "-0",
                    "1e-400",
                    "0.005",
                    "0.00999999",
                    "0.01",
                    "0.010",
                    "1e-2",
                    "0.0100001",
                    "0.5",
                    "1",
                    "1.0",
                    "",
                    "NaN",
                    "nan",
                    "inf",
                    "0,01",
                    "1.5",
                    "-0.1",
                    "1e400",
                    "-1e-400");

    @Test
    @DisplayName(
            "inclusive at exactly the cutoff, above it fails, at 0 and 1 too; the boundary from"
                    + " both sides by one ulp")
    void inclusiveBoundary() {
        PsmQValueFilter one = PsmQValueFilter.parse("0.01");
        assertEquals(Visibility.PASSES, one.classify(QValue.Status.KNOWN, 0.01));
        assertEquals(Visibility.PASSES, one.classify(QValue.Status.KNOWN, Math.nextDown(0.01)));
        assertEquals(Visibility.FAILS, one.classify(QValue.Status.KNOWN, Math.nextUp(0.01)));
        assertEquals(Visibility.PASSES, one.classify(QValue.Status.KNOWN, 0.0));
        assertEquals(Visibility.PASSES, one.classify(QValue.Status.KNOWN, -0.0));
        assertEquals(Visibility.FAILS, one.classify(QValue.Status.KNOWN, 1.0));
        PeptideQValueFilter zero = PeptideQValueFilter.parse("0");
        assertEquals(Visibility.PASSES, zero.classify(QValue.Status.KNOWN, 0.0));
        assertEquals(Visibility.FAILS, zero.classify(QValue.Status.KNOWN, Double.MIN_VALUE));
        PsmQValueFilter all = PsmQValueFilter.parse("1");
        assertEquals(Visibility.PASSES, all.classify(QValue.Status.KNOWN, 1.0));
    }

    @ParameterizedTest
    @EnumSource(
            value = QValue.Status.class,
            names = {"MISSING", "UNPARSABLE", "OUT_OF_RANGE"})
    @DisplayName(
            "an unknown status is the unknown category whatever value comes with it, at 0 and 1")
    void unknownWhateverTheValue(QValue.Status status) {
        for (String cutoff : List.of("0", "0.01", "1")) {
            PsmQValueFilter filter = PsmQValueFilter.parse(cutoff);
            for (double value : new double[] {Double.NaN, 0.0, 0.5, 1.0, -1.0, 2.0}) {
                assertEquals(
                        Visibility.UNKNOWN_Q_VALUE,
                        filter.classify(status, value),
                        status + " " + value + " at " + cutoff);
            }
        }
    }

    @Test
    @DisplayName(
            "a known status with a value no q-value has (NaN, infinite, below 0, above 1) is"
                    + " refused, never silently classified; a null status is refused")
    void knownWithImpossibleValueRefused() {
        PsmQValueFilter filter = PsmQValueFilter.DEFAULT;
        for (double value :
                new double[] {
                    Double.NaN,
                    Double.POSITIVE_INFINITY,
                    Double.NEGATIVE_INFINITY,
                    -Double.MIN_VALUE,
                    Math.nextUp(1.0),
                    2.0
                }) {
            IllegalArgumentException refused =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> filter.classify(QValue.Status.KNOWN, value));
            assertTrue(refused.getMessage().contains("within [0, 1]"), refused.getMessage());
        }
        QValue.Status none = java.util.Collections.singletonList((QValue.Status) null).get(0);
        assertThrows(NullPointerException.class, () -> filter.classify(none, 0.0));
    }

    @Test
    @DisplayName(
            "the status-and-value form agrees with classify(QValue) on every edge text at every"
                    + " cutoff")
    void agreesWithTheQValueForm() {
        for (String cutoff : List.of("0", "0.005", "0.01", "0.0100001", "0.5", "1")) {
            PsmQValueFilter filter = PsmQValueFilter.parse(cutoff);
            for (String text : TEXTS) {
                QValue q = QValue.of(text);
                double value = q.isKnown() ? q.value() : Double.NaN;
                assertEquals(
                        filter.classify(q),
                        filter.classify(q.status(), value),
                        "'" + text + "' at " + cutoff);
            }
        }
        PsmQValueFilter one = PsmQValueFilter.DEFAULT;
        assertEquals(Visibility.PASSES, one.classify(QValue.of("0.010")));
        assertEquals(Visibility.FAILS, one.classify(QValue.of("0.0100001")));
        assertEquals(Visibility.UNKNOWN_Q_VALUE, one.classify(QValue.of("1e400")));
    }
}
