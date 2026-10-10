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

package org.cometgui.tools.pdv;

/** How a source file stored one array of peak values, which is how it is written back. */
public enum PeakEncoding {

    /**
     * IEEE 754 single precision, as an mzML {@code 32-bit float} array stores it. The value is held
     * as the {@code double} it widens to exactly.
     */
    FLOAT32,

    /**
     * IEEE 754 double precision: an mzML {@code 64-bit float} array, or an MGF's decimal text
     * parsed as a {@code double}.
     */
    FLOAT64
}
