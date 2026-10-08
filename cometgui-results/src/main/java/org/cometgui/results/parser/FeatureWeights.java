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

import java.util.List;
import java.util.OptionalInt;

/**
 * One row of the learned feature weights summary: one feature's weight in every cross-validation
 * split and the statistics over them ({@code R-PERC-08}, design decision P10-8). Made only by
 * {@link WeightsSummary#of}, which documents how each value is computed.
 *
 * <p>Every statistic is over the <em>normalised</em> weights -- the coefficients Percolator learned
 * after its feature normalisation, the first weight row of each split in the file. The raw weights
 * are carried alongside for display only.
 */
public final class FeatureWeights {

    private final String name;
    private final boolean bias;
    private final List<Double> normalised;
    private final List<Double> raw;
    private final double meanSigned;
    private final double meanAbsolute;
    private final double standardDeviation;
    private final int positiveSplits;
    private final int negativeSplits;
    private final SignConsistency signConsistency;
    private final OptionalInt rank;

    FeatureWeights(
            String name,
            boolean bias,
            List<Double> normalised,
            List<Double> raw,
            double meanSigned,
            double meanAbsolute,
            double standardDeviation,
            int positiveSplits,
            int negativeSplits,
            OptionalInt rank) {
        this.name = name;
        this.bias = bias;
        this.normalised = List.copyOf(normalised);
        this.raw = List.copyOf(raw);
        this.meanSigned = meanSigned;
        this.meanAbsolute = meanAbsolute;
        this.standardDeviation = standardDeviation;
        this.positiveSplits = positiveSplits;
        this.negativeSplits = negativeSplits;
        this.signConsistency =
                SignConsistency.of(positiveSplits, negativeSplits, this.normalised.size());
        this.rank = rank;
    }

    /**
     * The feature's name, as the file's header gives it.
     *
     * @return for example {@code lnrSp}, or {@value LearnedWeights#BIAS_FEATURE} for the bias
     */
    public String name() {
        return name;
    }

    /**
     * Whether this is Percolator's bias term, {@value LearnedWeights#BIAS_FEATURE}: listed, never
     * ranked.
     *
     * @return {@code true} for the bias term
     */
    public boolean isBias() {
        return bias;
    }

    /**
     * The normalised weight in each split.
     *
     * @return one weight per split, in split order, exactly as the file holds them
     */
    public List<Double> normalised() {
        return normalised;
    }

    /**
     * The raw weight -- on the feature's original scale -- in each split, for display only; no
     * statistic here uses it.
     *
     * @return one weight per split, in split order, exactly as the file holds them
     */
    public List<Double> raw() {
        return raw;
    }

    /**
     * The mean of the normalised weights, summed in split order and divided by the split count.
     *
     * @return the mean signed weight
     */
    public double meanSigned() {
        return meanSigned;
    }

    /**
     * The mean of the normalised weights' absolute values, summed in split order and divided by the
     * split count: the ranking key.
     *
     * @return the mean absolute weight
     */
    public double meanAbsolute() {
        return meanAbsolute;
    }

    /**
     * The <em>population</em> standard deviation of the normalised weights: the square root of the
     * mean squared deviation from {@link #meanSigned()}, dividing by the split count <i>n</i>, not
     * <i>n</i> - 1. With one split it is therefore {@code 0}.
     *
     * @return the standard deviation
     */
    public double standardDeviation() {
        return standardDeviation;
    }

    /**
     * How many splits gave the feature a weight above zero.
     *
     * @return the count
     */
    public int positiveSplits() {
        return positiveSplits;
    }

    /**
     * How many splits gave the feature a weight below zero.
     *
     * @return the count
     */
    public int negativeSplits() {
        return negativeSplits;
    }

    /**
     * How many splits gave the feature a weight of exactly zero ({@code -0.0} included).
     *
     * @return the count
     */
    public int zeroSplits() {
        return normalised.size() - positiveSplits - negativeSplits;
    }

    /**
     * Whether the splits agree on the weight's sign; {@link SignConsistency} defines each verdict.
     *
     * @return the verdict
     */
    public SignConsistency signConsistency() {
        return signConsistency;
    }

    /**
     * The rank by {@link #meanAbsolute()}: 1 for the largest; features with exactly equal mean
     * absolute weights share a rank and the next rank skips (competition ranking: 1, 2, 2, 4).
     *
     * @return the rank, or empty for the bias term, which is not ranked
     */
    public OptionalInt rank() {
        return rank;
    }

    @Override
    public String toString() {
        return name
                + (bias ? " (bias)" : "")
                + " "
                + normalised
                + " mean "
                + meanSigned
                + " |mean| "
                + meanAbsolute
                + " sd "
                + standardDeviation
                + " "
                + signConsistency
                + " rank "
                + (rank.isPresent() ? String.valueOf(rank.getAsInt()) : "-");
    }
}
