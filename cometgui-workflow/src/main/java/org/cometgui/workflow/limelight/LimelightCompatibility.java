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

package org.cometgui.workflow.limelight;

import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.run.RunId;
import org.cometgui.params.percolator.resolution.CompatibilityAction;

/**
 * Whether a finished run's Percolator produced what the Limelight converter reads, read from what
 * <strong>that run</strong> recorded -- its Percolator execution in {@code provenance.json} and its
 * Percolator XML -- and never from the Percolator selected now (phase 12, design decision P12-9;
 * {@code R-LL-01}, {@code AC-LL-03}). The {@link LimelightPrerequisite#PERCOLATOR_XML} refusal of
 * {@link LimelightConversions#check} is this explanation, so "can this run convert, and if not why"
 * has one answer.
 *
 * <p>When the run's Percolator XML is the reason it cannot convert, {@link #action} is what the
 * scientist is offered, computed from Phase 09's resolution of a Limelight-enabled run over the
 * Percolator builds the Tool Manager offers now: the compatible rerun, labelled with the version
 * resolution returns, or -- where no XML-capable build can be obtained here ({@code R-LL-02}) --
 * local-binary registration and no rerun.
 *
 * @param runId the run
 * @param percolator the run's recorded Percolator, as resolution names a build -- {@code 3.09
 *     (registered local binary)}, {@code 3.07.1} -- or empty when the run records none or its
 *     record cannot be read
 * @param explanation why the run cannot be converted, before any attempt; empty when its Percolator
 *     XML is there, recorded complete and unchanged
 * @param action what is offered instead: present only when the run's Percolator XML is why it
 *     cannot be converted
 */
public record LimelightCompatibility(
        RunId runId,
        Optional<String> percolator,
        Optional<String> explanation,
        Optional<CompatibilityAction> action) {

    /**
     * Validates the answer.
     *
     * @throws NullPointerException if a component is {@code null}
     * @throws IllegalArgumentException if an action is offered for a convertible run, or an
     *     explanation is blank
     */
    public LimelightCompatibility {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(percolator, "percolator");
        Objects.requireNonNull(explanation, "explanation");
        Objects.requireNonNull(action, "action");
        if (explanation.isEmpty() && action.isPresent()) {
            throw new IllegalArgumentException(
                    "a run that can be converted is offered no Percolator action");
        }
        if (explanation.isPresent() && explanation.get().isBlank()) {
            throw new IllegalArgumentException("a run that cannot be converted says why");
        }
    }

    /**
     * Whether the run's Percolator wrote the XML the converter reads, recorded and unchanged.
     *
     * @return {@code true} exactly when there is no explanation
     */
    public boolean convertible() {
        return explanation.isEmpty();
    }
}
