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
 * The PSM display filter: {@code PSM visible := psm.q_value <= psm_filter}. Default 0.01, valid
 * range {@code [0, 1]}, inclusive; independent of {@link PeptideQValueFilter} and of Percolator's
 * {@code --testFDR}/{@code --trainFDR}. See {@link QValueFilter}.
 */
public final class PsmQValueFilter extends QValueFilter {

    private static final String WHAT = "PSM q-value filter";

    /** The default, 0.01 ({@code AC-RES-01}). */
    public static final PsmQValueFilter DEFAULT = new PsmQValueFilter(DEFAULT_CUTOFF);

    /**
     * A PSM filter.
     *
     * @param cutoff the cutoff
     * @throws IllegalArgumentException if it is below 0 or above 1
     * @throws NullPointerException if it is {@code null}
     */
    public PsmQValueFilter(BigDecimal cutoff) {
        super(checked(WHAT, cutoff));
    }

    /**
     * A PSM filter from typed text.
     *
     * @param text for example {@code 0.05}
     * @return the filter
     * @throws IllegalArgumentException if the text is not a number within {@code [0, 1]}
     */
    public static PsmQValueFilter parse(String text) {
        return new PsmQValueFilter(parseCutoff(WHAT, text));
    }

    /**
     * A PSM filter from a {@code double}.
     *
     * @param cutoff the cutoff
     * @return the filter
     * @throws IllegalArgumentException if it is {@code NaN}, infinite, below 0 or above 1
     */
    public static PsmQValueFilter of(double cutoff) {
        return new PsmQValueFilter(cutoffOf(WHAT, cutoff));
    }
}
