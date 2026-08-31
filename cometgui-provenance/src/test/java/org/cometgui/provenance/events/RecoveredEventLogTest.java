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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link RecoveredEventLog}, the value {@link ProvenanceEventLogReader} hands back.
 *
 * <p>{@link RecoveredEventLog#highestSequence()} is the one method here with a decision in it: it
 * is the <em>largest</em> number recovered, not the last one, because a resumed run continues from
 * it and a log whose numbering was disturbed must still yield a number no existing record uses.
 */
class RecoveredEventLogTest {

    @Test
    @DisplayName("an empty recovery is intact, empty and numbered zero")
    void emptyRecovery() {
        RecoveredEventLog recovered = new RecoveredEventLog(List.of(), List.of());

        assertAll(
                () -> assertTrue(recovered.intact()),
                () -> assertEquals(0L, recovered.highestSequence()),
                () -> assertEquals(List.of(), recovered.events()),
                () -> assertEquals(List.of(), recovered.defects()));
    }

    @Test
    @DisplayName("highestSequence is the largest number recovered, not the last one")
    void highestSequenceIsTheLargest() {
        RecoveredEventLog outOfOrder =
                new RecoveredEventLog(List.of(event(1L), event(9L), event(4L)), List.of());

        assertEquals(9L, outOfOrder.highestSequence());
    }

    @Test
    @DisplayName("any defect at all means the log is not intact")
    void anyDefectMeansNotIntact() {
        RecoveredEventLog recovered =
                new RecoveredEventLog(
                        List.of(event(1L)),
                        List.of(
                                new EventLogDefect(
                                        EventLogDefectKind.SEQUENCE_GAP, 2L, 90L, "a gap")));

        assertFalse(recovered.intact());
    }

    @Test
    @DisplayName("both lists are copied, so a later change to the caller's list changes nothing")
    void listsAreCopied() {
        List<ProvenanceEvent> events = new ArrayList<>(List.of(event(1L)));
        List<EventLogDefect> defects = new ArrayList<>();
        RecoveredEventLog recovered = new RecoveredEventLog(events, defects);

        events.add(event(2L));
        defects.add(new EventLogDefect(EventLogDefectKind.MALFORMED_LINE, 1L, 0L, "x"));

        assertAll(
                () -> assertEquals(1, recovered.events().size()),
                () -> assertTrue(recovered.intact()),
                () ->
                        assertThrows(
                                UnsupportedOperationException.class,
                                () -> recovered.events().add(event(3L))));
    }

    @Test
    @DisplayName("toString counts the recovery without printing a run's whole history")
    void toStringIsASummary() {
        RecoveredEventLog recovered =
                new RecoveredEventLog(
                        List.of(event(1L), event(2L)),
                        List.of(
                                new EventLogDefect(
                                        EventLogDefectKind.TORN_FINAL_LINE, 3L, 180L, "torn")));

        assertEquals(
                "RecoveredEventLog[events=2, highestSequence=2, defects=1]", recovered.toString());
    }

    @Test
    @DisplayName("null lists are rejected by name")
    void nullListsAreRejected() {
        assertAll(
                () ->
                        assertEquals(
                                "events",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new RecoveredEventLog(
                                                                deliberateNull(), List.of()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "defects",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new RecoveredEventLog(
                                                                List.of(), deliberateNull()))
                                        .getMessage()));
    }

    private static ProvenanceEvent event(long sequence) {
        return new ProvenanceEvent(
                sequence,
                Instant.parse("2026-08-31T09:15:00.000Z"),
                ProvenanceEventType.WARNING_RAISED,
                Map.of("message", "x"));
    }

    /**
     * A {@code null} that SpotBugs cannot see; see {@code ProvenanceEventTypeTest} for why.
     *
     * @param <T> whatever the call site needs
     * @return {@code null}
     */
    private static <T> T deliberateNull() {
        return null;
    }
}
