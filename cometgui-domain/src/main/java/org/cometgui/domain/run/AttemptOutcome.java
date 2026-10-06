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

package org.cometgui.domain.run;

import java.util.Objects;

/** How one attempt at a run ended, or that it has not yet. */
public enum AttemptOutcome {

    /** The attempt is in progress -- or was, when a process that died last wrote the file. */
    RUNNING("running"),

    /** Every planned step succeeded. */
    SUCCEEDED("succeeded"),

    /** A step failed. */
    FAILED("failed"),

    /** The user cancelled the attempt. */
    CANCELLED("cancelled");

    private final String wireName;

    AttemptOutcome(String wireName) {
        this.wireName = wireName;
    }

    /**
     * The name {@code run.json} records.
     *
     * @return the lower-case name
     */
    public String wireName() {
        return wireName;
    }

    /**
     * Whether the attempt has ended. An ended attempt has an end time and never changes again.
     *
     * @return {@code false} only for {@link #RUNNING}
     */
    public boolean isTerminal() {
        return this != RUNNING;
    }

    /**
     * The outcome a recorded name stands for.
     *
     * @param wireName the recorded name
     * @return the outcome
     * @throws NullPointerException if {@code wireName} is {@code null}
     * @throws IllegalArgumentException if no outcome has that name
     */
    public static AttemptOutcome fromWireName(String wireName) {
        Objects.requireNonNull(wireName, "wireName");
        for (AttemptOutcome outcome : values()) {
            if (outcome.wireName.equals(wireName)) {
                return outcome;
            }
        }
        throw new IllegalArgumentException("not an attempt outcome: \"" + wireName + "\"");
    }
}
