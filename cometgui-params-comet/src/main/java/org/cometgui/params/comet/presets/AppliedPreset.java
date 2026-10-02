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
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.validation.ValidationReport;

/**
 * The result of applying all or selected rows of a {@link PresetDiff}: a new parameter set, the
 * rows that were applied, and the validation of the new set -- a preset that makes a configuration
 * invalid is reported, not hidden. The set the diff was taken from is unchanged.
 *
 * @param model the new parameter set; the applied parameters have origin {@code PRESET}
 * @param applied the rows applied, in schema order
 * @param validation the standard validation of {@code model}
 * @param compatibility the preset's compatibility check against the set's Comet version, carried
 *     from the diff so that a delta the version could not take is still reported here
 */
public record AppliedPreset(
        CometParameters model,
        List<DiffRow> applied,
        ValidationReport validation,
        CompatibilityReport compatibility) {

    /** Validates presence and takes an immutable copy of the rows. */
    public AppliedPreset {
        Objects.requireNonNull(model, "model");
        applied = List.copyOf(applied);
        Objects.requireNonNull(validation, "validation");
        Objects.requireNonNull(compatibility, "compatibility");
    }

    /**
     * The applied rows, immutable.
     *
     * @return the rows
     */
    @Override
    public List<DiffRow> applied() {
        return List.copyOf(applied);
    }
}
