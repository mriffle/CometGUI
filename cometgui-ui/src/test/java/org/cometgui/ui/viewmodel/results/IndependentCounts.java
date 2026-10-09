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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Counts a Percolator table's rows at a cutoff with nothing from {@code org.cometgui.results}
 * (design decision P10-3): each line split on tabs, the {@code q-value} column found by its
 * heading, read as a {@link BigDecimal}; a field that is empty, not a {@code BigDecimal} ({@code
 * NaN}, {@code inf}, {@code 0,01}) or outside {@code [0, 1]} is unknown, otherwise it passes when
 * {@code q <= cutoff} in exact decimal arithmetic.
 *
 * @param total every row
 * @param passing known and at or below the cutoff
 * @param failing known and above it
 * @param unknown everything else
 */
record IndependentCounts(long total, long passing, long failing, long unknown) {

    static IndependentCounts of(Path table, String cutoff) {
        BigDecimal limit = new BigDecimal(cutoff);
        List<String> lines;
        try {
            lines = Files.readAllLines(table, StandardCharsets.UTF_8);
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
        List<String> header = List.of(lines.get(0).split("\t", -1));
        int column = header.indexOf("q-value");
        if (column < 0) {
            throw new IllegalStateException("no q-value column in " + table);
        }
        long passing = 0;
        long failing = 0;
        long unknown = 0;
        for (String line : lines.subList(1, lines.size())) {
            String field = line.split("\t", -1)[column];
            BigDecimal q;
            try {
                q = new BigDecimal(field);
            } catch (NumberFormatException notANumber) {
                unknown++;
                continue;
            }
            if (q.signum() < 0 || q.compareTo(BigDecimal.ONE) > 0) {
                unknown++;
            } else if (q.compareTo(limit) <= 0) {
                passing++;
            } else {
                failing++;
            }
        }
        return new IndependentCounts(lines.size() - 1L, passing, failing, unknown);
    }

    /**
     * The line numbers of the rows passing, failing or unknown, in file order -- the header is line
     * 1.
     *
     * @param table the table
     * @param cutoff the cutoff
     * @param which {@code "passing"}, {@code "failing"} or {@code "unknown"}
     * @return the lines
     */
    static List<Long> lines(Path table, String cutoff, String which) {
        BigDecimal limit = new BigDecimal(cutoff);
        List<String> lines;
        try {
            lines = Files.readAllLines(table, StandardCharsets.UTF_8);
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
        int column = List.of(lines.get(0).split("\t", -1)).indexOf("q-value");
        List<Long> found = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            String field = lines.get(index).split("\t", -1)[column];
            String where;
            try {
                BigDecimal q = new BigDecimal(field);
                if (q.signum() < 0 || q.compareTo(BigDecimal.ONE) > 0) {
                    where = "unknown";
                } else {
                    where = q.compareTo(limit) <= 0 ? "passing" : "failing";
                }
            } catch (NumberFormatException notANumber) {
                where = "unknown";
            }
            if (where.equals(which)) {
                found.add(index + 1L);
            }
        }
        return found;
    }
}
