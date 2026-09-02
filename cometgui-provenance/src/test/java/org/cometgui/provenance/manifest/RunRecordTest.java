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

package org.cometgui.provenance.manifest;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.cometgui.domain.run.RunId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link RunRecord}.
 *
 * <p>The group that matters is the one about a run with no end. {@code R-PROV-05} requires a crash
 * to leave useful history, which makes a manifest a document that exists <em>during</em> a run; a
 * record that could not represent "started, still going, no end yet" would force a sentinel
 * timestamp and make an interrupted run indistinguishable from a finished one. So the absence is
 * asserted directly, and so is the absence of a duration that would otherwise have to be invented.
 */
class RunRecordTest {

    private static final RunId RUN_ID = new RunId("run-20260831-091500");
    private static final Instant START = Instant.parse("2026-08-31T09:14:00Z");
    private static final Instant END = Instant.parse("2026-08-31T09:48:00Z");

    @Nested
    @DisplayName("a run that has finished")
    class Finished {

        @Test
        @DisplayName("every component comes back exactly as given")
        void everyComponentComesBack() {
            RunRecord run =
                    new RunRecord(
                            RUN_ID,
                            "project-alpha",
                            ProvenanceStatus.COMPLETED,
                            START,
                            Optional.of(END));

            assertAll(
                    () -> assertEquals(new RunId("run-20260831-091500"), run.runId()),
                    () -> assertEquals("run-20260831-091500", run.runId().value()),
                    () -> assertEquals("project-alpha", run.projectId()),
                    () -> assertEquals("completed", run.status().wireName()),
                    () -> assertEquals(Instant.parse("2026-08-31T09:14:00Z"), run.start()),
                    () ->
                            assertEquals(
                                    Optional.of(Instant.parse("2026-08-31T09:48:00Z")), run.end()));
        }

        @Test
        @DisplayName("the duration is the difference between the two instants")
        void theDurationIsTheDifference() {
            RunRecord run =
                    new RunRecord(
                            RUN_ID,
                            "project-alpha",
                            ProvenanceStatus.COMPLETED,
                            START,
                            Optional.of(END));

            assertAll(
                    () -> assertEquals(Optional.of(Duration.ofSeconds(2040)), run.duration()),
                    () -> assertEquals(2040L, run.duration().orElseThrow().toSeconds()),
                    () -> assertEquals("PT34M", run.duration().orElseThrow().toString()));
        }
    }

    @Nested
    @DisplayName("a run that has not finished")
    class StillRunning {

        @Test
        @DisplayName("has no end and therefore no duration, rather than a sentinel")
        void hasNoEndAndNoDuration() {
            RunRecord run =
                    new RunRecord(
                            RUN_ID,
                            "project-alpha",
                            ProvenanceStatus.RUNNING,
                            START,
                            Optional.empty());

            assertAll(
                    () -> assertFalse(run.end().isPresent()),
                    () -> assertFalse(run.duration().isPresent()),
                    () -> assertEquals(Optional.empty(), run.duration()),
                    () -> assertEquals("running", run.status().wireName()));
        }

        @Test
        @DisplayName("a cancelled run keeps its end and is not confused with a failed one")
        void aCancelledRunKeepsItsEnd() {
            RunRecord cancelled =
                    new RunRecord(
                            RUN_ID,
                            "project-alpha",
                            ProvenanceStatus.CANCELLED,
                            START,
                            Optional.of(END));
            RunRecord failed =
                    new RunRecord(
                            RUN_ID,
                            "project-alpha",
                            ProvenanceStatus.FAILED,
                            START,
                            Optional.of(END));

            assertAll(
                    () -> assertEquals("cancelled", cancelled.status().wireName()),
                    () -> assertEquals("failed", failed.status().wireName()),
                    () -> assertTrue(cancelled.duration().isPresent()),
                    () -> assertEquals(Optional.of(Duration.ofSeconds(2040)), failed.duration()));
        }
    }

    @Nested
    @DisplayName("what it refuses")
    class Rejections {

        @Test
        @DisplayName("an end before the start is rejected, printing both instants")
        void anEndBeforeTheStartIsRejected() {
            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    new RunRecord(
                                            RUN_ID,
                                            "project-alpha",
                                            ProvenanceStatus.COMPLETED,
                                            START,
                                            Optional.of(Instant.parse("2026-08-31T09:13:59Z"))));

            assertEquals(
                    "end must not be before start, but start was 2026-08-31T09:14:00Z and end was"
                            + " 2026-08-31T09:13:59Z",
                    thrown.getMessage());
        }

        @Test
        @DisplayName("a blank project identifier is rejected, naming the field and the value")
        void aBlankProjectIdentifierIsRejected() {
            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    new RunRecord(
                                            RUN_ID,
                                            "   ",
                                            ProvenanceStatus.COMPLETED,
                                            START,
                                            Optional.of(END)));

            assertEquals("projectId must not be blank, but was: \"   \"", thrown.getMessage());
        }

        @Test
        @DisplayName("every reference component is required, and the message names it")
        void everyReferenceComponentIsRequired() {
            assertAll(
                    () ->
                            assertEquals(
                                    "runId",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new RunRecord(
                                                                    null,
                                                                    "project-alpha",
                                                                    ProvenanceStatus.COMPLETED,
                                                                    START,
                                                                    Optional.of(END)))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "projectId",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new RunRecord(
                                                                    RUN_ID,
                                                                    null,
                                                                    ProvenanceStatus.COMPLETED,
                                                                    START,
                                                                    Optional.of(END)))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "status",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new RunRecord(
                                                                    RUN_ID,
                                                                    "project-alpha",
                                                                    null,
                                                                    START,
                                                                    Optional.of(END)))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "start",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new RunRecord(
                                                                    RUN_ID,
                                                                    "project-alpha",
                                                                    ProvenanceStatus.COMPLETED,
                                                                    null,
                                                                    Optional.of(END)))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "end",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new RunRecord(
                                                                    RUN_ID,
                                                                    "project-alpha",
                                                                    ProvenanceStatus.COMPLETED,
                                                                    START,
                                                                    null))
                                            .getMessage()));
        }
    }
}
