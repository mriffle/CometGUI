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

package org.cometgui.results.parser;

/**
 * The columns a Percolator PSM or peptide table must carry, by the name its header gives them.
 *
 * <p>Observed identical on 3.06.5, 3.07.1 and 3.09, in the target and decoy PSM and peptide tables
 * alike (the peptide table, too, heads its first column {@code PSMId}). They are found by name,
 * never by position and never by version, so a build that reorders them or adds a column is read
 * correctly, and one that renames one is refused with a message naming it.
 */
public enum ResultColumn {

    /** The PSM identifier; in the peptide table, the identifier of the peptide's best PSM. */
    PSM_ID("PSMId"),

    /** Percolator's score. */
    SCORE("score"),

    /** The q-value: the only field with a policy (R-RES-02). */
    Q_VALUE("q-value"),

    /** The posterior error probability. */
    POSTERIOR_ERROR_PROBABILITY("posterior_error_prob"),

    /** The peptide, with flanking residues as Percolator writes it. */
    PEPTIDE("peptide"),

    /**
     * The protein identifiers. When this is the header's last column, a row carries one protein in
     * it and every further protein in a further tab-separated field.
     */
    PROTEIN_IDS("proteinIds");

    private final String headerName;

    ResultColumn(String headerName) {
        this.headerName = headerName;
    }

    /**
     * The column's name in the header.
     *
     * @return for example {@code q-value}
     */
    public String headerName() {
        return headerName;
    }
}
