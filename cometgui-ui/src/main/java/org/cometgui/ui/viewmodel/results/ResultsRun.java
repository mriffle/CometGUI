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

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.RunId;
import org.cometgui.results.filtering.store.TableKind;

/**
 * One run of the session's project that has results, as {@link ResultsPort#runs()} lists it.
 *
 * @param id the run's identifier
 * @param created when the run was created; the most recent is the Results section's default
 * @param outcome how its last attempt ended, or {@link AttemptOutcome#RUNNING}
 * @param tables which of Percolator's four tables the run has, at least one
 * @param executing whether the workflow engine is executing the run now -- the engine then holds
 *     its provenance event log, so nothing can be exported from it
 */
public record ResultsRun(
        RunId id,
        Instant created,
        AttemptOutcome outcome,
        Set<TableKind> tables,
        boolean executing) {

    /**
     * A run.
     *
     * @throws IllegalArgumentException if it has no table
     * @throws NullPointerException if a reference is {@code null}
     */
    public ResultsRun {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(created, "created");
        Objects.requireNonNull(outcome, "outcome");
        if (tables.isEmpty()) {
            throw new IllegalArgumentException("a run with results has a table: " + id.value());
        }
        tables = Set.copyOf(EnumSet.copyOf(tables));
    }

    /**
     * How the run is named in the run selector.
     *
     * @return for example {@code 20261009T101500Z-ab12 -- created 2026-10-09T10:15:00Z, succeeded}
     */
    public String label() {
        return id.value()
                + " -- created "
                + created
                + ", "
                + (executing ? "executing now" : outcome.wireName());
    }
}
