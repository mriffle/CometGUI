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

package org.cometgui.ui.testing;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.ui.viewmodel.params.ActiveRun;
import org.cometgui.ui.viewmodel.params.EngineCheck;
import org.cometgui.ui.viewmodel.params.RunEnginePort;
import org.cometgui.ui.viewmodel.params.RunNotStartedException;
import org.cometgui.ui.viewmodel.params.RunObserver;
import org.cometgui.workflow.state.Plan;

/**
 * A workflow engine port that answers from a script, with two executors a test drains by hand --
 * the "background" the port is called on and the "interface thread" its answers are applied on --
 * so that a test decides exactly when each half of the round trip happens and can prove which
 * thread called the port.
 *
 * <p>Not thread-safe: every call happens on the test's own thread.
 */
public final class ScriptedEngine implements RunEnginePort {

    /** A start that the test has seen and may drive. */
    public record Started(CometParameters model, List<Path> spectra, RunObserver observer) {

        /** Copies the files. */
        public Started {
            spectra = List.copyOf(spectra);
        }

        @Override
        public List<Path> spectra() {
            return List.copyOf(spectra);
        }
    }

    private Function<CometParameters, EngineCheck> answer;

    private final Deque<RunNotStartedException> refusals = new ArrayDeque<>();

    private Plan plan;

    private String description = "";

    private final List<Started> started = new ArrayList<>();

    private final List<String> callers = new ArrayList<>();

    private final AtomicInteger checks = new AtomicInteger();

    private final AtomicInteger cancels = new AtomicInteger();

    private RuntimeException cancelFailure;

    private final Queue background = new Queue("background");

    private final Queue ui = new Queue("ui");

    /**
     * A port whose every check answers with a given result until told otherwise.
     *
     * @param answer the check's answer for a model
     */
    public ScriptedEngine(Function<CometParameters, EngineCheck> answer) {
        this.answer = Objects.requireNonNull(answer, "answer");
    }

    /**
     * Changes what the check answers from now on.
     *
     * @param next the check's answer for a model
     */
    public void answer(Function<CometParameters, EngineCheck> next) {
        this.answer = Objects.requireNonNull(next, "next");
    }

    /**
     * Makes every start announce a plan, as the real port does before the engine starts.
     *
     * @param planned the plan
     * @param described the run in words
     */
    public void announce(Plan planned, String described) {
        this.plan = Objects.requireNonNull(planned, "planned");
        this.description = Objects.requireNonNull(described, "described");
    }

    /**
     * Makes every cancellation of a started run throw.
     *
     * @param failure what it throws
     */
    public void failCancellations(RuntimeException failure) {
        this.cancelFailure = Objects.requireNonNull(failure, "failure");
    }

    /**
     * Makes the next start refuse.
     *
     * @param refusal what it throws
     */
    public void refuseNextStart(RunNotStartedException refusal) {
        refusals.add(refusal);
    }

    @Override
    public EngineCheck check(CometParameters model, List<Path> spectra) {
        callers.add(Thread.currentThread().getName() + ":" + background.draining);
        checks.incrementAndGet();
        return answer.apply(model);
    }

    @Override
    public ActiveRun start(CometParameters model, List<Path> spectra, RunObserver observer)
            throws RunNotStartedException {
        callers.add(Thread.currentThread().getName() + ":" + background.draining);
        RunNotStartedException refusal = refusals.pollFirst();
        if (refusal != null) {
            throw refusal;
        }
        started.add(new Started(model, List.copyOf(spectra), observer));
        if (plan != null) {
            observer.planned(plan, description);
        }
        return () -> {
            callers.add("cancel:" + background.draining);
            cancels.incrementAndGet();
            if (cancelFailure != null) {
                throw cancelFailure;
            }
        };
    }

    /**
     * How many checks the port has answered.
     *
     * @return the count
     */
    public int checks() {
        return checks.get();
    }

    /**
     * How many times a started run was asked to cancel.
     *
     * @return the count
     */
    public int cancels() {
        return cancels.get();
    }

    /**
     * The runs started, oldest first.
     *
     * @return the starts
     */
    public List<Started> started() {
        return List.copyOf(started);
    }

    /**
     * For every call of the port, whether it was made while the background queue was being drained:
     * {@code "<thread>:true"}, or {@code "cancel:true"} for a cancellation.
     *
     * @return the calls, oldest first
     */
    public List<String> callers() {
        return List.copyOf(callers);
    }

    /**
     * The executor the view-model calls the port on.
     *
     * @return the background queue
     */
    public Queue background() {
        return background;
    }

    /**
     * The executor the view-model applies answers on.
     *
     * @return the interface queue
     */
    public Queue ui() {
        return ui;
    }

    /** Runs the background queue, then the interface queue, until both are empty. */
    public void settle() {
        while (background.pending() + ui.pending() > 0) {
            background.drain();
            ui.drain();
        }
    }

    /** An executor that holds its tasks until drained. */
    public static final class Queue implements Executor {

        private final String name;

        private final Deque<Runnable> tasks = new ArrayDeque<>();

        private boolean draining;

        Queue(String name) {
            this.name = name;
        }

        @Override
        public void execute(Runnable task) {
            tasks.add(Objects.requireNonNull(task, "task"));
        }

        /**
         * How many tasks wait.
         *
         * @return the count
         */
        public int pending() {
            return tasks.size();
        }

        /**
         * Runs the oldest waiting task alone.
         *
         * @throws IllegalStateException if none waits
         */
        public void runOne() {
            Runnable next = tasks.pollFirst();
            if (next == null) {
                throw new IllegalStateException(name + " has no task waiting");
            }
            draining = true;
            try {
                next.run();
            } finally {
                draining = false;
            }
        }

        /** Runs every waiting task, including those added while draining. */
        public void drain() {
            draining = true;
            try {
                Runnable next;
                while ((next = tasks.pollFirst()) != null) {
                    next.run();
                }
            } finally {
                draining = false;
            }
        }

        @Override
        public String toString() {
            return name + " (" + tasks.size() + " waiting)";
        }
    }
}
