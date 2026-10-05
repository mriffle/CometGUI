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

package org.cometgui.ui.viewmodel.params;

/** Which attribute of a parameter a search query matched. */
public enum SearchMatch {

    /** The parameter name, as {@code comet.params} writes it. */
    NAME("name"),

    /** The curated display name. */
    DISPLAY_NAME("display name"),

    /** The selected release's short help. */
    HELP_TEXT("help text"),

    /** The display name of the parameter's category. */
    CATEGORY("category"),

    /** One of the curated aliases. */
    ALIAS("alias");

    private final String words;

    SearchMatch(String words) {
        this.words = words;
    }

    /**
     * The attribute in words.
     *
     * @return for example {@code display name}
     */
    public String words() {
        return words;
    }
}
