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

package org.cometgui.workflow.limelight;

import static org.cometgui.workflow.limelight.LimelightFixtures.TARGETS;
import static org.cometgui.workflow.limelight.LimelightFixtures.VERSION;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.tools.CapabilityEvidence;
import org.cometgui.domain.tools.DeclaredCapability;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.percolator.resolution.CompatibilityAction;
import org.cometgui.params.percolator.resolution.CompatibilityAction.Kind;
import org.cometgui.params.percolator.resolution.StageRemedy;
import org.cometgui.provenance.manifest.FileDirection;
import org.cometgui.provenance.manifest.FileRecord;
import org.cometgui.provenance.manifest.ProvenanceStatus;
import org.cometgui.workflow.limelight.LimelightFixtures.Converters;
import org.cometgui.workflow.limelight.LimelightFixtures.FinishedRun;
import org.cometgui.workflow.limelight.LimelightFixtures.ScriptedConverter;
import org.cometgui.workflow.limelight.LimelightFixtures.Writes;
import org.cometgui.workflow.steps.PercolatorProvenance;
import org.cometgui.workflow.steps.RunResultFiles;
import org.cometgui.workflow.storage.LimelightSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A run's Limelight compatibility, read from what the run's own Percolator recorded, and the action
 * offered when it cannot convert (phase 12, unit 4; design decision P12-9; {@code R-LL-01}, {@code
 * R-LL-02}, {@code AC-LL-03}). Each run is laid out on disk with the {@code percolator.*} settings
 * Phase 09 records; the Tool Manager's Percolator offers -- what this computer can obtain now --
 * are set by each test, and deliberately disagree with the run where that proves the answer comes
 * from the run. Every expected sentence is typed out here, Phase 09's recorded reason included.
 */
class LimelightCompatibilityTest {

    /** Phase 09's sentence for {@code -X} left out of a 3.09 command, as it records it. */
    private static final String X_NOT_EMITTED =
            "the pout XML an enabled downstream stage needs (-X) was not requested: the build's"
                    + " probed capabilities do not include XML_OUTPUT, and an option the build was"
                    + " not observed to accept is never passed (R-PERC-06)";

    private static final String NOT_NEEDED =
            "not requested: no enabled downstream stage needs pout XML, so none is written and"
                    + " none is expected";

    private static final String NINE =
            "DECOY_OUTPUT MAX_ITERATIONS_OPTION NO_ANALYTICS_OPTION PEPTIDE_TSV_OUTPUT"
                    + " PSM_TSV_OUTPUT SEED_OPTION TEST_FDR_OPTION THREAD_OPTION TRAIN_FDR_OPTION"
                    + " WEIGHTS_OUTPUT";

    private static final String ALL = NINE + " XML_DECOY_OUTPUT XML_OUTPUT";

    @TempDir private Path root;

    @AfterEach
    void nothingExecuting() {
        LimelightFixtures.EXECUTING.clear();
    }

    /** A run, the Tool Manager, and the conversions over a scripted converter. */
    private record World(
            FinishedRun run,
            Converters tools,
            ScriptedConverter converter,
            LimelightConversions conversions) {

        LimelightCompatibility compatibility() throws IOException {
            return conversions.compatibility(run.layout(), run.identity());
        }

        LimelightReadiness check() throws IOException {
            return conversions.check(
                    run.layout(), run.identity(), LimelightSettings.DEFAULTS, Optional.empty());
        }

        /** Sets what the Tool Manager offers now: the installed converter and these Percolators. */
        void offer(Path jar, ToolOffer... percolators) {
            List<ToolOffer> offers = new ArrayList<>();
            offers.add(LimelightFixtures.installed(VERSION, jar));
            offers.addAll(List.of(percolators));
            tools.offer(offers.toArray(ToolOffer[]::new));
        }
    }

    private Path jar;

    /** A finished run whose Percolator recorded these settings, with or without its pout XML. */
    private World world(Map<String, String> settings, boolean poutRecorded) throws IOException {
        FinishedRun run = LimelightFixtures.finishedRun(root, TARGETS, false);
        List<FileRecord> files = new ArrayList<>();
        files.add(LimelightFixtures.output(RunResultFiles.PEP_XML_ROLE, run.pepXml("k562_3")));
        files.add(LimelightFixtures.output(RunResultFiles.PEP_XML_ROLE, run.pepXml("k562_4")));
        if (poutRecorded) {
            files.add(LimelightFixtures.output(RunResultFiles.POUT_XML_ROLE, run.pout()));
        } else {
            Files.delete(run.pout());
        }
        LimelightFixtures.writeManifest(run.layout(), files, settings);
        jar = LimelightFixtures.converterJar(root.resolve("cache/limelight-converter"));
        Converters tools = new Converters(LimelightFixtures.installed(VERSION, jar));
        ScriptedConverter converter = new ScriptedConverter(Writes.VALID, VERSION, run.layout());
        return new World(run, tools, converter, LimelightFixtures.conversions(tools, converter));
    }

    /** The settings Phase 09 records for a run's Percolator. */
    private static Map<String, String> percolator(
            String version, String origin, String capabilities, String poutXml) {
        Map<String, String> settings = new TreeMap<>();
        settings.put(PercolatorProvenance.VERSION, version);
        settings.put(PercolatorProvenance.ORIGIN, origin);
        settings.put(PercolatorProvenance.CAPABILITIES, capabilities);
        settings.put(PercolatorProvenance.POUT_XML, poutXml);
        settings.put(PercolatorProvenance.BINARY_SHA256, "a".repeat(64));
        return settings;
    }

    /** 3.09, registered local, run with Limelight enabled: -X left out, and recorded why. */
    private static Map<String, String> threeNineWithLimelight() {
        Map<String, String> settings =
                percolator("3.09", "local", NINE, "not requested: " + X_NOT_EMITTED);
        settings.put(PercolatorProvenance.DOWNSTREAM_STAGES, "limelight-conversion");
        settings.put(PercolatorProvenance.NOT_EMITTED_PREFIX + "01.option", "--seed");
        settings.put(PercolatorProvenance.NOT_EMITTED_PREFIX + "01.reason", "not the XML reason");
        settings.put(PercolatorProvenance.NOT_EMITTED_PREFIX + "02.option", "-X");
        settings.put(PercolatorProvenance.NOT_EMITTED_PREFIX + "02.reason", X_NOT_EMITTED);
        return settings;
    }

    private static ToolOffer percolator(
            String version,
            ToolOrigin origin,
            ToolInstallState state,
            List<ToolCapability> capabilities) {
        List<DeclaredCapability> declared = new ArrayList<>();
        for (ToolCapability capability : capabilities) {
            declared.add(
                    new DeclaredCapability(
                            capability, CapabilityEvidence.OBSERVED_BY_EXECUTION, "test fixture"));
        }
        return new ToolOffer(
                ToolName.PERCOLATOR,
                ToolVersion.parse(version),
                origin,
                state,
                declared,
                List.of(),
                Optional.empty(),
                state == ToolInstallState.INSTALLED
                        ? Optional.of(Path.of("/tools/percolator-" + version))
                        : Optional.empty(),
                origin == ToolOrigin.MANAGED
                                && state != ToolInstallState.UNAVAILABLE_ON_THIS_PLATFORM
                        ? OptionalLong.of(2_798_963L)
                        : OptionalLong.empty());
    }

    private static final List<ToolCapability> XML =
            List.of(
                    ToolCapability.XML_OUTPUT,
                    ToolCapability.XML_DECOY_OUTPUT,
                    ToolCapability.PSM_TSV_OUTPUT,
                    ToolCapability.PEPTIDE_TSV_OUTPUT);

    private static final List<ToolCapability> NO_XML =
            List.of(ToolCapability.PSM_TSV_OUTPUT, ToolCapability.PEPTIDE_TSV_OUTPUT);

    private static ToolOffer installed3071() {
        return percolator("3.07.1", ToolOrigin.MANAGED, ToolInstallState.INSTALLED, XML);
    }

    private static ToolOffer local309() {
        return percolator("3.09", ToolOrigin.LOCAL, ToolInstallState.INSTALLED, NO_XML);
    }

    private static void noConverterStarted(World world) throws IOException {
        assertAll(
                () -> assertEquals(List.of(), world.converter().starts(), "no process started"),
                () -> assertEquals(List.of(), world.run().events(), "nothing recorded"));
    }

    @Test
    @DisplayName(
            "3.09 with Limelight enabled: not convertible, in Phase 09's recorded words, before any"
                    + " attempt; the rerun offered is labelled with what resolution returns here")
    void noXmlCapability() throws IOException {
        World world = world(threeNineWithLimelight(), false);
        world.offer(jar, local309(), installed3071());
        String expected =
                "run run-0001's Percolator 3.09 (registered local binary) did not write the"
                        + " Percolator XML the Limelight converter requires: "
                        + X_NOT_EMITTED;

        LimelightCompatibility compatibility = world.compatibility();
        LimelightReadiness readiness = world.check();
        LimelightConversionRefusedException refused =
                assertThrows(
                        LimelightConversionRefusedException.class,
                        () ->
                                world.conversions()
                                        .convert(
                                                world.run().layout(),
                                                world.run().identity(),
                                                LimelightSettings.DEFAULTS,
                                                Optional.empty()));

        CompatibilityAction action = compatibility.action().orElseThrow();
        assertAll(
                () -> assertFalse(compatibility.convertible()),
                () -> assertEquals(new RunId("run-0001"), compatibility.runId()),
                () ->
                        assertEquals(
                                Optional.of("3.09 (registered local binary)"),
                                compatibility.percolator()),
                () -> assertEquals(Optional.of(expected), compatibility.explanation()),
                () -> assertEquals(Kind.RERUN_PERCOLATOR, action.kind()),
                () -> assertEquals("Rerun Percolator with 3.07.1 for Limelight", action.label()),
                () -> assertFalse(action.installFirst()),
                () -> assertEquals(compatibility, readiness.compatibility()),
                () ->
                        assertEquals(
                                expected,
                                readiness
                                        .refusal(LimelightPrerequisite.PERCOLATOR_XML)
                                        .orElseThrow()
                                        .message()),
                () ->
                        assertEquals(
                                Optional.of(world.run().pout()),
                                readiness
                                        .refusal(LimelightPrerequisite.PERCOLATOR_XML)
                                        .orElseThrow()
                                        .file()),
                () ->
                        assertEquals(
                                List.of(LimelightPrerequisite.PERCOLATOR_XML),
                                refused.refusals().stream()
                                        .map(LimelightRefusal::prerequisite)
                                        .toList()),
                () -> assertEquals(expected, refused.refusals().get(0).message()));
        noConverterStarted(world);
    }

    @Test
    @DisplayName(
            "3.09 with Limelight off: its recorded capabilities lack XML_OUTPUT, which Limelight"
                    + " conversion needs")
    void noXmlCapabilityAndLimelightOff() throws IOException {
        World world = world(percolator("3.09", "local", NINE, NOT_NEEDED), false);
        world.offer(jar, local309(), installed3071());

        LimelightCompatibility compatibility = world.compatibility();

        assertEquals(
                Optional.of(
                        "run run-0001's Percolator 3.09 (registered local binary) did not write"
                                + " the Percolator XML the Limelight converter requires: its"
                                + " recorded capabilities do not include XML_OUTPUT, which"
                                + " Limelight conversion needs (the Limelight converter reads the"
                                + " Percolator XML that XML_OUTPUT writes)"),
                compatibility.explanation());
        assertEquals(
                "Rerun Percolator with 3.07.1 for Limelight",
                compatibility.action().orElseThrow().label());
    }

    @Test
    @DisplayName(
            "XML-capable 3.07.1 run with Limelight off: -X was not emitted, the record says why,"
                    + " and the rerun is still offered")
    void capableButLimelightOff() throws IOException {
        World world = world(percolator("3.07.1", "managed", ALL, NOT_NEEDED), false);
        world.offer(jar, local309(), installed3071());

        LimelightCompatibility compatibility = world.compatibility();

        assertEquals(
                Optional.of(
                        "run run-0001's Percolator 3.07.1 did not write the Percolator XML the"
                                + " Limelight converter requires: it was observed to have"
                                + " XML_OUTPUT, but the run records no Percolator XML output "
                                + world.run().pout()
                                + ", and its record of the pout XML reads: "
                                + NOT_NEEDED),
                compatibility.explanation());
        assertEquals(Optional.of("3.07.1"), compatibility.percolator());
        assertTrue(compatibility.action().orElseThrow().offersRerun());
    }

    @Test
    @DisplayName("a run whose record says nothing about pout XML says 'nothing'")
    void capableWithNoPoutRecord() throws IOException {
        Map<String, String> settings = percolator("3.07.1", "managed", ALL, "x");
        settings.remove(PercolatorProvenance.POUT_XML);
        World world = world(settings, false);

        assertTrue(
                world.compatibility()
                        .explanation()
                        .orElseThrow()
                        .endsWith("its record of the pout XML reads: nothing"));
    }

    @Test
    @DisplayName(
            "the pout XML recorded and then deleted: not convertible, the file named, the rerun"
                    + " offered")
    void poutMissing() throws IOException {
        World world = world(percolator("3.07.1", "managed", ALL, "requested"), true);
        world.offer(jar, local309(), installed3071());
        Files.delete(world.run().pout());

        LimelightCompatibility compatibility = world.compatibility();

        String explanation = compatibility.explanation().orElseThrow();
        assertTrue(
                explanation.startsWith(
                        "the Percolator XML "
                                + world.run().pout()
                                + " of run run-0001 does not"
                                + " exist or cannot be read"),
                explanation);
        assertEquals(Optional.of("3.07.1"), compatibility.percolator());
        assertTrue(compatibility.action().orElseThrow().offersRerun());
        assertEquals(
                explanation,
                world.check()
                        .refusal(LimelightPrerequisite.PERCOLATOR_XML)
                        .orElseThrow()
                        .message());
    }

    @Test
    @DisplayName("a pout XML recorded as not complete: not convertible, its status named")
    void poutNotComplete() throws IOException {
        World world = world(percolator("3.07.1", "managed", ALL, "requested"), true);
        Path pout = world.run().pout();
        LimelightFixtures.writeManifest(
                world.run().layout(),
                List.of(
                        new FileRecord(
                                FileDirection.OUTPUT,
                                RunResultFiles.POUT_XML_ROLE,
                                pout,
                                Files.size(pout),
                                LimelightFixtures.CREATED,
                                LimelightFixtures.hashes(pout),
                                ProvenanceStatus.PARTIAL)),
                percolator("3.07.1", "managed", ALL, "requested"));

        LimelightCompatibility compatibility = world.compatibility();

        assertEquals(
                Optional.of(
                        "the Percolator XML "
                                + pout
                                + " of run run-0001 is recorded partial, not complete, so it is not"
                                + " converted"),
                compatibility.explanation());
        assertTrue(compatibility.action().isPresent());
    }

    @Test
    @DisplayName(
            "the pout XML present, complete and unchanged: convertible -- even though the only"
                    + " Percolator offered now cannot write XML (read from the run, not the"
                    + " selection)")
    void poutPresentWhateverIsOfferedNow() throws IOException {
        World world = world(percolator("3.07.1", "managed", ALL, "requested"), true);
        world.offer(jar, local309());

        LimelightCompatibility compatibility = world.compatibility();

        assertTrue(compatibility.convertible());
        assertEquals(Optional.empty(), compatibility.explanation());
        assertEquals(Optional.empty(), compatibility.action());
        assertEquals(Optional.of("3.07.1"), compatibility.percolator());
        LimelightReadiness readiness = world.check();
        assertTrue(readiness.ready(), () -> readiness.refusals().toString());
        assertEquals(compatibility, readiness.compatibility());
    }

    @Test
    @DisplayName(
            "a 3.09 run is not convertible even when an XML-capable 3.07.1 is selected and"
                    + " installed now (read from the run, not the selection)")
    void notFromTheSelection() throws IOException {
        World world = world(threeNineWithLimelight(), false);
        world.offer(jar, installed3071());

        assertFalse(world.compatibility().convertible());
        assertFalse(world.check().ready());
    }

    @Test
    @DisplayName(
            "R-LL-02: no XML-capable build for this platform -- registration offered, no rerun,"
                    + " and the message says plainly that no managed build exists")
    void noManagedBuildForThisPlatform() throws IOException {
        World world = world(threeNineWithLimelight(), false);
        world.offer(
                jar,
                local309(),
                percolator(
                        "3.07.1",
                        ToolOrigin.MANAGED,
                        ToolInstallState.UNAVAILABLE_ON_THIS_PLATFORM,
                        XML));

        LimelightCompatibility compatibility = world.compatibility();

        CompatibilityAction action = compatibility.action().orElseThrow();
        assertAll(
                () -> assertFalse(compatibility.convertible()),
                () -> assertEquals(Kind.REGISTER_LOCAL_BINARY, action.kind()),
                () -> assertFalse(action.offersRerun()),
                () -> assertEquals(Optional.empty(), action.build()),
                () ->
                        assertEquals(
                                "Register a local Percolator binary for Limelight", action.label()),
                () ->
                        assertTrue(
                                action.explanation()
                                        .startsWith(
                                                "No managed Percolator build that can write"
                                                        + " XML_OUTPUT exists for this platform,"
                                                        + " so CometGUI cannot install one and"
                                                        + " offers no Percolator rerun for"
                                                        + " Limelight."),
                                action::explanation),
                () ->
                        assertEquals(
                                List.of(
                                        StageRemedy.REGISTER_LOCAL_BINARY,
                                        StageRemedy.CONVERT_ON_SUPPORTED_PLATFORM),
                                action.remedies()));
        noConverterStarted(world);
    }

    @Test
    @DisplayName(
            "a hypothetical XML-capable 3.08.2, managed and not installed: the label names 3.08.2"
                    + " and the build is installed first")
    void theLabelIsWhatResolutionReturns() throws IOException {
        World world = world(threeNineWithLimelight(), false);
        world.offer(
                jar,
                local309(),
                installed3071(),
                percolator("3.08.2", ToolOrigin.MANAGED, ToolInstallState.NOT_INSTALLED, XML));

        CompatibilityAction action = world.compatibility().action().orElseThrow();

        assertEquals("Rerun Percolator with 3.08.2 for Limelight", action.label());
        assertEquals("3.08.2", action.build().orElseThrow().version().text());
        assertTrue(action.installFirst());
    }

    @Test
    @DisplayName("a Comet-only run records no Percolator: said so, and the rerun offered")
    void noPercolatorRecorded() throws IOException {
        World world = world(Map.of(), false);
        world.offer(jar, installed3071());

        LimelightCompatibility compatibility = world.compatibility();

        assertEquals(
                Optional.of(
                        "run run-0001's provenance records no Percolator execution, so it has no"
                                + " Percolator XML "
                                + world.run().pout()
                                + ", which the Limelight converter requires"),
                compatibility.explanation());
        assertEquals(Optional.empty(), compatibility.percolator());
        assertTrue(compatibility.action().orElseThrow().offersRerun());
    }

    @Test
    @DisplayName("an executing run is not decided, and offered nothing")
    void executing() throws IOException {
        World world = world(threeNineWithLimelight(), false);
        LimelightFixtures.EXECUTING.add(new RunId("run-0001"));

        LimelightCompatibility compatibility = world.compatibility();

        assertEquals(
                Optional.of(
                        "run run-0001 is executing: its results are not final and the workflow"
                                + " engine holds its event log. Convert it when the run has"
                                + " ended."),
                compatibility.explanation());
        assertEquals(Optional.empty(), compatibility.action());
        assertEquals(compatibility, world.check().compatibility());
    }

    @Test
    @DisplayName("a run without a provenance record is not convertible, and offered nothing")
    void noProvenance() throws IOException {
        World world = world(threeNineWithLimelight(), false);
        Path manifest = world.run().layout().provenanceJsonFile();
        Files.delete(manifest);

        LimelightCompatibility compatibility = world.compatibility();

        assertEquals(
                Optional.of(
                        "run run-0001 has no provenance record "
                                + manifest
                                + ", so what it produced cannot be checked and nothing of it is"
                                + " converted"),
                compatibility.explanation());
        assertEquals(Optional.empty(), compatibility.action());
        assertEquals(Optional.empty(), compatibility.percolator());
    }

    @Test
    @DisplayName("the record: no action for a convertible run, no blank explanation")
    void theRecord() {
        RunId id = new RunId("run-0001");
        CompatibilityAction action =
                CompatibilityAction.forStage(
                        List.of(installed3071()),
                        org.cometgui.params.percolator.resolution.DownstreamStage
                                .LIMELIGHT_CONVERSION);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new LimelightCompatibility(
                                id, Optional.empty(), Optional.empty(), Optional.of(action)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new LimelightCompatibility(
                                id, Optional.empty(), Optional.of(" "), Optional.empty()));
        assertTrue(
                new LimelightCompatibility(id, Optional.empty(), Optional.empty(), Optional.empty())
                        .convertible());
    }
}
