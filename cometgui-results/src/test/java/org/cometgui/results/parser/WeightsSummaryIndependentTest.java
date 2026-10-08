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

import static org.cometgui.results.parser.SignConsistency.ALL_NEGATIVE;
import static org.cometgui.results.parser.SignConsistency.ALL_POSITIVE;
import static org.cometgui.results.parser.SignConsistency.ALL_ZERO;
import static org.cometgui.results.parser.SignConsistency.MIXED;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.cometgui.results.testing.Fixtures;
import org.cometgui.results.testing.RealK562;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Phase 10 gate item 7 at the model level ({@code AC-RES-09}, {@code R-PERC-09}): the weights
 * summary's values, ranks and sign verdicts against numbers computed <em>independently</em> of the
 * code under test (design decision P10-3).
 *
 * <p>Every expected number below was printed by a short Python 3.11.2 program, run once on
 * 2026-10-08 over each file and typed in here mechanically from its output; no number comes from
 * Java. The program is in {@code org/cometgui/results/parser/WEIGHTS-SUMMARY.txt}, with the exact
 * command, verbatim:
 *
 * <pre>{@code
 * python3 - FILE <<'PY'   # then the program in WEIGHTS-SUMMARY.txt, then a line: PY
 * }</pre>
 *
 * <p>It shares nothing with the Java: it splits the file itself, parses each value with {@code
 * fractions.Fraction} -- exact rational arithmetic, no binary rounding at all -- takes the mean,
 * mean absolute value and population variance exactly, the square root in {@code decimal} at 60
 * digits, counts signs by comparing the exact value with 0, ranks with exact comparisons, and
 * prints each value as the {@code double} nearest the exact result ({@code %.17g}, which reads back
 * to that same {@code double}).
 *
 * <p><b>Tolerance</b>: {@value #TOLERANCE}, absolute. The Java sums at most four {@code double}s of
 * magnitude below 4 (every normalised weight in these files) and divides once, so its rounding
 * error is a few units in the last place of numbers below 16 -- under 1e-14 -- and the square root
 * only shrinks a relative error; 1e-12 leaves a hundredfold margin. Every way of computing the
 * wrong thing moves a value by far more: the inputs have four decimal places, so a dropped or
 * repeated split, a sample ({@code n - 1}) deviation, a signed mean in place of an absolute one or
 * a wrong split count changes some value here by at least 1e-5. The per-split weights, ranks, sign
 * counts and verdicts are compared exactly.
 *
 * <p>D-006: for the K562 file only the summary numbers are pinned here and in {@code
 * WEIGHTS-SUMMARY.txt}, not its per-split weights, which are the artefact's content; its per-split
 * values are checked against what the reader read. The K562 file fails, never skips, when absent
 * ({@link RealK562.Weights}).
 */
class WeightsSummaryIndependentTest {

    /** The absolute tolerance on mean, mean absolute and standard deviation; see the class doc. */
    static final double TOLERANCE = 1e-12;

    /** One feature's expected row; {@code rank} 0 is the bias term (no rank). */
    record Expected(
            String name,
            int rank,
            SignConsistency sign,
            int positive,
            int negative,
            int zero,
            double mean,
            double meanAbsolute,
            double standardDeviation,
            double[] normalised) {}

    /** A row with its per-split normalised weights pinned. */
    private static Expected f(
            String name,
            int rank,
            SignConsistency sign,
            int positive,
            int negative,
            int zero,
            double mean,
            double meanAbsolute,
            double standardDeviation,
            double... normalised) {
        return new Expected(
                name,
                rank,
                sign,
                positive,
                negative,
                zero,
                mean,
                meanAbsolute,
                standardDeviation,
                normalised);
    }

    /** A row whose per-split weights are not pinned (K562, D-006). */
    private static Expected stats(
            String name,
            int rank,
            SignConsistency sign,
            int positive,
            int negative,
            int zero,
            double mean,
            double meanAbsolute,
            double standardDeviation) {
        return new Expected(
                name,
                rank,
                sign,
                positive,
                negative,
                zero,
                mean,
                meanAbsolute,
                standardDeviation,
                null);
    }

    // python3 - constructed/weights-two-splits.txt  ->  splits 2
    static final List<Expected> TWO_SPLITS =
            List.of(
                    f(
                            "lnrSp",
                            3,
                            ALL_POSITIVE,
                            2,
                            0,
                            0,
                            0.1275,
                            0.1275,
                            0.0025000000000000001,
                            0.125,
                            0.13),
                    f(
                            "deltLCn",
                            4,
                            ALL_NEGATIVE,
                            0,
                            2,
                            0,
                            -0.0275,
                            0.0275,
                            0.0025000000000000001,
                            -0.029999999999999999,
                            -0.025000000000000001),
                    f(
                            "Xcorr",
                            1,
                            ALL_POSITIVE,
                            2,
                            0,
                            0,
                            1.4750000000000001,
                            1.4750000000000001,
                            0.025000000000000001,
                            1.5,
                            1.45),
                    f(
                            "Charge2",
                            2,
                            MIXED,
                            1,
                            1,
                            0,
                            0.050000000000000003,
                            0.14999999999999999,
                            0.14999999999999999,
                            0.20000000000000001,
                            -0.10000000000000001),
                    f(
                            "m0",
                            0,
                            ALL_NEGATIVE,
                            0,
                            2,
                            0,
                            -2.4500000000000002,
                            2.4500000000000002,
                            0.050000000000000003,
                            -2.5,
                            -2.3999999999999999));

    // python3 - constructed/weights-four-splits.txt  ->  splits 4
    static final List<Expected> FOUR_SPLITS =
            List.of(
                    f(
                            "feat_a",
                            2,
                            MIXED,
                            2,
                            0,
                            2,
                            0.3125,
                            0.3125,
                            0.3247595264191645,
                            0,
                            0.5,
                            0,
                            0.75),
                    f(
                            "feat_b",
                            1,
                            MIXED,
                            1,
                            3,
                            0,
                            -0.875,
                            0.9375,
                            0.91429617739548708,
                            -2.25,
                            -0.25,
                            0.125,
                            -1.125),
                    f(
                            "m0",
                            0,
                            MIXED,
                            3,
                            1,
                            0,
                            0.64087499999999997,
                            1.1408750000000001,
                            1.7037476476506137,
                            3.5,
                            0.001,
                            -1,
                            0.0625));

    // python3 - real/percolator-3.06.5/weights.txt  ->  splits 3
    static final List<Expected> REAL_3065 =
            List.of(
                    f(
                            "feat1",
                            2,
                            MIXED,
                            1,
                            0,
                            2,
                            0.22866666666666666,
                            0.22866666666666666,
                            0.32338350126264775,
                            0.68600000000000005,
                            0,
                            0),
                    f(
                            "feat2",
                            1,
                            MIXED,
                            2,
                            0,
                            1,
                            0.23219999999999999,
                            0.23219999999999999,
                            0.19894955809618342,
                            0,
                            0.2107,
                            0.4859),
                    f("feat3", 3, ALL_ZERO, 0, 0, 3, 0, 0, 0, 0, 0, 0),
                    f(
                            "m0",
                            0,
                            ALL_NEGATIVE,
                            0,
                            3,
                            0,
                            -0.58413333333333328,
                            0.58413333333333328,
                            0.44512417992685543,
                            -1.1747000000000001,
                            -0.10009999999999999,
                            -0.47760000000000002));

    // python3 - real/percolator-3.07.1/weights.txt (3.09's is the same bytes)  ->  splits 3
    static final List<Expected> REAL_3071 =
            List.of(
                    f("feat1", 2, ALL_ZERO, 0, 0, 3, 0, 0, 0, 0, 0, 0),
                    f(
                            "feat2",
                            1,
                            ALL_POSITIVE,
                            3,
                            0,
                            0,
                            0.31793333333333335,
                            0.31793333333333335,
                            0.039832092030868217,
                            0.33460000000000001,
                            0.26300000000000001,
                            0.35620000000000002),
                    f("feat3", 2, ALL_ZERO, 0, 0, 3, 0, 0, 0, 0, 0, 0),
                    f(
                            "m0",
                            0,
                            ALL_NEGATIVE,
                            0,
                            3,
                            0,
                            -1.0250333333333332,
                            1.0250333333333332,
                            0.1317222920474064,
                            -0.85170000000000001,
                            -1.1708000000000001,
                            -1.0526));

    // python3 - scratch/scientific-path/percolator-3.07.1/weights.txt  ->  splits 3 (D-006:
    // summary numbers only)
    static final List<Expected> K562 =
            List.of(
                    stats(
                            "lnrSp",
                            1,
                            ALL_NEGATIVE,
                            0,
                            3,
                            0,
                            -0.33633333333333332,
                            0.33633333333333332,
                            0.079188944233957864),
                    stats("deltLCn", 18, ALL_ZERO, 0, 0, 3, 0, 0, 0),
                    stats(
                            "deltCn",
                            7,
                            ALL_POSITIVE,
                            3,
                            0,
                            0,
                            0.088066666666666668,
                            0.088066666666666668,
                            0.041435277508690853),
                    stats(
                            "lnExpect",
                            2,
                            ALL_NEGATIVE,
                            0,
                            3,
                            0,
                            -0.23543333333333333,
                            0.23543333333333333,
                            0.10420787984707405),
                    stats("Xcorr", 3, ALL_POSITIVE, 3, 0, 0, 0.1139, 0.1139, 0.01969839248940549),
                    stats(
                            "Sp",
                            9,
                            MIXED,
                            1,
                            2,
                            0,
                            -0.040066666666666667,
                            0.042466666666666666,
                            0.031526954957446951),
                    stats(
                            "IonFrac",
                            4,
                            ALL_POSITIVE,
                            3,
                            0,
                            0,
                            0.094200000000000006,
                            0.094200000000000006,
                            0.032167996518278848),
                    stats(
                            "Mass",
                            5,
                            MIXED,
                            2,
                            1,
                            0,
                            0.08953333333333334,
                            0.090399999999999994,
                            0.074673705025411874),
                    stats(
                            "PepLen",
                            15,
                            MIXED,
                            2,
                            1,
                            0,
                            -0.0117,
                            0.014500000000000001,
                            0.019536802877304839),
                    stats("Charge1", 18, ALL_ZERO, 0, 0, 3, 0, 0, 0),
                    stats(
                            "Charge2",
                            14,
                            MIXED,
                            2,
                            1,
                            0,
                            0.0076333333333333331,
                            0.015166666666666667,
                            0.014524997609485365),
                    stats(
                            "Charge3",
                            13,
                            MIXED,
                            1,
                            2,
                            0,
                            -0.0018,
                            0.018133333333333335,
                            0.01859910392106745),
                    stats(
                            "Charge4",
                            12,
                            MIXED,
                            1,
                            2,
                            0,
                            -0.017566666666666668,
                            0.019166666666666665,
                            0.0142712609424987),
                    stats(
                            "Charge5",
                            17,
                            ALL_POSITIVE,
                            3,
                            0,
                            0,
                            0.011066666666666667,
                            0.011066666666666667,
                            0.0093596058796416321),
                    stats("Charge6", 18, ALL_ZERO, 0, 0, 3, 0, 0, 0),
                    stats(
                            "enzN",
                            16,
                            ALL_POSITIVE,
                            3,
                            0,
                            0,
                            0.014066666666666667,
                            0.014066666666666667,
                            0.0085888817018799878),
                    stats("enzC", 18, ALL_ZERO, 0, 0, 3, 0, 0, 0),
                    stats(
                            "enzInt",
                            11,
                            MIXED,
                            1,
                            2,
                            0,
                            -0.019699999999999999,
                            0.027166666666666665,
                            0.036005647705140184),
                    stats(
                            "lnNumSP",
                            10,
                            MIXED,
                            2,
                            1,
                            0,
                            0.024533333333333334,
                            0.033133333333333334,
                            0.030216588527201781),
                    stats(
                            "dM",
                            8,
                            MIXED,
                            2,
                            1,
                            0,
                            0.053800000000000001,
                            0.054333333333333331,
                            0.067759919323053114),
                    stats(
                            "absdM",
                            6,
                            ALL_NEGATIVE,
                            0,
                            3,
                            0,
                            -0.088800000000000004,
                            0.088800000000000004,
                            0.075335560439056051),
                    stats(
                            "m0",
                            0,
                            ALL_NEGATIVE,
                            0,
                            3,
                            0,
                            -0.74250000000000005,
                            0.74250000000000005,
                            0.064065643418814322));

    static Stream<Arguments> checkedIn() {
        return Stream.of(
                Arguments.of(
                        "constructed/weights-two-splits.txt",
                        WeightsReaderTest.TWO_SPLITS_SHA256,
                        2,
                        TWO_SPLITS),
                Arguments.of(
                        "constructed/weights-four-splits.txt",
                        WeightsReaderTest.FOUR_SPLITS_SHA256,
                        4,
                        FOUR_SPLITS),
                Arguments.of(
                        "real/percolator-3.06.5/weights.txt",
                        "d24988bd722b0937a1ffa207ad7dbb6e22ef35885adacbfe21ee0ba4cbdff8c3",
                        3,
                        REAL_3065),
                Arguments.of(
                        "real/percolator-3.07.1/weights.txt",
                        "5370f2375d3ea1f13236f6208624c69f81fa8fb190e936e863488cfda4e7a434",
                        3,
                        REAL_3071),
                Arguments.of(
                        "real/percolator-3.09/weights.txt",
                        "5370f2375d3ea1f13236f6208624c69f81fa8fb190e936e863488cfda4e7a434",
                        3,
                        REAL_3071));
    }

    @ParameterizedTest(name = "{0}: {2} splits")
    @MethodSource("checkedIn")
    @DisplayName("checked-in files: every value against the independent computation")
    void checkedInFiles(String relative, String sha256, int splits, List<Expected> expected)
            throws IOException {
        check(
                WeightsSummary.of(WeightsReader.read(Fixtures.verified(relative, sha256))),
                splits,
                expected);
    }

    @ParameterizedTest
    @EnumSource(RealK562.Weights.class)
    @DisplayName("the real K562 weights (D-006, scratch): 3 splits, 22 features, against Python")
    void realK562(RealK562.Weights file) throws IOException {
        LearnedWeights weights = WeightsReader.read(file.path());
        WeightsSummary summary = WeightsSummary.of(weights);
        assertEquals(22, summary.features().size());
        check(summary, 3, K562);
        for (FeatureWeights row : summary.features()) {
            assertEquals(weights.normalisedWeightsOf(row.name()), row.normalised(), row.name());
        }
    }

    static void check(WeightsSummary summary, int splits, List<Expected> expected) {
        assertEquals(splits, summary.splitCount(), "the split count, read from the file");
        assertEquals(
                expected.stream().map(Expected::name).toList(),
                summary.features().stream().map(FeatureWeights::name).toList(),
                "one row per feature, in file order");
        List<Executable> checks = new ArrayList<>();
        for (int index = 0; index < expected.size(); index++) {
            Expected want = expected.get(index);
            FeatureWeights got = summary.features().get(index);
            String name = want.name();
            checks.add(() -> assertEquals(want.sign(), got.signConsistency(), name + " sign"));
            checks.add(() -> assertEquals(want.positive(), got.positiveSplits(), name + " +"));
            checks.add(() -> assertEquals(want.negative(), got.negativeSplits(), name + " -"));
            checks.add(() -> assertEquals(want.zero(), got.zeroSplits(), name + " 0"));
            checks.add(
                    () -> assertEquals(want.mean(), got.meanSigned(), TOLERANCE, name + " mean"));
            checks.add(
                    () ->
                            assertEquals(
                                    want.meanAbsolute(),
                                    got.meanAbsolute(),
                                    TOLERANCE,
                                    name + " mean |w|"));
            checks.add(
                    () ->
                            assertEquals(
                                    want.standardDeviation(),
                                    got.standardDeviation(),
                                    TOLERANCE,
                                    name + " sd"));
            checks.add(() -> assertEquals(splits, got.normalised().size(), name + " splits"));
            checks.add(() -> assertEquals(splits, got.raw().size(), name + " raw splits"));
            checks.add(
                    () ->
                            assertEquals(
                                    summary.weights().rawWeightsOf(name),
                                    got.raw(),
                                    name + " raw"));
            if (want.rank() == 0) {
                checks.add(() -> assertTrue(got.isBias(), name + " is the bias"));
                checks.add(() -> assertTrue(got.rank().isEmpty(), name + " is not ranked"));
            } else {
                checks.add(() -> assertFalse(got.isBias(), name + " is not the bias"));
                checks.add(() -> assertEquals(want.rank(), got.rank().getAsInt(), name + " rank"));
            }
            if (want.normalised() != null) {
                checks.add(() -> assertEquals(want.normalised().length, splits, name + " pinned"));
                for (int split = 0; split < splits; split++) {
                    int s = split;
                    // exact; a delta of 0 makes -0.0 equal 0.0, which Python cannot tell apart
                    checks.add(
                            () ->
                                    assertEquals(
                                            want.normalised()[s],
                                            got.normalised().get(s),
                                            0.0,
                                            name + " split " + (s + 1)));
                }
            }
        }
        assertAll(checks.stream());
    }
}
