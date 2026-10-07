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

package org.cometgui.results.filtering;

/** Where a row falls under a display q-value filter. */
public enum Visibility {

    /** The q-value is known and at or below the cutoff: the row is shown. */
    PASSES,

    /** The q-value is known and above the cutoff. */
    FAILS,

    /**
     * The q-value is missing, unparsable or out of range ({@code R-RES-02}): neither passing nor
     * failing, counted and shown as its own category, never silently dropped or included.
     */
    UNKNOWN_Q_VALUE
}
