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

/**
 * One documented value of a coded tuple part -- a terminus, or optional, required or exclusive --
 * with the words an editor shows for it, so that no one has to recall that {@code -1} means
 * exclusive.
 *
 * @param token the text the part is set from, such as {@code -1}
 * @param words what it is called, such as {@code exclusive}
 * @param explanation what it does, in a sentence from Comet's {@code variable_modXX} page
 */
public record VariableModChoice(String token, String words, String explanation) {

    /** Validates presence. */
    public VariableModChoice {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(words, "words");
        Objects.requireNonNull(explanation, "explanation");
    }
}
