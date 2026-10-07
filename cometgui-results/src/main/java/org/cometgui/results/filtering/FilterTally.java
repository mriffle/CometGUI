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

package org.cometgui.results.filtering;

import java.util.Objects;
import java.util.function.Consumer;
import org.cometgui.results.parser.ResultRow;

/**
 * Counts rows under one filter as they stream past, so a table of any size is counted without being
 * held: hand it to {@code ResultTableReader.forEach}. Not thread-safe.
 */
public final class FilterTally implements Consumer<ResultRow> {

    private final QValueFilter filter;
    private long passing;
    private long failing;
    private long unknownQValue;

    FilterTally(QValueFilter filter) {
        this.filter = Objects.requireNonNull(filter, "filter");
    }

    /**
     * Counts one row.
     *
     * @param row the row
     */
    @Override
    public void accept(ResultRow row) {
        switch (filter.classify(row)) {
            case PASSES -> passing++;
            case FAILS -> failing++;
            case UNKNOWN_Q_VALUE -> unknownQValue++;
        }
    }

    /**
     * The counts so far.
     *
     * @return the counts over every row accepted
     */
    public FilterCounts counts() {
        return new FilterCounts(passing + failing + unknownQValue, passing, failing, unknownQValue);
    }
}
