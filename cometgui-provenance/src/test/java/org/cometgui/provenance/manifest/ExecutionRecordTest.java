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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.cometgui.domain.ports.ToolCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ExecutionRecord}.
 *
 * <p>{@code AC-PRV-05} asks for start, end, duration and exit code for every process, and the
 * duration is the one of the four that is not stored. So the assertions here are on hand-typed
 * durations -- {@code Duration.ofSeconds(1950)} for a run from 09:15:00 to 09:47:30, arithmetic
 * done here rather than by the class under test -- and on the rejection of an interval that runs
 * backwards, which is the only way a derived duration can go wrong.
 *
 * <p>The {@code toString} group pins the whole rendered line as a literal. That is what makes it a
 * test of the secrecy property rather than a test that a string was produced: the expected text
 * contains {@code environmentNames=[UPLOAD_TOKEN]} and does not contain the token's value, so a
 * change that printed the environment fails on the comparison and not only on a {@code contains}
 * check that a later edit might forget to add.
 */
class ExecutionRecordTest {

    private static final Instant START = Instant.parse("2026-08-31T09:15:00Z");
    private static final Instant END = Instant.parse("2026-08-31T09:47:30Z");
    private static final String TOKEN = "glpat-Z1x9QeR7sVbN3mK0pLtY";

    private static ExecutionRecord execution(Instant start, Instant end, int exitCode) {
        return new ExecutionRecord(
                ManifestFixtures.command("UPLOAD_TOKEN", TOKEN),
                start,
                end,
                exitCode,
                Optional.empty(),
                Optional.empty(),
                ProvenanceStatus.COMPLETED);
    }

    @Nested
    @DisplayName("AC-PRV-05: start, end, duration and exit code")
    class Timing {

        @Test
        @DisplayName("the duration is the difference between the two instants")
        void theDurationIsTheDifference() {
            ExecutionRecord execution = execution(START, END, 0);

            assertAll(
                    () -> assertEquals(Duration.ofSeconds(1950), execution.duration()),
                    () -> assertEquals(1950L, execution.duration().toSeconds()),
                    () -> assertEquals("PT32M30S", execution.duration().toString()));
        }

        @Test
        @DisplayName("a process that started and ended in the same instant lasted no time")
        void aZeroLengthProcessLastedNoTime() {
            assertEquals(Duration.ZERO, execution(START, START, 0).duration());
        }

        @Test
        @DisplayName("sub-second precision survives into the duration")
        void subSecondPrecisionSurvives() {
            ExecutionRecord execution =
                    execution(
                            Instant.parse("2026-08-31T09:15:00.250Z"),
                            Instant.parse("2026-08-31T09:15:01.750Z"),
                            0);

            assertEquals(Duration.ofMillis(1500), execution.duration());
        }

        @Test
        @DisplayName("the four components come back exactly as given")
        void theFourComponentsComeBack() {
            ExecutionRecord execution = execution(START, END, 3);

            assertAll(
                    () -> assertEquals(Instant.parse("2026-08-31T09:15:00Z"), execution.start()),
                    () -> assertEquals(Instant.parse("2026-08-31T09:47:30Z"), execution.end()),
                    () -> assertEquals(3, execution.exitCode()),
                    () -> assertEquals(Duration.ofSeconds(1950), execution.duration()));
        }

        @Test
        @DisplayName("a signalled process's negative exit code is recorded, not rejected")
        void aNegativeExitCodeIsRecorded() {
            assertEquals(-9, execution(START, END, -9).exitCode());
        }

        @Test
        @DisplayName("an end before the start is rejected, printing both instants")
        void anEndBeforeTheStartIsRejected() {
            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> execution(START, Instant.parse("2026-08-31T09:14:59Z"), 0));

            assertEquals(
                    "end must not be before start, but start was 2026-08-31T09:15:00Z and end was"
                            + " 2026-08-31T09:14:59Z",
                    thrown.getMessage());
        }

        @Test
        @DisplayName("an end one nanosecond before the start is still rejected")
        void oneNanosecondBackwardsIsStillRejected() {
            assertThrows(
                    IllegalArgumentException.class, () -> execution(START, START.minusNanos(1), 0));
        }
    }

    @Nested
    @DisplayName("the command and the logs")
    class Contents {

        @Test
        @DisplayName("the exact argument array is kept, as a ToolCommand")
        void theExactArgumentArrayIsKept() {
            ExecutionRecord execution = execution(START, END, 0);

            assertAll(
                    () ->
                            assertEquals(
                                    java.util.List.of("/opt/comet/comet", "-P", "comet.params"),
                                    execution.command().argv()),
                    () ->
                            assertEquals(
                                    ManifestFixtures.RUN_DIRECTORY,
                                    execution.command().workingDirectory()),
                    () ->
                            assertEquals(
                                    TOKEN, execution.command().environment().get("UPLOAD_TOKEN")));
        }

        @Test
        @DisplayName("a process that captured no logs records none, rather than inventing paths")
        void aProcessWithNoLogsRecordsNone() {
            ExecutionRecord execution = execution(START, END, 0);

            assertAll(
                    () -> assertFalse(execution.stdout().isPresent()),
                    () -> assertFalse(execution.stderr().isPresent()));
        }

        @Test
        @DisplayName("archived logs are kept with their checksums")
        void archivedLogsAreKept() {
            LogRecord out =
                    new LogRecord(
                            ManifestFixtures.runFile("comet.stdout.log"),
                            ManifestFixtures.ABC_HASHES);
            LogRecord err =
                    new LogRecord(
                            ManifestFixtures.runFile("comet.stderr.log"),
                            ManifestFixtures.EMPTY_HASHES);

            ExecutionRecord execution =
                    new ExecutionRecord(
                            ManifestFixtures.command("OMP_NUM_THREADS", "8"),
                            START,
                            END,
                            0,
                            Optional.of(out),
                            Optional.of(err),
                            ProvenanceStatus.COMPLETED);

            assertAll(
                    () -> assertTrue(execution.stdout().isPresent()),
                    () ->
                            assertEquals(
                                    "900150983cd24fb0d6963f7d28e17f72",
                                    execution.stdout().orElseThrow().hashes().md5()),
                    () ->
                            assertEquals(
                                    "d41d8cd98f00b204e9800998ecf8427e",
                                    execution.stderr().orElseThrow().hashes().md5()));
        }

        @Test
        @DisplayName("a cancelled process is a legal, distinguishable record")
        void aCancelledProcessIsLegal() {
            ExecutionRecord cancelled =
                    new ExecutionRecord(
                            ManifestFixtures.command("OMP_NUM_THREADS", "8"),
                            START,
                            END,
                            143,
                            Optional.empty(),
                            Optional.empty(),
                            ProvenanceStatus.CANCELLED);

            assertAll(
                    () -> assertSame(ProvenanceStatus.CANCELLED, cancelled.status()),
                    () -> assertEquals("cancelled", cancelled.status().wireName()),
                    () -> assertEquals(143, cancelled.exitCode()));
        }
    }

    @Nested
    @DisplayName("what it refuses")
    class Rejections {

        @Test
        @DisplayName("every reference component is required, and the message names it")
        void everyReferenceComponentIsRequired() {
            ToolCommand command = ManifestFixtures.command("OMP_NUM_THREADS", "8");

            assertAll(
                    () ->
                            assertEquals(
                                    "command",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new ExecutionRecord(
                                                                    null,
                                                                    START,
                                                                    END,
                                                                    0,
                                                                    Optional.empty(),
                                                                    Optional.empty(),
                                                                    ProvenanceStatus.COMPLETED))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "start",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new ExecutionRecord(
                                                                    command,
                                                                    null,
                                                                    END,
                                                                    0,
                                                                    Optional.empty(),
                                                                    Optional.empty(),
                                                                    ProvenanceStatus.COMPLETED))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "end",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new ExecutionRecord(
                                                                    command,
                                                                    START,
                                                                    null,
                                                                    0,
                                                                    Optional.empty(),
                                                                    Optional.empty(),
                                                                    ProvenanceStatus.COMPLETED))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "stdout",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new ExecutionRecord(
                                                                    command,
                                                                    START,
                                                                    END,
                                                                    0,
                                                                    null,
                                                                    Optional.empty(),
                                                                    ProvenanceStatus.COMPLETED))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "stderr",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new ExecutionRecord(
                                                                    command,
                                                                    START,
                                                                    END,
                                                                    0,
                                                                    Optional.empty(),
                                                                    null,
                                                                    ProvenanceStatus.COMPLETED))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "status",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new ExecutionRecord(
                                                                    command,
                                                                    START,
                                                                    END,
                                                                    0,
                                                                    Optional.empty(),
                                                                    Optional.empty(),
                                                                    null))
                                            .getMessage()));
        }
    }

    @Nested
    @DisplayName("what it says about itself")
    class Rendering {

        @Test
        @DisplayName("the rendered line is exactly this, and the token is not in it")
        void theRenderedLineIsExactlyThis() {
            ExecutionRecord execution = execution(START, END, 0);

            assertEquals(
                    "ExecutionRecord[command=ToolCommand[argv=[\"/opt/comet/comet\", \"-P\","
                            + " \"comet.params\"], workingDirectory="
                            + ManifestFixtures.RUN_DIRECTORY
                            + ", environmentNames=[UPLOAD_TOKEN]], start=2026-08-31T09:15:00Z,"
                            + " end=2026-08-31T09:47:30Z, duration=PT32M30S, exitCode=0,"
                            + " stdout=Optional.empty, stderr=Optional.empty, status=COMPLETED]",
                    execution.toString());
        }

        @Test
        @DisplayName("the environment name is printed and the environment value is not")
        void theNameIsPrintedAndTheValueIsNot() {
            String rendered = execution(START, END, 0).toString();

            assertAll(
                    () -> assertTrue(rendered.contains("UPLOAD_TOKEN")),
                    () -> assertFalse(rendered.contains(TOKEN)),
                    () -> assertFalse(rendered.contains("glpat-")));
        }
    }
}
