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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An mzTab as PDV's {@code MztabImport} reads it, parsed by the fidelity suite itself (P11-9): the
 * {@code MTD} lines in order, the one {@code PSH} header, and each {@code PSM} line as a map from
 * column name to the field's text. The shape PDV requires is checked while parsing (P11-15): no
 * line of any other kind, every row exactly as wide as the header, and no empty field.
 *
 * @param metadata every {@code MTD} key and value, in file order
 * @param columns the {@code PSH} columns, in order
 * @param rows every {@code PSM} line, in order
 * @param shape every way the file breaks PDV's shape, empty when it has none
 */
record MzTabFile(
        Map<String, String> metadata,
        List<String> columns,
        List<Map<String, String>> rows,
        List<String> shape) {

    /**
     * Parses an mzTab file.
     *
     * @param file the file
     * @return its content
     * @throws IOException if it cannot be read
     */
    static MzTabFile read(Path file) throws IOException {
        return parse(Files.readAllLines(file, StandardCharsets.UTF_8));
    }

    /**
     * Parses an mzTab's lines.
     *
     * @param lines the lines
     * @return their content
     */
    static MzTabFile parse(List<String> lines) {
        Map<String, String> metadata = new LinkedHashMap<>();
        List<String> columns = null;
        List<Map<String, String>> rows = new ArrayList<>();
        List<String> shape = new ArrayList<>();
        int number = 0;
        for (String line : lines) {
            number++;
            String[] fields = line.split("\t", -1);
            switch (fields[0]) {
                case "MTD" -> {
                    if (columns != null) {
                        shape.add("line " + number + ": MTD after the PSH header");
                    }
                    if (fields.length != 3 || fields[1].isEmpty() || fields[2].isEmpty()) {
                        shape.add("line " + number + ": MTD is not key and value: " + line);
                    } else if (metadata.put(fields[1], fields[2]) != null) {
                        shape.add("line " + number + ": MTD key repeated: " + fields[1]);
                    }
                }
                case "PSH" -> {
                    if (columns != null) {
                        shape.add("line " + number + ": a second PSH header");
                    }
                    columns = List.of(fields).subList(1, fields.length);
                    if (columns.size() != columns.stream().distinct().count()) {
                        shape.add("line " + number + ": a PSH column name repeats");
                    }
                }
                case "PSM" -> {
                    if (columns == null) {
                        shape.add("line " + number + ": PSM before the PSH header");
                        break;
                    }
                    if (fields.length - 1 != columns.size()) {
                        shape.add(
                                "line "
                                        + number
                                        + ": "
                                        + (fields.length - 1)
                                        + " fields under "
                                        + columns.size()
                                        + " columns");
                        break;
                    }
                    Map<String, String> row = new LinkedHashMap<>();
                    for (int index = 0; index < columns.size(); index++) {
                        if (fields[index + 1].isEmpty()) {
                            shape.add("line " + number + ": empty field in " + columns.get(index));
                        }
                        row.put(columns.get(index), fields[index + 1]);
                    }
                    rows.add(row);
                }
                default -> shape.add("line " + number + ": not MTD, PSH or PSM: " + line);
            }
        }
        if (columns == null) {
            shape.add("no PSH header");
            columns = List.of();
        }
        return new MzTabFile(metadata, columns, rows, shape);
    }
}
