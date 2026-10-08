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

package org.cometgui.ui.viewmodel.percolator;

import org.cometgui.ui.viewmodel.params.ActiveRun;
import org.cometgui.ui.viewmodel.params.PercolatorRequest;
import org.cometgui.ui.viewmodel.params.RunNotStartedException;
import org.cometgui.ui.viewmodel.params.RunObserver;

/**
 * The compatible-version Percolator rerun (design decision P9-11), as the Percolator section sees
 * it: over the session's last run, which the composition root's engine port keeps, with the
 * Percolator half as it is now. A rerun is a new, derived run that reuses the last run's merged PIN
 * and runs no Comet.
 *
 * <p>Both methods hash files and the second starts processes, so <strong>neither is ever called on
 * the JavaFX application thread</strong>: {@link PercolatorRerunViewModel} calls both from its
 * background executor.
 */
public interface PercolatorRerunPort {

    /**
     * Whether Percolator can be rerun from the session's last run with this Percolator half, and
     * the preview of that rerun. Reads and re-hashes; creates and launches nothing.
     *
     * @param percolator the Percolator half as the section has it now
     * @return the answer; a refusal is an answer with a reason, never an exception
     */
    RerunCheck check(PercolatorRequest percolator);

    /**
     * Records the derived run and starts it. Before the engine starts, {@link RunObserver#planned}
     * is called with its plan; every step change and the end arrive on the observer from engine
     * threads.
     *
     * @param percolator the Percolator half to rerun with
     * @param observer told the plan, every transition and the outcome
     * @return the running attempt, to cancel
     * @throws RunNotStartedException if the rerun was refused or could not be created, saying why;
     *     nothing was launched
     */
    ActiveRun start(PercolatorRequest percolator, RunObserver observer)
            throws RunNotStartedException;
}
