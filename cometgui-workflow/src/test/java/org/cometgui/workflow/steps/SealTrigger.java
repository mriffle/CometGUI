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

package org.cometgui.workflow.steps;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

/**
 * The engine's clock, which also does one thing to a run at the one moment between its last step
 * and its seal: the first time the engine reads the time after the run's {@code run.json} records
 * {@code finalise-provenance} as succeeded.
 *
 * <p>Why the clock. Every step of a plan ends by recording its success in {@code run.json} and then
 * moving to its terminal state, which reads the time on the same thread before the engine can begin
 * finalising. {@code finalise-provenance} is the last step of every plan here, so the first time
 * read after {@code run.json} names it falls after every step's work and record, and before the
 * engine writes {@code provenance.json}, {@code provenance.rst} or the attempt's end. Done there,
 * synchronously, on the engine's own thread, the action cannot race the seal, and needs no hook in
 * the product: the engine is given this clock exactly as the application gives it the system's.
 * Until it is armed, and after it has fired, it is the system clock.
 *
 * <p>Whatever the action throws -- an assertion included, such as a directory that still accepts a
 * write -- is kept for the test to report, never thrown into the engine.
 */
final class SealTrigger extends Clock {

    /** Something done to the run once, before its seal. */
    @FunctionalInterface
    interface Action {

        /**
         * Does it.
         *
         * @throws IOException if it cannot
         */
        void run() throws IOException;
    }

    private final Clock delegate = Clock.systemUTC();

    private final Object lock = new Object();

    /** Guarded by {@link #lock}. */
    private Path runFile;

    /** Guarded by {@link #lock}. */
    private Action action;

    /** Guarded by {@link #lock}. */
    private boolean fired;

    /** Guarded by {@link #lock}. */
    private Throwable failure;

    /**
     * Arms the trigger for one run.
     *
     * @param run the run's {@code run.json}
     * @param what what to do before its seal
     */
    void arm(Path run, Action what) {
        synchronized (lock) {
            this.runFile = run;
            this.action = what;
            this.fired = false;
            this.failure = null;
        }
    }

    /**
     * Whether the action ran.
     *
     * @return {@code true} once it has
     */
    boolean fired() {
        synchronized (lock) {
            return fired;
        }
    }

    /**
     * What the action threw, if anything.
     *
     * @return the failure
     */
    Optional<Throwable> failure() {
        synchronized (lock) {
            return Optional.ofNullable(failure);
        }
    }

    @Override
    public Instant instant() {
        synchronized (lock) {
            if (action != null && !fired && recordsTheLastStep()) {
                fired = true;
                try {
                    action.run();
                } catch (IOException | RuntimeException | Error thrown) {
                    failure = thrown;
                }
            }
        }
        return delegate.instant();
    }

    private boolean recordsTheLastStep() {
        try {
            return Files.isRegularFile(runFile)
                    && Files.readString(runFile).contains("\"finalise-provenance\": {");
        } catch (IOException unreadable) {
            return false;
        }
    }

    @Override
    public ZoneId getZone() {
        return delegate.getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("the engine's clock is UTC");
    }
}
