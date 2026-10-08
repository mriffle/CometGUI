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

import java.util.Objects;
import java.util.Optional;

/**
 * The spectrum a Percolator row identifies, read from its {@code PSMId}.
 *
 * <p>Comet names each PSM by its {@code SpecId}, which Percolator carries into {@code PSMId}
 * unchanged: {@code <base>_<scan>_<charge>_<rank>}, where the base is Comet's {@code -N} output
 * name verbatim -- in a CometGUI run an absolute path, which may itself hold underscores, digits
 * and spaces. The identifier is therefore read <strong>from the right</strong>: the last three
 * underscore-separated fields are the rank, the charge and the scan, each a run of ASCII digits,
 * and everything before them, underscores included, is the base.
 *
 * <p>A {@code PSMId} not in that shape -- {@code psm84}, fewer than three trailing numeric fields,
 * an empty base, a sign, a non-ASCII digit, or a number too large for its type -- has no spectrum
 * reference, and {@link #of} says so with an empty result rather than guessing. Which spectrum file
 * a base names is the run's business, not this class's.
 *
 * @param base the {@code -N} base: everything before {@code _<scan>_<charge>_<rank>}, never empty
 * @param scan the scan number
 * @param charge the precursor charge
 * @param rank the rank of the match within its spectrum
 */
public record SpectrumReference(String base, long scan, int charge, int rank) {

    private static final char SEPARATOR = '_';

    /**
     * A reference.
     *
     * @throws IllegalArgumentException if the base is empty or a number is negative
     * @throws NullPointerException if the base is {@code null}
     */
    public SpectrumReference {
        Objects.requireNonNull(base, "base");
        if (base.isEmpty() || scan < 0 || charge < 0 || rank < 0) {
            throw new IllegalArgumentException(
                    "a spectrum reference needs a base and non-negative numbers: base '"
                            + base
                            + "', scan "
                            + scan
                            + ", charge "
                            + charge
                            + ", rank "
                            + rank);
        }
    }

    /**
     * Reads a {@code PSMId} in Comet's {@code SpecId} shape.
     *
     * @param psmId the identifier as Percolator wrote it
     * @return the reference, or empty when the identifier is not {@code
     *     <base>_<scan>_<charge>_<rank>}
     * @throws NullPointerException if {@code psmId} is {@code null}
     */
    public static Optional<SpectrumReference> of(String psmId) {
        Objects.requireNonNull(psmId, "psmId");
        // Each start is one past an underscore, or 0 when there is none to its left (lastIndexOf
        // returns -1 for a negative fromIndex, so a missing separator stays missing).
        int rankStart = psmId.lastIndexOf(SEPARATOR) + 1;
        int chargeStart = psmId.lastIndexOf(SEPARATOR, rankStart - 2) + 1;
        int scanStart = psmId.lastIndexOf(SEPARATOR, chargeStart - 2) + 1;
        if (scanStart <= 1) {
            // Fewer than three separators, or nothing before the scan's: no base.
            return Optional.empty();
        }
        long scan = digits(psmId, scanStart, chargeStart - 1, Long.MAX_VALUE);
        long charge = digits(psmId, chargeStart, rankStart - 1, Integer.MAX_VALUE);
        long rank = digits(psmId, rankStart, psmId.length(), Integer.MAX_VALUE);
        if (scan < 0 || charge < 0 || rank < 0) {
            return Optional.empty();
        }
        return Optional.of(
                new SpectrumReference(
                        psmId.substring(0, scanStart - 1), scan, (int) charge, (int) rank));
    }

    /**
     * Reads {@code text[from, to)} as a non-empty run of ASCII digits no larger than {@code max}.
     *
     * @return the value, or -1 if the range is empty, holds anything but {@code 0}-{@code 9}, or
     *     exceeds {@code max}
     */
    private static long digits(String text, int from, int to, long max) {
        if (from >= to) {
            return -1;
        }
        long value = 0;
        for (int index = from; index < to; index++) {
            char c = text.charAt(index);
            if (c < '0' || c > '9') {
                return -1;
            }
            int digit = c - '0';
            if (value > (max - digit) / 10) {
                return -1;
            }
            value = value * 10 + digit;
        }
        return value;
    }
}
