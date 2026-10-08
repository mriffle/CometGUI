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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.project.ProjectLayout;
import org.cometgui.domain.run.ArchivedFile;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.run.RunDerivation;
import org.cometgui.domain.run.RunIdentity;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.Diagnostic;
import org.cometgui.params.comet.parser.CometParamsParser;
import org.cometgui.params.comet.parser.ParseResult;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.MetadataLoader;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.provenance.manifest.ApplicationRecord;
import org.cometgui.tools.comet.PinDecoyConfiguration;
import org.cometgui.workflow.engine.RecordedFingerprints;
import org.cometgui.workflow.engine.ReuseRefusedException;
import org.cometgui.workflow.engine.RunHandle;
import org.cometgui.workflow.engine.StepStateListener;
import org.cometgui.workflow.engine.WorkflowEngine;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.Plan;
import org.cometgui.workflow.state.RerunDecision;
import org.cometgui.workflow.state.RerunPreview;
import org.cometgui.workflow.state.RerunReason;
import org.cometgui.workflow.state.StepInputs;
import org.cometgui.workflow.state.StepKind;
import org.cometgui.workflow.state.StepVerdict;
import org.cometgui.workflow.storage.ProjectLock;
import org.cometgui.workflow.storage.ProjectStore;
import org.cometgui.workflow.storage.ReservedRun;
import org.cometgui.workflow.storage.RunStore;

/**
 * The compatible-version Percolator rerun (design decision P9-11; the specification's <em>Stage
 * reruns</em>: "choosing an XML-capable Percolator for Limelight after running a version without
 * XML reruns Percolator from the preserved merged PIN, then conversion; Comet is reused").
 *
 * <p>The action is general: any earlier run of the project whose Comet search produced a merged PIN
 * may be rerun with any other Percolator selection, settings or enabled stages. It is always a
 * <strong>new run</strong>, because a different Percolator is a different configuration ({@code
 * R-RUN-06}; only an unchanged configuration is retried), and it is a <em>derived</em> run: its
 * {@code run.json} names the source in {@code derivedFrom}, and it executes no Comet.
 *
 * <h2>What a rerun does</h2>
 *
 * <ol>
 *   <li>{@link #preview} -- reads and checks the source, and creates nothing ({@link RerunSource}):
 *       its {@code run.json} and {@code provenance.json}; that it has ended; that its merged PIN
 *       and {@code comet.params}, re-hashed with {@code CachingHashService.rehash}, equal the
 *       SHA-256 it recorded ({@code R-RUN-02}); the selected Percolator itself (the same check a
 *       search's Percolator half gets). Then, over the declared graph, the Comet steps' recorded
 *       fingerprints: every Comet result step the new run takes from the source must be one the
 *       source's own rerun preview would <em>reuse</em> -- succeeded, with these inputs -- and
 *       {@code run-percolator} must not be: a source that already ran this exact Percolator has
 *       nothing to rerun.
 *   <li>{@link #prepare} -- the same checks, then the run is reserved, the source's {@code
 *       comet.params} and merged PIN are <strong>copied</strong> in and each copy re-hashed and
 *       held to the source's record, {@code percolator-settings.json} is written once, the command
 *       is built from the selection's probed capabilities, and {@code run.json} records the
 *       identity, schema version 2. Anything that fails after the run was reserved removes the run
 *       directory again, so a refused rerun leaves nothing behind.
 *   <li>{@link #start} -- an attempt through {@code WorkflowEngine.start}, as for any run.
 * </ol>
 *
 * <h2>Why a copy</h2>
 *
 * <p>The merged PIN is copied, not hard-linked. A hard link shares the file -- its bytes and its
 * permissions -- so anything that later wrote or re-permissioned one run's PIN would change the
 * other's, and the source would no longer be provably untouched; a copy keeps the source run's
 * directory byte-identical and makes the derived run self-contained, so either can be moved,
 * archived or deleted alone. The cost is one more file of the PIN's size.
 *
 * <h2>Comet in the new run's records</h2>
 *
 * <p>By reference, never as executed: no Comet tool record and no {@code comet.*} setting. The
 * {@code rerun.*} settings ({@link RerunProvenance}) name the source run, its manifest's SHA-256,
 * the Comet release and executable its search ran, its parameter file's and merged PIN's SHA-256
 * and the steps reused; {@code run.json}'s identity copies the source's Comet members, which
 * describe the search whose merged PIN the run rescored.
 */
public final class PercolatorRerun {

    /** The steps a derived run is asked to reach. */
    private static final Set<EngineStep> WANTED =
            EnumSet.of(EngineStep.PARSE_PERCOLATOR, EngineStep.FINALISE_PROVENANCE);

    private final CachingHashService hashes;

    private final CuratedMetadata metadata;

    /**
     * Creates the rerun action with the bundled Comet parameter metadata, which reads the source's
     * archived {@code comet.params} for its decoy configuration.
     *
     * @param hashes the one hasher -- the same instance the engine's services hold
     */
    public PercolatorRerun(CachingHashService hashes) {
        this(hashes, MetadataLoader.loadBundled());
    }

    PercolatorRerun(CachingHashService hashes, CuratedMetadata metadata) {
        this.hashes = Objects.requireNonNull(hashes, "hashes");
        this.metadata = Objects.requireNonNull(metadata, "metadata");
    }

    /**
     * The plan of a derived run: the Percolator steps and what they need, with the source's Comet
     * results provided. The provided steps are every result step of the source's search -- the
     * Comet-only plan for its index mode -- except {@code finalise-provenance}, which every run
     * does for itself.
     *
     * @param mode the source's index mode
     * @return {@code validate-configuration}, {@code resolve-percolator}, {@code run-percolator},
     *     {@code parse-percolator}, {@code finalise-provenance}; provided {@code
     *     serialise-comet-params}, [{@code build-comet-index},] {@code run-comet}, {@code
     *     validate-comet-outputs}, {@code merge-pin}
     */
    public static Plan planFor(IndexMode mode) {
        Set<EngineStep> provided = EnumSet.noneOf(EngineStep.class);
        for (EngineStep step : CometWorkflow.planFor(mode).steps()) {
            if (step.kind() == StepKind.RESULT && !WANTED.contains(step)) {
                provided.add(step);
            }
        }
        return Plan.covering(WANTED, provided);
    }

    /**
     * What a rerun of a run with a Percolator choice would execute and reuse. Reads and re-hashes;
     * creates, writes and launches nothing.
     *
     * @param store the project's run store
     * @param project the project
     * @param source the run to rerun Percolator for
     * @param choice the Percolator to rerun with
     * @return the preview
     * @throws RerunRefusedException if the rerun cannot be made, saying why
     * @throws IOException if a file the checks need cannot be read
     */
    public PercolatorRerunPreview preview(
            RunStore store, ProjectLayout project, RunLayout source, PercolatorChoice choice)
            throws IOException, RerunRefusedException {
        return check(store, project, source, choice).preview();
    }

    /**
     * Checks a rerun and, if nothing refuses it, records it as a new, derived run.
     *
     * @param store the project's run store
     * @param lock the project's lock, held
     * @param source the run to rerun Percolator for
     * @param choice the Percolator to rerun with
     * @param application the application record for provenance
     * @return the recorded run
     * @throws RerunRefusedException if the rerun cannot be made; no run directory exists for it
     * @throws IOException if the run cannot be reserved, copied into or recorded; its directory has
     *     been removed again. If removing it fails too, that failure is attached to the exception
     *     as a suppressed one, so the directory left behind is never silent.
     */
    public DerivedRun prepare(
            RunStore store,
            ProjectLock lock,
            RunLayout source,
            PercolatorChoice choice,
            ApplicationRecord application)
            throws IOException, RerunRefusedException {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(lock, "lock");
        Objects.requireNonNull(application, "application");
        ProjectLayout project = lock.project();
        Checked checked = check(store, project, source, choice);
        PinDecoyConfiguration decoys = decoysOf(checked.source());
        ReservedRun reserved = store.reserve(lock);
        try {
            return record(store, lock, reserved, checked, choice, decoys, application);
        } catch (RerunRefusedException | IOException | RuntimeException failed) {
            try {
                remove(reserved.layout().root());
            } catch (IOException notRemoved) {
                failed.addSuppressed(notRemoved);
            }
            throw failed;
        }
    }

    /**
     * Starts an attempt of a derived run -- the first, or a retry.
     *
     * @param engine the workflow engine, built with the same hasher
     * @param run the derived run
     * @param listener observes every state change
     * @return the running attempt
     * @throws IOException as {@code WorkflowEngine.start}
     * @throws ReuseRefusedException if a result the attempt would reuse no longer matches its
     *     record; nothing started
     */
    public RunHandle start(WorkflowEngine engine, DerivedRun run, StepStateListener listener)
            throws IOException, ReuseRefusedException {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(run, "run");
        return engine.start(run.request(), listener);
    }

    /** A source that passed every check, the new run's plan, and the preview shown. */
    private record Checked(RerunSource source, Plan plan, PercolatorRerunPreview preview) {}

    private Checked check(
            RunStore store, ProjectLayout project, RunLayout source, PercolatorChoice choice)
            throws IOException, RerunRefusedException {
        Objects.requireNonNull(choice, "choice");
        RerunSource checked = RerunSource.inspect(store, project, source, hashes);
        String name = checked.name();
        List<String> problems = PreRunChecks.percolatorProblems(hashes, project, choice);
        if (!problems.isEmpty()) {
            throw new RerunRefusedException(
                    "Percolator cannot be rerun for "
                            + name
                            + ":\n- "
                            + String.join("\n- ", problems));
        }
        RunIdentity identity = checked.identity();
        StepInputs inputs =
                RunInputs.withPercolator(
                        RunInputs.recorded(identity, checked.cometSha256()),
                        PercolatorSettingsFile.render(choice.settings(), choice.enabledStages()),
                        choice.selection().version().text(),
                        choice.selection().sha256());
        RerunPreview against =
                RerunPreview.compute(
                        CometWorkflow.planFor(identity.indexMode(), true),
                        inputs,
                        RecordedFingerprints.fromRecorded(
                                checked.descriptor().succeededFingerprints()));
        Plan plan = planFor(identity.indexMode());
        for (EngineStep step : plan.provided()) {
            StepVerdict verdict = against.verdict(step);
            if (verdict.decision() != RerunDecision.REUSE) {
                throw new RerunRefusedException(
                        name
                                + "'s "
                                + step.id()
                                + " result cannot be reused, so Percolator cannot be rerun from"
                                + " it: "
                                + reasons(verdict));
            }
        }
        if (against.verdict(EngineStep.RUN_PERCOLATOR).decision() == RerunDecision.REUSE) {
            throw new RerunRefusedException(
                    name
                            + " already ran Percolator "
                            + choice.selection().version().text()
                            + " with these settings and downstream stages, and it succeeded;"
                            + " a rerun with nothing changed would repeat it. Choose another build,"
                            + " other settings or other stages.");
        }
        RerunPreview own = RerunPreview.compute(plan, inputs, Map.of());
        return new Checked(
                checked,
                plan,
                new PercolatorRerunPreview(
                        identity.runId(),
                        own,
                        checked.layout().mergedPinFile(),
                        checked.mergedPin().hashes().sha256()));
    }

    private static String reasons(StepVerdict verdict) {
        List<String> reasons = new ArrayList<>();
        for (RerunReason reason : verdict.reasons()) {
            reasons.add(reason.describe());
        }
        return String.join("; ", reasons);
    }

    /**
     * The decoy configuration the source searched with, read from its archived {@code comet.params}
     * by the one parameter parser -- the prefix the merged PIN is checked with ({@code R-DEC-03}).
     */
    private PinDecoyConfiguration decoysOf(RerunSource source)
            throws RerunRefusedException, IOException {
        Path file = source.layout().cometParamsFile();
        ParseResult parsed;
        try {
            parsed =
                    new CometParamsParser(
                                    metadata, ToolVersion.parse(source.identity().cometRelease()))
                            .parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException unknownRelease) {
            throw new RerunRefusedException(
                    "the archived parameter file "
                            + file
                            + " of "
                            + source.name()
                            + " cannot be read for its decoy configuration: "
                            + unknownRelease.getMessage());
        }
        Optional<CometParameters> model = parsed.model();
        if (model.isEmpty()) {
            List<String> errors = new ArrayList<>();
            for (Diagnostic error : parsed.errors()) {
                errors.add(error.message());
            }
            throw new RerunRefusedException(
                    "the archived parameter file "
                            + file
                            + " of "
                            + source.name()
                            + " cannot be read for its decoy configuration: "
                            + String.join("; ", errors));
        }
        try {
            return CometRun.decoysOf(model.get());
        } catch (IllegalStateException noSource) {
            throw new RerunRefusedException(
                    "the archived parameter file "
                            + file
                            + " of "
                            + source.name()
                            + " has no usable decoy configuration: "
                            + noSource.getMessage());
        }
    }

    private DerivedRun record(
            RunStore store,
            ProjectLock lock,
            ReservedRun reserved,
            Checked checked,
            PercolatorChoice choice,
            PinDecoyConfiguration decoys,
            ApplicationRecord application)
            throws IOException, RerunRefusedException {
        RerunSource source = checked.source();
        RunLayout layout = reserved.layout();
        FileHashes parameters =
                copy(
                        source,
                        source.layout().cometParamsFile(),
                        layout.cometParamsFile(),
                        RunDeclarations.PARAMS,
                        source.identity().parameters().hashes().sha256());
        FileHashes pin =
                copy(
                        source,
                        source.layout().mergedPinFile(),
                        layout.mergedPinFile(),
                        RunDeclarations.MERGED_PIN,
                        source.mergedPin().hashes().sha256());
        PercolatorRun half = CometWorkflow.preparePercolator(layout, hashes, decoys, choice);
        RunIdentity from = source.identity();
        RunIdentity identity =
                new RunIdentity(
                        reserved.runId(),
                        ProjectStore.read(lock.project()).id(),
                        reserved.created(),
                        from.cometRelease(),
                        from.spectra(),
                        from.fasta(),
                        new ArchivedFile(
                                RunLayout.cometParamsRelativePath(),
                                Files.size(layout.cometParamsFile()),
                                parameters),
                        from.indexMode(),
                        from.databaseDelivery(),
                        Optional.of(
                                new RunDerivation(
                                        from.runId(),
                                        from.created(),
                                        source.manifestFile(),
                                        new ArchivedFile(
                                                RunLayout.mergedPinRelativePath(),
                                                Files.size(layout.mergedPinFile()),
                                                pin))));
        store.record(lock, identity);
        Map<String, String> settings =
                new TreeMap<>(
                        PercolatorProvenance.settings(
                                choice,
                                half.command(),
                                half.settings().hashes().sha256(),
                                decoys.prefix()));
        settings.putAll(RerunProvenance.settings(source, checked.plan().provided()));
        return new DerivedRun(
                lock.project(),
                identity,
                half,
                checked.plan(),
                store,
                lock,
                application,
                settings,
                source.cometSha256());
    }

    /**
     * Copies one of the source's files into the new run and re-hashes the copy -- never from the
     * cache -- against what the source recorded: the bytes the new run uses are the recorded ones,
     * whatever happened between the check and the copy.
     */
    private FileHashes copy(RerunSource source, Path from, Path to, String role, String recorded)
            throws IOException, RerunRefusedException {
        Files.copy(from, to);
        FileHashes copied = hashes.rehash(to);
        if (!copied.sha256().equals(recorded)) {
            throw new RerunRefusedException(
                    RerunSource.changed(source.name(), role, from, recorded, copied.sha256()));
        }
        return copied;
    }

    /** Removes a run directory this rerun reserved and could not finish recording. */
    private static void remove(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        List<Path> paths;
        try (Stream<Path> walked = Files.walk(root)) {
            paths = walked.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path path : paths) {
            Files.deleteIfExists(path);
        }
    }
}
