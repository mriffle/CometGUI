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
 * PDV from a run: installed on first use through the one Tool Manager, and launched only through
 * the one path that records every launch in the run's event log first (phase 11, design decisions
 * P11-1 and P11-4).
 *
 * <ul>
 *   <li>{@link org.cometgui.workflow.pdv.PdvProvisioning} reads which PDV the Tool Manager has --
 *       installed, installable, being installed or unavailable ({@link
 *       org.cometgui.workflow.pdv.PdvStatus}) -- and starts an install through the {@link
 *       org.cometgui.domain.tools.ToolManager} port, which is the only downloader there is. Nothing
 *       here downloads, unpacks or chooses an artefact, and a search never waits for any of it.
 *   <li>{@link org.cometgui.workflow.pdv.PdvLaunches} is <strong>the one way a PDV process is
 *       started</strong>. It hands out a {@link org.cometgui.domain.ports.ProcessRunner} bound to
 *       one run and one installed PDV; every {@code start} through it hashes the JAR with the one
 *       hasher, reads the version the JAR's own manifest reports, and appends a {@code
 *       pdv.launched} event to the run's {@code events.log} -- and only then asks the process
 *       service to start the process. A launch that then fails has already left its record; an
 *       event that cannot be written means nothing is started.
 *   <li>{@link org.cometgui.workflow.pdv.PsmFigures} draws the selected PSM's annotated spectrum
 *       with PDV's command line ({@code org.cometgui.tools.pdv.PdvFigures}) through such a runner.
 * </ul>
 *
 * <p>A later launch kind -- the {@code denovo-gui} window -- is a second {@link
 * org.cometgui.workflow.pdv.PdvLaunchKind} through the same {@link
 * org.cometgui.workflow.pdv.PdvLaunches#forRun}; there is no other constructor of a PDV launch.
 *
 * <p>Written by phase 11, work unit 3.
 */
package org.cometgui.workflow.pdv;
