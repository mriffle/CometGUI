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

package org.cometgui.ui.viewmodel.results;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.provenance.hashing.StreamingHashService;
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
import org.cometgui.workflow.storage.ViewStateStore;

/**
 * A {@link ResultsPort} over real stores: each run is a directory under a temporary project, its
 * tables are opened with {@code ResultStores.open}, its view state is read and written by {@code
 * ViewStateStore}, its weights summarised by {@code WeightsSummary}, and its exports written by the
 * real {@code ResultExporter}. Every call is recorded, in order, so a test can say what the section
 * asked for -- and what it did not.
 */
final class FakeResultsPort implements ResultsPort {

    /** The CometGUI version exports record. */
    static final String VERSION = "0.0.0-viewmodel-test";

    /** One run the port lists. */
    record Run(
            RunId id,
            Instant created,
            AttemptOutcome outcome,
            boolean executing,
            RunLayout layout,
            Map<TableKind, Path> tables,
            Optional<Path> weights,
            Map<String, String> sources) {

        ResultsRun listed() {
            return new ResultsRun(id, created, outcome, tables.keySet(), executing);
        }
    }

    private final Path project;

    private final Map<RunId, Run> runs = new LinkedHashMap<>();

    /** Every call, in order: {@code runs}, {@code open r1}, {@code close r1}, ... */
    private final List<String> calls = new ArrayList<>();

    /** Every store this port opened, in order. */
    private final List<ResultStore> openedStores = new ArrayList<>();

    /** Thrown by the next {@link #runs()}, then cleared. */
    private IOException failRuns;

    /** Thrown by every {@link #open}, while set. */
    private IOException failOpen;

    FakeResultsPort(Path project) {
        this.project = project.toAbsolutePath();
    }

    List<String> calls() {
        return calls;
    }

    List<ResultStore> openedStores() {
        return openedStores;
    }

    void failRuns(IOException next) {
        failRuns = next;
    }

    void failOpen(IOException next) {
        failOpen = next;
    }

    /**
     * Adds a run.
     *
     * @param id its id
     * @param created when it was created
     * @param executing whether it is executing
     * @param tables its tables
     * @param weights its weights file, if any
     * @param sources its -N bases to display names
     * @return the run
     */
    Run add(
            String id,
            Instant created,
            boolean executing,
            Map<TableKind, Path> tables,
            Optional<Path> weights,
            Map<String, String> sources) {
        RunId runId = new RunId(id);
        RunLayout layout = new RunLayout(project.resolve("runs").resolve(id));
        try {
            Files.createDirectories(layout.provenanceDirectory());
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
        Run run =
                new Run(
                        runId,
                        created,
                        executing ? AttemptOutcome.RUNNING : AttemptOutcome.SUCCEEDED,
                        executing,
                        layout,
                        new EnumMap<>(tables),
                        weights,
                        Map.copyOf(sources));
        runs.put(runId, run);
        return run;
    }

    /** Marks a run executing or not, as the next {@link #runs()} lists it. */
    void executing(String id, boolean executing) {
        Run run = runs.get(new RunId(id));
        runs.put(
                run.id(),
                new Run(
                        run.id(),
                        run.created(),
                        executing ? AttemptOutcome.RUNNING : AttemptOutcome.SUCCEEDED,
                        executing,
                        run.layout(),
                        run.tables(),
                        run.weights(),
                        run.sources()));
    }

    /** Removes a run from the listing. */
    void remove(String id) {
        runs.remove(new RunId(id));
    }

    Run run(String id) {
        return runs.get(new RunId(id));
    }

    @Override
    public List<ResultsRun> runs() throws IOException {
        calls.add("runs");
        if (failRuns != null) {
            IOException failure = failRuns;
            failRuns = null;
            throw failure;
        }
        List<ResultsRun> listed = new ArrayList<>();
        for (Run run : runs.values()) {
            listed.add(run.listed());
        }
        return listed;
    }

    @Override
    public OpenedResults open(RunId id) throws IOException {
        calls.add("open " + id.value());
        if (failOpen != null) {
            throw failOpen;
        }
        Run run = runs.get(id);
        Map<TableKind, ResultStore> stores = new EnumMap<>(TableKind.class);
        for (Map.Entry<TableKind, Path> table : run.tables().entrySet()) {
            ResultStore store =
                    ResultStores.open(
                            table.getValue(),
                            table.getKey(),
                            run.layout().resultIndexDirectory(),
                            new StreamingHashService());
            openedStores.add(store);
            stores.put(table.getKey(), store);
        }
        Optional<WeightsSummary> weights = Optional.empty();
        if (run.weights().isPresent()) {
            weights = Optional.of(WeightsSummary.of(WeightsReader.read(run.weights().get())));
        }
        return new OpenedResults(
                id, stores, weights, run.sources(), ViewStateStore.read(run.layout()));
    }

    @Override
    public void close(OpenedResults opened) {
        calls.add("close " + opened.run().value());
        for (ResultStore store : opened.stores().values()) {
            try {
                store.close();
            } catch (IOException failed) {
                throw new UncheckedIOException(failed);
            }
        }
    }

    @Override
    public void saveViewState(RunId id, DisplayFilters filters) throws IOException {
        calls.add(
                "save " + id.value() + " " + filters.psm().text() + "/" + filters.peptide().text());
        ViewStateStore.write(runs.get(id).layout(), filters);
    }

    @Override
    public TableExport exportTable(RunId id, TableKind kind, QValueFilter filter, Category category)
            throws IOException {
        calls.add("export " + id.value() + " " + kind + " " + filter.text() + " " + category);
        Run run = runs.get(id);
        return exporter(run).exportTable(run.tables().get(kind), kind, filter, category);
    }

    @Override
    public WeightsExport exportWeights(RunId id, WeightsSummary weights) throws IOException {
        calls.add("export weights " + id.value());
        return exporter(runs.get(id)).exportWeights(weights);
    }

    private static ResultExporter exporter(Run run) {
        return new ResultExporter(
                run.layout(),
                run.id(),
                VERSION,
                Clock.fixed(Instant.parse("2026-10-09T12:00:00Z"), ZoneOffset.UTC),
                new StreamingHashService(),
                SecretRedactor.patternsOnly());
    }
}
