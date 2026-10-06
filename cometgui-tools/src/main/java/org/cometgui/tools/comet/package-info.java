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
 * Comet adapter: per-spectrum-file invocation with distinct -N base names, output containment,
 * decoy validation and PIN merging.
 *
 * <p><strong>Phase 05 unit 7 landed the capability probe</strong>: {@code CometCapabilityProbe},
 * which makes the binary write its own default and complete parameter files and reads what it
 * declared, plus {@code R-TOOL-02}'s Thermo companion rule as a lookup over manifest data. The
 * search invocation itself is still phase 08's.
 *
 * <p><strong>Phase 08 unit 3 landed the pre-run readers</strong>: {@code FastaDecoyScanner}, which
 * streams a FASTA into its decoy census ({@code R-DEC-02}), and {@code CometIndexHeaderReader},
 * which reads an existing {@code .idx} file's self-description ({@code R-CMT-07}). Both return pure
 * values of {@code org.cometgui.domain.params}, which the one validator judges.
 */
package org.cometgui.tools.comet;
