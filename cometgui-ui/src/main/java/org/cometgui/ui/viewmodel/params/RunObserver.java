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

package org.cometgui.ui.viewmodel.params;

import org.cometgui.workflow.engine.StepStateListener;
import org.cometgui.workflow.state.Plan;

/**
 * What the Run section is told about a run it started: first the plan, then -- from the engine's
 * own threads -- every step transition and the outcome ({@link StepStateListener}).
 */
public interface RunObserver extends StepStateListener {

    /**
     * The run is about to start: the steps it executes, and where it lives. Called by {@link
     * RunEnginePort#start} before the engine starts, on the thread that called it.
     *
     * @param plan the steps the attempt executes
     * @param description the run in words: its identifier, its directory and whether it is a retry
     */
    void planned(Plan plan, String description);
}
