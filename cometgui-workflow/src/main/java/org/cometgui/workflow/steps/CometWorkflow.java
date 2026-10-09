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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.project.ProjectLayout;
import org.cometgui.domain.run.ArchivedFile;
import org.cometgui.domain.run.DatabaseDelivery;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.run.RecordedInput;
import org.cometgui.domain.run.RunIdentity;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ParameterValue;
import org.cometgui.params.comet.writer.CanonicalParamsWriter;
import org.cometgui.params.comet.writer.WrittenParams;
import org.cometgui.params.percolator.resolution.AdvisoryRendering;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.provenance.manifest.ApplicationRecord;
import org.cometgui.tools.comet.PinDecoyConfiguration;
import org.cometgui.tools.percolator.PercolatorCommand;
import org.cometgui.tools.percolator.PercolatorCommands;
import org.cometgui.tools.percolator.PercolatorRefusedException;
import org.cometgui.workflow.engine.ReuseCheck;
import org.cometgui.workflow.engine.ReuseRefusedException;
import org.cometgui.workflow.engine.RunHandle;
import org.cometgui.workflow.engine.StepStateListener;
import org.cometgui.workflow.engine.ToolIdentity;
import org.cometgui.workflow.engine.WorkflowEngine;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.InputValue;
import org.cometgui.workflow.state.Plan;
import org.cometgui.workflow.state.StepInputs;
import org.cometgui.workflow.storage.ProjectLock;
import org.cometgui.workflow.storage.ProjectStore;
import org.cometgui.workflow.storage.ReservedRun;
import org.cometgui.workflow.storage.RunStore;

/**
 * A run, end to end, through the one workflow engine -- the Comet search, and with a {@link
 * PercolatorChoice} in the request Percolator after it: the entry point the user interface (and
 * every test of a real run) uses.
 *
 * <h2>The life of a run</h2>
 *
 * <ol>
 *   <li>{@link #check} -- Run readiness. The pre-run check ({@link PreRunReport}): the files, the
 *       selected Comet, the FASTA's decoy census, an existing index's header, and the one validator
 *       over all of it. Callable on its own, any number of times; it writes nothing.
 *   <li>{@link #prepare} -- the same check, and if anything blocks, {@link RunBlockedException}
 *       with the report: <strong>no run directory is created and nothing is launched</strong>.
 *       Otherwise the run is reserved, {@code parameters/comet.params} is written once by {@code
 *       CanonicalParamsWriter.writeOnce} ({@code R-PARAM-12}; that file is every invocation's
 *       {@code -P}), the inputs are hashed by the one hasher, and {@code run.json} records the
 *       identity: the release, every spectrum file with its position and {@code -N} base, the
 *       database, the archived parameter file and its hashes, the index mode and how the database
 *       reaches Comet ({@code R-CMT-04}). With Percolator, {@code
 *       parameters/percolator-settings.json} is written once too, the command is built from the
 *       selection's probed capabilities, and the run's provenance settings ({@link
 *       PercolatorProvenance}) are fixed.
 *   <li>{@link #start} -- an attempt through {@code WorkflowEngine.start}. The plan is {@code
 *       validate-configuration}, {@code resolve-comet}, {@code serialise-comet-params}, {@code
 *       hash-inputs}, [{@code build-comet-index}], {@code run-comet}, {@code
 *       validate-comet-outputs}, {@code merge-pin} and {@code finalise-provenance} ({@link
 *       #planFor}), with Percolator also {@code resolve-percolator}, {@code run-percolator}, {@code
 *       parse-percolator} and {@code finalise-results}; a retry is simply another {@link #start} of
 *       the same {@link PreparedRun}.
 *   <li>{@link #preview} -- the rerun preview of a changed configuration against a recorded run
 *       ({@code R-RUN-01}), with every result it would reuse re-hashed.
 * </ol>
 *
 * <h2>Where each file goes</h2>
 *
 * <p>Everything a run writes is inside its run directory ({@code R-CMT-08}) -- with one designed
 * exception, the project's own index cache ({@code index-cache/<key>/}, P8-8), which is the
 * project's and not the user's. Nothing is written beside the spectra or the FASTA, and no input is
 * modified.
 */
public final class CometWorkflow {

    /** The provenance setting recording the Comet release. */
    public static final String RELEASE_SETTING = "comet.release";

    /** The provenance setting recording the index mode. */
    public static final String INDEX_MODE_SETTING = "comet.index-mode";

    /** The provenance setting recording how the database reaches Comet ({@code R-CMT-04}). */
    public static final String DELIVERY_SETTING = "comet.database-delivery";

    /** The provenance setting recording the archived {@code comet.params}'s SHA-256. */
    public static final String PARAMS_SHA256_SETTING = "comet.params-sha256";

    /** The provenance setting recording the index cache key, in an index mode. */
    public static final String INDEX_KEY_SETTING = "comet.index-cache-key";

    /** The logical tool name provenance records for Comet. */
    public static final String TOOL_NAME = "comet";

    /** The logical tool name provenance records for Percolator. */
    public static final String PERCOLATOR_TOOL_NAME = "percolator";

    private final CachingHashService hashes;

    private final CanonicalParamsWriter writer;

    private final PreRunChecks checks;

    /**
     * Creates the workflow.
     *
     * @param hashes the one hasher -- the same instance the engine's services hold, so the
     *     revalidated cache and its bypass are one
     * @param build the running build, whose version the parameter file's header names
     */
    public CometWorkflow(CachingHashService hashes, BuildIdentity build) {
        this(hashes, build, PreRunChecks.onWindows());
    }

    CometWorkflow(CachingHashService hashes, BuildIdentity build, boolean windows) {
        this.hashes = Objects.requireNonNull(hashes, "hashes");
        this.writer = new CanonicalParamsWriter(Objects.requireNonNull(build, "build"));
        this.checks = new PreRunChecks(hashes, writer, windows);
    }

    /**
     * The steps a run executes.
     *
     * @param mode the index mode
     * @return everything up to {@code finalise-provenance}, with {@code build-comet-index} when an
     *     index mode is set
     */
    public static Plan planFor(IndexMode mode) {
        return planFor(mode, false);
    }

    /**
     * The steps a run executes, with or without Percolator.
     *
     * <p>With Percolator the plan also wants {@code finalise-results}, which requires {@code
     * parse-percolator}, which requires {@code run-percolator}, which requires {@code merge-pin}
     * and {@code resolve-percolator}. The if-planned edge {@code finalise-results ->
     * finalise-provenance} then orders core provenance after the results, so it is finalised after
     * every Percolator step (design decision P10-6).
     *
     * @param mode the index mode
     * @param percolator whether the run rescores the merged PIN with Percolator
     * @return everything up to {@code finalise-provenance}, with {@code build-comet-index} when an
     *     index mode is set, and the three Percolator steps and {@code finalise-results} when
     *     {@code percolator} is set
     */
    public static Plan planFor(IndexMode mode, boolean percolator) {
        Objects.requireNonNull(mode, "mode");
        Set<EngineStep> wanted = EnumSet.of(EngineStep.FINALISE_PROVENANCE);
        if (mode != IndexMode.NONE) {
            wanted.add(EngineStep.BUILD_COMET_INDEX);
        }
        if (percolator) {
            wanted.add(EngineStep.FINALISE_RESULTS);
        }
        return Plan.covering(wanted);
    }

    /**
     * The pre-run check, on its own: what Run readiness shows. Writes nothing and launches nothing.
     *
     * @param project the project the run would belong to
     * @param request the search
     * @return the report; {@link PreRunReport#blocked()} when the run would be refused
     */
    public PreRunReport check(ProjectLayout project, SearchRequest request) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(request, "request");
        return checks.check(
                        project,
                        request.model(),
                        request.spectra(),
                        request.comet(),
                        request.indexMode(),
                        request.percolator())
                .report();
    }

    /**
     * Checks a search and, if nothing blocks it, records it as a run.
     *
     * @param store the project's run store
     * @param lock the project's lock, held
     * @param request the search
     * @param application the application record for provenance
     * @return the recorded run
     * @throws RunBlockedException if the pre-run check blocks the search; nothing was created
     * @throws IOException if the run cannot be reserved or recorded, or a file cannot be hashed
     */
    public PreparedRun prepare(
            RunStore store, ProjectLock lock, SearchRequest request, ApplicationRecord application)
            throws IOException, RunBlockedException {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(lock, "lock");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(application, "application");
        ProjectLayout project = lock.project();
        PreRunChecks.Outcome checked =
                checks.check(
                        project,
                        request.model(),
                        request.spectra(),
                        request.comet(),
                        request.indexMode(),
                        request.percolator());
        if (checked.report().blocked()) {
            throw new RunBlockedException(checked.report());
        }
        CometParameters model = request.model();
        CometSelection comet = request.comet();
        IndexMode mode = request.indexMode();

        ReservedRun reserved = store.reserve(lock);
        RunLayout layout = reserved.layout();
        WrittenParams written = writer.writeOnce(model, layout.cometParamsFile(), hashes);
        List<RecordedInput> spectra = new ArrayList<>();
        for (Path spectrum : request.spectra()) {
            spectra.add(record(spectrum));
        }
        RecordedInput database = record(Path.of(databaseName(model)));
        RunIdentity identity =
                new RunIdentity(
                        reserved.runId(),
                        ProjectStore.read(project).id(),
                        reserved.created(),
                        comet.release().text(),
                        RunIdentity.spectraOf(spectra),
                        database,
                        new ArchivedFile(
                                RunLayout.cometParamsRelativePath(),
                                written.size(),
                                written.hashes()),
                        mode,
                        mode == IndexMode.NONE
                                ? DatabaseDelivery.PARAMETER_FILE
                                : DatabaseDelivery.COMMAND_LINE);
        store.record(lock, identity);

        Optional<IndexCacheEntry> entry = Optional.empty();
        if (mode != IndexMode.NONE) {
            entry = Optional.of(checks.cacheEntry(project, model, comet, mode, database.path()));
        }
        boolean build = entry.isPresent() && entry.get().completion().isEmpty();
        ToolIdentity tool =
                new ToolIdentity(
                        TOOL_NAME,
                        comet.release().text(),
                        Optional.of("v" + comet.release().text()),
                        comet.executable(),
                        hashes.hash(comet.executable()),
                        comet.managed(),
                        comet.artefactIdentity(),
                        Set.of(),
                        List.of());
        CometRun run =
                new CometRun(
                        project, layout, identity, model, comet, tool, entry, build, hashes,
                        checks);
        Map<String, String> settings = new TreeMap<>();
        settings.put(RELEASE_SETTING, comet.release().text());
        settings.put(INDEX_MODE_SETTING, mode.wireName());
        settings.put(DELIVERY_SETTING, identity.databaseDelivery().wireName());
        settings.put(PARAMS_SHA256_SETTING, written.hashes().sha256());
        entry.ifPresent(
                cached ->
                        settings.put(
                                INDEX_KEY_SETTING,
                                String.valueOf(cached.directory().getFileName())));
        Optional<PercolatorRun> percolator = Optional.empty();
        if (request.percolator().isPresent()) {
            PercolatorRun half =
                    preparePercolator(layout, hashes, run.decoys(), request.percolator().get());
            settings.putAll(
                    PercolatorProvenance.settings(
                            half.choice(),
                            half.command(),
                            half.settings().hashes().sha256(),
                            half.decoys().prefix()));
            percolator = Optional.of(half);
        }
        return new PreparedRun(
                run,
                written,
                planFor(mode, percolator.isPresent()),
                store,
                lock,
                application,
                settings,
                percolator);
    }

    /**
     * The Percolator half of a run being prepared: {@code parameters/percolator-settings.json}
     * written once and hashed, the command built from the selection's probed capabilities, and the
     * tool as provenance records it -- its observed capabilities, and as warnings its advisories
     * and, without a weights file, {@code R-PERC-08}'s. A derived run ({@link PercolatorRerun})
     * prepares its Percolator half here too, so the two cannot differ.
     *
     * @param layout the run's directory, already reserved
     * @param hashes the one hasher
     * @param decoys the decoy configuration the merged PIN is checked with
     * @param choice the Percolator half of the search
     * @return the Percolator half
     * @throws IOException if the settings file cannot be written or the executable hashed
     */
    static PercolatorRun preparePercolator(
            RunLayout layout,
            CachingHashService hashes,
            PinDecoyConfiguration decoys,
            PercolatorChoice choice)
            throws IOException {
        PercolatorSettingsFile.Archived archived =
                PercolatorSettingsFile.writeOnce(
                        PercolatorDeclarations.settingsFile(layout),
                        PercolatorSettingsFile.render(choice.settings(), choice.enabledStages()),
                        hashes);
        PercolatorSelection selection = choice.selection();
        PercolatorCommand command;
        try {
            command =
                    PercolatorCommands.build(
                            PercolatorRun.request(
                                    selection.executable(),
                                    layout.mergedPinFile(),
                                    PercolatorDeclarations.outputDirectory(layout),
                                    choice));
        } catch (PercolatorRefusedException refused) {
            // The pre-run check built the same command from the same capabilities and passed.
            throw new IllegalStateException(refused.getMessage(), refused);
        }
        List<String> warnings = new ArrayList<>(AdvisoryRendering.forSelection(selection.offer()));
        PercolatorProvenance.weightsWarning(command).ifPresent(warnings::add);
        Set<String> capabilities = new TreeSet<>();
        for (ToolCapability capability : selection.capabilities()) {
            capabilities.add(capability.id());
        }
        ToolIdentity tool =
                new ToolIdentity(
                        PERCOLATOR_TOOL_NAME,
                        selection.version().text(),
                        Optional.empty(),
                        selection.executable(),
                        hashes.hash(selection.executable()),
                        selection.managed(),
                        Optional.empty(),
                        capabilities,
                        warnings);
        return new PercolatorRun(layout, hashes, decoys, choice, tool, command, archived);
    }

    /**
     * Starts an attempt of a recorded run -- the first, or a retry.
     *
     * @param engine the workflow engine, built with the same hasher as this workflow
     * @param run the recorded run
     * @param listener observes every state change
     * @return the running attempt
     * @throws IOException as {@code WorkflowEngine.start}
     * @throws ReuseRefusedException if a result the attempt would reuse no longer matches its
     *     record, naming the file; nothing started
     */
    public RunHandle start(WorkflowEngine engine, PreparedRun run, StepStateListener listener)
            throws IOException, ReuseRefusedException {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(run, "run");
        return engine.start(run.request(), listener);
    }

    /**
     * The rerun preview of a configuration against a recorded run: which of the run's steps would
     * execute again, which are reused (each re-hashed against the record), and why ({@code
     * R-RUN-01}, {@code AC-WF-04}).
     *
     * @param engine the workflow engine
     * @param run the recorded run
     * @param candidate the configuration as it is now; its index mode is compared as an input, the
     *     plan stays the run's
     * @return the check, whose {@link ReuseCheck#preview()} names the steps
     * @throws IOException if a file cannot be hashed or {@code run.json} read
     */
    public ReuseCheck preview(WorkflowEngine engine, PreparedRun run, SearchRequest candidate)
            throws IOException {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(run, "run");
        return engine.checkReuse(run.request(inputsOf(candidate)));
    }

    /**
     * The input values of a configuration as its files are now.
     *
     * @param candidate the configuration
     * @return the inputs a fingerprint of it is computed from
     * @throws IOException if a file cannot be resolved or hashed
     */
    public StepInputs inputsOf(SearchRequest candidate) throws IOException {
        Objects.requireNonNull(candidate, "candidate");
        List<InputValue.NamedFile> spectra = new ArrayList<>();
        for (Path spectrum : candidate.spectra()) {
            Path real = spectrum.toRealPath();
            spectra.add(RunInputs.named(real, hashes.hash(real)));
        }
        Path database = Path.of(databaseName(candidate.model())).toRealPath();
        StepInputs inputs =
                RunInputs.of(
                        spectra,
                        RunInputs.named(database, hashes.hash(database)),
                        InputValue.bytes(writer.bytes(candidate.model())).sha256(),
                        candidate.indexMode(),
                        candidate.comet().release().text(),
                        candidate.comet().sha256());
        if (candidate.percolator().isEmpty()) {
            return inputs;
        }
        PercolatorChoice choice = candidate.percolator().get();
        return RunInputs.withPercolator(
                inputs,
                PercolatorSettingsFile.render(choice.settings(), choice.enabledStages()),
                choice.selection().version().text(),
                choice.selection().sha256());
    }

    private static String databaseName(CometParameters model) {
        return ((ParameterValue.Text) model.value(PreRunChecks.DATABASE)).text();
    }

    /** One input as the run records it: canonical path, size, modification time and hashes. */
    private RecordedInput record(Path file) throws IOException {
        Path real = file.toRealPath();
        FileHashes digests = hashes.hash(real);
        return new RecordedInput(
                real, Files.size(real), Files.getLastModifiedTime(real).toInstant(), digests);
    }
}
