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

import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.tools.CapabilityEvidence;
import org.cometgui.domain.tools.ToolCapability;

/**
 * One capability an enabled stage needs that one Percolator build cannot be counted on for -- and
 * which of the two reasons applies.
 *
 * <p>The two reasons are different sentences to a scientist ({@code R-TOOL-08}): a build that
 * <em>lacks</em> the capability will never have it, while a build whose manifest <em>claims</em> it
 * from evidence other than execution may well have it, and installing it will probe it.
 *
 * @param capability the capability
 * @param stage the enabled stage that needs it
 * @param unobservedClaim the evidence of the build's claim when it claims the capability without
 *     having been observed to have it; empty when it does not claim it at all
 */
public record MissingCapability(
        ToolCapability capability,
        DownstreamStage stage,
        Optional<CapabilityEvidence> unobservedClaim) {

    /**
     * Validates the record.
     *
     * @throws NullPointerException if a component is {@code null}
     * @throws IllegalArgumentException if the claim's evidence is observed, since an observed
     *     capability is not missing
     */
    public MissingCapability {
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(unobservedClaim, "unobservedClaim");
        if (unobservedClaim.isPresent() && unobservedClaim.get().isObserved()) {
            throw new IllegalArgumentException(
                    capability.id()
                            + " was observed by execution, so it is not missing; an unobserved"
                            + " claim must carry evidence that is not observed-by-execution");
        }
    }

    /**
     * Whether the build claims the capability without its having been observed.
     *
     * @return {@code true} when installing the build would probe the claim
     */
    public boolean isUnobservedClaim() {
        return unobservedClaim.isPresent();
    }
}
