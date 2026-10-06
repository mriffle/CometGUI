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

package org.cometgui.workflow.engine;

import java.time.Instant;
import java.util.Objects;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.RunState;
import org.cometgui.workflow.state.StepState;

/**
 * One observed change of a step's state.
 *
 * @param step the step
 * @param from the state it left
 * @param to the state it entered
 * @param at when, from the engine's clock
 * @param runState the run state derived over the plan immediately after the change -- derived,
 *     never stored ({@code RunState#deriveFrom})
 */
public record StepTransition(
        EngineStep step, StepState from, StepState to, Instant at, RunState runState) {

    /**
     * Requires every component.
     *
     * @throws NullPointerException naming a component that is {@code null}
     */
    public StepTransition {
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(runState, "runState");
    }
}
