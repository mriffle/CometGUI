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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.cometgui.domain.secrets.SecretRedactor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
                () -> assertEquals("run.finished", ProvenanceEventType.RUN_FINISHED.wireName()),
                () -> assertEquals("export.written", ProvenanceEventType.EXPORT_WRITTEN.wireName()),
                () -> assertEquals("pdv.launched", ProvenanceEventType.PDV_LAUNCHED.wireName()),
                () -> assertEquals("mztab.written", ProvenanceEventType.MZTAB_WRITTEN.wireName()),
                () ->
                        assertEquals(
                                "limelight.converting",
                                ProvenanceEventType.LIMELIGHT_CONVERTING.wireName()),
                () ->
                        assertEquals(
                                "limelight.converted",
                                ProvenanceEventType.LIMELIGHT_CONVERTED.wireName()),
                () ->
                        assertEquals(
                                "limelight.unconverted",
                                ProvenanceEventType.LIMELIGHT_CONVERSION_FAILED.wireName()));
    }

    @Test
    @DisplayName(
            "phase 04's seven types, phase 10's export type, phase 11's PDV types and phase 12's"
                    + " Limelight conversion types are the types that exist")
    void theTypesArePinned() {
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
                        "RUN_FINISHED",
                        "EXPORT_WRITTEN",
                        "PDV_LAUNCHED",
                        "MZTAB_WRITTEN",
                        "LIMELIGHT_CONVERTING",
                        "LIMELIGHT_CONVERTED",
                        "LIMELIGHT_CONVERSION_FAILED"),
                names);
    }

    @Test
    @DisplayName("no two types share a wire name")
    void wireNamesAreDistinct() {
        Set<String> distinct = new HashSet<>();
        for (ProvenanceEventType type : ProvenanceEventType.values()) {
            assertTrue(distinct.add(type.wireName()), "two types share the wire name");
        }

        assertEquals(13, distinct.size());
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
                                ProvenanceEventType.fromWireName("run.finished")),
                () ->
                        assertSame(
                                ProvenanceEventType.EXPORT_WRITTEN,
                                ProvenanceEventType.fromWireName("export.written")),
                () ->
                        assertSame(
                                ProvenanceEventType.PDV_LAUNCHED,
                                ProvenanceEventType.fromWireName("pdv.launched")),
                () ->
                        assertSame(
                                ProvenanceEventType.MZTAB_WRITTEN,
                                ProvenanceEventType.fromWireName("mztab.written")),
                () ->
                        assertSame(
                                ProvenanceEventType.LIMELIGHT_CONVERTING,
                                ProvenanceEventType.fromWireName("limelight.converting")),
                () ->
                        assertSame(
                                ProvenanceEventType.LIMELIGHT_CONVERTED,
                                ProvenanceEventType.fromWireName("limelight.converted")),
                () ->
                        assertSame(
                                ProvenanceEventType.LIMELIGHT_CONVERSION_FAILED,
                                ProvenanceEventType.fromWireName("limelight.unconverted")));
    }

    @Test
    @DisplayName(
            "the three Limelight conversion events after run.finished are written and read back"
                    + " intact, in order")
    void limelightConversionEventsFollowTheRunAndReadBack(@TempDir Path directory)
            throws IOException {
        Path path = directory.resolve("events.log");
        Clock clock = Clock.fixed(Instant.parse("2026-10-11T12:00:00.750Z"), ZoneOffset.UTC);
        try (ProvenanceEventLog log =
                ProvenanceEventLog.openAppend(path, SecretRedactor.patternsOnly(), clock)) {
            log.append(ProvenanceEventType.RUN_FINISHED, Map.of("status", "completed"));
            log.append(
                    ProvenanceEventType.LIMELIGHT_CONVERTING, Map.of("limelight.q-cutoff", "0.01"));
            log.append(
                    ProvenanceEventType.LIMELIGHT_CONVERSION_FAILED,
                    Map.of("limelight.failure", "no-output"));
            log.append(
                    ProvenanceEventType.LIMELIGHT_CONVERTED,
                    Map.of("output.sha256", "e".repeat(64)));
        }

        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        assertEquals(
                "{\"seq\":2,\"time\":\"2026-10-11T12:00:00.750Z\","
                        + "\"type\":\"limelight.converting\","
                        + "\"payload\":{\"limelight.q-cutoff\":\"0.01\"}}",
                lines.get(1));
        assertEquals(
                "{\"seq\":3,\"time\":\"2026-10-11T12:00:00.750Z\","
                        + "\"type\":\"limelight.unconverted\","
                        + "\"payload\":{\"limelight.failure\":\"no-output\"}}",
                lines.get(2));
        RecoveredEventLog recovered = ProvenanceEventLogReader.recover(path);
        assertTrue(recovered.intact(), () -> "defects: " + recovered.defects());
        assertEquals(
                List.of(
                        ProvenanceEventType.RUN_FINISHED,
                        ProvenanceEventType.LIMELIGHT_CONVERTING,
                        ProvenanceEventType.LIMELIGHT_CONVERSION_FAILED,
                        ProvenanceEventType.LIMELIGHT_CONVERTED),
                recovered.events().stream().map(ProvenanceEvent::type).toList());
        assertEquals("e".repeat(64), recovered.events().get(3).payload().get("output.sha256"));
    }

    @Test
    @DisplayName("an mztab.written event after run.finished is written and read back intact")
    void anMzTabEventFollowsTheRunAndReadsBack(@TempDir Path directory) throws IOException {
        Path path = directory.resolve("events.log");
        Clock clock = Clock.fixed(Instant.parse("2026-10-10T12:00:00.500Z"), ZoneOffset.UTC);
        try (ProvenanceEventLog log =
                ProvenanceEventLog.openAppend(path, SecretRedactor.patternsOnly(), clock)) {
            log.append(ProvenanceEventType.RUN_FINISHED, Map.of("status", "completed"));
            log.append(
                    ProvenanceEventType.MZTAB_WRITTEN,
                    Map.of("mztab.path", "pdv/mztab/psms.mztab", "psms.out", "6670"));
        }

        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        assertEquals(
                "{\"seq\":2,\"time\":\"2026-10-10T12:00:00.500Z\",\"type\":\"mztab.written\","
                        + "\"payload\":{\"mztab.path\":\"pdv/mztab/psms.mztab\","
                        + "\"psms.out\":\"6670\"}}",
                lines.get(1));
        RecoveredEventLog recovered = ProvenanceEventLogReader.recover(path);
        assertTrue(recovered.intact(), () -> "defects: " + recovered.defects());
        assertSame(ProvenanceEventType.MZTAB_WRITTEN, recovered.events().get(1).type());
        assertEquals("6670", recovered.events().get(1).payload().get("psms.out"));
    }

    @Test
    @DisplayName("an export.written event after run.finished is written and read back intact")
    void anExportEventFollowsTheRunAndReadsBack(@TempDir Path directory) throws IOException {
        Path path = directory.resolve("events.log");
        Clock clock = Clock.fixed(Instant.parse("2026-10-09T12:00:00.123Z"), ZoneOffset.UTC);
        try (ProvenanceEventLog log =
                ProvenanceEventLog.openAppend(path, SecretRedactor.patternsOnly(), clock)) {
            log.append(ProvenanceEventType.RUN_FINISHED, Map.of("status", "completed"));
        }
        try (ProvenanceEventLog log =
                ProvenanceEventLog.openAppend(path, SecretRedactor.patternsOnly(), clock)) {
            log.append(
                    ProvenanceEventType.EXPORT_WRITTEN,
                    Map.of("file.path", "exports/x.tsv", "filter.cutoff", "0.01"));
        }

        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        assertEquals(
                "{\"seq\":2,\"time\":\"2026-10-09T12:00:00.123Z\",\"type\":\"export.written\","
                        + "\"payload\":{\"file.path\":\"exports/x.tsv\","
                        + "\"filter.cutoff\":\"0.01\"}}",
                lines.get(1));
        RecoveredEventLog recovered = ProvenanceEventLogReader.recover(path);
        assertTrue(recovered.intact(), () -> "defects: " + recovered.defects());
        assertSame(ProvenanceEventType.EXPORT_WRITTEN, recovered.events().get(1).type());
        assertEquals("0.01", recovered.events().get(1).payload().get("filter.cutoff"));
    }

    @Test
    @DisplayName(
            "a pdv.launched event after run.finished is written and read back intact, a multi-line"
                    + " value included")
    void aPdvLaunchFollowsTheRunAndReadsBack(@TempDir Path directory) throws IOException {
        Path path = directory.resolve("events.log");
        Clock clock = Clock.fixed(Instant.parse("2026-10-10T12:00:00.250Z"), ZoneOffset.UTC);
        try (ProvenanceEventLog log =
                ProvenanceEventLog.openAppend(path, SecretRedactor.patternsOnly(), clock)) {
            log.append(ProvenanceEventType.RUN_FINISHED, Map.of("status", "completed"));
            log.append(
                    ProvenanceEventType.PDV_LAUNCHED,
                    Map.of(
                            "pdv.jar.sha256",
                            "fc876310b6f9dd95bd9538f212e28d45ac0226ea1615a9c09f2e904d70d07908",
                            "process.environment",
                            "DISPLAY=:7\nLANG=C.UTF-8"));
        }

        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        assertEquals(2, lines.size(), "one line per event, the line break escaped");
        assertEquals(
                "{\"seq\":2,\"time\":\"2026-10-10T12:00:00.250Z\",\"type\":\"pdv.launched\","
                        + "\"payload\":{\"pdv.jar.sha256\":"
                        + "\"fc876310b6f9dd95bd9538f212e28d45ac0226ea1615a9c09f2e904d70d07908\","
                        + "\"process.environment\":\"DISPLAY=:7\\nLANG=C.UTF-8\"}}",
                lines.get(1));
        RecoveredEventLog recovered = ProvenanceEventLogReader.recover(path);
        assertTrue(recovered.intact(), () -> "defects: " + recovered.defects());
        assertSame(ProvenanceEventType.PDV_LAUNCHED, recovered.events().get(1).type());
        assertEquals(
                "DISPLAY=:7\nLANG=C.UTF-8",
                recovered.events().get(1).payload().get("process.environment"));
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
                        + " file.hashed, warning.raised, run.finished, export.written,"
                        + " pdv.launched, mztab.written, limelight.converting,"
                        + " limelight.converted, limelight.unconverted]",
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
