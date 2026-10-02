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

package org.cometgui.params.comet.validation;

/** How serious a {@link Finding} is. */
public enum Severity {

    /**
     * The configuration is wrong: Comet would refuse it, ignore part of it silently, or run a
     * search that cannot mean what the user asked for. A report with an error blocks a run.
     */
    ERROR,

    /**
     * The configuration is legal but unusual, or depends on something the user should confirm --
     * for example a deliberately asymmetric precursor window ({@code R-PARAM-04}).
     */
    WARNING
}
