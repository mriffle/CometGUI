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
 * Whether one feature's normalised weight has the same sign in every cross-validation split ({@code
 * R-PERC-08}, design decision P10-8).
 *
 * <p>Each weight is exactly one of <em>positive</em> ({@code > 0}), <em>negative</em> ({@code < 0})
 * or <em>zero</em> ({@code == 0}, which includes {@code -0.0}: a zero is neither sign). The verdict
 * asks whether every split falls in the same one of those three classes:
 *
 * <ul>
 *   <li>{@link #ALL_POSITIVE}: every split's weight is positive;
 *   <li>{@link #ALL_NEGATIVE}: every split's weight is negative;
 *   <li>{@link #ALL_ZERO}: every split's weight is zero;
 *   <li>{@link #MIXED}: anything else -- the splits fall in two or three of the classes.
 * </ul>
 *
 * <p>So a zero mixed with positives (say {@code 0, 0.2, 0.3}) is {@link #MIXED}, not {@link
 * #ALL_POSITIVE}: the splits did not agree, because one of them learned no direction for the
 * feature at all. The count of each class is on {@link FeatureWeights} for a view that wants to say
 * how they disagreed. Percolator writes four decimal places, so a "zero" here is a weight that
 * rounded to {@code 0.0000} in the file; the verdict is taken on the file's values, as written.
 */
public enum SignConsistency {
    /** Every split's weight is above zero. */
    ALL_POSITIVE,
    /** Every split's weight is below zero. */
    ALL_NEGATIVE,
    /** The splits' weights fall in more than one of positive, negative and zero. */
    MIXED,
    /** Every split's weight is zero. */
    ALL_ZERO;

    /**
     * The verdict from the counts of each class.
     *
     * @param positive how many splits' weights are above zero
     * @param negative how many are below zero
     * @param splits how many splits there are, at least one; the rest are zero
     * @return the verdict
     */
    static SignConsistency of(int positive, int negative, int splits) {
        if (positive == splits) {
            return ALL_POSITIVE;
        }
        if (negative == splits) {
            return ALL_NEGATIVE;
        }
        if (positive + negative == 0) {
            return ALL_ZERO;
        }
        return MIXED;
    }
}
