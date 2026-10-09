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

package org.cometgui.app.config;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.cometgui.domain.ports.HashService;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.RunAttempt;
import org.cometgui.domain.run.RunDescriptor;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.RunIdentity;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.run.SpectrumInput;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.results.export.ResultExporter;
import org.cometgui.results.export.TableExport;
import org.cometgui.results.export.WeightsExport;
import org.cometgui.results.filtering.DisplayFilters;
import org.cometgui.results.filtering.QValueFilter;
import org.cometgui.results.filtering.store.Category;
import org.cometgui.results.filtering.store.ResultStore;
import org.cometgui.results.filtering.store.ResultStores;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.results.parser.WeightsReader;
import org.cometgui.results.parser.WeightsSummary;
import org.cometgui.ui.viewmodel.results.OpenedResults;
import org.cometgui.ui.viewmodel.results.ResultsPort;
import org.cometgui.ui.viewmodel.results.ResultsRun;
import org.cometgui.workflow.steps.RunResultFiles;
import org.cometgui.workflow.storage.ViewStateStore;

/**
 * The Results section's port over the session's project (Phase 10 unit 8; {@link ResultsPort}).
 *
 * <h2>Which runs have results</h2>
 *
 * <p>{@link #runs()} lists the project's {@code runs/} directory and reads each run's {@code
 * run.json} through the run store's own reader -- without creating the project or taking its lock,
 * since reading needs neither ({@link ProjectSession#readRun}). A run is listed when at least one
 * of Percolator's four tables is under its {@code outputs/percolator/} ({@link RunResultFiles}). A
 * directory without a readable {@code run.json} is not a run of record and is not listed. Its
 * outcome is its last attempt's; whether it is executing is the session engine's answer.
 *
 * <h2>Opening a run</h2>
 *
 * <p>{@link #open} opens each table the run has through the one store factory, {@link
 * ResultStores#open}, with the run's own {@code results/index/} and the session's one hasher -- so
 * a table above the in-memory limit is opened on disk and its index, built by {@code
 * finalise-results}, is checked and reused. A failure closes what was opened. The weights are read
 * by the one weights reader and summarised by {@link WeightsSummary}. The spectrum files' display
 * names come from the run's recorded inputs: each Comet {@code -N} base -- the absolute path {@code
 * outputs/comet/<base>} the run gave Comet, which is what every {@code PSMId} begins with -- maps
 * to its spectrum file's name (its whole path when two inputs share a name). A derived run (a
 * Percolator rerun) rescored its source's merged PIN, so its {@code PSMId}s begin with the source
 * run's bases, and those are the ones mapped. The view state is {@link ViewStateStore}'s.
 *
 * <h2>Writes</h2>
 *
 * <p>Saving the view state and exporting write into a run, so each first takes the project's lock
 * (held for the session once taken): no other CometGUI can be writing the project meanwhile. An
 * export of a run the engine is executing is refused here, whatever the caller checked, because the
 * engine holds that run's provenance event log. Exports go through {@link ResultExporter} with the
 * build's version, the one hasher and the one secret rule set.
 *
 * <p><strong>Nothing here launches a process.</strong> This port is given no process service: a
 * filter change reaches it at most as {@link #saveViewState} ({@code R-RES-01}, phase 10 gate item
 * 2).
 *
 * <p>Every method may block on file I/O and is called off the JavaFX thread, one call at a time.
 */
final class ProjectResultsPort implements ResultsPort {

    private static final List<TableKind> TABLES =
            List.of(
                    TableKind.TARGET_PSMS,
                    TableKind.TARGET_PEPTIDES,
                    TableKind.DECOY_PSMS,
                    TableKind.DECOY_PEPTIDES);

    private final ProjectSession project;

    private final HashService hasher;

    private final String cometGuiVersion;

    private final Clock clock;

    private final Supplier<Set<RunId>> executing;

    /** The runs the last {@link #runs()} listed, by identifier. */
    private final Map<RunId, Listed> listed = new HashMap<>();

    /**
     * The port.
     *
     * @param project the session's project
     * @param hasher the session's one hasher
     * @param cometGuiVersion the running build's version, recorded in every export
     * @param clock the clock every export is dated by
     * @param executing the runs the session's engine is executing now
     */
    ProjectResultsPort(
            ProjectSession project,
            HashService hasher,
            String cometGuiVersion,
            Clock clock,
            Supplier<Set<RunId>> executing) {
        this.project = Objects.requireNonNull(project, "project");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
        this.cometGuiVersion = Objects.requireNonNull(cometGuiVersion, "cometGuiVersion");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.executing = Objects.requireNonNull(executing, "executing");
    }

    @Override
    public synchronized List<ResultsRun> runs() throws IOException {
        listed.clear();
        Path runsDirectory = project.layout().runsDirectory();
        if (!Files.isDirectory(runsDirectory)) {
            return List.of();
        }
        Set<RunId> now = Set.copyOf(executing.get());
        List<ResultsRun> found = new ArrayList<>();
        try (DirectoryStream<Path> directories = Files.newDirectoryStream(runsDirectory)) {
            for (Path directory : directories) {
                RunLayout layout = new RunLayout(directory.toAbsolutePath());
                Set<TableKind> tables = EnumSet.noneOf(TableKind.class);
                for (TableKind kind : TABLES) {
                    if (Files.isRegularFile(RunResultFiles.table(layout, kind))) {
                        tables.add(kind);
                    }
                }
                if (tables.isEmpty() || !Files.isRegularFile(layout.runFile())) {
                    continue;
                }
                RunDescriptor descriptor;
                try {
                    descriptor = project.readRun(layout);
                } catch (IOException | RuntimeException unreadable) {
                    // not a run of record: its run.json is missing, refused or of another version
                    continue;
                }
                RunIdentity identity = descriptor.identity();
                listed.put(identity.runId(), new Listed(layout, identity));
                found.add(
                        new ResultsRun(
                                identity.runId(),
                                identity.created(),
                                outcomeOf(descriptor),
                                tables,
                                now.contains(identity.runId())));
            }
        }
        return List.copyOf(found);
    }

    @Override
    public synchronized OpenedResults open(RunId run) throws IOException {
        Listed of = listedRun(run);
        Map<TableKind, ResultStore> stores = new EnumMap<>(TableKind.class);
        try {
            for (TableKind kind : TABLES) {
                Path table = RunResultFiles.table(of.layout(), kind);
                if (Files.isRegularFile(table)) {
                    stores.put(
                            kind,
                            ResultStores.open(
                                    table, kind, of.layout().resultIndexDirectory(), hasher));
                }
            }
            if (stores.isEmpty()) {
                throw new IOException(
                        "run " + run.value() + " has no Percolator result table any more");
            }
            Path weightsFile = RunResultFiles.weights(of.layout());
            Optional<WeightsSummary> weights =
                    Files.isRegularFile(weightsFile)
                            ? Optional.of(WeightsSummary.of(WeightsReader.read(weightsFile)))
                            : Optional.empty();
            return new OpenedResults(
                    run, stores, weights, sourceFiles(of), ViewStateStore.read(of.layout()));
        } catch (IOException | RuntimeException failed) {
            closeAll(stores.values(), failed);
            throw failed;
        }
    }

    @Override
    public void close(OpenedResults opened) {
        Objects.requireNonNull(opened, "opened");
        closeAll(opened.stores().values(), null);
    }

    @Override
    public synchronized void saveViewState(RunId run, DisplayFilters filters) throws IOException {
        Objects.requireNonNull(filters, "filters");
        Listed of = listedRun(run);
        project.lock();
        ViewStateStore.write(of.layout(), filters);
    }

    @Override
    public synchronized TableExport exportTable(
            RunId run, TableKind kind, QValueFilter filter, Category category) throws IOException {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(filter, "filter");
        Objects.requireNonNull(category, "category");
        Listed of = writable(run);
        return exporter(of)
                .exportTable(RunResultFiles.table(of.layout(), kind), kind, filter, category);
    }

    @Override
    public synchronized WeightsExport exportWeights(RunId run, WeightsSummary weights)
            throws IOException {
        Objects.requireNonNull(weights, "weights");
        return exporter(writable(run)).exportWeights(weights);
    }

    // ========================================================================= internals ====

    /** A run {@link #runs()} listed: where it is and what it records. */
    private record Listed(RunLayout layout, RunIdentity identity) {}

    private Listed listedRun(RunId run) throws IOException {
        Objects.requireNonNull(run, "run");
        Listed of = listed.get(run);
        if (of == null) {
            runs();
            of = listed.get(run);
        }
        if (of == null) {
            throw new IOException(
                    "run " + run.value() + " is not a run with results in " + project.directory());
        }
        return of;
    }

    /** A run that may be exported from now: listed, not executing, and the project locked. */
    private Listed writable(RunId run) throws IOException {
        Listed of = listedRun(run);
        if (executing.get().contains(run)) {
            throw new IOException(
                    "run "
                            + run.value()
                            + " is executing: the workflow engine holds its provenance event log,"
                            + " where every export is recorded. Export when the run has ended.");
        }
        project.lock();
        return of;
    }

    private ResultExporter exporter(Listed of) {
        return new ResultExporter(
                of.layout(),
                of.identity().runId(),
                cometGuiVersion,
                clock,
                hasher,
                SecretRedactor.patternsOnly());
    }

    /**
     * Each {@code -N} base of the run's spectrum files -- as the absolute path a {@code PSMId}
     * begins with -- to the spectrum file's display name.
     */
    private Map<String, String> sourceFiles(Listed of) {
        RunIdentity identity = of.identity();
        RunLayout searched =
                identity.derivedFrom()
                        .map(
                                source ->
                                        new RunLayout(
                                                project.layout()
                                                        .runsDirectory()
                                                        .resolve(source.directoryName())
                                                        .toAbsolutePath()))
                        .orElse(of.layout());
        List<SpectrumInput> spectra = identity.spectra();
        Set<String> names = new HashSet<>();
        boolean shared = false;
        for (SpectrumInput spectrum : spectra) {
            shared |= !names.add(fileName(spectrum));
        }
        Map<String, String> files = new HashMap<>();
        for (SpectrumInput spectrum : spectra) {
            files.put(
                    searched.cometOutputBase(spectrum.base()).toString(),
                    shared ? spectrum.file().path().toString() : fileName(spectrum));
        }
        return Map.copyOf(files);
    }

    private static String fileName(SpectrumInput spectrum) {
        Path name = spectrum.file().path().getFileName();
        return name == null ? spectrum.file().path().toString() : name.toString();
    }

    private static AttemptOutcome outcomeOf(RunDescriptor descriptor) {
        List<RunAttempt> attempts = descriptor.attempts();
        return attempts.isEmpty()
                ? AttemptOutcome.RUNNING
                : attempts.get(attempts.size() - 1).outcome();
    }

    /** Closes stores, adding a failure to close to {@code failure} if there is one. */
    private static void closeAll(Iterable<ResultStore> stores, Exception failure) {
        for (ResultStore store : stores) {
            try {
                store.close();
            } catch (IOException | RuntimeException notClosed) {
                // a store already closed, or one whose index file cannot be released: nothing
                // the caller can do, and the stores are not used again
                if (failure != null) {
                    failure.addSuppressed(notClosed);
                }
            }
        }
    }
}
