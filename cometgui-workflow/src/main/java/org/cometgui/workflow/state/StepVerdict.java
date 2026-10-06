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

package org.cometgui.workflow.state;

import java.util.List;
import java.util.Objects;

/**
 * One planned step's line in a {@link RerunPreview}: what happens to it, and why.
 *
 * @param step the step
 * @param decision whether it executes or is reused
 * @param reasons why it executes, in the order the preview found them; empty exactly when it does
 *     not execute
 */
public record StepVerdict(EngineStep step, RerunDecision decision, List<RerunReason> reasons) {

    /**
     * Validates and copies.
     *
     * @throws NullPointerException naming a component that is {@code null}
     * @throws IllegalArgumentException if an executing step has no reason or a step that does not
     *     execute has one
     */
    public StepVerdict {
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(decision, "decision");
        reasons = List.copyOf(reasons);
        if (executes(decision) == reasons.isEmpty()) {
            throw new IllegalArgumentException(
                    step.id() + " is " + decision + " with reasons " + reasons);
        }
    }

    /**
     * Whether the step executes in the rerun -- re-executed or prepared.
     *
     * @return {@code true} for {@link RerunDecision#RE_EXECUTE} and {@link RerunDecision#PREPARE}
     */
    public boolean executes() {
        return executes(decision);
    }

    private static boolean executes(RerunDecision decision) {
        return decision == RerunDecision.RE_EXECUTE || decision == RerunDecision.PREPARE;
    }
}
