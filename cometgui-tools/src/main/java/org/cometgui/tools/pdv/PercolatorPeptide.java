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

package org.cometgui.tools.pdv;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * A Percolator {@code peptide} field read as values, so that it can be compared with the pepXML hit
 * it names -- never transcribed: the mzTab takes residues and modification masses from the pepXML,
 * whose text is exact.
 *
 * <p>Comet writes the field into its PIN ({@code CometWritePercolator.cpp} at {@code v2026.02.2},
 * lines 259-276) and Percolator passes it through: {@code <prev>.<peptide>.<next>}, where the
 * peptide carries a <em>variable</em> modification's mass, printed {@code %0.4f}, in brackets after
 * its residue, a variable N-terminal one as {@code n[...]} before the first residue and a variable
 * C-terminal one as {@code c[...]} after the last. Static modifications are not shown.
 *
 * @param previous the residue before the peptide, or {@code -}
 * @param residues the residues, without modifications
 * @param next the residue after the peptide, or {@code -}
 * @param nTerminal the {@code n[...]} mass text, when present
 * @param byPosition each bracketed mass text by its residue's 1-based position
 * @param cTerminal the {@code c[...]} mass text, when present
 */
record PercolatorPeptide(
        String previous,
        String residues,
        String next,
        Optional<String> nTerminal,
        Map<Integer, String> byPosition,
        Optional<String> cTerminal) {

    private static final Pattern DECIMAL = Pattern.compile("[+-]?[0-9]+(?:\\.[0-9]+)?");

    /** Copies the map. */
    PercolatorPeptide {
        byPosition = Collections.unmodifiableMap(new TreeMap<>(byPosition));
    }

    /**
     * Reads a field.
     *
     * @param text the field as Percolator wrote it
     * @return the peptide, or empty if the text is not in the form above
     */
    static Optional<PercolatorPeptide> parse(String text) {
        int length = text.length();
        if (length < 5 || text.charAt(1) != '.' || text.charAt(length - 2) != '.') {
            return Optional.empty();
        }
        String body = text.substring(2, length - 2);
        StringBuilder residues = new StringBuilder();
        Map<Integer, String> byPosition = new TreeMap<>();
        String nTerminal = null;
        String cTerminal = null;
        int index = 0;
        if (body.startsWith("n[")) {
            int close = body.indexOf(']');
            nTerminal = decimal(body, 2, close);
            if (nTerminal == null) {
                return Optional.empty();
            }
            index = close + 1;
        }
        while (index < body.length()) {
            char c = body.charAt(index);
            if (c == 'c' && body.startsWith("c[", index) && body.endsWith("]")) {
                cTerminal = decimal(body, index + 2, body.length() - 1);
                if (cTerminal == null) {
                    return Optional.empty();
                }
                index = body.length();
            } else if (c >= 'A' && c <= 'Z') {
                residues.append(c);
                index++;
                if (index < body.length() && body.charAt(index) == '[') {
                    int close = body.indexOf(']', index);
                    String mass = decimal(body, index + 1, close);
                    if (mass == null) {
                        return Optional.empty();
                    }
                    byPosition.put(residues.length(), mass);
                    index = close + 1;
                }
            } else {
                return Optional.empty();
            }
        }
        if (residues.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(
                new PercolatorPeptide(
                        text.substring(0, 1),
                        residues.toString(),
                        text.substring(length - 1),
                        Optional.ofNullable(nTerminal),
                        byPosition,
                        Optional.ofNullable(cTerminal)));
    }

    /** {@code body[from, to)} when it is a decimal, else {@code null}. */
    private static String decimal(String body, int from, int to) {
        if (to < from) {
            return null;
        }
        String text = body.substring(from, to);
        return DECIMAL.matcher(text).matches() ? text : null;
    }
}
