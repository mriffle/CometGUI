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

package org.cometgui.tools.limelight;

import java.math.BigDecimal;

/**
 * The Limelight q-value cutoff: the converter's one {@code -q/--q-value} option, its own value and
 * never one of the display filters (design decision P12-6).
 *
 * <h2>What {@code -q} does, established by running the pinned v2.8.1 converter</h2>
 *
 * <p>On 2026-10-11 the K562 fixture ({@code scratch/scientific-path}: Comet 2026.02.2 pepXML for
 * {@code _3} and {@code _4}, Percolator 3.07.1 pout XML) was converted with no {@code -q} and with
 * {@code -q} 0.01, 0.05, 0, 1, 0.001, -0.5 and 2, and every output was compared with the 0.01 one.
 *
 * <ul>
 *   <li><strong>It changes exactly one value in the document</strong>: the {@code
 *       default_filter_value} of the Percolator search program's PSM-level {@code
 *       filterable_psm_annotation_type name="q-value"}. At {@code -q 0.05} that line reads {@code
 *       default_filter_value="0.05"}; nothing else differs except the {@code conversion_program}
 *       element's timestamp and argument list.
 *   <li><strong>It filters nothing.</strong> Every output holds all 3897 {@code psm} and 2985
 *       {@code reported_peptide} elements, whatever the cutoff: it is the default filter a
 *       Limelight viewer starts from, not a filter applied at conversion.
 *   <li>The reported-peptide {@code q-value} default stays {@code 0.01} at every cutoff, and the
 *       output with no {@code -q} is the output with {@code -q 0.01} -- so the converter's default
 *       is 0.01, and passing it explicitly changes nothing but the recorded arguments.
 *   <li>The converter parses the value as a {@code BigDecimal} and accepts <em>any</em> number:
 *       {@code -0.5} and {@code 2} were written into the file as given. {@code NaN}, {@code
 *       Infinity} and {@code abc} were rejected with "Invalid value for option '--q-value'" -- and
 *       exit status <strong>0</strong>, writing no file.
 * </ul>
 *
 * <p>So the range is this product's, not the converter's: a q-value is a false-discovery rate,
 * finite and within [0, 1], and anything else is refused here, before any command is built. {@link
 * LimelightConverter} checks after every conversion that the file carries the value asked for.
 *
 * @param value the cutoff, finite and within [0, 1]
 */
public record LimelightQValueCutoff(double value) {

    /** The cutoff a conversion uses unless the scientist changes it: 0.01 ({@code R-LL}). */
    public static final LimelightQValueCutoff DEFAULT = new LimelightQValueCutoff(0.01);

    /**
     * Validates the cutoff.
     *
     * @throws IllegalArgumentException if the value is not a number, is infinite, or lies outside
     *     [0, 1]
     */
    public LimelightQValueCutoff {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                    "the Limelight q-value cutoff must be a number from 0 to 1, but was "
                            + value
                            + ". The converter itself accepts any number and writes it into the"
                            + " file as the q-value filter Limelight starts from, so a value"
                            + " outside that range would not be refused by anything else");
        }
    }

    /**
     * The value as the converter is given it: plain decimal notation, no exponent, no trailing
     * zeros, independent of the default locale.
     *
     * @return the argument text, for example {@code 0.01}
     */
    public String argument() {
        return asDecimal().toPlainString();
    }

    /**
     * The value as an exact decimal, the way the converter reads it.
     *
     * @return the decimal, with trailing zeros stripped
     */
    public BigDecimal asDecimal() {
        return BigDecimal.valueOf(value).stripTrailingZeros();
    }
}
