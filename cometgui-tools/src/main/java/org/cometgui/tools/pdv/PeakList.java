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

package org.cometgui.tools.pdv;

import java.util.Arrays;
import java.util.Objects;

/**
 * One spectrum's peaks: the m/z and intensity arrays, and how the source stored each.
 *
 * <p>Immutable: both arrays are copied in and every accessor copies out, so a caller cannot change
 * a spectrum another caller is about to write.
 */
public final class PeakList {

    private final double[] mz;
    private final double[] intensity;
    private final PeakEncoding mzEncoding;
    private final PeakEncoding intensityEncoding;

    /**
     * Creates a peak list.
     *
     * @param mz the m/z values, in the source's order
     * @param mzEncoding how the source stored them
     * @param intensity the intensities, one per m/z value
     * @param intensityEncoding how the source stored them
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if the two arrays differ in length
     */
    public PeakList(
            double[] mz,
            PeakEncoding mzEncoding,
            double[] intensity,
            PeakEncoding intensityEncoding) {
        this.mz = Objects.requireNonNull(mz, "mz").clone();
        this.intensity = Objects.requireNonNull(intensity, "intensity").clone();
        this.mzEncoding = Objects.requireNonNull(mzEncoding, "mzEncoding");
        this.intensityEncoding = Objects.requireNonNull(intensityEncoding, "intensityEncoding");
        if (mz.length != intensity.length) {
            throw new IllegalArgumentException(
                    "a peak list needs one intensity per m/z value, but has "
                            + mz.length
                            + " m/z values and "
                            + intensity.length
                            + " intensities");
        }
    }

    /**
     * How many peaks there are.
     *
     * @return the count
     */
    public int size() {
        return mz.length;
    }

    /**
     * The m/z values.
     *
     * @return a copy, in the source's order
     */
    public double[] mz() {
        return mz.clone();
    }

    /**
     * The intensities.
     *
     * @return a copy, in the source's order
     */
    public double[] intensity() {
        return intensity.clone();
    }

    /**
     * How the source stored the m/z values.
     *
     * @return the encoding
     */
    public PeakEncoding mzEncoding() {
        return mzEncoding;
    }

    /**
     * How the source stored the intensities.
     *
     * @return the encoding
     */
    public PeakEncoding intensityEncoding() {
        return intensityEncoding;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PeakList that
                && Arrays.equals(mz, that.mz)
                && Arrays.equals(intensity, that.intensity)
                && mzEncoding == that.mzEncoding
                && intensityEncoding == that.intensityEncoding;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Arrays.hashCode(mz), Arrays.hashCode(intensity), mzEncoding, intensityEncoding);
    }

    @Override
    public String toString() {
        return "PeakList["
                + mz.length
                + " peaks, m/z "
                + mzEncoding
                + ", intensity "
                + intensityEncoding
                + "]";
    }
}
