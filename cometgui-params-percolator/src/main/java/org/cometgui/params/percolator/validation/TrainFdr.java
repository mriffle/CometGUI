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
 * Percolator's {@code --trainFDR}: the false discovery rate that defines the positive examples
 * Percolator trains its classifier on.
 *
 * <p><strong>Not a result filter, and not {@link TestFdr} either.</strong> {@code R-PERC-04} and
 * {@code AC-RES-05}: a learning threshold inside Percolator, passed when the run starts, unrelated
 * to the PSM and peptide q-value filters that change only what is displayed and exported. A type of
 * its own, so that none of the three can be handed where another is expected.
 *
 * @param value the threshold, greater than 0 and at most 1, without trailing zeros
 */
public record TrainFdr(BigDecimal value) {

    /** Percolator's own default, 0.01, which is also this product's. */
    public static final TrainFdr DEFAULT = new TrainFdr(new BigDecimal("0.01"));

    /**
     * Validates the threshold.
     *
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws IllegalArgumentException if it is not greater than 0 and at most 1, naming the value
     *     -- 0 included, which Percolator reads as "use testFDR" and so would record a value that
     *     was not the one used
     */
    public TrainFdr {
        value = FdrValues.checked("trainFDR", value);
    }

    /**
     * Reads a threshold as a user typed it.
     *
     * @param text the text, for example {@code 0.05}; a full stop is the only decimal separator
     * @return the threshold
     * @throws NullPointerException if {@code text} is {@code null}
     * @throws IllegalArgumentException if it is not a number, or out of range, quoting it
     */
    public static TrainFdr parse(String text) {
        return new TrainFdr(FdrValues.parse("trainFDR", text));
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
