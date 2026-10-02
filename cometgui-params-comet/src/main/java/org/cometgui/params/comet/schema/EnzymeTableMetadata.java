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

package org.cometgui.params.comet.schema;

import java.util.List;
import java.util.Objects;

/**
 * What the metadata says about the {@code [COMET_ENZYME_INFO]} table. The rows themselves are not
 * curated: the defaults come from Comet's own {@code -q} output, and the codec that reads them
 * belongs to the structured value types.
 *
 * @param header the line that starts the table
 * @param helpUrl the upstream page describing it
 * @param rowFormat the field order of a row, in words
 * @param senseChoices what the {@code sense} field's values mean
 * @param referencedBy the parameters whose values are row numbers in this table
 */
public record EnzymeTableMetadata(
        String header,
        String helpUrl,
        String rowFormat,
        List<Choice> senseChoices,
        List<String> referencedBy) {

    /** Validates presence and takes immutable copies. */
    public EnzymeTableMetadata {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(helpUrl, "helpUrl");
        Objects.requireNonNull(rowFormat, "rowFormat");
        senseChoices = List.copyOf(senseChoices);
        referencedBy = List.copyOf(referencedBy);
    }

    /**
     * The sense values and their meanings, immutable.
     *
     * @return the choices
     */
    @Override
    public List<Choice> senseChoices() {
        return List.copyOf(senseChoices);
    }

    /**
     * The referencing parameter names, immutable.
     *
     * @return the names
     */
    @Override
    public List<String> referencedBy() {
        return List.copyOf(referencedBy);
    }
}
