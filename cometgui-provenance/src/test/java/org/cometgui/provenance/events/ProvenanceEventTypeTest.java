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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ProvenanceEventType}.
 *
 * <p><strong>Every wire name below is typed out by hand.</strong> Not read from the enum, not
 * derived from {@code name()}, not built by lower-casing anything. That is the entire point of the
 * file: the wire name is a token in every event log this application will ever write, and a test
 * that asked the enum what its wire name was would agree with any answer it gave, including one an
 * ordinary rename had silently changed.
 */
class ProvenanceEventTypeTest {

    /** The shape every wire name has: two dotted lower-case ASCII segments. */
    private static final Pattern WIRE_SHAPE = Pattern.compile("[a-z]+\\.[a-z]+");

    @Test
    @DisplayName("every wire name is exactly the string typed here")
    void wireNamesArePinned() {
        assertAll(
                () -> assertEquals("run.started", ProvenanceEventType.RUN_STARTED.wireName()),
                () -> assertEquals("stage.started", ProvenanceEventType.STAGE_STARTED.wireName()),
                () -> assertEquals("stage.finished", ProvenanceEventType.STAGE_FINISHED.wireName()),
                () -> assertEquals("tool.invoked", ProvenanceEventType.TOOL_INVOKED.wireName()),
                () -> assertEquals("file.hashed", ProvenanceEventType.FILE_HASHED.wireName()),
                () -> assertEquals("warning.raised", ProvenanceEventType.WARNING_RAISED.wireName()),
                () -> assertEquals("run.finished", ProvenanceEventType.RUN_FINISHED.wireName()));
    }

    @Test
    @DisplayName("the seven types the work unit names are the seven types that exist")
    void theSevenTypesArePinned() {
        List<String> names = new ArrayList<>();
        for (ProvenanceEventType type : ProvenanceEventType.values()) {
            names.add(type.name());
        }

        assertEquals(
                List.of(
                        "RUN_STARTED",
                        "STAGE_STARTED",
                        "STAGE_FINISHED",
                        "TOOL_INVOKED",
                        "FILE_HASHED",
                        "WARNING_RAISED",
                        "RUN_FINISHED"),
                names);
    }

    @Test
    @DisplayName("no two types share a wire name")
    void wireNamesAreDistinct() {
        Set<String> distinct = new HashSet<>();
        for (ProvenanceEventType type : ProvenanceEventType.values()) {
            assertTrue(distinct.add(type.wireName()), "two types share the wire name");
        }

        assertEquals(7, distinct.size());
    }

    @Test
    @DisplayName("a wire name is not the Java constant lower-cased, and could not be")
    void wireNamesAreIndependentOfTheConstantName() {
        for (ProvenanceEventType type : ProvenanceEventType.values()) {
            assertNotEquals(
                    type.name().toLowerCase(Locale.ROOT),
                    type.wireName(),
                    "the wire name of " + type.name() + " is derivable from the constant");
            assertTrue(
                    WIRE_SHAPE.matcher(type.wireName()).matches(),
                    "the wire name of " + type.name() + " is not two dotted lower-case segments");
        }
    }

    @Test
    @DisplayName("every hand-typed wire name resolves back to its constant")
    void fromWireNameResolvesEveryPinnedName() {
        assertAll(
                () ->
                        assertSame(
                                ProvenanceEventType.RUN_STARTED,
                                ProvenanceEventType.fromWireName("run.started")),
                () ->
                        assertSame(
                                ProvenanceEventType.STAGE_STARTED,
                                ProvenanceEventType.fromWireName("stage.started")),
                () ->
                        assertSame(
                                ProvenanceEventType.STAGE_FINISHED,
                                ProvenanceEventType.fromWireName("stage.finished")),
                () ->
                        assertSame(
                                ProvenanceEventType.TOOL_INVOKED,
                                ProvenanceEventType.fromWireName("tool.invoked")),
                () ->
                        assertSame(
                                ProvenanceEventType.FILE_HASHED,
                                ProvenanceEventType.fromWireName("file.hashed")),
                () ->
                        assertSame(
                                ProvenanceEventType.WARNING_RAISED,
                                ProvenanceEventType.fromWireName("warning.raised")),
                () ->
                        assertSame(
                                ProvenanceEventType.RUN_FINISHED,
                                ProvenanceEventType.fromWireName("run.finished")));
    }

    @Test
    @DisplayName("resolution is exact: no case folding, no trimming, no underscores")
    void fromWireNameIsExact() {
        assertAll(
                () -> assertRejected("Run.Started"),
                () -> assertRejected("RUN.STARTED"),
                () -> assertRejected("run_started"),
                () -> assertRejected(" run.started"),
                () -> assertRejected("run.started "),
                () -> assertRejected("run.begun"),
                () -> assertRejected(""));
    }

    @Test
    @DisplayName("an unknown wire name is rejected with a message that lists what is accepted")
    void fromWireNameMessageIsPinned() {
        IllegalArgumentException rejected =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> ProvenanceEventType.fromWireName("run.begun"));

        assertEquals(
                "no provenance event type has the wire name \"run.begun\"; expected one of"
                        + " [run.started, stage.started, stage.finished, tool.invoked,"
                        + " file.hashed, warning.raised, run.finished]",
                rejected.getMessage());
    }

    @Test
    @DisplayName("the message lists exactly the wire names that exist")
    void theMessageDoesNotDriftFromTheConstants() {
        IllegalArgumentException rejected =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> ProvenanceEventType.fromWireName("nonsense"));

        for (ProvenanceEventType type : ProvenanceEventType.values()) {
            assertTrue(
                    rejected.getMessage().contains(type.wireName()),
                    "the rejection message does not mention " + type.name());
        }
    }

    @Test
    @DisplayName("a null wire name is rejected as null, not as unknown")
    void fromWireNameRejectsNull() {
        NullPointerException rejected =
                assertThrows(
                        NullPointerException.class,
                        () -> ProvenanceEventType.fromWireName(deliberateNull()));

        assertEquals("wire", rejected.getMessage());
    }

    private static void assertRejected(String wire) {
        assertThrows(
                IllegalArgumentException.class,
                () -> ProvenanceEventType.fromWireName(wire),
                "\"" + wire + "\" was accepted as a wire name");
    }

    /**
     * A {@code null} that SpotBugs cannot see, so that a null-rejection test can exist.
     *
     * <p>{@code NP_NULL_PARAM_DEREF_ALL_TARGETS_DANGEROUS} is not excluded for this project, so
     * passing a literal {@code null} to a method that calls {@link
     * java.util.Objects#requireNonNull} fails the build. Laundering it through a generic method
     * removes the analyser's certainty without removing the test.
     *
     * @param <T> whatever the call site needs
     * @return {@code null}
     */
    private static <T> T deliberateNull() {
        return null;
    }
}
