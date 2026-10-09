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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.project.UnsupportedSchemaVersionException;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.provenance.io.AtomicDocumentWriter;
import org.cometgui.results.filtering.DisplayFilters;

/**
 * Reads and writes a run's view state, {@code results/view-state.json} ({@link ViewStateJson}): the
 * display-filter values its results were last shown with ({@code R-RES-01}).
 *
 * <p>The view state is not part of the run's record. It is never in {@code run.json}, never in
 * provenance and never under {@code outputs/}: writing it touches {@code results/view-state.json}
 * and a temporary file beside it, and nothing else. Changing it reruns nothing.
 *
 * <h2>What each state of the file means to the caller</h2>
 *
 * <ul>
 *   <li><strong>No file</strong>: nothing was saved. {@link #read} gives the defaults, 0.01 and
 *       0.01 ({@link DisplayFilters#DEFAULTS}), as {@link
 *       ViewStateReading.Source#DEFAULTS_NOTHING_SAVED}.
 *   <li><strong>A version-1 file</strong>: its filters, as {@link ViewStateReading.Source#SAVED}.
 *   <li><strong>A file this build cannot read</strong> -- not UTF-8, not JSON, a missing or unknown
 *       member, a cutoff outside {@code [0, 1]}, or another schema version: {@link #read} does not
 *       throw. It gives the defaults as {@link ViewStateReading.Source#DEFAULTS_FILE_REFUSED}, with
 *       the refusal's own words (naming the file and the member, quoting no value) for the
 *       interface to show beside them. The file is left byte for byte as it was: reading never
 *       writes, and <strong>{@link #write} refuses to replace it</strong>, with the same refusal.
 *       So a view state written by a newer CometGUI is never overwritten by an older one -- {@code
 *       R-RUN-04}'s "refuse without data loss" -- and a damaged file stays for someone to look at;
 *       removing it is how the scientist saves filters for that run again.
 *   <li><strong>A file that cannot be read at all</strong> (permissions, an I/O error): {@link
 *       #read} throws {@link IOException}. That is not a statement about the document.
 * </ul>
 */
public final class ViewStateStore {

    private ViewStateStore() {
        throw new AssertionError("ViewStateStore is never instantiated");
    }

    /**
     * Reads a run's view state, falling back to the defaults when there is none or it is refused.
     *
     * @param run the run
     * @return the filters to show and where they came from
     * @throws IOException if the file exists but cannot be read
     * @throws NullPointerException if {@code run} is {@code null}
     */
    public static ViewStateReading read(RunLayout run) throws IOException {
        Objects.requireNonNull(run, "run");
        Path file = run.viewStateFile();
        if (!Files.exists(file)) {
            return new ViewStateReading(
                    DisplayFilters.DEFAULTS,
                    ViewStateReading.Source.DEFAULTS_NOTHING_SAVED,
                    Optional.empty());
        }
        try {
            return new ViewStateReading(
                    parse(file), ViewStateReading.Source.SAVED, Optional.empty());
        } catch (InvalidDocumentException | UnsupportedSchemaVersionException refused) {
            return new ViewStateReading(
                    DisplayFilters.DEFAULTS,
                    ViewStateReading.Source.DEFAULTS_FILE_REFUSED,
                    Optional.of(refused.getMessage()));
        }
    }

    /**
     * Saves a run's view state, atomically ({@code results/} is made if absent).
     *
     * @param run the run
     * @param filters the filters to save
     * @throws InvalidDocumentException if the run already has a view state this build cannot read;
     *     it is left untouched
     * @throws UnsupportedSchemaVersionException if the run's view state is of another schema
     *     version; it is left untouched
     * @throws IOException if the file cannot be read or written
     * @throws NullPointerException if an argument is {@code null}
     */
    public static void write(RunLayout run, DisplayFilters filters) throws IOException {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(filters, "filters");
        Path file = run.viewStateFile();
        if (Files.exists(file)) {
            parse(file);
        }
        Files.createDirectories(run.resultsDirectory());
        AtomicDocumentWriter.write(file, ViewStateJson.render(filters));
    }

    private static DisplayFilters parse(Path file) throws IOException {
        return ViewStateJson.parse(DocumentFields.readText(file), file.toString());
    }
}
