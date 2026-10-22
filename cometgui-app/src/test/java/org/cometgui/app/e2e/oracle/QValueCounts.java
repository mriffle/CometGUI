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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.cometgui.app.testing.IndependentCounts;
import org.cometgui.app.testing.IndependentCounts.Row;

/**
 * How many rows of a Percolator target PSM or peptide table pass a q-value cutoff, counted by the
 * test's own code (P14-7) -- canonical scenario steps 21 to 23: "independently compute the number
 * of PSMs with q &lt;= 0.01 from the raw Percolator file and compare with the GUI count".
 *
 * <p>The counting is {@link IndependentCounts}'s, which Phase 10 wrote for the Results section's
 * gate under the same rule and which uses no {@code org.cometgui} class at all (its imports are
 * {@code java.*} and JUnit's {@code fail}): the file's bytes split into lines and tabs by {@link
 * String#split}, the {@code q-value} column found by its header name, and each q-value read as an
 * exact {@link java.math.BigDecimal}, so {@code 9.27998e-07} is the number it says and {@code
 * 0.0100000001} is not {@code 0.01}. A row passes when its q-value is <em>at most</em> the cutoff,
 * compared exactly; one that is empty, not a plain decimal number, or outside {@code [0, 1]} is
 * <em>unknown</em> -- counted on its own and never as passing.
 *
 * @param total every data row
 * @param passing known and at most the cutoff
 * @param failing known and above the cutoff
 * @param unknown not a usable q-value
 * @param passingIds the first field ({@code PSMId}) of each passing row, in file order
 */
public record QValueCounts(
        long total, long passing, long failing, long unknown, List<String> passingIds) {

    /** The identifiers, copied. */
    public QValueCounts {
        passingIds = List.copyOf(passingIds);
    }

    /**
     * Counts a table under a cutoff.
     *
     * @param table a Percolator {@code --results-psms} or {@code --results-peptides} file
     * @param cutoff the cutoff exactly as typed, for example {@code "0.01"}
     * @return the counts
     * @throws IOException if the file cannot be read
     * @throws AssertionError if the file has no header or no {@code q-value} column
     * @throws NumberFormatException if {@code cutoff} is not a decimal number
     */
    public static QValueCounts of(Path table, String cutoff) throws IOException {
        long passing = 0;
        long failing = 0;
        long unknown = 0;
        List<String> ids = new ArrayList<>();
        List<Row> rows = IndependentCounts.rows(table, cutoff);
        for (Row row : rows) {
            switch (row.where()) {
                case PASSING -> {
                    passing++;
                    ids.add(row.psmId());
                }
                case FAILING -> failing++;
                case UNKNOWN -> unknown++;
            }
        }
        return new QValueCounts(rows.size(), passing, failing, unknown, ids);
    }
}
