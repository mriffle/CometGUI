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

import java.util.Objects;
import org.cometgui.results.parser.ResultRow;

/**
 * The text filter a table view applies on top of its q-value filter and category -- the one
 * definition every store uses.
 *
 * <p>A row matches when its {@code PSMId}, its {@code peptide} or any one of its proteins contains
 * the text, compared case-insensitively character by character as {@link
 * String#regionMatches(boolean, int, String, int, int) String.regionMatches(true, ...)} compares:
 * each pair of characters equal, or equal after {@link Character#toUpperCase(char)}, or after
 * {@link Character#toLowerCase(char)}. No locale is involved, so the result does not depend on the
 * machine's language. The text is matched as given; {@link ResultQuery} strips its surrounding
 * white space first, and empty text matches every row. A score, q-value or PEP is never matched as
 * text; filter those by the q-value filter and sort.
 */
public final class TextFilter {

    private TextFilter() {}

    /**
     * Whether a row matches.
     *
     * @param text the text; empty matches every row
     * @param row the row
     * @return {@code true} if the {@code PSMId}, peptide or a protein contains the text
     * @throws NullPointerException if either is {@code null}
     */
    public static boolean matches(String text, ResultRow row) {
        Objects.requireNonNull(text, "text");
        if (contains(row.psmId(), text) || contains(row.peptide(), text)) {
            return true;
        }
        for (String protein : row.proteinIds()) {
            if (contains(protein, text)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a field contains the text, ignoring case as described above.
     *
     * @param field the field
     * @param text the text
     * @return {@code true} if some region of the field equals the text ignoring case; always for
     *     empty text
     */
    static boolean contains(String field, String text) {
        int last = field.length() - text.length();
        for (int start = 0; start <= last; start++) {
            if (field.regionMatches(true, start, text, 0, text.length())) {
                return true;
            }
        }
        return false;
    }
}
