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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The characters one Comet release accepts in the residue token of a variable modification: the
 * residue letters and the {@link TerminalCode}s, as data in the release's version record ({@code
 * versions[].variableModTuple.residueAlphabet}), beside the tuple layout it qualifies.
 *
 * <p>Any combination of accepted characters is a token, repeats included: Comet reads the token
 * with {@code %31s} and sorts and de-duplicates its characters before the search ({@code
 * CometSearch/CometSearchManager.cpp} lines 1514-1521 at {@code v2026.03.0}). What a combination
 * means -- {@code n} with {@code ^} is just {@code n} -- is {@link TerminalCode}'s and the value's
 * summary's to say, and whether it is sensible is validation's.
 *
 * @param characters every accepted character, each once
 * @param source the {@code https://} reference to where the release shows them
 */
public record ResidueAlphabet(String characters, String source) {

    private static final int LETTERS = 26;

    /**
     * Validates the alphabet.
     *
     * @throws IllegalArgumentException if it is empty, lists a character twice, or lists one this
     *     project cannot describe (neither a letter {@code A}-{@code Z} nor a {@link TerminalCode})
     */
    public ResidueAlphabet {
        Objects.requireNonNull(characters, "characters");
        Objects.requireNonNull(source, "source");
        if (characters.isEmpty()) {
            throw new IllegalArgumentException("the residue alphabet is empty");
        }
        for (int index = 0; index < characters.length(); index++) {
            char character = characters.charAt(index);
            if (!TerminalCode.describable(character)) {
                throw new IllegalArgumentException(
                        "the residue alphabet holds '"
                                + character
                                + "', which is neither a residue letter A-Z nor a terminal code"
                                + " (n, c, ^, $)");
            }
            if (characters.indexOf(character) != index) {
                throw new IllegalArgumentException(
                        "the residue alphabet lists '" + character + "' twice");
            }
        }
    }

    /**
     * Whether the release accepts a character in a residue token.
     *
     * @param character the character
     * @return {@code true} if it is in the alphabet
     */
    public boolean accepts(char character) {
        return characters.indexOf(character) >= 0;
    }

    /**
     * The residue letters the release accepts, in alphabetical order: what a residue multi-select
     * offers.
     *
     * @return for example {@code ABCDEFGHIJKLMNOPQRSTUVWXYZ}
     */
    public String letters() {
        StringBuilder letters = new StringBuilder();
        for (char letter = 'A'; letter <= 'Z'; letter++) {
            if (accepts(letter)) {
                letters.append(letter);
            }
        }
        return letters.toString();
    }

    /**
     * The terminal codes the release accepts, in {@link TerminalCode} order: the terminus choices
     * an editor offers. Comet 2026.03.0's include {@code ^} and {@code $}; earlier releases' do
     * not.
     *
     * @return the codes
     */
    public List<TerminalCode> terminalCodes() {
        List<TerminalCode> codes = new ArrayList<>();
        for (TerminalCode terminal : TerminalCode.values()) {
            if (accepts(terminal.code())) {
                codes.add(terminal);
            }
        }
        return List.copyOf(codes);
    }

    /**
     * Why a residue token is not one this release accepts.
     *
     * @param token the residue token
     * @return the first character the release does not accept, or empty if every character is
     *     accepted (an empty token is the value's shape problem, not the alphabet's)
     */
    public Optional<Character> firstRefused(String token) {
        Objects.requireNonNull(token, "token");
        for (int index = 0; index < token.length(); index++) {
            if (!accepts(token.charAt(index))) {
                return Optional.of(token.charAt(index));
            }
        }
        return Optional.empty();
    }

    /**
     * The alphabet in words, for a diagnostic: the letters ({@code A-Z} when all 26 are accepted),
     * then each terminal code with its meaning.
     *
     * @return for example {@code A-Z, n (N-terminus), c (C-terminus)}
     */
    public String describe() {
        String letters = letters();
        List<String> words = new ArrayList<>();
        if (letters.length() == LETTERS) {
            words.add("A-Z");
        } else if (!letters.isEmpty()) {
            words.add(letters);
        }
        for (TerminalCode terminal : terminalCodes()) {
            words.add(terminal.code() + " (" + terminal.words() + ")");
        }
        return String.join(", ", words);
    }
}
