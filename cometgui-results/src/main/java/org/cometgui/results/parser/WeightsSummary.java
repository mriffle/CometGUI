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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The learned feature weights summary ({@code R-PERC-08}, {@code R-PERC-09}, design decision
 * P10-8): one {@link FeatureWeights} row per feature of a {@link LearnedWeights}, in the file's
 * feature order. It is the one source of the values the weights table shows and the weights export
 * writes.
 *
 * <p>How each value is computed, over the feature's <em>normalised</em> weight in each of the
 * <i>n</i> splits, where <i>n</i> is {@link LearnedWeights#splitCount()} -- read from the file,
 * never assumed to be three:
 *
 * <ul>
 *   <li><b>mean signed</b>: the weights summed in split order, as {@code double}s starting from
 *       {@code 0.0}, divided by <i>n</i>;
 *   <li><b>mean absolute</b>: the same over {@link Math#abs(double) absolute values};
 *   <li><b>standard deviation</b>: the <em>population</em> deviation, {@code sqrt(sum((w - mean)^2)
 *       / n)}, the squares summed in split order -- dividing by <i>n</i>, not <i>n</i> - 1, so one
 *       split gives {@code 0} rather than a division by zero;
 *   <li><b>sign consistency</b>: as {@link SignConsistency} defines it; a zero is neither sign;
 *   <li><b>rank</b>: by mean absolute weight, 1 for the largest. A feature's rank is one more than
 *       the number of ranked features whose mean absolute weight is strictly greater, so features
 *       whose computed mean absolute weights are exactly equal -- {@code ==} on the {@code double}s
 *       above, no tolerance -- share a rank and the next rank skips (competition ranking: 1, 2, 2,
 *       4). Among tied features the listing order is the file's.
 * </ul>
 *
 * <p>The bias term {@value LearnedWeights#BIAS_FEATURE} is listed with every statistic, {@link
 * FeatureWeights#isBias() marked as the bias}, and <b>not ranked</b>: its rank is empty, and it
 * neither takes a rank number nor pushes any feature down, however large its weight.
 *
 * <p>The weights are exactly the values {@link WeightsReader} parsed from the file. Sorting the
 * table by a column is the view's job: it sorts these rows and recomputes nothing.
 */
public final class WeightsSummary {

    private final LearnedWeights weights;
    private final List<FeatureWeights> features;

    private WeightsSummary(LearnedWeights weights, List<FeatureWeights> features) {
        this.weights = weights;
        this.features = List.copyOf(features);
    }

    /**
     * Summarises learned weights.
     *
     * @param weights the weights, as {@link WeightsReader} read them
     * @return the summary, one row per feature in file order
     * @throws NullPointerException if {@code weights} is {@code null}
     */
    public static WeightsSummary of(LearnedWeights weights) {
        Objects.requireNonNull(weights, "weights");
        int splits = weights.splitCount();
        List<String> names = weights.featureNames();
        double[] meanAbsolutes = new double[names.size()];
        List<List<Double>> normalised = new ArrayList<>(names.size());
        for (int feature = 0; feature < names.size(); feature++) {
            List<Double> values = new ArrayList<>(splits);
            for (WeightsSplit split : weights.splits()) {
                values.add(split.normalised().get(feature));
            }
            normalised.add(values);
            double absoluteSum = 0.0;
            for (double value : values) {
                absoluteSum += Math.abs(value);
            }
            meanAbsolutes[feature] = absoluteSum / splits;
        }
        List<FeatureWeights> rows = new ArrayList<>(names.size());
        for (int feature = 0; feature < names.size(); feature++) {
            String name = names.get(feature);
            boolean bias = isBias(name);
            List<Double> values = normalised.get(feature);
            List<Double> raw = new ArrayList<>(splits);
            for (WeightsSplit split : weights.splits()) {
                raw.add(split.raw().get(feature));
            }
            double sum = 0.0;
            int positive = 0;
            int negative = 0;
            for (double value : values) {
                sum += value;
                if (value > 0) {
                    positive++;
                } else if (value < 0) {
                    negative++;
                }
            }
            double mean = sum / splits;
            double squares = 0.0;
            for (double value : values) {
                double deviation = value - mean;
                squares += deviation * deviation;
            }
            rows.add(
                    new FeatureWeights(
                            name,
                            bias,
                            values,
                            raw,
                            mean,
                            meanAbsolutes[feature],
                            Math.sqrt(squares / splits),
                            positive,
                            negative,
                            bias
                                    ? OptionalInt.empty()
                                    : OptionalInt.of(rank(names, meanAbsolutes, feature))));
        }
        return new WeightsSummary(weights, rows);
    }

    private static boolean isBias(String name) {
        return LearnedWeights.BIAS_FEATURE.equals(name);
    }

    private static int rank(List<String> names, double[] meanAbsolutes, int feature) {
        int greater = 0;
        for (int other = 0; other < names.size(); other++) {
            if (!isBias(names.get(other)) && meanAbsolutes[other] > meanAbsolutes[feature]) {
                greater++;
            }
        }
        return greater + 1;
    }

    /**
     * The learned weights summarised.
     *
     * @return the weights, comments included
     */
    public LearnedWeights weights() {
        return weights;
    }

    /**
     * The weights artefact the summary was computed from.
     *
     * @return its path
     */
    public Path file() {
        return weights.file();
    }

    /**
     * How many cross-validation splits the artefact holds ({@code R-PERC-09}).
     *
     * @return the split count, read from the file
     */
    public int splitCount() {
        return weights.splitCount();
    }

    /**
     * Every feature's row, the bias term included.
     *
     * @return the rows, in the file's feature order
     */
    public List<FeatureWeights> features() {
        return features;
    }

    /**
     * One feature's row.
     *
     * @param name a feature name from the file
     * @return its row
     * @throws IllegalArgumentException if no feature has that name
     */
    public FeatureWeights feature(String name) {
        for (FeatureWeights row : features) {
            if (row.name().equals(name)) {
                return row;
            }
        }
        throw new IllegalArgumentException(
                "the weights in " + weights.file() + " name no feature '" + name + "'");
    }

    /**
     * The bias term's row.
     *
     * @return the {@value LearnedWeights#BIAS_FEATURE} row, or empty if the file names no bias
     */
    public Optional<FeatureWeights> bias() {
        return features.stream().filter(FeatureWeights::isBias).findFirst();
    }
}
