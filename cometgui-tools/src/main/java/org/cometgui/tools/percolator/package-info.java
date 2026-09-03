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
 * Percolator adapter: capability-driven command building, PSM, peptide and weights output, XML
 * where the probed binary supports it, and version advisories. No version number may be hard-coded
 * as implying XML support.
 *
 * <p><strong>Phase 05 unit 7 landed the functional capability probe</strong> {@code R-PERC-02}
 * requires -- {@code SyntheticPin}, {@code PoutDocument} and {@code PercolatorCapabilityProbe} --
 * together with {@code R-TOOL-08}'s local binary registration. Command building for a real
 * rescoring run, and the version advisories, are still phase 09's.
 */
package org.cometgui.tools.percolator;
