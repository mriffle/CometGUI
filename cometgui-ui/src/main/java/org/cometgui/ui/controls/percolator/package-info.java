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
 * The Percolator section's JavaFX controls (Phase 09, decision P9-12): a thin view over {@link
 * org.cometgui.ui.viewmodel.percolator.PercolatorViewModel} and {@link
 * org.cometgui.ui.viewmodel.percolator.PercolatorRerunViewModel}. Every control carries a stable
 * identifier from {@link org.cometgui.ui.controls.UiIds} and an accessible name of its own.
 */
package org.cometgui.ui.controls.percolator;
