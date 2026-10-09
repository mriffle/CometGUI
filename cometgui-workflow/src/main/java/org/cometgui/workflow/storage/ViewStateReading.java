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

package org.cometgui.workflow.storage;

import java.util.Objects;
import java.util.Optional;
import org.cometgui.results.filtering.DisplayFilters;

/**
 * What {@link ViewStateStore#read} found: the display filters to show, where they came from, and --
 * when the file could not be read -- why, in words the interface can show.
 *
 * @param filters the filters to show: the saved ones, or {@link DisplayFilters#DEFAULTS}
 * @param source where they came from
 * @param refusal why the file was refused -- naming the file and the member, quoting no value --
 *     present exactly when {@code source} is {@link Source#DEFAULTS_FILE_REFUSED}
 */
public record ViewStateReading(DisplayFilters filters, Source source, Optional<String> refusal) {

    /** Where a reading's filters came from. */
    public enum Source {

        /** The run's {@code view-state.json}. */
        SAVED,

        /** The defaults, because the run has no {@code view-state.json}: nothing was saved yet. */
        DEFAULTS_NOTHING_SAVED,

        /**
         * The defaults, because the run's {@code view-state.json} could not be read: damaged, of
         * another schema version, or holding a cutoff outside {@code [0, 1]}. The file is left
         * exactly as it was, and {@link ViewStateStore#write} will not replace it.
         */
        DEFAULTS_FILE_REFUSED
    }

    /**
     * Checks that the parts agree.
     *
     * @throws NullPointerException if any part is {@code null}
     * @throws IllegalArgumentException if a refusal is present without {@link
     *     Source#DEFAULTS_FILE_REFUSED} or absent with it, or defaults are not {@link
     *     DisplayFilters#DEFAULTS}
     */
    public ViewStateReading {
        Objects.requireNonNull(filters, "filters");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(refusal, "refusal");
        if (refusal.isPresent() != (source == Source.DEFAULTS_FILE_REFUSED)) {
            throw new IllegalArgumentException(
                    "a refusal is present exactly when the file was refused, but source is "
                            + source
                            + " and the refusal is "
                            + (refusal.isPresent() ? "present" : "absent"));
        }
        if (source != Source.SAVED && !filters.equals(DisplayFilters.DEFAULTS)) {
            throw new IllegalArgumentException(
                    "a reading from " + source + " holds the default filters, not " + filters);
        }
    }
}
