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

package org.cometgui.results.filtering;

import java.util.Objects;

/**
 * The two display q-value filters a results view applies: one to the PSM table, one to the peptide
 * table, each set without touching the other ({@code AC-RES-03}). This is view state; it is never
 * passed to Percolator and changing it reruns nothing.
 *
 * @param psm the PSM filter
 * @param peptide the peptide filter
 */
public record DisplayFilters(PsmQValueFilter psm, PeptideQValueFilter peptide) {

    /** Both at their default, 0.01 and 0.01 ({@code AC-RES-01}, {@code AC-RES-02}). */
    public static final DisplayFilters DEFAULTS =
            new DisplayFilters(PsmQValueFilter.DEFAULT, PeptideQValueFilter.DEFAULT);

    /**
     * Both filters.
     *
     * @throws NullPointerException if either is {@code null}
     */
    public DisplayFilters {
        Objects.requireNonNull(psm, "psm");
        Objects.requireNonNull(peptide, "peptide");
    }

    /**
     * A new PSM filter, the peptide filter unchanged.
     *
     * @param newPsm the PSM filter
     * @return the filters
     */
    public DisplayFilters withPsm(PsmQValueFilter newPsm) {
        return new DisplayFilters(newPsm, peptide);
    }

    /**
     * A new peptide filter, the PSM filter unchanged.
     *
     * @param newPeptide the peptide filter
     * @return the filters
     */
    public DisplayFilters withPeptide(PeptideQValueFilter newPeptide) {
        return new DisplayFilters(psm, newPeptide);
    }
}
