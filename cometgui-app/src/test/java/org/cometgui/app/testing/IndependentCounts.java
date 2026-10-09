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

package org.cometgui.app.testing;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A deliberately minimal counter of a Percolator table's rows under a q-value cutoff, for the
 * Results section's GUI gate tests (Phase 10 design decision P10-3): the expected numbers these
 * tests compare the screen with are not produced by the code under test.
 *
 * <p>It uses <strong>no {@code org.cometgui} class at all</strong>: the file's bytes are split into
 * lines at {@code \n} here, each line split at tabs with {@link String#split}, the {@code q-value}
 * column found by its header name, and the q-value read as an exact {@link BigDecimal}. The rule is
 * the one {@code cometgui-results/.../real-k562/PROVENANCE.txt}'s {@code awk} command applies,
 * written out again: a q-value that is not a plain decimal number (optionally signed, optionally
 * with an exponent) -- empty, {@code NaN}, {@code inf}, {@code 0,01} -- or that is outside {@code
 * [0, 1]} is <em>unknown</em>; otherwise the row <em>passes</em> when {@code q <= cutoff}, compared
 * exactly, and <em>fails</em> when not.
 */
public final class IndependentCounts {

    /** A plain decimal number, as the {@code awk} pin's regular expression has it. */
    private static final Pattern NUMBER =
            Pattern.compile("[+-]?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)([eE][+-]?[0-9]+)?");

    /** Where a row falls. */
    public enum Where {
        /** Known and at most the cutoff. */
        PASSING,
        /** Known and above the cutoff. */
        FAILING,
        /** Missing, not a plain number, or outside [0, 1]. */
        UNKNOWN
    }

    /**
     * One data row.
     *
     * @param line its line number in the file, the header being line 1
     * @param bytes the row's text exactly as in the file, its {@code \n} included
     * @param psmId its first field
     * @param where where it falls under the cutoff
     */
    public record Row(int line, String bytes, String psmId, Where where) {}

    private IndependentCounts() {}

    /**
     * Every data row of a table, classified under a cutoff.
     *
     * @param table the table
     * @param cutoff the cutoff, as typed
     * @return the rows in file order
     * @throws IOException if the file cannot be read
     */
    public static List<Row> rows(Path table, String cutoff) throws IOException {
        BigDecimal limit = new BigDecimal(cutoff);
        String text = Files.readString(table, StandardCharsets.UTF_8);
        List<String> lines = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = text.indexOf('\n', start);
            end = end < 0 ? text.length() : end + 1;
            lines.add(text.substring(start, end));
            start = end;
        }
        if (lines.isEmpty()) {
            return fail(table + " has no header line");
        }
        List<String> header = Arrays.asList(strip(lines.get(0)).split("\t", -1));
        int q = header.indexOf("q-value");
        if (q < 0) {
            return fail(table + " has no q-value column: " + header);
        }
        List<Row> rows = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            String line = lines.get(index);
            String[] fields = strip(line).split("\t", -1);
            String value = fields.length > q ? fields[q] : "";
            rows.add(new Row(index + 1, line, fields[0], classify(value, limit)));
        }
        return rows;
    }

    /**
     * The four counts of a table under a cutoff.
     *
     * @param table the table
     * @param cutoff the cutoff, as typed
     * @return total, passing, failing, unknown -- as the Results section shows them, as text
     * @throws IOException if the file cannot be read
     */
    public static List<String> counts(Path table, String cutoff) throws IOException {
        long passing = 0;
        long failing = 0;
        long unknown = 0;
        List<Row> rows = rows(table, cutoff);
        for (Row row : rows) {
            switch (row.where()) {
                case PASSING -> passing++;
                case FAILING -> failing++;
                case UNKNOWN -> unknown++;
            }
        }
        return List.of(
                Long.toString(rows.size()),
                Long.toString(passing),
                Long.toString(failing),
                Long.toString(unknown));
    }

    /**
     * The rows of one kind, in file order.
     *
     * @param rows what {@link #rows} gave
     * @param where which rows; {@code null} for all of them
     * @return those rows
     */
    public static List<Row> only(List<Row> rows, Where where) {
        return rows.stream().filter(row -> where == null || row.where() == where).toList();
    }

    private static Where classify(String value, BigDecimal cutoff) {
        if (!NUMBER.matcher(value).matches()) {
            return Where.UNKNOWN;
        }
        BigDecimal q = new BigDecimal(value);
        if (q.signum() < 0 || q.compareTo(BigDecimal.ONE) > 0) {
            return Where.UNKNOWN;
        }
        return q.compareTo(cutoff) <= 0 ? Where.PASSING : Where.FAILING;
    }

    private static String strip(String line) {
        String without = line.endsWith("\n") ? line.substring(0, line.length() - 1) : line;
        return without.endsWith("\r") ? without.substring(0, without.length() - 1) : without;
    }
}
