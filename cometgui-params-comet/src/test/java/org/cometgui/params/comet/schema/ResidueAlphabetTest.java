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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The alphabet type and the terminal-code vocabulary, over CONSTRUCTED alphabets. */
class ResidueAlphabetTest {

    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private static final String SOURCE = "https://example.org/alphabet";

    private static String refused(String characters) {
        return assertThrows(
                        IllegalArgumentException.class,
                        () -> new ResidueAlphabet(characters, SOURCE))
                .getMessage();
    }

    @Test
    void theVocabulary() {
        assertEquals('n', TerminalCode.PEPTIDE_N.code());
        assertEquals('c', TerminalCode.PEPTIDE_C.code());
        assertEquals('^', TerminalCode.PROTEIN_N.code());
        assertEquals('$', TerminalCode.PROTEIN_C.code());
        assertEquals("N-terminus", TerminalCode.PEPTIDE_N.words());
        assertEquals("C-terminus", TerminalCode.PEPTIDE_C.words());
        assertEquals("protein N-terminus", TerminalCode.PROTEIN_N.words());
        assertEquals("protein C-terminus", TerminalCode.PROTEIN_C.words());
        for (TerminalCode terminal : TerminalCode.values()) {
            assertEquals(Optional.of(terminal), TerminalCode.of(terminal.code()));
            assertTrue(TerminalCode.describable(terminal.code()));
        }
        for (char c : new char[] {'A', 'M', 'Z'}) {
            assertEquals(Optional.empty(), TerminalCode.of(c));
            assertTrue(TerminalCode.describable(c));
        }
        for (char c : new char[] {'a', 'm', 'z', '@', '[', '-', '*', '#', '1', 'x'}) {
            assertFalse(TerminalCode.describable(c), String.valueOf(c));
        }
        assertEquals(Optional.empty(), TerminalCode.of('N'));
        assertEquals(Optional.empty(), TerminalCode.of('C'));
    }

    @Test
    void whatItAcceptsAndRefuses() {
        ResidueAlphabet alphabet = new ResidueAlphabet(LETTERS + "nc", SOURCE);
        assertTrue(alphabet.accepts('A'));
        assertTrue(alphabet.accepts('n'));
        assertFalse(alphabet.accepts('^'));
        assertFalse(alphabet.accepts('$'));
        assertEquals(Optional.empty(), alphabet.firstRefused("nKc"));
        assertEquals(Optional.empty(), alphabet.firstRefused(""));
        assertEquals(Optional.of('^'), alphabet.firstRefused("^"));
        assertEquals(Optional.of('$'), alphabet.firstRefused("KR$^"));
        assertEquals(Optional.of('^'), alphabet.firstRefused("M^"));
        ResidueAlphabet wider = new ResidueAlphabet(LETTERS + "nc^$", SOURCE);
        assertEquals(Optional.empty(), wider.firstRefused("K^c$n"));
        assertEquals(LETTERS + "nc^$", wider.characters());
        assertEquals(SOURCE, wider.source());
    }

    @Test
    void inWords() {
        assertEquals(
                "A-Z, n (N-terminus), c (C-terminus)",
                new ResidueAlphabet(LETTERS + "nc", SOURCE).describe());
        assertEquals(
                "A-Z, n (N-terminus), c (C-terminus), ^ (protein N-terminus), $ (protein"
                        + " C-terminus)",
                new ResidueAlphabet("$^cn" + LETTERS, SOURCE).describe());
        assertEquals("STY", new ResidueAlphabet("YTS", SOURCE).describe());
        assertEquals(
                "^ (protein N-terminus), $ (protein C-terminus)",
                new ResidueAlphabet("$^", SOURCE).describe());
        assertEquals(
                "ABCDEFGHIJKLMNOPQRSTUVWXY",
                new ResidueAlphabet(LETTERS.substring(0, 25), SOURCE).describe());
    }

    @Test
    void refusals() {
        assertEquals("the residue alphabet is empty", refused(""));
        assertEquals(
                "the residue alphabet holds '#', which is neither a residue letter A-Z nor a"
                        + " terminal code (n, c, ^, $)",
                refused("AB#"));
        assertEquals(
                "the residue alphabet holds 'k', which is neither a residue letter A-Z nor a"
                        + " terminal code (n, c, ^, $)",
                refused("Kk"));
        assertEquals("the residue alphabet lists 'K' twice", refused("KRK"));
        assertEquals("the residue alphabet lists 'n' twice", refused("nAn"));
        assertThrows(NullPointerException.class, () -> new ResidueAlphabet(null, SOURCE));
        assertThrows(NullPointerException.class, () -> new ResidueAlphabet("K", null));
    }
}
