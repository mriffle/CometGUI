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

package org.cometgui.ui.viewmodel.params;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.property.ReadOnlyObjectProperty;
import org.cometgui.domain.ports.FileSystemAccess;
import org.cometgui.ui.viewmodel.NonNullProperty;

/**
 * The Essentials Inputs section: the spectrum files to search and the sequence database (decision
 * P7-9).
 *
 * <p><strong>Spectra are run inputs, not parameters.</strong> The chosen paths are kept here and
 * are never written into {@code comet.params}; the workflow passes them to Comet (Phase 08). The
 * database is a parameter: a chosen database is set as {@code database_name} through the session,
 * and so through the model.
 *
 * <p>Files are chosen through a {@link FileChooserPort}, which the view implements and a test
 * replaces. What the file system says about each file -- whether it exists and can be read -- is
 * read through the domain {@link FileSystemAccess} port and shown in words. Whether a file is a
 * supported spectrum format, and whether the database holds decoys, are checked before a run (Phase
 * 08), not here.
 */
public final class SpectrumInputsViewModel {

    /** The parameter a chosen database is written to. */
    public static final String DATABASE_PARAMETER = "database_name";

    private final ParameterSession session;

    private final FileChooserPort chooser;

    private final FileSystemAccess files;

    private final NonNullProperty<List<InputFile>> spectra;

    private final NonNullProperty<InputFile> database;

    /**
     * The Inputs section over a session.
     *
     * @param session the session whose {@code database_name} the database is
     * @param chooser the file chooser
     * @param files the file system
     */
    public SpectrumInputsViewModel(
            ParameterSession session, FileChooserPort chooser, FileSystemAccess files) {
        this.session = Objects.requireNonNull(session, "session");
        this.chooser = Objects.requireNonNull(chooser, "chooser");
        this.files = Objects.requireNonNull(files, "files");
        this.spectra = new NonNullProperty<>(this, "spectra", List.of());
        this.database = new NonNullProperty<>(this, "database", databaseFile());
        session.modelProperty().addListener((observable, before, after) -> refreshDatabase());
    }

    /**
     * The chosen spectrum files, in the order chosen, each with its status.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<List<InputFile>> spectraProperty() {
        return spectra.getReadOnlyProperty();
    }

    /**
     * The chosen spectrum files.
     *
     * @return the files, immutable
     */
    public List<InputFile> spectra() {
        return spectra.get();
    }

    /**
     * The chosen spectrum paths, for the run.
     *
     * @return the paths, in the order chosen
     */
    public List<Path> spectrumPaths() {
        return spectra.get().stream().map(InputFile::path).toList();
    }

    /**
     * The configuration's database, with its status.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<InputFile> databaseProperty() {
        return database.getReadOnlyProperty();
    }

    /**
     * The configuration's database, with its status.
     *
     * @return the database
     */
    public InputFile database() {
        return database.get();
    }

    /**
     * Asks the chooser for spectrum files and adds those not chosen already.
     *
     * @return how many were added; 0 if the chooser was cancelled
     */
    public int chooseSpectra() {
        List<InputFile> next = new ArrayList<>(spectra.get());
        List<Path> present = spectrumPaths();
        int added = 0;
        for (Path chosen : chooser.chooseSpectrumFiles()) {
            if (!present.contains(chosen)) {
                next.add(new InputFile(chosen, statusOf(chosen)));
                present = next.stream().map(InputFile::path).toList();
                added++;
            }
        }
        spectra.set(List.copyOf(next));
        return added;
    }

    /**
     * Removes one chosen spectrum file.
     *
     * @param path the file's path
     * @return {@code true} if it was among the chosen files
     */
    public boolean removeSpectrum(Path path) {
        Objects.requireNonNull(path, "path");
        List<InputFile> kept = spectra.get().stream().filter(f -> !f.path().equals(path)).toList();
        boolean removed = kept.size() != spectra.get().size();
        spectra.set(kept);
        return removed;
    }

    /**
     * Asks the chooser for the database and, if one was chosen, sets it as {@code database_name}
     * through the session.
     *
     * @return the session's outcome, or empty if the chooser was cancelled
     */
    public Optional<EditOutcome> chooseDatabase() {
        Optional<Path> chosen = chooser.chooseDatabase();
        Optional<EditOutcome> outcome =
                chosen.map(path -> session.edit(DATABASE_PARAMETER, path.toString()));
        refreshDatabase();
        return outcome;
    }

    /** Reads every file's status again, for files that have appeared or changed since. */
    public void refresh() {
        spectra.set(
                spectra.get().stream()
                        .map(file -> new InputFile(file.path(), statusOf(file.path())))
                        .toList());
        refreshDatabase();
    }

    private void refreshDatabase() {
        database.set(databaseFile());
    }

    private InputFile databaseFile() {
        String text = session.model().text(DATABASE_PARAMETER);
        if (text.isEmpty()) {
            return new InputFile(Path.of(""), FileStatus.NONE);
        }
        Path path;
        try {
            path = Path.of(text);
        } catch (InvalidPathException unusable) {
            return new InputFile(Path.of(""), FileStatus.NOT_A_PATH);
        }
        return new InputFile(path, statusOf(path));
    }

    private FileStatus statusOf(Path path) {
        if (!files.exists(path)) {
            return FileStatus.MISSING;
        }
        if (files.isDirectory(path)) {
            return FileStatus.DIRECTORY;
        }
        return files.isReadable(path) ? FileStatus.READABLE : FileStatus.UNREADABLE;
    }
}
