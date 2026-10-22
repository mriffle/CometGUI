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

package org.cometgui.app.e2e.oracle;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The weights oracle against a hand-written file in Percolator's layout, with every mean and rank
 * worked out by hand: features a, b, c, d and the bias m0 over two splits, b and d tied.
 *
 * <pre>
 *   feature  split 1   split 2   mean signed  mean absolute  rank
 *   a         0.5000    0.3000    0.4          0.4            1
 *   b        -0.2500    0.2500    0            0.25           2
 *   c         0.0000   -0.0000    0            0              4
 *   d         0.2500   -0.2500    0            0.25           2
 *   m0       -1.0000   -2.0000   -1.5          1.5            (not ranked)
 * </pre>
 */
class PercolatorWeightsTest {

    private static final String FILE =
            "# This file contains the weights from each cross validation bin from percolator"
                    + " training\n"
                    + "# First line is the feature names, followed by normalized weights, and the"
                    + " raw"
                    + " weights of bin 1\n"
                    + "# This is repeated for the other bins\n"
                    + "a\tb\tc\td\tm0\n"
                    + "0.5000\t-0.2500\t0.0000\t0.2500\t-1.0000\n"
                    + "1.5\t-0.75\t0\t0.75\t-3.1\n"
                    + "a\tb\tc\td\tm0\n"
                    + "0.3000\t0.2500\t-0.0000\t-0.2500\t-2.0000\n"
                    + "0.9\t0.75\t-0\t-0.75\t-6.2\n";

    @Test
    @DisplayName("two splits; each weight exactly as written, the raw row kept apart")
    void theSplitsAreRead() {
        PercolatorWeights weights = PercolatorWeights.parse(FILE, "weights.txt");
        assertAll(
                () -> assertEquals(List.of("a", "b", "c", "d", "m0"), weights.names()),
                () -> assertEquals(2, weights.splits().size()),
                () ->
                        assertEquals(
                                List.of("0.5000", "-0.2500", "0.0000", "0.2500", "-1.0000"),
                                weights.splits().get(0).normalised().stream()
                                        .map(BigDecimal::toPlainString)
                                        .toList()),
                () ->
                        assertEquals(
                                List.of("0.9", "0.75", "0", "-0.75", "-6.2"),
                                weights.splits().get(1).raw().stream()
                                        .map(BigDecimal::toPlainString)
                                        .toList()));
    }

    @Test
    @DisplayName(
            "means are exact; b and d tie at rank 2 and c, below them, is rank 4; m0 is unranked")
    void meansAndRanks() {
        PercolatorWeights weights = PercolatorWeights.parse(FILE, "weights.txt");
        assertAll(
                () -> assertExactly("0.4", weights.feature("a").meanSigned()),
                () -> assertExactly("0.4", weights.feature("a").meanAbsolute()),
                () -> assertExactly("0", weights.feature("b").meanSigned()),
                () -> assertExactly("0.25", weights.feature("b").meanAbsolute()),
                () -> assertExactly("0", weights.feature("c").meanAbsolute()),
                () -> assertExactly("0.25", weights.feature("d").meanAbsolute()),
                () -> assertExactly("-1.5", weights.feature("m0").meanSigned()),
                () -> assertExactly("1.5", weights.feature("m0").meanAbsolute()),
                () ->
                        assertEquals(
                                List.of(1, 2, 4, 2, 0),
                                weights.features().stream()
                                        .map(PercolatorWeights.Feature::rank)
                                        .toList()),
                () -> assertEquals(List.of("a", "b", "d", "c"), weights.ranking()));
    }

    @Test
    @DisplayName("three splits: a mean that does not terminate is still ranked exactly")
    void threeSplits() {
        String file =
                "x\ty\tm0\n0.1\t0.2\t1\n1\t1\t1\n"
                        + "x\ty\tm0\n0.1\t0\t1\n1\t1\t1\n"
                        + "x\ty\tm0\n0.2\t0.1\t1\n1\t1\t1\n";
        PercolatorWeights weights = PercolatorWeights.parse(file, "three.txt");
        // x: (0.1 + 0.1 + 0.2) / 3 = 0.1333...; y: (0.2 + 0 + 0.1) / 3 = 0.1
        assertAll(
                () -> assertEquals(3, weights.splits().size()),
                () -> assertExactly("0.1", weights.feature("y").meanAbsolute()),
                () ->
                        assertTrue(
                                weights.feature("x")
                                        .meanAbsolute()
                                        .toPlainString()
                                        .startsWith("0.13333333333333333")),
                () -> assertEquals(List.of("x", "y"), weights.ranking()));
    }

    @Test
    @DisplayName(
            "a file that is not whole splits, disagrees on features, or holds a non-number is"
                    + " refused, naming the line")
    void malformedFilesAreRefused() {
        assertEquals(
                "w has 4 non-comment lines, not whole splits of three",
                assertThrows(
                                AssertionError.class,
                                () -> PercolatorWeights.parse("a\tm0\n1\t2\n1\t2\na\tm0\n", "w"))
                        .getMessage());
        assertEquals(
                "w line 4 names other features: [b, m0]",
                assertThrows(
                                AssertionError.class,
                                () ->
                                        PercolatorWeights.parse(
                                                "a\tm0\n1\t2\n1\t2\nb\tm0\n1\t2\n1\t2\n", "w"))
                        .getMessage());
        assertEquals(
                "w line 2 has a weight that is not a number: abc",
                assertThrows(
                                AssertionError.class,
                                () -> PercolatorWeights.parse("a\tm0\nabc\t2\n1\t2\n", "w"))
                        .getMessage());
        assertEquals(
                "w line 1 does not end with m0",
                assertThrows(
                                AssertionError.class,
                                () -> PercolatorWeights.parse("a\tb\n1\t2\n1\t2\n", "w"))
                        .getMessage());
    }

    private static void assertExactly(String expected, BigDecimal actual) {
        assertEquals(
                0,
                new BigDecimal(expected).compareTo(actual),
                () -> "expected exactly " + expected + ", was " + actual.toPlainString());
    }
}
