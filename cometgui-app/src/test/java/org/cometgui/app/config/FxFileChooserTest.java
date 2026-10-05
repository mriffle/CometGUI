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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.cometgui.app.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the JavaFX file chooser decides -- titles, filters, the suggested name, and how a cancelled
 * dialog is answered -- through a recording seam in place of the native dialogs, which a headless
 * run cannot open. The dialogs themselves are JavaFX's; the GUI tests replace the whole port.
 */
class FxFileChooserTest {

    /** Records each dialog it is asked for, and answers from a script. */
    private static final class RecordingDialogs implements FxFileChooser.Dialogs {

        private final List<String> asked = new ArrayList<>();

        private final List<FileChooser> choosers = new ArrayList<>();

        private final List<Window> owners = new ArrayList<>();

        private List<File> multiple;

        private File single;

        @Override
        public List<File> openMultiple(FileChooser chooser, Window owner) {
            record("open multiple", chooser, owner);
            return multiple;
        }

        @Override
        public File open(FileChooser chooser, Window owner) {
            record("open", chooser, owner);
            return single;
        }

        @Override
        public File save(FileChooser chooser, Window owner) {
            record("save", chooser, owner);
            return single;
        }

        private void record(String dialog, FileChooser chooser, Window owner) {
            asked.add(dialog);
            choosers.add(chooser);
            owners.add(owner);
        }

        FileChooser last() {
            return choosers.get(choosers.size() - 1);
        }
    }

    /** An absolute file the dialogs answer with, built rather than written as a literal. */
    private static File file(String directory, String name) {
        return path(directory, name).toFile();
    }

    /** The absolute path of {@link #file(String, String)}, typed out from the same parts. */
    private static Path path(String directory, String name) {
        return Path.of(directory, name).toAbsolutePath();
    }

    private static List<String> filters(FileChooser chooser) {
        List<String> described = new ArrayList<>();
        for (FileChooser.ExtensionFilter filter : chooser.getExtensionFilters()) {
            described.add(filter.getDescription() + " " + filter.getExtensions());
        }
        return described;
    }

    @Test
    @DisplayName("spectra: several files, the spectrum formats first, every chosen path in order")
    void spectra() {
        RecordingDialogs dialogs = new RecordingDialogs();
        dialogs.multiple = List.of(file("data", "a.mzML"), file("data", "b.mgf"));
        FxFileChooser chooser = new FxFileChooser(() -> null, dialogs);
        assertEquals(
                List.of(path("data", "a.mzML"), path("data", "b.mgf")),
                chooser.chooseSpectrumFiles());
        assertAll(
                () -> assertEquals(List.of("open multiple"), dialogs.asked),
                () -> assertEquals("Choose spectrum files to search", dialogs.last().getTitle()),
                () ->
                        assertEquals(
                                List.of(
                                        "Spectrum files (mzML, mzXML, MGF, MS2, Thermo RAW)"
                                                + " [*.mzML, *.mzml, *.mzXML, *.mzxml, *.mgf,"
                                                + " *.ms2, *.cms2, *.bms2, *.raw, *.RAW]",
                                        "All files [*]"),
                                filters(dialogs.last())));
    }

    @Test
    @DisplayName("a cancelled dialog is no file, never null")
    void cancelled() {
        RecordingDialogs dialogs = new RecordingDialogs();
        FxFileChooser chooser = new FxFileChooser(() -> null, dialogs);
        assertAll(
                () -> assertEquals(List.of(), chooser.chooseSpectrumFiles()),
                () -> assertEquals(Optional.empty(), chooser.chooseDatabase()),
                () -> assertEquals(Optional.empty(), chooser.chooseFile("Spectral library file")),
                () -> assertEquals(Optional.empty(), chooser.chooseSaveTarget()),
                () ->
                        assertEquals(
                                List.of("open multiple", "open", "open", "save"), dialogs.asked));
    }

    @Test
    @DisplayName("the database: FASTA first, then Comet's index, then anything")
    void database() {
        RecordingDialogs dialogs = new RecordingDialogs();
        dialogs.single = file("data", "human.fasta");
        FxFileChooser chooser = new FxFileChooser(() -> null, dialogs);
        assertEquals(Optional.of(path("data", "human.fasta")), chooser.chooseDatabase());
        assertEquals("Choose the sequence database", dialogs.last().getTitle());
        assertEquals(
                List.of(
                        "FASTA files [*.fasta, *.fa, *.faa, *.fas]",
                        "Comet index files [*.idx]",
                        "All files [*]"),
                filters(dialogs.last()));
    }

    @Test
    @DisplayName("a file parameter: titled with what it is for")
    void fileParameter() {
        RecordingDialogs dialogs = new RecordingDialogs();
        dialogs.single = file("data", "unimod.obo");
        FxFileChooser chooser = new FxFileChooser(() -> null, dialogs);
        assertEquals(
                Optional.of(path("data", "unimod.obo")),
                chooser.chooseFile("PEFF modification ontology (OBO) file"));
        assertEquals(
                "Choose the file for PEFF modification ontology (OBO) file",
                dialogs.last().getTitle());
        assertEquals(List.of("All files [*]"), filters(dialogs.last()));
        assertThrows(NullPointerException.class, () -> chooser.chooseFile(Nulls.of(String.class)));
    }

    @Test
    @DisplayName("saving: a save dialog suggesting comet.params")
    void save() {
        RecordingDialogs dialogs = new RecordingDialogs();
        dialogs.single = file("runs", "search.params");
        FxFileChooser chooser = new FxFileChooser(() -> null, dialogs);
        assertEquals(Optional.of(path("runs", "search.params")), chooser.chooseSaveTarget());
        assertAll(
                () -> assertEquals(List.of("save"), dialogs.asked),
                () -> assertEquals("Save the Comet parameter file", dialogs.last().getTitle()),
                () -> assertEquals("comet.params", dialogs.last().getInitialFileName()),
                () ->
                        assertEquals(
                                List.of("Comet parameter files [*.params]", "All files [*]"),
                                filters(dialogs.last())));
    }

    @Test
    @DisplayName("the owner window is asked for each time a dialog opens, not once")
    void ownerIsAskedEachTime() {
        RecordingDialogs dialogs = new RecordingDialogs();
        int[] asked = {0};
        FxFileChooser chooser =
                new FxFileChooser(
                        () -> {
                            asked[0]++;
                            return null;
                        },
                        dialogs);
        chooser.chooseDatabase();
        chooser.chooseSpectrumFiles();
        chooser.chooseFile("x");
        chooser.chooseSaveTarget();
        assertEquals(4, asked[0]);
        assertEquals(4, dialogs.owners.size());
        assertThrows(NullPointerException.class, () -> new FxFileChooser(null, dialogs));
        assertThrows(NullPointerException.class, () -> new FxFileChooser(() -> null, null));
    }
}
