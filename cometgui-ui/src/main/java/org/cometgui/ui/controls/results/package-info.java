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
 * The Results section's JavaFX controls (Phase 10, design decision P10-9): a thin view over {@link
 * org.cometgui.ui.viewmodel.results.ResultsViewModel}, {@link
 * org.cometgui.ui.viewmodel.results.ResultTableViewModel}, {@link
 * org.cometgui.ui.viewmodel.results.WeightsViewModel} and the interface's one {@link
 * org.cometgui.ui.viewmodel.results.DisplayFiltersViewModel}.
 *
 * <p>The results table's items are the view-model's <strong>page</strong> -- at most {@link
 * org.cometgui.ui.viewmodel.results.ResultTableViewModel#PAGE_SIZE} rows -- and never a list of
 * every row ({@code R-RES-03}); its headings sort through the view-model, never by the table's own
 * sort of the page. Nothing here reads a number, compares a q-value, parses or hashes. Every
 * control carries a stable identifier from {@link org.cometgui.ui.controls.UiIds} and an accessible
 * name of its own.
 */
package org.cometgui.ui.controls.results;
