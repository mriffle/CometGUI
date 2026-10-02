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
 * Reading comet.params files: scalar types, enzyme table, variable-modification tuples, comments,
 * unknown parameters, duplicates and malformed lines. Nothing imported may be silently lost.
 *
 * <p>{@link org.cometgui.params.comet.parser.ParamsLineReader} is the one place that knows how a
 * comet.params line is shaped: it classifies every line (version marker, comment, blank,
 * declaration, enzyme-table header and rows, malformed with its line number) and interprets
 * nothing. Schema discovery reads it, and so does {@link
 * org.cometgui.params.comet.parser.CometParamsParser}, which turns a whole file into the typed
 * model all or nothing: errors yield no model, warnings travel on it. Phase 06 (Comet parameter
 * model).
 */
package org.cometgui.params.comet.parser;
