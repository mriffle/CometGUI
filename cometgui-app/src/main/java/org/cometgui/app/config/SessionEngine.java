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

package org.cometgui.app.config;

import java.util.Set;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.params.percolator.resolution.CompatibilityAction;
import org.cometgui.ui.viewmodel.limelight.LimelightRerun;
import org.cometgui.ui.viewmodel.params.RunEnginePort;
import org.cometgui.ui.viewmodel.params.RunNotStartedException;
import org.cometgui.ui.viewmodel.params.RunObserver;
import org.cometgui.ui.viewmodel.percolator.PercolatorRerunPort;

/**
 * The session's workflow engine as the interface reaches it: the Run section's port and the
 * Percolator section's rerun port, implemented by one object because both work over the same
 * session -- its project, its one hasher, its engine and its last run (decisions P8-16, P9-11).
 */
public interface SessionEngine extends RunEnginePort, PercolatorRerunPort {

    /**
     * The runs this session's engine is executing now: from the moment a run, a retry or a rerun is
     * started until the engine reports it finished -- after its provenance event log is closed.
     * While a run is executing the engine holds that log, so nothing can be exported from it.
     *
     * @return the runs' identifiers; empty when none is executing
     */
    Set<RunId> executingRuns();

    /**
     * Phase 09's compatible rerun of a given finished run's Percolator stage, with the build a
     * downstream stage's {@link CompatibilityAction} names (phase 12, {@code R-LL-01}): the same
     * rerun as the Percolator section's -- {@code PercolatorRerun.compatibleChoice}, then {@code
     * prepare} and {@code start} -- from a run the scientist chose rather than the session's last
     * one. A new, derived run reusing the source's merged PIN, running no Comet; held as executing
     * until the engine reports it finished.
     *
     * @param source the run whose Percolator stage is rerun
     * @param action the action offered for it, naming an installed build
     * @param settings the Percolator settings the rerun runs with
     * @param observer told the plan before the engine starts, then every transition and the end
     * @return the derived run and the handle that cancels it
     * @throws RunNotStartedException naming why nothing was started
     */
    LimelightRerun rerunCompatible(
            RunLayout source,
            CompatibilityAction action,
            PercolatorSettings settings,
            RunObserver observer)
            throws RunNotStartedException;
}
