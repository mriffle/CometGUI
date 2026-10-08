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

package org.cometgui.workflow.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.tools.CapabilityEvidence;
import org.cometgui.domain.tools.DeclaredCapability;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.params.percolator.resolution.DownstreamStage;
import org.cometgui.params.percolator.resolution.PercolatorResolver;
import org.cometgui.params.percolator.schema.PercolatorSetting;
import org.cometgui.params.percolator.validation.TestFdr;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.tools.percolator.PercolatorArtefact;
import org.cometgui.tools.percolator.PercolatorCommand;
import org.cometgui.tools.percolator.PercolatorCommands;
import org.cometgui.tools.percolator.PercolatorOption;
import org.cometgui.tools.percolator.PercolatorRefusedException;
import org.cometgui.workflow.engine.StepFailedException;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.testing.TestPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Percolator request's values and the pure parts of the Percolator steps: the selection's and
 * the choice's rules, the plan, the settings document, the option map, the roles and the provenance
 * settings. Every expected value is typed out here.
 */
class PercolatorValuesTest {

    private static final Path EXE = TestPaths.absolute("opt/percolator/bin/percolator");

    private static final String SHA = "ab".repeat(32);

    private static ToolOffer offer(
            Path executable, String version, ToolOrigin origin, Set<ToolCapability> observed) {
        return FakePercolator.offer(executable, version, origin, observed, List.of());
    }

    private static PercolatorChoice choice(ToolOffer selected, Set<DownstreamStage> stages) {
        return new PercolatorChoice(
                new PercolatorSelection(selected, selected.installedPath().orElseThrow(), SHA),
                PercolatorSettings.defaults(),
                stages,
                PercolatorResolver.resolve(List.of(selected), stages));
    }

    @Test
    @DisplayName("a selection is an installed Percolator at its installed path, with a SHA-256")
    void selectionRules() {
        ToolOffer installed = offer(EXE, "3.07.1", ToolOrigin.MANAGED, FakePercolator.EVERY);
        PercolatorSelection selection =
                new PercolatorSelection(
                        installed,
                        TestPaths.absolute("opt/percolator/bin/../bin/percolator"),
                        "AB".repeat(32));
        assertEquals(EXE, selection.executable());
        assertEquals(SHA, selection.sha256());
        assertEquals(ToolVersion.parse("3.07.1"), selection.version());
        assertTrue(selection.managed());
        assertEquals("managed", selection.originId());
        assertEquals(FakePercolator.EVERY, selection.capabilities());
        PercolatorSelection local =
                new PercolatorSelection(offer(EXE, "3.09", ToolOrigin.LOCAL, Set.of()), EXE, SHA);
        assertFalse(local.managed());
        assertEquals("local", local.originId());

        ToolOffer comet =
                new ToolOffer(
                        ToolName.COMET,
                        ToolVersion.parse("2026.03.0"),
                        ToolOrigin.LOCAL,
                        ToolInstallState.INSTALLED,
                        List.of(),
                        List.of(),
                        Optional.empty(),
                        Optional.of(EXE),
                        OptionalLong.empty());
        assertEquals(
                "a Percolator selection needs a Percolator offer, not one of comet",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new PercolatorSelection(comet, EXE, SHA))
                        .getMessage());
        ToolOffer notInstalled =
                new ToolOffer(
                        ToolName.PERCOLATOR,
                        ToolVersion.parse("3.07.1"),
                        ToolOrigin.MANAGED,
                        ToolInstallState.NOT_INSTALLED,
                        List.of(),
                        List.of(),
                        Optional.empty(),
                        Optional.empty(),
                        OptionalLong.of(1));
        assertEquals(
                "only an installed Percolator can run, but Percolator 3.07.1 is NOT_INSTALLED",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new PercolatorSelection(notInstalled, EXE, SHA))
                        .getMessage());
        assertEquals(
                "the Percolator executable must be an absolute path, not \"bin/percolator\"",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new PercolatorSelection(
                                                installed, Path.of("bin/percolator"), SHA))
                        .getMessage());
        assertEquals(
                "the Percolator executable /elsewhere/percolator is not the offer's installed path"
                        + " /opt/percolator/bin/percolator",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new PercolatorSelection(
                                                installed,
                                                TestPaths.absolute("elsewhere/percolator"),
                                                SHA))
                        .getMessage());
        assertEquals(
                "a SHA-256 is 64 hexadecimal characters, not \"abc\"",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new PercolatorSelection(installed, EXE, "abc"))
                        .getMessage());
        assertThrows(
                IllegalArgumentException.class,
                () -> new PercolatorSelection(installed, EXE, "g".repeat(64)));
        assertThrows(NullPointerException.class, () -> new PercolatorSelection(null, EXE, SHA));
        assertThrows(
                NullPointerException.class, () -> new PercolatorSelection(installed, null, SHA));
        assertThrows(
                NullPointerException.class, () -> new PercolatorSelection(installed, EXE, null));
    }

    @Test
    @DisplayName("only observed capabilities are the selection's: an inferred claim is not")
    void inferredClaimsAreNotCapabilities() {
        ToolOffer offer =
                new ToolOffer(
                        ToolName.PERCOLATOR,
                        ToolVersion.parse("3.07.1"),
                        ToolOrigin.LOCAL,
                        ToolInstallState.INSTALLED,
                        List.of(
                                new DeclaredCapability(
                                        ToolCapability.PSM_TSV_OUTPUT,
                                        CapabilityEvidence.OBSERVED_BY_EXECUTION,
                                        "probed"),
                                new DeclaredCapability(
                                        ToolCapability.XML_OUTPUT,
                                        CapabilityEvidence.INFERRED_FROM_ARTEFACT_BYTES,
                                        "bytes")),
                        List.of(),
                        Optional.empty(),
                        Optional.of(EXE),
                        OptionalLong.empty());
        assertEquals(
                Set.of(ToolCapability.PSM_TSV_OUTPUT),
                new PercolatorSelection(offer, EXE, SHA).capabilities());
    }

    @Test
    @DisplayName(
            "a choice: the resolution's stages must be the run's; pout XML is needed exactly when"
                    + " an enabled stage requires XML_OUTPUT; the default is recognised")
    void choiceRules() {
        ToolOffer full = offer(EXE, "3.07.1", ToolOrigin.MANAGED, FakePercolator.EVERY);
        ToolOffer other =
                offer(
                        TestPaths.absolute("opt/other/percolator"),
                        "3.09",
                        ToolOrigin.LOCAL,
                        Set.of());
        PercolatorChoice limelight = choice(full, EnumSet.of(DownstreamStage.LIMELIGHT_CONVERSION));
        assertTrue(limelight.xmlNeeded());
        assertTrue(limelight.isResolvedDefault());
        assertEquals(EnumSet.of(DownstreamStage.LIMELIGHT_CONVERSION), limelight.enabledStages());
        assertThrows(
                UnsupportedOperationException.class,
                () -> limelight.enabledStages().add(DownstreamStage.LIMELIGHT_CONVERSION));
        PercolatorChoice none = choice(full, Set.of());
        assertFalse(none.xmlNeeded());

        PercolatorChoice notTheDefault =
                new PercolatorChoice(
                        new PercolatorSelection(
                                other, TestPaths.absolute("opt/other/percolator"), SHA),
                        PercolatorSettings.defaults(),
                        Set.of(),
                        PercolatorResolver.resolve(List.of(full), Set.of()));
        assertFalse(notTheDefault.isResolvedDefault());
        PercolatorChoice nothingResolved =
                new PercolatorChoice(
                        new PercolatorSelection(full, EXE, SHA),
                        PercolatorSettings.defaults(),
                        Set.of(),
                        PercolatorResolver.resolve(List.of(), Set.of()));
        assertFalse(nothingResolved.isResolvedDefault());

        IllegalArgumentException mismatch =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new PercolatorChoice(
                                        new PercolatorSelection(full, EXE, SHA),
                                        PercolatorSettings.defaults(),
                                        EnumSet.of(DownstreamStage.LIMELIGHT_CONVERSION),
                                        PercolatorResolver.resolve(List.of(full), Set.of())));
        assertEquals(
                "the resolution was computed for the enabled stages [], but the run enables"
                        + " [LIMELIGHT_CONVERSION]",
                mismatch.getMessage());
    }

    @Test
    @DisplayName(
            "a search request: Comet-only by default; withPercolator adds the choice and changes"
                    + " nothing else")
    void searchRequest(@TempDir Path directory) throws IOException {
        FakeSearch fake = FakeSearch.create(directory);
        SearchRequest comet =
                fake.request(
                        org.cometgui.params.comet.model.DecoySource.COMET_INTERNAL_CONCATENATED,
                        IndexMode.NONE);
        assertEquals(Optional.empty(), comet.percolator());
        PercolatorChoice choice =
                choice(offer(EXE, "3.07.1", ToolOrigin.MANAGED, FakePercolator.EVERY), Set.of());
        SearchRequest both = comet.withPercolator(choice);
        assertEquals(Optional.of(choice), both.percolator());
        assertEquals(comet.model(), both.model());
        assertEquals(comet.spectra(), both.spectra());
        assertEquals(comet.comet(), both.comet());
        assertEquals(comet.indexMode(), both.indexMode());
        assertThrows(
                NullPointerException.class,
                () ->
                        new SearchRequest(
                                comet.model(),
                                comet.spectra(),
                                comet.comet(),
                                IndexMode.NONE,
                                null));
    }

    @Test
    @DisplayName(
            "the plan with Percolator: the three Percolator steps after merge-pin, and not"
                    + " finalise-results (Phase 10), which nothing planned requires")
    void plans() {
        assertEquals(
                "Plan[validate-configuration, resolve-comet, resolve-percolator,"
                        + " serialise-comet-params, hash-inputs, run-comet, validate-comet-outputs,"
                        + " merge-pin, run-percolator, parse-percolator, finalise-provenance]",
                CometWorkflow.planFor(IndexMode.NONE, true).toString());
        assertEquals(
                "Plan[validate-configuration, resolve-comet, resolve-percolator,"
                        + " serialise-comet-params, hash-inputs, build-comet-index, run-comet,"
                        + " validate-comet-outputs, merge-pin, run-percolator, parse-percolator,"
                        + " finalise-provenance]",
                CometWorkflow.planFor(IndexMode.FRAGMENT_ION, true).toString());
        assertFalse(
                CometWorkflow.planFor(IndexMode.NONE, true).contains(EngineStep.FINALISE_RESULTS));
        assertEquals(
                CometWorkflow.planFor(IndexMode.NONE).steps(),
                CometWorkflow.planFor(IndexMode.NONE, false).steps());
        assertFalse(
                CometWorkflow.planFor(IndexMode.NONE, false)
                        .contains(EngineStep.RESOLVE_PERCOLATOR));
    }

    @Test
    @DisplayName("the settings document, canonical: no stage enabled, and changed settings")
    void settingsDocument() {
        assertEquals(
                "{\n"
                        + "  \"schemaVersion\": 1,\n"
                        + "  \"settings\": {\n"
                        + "    \"test-fdr\": \"0.05\",\n"
                        + "    \"train-fdr\": \"0.01\",\n"
                        + "    \"random-seed\": \"9001\",\n"
                        + "    \"maximum-iterations\": \"10\",\n"
                        + "    \"thread-count\": \"7\"\n"
                        + "  },\n"
                        + "  \"downstreamStages\": []\n"
                        + "}\n",
                PercolatorSettingsFile.render(
                        PercolatorSettings.defaults()
                                .withTestFdr(new TestFdr(new BigDecimal("0.05")))
                                .withRandomSeed(9001)
                                .withThreadCount(7),
                        Set.of()));
    }

    @Test
    @DisplayName(
            "the settings file is written once and read back; a second write and a changed or"
                    + " missing file are refused, naming the file and both digests")
    void settingsFileWrittenOnce(@TempDir Path directory) throws IOException, StepFailedException {
        CachingHashService hashes = FakeSearch.hashes();
        Path file = directory.resolve("percolator-settings.json");
        String text = PercolatorSettingsFile.render(PercolatorSettings.defaults(), Set.of());
        PercolatorSettingsFile.Archived archived =
                PercolatorSettingsFile.writeOnce(file, text, hashes);
        assertEquals(text, Files.readString(file, StandardCharsets.UTF_8));
        assertEquals(text, archived.text());
        assertEquals(text.getBytes(StandardCharsets.UTF_8).length, archived.size());
        assertEquals(RealComet.sha256(file), archived.hashes().sha256());

        FileAlreadyExistsException again =
                assertThrows(
                        FileAlreadyExistsException.class,
                        () -> PercolatorSettingsFile.writeOnce(file, "{}\n", hashes));
        assertEquals(
                file
                        + ": a run's Percolator settings are written once, when the run is prepared"
                        + " (R-RUN-06)",
                again.getMessage());
        assertEquals(text, Files.readString(file, StandardCharsets.UTF_8), "not touched");

        PercolatorSettingsFile.verify(file, archived, hashes);
        Files.writeString(file, text.replace("0.01", "0.02"), StandardCharsets.UTF_8);
        StepFailedException changed =
                assertThrows(
                        StepFailedException.class,
                        () -> PercolatorSettingsFile.verify(file, archived, hashes));
        assertEquals(
                "the run's archived Percolator settings file "
                        + file
                        + " has SHA-256 "
                        + RealComet.sha256(file)
                        + " ("
                        + archived.size()
                        + " bytes), but the run recorded "
                        + archived.hashes().sha256()
                        + " ("
                        + archived.size()
                        + " bytes) when it was written; a run's settings are written once, so"
                        + " Percolator is not run",
                changed.getMessage());
        Files.delete(file);
        assertEquals(
                "the run's archived Percolator settings file " + file + " no longer exists",
                assertThrows(
                                StepFailedException.class,
                                () -> PercolatorSettingsFile.verify(file, archived, hashes))
                        .getMessage());
    }

    @Test
    @DisplayName(
            "each setting reaches Percolator through the option whose capability is the"
                    + " setting's own, with its text")
    void optionsFollowCapabilities() {
        for (PercolatorSetting setting : PercolatorSetting.values()) {
            assertEquals(
                    setting.requiredCapability(),
                    PercolatorRun.optionOf(setting).capability(),
                    setting.id());
        }
        Map<PercolatorOption, String> expected = new EnumMap<>(PercolatorOption.class);
        expected.put(PercolatorOption.SEED, "9001");
        expected.put(PercolatorOption.NUM_THREADS, "7");
        expected.put(PercolatorOption.TEST_FDR, "0.05");
        expected.put(PercolatorOption.TRAIN_FDR, "0.01");
        expected.put(PercolatorOption.MAX_ITERATIONS, "10");
        assertEquals(
                expected,
                PercolatorRun.values(
                        PercolatorSettings.defaults()
                                .withTestFdr(new TestFdr(new BigDecimal("0.05")))
                                .withRandomSeed(9001)
                                .withThreadCount(7)));
    }

    @Test
    @DisplayName("roles and paths, typed out")
    void rolesAndPaths() {
        Map<PercolatorArtefact, String> roles = new EnumMap<>(PercolatorArtefact.class);
        for (PercolatorArtefact artefact : PercolatorArtefact.values()) {
            roles.put(artefact, PercolatorDeclarations.roleOf(artefact));
        }
        assertEquals(
                Map.of(
                        PercolatorArtefact.TARGET_PSMS, "percolator-psms",
                        PercolatorArtefact.TARGET_PEPTIDES, "percolator-peptides",
                        PercolatorArtefact.DECOY_PSMS, "percolator-decoy-psms",
                        PercolatorArtefact.DECOY_PEPTIDES, "percolator-decoy-peptides",
                        PercolatorArtefact.WEIGHTS, "percolator-weights",
                        PercolatorArtefact.POUT_XML, "percolator-pout-xml"),
                roles);
        RunLayout layout = new RunLayout(TestPaths.absolute("p/runs/r1"));
        assertEquals(
                TestPaths.absolute("p/runs/r1/parameters/percolator-settings.json"),
                PercolatorDeclarations.settingsFile(layout));
        assertEquals(
                "parameters/percolator-settings.json",
                PercolatorDeclarations.settingsRelativePath());
        assertEquals(
                TestPaths.absolute("p/runs/r1/outputs/percolator"),
                PercolatorDeclarations.outputDirectory(layout));
    }

    @Test
    @DisplayName(
            "provenance settings when resolution selected nothing and the build lacks every"
                    + " optional capability: 'none', every omission numbered, R-PERC-08's warning")
    void provenanceOfALeanBuild() throws PercolatorRefusedException {
        Set<ToolCapability> lean =
                EnumSet.of(ToolCapability.PSM_TSV_OUTPUT, ToolCapability.PEPTIDE_TSV_OUTPUT);
        ToolOffer bare = offer(EXE, "3.10", ToolOrigin.LOCAL, lean);
        PercolatorChoice choice =
                new PercolatorChoice(
                        new PercolatorSelection(bare, EXE, SHA),
                        PercolatorSettings.defaults(),
                        Set.of(),
                        PercolatorResolver.resolve(List.of(), Set.of()));
        PercolatorCommand command =
                PercolatorCommands.build(
                        PercolatorRun.request(
                                EXE,
                                TestPaths.absolute("r/merged.pin"),
                                TestPaths.absolute("r/out"),
                                choice));
        Map<String, String> settings =
                PercolatorProvenance.settings(choice, command, "cd".repeat(32), "DECOY_");
        assertEquals("none", settings.get(PercolatorProvenance.RESOLVED_DEFAULT));
        assertEquals("user-choice", settings.get(PercolatorProvenance.SELECTION));
        assertEquals("none", settings.get(PercolatorProvenance.DOWNSTREAM_STAGES));
        assertEquals("not-passed", settings.get(PercolatorProvenance.SEED));
        assertEquals(
                "PEPTIDE_TSV_OUTPUT PSM_TSV_OUTPUT",
                settings.get(PercolatorProvenance.CAPABILITIES));
        assertEquals("3.10", settings.get(PercolatorProvenance.VERSION));
        assertEquals("local", settings.get(PercolatorProvenance.ORIGIN));
        assertEquals(SHA, settings.get(PercolatorProvenance.BINARY_SHA256));
        assertEquals("cd".repeat(32), settings.get(PercolatorProvenance.SETTINGS_SHA256));
        assertEquals("DECOY_", settings.get(PercolatorProvenance.DECOY_PREFIX));
        List<String> omitted =
                List.of(
                        "--decoy-results-psms",
                        "--decoy-results-peptides",
                        "--weights",
                        "--seed",
                        "--num-threads",
                        "--testFDR",
                        "--trainFDR",
                        "--maxiter",
                        "--no-analytics");
        for (int index = 0; index < omitted.size(); index++) {
            String prefix = "percolator.not-emitted.0" + (index + 1) + ".";
            assertEquals(omitted.get(index), settings.get(prefix + "option"), prefix);
            assertEquals(command.notEmitted().get(index).reason(), settings.get(prefix + "reason"));
        }
        assertFalse(settings.containsKey("percolator.not-emitted.10.option"));
        assertTrue(
                settings.get(PercolatorProvenance.WEIGHTS_WARNING)
                        .startsWith(
                                "R-PERC-08: no learned weights file was written, so the weights are"
                                        + " not available from a file: the learned weights file"
                                        + " (--weights) was not requested"),
                settings.get(PercolatorProvenance.WEIGHTS_WARNING));
        assertEquals(
                "not requested: no enabled downstream stage needs pout XML, so none is written"
                        + " and none is expected",
                settings.get(PercolatorProvenance.POUT_XML));
        assertFalse(
                settings.keySet().stream().anyMatch(key -> key.startsWith("percolator.skipped.")));
        assertFalse(
                settings.keySet().stream()
                        .anyMatch(key -> key.startsWith(PercolatorProvenance.ADVISORY_PREFIX)));
        assertEquals(
                Optional.empty(),
                PercolatorProvenance.weightsWarning(
                        PercolatorCommands.build(
                                PercolatorRun.request(
                                        EXE,
                                        TestPaths.absolute("r/merged.pin"),
                                        TestPaths.absolute("r/out"),
                                        choice(
                                                offer(
                                                        EXE,
                                                        "3.07.1",
                                                        ToolOrigin.MANAGED,
                                                        FakePercolator.EVERY),
                                                Set.of())))));
        for (String key : settings.keySet()) {
            assertTrue(
                    key.matches(
                            org.cometgui.provenance.manifest.ProvenanceSchema.SETTINGS_KEY_PATTERN),
                    key);
        }
    }

    @Test
    @DisplayName(
            "a prepared run plans run-percolator exactly when it has a Percolator half; the"
                    + " refusal names the plan")
    void aPreparedRunsPlanMatchesItsHalf() {
        IllegalArgumentException refused =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new PreparedRun(
                                        null,
                                        null,
                                        CometWorkflow.planFor(IndexMode.NONE, true),
                                        null,
                                        null,
                                        null,
                                        Map.of(),
                                        Optional.empty()));
        assertEquals(
                "a run plans run-percolator exactly when it has a Percolator half; plan"
                        + " Plan[validate-configuration, resolve-comet, resolve-percolator,"
                        + " serialise-comet-params, hash-inputs, run-comet, validate-comet-outputs,"
                        + " merge-pin, run-percolator, parse-percolator, finalise-provenance],"
                        + " Percolator absent",
                refused.getMessage());
    }

    @Test
    @DisplayName(
            "a raw output on a file system with neither POSIX permissions nor a DOS attribute"
                    + " cannot be made read-only, and says so")
    void readOnlyNeedsAnAttributeView(@TempDir Path directory) throws IOException {
        Path zip = directory.resolve("outputs.zip");
        try (java.nio.file.FileSystem zipfs =
                java.nio.file.FileSystems.newFileSystem(zip, Map.of("create", "true"))) {
            Path file = Files.writeString(zipfs.getPath("psms.tsv"), "x\n");
            IOException refused =
                    assertThrows(IOException.class, () -> PercolatorSteps.makeReadOnly(file));
            assertEquals(
                    "the raw Percolator output psms.tsv cannot be made read-only: its file system"
                            + " offers neither POSIX permissions nor a DOS read-only attribute",
                    refused.getMessage());
        }
    }
}
