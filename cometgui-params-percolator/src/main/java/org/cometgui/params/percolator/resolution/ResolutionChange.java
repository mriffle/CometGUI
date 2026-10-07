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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.tools.ToolOffer;

/**
 * What re-resolving did to the default Percolator -- after a downstream stage was switched on or
 * off, or the builds on this computer changed -- in a notice the interface shows ({@code
 * R-PERC-02}: "changing which downstream stages are enabled shall therefore re-evaluate the default
 * and tell the user it changed").
 *
 * <p>Two defaults are the same when they are the same version from the same origin. A build that
 * merely moved from not installed to installed is still the same default.
 *
 * @param changed whether the default is a different build
 * @param from the default before; empty when there was none
 * @param to the default after; empty when there is none
 * @param message the notice, naming both versions and the capability that made the difference
 */
public record ResolutionChange(
        boolean changed, Optional<ToolOffer> from, Optional<ToolOffer> to, String message) {

    /**
     * Validates the record.
     *
     * @throws NullPointerException if a component is {@code null}
     * @throws IllegalArgumentException if {@code message} is blank
     */
    public ResolutionChange {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(message, "message");
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }

    /**
     * Compares two resolutions, typically one before and one after a stage was switched.
     *
     * @param before the earlier resolution
     * @param after the later resolution
     * @return the change, with its notice
     * @throws NullPointerException if either argument is {@code null}
     */
    public static ResolutionChange between(
            PercolatorResolution before, PercolatorResolution after) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        ToolOffer from = before.selected().orElse(null);
        ToolOffer to = after.selected().orElse(null);
        boolean changed = !sameBuild(from, to);
        String cause = cause(before, after);
        String message =
                changed
                        ? ResolutionMessages.changed(from, to, cause)
                        : ResolutionMessages.unchanged(to, cause);
        return new ResolutionChange(changed, before.selected(), after.selected(), message);
    }

    private static boolean sameBuild(ToolOffer one, ToolOffer other) {
        if (one == null || other == null) {
            return one == other;
        }
        return one.version().equals(other.version()) && one.origin() == other.origin();
    }

    private static String cause(PercolatorResolution before, PercolatorResolution after) {
        List<String> parts = new ArrayList<>();
        for (DownstreamStage stage : DownstreamStage.values()) {
            boolean wasOn = before.enabledStages().contains(stage);
            boolean isOn = after.enabledStages().contains(stage);
            if (isOn && !wasOn) {
                parts.add(
                        after.isAvailable(stage)
                                ? ResolutionMessages.switchedOn(stage)
                                : ResolutionMessages.switchedOnButUnavailable(stage));
            } else if (wasOn && !isOn) {
                parts.add(ResolutionMessages.switchedOff(stage));
            }
        }
        if (parts.isEmpty()) {
            return ResolutionMessages.buildsChanged();
        }
        return String.join(", and ", parts);
    }
}
