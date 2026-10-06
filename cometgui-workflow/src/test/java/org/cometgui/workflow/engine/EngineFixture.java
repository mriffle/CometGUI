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

package org.cometgui.workflow.engine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.project.ProjectId;
import org.cometgui.domain.project.ProjectLayout;
import org.cometgui.domain.run.ArchivedFile;
import org.cometgui.domain.run.DatabaseDelivery;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.run.RecordedInput;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.RunIdentity;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.run.SpectrumInput;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.provenance.manifest.ApplicationRecord;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.InputKind;
import org.cometgui.workflow.state.InputValue;
import org.cometgui.workflow.state.Plan;
import org.cometgui.workflow.state.StepInputs;
import org.cometgui.workflow.storage.ProjectLock;
import org.cometgui.workflow.storage.ProjectStore;
import org.cometgui.workflow.storage.ReservedRun;
import org.cometgui.workflow.storage.RunStore;

/**
 * A real project with one recorded run, real input files, the real process service and the real
 * hasher -- everything the engine touches, so that what a test reads back is what the engine wrote.
 *
 * <p>The plan is phase 08's: everything up to finalising core provenance, without an index. The
 * standard actions stand in for the Comet steps unit 6 will write: {@code run-comet} runs one fake
 * invocation per spectrum file, writing that file's pepXML and PIN; {@code merge-pin} writes the
 * merged PIN in Java.
 */
final class EngineFixture implements AutoCloseable {

    static final int CORES = 4;

    static final int CAP = 8;

    static final Plan PLAN = Plan.covering(Set.of(EngineStep.FINALISE_PROVENANCE));

    private final Path root;

    private final Path records;

    private final ProjectLayout project;

    private final ProjectLock lock;

    private final RunStore store;

    private final RunLayout layout;

    private final List<Path> spectra;

    private final Path fasta;

    private final RunIdentity identity;

    private final CapturingSink sink = new CapturingSink();

    private final CachingHashService hashes;

    private EngineFixture(
            Path root,
            Path records,
            ProjectLayout project,
            ProjectLock lock,
            RunStore store,
            RunLayout layout,
            List<Path> spectra,
            Path fasta,
            RunIdentity identity,
            CachingHashService hashes) {
        this.root = root;
        this.records = records;
        this.project = project;
        this.lock = lock;
        this.store = store;
        this.layout = layout;
        this.spectra = spectra;
        this.fasta = fasta;
        this.identity = identity;
        this.hashes = hashes;
    }

    Path root() {
        return root;
    }

    Path records() {
        return records;
    }

    ProjectLayout project() {
        return project;
    }

    ProjectLock lock() {
        return lock;
    }

    RunStore store() {
        return store;
    }

    RunLayout layout() {
        return layout;
    }

    List<Path> spectra() {
        return spectra;
    }

    Path fasta() {
        return fasta;
    }

    RunIdentity identity() {
        return identity;
    }

    CapturingSink sink() {
        return sink;
    }

    CachingHashService hashes() {
        return hashes;
    }

    static EngineFixture create(Path tmp, int spectrumCount) throws IOException {
        return create(tmp, spectrumCount, new CachingHashService(new StreamingHashService()));
    }

    static EngineFixture create(Path tmp, int spectrumCount, CachingHashService hashes)
            throws IOException {
        Path root = tmp.toRealPath();
        Path inputs = Files.createDirectories(root.resolve("in"));
        Path records = Files.createDirectories(root.resolve("records"));
        StreamingHashService hasher = new StreamingHashService();
        List<Path> spectra = new ArrayList<>();
        List<RecordedInput> recorded = new ArrayList<>();
        for (int position = 1; position <= spectrumCount; position++) {
            Path spectrum = inputs.resolve("k562_" + position + ".mzML");
            Files.writeString(spectrum, "spectra " + position + "\n", StandardCharsets.UTF_8);
            spectra.add(spectrum);
            recorded.add(recordedInput(spectrum, hasher));
        }
        Path fasta = inputs.resolve("db.fasta");
        Files.writeString(fasta, ">P1\nPEPTIDEK\n", StandardCharsets.UTF_8);
        Clock clock = Clock.systemUTC();
        ProjectLayout project = new ProjectLayout(root.resolve("project"));
        new ProjectStore(clock).create(project, new ProjectId("project-engine"));
        ProjectLock lock = ProjectLock.acquire(project, clock);
        RunStore store = new RunStore(project, clock, () -> new RunId("run-0001"));
        ReservedRun reserved = store.reserve(lock);
        RunLayout layout = reserved.layout();
        Files.writeString(
                layout.cometParamsFile(),
                "# comet.params\nnum_threads = 1\n",
                StandardCharsets.UTF_8);
        FileHashes paramsHashes = hasher.hash(layout.cometParamsFile());
        RunIdentity identity =
                new RunIdentity(
                        reserved.runId(),
                        new ProjectId("project-engine"),
                        reserved.created(),
                        "2026.03.0",
                        RunIdentity.spectraOf(recorded),
                        recordedInput(fasta, hasher),
                        new ArchivedFile(
                                RunLayout.cometParamsRelativePath(),
                                Files.size(layout.cometParamsFile()),
                                paramsHashes),
                        IndexMode.NONE,
                        DatabaseDelivery.PARAMETER_FILE);
        store.record(lock, identity);
        return new EngineFixture(
                root, records, project, lock, store, layout, spectra, fasta, identity, hashes);
    }

    private static RecordedInput recordedInput(Path file, StreamingHashService hasher)
            throws IOException {
        return new RecordedInput(
                file,
                Files.size(file),
                Files.getLastModifiedTime(file).toInstant(),
                hasher.hash(file));
    }

    EngineServices services(int cores, int cap) {
        Clock clock = Clock.systemUTC();
        return new EngineServices(
                new ProcessService(clock),
                clock,
                SecretRedactor.patternsOnly(),
                sink,
                hashes,
                cores,
                cap);
    }

    WorkflowEngine engine() {
        return new WorkflowEngine(services(CORES, CAP));
    }

    /** The inputs as the recorded identity describes them -- what a retry of this run uses. */
    StepInputs inputs() throws IOException {
        List<InputValue.NamedFile> files = new ArrayList<>();
        for (SpectrumInput spectrum : identity.spectra()) {
            files.add(
                    new InputValue.NamedFile(
                            "k562_" + spectrum.position() + ".mzML", spectrum.file().hashes()));
        }
        Map<InputKind, InputValue> values = new EnumMap<>(InputKind.class);
        values.put(InputKind.SPECTRUM_FILES, InputValue.files(files));
        values.put(InputKind.FASTA, InputValue.file("db.fasta", identity.fasta().hashes()));
        values.put(
                InputKind.COMET_PARAMETERS,
                new InputValue.Bytes(identity.parameters().hashes().sha256()));
        values.put(InputKind.COMET_INDEX_MODE, InputValue.text("none"));
        values.put(InputKind.COMET_TOOL, InputValue.tool("fake-1.0", fakeTool().hashes().sha256()));
        return StepInputs.of(values);
    }

    RunRequest request(Map<EngineStep, StepAction> actions) throws IOException {
        return new RunRequest(
                store,
                lock,
                layout,
                PLAN,
                inputs(),
                Set.of(),
                actions,
                ApplicationRecord.capture("0.1.0-test", "engine-tests"),
                Map.of("comet.release", "2026.03.0"));
    }

    ToolIdentity fakeTool() {
        Path tool = EngineFakes.classFile();
        try {
            return new ToolIdentity(
                    "fake-comet",
                    "1.0",
                    Optional.of("v1.0"),
                    tool,
                    new StreamingHashService().hash(tool),
                    false,
                    Optional.empty(),
                    Set.of("fake"),
                    List.of());
        } catch (IOException unreadable) {
            throw new IllegalStateException(unreadable);
        }
    }

    Invocation fake(String stageId, String scenario, String... args) {
        return new Invocation(
                stageId,
                fakeTool(),
                new ToolCommand(EngineFakes.argv(scenario, args), layout.root(), Map.of()));
    }

    static String stageId(int position) {
        return RunLayout.cometStageId(position);
    }

    String base(int position) {
        return "k562_" + position;
    }

    Path pepXml(int position) {
        return layout.pepXmlFile(base(position));
    }

    Path pin(int position) {
        return layout.pinFile(base(position));
    }

    /** What run-comet declares: every spectrum and the parameter file in, two outputs per file. */
    StepDeclaration cometDeclaration() {
        List<DeclaredFile> files = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (int position = 1; position <= spectra.size(); position++) {
            files.add(DeclaredFile.input("spectrum", spectra.get(position - 1)));
        }
        files.add(DeclaredFile.input("comet-params", layout.cometParamsFile()));
        for (int position = 1; position <= spectra.size(); position++) {
            files.add(DeclaredFile.output("pepxml", pepXml(position)));
            files.add(DeclaredFile.output("pin", pin(position)));
            ids.add(stageId(position));
        }
        return new StepDeclaration(files, ids);
    }

    /** One fake invocation per spectrum writing its two outputs, run with the given threads. */
    FakeStep.Body succeedingComet(int threads) {
        return context -> {
            List<Invocation> invocations = new ArrayList<>();
            for (int position = 1; position <= spectra.size(); position++) {
                invocations.add(
                        fake(
                                stageId(position),
                                "succeed",
                                pepXml(position).toString(),
                                pin(position).toString()));
            }
            context.invokeAll(invocations, threads);
        };
    }

    StepDeclaration pinInputs() {
        List<DeclaredFile> files = new ArrayList<>();
        for (int position = 1; position <= spectra.size(); position++) {
            files.add(DeclaredFile.input("pin", pin(position)));
        }
        return new StepDeclaration(files, List.of());
    }

    StepDeclaration mergeDeclaration() {
        List<DeclaredFile> files = new ArrayList<>(pinInputs().files());
        files.add(DeclaredFile.output("merged-pin", layout.mergedPinFile()));
        return new StepDeclaration(files, List.of());
    }

    FakeStep.Body writingMergedPin() {
        return context -> {
            StringBuilder merged = new StringBuilder("SpecId\tLabel\n");
            for (int position = 1; position <= spectra.size(); position++) {
                merged.append(Files.readString(pin(position), StandardCharsets.UTF_8));
            }
            Files.writeString(layout.mergedPinFile(), merged, StandardCharsets.UTF_8);
            context.addDetail("merge.rows", Integer.toString(spectra.size()));
        };
    }

    /** The standard eight actions; a test replaces the ones it is about. */
    Map<EngineStep, StepAction> standardActions() {
        Map<EngineStep, StepAction> actions = new EnumMap<>(EngineStep.class);
        actions.put(
                EngineStep.VALIDATE_CONFIGURATION,
                new FakeStep(StepDeclaration.NOTHING, true, context -> {}, context -> {}));
        actions.put(EngineStep.RESOLVE_COMET, FakeStep.nothing());
        actions.put(
                EngineStep.SERIALISE_COMET_PARAMS,
                FakeStep.doing(
                        new StepDeclaration(
                                List.of(
                                        DeclaredFile.output(
                                                "comet-params", layout.cometParamsFile())),
                                List.of()),
                        context -> {}));
        List<DeclaredFile> hashed = new ArrayList<>();
        for (Path spectrum : spectra) {
            hashed.add(DeclaredFile.input("spectrum", spectrum));
        }
        hashed.add(DeclaredFile.input("fasta", fasta));
        actions.put(
                EngineStep.HASH_INPUTS,
                FakeStep.doing(new StepDeclaration(hashed, List.of()), context -> {}));
        actions.put(EngineStep.RUN_COMET, FakeStep.doing(cometDeclaration(), succeedingComet(1)));
        actions.put(EngineStep.VALIDATE_COMET_OUTPUTS, FakeStep.doing(pinInputs(), context -> {}));
        actions.put(EngineStep.MERGE_PIN, FakeStep.doing(mergeDeclaration(), writingMergedPin()));
        actions.put(EngineStep.FINALISE_PROVENANCE, FakeStep.nothing());
        return actions;
    }

    @Override
    public void close() throws IOException {
        lock.close();
    }
}
