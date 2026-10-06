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

import static org.cometgui.workflow.state.EngineStep.APPEND_DOWNSTREAM_PROVENANCE;
import static org.cometgui.workflow.state.EngineStep.BUILD_COMET_INDEX;
import static org.cometgui.workflow.state.EngineStep.CONVERT_LIMELIGHT;
import static org.cometgui.workflow.state.EngineStep.FINALISE_PROVENANCE;
import static org.cometgui.workflow.state.EngineStep.FINALISE_RESULTS;
import static org.cometgui.workflow.state.EngineStep.HASH_INPUTS;
import static org.cometgui.workflow.state.EngineStep.LAUNCH_PDV;
import static org.cometgui.workflow.state.EngineStep.MERGE_PIN;
import static org.cometgui.workflow.state.EngineStep.PARSE_PERCOLATOR;
import static org.cometgui.workflow.state.EngineStep.RESOLVE_COMET;
import static org.cometgui.workflow.state.EngineStep.RESOLVE_PERCOLATOR;
import static org.cometgui.workflow.state.EngineStep.RUN_COMET;
import static org.cometgui.workflow.state.EngineStep.RUN_PERCOLATOR;
import static org.cometgui.workflow.state.EngineStep.SERIALISE_COMET_PARAMS;
import static org.cometgui.workflow.state.EngineStep.UPLOAD_LIMELIGHT;
import static org.cometgui.workflow.state.EngineStep.VALIDATE_COMET_OUTPUTS;
import static org.cometgui.workflow.state.EngineStep.VALIDATE_CONFIGURATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.cometgui.workflow.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link Plan}: which steps a run plans, with hand-typed expected lists. */
class PlanTest {

    @Test
    @DisplayName("phase 08's plan runs to merging the PINs and finalising provenance, no further")
    void phase08Plan() {
        Plan plan = Scenario.phase08();

        assertEquals(
                List.of(
                        VALIDATE_CONFIGURATION,
                        RESOLVE_COMET,
                        SERIALISE_COMET_PARAMS,
                        HASH_INPUTS,
                        RUN_COMET,
                        VALIDATE_COMET_OUTPUTS,
                        MERGE_PIN,
                        FINALISE_PROVENANCE),
                plan.steps());
        assertFalse(plan.contains(RESOLVE_PERCOLATOR));
        assertFalse(plan.contains(RUN_PERCOLATOR));
        assertFalse(plan.contains(BUILD_COMET_INDEX), "the index is planned only when wanted");
        assertTrue(plan.contains(MERGE_PIN));
        assertEquals(
                "Plan[validate-configuration, resolve-comet, serialise-comet-params,"
                        + " hash-inputs, run-comet, validate-comet-outputs, merge-pin,"
                        + " finalise-provenance]",
                plan.toString());
    }

    @Test
    @DisplayName("an index mode adds the index build, before Comet")
    void withIndex() {
        Plan plan = Plan.covering(EnumSet.of(FINALISE_PROVENANCE, BUILD_COMET_INDEX));

        assertEquals(
                List.of(
                        VALIDATE_CONFIGURATION,
                        RESOLVE_COMET,
                        SERIALISE_COMET_PARAMS,
                        HASH_INPUTS,
                        BUILD_COMET_INDEX,
                        RUN_COMET,
                        VALIDATE_COMET_OUTPUTS,
                        MERGE_PIN,
                        FINALISE_PROVENANCE),
                plan.steps());
        assertEquals(
                List.of(RESOLVE_COMET, SERIALISE_COMET_PARAMS, HASH_INPUTS, BUILD_COMET_INDEX),
                plan.upstreamOf(RUN_COMET));
    }

    @Test
    @DisplayName("asking for PDV pulls in Percolator and the results it views")
    void pdvPullsInItsRequirements() {
        Plan plan = Plan.covering(EnumSet.of(LAUNCH_PDV));

        assertEquals(
                List.of(
                        VALIDATE_CONFIGURATION,
                        RESOLVE_COMET,
                        RESOLVE_PERCOLATOR,
                        SERIALISE_COMET_PARAMS,
                        HASH_INPUTS,
                        RUN_COMET,
                        VALIDATE_COMET_OUTPUTS,
                        MERGE_PIN,
                        RUN_PERCOLATOR,
                        PARSE_PERCOLATOR,
                        FINALISE_RESULTS,
                        LAUNCH_PDV),
                plan.steps());
    }

    @Test
    @DisplayName("asking for the upload pulls in the conversion it uploads")
    void uploadPullsInConversion() {
        Plan plan = Plan.covering(EnumSet.of(UPLOAD_LIMELIGHT));

        assertTrue(plan.contains(CONVERT_LIMELIGHT));
        assertFalse(plan.contains(FINALISE_RESULTS));
        assertFalse(plan.contains(LAUNCH_PDV));
    }

    @Test
    @DisplayName("a non-required edge orders two planned steps but pulls in neither")
    void nonRequiredEdges() {
        Plan full = Scenario.full();

        assertEquals(List.of(MERGE_PIN, FINALISE_RESULTS), full.upstreamOf(FINALISE_PROVENANCE));
        assertEquals(List.of(MERGE_PIN), Scenario.phase08().upstreamOf(FINALISE_PROVENANCE));
        assertEquals(
                List.of(FINALISE_PROVENANCE, LAUNCH_PDV, CONVERT_LIMELIGHT, UPLOAD_LIMELIGHT),
                full.upstreamOf(APPEND_DOWNSTREAM_PROVENANCE));
    }

    @Test
    @DisplayName("downstream steps within the plan only")
    void downstreamWithinThePlan() {
        assertEquals(
                List.of(RUN_PERCOLATOR, FINALISE_PROVENANCE),
                Scenario.full().downstreamOf(MERGE_PIN));
        assertEquals(List.of(FINALISE_PROVENANCE), Scenario.phase08().downstreamOf(MERGE_PIN));
        assertEquals(
                List.of(RESOLVE_COMET, SERIALISE_COMET_PARAMS, HASH_INPUTS),
                Scenario.phase08().downstreamOf(VALIDATE_CONFIGURATION));
    }

    @Test
    @DisplayName("the planned steps under each stage")
    void stepsInAStage() {
        Plan plan = Scenario.phase08();

        assertEquals(
                List.of(
                        RESOLVE_COMET,
                        SERIALISE_COMET_PARAMS,
                        RUN_COMET,
                        VALIDATE_COMET_OUTPUTS,
                        MERGE_PIN),
                plan.stepsIn(WorkflowStage.COMET));
        assertEquals(List.of(HASH_INPUTS), plan.stepsIn(WorkflowStage.INPUTS));
        assertEquals(List.of(), plan.stepsIn(WorkflowStage.PERCOLATOR));
        assertEquals(List.of(FINALISE_PROVENANCE), plan.stepsIn(WorkflowStage.RESULTS));
    }

    @Test
    @DisplayName("the inputs a plan needs are its steps' declared inputs and no others")
    void inputs() {
        assertEquals(
                List.of(
                        InputKind.SPECTRUM_FILES,
                        InputKind.FASTA,
                        InputKind.COMET_PARAMETERS,
                        InputKind.COMET_INDEX_MODE,
                        InputKind.COMET_TOOL),
                new ArrayList<>(Scenario.phase08().inputs()));
        assertEquals(
                Set.of(
                        InputKind.SPECTRUM_FILES,
                        InputKind.FASTA,
                        InputKind.COMET_PARAMETERS,
                        InputKind.COMET_INDEX_MODE,
                        InputKind.COMET_TOOL,
                        InputKind.PERCOLATOR_SETTINGS,
                        InputKind.PERCOLATOR_TOOL,
                        InputKind.PDV_TOOL,
                        InputKind.LIMELIGHT_Q_CUTOFF,
                        InputKind.LIMELIGHT_CONVERTER_OPTIONS,
                        InputKind.LIMELIGHT_CONVERTER_TOOL,
                        InputKind.LIMELIGHT_UPLOAD_TARGET),
                Scenario.full().inputs());
    }

    @Test
    @DisplayName("a plan over a declared graph keeps that graph")
    void overADeclaredGraph() {
        StepGraph graph =
                StepGraph.declare(
                        EnumSet.of(RUN_COMET, MERGE_PIN),
                        List.of(new StepEdge(RUN_COMET, MERGE_PIN, false)));

        Plan plan = Plan.covering(graph, EnumSet.of(MERGE_PIN));

        assertSame(graph, plan.graph());
        assertEquals(List.of(MERGE_PIN), plan.steps());
        assertSame(StepGraph.canonical(), Scenario.phase08().graph());
    }

    @Test
    @DisplayName("refuses an empty want, an undeclared step and questions about unplanned steps")
    void refusals() {
        StepGraph graph = StepGraph.declare(EnumSet.of(RUN_COMET), List.of());

        assertEquals(
                "a plan must want at least one step",
                assertThrows(IllegalArgumentException.class, () -> Plan.covering(Set.of()))
                        .getMessage());
        assertEquals(
                "step merge-pin is wanted but is not declared in the graph",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> Plan.covering(graph, EnumSet.of(MERGE_PIN)))
                        .getMessage());
        assertEquals(
                "step run-percolator is not in this plan",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> Scenario.phase08().upstreamOf(RUN_PERCOLATOR))
                        .getMessage());
        assertEquals(
                "step run-percolator is not in this plan",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> Scenario.phase08().downstreamOf(RUN_PERCOLATOR))
                        .getMessage());
    }

    @Test
    @DisplayName("refuses nulls, naming them")
    void nulls() {
        @SuppressWarnings("unchecked")
        Set<EngineStep> noWanted = Nulls.of(Set.class);
        Set<EngineStep> nullWanted = new HashSet<>();
        nullWanted.add(null);

        assertEquals(
                "wanted",
                assertThrows(NullPointerException.class, () -> Plan.covering(noWanted))
                        .getMessage());
        assertEquals(
                "graph",
                assertThrows(
                                NullPointerException.class,
                                () ->
                                        Plan.covering(
                                                Nulls.of(StepGraph.class), EnumSet.of(MERGE_PIN)))
                        .getMessage());
        assertEquals(
                "wanted contains null",
                assertThrows(NullPointerException.class, () -> Plan.covering(nullWanted))
                        .getMessage());
        assertEquals(
                "step",
                assertThrows(
                                NullPointerException.class,
                                () -> Scenario.phase08().upstreamOf(Nulls.of(EngineStep.class)))
                        .getMessage());
        assertEquals(
                "stage",
                assertThrows(
                                NullPointerException.class,
                                () -> Scenario.phase08().stepsIn(Nulls.of(WorkflowStage.class)))
                        .getMessage());
    }
}
