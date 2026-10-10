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

package org.cometgui.tools.pdv.fidelity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A Percolator PSM table read by the fidelity suite's own reader: a plain split on tabs, every
 * value kept as the file's text. It shares nothing with the product's table reader (design decision
 * P11-9).
 *
 * <p>Percolator writes {@code PSMId}, {@code score}, {@code q-value}, {@code posterior_error_prob},
 * {@code peptide} and then one column per protein: {@code proteinIds} is the header of the first,
 * and every further field of the row is another protein.
 */
final class PercolatorTable {

    /** One row, as the file states it. */
    record Row(
            boolean decoy,
            int line,
            String psmId,
            String score,
            String qValue,
            String pep,
            String peptide,
            List<String> proteins) {

        /** The identity the suite matches rows on: the table, then the {@code PSMId}. */
        String key() {
            return key(decoy, psmId);
        }

        static String key(boolean decoy, String psmId) {
            return (decoy ? "decoy/" : "target/") + psmId;
        }
    }

    private PercolatorTable() {}

    /**
     * Reads a table.
     *
     * @param file the table
     * @param decoy whether it is Percolator's decoy table
     * @return its rows, in file order
     * @throws IOException if it cannot be read or is not a Percolator PSM table
     */
    static List<Row> read(Path file, boolean decoy) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            throw new IOException(file + " is empty: not a Percolator PSM table");
        }
        List<String> header = List.of(lines.get(0).split("\t", -1));
        List<String> expected =
                List.of(
                        "PSMId",
                        "score",
                        "q-value",
                        "posterior_error_prob",
                        "peptide",
                        "proteinIds");
        if (!header.equals(expected)) {
            throw new IOException(file + " header " + header + " is not " + expected);
        }
        List<Row> rows = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            String line = lines.get(index);
            String[] fields = line.split("\t", -1);
            if (fields.length < 6) {
                throw new IOException(
                        file + " line " + (index + 1) + " has " + fields.length + " fields");
            }
            List<String> proteins = new ArrayList<>();
            for (int field = 5; field < fields.length; field++) {
                proteins.add(fields[field]);
            }
            rows.add(
                    new Row(
                            decoy,
                            index + 1,
                            fields[0],
                            fields[1],
                            fields[2],
                            fields[3],
                            fields[4],
                            List.copyOf(proteins)));
        }
        return rows;
    }
}
