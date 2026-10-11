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
 * version it prints.
 *
 * <p><strong>Phase 12 unit 2 landed the converter adapter</strong>: {@link
 * org.cometgui.tools.limelight.PercolatorXmlPsms} reads what the converter will read from the run's
 * Percolator XML (PSM ids, decoy PSMs); {@link org.cometgui.tools.limelight.PepXmlSet} derives
 * every pepXML name the converter will open and proves each is the run's own; {@link
 * org.cometgui.tools.limelight.LimelightConversionRequest} is a typed request that cannot be made
 * with a decoy choice its Percolator XML contradicts; {@link
 * org.cometgui.tools.limelight.LimelightConverterCommand} builds the argument array from names the
 * real JAR's help prints; {@link org.cometgui.tools.limelight.LimelightConverter} runs it and keeps
 * the output only when {@link org.cometgui.tools.limelight.LimelightXmlJudge} finds it a valid
 * Limelight XML file against the schema inside the converter JAR ({@link
 * org.cometgui.tools.limelight.LimelightSchema}).
 */
package org.cometgui.tools.limelight;
