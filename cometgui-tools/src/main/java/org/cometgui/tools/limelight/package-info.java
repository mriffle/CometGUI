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
 * Limelight converter adapter: Percolator XML in, Limelight XML out, plus the cutoff and decoy
 * handling the converter needs.
 *
 * <p><strong>Phase 05 unit 7 landed the identity probe</strong>, which starts the JAR and reads the
 * version it prints. The conversion itself, with its cutoff and decoy handling, is still phase
 * 12's.
 */
package org.cometgui.tools.limelight;
