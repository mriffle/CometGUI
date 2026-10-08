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

package org.cometgui.results.testing;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * The minimal independent q-value counter of design decision P10-3: a Percolator table read with
 * {@code String.split("\t", -1)}, its {@code q-value} field compared with the cutoff as an exact
 * {@link BigDecimal}, inclusively ({@code q <= cutoff}).
 *
 * <p>It is what later gate tests compare the results store, the interface and the export against,
 * so it must not share code with them: it uses <strong>no {@code org.cometgui.results} production
 * class</strong> -- not the reader, not {@code QValue}, not the filter, not their count types --
 * and {@code IndependentCounterTest} proves that from its compiled bytes. It is checked in turn
 * against numbers nobody's Java produced: the large fixture's manifest (Python {@code decimal}),
 * and {@code awk} counts pinned for the real Percolator outputs.
 *
 * <p>The unknown category ({@code R-RES-02}) is the specification's policy restated here on its
 * own: an empty field is {@code MISSING}; text that is not a decimal number in {@code Locale.ROOT}
 * form (optional sign, digits with a {@code .}, optional exponent) is {@code UNPARSABLE} -- {@code
 * NaN}, {@code nan}, {@code inf}, {@code Infinity}, {@code 0,01}; a decimal outside {@code [0, 1]}
 * is {@code OUT_OF_RANGE}. Every other row is known, and passes or fails each cutoff.
 */
public final class IndependentCounter {

    /** The reason an empty q-value field is unknown. */
    public static final String MISSING = "MISSING";

    /** The reason a q-value field that is not a decimal number is unknown. */
    public static final String UNPARSABLE = "UNPARSABLE";

    /** The reason a decimal q-value outside {@code [0, 1]} is unknown. */
    public static final String OUT_OF_RANGE = "OUT_OF_RANGE";

    private static final Pattern DECIMAL =
            Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");

    private IndependentCounter() {}

    /**
     * Counts one table at several cutoffs in one pass.
     *
     * @param table a Percolator PSM or peptide table, header first
     * @param cutoffs the cutoffs, each as decimal text such as {@code 0.01}
     * @return the tally
     * @throws IOException if the file cannot be read
     * @throws IllegalArgumentException if the header has no {@code q-value} column or a row is too
     *     short to hold one
     */
    public static Tally count(Path table, List<String> cutoffs) throws IOException {
        BigDecimal[] limits = new BigDecimal[cutoffs.size()];
        for (int i = 0; i < limits.length; i++) {
            limits[i] = new BigDecimal(cutoffs.get(i));
        }
        long[] passing = new long[limits.length];
        long total = 0;
        long known = 0;
        Map<String, Long> unknownByText = new TreeMap<>();
        Map<String, String> reasonByText = new TreeMap<>();
        try (BufferedReader in = Files.newBufferedReader(table, StandardCharsets.UTF_8)) {
            String header = in.readLine();
            if (header == null) {
                throw new IllegalArgumentException(table + " is empty: no header");
            }
            int column = Arrays.asList(header.split("\t", -1)).indexOf("q-value");
            if (column < 0) {
                throw new IllegalArgumentException(table + " has no q-value column: " + header);
            }
            String line;
            while ((line = in.readLine()) != null) {
                total++;
                String[] fields = line.split("\t", -1);
                if (fields.length <= column) {
                    throw new IllegalArgumentException(
                            table + " data row " + total + " has no q-value field: " + line);
                }
                String q = fields[column];
                String reason = reason(q);
                if (reason != null) {
                    unknownByText.merge(q, 1L, Long::sum);
                    reasonByText.put(q, reason);
                    continue;
                }
                known++;
                BigDecimal value = new BigDecimal(q);
                for (int i = 0; i < limits.length; i++) {
                    if (value.compareTo(limits[i]) <= 0) {
                        passing[i]++;
                    }
                }
            }
        }
        Map<String, IndependentCounts> byCutoff = new LinkedHashMap<>();
        long unknown = total - known;
        for (int i = 0; i < limits.length; i++) {
            byCutoff.put(
                    cutoffs.get(i),
                    new IndependentCounts(total, passing[i], known - passing[i], unknown));
        }
        return new Tally(total, byCutoff, unknownByText, reasonByText);
    }

    /**
     * Why a q-value field is unknown, or that it is not.
     *
     * @param q the field exactly as written
     * @return {@link #MISSING}, {@link #UNPARSABLE} or {@link #OUT_OF_RANGE}; {@code null} for a
     *     known q-value
     */
    public static String reason(String q) {
        if (q.isEmpty()) {
            return MISSING;
        }
        if (!DECIMAL.matcher(q).matches()) {
            return UNPARSABLE;
        }
        BigDecimal value = new BigDecimal(q);
        if (value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            return OUT_OF_RANGE;
        }
        return null;
    }

    /**
     * One table, counted.
     *
     * @param total every data row
     * @param byCutoff the counts at each cutoff asked for, keyed by its text, in the order asked
     * @param unknownByText the unknown q-value fields, by their exact text, and how many rows have
     *     each
     * @param reasonByText why each of those is unknown
     */
    public record Tally(
            long total,
            Map<String, IndependentCounts> byCutoff,
            Map<String, Long> unknownByText,
            Map<String, String> reasonByText) {

        /** Copies the maps, so a tally cannot change after it is made. */
        public Tally {
            byCutoff = Collections.unmodifiableMap(new LinkedHashMap<>(byCutoff));
            unknownByText = Map.copyOf(unknownByText);
            reasonByText = Map.copyOf(reasonByText);
        }

        /**
         * The counts at one cutoff.
         *
         * @param cutoff the cutoff's text, as it was given to {@link #count}
         * @return the counts
         * @throws IllegalArgumentException if that cutoff was not counted
         */
        public IndependentCounts at(String cutoff) {
            IndependentCounts counts = byCutoff.get(cutoff);
            if (counts == null) {
                throw new IllegalArgumentException(
                        "cutoff " + cutoff + " was not counted; counted " + byCutoff.keySet());
            }
            return counts;
        }
    }
}
