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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.cometgui.workflow.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the small value types: {@link StepInputs}, {@link StepFingerprint}, {@link
 * StepVerdict}.
 */
class StepValuesTest {

    @Nested
    @DisplayName("StepInputs")
    class Inputs {

        @Test
        @DisplayName("holds each kind's value and answers empty for an absent one")
        void holdsValues() {
            StepInputs inputs =
                    StepInputs.of(Map.of(InputKind.COMET_INDEX_MODE, InputValue.text("none")));

            assertEquals(
                    Optional.of(InputValue.text("none")), inputs.get(InputKind.COMET_INDEX_MODE));
            assertEquals(Optional.empty(), inputs.get(InputKind.FASTA));
            assertEquals(
                    Map.of(InputKind.COMET_INDEX_MODE, InputValue.text("none")), inputs.values());
        }

        @Test
        @DisplayName("with() replaces one value and leaves the original unchanged")
        void with() {
            StepInputs before = Scenario.baseline();
            StepInputs after =
                    before.with(InputKind.PERCOLATOR_SETTINGS, InputValue.text("changed"));

            assertEquals(
                    Optional.of(InputValue.text("changed")),
                    after.get(InputKind.PERCOLATOR_SETTINGS));
            assertEquals(
                    Optional.of(InputValue.text("test-fdr=0.01")),
                    before.get(InputKind.PERCOLATOR_SETTINGS));
            assertEquals(before.values().size(), after.values().size());
        }

        @Test
        @DisplayName("values iterate in input-kind order and cannot be modified")
        void valuesAreOrderedAndImmutable() {
            Map<InputKind, InputValue> values = new HashMap<>();
            values.put(InputKind.PSM_DISPLAY_FILTER, InputValue.decimal(BigDecimal.ONE));
            values.put(InputKind.COMET_INDEX_MODE, InputValue.text("none"));
            StepInputs inputs = StepInputs.of(values);

            assertEquals(
                    List.of(InputKind.COMET_INDEX_MODE, InputKind.PSM_DISPLAY_FILTER),
                    new ArrayList<>(inputs.values().keySet()));
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> inputs.values().remove(InputKind.COMET_INDEX_MODE));
            values.clear();
            assertEquals(2, inputs.values().size(), "the caller's map was copied");
        }

        @Test
        @DisplayName("a value of the wrong type for its kind is refused, naming both types")
        void wrongType() {
            assertEquals(
                    "input comet-parameters takes a BYTES value, but was given a TEXT value",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            StepInputs.of(
                                                    Map.of(
                                                            InputKind.COMET_PARAMETERS,
                                                            InputValue.text("x"))))
                            .getMessage());
            assertEquals(
                    "input limelight-q-cutoff takes a DECIMAL value, but was given a TEXT value",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            assertNotNull(
                                                    Scenario.baseline()
                                                            .with(
                                                                    InputKind.LIMELIGHT_Q_CUTOFF,
                                                                    InputValue.text("0.01"))))
                            .getMessage());
        }

        @Test
        @DisplayName("nulls are refused, naming them")
        void nulls() {
            Map<InputKind, InputValue> nullKey = new HashMap<>();
            nullKey.put(null, InputValue.text("x"));
            Map<InputKind, InputValue> nullValue = new HashMap<>();
            nullValue.put(InputKind.FASTA, null);
            @SuppressWarnings("unchecked")
            Map<InputKind, InputValue> none = Nulls.of(Map.class);

            assertEquals(
                    "values",
                    assertThrows(NullPointerException.class, () -> StepInputs.of(none))
                            .getMessage());
            assertEquals(
                    "values contains a null kind",
                    assertThrows(NullPointerException.class, () -> StepInputs.of(nullKey))
                            .getMessage());
            assertEquals(
                    "no value for fasta",
                    assertThrows(NullPointerException.class, () -> StepInputs.of(nullValue))
                            .getMessage());
            assertEquals(
                    "kind",
                    assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            assertNotNull(
                                                    Scenario.baseline()
                                                            .with(
                                                                    Nulls.of(InputKind.class),
                                                                    InputValue.text("x"))))
                            .getMessage());
            assertEquals(
                    "kind",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> Scenario.baseline().get(Nulls.of(InputKind.class)))
                            .getMessage());
        }
    }

    @Nested
    @DisplayName("StepFingerprint")
    class Fingerprint {

        @Test
        @DisplayName("holds digests in lower case, in input-kind order, immutably")
        void canonical() {
            Map<InputKind, String> digests = new HashMap<>();
            digests.put(InputKind.COMET_TOOL, Scenario.SHA_D.toUpperCase(java.util.Locale.ROOT));
            digests.put(InputKind.FASTA, Scenario.SHA_C);
            StepFingerprint fingerprint =
                    new StepFingerprint(
                            EngineStep.RUN_COMET,
                            Scenario.SHA_A.toUpperCase(java.util.Locale.ROOT),
                            digests);

            assertEquals(Scenario.SHA_A, fingerprint.value());
            assertEquals(
                    List.of(InputKind.FASTA, InputKind.COMET_TOOL),
                    new ArrayList<>(fingerprint.inputDigests().keySet()));
            assertEquals(Scenario.SHA_D, fingerprint.inputDigests().get(InputKind.COMET_TOOL));
            digests.clear();
            assertEquals(2, fingerprint.inputDigests().size());
            assertThrows(
                    UnsupportedOperationException.class, () -> fingerprint.inputDigests().clear());
        }

        @Test
        @DisplayName("a malformed recorded fingerprint or digest is refused, naming which")
        void malformed() {
            assertEquals(
                    "fingerprint of run-comet must be 64 hexadecimal characters (a SHA-256), but"
                            + " was: \"xyz\"",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            new StepFingerprint(
                                                    EngineStep.RUN_COMET, "xyz", Map.of()))
                            .getMessage());
            assertEquals(
                    "digest of fasta for run-comet must be 64 hexadecimal characters (a SHA-256),"
                            + " but was: \"\"",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            new StepFingerprint(
                                                    EngineStep.RUN_COMET,
                                                    Scenario.SHA_A,
                                                    Map.of(InputKind.FASTA, "")))
                            .getMessage());
        }

        @Test
        @DisplayName("nulls are refused, naming them")
        void nulls() {
            Map<InputKind, String> nullKey = new HashMap<>();
            nullKey.put(null, Scenario.SHA_A);
            Map<InputKind, String> nullValue = new EnumMap<>(InputKind.class);
            nullValue.put(InputKind.FASTA, null);
            @SuppressWarnings("unchecked")
            Map<InputKind, String> none = Nulls.of(Map.class);

            assertEquals(
                    "step",
                    assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            new StepFingerprint(
                                                    Nulls.of(EngineStep.class),
                                                    Scenario.SHA_A,
                                                    Map.of()))
                            .getMessage());
            assertEquals(
                    "fingerprint of merge-pin",
                    assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            new StepFingerprint(
                                                    EngineStep.MERGE_PIN,
                                                    Nulls.of(String.class),
                                                    Map.of()))
                            .getMessage());
            assertEquals(
                    "inputDigests",
                    assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            new StepFingerprint(
                                                    EngineStep.MERGE_PIN, Scenario.SHA_A, none))
                            .getMessage());
            assertEquals(
                    "inputDigests has a null key",
                    assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            new StepFingerprint(
                                                    EngineStep.MERGE_PIN, Scenario.SHA_A, nullKey))
                            .getMessage());
            assertEquals(
                    "digest of fasta for merge-pin",
                    assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            new StepFingerprint(
                                                    EngineStep.MERGE_PIN,
                                                    Scenario.SHA_A,
                                                    nullValue))
                            .getMessage());
        }
    }

    @Nested
    @DisplayName("StepVerdict")
    class Verdict {

        @Test
        @DisplayName("executes for re-execute and prepare only")
        void executes() {
            List<RerunReason> why = List.of(new RerunReason.Forced());
            assertTrue(
                    new StepVerdict(EngineStep.RUN_COMET, RerunDecision.RE_EXECUTE, why)
                            .executes());
            assertTrue(
                    new StepVerdict(EngineStep.HASH_INPUTS, RerunDecision.PREPARE, why).executes());
            assertFalse(
                    new StepVerdict(EngineStep.RUN_COMET, RerunDecision.REUSE, List.of())
                            .executes());
            assertFalse(
                    new StepVerdict(EngineStep.HASH_INPUTS, RerunDecision.NOT_NEEDED, List.of())
                            .executes());
        }

        @Test
        @DisplayName("an executing step needs a reason, and a reused one may not have one")
        void reasonsMatchTheDecision() {
            assertEquals(
                    "run-comet is RE_EXECUTE with reasons []",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            new StepVerdict(
                                                    EngineStep.RUN_COMET,
                                                    RerunDecision.RE_EXECUTE,
                                                    List.of()))
                            .getMessage());
            assertEquals(
                    "hash-inputs is NOT_NEEDED with reasons [Forced[]]",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            new StepVerdict(
                                                    EngineStep.HASH_INPUTS,
                                                    RerunDecision.NOT_NEEDED,
                                                    List.of(new RerunReason.Forced())))
                            .getMessage());
        }

        @Test
        @DisplayName("nulls are refused, naming them")
        void nulls() {
            assertEquals(
                    "step",
                    assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            new StepVerdict(
                                                    Nulls.of(EngineStep.class),
                                                    RerunDecision.REUSE,
                                                    List.of()))
                            .getMessage());
            assertEquals(
                    "decision",
                    assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            new StepVerdict(
                                                    EngineStep.RUN_COMET,
                                                    Nulls.of(RerunDecision.class),
                                                    List.of()))
                            .getMessage());
            assertEquals(
                    "input",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> new RerunReason.InputChanged(Nulls.of(InputKind.class)))
                            .getMessage());
            assertEquals(
                    "upstream",
                    assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            new RerunReason.UpstreamReExecutes(
                                                    Nulls.of(EngineStep.class)))
                            .getMessage());
            assertEquals(
                    "step",
                    assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            new RerunReason.PrerequisiteOf(
                                                    Nulls.of(EngineStep.class)))
                            .getMessage());
        }
    }
}
