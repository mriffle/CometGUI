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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.cometgui.workflow.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link RerunPreview}: gate item 6 and AC-WF-04, "the rerun preview names exactly the
 * stages that will re-execute".
 *
 * <p>Every expected set below is typed by hand from the specification's <em>Stage reruns</em>
 * paragraph and the edges declared on {@link StepGraph}, never computed from the production graph.
 * Each scenario asserts all four sets -- re-executed, prepared, reused and executed -- so a preview
 * that drops a downstream step, adds an upstream one, or confuses reuse with preparation fails by
 * name.
 *
 * <h2>Where the expected sets go beyond the specification's words, and why</h2>
 *
 * <ul>
 *   <li><strong>Results, PDV and provenance re-execute after a Percolator change.</strong> The
 *       specification says "Percolator and downstream conversion". The result indexes are built
 *       from Percolator's output, PDV's mzTab is generated from those results ({@code D-005}), core
 *       provenance hashes those outputs, and step 17 records the new conversion. All of them are
 *       downstream of Percolator in the declared graph, so they are in the set.
 *   <li><strong>Step 17 re-executes after a q-cutoff change.</strong> The specification says "only
 *       conversion and upload"; the provenance of the new conversion still has to be appended. It
 *       is bookkeeping, not science, and it is named so the preview stays exact.
 *   <li><strong>Preparation steps execute in a rerun</strong> -- validation, and resolving the tool
 *       a re-executed step runs. They are listed separately ({@link RerunPreview#prepared()}),
 *       never among the re-executed results, and they never cause a result to re-execute.
 * </ul>
 */
class RerunPreviewTest {

    /** The twelve RESULT steps of {@link Scenario#full()}, typed out. */
    private static final Set<EngineStep> ALL_FULL_RESULTS =
            EnumSet.of(
                    SERIALISE_COMET_PARAMS,
                    RUN_COMET,
                    VALIDATE_COMET_OUTPUTS,
                    MERGE_PIN,
                    RUN_PERCOLATOR,
                    PARSE_PERCOLATOR,
                    FINALISE_RESULTS,
                    FINALISE_PROVENANCE,
                    LAUNCH_PDV,
                    CONVERT_LIMELIGHT,
                    UPLOAD_LIMELIGHT,
                    APPEND_DOWNSTREAM_PROVENANCE);

    private static RerunPreview previewOfFull(StepInputs current) {
        return RerunPreview.compute(
                Scenario.full(), current, Scenario.recorded(Scenario.full(), Scenario.baseline()));
    }

    private static void assertSets(
            RerunPreview preview,
            Set<EngineStep> reExecuted,
            Set<EngineStep> prepared,
            Set<EngineStep> reused) {
        assertEquals(reExecuted, preview.reExecuted(), "re-executed");
        assertEquals(prepared, preview.prepared(), "prepared");
        assertEquals(reused, preview.reused(), "reused");
        Set<EngineStep> executed = EnumSet.noneOf(EngineStep.class);
        executed.addAll(reExecuted);
        executed.addAll(prepared);
        assertEquals(executed, preview.executed(), "executed");
    }

    @Nested
    @DisplayName("the specification's stage-rerun scenarios")
    class StageReruns {

        @Test
        @DisplayName("(a) changing only the PSM and peptide display filters executes nothing")
        void displayFiltersExecuteNothing() {
            StepInputs current =
                    Scenario.baseline()
                            .with(
                                    InputKind.PSM_DISPLAY_FILTER,
                                    InputValue.decimal(new BigDecimal("0.05")))
                            .with(
                                    InputKind.PEPTIDE_DISPLAY_FILTER,
                                    InputValue.decimal(new BigDecimal("0.001")));

            RerunPreview preview = previewOfFull(current);

            assertSets(preview, Set.of(), Set.of(), ALL_FULL_RESULTS);
            assertEquals(
                    Set.of(),
                    preview.executed(),
                    "a display-filter change must run nothing at all");
            assertEquals(
                    RerunDecision.NOT_NEEDED, preview.verdict(VALIDATE_CONFIGURATION).decision());
        }

        @Test
        @DisplayName(
                "(b) changing Percolator parameters re-executes Percolator and downstream only")
        void percolatorParametersRerunPercolatorAndDownstream() {
            StepInputs current =
                    Scenario.baseline()
                            .with(InputKind.PERCOLATOR_SETTINGS, InputValue.text("test-fdr=0.05"));

            RerunPreview preview = previewOfFull(current);

            assertSets(
                    preview,
                    Set.of(
                            RUN_PERCOLATOR,
                            PARSE_PERCOLATOR,
                            FINALISE_RESULTS,
                            FINALISE_PROVENANCE,
                            LAUNCH_PDV,
                            CONVERT_LIMELIGHT,
                            UPLOAD_LIMELIGHT,
                            APPEND_DOWNSTREAM_PROVENANCE),
                    Set.of(VALIDATE_CONFIGURATION, RESOLVE_PERCOLATOR),
                    Set.of(SERIALISE_COMET_PARAMS, RUN_COMET, VALIDATE_COMET_OUTPUTS, MERGE_PIN));
            assertEquals(
                    List.of(new RerunReason.InputChanged(InputKind.PERCOLATOR_SETTINGS)),
                    preview.verdict(RUN_PERCOLATOR).reasons());
            assertEquals(
                    List.of(new RerunReason.PrerequisiteOf(RUN_PERCOLATOR)),
                    preview.verdict(RESOLVE_PERCOLATOR).reasons());
            assertEquals(
                    List.of(new RerunReason.PrerequisiteOf(RESOLVE_PERCOLATOR)),
                    preview.verdict(VALIDATE_CONFIGURATION).reasons());
        }

        @Test
        @DisplayName(
                "(c) an XML-capable Percolator for Limelight reruns Percolator from the merged PIN,"
                        + " then conversion; Comet and the merge are reused")
        void xmlCapablePercolatorRerunsFromThePreservedMergedPin() {
            Plan before = Plan.covering(EnumSet.of(FINALISE_RESULTS, FINALISE_PROVENANCE));
            Plan now =
                    Plan.covering(
                            EnumSet.of(
                                    FINALISE_RESULTS,
                                    FINALISE_PROVENANCE,
                                    CONVERT_LIMELIGHT,
                                    UPLOAD_LIMELIGHT,
                                    APPEND_DOWNSTREAM_PROVENANCE));
            StepInputs current =
                    Scenario.baseline()
                            .with(
                                    InputKind.PERCOLATOR_TOOL,
                                    InputValue.tool("3.07.1", Scenario.SHA_2));

            RerunPreview preview =
                    RerunPreview.compute(
                            now, current, Scenario.recorded(before, Scenario.baseline()));

            assertSets(
                    preview,
                    Set.of(
                            RUN_PERCOLATOR,
                            PARSE_PERCOLATOR,
                            FINALISE_RESULTS,
                            FINALISE_PROVENANCE,
                            CONVERT_LIMELIGHT,
                            UPLOAD_LIMELIGHT,
                            APPEND_DOWNSTREAM_PROVENANCE),
                    Set.of(VALIDATE_CONFIGURATION, RESOLVE_PERCOLATOR),
                    Set.of(SERIALISE_COMET_PARAMS, RUN_COMET, VALIDATE_COMET_OUTPUTS, MERGE_PIN));
            assertEquals(
                    List.of(new RerunReason.InputChanged(InputKind.PERCOLATOR_TOOL)),
                    preview.verdict(RUN_PERCOLATOR).reasons());
            assertEquals(
                    List.of(
                            new RerunReason.NotRecorded(),
                            new RerunReason.UpstreamReExecutes(PARSE_PERCOLATOR)),
                    preview.verdict(CONVERT_LIMELIGHT).reasons(),
                    "conversion never ran before: that is said, distinctly from a change");
        }

        @Test
        @DisplayName("(d) changing Comet parameters re-executes Comet and everything downstream")
        void cometParametersRerunEverything() {
            StepInputs current =
                    Scenario.baseline()
                            .with(
                                    InputKind.COMET_PARAMETERS,
                                    Scenario.cometParams(
                                            "# comet_version 2026.03 rev. 0\nnum_threads = 8\n"));

            RerunPreview preview = previewOfFull(current);

            assertSets(
                    preview,
                    Set.of(
                            SERIALISE_COMET_PARAMS,
                            RUN_COMET,
                            VALIDATE_COMET_OUTPUTS,
                            MERGE_PIN,
                            RUN_PERCOLATOR,
                            PARSE_PERCOLATOR,
                            FINALISE_RESULTS,
                            FINALISE_PROVENANCE,
                            LAUNCH_PDV,
                            CONVERT_LIMELIGHT,
                            UPLOAD_LIMELIGHT,
                            APPEND_DOWNSTREAM_PROVENANCE),
                    Set.of(VALIDATE_CONFIGURATION, RESOLVE_COMET, RESOLVE_PERCOLATOR, HASH_INPUTS),
                    Set.of());
            assertEquals(
                    List.of(
                            new RerunReason.InputChanged(InputKind.COMET_PARAMETERS),
                            new RerunReason.UpstreamReExecutes(SERIALISE_COMET_PARAMS)),
                    preview.verdict(RUN_COMET).reasons());
            assertEquals(
                    List.of(
                            new RerunReason.UpstreamReExecutes(MERGE_PIN),
                            new RerunReason.UpstreamReExecutes(FINALISE_RESULTS)),
                    preview.verdict(FINALISE_PROVENANCE).reasons());
        }

        @Test
        @DisplayName("(d) with an index mode, changing Comet parameters rebuilds the index too")
        void cometParametersRebuildTheIndex() {
            StepInputs current =
                    Scenario.baseline()
                            .with(InputKind.COMET_PARAMETERS, Scenario.cometParams("changed\n"));

            RerunPreview preview =
                    RerunPreview.compute(
                            Scenario.fullWithIndex(),
                            current,
                            Scenario.recorded(Scenario.fullWithIndex(), Scenario.baseline()));

            Set<EngineStep> expected = EnumSet.copyOf(ALL_FULL_RESULTS);
            expected.add(BUILD_COMET_INDEX);
            assertSets(
                    preview,
                    expected,
                    Set.of(VALIDATE_CONFIGURATION, RESOLVE_COMET, RESOLVE_PERCOLATOR, HASH_INPUTS),
                    Set.of());
            assertEquals(
                    List.of(
                            new RerunReason.InputChanged(InputKind.COMET_PARAMETERS),
                            new RerunReason.UpstreamReExecutes(SERIALISE_COMET_PARAMS)),
                    preview.verdict(BUILD_COMET_INDEX).reasons());
        }

        @Test
        @DisplayName("(e) changing only the Limelight q cutoff re-executes conversion and upload")
        void limelightCutoffRerunsConversionAndUpload() {
            StepInputs current =
                    Scenario.baseline()
                            .with(
                                    InputKind.LIMELIGHT_Q_CUTOFF,
                                    InputValue.decimal(new BigDecimal("0.05")));

            RerunPreview preview = previewOfFull(current);

            assertSets(
                    preview,
                    Set.of(CONVERT_LIMELIGHT, UPLOAD_LIMELIGHT, APPEND_DOWNSTREAM_PROVENANCE),
                    Set.of(),
                    Set.of(
                            SERIALISE_COMET_PARAMS,
                            RUN_COMET,
                            VALIDATE_COMET_OUTPUTS,
                            MERGE_PIN,
                            RUN_PERCOLATOR,
                            PARSE_PERCOLATOR,
                            FINALISE_RESULTS,
                            FINALISE_PROVENANCE,
                            LAUNCH_PDV));
            assertEquals(
                    List.of(new RerunReason.InputChanged(InputKind.LIMELIGHT_Q_CUTOFF)),
                    preview.verdict(CONVERT_LIMELIGHT).reasons());
            assertEquals(
                    List.of(new RerunReason.UpstreamReExecutes(CONVERT_LIMELIGHT)),
                    preview.verdict(UPLOAD_LIMELIGHT).reasons());
        }

        @Test
        @DisplayName("an equal q cutoff written differently (0.010) re-executes nothing")
        void anEqualCutoffWrittenDifferentlyIsNoChange() {
            StepInputs current =
                    Scenario.baseline()
                            .with(
                                    InputKind.LIMELIGHT_Q_CUTOFF,
                                    InputValue.decimal(new BigDecimal("0.010")));

            assertEquals(Set.of(), previewOfFull(current).executed());
        }
    }

    @Nested
    @DisplayName("changed inputs and tools")
    class ChangedInputs {

        private final Set<EngineStep> cometAndDownstream =
                EnumSet.of(
                        RUN_COMET,
                        VALIDATE_COMET_OUTPUTS,
                        MERGE_PIN,
                        RUN_PERCOLATOR,
                        PARSE_PERCOLATOR,
                        FINALISE_RESULTS,
                        FINALISE_PROVENANCE,
                        LAUNCH_PDV,
                        CONVERT_LIMELIGHT,
                        UPLOAD_LIMELIGHT,
                        APPEND_DOWNSTREAM_PROVENANCE);

        @Test
        @DisplayName("a changed spectrum file re-executes Comet and downstream, not the parameters")
        void aChangedSpectrumFile() {
            StepInputs current =
                    Scenario.baseline()
                            .with(
                                    InputKind.SPECTRUM_FILES,
                                    InputValue.files(
                                            List.of(
                                                    Scenario.file("k562_3.mzML", Scenario.SHA_A),
                                                    Scenario.file("k562_4.mzML", Scenario.SHA_C))));

            RerunPreview preview =
                    RerunPreview.compute(
                            Scenario.fullWithIndex(),
                            current,
                            Scenario.recorded(Scenario.fullWithIndex(), Scenario.baseline()));

            assertSets(
                    preview,
                    cometAndDownstream,
                    Set.of(VALIDATE_CONFIGURATION, RESOLVE_COMET, RESOLVE_PERCOLATOR, HASH_INPUTS),
                    Set.of(SERIALISE_COMET_PARAMS, BUILD_COMET_INDEX));
            assertEquals(
                    List.of(new RerunReason.InputChanged(InputKind.SPECTRUM_FILES)),
                    preview.verdict(RUN_COMET).reasons());
        }

        @Test
        @DisplayName("a changed FASTA rebuilds the index and re-executes Comet and downstream")
        void aChangedFasta() {
            StepInputs current =
                    Scenario.baseline()
                            .with(
                                    InputKind.FASTA,
                                    InputValue.file(
                                            "uniprot-1000.fasta", Scenario.hashes(Scenario.SHA_D)));

            RerunPreview preview =
                    RerunPreview.compute(
                            Scenario.fullWithIndex(),
                            current,
                            Scenario.recorded(Scenario.fullWithIndex(), Scenario.baseline()));

            Set<EngineStep> expected = EnumSet.copyOf(cometAndDownstream);
            expected.add(BUILD_COMET_INDEX);
            assertSets(
                    preview,
                    expected,
                    Set.of(VALIDATE_CONFIGURATION, RESOLVE_COMET, RESOLVE_PERCOLATOR, HASH_INPUTS),
                    Set.of(SERIALISE_COMET_PARAMS));
            assertEquals(
                    List.of(
                            new RerunReason.InputChanged(InputKind.FASTA),
                            new RerunReason.UpstreamReExecutes(BUILD_COMET_INDEX)),
                    preview.verdict(RUN_COMET).reasons());
            assertEquals(
                    List.of(
                            new RerunReason.PrerequisiteOf(BUILD_COMET_INDEX),
                            new RerunReason.PrerequisiteOf(RUN_COMET)),
                    preview.verdict(HASH_INPUTS).reasons());
        }

        @Test
        @DisplayName("a changed Comet binary re-executes Comet and downstream, not the parameters")
        void aChangedCometBinary() {
            StepInputs current =
                    Scenario.baseline()
                            .with(
                                    InputKind.COMET_TOOL,
                                    InputValue.tool("2026.03.0", Scenario.SHA_2));

            RerunPreview preview = previewOfFull(current);

            assertSets(
                    preview,
                    cometAndDownstream,
                    Set.of(VALIDATE_CONFIGURATION, RESOLVE_COMET, RESOLVE_PERCOLATOR, HASH_INPUTS),
                    Set.of(SERIALISE_COMET_PARAMS));
            assertEquals(
                    List.of(new RerunReason.InputChanged(InputKind.COMET_TOOL)),
                    preview.verdict(RUN_COMET).reasons());
        }

        @Test
        @DisplayName("selecting an index mode builds the index and re-executes Comet")
        void selectingAnIndexMode() {
            StepInputs current =
                    Scenario.baseline()
                            .with(InputKind.COMET_INDEX_MODE, InputValue.text("fragment-ion"));

            RerunPreview preview =
                    RerunPreview.compute(
                            Scenario.fullWithIndex(),
                            current,
                            Scenario.recorded(Scenario.full(), Scenario.baseline()));

            Set<EngineStep> expected = EnumSet.copyOf(cometAndDownstream);
            expected.add(BUILD_COMET_INDEX);
            assertEquals(expected, preview.reExecuted());
            assertEquals(
                    List.of(new RerunReason.NotRecorded()),
                    preview.verdict(BUILD_COMET_INDEX).reasons());
            assertEquals(
                    List.of(
                            new RerunReason.InputChanged(InputKind.COMET_INDEX_MODE),
                            new RerunReason.UpstreamReExecutes(BUILD_COMET_INDEX)),
                    preview.verdict(RUN_COMET).reasons());
        }

        @Test
        @DisplayName("a phase 08 run with changed Comet parameters re-executes its five results")
        void phase08PlanWithChangedParameters() {
            StepInputs current =
                    Scenario.baseline()
                            .with(InputKind.COMET_PARAMETERS, Scenario.cometParams("changed\n"));

            RerunPreview preview =
                    RerunPreview.compute(
                            Scenario.phase08(),
                            current,
                            Scenario.recorded(Scenario.phase08(), Scenario.baseline()));

            assertSets(
                    preview,
                    Set.of(
                            SERIALISE_COMET_PARAMS,
                            RUN_COMET,
                            VALIDATE_COMET_OUTPUTS,
                            MERGE_PIN,
                            FINALISE_PROVENANCE),
                    Set.of(VALIDATE_CONFIGURATION, RESOLVE_COMET, HASH_INPUTS),
                    Set.of());
        }

        @Test
        @DisplayName("an unchanged configuration re-executes nothing")
        void nothingChanged() {
            RerunPreview preview = previewOfFull(Scenario.baseline());

            assertSets(preview, Set.of(), Set.of(), ALL_FULL_RESULTS);
        }
    }

    @Nested
    @DisplayName("propagation that fingerprints alone would miss")
    class Propagation {

        @Test
        @DisplayName("a forced step re-executes with everything downstream of it, nothing upstream")
        void aForcedStepPropagatesDownstream() {
            RerunPreview preview =
                    RerunPreview.compute(
                            Scenario.full(),
                            Scenario.baseline(),
                            Scenario.recorded(Scenario.full(), Scenario.baseline()),
                            Set.of(MERGE_PIN));

            assertSets(
                    preview,
                    Set.of(
                            MERGE_PIN,
                            RUN_PERCOLATOR,
                            PARSE_PERCOLATOR,
                            FINALISE_RESULTS,
                            FINALISE_PROVENANCE,
                            LAUNCH_PDV,
                            CONVERT_LIMELIGHT,
                            UPLOAD_LIMELIGHT,
                            APPEND_DOWNSTREAM_PROVENANCE),
                    Set.of(VALIDATE_CONFIGURATION, RESOLVE_PERCOLATOR),
                    Set.of(SERIALISE_COMET_PARAMS, RUN_COMET, VALIDATE_COMET_OUTPUTS));
            assertEquals(List.of(new RerunReason.Forced()), preview.verdict(MERGE_PIN).reasons());
            assertEquals(
                    List.of(new RerunReason.UpstreamReExecutes(MERGE_PIN)),
                    preview.verdict(RUN_PERCOLATOR).reasons());
        }

        @Test
        @DisplayName("a forced preparation step executes after its prerequisite; no result re-runs")
        void aForcedPreparationStep() {
            RerunPreview preview =
                    RerunPreview.compute(
                            Scenario.phase08(),
                            Scenario.baseline(),
                            Scenario.recorded(Scenario.phase08(), Scenario.baseline()),
                            Set.of(HASH_INPUTS));

            assertSets(
                    preview,
                    Set.of(),
                    Set.of(VALIDATE_CONFIGURATION, HASH_INPUTS),
                    Set.of(
                            SERIALISE_COMET_PARAMS,
                            RUN_COMET,
                            VALIDATE_COMET_OUTPUTS,
                            MERGE_PIN,
                            FINALISE_PROVENANCE));
            assertEquals(List.of(new RerunReason.Forced()), preview.verdict(HASH_INPUTS).reasons());
        }

        @Test
        @DisplayName("a step with no record re-executes, and so does everything downstream of it")
        void anUnrecordedStepPropagatesDownstream() {
            Map<EngineStep, StepFingerprint> recorded =
                    new EnumMap<>(Scenario.recorded(Scenario.phase08(), Scenario.baseline()));
            recorded.remove(VALIDATE_COMET_OUTPUTS);

            RerunPreview preview =
                    RerunPreview.compute(Scenario.phase08(), Scenario.baseline(), recorded);

            assertSets(
                    preview,
                    Set.of(VALIDATE_COMET_OUTPUTS, MERGE_PIN, FINALISE_PROVENANCE),
                    Set.of(),
                    Set.of(SERIALISE_COMET_PARAMS, RUN_COMET));
            assertEquals(
                    List.of(new RerunReason.NotRecorded()),
                    preview.verdict(VALIDATE_COMET_OUTPUTS).reasons());
        }

        @Test
        @DisplayName(
                "a fingerprint that differs with no input or upstream change still re-executes")
        void aDifferentFingerprintAloneReExecutes() {
            Map<EngineStep, StepFingerprint> recorded =
                    new EnumMap<>(Scenario.recorded(Scenario.phase08(), Scenario.baseline()));
            StepFingerprint real = recorded.get(MERGE_PIN);
            recorded.put(
                    MERGE_PIN, new StepFingerprint(MERGE_PIN, "0".repeat(64), real.inputDigests()));

            RerunPreview preview =
                    RerunPreview.compute(Scenario.phase08(), Scenario.baseline(), recorded);

            assertSets(
                    preview,
                    Set.of(MERGE_PIN, FINALISE_PROVENANCE),
                    Set.of(),
                    Set.of(SERIALISE_COMET_PARAMS, RUN_COMET, VALIDATE_COMET_OUTPUTS));
            assertEquals(
                    List.of(new RerunReason.FingerprintChanged()),
                    preview.verdict(MERGE_PIN).reasons());
        }

        @Test
        @DisplayName("a recorded input digest that is absent counts as changed")
        void anAbsentRecordedDigestIsAChange() {
            Map<EngineStep, StepFingerprint> recorded =
                    new EnumMap<>(Scenario.recorded(Scenario.phase08(), Scenario.baseline()));
            StepFingerprint real = recorded.get(SERIALISE_COMET_PARAMS);
            recorded.put(
                    SERIALISE_COMET_PARAMS,
                    new StepFingerprint(SERIALISE_COMET_PARAMS, real.value(), Map.of()));

            RerunPreview preview =
                    RerunPreview.compute(Scenario.phase08(), Scenario.baseline(), recorded);

            assertEquals(
                    List.of(new RerunReason.InputChanged(InputKind.COMET_PARAMETERS)),
                    preview.verdict(SERIALISE_COMET_PARAMS).reasons());
        }

        @Test
        @DisplayName(
                "a full run's records serve a phase 08 plan; only provenance, whose upstream set"
                        + " shrank, re-executes")
        void recordsOutsideThePlanAreIgnored() {
            Map<EngineStep, StepFingerprint> recorded =
                    new EnumMap<>(Scenario.recorded(Scenario.full(), Scenario.baseline()));

            RerunPreview preview =
                    RerunPreview.compute(Scenario.phase08(), Scenario.baseline(), recorded);

            assertSets(
                    preview,
                    Set.of(FINALISE_PROVENANCE),
                    Set.of(),
                    Set.of(SERIALISE_COMET_PARAMS, RUN_COMET, VALIDATE_COMET_OUTPUTS, MERGE_PIN));
            assertEquals(
                    List.of(new RerunReason.FingerprintChanged()),
                    preview.verdict(FINALISE_PROVENANCE).reasons());
            assertEquals(Scenario.phase08().steps().size(), preview.verdicts().size());
        }
    }

    @Nested
    @DisplayName("the value objects a view renders")
    class Rendering {

        @Test
        @DisplayName("verdicts come in plan order with their decisions")
        void verdictsInPlanOrder() {
            StepInputs current =
                    Scenario.baseline()
                            .with(
                                    InputKind.COMET_TOOL,
                                    InputValue.tool("2026.03.0", Scenario.SHA_2));
            Plan plan = Scenario.phase08();
            RerunPreview preview =
                    RerunPreview.compute(
                            plan, current, Scenario.recorded(plan, Scenario.baseline()));

            List<StepVerdict> expected =
                    List.of(
                            new StepVerdict(
                                    VALIDATE_CONFIGURATION,
                                    RerunDecision.PREPARE,
                                    List.of(
                                            new RerunReason.PrerequisiteOf(RESOLVE_COMET),
                                            new RerunReason.PrerequisiteOf(HASH_INPUTS))),
                            new StepVerdict(
                                    RESOLVE_COMET,
                                    RerunDecision.PREPARE,
                                    List.of(new RerunReason.PrerequisiteOf(RUN_COMET))),
                            new StepVerdict(SERIALISE_COMET_PARAMS, RerunDecision.REUSE, List.of()),
                            new StepVerdict(
                                    HASH_INPUTS,
                                    RerunDecision.PREPARE,
                                    List.of(new RerunReason.PrerequisiteOf(RUN_COMET))),
                            new StepVerdict(
                                    RUN_COMET,
                                    RerunDecision.RE_EXECUTE,
                                    List.of(new RerunReason.InputChanged(InputKind.COMET_TOOL))),
                            new StepVerdict(
                                    VALIDATE_COMET_OUTPUTS,
                                    RerunDecision.RE_EXECUTE,
                                    List.of(new RerunReason.UpstreamReExecutes(RUN_COMET))),
                            new StepVerdict(
                                    MERGE_PIN,
                                    RerunDecision.RE_EXECUTE,
                                    List.of(
                                            new RerunReason.UpstreamReExecutes(
                                                    VALIDATE_COMET_OUTPUTS))),
                            new StepVerdict(
                                    FINALISE_PROVENANCE,
                                    RerunDecision.RE_EXECUTE,
                                    List.of(new RerunReason.UpstreamReExecutes(MERGE_PIN))));
            assertEquals(expected, preview.verdicts());
            assertSame(plan, preview.plan());
        }

        @Test
        @DisplayName("the preview carries the current fingerprints to record after the run")
        void carriesCurrentFingerprints() {
            StepInputs current =
                    Scenario.baseline()
                            .with(InputKind.COMET_PARAMETERS, Scenario.cometParams("changed\n"));
            RerunPreview preview =
                    RerunPreview.compute(
                            Scenario.phase08(),
                            current,
                            Scenario.recorded(Scenario.phase08(), Scenario.baseline()));

            assertEquals(Fingerprints.compute(Scenario.phase08(), current), preview.fingerprints());
        }

        @Test
        @DisplayName("each reason describes itself by identifier")
        void reasonsDescribeThemselves() {
            assertEquals("re-execution was requested", new RerunReason.Forced().describe());
            assertEquals(
                    "no successful earlier execution is recorded",
                    new RerunReason.NotRecorded().describe());
            assertEquals(
                    "limelight-q-cutoff changed",
                    new RerunReason.InputChanged(InputKind.LIMELIGHT_Q_CUTOFF).describe());
            assertEquals(
                    "merge-pin re-executes",
                    new RerunReason.UpstreamReExecutes(MERGE_PIN).describe());
            assertEquals(
                    "its fingerprint differs from the recorded one",
                    new RerunReason.FingerprintChanged().describe());
            assertEquals(
                    "needed by run-comet", new RerunReason.PrerequisiteOf(RUN_COMET).describe());
        }

        @Test
        @DisplayName("asking for the verdict of an unplanned step is refused, naming it")
        void verdictOfAnUnplannedStep() {
            RerunPreview preview =
                    RerunPreview.compute(
                            Scenario.phase08(),
                            Scenario.baseline(),
                            Scenario.recorded(Scenario.phase08(), Scenario.baseline()));

            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class, () -> preview.verdict(RUN_PERCOLATOR));
            assertEquals("step run-percolator is not in the plan", thrown.getMessage());
        }
    }

    @Nested
    @DisplayName("refused arguments")
    class Refusals {

        @Test
        @DisplayName("a forced step outside the plan is refused, naming it")
        void forcedOutsideThePlan() {
            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    RerunPreview.compute(
                                            Scenario.phase08(),
                                            Scenario.baseline(),
                                            Map.of(),
                                            Set.of(RUN_PERCOLATOR)));
            assertEquals(
                    "step run-percolator is forced but is not in the plan", thrown.getMessage());
        }

        @Test
        @DisplayName("a fingerprint recorded under the wrong step is refused, naming both")
        void recordedUnderTheWrongStep() {
            StepFingerprint mergePin =
                    Scenario.recorded(Scenario.phase08(), Scenario.baseline()).get(MERGE_PIN);
            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    RerunPreview.compute(
                                            Scenario.phase08(),
                                            Scenario.baseline(),
                                            Map.of(RUN_COMET, mergePin)));
            assertEquals(
                    "the fingerprint recorded under run-comet is the fingerprint of merge-pin",
                    thrown.getMessage());
        }

        @Test
        @DisplayName("a missing input is refused before anything is decided, naming step and input")
        void aMissingInput() {
            Map<InputKind, InputValue> values = new HashMap<>(Scenario.baseline().values());
            values.remove(InputKind.COMET_TOOL);

            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    RerunPreview.compute(
                                            Scenario.phase08(), StepInputs.of(values), Map.of()));
            assertEquals(
                    "step resolve-comet cannot be fingerprinted without its declared inputs;"
                            + " missing: comet-tool",
                    thrown.getMessage());
        }

        @Test
        @DisplayName("null arguments and null entries are refused, naming them")
        void nulls() {
            Plan plan = Scenario.phase08();
            StepInputs inputs = Scenario.baseline();
            @SuppressWarnings("unchecked")
            Map<EngineStep, StepFingerprint> noRecord = Nulls.of(Map.class);
            @SuppressWarnings("unchecked")
            Set<EngineStep> noForced = Nulls.of(Set.class);
            Map<EngineStep, StepFingerprint> nullValue = new HashMap<>();
            nullValue.put(RUN_COMET, null);
            Set<EngineStep> nullForced = new HashSet<>();
            nullForced.add(null);
            Map<EngineStep, StepFingerprint> nullKey = new HashMap<>();
            nullKey.put(null, Scenario.recorded(plan, inputs).get(RUN_COMET));

            assertEquals(
                    "recorded",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> RerunPreview.compute(plan, inputs, noRecord))
                            .getMessage());
            assertEquals(
                    "forced",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> RerunPreview.compute(plan, inputs, Map.of(), noForced))
                            .getMessage());
            assertEquals(
                    "recorded has no value for run-comet",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> RerunPreview.compute(plan, inputs, nullValue))
                            .getMessage());
            assertEquals(
                    "recorded has a null step",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> RerunPreview.compute(plan, inputs, nullKey))
                            .getMessage());
            assertEquals(
                    "forced contains null",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> RerunPreview.compute(plan, inputs, Map.of(), nullForced))
                            .getMessage());
            assertEquals(
                    "step",
                    assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            RerunPreview.compute(plan, inputs, Map.of())
                                                    .verdict(Nulls.of(EngineStep.class)))
                            .getMessage());
        }
    }
}
