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
 * The parameter editor's custom controls (Phase 07): one typed control per parameter, the
 * variable-modification slot editor, the spectrum inputs, the validation summary and the Run
 * control.
 *
 * <p>Every control here binds to a view-model of {@code org.cometgui.ui.viewmodel.params} and holds
 * no scientific logic, parsing or validation of its own (decision P7-1): a text field hands its
 * text to the field's view-model, a check box hands its state to the model's flag, and every word a
 * control shows about a value -- its origin, its findings, its lock -- is the view-model's. Every
 * control carries a stable identifier from {@link org.cometgui.ui.controls.UiIds} and an accessible
 * name from {@link org.cometgui.ui.controls.AccessibleControls}; validation state is always shown
 * in text, never by colour alone.
 */
package org.cometgui.ui.controls.params;
