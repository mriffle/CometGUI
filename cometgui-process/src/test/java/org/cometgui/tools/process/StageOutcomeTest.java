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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.cometgui.domain.run.StageTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The finished-stage value: what it holds, what it derives, and what it refuses to be. */
class StageOutcomeTest {

    private static final StageTag COMET = TestStage.named("comet");

    private static final Instant STARTED = Instant.parse("2026-08-31T19:04:51.250Z");

    private static final Instant ENDED = Instant.parse("2026-08-31T19:06:03.007Z");

    /**
     * The log file. Relative, because SpotBugs reports a hard-coded absolute file name in a test as
     * {@code DMI_HARDCODED_ABSOLUTE_FILENAME} and this project fixes findings rather than excluding
     * them. This value object neither opens nor resolves it.
     */
    private static final Path LOG = Path.of("comet.log");

    /**
     * A null the static analyser cannot see through; see {@code StageLogFormatTest} for why.
     *
     * @param <T> whatever the call site needs
     * @return null
     */
    private static <T> T deliberateNull() {
        List<T> holder = new ArrayList<>(1);
        holder.add(null);
        return holder.get(0);
    }

    private static StageOutcome outcome() {
        return new StageOutcome(
                COMET,
                "[\"/opt/comet\", \"-P\", \"comet.params\"]",
                LOG,
                0,
                STARTED,
                ENDED,
                412L,
                7L,
                false,
                false,
                0L);
    }

    @Test
    @DisplayName("holds every fact a provenance record and a console need")
    void everyComponent() {
        StageOutcome finished = outcome();

        assertEquals(COMET, finished.stage());
        assertEquals(
                "[\"/opt/comet\", \"-P\", \"comet.params\"]", finished.redactedDisplayCommand());
        assertEquals(Path.of("comet.log"), finished.logFile());
        assertEquals(0, finished.exitCode());
        assertEquals(Instant.parse("2026-08-31T19:04:51.250Z"), finished.startedAt());
        assertEquals(Instant.parse("2026-08-31T19:06:03.007Z"), finished.endedAt());
        assertEquals(412L, finished.standardOutputLines());
        assertEquals(7L, finished.standardErrorLines());
        assertFalse(finished.cancellationRequested());
        assertFalse(finished.timedOut());
        assertEquals(0L, finished.logWriteFailures());
    }

    @Test
    @DisplayName("the duration is derived from the two instants, so it cannot disagree with them")
    void duration() {
        assertEquals(Duration.ofMillis(71_757), outcome().duration());
        assertEquals("PT1M11.757S", outcome().duration().toString());
    }

    @Test
    @DisplayName("a clock that went backwards gives a negative duration rather than a lie")
    void aClockThatWentBackwards() {
        StageOutcome finished =
                new StageOutcome(COMET, "[]", LOG, 0, ENDED, STARTED, 0L, 0L, false, false, 0L);

        assertEquals(Duration.ofMillis(-71_757), finished.duration());
    }

    @Test
    @DisplayName("a cancelled stage and a timed-out stage are told apart")
    void cancelledAndTimedOut() {
        StageOutcome cancelled =
                new StageOutcome(COMET, "[]", LOG, 143, STARTED, ENDED, 1L, 0L, true, false, 0L);
        StageOutcome timedOut =
                new StageOutcome(COMET, "[]", LOG, 143, STARTED, ENDED, 1L, 0L, true, true, 0L);

        assertTrue(cancelled.cancellationRequested());
        assertFalse(cancelled.timedOut());
        assertTrue(timedOut.cancellationRequested());
        assertTrue(timedOut.timedOut());
    }

    @Test
    @DisplayName("a timeout that asked for no cancellation is refused: it cannot have happened")
    void refusesTimedOutWithoutCancellation() {
        IllegalArgumentException refused =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new StageOutcome(
                                        COMET, "[]", LOG, 143, STARTED, ENDED, 0L, 0L, false, true,
                                        0L));

        assertTrue(
                refused.getMessage().contains("cannot happen"),
                "the message should say why, but was: " + refused.getMessage());
    }

    @Test
    @DisplayName("a negative line count or failure count is refused, naming the component")
    void refusesNegativeCounts() {
        assertEquals(
                "standardOutputLines must not be negative, but was: -1",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new StageOutcome(
                                                COMET, "[]", LOG, 0, STARTED, ENDED, -1L, 0L, false,
                                                false, 0L))
                        .getMessage());
        assertEquals(
                "standardErrorLines must not be negative, but was: -1",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new StageOutcome(
                                                COMET, "[]", LOG, 0, STARTED, ENDED, 0L, -1L, false,
                                                false, 0L))
                        .getMessage());
        assertEquals(
                "logWriteFailures must not be negative, but was: -3",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new StageOutcome(
                                                COMET, "[]", LOG, 0, STARTED, ENDED, 0L, 0L, false,
                                                false, -3L))
                        .getMessage());
    }

    @Test
    @DisplayName("zero is not negative: the ordinary run is accepted")
    void zeroCountsAreFine() {
        StageOutcome finished =
                new StageOutcome(COMET, "[]", LOG, 0, STARTED, ENDED, 0L, 0L, false, false, 0L);

        assertEquals(0L, finished.standardOutputLines());
        assertEquals(0L, finished.standardErrorLines());
        assertEquals(0L, finished.logWriteFailures());
    }

    @Test
    @DisplayName("rejects a null component, naming it")
    void refusesNulls() {
        assertEquals(
                "stage",
                assertThrows(
                                NullPointerException.class,
                                () ->
                                        new StageOutcome(
                                                deliberateNull(),
                                                "[]",
                                                LOG,
                                                0,
                                                STARTED,
                                                ENDED,
                                                0L,
                                                0L,
                                                false,
                                                false,
                                                0L))
                        .getMessage());
        assertEquals(
                "redactedDisplayCommand",
                assertThrows(
                                NullPointerException.class,
                                () ->
                                        new StageOutcome(
                                                COMET,
                                                deliberateNull(),
                                                LOG,
                                                0,
                                                STARTED,
                                                ENDED,
                                                0L,
                                                0L,
                                                false,
                                                false,
                                                0L))
                        .getMessage());
        assertEquals(
                "logFile",
                assertThrows(
                                NullPointerException.class,
                                () ->
                                        new StageOutcome(
                                                COMET,
                                                "[]",
                                                deliberateNull(),
                                                0,
                                                STARTED,
                                                ENDED,
                                                0L,
                                                0L,
                                                false,
                                                false,
                                                0L))
                        .getMessage());
        assertEquals(
                "startedAt",
                assertThrows(
                                NullPointerException.class,
                                () ->
                                        new StageOutcome(
                                                COMET,
                                                "[]",
                                                LOG,
                                                0,
                                                deliberateNull(),
                                                ENDED,
                                                0L,
                                                0L,
                                                false,
                                                false,
                                                0L))
                        .getMessage());
        assertEquals(
                "endedAt",
                assertThrows(
                                NullPointerException.class,
                                () ->
                                        new StageOutcome(
                                                COMET,
                                                "[]",
                                                LOG,
                                                0,
                                                STARTED,
                                                deliberateNull(),
                                                0L,
                                                0L,
                                                false,
                                                false,
                                                0L))
                        .getMessage());
    }

    @Test
    @DisplayName("two outcomes describing the same stage are equal, and say so in words")
    void valueSemantics() {
        assertEquals(outcome(), outcome());
        assertEquals(outcome().hashCode(), outcome().hashCode());
        assertTrue(
                outcome().toString().contains("exitCode=0"),
                "a record's own toString is enough here, but it has to be there: "
                        + outcome().toString());
    }
}
