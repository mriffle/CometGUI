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

package org.cometgui.params.percolator.validation;

import java.math.BigDecimal;

/**
 * Percolator's {@code --testFDR}: the false discovery rate at which Percolator selects the best
 * cross-validation result and reports its final results.
 *
 * <p><strong>Not the PSM q-value result filter.</strong> {@code R-PERC-04} and {@code AC-RES-05}:
 * this is a learning threshold that changes what Percolator computes, and it reaches Percolator
 * when the run starts. The result filters change only which rows are displayed and exported, and
 * never rerun anything. Both default to 0.01, which is exactly why they are easy to confuse and why
 * this is a type of its own rather than a number that could be passed where a filter is expected.
 *
 * @param value the threshold, greater than 0 and at most 1, without trailing zeros
 */
public record TestFdr(BigDecimal value) {

    /** Percolator's own default, 0.01, which is also this product's. */
    public static final TestFdr DEFAULT = new TestFdr(new BigDecimal("0.01"));

    /**
     * Validates the threshold.
     *
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws IllegalArgumentException if it is not greater than 0 and at most 1, naming the value
     */
    public TestFdr {
        value = FdrValues.checked("testFDR", value);
    }

    /**
     * Reads a threshold as a user typed it.
     *
     * @param text the text, for example {@code 0.05}; a full stop is the only decimal separator
     * @return the threshold
     * @throws NullPointerException if {@code text} is {@code null}
     * @throws IllegalArgumentException if it is not a number, or out of range, quoting it
     */
    public static TestFdr parse(String text) {
        return new TestFdr(FdrValues.parse("testFDR", text));
    }

    /**
     * The value as it is written on a command line and in provenance: plain decimal notation, no
     * exponent, no grouping, the same in every locale.
     *
     * @return for example {@code 0.01}
     */
    public String text() {
        return value.toPlainString();
    }
}
