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

/**
 * One section of the Essentials surface with its fields, for the selected release.
 *
 * @param section the section
 * @param fields its fields, in the section's order
 */
public record EssentialsGroup(EssentialsSection section, List<FieldViewModel> fields) {

    /** Validates presence and takes an immutable copy. */
    public EssentialsGroup {
        Objects.requireNonNull(section, "section");
        fields = List.copyOf(fields);
    }

    /**
     * The section heading.
     *
     * @return the heading
     */
    public String title() {
        return section.title();
    }
}
