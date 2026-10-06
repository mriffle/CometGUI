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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.cometgui.workflow.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Fingerprints}: the encoding pinned by digests computed outside Java.
 *
 * <p>The digests below were computed with {@code printf ... | sha256sum}; each comment gives the
 * exact bytes. Writing {@code SPEC}, {@code FASTA}, {@code PARAMS}, {@code MODE}, {@code TOOL} for
 * the input digests {@link InputValueTest} pins:
 *
 * <pre>
 * SPEC   = fd17b1126873ec964e7c4b3749d69b870960da2519c03a670c9de8b2c2ca850b
 * FASTA  = 9579c2ff0817c9c8d5e00f42fc53f3177351c61593a37e76c572ad08db801728
 * PARAMS = 56400815b85272afc1951a04d23e705cb56a785c189bef39e0b735cb52dc6d9a
 * MODE   = 140bedbf9c3f6d56a9846d2ba7088798683f4da0c248231336e6a05679e4fdfe
 * TOOL   = e522cbb68f569b7a8fc831423b260965a608ed85b5d6eb5a730e64f370aeff98
 * </pre>
 */
class FingerprintsTest {

    /*
     * printf 'cometgui-step-fingerprint 1\nstep hash-inputs\ninput spectrum-files %s\n
     *         input fasta %s\n' $SPEC $FASTA | sha256sum
     * (one printf; the format string is split here only for width)
     */
    private static final String HASH_INPUTS =
            "51b242c6000cecba559df130e6593a456f1c3fd5fe0206d3d85bb41b1052f93c";

    /*
     * printf 'cometgui-step-fingerprint 1\nstep serialise-comet-params\n
     *         input comet-parameters %s\n' $PARAMS | sha256sum
     */
    private static final String SERIALISE =
            "2d9d93877381e5c135636548b690aa43d693f796bd6d0b2f514d3527fabbad06";

    /*
     * printf 'cometgui-step-fingerprint 1\nstep run-comet\ninput spectrum-files %s\n
     *         input fasta %s\ninput comet-parameters %s\ninput comet-index-mode %s\n
     *         input comet-tool %s\nupstream serialise-comet-params %s\n'
     *         $SPEC $FASTA $PARAMS $MODE $TOOL $SERIALISE | sha256sum
     *
     * No line for hash-inputs or resolve-comet: they are PREPARATION steps.
     */
    private static final String RUN_COMET =
            "4659a95b4ef2cccf51ebee255f2d784e4fc77218eb42e6f25e09bc1efe0cae6d";

    /*
     * printf 'cometgui-step-fingerprint 1\nstep validate-configuration\ninput spectrum-files %s\n
     *         input fasta %s\ninput comet-parameters %s\ninput comet-index-mode %s\n'
     *         $SPEC $FASTA $PARAMS $MODE | sha256sum
     */
    private static final String VALIDATE =
            "bb5faf2cdfd57d31fe693f3ae8c3be4483379c0e2c201539afbb779a843911ce";

    /*
     * printf 'cometgui-step-fingerprint 1\nstep build-comet-index\ninput fasta %s\n
     *         input comet-parameters %s\ninput comet-tool %s\ninput comet-index-mode %s\n
     *         upstream serialise-comet-params %s\n' $FASTA $PARAMS $TOOL $MODE $SERIALISE
     *         | sha256sum
     */
    private static final String BUILD_INDEX =
            "2c9c9996974d2011c29a5bdb924756078f075abddc14c100afb33a1db2b14618";

    /*
     * As RUN_COMET, followed by one more line: 'upstream build-comet-index %s\n' $BUILD_INDEX.
     * Upstream lines are in step order: serialise-comet-params (4) before build-comet-index (6).
     */
    private static final String RUN_COMET_WITH_INDEX =
            "03c1b3c590b810de95e4908d0a8bda20619ab868c961c3ab6ead576e157821a0";

    @Test
    @DisplayName("the phase 08 plan's fingerprints match digests computed by sha256sum")
    void pinnedFingerprints() {
        Map<EngineStep, StepFingerprint> fingerprints =
                Fingerprints.compute(Scenario.phase08(), Scenario.baseline());

        assertEquals(VALIDATE, fingerprints.get(EngineStep.VALIDATE_CONFIGURATION).value());
        assertEquals(HASH_INPUTS, fingerprints.get(EngineStep.HASH_INPUTS).value());
        assertEquals(SERIALISE, fingerprints.get(EngineStep.SERIALISE_COMET_PARAMS).value());
        assertEquals(RUN_COMET, fingerprints.get(EngineStep.RUN_COMET).value());
    }

    @Test
    @DisplayName("a planned index build is an upstream of Comet and enters its fingerprint")
    void indexEntersCometsFingerprint() {
        Map<EngineStep, StepFingerprint> fingerprints =
                Fingerprints.compute(Scenario.fullWithIndex(), Scenario.baseline());

        assertEquals(BUILD_INDEX, fingerprints.get(EngineStep.BUILD_COMET_INDEX).value());
        assertEquals(RUN_COMET_WITH_INDEX, fingerprints.get(EngineStep.RUN_COMET).value());
    }

    @Test
    @DisplayName("each fingerprint carries its own inputs' digests and no others")
    void inputDigests() {
        StepFingerprint runComet =
                Fingerprints.compute(Scenario.phase08(), Scenario.baseline())
                        .get(EngineStep.RUN_COMET);

        assertEquals(EngineStep.RUN_COMET, runComet.step());
        assertEquals(
                Map.of(
                        InputKind.SPECTRUM_FILES,
                        "fd17b1126873ec964e7c4b3749d69b870960da2519c03a670c9de8b2c2ca850b",
                        InputKind.FASTA,
                        "9579c2ff0817c9c8d5e00f42fc53f3177351c61593a37e76c572ad08db801728",
                        InputKind.COMET_PARAMETERS,
                        "56400815b85272afc1951a04d23e705cb56a785c189bef39e0b735cb52dc6d9a",
                        InputKind.COMET_INDEX_MODE,
                        "140bedbf9c3f6d56a9846d2ba7088798683f4da0c248231336e6a05679e4fdfe",
                        InputKind.COMET_TOOL,
                        "e522cbb68f569b7a8fc831423b260965a608ed85b5d6eb5a730e64f370aeff98"),
                runComet.inputDigests());
        assertEquals(
                Map.of(),
                Fingerprints.compute(Scenario.phase08(), Scenario.baseline())
                        .get(EngineStep.MERGE_PIN)
                        .inputDigests());
    }

    @Test
    @DisplayName("the map is in plan order")
    void planOrder() {
        assertEquals(
                Scenario.phase08().steps(),
                new ArrayList<>(
                        Fingerprints.compute(Scenario.phase08(), Scenario.baseline()).keySet()));
    }

    @Test
    @DisplayName("an upstream change reaches every result downstream along data edges")
    void upstreamChangesPropagate() {
        Map<EngineStep, StepFingerprint> before =
                Fingerprints.compute(Scenario.phase08(), Scenario.baseline());
        Map<EngineStep, StepFingerprint> after =
                Fingerprints.compute(
                        Scenario.phase08(),
                        Scenario.baseline()
                                .with(
                                        InputKind.COMET_PARAMETERS,
                                        Scenario.cometParams("changed\n")));

        for (EngineStep step :
                List.of(
                        EngineStep.SERIALISE_COMET_PARAMS,
                        EngineStep.RUN_COMET,
                        EngineStep.VALIDATE_COMET_OUTPUTS,
                        EngineStep.MERGE_PIN,
                        EngineStep.FINALISE_PROVENANCE)) {
            assertNotEquals(before.get(step).value(), after.get(step).value(), step.id());
        }
        assertEquals(before.get(EngineStep.HASH_INPUTS), after.get(EngineStep.HASH_INPUTS));
        assertEquals(before.get(EngineStep.RESOLVE_COMET), after.get(EngineStep.RESOLVE_COMET));
    }

    @Test
    @DisplayName("a step with no inputs and no upstream still has a fingerprint of its identity")
    void identityAlone() {
        StepGraph graph = StepGraph.declare(EnumSet.of(EngineStep.MERGE_PIN), List.of());
        Plan plan = Plan.covering(graph, EnumSet.of(EngineStep.MERGE_PIN));

        // printf 'cometgui-step-fingerprint 1\nstep merge-pin\n' | sha256sum
        assertEquals(
                "15ad627cd172ab063c38afd2204d8cf543772b7972d8d080eba95adc7a7ea12e",
                Fingerprints.compute(plan, Scenario.baseline()).get(EngineStep.MERGE_PIN).value());
    }

    @Test
    @DisplayName("the same digest under a default locale with other digits and case rules")
    void localeIndependent() {
        Locale saved = Locale.getDefault();
        try {
            for (String tag : List.of("tr-TR", "ar-EG", "hi-IN-u-nu-deva", "th-TH-u-nu-thai")) {
                Locale.setDefault(Locale.forLanguageTag(tag));
                assertEquals(
                        RUN_COMET,
                        Fingerprints.compute(Scenario.phase08(), Scenario.baseline())
                                .get(EngineStep.RUN_COMET)
                                .value(),
                        tag);
            }
        } finally {
            Locale.setDefault(saved);
        }
    }

    @Test
    @DisplayName("a missing declared input is refused, naming the step and every missing input")
    void missingInputs() {
        Map<InputKind, InputValue> values = new HashMap<>(Scenario.baseline().values());
        values.remove(InputKind.FASTA);
        values.remove(InputKind.SPECTRUM_FILES);

        IllegalArgumentException thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> Fingerprints.compute(Scenario.phase08(), StepInputs.of(values)));
        assertEquals(
                "step validate-configuration cannot be fingerprinted without its declared"
                        + " inputs; missing: spectrum-files, fasta",
                thrown.getMessage());
    }

    @Test
    @DisplayName("a missing input of a later step names that step")
    void missingInputOfALaterStep() {
        Map<InputKind, InputValue> values = new HashMap<>(Scenario.baseline().values());
        values.remove(InputKind.PERCOLATOR_SETTINGS);

        IllegalArgumentException thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> Fingerprints.compute(Scenario.full(), StepInputs.of(values)));
        assertEquals(
                "step run-percolator cannot be fingerprinted without its declared inputs;"
                        + " missing: percolator-settings",
                thrown.getMessage());
    }

    @Test
    @DisplayName("an input no planned step reads may be absent")
    void unreadInputsMayBeAbsent() {
        Map<InputKind, InputValue> values = new HashMap<>(Scenario.baseline().values());
        values.remove(InputKind.PERCOLATOR_SETTINGS);
        values.remove(InputKind.PSM_DISPLAY_FILTER);

        assertEquals(
                RUN_COMET,
                Fingerprints.compute(Scenario.phase08(), StepInputs.of(values))
                        .get(EngineStep.RUN_COMET)
                        .value());
    }

    @Test
    @DisplayName("null arguments are refused, naming them")
    void nulls() {
        assertEquals(
                "plan",
                assertThrows(
                                NullPointerException.class,
                                () ->
                                        Fingerprints.compute(
                                                Nulls.of(Plan.class), Scenario.baseline()))
                        .getMessage());
        assertEquals(
                "inputs",
                assertThrows(
                                NullPointerException.class,
                                () ->
                                        Fingerprints.compute(
                                                Scenario.phase08(), Nulls.of(StepInputs.class)))
                        .getMessage());
    }

    @Test
    @DisplayName("the encoding's first line names its version")
    void encodingName() {
        assertEquals("cometgui-step-fingerprint 1", Fingerprints.ENCODING);
    }
}
