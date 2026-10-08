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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.cometgui.ui.viewmodel.params.ActiveRun;
import org.cometgui.ui.viewmodel.params.PercolatorRequest;
import org.cometgui.ui.viewmodel.params.RunNotStartedException;
import org.cometgui.ui.viewmodel.params.RunObserver;
import org.cometgui.ui.viewmodel.percolator.PercolatorRerunPort;
import org.cometgui.ui.viewmodel.percolator.RerunCheck;
import org.cometgui.workflow.state.Plan;

/**
 * A rerun port that answers from a script, with the same two hand-drained queues as {@link
 * ScriptedEngine}: the "background" the port is called on and the "interface thread" its answers
 * are applied on. Not thread-safe: every call happens on the test's own thread.
 */
public final class ScriptedRerun implements PercolatorRerunPort {

    /** A start the test has seen and may drive. */
    public record Started(PercolatorRequest percolator, RunObserver observer) {}

    private Function<PercolatorRequest, RerunCheck> answer;

    private final Deque<Exception> refusals = new ArrayDeque<>();

    private final List<Started> started = new ArrayList<>();

    private final List<PercolatorRequest> checked = new ArrayList<>();

    private final List<String> callers = new ArrayList<>();

    private Plan plan;

    private String description = "";

    private int cancels;

    private RuntimeException cancelFailure;

    private final ScriptedEngine.Queue background = new ScriptedEngine.Queue("background");

    private final ScriptedEngine.Queue ui = new ScriptedEngine.Queue("ui");

    /**
     * A port answering every check with one answer until told otherwise.
     *
     * @param answer the answer for a request
     */
    public ScriptedRerun(Function<PercolatorRequest, RerunCheck> answer) {
        this.answer = Objects.requireNonNull(answer, "answer");
    }

    /**
     * Changes what checks answer from now on.
     *
     * @param next the answer for a request
     */
    public void answer(Function<PercolatorRequest, RerunCheck> next) {
        this.answer = Objects.requireNonNull(next, "next");
    }

    /**
     * Makes every start announce a plan, as the real port does.
     *
     * @param planned the plan
     * @param described the run in words
     */
    public void announce(Plan planned, String described) {
        this.plan = Objects.requireNonNull(planned, "planned");
        this.description = Objects.requireNonNull(described, "described");
    }

    /**
     * Makes the next start throw: a {@link RunNotStartedException} or a runtime exception.
     *
     * @param refusal what it throws
     */
    public void refuseNextStart(Exception refusal) {
        refusals.add(refusal);
    }

    /**
     * Makes every cancellation throw.
     *
     * @param failure what it throws
     */
    public void failCancellations(RuntimeException failure) {
        this.cancelFailure = failure;
    }

    @Override
    public RerunCheck check(PercolatorRequest percolator) {
        callers.add("check:" + background.draining());
        checked.add(percolator);
        return answer.apply(percolator);
    }

    @Override
    public ActiveRun start(PercolatorRequest percolator, RunObserver observer)
            throws RunNotStartedException {
        callers.add("start:" + background.draining());
        Exception refusal = refusals.pollFirst();
        if (refusal instanceof RunNotStartedException notStarted) {
            throw notStarted;
        }
        if (refusal instanceof RuntimeException failed) {
            throw failed;
        }
        started.add(new Started(percolator, observer));
        if (plan != null) {
            observer.planned(plan, description);
        }
        return () -> {
            callers.add("cancel:" + background.draining());
            cancels++;
            if (cancelFailure != null) {
                throw cancelFailure;
            }
        };
    }

    /**
     * The requests checks were given, oldest first.
     *
     * @return the requests
     */
    public List<PercolatorRequest> checked() {
        return List.copyOf(checked);
    }

    /**
     * The starts, oldest first.
     *
     * @return the starts
     */
    public List<Started> started() {
        return List.copyOf(started);
    }

    /**
     * Every call and whether it was made while the background queue was draining.
     *
     * @return {@code "check:true"}, {@code "start:true"}, {@code "cancel:true"} ...
     */
    public List<String> callers() {
        return List.copyOf(callers);
    }

    /**
     * How many cancellations reached the port.
     *
     * @return the count
     */
    public int cancels() {
        return cancels;
    }

    /**
     * The executor the view-model calls the port on.
     *
     * @return the background queue
     */
    public ScriptedEngine.Queue background() {
        return background;
    }

    /**
     * The executor the view-model applies answers on.
     *
     * @return the interface queue
     */
    public ScriptedEngine.Queue ui() {
        return ui;
    }

    /** Runs the background queue, then the interface queue, until both are empty. */
    public void settle() {
        while (background.pending() + ui.pending() > 0) {
            background.drain();
            ui.drain();
        }
    }
}
