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

import java.util.List;
import java.util.Objects;
import org.cometgui.params.comet.value.VariableModChoice;
import org.cometgui.params.comet.value.VariableModPart;

/**
 * One part of one slot's variable modification as its control shows it: the release's layout
 * decides which parts a slot has, the model gives every word and text.
 *
 * @param part the part
 * @param label what the control is called, such as {@code minimum count per peptide}
 * @param text the part's text in the configuration; empty for an absent optional half
 * @param explanation what the part means, from Comet's page
 * @param choices the documented codes with their words, for the terminus and the requirement; empty
 *     for a part entered as a number or a residue token
 */
public record VariableModPartView(
        VariableModPart part,
        String label,
        String text,
        String explanation,
        List<VariableModChoice> choices) {

    /** Validates presence and takes an immutable copy. */
    public VariableModPartView {
        Objects.requireNonNull(part, "part");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(explanation, "explanation");
        choices = List.copyOf(choices);
    }
}
