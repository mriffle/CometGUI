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

package org.cometgui.domain.run;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * One attempt at executing a run: the first execution, or a retry ({@code R-RUN-06}: "editing the
 * project creates a new prospective configuration or a new run/retry record").
 *
 * <p>An attempt that is {@link AttemptOutcome#RUNNING} has no end and may still gain fingerprints;
 * one that has ended has an end time and never changes again. {@link #succeededSteps()} holds the
 * fingerprint of every step that <strong>succeeded</strong> in this attempt, keyed by the step's
 * stable identifier ({@code EngineStep.id()}): a step that failed, was cancelled, was skipped or
 * was reused from an earlier attempt has no entry here, so a rerun preview computed from these is
 * never told that something was produced when it was not.
 *
 * <p>Times are truncated to milliseconds, the precision {@code run.json} records.
 *
 * @param number the attempt's 1-based number within its run
 * @param started when the attempt started
 * @param ended when it ended; empty while it is running
 * @param outcome how it ended, or {@link AttemptOutcome#RUNNING}
 * @param succeededSteps the fingerprints of the steps that succeeded in it, by step identifier
 */
public record RunAttempt(
        int number,
        Instant started,
        Optional<Instant> ended,
        AttemptOutcome outcome,
        Map<String, RecordedFingerprint> succeededSteps) {

    /**
     * Validates and copies the attempt; the fingerprints are held sorted by step identifier.
     *
     * @throws NullPointerException naming a component, key or value that is {@code null}
     * @throws IllegalArgumentException if the number is not positive, the end is present for a
     *     running attempt or absent for an ended one, the end is before the start, or a step
     *     identifier is malformed
     */
    public RunAttempt {
        if (number < 1) {
            throw new IllegalArgumentException("an attempt number is 1-based, but was " + number);
        }
        started = Objects.requireNonNull(started, "started").truncatedTo(ChronoUnit.MILLIS);
        Objects.requireNonNull(ended, "ended");
        Objects.requireNonNull(outcome, "outcome");
        if (outcome.isTerminal() && ended.isEmpty()) {
            throw new IllegalArgumentException(
                    "attempt " + number + " is " + outcome.wireName() + " but has no end time");
        }
        if (!outcome.isTerminal() && ended.isPresent()) {
            throw new IllegalArgumentException(
                    "attempt " + number + " is running but has an end time");
        }
        ended = ended.map(end -> end.truncatedTo(ChronoUnit.MILLIS));
        if (ended.isPresent() && ended.get().isBefore(started)) {
            throw new IllegalArgumentException("attempt " + number + " ends before it starts");
        }
        Objects.requireNonNull(succeededSteps, "succeededSteps");
        Map<String, RecordedFingerprint> sorted = new TreeMap<>();
        for (Map.Entry<String, RecordedFingerprint> entry : succeededSteps.entrySet()) {
            String step = RecordedFingerprint.requireIdentifier(entry.getKey(), "step id");
            sorted.put(step, Objects.requireNonNull(entry.getValue(), "fingerprint of " + step));
        }
        succeededSteps = Collections.unmodifiableMap(sorted);
    }

    /**
     * A new attempt, running, with nothing yet succeeded.
     *
     * @param number its 1-based number
     * @param started when it started
     * @return the attempt
     */
    public static RunAttempt started(int number, Instant started) {
        return new RunAttempt(number, started, Optional.empty(), AttemptOutcome.RUNNING, Map.of());
    }

    /**
     * The fingerprints of the steps that succeeded, sorted by step identifier and unmodifiable.
     *
     * @return the fingerprints
     */
    @Override
    public Map<String, RecordedFingerprint> succeededSteps() {
        return Collections.unmodifiableMap(new TreeMap<>(succeededSteps));
    }

    /**
     * This attempt with one more step recorded as succeeded.
     *
     * @param stepId the step's identifier
     * @param fingerprint its fingerprint
     * @return the new attempt
     * @throws IllegalStateException if the attempt has ended, or the step is already recorded with
     *     a different fingerprint
     */
    public RunAttempt withStepSucceeded(String stepId, RecordedFingerprint fingerprint) {
        if (outcome.isTerminal()) {
            throw new IllegalStateException(
                    "attempt "
                            + number
                            + " has ended ("
                            + outcome.wireName()
                            + ") and cannot change");
        }
        RecordedFingerprint already = succeededSteps.get(stepId);
        if (already != null && !already.equals(fingerprint)) {
            throw new IllegalStateException(
                    "attempt "
                            + number
                            + " already records step "
                            + stepId
                            + " as succeeded with a different fingerprint");
        }
        Map<String, RecordedFingerprint> more = new TreeMap<>(succeededSteps);
        more.put(stepId, fingerprint);
        return new RunAttempt(number, started, ended, outcome, more);
    }

    /**
     * This attempt, ended.
     *
     * @param ending how it ended; not {@link AttemptOutcome#RUNNING}
     * @param end when it ended
     * @return the ended attempt
     * @throws IllegalStateException if the attempt has already ended
     * @throws IllegalArgumentException if {@code ending} is {@link AttemptOutcome#RUNNING} or the
     *     end is before the start
     */
    public RunAttempt finished(AttemptOutcome ending, Instant end) {
        if (outcome.isTerminal()) {
            throw new IllegalStateException(
                    "attempt " + number + " has already ended (" + outcome.wireName() + ")");
        }
        return new RunAttempt(number, started, Optional.of(end), ending, succeededSteps);
    }
}
