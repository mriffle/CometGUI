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

package org.cometgui.workflow.provenance;

/**
 * What a run directory says about its run, as the Provenance section's run list shows it.
 *
 * <p>Read from the run's {@code run.json} -- the latest attempt's outcome -- and from whether the
 * application is executing the run right now. The two "not finished" words are kept apart on
 * purpose: a run whose {@code run.json} still says {@code running} while nothing in this session is
 * executing it was interrupted (the application or the machine stopped under it), and that is
 * exactly the run a scientist opens the Provenance section to understand.
 */
public enum RunCondition {

    /** Prepared and recorded, but no attempt was ever started. */
    NOT_STARTED("not started"),

    /** An attempt is running in this session. */
    RUNNING("running"),

    /**
     * The latest attempt is recorded as running, but this session is not executing it: the
     * application or the machine stopped while it ran.
     */
    INTERRUPTED("interrupted"),

    /** The latest attempt succeeded. */
    COMPLETED("completed"),

    /** The latest attempt failed. */
    FAILED("failed"),

    /** The latest attempt was cancelled. */
    CANCELLED("cancelled"),

    /** The run's {@code run.json} is missing or could not be read. */
    UNREADABLE("run.json unreadable");

    private final String word;

    RunCondition(String word) {
        this.word = word;
    }

    /**
     * The words the run list shows.
     *
     * @return the display word, lower case
     */
    public String word() {
        return word;
    }
}
