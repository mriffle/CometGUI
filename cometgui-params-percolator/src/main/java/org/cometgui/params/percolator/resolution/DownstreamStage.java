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

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.cometgui.domain.tools.ToolCapability;

/**
 * The stages after Percolator that need something of it, and what they need: the requirement table
 * resolution reads ({@code R-PERC-02} (c)), held in this one place.
 *
 * <p>Only stages that can be switched on or off are here. Rescoring itself -- tab-separated PSM and
 * peptide output -- is not a resolution criterion: a build that is not installed yet has no
 * observed tab-separated capability to resolve on, so the command builder checks those needs when
 * the run starts and refuses with a named reason.
 */
public enum DownstreamStage {

    /**
     * Limelight conversion: the Limelight converter reads Percolator pout XML, so the build must
     * have been observed writing it ({@link ToolCapability#XML_OUTPUT}).
     */
    LIMELIGHT_CONVERSION(
            "limelight-conversion",
            "Limelight conversion",
            EnumSet.of(ToolCapability.XML_OUTPUT),
            "the Limelight converter reads the Percolator XML that XML_OUTPUT writes",
            List.of(StageRemedy.REGISTER_LOCAL_BINARY, StageRemedy.CONVERT_ON_SUPPORTED_PLATFORM));

    private final String id;
    private final String label;
    private final Set<ToolCapability> requiredCapabilities;
    private final String why;
    private final List<StageRemedy> remedies;

    DownstreamStage(
            String id,
            String label,
            Set<ToolCapability> requiredCapabilities,
            String why,
            List<StageRemedy> remedies) {
        this.id = id;
        this.label = label;
        this.requiredCapabilities = Collections.unmodifiableSet(requiredCapabilities);
        this.why = why;
        this.remedies = List.copyOf(remedies);
    }

    /**
     * The stable identifier, for provenance and settings.
     *
     * @return for example {@code limelight-conversion}
     */
    public String id() {
        return id;
    }

    /**
     * The name the interface and messages use.
     *
     * @return for example {@code Limelight conversion}
     */
    public String label() {
        return label;
    }

    /**
     * Every capability a Percolator build must have been observed to have for this stage.
     *
     * @return the capabilities, immutable, in declaration order
     */
    public Set<ToolCapability> requiredCapabilities() {
        return requiredCapabilities;
    }

    /**
     * Why the stage needs them, as a clause a message can quote.
     *
     * @return for example "the Limelight converter reads the Percolator XML that XML_OUTPUT writes"
     */
    public String why() {
        return why;
    }

    /**
     * What a scientist can do when no Percolator here satisfies this stage.
     *
     * @return the remedies, immutable, in the order they are offered
     */
    public List<StageRemedy> remedies() {
        return remedies;
    }

    /**
     * Whether a build with these observed capabilities satisfies this stage.
     *
     * @param observed the build's observed capabilities
     * @return {@code true} if every required capability is among them
     * @throws NullPointerException if {@code observed} is {@code null}
     */
    public boolean isSatisfiedBy(Set<ToolCapability> observed) {
        return Objects.requireNonNull(observed, "observed").containsAll(requiredCapabilities);
    }
}
