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

package org.cometgui.domain.log;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.cometgui.domain.run.StageTag;
import org.cometgui.domain.testing.FakeStage;
import org.cometgui.domain.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link BoundedMessageLog}'s retention policy, its filters and its discard accounting.
 *
 * <p>The policy is a promise with two halves, and both are asserted here rather than assumed: the
 * size never exceeds the capacity, and what is dropped is always the oldest. The second half is the
 * one that is easy to get wrong and impossible to notice, because a log that drops an arbitrary
 * message still looks bounded. Every assertion below therefore names the exact messages expected,
 * in order.
 *
 * <p>The flood -- a million messages from several threads, with the heap measured -- is {@link
 * BoundedMessageLogFloodTest}.
 */
class BoundedMessageLogTest {

    private static final Instant RECORDED_AT = Instant.parse("2026-08-30T09:15:30.250Z");
    private static final StageTag COMET = FakeStage.named("comet");
    private static final StageTag PERCOLATOR = FakeStage.named("percolator");

    @Test
    @DisplayName("the documented default retention is the newest 10,000 messages")
    void theDefaultRetentionIsTenThousand() {
        BoundedMessageLog log = new BoundedMessageLog();

        assertAll(
                () -> assertEquals(10_000, BoundedMessageLog.DEFAULT_CAPACITY),
                () -> assertEquals(10_000, log.capacity()),
                () -> assertEquals(0, log.size()),
                () -> assertEquals(0L, log.discardedCount()),
                () -> assertEquals(List.of(), log.snapshot()));
    }

    @ParameterizedTest(name = "[{index}] capacity {0}")
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    @DisplayName("a capacity below 1 is rejected, and the message names the value")
    void rejectsACapacityBelowOne(int capacity) {
        IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, () -> new BoundedMessageLog(capacity));

        assertEquals(
                "a bounded message log must retain at least 1 message, but the capacity was: "
                        + capacity,
                thrown.getMessage());
    }

    @Test
    @DisplayName("a capacity of 1 is legal and keeps only the newest message")
    void aCapacityOfOneKeepsOnlyTheNewest() {
        BoundedMessageLog log = new BoundedMessageLog(1);

        log.append(line("first"));
        log.append(line("second"));
        log.append(line("third"));

        assertAll(
                () -> assertEquals(1, log.capacity()),
                () -> assertEquals(1, log.size()),
                () -> assertEquals(2L, log.discardedCount()),
                () -> assertEquals(List.of("third"), textsOf(log.snapshot())));
    }

    @Test
    @DisplayName("below the capacity nothing is discarded and the order is the append order")
    void keepsEverythingBelowCapacity() {
        BoundedMessageLog log = new BoundedMessageLog(5);

        log.append(line("one"));
        log.append(line("two"));
        log.append(line("three"));

        assertAll(
                () -> assertEquals(3, log.size()),
                () -> assertEquals(0L, log.discardedCount()),
                () -> assertEquals(List.of("one", "two", "three"), textsOf(log.snapshot())));
    }

    @Test
    @DisplayName("at exactly the capacity nothing has been discarded yet")
    void discardsNothingUntilTheCapacityIsExceeded() {
        BoundedMessageLog log = new BoundedMessageLog(3);

        log.append(line("one"));
        log.append(line("two"));
        log.append(line("three"));

        assertAll(
                () -> assertEquals(3, log.size()),
                () -> assertEquals(0L, log.discardedCount()),
                () -> assertEquals(List.of("one", "two", "three"), textsOf(log.snapshot())));
    }

    @Test
    @DisplayName("appending past the capacity discards the oldest, one for one, in order")
    void discardsTheOldestWhenFull() {
        BoundedMessageLog log = new BoundedMessageLog(3);

        for (int number = 1; number <= 5; number++) {
            log.append(line("line " + number));
        }

        assertAll(
                () -> assertEquals(3, log.size()),
                () -> assertEquals(2L, log.discardedCount()),
                () -> assertEquals(List.of("line 3", "line 4", "line 5"), textsOf(log.snapshot())));
    }

    @Test
    @DisplayName("the size never exceeds the capacity and size + discarded is what was appended")
    void theSizeNeverExceedsTheCapacity() {
        BoundedMessageLog log = new BoundedMessageLog(4);
        List<String> wrong = new ArrayList<>();

        for (int appended = 1; appended <= 100; appended++) {
            log.append(line("line " + appended));
            int expectedSize = Math.min(appended, 4);
            long expectedDiscarded = Math.max(0, appended - 4);
            if (log.size() != expectedSize || log.discardedCount() != expectedDiscarded) {
                wrong.add(
                        "after "
                                + appended
                                + " appends: size="
                                + log.size()
                                + " discarded="
                                + log.discardedCount()
                                + ", expected size="
                                + expectedSize
                                + " discarded="
                                + expectedDiscarded);
            }
        }

        assertAll(
                () -> assertEquals(List.of(), wrong),
                () ->
                        assertEquals(
                                List.of("line 97", "line 98", "line 99", "line 100"),
                                textsOf(log.snapshot())));
    }

    @Test
    @DisplayName("a snapshot is immutable")
    void aSnapshotIsImmutable() {
        BoundedMessageLog log = new BoundedMessageLog(2);
        log.append(line("one"));

        List<LogMessage> snapshot = log.snapshot();

        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(line("two")));
    }

    @Test
    @DisplayName("a snapshot is a copy: later appends, discards and clears do not change it")
    void aSnapshotIsACopyOfThatMoment() {
        BoundedMessageLog log = new BoundedMessageLog(2);
        log.append(line("one"));
        log.append(line("two"));

        List<LogMessage> taken = log.snapshot();
        log.append(line("three"));
        List<LogMessage> afterOverflow = log.snapshot();
        log.clear();

        assertAll(
                () -> assertEquals(List.of("one", "two"), textsOf(taken)),
                () -> assertEquals(List.of("two", "three"), textsOf(afterOverflow)),
                () -> assertEquals(List.of(), textsOf(log.snapshot())));
    }

    @Test
    @DisplayName("a null message is rejected by name and does not reach the buffer")
    void rejectsANullMessage() {
        BoundedMessageLog log = new BoundedMessageLog(2);

        NullPointerException thrown =
                assertThrows(
                        NullPointerException.class, () -> log.append(Nulls.of(LogMessage.class)));

        assertAll(
                () -> assertEquals("message", thrown.getMessage()),
                () -> assertEquals(0, log.size()),
                () -> assertEquals(0L, log.discardedCount()));
    }

    @Test
    @DisplayName("clear() empties the buffer and resets the discard count")
    void clearForgetsTheMessagesAndTheDiscardCount() {
        BoundedMessageLog log = new BoundedMessageLog(2);
        log.append(line("one"));
        log.append(line("two"));
        log.append(line("three"));

        log.clear();

        assertAll(
                () -> assertEquals(0, log.size()),
                () -> assertEquals(0L, log.discardedCount()),
                () -> assertEquals(List.of(), log.snapshot()),
                () -> assertEquals(2, log.capacity()));
    }

    @Test
    @DisplayName("a cleared log keeps its capacity and counts fresh discards from zero")
    void aClearedLogGoesOnWorking() {
        BoundedMessageLog log = new BoundedMessageLog(2);
        log.append(line("one"));
        log.append(line("two"));
        log.append(line("three"));
        log.clear();

        log.append(line("four"));
        log.append(line("five"));
        log.append(line("six"));

        assertAll(
                () -> assertEquals(2, log.size()),
                () -> assertEquals(1L, log.discardedCount()),
                () -> assertEquals(List.of("five", "six"), textsOf(log.snapshot())));
    }

    @Test
    @DisplayName("the severity filter keeps the order and admits exactly the severities asked for")
    void filtersByMinimumSeverity() {
        BoundedMessageLog log = new BoundedMessageLog(10);
        log.append(tagged(COMET, MessageSeverity.INFO, "info"));
        log.append(tagged(COMET, MessageSeverity.STDERR, "stderr"));
        log.append(tagged(COMET, MessageSeverity.WARNING, "warning"));
        log.append(tagged(COMET, MessageSeverity.ERROR, "error"));

        assertAll(
                () ->
                        assertEquals(
                                List.of("info", "stderr", "warning", "error"),
                                textsOf(log.snapshotAtLeast(MessageSeverity.INFO))),
                () ->
                        assertEquals(
                                List.of("stderr", "warning", "error"),
                                textsOf(log.snapshotAtLeast(MessageSeverity.STDERR))),
                () ->
                        assertEquals(
                                List.of("warning", "error"),
                                textsOf(log.snapshotAtLeast(MessageSeverity.WARNING))),
                () ->
                        assertEquals(
                                List.of("error"),
                                textsOf(log.snapshotAtLeast(MessageSeverity.ERROR))));
    }

    @Test
    @DisplayName("a severity-filtered snapshot is immutable too")
    void aFilteredSnapshotIsImmutable() {
        BoundedMessageLog log = new BoundedMessageLog(4);
        log.append(tagged(COMET, MessageSeverity.ERROR, "error"));

        List<LogMessage> filtered = log.snapshotAtLeast(MessageSeverity.ERROR);

        assertAll(
                () -> assertEquals(1, filtered.size()),
                () ->
                        assertThrows(
                                UnsupportedOperationException.class,
                                () -> filtered.add(line("another"))));
    }

    @Test
    @DisplayName("the stage filter keeps one stage and excludes messages belonging to no stage")
    void filtersByStage() {
        BoundedMessageLog log = new BoundedMessageLog(10);
        log.append(tagged(COMET, MessageSeverity.INFO, "comet 1"));
        log.append(tagged(PERCOLATOR, MessageSeverity.INFO, "percolator 1"));
        log.append(line("no stage at all"));
        log.append(tagged(COMET, MessageSeverity.INFO, "comet 2"));

        assertAll(
                () ->
                        assertEquals(
                                List.of("comet 1", "comet 2"),
                                textsOf(log.snapshotForStage(COMET, MessageSeverity.INFO))),
                () ->
                        assertEquals(
                                List.of("percolator 1"),
                                textsOf(log.snapshotForStage(PERCOLATOR, MessageSeverity.INFO))),
                () ->
                        assertEquals(
                                List.of(),
                                textsOf(
                                        log.snapshotForStage(
                                                FakeStage.named("pdv"), MessageSeverity.INFO))));
    }

    @Test
    @DisplayName(
            "stages are matched by id, so a different implementation of the same stage matches")
    void matchesStagesByIdRatherThanByClass() {
        BoundedMessageLog log = new BoundedMessageLog(4);
        log.append(tagged(COMET, MessageSeverity.INFO, "comet 1"));

        StageTag sameStageOtherClass = new OtherStage("comet");
        StageTag differentStage = new OtherStage("comet2");

        assertAll(
                () -> assertEquals(FakeStage.class, COMET.getClass()),
                () ->
                        assertEquals(
                                List.of("comet 1"),
                                textsOf(
                                        log.snapshotForStage(
                                                sameStageOtherClass, MessageSeverity.INFO))),
                () ->
                        assertEquals(
                                List.of(),
                                textsOf(
                                        log.snapshotForStage(
                                                differentStage, MessageSeverity.INFO))));
    }

    @Test
    @DisplayName("stage and severity filter together, not one or the other")
    void filtersByStageAndSeverityTogether() {
        BoundedMessageLog log = new BoundedMessageLog(10);
        log.append(tagged(COMET, MessageSeverity.INFO, "comet info"));
        log.append(tagged(COMET, MessageSeverity.ERROR, "comet error"));
        log.append(tagged(PERCOLATOR, MessageSeverity.ERROR, "percolator error"));

        assertEquals(
                List.of("comet error"),
                textsOf(log.snapshotForStage(COMET, MessageSeverity.WARNING)));
    }

    @Test
    @DisplayName("a discarded message is gone from the filtered views as well")
    void discardedMessagesLeaveTheFilteredViewsToo() {
        BoundedMessageLog log = new BoundedMessageLog(2);
        log.append(tagged(COMET, MessageSeverity.ERROR, "first error"));
        log.append(tagged(COMET, MessageSeverity.ERROR, "second error"));
        log.append(tagged(COMET, MessageSeverity.ERROR, "third error"));

        assertAll(
                () -> assertEquals(1L, log.discardedCount()),
                () ->
                        assertEquals(
                                List.of("second error", "third error"),
                                textsOf(log.snapshotAtLeast(MessageSeverity.ERROR))),
                () ->
                        assertEquals(
                                List.of("second error", "third error"),
                                textsOf(log.snapshotForStage(COMET, MessageSeverity.ERROR))));
    }

    @Test
    @DisplayName("the filters reject null arguments by name")
    void theFiltersRejectNulls() {
        BoundedMessageLog log = new BoundedMessageLog(2);

        NullPointerException noSeverity =
                assertThrows(
                        NullPointerException.class,
                        () -> log.snapshotAtLeast(Nulls.of(MessageSeverity.class)));
        NullPointerException noStage =
                assertThrows(
                        NullPointerException.class,
                        () -> log.snapshotForStage(Nulls.of(StageTag.class), MessageSeverity.INFO));
        NullPointerException noStageSeverity =
                assertThrows(
                        NullPointerException.class,
                        () -> log.snapshotForStage(COMET, Nulls.of(MessageSeverity.class)));

        assertAll(
                () -> assertEquals("minimumSeverity", noSeverity.getMessage()),
                () -> assertEquals("stage", noStage.getMessage()),
                () -> assertEquals("minimumSeverity", noStageSeverity.getMessage()));
    }

    @Test
    @DisplayName("an empty log filters to nothing rather than failing")
    void anEmptyLogFiltersToNothing() {
        BoundedMessageLog log = new BoundedMessageLog(3);

        assertAll(
                () -> assertTrue(log.snapshot().isEmpty()),
                () -> assertTrue(log.snapshotAtLeast(MessageSeverity.INFO).isEmpty()),
                () -> assertTrue(log.snapshotForStage(COMET, MessageSeverity.INFO).isEmpty()));
    }

    private static LogMessage line(String text) {
        return LogMessage.at(RECORDED_AT, null, MessageSeverity.INFO, text);
    }

    private static LogMessage tagged(StageTag stage, MessageSeverity severity, String text) {
        return LogMessage.at(RECORDED_AT, stage, severity, text);
    }

    private static List<String> textsOf(List<LogMessage> messages) {
        return messages.stream().map(LogMessage::text).toList();
    }

    /** A second {@link StageTag} implementation, so that id-matching can be told from equality. */
    private record OtherStage(String id) implements StageTag {

        @Override
        public String displayName() {
            return "The " + id + " stage, again";
        }
    }
}
