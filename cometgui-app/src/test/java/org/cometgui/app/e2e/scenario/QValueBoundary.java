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

package org.cometgui.app.e2e.scenario;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * A q-value that a Percolator table really holds, at most a ceiling, with how many rows hold
 * exactly it -- read by the scenario's own code from the raw file (P14-7).
 *
 * <p>Why it exists: the specification's own cutoffs (0.01, 0.005, 0.02) need not be q-values any
 * row has, and where no row's q-value equals the cutoff, filtering with {@code <} and with {@code
 * <=} give the same counts -- so a test that only used those cutoffs could not go red on the defect
 * the canonical scenario is designed to catch ("filtering uses {@code <} instead of {@code <=}").
 * Typing a cutoff that some rows equal exactly makes the two comparisons disagree by those rows,
 * and {@link #ties()} is the size of the disagreement.
 *
 * @param text the q-value exactly as the file writes it, which is what the scenario types
 * @param value the same, as an exact number
 * @param ties how many data rows hold exactly this q-value
 */
public record QValueBoundary(String text, BigDecimal value, long ties) {

    /**
     * The largest q-value of a table that is at most a ceiling.
     *
     * @param table a Percolator {@code --results-psms} or {@code --results-peptides} file
     * @param ceiling the ceiling, for example {@code 0.01}
     * @return the boundary
     * @throws IOException if the file cannot be read
     * @throws AssertionError if the file has no q-value column or no q-value at most the ceiling
     */
    public static QValueBoundary atMost(Path table, String ceiling) throws IOException {
        BigDecimal limit = new BigDecimal(ceiling);
        List<String> lines = Files.readAllLines(table, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            throw new AssertionError(table + " has no header line");
        }
        int column = Arrays.asList(lines.get(0).split("\t", -1)).indexOf("q-value");
        if (column < 0) {
            throw new AssertionError(table + " has no q-value column: " + lines.get(0));
        }
        String best = null;
        BigDecimal bestValue = null;
        for (String line : lines.subList(1, lines.size())) {
            String[] fields = line.split("\t", -1);
            if (line.isBlank() || fields.length <= column) {
                continue;
            }
            BigDecimal q;
            try {
                q = new BigDecimal(fields[column]);
            } catch (NumberFormatException notANumber) {
                continue;
            }
            if (q.compareTo(limit) <= 0 && (bestValue == null || q.compareTo(bestValue) > 0)) {
                best = fields[column];
                bestValue = q;
            }
        }
        if (best == null) {
            throw new AssertionError(table + " has no q-value at most " + ceiling);
        }
        long ties = 0;
        for (String line : lines.subList(1, lines.size())) {
            String[] fields = line.split("\t", -1);
            if (fields.length > column) {
                try {
                    if (new BigDecimal(fields[column]).compareTo(bestValue) == 0) {
                        ties++;
                    }
                } catch (NumberFormatException notANumber) {
                    // not a q-value: neither a tie nor anything else
                }
            }
        }
        return new QValueBoundary(best, bestValue, ties);
    }
}
