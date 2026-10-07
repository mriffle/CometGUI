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
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.tools.ToolAdvisory;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolOffer;

/**
 * What <em>latest compatible</em> resolution decided, and everything it has to say about it ({@code
 * R-PERC-02}, {@code R-PERC-10}, {@code R-PERC-03}, {@code R-PERC-11}). Built by {@link
 * PercolatorResolver}; the interface shows it and provenance records it.
 *
 * @param enabledStages the downstream stages switched on when this was resolved
 * @param selected the default Percolator, or empty when no Percolator can be used here at all
 * @param selectionReason why it is the default -- the {@code R-PERC-10} sentences naming each newer
 *     version passed over and the capability it lacks -- or, with nothing selected, that nothing
 *     can be
 * @param skipped every candidate newer than {@code selected} that was not selected, newest first
 * @param stages one availability per {@link DownstreamStage}, in declaration order, enabled or not
 * @param excluded every offer that could not be a candidate -- not runnable here, failed, or not
 *     installable -- in the order given
 * @param advisories the selected build's advisories, in manifest order; empty with nothing selected
 */
public record PercolatorResolution(
        Set<DownstreamStage> enabledStages,
        Optional<ToolOffer> selected,
        String selectionReason,
        List<SkippedVersion> skipped,
        List<StageAvailability> stages,
        List<ToolOffer> excluded,
        List<ToolAdvisory> advisories) {

    /**
     * Copies every collection and checks the record is whole.
     *
     * @throws NullPointerException if a component is {@code null} or holds {@code null}
     * @throws IllegalArgumentException if {@code stages} does not hold exactly one availability per
     *     downstream stage in declaration order, or {@code selectionReason} is blank
     */
    public PercolatorResolution {
        EnumSet<DownstreamStage> stagesOn = EnumSet.noneOf(DownstreamStage.class);
        stagesOn.addAll(Objects.requireNonNull(enabledStages, "enabledStages"));
        enabledStages = Collections.unmodifiableSet(stagesOn);
        Objects.requireNonNull(selected, "selected");
        Objects.requireNonNull(selectionReason, "selectionReason");
        if (selectionReason.isBlank()) {
            throw new IllegalArgumentException("selectionReason must not be blank");
        }
        skipped = List.copyOf(skipped);
        stages = List.copyOf(stages);
        excluded = List.copyOf(excluded);
        advisories = List.copyOf(advisories);
        List<DownstreamStage> order = new ArrayList<>();
        for (StageAvailability availability : stages) {
            order.add(availability.stage());
        }
        if (!order.equals(List.of(DownstreamStage.values()))) {
            throw new IllegalArgumentException(
                    "stages must hold one availability per downstream stage, in declaration"
                            + " order, but named "
                            + order);
        }
    }

    /**
     * The availability of one stage.
     *
     * @param stage the stage
     * @return its availability
     * @throws NullPointerException if {@code stage} is {@code null}
     */
    public StageAvailability availability(DownstreamStage stage) {
        return stages.get(Objects.requireNonNull(stage, "stage").ordinal());
    }

    /**
     * Whether a stage can run with some Percolator here.
     *
     * @param stage the stage
     * @return {@code true} if it is available
     * @throws NullPointerException if {@code stage} is {@code null}
     */
    public boolean isAvailable(DownstreamStage stage) {
        return availability(stage).available();
    }

    /**
     * The selected build's observed capabilities: the set the settings' applicability and the
     * command builder read.
     *
     * @return the observed capabilities, immutable; empty with nothing selected
     */
    public Set<ToolCapability> selectedCapabilities() {
        return selected.map(PercolatorResolver::observedCapabilities)
                .orElse(Collections.unmodifiableSet(EnumSet.noneOf(ToolCapability.class)));
    }
}
