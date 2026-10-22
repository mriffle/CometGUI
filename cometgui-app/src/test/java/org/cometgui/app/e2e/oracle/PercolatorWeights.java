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

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A Percolator weights file ({@code --weights}), read by the test's own code (P14-7): each
 * feature's weight in each cross-validation split, and the ranking the Learned feature weights view
 * is compared with (canonical scenario step 24).
 *
 * <h2>The format, as Percolator 3.x writes it</h2>
 *
 * <p>Lines beginning {@code #} are comments. The rest come in groups of three, one group per
 * cross-validation split (Percolator writes three): a line of tab-separated feature names ending
 * with the bias term {@code m0}; a line of the split's <em>normalised</em> weights; a line of its
 * <em>raw</em> weights. Every split names the same features in the same order. Each weight is read
 * as an exact {@link BigDecimal}, so {@code -0.0000} is zero and {@code 0.3346} is not a double
 * near it.
 *
 * <h2>The ranking</h2>
 *
 * <p>A feature's importance is the mean, over the splits, of the absolute value of its normalised
 * weight. Its rank is competition ranking on that, compared exactly: one more than the number of
 * features with a strictly greater mean absolute weight, so two equal features share a rank and the
 * next rank is skipped. The bias {@code m0} is not a feature of a PSM and is not ranked. This is
 * the rule {@code cometgui-results}' {@code WEIGHTS-SUMMARY.txt} records, applied again here by
 * code that shares nothing with the product's parser.
 *
 * @param names the feature names, in file order, {@code m0} included
 * @param splits each split's weights
 */
public record PercolatorWeights(List<String> names, List<Split> splits) {

    /** The bias term's name. */
    public static final String BIAS = "m0";

    /** Enough digits that a mean of weights written with four or six places is exact. */
    private static final MathContext MEAN = MathContext.DECIMAL128;

    /**
     * One cross-validation split.
     *
     * @param normalised the normalised weight of each feature, in {@link #names()} order
     * @param raw the raw weight of each feature, in the same order
     */
    public record Split(List<BigDecimal> normalised, List<BigDecimal> raw) {

        /** The weights, copied. */
        public Split {
            normalised = List.copyOf(normalised);
            raw = List.copyOf(raw);
        }
    }

    /**
     * One feature, summarised over the splits.
     *
     * @param name its name
     * @param normalised its normalised weight in each split, in split order
     * @param meanSigned the mean of those weights
     * @param meanAbsolute the mean of their absolute values
     * @param rank its competition rank by {@code meanAbsolute}, from 1; 0 for the bias {@code m0}
     */
    public record Feature(
            String name,
            List<BigDecimal> normalised,
            BigDecimal meanSigned,
            BigDecimal meanAbsolute,
            int rank) {

        /** The weights, copied. */
        public Feature {
            normalised = List.copyOf(normalised);
        }
    }

    /** The parts, copied. */
    public PercolatorWeights {
        names = List.copyOf(names);
        splits = List.copyOf(splits);
    }

    /**
     * Reads a weights file as UTF-8.
     *
     * @param file the file
     * @return its splits
     * @throws IOException if it cannot be read
     * @throws AssertionError if it is not whole groups of three, the groups disagree on the
     *     features, or a weight is not a number -- naming the line
     */
    public static PercolatorWeights read(Path file) throws IOException {
        return parse(Files.readString(file, StandardCharsets.UTF_8), file.toString());
    }

    /**
     * Reads a weights file's text.
     *
     * @param text the whole file
     * @param name what to call it in a failure message
     * @return its splits
     * @throws AssertionError if it is not well formed, naming the line
     */
    public static PercolatorWeights parse(String text, String name) {
        Objects.requireNonNull(text, "text");
        List<String> data = new ArrayList<>();
        List<Integer> numbers = new ArrayList<>();
        int number = 0;
        for (String raw : text.split("\n", -1)) {
            number++;
            String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
            if (line.startsWith("#") || line.isEmpty()) {
                continue;
            }
            data.add(line);
            numbers.add(number);
        }
        if (data.isEmpty() || data.size() % 3 != 0) {
            throw new AssertionError(
                    name + " has " + data.size() + " non-comment lines, not whole splits of three");
        }
        List<String> names = List.of(data.get(0).split("\t", -1));
        if (!names.get(names.size() - 1).equals(BIAS)) {
            throw new AssertionError(name + " line " + numbers.get(0) + " does not end with m0");
        }
        List<Split> splits = new ArrayList<>();
        for (int group = 0; group < data.size(); group += 3) {
            List<String> header = List.of(data.get(group).split("\t", -1));
            if (!header.equals(names)) {
                throw new AssertionError(
                        name + " line " + numbers.get(group) + " names other features: " + header);
            }
            splits.add(
                    new Split(
                            weights(
                                    data.get(group + 1),
                                    names.size(),
                                    name,
                                    numbers.get(group + 1)),
                            weights(
                                    data.get(group + 2),
                                    names.size(),
                                    name,
                                    numbers.get(group + 2))));
        }
        return new PercolatorWeights(names, splits);
    }

    private static List<BigDecimal> weights(String line, int expected, String name, int number) {
        String[] fields = line.split("\t", -1);
        if (fields.length != expected) {
            throw new AssertionError(
                    name
                            + " line "
                            + number
                            + " has "
                            + fields.length
                            + " weights, not "
                            + expected);
        }
        List<BigDecimal> weights = new ArrayList<>();
        for (String field : fields) {
            try {
                weights.add(new BigDecimal(field));
            } catch (NumberFormatException notANumber) {
                throw new AssertionError(
                        name + " line " + number + " has a weight that is not a number: " + field,
                        notANumber);
            }
        }
        return weights;
    }

    /**
     * Every feature, the bias included, in file order, summarised and ranked.
     *
     * @return the features
     */
    public List<Feature> features() {
        int count = splits.size();
        BigDecimal splitsCount = BigDecimal.valueOf(count);
        List<BigDecimal> absoluteSums = new ArrayList<>();
        List<Feature> unranked = new ArrayList<>();
        for (int index = 0; index < names.size(); index++) {
            List<BigDecimal> weights = new ArrayList<>();
            BigDecimal sum = BigDecimal.ZERO;
            BigDecimal absolute = BigDecimal.ZERO;
            for (Split split : splits) {
                BigDecimal weight = split.normalised().get(index);
                weights.add(weight);
                sum = sum.add(weight);
                absolute = absolute.add(weight.abs());
            }
            absoluteSums.add(absolute);
            unranked.add(
                    new Feature(
                            names.get(index),
                            weights,
                            sum.divide(splitsCount, MEAN),
                            absolute.divide(splitsCount, MEAN),
                            0));
        }
        List<Feature> features = new ArrayList<>();
        for (int index = 0; index < unranked.size(); index++) {
            Feature feature = unranked.get(index);
            int rank = 0;
            if (!feature.name().equals(BIAS)) {
                rank = 1;
                for (int other = 0; other < unranked.size(); other++) {
                    // the sums are exact, and every mean divides by the same count
                    if (!unranked.get(other).name().equals(BIAS)
                            && absoluteSums.get(other).compareTo(absoluteSums.get(index)) > 0) {
                        rank++;
                    }
                }
            }
            features.add(
                    new Feature(
                            feature.name(),
                            feature.normalised(),
                            feature.meanSigned(),
                            feature.meanAbsolute(),
                            rank));
        }
        return List.copyOf(features);
    }

    /**
     * The ranked features' names, most important first; equal ranks keep the file's order.
     *
     * @return the names, without {@code m0}
     */
    public List<String> ranking() {
        return features().stream()
                .filter(feature -> feature.rank() > 0)
                .sorted(Comparator.comparingInt(Feature::rank))
                .map(Feature::name)
                .toList();
    }

    /**
     * One feature by name.
     *
     * @param name the feature
     * @return it
     * @throws AssertionError if the file has no such feature
     */
    public Feature feature(String name) {
        return features().stream()
                .filter(feature -> feature.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no feature " + name + " in " + names));
    }
}
