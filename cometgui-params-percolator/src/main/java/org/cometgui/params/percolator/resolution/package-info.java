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
 * Which Percolator a run uses: the <em>latest compatible</em> resolver ({@code R-PERC-02}), the
 * downstream-stage requirement table, the reasons a newer version was not selected ({@code
 * R-PERC-10}), stage availability with its remedies ({@code R-PERC-03}) and the version advisories
 * shown at selection ({@code R-PERC-11}).
 *
 * <p>Pure: no file, process or thread. It reads {@link org.cometgui.domain.tools.ToolOffer}s and
 * decides on observed {@link org.cometgui.domain.tools.ToolCapability capabilities} alone; a
 * Percolator version number is used to order candidates and to name them, never to decide what a
 * build can do.
 */
package org.cometgui.params.percolator.resolution;
