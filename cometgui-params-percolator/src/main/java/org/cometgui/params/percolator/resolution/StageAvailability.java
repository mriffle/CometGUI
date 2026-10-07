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
import java.util.Optional;

/**
 * Whether one downstream stage can run with any Percolator on this computer, and when it cannot,
 * why and what to do about it ({@code R-PERC-03}).
 *
 * <p>Answered whether or not the stage is enabled, so that the interface can show Limelight
 * conversion as unavailable -- with its explanation and remedies -- before the scientist switches
 * it on, rather than letting the switch fail.
 *
 * @param stage the stage
 * @param available whether some usable Percolator here was observed to satisfy it
 * @param explanation when unavailable, why -- naming the capability and any build that claims it
 *     without having been observed to have it; empty when available
 * @param remedies when unavailable, the stage's remedies; empty when available
 */
public record StageAvailability(
        DownstreamStage stage,
        boolean available,
        Optional<String> explanation,
        List<StageRemedy> remedies) {

    /**
     * Validates the record.
     *
     * @throws NullPointerException if a component is {@code null}
     * @throws IllegalArgumentException if an available stage carries an explanation or remedies, or
     *     an unavailable one lacks either
     */
    public StageAvailability {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(explanation, "explanation");
        remedies = List.copyOf(remedies);
        if (available && (explanation.isPresent() || !remedies.isEmpty())) {
            throw new IllegalArgumentException(
                    stage.id() + " is available, so it has nothing to explain or remedy");
        }
        if (!available && (explanation.isEmpty() || remedies.isEmpty())) {
            throw new IllegalArgumentException(
                    stage.id() + " is unavailable, so it must say why and what to do about it");
        }
    }
}
