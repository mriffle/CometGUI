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
import java.util.List;
import org.cometgui.domain.run.RunId;
import org.cometgui.results.export.TableExport;
import org.cometgui.results.export.WeightsExport;
import org.cometgui.results.filtering.DisplayFilters;
import org.cometgui.results.filtering.QValueFilter;
import org.cometgui.results.filtering.store.Category;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.results.parser.WeightsSummary;

/**
 * What the Results section needs from the session's project, implemented by the composition root
 * (Phase 10 unit 8) over the run store, {@code ResultStores}, {@code ViewStateStore} and {@code
 * ResultExporter}.
 *
 * <p><strong>Every method reads or writes files and may block for seconds</strong> -- opening a
 * million-row table hashes it and may build its index -- so <strong>none is ever called on the
 * JavaFX application thread</strong>: {@link ResultsViewModel} calls each from its background
 * executor, one call at a time, and applies the answer on the interface thread. An implementation
 * need not be thread-safe beyond that.
 *
 * <p>Nothing here launches a process or reruns a tool ({@code R-RES-01}, phase 10 gate item 2):
 * changing a filter at most writes the run's view state.
 */
public interface ResultsPort {

    /**
     * The runs of the session's project that have results: at least one of Percolator's four
     * tables. Reads the project's runs; blocking.
     *
     * @return the runs, in any order; empty when there is no project or no run has results
     * @throws IOException if the project's runs cannot be read
     */
    List<ResultsRun> runs() throws IOException;

    /**
     * Opens one run's results: every table it has, its weights summary, its spectrum files' display
     * names and its saved view state. Blocking; may take seconds for a large table.
     *
     * @param run the run, from {@link #runs()}
     * @return the opened results; the caller gives them back to {@link #close}
     * @throws IOException if a table or the weights artefact cannot be read; nothing is left open
     */
    OpenedResults open(RunId run) throws IOException;

    /**
     * Closes the stores of results {@link #open} returned. Never throws for a store already closed.
     *
     * @param opened the opened results
     */
    void close(OpenedResults opened);

    /**
     * Saves a run's display filters as its view state ({@code results/view-state.json}). Blocking.
     *
     * @param run the run
     * @param filters the filters
     * @throws IOException if they cannot be saved -- among other reasons because the run's existing
     *     view state could not be read and is never overwritten
     */
    void saveViewState(RunId run, DisplayFilters filters) throws IOException;

    /**
     * Exports the rows of one category of one of a run's tables under a filter, with its sidecar
     * and provenance event ({@code R-RES-04}). Blocking. An implementation refuses a run the engine
     * is executing.
     *
     * @param run the run
     * @param kind which table
     * @param filter the filter: the PSM filter for a PSM table, the peptide filter for a peptide
     *     table
     * @param category the rows written
     * @return what was written
     * @throws IOException if nothing was exported, saying why
     */
    TableExport exportTable(RunId run, TableKind kind, QValueFilter filter, Category category)
            throws IOException;

    /**
     * Exports a run's learned feature weights summary, with its sidecar and provenance event.
     * Blocking. An implementation refuses a run the engine is executing.
     *
     * @param run the run
     * @param weights the summary {@link #open} gave
     * @return what was written
     * @throws IOException if nothing was exported, saying why
     */
    WeightsExport exportWeights(RunId run, WeightsSummary weights) throws IOException;
}
