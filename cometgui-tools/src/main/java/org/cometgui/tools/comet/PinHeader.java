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

package org.cometgui.tools.comet;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The header line of a PIN file -- Percolator's tab-separated input, which Comet writes with {@code
 * output_percolatorfile = 1} -- and the rule that says which of its columns are features.
 *
 * <h2>The column rule</h2>
 *
 * <p>A PIN header is {@link #LEADING} ({@code SpecId}, {@code Label}, {@code ScanNr}), then one or
 * more <strong>feature columns</strong>, then {@link #TRAILING} ({@code Peptide}, {@code
 * Proteins}). Everything strictly between {@code ScanNr} and {@code Peptide} is a feature column.
 * Comet 2026.03.0 writes 28 columns, so 23 features: {@code ExpMass CalcMass lnrSp deltLCn deltCn
 * lnExpect Xcorr Sp IonFrac Mass PepLen Charge1..Charge6 enzN enzC enzInt lnNumSP dM absdM}
 * (measured, and the same 28 {@code docs/feasibility/scientific-path.rst} records for 2026.02.2).
 *
 * <p>Percolator itself treats {@code ExpMass} and {@code CalcMass} as optional descriptive columns
 * rather than features. They are counted as features here deliberately: this rule exists to compare
 * files with one another, and a narrower set would let two files that disagree about those columns
 * merge silently. Counting them is only ever stricter.
 *
 * <p>Refused: fewer than {@code LEADING} plus one feature plus {@code TRAILING} columns, a leading
 * or trailing column missing or out of place, an empty column name, and a name given twice.
 *
 * @param columns every column name, in order
 */
public record PinHeader(List<String> columns) {

    /** The fixed columns a PIN header begins with. */
    public static final List<String> LEADING = List.of("SpecId", "Label", "ScanNr");

    /** The fixed columns a PIN header ends with. */
    public static final List<String> TRAILING = List.of("Peptide", "Proteins");

    /**
     * Validates the header.
     *
     * @throws NullPointerException if {@code columns} or a name is {@code null}
     * @throws IllegalArgumentException if the header breaks the column rule, saying how
     */
    public PinHeader {
        columns = List.copyOf(columns);
        int size = columns.size();
        if (size < LEADING.size() + 1 + TRAILING.size()) {
            throw new IllegalArgumentException(
                    "its header has "
                            + size
                            + (size == 1 ? " column" : " columns")
                            + ", fewer than SpecId, Label, ScanNr, one feature, Peptide and"
                            + " Proteins");
        }
        if (!columns.subList(0, LEADING.size()).equals(LEADING)) {
            throw new IllegalArgumentException(
                    "its header begins "
                            + columns.subList(0, LEADING.size())
                            + " where a PIN header begins "
                            + LEADING);
        }
        if (!columns.subList(size - TRAILING.size(), size).equals(TRAILING)) {
            throw new IllegalArgumentException(
                    "its header ends "
                            + columns.subList(size - TRAILING.size(), size)
                            + " where a PIN header ends "
                            + TRAILING);
        }
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < size; index++) {
            String name = columns.get(index);
            if (name.isEmpty()) {
                throw new IllegalArgumentException(
                        "its header's column " + (index + 1) + " has no name");
            }
            if (!seen.add(name)) {
                throw new IllegalArgumentException(
                        "its header names the column \"" + name + "\" twice");
            }
        }
    }

    /**
     * Parses a header line: the column names separated by tabs, without the line terminator.
     *
     * @param line the header line
     * @return the header
     * @throws IllegalArgumentException if the line breaks the column rule
     */
    public static PinHeader parse(String line) {
        Objects.requireNonNull(line, "line");
        return new PinHeader(List.of(line.split("\t", -1)));
    }

    /**
     * Every column name, in order.
     *
     * @return the columns, immutable
     */
    @Override
    public List<String> columns() {
        return List.copyOf(columns);
    }

    /**
     * The feature columns: everything between {@code ScanNr} and {@code Peptide}.
     *
     * @return the feature column names, in order, immutable
     */
    public List<String> featureColumns() {
        return List.copyOf(columns.subList(LEADING.size(), columns.size() - TRAILING.size()));
    }
}
