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

package org.cometgui.params.percolator.resolution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.cometgui.domain.tools.ToolAdvisory;
import org.cometgui.domain.tools.ToolOffer;

/**
 * A Percolator build's version advisories as the interface shows them at selection time and as
 * provenance records them ({@code R-PERC-11}).
 *
 * <p>The advisories are the manifest's data, carried on the {@link ToolOffer}; nothing here decides
 * which advisory a version has. Both renderings keep the manifest's order, which is deterministic
 * and is the order the manifest's author chose.
 */
public final class AdvisoryRendering {

    private AdvisoryRendering() {}

    /**
     * The sentences shown when the build is selected, one per advisory. Each manifest sentence
     * already names the version it is about, so it is shown as written.
     *
     * @param offer the selected build
     * @return the advisory texts, immutable; empty when it has none
     * @throws NullPointerException if {@code offer} is {@code null}
     */
    public static List<String> forSelection(ToolOffer offer) {
        Objects.requireNonNull(offer, "offer");
        List<String> lines = new ArrayList<>();
        for (ToolAdvisory advisory : offer.advisories()) {
            lines.add(advisory.text());
        }
        return List.copyOf(lines);
    }

    /**
     * The advisories as provenance records them: stable identifier to text.
     *
     * @param offer the build the run used
     * @return identifier to text, in manifest order, immutable; empty when it has none
     * @throws NullPointerException if {@code offer} is {@code null}
     */
    public static Map<String, String> forProvenance(ToolOffer offer) {
        Objects.requireNonNull(offer, "offer");
        Map<String, String> recorded = new LinkedHashMap<>();
        for (ToolAdvisory advisory : offer.advisories()) {
            recorded.put(advisory.id(), advisory.text());
        }
        return Collections.unmodifiableMap(recorded);
    }
}
