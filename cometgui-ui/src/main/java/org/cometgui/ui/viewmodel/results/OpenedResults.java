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

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.run.RunId;
import org.cometgui.results.filtering.store.ResultStore;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.results.parser.WeightsSummary;
import org.cometgui.workflow.storage.ViewStateReading;

/**
 * One run's results, opened by {@link ResultsPort#open}: everything the Results section shows for
 * it. The stores are open until {@link ResultsPort#close} is given this value.
 *
 * @param run the run
 * @param stores the run's tables that exist, each opened by {@code ResultStores.open} -- in memory
 *     or on disk by its size
 * @param weights the learned feature weights summary, if the run has a weights artefact
 * @param sourceFiles the run's spectrum files, from its recorded inputs: each Comet {@code -N} base
 *     (what a {@code PSMId} begins with) to the spectrum file's display name. Its size is the run's
 *     number of spectrum files; with more than one, the source-file column cannot be hidden
 * @param viewState the run's saved display filters, or the defaults and why
 */
public record OpenedResults(
        RunId run,
        Map<TableKind, ResultStore> stores,
        Optional<WeightsSummary> weights,
        Map<String, String> sourceFiles,
        ViewStateReading viewState) {

    /**
     * Opened results.
     *
     * @throws IllegalArgumentException if there is no store, or a store is under another kind
     * @throws NullPointerException if a reference is {@code null}
     */
    public OpenedResults {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(weights, "weights");
        Objects.requireNonNull(viewState, "viewState");
        if (stores.isEmpty()) {
            throw new IllegalArgumentException("opened results hold a table: " + run.value());
        }
        for (Map.Entry<TableKind, ResultStore> entry : new EnumMap<>(stores).entrySet()) {
            if (entry.getValue().kind() != entry.getKey()) {
                throw new IllegalArgumentException(
                        "the "
                                + entry.getKey()
                                + " store holds a "
                                + entry.getValue().kind()
                                + " table");
            }
        }
        stores = Map.copyOf(stores);
        sourceFiles = Map.copyOf(sourceFiles);
    }
}
