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

/**
 * Observes a run.
 *
 * <p>Callbacks arrive on one engine-owned thread per run, in exactly the order the transitions
 * happened, and <strong>never</strong> on the JavaFX application thread: a listener that touches
 * the user interface hands over to it itself. A callback must not block for long, because the next
 * one waits for it. A callback that throws does not stop the run or later callbacks; the throw is
 * counted in {@link RunResult#listenerFailures()}.
 */
public interface StepStateListener {

    /** A listener that ignores everything. */
    StepStateListener NONE = transition -> {};

    /**
     * A step changed state.
     *
     * @param transition the change
     */
    void onTransition(StepTransition transition);

    /**
     * The run finished and its provenance is final. Delivered after every transition.
     *
     * @param result how it ended
     */
    default void onRunFinished(RunResult result) {}
}
