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

package org.cometgui.results.filtering.store;

import java.util.Objects;
import org.cometgui.results.filtering.PeptideQValueFilter;
import org.cometgui.results.filtering.PsmQValueFilter;
import org.cometgui.results.filtering.QValueFilter;

/**
 * Which of Percolator's four result tables a store holds. The four share one shape, so the reader
 * cannot tell them apart; the caller that names the file says which it is.
 *
 * <p>The kind decides which display filter applies ({@code AC-RES-03}): a PSM table, target or
 * decoy, is filtered by the {@link PsmQValueFilter} and a peptide table by the {@link
 * PeptideQValueFilter}. A store refuses the other one, so the two filters cannot be crossed by a
 * wiring mistake.
 */
public enum TableKind {

    /** The target PSM table ({@code --results-psms}). */
    TARGET_PSMS(true, false),

    /** The decoy PSM table ({@code --decoy-results-psms}). */
    DECOY_PSMS(true, true),

    /** The target peptide table ({@code --results-peptides}). */
    TARGET_PEPTIDES(false, false),

    /** The decoy peptide table ({@code --decoy-results-peptides}). */
    DECOY_PEPTIDES(false, true);

    private final boolean psms;
    private final boolean decoy;

    TableKind(boolean psms, boolean decoy) {
        this.psms = psms;
        this.decoy = decoy;
    }

    /**
     * Whether this is a PSM table.
     *
     * @return {@code true} for a PSM table, {@code false} for a peptide table
     */
    public boolean isPsms() {
        return psms;
    }

    /**
     * Whether this is a decoy table.
     *
     * @return {@code true} for a decoy table
     */
    public boolean isDecoy() {
        return decoy;
    }

    /**
     * Checks that a filter is the one this kind of table is filtered by.
     *
     * @param filter the filter
     * @return the same filter
     * @throws IllegalArgumentException if a PSM table is given a peptide filter or the reverse
     * @throws NullPointerException if {@code filter} is {@code null}
     */
    public QValueFilter check(QValueFilter filter) {
        Objects.requireNonNull(filter, "filter");
        boolean psmFilter = filter instanceof PsmQValueFilter;
        if (psmFilter != psms) {
            throw new IllegalArgumentException(
                    "the "
                            + this
                            + " table is filtered by the "
                            + (psms ? "PSM" : "peptide")
                            + " q-value filter, not by "
                            + filter);
        }
        return filter;
    }
}
