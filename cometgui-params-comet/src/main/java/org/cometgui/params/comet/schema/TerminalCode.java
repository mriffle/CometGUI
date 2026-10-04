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

package org.cometgui.params.comet.schema;

import java.util.Optional;

/**
 * A character of a variable modification's residue token that names a terminus rather than a
 * residue, and what it means.
 *
 * <p>This is the vocabulary, not the law: it says what each code means wherever it is accepted.
 * Which codes a Comet release accepts is that release's {@link ResidueAlphabet}, which is data in
 * the release's version record -- {@code ^} and {@code $} are in Comet 2026.03.0's alphabet and in
 * no earlier one. Meanings follow Comet's own documentation of {@code variable_modXX} for the
 * 2026.03 release and its source at {@code v2026.03.0} ({@code CometSearch/CometSearchManager.cpp}
 * lines 1538-1563); {@code docs/developer/comet_parameter_schema.rst} cites both.
 */
public enum TerminalCode {

    /** {@code n}: any peptide N-terminus. */
    PEPTIDE_N('n', "N-terminus"),

    /** {@code c}: any peptide C-terminus. */
    PEPTIDE_C('c', "C-terminus"),

    /**
     * {@code ^}: the protein N-terminus only -- the N-terminus of a peptide that starts the protein
     * (or follows a clipped initial methionine).
     */
    PROTEIN_N('^', "protein N-terminus"),

    /**
     * {@code $}: the protein C-terminus only -- the C-terminus of a peptide that ends the protein.
     */
    PROTEIN_C('$', "protein C-terminus");

    private final char code;

    private final String words;

    TerminalCode(char code, String words) {
        this.code = code;
        this.words = words;
    }

    /**
     * The character written in the residue token.
     *
     * @return {@code n}, {@code c}, {@code ^} or {@code $}
     */
    public char code() {
        return code;
    }

    /**
     * What the code targets, in words.
     *
     * @return for example {@code protein N-terminus}
     */
    public String words() {
        return words;
    }

    /**
     * The terminal code a character is, if it is one.
     *
     * @param character a character of a residue token
     * @return the code, or empty for a residue letter or any other character
     */
    public static Optional<TerminalCode> of(char character) {
        for (TerminalCode terminal : values()) {
            if (terminal.code == character) {
                return Optional.of(terminal);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether a character is one any curated release could mean: a residue letter {@code A}-{@code
     * Z} or a terminal code. A release's own alphabet is a subset of these.
     *
     * @param character the character
     * @return {@code true} if this project can say what it means
     */
    public static boolean describable(char character) {
        return (character >= 'A' && character <= 'Z') || of(character).isPresent();
    }
}
