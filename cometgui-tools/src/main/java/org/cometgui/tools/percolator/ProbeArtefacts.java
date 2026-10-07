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

package org.cometgui.tools.percolator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * The capability probe's reading of the tab-separated artefacts a probe run wrote: just enough to
 * say "this artefact was written, with the expected header and the expected rows", and no more.
 *
 * <p><strong>This is not a results parser and must not grow into one.</strong> The PSM, peptide and
 * weights parsers are {@code org.cometgui.results.parser}'s (phase 09 design decision P9-3), and a
 * second set here would be the duplicated design {@code ONBOARDING.rst} warns about. What the probe
 * needs is narrower: a verdict on one file, over a fixture whose every row this product generated,
 * so it knows exactly how many rows of which kind a capable build must write.
 *
 * <h2>The table check</h2>
 *
 * <p>Observed 2026-10-07 from the real 3.06.5, 3.07.1 and 3.09 binaries over the probe's 64 plus 64
 * fixture: every table -- {@code --results-psms}, {@code --results-peptides}, {@code
 * --decoy-results-psms}, {@code --decoy-results-peptides}, and the peptide table a run prints on
 * standard output when no {@code --results-peptides} is given -- is a header {@code
 * PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds} and 64 rows, and the decoy
 * tables' rows are exactly the fixture's decoys. The check requires every one of those six column
 * names (by name, not by position, so a release that adds a column is not refused for it), exactly
 * the expected number of rows, and every row's protein to be a decoy or a target as the table
 * requires -- so a decoy file holding the targets, or a target file holding the decoys, is not the
 * artefact it claims to be.
 *
 * <p><strong>A known limit, recorded rather than hidden.</strong> The fixture's 128 peptides are
 * all distinct, so its PSM table and its peptide table have the same row count, and this check
 * cannot tell a build that wrote the peptide table to {@code --results-psms} from one that wrote
 * the PSM table. The fixture's bytes are pinned by {@code SyntheticPinTest} and its seed was
 * measured ({@code R-PERC-02}), so the probe does not change the fixture to buy that distinction.
 *
 * <h2>The weights check</h2>
 *
 * <p>The weights file names its own layout: comment lines starting {@code #}, then for each
 * cross-validation bin a header of feature names, a normalised-weights row and a raw-weights row.
 * The check requires at least one bin, every bin's header to name every feature of the fixture, and
 * both of its rows to be the header's width of finite numbers. How many bins there are is the
 * parser's question, not the probe's.
 */
final class ProbeArtefacts {

    /** The columns every result table the probe reads must carry, observed on all three builds. */
    static final List<String> RESULT_COLUMNS =
            List.of("PSMId", "score", "q-value", "posterior_error_prob", "peptide", "proteinIds");

    private static final String PROTEIN_COLUMN = "proteinIds";
    private static final int ROWS_PER_WEIGHTS_BIN = 3;

    private ProbeArtefacts() {}

    /**
     * Whether a file holds a result table of the expected size and kind.
     *
     * @param written the file the run was told to write
     * @param expectedRows how many rows a capable build writes over the fixture
     * @param decoys {@code true} if every row must be a decoy, {@code false} if every row must be a
     *     target
     * @return {@code true} only for a readable file that passes {@link #isResultTable}
     */
    static boolean isResultFile(Path written, int expectedRows, boolean decoys) {
        return isResultTable(linesOf(written), expectedRows, decoys);
    }

    /**
     * Whether lines hold a result table of the expected size and kind.
     *
     * @param lines the table, header first; an empty list is not a table
     * @param expectedRows how many rows a capable build writes over the fixture
     * @param decoys {@code true} if every row must be a decoy, {@code false} if every row must be a
     *     target
     * @return {@code true} when the header names every {@link #RESULT_COLUMNS} column and exactly
     *     {@code expectedRows} rows follow, each of the required kind
     */
    static boolean isResultTable(List<String> lines, int expectedRows, boolean decoys) {
        if (lines.isEmpty()) {
            return false;
        }
        List<String> header = Arrays.asList(lines.get(0).split("\t", -1));
        if (!header.containsAll(RESULT_COLUMNS)) {
            return false;
        }
        List<String> rows = lines.subList(1, lines.size());
        if (rows.size() != expectedRows) {
            return false;
        }
        int proteinColumn = header.indexOf(PROTEIN_COLUMN);
        for (String row : rows) {
            String[] fields = row.split("\t", -1);
            if (fields.length < header.size()) {
                return false;
            }
            boolean decoyRow = fields[proteinColumn].startsWith(SyntheticPin.DECOY_PROTEIN_PREFIX);
            if (decoyRow != decoys) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a file holds learned weights for the fixture's features.
     *
     * @param written the file the run was told to write
     * @return {@code true} for a readable file with at least one complete bin, every bin naming
     *     every {@link SyntheticPin#FEATURE_NAMES} feature and carrying two rows of finite numbers
     *     as wide as its header
     */
    static boolean isWeightsFile(Path written) {
        List<String> data =
                linesOf(written).stream().filter(line -> !line.startsWith("#")).toList();
        if (data.isEmpty() || data.size() % ROWS_PER_WEIGHTS_BIN != 0) {
            return false;
        }
        for (int bin = 0; bin < data.size(); bin += ROWS_PER_WEIGHTS_BIN) {
            List<String> features = Arrays.asList(data.get(bin).split("\t", -1));
            if (!features.containsAll(SyntheticPin.FEATURE_NAMES)) {
                return false;
            }
            if (!isNumericRow(data.get(bin + 1), features.size())
                    || !isNumericRow(data.get(bin + 2), features.size())) {
                return false;
            }
        }
        return true;
    }

    private static boolean isNumericRow(String row, int width) {
        String[] fields = row.split("\t", -1);
        if (fields.length != width) {
            return false;
        }
        for (String field : fields) {
            try {
                if (!Double.isFinite(Double.parseDouble(field))) {
                    return false;
                }
            } catch (NumberFormatException notANumber) {
                return false;
            }
        }
        return true;
    }

    /*
     * An artefact that is absent, unreadable or not UTF-8 is no artefact: the verdict is "not
     * written", which R-TOOL-08 turns into an absent capability.  The run itself already proved the
     * binary started -- the banner was checked first -- so this cannot hide a loader failure.
     */
    private static List<String> linesOf(Path written) {
        try {
            return Files.readAllLines(written, StandardCharsets.UTF_8);
        } catch (IOException notWritten) {
            return List.of();
        }
    }
}
