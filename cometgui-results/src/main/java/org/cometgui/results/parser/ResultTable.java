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
import java.util.List;
import java.util.Objects;

/**
 * A whole result table in memory, as {@link ResultTableReader#readAll} reads it.
 *
 * @param file the file it was read from
 * @param header its header
 * @param rows every row, in file order
 * @param counts the counts over every row
 */
public record ResultTable(
        Path file, ResultTableHeader header, List<ResultRow> rows, ResultTableCounts counts) {

    /**
     * A table.
     *
     * @throws IllegalArgumentException if {@code counts} does not count {@code rows}
     */
    public ResultTable {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(header, "header");
        rows = List.copyOf(rows);
        if (counts.rows() != rows.size()) {
            throw new IllegalArgumentException(
                    "the counts say " + counts.rows() + " rows, but " + rows.size() + " were read");
        }
    }

    /**
     * The rows whose q-value is not known, which R-RES-02 shows as their own category.
     *
     * @return those rows, in file order
     */
    public List<ResultRow> unknownQValueRows() {
        return rows.stream().filter(row -> !row.qValue().isKnown()).toList();
    }
}
