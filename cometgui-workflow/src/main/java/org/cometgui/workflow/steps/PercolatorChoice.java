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

package org.cometgui.workflow.steps;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.params.percolator.resolution.DownstreamStage;
import org.cometgui.params.percolator.resolution.PercolatorResolution;

/**
 * The Percolator half of a search: which build runs, with which settings, for which enabled
 * downstream stages, and the resolution that was shown when it was chosen. A {@link SearchRequest}
 * carrying one plans {@code resolve-percolator}, {@code run-percolator} and {@code
 * parse-percolator} after the Comet steps.
 *
 * <p>The selection need not be the resolution's default: a scientist may choose another installed
 * build. Provenance then records both -- the build that ran, and what resolution would have chosen
 * and why ({@code R-PERC-10}).
 *
 * @param selection the build that runs
 * @param settings the Percolator settings
 * @param enabledStages the downstream stages switched on; equal to the resolution's
 * @param resolution the <em>latest compatible</em> resolution computed for those stages
 */
public record PercolatorChoice(
        PercolatorSelection selection,
        PercolatorSettings settings,
        Set<DownstreamStage> enabledStages,
        PercolatorResolution resolution) {

    /**
     * Validates and copies.
     *
     * @throws NullPointerException naming a component that is {@code null}, or if {@code
     *     enabledStages} holds {@code null}
     * @throws IllegalArgumentException if the resolution was computed for other stages, naming both
     */
    public PercolatorChoice {
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(resolution, "resolution");
        Set<DownstreamStage> stages = EnumSet.noneOf(DownstreamStage.class);
        stages.addAll(Objects.requireNonNull(enabledStages, "enabledStages"));
        if (!stages.equals(resolution.enabledStages())) {
            throw new IllegalArgumentException(
                    "the resolution was computed for the enabled stages "
                            + resolution.enabledStages()
                            + ", but the run enables "
                            + stages);
        }
        enabledStages = Collections.unmodifiableSet(stages);
    }

    @Override
    public Set<DownstreamStage> enabledStages() {
        Set<DownstreamStage> copy = EnumSet.noneOf(DownstreamStage.class);
        copy.addAll(enabledStages);
        return Collections.unmodifiableSet(copy);
    }

    /**
     * Whether an enabled downstream stage needs Percolator's pout XML -- read from the stages'
     * requirement table, never from the build or its version (P9-2).
     *
     * @return {@code true} if some enabled stage requires {@link ToolCapability#XML_OUTPUT}
     */
    public boolean xmlNeeded() {
        for (DownstreamStage stage : enabledStages) {
            if (stage.requiredCapabilities().contains(ToolCapability.XML_OUTPUT)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the build that runs is the one resolution chose.
     *
     * @return {@code true} when the selection's offer is the resolution's default
     */
    public boolean isResolvedDefault() {
        return resolution.selected().map(selection.offer()::equals).orElse(false);
    }
}
