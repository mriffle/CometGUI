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
import org.cometgui.ui.viewmodel.params.RunEnginePort;
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
}
