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
 * The validation state of one field, in words as well as in kind (exit gate item 7: validation
 * state is conveyed in text, never by colour alone).
 */
public enum FieldState {

    /** Nothing to report. */
    VALID("No problems"),

    /** At least one warning and no error. */
    WARNING("Warning"),

    /** At least one error, or an edit the model refused. */
    ERROR("Error");

    private final String words;

    FieldState(String words) {
        this.words = words;
    }

    /**
     * The state in words.
     *
     * @return for example {@code Error}
     */
    public String words() {
        return words;
    }
}
