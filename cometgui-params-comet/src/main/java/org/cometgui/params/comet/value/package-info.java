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
 * Typed values for the parameters of comet.params that are not scalars, and the codecs that read
 * them from, and write them to, the value text of one declaration (R-PARAM-09 model half,
 * R-PARAM-11, R-PARAM-04's pair modelled here and validated elsewhere).
 *
 * <p>The variable-modification tuple, whose field layout is read from the version schema rather
 * than written into code; the [COMET_ENZYME_INFO] table with custom enzymes; the signed precursor
 * tolerance pair; the two-value ranges; the mass-offset list; and the ion-series family. Every
 * codec takes the text between "=" and "#" that org.cometgui.params.comet.parser.ParamsLine
 * .Declaration holds, rejects only text it cannot read (legality across fields is the validation
 * package's), and reads and writes numbers the same way whatever the default locale is: through
 * BigDecimal and Integer text, never a locale-sensitive formatter (R-PARAM-11). Phase 06 (Comet
 * parameter model).
 */
package org.cometgui.params.comet.value;
