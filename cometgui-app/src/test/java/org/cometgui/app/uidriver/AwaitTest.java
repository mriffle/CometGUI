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

package org.cometgui.app.uidriver;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The one wait primitive (P14-8) does what its name says: it re-evaluates the condition, returns
 * the first value that satisfies it, and otherwise fails with the bound and the last value seen.
 *
 * <p>Every assertion about time here is a lower bound -- the wait did not give up before its bound
 * -- which no scheduler can make false. None asserts that anything took less than some time.
 */
class AwaitTest {

    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);

    private static final Duration ONE_MILLISECOND = Duration.ofMillis(1);

    @Test
    @DisplayName(
            "a condition that becomes true on the fifth look returns that value, after five looks")
    void returnsTheFirstSatisfyingValue() {
        AtomicInteger looks = new AtomicInteger();
        Integer value =
                Await.until(
                        "the counter to reach 5",
                        looks::incrementAndGet,
                        seen -> seen >= 5,
                        ONE_MINUTE,
                        ONE_MILLISECOND);
        assertAll(
                () -> assertEquals(5, value, "the value that satisfied the condition"),
                () -> assertEquals(5, looks.get(), "observed once per poll, and not once more"));
    }

    @Test
    @DisplayName("a condition already true is observed exactly once")
    void anAlreadyTrueConditionIsObservedOnce() {
        AtomicInteger looks = new AtomicInteger();
        String value =
                Await.until(
                        "a ready value",
                        () -> "ready " + looks.incrementAndGet(),
                        seen -> seen.startsWith("ready"),
                        ONE_MINUTE);
        assertEquals("ready 1", value);
        assertEquals(1, looks.get());
    }

    @Test
    @DisplayName(
            "a condition that never holds fails, naming what was awaited, the bound, the number of"
                    + " looks and the last value observed -- and not before the bound")
    void aConditionThatNeverHoldsFailsWithTheLastValue() {
        AtomicInteger looks = new AtomicInteger();
        Duration bound = Duration.ofMillis(300);
        long started = System.nanoTime();
        AssertionError failed =
                assertThrows(
                        AssertionError.class,
                        () ->
                                Await.until(
                                        "the counter to reach a million",
                                        () -> "count " + looks.incrementAndGet(),
                                        seen -> seen.equals("count 1000000"),
                                        bound,
                                        Duration.ofMillis(10)));
        long elapsed = System.nanoTime() - started;
        int seen = looks.get();
        assertAll(
                () ->
                        assertEquals(
                                "waited PT0.3S for the counter to reach a million, observing "
                                        + seen
                                        + " times; the last value observed was: count "
                                        + seen,
                                failed.getMessage()),
                () -> assertTrue(seen >= 2, () -> "re-evaluated, not looked at once: " + seen),
                () ->
                        assertTrue(
                                elapsed >= bound.toNanos(),
                                () -> "gave up after " + elapsed + " ns, before the bound"));
    }

    @Test
    @DisplayName("a zero bound observes once, and says so in the singular")
    void aZeroBoundObservesOnce() {
        AtomicInteger looks = new AtomicInteger();
        AssertionError failed =
                assertThrows(
                        AssertionError.class,
                        () ->
                                Await.until(
                                        "never",
                                        looks::incrementAndGet,
                                        seen -> false,
                                        Duration.ZERO));
        assertEquals(1, looks.get());
        assertEquals(
                "waited PT0S for never, observing 1 time; the last value observed was: 1",
                failed.getMessage());
    }

    @Test
    @DisplayName("what the observation throws propagates unchanged, and stops the wait")
    void anObservationFailurePropagates() {
        IllegalStateException broken = new IllegalStateException("the launcher died");
        AtomicInteger looks = new AtomicInteger();
        IllegalStateException thrown =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                Await.until(
                                        "anything",
                                        () -> {
                                            if (looks.incrementAndGet() == 3) {
                                                throw broken;
                                            }
                                            return looks.get();
                                        },
                                        seen -> false,
                                        ONE_MINUTE,
                                        ONE_MILLISECOND));
        assertSame(broken, thrown);
        assertEquals(3, looks.get());
    }

    @Test
    @DisplayName(
            "an interrupted wait stops with IllegalStateException and keeps the interrupt flag")
    void anInterruptedWaitStops() {
        Thread.currentThread().interrupt();
        IllegalStateException thrown;
        try {
            thrown =
                    assertThrows(
                            IllegalStateException.class,
                            () -> Await.until("never", () -> 0, seen -> false, ONE_MINUTE));
        } finally {
            assertTrue(
                    Thread.interrupted(), "the interrupt flag was restored (and is cleared now)");
        }
        assertEquals("interrupted while waiting", thrown.getMessage());
        assertTrue(thrown.getCause() instanceof InterruptedException);
    }

    @Test
    @DisplayName("a negative bound and a non-positive interval are refused")
    void argumentsAreChecked() {
        assertAll(
                () ->
                        assertEquals(
                                "a bound cannot be negative: PT-0.001S",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        Await.until(
                                                                "x",
                                                                () -> 0,
                                                                seen -> true,
                                                                Duration.ofMillis(-1)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "an interval must be positive: PT0S",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        Await.until(
                                                                "x",
                                                                () -> 0,
                                                                seen -> true,
                                                                ONE_MINUTE,
                                                                Duration.ZERO))
                                        .getMessage()));
    }
}
