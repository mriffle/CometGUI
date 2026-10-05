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
import java.util.Optional;
import org.cometgui.params.comet.presets.ModificationPreset;
import org.cometgui.params.comet.value.VariableModPart;
import org.cometgui.params.comet.value.VariableModification;

/**
 * One variable-modification slot of the configuration, as the slot editor shows it: a snapshot made
 * after every change, every word and text from the model.
 *
 * @param name the slot, such as {@code variable_mod03}
 * @param number its position, 1 for the first slot
 * @param value the slot's value
 * @param active whether it holds a modification: a mass difference other than 0
 * @param summary the value in words ({@code VariableModification.summary}), named after the
 *     common-modification preset it equals, if any
 * @param serialised the value as the canonical file writes it, for expert inspection
 * @param needsAttention whether a migration under review left the slot unresolved; such a slot is
 *     not moved and is not chosen for an added modification
 * @param preset the common-modification preset the value equals, if any
 * @param parts every part of the release's layout, in its order
 * @param displayName the slot's display name, such as {@code Variable modification 3}; its field --
 *     origin, findings, refusal and state in words -- is {@link VariableModsViewModel#field}
 */
public record VariableModSlotView(
        String name,
        int number,
        VariableModification value,
        boolean active,
        String summary,
        String serialised,
        boolean needsAttention,
        Optional<ModificationPreset> preset,
        List<VariableModPartView> parts,
        String displayName) {

    /** Validates presence and takes an immutable copy. */
    public VariableModSlotView {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(serialised, "serialised");
        Objects.requireNonNull(preset, "preset");
        parts = List.copyOf(parts);
        Objects.requireNonNull(displayName, "displayName");
    }

    /**
     * Whether the residue multi-select shows a character as selected.
     *
     * @param character a residue letter or terminal code
     * @return {@code true} if the slot's residue token holds it
     */
    public boolean selects(char character) {
        return value.holds(character);
    }

    /**
     * One part's view.
     *
     * @param part the part
     * @return its view, or empty if the release's layout has no such part
     */
    public Optional<VariableModPartView> part(VariableModPart part) {
        return parts.stream().filter(view -> view.part() == part).findFirst();
    }

    /**
     * The slot in words, for its heading and accessible name.
     *
     * @return for example {@code Variable modification 3: Oxidation: +15.994915 on M; max 3 per
     *     peptide; optional}
     */
    public String heading() {
        return displayName + ": " + summary;
    }
}
