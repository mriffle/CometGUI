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

package org.cometgui.ui.viewmodel.results;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.cometgui.results.filtering.store.ResultSort;
import org.cometgui.results.filtering.store.TableKind;

/**
 * A column of a results table (design decision P10-9): the PSM table shows all nine, the peptide
 * table the last five. Each sorts by the store's own column of the same meaning ({@link
 * ResultSort.Column}), so a sort is never done here.
 */
public enum ResultsColumn {

    /** Percolator's {@code PSMId}, as written. */
    PSM_ID("PSMId", ResultSort.Column.PSM_ID, false),

    /** The spectrum file the {@code PSMId}'s Comet {@code -N} base stands for. */
    SOURCE_FILE("Source file", ResultSort.Column.SOURCE_FILE, false),

    /** The scan number read from the {@code PSMId}. */
    SCAN("Scan", ResultSort.Column.SCAN, false),

    /** The precursor charge read from the {@code PSMId}. */
    CHARGE("Charge", ResultSort.Column.CHARGE, false),

    /** The peptide, as written. */
    PEPTIDE("Peptide", ResultSort.Column.PEPTIDE, true),

    /** Every protein the row names. */
    PROTEINS("Proteins", ResultSort.Column.PROTEINS, true),

    /** Percolator's score, as written. */
    SCORE("Score", ResultSort.Column.SCORE, true),

    /** The q-value, as written. */
    Q_VALUE("q-value", ResultSort.Column.Q_VALUE, true),

    /** The posterior error probability, as written. */
    PEP("PEP", ResultSort.Column.PEP, true);

    private final String label;

    private final ResultSort.Column sortColumn;

    private final boolean inPeptideTable;

    ResultsColumn(String label, ResultSort.Column sortColumn, boolean inPeptideTable) {
        this.label = label;
        this.sortColumn = sortColumn;
        this.inPeptideTable = inPeptideTable;
    }

    /**
     * The column's heading, also the copied text's header cell.
     *
     * @return for example {@code q-value}
     */
    public String label() {
        return label;
    }

    /**
     * The store's column this one sorts by.
     *
     * @return the sort column
     */
    public ResultSort.Column sortColumn() {
        return sortColumn;
    }

    /**
     * Whether a table of a kind has this column.
     *
     * @param kind the table's kind
     * @return {@code true} for every column of a PSM table and the last five of a peptide table
     */
    public boolean isIn(TableKind kind) {
        return kind.isPsms() || inPeptideTable;
    }

    /**
     * The columns of a kind of table, in display order.
     *
     * @param kind the table's kind
     * @return the columns
     */
    public static List<ResultsColumn> of(TableKind kind) {
        Objects.requireNonNull(kind, "kind");
        List<ResultsColumn> columns = new ArrayList<>();
        for (ResultsColumn column : values()) {
            if (column.isIn(kind)) {
                columns.add(column);
            }
        }
        return List.copyOf(columns);
    }
}
