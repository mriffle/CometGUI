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

import java.util.Objects;
import org.cometgui.params.comet.value.EnzymeDefinition;

/**
 * One row of the configuration's enzyme table, as the enzyme selectors offer it.
 *
 * @param row the row
 * @param senseWords the side it cleaves on, in the metadata's words
 */
public record EnzymeOption(EnzymeDefinition row, String senseWords) {

    /** Validates presence. */
    public EnzymeOption {
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(senseWords, "senseWords");
    }

    /**
     * The row's number, which a selector writes into the enzyme parameter.
     *
     * @return the number
     */
    public int number() {
        return row.number();
    }

    /**
     * What a selector shows.
     *
     * @return for example {@code 1. Trypsin}
     */
    public String label() {
        return row.number() + ". " + row.name();
    }

    /**
     * The row's rule in words: the sense, the cut residues and the no-cut residues, "none" for an
     * empty set; a row with neither is non-specific cleavage.
     *
     * @return for example {@code Cleaves on the C-terminal side of the cut residues; cut residues
     *     KR; no-cut residues P}
     */
    public String description() {
        if (row.nonSpecific()) {
            return "No enzyme rule: non-specific cleavage (no cut and no no-cut residues)";
        }
        return senseWords
                + "; cut residues "
                + words(row.cutResidues())
                + "; no-cut residues "
                + words(row.noCutResidues());
    }

    private static String words(String residues) {
        return residues.isEmpty() ? "none" : residues;
    }
}
