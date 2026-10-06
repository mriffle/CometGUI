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

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A validated, acyclic dependency graph over {@link EngineStep engine steps}, and the one declared
 * graph the engine runs: {@link #canonical()}.
 *
 * <p>{@code R-RUN-01} requires invalidation to be "computed from a declared dependency graph ...
 * not from ad hoc conditionals". This class is that declaration. Every edge is written once, below,
 * with its reason; {@link Plan}, {@link Fingerprints} and {@link RerunPreview} read it and contain
 * no step-specific logic of their own.
 *
 * <h2>The canonical edges and why each exists</h2>
 *
 * <p>Written as {@code upstream -> downstream}. "Ordering" marks an edge out of a {@link
 * StepKind#PREPARATION} step, which orders but carries no invalidation; "data" an edge out of a
 * {@link StepKind#RESULT} step. "If planned" marks the two non-required edges.
 *
 * <ol>
 *   <li>{@code validate-configuration -> resolve-comet} (ordering). Nothing is downloaded or
 *       launched -- probing launches Comet -- for a configuration the validator blocks; the decoy
 *       blocks must stop a run "before Comet starts".
 *   <li>{@code validate-configuration -> resolve-percolator} (ordering). The same, for Percolator.
 *   <li>{@code validate-configuration -> serialise-comet-params} (ordering). A parameter set the
 *       validator rejects is never archived as a run's {@code comet.params}, which is written once.
 *   <li>{@code validate-configuration -> hash-inputs} (ordering). Hours of hashing are not spent on
 *       a run that is blocked. Hashing still runs concurrently with tool installation, as the
 *       specification allows: there is deliberately no edge between {@code hash-inputs} and either
 *       {@code resolve-*} step.
 *   <li>{@code serialise-comet-params -> build-comet-index} (data). The index build reads the
 *       archived file with {@code -P}; a tool must never read a file CometGUI is still writing.
 *   <li>{@code resolve-comet -> build-comet-index} (ordering). It runs the resolved binary.
 *   <li>{@code hash-inputs -> build-comet-index} (ordering). The index cache is keyed by the
 *       FASTA's SHA-256 (P8-8), and the hash recorded must be of the bytes Comet then read.
 *   <li>{@code serialise-comet-params -> run-comet} (data). Each invocation's {@code -P} is the
 *       archived file.
 *   <li>{@code resolve-comet -> run-comet} (ordering). It runs the resolved binary.
 *   <li>{@code hash-inputs -> run-comet} (ordering). "The input fingerprint recorded for a run must
 *       correspond to the file contents actually used": the spectra and the FASTA are hashed before
 *       Comet reads them.
 *   <li>{@code build-comet-index -> run-comet} (data, if planned). With an index mode selected the
 *       search's {@code -D} is the {@code .idx} the build wrote, and Comet must not read it while
 *       it is being written. Without one there is no index step, and the search reads the FASTA.
 *   <li>{@code run-comet -> validate-comet-outputs} (data). It validates what Comet wrote.
 *   <li>{@code validate-comet-outputs -> merge-pin} (data). Only validated PINs are merged.
 *   <li>{@code merge-pin -> run-percolator} (data). Percolator reads {@code merged.pin}; this is
 *       the "preserved merged PIN" a Percolator-only rerun starts from.
 *   <li>{@code resolve-percolator -> run-percolator} (ordering). It runs the resolved binary.
 *   <li>{@code run-percolator -> parse-percolator} (data). It parses what Percolator wrote.
 *   <li>{@code parse-percolator -> finalise-results} (data). Result indexes are built from the
 *       parsed Percolator results.
 *   <li>{@code merge-pin -> finalise-provenance} (data). Core provenance hashes the Comet outputs
 *       and {@code merged.pin}; in phase 08 this is the step it closes.
 *   <li>{@code finalise-results -> finalise-provenance} (data, if planned). When Percolator and the
 *       results are part of the run, core provenance waits for them and hashes their outputs too.
 *   <li>{@code finalise-results -> launch-pdv} (data). PDV is driven through an mzTab generated
 *       from the Comet and Percolator results ({@code D-005}).
 *   <li>{@code parse-percolator -> convert-limelight} (data). The converter consumes the validated
 *       Percolator XML (and, upstream of it, the run's pepXML).
 *   <li>{@code convert-limelight -> upload-limelight} (data). It uploads what the converter wrote.
 *   <li>{@code finalise-provenance -> append-downstream-provenance} (data). Downstream events are
 *       appended to finalised core provenance.
 *   <li>{@code launch-pdv -> append-downstream-provenance} (data, if planned). PDV's version and
 *       JAR checksum are recorded whenever it is launched from a run.
 *   <li>{@code convert-limelight -> append-downstream-provenance} (data, if planned). The
 *       conversion is recorded.
 *   <li>{@code upload-limelight -> append-downstream-provenance} (data, if planned). The upload is
 *       recorded.
 * </ol>
 *
 * <p>Edges are kept to what a step actually needs; an edge implied by a chain of others is not
 * repeated unless the step reads the upstream step's output directly. Percolator's resolution
 * deliberately does not wait for Comet: the specification lets installation overlap other work.
 *
 * <h2>What a declaration must satisfy</h2>
 *
 * <p>{@link #declare(Set, List)} refuses a self edge, an edge naming a step the graph does not
 * declare, the same edge twice, and a cycle -- each with a message naming the steps involved. The
 * canonical graph goes through the same checks, so it cannot be malformed and load.
 */
public final class StepGraph {

    private static final StepGraph CANONICAL =
            declare(
                    EnumSet.allOf(EngineStep.class),
                    List.of(
                            requires(EngineStep.VALIDATE_CONFIGURATION, EngineStep.RESOLVE_COMET),
                            requires(
                                    EngineStep.VALIDATE_CONFIGURATION,
                                    EngineStep.RESOLVE_PERCOLATOR),
                            requires(
                                    EngineStep.VALIDATE_CONFIGURATION,
                                    EngineStep.SERIALISE_COMET_PARAMS),
                            requires(EngineStep.VALIDATE_CONFIGURATION, EngineStep.HASH_INPUTS),
                            requires(
                                    EngineStep.SERIALISE_COMET_PARAMS,
                                    EngineStep.BUILD_COMET_INDEX),
                            requires(EngineStep.RESOLVE_COMET, EngineStep.BUILD_COMET_INDEX),
                            requires(EngineStep.HASH_INPUTS, EngineStep.BUILD_COMET_INDEX),
                            requires(EngineStep.SERIALISE_COMET_PARAMS, EngineStep.RUN_COMET),
                            requires(EngineStep.RESOLVE_COMET, EngineStep.RUN_COMET),
                            requires(EngineStep.HASH_INPUTS, EngineStep.RUN_COMET),
                            ifPlanned(EngineStep.BUILD_COMET_INDEX, EngineStep.RUN_COMET),
                            requires(EngineStep.RUN_COMET, EngineStep.VALIDATE_COMET_OUTPUTS),
                            requires(EngineStep.VALIDATE_COMET_OUTPUTS, EngineStep.MERGE_PIN),
                            requires(EngineStep.MERGE_PIN, EngineStep.RUN_PERCOLATOR),
                            requires(EngineStep.RESOLVE_PERCOLATOR, EngineStep.RUN_PERCOLATOR),
                            requires(EngineStep.RUN_PERCOLATOR, EngineStep.PARSE_PERCOLATOR),
                            requires(EngineStep.PARSE_PERCOLATOR, EngineStep.FINALISE_RESULTS),
                            requires(EngineStep.MERGE_PIN, EngineStep.FINALISE_PROVENANCE),
                            ifPlanned(EngineStep.FINALISE_RESULTS, EngineStep.FINALISE_PROVENANCE),
                            requires(EngineStep.FINALISE_RESULTS, EngineStep.LAUNCH_PDV),
                            requires(EngineStep.PARSE_PERCOLATOR, EngineStep.CONVERT_LIMELIGHT),
                            requires(EngineStep.CONVERT_LIMELIGHT, EngineStep.UPLOAD_LIMELIGHT),
                            requires(
                                    EngineStep.FINALISE_PROVENANCE,
                                    EngineStep.APPEND_DOWNSTREAM_PROVENANCE),
                            ifPlanned(
                                    EngineStep.LAUNCH_PDV, EngineStep.APPEND_DOWNSTREAM_PROVENANCE),
                            ifPlanned(
                                    EngineStep.CONVERT_LIMELIGHT,
                                    EngineStep.APPEND_DOWNSTREAM_PROVENANCE),
                            ifPlanned(
                                    EngineStep.UPLOAD_LIMELIGHT,
                                    EngineStep.APPEND_DOWNSTREAM_PROVENANCE)));

    private final Set<EngineStep> steps;

    private final List<StepEdge> edges;

    private final List<EngineStep> topologicalOrder;

    private StepGraph(Set<EngineStep> steps, List<StepEdge> edges, List<EngineStep> order) {
        this.steps = steps;
        this.edges = edges;
        this.topologicalOrder = order;
    }

    private static StepEdge requires(EngineStep upstream, EngineStep downstream) {
        return new StepEdge(upstream, downstream, true);
    }

    private static StepEdge ifPlanned(EngineStep upstream, EngineStep downstream) {
        return new StepEdge(upstream, downstream, false);
    }

    /**
     * The engine's one declared graph: all seventeen steps and the edges listed on this type.
     *
     * @return the canonical graph, never {@code null}
     */
    public static StepGraph canonical() {
        return CANONICAL;
    }

    /**
     * Declares a graph, refusing anything that is not a well-formed acyclic one.
     *
     * @param steps the steps the graph contains
     * @param edges its edges, in declaration order
     * @return the validated graph
     * @throws NullPointerException if either argument or an element of either is {@code null}
     * @throws IllegalArgumentException for a self edge, an edge naming an undeclared step, two
     *     edges between the same two steps in the same direction (whether or not they agree on
     *     being required), or a cycle; the message names the steps
     */
    public static StepGraph declare(Set<EngineStep> steps, List<StepEdge> edges) {
        Objects.requireNonNull(steps, "steps");
        Objects.requireNonNull(edges, "edges");
        Set<EngineStep> declared = EnumSet.noneOf(EngineStep.class);
        for (EngineStep step : steps) {
            declared.add(Objects.requireNonNull(step, "steps contains null"));
        }
        Set<List<EngineStep>> seen = new HashSet<>();
        for (StepEdge edge : edges) {
            Objects.requireNonNull(edge, "edges contains null");
            checkEdge(declared, edge);
            if (!seen.add(List.of(edge.upstream(), edge.downstream()))) {
                throw new IllegalArgumentException("edge " + edge + " is declared twice");
            }
        }
        List<StepEdge> edgeList = List.copyOf(edges);
        return new StepGraph(
                Collections.unmodifiableSet(declared),
                edgeList,
                topologicalOrder(declared, edgeList));
    }

    private static void checkEdge(Set<EngineStep> declared, StepEdge edge) {
        if (edge.upstream() == edge.downstream()) {
            throw new IllegalArgumentException(
                    "step " + edge.upstream().id() + " cannot depend on itself");
        }
        for (EngineStep end : List.of(edge.upstream(), edge.downstream())) {
            if (!declared.contains(end)) {
                throw new IllegalArgumentException(
                        "edge "
                                + edge
                                + " names "
                                + end.id()
                                + ", which is not a step declared in this graph");
            }
        }
    }

    /**
     * Orders the steps so that every edge points forward, preferring the lower-numbered step
     * whenever several are ready -- so the order is deterministic, and for the canonical graph it
     * is the specification's own numbering.
     */
    private static List<EngineStep> topologicalOrder(Set<EngineStep> steps, List<StepEdge> edges) {
        List<EngineStep> order = new ArrayList<>();
        Set<EngineStep> placed = EnumSet.noneOf(EngineStep.class);
        while (order.size() < steps.size()) {
            EngineStep next = firstReady(steps, edges, placed);
            if (next == null) {
                throw new IllegalArgumentException(
                        "the declared steps form a cycle: " + describeCycle(steps, edges, placed));
            }
            order.add(next);
            placed.add(next);
        }
        return List.copyOf(order);
    }

    private static EngineStep firstReady(
            Set<EngineStep> steps, List<StepEdge> edges, Set<EngineStep> placed) {
        for (EngineStep step : steps) {
            if (!placed.contains(step) && allUpstreamPlaced(step, edges, placed)) {
                return step;
            }
        }
        return null;
    }

    private static boolean allUpstreamPlaced(
            EngineStep step, List<StepEdge> edges, Set<EngineStep> placed) {
        for (StepEdge edge : edges) {
            if (edge.downstream() == step && !placed.contains(edge.upstream())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Every unplaced step has an unplaced upstream step, or the ordering would not have stalled; so
     * walking upstream from any of them must revisit a step, and the walk from that step's first
     * visit is a cycle. It is written in dependency order, upstream first, closed on its first
     * step.
     */
    private static String describeCycle(
            Set<EngineStep> steps, List<StepEdge> edges, Set<EngineStep> placed) {
        List<EngineStep> walk = new ArrayList<>();
        EngineStep current = firstUnplaced(steps, placed);
        while (!walk.contains(current)) {
            walk.add(current);
            current = firstUnplacedUpstream(current, edges, placed);
        }
        List<EngineStep> cycle = new ArrayList<>(walk.subList(walk.indexOf(current), walk.size()));
        Collections.reverse(cycle);
        cycle.add(cycle.get(0));
        List<String> ids = new ArrayList<>();
        for (EngineStep step : cycle) {
            ids.add(step.id());
        }
        return String.join(" -> ", ids);
    }

    private static EngineStep firstUnplaced(Set<EngineStep> steps, Set<EngineStep> placed) {
        for (EngineStep step : steps) {
            if (!placed.contains(step)) {
                return step;
            }
        }
        throw new IllegalStateException("no unplaced step");
    }

    private static EngineStep firstUnplacedUpstream(
            EngineStep step, List<StepEdge> edges, Set<EngineStep> placed) {
        Set<EngineStep> unplaced = EnumSet.noneOf(EngineStep.class);
        for (StepEdge edge : edges) {
            if (edge.downstream() == step && !placed.contains(edge.upstream())) {
                unplaced.add(edge.upstream());
            }
        }
        return unplaced.iterator().next();
    }

    /**
     * The steps this graph declares.
     *
     * @return an immutable set, iterating in {@link EngineStep} order
     */
    public Set<EngineStep> steps() {
        return Collections.unmodifiableSet(steps);
    }

    /**
     * The edges, in declaration order.
     *
     * @return an immutable list
     */
    public List<StepEdge> edges() {
        return List.copyOf(edges);
    }

    /**
     * Every step, ordered so that each edge points forward; ties go to the lower-numbered step.
     *
     * @return an immutable list of every declared step
     */
    public List<EngineStep> topologicalOrder() {
        return List.copyOf(topologicalOrder);
    }

    /**
     * The edges whose downstream end is {@code step}, in declaration order.
     *
     * @param step a step of this graph
     * @return an immutable list, empty for a step with no upstream steps
     * @throws IllegalArgumentException if {@code step} is not declared in this graph
     */
    public List<StepEdge> edgesInto(EngineStep step) {
        requireDeclared(step);
        List<StepEdge> into = new ArrayList<>();
        for (StepEdge edge : edges) {
            if (edge.downstream() == step) {
                into.add(edge);
            }
        }
        return List.copyOf(into);
    }

    /**
     * The edges whose upstream end is {@code step}, in declaration order.
     *
     * @param step a step of this graph
     * @return an immutable list, empty for a step nothing depends on
     * @throws IllegalArgumentException if {@code step} is not declared in this graph
     */
    public List<StepEdge> edgesOutOf(EngineStep step) {
        requireDeclared(step);
        List<StepEdge> outOf = new ArrayList<>();
        for (StepEdge edge : edges) {
            if (edge.upstream() == step) {
                outOf.add(edge);
            }
        }
        return List.copyOf(outOf);
    }

    private void requireDeclared(EngineStep step) {
        Objects.requireNonNull(step, "step");
        if (!steps.contains(step)) {
            throw new IllegalArgumentException(
                    "step " + step.id() + " is not declared in this graph");
        }
    }
}
