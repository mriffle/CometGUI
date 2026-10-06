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

package org.cometgui.workflow.steps;

import java.util.Objects;

/**
 * A search was refused before it became a run: the pre-run check blocked it. Nothing was created --
 * no run directory, no parameter file -- and nothing was launched. {@link #report()} says why.
 */
public final class RunBlockedException extends Exception {

    private static final long serialVersionUID = 1L;

    /** The report is a value the caller shows; it is not part of a serialised form. */
    private final transient PreRunReport report;

    /**
     * Creates the exception.
     *
     * @param report the blocking report
     * @throws IllegalArgumentException if the report blocks nothing
     */
    public RunBlockedException(PreRunReport report) {
        super(Objects.requireNonNull(report, "report").message());
        if (!report.blocked()) {
            throw new IllegalArgumentException("a report that blocks nothing does not block a run");
        }
        this.report = report;
    }

    /**
     * The report that blocked the run.
     *
     * @return the report
     */
    public PreRunReport report() {
        return report;
    }
}
