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

package org.cometgui.workflow.pdv;

/**
 * What a PDV launch is for, as the {@code pdv.launched} event names it.
 *
 * <p>The wire name is a field, never derived from the constant, for the reason {@code
 * org.cometgui.provenance.events.ProvenanceEventType} gives: it is a token in every event log
 * written, and a rename of a Java constant must not change it.
 */
public enum PdvLaunchKind {

    /** PDV's command line drawing annotated figures to files: {@code PdvFigures}. */
    CLI_FIGURE("cli-figure"),

    /** PDV's {@code denovo-gui} window held open on a loopback control port. */
    DENOVO_GUI("denovo-gui");

    private final String wireName;

    PdvLaunchKind(String wireName) {
        this.wireName = wireName;
    }

    /**
     * The token the event records.
     *
     * @return the wire name
     */
    public String wireName() {
        return wireName;
    }
}
