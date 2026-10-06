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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Tests for {@link EngineStep}: the seventeen declared steps, every property pinned by hand-typed
 * literals in the specification's order.
 */
class EngineStepTest {

    @Test
    @DisplayName("the seventeen steps, in the specification's order, with stable identifiers")
    void identifiers() {
        List<String> ids = new ArrayList<>();
        for (EngineStep step : EngineStep.values()) {
            ids.add(step.id());
        }
        assertEquals(
                List.of(
                        "validate-configuration",
                        "resolve-comet",
                        "resolve-percolator",
                        "serialise-comet-params",
                        "hash-inputs",
                        "build-comet-index",
                        "run-comet",
                        "validate-comet-outputs",
                        "merge-pin",
                        "run-percolator",
                        "parse-percolator",
                        "finalise-results",
                        "finalise-provenance",
                        "launch-pdv",
                        "convert-limelight",
                        "upload-limelight",
                        "append-downstream-provenance"),
                ids);
    }

    @Test
    @DisplayName("the display names are the specification's wording")
    void displayNames() {
        List<String> names = new ArrayList<>();
        for (EngineStep step : EngineStep.values()) {
            names.add(step.displayName());
        }
        assertEquals(
                List.of(
                        "Validate project inputs and configuration",
                        "Resolve, install and probe Comet",
                        "Resolve, install and probe Percolator",
                        "Serialise the canonical Comet parameter file",
                        "Hash immutable inputs",
                        "Build or reuse the Comet index",
                        "Run Comet once per spectrum file",
                        "Validate Comet pepXML and PIN outputs per file",
                        "Merge PIN files",
                        "Run Percolator",
                        "Validate and parse Percolator outputs and learned weights",
                        "Finalise core result indexes and summaries",
                        "Hash outputs and finalise core provenance",
                        "Launch or use PDV",
                        "Run the Limelight converter",
                        "Upload to Limelight",
                        "Append downstream provenance events and refresh the report"),
                names);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(EngineStep.class)
    @DisplayName("every identifier is a valid process-service stage id with room for a suffix")
    void identifiersFitAStageId(EngineStep step) {
        assertTrue(
                Pattern.matches("[a-z0-9-]{1,60}", step.id()),
                step.id() + " must be lower-case-hyphenated and leave room for -<nn>");
        assertTrue(
                Pattern.matches("[A-Za-z0-9_-]{1,64}", step.id() + "-01"),
                step.id() + "-01 must be a valid process-service stage identifier");
    }

    @Test
    @DisplayName("each step maps onto exactly the stepper stage pinned here")
    void stageMapping() {
        List<WorkflowStage> stages = new ArrayList<>();
        for (EngineStep step : EngineStep.values()) {
            stages.add(step.stage());
        }
        assertEquals(
                List.of(
                        WorkflowStage.VALIDATE,
                        WorkflowStage.COMET,
                        WorkflowStage.PERCOLATOR,
                        WorkflowStage.COMET,
                        WorkflowStage.INPUTS,
                        WorkflowStage.COMET,
                        WorkflowStage.COMET,
                        WorkflowStage.COMET,
                        WorkflowStage.COMET,
                        WorkflowStage.PERCOLATOR,
                        WorkflowStage.PERCOLATOR,
                        WorkflowStage.RESULTS,
                        WorkflowStage.RESULTS,
                        WorkflowStage.PDV,
                        WorkflowStage.LIMELIGHT_XML,
                        WorkflowStage.LIMELIGHT_UPLOAD,
                        WorkflowStage.RESULTS),
                stages);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(WorkflowStage.class)
    @DisplayName("every stepper stage has at least one engine step")
    void everyStageHasAStep(WorkflowStage stage) {
        boolean found = false;
        for (EngineStep step : EngineStep.values()) {
            found |= step.stage() == stage;
        }
        assertTrue(found, "no engine step is drawn under " + stage.id());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(EngineStep.class)
    @DisplayName("a step is optional exactly when its stage is an optional downstream stage")
    void optionalAgreesWithTheStage(EngineStep step) {
        assertEquals(!step.stage().isCore(), step.isOptional(), step.id());
    }

    @Test
    @DisplayName("exactly PDV, the converter and the upload are optional")
    void optionalSteps() {
        Set<EngineStep> optional = EnumSet.noneOf(EngineStep.class);
        for (EngineStep step : EngineStep.values()) {
            if (step.isOptional()) {
                optional.add(step);
            }
        }
        assertEquals(
                Set.of(
                        EngineStep.LAUNCH_PDV,
                        EngineStep.CONVERT_LIMELIGHT,
                        EngineStep.UPLOAD_LIMELIGHT),
                optional);
    }

    @Test
    @DisplayName("exactly validation, the two resolutions and input hashing are preparation")
    void preparationSteps() {
        Set<EngineStep> preparation = EnumSet.noneOf(EngineStep.class);
        for (EngineStep step : EngineStep.values()) {
            if (step.kind() == StepKind.PREPARATION) {
                preparation.add(step);
            }
        }
        assertEquals(
                Set.of(
                        EngineStep.VALIDATE_CONFIGURATION,
                        EngineStep.RESOLVE_COMET,
                        EngineStep.RESOLVE_PERCOLATOR,
                        EngineStep.HASH_INPUTS),
                preparation);
    }

    @Test
    @DisplayName("the phase implementing each step is data, and phase 08 owns nine")
    void implementingPhases() {
        List<Integer> phases = new ArrayList<>();
        for (EngineStep step : EngineStep.values()) {
            phases.add(step.implementedInPhase());
        }
        assertEquals(List.of(8, 8, 9, 8, 8, 8, 8, 8, 8, 9, 9, 10, 8, 11, 12, 12, 11), phases);
    }

    @Test
    @DisplayName("each step declares exactly the inputs pinned here, in fingerprint order")
    void declaredInputs() {
        List<List<InputKind>> inputs = new ArrayList<>();
        for (EngineStep step : EngineStep.values()) {
            inputs.add(step.inputs());
        }
        assertEquals(
                List.of(
                        List.of(
                                InputKind.SPECTRUM_FILES,
                                InputKind.FASTA,
                                InputKind.COMET_PARAMETERS,
                                InputKind.COMET_INDEX_MODE),
                        List.of(InputKind.COMET_TOOL),
                        List.of(InputKind.PERCOLATOR_TOOL),
                        List.of(InputKind.COMET_PARAMETERS),
                        List.of(InputKind.SPECTRUM_FILES, InputKind.FASTA),
                        List.of(
                                InputKind.FASTA,
                                InputKind.COMET_PARAMETERS,
                                InputKind.COMET_TOOL,
                                InputKind.COMET_INDEX_MODE),
                        List.of(
                                InputKind.SPECTRUM_FILES,
                                InputKind.FASTA,
                                InputKind.COMET_PARAMETERS,
                                InputKind.COMET_INDEX_MODE,
                                InputKind.COMET_TOOL),
                        List.of(),
                        List.of(),
                        List.of(InputKind.PERCOLATOR_SETTINGS, InputKind.PERCOLATOR_TOOL),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(InputKind.PDV_TOOL),
                        List.of(
                                InputKind.LIMELIGHT_Q_CUTOFF,
                                InputKind.LIMELIGHT_CONVERTER_OPTIONS,
                                InputKind.LIMELIGHT_CONVERTER_TOOL),
                        List.of(InputKind.LIMELIGHT_UPLOAD_TARGET),
                        List.of()),
                inputs);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(EngineStep.class)
    @DisplayName("no step reads the PSM or peptide display filter")
    void noStepReadsADisplayFilter(EngineStep step) {
        assertTrue(
                !step.inputs().contains(InputKind.PSM_DISPLAY_FILTER)
                        && !step.inputs().contains(InputKind.PEPTIDE_DISPLAY_FILTER),
                step.id() + " must not read a display filter");
    }
}
