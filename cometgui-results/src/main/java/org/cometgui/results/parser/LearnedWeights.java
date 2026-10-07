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
import java.util.List;
import java.util.Objects;

/**
 * The weights Percolator learned, as its weights artefact records them: the feature names and, for
 * each cross-validation split, a normalised and a raw weight per feature.
 *
 * <p>{@code R-PERC-09}: {@link #splitCount()} is however many splits the file holds -- Percolator
 * writes three, but nothing here assumes it. The last feature Percolator names is {@value
 * #BIAS_FEATURE}, the bias term; it is kept as a feature like any other, and a caller that ranks
 * features decides whether to show it.
 *
 * <p>The summary statistics of {@code R-PERC-08} -- mean signed and mean absolute weight, standard
 * deviation, sign consistency, rank -- are computed from this by the results model, not here.
 *
 * @param file the artefact it was read from
 * @param comments the file's comment lines, {@code #} included, in order
 * @param featureNames the feature names every split shares, in file order
 * @param splits every split, in file order, numbered from 1
 */
public record LearnedWeights(
        Path file, List<String> comments, List<String> featureNames, List<WeightsSplit> splits) {

    /** The name Percolator gives the bias term, the last of its feature names. */
    public static final String BIAS_FEATURE = "m0";

    /**
     * The weights.
     *
     * @throws IllegalArgumentException if there is no split, a split is numbered out of order, or a
     *     split's width differs from the feature names'
     */
    public LearnedWeights {
        Objects.requireNonNull(file, "file");
        comments = List.copyOf(comments);
        featureNames = List.copyOf(featureNames);
        splits = List.copyOf(splits);
        if (splits.isEmpty()) {
            throw new IllegalArgumentException("learned weights need at least one split");
        }
        for (int index = 0; index < splits.size(); index++) {
            WeightsSplit split = splits.get(index);
            if (split.number() != index + 1 || split.normalised().size() != featureNames.size()) {
                throw new IllegalArgumentException(
                        "split number "
                                + split.number()
                                + " at position "
                                + (index + 1)
                                + " has "
                                + split.normalised().size()
                                + " weights for "
                                + featureNames.size()
                                + " features");
            }
        }
    }

    /**
     * How many cross-validation splits the artefact holds.
     *
     * @return the number of splits, read from the file
     */
    public int splitCount() {
        return splits.size();
    }

    /**
     * One feature's normalised weight in every split.
     *
     * @param feature a name from {@link #featureNames()}
     * @return its normalised weights, one per split in split order
     * @throws IllegalArgumentException if no feature has that name
     */
    public List<Double> normalisedWeightsOf(String feature) {
        int index = indexOf(feature);
        return splits.stream().map(split -> split.normalised().get(index)).toList();
    }

    /**
     * One feature's raw weight in every split.
     *
     * @param feature a name from {@link #featureNames()}
     * @return its raw weights, one per split in split order
     * @throws IllegalArgumentException if no feature has that name
     */
    public List<Double> rawWeightsOf(String feature) {
        int index = indexOf(feature);
        return splits.stream().map(split -> split.raw().get(index)).toList();
    }

    private int indexOf(String feature) {
        int index = featureNames.indexOf(feature);
        if (index < 0) {
            throw new IllegalArgumentException(
                    "the weights in " + file + " name no feature '" + feature + "'");
        }
        return index;
    }
}
