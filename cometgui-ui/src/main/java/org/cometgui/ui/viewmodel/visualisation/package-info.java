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
 * The Visualisation section's view-models (phase 11): toolkit-free, like the rest of {@code
 * org.cometgui.ui.viewmodel} -- no scene graph, no stage, no {@code Platform.runLater}.
 *
 * <ul>
 *   <li>{@link org.cometgui.ui.viewmodel.visualisation.VisualisationViewModel} -- PDV's state, the
 *       run and PSM selected in the Results section (its one selection, not a second), and the
 *       action that draws that PSM's annotated spectrum, installing PDV first when it is not
 *       installed.
 *   <li>{@link org.cometgui.ui.viewmodel.visualisation.VisualisationPort} -- what the composition
 *       root implements over the Tool Manager, the session's project and {@code
 *       org.cometgui.workflow.pdv}; never called on the interface thread.
 * </ul>
 *
 * <p>Nothing here hashes, downloads, parses a spectrum or starts a process: PDV's state, the
 * install and the drawing are the port's, and the port's are the workflow's.
 */
package org.cometgui.ui.viewmodel.visualisation;
