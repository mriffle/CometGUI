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

package org.cometgui.workflow.limelight;

/**
 * What a run must have, and be, before it can be converted to Limelight XML -- the specification's
 * <em>Conversion prerequisites</em>, one constant each, plus the two conditions this product adds.
 * A refusal names one of these; the Limelight section shows each by name.
 */
public enum LimelightPrerequisite {

    /** The run is not being executed by the workflow engine: its results are final. */
    RUN_ENDED,

    /** The run's canonical {@code comet.params} exists and is the one the run recorded. */
    COMET_PARAMS,

    /**
     * The run's Comet pepXML exist in its {@code outputs/comet/}, are the ones its search recorded,
     * and are every pepXML the converter will read.
     */
    COMET_PEPXML,

    /** The run's Percolator wrote XML, recorded, unchanged and readable. */
    PERCOLATOR_XML,

    /**
     * The Limelight converter is installed, verified, and reports the version it was offered as.
     */
    CONVERTER,

    /** The run's FASTA is where the run recorded it, and is the file the run recorded. */
    FASTA,

    /** The Limelight XML can be written: a new file, in a writable directory. */
    OUTPUT,

    /**
     * The decoy choice agrees with the run's Percolator output: decoy import only when it holds
     * decoy PSMs, and always when it does (design decision P12-7).
     */
    DECOY_IMPORT,

    /** The converter's other options are ones it accepts: the independent-decoy prefix. */
    OPTIONS
}
