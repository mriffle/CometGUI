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

import org.cometgui.ui.viewmodel.params.RunEnginePort;
import org.cometgui.ui.viewmodel.percolator.PercolatorRerunPort;

/**
 * The session's workflow engine as the interface reaches it: the Run section's port and the
 * Percolator section's rerun port, implemented by one object because both work over the same
 * session -- its project, its one hasher, its engine and its last run (decisions P8-16, P9-11).
 */
public interface SessionEngine extends RunEnginePort, PercolatorRerunPort {}
