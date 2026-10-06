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

import org.cometgui.workflow.state.StepState;

/**
 * The token each step state is written as in the event log, and the order the engine allows a step
 * to move through them.
 *
 * <p>A wire name is a literal, never {@code name().toLowerCase()}: a constant is a name in this
 * code base, a wire name is a token in every event log ever written, and deriving one from the
 * other would let an ordinary rename change the format -- and under a Turkish default locale would
 * write {@code runnıng}.
 *
 * <h2>The transitions</h2>
 *
 * <pre>
 *     NOT_STARTED      -&gt; VALIDATING | READY | SKIPPED | CANCELLED
 *     VALIDATING       -&gt; READY | FAILED | CANCEL_REQUESTED
 *     READY            -&gt; RUNNING | CANCEL_REQUESTED
 *     RUNNING          -&gt; SUCCEEDED | FAILED | CANCEL_REQUESTED
 *     CANCEL_REQUESTED -&gt; CANCELLED | SUCCEEDED
 * </pre>
 *
 * <p>{@code VALIDATING} appears only for a step that validates. {@code SKIPPED} is a step the rerun
 * preview reuses or does not need. {@code CANCEL_REQUESTED -> SUCCEEDED} is a step that finished
 * its work before the cancellation reached it. {@code NOT_STARTED -> CANCELLED} happens only in
 * that same race, to the steps that would have started next, so that a cancelled run always derives
 * {@code CANCELLED}; every other step not started when a run is cancelled stays {@code
 * NOT_STARTED}. The terminal states have no way out.
 */
final class StateWireNames {

    private StateWireNames() {}

    static String of(StepState state) {
        return switch (state) {
            case NOT_STARTED -> "not-started";
            case VALIDATING -> "validating";
            case READY -> "ready";
            case RUNNING -> "running";
            case SUCCEEDED -> "succeeded";
            case FAILED -> "failed";
            case CANCEL_REQUESTED -> "cancel-requested";
            case CANCELLED -> "cancelled";
            case SKIPPED -> "skipped";
        };
    }

    static boolean allowed(StepState from, StepState to) {
        return switch (from) {
            case NOT_STARTED ->
                    to == StepState.VALIDATING
                            || to == StepState.READY
                            || to == StepState.SKIPPED
                            || to == StepState.CANCELLED;
            case VALIDATING ->
                    to == StepState.READY
                            || to == StepState.FAILED
                            || to == StepState.CANCEL_REQUESTED;
            case READY -> to == StepState.RUNNING || to == StepState.CANCEL_REQUESTED;
            case RUNNING ->
                    to == StepState.SUCCEEDED
                            || to == StepState.FAILED
                            || to == StepState.CANCEL_REQUESTED;
            case CANCEL_REQUESTED -> to == StepState.CANCELLED || to == StepState.SUCCEEDED;
            case SUCCEEDED, FAILED, CANCELLED, SKIPPED -> false;
        };
    }
}
