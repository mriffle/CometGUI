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

package org.cometgui.params.comet.validation;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ParameterValue;

/**
 * The text parameters Comet reads as <em>one</em> white-space delimited token: everything after the
 * first blank is silently dropped, and so is everything past the token's length limit.
 *
 * <p>Comet 2026.02.2 reads these with its {@code parse_string} helper, {@code sscanf} with {@code
 * "%255s"} ({@code Comet.cpp} lines 301-306 and 348-351 at {@code v2026.02.2}); Comet's own page
 * for {@code decoy_prefix} says the prefix is "any string you want without spaces". So a value
 * holding white space, or longer than 255 bytes, would not be what Comet searches with, and is an
 * error. ({@code activation_method} is read the same way, but its value is one of its choices,
 * which the {@code choice} validator checks.)
 */
final class TextTokenRule {

    /** Each parameter read as one token, with the longest token Comet reads for it. */
    private static final Map<String, Integer> TOKENS =
            Map.of(
                    "decoy_prefix", 255,
                    "pinfile_protein_delimiter", 255,
                    "protein_modslist_file", 255);

    private TextTokenRule() {}

    /**
     * Whether Comet reads a parameter as one token.
     *
     * @param name the parameter
     * @return {@code true} if it does
     */
    static boolean readsAsToken(String name) {
        return TOKENS.containsKey(name);
    }

    static void check(CometParameters model, Findings findings) {
        TOKENS.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(
                        token ->
                                model.entry(token.getKey())
                                        .ifPresent(
                                                entry ->
                                                        check(
                                                                token.getKey(),
                                                                ((ParameterValue.Text)
                                                                                entry.value())
                                                                        .text(),
                                                                token.getValue(),
                                                                findings)));
    }

    private static void check(String name, String text, int limit, Findings findings) {
        if (text.chars().anyMatch(Character::isWhitespace)) {
            findings.add(
                    Rule.TEXT_NOT_ONE_TOKEN,
                    name,
                    name
                            + " = "
                            + text
                            + " holds white space; Comet reads only up to the first blank (\""
                            + text.split("\\s", 2)[0]
                            + "\"), so use one word with no spaces");
        }
        int bytes = text.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > limit) {
            findings.add(
                    Rule.TEXT_TOO_LONG,
                    name,
                    name
                            + " is "
                            + bytes
                            + " bytes long; Comet reads at most "
                            + limit
                            + ", so shorten it");
        }
    }
}
