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

/**
 * The lowest editor level at which a parameter is shown (specification, <em>Parameter editor
 * levels</em>). A parameter shown in {@link #ESSENTIALS} also appears in Advanced, under its
 * category, and every parameter appears in Expert's raw text.
 */
public enum VisibilityLevel {

    /** The common workflow-defining controls. */
    ESSENTIALS,

    /** Grouped by scientific concept; every supported user-relevant parameter. */
    ADVANCED,

    /** Raw text, and parameters few searches need. */
    EXPERT
}
