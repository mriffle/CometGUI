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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalInt;
import org.cometgui.results.testing.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The learned feature weights summary over two files CONSTRUCTED for it ({@code
 * constructed/CONSTRUCTED.txt}), with every expected value worked out by hand and typed in. Every
 * weight in them is a multiple of 1/8, so every mean and deviation is exact in binary and the
 * comparisons are exact. The same numbers were confirmed afterwards by the independent Python
 * program of {@code WEIGHTS-SUMMARY.txt}; the real files are in {@link
 * WeightsSummaryIndependentTest}.
 */
class WeightsSummaryTest {

    static final String TIES_SHA256 =
            "86466aa30c1d4f72915a45d50f7828284b5e36750ce3a022cb65880fb6b06c92";
    static final String ONE_SPLIT_SHA256 =
            "28111d5c8d8052d8ffee6be61876e1311037b05bfdefc232efa014c379c8a571";

    @TempDir private Path directory;

    private static WeightsSummary ties() throws IOException {
        return WeightsSummary.of(
                WeightsReader.read(Fixtures.verified("constructed/weights-ties.txt", TIES_SHA256)));
    }

    private static WeightsSummary oneSplit() throws IOException {
        return WeightsSummary.of(
                WeightsReader.read(
                        Fixtures.verified("constructed/weights-one-split.txt", ONE_SPLIT_SHA256)));
    }

    private WeightsSummary inline(String content) throws IOException {
        Path file = directory.resolve("weights.txt");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return WeightsSummary.of(WeightsReader.read(file));
    }

    /** Asserts one row exactly; {@code rank} 0 means not ranked. */
    private static void row(
            WeightsSummary summary,
            String name,
            int rank,
            SignConsistency sign,
            int positive,
            int negative,
            int zero,
            double mean,
            double meanAbsolute,
            double deviation,
            List<Double> normalised,
            List<Double> raw) {
        FeatureWeights row = summary.feature(name);
        assertAll(
                name,
                () -> assertEquals(name, row.name()),
                () ->
                        assertEquals(
                                rank == 0 ? OptionalInt.empty() : OptionalInt.of(rank),
                                row.rank(),
                                "rank"),
                () -> assertEquals(sign, row.signConsistency(), "sign"),
                () -> assertEquals(positive, row.positiveSplits(), "positive"),
                () -> assertEquals(negative, row.negativeSplits(), "negative"),
                () -> assertEquals(zero, row.zeroSplits(), "zero"),
                () -> assertEquals(mean, row.meanSigned(), 0.0, "mean"),
                () -> assertEquals(meanAbsolute, row.meanAbsolute(), 0.0, "mean |w|"),
                () -> assertEquals(deviation, row.standardDeviation(), 0.0, "sd"),
                () -> assertEquals(normalised, row.normalised(), "normalised"),
                () -> assertEquals(raw, row.raw(), "raw"));
    }

    @Nested
    @DisplayName("weights-ties.txt: two splits, ties, zeros, a mixed sign, a big bias")
    class Ties {

        @Test
        @DisplayName("the split count is read from the file: 2")
        void splitCount() throws IOException {
            WeightsSummary summary = ties();
            assertEquals(2, summary.splitCount());
            assertEquals(2, summary.weights().splitCount());
            assertTrue(summary.file().endsWith(Path.of("constructed", "weights-ties.txt")));
        }

        @Test
        @DisplayName("rows in file order, every value hand-computed")
        void everyRow() throws IOException {
            WeightsSummary summary = ties();
            assertEquals(
                    List.of("c", "z", "a", "f", "d", "b", "e", "y", "m0"),
                    summary.features().stream().map(FeatureWeights::name).toList());
            // c: 0.75, -0.25 -> mean 0.25, |mean| 0.5, deviations +-0.5 -> sd 0.5
            row(
                    summary,
                    "c",
                    2,
                    SignConsistency.MIXED,
                    1,
                    1,
                    0,
                    0.25,
                    0.5,
                    0.5,
                    List.of(0.75, -0.25),
                    List.of(1.5, -0.5));
            // z: 0, -0.0 -> all zero; -0.0 is zero, not negative
            row(
                    summary,
                    "z",
                    7,
                    SignConsistency.ALL_ZERO,
                    0,
                    0,
                    2,
                    0,
                    0,
                    0,
                    List.of(0.0, -0.0),
                    List.of(0.0, 0.0));
            // a: 0.5, 0.5 -> sd 0; ties c and b on |mean| 0.5
            row(
                    summary,
                    "a",
                    2,
                    SignConsistency.ALL_POSITIVE,
                    2,
                    0,
                    0,
                    0.5,
                    0.5,
                    0,
                    List.of(0.5, 0.5),
                    List.of(1.0, 1.0));
            // f: 2, 1 -> mean 1.5, deviations +-0.5 -> sd 0.5; the largest feature
            row(
                    summary,
                    "f",
                    1,
                    SignConsistency.ALL_POSITIVE,
                    2,
                    0,
                    0,
                    1.5,
                    1.5,
                    0.5,
                    List.of(2.0, 1.0),
                    List.of(4.0, 2.0));
            // d: 0, 0.5 -> a zero mixed with a positive is MIXED; rank 5 after the 2, 2, 2 tie
            row(
                    summary,
                    "d",
                    5,
                    SignConsistency.MIXED,
                    1,
                    0,
                    1,
                    0.25,
                    0.25,
                    0.25,
                    List.of(0.0, 0.5),
                    List.of(0.0, 1.0));
            // b: -0.25, -0.75 -> mean -0.5, |mean| 0.5, sd 0.25
            row(
                    summary,
                    "b",
                    2,
                    SignConsistency.ALL_NEGATIVE,
                    0,
                    2,
                    0,
                    -0.5,
                    0.5,
                    0.25,
                    List.of(-0.25, -0.75),
                    List.of(-0.5, -1.5));
            row(
                    summary,
                    "e",
                    6,
                    SignConsistency.ALL_POSITIVE,
                    2,
                    0,
                    0,
                    0.125,
                    0.125,
                    0,
                    List.of(0.125, 0.125),
                    List.of(0.25, 0.25));
            // y and z tie at 0: both rank 7
            row(
                    summary,
                    "y",
                    7,
                    SignConsistency.ALL_ZERO,
                    0,
                    0,
                    2,
                    0,
                    0,
                    0,
                    List.of(0.0, 0.0),
                    List.of(0.0, 0.0));
            // m0: -3, -1 -> mean -2, |mean| 2 -- larger than f, yet unranked
            row(
                    summary,
                    "m0",
                    0,
                    SignConsistency.ALL_NEGATIVE,
                    0,
                    2,
                    0,
                    -2,
                    2,
                    1,
                    List.of(-3.0, -1.0),
                    List.of(-6.0, -2.0));
        }

        @Test
        @DisplayName("the bias is flagged, unranked, and takes no rank number")
        void bias() throws IOException {
            WeightsSummary summary = ties();
            FeatureWeights bias = summary.bias().orElseThrow();
            assertSame(summary.feature("m0"), bias);
            assertTrue(bias.isBias());
            assertTrue(bias.rank().isEmpty());
            assertEquals(
                    1,
                    summary.features().stream().filter(FeatureWeights::isBias).count(),
                    "only m0 is the bias");
            assertEquals(
                    List.of(2, 7, 2, 1, 5, 2, 6, 7),
                    summary.features().stream()
                            .filter(row -> !row.isBias())
                            .map(row -> row.rank().getAsInt())
                            .toList(),
                    "competition ranks 1, 2, 2, 2, 5, 6, 7, 7: m0 takes none of them");
        }

        @Test
        @DisplayName("a feature named but absent is refused")
        void unknownFeature() throws IOException {
            WeightsSummary summary = ties();
            IllegalArgumentException refusal =
                    assertThrows(IllegalArgumentException.class, () -> summary.feature("q"));
            assertEquals(
                    "the weights in " + summary.file() + " name no feature 'q'",
                    refusal.getMessage());
        }

        @Test
        @DisplayName("the rows and their weight lists cannot be changed")
        void immutable() throws IOException {
            WeightsSummary summary = ties();
            assertThrows(UnsupportedOperationException.class, () -> summary.features().clear());
            FeatureWeights row = summary.feature("a");
            assertThrows(UnsupportedOperationException.class, () -> row.normalised().set(0, 9.0));
            assertThrows(UnsupportedOperationException.class, () -> row.raw().set(0, 9.0));
        }

        @Test
        @DisplayName("toString names the values, for test failures and logs")
        void text() throws IOException {
            WeightsSummary summary = ties();
            assertEquals(
                    "b [-0.25, -0.75] mean -0.5 |mean| 0.5 sd 0.25 ALL_NEGATIVE rank 2",
                    summary.feature("b").toString());
            assertEquals(
                    "m0 (bias) [-3.0, -1.0] mean -2.0 |mean| 2.0 sd 1.0 ALL_NEGATIVE rank -",
                    summary.feature("m0").toString());
        }
    }

    @Nested
    @DisplayName("weights-one-split.txt: one split")
    class OneSplit {

        @Test
        @DisplayName("split count 1; population deviation 0 for every feature; ranks by |w|")
        void everyRow() throws IOException {
            WeightsSummary summary = oneSplit();
            assertEquals(1, summary.splitCount());
            row(
                    summary,
                    "p",
                    2,
                    SignConsistency.ALL_POSITIVE,
                    1,
                    0,
                    0,
                    0.5,
                    0.5,
                    0,
                    List.of(0.5),
                    List.of(1.0));
            row(
                    summary,
                    "n",
                    1,
                    SignConsistency.ALL_NEGATIVE,
                    0,
                    1,
                    0,
                    -1.5,
                    1.5,
                    0,
                    List.of(-1.5),
                    List.of(-3.0));
            row(
                    summary,
                    "zz",
                    3,
                    SignConsistency.ALL_ZERO,
                    0,
                    0,
                    1,
                    0,
                    0,
                    0,
                    List.of(0.0),
                    List.of(0.0));
            row(
                    summary,
                    "m0",
                    0,
                    SignConsistency.ALL_POSITIVE,
                    1,
                    0,
                    0,
                    0.25,
                    0.25,
                    0,
                    List.of(0.25),
                    List.of(0.5));
        }
    }

    @Nested
    @DisplayName("inline files")
    class Inline {

        @Test
        @DisplayName("without m0 there is no bias and every feature is ranked")
        void noBias() throws IOException {
            WeightsSummary summary = inline("x\tw\n1\t-2\n1\t-2\nx\tw\n3\t-2\n3\t-2\n");
            assertTrue(summary.bias().isEmpty());
            assertEquals(OptionalInt.of(1), summary.feature("x").rank());
            assertEquals(OptionalInt.of(1), summary.feature("w").rank(), "|2| ties |mean 2|");
            assertFalse(summary.feature("x").isBias());
        }

        @Test
        @DisplayName("a zero mixed with negatives is MIXED; three splits, three classes, MIXED")
        void mixedWithZero() throws IOException {
            WeightsSummary summary =
                    inline(
                            "g\th\tm0\n-1\t1\t0\n0\t0\t0\n"
                                    + "g\th\tm0\n0\t0\t0\n0\t0\t0\n"
                                    + "g\th\tm0\n-1\t-1\t0\n0\t0\t0\n");
            assertEquals(3, summary.splitCount());
            assertEquals(SignConsistency.MIXED, summary.feature("g").signConsistency());
            assertEquals(SignConsistency.MIXED, summary.feature("h").signConsistency());
            assertEquals(1, summary.feature("h").zeroSplits());
            assertEquals(SignConsistency.ALL_ZERO, summary.feature("m0").signConsistency());
            assertTrue(summary.feature("m0").isBias());
            // g: -1, 0, -1 -> mean -2/3, sd sqrt(2/9)
            assertEquals(-2.0 / 3.0, summary.feature("g").meanSigned(), 1e-15);
            assertEquals(Math.sqrt(2.0 / 9.0), summary.feature("g").standardDeviation(), 1e-15);
        }

        @Test
        @DisplayName("a summary of nothing is refused")
        void nullWeights() {
            assertThrows(NullPointerException.class, () -> WeightsSummary.of(null));
        }
    }
}
