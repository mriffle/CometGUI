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

package org.cometgui.results.export;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.cometgui.results.parser.FeatureWeights;
import org.cometgui.results.parser.SignConsistency;
import org.cometgui.results.parser.WeightsSummary;

/**
 * The learned feature weights as a tab-separated table ({@code AC-RES-08}): the {@link
 * WeightsSummary}, which is the one source of the values the weights view shows, written row for
 * row and recomputing nothing.
 *
 * <p>Columns, for a summary of <i>n</i> splits: {@code feature}, {@code bias}, {@code
 * split_1_normalised} .. {@code split_<n>_normalised}, {@code mean_signed}, {@code mean_absolute},
 * {@code standard_deviation}, {@code sign_consistency}, {@code rank}, {@code split_1_raw} .. {@code
 * split_<n>_raw}. One row per feature in the artefact's order, the bias term included. {@code bias}
 * is {@code true} or {@code false}; {@code rank} is empty for the bias term, which is not ranked;
 * {@code sign_consistency} is {@code all positive}, {@code all negative}, {@code mixed} or {@code
 * all zero}.
 *
 * <p>Every number is written by {@link Double#toString(double)}: the shortest decimal that reads
 * back as exactly the same {@code double}, so the file round-trips without loss, with a {@code .}
 * decimal point and no grouping whatever the default locale -- {@code Double.toString} consults no
 * locale. Very small or large magnitudes use its {@code E} notation ({@code 1.0E-4}). Lines end
 * with {@code \n}; the bytes are UTF-8.
 */
final class WeightsTable {

    private WeightsTable() {
        throw new AssertionError("WeightsTable is never instantiated");
    }

    /**
     * Writes the table.
     *
     * @param summary the summary
     * @param out where the bytes go; not closed
     * @throws IOException if they cannot be written
     */
    static void write(WeightsSummary summary, OutputStream out) throws IOException {
        int splits = summary.splitCount();
        List<String> header = new ArrayList<>();
        header.add("feature");
        header.add("bias");
        for (int split = 1; split <= splits; split++) {
            header.add("split_" + split + "_normalised");
        }
        header.add("mean_signed");
        header.add("mean_absolute");
        header.add("standard_deviation");
        header.add("sign_consistency");
        header.add("rank");
        for (int split = 1; split <= splits; split++) {
            header.add("split_" + split + "_raw");
        }
        line(header, out);
        for (FeatureWeights feature : summary.features()) {
            List<String> fields = new ArrayList<>(header.size());
            fields.add(feature.name());
            fields.add(Boolean.toString(feature.isBias()));
            for (double value : feature.normalised()) {
                fields.add(Double.toString(value));
            }
            fields.add(Double.toString(feature.meanSigned()));
            fields.add(Double.toString(feature.meanAbsolute()));
            fields.add(Double.toString(feature.standardDeviation()));
            fields.add(words(feature.signConsistency()));
            fields.add(
                    feature.rank().isPresent() ? Integer.toString(feature.rank().getAsInt()) : "");
            for (double value : feature.raw()) {
                fields.add(Double.toString(value));
            }
            line(fields, out);
        }
    }

    /**
     * The words for a sign-consistency verdict, as design decision P10-8 names them.
     *
     * @param verdict the verdict
     * @return {@code all positive}, {@code all negative}, {@code mixed} or {@code all zero}
     */
    static String words(SignConsistency verdict) {
        return switch (verdict) {
            case ALL_POSITIVE -> "all positive";
            case ALL_NEGATIVE -> "all negative";
            case MIXED -> "mixed";
            case ALL_ZERO -> "all zero";
        };
    }

    private static void line(List<String> fields, OutputStream out) throws IOException {
        out.write((String.join("\t", fields) + "\n").getBytes(StandardCharsets.UTF_8));
    }
}
