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

import static org.cometgui.workflow.state.EngineStep.MERGE_PIN;
import static org.cometgui.workflow.state.EngineStep.RESOLVE_COMET;
import static org.cometgui.workflow.state.EngineStep.RUN_COMET;
import static org.cometgui.workflow.state.EngineStep.VALIDATE_COMET_OUTPUTS;
import static org.cometgui.workflow.state.EngineStep.VALIDATE_CONFIGURATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.cometgui.workflow.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link StepGraph}: the canonical edge list pinned by hand-typed literals, and the
 * refusal of every malformed declaration with a message naming the steps.
 */
class StepGraphTest {

    private static StepEdge edge(EngineStep upstream, EngineStep downstream) {
        return new StepEdge(upstream, downstream, true);
    }

    private static List<String> describe(List<StepEdge> edges) {
        List<String> rows = new ArrayList<>();
        for (StepEdge edge : edges) {
            rows.add(
                    edge.upstream().id()
                            + " -> "
                            + edge.downstream().id()
                            + (edge.required() ? " required" : " if-planned")
                            + (edge.isDataEdge() ? " data" : " ordering"));
        }
        return rows;
    }

    @Nested
    @DisplayName("the canonical graph")
    class Canonical {

        @Test
        @DisplayName("has exactly these twenty-six edges, in this order")
        void edges() {
            assertEquals(
                    List.of(
                            "validate-configuration -> resolve-comet required ordering",
                            "validate-configuration -> resolve-percolator required ordering",
                            "validate-configuration -> serialise-comet-params required ordering",
                            "validate-configuration -> hash-inputs required ordering",
                            "serialise-comet-params -> build-comet-index required data",
                            "resolve-comet -> build-comet-index required ordering",
                            "hash-inputs -> build-comet-index required ordering",
                            "serialise-comet-params -> run-comet required data",
                            "resolve-comet -> run-comet required ordering",
                            "hash-inputs -> run-comet required ordering",
                            "build-comet-index -> run-comet if-planned data",
                            "run-comet -> validate-comet-outputs required data",
                            "validate-comet-outputs -> merge-pin required data",
                            "merge-pin -> run-percolator required data",
                            "resolve-percolator -> run-percolator required ordering",
                            "run-percolator -> parse-percolator required data",
                            "parse-percolator -> finalise-results required data",
                            "merge-pin -> finalise-provenance required data",
                            "finalise-results -> finalise-provenance if-planned data",
                            "finalise-results -> launch-pdv required data",
                            "parse-percolator -> convert-limelight required data",
                            "convert-limelight -> upload-limelight required data",
                            "finalise-provenance -> append-downstream-provenance required data",
                            "launch-pdv -> append-downstream-provenance if-planned data",
                            "convert-limelight -> append-downstream-provenance if-planned data",
                            "upload-limelight -> append-downstream-provenance if-planned data"),
                    describe(StepGraph.canonical().edges()));
        }

        @Test
        @DisplayName("declares all seventeen steps")
        void steps() {
            assertEquals(EnumSet.allOf(EngineStep.class), StepGraph.canonical().steps());
        }

        @Test
        @DisplayName("orders its steps in the specification's numbering")
        void topologicalOrder() {
            assertEquals(
                    Arrays.asList(EngineStep.values()), StepGraph.canonical().topologicalOrder());
        }

        @Test
        @DisplayName("is one object, declared once")
        void declaredOnce() {
            assertSame(StepGraph.canonical(), StepGraph.canonical());
        }

        @Test
        @DisplayName("hashing has no edge to or from either tool resolution")
        void hashingRunsConcurrentlyWithInstallation() {
            for (StepEdge edge : StepGraph.canonical().edges()) {
                Set<EngineStep> ends = EnumSet.of(edge.upstream(), edge.downstream());
                assertEquals(
                        false,
                        ends.contains(EngineStep.HASH_INPUTS)
                                && (ends.contains(RESOLVE_COMET)
                                        || ends.contains(EngineStep.RESOLVE_PERCOLATOR)),
                        edge.toString());
            }
        }

        @Test
        @DisplayName("no required edge pulls an optional step into a non-optional one")
        void optionalStepsAreNeverRequiredByCoreSteps() {
            for (StepEdge edge : StepGraph.canonical().edges()) {
                if (edge.required() && edge.upstream().isOptional()) {
                    assertEquals(true, edge.downstream().isOptional(), edge.toString());
                }
            }
        }

        @Test
        @DisplayName("edges into and out of a step, in declaration order")
        void edgesIntoAndOutOf() {
            assertEquals(
                    List.of(
                            "serialise-comet-params -> run-comet required data",
                            "resolve-comet -> run-comet required ordering",
                            "hash-inputs -> run-comet required ordering",
                            "build-comet-index -> run-comet if-planned data"),
                    describe(StepGraph.canonical().edgesInto(RUN_COMET)));
            assertEquals(
                    List.of("run-comet -> validate-comet-outputs required data"),
                    describe(StepGraph.canonical().edgesOutOf(RUN_COMET)));
            assertEquals(List.of(), StepGraph.canonical().edgesInto(VALIDATE_CONFIGURATION));
            assertEquals(
                    List.of(),
                    StepGraph.canonical().edgesOutOf(EngineStep.APPEND_DOWNSTREAM_PROVENANCE));
        }
    }

    @Nested
    @DisplayName("a declared graph")
    class Declared {

        @Test
        @DisplayName("is ordered by its edges, not by step number")
        void orderFollowsEdges() {
            StepGraph graph =
                    StepGraph.declare(
                            EnumSet.of(VALIDATE_CONFIGURATION, RUN_COMET, MERGE_PIN),
                            List.of(
                                    edge(MERGE_PIN, VALIDATE_CONFIGURATION),
                                    edge(RUN_COMET, MERGE_PIN)));

            assertEquals(
                    List.of(RUN_COMET, MERGE_PIN, VALIDATE_CONFIGURATION),
                    graph.topologicalOrder());
        }

        @Test
        @DisplayName("breaks ties by step number")
        void tiesGoToTheLowerNumber() {
            StepGraph graph =
                    StepGraph.declare(
                            EnumSet.of(MERGE_PIN, RESOLVE_COMET, RUN_COMET),
                            List.of(edge(RESOLVE_COMET, MERGE_PIN)));

            assertEquals(List.of(RESOLVE_COMET, RUN_COMET, MERGE_PIN), graph.topologicalOrder());
            assertEquals(List.of(edge(RESOLVE_COMET, MERGE_PIN)), graph.edges());
            assertEquals(EnumSet.of(MERGE_PIN, RESOLVE_COMET, RUN_COMET), graph.steps());
        }

        @Test
        @DisplayName("may be empty")
        void empty() {
            StepGraph graph = StepGraph.declare(Set.of(), List.of());

            assertEquals(List.of(), graph.topologicalOrder());
        }

        @Test
        @DisplayName("refuses a question about a step it does not declare")
        void undeclaredQuery() {
            StepGraph graph = StepGraph.declare(EnumSet.of(RUN_COMET), List.of());

            assertEquals(
                    "step merge-pin is not declared in this graph",
                    assertThrows(IllegalArgumentException.class, () -> graph.edgesInto(MERGE_PIN))
                            .getMessage());
            assertEquals(
                    "step merge-pin is not declared in this graph",
                    assertThrows(IllegalArgumentException.class, () -> graph.edgesOutOf(MERGE_PIN))
                            .getMessage());
            assertEquals(
                    "step",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> graph.edgesInto(Nulls.of(EngineStep.class)))
                            .getMessage());
        }
    }

    @Nested
    @DisplayName("a malformed declaration is refused, naming the steps")
    class Refusals {

        private IllegalArgumentException refused(Set<EngineStep> steps, List<StepEdge> edges) {
            return assertThrows(
                    IllegalArgumentException.class, () -> StepGraph.declare(steps, edges));
        }

        @Test
        @DisplayName("a self edge")
        void selfEdge() {
            assertEquals(
                    "step run-comet cannot depend on itself",
                    refused(EnumSet.of(RUN_COMET), List.of(edge(RUN_COMET, RUN_COMET)))
                            .getMessage());
        }

        @Test
        @DisplayName("an edge to an undeclared step")
        void undeclaredDownstream() {
            assertEquals(
                    "edge run-comet -> validate-comet-outputs names validate-comet-outputs, which"
                            + " is not a step declared in this graph",
                    refused(
                                    EnumSet.of(RUN_COMET, MERGE_PIN),
                                    List.of(edge(RUN_COMET, VALIDATE_COMET_OUTPUTS)))
                            .getMessage());
        }

        @Test
        @DisplayName("an edge from an undeclared step")
        void undeclaredUpstream() {
            assertEquals(
                    "edge resolve-comet -> run-comet (if planned) names resolve-comet, which is"
                            + " not a step declared in this graph",
                    refused(
                                    EnumSet.of(RUN_COMET),
                                    List.of(new StepEdge(RESOLVE_COMET, RUN_COMET, false)))
                            .getMessage());
        }

        @Test
        @DisplayName("the same edge twice, even disagreeing about being required")
        void duplicateEdge() {
            assertEquals(
                    "edge run-comet -> merge-pin (if planned) is declared twice",
                    refused(
                                    EnumSet.of(RUN_COMET, MERGE_PIN),
                                    List.of(
                                            edge(RUN_COMET, MERGE_PIN),
                                            new StepEdge(RUN_COMET, MERGE_PIN, false)))
                            .getMessage());
        }

        @Test
        @DisplayName("a two-step cycle")
        void twoStepCycle() {
            assertEquals(
                    "the declared steps form a cycle: merge-pin -> run-comet -> merge-pin",
                    refused(
                                    EnumSet.of(RUN_COMET, MERGE_PIN),
                                    List.of(edge(RUN_COMET, MERGE_PIN), edge(MERGE_PIN, RUN_COMET)))
                            .getMessage());
        }

        @Test
        @DisplayName("a three-step cycle behind an acyclic step")
        void threeStepCycle() {
            assertEquals(
                    "the declared steps form a cycle: validate-comet-outputs -> merge-pin ->"
                            + " run-comet -> validate-comet-outputs",
                    refused(
                                    EnumSet.of(
                                            VALIDATE_CONFIGURATION,
                                            RUN_COMET,
                                            VALIDATE_COMET_OUTPUTS,
                                            MERGE_PIN),
                                    List.of(
                                            edge(VALIDATE_CONFIGURATION, RUN_COMET),
                                            edge(RUN_COMET, VALIDATE_COMET_OUTPUTS),
                                            edge(VALIDATE_COMET_OUTPUTS, MERGE_PIN),
                                            edge(MERGE_PIN, RUN_COMET)))
                            .getMessage());
        }

        @Test
        @DisplayName("a cycle reached from a step that is not on it names only the cycle")
        void cycleNotThroughTheFirstStep() {
            assertEquals(
                    "the declared steps form a cycle: merge-pin -> run-comet -> merge-pin",
                    refused(
                                    EnumSet.of(RESOLVE_COMET, RUN_COMET, MERGE_PIN),
                                    List.of(
                                            edge(RUN_COMET, RESOLVE_COMET),
                                            edge(RUN_COMET, MERGE_PIN),
                                            edge(MERGE_PIN, RUN_COMET)))
                            .getMessage());
        }

        @Test
        @DisplayName("of two cycles, the walk follows the lower-numbered upstream step")
        void walkFollowsTheLowerNumberedUpstream() {
            assertEquals(
                    "the declared steps form a cycle: validate-comet-outputs -> run-comet ->"
                            + " validate-comet-outputs",
                    refused(
                                    EnumSet.of(RUN_COMET, VALIDATE_COMET_OUTPUTS, MERGE_PIN),
                                    List.of(
                                            edge(MERGE_PIN, RUN_COMET),
                                            edge(VALIDATE_COMET_OUTPUTS, RUN_COMET),
                                            edge(RUN_COMET, VALIDATE_COMET_OUTPUTS),
                                            edge(RUN_COMET, MERGE_PIN)))
                            .getMessage());
        }

        @Test
        @DisplayName("null arguments and null elements")
        void nulls() {
            @SuppressWarnings("unchecked")
            Set<EngineStep> noSteps = Nulls.of(Set.class);
            @SuppressWarnings("unchecked")
            List<StepEdge> noEdges = Nulls.of(List.class);
            Set<EngineStep> nullStep = new HashSet<>();
            nullStep.add(null);
            List<StepEdge> nullEdge = new ArrayList<>();
            nullEdge.add(null);

            assertEquals(
                    "steps",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> StepGraph.declare(noSteps, List.of()))
                            .getMessage());
            assertEquals(
                    "edges",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> StepGraph.declare(Set.of(), noEdges))
                            .getMessage());
            assertEquals(
                    "steps contains null",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> StepGraph.declare(nullStep, List.of()))
                            .getMessage());
            assertEquals(
                    "edges contains null",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> StepGraph.declare(Set.of(), nullEdge))
                            .getMessage());
        }
    }

    @Test
    @DisplayName("an edge names both ends, and refuses a null one")
    void stepEdge() {
        assertEquals("run-comet -> merge-pin", edge(RUN_COMET, MERGE_PIN).toString());
        assertEquals(
                "run-comet -> merge-pin (if planned)",
                new StepEdge(RUN_COMET, MERGE_PIN, false).toString());
        assertEquals(
                "upstream",
                assertThrows(
                                NullPointerException.class,
                                () -> new StepEdge(Nulls.of(EngineStep.class), RUN_COMET, true))
                        .getMessage());
        assertEquals(
                "downstream",
                assertThrows(
                                NullPointerException.class,
                                () -> new StepEdge(RUN_COMET, Nulls.of(EngineStep.class), true))
                        .getMessage());
    }
}
