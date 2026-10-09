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
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.cometgui.results.filtering.DisplayFilters;
import org.cometgui.results.filtering.FilterCounts;
import org.cometgui.results.filtering.QValueFilter;
import org.cometgui.results.filtering.store.ResultStore;
import org.cometgui.results.filtering.store.ResultStores;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.results.parser.WeightsReader;
import org.cometgui.results.parser.WeightsSummary;
import org.cometgui.tools.percolator.PercolatorArtefact;
import org.cometgui.workflow.engine.StepAction;
import org.cometgui.workflow.engine.StepContext;
import org.cometgui.workflow.engine.StepDeclaration;
import org.cometgui.workflow.engine.StepFailedException;

/**
 * The results step, {@code finalise-results}: step 12 of the specification's <em>Canonical workflow
 * DAG</em>, "Finalise core result indexes and summaries" (design decision P10-6).
 */
final class ResultSteps {

    /** The four result tables, in the order they are opened and recorded. */
    private static final List<TableKind> TABLES =
            List.of(
                    TableKind.TARGET_PSMS,
                    TableKind.TARGET_PEPTIDES,
                    TableKind.DECOY_PSMS,
                    TableKind.DECOY_PEPTIDES);

    private ResultSteps() {}

    /**
     * The artefact that holds one table.
     *
     * @param kind the table
     * @return its artefact
     */
    static PercolatorArtefact artefactOf(TableKind kind) {
        return switch (kind) {
            case TARGET_PSMS -> PercolatorArtefact.TARGET_PSMS;
            case TARGET_PEPTIDES -> PercolatorArtefact.TARGET_PEPTIDES;
            case DECOY_PSMS -> PercolatorArtefact.DECOY_PSMS;
            case DECOY_PEPTIDES -> PercolatorArtefact.DECOY_PEPTIDES;
        };
    }

    /**
     * {@code finalise-results}: opens every result table the run's command produced through the one
     * store factory, {@link ResultStores#open}, with the run's own index directory ({@code
     * results/index/}) and the one hasher -- so a table above the in-memory row limit has its disk
     * index built and checked now, and the interface opens it fast later -- and records, as details
     * of its {@code stage.finished} event, each table's row count and its counts at the default
     * display filters ({@link DisplayFilters#DEFAULTS}, 0.01 and 0.01), plus the learned weights'
     * split and feature counts through {@link WeightsSummary} when the weights were written.
     *
     * <p>The raw tables are only ever read; nothing is written under {@code outputs/} ({@code
     * R-PERC-07}), and every raw output is byte-identical afterwards. A table the command did not
     * ask for -- a decoy table without {@code DECOY_OUTPUT} -- is simply not there to open. A table
     * the reader refuses, or whose index cannot be trusted even when freshly built, fails the step
     * naming the file.
     *
     * <p>The details, per table (role as in provenance, for example {@code percolator-psms}):
     * {@code tables.<role>.rows}, {@code .store} ({@code memory} or {@code disk}), {@code .total},
     * {@code .passing}, {@code .failing} and {@code .unknown-q}; {@code filters.psm} and {@code
     * filters.peptide}, the cutoffs counted at; {@code weights.splits} and {@code
     * weights.features}.
     */
    static final class FinaliseResults implements StepAction {

        private final PercolatorRun run;

        private final long inMemoryRowLimit;

        /**
         * The step, with the product's in-memory row limit.
         *
         * @param run the run's Percolator half
         */
        FinaliseResults(PercolatorRun run) {
            this(run, ResultStores.IN_MEMORY_ROW_LIMIT);
        }

        /**
         * The step with another in-memory row limit: for tests, so that a small table takes the
         * disk path.
         *
         * @param run the run's Percolator half
         * @param inMemoryRowLimit the most rows a table held in memory may have
         */
        FinaliseResults(PercolatorRun run, long inMemoryRowLimit) {
            this.run = Objects.requireNonNull(run, "run");
            this.inMemoryRowLimit = inMemoryRowLimit;
        }

        @Override
        public StepDeclaration declaration() {
            return PercolatorDeclarations.finaliseResults(run);
        }

        @Override
        public void execute(StepContext context) throws StepFailedException, IOException {
            Map<PercolatorArtefact, Path> artefacts = run.command().artefacts();
            DisplayFilters filters = DisplayFilters.DEFAULTS;
            context.addDetail("filters.psm", filters.psm().text());
            context.addDetail("filters.peptide", filters.peptide().text());
            Path index = run.layout().resultIndexDirectory();
            for (TableKind kind : TABLES) {
                PercolatorArtefact artefact = artefactOf(kind);
                Path file = artefacts.get(artefact);
                if (file != null) {
                    String role = PercolatorDeclarations.roleOf(artefact);
                    QValueFilter filter = kind.isPsms() ? filters.psm() : filters.peptide();
                    record(context, "tables." + role + ".", open(role, file, kind, index), filter);
                }
            }
            Path weights = artefacts.get(PercolatorArtefact.WEIGHTS);
            if (weights != null) {
                WeightsSummary summary;
                try {
                    summary = WeightsSummary.of(WeightsReader.read(weights));
                } catch (IOException refused) {
                    throw refusal(PercolatorDeclarations.WEIGHTS, weights, refused);
                }
                context.addDetail("weights.splits", Integer.toString(summary.splitCount()));
                context.addDetail("weights.features", Integer.toString(summary.features().size()));
            }
        }

        private ResultStore open(String role, Path file, TableKind kind, Path index)
                throws StepFailedException {
            try {
                return ResultStores.open(file, kind, index, run.hashes(), inMemoryRowLimit);
            } catch (IOException refused) {
                throw refusal(role, file, refused);
            }
        }

        private void record(
                StepContext context, String prefix, ResultStore opened, QValueFilter filter)
                throws IOException {
            try (ResultStore store = opened) {
                long rows = store.rowCount();
                FilterCounts counts = store.counts(filter);
                context.addDetail(prefix + "rows", Long.toString(rows));
                context.addDetail(prefix + "store", rows > inMemoryRowLimit ? "disk" : "memory");
                context.addDetail(prefix + "total", Long.toString(counts.total()));
                context.addDetail(prefix + "passing", Long.toString(counts.passing()));
                context.addDetail(prefix + "failing", Long.toString(counts.failing()));
                context.addDetail(prefix + "unknown-q", Long.toString(counts.unknownQValue()));
            }
        }

        private static StepFailedException refusal(String role, Path file, IOException refused) {
            return new StepFailedException(
                    "the "
                            + role
                            + " file "
                            + file
                            + " cannot be finalised for the results: "
                            + refused.getMessage(),
                    refused);
        }
    }
}
