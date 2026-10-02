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

package org.cometgui.params.comet.value;

import java.util.Objects;

/** What the two range kinds share: two tokens, and the names of their fields in a diagnostic. */
final class Ranges {

    /** The first value's name in a diagnostic. */
    static final String FIRST = "first value";

    /** The second value's name in a diagnostic. */
    static final String SECOND = "second value";

    private Ranges() {}

    /**
     * Splits a two-value text.
     *
     * @param name the parameter
     * @param text the value text
     * @return exactly two tokens
     * @throws ValueSyntaxException if there are not exactly two
     */
    static String[] two(String name, String text) {
        Objects.requireNonNull(text, "text");
        String[] tokens = Numbers.tokens(text);
        if (tokens.length != 2) {
            throw new ValueSyntaxException(
                    name,
                    "the range",
                    "\"" + text.strip() + "\" holds " + tokens.length + " values, not two");
        }
        return tokens;
    }
}
