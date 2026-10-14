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
 * The Provenance section's view-model (phase 13, work unit 5): toolkit-free, like the rest of
 * {@code org.cometgui.ui.viewmodel} -- no scene graph, no stage, no {@code Platform.runLater}.
 *
 * <ul>
 *   <li>{@link org.cometgui.ui.viewmodel.provenance.ProvenanceViewModel} -- every run of the
 *       session's project, and for the chosen one the seven parts the specification's
 *       <em>Provenance UI</em> names, with Copy MD5, Copy SHA-256, Copy command, Open file
 *       location, Export JSON, Export RST and Verify against disk.
 *   <li>{@link org.cometgui.ui.viewmodel.provenance.ProvenancePort} -- what the composition root
 *       implements over the read model ({@code org.cometgui.workflow.provenance}) and the one
 *       process service; never called on the interface thread (P13-8).
 *   <li>{@link org.cometgui.ui.viewmodel.provenance.OpenedRecord} -- one opened run, which the
 *       exports and the verification act on.
 * </ul>
 *
 * <p>Nothing here reads a file, hashes, parses, redacts, renders a document or starts a process:
 * all of that is the read model's, reached through the port. What is shown is the read model's
 * display text, laid out.
 */
package org.cometgui.ui.viewmodel.provenance;
