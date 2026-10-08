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
 * The results store: one Percolator PSM or peptide table, opened once and queried a page at a time,
 * so that no view ever holds a list of every row ({@code R-RES-03}).
 *
 * <p>{@link org.cometgui.results.filtering.store.ResultStore} is the contract; {@link
 * org.cometgui.results.filtering.store.ResultStores} opens one. A {@link
 * org.cometgui.results.filtering.store.ResultQuery} -- q-value filter, {@link
 * org.cometgui.results.filtering.store.Category}, text, {@link
 * org.cometgui.results.filtering.store.ResultSort}, offset and limit -- returns a {@link
 * org.cometgui.results.filtering.store.ResultPage} of at most {@link
 * org.cometgui.results.filtering.store.ResultQuery#MAX_PAGE_SIZE} rows, the number of rows the
 * whole query matches, and the filter's counts over the whole table. A row is held by its {@link
 * org.cometgui.results.filtering.store.RowKey}, its line in the raw file.
 *
 * <p>Design decisions P10-1, P10-4 and P10-5 ({@code handoffs/PHASE-10-worklog.rst}): every row is
 * read through the one {@code ResultTableReader}, and every row is classified by the one predicate,
 * {@code QValueFilter.classify}; nothing here compares a q-value with a cutoff. The raw file is
 * only ever opened for reading.
 */
package org.cometgui.results.filtering.store;
