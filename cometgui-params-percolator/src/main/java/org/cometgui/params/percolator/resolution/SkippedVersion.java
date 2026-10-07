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

import java.util.List;
import java.util.Objects;
import org.cometgui.domain.tools.ToolOffer;

/**
 * A Percolator newer than the one resolution selected, which it did not select, and why ({@code
 * R-PERC-10}).
 *
 * @param offer the newer build
 * @param missing every capability an enabled stage needs that it cannot be counted on for, never
 *     empty
 * @param reason the sentence shown in the interface and recorded in provenance, naming the selected
 *     version, this version and the capability; built by {@link ResolutionMessages}
 */
public record SkippedVersion(ToolOffer offer, List<MissingCapability> missing, String reason) {

    /**
     * Validates the record.
     *
     * @throws NullPointerException if a component is {@code null}
     * @throws IllegalArgumentException if {@code missing} is empty or {@code reason} blank
     */
    public SkippedVersion {
        Objects.requireNonNull(offer, "offer");
        missing = List.copyOf(missing);
        Objects.requireNonNull(reason, "reason");
        if (missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "a skipped version must name at least one missing capability");
        }
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
    }
}
