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
 * The Results section's view-models (Phase 10, design decision P10-9): toolkit-free, like the rest
 * of {@code org.cometgui.ui.viewmodel}, and bound by the same rule -- no scene graph, no stage, no
 * application class, no {@code Platform.runLater}.
 *
 * <p>Everything scientific is the model's. Which rows a filter shows, the counts, the order of a
 * sort and the rows matching a text are the result store's ({@code
 * org.cometgui.results.filtering.store}), which decides where a row falls by the one q-value
 * predicate (design decision P10-1); a cutoff typed as text is read by the filters' own {@code
 * parse}; the learned feature weights' statistics are {@code WeightsSummary}'s. Nothing here reads
 * a number from text, compares a q-value or computes a statistic, and a row's score, q-value and
 * PEP are shown exactly as Percolator wrote them.
 *
 * <ul>
 *   <li>{@link org.cometgui.ui.viewmodel.results.DisplayFiltersViewModel} -- the interface's one
 *       display-filter state, shared with the Percolator section.
 *   <li>{@link org.cometgui.ui.viewmodel.results.ResultTableViewModel} -- one table as a page of at
 *       most {@link org.cometgui.ui.viewmodel.results.ResultTableViewModel#PAGE_SIZE} rows, never
 *       every row ({@code R-RES-03}); category, sort, text filter, column visibility, selection by
 *       row key and copy.
 *   <li>{@link org.cometgui.ui.viewmodel.results.WeightsViewModel} -- the learned feature weights
 *       table.
 *   <li>{@link org.cometgui.ui.viewmodel.results.ResultsViewModel} -- the run and table shown, the
 *       run's view state and the exports.
 *   <li>{@link org.cometgui.ui.viewmodel.results.ResultsPort} -- what the composition root
 *       implements over the project's runs, the stores, the view state and the exporter; never
 *       called on the interface thread.
 * </ul>
 */
package org.cometgui.ui.viewmodel.results;
