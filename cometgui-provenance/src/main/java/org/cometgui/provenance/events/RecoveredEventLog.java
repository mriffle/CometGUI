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

package org.cometgui.provenance.events;

import java.util.List;
import java.util.Objects;

/**
 * Everything {@link ProvenanceEventLogReader} could recover from one event log, and everything that
 * was wrong with it.
 *
 * <p>This is the shape of the answer to phase 04's exit gate item 4 -- "a crash simulated mid-run
 * leaves a parsable event log with usable history". A reader that threw on the first damaged byte
 * would meet the letter of "parsable" and none of its purpose: the run that mattered is the one
 * that crashed, and its history is exactly what an exception would discard. So recovery always
 * succeeds and always says what it found. The events are what survived, in file order; the defects
 * are what did not, with their positions.
 *
 * <p><strong>Both halves are needed and neither may be inferred from the other.</strong> A caller
 * that only looked at {@link #events()} would silently accept a log with a hole in the middle. A
 * caller that only looked at {@link #defects()} would know something was wrong and have no history
 * to show. {@link #intact()} is the single question most callers actually want to ask, and it is
 * defined as "no defects at all" rather than "no torn tail", because a sequence gap is just as
 * fatal to a reproduction claim as a missing final record.
 *
 * @param events every complete, well-formed event, in the order the file lists them
 * @param defects everything wrong with the file, in the order it was found
 */
public record RecoveredEventLog(List<ProvenanceEvent> events, List<EventLogDefect> defects) {

    /**
     * Validates the result and takes immutable copies of both lists.
     *
     * @throws NullPointerException if either list is {@code null} or holds a {@code null} element
     */
    public RecoveredEventLog {
        events = List.copyOf(Objects.requireNonNull(events, "events"));
        defects = List.copyOf(Objects.requireNonNull(defects, "defects"));
    }

    /**
     * Whether the log was whole: every line a record, nothing missing, nothing cut short.
     *
     * @return {@code true} if no defect at all was found
     */
    public boolean intact() {
        return defects.isEmpty();
    }

    /**
     * The largest sequence number recovered, which is where an appender continues from.
     *
     * <p>The largest rather than the last: a log whose sequence numbers were disturbed still has to
     * yield a number that no existing record uses, or a run resumed after a crash would write a
     * second event 4 and make the damage permanent.
     *
     * @return the highest sequence number among the recovered events, or 0 if none was recovered
     */
    public long highestSequence() {
        long highest = 0;
        for (ProvenanceEvent event : events) {
            highest = Math.max(highest, event.sequence());
        }
        return highest;
    }

    /**
     * Every complete, well-formed event, in the order the file lists them.
     *
     * <p>The copy is what makes the immutability visible at the call site -- and to SpotBugs, which
     * reports a record accessor handing out a collection field as {@code EI_EXPOSE_REP}.
     *
     * @return the recovered events, immutable
     */
    public List<ProvenanceEvent> events() {
        return List.copyOf(events);
    }

    /**
     * Everything that was wrong with the file, in the order it was found.
     *
     * @return the defects, immutable
     */
    public List<EventLogDefect> defects() {
        return List.copyOf(defects);
    }

    /**
     * Describes the recovery without printing a single payload value or an unbounded list.
     *
     * <p>A log has as many events as a run has moments, so the generated {@code toString} would put
     * a run's whole history into one exception message. This one gives the three numbers that
     * identify a recovery; the events and the defects are available through their accessors.
     *
     * @return a description safe to put in a log line or an exception message
     */
    @Override
    public String toString() {
        return "RecoveredEventLog[events="
                + events.size()
                + ", highestSequence="
                + highestSequence()
                + ", defects="
                + defects.size()
                + "]";
    }
}
