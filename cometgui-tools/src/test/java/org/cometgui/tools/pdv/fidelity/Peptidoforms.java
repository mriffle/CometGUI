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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The three textual forms of a modified peptide the suite meets, each parsed into the same value: a
 * plain sequence and, for each position, the list of mass deltas on it (position 0 is the N
 * terminus, 1..n the residues, n + 1 the C terminus). Comparison is of these parsed values, never
 * of strings ({@code R-PDV-03}).
 *
 * <ul>
 *   <li>ProForma as the mzTab's {@code opt_..._proforma_peptidoform_sequence} holds it: {@code
 *       [+42.010565]-AC[+57.021464][+15.994915]K}, a C-terminal {@code -[+x]} recognised so that
 *       its presence can be reported;
 *   <li>mzTab 1.0's {@code modifications} column: {@code null} or {@code
 *       0-CHEMMOD:+42.010565,2-CHEMMOD:+57.021464};
 *   <li>Percolator's {@code peptide}: {@code K.n[42.0106]AC[15.9949]K.L}, flanks and all.
 * </ul>
 */
final class Peptidoforms {

    /**
     * A parsed peptide.
     *
     * @param sequence the residues
     * @param deltas for each position that carries any, its deltas in the order written
     * @param previous the preceding residue, for the Percolator form only
     * @param next the following residue, for the Percolator form only
     */
    record Parsed(
            String sequence, Map<Integer, List<BigDecimal>> deltas, String previous, String next) {

        /** The deltas per position, normalised ({@link #normalised}) for comparison. */
        Map<Integer, List<BigDecimal>> sortedDeltas() {
            return normalised(deltas);
        }
    }

    private Peptidoforms() {}

    /**
     * Deltas by position as comparable values: each delta by its numeric value alone ({@code
     * 15.994900} is {@code 15.9949}: the same decimal, written to another precision), each
     * position's deltas sorted, so that two maps are equal exactly when they state the same
     * multiset of exact decimal deltas at the same positions.
     *
     * @param deltas deltas by position
     * @return the comparable form
     */
    static Map<Integer, List<BigDecimal>> normalised(Map<Integer, List<BigDecimal>> deltas) {
        Map<Integer, List<BigDecimal>> sorted = new TreeMap<>();
        for (Map.Entry<Integer, List<BigDecimal>> entry : deltas.entrySet()) {
            List<BigDecimal> list = new ArrayList<>();
            for (BigDecimal delta : entry.getValue()) {
                list.add(delta.stripTrailingZeros());
            }
            list.sort(BigDecimal::compareTo);
            sorted.put(entry.getKey(), List.copyOf(list));
        }
        return sorted;
    }

    /**
     * Parses the ProForma column.
     *
     * @param text the field
     * @return the peptide
     * @throws IllegalArgumentException if the text is not that form
     */
    static Parsed proForma(String text) {
        Map<Integer, List<BigDecimal>> deltas = new TreeMap<>();
        int at = 0;
        // N-terminal: one or more [x] followed by '-'
        int dash = text.indexOf("]-");
        if (text.startsWith("[") && dash > 0) {
            String head = text.substring(0, dash + 1);
            for (String mass : brackets(head, text)) {
                deltas.computeIfAbsent(0, ignored -> new ArrayList<>()).add(decimal(mass, text));
            }
            at = dash + 2;
        }
        StringBuilder sequence = new StringBuilder();
        while (at < text.length()) {
            char c = text.charAt(at);
            if (c == '-' && at + 1 < text.length() && text.charAt(at + 1) == '[') {
                int position = sequence.length() + 1;
                for (String mass : brackets(text.substring(at + 1), text)) {
                    deltas.computeIfAbsent(position, ignored -> new ArrayList<>())
                            .add(decimal(mass, text));
                }
                break;
            }
            if (c < 'A' || c > 'Z') {
                throw new IllegalArgumentException("not ProForma at " + at + ": " + text);
            }
            sequence.append(c);
            at++;
            while (at < text.length() && text.charAt(at) == '[') {
                int close = text.indexOf(']', at);
                if (close < 0) {
                    throw new IllegalArgumentException("unclosed bracket: " + text);
                }
                deltas.computeIfAbsent(sequence.length(), ignored -> new ArrayList<>())
                        .add(decimal(text.substring(at + 1, close), text));
                at = close + 1;
            }
        }
        return new Parsed(sequence.toString(), deltas, null, null);
    }

    /**
     * Parses the mzTab {@code modifications} column.
     *
     * @param text the field
     * @return the deltas by position; empty for {@code null}
     * @throws IllegalArgumentException if the text is not that form
     */
    static Map<Integer, List<BigDecimal>> mzTabModifications(String text) {
        Map<Integer, List<BigDecimal>> deltas = new TreeMap<>();
        if ("null".equals(text)) {
            return deltas;
        }
        for (String one : text.split(",", -1)) {
            int dash = one.indexOf("-CHEMMOD:");
            if (dash <= 0) {
                throw new IllegalArgumentException("not position-CHEMMOD:mass: " + text);
            }
            int position = Integer.parseInt(one.substring(0, dash));
            deltas.computeIfAbsent(position, ignored -> new ArrayList<>())
                    .add(decimal(one.substring(dash + "-CHEMMOD:".length()), text));
        }
        return deltas;
    }

    /**
     * Parses Percolator's peptide.
     *
     * @param text the field, {@code X.PEPTIDE.Y}
     * @return the peptide, with its flanks
     * @throws IllegalArgumentException if the text is not that form
     */
    static Parsed percolator(String text) {
        if (text.length() < 5 || text.charAt(1) != '.' || text.charAt(text.length() - 2) != '.') {
            throw new IllegalArgumentException("not X.PEPTIDE.Y: " + text);
        }
        String core = text.substring(2, text.length() - 2);
        Map<Integer, List<BigDecimal>> deltas = new TreeMap<>();
        StringBuilder sequence = new StringBuilder();
        int at = 0;
        while (at < core.length()) {
            char c = core.charAt(at);
            int position;
            if (c == 'n' && sequence.length() == 0) {
                position = 0;
            } else if (c == 'c') {
                position = sequence.length() + 1;
            } else if (c >= 'A' && c <= 'Z') {
                sequence.append(c);
                position = sequence.length();
            } else {
                throw new IllegalArgumentException("not a Percolator peptide: " + text);
            }
            at++;
            while (at < core.length() && core.charAt(at) == '[') {
                int close = core.indexOf(']', at);
                if (close < 0) {
                    throw new IllegalArgumentException("unclosed bracket: " + text);
                }
                deltas.computeIfAbsent(position, ignored -> new ArrayList<>())
                        .add(decimal(core.substring(at + 1, close), text));
                at = close + 1;
            }
        }
        return new Parsed(
                sequence.toString(),
                deltas,
                text.substring(0, 1),
                text.substring(text.length() - 1));
    }

    private static List<String> brackets(String head, String whole) {
        List<String> masses = new ArrayList<>();
        int at = 0;
        while (at < head.length() && head.charAt(at) == '[') {
            int close = head.indexOf(']', at);
            if (close < 0) {
                throw new IllegalArgumentException("unclosed bracket: " + whole);
            }
            masses.add(head.substring(at + 1, close));
            at = close + 1;
        }
        return masses;
    }

    private static BigDecimal decimal(String text, String whole) {
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException(
                    "not a decimal mass \"" + text + "\" in " + whole, notANumber);
        }
    }
}
