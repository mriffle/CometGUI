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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.domain.tools.CapabilityEvidence;
import org.cometgui.domain.tools.DeclaredCapability;
import org.cometgui.domain.tools.ToolAdvisory;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.provenance.manifest.ManifestReader;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ToolRecord;
import org.cometgui.tools.percolator.SyntheticPin;
import org.cometgui.workflow.engine.DeclaredFile;
import org.cometgui.workflow.engine.ReuseRefusedException;
import org.cometgui.workflow.engine.RunRequest;
import org.cometgui.workflow.engine.RunResult;
import org.cometgui.workflow.engine.StepAction;
import org.cometgui.workflow.engine.StepContext;
import org.cometgui.workflow.engine.StepDeclaration;
import org.cometgui.workflow.engine.StepStateListener;
import org.cometgui.workflow.provenance.ProvenanceReader;
import org.cometgui.workflow.provenance.ProvenanceView;
import org.cometgui.workflow.provenance.WarningSource;
import org.cometgui.workflow.state.EngineStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The tool warnings {@link ToolWarnings} words, through the real engine and the real process
 * service into {@code provenance.json} -- read back with {@link ManifestReader} -- and on into the
 * Provenance section's read model: Comet's advisories and manifest/probe discrepancies ({@code
 * R-TOOL-07}) on Comet's tool records, Percolator's discrepancies on Percolator's, each shown once
 * under its own source. And a run with nothing to warn about records nothing.
 *
 * <p>Comet is a stand-in shell script that exits 0 and writes nothing, so the run fails at {@code
 * validate-comet-outputs} after both Comet invocations have been recorded; Percolator is {@link
 * FakePercolator} after the Comet steps are replaced, as {@code PercolatorStepTest} runs it. Every
 * expected sentence is typed out in {@link ToolWarningsTest}.
 *
 * <p>POSIX only: both stand-ins are shell scripts.
 */
@EnabledOnOs(
        value = {OS.LINUX, OS.MAC},
        disabledReason = "the stand-in Comet and Percolator are POSIX shell scripts")
class ToolWarningsRunTest {

    /** Does nothing and declares nothing: stands in for a Comet step. */
    record Nothing() implements StepAction {

        @Override
        public StepDeclaration declaration() {
            return StepDeclaration.NOTHING;
        }

        @Override
        public void execute(StepContext context) {}
    }

    /** Stands in for {@code merge-pin}: writes the synthetic merged PIN, declared as its output. */
    record WriteMergedPin(Path merged) implements StepAction {

        @Override
        public StepDeclaration declaration() {
            return new StepDeclaration(
                    List.of(DeclaredFile.output(RunDeclarations.MERGED_PIN, merged)), List.of());
        }

        @Override
        public void execute(StepContext context) throws IOException {
            Files.writeString(
                    merged,
                    SyntheticPin.of(64, SyntheticPin.PROBE_SEED),
                    StandardCharsets.US_ASCII);
        }
    }

    private static ProvenanceReader reader() {
        return new ProvenanceReader(
                SecretRedactor::patternsOnly, new CachingHashService(new StreamingHashService()));
    }

    /** A Comet that runs, says nothing and writes nothing: exit 0. */
    static void exitZero(Path executable) throws IOException {
        Files.writeString(executable, "#!/bin/sh\nexit 0\n", StandardCharsets.US_ASCII);
        Files.setPosixFilePermissions(executable, PosixFilePermissions.fromString("rwx------"));
    }

    private static CometSelection comet(
            FakeSearch search, List<ToolAdvisory> advisories, List<DeclaredCapability> claims)
            throws IOException {
        return new CometSelection(
                search.executable(),
                ToolVersion.parse(RealComet.NEWER),
                search.executableSha256(),
                true,
                Optional.empty(),
                advisories,
                claims);
    }

    private static List<ToolRecord> toolsNamed(ProvenanceManifest manifest, String name) {
        return manifest.tools().stream().filter(tool -> tool.name().equals(name)).toList();
    }

    private static Map<String, String> warningSettings(ProvenanceManifest manifest) {
        Map<String, String> found = new TreeMap<>();
        for (Map.Entry<String, String> setting : manifest.settings().entrySet()) {
            String key = setting.getKey();
            if (key.startsWith("comet.advisory.")
                    || key.startsWith("comet.probe-discrepancy.")
                    || key.startsWith("percolator.probe-discrepancy.")) {
                found.put(key, setting.getValue());
            }
        }
        return found;
    }

    private static List<ProvenanceView.Warning> of(
            List<ProvenanceView.Warning> warnings, Set<WarningSource> sources) {
        return warnings.stream().filter(warning -> sources.contains(warning.source())).toList();
    }

    @Test
    @DisplayName(
            "Comet: an advisory and a claim the probe did not confirm reach both tool records,"
                    + " the settings and the read model, each once")
    void cometAdvisoryAndDiscrepancy(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        FakeSearch search = FakeSearch.create(directory);
        exitZero(search.executable());
        try (RealProject project = RealProject.create(search.project())) {
            PreparedRun prepared =
                    project.prepare(
                            new SearchRequest(
                                    search.model(DecoySource.COMET_INTERNAL_CONCATENATED),
                                    search.spectra(),
                                    comet(
                                            search,
                                            List.of(ToolWarningsTest.COMET_ADVISORY),
                                            List.of(ToolWarningsTest.PARAMS_CLAIM)),
                                    IndexMode.NONE));
            RunResult result = project.run(prepared);
            assertEquals(AttemptOutcome.FAILED, result.outcome(), "no Comet output was written");

            ProvenanceManifest manifest =
                    ManifestReader.readFrom(prepared.layout().provenanceJsonFile());
            List<ToolRecord> comets = toolsNamed(manifest, "comet");
            Map<String, String> settings = new TreeMap<>();
            settings.put(
                    "comet.advisory.comet.local-binary-is-unverified",
                    "This Comet was registered from a file on this machine.");
            settings.put("comet.probe-discrepancy.01", ToolWarningsTest.PARAMS_SENTENCE);
            List<ProvenanceView.Warning> shown = reader().load(prepared.layout()).view().warnings();
            assertAll(
                    () -> assertEquals(2, comets.size(), "one Comet record per spectrum file"),
                    () ->
                            assertEquals(
                                    List.of(
                                            "This Comet was registered from a file on this"
                                                    + " machine.",
                                            ToolWarningsTest.PARAMS_SENTENCE),
                                    comets.get(0).warnings()),
                    () ->
                            assertEquals(
                                    List.of(
                                            "This Comet was registered from a file on this"
                                                    + " machine.",
                                            ToolWarningsTest.PARAMS_SENTENCE),
                                    comets.get(1).warnings()),
                    () -> assertEquals(settings, warningSettings(manifest)),
                    () ->
                            assertEquals(
                                    List.of(
                                            new ProvenanceView.Warning(
                                                    WarningSource.VERSION_ADVISORY,
                                                    "comet.local-binary-is-unverified",
                                                    "This Comet was registered from a file on this"
                                                            + " machine."),
                                            new ProvenanceView.Warning(
                                                    WarningSource.MANIFEST_PROBE_DISCREPANCY,
                                                    "comet.probe-discrepancy.01",
                                                    ToolWarningsTest.PARAMS_SENTENCE)),
                                    of(
                                            shown,
                                            EnumSet.of(
                                                    WarningSource.VERSION_ADVISORY,
                                                    WarningSource.MANIFEST_PROBE_DISCREPANCY,
                                                    WarningSource.TOOL_WARNING)),
                                    "each once, under its own source, and not again as a tool"
                                            + " warning"));
        }
    }

    @Test
    @DisplayName("Comet: a build with no advisory and no unconfirmed claim records no warning")
    void cometNothingToWarnAbout(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        FakeSearch search = FakeSearch.create(directory);
        exitZero(search.executable());
        try (RealProject project = RealProject.create(search.project())) {
            PreparedRun prepared =
                    project.prepare(
                            search.request(
                                    DecoySource.COMET_INTERNAL_CONCATENATED, IndexMode.NONE));
            project.run(prepared);

            ProvenanceManifest manifest =
                    ManifestReader.readFrom(prepared.layout().provenanceJsonFile());
            List<ToolRecord> comets = toolsNamed(manifest, "comet");
            List<ProvenanceView.Warning> shown = reader().load(prepared.layout()).view().warnings();
            assertAll(
                    () -> assertEquals(2, comets.size()),
                    () -> assertEquals(List.of(), comets.get(0).warnings()),
                    () -> assertEquals(List.of(), comets.get(1).warnings()),
                    () -> assertEquals(Map.of(), warningSettings(manifest)),
                    () ->
                            assertEquals(
                                    List.of(),
                                    of(
                                            shown,
                                            EnumSet.of(
                                                    WarningSource.VERSION_ADVISORY,
                                                    WarningSource.MANIFEST_PROBE_DISCREPANCY,
                                                    WarningSource.TOOL_WARNING))));
        }
    }

    private static ToolOffer percolatorOffer(
            Path executable, List<DeclaredCapability> claims, Optional<String> identity) {
        Set<ToolCapability> probed = EnumSet.copyOf(FakePercolator.EVERY);
        for (DeclaredCapability claim : claims) {
            probed.remove(claim.capability());
        }
        ToolOffer observed =
                FakePercolator.offer(executable, "3.07.1", ToolOrigin.MANAGED, probed, List.of());
        return new ToolOffer(
                ToolName.PERCOLATOR,
                observed.version(),
                ToolOrigin.MANAGED,
                ToolInstallState.INSTALLED,
                observed.capabilities(),
                List.of(),
                Optional.empty(),
                Optional.of(executable),
                OptionalLong.of(1000),
                claims,
                identity);
    }

    private static PreparedRun percolatorRun(
            FakeSearch search, RealProject project, List<DeclaredCapability> claims)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        return percolatorRun(search, project, claims, Optional.empty());
    }

    /**
     * A run whose Comet steps are replaced and whose Percolator is {@link FakePercolator}, offered
     * as an installed managed 3.07.1 with these unconfirmed claims and this artefact identity.
     */
    static PreparedRun percolatorRun(
            FakeSearch search,
            RealProject project,
            List<DeclaredCapability> claims,
            Optional<String> identity)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Path percolator =
                FakePercolator.write(
                        search.root().resolve("bin/percolator"), FakePercolator.Behaviour.normal());
        ToolOffer offer = percolatorOffer(percolator, claims, identity);
        PercolatorChoice choice =
                FakePercolator.choice(
                        offer, List.of(offer), Set.of(), PercolatorSettings.defaults());
        PreparedRun prepared =
                project.prepare(
                        new SearchRequest(
                                search.model(DecoySource.COMET_INTERNAL_CONCATENATED)
                                        .withText(
                                                "decoy_prefix",
                                                SyntheticPin.DECOY_PROTEIN_PREFIX,
                                                ValueOrigin.USER),
                                search.spectra(),
                                search.selection(),
                                IndexMode.NONE,
                                Optional.of(choice)));
        Map<EngineStep, StepAction> actions = new EnumMap<>(prepared.actions());
        actions.put(EngineStep.RUN_COMET, new Nothing());
        actions.put(EngineStep.VALIDATE_COMET_OUTPUTS, new Nothing());
        actions.put(EngineStep.MERGE_PIN, new WriteMergedPin(prepared.layout().mergedPinFile()));
        RunResult result =
                project.engine()
                        .start(
                                new RunRequest(
                                        project.store(),
                                        project.lock(),
                                        prepared.layout(),
                                        prepared.plan(),
                                        prepared.inputs(),
                                        Set.of(),
                                        actions,
                                        RealProject.application(),
                                        prepared.settings()),
                                StepStateListener.NONE)
                        .await(RealProject.RUN_BOUND)
                        .orElseThrow();
        assertEquals(AttemptOutcome.SUCCEEDED, result.outcome(), result.failures()::toString);
        return prepared;
    }

    @Test
    @DisplayName(
            "Percolator: a claim the probe did not confirm reaches its tool record, the settings"
                    + " and the read model, once")
    void percolatorDiscrepancy(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        FakeSearch search = FakeSearch.create(directory);
        DeclaredCapability decoys =
                new DeclaredCapability(
                        ToolCapability.XML_DECOY_OUTPUT,
                        CapabilityEvidence.INFERRED_FROM_ARTEFACT_BYTES,
                        "a writer literal in the artefact's bytes");
        try (RealProject project = RealProject.create(search.project())) {
            PreparedRun prepared = percolatorRun(search, project, List.of(decoys));

            ProvenanceManifest manifest =
                    ManifestReader.readFrom(prepared.layout().provenanceJsonFile());
            List<ToolRecord> percolators = toolsNamed(manifest, "percolator");
            List<ProvenanceView.Warning> shown = reader().load(prepared.layout()).view().warnings();
            assertAll(
                    () -> assertEquals(1, percolators.size()),
                    () ->
                            assertEquals(
                                    List.of(ToolWarningsTest.DECOY_SENTENCE),
                                    percolators.get(0).warnings()),
                    () ->
                            assertFalse(
                                    percolators.get(0).capabilities().contains("XML_DECOY_OUTPUT"),
                                    "the probe won: the record's probed set lacks it"),
                    () ->
                            assertEquals(
                                    Map.of(
                                            "percolator.probe-discrepancy.01",
                                            ToolWarningsTest.DECOY_SENTENCE),
                                    warningSettings(manifest)),
                    () ->
                            assertEquals(
                                    List.of(
                                            new ProvenanceView.Warning(
                                                    WarningSource.MANIFEST_PROBE_DISCREPANCY,
                                                    "percolator.probe-discrepancy.01",
                                                    ToolWarningsTest.DECOY_SENTENCE)),
                                    of(
                                            shown,
                                            EnumSet.of(
                                                    WarningSource.MANIFEST_PROBE_DISCREPANCY,
                                                    WarningSource.TOOL_WARNING))));
        }
    }

    @Test
    @DisplayName("Percolator: a probe that confirmed every claim records no discrepancy")
    void percolatorNothingToWarnAbout(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        FakeSearch search = FakeSearch.create(directory);
        try (RealProject project = RealProject.create(search.project())) {
            PreparedRun prepared = percolatorRun(search, project, List.of());

            ProvenanceManifest manifest =
                    ManifestReader.readFrom(prepared.layout().provenanceJsonFile());
            List<ToolRecord> percolators = toolsNamed(manifest, "percolator");
            assertAll(
                    () -> assertEquals(1, percolators.size()),
                    () -> assertEquals(List.of(), percolators.get(0).warnings()),
                    () -> assertEquals(Map.of(), warningSettings(manifest)),
                    () ->
                            assertEquals(
                                    List.of(),
                                    of(
                                            reader().load(prepared.layout()).view().warnings(),
                                            EnumSet.of(
                                                    WarningSource.MANIFEST_PROBE_DISCREPANCY,
                                                    WarningSource.TOOL_WARNING))));
        }
    }
}
