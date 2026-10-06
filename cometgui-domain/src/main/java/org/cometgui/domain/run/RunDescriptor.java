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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * What {@code run.json} holds: the run's {@link RunIdentity}, written once, and its attempts, which
 * are only ever added to ({@code R-RUN-06}).
 *
 * <h2>How a run may change, and how it may not</h2>
 *
 * <p>{@link #requireSuccessor(RunDescriptor)} is the rule, as a pure function, and {@code
 * RunStore.update} applies it against the file on disk before every write. A successor:
 *
 * <ul>
 *   <li>has an identical {@link #identity()} -- no input, hash, parameter file, release, index mode
 *       or database mechanism can be re-recorded after the run started;
 *   <li>keeps every attempt the run already has, in order: an attempt cannot be removed;
 *   <li>leaves an <strong>ended</strong> attempt exactly as it was;
 *   <li>may end the running attempt, and may add fingerprints to it, but cannot change its start or
 *       change or remove a fingerprint it already recorded;
 *   <li>may append new attempts: a retry is a new attempt record, never a rewrite of an old one.
 * </ul>
 *
 * <p>The constructor holds the attempts' own shape: numbered {@code 1..n} in order, and only the
 * latest may be running.
 *
 * <p>A running attempt found in a run whose project lock nobody held was interrupted -- the process
 * that wrote it died. Ending it as {@link AttemptOutcome#FAILED} is a permitted successor, and is
 * how it is recovered.
 *
 * @param identity the run's identity
 * @param attempts its attempts, oldest first
 */
public record RunDescriptor(RunIdentity identity, List<RunAttempt> attempts) {

    /** The {@code run.json} format this build reads and writes. */
    public static final int SCHEMA_VERSION = 1;

    /**
     * Validates and copies the descriptor.
     *
     * @throws NullPointerException naming a component or element that is {@code null}
     * @throws IllegalArgumentException if the attempts are not numbered {@code 1..n} in order, or
     *     an attempt other than the latest is running
     */
    public RunDescriptor {
        Objects.requireNonNull(identity, "identity");
        attempts = List.copyOf(Objects.requireNonNull(attempts, "attempts"));
        for (int index = 0; index < attempts.size(); index++) {
            RunAttempt attempt = attempts.get(index);
            if (attempt.number() != index + 1) {
                throw new IllegalArgumentException(
                        "attempts["
                                + index
                                + "] has number "
                                + attempt.number()
                                + ", but attempts are numbered 1, 2, 3 ... in order");
            }
            if (index < attempts.size() - 1 && !attempt.outcome().isTerminal()) {
                throw new IllegalArgumentException(
                        "attempts["
                                + index
                                + "] is running, but only the latest attempt may be running");
            }
        }
    }

    /**
     * A new run's record: its identity and no attempts yet.
     *
     * @param identity the identity
     * @return the descriptor
     */
    public static RunDescriptor of(RunIdentity identity) {
        return new RunDescriptor(identity, List.of());
    }

    /**
     * The attempts, oldest first, unmodifiable.
     *
     * @return the attempts
     */
    @Override
    public List<RunAttempt> attempts() {
        return List.copyOf(attempts);
    }

    /**
     * The attempt in progress, if there is one.
     *
     * @return the latest attempt if it is running, otherwise empty
     */
    public Optional<RunAttempt> runningAttempt() {
        if (attempts.isEmpty()) {
            return Optional.empty();
        }
        RunAttempt latest = attempts.get(attempts.size() - 1);
        return latest.outcome().isTerminal() ? Optional.empty() : Optional.of(latest);
    }

    /**
     * This run with a new attempt started: the first execution, or a retry.
     *
     * @param started when the attempt starts
     * @return the new descriptor
     * @throws IllegalStateException if an attempt is still running
     */
    public RunDescriptor withNewAttempt(Instant started) {
        Optional<RunAttempt> running = runningAttempt();
        if (running.isPresent()) {
            throw new IllegalStateException(
                    "attempt "
                            + running.get().number()
                            + " of run "
                            + identity.runId()
                            + " is still running; a new attempt starts only after it has ended");
        }
        List<RunAttempt> more = new ArrayList<>(attempts);
        more.add(RunAttempt.started(attempts.size() + 1, started));
        return new RunDescriptor(identity, more);
    }

    /**
     * This run with one more step recorded as succeeded in the running attempt.
     *
     * @param stepId the step's identifier
     * @param fingerprint its fingerprint
     * @return the new descriptor
     * @throws IllegalStateException if no attempt is running, or as {@link
     *     RunAttempt#withStepSucceeded}
     */
    public RunDescriptor withStepSucceeded(String stepId, RecordedFingerprint fingerprint) {
        return replaceRunning(requireRunning().withStepSucceeded(stepId, fingerprint));
    }

    /**
     * This run with its running attempt ended.
     *
     * @param outcome how it ended
     * @param ended when it ended
     * @return the new descriptor
     * @throws IllegalStateException if no attempt is running
     * @throws IllegalArgumentException as {@link RunAttempt#finished}
     */
    public RunDescriptor withAttemptFinished(AttemptOutcome outcome, Instant ended) {
        return replaceRunning(requireRunning().finished(outcome, ended));
    }

    private RunAttempt requireRunning() {
        return runningAttempt()
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "run " + identity.runId() + " has no running attempt"));
    }

    private RunDescriptor replaceRunning(RunAttempt replacement) {
        List<RunAttempt> replaced = new ArrayList<>(attempts);
        replaced.set(replaced.size() - 1, replacement);
        return new RunDescriptor(identity, replaced);
    }

    /**
     * The fingerprint of every step that succeeded in any attempt, the latest attempt's winning
     * where two recorded the same step -- what a later rerun preview compares against.
     *
     * @return the fingerprints by step identifier, sorted and unmodifiable
     */
    public Map<String, RecordedFingerprint> succeededFingerprints() {
        Map<String, RecordedFingerprint> merged = new TreeMap<>();
        for (RunAttempt attempt : attempts) {
            merged.putAll(attempt.succeededSteps());
        }
        return Collections.unmodifiableMap(merged);
    }

    /**
     * Requires {@code next} to be a permitted successor of this record; see the class documentation
     * for the rules.
     *
     * @param next the record about to replace this one
     * @throws NullPointerException if {@code next} is {@code null}
     * @throws RunImmutabilityException naming the first member {@code next} changes that it may not
     */
    public void requireSuccessor(RunDescriptor next) {
        Objects.requireNonNull(next, "next");
        String changed = identity.firstDifference(next.identity);
        if (!changed.isEmpty()) {
            throw new RunImmutabilityException(
                    changed,
                    "run "
                            + identity.runId()
                            + ": \""
                            + changed
                            + "\" is part of the run's identity, which is written once when the"
                            + " run starts and never changed; a different configuration is a new"
                            + " run");
        }
        if (next.attempts.size() < attempts.size()) {
            throw new RunImmutabilityException(
                    "attempts",
                    "run "
                            + identity.runId()
                            + " records "
                            + attempts.size()
                            + " attempt(s) and an attempt is never removed, but the update has "
                            + next.attempts.size());
        }
        for (int index = 0; index < attempts.size(); index++) {
            requireAttemptSuccessor(index, attempts.get(index), next.attempts.get(index));
        }
    }

    private void requireAttemptSuccessor(int index, RunAttempt before, RunAttempt after) {
        String member = "attempts[" + index + "]";
        if (before.outcome().isTerminal()) {
            if (!before.equals(after)) {
                throw new RunImmutabilityException(
                        member,
                        "run "
                                + identity.runId()
                                + ": attempt "
                                + before.number()
                                + " has ended ("
                                + before.outcome().wireName()
                                + ") and its record cannot change; a retry is a new attempt");
            }
            return;
        }
        if (!before.started().equals(after.started())) {
            throw new RunImmutabilityException(
                    member + ".started",
                    "run "
                            + identity.runId()
                            + ": the start of attempt "
                            + before.number()
                            + " cannot change");
        }
        Map<String, RecordedFingerprint> kept = after.succeededSteps();
        for (Map.Entry<String, RecordedFingerprint> recorded : before.succeededSteps().entrySet()) {
            if (!recorded.getValue().equals(kept.get(recorded.getKey()))) {
                throw new RunImmutabilityException(
                        member + ".succeededSteps." + recorded.getKey(),
                        "run "
                                + identity.runId()
                                + ": attempt "
                                + before.number()
                                + " recorded step "
                                + recorded.getKey()
                                + " as succeeded, and that record cannot be changed or removed");
            }
        }
    }
}
