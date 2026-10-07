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

import java.math.BigDecimal;

/**
 * The peptide display filter: {@code Peptide visible := peptide.q_value <= peptide_filter}. Default
 * 0.01, valid range {@code [0, 1]}, inclusive; independent of {@link PsmQValueFilter} and of
 * Percolator's {@code --testFDR}/{@code --trainFDR}. See {@link QValueFilter}.
 */
public final class PeptideQValueFilter extends QValueFilter {

    private static final String WHAT = "peptide q-value filter";

    /** The default, 0.01 ({@code AC-RES-02}). */
    public static final PeptideQValueFilter DEFAULT = new PeptideQValueFilter(DEFAULT_CUTOFF);

    /**
     * A peptide filter.
     *
     * @param cutoff the cutoff
     * @throws IllegalArgumentException if it is below 0 or above 1
     * @throws NullPointerException if it is {@code null}
     */
    public PeptideQValueFilter(BigDecimal cutoff) {
        super(checked(WHAT, cutoff));
    }

    /**
     * A peptide filter from typed text.
     *
     * @param text for example {@code 0.05}
     * @return the filter
     * @throws IllegalArgumentException if the text is not a number within {@code [0, 1]}
     */
    public static PeptideQValueFilter parse(String text) {
        return new PeptideQValueFilter(parseCutoff(WHAT, text));
    }

    /**
     * A peptide filter from a {@code double}.
     *
     * @param cutoff the cutoff
     * @return the filter
     * @throws IllegalArgumentException if it is {@code NaN}, infinite, below 0 or above 1
     */
    public static PeptideQValueFilter of(double cutoff) {
        return new PeptideQValueFilter(cutoffOf(WHAT, cutoff));
    }
}
