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
 * q-value filtering with independent PSM and peptide thresholds. The boundary at exactly 0.01 is
 * inclusive; a mutation that turns the comparison into a strict one must fail a test.
 *
 * <p>Phase 09 put here only the display filter values and their predicate ({@link
 * org.cometgui.results.filtering.PsmQValueFilter}, {@link
 * org.cometgui.results.filtering.PeptideQValueFilter}, {@link
 * org.cometgui.results.filtering.DisplayFilters}); phase 10 adds the store, the counts view and
 * export over them. These are never Percolator's {@code --testFDR}/{@code --trainFDR}.
 */
package org.cometgui.results.filtering;
