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

package org.cometgui.params.comet.presets;

import java.util.Objects;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.value.VariableModification;

/**
 * A common variable modification an editor can add in one step: a named, cited tuple.
 *
 * <p>The mass is scientific data, so it is not typed into any editor's code: each preset is a row
 * of the bundled {@code comet-modification-presets.json}, whose mass cites its Unimod record and
 * whose other fields cite the Comet documentation of the form, read and checked by {@link
 * ModificationPresets}.
 *
 * @param id a stable identifier, lower-case letters, digits and hyphens
 * @param name what the modification is called, such as {@code Oxidation}
 * @param description what it is and where it applies, in a sentence
 * @param writtenFor the Comet release whose tuple syntax {@code tuple} is written in
 * @param tuple the tuple as that release writes it, such as {@code 15.994915 M 0 3 -1 0 0 0.0}
 * @param modification the tuple, read
 * @param massSource the {@code https://} reference the mass is taken from, quoting the mass
 * @param formSource the {@code https://} reference for the tuple's other fields
 */
public record ModificationPreset(
        String id,
        String name,
        String description,
        ToolVersion writtenFor,
        String tuple,
        VariableModification modification,
        String massSource,
        String formSource) {

    /** Validates presence. */
    public ModificationPreset {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(writtenFor, "writtenFor");
        Objects.requireNonNull(tuple, "tuple");
        Objects.requireNonNull(modification, "modification");
        Objects.requireNonNull(massSource, "massSource");
        Objects.requireNonNull(formSource, "formSource");
    }

    /**
     * The preset in words, as the variable-modification editor summarises a slot.
     *
     * @return for example {@code Oxidation: +15.994915 on M; max 3 per peptide; optional}
     */
    public String summary() {
        return modification.summary(name);
    }
}
