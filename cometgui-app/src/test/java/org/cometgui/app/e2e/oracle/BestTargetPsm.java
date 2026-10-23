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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The PSM canonical scenario step 25 draws, chosen by the test from Percolator's raw target PSM
 * table and never asked of the product (P14-7): the best-scoring target PSM whose q-value is at
 * most a cutoff and whose peptide carries no modification -- a "known PSM" whose identity, score
 * and peptide the scenario states before it touches the interface.
 *
 * <p>The table is read on the JDK alone: lines split by {@link String#split}, the columns {@code
 * PSMId}, {@code score}, {@code q-value} and {@code peptide} found by their header names, every
 * number read as an exact {@link BigDecimal}. A row whose score or q-value is not a plain decimal
 * number is never chosen. A modified peptide is one with a bracket in it ({@code M[15.9949]}); it
 * is passed over so that the peptide PDV annotates can be compared, letter for letter, with the
 * table's. Of equal best scores the first in the file is chosen.
 *
 * @param psmId the row's {@code PSMId}
 * @param score its score, as written
 * @param qValue its q-value, as written
 * @param peptide its peptide with the flanking residues, as written, for example {@code
 *     K.PEPTIDE.R}
 * @param candidates how many rows were eligible: at most the cutoff, numeric, unmodified
 */
public record BestTargetPsm(
        String psmId, String score, String qValue, String peptide, int candidates) {

    private static final Pattern NUMBER =
            Pattern.compile("[+-]?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)([eE][+-]?[0-9]+)?");

    /**
     * The peptide without its flanking residues: {@code K.PEPTIDE.R} gives {@code PEPTIDE}.
     *
     * @return the sequence between the first and the last dot
     */
    public String sequence() {
        return peptide.substring(peptide.indexOf('.') + 1, peptide.lastIndexOf('.'));
    }

    /**
     * Chooses the PSM.
     *
     * @param table Percolator's {@code --results-psms} file
     * @param cutoff the q-value cutoff, exactly as typed, for example {@code "0.01"}
     * @return the best-scoring eligible row
     * @throws IOException if the file cannot be read
     * @throws AssertionError if the header lacks a column, or no row is eligible
     */
    public static BestTargetPsm of(Path table, String cutoff) throws IOException {
        BigDecimal limit = new BigDecimal(cutoff);
        List<String> lines = Files.readAllLines(table, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            throw new AssertionError(table + " is empty");
        }
        List<String> header = Arrays.asList(lines.get(0).split("\t", -1));
        int id = column(header, "PSMId", table);
        int score = column(header, "score", table);
        int q = column(header, "q-value", table);
        int peptide = column(header, "peptide", table);
        String[] best = null;
        BigDecimal bestScore = null;
        int candidates = 0;
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) {
                continue;
            }
            String[] fields = line.split("\t", -1);
            if (fields.length <= Math.max(Math.max(id, score), Math.max(q, peptide))) {
                continue;
            }
            if (!NUMBER.matcher(fields[q]).matches()
                    || !NUMBER.matcher(fields[score]).matches()
                    || new BigDecimal(fields[q]).compareTo(limit) > 0
                    || fields[peptide].contains("[")) {
                continue;
            }
            candidates++;
            BigDecimal value = new BigDecimal(fields[score]);
            if (bestScore == null || value.compareTo(bestScore) > 0) {
                bestScore = value;
                best = fields;
            }
        }
        if (best == null) {
            throw new AssertionError(
                    table + " has no unmodified target PSM with a q-value of at most " + cutoff);
        }
        return new BestTargetPsm(best[id], best[score], best[q], best[peptide], candidates);
    }

    private static int column(List<String> header, String name, Path table) {
        int index = header.indexOf(name);
        if (index < 0) {
            throw new AssertionError(table + " has no " + name + " column: " + header);
        }
        return index;
    }
}
