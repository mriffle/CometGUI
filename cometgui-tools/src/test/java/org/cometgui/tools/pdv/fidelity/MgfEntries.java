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

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The entries of an MGF, in file order, as PDV numbers them: the {@code k}-th {@code BEGIN IONS}
 * block is entry {@code k}, 0-based. Read by the fidelity suite's own parser (P11-9); every value
 * is kept as the file's text.
 */
final class MgfEntries {

    /** One entry: its header lines and its peaks, each as the file's text. */
    record Entry(int ordinal, Map<String, String> headers, List<String[]> peaks) {}

    private MgfEntries() {}

    /**
     * Reads an MGF.
     *
     * @param file the file
     * @return every entry, in order
     * @throws IOException if it cannot be read or is malformed
     */
    static List<Entry> read(Path file) throws IOException {
        List<Entry> entries = new ArrayList<>();
        try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Map<String, String> headers = null;
            List<String[]> peaks = null;
            int number = 0;
            for (String line = in.readLine(); line != null; line = in.readLine()) {
                number++;
                if ("BEGIN IONS".equals(line)) {
                    if (headers != null) {
                        throw new IOException(file + " line " + number + ": nested BEGIN IONS");
                    }
                    headers = new LinkedHashMap<>();
                    peaks = new ArrayList<>();
                } else if ("END IONS".equals(line)) {
                    if (headers == null) {
                        throw new IOException(file + " line " + number + ": END IONS alone");
                    }
                    entries.add(new Entry(entries.size(), headers, peaks));
                    headers = null;
                    peaks = null;
                } else if (headers == null) {
                    if (!line.isBlank()) {
                        throw new IOException(
                                file + " line " + number + ": text outside an entry: " + line);
                    }
                } else if (!line.isEmpty() && Character.isLetter(line.charAt(0))) {
                    int equals = line.indexOf('=');
                    if (equals < 0) {
                        throw new IOException(file + " line " + number + ": " + line);
                    }
                    String key = line.substring(0, equals);
                    if (headers.put(key, line.substring(equals + 1)) != null) {
                        throw new IOException(file + " line " + number + ": repeated " + key);
                    }
                } else {
                    String[] fields = line.strip().split("[ \t]+");
                    if (fields.length < 2) {
                        throw new IOException(file + " line " + number + ": peak " + line);
                    }
                    peaks.add(fields);
                }
            }
            if (headers != null) {
                throw new IOException(file + " ends inside an entry");
            }
        }
        return entries;
    }
}
