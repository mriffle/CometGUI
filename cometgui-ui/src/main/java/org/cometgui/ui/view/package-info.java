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
 * The application shell and its section panes: the specification's information architecture, drawn.
 *
 * <p>{@link org.cometgui.ui.view.ShellView} is the frame -- a header with the host-baseline banner
 * slot, a left navigation over every {@link org.cometgui.ui.viewmodel.SectionId}, and a content
 * area holding exactly the selected section's pane. {@link org.cometgui.ui.view.SectionPane} is one
 * of those panes: a heading, the section's own description, and a note naming the phase that fills
 * it, because an empty section and a broken one are otherwise indistinguishable. Two panes are
 * given content here -- Run hosts the stage stepper, Console hosts the console.
 *
 * <p>Every view-model a view shows is injected through its constructor. Nothing in this package
 * constructs one, and nothing in it holds state a view-model should hold; the layer's job is to
 * translate a user action into a call on a view-model and to observe the result.
 *
 * <p>Every control carries a stable identifier from {@link org.cometgui.ui.controls.UiIds} and an
 * explicit accessible name from {@link org.cometgui.ui.controls.AccessibleControls}, which is what
 * {@code R-TEST-04} and this phase's fourth exit-gate item require.
 *
 * <p>Filled by phase 02 (application shell and navigation) and by every UI-bearing phase after it.
 */
package org.cometgui.ui.view;
