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
 * Export of filtered results, carrying the filter values actually applied so an exported table can
 * be explained later ({@code R-RES-04}, {@code R-RES-01}, {@code R-PERC-07}, design decision
 * P10-7).
 *
 * <p>{@link org.cometgui.results.export.ResultExporter} writes a filtered Percolator table -- its
 * header and the rows of one category under one q-value filter, byte for byte and in file order --
 * or the learned feature weights, as a new file under the run's {@code exports/} directory, never
 * overwriting one; beside it a schema-versioned JSON sidecar records the run ID, the source and its
 * checksums, the cutoff, the category, the counts before and the rows after, and the CometGUI
 * version; and one {@code export.written} event goes to the run's provenance event log. Files are
 * written by the one atomic writer and the sidecar by the one JSON writer, both in {@code
 * cometgui-provenance}. {@code docs/reference/project_format.rst} describes the formats.
 */
package org.cometgui.results.export;
