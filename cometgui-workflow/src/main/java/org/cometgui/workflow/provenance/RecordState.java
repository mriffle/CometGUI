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
 * Whether a loaded run has a sealed provenance record ({@code P13-5}).
 *
 * <p>A run never opens as an empty run. It has a record that was read whole and parsed, or it has
 * no record at all -- a crash before finalisation, or a run still going -- and is shown from {@code
 * run.json} and {@code events.log}, or its record exists and could not be read, and the reader's
 * message says which member of which file to look at.
 */
public enum RecordState {

    /** {@code provenance.json} exists and was read and parsed. */
    SEALED("sealed record"),

    /** {@code provenance.json} does not exist: shown from {@code run.json} and the event log. */
    NO_SEALED_RECORD("no sealed record"),

    /** {@code provenance.json} exists and could not be read or parsed. */
    RECORD_UNREADABLE("record unreadable");

    private final String word;

    RecordState(String word) {
        this.word = word;
    }

    /**
     * The words the Provenance section shows.
     *
     * @return the display words, lower case
     */
    public String word() {
        return word;
    }
}
