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

package org.cometgui.params.comet.schema;

/** How much of the parameter set a dump is known to declare ({@code R-PARAM-01}, {@code -02}). */
public enum DiscoveryMode {

    /**
     * From {@code comet -q}: the complete parameter file. A curated parameter it does not declare
     * is one the binary no longer recognises.
     */
    COMPLETE,

    /**
     * From {@code comet -p}, for a binary without {@code -q}: the default file, which omits
     * parameters the binary does support (22 of 118 for 2026.02.2). Absence from it proves nothing,
     * so drift detection never reports a curated parameter as removed on its evidence.
     */
    PARTIAL_DISCOVERY
}
