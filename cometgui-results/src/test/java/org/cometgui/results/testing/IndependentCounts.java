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

package org.cometgui.results.testing;

/**
 * One table's rows at one cutoff, as counted by something other than the code under test: the large
 * fixture's generator, {@code awk}, or {@link IndependentCounter}. Deliberately not {@code
 * org.cometgui.results.filtering.FilterCounts}, so that an independent count shares no type with
 * the counts it checks.
 *
 * @param total every data row
 * @param passing rows with a known q-value at or below the cutoff
 * @param failing rows with a known q-value above it
 * @param unknown rows whose q-value is missing, unparsable or outside {@code [0, 1]}
 */
public record IndependentCounts(long total, long passing, long failing, long unknown) {

    /**
     * Requires the four to add up.
     *
     * @throws IllegalArgumentException if a count is negative or {@code passing + failing + unknown
     *     != total}
     */
    public IndependentCounts {
        if (total < 0 || passing < 0 || failing < 0 || unknown < 0) {
            throw new IllegalArgumentException(
                    "a negative count: total "
                            + total
                            + ", passing "
                            + passing
                            + ", failing "
                            + failing
                            + ", unknown "
                            + unknown);
        }
        if (passing + failing + unknown != total) {
            throw new IllegalArgumentException(
                    "passing "
                            + passing
                            + " + failing "
                            + failing
                            + " + unknown "
                            + unknown
                            + " != total "
                            + total);
        }
    }
}
