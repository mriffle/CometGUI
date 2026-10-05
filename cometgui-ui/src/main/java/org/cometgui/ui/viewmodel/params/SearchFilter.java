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
 * A filter of the global parameter search (<em>Global parameter search</em>). Every active filter
 * must hold for a parameter to be listed.
 */
public enum SearchFilter {

    /**
     * The value differs from the selected release's default. The value decides, asked of the model
     * ({@code resetToDefault}), not the origin: a value set back to the default by hand is not
     * modified, and an output the workflow switched on is -- it is a real change the application
     * makes ({@code R-CMT-01}). An unknown parameter is not in the default set, so it counts.
     */
    MODIFIED("Modified only"),

    /** The parameter has an error in the report, or its field holds a refused edit. */
    ERRORS("Errors only"),

    /** The parameter has a warning in the report. */
    WARNINGS("Warnings only"),

    /** The parameter's curated visibility is Expert. */
    EXPERT("Expert parameters"),

    /** A parameter of the configuration the selected release does not model (unknown, imported). */
    UNSUPPORTED("Unsupported/imported parameters");

    private final String words;

    SearchFilter(String words) {
        this.words = words;
    }

    /**
     * The filter's label.
     *
     * @return for example {@code Modified only}
     */
    public String words() {
        return words;
    }
}
