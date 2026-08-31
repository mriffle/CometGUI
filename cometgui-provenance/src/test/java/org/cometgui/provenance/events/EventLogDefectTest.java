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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link EventLogDefect} and {@link EventLogDefectKind}.
 *
 * <p>A defect is what a caller sees instead of an exception, so its own invariants matter: a line
 * number that counts from zero or a detail that says nothing would make a recovery result look
 * complete while telling a reader nothing about where to look.
 */
class EventLogDefectTest {

    @Test
    @DisplayName("the four components come back as given")
    void componentsAreKept() {
        EventLogDefect defect =
                new EventLogDefect(
                        EventLogDefectKind.TORN_FINAL_LINE, 4L, 280L, "no terminating newline");

        assertAll(
                () -> assertEquals(EventLogDefectKind.TORN_FINAL_LINE, defect.kind()),
                () -> assertEquals(4L, defect.lineNumber()),
                () -> assertEquals(280L, defect.byteOffset()),
                () -> assertEquals("no terminating newline", defect.detail()));
    }

    @Test
    @DisplayName("the three kinds of damage are these three, named here")
    void theKindsArePinned() {
        assertAll(
                () -> assertEquals(3, EventLogDefectKind.values().length),
                () -> assertEquals("TORN_FINAL_LINE", EventLogDefectKind.TORN_FINAL_LINE.name()),
                () -> assertEquals("MALFORMED_LINE", EventLogDefectKind.MALFORMED_LINE.name()),
                () -> assertEquals("SEQUENCE_GAP", EventLogDefectKind.SEQUENCE_GAP.name()));
    }

    @Test
    @DisplayName("a defect with no position or no detail is rejected")
    void invariantsAreEnforced() {
        assertAll(
                () ->
                        assertEquals(
                                "a defect's line number counts from 1, but was: 0",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new EventLogDefect(
                                                                EventLogDefectKind.MALFORMED_LINE,
                                                                0L,
                                                                0L,
                                                                "x"))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "a defect's byte offset counts from 0, but was: -1",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new EventLogDefect(
                                                                EventLogDefectKind.MALFORMED_LINE,
                                                                1L,
                                                                -1L,
                                                                "x"))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "a defect's detail must say what was wrong",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new EventLogDefect(
                                                                EventLogDefectKind.MALFORMED_LINE,
                                                                1L,
                                                                0L,
                                                                "  "))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "kind",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new EventLogDefect(
                                                                deliberateNull(), 1L, 0L, "x"))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "detail",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new EventLogDefect(
                                                                EventLogDefectKind.SEQUENCE_GAP,
                                                                1L,
                                                                0L,
                                                                deliberateNull()))
                                        .getMessage()));
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
