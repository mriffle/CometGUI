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

package org.cometgui.tools.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import org.cometgui.domain.log.MessageSeverity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pins the stage log file's line format, character by character.
 *
 * <p><strong>Every expectation below is a hand-typed literal.</strong> Nothing here is derived from
 * the formatter, from a constant it uses or from the code under test; the whole point of the class
 * is that a later change to the format breaks a test visibly instead of agreeing with itself.
 *
 * <p>The format is a published artefact of a run, not an internal detail: a stage log is read by a
 * user, kept next to a provenance record, and may be parsed by something later. That is why it is
 * pinned this hard.
 */
class StageLogFormatTest {

    /** An arbitrary but fixed instant, chosen so every field is non-zero and two-digit. */
    private static final Instant AT = Instant.parse("2026-08-31T19:04:51.250Z");

    /**
     * A null the static analyser cannot see through.
     *
     * <p>Proving a method rejects null means passing it null, which SpotBugs reports as {@code
     * NP_NULL_PARAM_DEREF_ALL_TARGETS_DANGEROUS} -- a pattern the project's filter deliberately
     * does not exclude. Laundering the null through a collection keeps the test without weakening
     * the shared gate.
     *
     * @param <T> whatever the call site needs
     * @return null
     */
    private static <T> T deliberateNull() {
        List<T> holder = new ArrayList<>(1);
        holder.add(null);
        return holder.get(0);
    }

    @Nested
    @DisplayName("one line")
    class Line {

        @Test
        @DisplayName("is timestamp, bracketed stream tag, then the text")
        void theShape() {
            assertEquals(
                    "2026-08-31T19:04:51.250Z [stdout] Comet version 2024.01 rev. 0",
                    StageLogFormat.line(AT, "stdout", "Comet version 2024.01 rev. 0"));
            assertEquals(
                    "2026-08-31T19:04:51.250Z [stderr] Search 12% complete",
                    StageLogFormat.line(AT, "stderr", "Search 12% complete"));
            assertEquals(
                    "2026-08-31T19:04:51.250Z [cometgui] stage comet started",
                    StageLogFormat.line(AT, "cometgui", "stage comet started"));
        }

        @Test
        @DisplayName("the two tool tags are the same width, so the text starts at one column")
        void theTagsLineUp() {
            String out = StageLogFormat.line(AT, ToolStream.STANDARD_OUTPUT.tag(), "x");
            String err = StageLogFormat.line(AT, ToolStream.STANDARD_ERROR.tag(), "x");

            assertEquals(35, out.length(), "24 for the timestamp, then \" [stdout] x\"");
            assertEquals(35, err.length(), "24 for the timestamp, then \" [stderr] x\"");
            assertEquals("2026-08-31T19:04:51.250Z [stdout] x", out);
            assertEquals("2026-08-31T19:04:51.250Z [stderr] x", err);
        }

        @Test
        @DisplayName("the service tag is the literal cometgui")
        void theServiceTag() {
            assertEquals("cometgui", StageLogFormat.SERVICE_TAG);
        }

        @Test
        @DisplayName("an empty line is a line: it renders as the prefix and nothing else")
        void anEmptyLine() {
            assertEquals(
                    "2026-08-31T19:04:51.250Z [stdout] ", StageLogFormat.line(AT, "stdout", ""));
        }

        @Test
        @DisplayName("the fraction is truncated to milliseconds, never rounded")
        void millisecondsAreTruncated() {
            assertEquals(
                    "2026-08-31T19:04:51.999Z [stdout] x",
                    StageLogFormat.line(
                            Instant.parse("2026-08-31T19:04:51.999999999Z"), "stdout", "x"));
            assertEquals(
                    "2026-08-31T19:04:51.000Z [stdout] x",
                    StageLogFormat.line(
                            Instant.parse("2026-08-31T19:04:51.000999Z"), "stdout", "x"));
            assertEquals(
                    "2026-08-31T19:04:51.000Z [stdout] x",
                    StageLogFormat.line(Instant.parse("2026-08-31T19:04:51Z"), "stdout", "x"));
        }

        @Test
        @DisplayName("the timestamp is UTC even when this machine is not")
        void alwaysUtc() {
            /*
             * The default zone is changed on purpose.  On a machine whose zone is already UTC --
             * this build container's is -- an implementation that formatted in the SYSTEM zone
             * would produce the right answer for the wrong reason and the assertion would pass
             * while the property it claims to check was absent.  That is the shape of defect this
             * phase has already shipped once: a property true only because the machine happened to
             * satisfy it.
             */
            TimeZone original = TimeZone.getDefault();
            try {
                TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));

                assertEquals(
                        "2026-08-31T23:30:00.000Z [stdout] late",
                        StageLogFormat.line(
                                Instant.parse("2026-08-31T23:30:00Z"), "stdout", "late"),
                        "Tokyo is UTC+9, so a system-zone rendering would say 2026-09-01T08:30");
            } finally {
                TimeZone.setDefault(original);
            }
        }

        @Test
        @DisplayName("rejects a null instant, tag or text, naming the argument")
        void rejectsNulls() {
            assertEquals(
                    "at",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> StageLogFormat.line(deliberateNull(), "stdout", "x"))
                            .getMessage());
            assertEquals(
                    "tag",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> StageLogFormat.line(AT, deliberateNull(), "x"))
                            .getMessage());
            assertEquals(
                    "text",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> StageLogFormat.line(AT, "stdout", deliberateNull()))
                            .getMessage());
        }
    }

    @Nested
    @DisplayName("the lines the service writes itself")
    class ServiceLines {

        @Test
        @DisplayName("the header names the stage and the working directory")
        void started() {
            /*
             * A relative path, and only because SpotBugs reports a hard-coded absolute file name
             * in a test as DMI_HARDCODED_ABSOLUTE_FILENAME and the project fixes findings rather
             * than excluding them.  What is being pinned is the rendering, not the path: a real
             * working directory is absolute (R-PROC-04 requires it) and StageRunnerTest asserts
             * this same line with one.
             */
            assertEquals(
                    "stage comet started in run-2026-08-31",
                    StageLogFormat.startedText("comet", Path.of("run-2026-08-31")));
        }

        @Test
        @DisplayName("the header names the redacted command, and escapes nothing itself")
        void command() {
            assertEquals(
                    "command [\"/opt/comet\", \"-P\", \"[REDACTED]\"]",
                    StageLogFormat.commandText("[\"/opt/comet\", \"-P\", \"[REDACTED]\"]"));
        }

        @Test
        @DisplayName("the footer gives the exit code and the duration")
        void ended() {
            assertEquals(
                    "stage comet ended: exit code 0 after PT1M11.757S",
                    StageLogFormat.endedText("comet", 0, Duration.ofMillis(71_757), false, false));
        }

        @Test
        @DisplayName("the footer says so when the stage was cancelled")
        void endedCancelled() {
            assertEquals(
                    "stage comet ended: exit code 143 after PT2S, cancellation requested",
                    StageLogFormat.endedText("comet", 143, Duration.ofSeconds(2), true, false));
        }

        @Test
        @DisplayName("the footer distinguishes a timeout from a cancellation")
        void endedTimedOut() {
            assertEquals(
                    "stage comet ended: exit code 143 after PT2S, cancellation requested,"
                            + " timed out",
                    StageLogFormat.endedText("comet", 143, Duration.ofSeconds(2), true, true));
        }

        @Test
        @DisplayName("the two flags are independent: timed out alone renders alone")
        void endedTimedOutWithoutCancellation() {
            assertEquals(
                    "stage comet ended: exit code 1 after PT0S, timed out",
                    StageLogFormat.endedText("comet", 1, Duration.ZERO, false, true),
                    "StageOutcome rejects this combination; the renderer is total anyway, so"
                            + " neither flag can be silently ignored");
        }

        @Test
        @DisplayName("a negative exit code is printed as it was reported")
        void endedNegative() {
            assertEquals(
                    "stage pdv ended: exit code -1 after PT0.5S",
                    StageLogFormat.endedText("pdv", -1, Duration.ofMillis(500), false, false));
        }

        @Test
        @DisplayName("a stage that never started says why, and the header above says what")
        void couldNotStart() {
            assertEquals(
                    "stage comet could not be started: java.io.IOException: no such directory",
                    StageLogFormat.couldNotStartText(
                            "comet", "java.io.IOException: no such directory"));
        }

        @Test
        @DisplayName("rejects nulls, naming the argument")
        void rejectsNulls() {
            assertEquals(
                    "stageId",
                    assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            StageLogFormat.startedText(
                                                    deliberateNull(), Path.of("work")))
                            .getMessage());
            assertEquals(
                    "workingDirectory",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> StageLogFormat.startedText("comet", deliberateNull()))
                            .getMessage());
            assertEquals(
                    "redactedDisplayCommand",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> StageLogFormat.commandText(deliberateNull()))
                            .getMessage());
            assertEquals(
                    "stageId",
                    assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            StageLogFormat.endedText(
                                                    deliberateNull(),
                                                    0,
                                                    Duration.ZERO,
                                                    false,
                                                    false))
                            .getMessage());
            assertEquals(
                    "duration",
                    assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            StageLogFormat.endedText(
                                                    "comet", 0, deliberateNull(), false, false))
                            .getMessage());
            assertEquals(
                    "failure",
                    assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            StageLogFormat.couldNotStartText(
                                                    "comet", deliberateNull()))
                            .getMessage());
        }
    }

    @Nested
    @DisplayName("the stream tags")
    class Tags {

        @Test
        @DisplayName("are stdout and stderr, with the console severities that go with them")
        void tagsAndSeverities() {
            assertEquals("stdout", ToolStream.STANDARD_OUTPUT.tag());
            assertEquals("stderr", ToolStream.STANDARD_ERROR.tag());
            assertEquals(MessageSeverity.INFO, ToolStream.STANDARD_OUTPUT.severity());
            assertEquals(
                    MessageSeverity.STDERR,
                    ToolStream.STANDARD_ERROR.severity(),
                    "stderr is not an error: Comet and Percolator write progress to it");
        }
    }
}
