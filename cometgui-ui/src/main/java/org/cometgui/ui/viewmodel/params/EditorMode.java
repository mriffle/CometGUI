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

/**
 * The three levels of the parameter editor, in the specification's order (<em>Parameter editor
 * levels</em>): progressive disclosure from the curated Essentials surface to every parameter by
 * category, and to the raw canonical text.
 */
public enum EditorMode {

    /** The curated, task-ordered common controls. */
    ESSENTIALS("Essentials"),

    /** Every parameter of the release, grouped by scientific concept. */
    ADVANCED("Advanced"),

    /** The canonical raw {@code comet.params} text. */
    EXPERT("Expert");

    private final String words;

    EditorMode(String words) {
        this.words = words;
    }

    /**
     * The level's name, as its switch shows it.
     *
     * @return for example {@code Advanced}
     */
    public String words() {
        return words;
    }
}
