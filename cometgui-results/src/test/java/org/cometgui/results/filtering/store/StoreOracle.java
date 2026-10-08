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

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.cometgui.results.testing.IndependentCounter;

/**
 * What a store should answer, worked out a second way: the raw table read with {@code split("\t")},
 * q-values compared as exact {@link BigDecimal}s through {@link IndependentCounter}'s policy, the
 * {@code SpecId} split by a regular expression, text matched by {@code
 * toLowerCase(Locale.ROOT).contains}, and the sort rules of the contract restated here from its
 * documentation. It uses <strong>no {@code org.cometgui.results} production class</strong>; {@code
 * InMemoryResultStoreTest} proves that from its compiled bytes. The caller passes the query's names
 * ({@code PASSING}, {@code Q_VALUE}), not its types, for the same reason.
 */
final class StoreOracle {

    private static final Pattern DECIMAL =
            Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");

    /* Greedy: the base takes everything up to the last three numeric fields. */
    private static final Pattern SPEC_ID = Pattern.compile("(.+)_([0-9]+)_([0-9]+)_([0-9]+)");

    private final List<Row> rows;

    private StoreOracle(List<Row> rows) {
        this.rows = rows;
    }

    /** One raw row, as this oracle reads it. */
    record Row(
            long line,
            String psmId,
            String qText,
            BigDecimal score,
            BigDecimal q,
            BigDecimal pep,
            String peptide,
            List<String> proteins,
            String base,
            BigDecimal scan,
            BigDecimal charge) {}

    static StoreOracle read(Path table) throws IOException {
        List<Row> rows = new ArrayList<>();
        try (BufferedReader in = Files.newBufferedReader(table, StandardCharsets.UTF_8)) {
            String first = in.readLine();
            if (first == null) {
                throw new IOException(table + " has no header");
            }
            List<String> header = Arrays.asList(first.split("\t", -1));
            int id = header.indexOf("PSMId");
            int score = header.indexOf("score");
            int q = header.indexOf("q-value");
            int pep = header.indexOf("posterior_error_prob");
            int peptide = header.indexOf("peptide");
            int proteins = header.indexOf("proteinIds");
            long line = 1;
            String text;
            while ((text = in.readLine()) != null) {
                line++;
                String[] fields = text.split("\t", -1);
                Matcher spec = SPEC_ID.matcher(fields[id]);
                boolean shaped = spec.matches();
                rows.add(
                        new Row(
                                line,
                                fields[id],
                                fields[q],
                                decimal(fields[score]),
                                IndependentCounter.reason(fields[q]) == null
                                        ? new BigDecimal(fields[q])
                                        : null,
                                decimal(fields[pep]),
                                fields[peptide],
                                List.of(fields).subList(proteins, fields.length),
                                shaped ? spec.group(1) : null,
                                shaped ? new BigDecimal(spec.group(2)) : null,
                                shaped ? new BigDecimal(spec.group(3)) : null));
            }
        }
        return new StoreOracle(List.copyOf(rows));
    }

    List<Row> rows() {
        return rows;
    }

    /**
     * The lines a query should match, in its order.
     *
     * @param cutoff the q-value cutoff's text
     * @param category {@code PASSING}, {@code FAILING}, {@code UNKNOWN_Q_VALUE} or {@code ALL}
     * @param text the text filter, as typed
     * @param column a sort column's name
     * @param descending the direction
     * @return the matching lines, ordered
     */
    List<Long> expect(
            String cutoff, String category, String text, String column, boolean descending) {
        BigDecimal limit = new BigDecimal(cutoff);
        String needle = text.strip().toLowerCase(Locale.ROOT);
        Comparator<Row> order = order(column, descending);
        return rows.stream()
                .filter(row -> inCategory(row, limit, category))
                .filter(row -> matches(row, needle))
                .sorted(order)
                .map(Row::line)
                .toList();
    }

    /**
     * The first lines a query should match, in its order, without sorting every row: a bounded
     * selection, for the large fixture.
     *
     * @param cutoff the q-value cutoff's text
     * @param category {@code PASSING}, {@code FAILING}, {@code UNKNOWN_Q_VALUE} or {@code ALL}
     * @param text the text filter, as typed
     * @param column a sort column's name
     * @param descending the direction
     * @param count how many
     * @param fromTheEnd the last {@code count} instead of the first, still in the query's order
     * @return the lines
     */
    List<Long> expectEnd(
            String cutoff,
            String category,
            String text,
            String column,
            boolean descending,
            int count,
            boolean fromTheEnd) {
        BigDecimal limit = new BigDecimal(cutoff);
        String needle = text.strip().toLowerCase(Locale.ROOT);
        Comparator<Row> order = order(column, descending);
        Comparator<Row> kept = fromTheEnd ? order : order.reversed();
        java.util.PriorityQueue<Row> worst = new java.util.PriorityQueue<>(count + 1, kept);
        for (Row row : rows) {
            if (inCategory(row, limit, category) && matches(row, needle)) {
                if (worst.size() < count) {
                    worst.add(row);
                } else if (kept.compare(row, worst.peek()) > 0) {
                    worst.poll();
                    worst.add(row);
                }
            }
        }
        List<Row> selected = new ArrayList<>(worst);
        selected.sort(order);
        return selected.stream().map(Row::line).toList();
    }

    static String visibility(Row row, BigDecimal limit) {
        if (row.q() == null) {
            return "UNKNOWN_Q_VALUE";
        }
        return row.q().compareTo(limit) <= 0 ? "PASSING" : "FAILING";
    }

    private static boolean inCategory(Row row, BigDecimal limit, String category) {
        return "ALL".equals(category) || visibility(row, limit).equals(category);
    }

    private static boolean matches(Row row, String needle) {
        if (row.psmId().toLowerCase(Locale.ROOT).contains(needle)
                || row.peptide().toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        return row.proteins().stream()
                .anyMatch(protein -> protein.toLowerCase(Locale.ROOT).contains(needle));
    }

    private static BigDecimal decimal(String text) {
        return DECIMAL.matcher(text).matches() ? new BigDecimal(text) : null;
    }

    /* Missing last in both directions, then by value in the direction asked, ties by line. */
    private static Comparator<Row> order(String column, boolean descending) {
        Comparator<Row> byValue =
                switch (column) {
                    case "FILE_ORDER" -> nullsLast(Row::line, descending);
                    case "PSM_ID" -> nullsLast(Row::psmId, descending);
                    case "SOURCE_FILE" -> nullsLast(Row::base, descending);
                    case "SCAN" -> nullsLast(Row::scan, descending);
                    case "CHARGE" -> nullsLast(Row::charge, descending);
                    case "PEPTIDE" -> nullsLast(Row::peptide, descending);
                    case "PROTEINS" ->
                            nullsLast(row -> String.join("\u0000", row.proteins()), descending);
                    case "SCORE" -> nullsLast(Row::score, descending);
                    case "Q_VALUE" -> nullsLast(Row::q, descending);
                    case "PEP" -> nullsLast(Row::pep, descending);
                    default -> throw new IllegalArgumentException(column);
                };
        return byValue.thenComparing(Row::line);
    }

    private static <T extends Comparable<? super T>> Comparator<Row> nullsLast(
            java.util.function.Function<Row, T> key, boolean descending) {
        Comparator<T> natural = Comparator.naturalOrder();
        return Comparator.comparing(
                key, Comparator.nullsLast(descending ? natural.reversed() : natural));
    }
}
