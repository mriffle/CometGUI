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

package org.cometgui.workflow.engine;

import java.util.Map;
import org.cometgui.provenance.events.ProvenanceEventType;

/** Where a step's context writes the events it produces: the run's one event log. */
@FunctionalInterface
interface EventRecorder {

    /**
     * Appends one event. Never throws: a failure to append is remembered by the run and reported in
     * {@link RunResult#finalisationErrors()}, because losing a log line must not lose a run.
     *
     * @param type the event type
     * @param payload its payload
     */
    void record(ProvenanceEventType type, Map<String, String> payload);
}
