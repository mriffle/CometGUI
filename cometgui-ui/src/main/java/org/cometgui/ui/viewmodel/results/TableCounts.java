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

import java.util.Objects;
import java.util.Optional;
import org.cometgui.results.filtering.FilterCounts;
import org.cometgui.results.filtering.QValueFilter;
import org.cometgui.results.filtering.store.TableKind;

/**
 * The open table's counts under its q-value filter, as the store computed them over the whole
 * table: total, passing, failing and unknown q-value -- all four always shown (design decision
 * P10-5, {@code R-RES-01}, {@code R-RES-02}).
 *
 * @param counts the store's counts; empty when there are none to show
 * @param total the total, as text
 * @param passing the passing count, as text
 * @param failing the failing count, as text
 * @param unknownQValue the unknown-q-value count, as text
 * @param summary all four in a sentence, with the table and the cutoff
 */
public record TableCounts(
        Optional<FilterCounts> counts,
        String total,
        String passing,
        String failing,
        String unknownQValue,
        String summary) {

    /** The counts of a table that is not open. */
    public static final TableCounts NONE =
            new TableCounts(Optional.empty(), "", "", "", "", "No table is open.");

    /**
     * Counts.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public TableCounts {
        Objects.requireNonNull(counts, "counts");
        Objects.requireNonNull(total, "total");
        Objects.requireNonNull(passing, "passing");
        Objects.requireNonNull(failing, "failing");
        Objects.requireNonNull(unknownQValue, "unknownQValue");
        Objects.requireNonNull(summary, "summary");
    }

    /**
     * The counts a store gave for a table under a filter.
     *
     * @param kind the table
     * @param filter the filter they are under
     * @param counts the store's counts
     * @return the counts, in words
     */
    static TableCounts of(TableKind kind, QValueFilter filter, FilterCounts counts) {
        String rows = rowsName(kind);
        String summary =
                counts.total()
                        + " "
                        + rows
                        + " in total. At a q-value cutoff of "
                        + filter.text()
                        + " (a q-value equal to the cutoff passing): "
                        + counts.passing()
                        + " passing, "
                        + counts.failing()
                        + " failing, and "
                        + counts.unknownQValue()
                        + " with an unknown q-value, which neither pass nor fail.";
        return new TableCounts(
                Optional.of(counts),
                Long.toString(counts.total()),
                Long.toString(counts.passing()),
                Long.toString(counts.failing()),
                Long.toString(counts.unknownQValue()),
                summary);
    }

    /**
     * Counts that could not be read.
     *
     * @param why why
     * @return the counts, saying so
     */
    static TableCounts unavailable(String why) {
        return new TableCounts(
                Optional.empty(), "", "", "", "", "The counts could not be read: " + why);
    }

    /**
     * What a table's rows are called.
     *
     * @param kind the table
     * @return for example {@code target PSMs}
     */
    static String rowsName(TableKind kind) {
        return switch (kind) {
            case TARGET_PSMS -> "target PSMs";
            case DECOY_PSMS -> "decoy PSMs";
            case TARGET_PEPTIDES -> "target peptides";
            case DECOY_PEPTIDES -> "decoy peptides";
        };
    }
}
