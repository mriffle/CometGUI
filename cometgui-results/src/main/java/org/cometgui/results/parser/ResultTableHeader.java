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

package org.cometgui.results.parser;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A result table's header: every column it names, in order, and where each {@link ResultColumn} is.
 */
public final class ResultTableHeader {

    private final List<String> columns;
    private final Map<ResultColumn, Integer> positions;

    private ResultTableHeader(List<String> columns, Map<ResultColumn, Integer> positions) {
        this.columns = List.copyOf(columns);
        this.positions = positions;
    }

    /**
     * Reads a header line.
     *
     * @param line the first line of the file, without its line terminator
     * @param file the file it came from, named in any refusal
     * @return the header
     * @throws PercolatorOutputException if a {@link ResultColumn} is absent ({@link
     *     PercolatorOutputException.Problem#MISSING_COLUMN}) or named twice ({@link
     *     PercolatorOutputException.Problem#DUPLICATE_COLUMN})
     */
    static ResultTableHeader parse(String line, Path file) throws PercolatorOutputException {
        List<String> columns = List.of(line.split("\t", -1));
        Map<ResultColumn, Integer> positions = new EnumMap<>(ResultColumn.class);
        for (ResultColumn column : ResultColumn.values()) {
            int first = columns.indexOf(column.headerName());
            if (first < 0) {
                throw new PercolatorOutputException(
                        file,
                        PercolatorOutputException.Problem.MISSING_COLUMN,
                        "The Percolator result table "
                                + file
                                + " has no '"
                                + column.headerName()
                                + "' column. Its header names "
                                + columns
                                + "; a PSM or peptide table needs "
                                + Arrays.stream(ResultColumn.values())
                                        .map(ResultColumn::headerName)
                                        .toList(),
                        null);
            }
            if (columns.lastIndexOf(column.headerName()) != first) {
                throw new PercolatorOutputException(
                        file,
                        PercolatorOutputException.Problem.DUPLICATE_COLUMN,
                        "The Percolator result table "
                                + file
                                + " names the '"
                                + column.headerName()
                                + "' column more than once, so which one holds the value is"
                                + " ambiguous. Its header names "
                                + columns,
                        null);
            }
            positions.put(column, first);
        }
        return new ResultTableHeader(columns, positions);
    }

    /**
     * Every column the header names, in order.
     *
     * @return the column names
     */
    public List<String> columns() {
        return columns;
    }

    /**
     * Where a column is.
     *
     * @param column the column
     * @return its zero-based position in the header
     */
    public int positionOf(ResultColumn column) {
        return positions.get(column);
    }

    /**
     * Whether the protein column is the header's last, so that a row's further fields are further
     * proteins.
     *
     * @return {@code true} for every table Percolator 3.06.5, 3.07.1 and 3.09 write
     */
    public boolean proteinsContinueToEndOfRow() {
        return positionOf(ResultColumn.PROTEIN_IDS) == columns.size() - 1;
    }
}
