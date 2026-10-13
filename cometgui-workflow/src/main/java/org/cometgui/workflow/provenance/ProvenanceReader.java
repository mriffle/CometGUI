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

package org.cometgui.workflow.provenance;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.cometgui.domain.project.ProjectLayout;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.RunAttempt;
import org.cometgui.domain.run.RunDescriptor;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.MetadataLoader;
import org.cometgui.provenance.events.ProvenanceEventLogReader;
import org.cometgui.provenance.events.RecoveredEventLog;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.provenance.manifest.ManifestReader;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.workflow.storage.RunStore;

/**
 * The one place that turns a project's run directories into what the Provenance section shows
 * ({@code P13-2}).
 *
 * <p>Two operations, both blocking and both for a background thread: {@link #list} names every run
 * directory of a project with what its {@code run.json} says, and {@link #load} reads one run and
 * builds its {@link ProvenanceView}. Neither writes anything, and neither keeps a file open after
 * it returns.
 *
 * <h2>It re-implements nothing</h2>
 *
 * <ul>
 *   <li>The record is read by {@link ManifestReader#readFrom} -- the whole file read and closed,
 *       then parsed -- and the event log by {@link ProvenanceEventLogReader#recover}; {@code
 *       run.json} by {@link RunStore#read}.
 *   <li>Every digest is computed by the product's {@link CachingHashService}, always through {@link
 *       CachingHashService#rehash}, so the cache is bypassed whenever a file is compared with its
 *       record.
 *   <li>Every displayed text passes the session's {@link SecretRedactor}; commands are rendered by
 *       the process service's {@code ProcessRedactor}.
 *   <li>The archived {@code comet.params} is read by the parameter system's own parser for the
 *       recorded release.
 *   <li>The exports are phase 04's {@code ManifestWriter} and {@code ProvenanceReportWriter}; see
 *       {@link LoadedRun}.
 * </ul>
 *
 * <h2>A run without a sealed record still opens ({@code P13-5})</h2>
 *
 * <p>{@link #load} never throws for what a run directory <em>contains</em>. A missing {@code
 * provenance.json}, one that does not parse, a missing or damaged {@code run.json}, a torn or
 * missing event log: each becomes a {@link RecordState}, a problem message and a warning, and the
 * run is shown from whatever is left. It throws only when the run directory itself is not there.
 */
public final class ProvenanceReader {

    /**
     * The largest archived parameter file whose text is shown: {@value} bytes. A {@code
     * comet.params} is about ten kilobytes and a Percolator settings file far less; a file a
     * hundred times larger is not one CometGUI wrote, and is reported rather than read into the
     * section.
     */
    public static final long PARAMETER_TEXT_LIMIT = 1L << 20;

    private final Supplier<SecretRedactor> redactor;

    private final CachingHashService hashes;

    private final Supplier<CuratedMetadata> metadata;

    private final ExportRenderer renderer;

    private final Object metadataLock = new Object();

    private CuratedMetadata loadedMetadata;

    /**
     * Creates the reader.
     *
     * @param redactor the session's secret rule set, asked afresh for every load and every action,
     *     so that a credential admitted later is redacted from then on
     * @param hashes the product's one hashing service; this reader always bypasses its cache
     * @throws NullPointerException if either argument is {@code null}
     */
    public ProvenanceReader(Supplier<SecretRedactor> redactor, CachingHashService hashes) {
        this(redactor, hashes, MetadataLoader::loadBundled, ExportRenderer.WRITERS);
    }

    /**
     * Creates a reader with its collaborators substituted, for tests.
     *
     * @param redactor the session's secret rule set
     * @param hashes the hashing service
     * @param metadata the Comet parameter metadata, asked for once, on first use
     * @param renderer renders the two exports
     */
    ProvenanceReader(
            Supplier<SecretRedactor> redactor,
            CachingHashService hashes,
            Supplier<CuratedMetadata> metadata,
            ExportRenderer renderer) {
        this.redactor = Objects.requireNonNull(redactor, "redactor");
        this.hashes = Objects.requireNonNull(hashes, "hashes");
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    /**
     * Lists every run directory of a project, whatever became of its run.
     *
     * <p>Blocking; reads each run's {@code run.json} and checks whether its {@code provenance.json}
     * exists, but parses no record.
     *
     * @param project the project
     * @param executing the runs this session is executing now, which tells a running run from an
     *     interrupted one
     * @return one entry per directory under {@code runs/}, in directory-name order -- which is
     *     creation order for every run CometGUI made; empty when the project has no {@code runs/}
     * @throws IOException if {@code runs/} exists and cannot be listed
     */
    public List<RunListing> list(ProjectLayout project, Set<RunId> executing) throws IOException {
        Objects.requireNonNull(project, "project");
        Set<RunId> now = Set.copyOf(executing);
        Path runs = project.runsDirectory();
        List<RunListing> listed = new ArrayList<>();
        if (Files.isDirectory(runs)) {
            DisplayText display = new DisplayText(redactor.get());
            RunStore store = readOnlyStore(project);
            try (DirectoryStream<Path> directories = Files.newDirectoryStream(runs)) {
                for (Path directory : directories) {
                    if (Files.isDirectory(directory)) {
                        listed.add(
                                listing(
                                        store,
                                        new RunLayout(directory.toAbsolutePath()),
                                        now,
                                        display));
                    }
                }
            }
        }
        listed.sort(Comparator.comparing(RunListing::directoryName));
        return List.copyOf(listed);
    }

    private static RunListing listing(
            RunStore store, RunLayout layout, Set<RunId> executing, DisplayText display) {
        String name = String.valueOf(layout.root().getFileName());
        boolean sealed = Files.isRegularFile(layout.provenanceJsonFile());
        RunDescriptor descriptor;
        try {
            descriptor = store.read(layout);
        } catch (IOException | RuntimeException unreadable) {
            return new RunListing(
                    name,
                    layout.root(),
                    Optional.empty(),
                    Optional.empty(),
                    RunCondition.UNREADABLE,
                    sealed,
                    Optional.of(display.clean(problem(unreadable))));
        }
        RunId id = descriptor.identity().runId();
        return new RunListing(
                name,
                layout.root(),
                Optional.of(id.value()),
                Optional.of(DisplayText.timestamp(descriptor.identity().created())),
                condition(descriptor, executing.contains(id)),
                sealed,
                Optional.empty());
    }

    /** What the latest attempt of a run says, and whether this session is executing it. */
    static RunCondition condition(RunDescriptor descriptor, boolean executing) {
        List<RunAttempt> attempts = descriptor.attempts();
        if (attempts.isEmpty()) {
            return executing ? RunCondition.RUNNING : RunCondition.NOT_STARTED;
        }
        AttemptOutcome outcome = attempts.get(attempts.size() - 1).outcome();
        return switch (outcome) {
            case RUNNING -> executing ? RunCondition.RUNNING : RunCondition.INTERRUPTED;
            case SUCCEEDED -> RunCondition.COMPLETED;
            case FAILED -> RunCondition.FAILED;
            case CANCELLED -> RunCondition.CANCELLED;
        };
    }

    /**
     * Loads one run ({@code P13-5}).
     *
     * <p>Blocking: reads {@code run.json}, {@code events.log} and {@code provenance.json} whole,
     * hashes the archived parameter files again, and parses the archived {@code comet.params}. No
     * file it opened is open when it returns.
     *
     * @param run the run's directory
     * @return the loaded run, never an empty one: a run with no readable record says so
     * @throws NoSuchFileException if the run directory does not exist
     * @throws IOException never otherwise; a problem with a file inside the directory is a state
     */
    public LoadedRun load(RunLayout run) throws IOException {
        Objects.requireNonNull(run, "run");
        if (!Files.isDirectory(run.root())) {
            throw new NoSuchFileException(run.root().toString(), null, "no such run directory");
        }
        SecretRedactor rules = redactor.get();
        DisplayText display = new DisplayText(rules);

        Optional<RunDescriptor> runJson = Optional.empty();
        Optional<String> runJsonProblem = Optional.empty();
        try {
            // RunStore.read reads the layout it is given and nothing of the project's.
            runJson = Optional.of(readOnlyStore(new ProjectLayout(run.root())).read(run));
        } catch (IOException | RuntimeException unreadable) {
            runJsonProblem = Optional.of(display.clean(problem(unreadable)));
        }

        RecoveredEventLog events = new RecoveredEventLog(List.of(), List.of());
        Optional<String> eventLogProblem = Optional.empty();
        if (!Files.exists(run.eventLogFile())) {
            eventLogProblem = Optional.of("this run has no events.log");
        } else {
            try {
                events = ProvenanceEventLogReader.recover(run.eventLogFile(), rules);
            } catch (IOException unreadable) {
                eventLogProblem = Optional.of(display.clean(problem(unreadable)));
            }
        }

        RecordState state;
        Optional<ProvenanceManifest> manifest = Optional.empty();
        Optional<String> recordProblem = Optional.empty();
        if (!Files.exists(run.provenanceJsonFile())) {
            state = RecordState.NO_SEALED_RECORD;
        } else {
            try {
                manifest = Optional.of(ManifestReader.readFrom(run.provenanceJsonFile()));
                state = RecordState.SEALED;
            } catch (IOException | ManifestReader.InvalidManifestException unreadable) {
                state = RecordState.RECORD_UNREADABLE;
                recordProblem = Optional.of(display.clean(problem(unreadable)));
            }
        }

        ViewBuilder.Loaded loaded =
                new ViewBuilder.Loaded(
                        run,
                        state,
                        manifest,
                        recordProblem,
                        runJson,
                        runJsonProblem,
                        events,
                        eventLogProblem);
        ParametersBuilder parameters =
                new ParametersBuilder(
                        run,
                        manifest,
                        runJson,
                        this::metadata,
                        hashes,
                        display,
                        PARAMETER_TEXT_LIMIT);
        ProvenanceView.Parameters built = parameters.build();
        ProvenanceView view = new ViewBuilder(loaded, display).build(built, parameters.warnings());
        return new LoadedRun(loaded, view, redactor, hashes, renderer);
    }

    /**
     * Loads one run from its directory.
     *
     * @param runDirectory the run's directory
     * @return as {@link #load(RunLayout)}
     * @throws IOException as {@link #load(RunLayout)}
     */
    public LoadedRun load(Path runDirectory) throws IOException {
        return load(new RunLayout(runDirectory.toAbsolutePath()));
    }

    private CuratedMetadata metadata() {
        synchronized (metadataLock) {
            if (loadedMetadata == null) {
                loadedMetadata = Objects.requireNonNull(metadata.get(), "metadata");
            }
            return loadedMetadata;
        }
    }

    /**
     * A message for a failure, naming its type when it has no message of its own. Every reader this
     * class calls words its messages without quoting document content.
     */
    private static String problem(Exception failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    /**
     * A store used only to read: {@link RunStore#read} needs neither the clock nor a run-id source,
     * and the source here refuses rather than invent an identifier.
     */
    private static RunStore readOnlyStore(ProjectLayout project) {
        return new RunStore(
                project,
                Clock.systemUTC(),
                () -> {
                    throw new IllegalStateException("the provenance reader never reserves a run");
                });
    }
}
