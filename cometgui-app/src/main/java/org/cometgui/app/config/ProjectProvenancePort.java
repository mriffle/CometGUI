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

package org.cometgui.app.config;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.cometgui.domain.run.RunId;
import org.cometgui.ui.viewmodel.provenance.OpenedRecord;
import org.cometgui.ui.viewmodel.provenance.ProvenancePort;
import org.cometgui.workflow.provenance.DiskVerification;
import org.cometgui.workflow.provenance.LoadedRun;
import org.cometgui.workflow.provenance.ProvenanceReader;
import org.cometgui.workflow.provenance.ProvenanceView;
import org.cometgui.workflow.provenance.RunListing;

/**
 * The Provenance section's port over the session's project and the read model (phase 13, work unit
 * 5; {@link ProvenancePort}).
 *
 * <p>Every answer is {@link ProvenanceReader}'s: the runs are {@link ProvenanceReader#list} of the
 * session's project with the engine's executing runs, a run is {@link ProvenanceReader#load}, and
 * the exports and the verification are the loaded run's own -- so both exports render, and the
 * verification checks, <em>the record the section shows</em>, never one read again later (P13-3).
 * Listing needs no project lock: nothing here writes into the project. Open file location is {@link
 * FileManagerLauncher}'s, through the one process service (P13-6).
 *
 * <p>Every method blocks and is called off the JavaFX thread.
 */
final class ProjectProvenancePort implements ProvenancePort {

    private final ProjectSession project;

    private final Supplier<Set<RunId>> executing;

    private final ProvenanceReader reader;

    private final FileManagerLauncher files;

    /**
     * The port.
     *
     * @param project the session's project
     * @param executing the runs the session's engine is executing now
     * @param reader the read model, over the session's redactor and the product's one hasher
     * @param files starts the file manager
     */
    ProjectProvenancePort(
            ProjectSession project,
            Supplier<Set<RunId>> executing,
            ProvenanceReader reader,
            FileManagerLauncher files) {
        this.project = Objects.requireNonNull(project, "project");
        this.executing = Objects.requireNonNull(executing, "executing");
        this.reader = Objects.requireNonNull(reader, "reader");
        this.files = Objects.requireNonNull(files, "files");
    }

    @Override
    public List<RunListing> runs() throws IOException {
        return reader.list(project.layout(), executing.get());
    }

    @Override
    public OpenedRecord open(RunListing run) throws IOException {
        Objects.requireNonNull(run, "run");
        return new Opened(run, reader.load(run.directory()));
    }

    @Override
    public Path exportJson(OpenedRecord run, Path target, boolean replaceExisting)
            throws IOException {
        return loaded(run).exportJson(target, replaceExisting);
    }

    @Override
    public Path exportRst(OpenedRecord run, Path target, boolean replaceExisting)
            throws IOException {
        return loaded(run).exportRst(target, replaceExisting);
    }

    @Override
    public DiskVerification verify(OpenedRecord run, BooleanSupplier cancelled) throws IOException {
        return loaded(run).verifyAgainstDisk(cancelled);
    }

    @Override
    public String openLocation(Path file) throws IOException {
        return files.open(file);
    }

    private static LoadedRun loaded(OpenedRecord run) throws IOException {
        if (run instanceof Opened opened) {
            return opened.loaded;
        }
        throw new IOException(
                "this run was not opened by the application's provenance port, so there is no"
                        + " loaded record to act on");
    }

    /** One run as the read model loaded it. */
    private static final class Opened implements OpenedRecord {

        private final RunListing listing;

        private final LoadedRun loaded;

        Opened(RunListing listing, LoadedRun loaded) {
            this.listing = listing;
            this.loaded = loaded;
        }

        @Override
        public RunListing listing() {
            return listing;
        }

        @Override
        public ProvenanceView view() {
            return loaded.view();
        }

        @Override
        public Optional<String> recordProblem() {
            return loaded.recordProblem();
        }

        @Override
        public boolean canExport() {
            return loaded.canExport();
        }
    }
}
