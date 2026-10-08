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
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.HashService;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.run.RunDerivation;
import org.cometgui.domain.run.RunDescriptor;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.params.percolator.resolution.DownstreamStage;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.provenance.manifest.FileDirection;
import org.cometgui.provenance.manifest.FileRecord;
import org.cometgui.provenance.manifest.ManifestWriter;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ToolRecord;
import org.cometgui.tools.percolator.SyntheticPin;
import org.cometgui.workflow.engine.DeclaredFile;
import org.cometgui.workflow.engine.Invocation;
import org.cometgui.workflow.engine.ReuseRefusedException;
import org.cometgui.workflow.engine.RunRequest;
import org.cometgui.workflow.engine.RunResult;
import org.cometgui.workflow.engine.StepAction;
import org.cometgui.workflow.engine.StepContext;
import org.cometgui.workflow.engine.StepDeclaration;
import org.cometgui.workflow.engine.StepFailedException;
import org.cometgui.workflow.engine.StepStateListener;
import org.cometgui.workflow.engine.ToolIdentity;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.StepState;
import org.cometgui.workflow.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The compatible-version Percolator rerun ({@link PercolatorRerun}) through the real engine and the
 * real process service, with stand-ins: a source run whose Comet is a shell script that exits 0
 * (one recorded invocation, so its provenance has a Comet tool record), whose merged PIN is
 * CometGUI's synthetic 64 + 64 PIN written where {@code merge-pin} writes it, and two stand-in
 * Percolator builds ({@link FakePercolator}) -- one without the XML capabilities, as 3.09, and one
 * with every capability, as 3.07.1. The real-binary test ({@code RealPercolatorRerunTest}) proves
 * gate items 6 and 9 on real binaries; these prove every refusal, the record and the preview,
 * quickly. Every expected value is typed out here or computed independently from the files.
 *
 * <p>POSIX only: the stand-ins are shell scripts.
 */
@EnabledOnOs(
        value = {OS.LINUX, OS.MAC},
        disabledReason = "the stand-in Comet and Percolator are POSIX shell scripts")
class PercolatorRerunTest {

    private static final String PREFIX = SyntheticPin.DECOY_PROTEIN_PREFIX;

    private static final Set<DownstreamStage> LIMELIGHT =
            EnumSet.of(DownstreamStage.LIMELIGHT_CONVERSION);

    private static final Set<DownstreamStage> NONE = EnumSet.noneOf(DownstreamStage.class);

    /** Every capability except the two XML ones: the 3.09 stand-in's. */
    private static final Set<ToolCapability> NO_XML = noXml();

    private static Set<ToolCapability> noXml() {
        Set<ToolCapability> capabilities = EnumSet.copyOf(FakePercolator.EVERY);
        capabilities.remove(ToolCapability.XML_OUTPUT);
        capabilities.remove(ToolCapability.XML_DECOY_OUTPUT);
        return capabilities;
    }

    /** Does nothing and declares nothing: stands in for a Comet step. */
    private record Nothing() implements StepAction {

        @Override
        public StepDeclaration declaration() {
            return StepDeclaration.NOTHING;
        }

        @Override
        public void execute(StepContext context) {}
    }

    /** Stands in for {@code run-comet}: one invocation of the stand-in Comet, recorded. */
    private record InvokeComet(ToolIdentity tool, Path workingDirectory) implements StepAction {

        @Override
        public StepDeclaration declaration() {
            return new StepDeclaration(List.of(), List.of("comet-01"));
        }

        @Override
        public void execute(StepContext context)
                throws StepFailedException, IOException, InterruptedException {
            context.invoke(
                    new Invocation(
                            "comet-01",
                            tool,
                            new ToolCommand(
                                    List.of(tool.executablePath().toString()),
                                    workingDirectory,
                                    Map.of("LANG", "C.UTF-8"))));
        }
    }

    /**
     * Stands in for {@code merge-pin}: writes the merged PIN, declared as its output, and then
     * fails if told to -- which leaves the PIN recorded {@code partial}.
     */
    private record WriteMergedPin(Path merged, boolean fail) implements StepAction {

        @Override
        public StepDeclaration declaration() {
            return new StepDeclaration(
                    List.of(DeclaredFile.output(RunDeclarations.MERGED_PIN, merged)), List.of());
        }

        @Override
        public void execute(StepContext context) throws IOException, StepFailedException {
            Files.writeString(
                    merged,
                    SyntheticPin.of(64, SyntheticPin.PROBE_SEED),
                    StandardCharsets.US_ASCII);
            if (fail) {
                throw new StepFailedException("merge-pin stand-in told to fail");
            }
        }
    }

    /** Stands in for {@code merge-pin}, failing before it writes anything. */
    private record FailMerge(Path merged) implements StepAction {

        @Override
        public StepDeclaration declaration() {
            return new StepDeclaration(
                    List.of(DeclaredFile.output(RunDeclarations.MERGED_PIN, merged)), List.of());
        }

        @Override
        public void execute(StepContext context) throws StepFailedException {
            throw new StepFailedException("merge-pin stand-in failed before writing");
        }
    }

    /** How the stand-in source's Comet steps behave. */
    private enum Comet {
        /** One recorded Comet invocation and a merged PIN. */
        NORMAL,
        /** No Comet invocation at all. */
        NOT_INVOKED,
        /** Comet ran, merge-pin wrote the PIN and then failed: recorded partial. */
        PARTIAL_PIN,
        /** Comet ran, merge-pin failed before writing: no merged PIN recorded. */
        NO_PIN
    }

    /** A project with one source run, its two stand-in Percolators and their offers. */
    private record Fixture(
            FakeSearch search,
            RealProject project,
            ToolOffer withoutXml,
            ToolOffer withXml,
            PreparedRun source) {

        RunLayout layout() {
            return source.layout();
        }

        PercolatorChoice choice(ToolOffer offer, Set<DownstreamStage> stages) throws IOException {
            return FakePercolator.choice(
                    offer, List.of(withoutXml, withXml), stages, PercolatorSettings.defaults());
        }

        /** The rerun the user wants: the XML-capable build, with Limelight conversion. */
        PercolatorChoice compatible() throws IOException {
            return choice(withXml, LIMELIGHT);
        }

        PercolatorRerun rerun() {
            return new PercolatorRerun(project.hashes());
        }

        PercolatorRerunPreview preview(PercolatorChoice choice)
                throws IOException, RerunRefusedException {
            return rerun().preview(project.store(), project.project(), layout(), choice);
        }

        DerivedRun prepare(PercolatorChoice choice) throws IOException, RerunRefusedException {
            return rerun().prepare(
                            project.store(),
                            project.lock(),
                            layout(),
                            choice,
                            RealProject.application());
        }

        RunResult start(DerivedRun derived)
                throws IOException, ReuseRefusedException, InterruptedException {
            return rerun().start(project.engine(), derived, StepStateListener.NONE)
                    .await(RealProject.RUN_BOUND)
                    .orElseThrow();
        }

        String refusal(PercolatorChoice choice) throws IOException {
            List<String> before = project.runDirectories();
            int launches = project.runner().launches().size();
            RerunRefusedException previewed =
                    assertThrows(RerunRefusedException.class, () -> preview(choice));
            RerunRefusedException prepared =
                    assertThrows(RerunRefusedException.class, () -> prepare(choice));
            assertEquals(previewed.getMessage(), prepared.getMessage());
            assertEquals(before, project.runDirectories(), "a refused rerun creates no run");
            assertEquals(launches, project.runner().launches().size(), "nothing is launched");
            return prepared.getMessage();
        }
    }

    private static Fixture fixture(Path directory, boolean percolator, Comet comet, boolean run)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        FakeSearch search = FakeSearch.create(directory);
        Files.writeString(search.executable(), "#!/bin/sh\nexit 0\n", StandardCharsets.US_ASCII);
        Path without =
                FakePercolator.write(
                        search.root().resolve("bin/percolator-3.09"),
                        FakePercolator.Behaviour.normal());
        Files.writeString(
                without,
                "# the 3.09 stand-in\n",
                StandardCharsets.US_ASCII,
                StandardOpenOption.APPEND);
        Path with =
                FakePercolator.write(
                        search.root().resolve("bin/percolator-3.07.1"),
                        FakePercolator.Behaviour.normal());
        ToolOffer withoutXml =
                FakePercolator.offer(without, "3.09", ToolOrigin.LOCAL, NO_XML, List.of());
        ToolOffer withXml =
                FakePercolator.offer(
                        with, "3.07.1", ToolOrigin.MANAGED, FakePercolator.EVERY, List.of());
        RealProject project = RealProject.create(search.project());
        SearchRequest request =
                new SearchRequest(
                        search.model(DecoySource.COMET_INTERNAL_CONCATENATED)
                                .withText("decoy_prefix", PREFIX, ValueOrigin.USER),
                        search.spectra(),
                        search.selection(),
                        IndexMode.NONE);
        if (percolator) {
            request =
                    request.withPercolator(
                            FakePercolator.choice(
                                    withoutXml,
                                    List.of(withoutXml, withXml),
                                    NONE,
                                    PercolatorSettings.defaults()));
        }
        PreparedRun source = project.prepare(request);
        Fixture fixture = new Fixture(search, project, withoutXml, withXml, source);
        if (run) {
            RunResult result = runSource(fixture, comet);
            AttemptOutcome expected =
                    comet == Comet.NORMAL || comet == Comet.NOT_INVOKED
                            ? AttemptOutcome.SUCCEEDED
                            : AttemptOutcome.FAILED;
            assertEquals(expected, result.outcome(), () -> result.failures().toString());
        }
        return fixture;
    }

    private static RunResult runSource(Fixture fixture, Comet comet)
            throws IOException, InterruptedException, ReuseRefusedException {
        PreparedRun source = fixture.source();
        Map<EngineStep, StepAction> actions = new EnumMap<>(source.actions());
        ToolIdentity tool =
                new ToolIdentity(
                        CometWorkflow.TOOL_NAME,
                        RealComet.NEWER,
                        Optional.of("v" + RealComet.NEWER),
                        fixture.search().executable(),
                        fixture.project().hashes().hash(fixture.search().executable()),
                        false,
                        Optional.empty(),
                        Set.of(),
                        List.of());
        actions.put(
                EngineStep.RUN_COMET,
                comet == Comet.NOT_INVOKED
                        ? new Nothing()
                        : new InvokeComet(tool, source.layout().root()));
        actions.put(EngineStep.VALIDATE_COMET_OUTPUTS, new Nothing());
        Path merged = source.layout().mergedPinFile();
        actions.put(
                EngineStep.MERGE_PIN,
                comet == Comet.NO_PIN
                        ? new FailMerge(merged)
                        : new WriteMergedPin(merged, comet == Comet.PARTIAL_PIN));
        RunRequest request =
                new RunRequest(
                        fixture.project().store(),
                        fixture.project().lock(),
                        source.layout(),
                        source.plan(),
                        source.inputs(),
                        Set.of(),
                        actions,
                        RealProject.application(),
                        source.settings());
        return fixture.project()
                .engine()
                .start(request, StepStateListener.NONE)
                .await(RealProject.RUN_BOUND)
                .orElseThrow();
    }

    private static Fixture fixture(Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        return fixture(directory, true, Comet.NORMAL, true);
    }

    /** The launches of one executable, counted around the real process service. */
    private static int launchesOf(Fixture fixture, Path executable) {
        return fixture.project().runner().launchesOf(executable).size();
    }

    @Test
    @DisplayName(
            "the preview: the Percolator steps execute in a new run, the Comet results are"
                    + " reused from the source, named in the vocabulary of every preview")
    void preview(@TempDir Path directory)
            throws IOException,
                    InterruptedException,
                    RunBlockedException,
                    ReuseRefusedException,
                    RerunRefusedException {
        Fixture fixture = fixture(directory);
        try (RealProject project = fixture.project()) {
            Map<String, String> before = RunEvidence.tree(project.project().root());
            PercolatorRerunPreview preview = fixture.preview(fixture.compatible());
            Path merged = fixture.layout().mergedPinFile();
            String sha = RealComet.sha256(merged);

            assertEquals(new RunId("run-0001"), preview.source());
            assertEquals(merged, preview.mergedPin());
            assertEquals(sha, preview.mergedPinSha256());
            assertEquals(
                    List.of(
                            EngineStep.VALIDATE_CONFIGURATION,
                            EngineStep.RESOLVE_PERCOLATOR,
                            EngineStep.RUN_PERCOLATOR,
                            EngineStep.PARSE_PERCOLATOR,
                            EngineStep.FINALISE_PROVENANCE),
                    preview.plan().steps());
            assertEquals(
                    EnumSet.of(
                            EngineStep.SERIALISE_COMET_PARAMS,
                            EngineStep.RUN_COMET,
                            EngineStep.VALIDATE_COMET_OUTPUTS,
                            EngineStep.MERGE_PIN),
                    preview.reusedFromSource());
            assertEquals(Set.copyOf(preview.plan().steps()), preview.executed());
            assertEquals(
                    List.of(
                            "Percolator reruns in a new run on the merged PIN of run run-0001 ("
                                    + merged
                                    + ", SHA-256 "
                                    + sha
                                    + ", re-hashed and unchanged); Comet is not run.",
                            "validate-configuration: prepares -- needed by resolve-percolator",
                            "resolve-percolator: prepares -- needed by run-percolator",
                            "serialise-comet-params: not executed -- its result is reused from run"
                                    + " run-0001",
                            "run-comet: not executed -- its result is reused from run run-0001",
                            "validate-comet-outputs: not executed -- its result is reused from run"
                                    + " run-0001",
                            "merge-pin: not executed -- its result is reused from run run-0001",
                            "run-percolator: executes -- no successful earlier execution is"
                                    + " recorded",
                            "parse-percolator: executes -- no successful earlier execution is"
                                    + " recorded; run-percolator re-executes",
                            "finalise-provenance: executes -- no successful earlier execution is"
                                    + " recorded"),
                    preview.lines());
            assertEquals(
                    before, RunEvidence.tree(project.project().root()), "a preview writes nothing");
        }
    }

    @Test
    @DisplayName(
            "the rerun: a second, derived run; no Comet; the merged PIN and parameters copied and"
                    + " equal; the new build, checksum and argv recorded; the source untouched")
    void rerun(@TempDir Path directory)
            throws IOException,
                    InterruptedException,
                    RunBlockedException,
                    ReuseRefusedException,
                    RerunRefusedException {
        Fixture fixture = fixture(directory);
        try (RealProject project = fixture.project()) {
            RunLayout source = fixture.layout();
            Map<String, String> before = RunEvidence.tree(source.root());
            String sourceManifest = RealComet.sha256(source.provenanceJsonFile());
            int comet = launchesOf(fixture, fixture.search().executable());
            Path with = fixture.withXml().installedPath().orElseThrow();

            DerivedRun derived = fixture.prepare(fixture.compatible());
            RunResult result = fixture.start(derived);

            assertEquals(
                    AttemptOutcome.SUCCEEDED, result.outcome(), () -> result.failures().toString());
            assertEquals(comet, launchesOf(fixture, fixture.search().executable()), "no Comet");
            assertEquals(1, launchesOf(fixture, with));
            assertEquals(
                    EnumSet.of(
                            EngineStep.VALIDATE_CONFIGURATION,
                            EngineStep.RESOLVE_PERCOLATOR,
                            EngineStep.RUN_PERCOLATOR,
                            EngineStep.PARSE_PERCOLATOR,
                            EngineStep.FINALISE_PROVENANCE),
                    result.states().keySet());
            for (StepState state : result.states().values()) {
                assertEquals(StepState.SUCCEEDED, state);
            }
            assertEquals(before, RunEvidence.tree(source.root()), "the source is untouched");

            RunLayout layout = derived.layout();
            assertTrue(layout.root().getFileName().toString().endsWith("-run-0002"));
            assertEquals(
                    Files.readString(source.cometParamsFile()),
                    Files.readString(layout.cometParamsFile()));
            assertEquals(
                    RealComet.sha256(source.mergedPinFile()),
                    RealComet.sha256(layout.mergedPinFile()));
            assertEquals(List.of(), RunEvidence.listing(layout.cometOutputDirectory()));
            assertEquals(
                    List.of(
                            "decoy-peptides.tsv",
                            "decoy-psms.tsv",
                            "peptides.tsv",
                            "pout.xml",
                            "psms.tsv",
                            "weights.txt"),
                    RunEvidence.listing(derived.percolatorOutputDirectory()));

            RunDescriptor recorded = project.store().read(layout);
            RunDerivation from = recorded.identity().derivedFrom().orElseThrow();
            assertEquals(2, recorded.schemaVersion());
            assertTrue(
                    Files.readString(layout.runFile()).startsWith("{\n  \"schemaVersion\": 2,\n"));
            assertEquals(new RunId("run-0001"), from.runId());
            assertEquals(fixture.source().identity().created(), from.created());
            assertEquals(sourceManifest, from.provenance().hashes().sha256());
            assertEquals(Files.size(source.provenanceJsonFile()), from.provenance().size());
            assertEquals(
                    RealComet.sha256(source.mergedPinFile()), from.mergedPin().hashes().sha256());
            assertEquals(Files.size(source.mergedPinFile()), from.mergedPin().size());
            RunDescriptor sourceRecord = project.store().read(source);
            assertEquals(sourceRecord.identity().spectra(), recorded.identity().spectra());
            assertEquals(sourceRecord.identity().fasta(), recorded.identity().fasta());
            assertEquals(sourceRecord.identity().parameters(), recorded.identity().parameters());
            assertEquals(
                    sourceRecord.identity().cometRelease(), recorded.identity().cometRelease());
            assertEquals(AttemptOutcome.SUCCEEDED, recorded.attempts().get(0).outcome());

            ProvenanceManifest manifest = RunEvidence.manifest(layout);
            assertEquals(List.of(), RunEvidence.tools(manifest, CometWorkflow.TOOL_NAME));
            List<ToolRecord> percolator =
                    RunEvidence.tools(manifest, CometWorkflow.PERCOLATOR_TOOL_NAME);
            assertEquals(1, percolator.size());
            assertEquals("3.07.1", percolator.get(0).version());
            assertEquals(RealComet.sha256(with), percolator.get(0).hashes().sha256());
            Path out = derived.percolatorOutputDirectory();
            assertEquals(
                    List.of(
                            with.toString(),
                            "--results-psms",
                            out + "/psms.tsv",
                            "--results-peptides",
                            out + "/peptides.tsv",
                            "--decoy-results-psms",
                            out + "/decoy-psms.tsv",
                            "--decoy-results-peptides",
                            out + "/decoy-peptides.tsv",
                            "--weights",
                            out + "/weights.txt",
                            "-X",
                            out + "/pout.xml",
                            "--seed",
                            "1",
                            "--num-threads",
                            "3",
                            "--testFDR",
                            "0.01",
                            "--trainFDR",
                            "0.01",
                            "--maxiter",
                            "10",
                            layout.mergedPinFile().toString()),
                    percolator.get(0).execution().command().argv());
            assertEquals(derived.percolatorArgv(), percolator.get(0).execution().command().argv());

            Map<String, String> settings = manifest.settings();
            Map<String, String> rerun = new TreeMap<>();
            for (Map.Entry<String, String> setting : settings.entrySet()) {
                if (setting.getKey().startsWith("rerun.")) {
                    rerun.put(setting.getKey(), setting.getValue());
                }
            }
            Map<String, String> expected = new TreeMap<>();
            expected.put("rerun.merged-pin-sha256", RealComet.sha256(source.mergedPinFile()));
            expected.put(
                    "rerun.reused-steps",
                    "serialise-comet-params run-comet validate-comet-outputs merge-pin");
            expected.put(
                    "rerun.source-comet-binary-sha256",
                    RealComet.sha256(fixture.search().executable()));
            expected.put(
                    "rerun.source-comet-params-sha256", RealComet.sha256(source.cometParamsFile()));
            expected.put("rerun.source-comet-release", "2026.03.0");
            expected.put("rerun.source-percolator-version", "3.09");
            expected.put("rerun.source-provenance-sha256", sourceManifest);
            expected.put("rerun.source-run-directory", "runs/" + source.root().getFileName());
            expected.put("rerun.source-run-id", "run-0001");
            assertEquals(expected, rerun);
            assertEquals("3.07.1", settings.get("percolator.version"));
            assertEquals(PREFIX, settings.get("percolator.decoy-prefix"));
            for (String key : settings.keySet()) {
                assertFalse(key.startsWith("comet."), () -> "a Comet setting: " + key);
            }
            assertEquals(
                    "validate-configuration resolve-percolator run-percolator parse-percolator"
                            + " finalise-provenance",
                    settings.get("workflow.plan"));
            Map<String, String> inputs = new TreeMap<>();
            for (FileRecord file : manifest.files()) {
                if (file.direction() == FileDirection.INPUT) {
                    inputs.put(file.role(), file.path().toString());
                }
            }
            assertEquals(layout.mergedPinFile().toString(), inputs.get("merged-pin"));
            assertEquals(layout.cometParamsFile().toString(), inputs.get("comet-params"));
            Map<String, String> finalised =
                    RunEvidence.finished(layout, EngineStep.FINALISE_PROVENANCE);
            RunEvidence.assertDetail(
                    finalised, "pin.merged-sha256", RealComet.sha256(source.mergedPinFile()));
            RunEvidence.assertDetail(
                    finalised, "comet.params-sha256", RealComet.sha256(source.cometParamsFile()));
        }
    }

    @Test
    @DisplayName("a Comet-only source is rerun too; it ran no Percolator")
    void cometOnlySource(@TempDir Path directory)
            throws IOException,
                    InterruptedException,
                    RunBlockedException,
                    ReuseRefusedException,
                    RerunRefusedException {
        Fixture fixture = fixture(directory, false, Comet.NORMAL, true);
        try (RealProject project = fixture.project()) {
            DerivedRun derived = fixture.prepare(fixture.choice(fixture.withoutXml(), NONE));
            RunResult result = fixture.start(derived);
            assertEquals(
                    AttemptOutcome.SUCCEEDED, result.outcome(), () -> result.failures().toString());
            assertEquals(
                    RerunProvenance.NONE,
                    RunEvidence.manifest(derived.layout())
                            .settings()
                            .get("rerun.source-percolator-version"));
            assertEquals("none", RerunProvenance.NONE);
        }
    }

    @Test
    @DisplayName("a changed merged PIN refuses the rerun naming the file, its role and both hashes")
    void changedMergedPin(@TempDir Path directory)
            throws IOException,
                    InterruptedException,
                    RunBlockedException,
                    ReuseRefusedException,
                    RerunRefusedException {
        Fixture fixture = fixture(directory);
        try (RealProject project = fixture.project()) {
            Path merged = fixture.layout().mergedPinFile();
            String was = RealComet.sha256(merged);
            Files.writeString(merged, "\n", StandardCharsets.US_ASCII, StandardOpenOption.APPEND);
            String now = RealComet.sha256(merged);
            assertEquals(
                    "the file "
                            + merged
                            + " (role merged-pin) of run run-0001 has SHA-256 "
                            + now
                            + ", but run run-0001 recorded SHA-256 "
                            + was
                            + "; it has changed since it was recorded, so it is not reused and"
                            + " Percolator is not rerun from it (R-RUN-02). Nothing was created.",
                    fixture.refusal(fixture.compatible()));
        }
    }

    @Test
    @DisplayName(
            "a changed parameter file refuses the rerun naming the file, its role and both hashes")
    void changedParameters(@TempDir Path directory)
            throws IOException,
                    InterruptedException,
                    RunBlockedException,
                    ReuseRefusedException,
                    RerunRefusedException {
        Fixture fixture = fixture(directory);
        try (RealProject project = fixture.project()) {
            Path params = fixture.layout().cometParamsFile();
            String was = RealComet.sha256(params);
            Files.writeString(
                    params, "# x\n", StandardCharsets.US_ASCII, StandardOpenOption.APPEND);
            String now = RealComet.sha256(params);
            assertEquals(
                    "the file "
                            + params
                            + " (role comet-params) of run run-0001 has SHA-256 "
                            + now
                            + ", but run run-0001 recorded SHA-256 "
                            + was
                            + "; it has changed since it was recorded, so it is not reused and"
                            + " Percolator is not rerun from it (R-RUN-02). Nothing was created.",
                    fixture.refusal(fixture.compatible()));
        }
    }

    @Test
    @DisplayName(
            "a missing merged PIN or parameter file is refused, naming it and its recorded hash")
    void missingFiles(@TempDir Path directory)
            throws IOException,
                    InterruptedException,
                    RunBlockedException,
                    ReuseRefusedException,
                    RerunRefusedException {
        Fixture fixture = fixture(directory);
        try (RealProject project = fixture.project()) {
            Path params = fixture.layout().cometParamsFile();
            String paramsSha = RealComet.sha256(params);
            Path moved = fixture.search().root().resolve("comet.params.moved");
            Files.move(params, moved);
            assertEquals(
                    "the archived parameter file (role comet-params) "
                            + params
                            + " of run run-0001 no longer exists; run run-0001 recorded SHA-256 "
                            + paramsSha,
                    fixture.refusal(fixture.compatible()));
            Files.move(moved, params);

            Path merged = fixture.layout().mergedPinFile();
            String mergedSha = RealComet.sha256(merged);
            Files.delete(merged);
            assertEquals(
                    "the merged PIN (role merged-pin) "
                            + merged
                            + " of run run-0001 no longer exists; run run-0001 recorded SHA-256 "
                            + mergedSha,
                    fixture.refusal(fixture.compatible()));
        }
    }

    @Test
    @DisplayName("a source with no merged PIN recorded -- merge-pin never succeeded -- is refused")
    void noMergedPin(@TempDir Path directory)
            throws IOException,
                    InterruptedException,
                    RunBlockedException,
                    ReuseRefusedException,
                    RerunRefusedException {
        Fixture fixture = fixture(directory, true, Comet.NO_PIN, true);
        try (RealProject project = fixture.project()) {
            assertEquals(
                    "run run-0001 has no merged PIN to rerun Percolator from: its provenance"
                            + " records no merged-pin output "
                            + fixture.layout().mergedPinFile()
                            + ", so merge-pin never succeeded in it",
                    fixture.refusal(fixture.compatible()));
        }
    }

    @Test
    @DisplayName("a merged PIN recorded partial is refused, naming its recorded hash")
    void partialMergedPin(@TempDir Path directory)
            throws IOException,
                    InterruptedException,
                    RunBlockedException,
                    ReuseRefusedException,
                    RerunRefusedException {
        Fixture fixture = fixture(directory, true, Comet.PARTIAL_PIN, true);
        try (RealProject project = fixture.project()) {
            Path merged = fixture.layout().mergedPinFile();
            assertEquals(
                    "the merged PIN (role merged-pin) "
                            + merged
                            + " of run run-0001 is recorded partial (SHA-256 "
                            + RealComet.sha256(merged)
                            + "), not complete, so Percolator is not rerun from it",
                    fixture.refusal(fixture.compatible()));
        }
    }

    @Test
    @DisplayName("a source whose provenance records no Comet invocation is refused")
    void noCometInvocation(@TempDir Path directory)
            throws IOException,
                    InterruptedException,
                    RunBlockedException,
                    ReuseRefusedException,
                    RerunRefusedException {
        Fixture fixture = fixture(directory, true, Comet.NOT_INVOKED, true);
        try (RealProject project = fixture.project()) {
            assertEquals(
                    "run run-0001's provenance records no Comet invocation, so there is no Comet"
                            + " search to reuse",
                    fixture.refusal(fixture.compatible()));
        }
    }

    @Test
    @DisplayName("Comet invocations of two different executables are refused")
    void twoCometBinaries(@TempDir Path directory)
            throws IOException,
                    InterruptedException,
                    RunBlockedException,
                    ReuseRefusedException,
                    RerunRefusedException {
        Fixture fixture = fixture(directory);
        try (RealProject project = fixture.project()) {
            Path file = fixture.layout().provenanceJsonFile();
            ProvenanceManifest manifest = RunEvidence.manifest(fixture.layout());
            List<ToolRecord> tools = new ArrayList<>(manifest.tools());
            ToolRecord comet = RunEvidence.tools(manifest, CometWorkflow.TOOL_NAME).get(0);
            String other = "c".repeat(64);
            tools.add(
                    new ToolRecord(
                            comet.name(),
                            comet.version(),
                            comet.releaseTag(),
                            comet.executablePath(),
                            new FileHashes("d".repeat(32), other),
                            comet.managed(),
                            comet.artefactIdentity(),
                            comet.capabilities(),
                            Optional.of("comet-02"),
                            comet.execution(),
                            comet.warnings()));
            rewrite(file, manifest, tools, manifest.files());
            List<String> both = new ArrayList<>(List.of(comet.hashes().sha256(), other));
            both.sort(null);
            assertEquals(
                    "run run-0001's provenance records Comet invocations of 2 different"
                            + " executables ("
                            + String.join(", ", both)
                            + "), so which search to reuse is not one answer",
                    fixture.refusal(fixture.compatible()));
        }
    }

    private static void rewrite(
            Path file, ProvenanceManifest manifest, List<ToolRecord> tools, List<FileRecord> files)
            throws IOException {
        ManifestWriter.redactingWith(SecretRedactor.patternsOnly())
                .writeTo(
                        file,
                        new ProvenanceManifest(
                                manifest.schemaVersion(),
                                manifest.run(),
                                manifest.application(),
                                manifest.settings(),
                                tools,
                                files));
    }

    @Test
    @DisplayName(
            "only the merged-pin output record at the run's merged PIN counts: one read as an"
                    + " input, under another role or at another path does not")
    void mergedPinRecordMustBeTheOutput(@TempDir Path directory)
            throws IOException,
                    InterruptedException,
                    RunBlockedException,
                    ReuseRefusedException,
                    RerunRefusedException {
        Fixture fixture = fixture(directory);
        try (RealProject project = fixture.project()) {
            Path file = fixture.layout().provenanceJsonFile();
            ProvenanceManifest manifest = RunEvidence.manifest(fixture.layout());
            String expected =
                    "run run-0001 has no merged PIN to rerun Percolator from: its provenance"
                            + " records no merged-pin output "
                            + fixture.layout().mergedPinFile()
                            + ", so merge-pin never succeeded in it";
            for (String change : List.of("dropped", "role", "path")) {
                List<FileRecord> files = new ArrayList<>();
                for (FileRecord record : manifest.files()) {
                    boolean output =
                            record.direction() == FileDirection.OUTPUT
                                    && record.role().equals(RunDeclarations.MERGED_PIN);
                    if (!output) {
                        files.add(record);
                    } else if (!"dropped".equals(change)) {
                        files.add(
                                new FileRecord(
                                        FileDirection.OUTPUT,
                                        "role".equals(change) ? "pin" : record.role(),
                                        "path".equals(change)
                                                ? fixture.layout().pinFile("k562_3")
                                                : record.path(),
                                        record.sizeBytes(),
                                        record.modifiedAt(),
                                        record.hashes(),
                                        record.status()));
                    }
                }
                rewrite(file, manifest, manifest.tools(), files);
                assertEquals(expected, fixture.refusal(fixture.compatible()), change);
            }
        }
    }

    @Test
    @DisplayName("a provenance record that is missing or unreadable is refused")
    void provenanceRecord(@TempDir Path directory)
            throws IOException,
                    InterruptedException,
                    RunBlockedException,
                    ReuseRefusedException,
                    RerunRefusedException {
        Fixture fixture = fixture(directory);
        try (RealProject project = fixture.project()) {
            Path file = fixture.layout().provenanceJsonFile();
            byte[] kept = Files.readAllBytes(file);
            Files.writeString(file, "{\"not\": \"a manifest\"}\n", StandardCharsets.US_ASCII);
            String unreadable = fixture.refusal(fixture.compatible());
            assertTrue(
                    unreadable.startsWith(
                            "run run-0001's provenance record "
                                    + file
                                    + " cannot be read, so nothing of the run is reused: "),
                    unreadable);
            Files.delete(file);
            assertEquals(
                    "run run-0001 has no provenance record "
                            + file
                            + ", so what it produced cannot be checked and nothing of it is"
                            + " reused",
                    fixture.refusal(fixture.compatible()));
            Files.write(file, kept);
            assertEquals("run-0001", fixture.preview(fixture.compatible()).source().value());
        }
    }

    @Test
    @DisplayName("a run never started, or still running, has nothing to rerun from")
    void notEnded(@TempDir Path directory)
            throws IOException,
                    InterruptedException,
                    RunBlockedException,
                    ReuseRefusedException,
                    RerunRefusedException {
        Fixture fixture = fixture(directory, true, Comet.NORMAL, false);
        try (RealProject project = fixture.project()) {
            assertEquals(
                    "run run-0001 has never been started, so it has no merged PIN to rerun"
                            + " Percolator from",
                    fixture.refusal(fixture.compatible()));
            RunDescriptor recorded = project.store().read(fixture.layout());
            project.store()
                    .update(
                            project.lock(),
                            recorded.withNewAttempt(Instant.parse("2026-10-08T00:00:00.000Z")));
            assertEquals(
                    "run run-0001 is still running (attempt 1); a rerun reuses only a run that"
                            + " has ended",
                    fixture.refusal(fixture.compatible()));
        }
    }

    @Test
    @DisplayName("the same Percolator, settings and stages as the source: nothing to rerun")
    void nothingChanged(@TempDir Path directory)
            throws IOException,
                    InterruptedException,
                    RunBlockedException,
                    ReuseRefusedException,
                    RerunRefusedException {
        Fixture fixture = fixture(directory);
        try (RealProject project = fixture.project()) {
            assertEquals(
                    "run run-0001 already ran Percolator 3.09 with these settings and downstream"
                            + " stages, and it succeeded; a rerun with nothing changed would repeat"
                            + " it. Choose another build, other settings or other stages.",
                    fixture.refusal(fixture.choice(fixture.withoutXml(), NONE)));
            // The same build with Limelight switched on is a change: it is accepted.
            assertEquals(
                    "run-0001",
                    fixture.preview(fixture.choice(fixture.withoutXml(), LIMELIGHT))
                            .source()
                            .value());
        }
    }

    @Test
    @DisplayName(
            "a Comet result the source's record does not hold is refused, by the declared graph")
    void cometResultNotRecorded(@TempDir Path directory)
            throws IOException,
                    InterruptedException,
                    RunBlockedException,
                    ReuseRefusedException,
                    RerunRefusedException {
        Fixture fixture = fixture(directory);
        try (RealProject project = fixture.project()) {
            Path runJson = fixture.layout().runFile();
            String text = Files.readString(runJson, StandardCharsets.UTF_8);
            int at = text.indexOf("        \"run-comet\": {");
            int end = text.indexOf("        },\n", at) + "        },\n".length();
            Files.writeString(runJson, text.substring(0, at) + text.substring(end));
            assertEquals(
                    "run run-0001's run-comet result cannot be reused, so Percolator cannot be"
                            + " rerun from it: no successful earlier execution is recorded",
                    fixture.refusal(fixture.compatible()));
        }
    }

    @Test
    @DisplayName("the selected Percolator itself is checked as a search's is")
    void choiceChecked(@TempDir Path directory)
            throws IOException,
                    InterruptedException,
                    RunBlockedException,
                    ReuseRefusedException,
                    RerunRefusedException {
        Fixture fixture = fixture(directory);
        try (RealProject project = fixture.project()) {
            PercolatorChoice choice = fixture.compatible();
            Path with = fixture.withXml().installedPath().orElseThrow();
            Path kept = fixture.search().root().resolve("kept");
            Files.move(with, kept);
            assertEquals(
                    "Percolator cannot be rerun for run run-0001:\n- the selected Percolator"
                            + " executable "
                            + with
                            + " does not exist or cannot be executed",
                    fixture.refusal(choice));
            Files.move(kept, with);
        }
    }

    @Test
    @DisplayName("a directory that is not a run of the project, or holds no run.json, is refused")
    void notARunOfTheProject(@TempDir Path directory)
            throws IOException,
                    InterruptedException,
                    RunBlockedException,
                    ReuseRefusedException,
                    RerunRefusedException {
        Fixture fixture = fixture(directory);
        try (RealProject project = fixture.project()) {
            PercolatorRerun rerun = fixture.rerun();
            RunLayout elsewhere = new RunLayout(fixture.search().root().resolve("elsewhere"));
            assertEquals(
                    elsewhere.root()
                            + " is not a run directory of the project "
                            + project.project().root()
                            + "; a rerun reuses a run of the same project",
                    assertThrows(
                                    RerunRefusedException.class,
                                    () ->
                                            rerun.preview(
                                                    project.store(),
                                                    project.project(),
                                                    elsewhere,
                                                    fixture.compatible()))
                            .getMessage());
            RunLayout empty =
                    new RunLayout(project.project().runsDirectory().resolve("20260101T000000Z-x"));
            String message =
                    assertThrows(
                                    RerunRefusedException.class,
                                    () ->
                                            rerun.preview(
                                                    project.store(),
                                                    project.project(),
                                                    empty,
                                                    fixture.compatible()))
                            .getMessage();
            assertTrue(
                    message.startsWith(
                            "the run at "
                                    + empty.root()
                                    + " cannot be rerun: its run.json cannot be read: "),
                    message);
            RunLayout rootless =
                    new RunLayout(
                            fixture.search()
                                    .root()
                                    .getFileSystem()
                                    .getRootDirectories()
                                    .iterator()
                                    .next());
            assertThrows(
                    RerunRefusedException.class,
                    () ->
                            rerun.preview(
                                    project.store(),
                                    project.project(),
                                    rootless,
                                    fixture.compatible()));
        }
    }

    @Test
    @DisplayName("a derived run is not rerun again: the message names its own source")
    void derivedSourceRefused(@TempDir Path directory)
            throws IOException,
                    InterruptedException,
                    RunBlockedException,
                    ReuseRefusedException,
                    RerunRefusedException {
        Fixture fixture = fixture(directory);
        try (RealProject project = fixture.project()) {
            DerivedRun derived = fixture.prepare(fixture.compatible());
            assertEquals(AttemptOutcome.SUCCEEDED, fixture.start(derived).outcome());
            RerunRefusedException thrown =
                    assertThrows(
                            RerunRefusedException.class,
                            () ->
                                    fixture.rerun()
                                            .preview(
                                                    project.store(),
                                                    project.project(),
                                                    derived.layout(),
                                                    fixture.choice(fixture.withoutXml(), NONE)));
            assertEquals(
                    "run run-0002 is itself a Percolator rerun of run run-0001 and ran no Comet"
                            + " search of its own; rerun Percolator from run run-0001, whose Comet"
                            + " results it reused",
                    thrown.getMessage());
        }
    }

    @Test
    @DisplayName(
            "a copy that does not hash to the source's record refuses the rerun and leaves no run"
                    + " directory behind")
    void badCopyLeavesNothing(@TempDir Path directory)
            throws IOException,
                    InterruptedException,
                    RunBlockedException,
                    ReuseRefusedException,
                    RerunRefusedException {
        Fixture fixture = fixture(directory);
        try (RealProject project = fixture.project()) {
            StreamingHashService real = new StreamingHashService();
            Path runs = project.project().runsDirectory();
            Path sourceRoot = fixture.layout().root();
            String lie = "e".repeat(64);
            HashService lying =
                    path -> {
                        FileHashes hashes = real.hash(path);
                        boolean copy =
                                path.startsWith(runs)
                                        && !path.startsWith(sourceRoot)
                                        && "merged.pin".equals(String.valueOf(path.getFileName()));
                        return copy ? new FileHashes(hashes.md5(), lie) : hashes;
                    };
            PercolatorRerun rerun = new PercolatorRerun(new CachingHashService(lying));
            List<String> before = project.runDirectories();
            RerunRefusedException thrown =
                    assertThrows(
                            RerunRefusedException.class,
                            () ->
                                    rerun.prepare(
                                            project.store(),
                                            project.lock(),
                                            fixture.layout(),
                                            fixture.compatible(),
                                            RealProject.application()));
            Path merged = fixture.layout().mergedPinFile();
            assertEquals(
                    "the file "
                            + merged
                            + " (role merged-pin) of run run-0001 has SHA-256 "
                            + lie
                            + ", but run run-0001 recorded SHA-256 "
                            + RealComet.sha256(merged)
                            + "; it has changed since it was recorded, so it is not reused and"
                            + " Percolator is not rerun from it (R-RUN-02). Nothing was created.",
                    thrown.getMessage());
            assertEquals(before, project.runDirectories(), "the reserved run was removed");
            assertEquals(1, before.size());
        }
    }

    @Test
    @DisplayName(
            "a rerun refused after its run was reserved, whose directory then cannot be removed,"
                    + " says so: the removal's failure rides on the refusal")
    void failedRemovalIsReported(@TempDir Path directory)
            throws IOException,
                    InterruptedException,
                    RunBlockedException,
                    ReuseRefusedException,
                    RerunRefusedException {
        Fixture fixture = fixture(directory);
        try (RealProject project = fixture.project()) {
            StreamingHashService real = new StreamingHashService();
            Path runs = project.project().runsDirectory();
            Path sourceRoot = fixture.layout().root();
            List<Path> locked = new ArrayList<>();
            HashService lying =
                    path -> {
                        FileHashes hashes = real.hash(path);
                        boolean copy =
                                path.startsWith(runs)
                                        && !path.startsWith(sourceRoot)
                                        && "merged.pin".equals(String.valueOf(path.getFileName()));
                        if (!copy) {
                            return hashes;
                        }
                        Path parent = RealComet.parentOf(path);
                        Files.setPosixFilePermissions(
                                parent, PosixFilePermissions.fromString("r-x------"));
                        locked.add(parent);
                        return new FileHashes(hashes.md5(), "e".repeat(64));
                    };
            PercolatorRerun rerun = new PercolatorRerun(new CachingHashService(lying));
            try {
                RerunRefusedException thrown =
                        assertThrows(
                                RerunRefusedException.class,
                                () ->
                                        rerun.prepare(
                                                project.store(),
                                                project.lock(),
                                                fixture.layout(),
                                                fixture.compatible(),
                                                RealProject.application()));
                assertEquals(1, locked.size());
                assertEquals(
                        1,
                        thrown.getSuppressed().length,
                        "the removal must fail on a directory without write permission; running as"
                                + " root, or on a file system ignoring modes, it would not");
                assertTrue(
                        thrown.getSuppressed()[0] instanceof AccessDeniedException,
                        thrown.getSuppressed()[0]::toString);
                assertEquals(
                        locked.get(0).resolve("merged.pin").toString(),
                        ((AccessDeniedException) thrown.getSuppressed()[0]).getFile());
                assertTrue(Files.isDirectory(locked.get(0)), "left behind, and reported");
            } finally {
                for (Path parent : locked) {
                    Files.setPosixFilePermissions(
                            parent, PosixFilePermissions.fromString("rwx------"));
                }
            }
        }
    }

    @Test
    @DisplayName(
            "the derived run re-checks its copies when it starts: a changed copy fails"
                    + " validate-configuration and Percolator is not launched")
    void copyChangedBeforeStart(@TempDir Path directory)
            throws IOException,
                    InterruptedException,
                    RunBlockedException,
                    ReuseRefusedException,
                    RerunRefusedException {
        Fixture fixture = fixture(directory);
        try (RealProject project = fixture.project()) {
            DerivedRun derived = fixture.prepare(fixture.compatible());
            Path copy = derived.layout().mergedPinFile();
            String was = RealComet.sha256(copy);
            long size = Files.size(copy);
            Files.writeString(copy, "\n", StandardCharsets.US_ASCII, StandardOpenOption.APPEND);
            Path with = fixture.withXml().installedPath().orElseThrow();
            RunResult result = fixture.start(derived);
            assertEquals(AttemptOutcome.FAILED, result.outcome());
            assertEquals(StepState.FAILED, result.states().get(EngineStep.VALIDATE_CONFIGURATION));
            assertEquals(0, launchesOf(fixture, with));
            assertEquals(
                    "the file "
                            + copy
                            + " (role merged-pin) of run run-0002 has SHA-256 "
                            + RealComet.sha256(copy)
                            + " ("
                            + (size + 1)
                            + " bytes), but run run-0002 recorded SHA-256 "
                            + was
                            + " ("
                            + size
                            + " bytes) when it was copied from run run-0001; a run's inputs are"
                            + " fixed when it is created, so this run cannot continue",
                    result.failures().get(EngineStep.VALIDATE_CONFIGURATION));
        }
    }

    @Test
    @DisplayName("a derived run's validate step checks the selected Percolator again")
    void validateChecksTheBuild(@TempDir Path directory)
            throws IOException,
                    InterruptedException,
                    RunBlockedException,
                    ReuseRefusedException,
                    RerunRefusedException {
        Fixture fixture = fixture(directory);
        try (RealProject project = fixture.project()) {
            DerivedRun derived = fixture.prepare(fixture.compatible());
            Path with = fixture.withXml().installedPath().orElseThrow();
            Files.writeString(
                    with, "# changed\n", StandardCharsets.US_ASCII, StandardOpenOption.APPEND);
            RunResult result = fixture.start(derived);
            assertEquals(AttemptOutcome.FAILED, result.outcome());
            assertEquals(StepState.FAILED, result.states().get(EngineStep.VALIDATE_CONFIGURATION));
            assertEquals(0, launchesOf(fixture, with));
            assertEquals(
                    "the rerun cannot start:\n- the Percolator executable "
                            + with
                            + " has SHA-256 "
                            + RealComet.sha256(with)
                            + ", but Percolator 3.07.1 was selected at "
                            + derived.settings().get("percolator.binary-sha256")
                            + "; it has changed since it was selected",
                    result.failures().get(EngineStep.VALIDATE_CONFIGURATION));
        }
    }

    @Test
    @DisplayName("the derived run's finalise step fails if a copy changed while Percolator ran")
    void finaliseRechecks(@TempDir Path directory)
            throws IOException,
                    InterruptedException,
                    RunBlockedException,
                    ReuseRefusedException,
                    RerunRefusedException {
        Fixture fixture = fixture(directory);
        try (RealProject project = fixture.project()) {
            DerivedRun derived = fixture.prepare(fixture.compatible());
            Path params = derived.layout().cometParamsFile();
            String was = RealComet.sha256(params);
            long size = Files.size(params);
            StepAction finalise = derived.actions().get(EngineStep.FINALISE_PROVENANCE);
            Files.writeString(params, "#\n", StandardCharsets.US_ASCII, StandardOpenOption.APPEND);
            StepFailedException thrown =
                    assertThrows(
                            StepFailedException.class,
                            () -> finalise.execute(Nulls.of(StepContext.class)));
            assertEquals(
                    "the file "
                            + params
                            + " (role comet-params) of run run-0002 has SHA-256 "
                            + RealComet.sha256(params)
                            + " ("
                            + (size + 2)
                            + " bytes), but run run-0002 recorded SHA-256 "
                            + was
                            + " ("
                            + size
                            + " bytes) when it was copied from run run-0001; a run's inputs are"
                            + " fixed when it is created, so this run cannot continue",
                    thrown.getMessage());
            Files.delete(params);
            assertEquals(
                    "the file "
                            + params
                            + " (role comet-params) of run run-0002, copied from run run-0001, no"
                            + " longer exists; run run-0002 recorded SHA-256 "
                            + was,
                    assertThrows(
                                    StepFailedException.class,
                                    () -> finalise.execute(Nulls.of(StepContext.class)))
                            .getMessage());
        }
    }

    @Test
    @DisplayName("a derived run's plan and actions: no Comet step has an action")
    void derivedRunShape(@TempDir Path directory)
            throws IOException,
                    InterruptedException,
                    RunBlockedException,
                    ReuseRefusedException,
                    RerunRefusedException {
        Fixture fixture = fixture(directory);
        try (RealProject project = fixture.project()) {
            DerivedRun derived = fixture.prepare(fixture.compatible());
            assertEquals(
                    EnumSet.of(
                            EngineStep.VALIDATE_CONFIGURATION,
                            EngineStep.RESOLVE_PERCOLATOR,
                            EngineStep.RUN_PERCOLATOR,
                            EngineStep.PARSE_PERCOLATOR,
                            EngineStep.FINALISE_PROVENANCE),
                    derived.actions().keySet());
            assertEquals(
                    PercolatorRerun.planFor(IndexMode.NONE).toString(), derived.plan().toString());
            assertEquals(new RunId("run-0001"), derived.source().runId());
            assertEquals(new RunId("run-0002"), derived.identity().runId());
            assertTrue(derived.actions().get(EngineStep.VALIDATE_CONFIGURATION).validates());
            assertEquals(
                    List.of(
                            DeclaredFile.input(
                                    RunDeclarations.PARAMS, derived.layout().cometParamsFile())),
                    derived.actions().get(EngineStep.VALIDATE_CONFIGURATION).declaration().files());
            assertEquals(
                    StepDeclaration.NOTHING,
                    derived.actions().get(EngineStep.FINALISE_PROVENANCE).declaration());
            assertEquals(derived.request().plan().toString(), derived.plan().toString());
            assertEquals(derived.settings(), derived.request().settings());
            IllegalArgumentException notDerived =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    new DerivedRun(
                                            project.project(),
                                            fixture.source().identity(),
                                            derived.percolator(),
                                            derived.plan(),
                                            project.store(),
                                            project.lock(),
                                            RealProject.application(),
                                            derived.settings(),
                                            "f".repeat(64)));
            assertEquals(
                    "run run-0001 is not a derived run: it names no source",
                    notDerived.getMessage());
        }
    }

    @Test
    @DisplayName("the derived plan for an index mode provides the index build too")
    void planWithIndex() {
        assertEquals(
                "Plan[validate-configuration, resolve-percolator, run-percolator,"
                        + " parse-percolator, finalise-provenance] provided[serialise-comet-params,"
                        + " build-comet-index, run-comet, validate-comet-outputs, merge-pin]",
                PercolatorRerun.planFor(IndexMode.FRAGMENT_ION).toString());
        assertEquals(
                "Plan[validate-configuration, resolve-percolator, run-percolator,"
                        + " parse-percolator, finalise-provenance] provided[serialise-comet-params,"
                        + " run-comet, validate-comet-outputs, merge-pin]",
                PercolatorRerun.planFor(IndexMode.NONE).toString());
    }
}
