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

import java.util.Objects;

/**
 * One search hit with the spectrum query it belongs to.
 *
 * @param query the query's attributes, shared by every hit of the query that was kept
 * @param hit the hit
 */
public record PepXmlPsm(PepXmlQuery query, PepXmlSearchHit hit) {

    /**
     * Validates the pair.
     *
     * @throws NullPointerException if either component is {@code null}
     */
    public PepXmlPsm {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(hit, "hit");
    }

    /**
     * This hit's key.
     *
     * @return the query's scan and charge with the hit's position
     */
    public PepXmlHitKey key() {
        return new PepXmlHitKey(query.startScan(), query.assumedCharge(), hit.position());
    }
}
