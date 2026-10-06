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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.cometgui.domain.ports.FileHashes;

/**
 * The inputs and plans the fingerprint and rerun tests share.
 *
 * <p>Every value is hand-chosen and fixed. The SHA-256s are repeated hex letters so that a digest
 * pinned in a test can be recomputed with {@code printf} and {@code sha256sum} from the comment
 * next to it, without trusting any code in this module.
 */
final class Scenario {

    static final String SHA_A = "a".repeat(64);
    static final String SHA_B = "b".repeat(64);
    static final String SHA_C = "c".repeat(64);
    static final String SHA_D = "d".repeat(64);
    static final String SHA_E = "e".repeat(64);
    static final String SHA_F = "f".repeat(64);
    static final String SHA_1 = "1".repeat(64);
    static final String SHA_2 = "2".repeat(64);
    static final String MD5_0 = "0".repeat(32);

    /** The canonical Comet parameter bytes of the baseline. */
    static final String COMET_PARAMS_TEXT = "# comet_version 2026.03 rev. 0\nnum_threads = 4\n";

    private Scenario() {}

    static FileHashes hashes(String sha256) {
        return new FileHashes(MD5_0, sha256);
    }

    static InputValue.NamedFile file(String name, String sha256) {
        return new InputValue.NamedFile(name, hashes(sha256));
    }

    static InputValue.Bytes cometParams(String text) {
        return InputValue.bytes(text.getBytes(StandardCharsets.UTF_8));
    }

    /** A configuration in which every input kind has a value. */
    static StepInputs baseline() {
        Map<InputKind, InputValue> values = new EnumMap<>(InputKind.class);
        values.put(
                InputKind.SPECTRUM_FILES,
                InputValue.files(List.of(file("k562_3.mzML", SHA_A), file("k562_4.mzML", SHA_B))));
        values.put(InputKind.FASTA, InputValue.file("uniprot-1000.fasta", hashes(SHA_C)));
        values.put(InputKind.COMET_PARAMETERS, cometParams(COMET_PARAMS_TEXT));
        values.put(InputKind.COMET_INDEX_MODE, InputValue.text("none"));
        values.put(InputKind.COMET_TOOL, InputValue.tool("2026.03.0", SHA_D));
        values.put(InputKind.PERCOLATOR_SETTINGS, InputValue.text("test-fdr=0.01"));
        values.put(InputKind.PERCOLATOR_TOOL, InputValue.tool("3.09.0", SHA_E));
        values.put(InputKind.PDV_TOOL, InputValue.tool("2.7.0", SHA_F));
        values.put(InputKind.LIMELIGHT_Q_CUTOFF, InputValue.decimal(new BigDecimal("0.01")));
        values.put(InputKind.LIMELIGHT_CONVERTER_OPTIONS, InputValue.text("import-decoys=false"));
        values.put(InputKind.LIMELIGHT_CONVERTER_TOOL, InputValue.tool("2.8.1", SHA_1));
        values.put(
                InputKind.LIMELIGHT_UPLOAD_TARGET,
                InputValue.text("https://limelight.example/project/1"));
        values.put(InputKind.PSM_DISPLAY_FILTER, InputValue.decimal(new BigDecimal("0.01")));
        values.put(InputKind.PEPTIDE_DISPLAY_FILTER, InputValue.decimal(new BigDecimal("0.01")));
        return StepInputs.of(values);
    }

    /** Phase 08's plan: up to merging the PIN files, then core provenance. */
    static Plan phase08() {
        return Plan.covering(EnumSet.of(EngineStep.FINALISE_PROVENANCE));
    }

    /** Every step but the index build: a run asking for results, PDV, conversion and upload. */
    static Plan full() {
        return Plan.covering(
                EnumSet.of(
                        EngineStep.FINALISE_RESULTS,
                        EngineStep.FINALISE_PROVENANCE,
                        EngineStep.LAUNCH_PDV,
                        EngineStep.CONVERT_LIMELIGHT,
                        EngineStep.UPLOAD_LIMELIGHT,
                        EngineStep.APPEND_DOWNSTREAM_PROVENANCE));
    }

    /** {@link #full()} with an index mode selected. */
    static Plan fullWithIndex() {
        return Plan.covering(
                EnumSet.of(
                        EngineStep.BUILD_COMET_INDEX,
                        EngineStep.FINALISE_RESULTS,
                        EngineStep.FINALISE_PROVENANCE,
                        EngineStep.LAUNCH_PDV,
                        EngineStep.CONVERT_LIMELIGHT,
                        EngineStep.UPLOAD_LIMELIGHT,
                        EngineStep.APPEND_DOWNSTREAM_PROVENANCE));
    }

    /** Every planned step in one state. */
    static Map<EngineStep, StepState> allPlanned(Plan plan, StepState state) {
        Map<EngineStep, StepState> states = new EnumMap<>(EngineStep.class);
        for (EngineStep step : plan.steps()) {
            states.put(step, state);
        }
        return states;
    }

    /** What a run of {@code plan} over {@code inputs} records when every step succeeds. */
    static Map<EngineStep, StepFingerprint> recorded(Plan plan, StepInputs inputs) {
        return Fingerprints.compute(plan, inputs);
    }
}
