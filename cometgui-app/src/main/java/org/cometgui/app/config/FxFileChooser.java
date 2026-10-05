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

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.cometgui.ui.viewmodel.params.FileChooserPort;

/**
 * The parameter editor's {@link FileChooserPort} on JavaFX's own {@link FileChooser}: a modal
 * dialog over the application window, called on the interface thread.
 *
 * <p>The dialogs themselves are one seam ({@link Dialogs}), so that what this class decides -- the
 * title, the file-type filters, the suggested file name, and how a cancelled dialog is answered --
 * is tested without opening a native window, which a headless test run cannot do. The GUI tests
 * replace the whole port instead (the specification's "file chooser abstraction and its test
 * injection"): {@code CometGuiApplication} takes the port's factory as a constructor argument.
 */
public final class FxFileChooser implements FileChooserPort {

    /** The three JavaFX dialogs, as one seam. */
    public interface Dialogs {

        /**
         * Shows an open dialog for several files.
         *
         * @param chooser the configured chooser
         * @param owner the window the dialog is modal over
         * @return the files, or {@code null} if cancelled, as JavaFX answers
         */
        List<File> openMultiple(FileChooser chooser, Window owner);

        /**
         * Shows an open dialog for one file.
         *
         * @param chooser the configured chooser
         * @param owner the window the dialog is modal over
         * @return the file, or {@code null} if cancelled
         */
        File open(FileChooser chooser, Window owner);

        /**
         * Shows a save dialog.
         *
         * @param chooser the configured chooser
         * @param owner the window the dialog is modal over
         * @return the file, or {@code null} if cancelled
         */
        File save(FileChooser chooser, Window owner);
    }

    /** JavaFX's own dialogs. */
    static final Dialogs JAVAFX =
            new Dialogs() {
                @Override
                public List<File> openMultiple(FileChooser chooser, Window owner) {
                    return chooser.showOpenMultipleDialog(owner);
                }

                @Override
                public File open(FileChooser chooser, Window owner) {
                    return chooser.showOpenDialog(owner);
                }

                @Override
                public File save(FileChooser chooser, Window owner) {
                    return chooser.showSaveDialog(owner);
                }
            };

    /** The name a new parameter file is offered. */
    static final String SUGGESTED_PARAMETER_FILE = "comet.params";

    private final Supplier<Window> owner;

    private final Dialogs dialogs;

    /**
     * The chooser over the application window, with JavaFX's dialogs.
     *
     * @param owner the window the dialogs are modal over, asked for each time a dialog opens
     */
    public FxFileChooser(Supplier<Window> owner) {
        this(owner, JAVAFX);
    }

    /**
     * The chooser with given dialogs.
     *
     * @param owner the window the dialogs are modal over
     * @param dialogs the dialogs
     */
    FxFileChooser(Supplier<Window> owner, Dialogs dialogs) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
    }

    @Override
    public List<Path> chooseSpectrumFiles() {
        FileChooser chooser = chooser("Choose spectrum files to search");
        chooser.getExtensionFilters()
                .addAll(
                        new FileChooser.ExtensionFilter(
                                "Spectrum files (mzML, mzXML, MGF, MS2, Thermo RAW)",
                                "*.mzML",
                                "*.mzml",
                                "*.mzXML",
                                "*.mzxml",
                                "*.mgf",
                                "*.ms2",
                                "*.cms2",
                                "*.bms2",
                                "*.raw",
                                "*.RAW"),
                        allFiles());
        List<File> chosen = dialogs.openMultiple(chooser, owner.get());
        return chosen == null ? List.of() : chosen.stream().map(File::toPath).toList();
    }

    @Override
    public Optional<Path> chooseDatabase() {
        FileChooser chooser = chooser("Choose the sequence database");
        chooser.getExtensionFilters()
                .addAll(
                        new FileChooser.ExtensionFilter(
                                "FASTA files", "*.fasta", "*.fa", "*.faa", "*.fas"),
                        new FileChooser.ExtensionFilter("Comet index files", "*.idx"),
                        allFiles());
        return Optional.ofNullable(dialogs.open(chooser, owner.get())).map(File::toPath);
    }

    @Override
    public Optional<Path> chooseFile(String what) {
        FileChooser chooser = chooser("Choose the file for " + Objects.requireNonNull(what));
        chooser.getExtensionFilters().add(allFiles());
        return Optional.ofNullable(dialogs.open(chooser, owner.get())).map(File::toPath);
    }

    @Override
    public Optional<Path> chooseSaveTarget() {
        FileChooser chooser = chooser("Save the Comet parameter file");
        chooser.setInitialFileName(SUGGESTED_PARAMETER_FILE);
        chooser.getExtensionFilters()
                .addAll(
                        new FileChooser.ExtensionFilter("Comet parameter files", "*.params"),
                        allFiles());
        return Optional.ofNullable(dialogs.save(chooser, owner.get())).map(File::toPath);
    }

    private static FileChooser chooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        return chooser;
    }

    private static FileChooser.ExtensionFilter allFiles() {
        return new FileChooser.ExtensionFilter("All files", "*");
    }
}
