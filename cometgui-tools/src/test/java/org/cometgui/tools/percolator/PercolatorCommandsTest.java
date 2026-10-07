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

package org.cometgui.tools.percolator;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.cometgui.domain.tools.ToolCapability;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@code R-PERC-06}: the argument array, element by element, against hand-typed expected arrays,
 * for fake probed sets -- the full set, each capability taken away in turn, XML capable but not
 * needed, and XML needed but not capable. Nothing touches the disk.
 *
 * <p>The paths hold a space on purpose: a path is one argument-array element, never split.
 */
class PercolatorCommandsTest {

    /* Without their leading slash: see absolute(String). */
    private static final String EXE = "opt/percolator 3/percolator";
    private static final String OUT = "data/run 1/outputs/percolator";
    private static final String PIN = "data/run 1/inputs/pin/merged.pin";

    /** Every Percolator capability: what a fully capable build probes to. */
    static final Set<ToolCapability> FULL =
            EnumSet.of(
                    ToolCapability.XML_OUTPUT,
                    ToolCapability.XML_DECOY_OUTPUT,
                    ToolCapability.PSM_TSV_OUTPUT,
                    ToolCapability.PEPTIDE_TSV_OUTPUT,
                    ToolCapability.DECOY_OUTPUT,
                    ToolCapability.WEIGHTS_OUTPUT,
                    ToolCapability.THREAD_OPTION,
                    ToolCapability.SEED_OPTION,
                    ToolCapability.TEST_FDR_OPTION,
                    ToolCapability.TRAIN_FDR_OPTION,
                    ToolCapability.MAX_ITERATIONS_OPTION);

    /** The product's defaults, as their text. */
    static final Map<PercolatorOption, String> VALUES = values();

    private static Map<PercolatorOption, String> values() {
        Map<PercolatorOption, String> values = new EnumMap<>(PercolatorOption.class);
        values.put(PercolatorOption.SEED, "1");
        values.put(PercolatorOption.NUM_THREADS, "3");
        values.put(PercolatorOption.TEST_FDR, "0.01");
        values.put(PercolatorOption.TRAIN_FDR, "0.02");
        values.put(PercolatorOption.MAX_ITERATIONS, "10");
        return values;
    }

    /*
     * An absolute POSIX path from one written without its leading slash: SpotBugs reports a
     * string constant that looks like an absolute pathname reaching a Path as
     * DMI_HARDCODED_ABSOLUTE_FILENAME, which is sound about production code and wrong about a
     * fixture (TestPaths in tools.comet).
     */
    private static Path absolute(String withoutLeadingSlash) {
        return Path.of("/" + withoutLeadingSlash);
    }

    static PercolatorRequest request(Set<ToolCapability> capabilities, boolean xmlNeeded) {
        return new PercolatorRequest(
                absolute(EXE), absolute(PIN), absolute(OUT), capabilities, xmlNeeded, VALUES);
    }

    private static Set<ToolCapability> without(ToolCapability removed) {
        Set<ToolCapability> set = EnumSet.copyOf(FULL);
        set.remove(removed);
        return set;
    }

    private static PercolatorCommand build(Set<ToolCapability> capabilities, boolean xmlNeeded)
            throws PercolatorRefusedException {
        return PercolatorCommands.build(request(capabilities, xmlNeeded));
    }

    @Test
    @DisplayName("the full probed set, XML needed: every option, -X, the PIN last; no -Z")
    void fullSetWithXml() throws PercolatorRefusedException {
        PercolatorCommand built = build(FULL, true);

        assertAll(
                () ->
                        assertEquals(
                                List.of(
                                        "/opt/percolator 3/percolator",
                                        "--results-psms",
                                        "/data/run 1/outputs/percolator/psms.tsv",
                                        "--results-peptides",
                                        "/data/run 1/outputs/percolator/peptides.tsv",
                                        "--decoy-results-psms",
                                        "/data/run 1/outputs/percolator/decoy-psms.tsv",
                                        "--decoy-results-peptides",
                                        "/data/run 1/outputs/percolator/decoy-peptides.tsv",
                                        "--weights",
                                        "/data/run 1/outputs/percolator/weights.txt",
                                        "-X",
                                        "/data/run 1/outputs/percolator/pout.xml",
                                        "--seed",
                                        "1",
                                        "--num-threads",
                                        "3",
                                        "--testFDR",
                                        "0.01",
                                        "--trainFDR",
                                        "0.02",
                                        "--maxiter",
                                        "10",
                                        "/data/run 1/inputs/pin/merged.pin"),
                                built.command().argv()),
                () -> assertEquals(absolute(OUT), built.command().workingDirectory()),
                () -> assertEquals(Map.of("LANG", "C.UTF-8"), built.command().environment()),
                () -> assertEquals(List.of(), built.notEmitted()),
                () -> assertTrue(built.writesXml()),
                () -> assertTrue(built.writesWeights()),
                () ->
                        assertEquals(
                                List.of(
                                        PercolatorArtefact.TARGET_PSMS,
                                        PercolatorArtefact.TARGET_PEPTIDES,
                                        PercolatorArtefact.DECOY_PSMS,
                                        PercolatorArtefact.DECOY_PEPTIDES,
                                        PercolatorArtefact.WEIGHTS,
                                        PercolatorArtefact.POUT_XML),
                                List.copyOf(built.artefacts().keySet())),
                () ->
                        assertEquals(
                                absolute("data/run 1/outputs/percolator/pout.xml"),
                                built.artefacts().get(PercolatorArtefact.POUT_XML)),
                () ->
                        assertEquals(
                                absolute("data/run 1/outputs/percolator/weights.txt"),
                                built.artefacts().get(PercolatorArtefact.WEIGHTS)),
                () -> assertFalse(built.command().argv().contains("-Z")));
    }

    @Test
    @DisplayName("XML capable but no enabled stage needs it: no -X, no pout.xml, nothing missing")
    void capableButNotNeeded() throws PercolatorRefusedException {
        PercolatorCommand built = build(FULL, false);

        assertAll(
                () ->
                        assertEquals(
                                List.of(
                                        "/opt/percolator 3/percolator",
                                        "--results-psms",
                                        "/data/run 1/outputs/percolator/psms.tsv",
                                        "--results-peptides",
                                        "/data/run 1/outputs/percolator/peptides.tsv",
                                        "--decoy-results-psms",
                                        "/data/run 1/outputs/percolator/decoy-psms.tsv",
                                        "--decoy-results-peptides",
                                        "/data/run 1/outputs/percolator/decoy-peptides.tsv",
                                        "--weights",
                                        "/data/run 1/outputs/percolator/weights.txt",
                                        "--seed",
                                        "1",
                                        "--num-threads",
                                        "3",
                                        "--testFDR",
                                        "0.01",
                                        "--trainFDR",
                                        "0.02",
                                        "--maxiter",
                                        "10",
                                        "/data/run 1/inputs/pin/merged.pin"),
                                built.command().argv()),
                () -> assertFalse(built.writesXml()),
                () -> assertFalse(built.artefacts().containsKey(PercolatorArtefact.POUT_XML)),
                () ->
                        assertEquals(
                                List.of(),
                                built.notEmitted(),
                                "XML was not requested, so its absence is not an omission"),
                () -> assertEquals(Optional.empty(), built.omission(PercolatorOption.XML_OUTPUT)));
    }

    @Test
    @DisplayName("XML needed but not capable (3.09's set): no -X, and the result says why")
    void neededButNotCapable() throws PercolatorRefusedException {
        Set<ToolCapability> threeOhNine = without(ToolCapability.XML_OUTPUT);
        threeOhNine.remove(ToolCapability.XML_DECOY_OUTPUT);

        PercolatorCommand built = build(threeOhNine, true);

        assertAll(
                () ->
                        assertEquals(
                                List.of(
                                        "/opt/percolator 3/percolator",
                                        "--results-psms",
                                        "/data/run 1/outputs/percolator/psms.tsv",
                                        "--results-peptides",
                                        "/data/run 1/outputs/percolator/peptides.tsv",
                                        "--decoy-results-psms",
                                        "/data/run 1/outputs/percolator/decoy-psms.tsv",
                                        "--decoy-results-peptides",
                                        "/data/run 1/outputs/percolator/decoy-peptides.tsv",
                                        "--weights",
                                        "/data/run 1/outputs/percolator/weights.txt",
                                        "--seed",
                                        "1",
                                        "--num-threads",
                                        "3",
                                        "--testFDR",
                                        "0.01",
                                        "--trainFDR",
                                        "0.02",
                                        "--maxiter",
                                        "10",
                                        "/data/run 1/inputs/pin/merged.pin"),
                                built.command().argv()),
                () -> assertFalse(built.writesXml()),
                () ->
                        assertEquals(
                                List.of(
                                        new NotEmitted(
                                                PercolatorOption.XML_OUTPUT,
                                                "the pout XML an enabled downstream stage needs"
                                                        + " (-X) was not requested: the build's"
                                                        + " probed capabilities do not include"
                                                        + " XML_OUTPUT, and an option the build"
                                                        + " was not observed to accept is never"
                                                        + " passed (R-PERC-06)")),
                                built.notEmitted()),
                () ->
                        assertEquals(
                                ToolCapability.XML_OUTPUT,
                                built.omission(PercolatorOption.XML_OUTPUT)
                                        .orElseThrow()
                                        .missing()));
    }

    /*
     * EACH CAPABILITY TAKEN AWAY IN TURN, from the full set with XML needed: the expected array is
     * typed out whole for every case, so a builder that dropped the wrong option, or one too many,
     * fails on the element that differs.
     */
    static Stream<Arguments> eachCapabilityRemoved() {
        return Stream.of(
                Arguments.of(
                        ToolCapability.XML_OUTPUT,
                        List.of(
                                "/opt/percolator 3/percolator",
                                "--results-psms",
                                "/data/run 1/outputs/percolator/psms.tsv",
                                "--results-peptides",
                                "/data/run 1/outputs/percolator/peptides.tsv",
                                "--decoy-results-psms",
                                "/data/run 1/outputs/percolator/decoy-psms.tsv",
                                "--decoy-results-peptides",
                                "/data/run 1/outputs/percolator/decoy-peptides.tsv",
                                "--weights",
                                "/data/run 1/outputs/percolator/weights.txt",
                                "--seed",
                                "1",
                                "--num-threads",
                                "3",
                                "--testFDR",
                                "0.01",
                                "--trainFDR",
                                "0.02",
                                "--maxiter",
                                "10",
                                "/data/run 1/inputs/pin/merged.pin"),
                        List.of(PercolatorOption.XML_OUTPUT)),
                Arguments.of(
                        ToolCapability.XML_DECOY_OUTPUT,
                        List.of(
                                "/opt/percolator 3/percolator",
                                "--results-psms",
                                "/data/run 1/outputs/percolator/psms.tsv",
                                "--results-peptides",
                                "/data/run 1/outputs/percolator/peptides.tsv",
                                "--decoy-results-psms",
                                "/data/run 1/outputs/percolator/decoy-psms.tsv",
                                "--decoy-results-peptides",
                                "/data/run 1/outputs/percolator/decoy-peptides.tsv",
                                "--weights",
                                "/data/run 1/outputs/percolator/weights.txt",
                                "-X",
                                "/data/run 1/outputs/percolator/pout.xml",
                                "--seed",
                                "1",
                                "--num-threads",
                                "3",
                                "--testFDR",
                                "0.01",
                                "--trainFDR",
                                "0.02",
                                "--maxiter",
                                "10",
                                "/data/run 1/inputs/pin/merged.pin"),
                        List.of()),
                Arguments.of(
                        ToolCapability.DECOY_OUTPUT,
                        List.of(
                                "/opt/percolator 3/percolator",
                                "--results-psms",
                                "/data/run 1/outputs/percolator/psms.tsv",
                                "--results-peptides",
                                "/data/run 1/outputs/percolator/peptides.tsv",
                                "--weights",
                                "/data/run 1/outputs/percolator/weights.txt",
                                "-X",
                                "/data/run 1/outputs/percolator/pout.xml",
                                "--seed",
                                "1",
                                "--num-threads",
                                "3",
                                "--testFDR",
                                "0.01",
                                "--trainFDR",
                                "0.02",
                                "--maxiter",
                                "10",
                                "/data/run 1/inputs/pin/merged.pin"),
                        List.of(
                                PercolatorOption.DECOY_RESULTS_PSMS,
                                PercolatorOption.DECOY_RESULTS_PEPTIDES)),
                Arguments.of(
                        ToolCapability.WEIGHTS_OUTPUT,
                        List.of(
                                "/opt/percolator 3/percolator",
                                "--results-psms",
                                "/data/run 1/outputs/percolator/psms.tsv",
                                "--results-peptides",
                                "/data/run 1/outputs/percolator/peptides.tsv",
                                "--decoy-results-psms",
                                "/data/run 1/outputs/percolator/decoy-psms.tsv",
                                "--decoy-results-peptides",
                                "/data/run 1/outputs/percolator/decoy-peptides.tsv",
                                "-X",
                                "/data/run 1/outputs/percolator/pout.xml",
                                "--seed",
                                "1",
                                "--num-threads",
                                "3",
                                "--testFDR",
                                "0.01",
                                "--trainFDR",
                                "0.02",
                                "--maxiter",
                                "10",
                                "/data/run 1/inputs/pin/merged.pin"),
                        List.of(PercolatorOption.WEIGHTS)),
                Arguments.of(
                        ToolCapability.SEED_OPTION,
                        List.of(
                                "/opt/percolator 3/percolator",
                                "--results-psms",
                                "/data/run 1/outputs/percolator/psms.tsv",
                                "--results-peptides",
                                "/data/run 1/outputs/percolator/peptides.tsv",
                                "--decoy-results-psms",
                                "/data/run 1/outputs/percolator/decoy-psms.tsv",
                                "--decoy-results-peptides",
                                "/data/run 1/outputs/percolator/decoy-peptides.tsv",
                                "--weights",
                                "/data/run 1/outputs/percolator/weights.txt",
                                "-X",
                                "/data/run 1/outputs/percolator/pout.xml",
                                "--num-threads",
                                "3",
                                "--testFDR",
                                "0.01",
                                "--trainFDR",
                                "0.02",
                                "--maxiter",
                                "10",
                                "/data/run 1/inputs/pin/merged.pin"),
                        List.of(PercolatorOption.SEED)),
                Arguments.of(
                        ToolCapability.THREAD_OPTION,
                        List.of(
                                "/opt/percolator 3/percolator",
                                "--results-psms",
                                "/data/run 1/outputs/percolator/psms.tsv",
                                "--results-peptides",
                                "/data/run 1/outputs/percolator/peptides.tsv",
                                "--decoy-results-psms",
                                "/data/run 1/outputs/percolator/decoy-psms.tsv",
                                "--decoy-results-peptides",
                                "/data/run 1/outputs/percolator/decoy-peptides.tsv",
                                "--weights",
                                "/data/run 1/outputs/percolator/weights.txt",
                                "-X",
                                "/data/run 1/outputs/percolator/pout.xml",
                                "--seed",
                                "1",
                                "--testFDR",
                                "0.01",
                                "--trainFDR",
                                "0.02",
                                "--maxiter",
                                "10",
                                "/data/run 1/inputs/pin/merged.pin"),
                        List.of(PercolatorOption.NUM_THREADS)),
                Arguments.of(
                        ToolCapability.TEST_FDR_OPTION,
                        List.of(
                                "/opt/percolator 3/percolator",
                                "--results-psms",
                                "/data/run 1/outputs/percolator/psms.tsv",
                                "--results-peptides",
                                "/data/run 1/outputs/percolator/peptides.tsv",
                                "--decoy-results-psms",
                                "/data/run 1/outputs/percolator/decoy-psms.tsv",
                                "--decoy-results-peptides",
                                "/data/run 1/outputs/percolator/decoy-peptides.tsv",
                                "--weights",
                                "/data/run 1/outputs/percolator/weights.txt",
                                "-X",
                                "/data/run 1/outputs/percolator/pout.xml",
                                "--seed",
                                "1",
                                "--num-threads",
                                "3",
                                "--trainFDR",
                                "0.02",
                                "--maxiter",
                                "10",
                                "/data/run 1/inputs/pin/merged.pin"),
                        List.of(PercolatorOption.TEST_FDR)),
                Arguments.of(
                        ToolCapability.TRAIN_FDR_OPTION,
                        List.of(
                                "/opt/percolator 3/percolator",
                                "--results-psms",
                                "/data/run 1/outputs/percolator/psms.tsv",
                                "--results-peptides",
                                "/data/run 1/outputs/percolator/peptides.tsv",
                                "--decoy-results-psms",
                                "/data/run 1/outputs/percolator/decoy-psms.tsv",
                                "--decoy-results-peptides",
                                "/data/run 1/outputs/percolator/decoy-peptides.tsv",
                                "--weights",
                                "/data/run 1/outputs/percolator/weights.txt",
                                "-X",
                                "/data/run 1/outputs/percolator/pout.xml",
                                "--seed",
                                "1",
                                "--num-threads",
                                "3",
                                "--testFDR",
                                "0.01",
                                "--maxiter",
                                "10",
                                "/data/run 1/inputs/pin/merged.pin"),
                        List.of(PercolatorOption.TRAIN_FDR)),
                Arguments.of(
                        ToolCapability.MAX_ITERATIONS_OPTION,
                        List.of(
                                "/opt/percolator 3/percolator",
                                "--results-psms",
                                "/data/run 1/outputs/percolator/psms.tsv",
                                "--results-peptides",
                                "/data/run 1/outputs/percolator/peptides.tsv",
                                "--decoy-results-psms",
                                "/data/run 1/outputs/percolator/decoy-psms.tsv",
                                "--decoy-results-peptides",
                                "/data/run 1/outputs/percolator/decoy-peptides.tsv",
                                "--weights",
                                "/data/run 1/outputs/percolator/weights.txt",
                                "-X",
                                "/data/run 1/outputs/percolator/pout.xml",
                                "--seed",
                                "1",
                                "--num-threads",
                                "3",
                                "--testFDR",
                                "0.01",
                                "--trainFDR",
                                "0.02",
                                "/data/run 1/inputs/pin/merged.pin"),
                        List.of(PercolatorOption.MAX_ITERATIONS)));
    }

    @ParameterizedTest(name = "[{index}] without {0}")
    @MethodSource("eachCapabilityRemoved")
    @DisplayName("each capability taken away drops exactly its own option(s), and says so")
    void eachRemovedInTurn(
            ToolCapability removed, List<String> expected, List<PercolatorOption> leftOut)
            throws PercolatorRefusedException {
        PercolatorCommand built = build(without(removed), true);

        assertAll(
                () -> assertEquals(expected, built.command().argv()),
                () ->
                        assertEquals(
                                leftOut,
                                built.notEmitted().stream().map(NotEmitted::option).toList()),
                () ->
                        assertTrue(
                                built.notEmitted().stream()
                                        .allMatch(left -> left.missing() == removed),
                                "every omission names the capability that was taken away"));
    }

    @Test
    @DisplayName("every omission's reason, hand-typed, when every optional capability is missing")
    void everyReason() throws PercolatorRefusedException {
        PercolatorCommand built =
                build(
                        EnumSet.of(
                                ToolCapability.PSM_TSV_OUTPUT, ToolCapability.PEPTIDE_TSV_OUTPUT),
                        true);
        String rule =
                ", and an option the build was not observed to accept is never passed"
                        + " (R-PERC-06)";

        assertAll(
                () ->
                        assertEquals(
                                List.of(
                                        "/opt/percolator 3/percolator",
                                        "--results-psms",
                                        "/data/run 1/outputs/percolator/psms.tsv",
                                        "--results-peptides",
                                        "/data/run 1/outputs/percolator/peptides.tsv",
                                        "/data/run 1/inputs/pin/merged.pin"),
                                built.command().argv()),
                () ->
                        assertEquals(
                                List.of(
                                        "the decoy PSM table (--decoy-results-psms) was not"
                                                + " requested: the build's probed capabilities do"
                                                + " not include DECOY_OUTPUT"
                                                + rule,
                                        "the decoy peptide table (--decoy-results-peptides) was"
                                                + " not requested: the build's probed capabilities"
                                                + " do not include DECOY_OUTPUT"
                                                + rule,
                                        "the learned weights file (--weights) was not requested:"
                                                + " the build's probed capabilities do not include"
                                                + " WEIGHTS_OUTPUT"
                                                + rule
                                                + "; the weights must come from R-PERC-08's"
                                                + " fallback, which the run records as a"
                                                + " provenance warning",
                                        "the pout XML an enabled downstream stage needs (-X) was"
                                                + " not requested: the build's probed capabilities"
                                                + " do not include XML_OUTPUT"
                                                + rule,
                                        "--seed 1 was requested and not passed, so Percolator"
                                                + " uses its own default: the build's probed"
                                                + " capabilities do not include SEED_OPTION"
                                                + rule,
                                        "--num-threads 3 was requested and not passed, so"
                                                + " Percolator uses its own default: the build's"
                                                + " probed capabilities do not include"
                                                + " THREAD_OPTION"
                                                + rule,
                                        "--testFDR 0.01 was requested and not passed, so"
                                                + " Percolator uses its own default: the build's"
                                                + " probed capabilities do not include"
                                                + " TEST_FDR_OPTION"
                                                + rule,
                                        "--trainFDR 0.02 was requested and not passed, so"
                                                + " Percolator uses its own default: the build's"
                                                + " probed capabilities do not include"
                                                + " TRAIN_FDR_OPTION"
                                                + rule,
                                        "--maxiter 10 was requested and not passed, so"
                                                + " Percolator uses its own default: the build's"
                                                + " probed capabilities do not include"
                                                + " MAX_ITERATIONS_OPTION"
                                                + rule),
                                built.notEmitted().stream().map(NotEmitted::reason).toList()),
                () -> assertFalse(built.writesWeights()),
                () ->
                        assertEquals(
                                ToolCapability.WEIGHTS_OUTPUT,
                                built.omission(PercolatorOption.WEIGHTS).orElseThrow().missing()),
                () ->
                        assertEquals(
                                List.of(
                                        PercolatorArtefact.TARGET_PSMS,
                                        PercolatorArtefact.TARGET_PEPTIDES),
                                List.copyOf(built.artefacts().keySet())));
    }

    @Test
    @DisplayName("a value not requested is neither passed nor reported as left out")
    void onlyRequestedValues() throws PercolatorRefusedException {
        PercolatorRequest seedOnly =
                new PercolatorRequest(
                        absolute(EXE),
                        absolute(PIN),
                        absolute(OUT),
                        EnumSet.of(
                                ToolCapability.PSM_TSV_OUTPUT, ToolCapability.PEPTIDE_TSV_OUTPUT),
                        false,
                        Map.of(PercolatorOption.SEED, "7"));

        PercolatorCommand built = PercolatorCommands.build(seedOnly);

        assertAll(
                () ->
                        assertEquals(
                                List.of(
                                        PercolatorOption.DECOY_RESULTS_PSMS,
                                        PercolatorOption.DECOY_RESULTS_PEPTIDES,
                                        PercolatorOption.WEIGHTS,
                                        PercolatorOption.SEED),
                                built.notEmitted().stream().map(NotEmitted::option).toList()),
                () ->
                        assertEquals(
                                "--seed 7 was requested and not passed, so Percolator uses its own"
                                        + " default: the build's probed capabilities do not"
                                        + " include SEED_OPTION, and an option the build was not"
                                        + " observed to accept is never passed (R-PERC-06)",
                                built.omission(PercolatorOption.SEED).orElseThrow().reason()),
                () ->
                        assertEquals(
                                Optional.empty(), built.omission(PercolatorOption.MAX_ITERATIONS)));
    }

    @Test
    @DisplayName("P9-6: without PSM_TSV_OUTPUT the run is refused, naming the capability")
    void refusedWithoutPsmTable() {
        PercolatorRefusedException refused =
                assertThrows(
                        PercolatorRefusedException.class,
                        () -> build(without(ToolCapability.PSM_TSV_OUTPUT), true));

        assertAll(
                () ->
                        assertEquals(
                                "Percolator was not started: the build at /opt/percolator"
                                        + " 3/percolator cannot write the target results this run"
                                        + " reads -- its probed capabilities do not include"
                                        + " PSM_TSV_OUTPUT (--results-psms). Choose a Percolator"
                                        + " build whose probe observed both PSM_TSV_OUTPUT and"
                                        + " PEPTIDE_TSV_OUTPUT",
                                refused.getMessage()),
                () -> assertEquals(Optional.empty(), refused.file()));
    }

    @Test
    @DisplayName("P9-6: without PEPTIDE_TSV_OUTPUT the run is refused, naming the capability")
    void refusedWithoutPeptideTable() {
        assertEquals(
                "Percolator was not started: the build at /opt/percolator 3/percolator cannot"
                        + " write the target results this run reads -- its probed capabilities do"
                        + " not include PEPTIDE_TSV_OUTPUT (--results-peptides). Choose a"
                        + " Percolator build whose probe observed both PSM_TSV_OUTPUT and"
                        + " PEPTIDE_TSV_OUTPUT",
                assertThrows(
                                PercolatorRefusedException.class,
                                () -> build(without(ToolCapability.PEPTIDE_TSV_OUTPUT), false))
                        .getMessage());
    }

    @Test
    @DisplayName("an empty probed set is refused naming both target tables")
    void refusedWithNothing() {
        assertEquals(
                "Percolator was not started: the build at /opt/percolator 3/percolator cannot"
                        + " write the target results this run reads -- its probed capabilities do"
                        + " not include PSM_TSV_OUTPUT (--results-psms) or PEPTIDE_TSV_OUTPUT"
                        + " (--results-peptides). Choose a Percolator build whose probe observed"
                        + " both PSM_TSV_OUTPUT and PEPTIDE_TSV_OUTPUT",
                assertThrows(PercolatorRefusedException.class, () -> build(Set.of(), true))
                        .getMessage());
    }

    @Test
    @DisplayName("the artefacts' file names, fixed in one place, hand-typed")
    void fileNames() {
        Map<PercolatorArtefact, String> names = new EnumMap<>(PercolatorArtefact.class);
        for (PercolatorArtefact artefact : PercolatorArtefact.values()) {
            names.put(artefact, artefact.fileName());
        }
        Map<PercolatorArtefact, String> expected = new EnumMap<>(PercolatorArtefact.class);
        expected.put(PercolatorArtefact.TARGET_PSMS, "psms.tsv");
        expected.put(PercolatorArtefact.TARGET_PEPTIDES, "peptides.tsv");
        expected.put(PercolatorArtefact.DECOY_PSMS, "decoy-psms.tsv");
        expected.put(PercolatorArtefact.DECOY_PEPTIDES, "decoy-peptides.tsv");
        expected.put(PercolatorArtefact.WEIGHTS, "weights.txt");
        expected.put(PercolatorArtefact.POUT_XML, "pout.xml");

        assertAll(
                () -> assertEquals(expected, names),
                () ->
                        assertEquals(
                                PercolatorOption.XML_OUTPUT, PercolatorArtefact.POUT_XML.option()),
                () -> assertEquals(PercolatorOption.WEIGHTS, PercolatorArtefact.WEIGHTS.option()),
                () ->
                        assertEquals(
                                PercolatorOption.DECOY_RESULTS_PEPTIDES,
                                PercolatorArtefact.DECOY_PEPTIDES.option()));
    }
}
