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

import java.time.Duration;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The one way a GUI or end-to-end test waits for something it cannot be told about: it observes a
 * value, again and again, until the value is what it wants or a deadline passes (Phase 14 design
 * decision P14-8).
 *
 * <p><strong>What it is not.</strong> It is not a delay. Nothing here waits a fixed time and then
 * assumes the world has moved on: the condition is evaluated before the first pause and after every
 * pause, and the call returns the moment it holds. The bound is a deadlock detector, never a timing
 * assumption -- a test that passes only because the bound happens to be long enough is a test that
 * asserts on the clock.
 *
 * <p><strong>How it fails.</strong> With an {@link AssertionError} naming what was awaited, the
 * bound, how many times the value was observed, and <em>the last value observed</em> -- which is
 * the one fact a person reading a red build needs, and the one a bare "timed out" withholds.
 *
 * <p><strong>The only timed pause in the end-to-end harness.</strong> {@link #pause(Duration)} is
 * the single place in the end-to-end sources, and in the driver code they run, that suspends a
 * thread for a fixed time. {@code scripts/e2e/check-no-fixed-sleep.sh} reads the compiled classes
 * and permits a timed wait in exactly this class and exactly that method -- by class and method,
 * not by file, so a second pause written anywhere else in this file is still rejected. If a wait
 * can be on an event instead (a listener, a latch, a future), it should be: this primitive is for
 * the facts no event announces, such as a file appearing or another process answering.
 *
 * <p>Uses no JUnit type, so that the no-fixed-sleep check's self-test can compile an injured copy
 * of it with nothing but the JDK on the class path.
 */
public final class Await {

    /** How long to pause between two observations when the caller does not say. */
    public static final Duration DEFAULT_INTERVAL = Duration.ofMillis(25);

    private Await() {}

    /**
     * Observes a value until it satisfies a condition, pausing {@link #DEFAULT_INTERVAL} between
     * observations.
     *
     * @param <T> the observed value's type
     * @param what what is awaited, in words that complete "waited ... for": for example {@code "the
     *     Tool Manager to read its tool list"}
     * @param observe reads the current value; called at least once, and once more after every pause
     * @param wanted whether a value is the one awaited
     * @param bound how long to keep observing at most
     * @return the first observed value that satisfied {@code wanted}
     * @throws AssertionError if no observed value satisfied {@code wanted} before the bound passed,
     *     naming {@code what}, the bound and the last value observed
     * @throws IllegalStateException if the waiting thread is interrupted
     */
    public static <T> T until(
            String what,
            Supplier<? extends T> observe,
            Predicate<? super T> wanted,
            Duration bound) {
        return until(what, observe, wanted, bound, DEFAULT_INTERVAL);
    }

    /**
     * Observes a value until it satisfies a condition.
     *
     * <p>The value is observed first, then the deadline is consulted, then the thread pauses for
     * the interval or for what is left of the bound, whichever is shorter -- so the last
     * observation is made at or after the deadline, and a condition that became true during the
     * final pause is still seen.
     *
     * @param <T> the observed value's type
     * @param what what is awaited, in words that complete "waited ... for"
     * @param observe reads the current value; whatever it throws propagates unchanged
     * @param wanted whether a value is the one awaited
     * @param bound how long to keep observing at most; zero means "observe once"
     * @param interval how long to pause between observations; must be positive
     * @return the first observed value that satisfied {@code wanted}
     * @throws AssertionError if no observed value satisfied {@code wanted} before the bound passed
     * @throws IllegalArgumentException if {@code bound} is negative or {@code interval} is not
     *     positive
     * @throws IllegalStateException if the waiting thread is interrupted
     */
    public static <T> T until(
            String what,
            Supplier<? extends T> observe,
            Predicate<? super T> wanted,
            Duration bound,
            Duration interval) {
        Objects.requireNonNull(what, "what");
        Objects.requireNonNull(observe, "observe");
        Objects.requireNonNull(wanted, "wanted");
        Objects.requireNonNull(bound, "bound");
        Objects.requireNonNull(interval, "interval");
        if (bound.isNegative()) {
            throw new IllegalArgumentException("a bound cannot be negative: " + bound);
        }
        if (interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("an interval must be positive: " + interval);
        }
        long started = System.nanoTime();
        long limit = bound.toNanos();
        long observations = 0;
        while (true) {
            T value = observe.get();
            observations++;
            if (wanted.test(value)) {
                return value;
            }
            long elapsed = System.nanoTime() - started;
            if (elapsed >= limit) {
                throw new AssertionError(
                        "waited "
                                + bound
                                + " for "
                                + what
                                + ", observing "
                                + observations
                                + (observations == 1 ? " time" : " times")
                                + "; the last value observed was: "
                                + value);
            }
            pause(Duration.ofNanos(Math.min(interval.toNanos(), limit - elapsed)));
        }
    }

    /**
     * The poll's pause: the one timed wait the end-to-end harness permits (see the class comment).
     *
     * @param length how long to pause
     * @throws IllegalStateException if the thread is interrupted, with its interrupt flag restored
     */
    private static void pause(Duration length) {
        try {
            Thread.sleep(length);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting", interrupted);
        }
    }
}
