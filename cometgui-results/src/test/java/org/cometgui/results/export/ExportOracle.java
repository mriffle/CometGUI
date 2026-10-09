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

package org.cometgui.results.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.cometgui.results.testing.IndependentCounter;
import org.cometgui.results.testing.IndependentCounts;

/**
 * What an export of a raw table should hold, worked out a second way and using <strong>no {@code
 * org.cometgui.results} production class</strong>: the raw file's bytes split into lines -- each
 * kept with its own terminator, split at {@code \n}, {@code \r} or {@code \r\n} -- the q-value
 * column found by name in the header, and every q-value judged by {@link IndependentCounter}'s
 * policy and exact {@link BigDecimal} comparison. The category is passed by its name ({@code
 * PASSING}, {@code UNKNOWN_Q_VALUE}, {@code FAILING}, {@code ALL}).
 */
final class ExportOracle {

    private ExportOracle() {}

    /**
     * The expected export.
     *
     * @param bytes the whole file: the header line and the selected lines, terminators included
     * @param rows how many rows were selected
     * @param counts where every row falls under the cutoff
     * @param lines every line of the raw file, the header first, terminators included
     * @param selected the selected rows' line numbers, counting the header as line 1
     */
    record Expected(
            byte[] bytes,
            long rows,
            IndependentCounts counts,
            List<byte[]> lines,
            List<Long> selected) {}

    static Expected expect(Path raw, String cutoff, String category) throws IOException {
        List<byte[]> lines = lines(Files.readAllBytes(raw));
        BigDecimal limit = new BigDecimal(cutoff);
        int column = Arrays.asList(text(lines.get(0)).split("\t", -1)).indexOf("q-value");
        if (column < 0) {
            throw new IllegalArgumentException(raw + " has no q-value column");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(lines.get(0));
        long passing = 0;
        long failing = 0;
        long unknown = 0;
        List<Long> selected = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            String where = visibility(text(lines.get(index)).split("\t", -1)[column], limit);
            switch (where) {
                case "PASSING" -> passing++;
                case "FAILING" -> failing++;
                default -> unknown++;
            }
            if ("ALL".equals(category) || where.equals(category)) {
                out.write(lines.get(index));
                selected.add((long) index + 1);
            }
        }
        return new Expected(
                out.toByteArray(),
                selected.size(),
                new IndependentCounts(passing + failing + unknown, passing, failing, unknown),
                lines,
                selected);
    }

    /**
     * Where a q-value's text falls under a cutoff.
     *
     * @return {@code PASSING}, {@code FAILING} or {@code UNKNOWN_Q_VALUE}
     */
    static String visibility(String q, BigDecimal limit) {
        if (IndependentCounter.reason(q) != null) {
            return "UNKNOWN_Q_VALUE";
        }
        return new BigDecimal(q).compareTo(limit) <= 0 ? "PASSING" : "FAILING";
    }

    /** A file's lines, each with its own terminator. */
    static List<byte[]> lines(byte[] bytes) {
        List<byte[]> lines = new ArrayList<>();
        int start = 0;
        int at = 0;
        while (at < bytes.length) {
            byte b = bytes[at++];
            if (b == '\n' || b == '\r') {
                if (b == '\r' && at < bytes.length && bytes[at] == '\n') {
                    at++;
                }
                lines.add(Arrays.copyOfRange(bytes, start, at));
                start = at;
            }
        }
        if (start < bytes.length) {
            lines.add(Arrays.copyOfRange(bytes, start, bytes.length));
        }
        return lines;
    }

    /** A line's text, its terminator removed. */
    static String text(byte[] line) {
        int end = line.length;
        while (end > 0 && (line[end - 1] == '\n' || line[end - 1] == '\r')) {
            end--;
        }
        return new String(line, 0, end, StandardCharsets.UTF_8);
    }
}
