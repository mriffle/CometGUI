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
import org.cometgui.params.comet.schema.ParameterCategory;

/**
 * One group of Advanced mode: every parameter of the selected release in one scientific category,
 * in the release's order. Advanced mode is the fourteen of these, in the specification's order, and
 * holds every parameter the release models exactly once ({@code AC-PAR-04}); a field marks itself
 * when it is an expert parameter.
 *
 * @param category the category
 * @param fields its fields
 */
public record AdvancedCategory(ParameterCategory category, List<FieldViewModel> fields) {

    /** Validates presence and takes an immutable copy. */
    public AdvancedCategory {
        Objects.requireNonNull(category, "category");
        fields = List.copyOf(fields);
    }

    /**
     * The group heading.
     *
     * @return for example {@code Precursor mass and isotope handling}
     */
    public String title() {
        return category.displayName();
    }
}
