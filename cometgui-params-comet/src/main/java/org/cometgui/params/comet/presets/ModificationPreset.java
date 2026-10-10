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

import java.util.List;
import java.util.Objects;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.value.VariableModification;

/**
 * A common variable modification an editor can add in one step: a named, cited tuple, offered for
 * the Comet releases it lists.
 *
 * <p>The mass is scientific data, so it is not typed into any editor's code: each preset is a row
 * of the bundled {@code comet-modification-presets.json}, whose mass cites its Unimod record and
 * whose other fields cite, release by release, that release's Comet parameter documentation, read
 * and checked by {@link ModificationPresets}.
 *
 * <p><strong>Which releases offer it is data, not a version test.</strong> A preset is offered for
 * exactly the releases in {@link #releases()}. That is how one modification can be offered in a
 * different form per release -- protein N-terminal acetylation is {@code n} at distance 0 in
 * 2026.02.2 and {@code ^} in 2026.03.0 -- with exactly one preset per release.
 *
 * @param id a stable identifier, lower-case letters, digits and hyphens
 * @param name what the modification is called, such as {@code Oxidation}
 * @param description what it is and where it applies, in a sentence
 * @param tuple the tuple as each listed release writes it, such as {@code 15.994915 M 0 3 -1 0 0
 *     0.0}
 * @param modification the tuple, read
 * @param massSource the {@code https://} reference the mass is taken from, quoting the mass
 * @param releases the releases it is offered for, each with its form's documentation; never empty
 */
public record ModificationPreset(
        String id,
        String name,
        String description,
        String tuple,
        VariableModification modification,
        String massSource,
        List<Release> releases) {

    /**
     * Validates presence and copies the release list.
     *
     * @throws IllegalArgumentException if no release is listed
     */
    public ModificationPreset {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(tuple, "tuple");
        Objects.requireNonNull(modification, "modification");
        Objects.requireNonNull(massSource, "massSource");
        releases = List.copyOf(Objects.requireNonNull(releases, "releases"));
        if (releases.isEmpty()) {
            throw new IllegalArgumentException(
                    "a preset offered for no release is offered nowhere: " + id);
        }
    }

    /**
     * Whether this preset is offered for a release.
     *
     * @param release the Comet release
     * @return {@code true} if {@link #releases()} lists it
     */
    public boolean offeredFor(ToolVersion release) {
        Objects.requireNonNull(release, "release");
        return releases.stream().anyMatch(listed -> listed.cometVersion().equals(release));
    }

    /**
     * The preset in words, as the variable-modification editor summarises a slot.
     *
     * @return for example {@code Oxidation: +15.994915 on M; max 3 per peptide; optional}
     */
    public String summary() {
        return modification.summary(name);
    }

    /**
     * One release a preset is offered for, and the documentation of the tuple's form there.
     *
     * @param cometVersion the release
     * @param formSource the {@code https://} reference for the tuple's fields in that release --
     *     that release's own Comet parameter documentation
     */
    public record Release(ToolVersion cometVersion, String formSource) {

        /** Validates presence. */
        public Release {
            Objects.requireNonNull(cometVersion, "cometVersion");
            Objects.requireNonNull(formSource, "formSource");
        }
    }
}
