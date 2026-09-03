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

/**
 * The runtime behind {@code org.cometgui.domain.tools.ToolManager}: the composition that turns the
 * manifest, the host, the cache and the probes into the rows a scientist is shown, and that starts
 * and cancels an install on their behalf.
 *
 * <p>Every other package in this module answers one question -- what does upstream publish, how are
 * bytes fetched, are they the right bytes, how is an archive opened safely, what is installed, does
 * this build run here. This one asks them in order and turns the answers into {@code
 * org.cometgui.domain.tools.ToolOffer}s, which is the only vocabulary the user interface is allowed
 * to see.
 *
 * <p>Filled by phase 05 unit 8 (tool registry and installer).
 */
package org.cometgui.install.manager;
