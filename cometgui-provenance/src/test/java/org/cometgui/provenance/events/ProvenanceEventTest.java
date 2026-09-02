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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.cometgui.provenance.manifest.ProvenanceSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ProvenanceEvent}.
 *
 * <p>Every expected value here is a literal: the sorted key order is a hand-typed {@code List.of},
 * the truncated instant is a hand-typed {@code Instant.parse}, and the rendered {@code toString} is
 * a hand-typed string. Nothing asks the record what it holds and then asserts that it holds it.
 *
 * <p>Three properties carry the weight. The payload is <em>sorted</em>, so two runs that differ
 * only in map iteration order produce the same line. The timestamp is <em>truncated to
 * milliseconds</em>, so an event equals the event read back from the line it produced -- without
 * that, a machine with a microsecond clock would fail the reader's round trip and no other. And
 * {@code toString} prints <em>keys but never values</em>, because the payload is an open namespace
 * that later phases fill with whatever a stage records.
 */
class ProvenanceEventTest {

    private static final Instant WHEN = Instant.parse("2026-08-31T09:15:00.000Z");

    @Test
    @DisplayName("the four components come back exactly as given")
    void componentsAreKept() {
        ProvenanceEvent event =
                new ProvenanceEvent(
                        7L,
                        WHEN,
                        ProvenanceEventType.TOOL_INVOKED,
                        Map.of("tool", "comet", "version", "2026.02.2"));

        assertAll(
                () -> assertEquals(7L, event.sequence()),
                () -> assertEquals(Instant.parse("2026-08-31T09:15:00.000Z"), event.timestamp()),
                () -> assertEquals(ProvenanceEventType.TOOL_INVOKED, event.type()),
                () -> assertEquals("comet", event.payload().get("tool")),
                () -> assertEquals("2026.02.2", event.payload().get("version")));
    }

    @Test
    @DisplayName("the payload is iterated in ascending key order whatever order it arrived in")
    void payloadIsSorted() {
        Map<String, String> reversed = new LinkedHashMap<>();
        reversed.put("zeta", "3");
        reversed.put("mu", "2");
        reversed.put("alpha", "1");

        ProvenanceEvent event =
                new ProvenanceEvent(1L, WHEN, ProvenanceEventType.WARNING_RAISED, reversed);

        assertEquals(List.of("alpha", "mu", "zeta"), List.copyOf(event.payload().keySet()));
    }

    @Test
    @DisplayName("two events built from differently ordered maps are equal")
    void equalityIgnoresMapOrder() {
        Map<String, String> oneOrder = new LinkedHashMap<>();
        oneOrder.put("a", "1");
        oneOrder.put("b", "2");
        Map<String, String> otherOrder = new TreeMap<>(Comparator.reverseOrder());
        otherOrder.put("b", "2");
        otherOrder.put("a", "1");

        assertEquals(
                new ProvenanceEvent(1L, WHEN, ProvenanceEventType.FILE_HASHED, oneOrder),
                new ProvenanceEvent(1L, WHEN, ProvenanceEventType.FILE_HASHED, otherOrder));
    }

    @Test
    @DisplayName("a timestamp is truncated to milliseconds, so an event equals its own wire form")
    void timestampIsTruncatedToMilliseconds() {
        ProvenanceEvent event =
                new ProvenanceEvent(
                        1L,
                        Instant.parse("2026-08-31T09:15:00.123456789Z"),
                        ProvenanceEventType.RUN_STARTED,
                        Map.of());

        assertEquals(Instant.parse("2026-08-31T09:15:00.123Z"), event.timestamp());
    }

    @Test
    @DisplayName("the payload cannot be changed through the accessor or through its source map")
    void payloadIsImmutable() {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("stage", "comet");
        ProvenanceEvent event =
                new ProvenanceEvent(1L, WHEN, ProvenanceEventType.STAGE_STARTED, source);

        source.put("stage", "percolator");
        source.put("smuggled", "value");

        assertAll(
                () -> assertEquals(Map.of("stage", "comet"), event.payload()),
                () ->
                        assertThrows(
                                UnsupportedOperationException.class,
                                () -> event.payload().put("smuggled", "value")));
    }

    @Test
    @DisplayName("toString names the event and its payload keys, and prints no payload value")
    void toStringPrintsKeysAndNoValues() {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("upload.token", "glpat-Z1x9QeR7sVbN3mK0pLtY");
        payload.put("endpoint", "https://limelight.example.org");

        ProvenanceEvent event =
                new ProvenanceEvent(42L, WHEN, ProvenanceEventType.TOOL_INVOKED, payload);

        assertAll(
                () ->
                        assertEquals(
                                "ProvenanceEvent[sequence=42,"
                                        + " timestamp=2026-08-31T09:15:00.000Z,"
                                        + " type=tool.invoked,"
                                        + " payloadKeys=[endpoint, upload.token]]",
                                event.toString()),
                () -> assertFalse(event.toString().contains("glpat-")),
                () -> assertFalse(event.toString().contains("limelight.example.org")));
    }

    @Test
    @DisplayName("the two pinned constants are the strings written here")
    void constantsArePinned() {
        assertAll(
                () -> assertEquals(1L, ProvenanceEvent.FIRST_SEQUENCE),
                () -> assertEquals("status", ProvenanceEvent.STATUS_KEY));
    }

    @Test
    @DisplayName("a sequence number below one is rejected, and one is accepted")
    void sequenceStartsAtOne() {
        IllegalArgumentException zero =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new ProvenanceEvent(
                                        0L, WHEN, ProvenanceEventType.RUN_STARTED, Map.of()));
        IllegalArgumentException negative =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new ProvenanceEvent(
                                        -1L, WHEN, ProvenanceEventType.RUN_STARTED, Map.of()));

        assertAll(
                () ->
                        assertEquals(
                                "an event sequence number starts at 1, but was: 0",
                                zero.getMessage()),
                () ->
                        assertEquals(
                                "an event sequence number starts at 1, but was: -1",
                                negative.getMessage()),
                () ->
                        assertEquals(
                                1L,
                                new ProvenanceEvent(
                                                1L, WHEN, ProvenanceEventType.RUN_STARTED, Map.of())
                                        .sequence()));
    }

    @Test
    @DisplayName("a timestamp the fixed-width wire form cannot carry is rejected at both ends")
    void timestampRangeIsEnforced() {
        assertAll(
                () -> assertTimestampRejected(Instant.parse("+10000-01-01T00:00:00Z")),
                () -> assertTimestampRejected(Instant.parse("-0001-12-31T23:59:59Z")),
                () ->
                        assertTimestampAccepted(
                                Instant.parse("0000-01-01T00:00:00Z"),
                                Instant.parse("0000-01-01T00:00:00Z")),
                () ->
                        assertTimestampAccepted(
                                Instant.parse("9999-12-31T23:59:59.999Z"),
                                Instant.parse("9999-12-31T23:59:59.999Z")),
                // Truncation happens first, so this one lands exactly on the upper bound
                // instead of being rejected for digits the wire form never carries.
                () ->
                        assertTimestampAccepted(
                                Instant.parse("9999-12-31T23:59:59.999999Z"),
                                Instant.parse("9999-12-31T23:59:59.999Z")));
    }

    @Test
    @DisplayName("a run.finished event must carry a terminal status")
    void runFinishedRequiresATerminalStatus() {
        IllegalArgumentException missing =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> runFinishedWith(Map.of("stage", "percolator")));
        IllegalArgumentException running =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> runFinishedWith(Map.of("status", "running")));
        IllegalArgumentException notAStatus =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> runFinishedWith(Map.of("status", "finished")));

        assertAll(
                () ->
                        assertEquals(
                                "a run.finished event must carry its terminal status under the"
                                        + " \"status\" payload key, and this one carries no such"
                                        + " key",
                                missing.getMessage()),
                () ->
                        assertEquals(
                                "a run.finished event must carry a terminal status, and"
                                        + " \"running\" is not one",
                                running.getMessage()),
                () ->
                        assertEquals(
                                "the \"status\" payload key of a run.finished event must hold a"
                                        + " provenance status wire name, and this one does not",
                                notAStatus.getMessage()));
    }

    @Test
    @DisplayName("every terminal status is accepted on a run.finished event")
    void runFinishedAcceptsEveryTerminalStatus() {
        assertAll(
                () -> assertEquals("completed", statusOf(runFinishedWith("completed"))),
                () -> assertEquals("failed", statusOf(runFinishedWith("failed"))),
                () -> assertEquals("cancelled", statusOf(runFinishedWith("cancelled"))),
                () -> assertEquals("partial", statusOf(runFinishedWith("partial"))));
    }

    @Test
    @DisplayName("the status rule applies to run.finished and to no other type")
    void otherTypesNeedNoStatus() {
        assertEquals(
                Map.of(),
                new ProvenanceEvent(1L, WHEN, ProvenanceEventType.STAGE_FINISHED, Map.of())
                        .payload());
    }

    @Test
    @DisplayName("a payload key must be lower-case ASCII, one segment or dotted")
    void payloadKeysMustMatchThePinnedShape() {
        assertAll(
                () -> assertKeyAccepted("status"),
                () -> assertKeyAccepted("run.id"),
                () -> assertKeyAccepted("argv.0"),
                () -> assertKeyAccepted("file.sha256"),
                () -> assertKeyAccepted("a"),
                () -> assertKeyAccepted("9"),
                // A hyphen is legal only after the first dot, exactly as it is for a settings key.
                () -> assertKeyAccepted("comet.num-threads"),
                () -> assertKeyRejected("runId"),
                () -> assertKeyRejected("run_id"),
                () -> assertKeyRejected("RUN.ID"),
                () -> assertKeyRejected("Run.Id"),
                () -> assertKeyRejected("num-threads"),
                () -> assertKeyRejected("run."),
                () -> assertKeyRejected(".id"),
                () -> assertKeyRejected("run..id"),
                () -> assertKeyRejected("run id"),
                () -> assertKeyRejected("run.id "),
                () -> assertKeyRejected(""),
                () -> assertKeyRejected("   "));
    }

    @Test
    @DisplayName("the rejection message names the offending key and the rule it broke")
    void theKeyRejectionMessageIsPinned() {
        IllegalArgumentException rejected =
                assertThrows(
                        IllegalArgumentException.class, () -> eventWith(Map.of("runId", "R-1")));

        assertEquals(
                "a payload key must be lower-case ASCII, either one segment or dotted segments,"
                        + " matching (?:[a-z0-9]+(\\.[a-z0-9-]+)+)|[a-z0-9]+, but was: \"runId\"",
                rejected.getMessage());
    }

    @Test
    @DisplayName("the payload rule contains the settings rule rather than restating it")
    void thePayloadRuleReusesTheSettingsRule() {
        assertAll(
                // Textually derived, so the two namespaces cannot drift: if the settings pattern
                // changes, the dotted half of the payload pattern changes with it.
                () ->
                        assertTrue(
                                ProvenanceEvent.PAYLOAD_KEY_PATTERN.contains(
                                        ProvenanceSchema.SETTINGS_KEY_PATTERN),
                                "the payload rule no longer contains the settings rule"),
                () ->
                        assertEquals(
                                "(?:[a-z0-9]+(\\.[a-z0-9-]+)+)|[a-z0-9]+",
                                ProvenanceEvent.PAYLOAD_KEY_PATTERN),
                // Every settings key is a legal payload key, including the one already pinned.
                () -> assertKeyAccepted(ProvenanceSchema.PERCOLATOR_SEED_SETTING));
    }

    @Test
    @DisplayName("the keys this phase owns are exactly these, and every one matches the rule")
    void thePinnedKeysArePinned() {
        assertAll(
                () -> assertEquals("status", ProvenanceEvent.STATUS_KEY),
                () -> assertEquals("run.id", ProvenanceEvent.RUN_ID_KEY),
                () -> assertEquals("stage", ProvenanceEvent.STAGE_KEY),
                () -> assertEquals("tool", ProvenanceEvent.TOOL_KEY),
                () -> assertEquals("tool.version", ProvenanceEvent.TOOL_VERSION_KEY),
                () -> assertEquals("file.path", ProvenanceEvent.FILE_PATH_KEY),
                () -> assertEquals("file.md5", ProvenanceEvent.FILE_MD5_KEY),
                () -> assertEquals("file.sha256", ProvenanceEvent.FILE_SHA256_KEY),
                () -> assertEquals("message", ProvenanceEvent.MESSAGE_KEY));

        // The check that makes the convention enforceable rather than aspirational, which is what
        // ProvenanceSchema asks every phase to do for the keys it adds.
        for (String key :
                List.of(
                        ProvenanceEvent.STATUS_KEY,
                        ProvenanceEvent.RUN_ID_KEY,
                        ProvenanceEvent.STAGE_KEY,
                        ProvenanceEvent.TOOL_KEY,
                        ProvenanceEvent.TOOL_VERSION_KEY,
                        ProvenanceEvent.FILE_PATH_KEY,
                        ProvenanceEvent.FILE_MD5_KEY,
                        ProvenanceEvent.FILE_SHA256_KEY,
                        ProvenanceEvent.MESSAGE_KEY)) {
            assertKeyAccepted(key);
        }
    }

    @Test
    @DisplayName("a null payload key is rejected as null, not as the wrong shape")
    void aNullPayloadKeyIsRejected() {
        Map<String, String> nullKey = new LinkedHashMap<>();
        nullKey.put(null, "value");

        assertEquals(
                "a payload key",
                assertThrows(NullPointerException.class, () -> eventWith(nullKey)).getMessage());
    }

    @Test
    @DisplayName("a null payload value is rejected, and the message names its key")
    void payloadValuesMayNotBeNull() {
        Map<String, String> nullValue = new LinkedHashMap<>();
        nullValue.put("stage", null);

        NullPointerException rejected =
                assertThrows(NullPointerException.class, () -> eventWith(nullValue));

        assertEquals("the payload value of \"stage\"", rejected.getMessage());
    }

    @Test
    @DisplayName("null components are rejected by name")
    void nullComponentsAreRejected() {
        assertAll(
                () ->
                        assertEquals(
                                "timestamp",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new ProvenanceEvent(
                                                                1L,
                                                                deliberateNull(),
                                                                ProvenanceEventType.RUN_STARTED,
                                                                Map.of()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "type",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new ProvenanceEvent(
                                                                1L,
                                                                WHEN,
                                                                deliberateNull(),
                                                                Map.of()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "payload",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new ProvenanceEvent(
                                                                1L,
                                                                WHEN,
                                                                ProvenanceEventType.RUN_STARTED,
                                                                deliberateNull()))
                                        .getMessage()));
    }

    @Test
    @DisplayName("two events that differ in any component are not equal")
    void inequality() {
        ProvenanceEvent event =
                new ProvenanceEvent(
                        1L, WHEN, ProvenanceEventType.RUN_STARTED, Map.of("run.id", "R-1"));

        assertAll(
                () ->
                        assertNotEquals(
                                event,
                                new ProvenanceEvent(
                                        2L,
                                        WHEN,
                                        ProvenanceEventType.RUN_STARTED,
                                        Map.of("run.id", "R-1"))),
                () ->
                        assertNotEquals(
                                event,
                                new ProvenanceEvent(
                                        1L,
                                        Instant.parse("2026-08-31T09:15:01.000Z"),
                                        ProvenanceEventType.RUN_STARTED,
                                        Map.of("run.id", "R-1"))),
                () ->
                        assertNotEquals(
                                event,
                                new ProvenanceEvent(
                                        1L,
                                        WHEN,
                                        ProvenanceEventType.STAGE_STARTED,
                                        Map.of("run.id", "R-1"))),
                () ->
                        assertNotEquals(
                                event,
                                new ProvenanceEvent(
                                        1L,
                                        WHEN,
                                        ProvenanceEventType.RUN_STARTED,
                                        Map.of("run.id", "R-2"))));
    }

    private static void assertKeyAccepted(String key) {
        assertEquals(
                Map.of(key, "value"),
                eventWith(Map.of(key, "value")).payload(),
                "the key \"" + key + "\" was rejected");
    }

    private static void assertKeyRejected(String key) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put(key, "value");

        assertThrows(
                IllegalArgumentException.class,
                () -> eventWith(payload),
                "the key \"" + key + "\" was accepted");
    }

    private static ProvenanceEvent eventWith(Map<String, String> payload) {
        return new ProvenanceEvent(1L, WHEN, ProvenanceEventType.WARNING_RAISED, payload);
    }

    private static ProvenanceEvent runFinishedWith(Map<String, String> payload) {
        return new ProvenanceEvent(1L, WHEN, ProvenanceEventType.RUN_FINISHED, payload);
    }

    private static ProvenanceEvent runFinishedWith(String status) {
        return runFinishedWith(Map.of("status", status));
    }

    private static String statusOf(ProvenanceEvent event) {
        return event.payload().get("status");
    }

    private static void assertTimestampRejected(Instant timestamp) {
        IllegalArgumentException rejected =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new ProvenanceEvent(
                                        1L, timestamp, ProvenanceEventType.RUN_STARTED, Map.of()));

        assertEquals(
                "an event timestamp must lie between 0000-01-01T00:00:00Z and"
                        + " 9999-12-31T23:59:59.999Z, which is what the fixed-width wire form can"
                        + " represent",
                rejected.getMessage());
    }

    private static void assertTimestampAccepted(Instant timestamp, Instant expected) {
        assertEquals(
                expected,
                new ProvenanceEvent(1L, timestamp, ProvenanceEventType.RUN_STARTED, Map.of())
                        .timestamp());
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
