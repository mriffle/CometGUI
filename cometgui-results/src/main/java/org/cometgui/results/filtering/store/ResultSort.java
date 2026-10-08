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

package org.cometgui.results.filtering.store;

import java.util.Objects;

/**
 * The order a query returns rows in: one column, ascending or descending.
 *
 * <p>The rules, the same in every store:
 *
 * <ul>
 *   <li><strong>Text</strong> ({@link Column#PSM_ID}, {@link Column#SOURCE_FILE}, {@link
 *       Column#PEPTIDE}, {@link Column#PROTEINS}) is ordered by {@link String#compareTo}: by UTF-16
 *       code unit, case-sensitive, independent of any locale. Proteins compare as a list, first
 *       protein first; a list that is a prefix of another sorts before it.
 *   <li><strong>Numbers</strong> ({@link Column#SCAN}, {@link Column#CHARGE}, {@link Column#SCORE},
 *       {@link Column#Q_VALUE}, {@link Column#PEP}) are ordered numerically; {@code -0} equals
 *       {@code 0}. The q-value orders by its value, never by its text.
 *   <li><strong>A row with no value goes last, in both directions</strong>: a q-value that is not
 *       known ({@code R-RES-02}), a score or PEP that is not a decimal number, and a source file,
 *       scan or charge where the {@code PSMId} is not in Comet's {@code SpecId} shape. Rows with no
 *       value keep file order among themselves.
 *   <li><strong>Ties go by file order, ascending, in both directions</strong>, so descending is not
 *       ascending reversed: two rows with equal scores appear in file order either way.
 * </ul>
 *
 * @param column the column
 * @param direction the direction
 */
public record ResultSort(Column column, Direction direction) {

    /** File order, as Percolator wrote the rows: the default. */
    public static final ResultSort FILE_ORDER =
            new ResultSort(Column.FILE_ORDER, Direction.ASCENDING);

    /**
     * A sort.
     *
     * @throws NullPointerException if either is {@code null}
     */
    public ResultSort {
        Objects.requireNonNull(column, "column");
        Objects.requireNonNull(direction, "direction");
    }

    /**
     * Ascending by a column.
     *
     * @param column the column
     * @return the sort
     */
    public static ResultSort ascending(Column column) {
        return new ResultSort(column, Direction.ASCENDING);
    }

    /**
     * Descending by a column.
     *
     * @param column the column
     * @return the sort
     */
    public static ResultSort descending(Column column) {
        return new ResultSort(column, Direction.DESCENDING);
    }

    /** A column a table can be sorted by. */
    public enum Column {
        /** The row's position in the raw file; never missing, never tied. */
        FILE_ORDER,
        /** {@code PSMId}, as written. */
        PSM_ID,
        /** The {@code -N} base the {@code PSMId} begins with, standing for the source file. */
        SOURCE_FILE,
        /** The scan number read from the {@code PSMId}. */
        SCAN,
        /** The precursor charge read from the {@code PSMId}. */
        CHARGE,
        /** {@code peptide}, as written. */
        PEPTIDE,
        /** {@code proteinIds}, every protein in order. */
        PROTEINS,
        /** Percolator's score. */
        SCORE,
        /** The q-value. */
        Q_VALUE,
        /** The posterior error probability. */
        PEP
    }

    /** Which way a column is ordered. */
    public enum Direction {
        /** Smallest first. */
        ASCENDING,
        /** Largest first. */
        DESCENDING
    }
}
