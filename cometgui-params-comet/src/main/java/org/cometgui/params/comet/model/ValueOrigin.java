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

package org.cometgui.params.comet.model;

/**
 * Where a parameter's current value came from -- the five origins the specification names after its
 * <em>Parameter definition model</em>, which the editor shows beside each value.
 */
public enum ValueOrigin {

    /** The schema default for the version: what {@code comet -q} writes. */
    COMET_DEFAULT,

    /** Set by an application preset. */
    PRESET,

    /** Changed by the user. */
    USER,

    /** Read from an imported parameter file. */
    IMPORTED,

    /**
     * Forced by the workflow ({@code R-CMT-01}): {@code output_pepxmlfile} and {@code
     * output_percolatorfile}, which the downstream stages need. Shown as a change the application
     * made on the user's behalf.
     */
    WORKFLOW_ENFORCED
}
