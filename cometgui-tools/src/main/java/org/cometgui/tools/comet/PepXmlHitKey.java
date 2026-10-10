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

package org.cometgui.tools.comet;

/**
 * Where a Percolator PSM's hit is in one pepXML file: the {@code scan}, {@code charge} and {@code
 * rank} of its {@code PSMId}, {@code <base>_<scan>_<charge>_<rank>}, whose base names the file.
 *
 * <p>The rank is the hit's 1-based <strong>position</strong> in its query, not its {@code hit_rank}
 * (P11-13).
 *
 * @param startScan the query's {@code start_scan}
 * @param assumedCharge the query's {@code assumed_charge}
 * @param position the hit's 1-based position in the query
 */
public record PepXmlHitKey(long startScan, int assumedCharge, int position) {

    /**
     * Validates the key.
     *
     * @throws IllegalArgumentException if the scan or charge is negative or the position is not at
     *     least 1
     */
    public PepXmlHitKey {
        if (startScan < 0 || assumedCharge < 0 || position < 1) {
            throw new IllegalArgumentException(
                    "a hit key needs a non-negative scan and charge and a position of at least 1:"
                            + " scan "
                            + startScan
                            + ", charge "
                            + assumedCharge
                            + ", position "
                            + position);
        }
    }
}
