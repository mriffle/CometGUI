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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The steps one run executes or reuses: the sub-graph needed to reach the steps it was asked for.
 *
 * <p>A plan is built from the steps wanted -- a target such as {@link
 * EngineStep#FINALISE_PROVENANCE}, plus any optional or conditional steps the user asked for -- and
 * closed under <em>required</em> edges: every step a wanted step requires is planned, and every
 * step those require, and so on. A {@link StepEdge#required() non-required} upstream step is
 * planned only if it is wanted or required by something else. Nothing else is in a plan.
 *
 * <h2>Phase 08's plans</h2>
 *
 * <p>Phase 08 implements the steps up to merging the PIN files plus finalising core provenance
 * (P8-9). Its plan is {@code covering({FINALISE_PROVENANCE})}, with {@link
 * EngineStep#BUILD_COMET_INDEX} added when an index mode is selected:
 *
 * <pre>
 *     validate-configuration, resolve-comet, serialise-comet-params, hash-inputs,
 *     [build-comet-index,] run-comet, validate-comet-outputs, merge-pin, finalise-provenance
 * </pre>
 *
 * <p>Percolator and every later step are declared in the graph but are not in that plan, and so are
 * reported as not planned -- never as succeeded or skipped (see {@link StageProjection} and {@link
 * RunState#deriveFrom(Plan, java.util.Map)}).
 *
 * <h2>Within a plan</h2>
 *
 * <p>Every edge of the graph whose two ends are both planned applies -- it orders the two steps
 * and, if it is a data edge, carries a fingerprint and invalidation -- whether or not it is
 * required. {@link #upstreamOf(EngineStep)} and {@link #downstreamOf(EngineStep)} answer within the
 * plan, in {@link EngineStep} order.
 */
public final class Plan {

    private final StepGraph graph;

    private final List<EngineStep> steps;

    private Plan(StepGraph graph, List<EngineStep> steps) {
        this.graph = graph;
        this.steps = steps;
    }

    /**
     * A plan over the {@link StepGraph#canonical() canonical graph}.
     *
     * @param wanted the steps the run is asked to reach; not empty
     * @return the plan
     * @throws NullPointerException if {@code wanted} or an element of it is {@code null}
     * @throws IllegalArgumentException if {@code wanted} is empty
     */
    public static Plan covering(Set<EngineStep> wanted) {
        return covering(StepGraph.canonical(), wanted);
    }

    /**
     * A plan over a given graph: the wanted steps and everything they transitively require.
     *
     * @param graph the graph to plan over
     * @param wanted the steps the run is asked to reach; not empty, and all declared in {@code
     *     graph}
     * @return the plan
     * @throws NullPointerException if an argument or an element of {@code wanted} is {@code null}
     * @throws IllegalArgumentException if {@code wanted} is empty or names a step the graph does
     *     not declare, naming it
     */
    public static Plan covering(StepGraph graph, Set<EngineStep> wanted) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(wanted, "wanted");
        if (wanted.isEmpty()) {
            throw new IllegalArgumentException("a plan must want at least one step");
        }
        Set<EngineStep> planned = EnumSet.noneOf(EngineStep.class);
        Deque<EngineStep> pending = new ArrayDeque<>();
        for (EngineStep step : wanted) {
            Objects.requireNonNull(step, "wanted contains null");
            if (!graph.steps().contains(step)) {
                throw new IllegalArgumentException(
                        "step " + step.id() + " is wanted but is not declared in the graph");
            }
            pending.add(step);
        }
        while (!pending.isEmpty()) {
            EngineStep step = pending.remove();
            if (planned.add(step)) {
                for (StepEdge edge : graph.edgesInto(step)) {
                    if (edge.required()) {
                        pending.add(edge.upstream());
                    }
                }
            }
        }
        List<EngineStep> ordered = new ArrayList<>();
        for (EngineStep step : graph.topologicalOrder()) {
            if (planned.contains(step)) {
                ordered.add(step);
            }
        }
        return new Plan(graph, List.copyOf(ordered));
    }

    /**
     * The graph this plan was drawn from.
     *
     * @return the graph
     */
    public StepGraph graph() {
        return graph;
    }

    /**
     * The planned steps, in the graph's topological order.
     *
     * @return an immutable, non-empty list
     */
    public List<EngineStep> steps() {
        return List.copyOf(steps);
    }

    /**
     * Whether a step is planned.
     *
     * @param step any step
     * @return {@code true} if this plan contains it
     */
    public boolean contains(EngineStep step) {
        return steps.contains(step);
    }

    /**
     * The planned steps directly upstream of a planned step, along any edge.
     *
     * @param step a planned step
     * @return an immutable list in {@link EngineStep} order
     * @throws IllegalArgumentException if {@code step} is not planned, naming it
     */
    public List<EngineStep> upstreamOf(EngineStep step) {
        requirePlanned(step);
        Set<EngineStep> upstream = EnumSet.noneOf(EngineStep.class);
        for (StepEdge edge : graph.edgesInto(step)) {
            if (contains(edge.upstream())) {
                upstream.add(edge.upstream());
            }
        }
        return List.copyOf(upstream);
    }

    /**
     * The planned steps directly downstream of a planned step, along any edge.
     *
     * @param step a planned step
     * @return an immutable list in {@link EngineStep} order
     * @throws IllegalArgumentException if {@code step} is not planned, naming it
     */
    public List<EngineStep> downstreamOf(EngineStep step) {
        requirePlanned(step);
        Set<EngineStep> downstream = EnumSet.noneOf(EngineStep.class);
        for (StepEdge edge : graph.edgesOutOf(step)) {
            if (contains(edge.downstream())) {
                downstream.add(edge.downstream());
            }
        }
        return List.copyOf(downstream);
    }

    /**
     * The planned steps drawn under one stepper stage.
     *
     * @param stage a stage
     * @return an immutable list in plan order; empty when no planned step maps to the stage
     */
    public List<EngineStep> stepsIn(WorkflowStage stage) {
        Objects.requireNonNull(stage, "stage");
        List<EngineStep> in = new ArrayList<>();
        for (EngineStep step : steps) {
            if (step.stage() == stage) {
                in.add(step);
            }
        }
        return List.copyOf(in);
    }

    /**
     * Every input kind some planned step declares: what a caller must supply to fingerprint the
     * plan.
     *
     * @return an immutable set in {@link InputKind} order
     */
    public Set<InputKind> inputs() {
        Set<InputKind> inputs = EnumSet.noneOf(InputKind.class);
        for (EngineStep step : steps) {
            inputs.addAll(step.inputs());
        }
        return Collections.unmodifiableSet(inputs);
    }

    private void requirePlanned(EngineStep step) {
        Objects.requireNonNull(step, "step");
        if (!contains(step)) {
            throw new IllegalArgumentException("step " + step.id() + " is not in this plan");
        }
    }

    /**
     * The planned step identifiers, in order.
     *
     * @return a readable form for messages and test failures
     */
    @Override
    public String toString() {
        List<String> ids = new ArrayList<>();
        for (EngineStep step : steps) {
            ids.add(step.id());
        }
        return "Plan" + ids;
    }
}
