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

package org.cometgui.ui.viewmodel.provenance;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.cometgui.workflow.provenance.DiskVerification;
import org.cometgui.workflow.provenance.RunListing;

/**
 * What the Provenance section asks of the application (phase 13, design decisions P13-2, P13-6 and
 * P13-8): the session project's runs, one run's record, the two exports, verification against disk,
 * and opening a file's location in the platform's file manager.
 *
 * <p><strong>Every method blocks</strong> -- it reads run directories, hashes files, writes a
 * document or starts a process -- <strong>and none is ever called on the JavaFX application
 * thread</strong>: {@link ProvenanceViewModel} calls each from its background executor and applies
 * the answer on the interface thread.
 *
 * <p>Every answer is the read model's ({@code org.cometgui.workflow.provenance}): the port lists
 * with {@code ProvenanceReader.list}, opens with {@code ProvenanceReader.load}, and acts through
 * the loaded run's own {@code exportJson}, {@code exportRst} and {@code verifyAgainstDisk}. Nothing
 * here re-implements a reader, a hasher, a redactor or a writer.
 */
public interface ProvenancePort {

    /**
     * Every run directory of the session's project, whatever became of its run: completed, failed,
     * cancelled, running, interrupted, never started, and one whose {@code run.json} cannot be
     * read.
     *
     * @return the runs, in directory-name order; empty when the project has none
     * @throws IOException if the project's runs cannot be listed
     */
    List<RunListing> runs() throws IOException;

    /**
     * Reads one run's provenance: its sealed record, or the stated reason it has none.
     *
     * @param run one of {@link #runs()}'s runs
     * @return the opened run; never an empty one
     * @throws IOException if the run's directory no longer exists
     */
    OpenedRecord open(RunListing run) throws IOException;

    /**
     * Exports an opened run's record as JSON, rendered again from the record that was read.
     *
     * @param run a run {@link #open} returned
     * @param target where to write; its directory must exist
     * @param replaceExisting whether an existing file at {@code target} may be replaced
     * @return the file written
     * @throws FileAlreadyExistsException if {@code target} exists and {@code replaceExisting} is
     *     {@code false}, or is one of the run's own sealed documents, with the reason
     * @throws IOException if nothing was written, saying why -- among other reasons because the run
     *     has no sealed record to export
     */
    Path exportJson(OpenedRecord run, Path target, boolean replaceExisting) throws IOException;

    /**
     * Exports an opened run's record as the RST report, rendered from the same record {@link
     * #exportJson} renders.
     *
     * @param run a run {@link #open} returned
     * @param target where to write; its directory must exist
     * @param replaceExisting whether an existing file at {@code target} may be replaced
     * @return the file written
     * @throws FileAlreadyExistsException as {@link #exportJson}
     * @throws IOException as {@link #exportJson}
     */
    Path exportRst(OpenedRecord run, Path target, boolean replaceExisting) throws IOException;

    /**
     * Hashes again every file an opened run's record lists and compares each with its record.
     *
     * @param run a run {@link #open} returned
     * @param cancelled asked before each file whether to stop
     * @return what was found, per file; incomplete when cancelled
     * @throws IOException if the run has no sealed record to verify, saying why
     */
    DiskVerification verify(OpenedRecord run, BooleanSupplier cancelled) throws IOException;

    /**
     * Shows a file's location in the platform's own file manager, started through the one process
     * service with an argument array (P13-6).
     *
     * @param file the file whose location is shown
     * @return what was started, in words, for the section to say
     * @throws IOException if no file manager could be started -- a missing launcher among them --
     *     saying why
     */
    String openLocation(Path file) throws IOException;
}
