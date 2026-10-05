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

import static org.cometgui.ui.viewmodel.params.Sessions.C03;
import static org.cometgui.ui.viewmodel.params.Sessions.startingIn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.cometgui.domain.ports.FileSystemAccess;
import org.cometgui.params.comet.model.ValueOrigin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Spectrum inputs and the database through the file-chooser port (decision P7-9) and its test
 * injection: a scripted chooser and an in-memory file system.
 */
class SpectrumInputsTest {

    /** A chooser that answers from a script, one answer per call. */
    private static final class ScriptedChooser implements FileChooserPort {

        private final Deque<List<Path>> spectra = new ArrayDeque<>();

        private final Deque<Optional<Path>> databases = new ArrayDeque<>();

        @Override
        public List<Path> chooseSpectrumFiles() {
            return spectra.removeFirst();
        }

        @Override
        public Optional<Path> chooseDatabase() {
            return databases.removeFirst();
        }

        @Override
        public Optional<Path> chooseFile(String what) {
            throw new AssertionError("the inputs never ask for a file parameter's file");
        }

        @Override
        public Optional<Path> chooseSaveTarget() {
            throw new AssertionError("the inputs never ask where to save");
        }
    }

    /** What the file system says, per path; anything not listed does not exist. */
    private static final class MemoryFiles implements FileSystemAccess {

        private final Map<Path, String> kinds = new HashMap<>();

        @Override
        public boolean exists(Path path) {
            return kinds.containsKey(path);
        }

        @Override
        public boolean isReadable(Path path) {
            return "readable".equals(kinds.get(path));
        }

        @Override
        public boolean isDirectory(Path path) {
            return "directory".equals(kinds.get(path));
        }

        @Override
        public void createDirectories(Path path) {
            throw new UnsupportedOperationException("not used by the inputs");
        }

        @Override
        public Path applicationDataDirectory() {
            throw new UnsupportedOperationException("not used by the inputs");
        }
    }

    /** Relative, so that no test names an absolute path on the machine running it. */
    private static final Path DATA = Path.of("data");

    private final ScriptedChooser chooser = new ScriptedChooser();

    private final MemoryFiles files = new MemoryFiles();

    private final ParameterSession session = startingIn(C03);

    private final SpectrumInputsViewModel inputs =
            new SpectrumInputsViewModel(session, chooser, files);

    @Test
    @DisplayName("chosen spectra are kept with their status, in order, each once")
    void chooseSpectra() {
        Path one = DATA.resolve("run1.mzML");
        Path two = DATA.resolve("run2.mzML");
        Path gone = DATA.resolve("missing.mgf");
        Path folder = DATA.resolve("folder");
        Path locked = DATA.resolve("locked.mzXML");
        files.kinds.put(one, "readable");
        files.kinds.put(two, "readable");
        files.kinds.put(folder, "directory");
        files.kinds.put(locked, "unreadable");
        chooser.spectra.add(List.of(one, two));
        chooser.spectra.add(List.of(two, gone, folder, locked, gone));
        chooser.spectra.add(List.of());

        assertEquals(2, inputs.chooseSpectra());
        assertEquals(3, inputs.chooseSpectra());
        assertEquals(0, inputs.chooseSpectra());

        assertEquals(List.of(one, two, gone, folder, locked), inputs.spectrumPaths());
        assertEquals(
                List.of(
                        "Found and readable: data/run1.mzML",
                        "Found and readable: data/run2.mzML",
                        "Not found: data/missing.mgf",
                        "A folder, not a file: data/folder",
                        "Found but cannot be read: data/locked.mzXML"),
                inputs.spectra().stream().map(InputFile::text).toList());
        assertEquals(inputs.spectra(), inputs.spectraProperty().get());
        // spectra are run inputs: nothing about them reaches the parameters
        assertEquals("/some/path/db.fasta", session.model().text("database_name"));

        assertTrue(inputs.removeSpectrum(two));
        assertFalse(inputs.removeSpectrum(two));
        assertEquals(List.of(one, gone, folder, locked), inputs.spectrumPaths());
    }

    @Test
    @DisplayName("a refresh reads each status again")
    void refresh() {
        Path later = DATA.resolve("later.mzML");
        chooser.spectra.add(List.of(later));
        inputs.chooseSpectra();
        assertEquals(FileStatus.MISSING, inputs.spectra().get(0).status());
        files.kinds.put(later, "readable");
        files.kinds.put(Path.of(session.model().text("database_name")), "readable");

        inputs.refresh();

        assertEquals(FileStatus.READABLE, inputs.spectra().get(0).status());
        assertEquals(FileStatus.READABLE, inputs.database().status());
    }

    @Test
    @DisplayName("a chosen database is database_name, set through the model, origin USER")
    void chooseDatabase() {
        assertEquals("Not found: /some/path/db.fasta", inputs.database().text());
        Path fasta = DATA.resolve("human.fasta");
        files.kinds.put(fasta, "readable");
        chooser.databases.add(Optional.of(fasta));
        chooser.databases.add(Optional.empty());

        assertEquals(Optional.of(EditOutcome.applied()), inputs.chooseDatabase());

        assertEquals("data/human.fasta", session.model().text("database_name"));
        assertEquals(ValueOrigin.USER, session.model().origin("database_name"));
        assertEquals("Found and readable: data/human.fasta", inputs.database().text());
        assertEquals(inputs.database(), inputs.databaseProperty().get());

        assertEquals(Optional.empty(), inputs.chooseDatabase());
        assertEquals("data/human.fasta", session.model().text("database_name"));
    }

    @Test
    @DisplayName("choosing the database already set reads its status again")
    void chooseTheSameDatabase() {
        Path fasta = DATA.resolve("mouse.fasta");
        chooser.databases.add(Optional.of(fasta));
        chooser.databases.add(Optional.of(fasta));
        inputs.chooseDatabase();
        assertEquals("Not found: data/mouse.fasta", inputs.database().text());
        files.kinds.put(fasta, "readable");

        inputs.chooseDatabase();

        assertEquals("Found and readable: data/mouse.fasta", inputs.database().text());
    }

    @Test
    @DisplayName("the database status follows edits made anywhere, and a reset")
    void databaseFollowsTheModel() {
        Path fasta = DATA.resolve("yeast.fasta");
        files.kinds.put(fasta, "readable");
        session.field("database_name").setText("data/yeast.fasta");
        assertEquals("Found and readable: data/yeast.fasta", inputs.database().text());
        session.field("database_name").reset();
        assertEquals("Not found: /some/path/db.fasta", inputs.database().text());
    }

    @Test
    @DisplayName("a database text the file system cannot use as a path is said to be so")
    void notAPath() {
        session.field("database_name").setText("a\u0000b");
        assertEquals(FileStatus.NOT_A_PATH, inputs.database().status());
        assertEquals("Not a usable path", inputs.database().text());
    }

    @Test
    @DisplayName("an empty database_name is no file chosen")
    void noDatabase() {
        assertEquals(EditOutcome.applied(), session.field("database_name").setText(""));
        assertEquals(FileStatus.NONE, inputs.database().status());
        assertEquals("No file chosen", inputs.database().text());
    }
}
