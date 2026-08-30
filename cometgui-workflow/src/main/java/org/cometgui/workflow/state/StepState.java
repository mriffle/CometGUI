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

package org.cometgui.workflow.state;

/**
 * The state of one workflow step.
 *
 * <p>These are the specification's nine explicit step states, under the names it gives them, in the
 * order it lists them (<em>Workflow state model</em>). There is no tenth: a situation that seems to
 * need one is a situation to take back to the specification, because these constants are written
 * into provenance and a state that appears in a run record has to mean the same thing when the
 * record is read back years later.
 *
 * <h2>The three predicates, and why these three</h2>
 *
 * <p>Nine states are too many for a stepper to draw nine ways, so the UI groups them. The grouping
 * is here rather than in the view model because two views need the same answer -- the stage stepper
 * and the console's stage filter -- and two independently written groupings would eventually
 * disagree about, say, whether {@link #CANCEL_REQUESTED} still counts as running.
 *
 * <ul>
 *   <li>{@link #isPending()} -- {@link #NOT_STARTED}, {@link #READY}. Nothing has happened yet. The
 *       stepper draws these grey, and {@link #READY} differs from {@link #NOT_STARTED} only in that
 *       its prerequisites are known to be satisfied, which is a tooltip, not a colour.
 *   <li>{@link #isActive()} -- {@link #VALIDATING}, {@link #RUNNING}, {@link #CANCEL_REQUESTED}.
 *       Work is in flight: the stepper spins, and the run cannot be declared finished. {@code
 *       CANCEL_REQUESTED} belongs here and not with the terminal states because the process is
 *       still alive; the stage has been <em>asked</em> to stop and has not yet stopped.
 *   <li>{@link #isTerminal()} -- {@link #SUCCEEDED}, {@link #FAILED}, {@link #CANCELLED}, {@link
 *       #SKIPPED}. The stage will not change again without something re-running it.
 * </ul>
 *
 * <p>The three partition the nine: every state is in exactly one group. That is asserted as a test
 * rather than left to inspection, because the run-state derivation reads "no stage is active" as
 * "the run has settled", which is only sound while the partition holds.
 *
 * <p>{@link #isFailureLike()} cuts across the partition rather than extending it. It answers a
 * different question -- "did this stage end without producing what it was asked for?" -- and is
 * true of {@link #FAILED} and {@link #CANCELLED}. It is deliberately false of {@link #SKIPPED},
 * which means the work was not needed, and false of {@link #CANCEL_REQUESTED}, which has not ended
 * at all and may still succeed if the stage finishes before the cancellation reaches it.
 */
public enum StepState {

    /** The stage has not begun and its prerequisites have not been checked. */
    NOT_STARTED,

    /** The stage's inputs and configuration are being checked. */
    VALIDATING,

    /** Prerequisites are satisfied; the stage is waiting to be run. */
    READY,

    /** The stage's work is in progress. */
    RUNNING,

    /** The stage completed and produced its outputs. */
    SUCCEEDED,

    /** The stage could not complete. */
    FAILED,

    /** A cancellation has been asked for; the stage has not stopped yet. */
    CANCEL_REQUESTED,

    /** The stage stopped because it was cancelled. Its outputs are partial. */
    CANCELLED,

    /**
     * The stage was not needed: its work is already done and still valid, or it was not selected.
     */
    SKIPPED;

    /**
     * Whether nothing has happened in this stage yet.
     *
     * @return {@code true} for {@link #NOT_STARTED} and {@link #READY}
     */
    public boolean isPending() {
        return switch (this) {
            case NOT_STARTED, READY -> true;
            case VALIDATING, RUNNING, CANCEL_REQUESTED, SUCCEEDED, FAILED, CANCELLED, SKIPPED ->
                    false;
        };
    }

    /**
     * Whether work is in flight in this stage, so the run cannot yet have settled.
     *
     * @return {@code true} for {@link #VALIDATING}, {@link #RUNNING} and {@link #CANCEL_REQUESTED}
     */
    public boolean isActive() {
        return switch (this) {
            case VALIDATING, RUNNING, CANCEL_REQUESTED -> true;
            case NOT_STARTED, READY, SUCCEEDED, FAILED, CANCELLED, SKIPPED -> false;
        };
    }

    /**
     * Whether this stage has finished and will not change without being re-run.
     *
     * @return {@code true} for {@link #SUCCEEDED}, {@link #FAILED}, {@link #CANCELLED} and {@link
     *     #SKIPPED}
     */
    public boolean isTerminal() {
        return switch (this) {
            case SUCCEEDED, FAILED, CANCELLED, SKIPPED -> true;
            case NOT_STARTED, READY, VALIDATING, RUNNING, CANCEL_REQUESTED -> false;
        };
    }

    /**
     * Whether this stage ended without producing what it was asked for.
     *
     * @return {@code true} for {@link #FAILED} and {@link #CANCELLED}
     */
    public boolean isFailureLike() {
        return switch (this) {
            case FAILED, CANCELLED -> true;
            case NOT_STARTED, READY, VALIDATING, RUNNING, CANCEL_REQUESTED, SUCCEEDED, SKIPPED ->
                    false;
        };
    }
}
