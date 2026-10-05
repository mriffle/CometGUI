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
 * The Comet parameter editor's views (Phase 07): the editor in the Comet Parameters section -- the
 * release selector, the level switch, the validation summary, and the Essentials, Advanced and
 * Expert levels -- composed from the controls of {@code org.cometgui.ui.controls.params}.
 *
 * <p>No scientific logic, parsing or validation lives here (decision P7-1): a view arranges
 * controls, follows the session's release, and moves the focus where a summary entry points. Every
 * control carries a stable identifier from {@link org.cometgui.ui.controls.UiIds}.
 */
package org.cometgui.ui.view.params;
