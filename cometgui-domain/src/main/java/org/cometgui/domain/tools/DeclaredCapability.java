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

package org.cometgui.domain.tools;

import java.util.Objects;

/**
 * One capability claimed of one tool build, together with how the claim was established and where
 * that evidence came from.
 *
 * <p>The three parts travel together deliberately. A bare {@link ToolCapability} in a manifest is a
 * claim with no accountability; pairing it with a {@link CapabilityEvidence} says how strong the
 * claim is, and the note says who established it and on what, so a reader a year later can check.
 *
 * <p><strong>A blank note is rejected: evidence with no provenance is not evidence.</strong> It
 * costs one sentence to write {@code executed on linux-x86-64 by phase 00 and phase 05}, and a
 * manifest row whose note is empty is indistinguishable from one nobody ever checked.
 *
 * @param capability what the build can do
 * @param evidence how that was established -- and therefore what the application is allowed to say
 *     about it; see {@link CapabilityEvidence}
 * @param note where the evidence came from, in a sentence a reader can act on: which platform, by
 *     which phase, from which artefact. Stripped of surrounding whitespace and never blank.
 */
public record DeclaredCapability(
        ToolCapability capability, CapabilityEvidence evidence, String note) {

    /**
     * Validates the claim.
     *
     * @throws NullPointerException if {@code capability} or {@code evidence} is {@code null}
     * @throws IllegalArgumentException if {@code note} is blank, with a message naming the field
     */
    public DeclaredCapability {
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(note, "note");
        if (note.isBlank()) {
            throw new IllegalArgumentException(
                    "note must not be blank: evidence with no provenance is not evidence, so say"
                            + " where it came from -- for example \"executed on linux-x86-64 by"
                            + " phase 00 and phase 05\"");
        }
        note = note.strip();
    }

    /**
     * Whether this claim rests on having run the program.
     *
     * <p>The question any wording that claims a capability must ask first; see {@link
     * CapabilityEvidence#isObserved()}.
     *
     * @return {@code true} only when the evidence is {@link
     *     CapabilityEvidence#OBSERVED_BY_EXECUTION}
     */
    public boolean isObserved() {
        return evidence.isObserved();
    }
}
