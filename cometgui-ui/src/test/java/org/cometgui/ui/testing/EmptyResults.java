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

package org.cometgui.ui.testing;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.cometgui.domain.run.RunId;
import org.cometgui.results.export.TableExport;
import org.cometgui.results.export.WeightsExport;
import org.cometgui.results.filtering.DisplayFilters;
import org.cometgui.results.filtering.QValueFilter;
import org.cometgui.results.filtering.store.Category;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.results.parser.WeightsSummary;
import org.cometgui.ui.viewmodel.results.OpenedResults;
import org.cometgui.ui.viewmodel.results.ResultsPort;
import org.cometgui.ui.viewmodel.results.ResultsRun;

/**
 * A Results port for a shell test: a project in which no run has results. It counts how often its
 * runs were read, so a test can show that building the shell reads nothing.
 */
public final class EmptyResults implements ResultsPort {

    /** What every other call says. */
    public static final String REASON = "No run has results in this test.";

    private final AtomicInteger reads = new AtomicInteger();

    /**
     * How often {@link #runs()} was called.
     *
     * @return the number of reads
     */
    public int reads() {
        return reads.get();
    }

    @Override
    public List<ResultsRun> runs() {
        reads.incrementAndGet();
        return List.of();
    }

    @Override
    public OpenedResults open(RunId run) throws IOException {
        throw new IOException(REASON);
    }

    @Override
    public void close(OpenedResults opened) {
        // nothing is ever opened
    }

    @Override
    public void saveViewState(RunId run, DisplayFilters filters) throws IOException {
        throw new IOException(REASON);
    }

    @Override
    public TableExport exportTable(
            RunId run, TableKind kind, QValueFilter filter, Category category) throws IOException {
        throw new IOException(REASON);
    }

    @Override
    public WeightsExport exportWeights(RunId run, WeightsSummary weights) throws IOException {
        throw new IOException(REASON);
    }
}
