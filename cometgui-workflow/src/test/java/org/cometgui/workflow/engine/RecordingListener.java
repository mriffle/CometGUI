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

package org.cometgui.workflow.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.StepState;

/** Records every transition and completes futures when chosen transitions are seen. */
final class RecordingListener implements StepStateListener {

    private final Object lock = new Object();

    private final List<StepTransition> transitions = new ArrayList<>();

    private final List<Waiter> waiters = new ArrayList<>();

    private final CompletableFuture<RunResult> finished = new CompletableFuture<>();

    private final List<String> threadNames = new ArrayList<>();

    @Override
    public void onTransition(StepTransition transition) {
        synchronized (lock) {
            transitions.add(transition);
            threadNames.add(Thread.currentThread().getName());
            for (Waiter waiter : waiters) {
                if (waiter.wanted().test(transition)) {
                    waiter.seen().complete(transition);
                }
            }
        }
    }

    @Override
    public void onRunFinished(RunResult result) {
        finished.complete(result);
    }

    /**
     * A future completed by the first transition of a step into a state, including one already
     * recorded.
     */
    CompletableFuture<StepTransition> when(EngineStep step, StepState state) {
        Predicate<StepTransition> wanted = t -> t.step() == step && t.to() == state;
        CompletableFuture<StepTransition> seen = new CompletableFuture<>();
        synchronized (lock) {
            for (StepTransition transition : transitions) {
                if (wanted.test(transition)) {
                    seen.complete(transition);
                }
            }
            waiters.add(new Waiter(wanted, seen));
        }
        return seen;
    }

    List<StepTransition> transitions() {
        synchronized (lock) {
            return List.copyOf(transitions);
        }
    }

    List<String> threadNames() {
        synchronized (lock) {
            return List.copyOf(threadNames);
        }
    }

    /** The states one step passed through, starting with the state it left first. */
    List<StepState> path(EngineStep step) {
        List<StepState> path = new ArrayList<>();
        for (StepTransition transition : transitions()) {
            if (transition.step() == step) {
                if (path.isEmpty()) {
                    path.add(transition.from());
                }
                path.add(transition.to());
            }
        }
        return path;
    }

    CompletableFuture<RunResult> finished() {
        return finished;
    }

    private record Waiter(
            Predicate<StepTransition> wanted, CompletableFuture<StepTransition> seen) {}
}
