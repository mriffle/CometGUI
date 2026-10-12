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
 * The Limelight section's view-model (phase 12, work unit 6): toolkit-free, like the rest of {@code
 * org.cometgui.ui.viewmodel} -- no scene graph, no stage, no {@code Platform.runLater}.
 *
 * <ul>
 *   <li>{@link org.cometgui.ui.viewmodel.limelight.LimelightViewModel} -- for the run chosen in the
 *       Results section (its one selection, not a second): whether that run can be converted, shown
 *       before anything can be attempted; the action offered when it cannot (Phase 09's compatible
 *       rerun, or local-binary registration); the converter's settings; the conversion, its live
 *       log and its validated result.
 *   <li>{@link org.cometgui.ui.viewmodel.limelight.LimelightPort} -- what the composition root
 *       implements over the Tool Manager, the session's project, the session's engine and {@code
 *       org.cometgui.workflow.limelight}; never called on the interface thread.
 *   <li>{@link org.cometgui.ui.viewmodel.limelight.LimelightOptions}, {@link
 *       org.cometgui.ui.viewmodel.limelight.LimelightRunState}, {@link
 *       org.cometgui.ui.viewmodel.limelight.LimelightResult} and {@link
 *       org.cometgui.ui.viewmodel.limelight.LimelightRerun} -- what crosses the port.
 * </ul>
 *
 * <p>Nothing here hashes, downloads, parses, validates a setting or starts a process: whether a run
 * can convert, every refusal, the q-value cutoff's rule, the checksums and the conversion are the
 * workflow's, reached through the port.
 */
package org.cometgui.ui.viewmodel.limelight;
