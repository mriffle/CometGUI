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

package org.cometgui.params.comet.model;

import java.util.Optional;

/**
 * Where a search's decoys come from ({@code R-DEC-01}): the specification's three decoy sources,
 * presented to the user as one value instead of the bare number {@code decoy_search}.
 *
 * <p>Each source is exactly one {@code decoy_search} value. Comet 2026.02.2 accepts {@code 0},
 * {@code 1} and {@code 2} and treats any other value as {@code 0} ({@code CometSearchManager.cpp}
 * at {@code v2026.02.2}, where {@code decoy_search} is read into {@code iDecoySearch}); such a
 * value has no source here, and the validators report it.
 */
public enum DecoySource {

    /**
     * {@code decoy_search = 0}: Comet generates no decoys, so the FASTA must already contain them.
     * Whether it does is checked against the file before the run ({@code R-DEC-02}), not here.
     */
    FASTA_CONTAINS_DECOYS(0),

    /** {@code decoy_search = 1}: Comet's internal decoys, reported together with the targets. */
    COMET_INTERNAL_CONCATENATED(1),

    /** {@code decoy_search = 2}: Comet's internal decoys, reported separately. */
    COMET_INTERNAL_SEPARATE(2);

    /** The parameter that holds the decoy source. */
    public static final String PARAMETER = "decoy_search";

    private final int decoySearch;

    DecoySource(int decoySearch) {
        this.decoySearch = decoySearch;
    }

    /**
     * The {@code decoy_search} value this source is written as.
     *
     * @return {@code 0}, {@code 1} or {@code 2}
     */
    public int decoySearch() {
        return decoySearch;
    }

    /**
     * The source a {@code decoy_search} value means.
     *
     * @param decoySearch the value
     * @return the source, or empty for a value Comet does not document
     */
    public static Optional<DecoySource> fromDecoySearch(int decoySearch) {
        for (DecoySource source : values()) {
            if (source.decoySearch == decoySearch) {
                return Optional.of(source);
            }
        }
        return Optional.empty();
    }
}
