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
 * Writing comet.params files: deterministic, byte-stable canonical output, locale-independent
 * (including under a comma-decimal locale), with a generated header naming the CometGUI and Comet
 * versions, curated inline comments, unknown parameters written back, and the enzyme table last.
 *
 * <p>{@link org.cometgui.params.comet.writer.CanonicalParamsWriter} refuses a model whose enzyme
 * numbers are absent from the table it writes, and writes a file once and hashes what it wrote
 * (R-PARAM-11, R-PARAM-12). Phase 06 (Comet parameter model).
 */
package org.cometgui.params.comet.writer;
