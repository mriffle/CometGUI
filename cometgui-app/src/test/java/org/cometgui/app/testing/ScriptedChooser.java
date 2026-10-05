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

package org.cometgui.app.testing;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import org.cometgui.ui.viewmodel.params.FileChooserPort;

/**
 * The file chooser a GUI test injects (the specification's "file chooser abstraction and its test
 * injection"): it answers from a script, one answer per call, records every question it was asked,
 * and cancels a call nobody scripted.
 *
 * <p>Called on the JavaFX application thread by the application, and scripted from the test thread
 * before the click that asks; every method holds a private lock so the two never see a half-written
 * queue.
 */
public final class ScriptedChooser implements FileChooserPort {

    private final Deque<List<Path>> spectra = new ArrayDeque<>();

    private final Deque<Path> databases = new ArrayDeque<>();

    private final Deque<Path> files = new ArrayDeque<>();

    private final Deque<Path> targets = new ArrayDeque<>();

    private final Deque<Path> parameterFiles = new ArrayDeque<>();

    private final List<String> asked = new ArrayList<>();

    /** A lock of this object's own, so that no caller can hold it. */
    private final Object lock = new Object();

    /**
     * Scripts the next spectrum choice.
     *
     * @param chosen the files the chooser answers
     * @return this chooser
     */
    public ScriptedChooser spectra(Path... chosen) {
        synchronized (lock) {
            spectra.add(List.of(chosen));
            return this;
        }
    }

    /**
     * Scripts the next database choice.
     *
     * @param chosen the file
     * @return this chooser
     */
    public ScriptedChooser database(Path chosen) {
        synchronized (lock) {
            databases.add(chosen);
            return this;
        }
    }

    /**
     * Scripts the next file-parameter choice.
     *
     * @param chosen the file
     * @return this chooser
     */
    public ScriptedChooser file(Path chosen) {
        synchronized (lock) {
            files.add(chosen);
            return this;
        }
    }

    /**
     * Scripts the next save target.
     *
     * @param chosen the path
     * @return this chooser
     */
    public ScriptedChooser saveTo(Path chosen) {
        synchronized (lock) {
            targets.add(chosen);
            return this;
        }
    }

    /**
     * Scripts the next parameter file to import.
     *
     * @param chosen the file
     * @return this chooser
     */
    public ScriptedChooser parameterFile(Path chosen) {
        synchronized (lock) {
            parameterFiles.add(chosen);
            return this;
        }
    }

    /**
     * Every question asked so far, in order: {@code spectra}, {@code database}, {@code
     * file:<what>}, {@code parameter file} or {@code save}.
     *
     * @return a copy of the questions
     */
    public List<String> asked() {
        synchronized (lock) {
            return List.copyOf(asked);
        }
    }

    @Override
    public List<Path> chooseSpectrumFiles() {
        synchronized (lock) {
            asked.add("spectra");
            return spectra.isEmpty() ? List.of() : spectra.removeFirst();
        }
    }

    @Override
    public Optional<Path> chooseDatabase() {
        synchronized (lock) {
            asked.add("database");
            return Optional.ofNullable(databases.pollFirst());
        }
    }

    @Override
    public Optional<Path> chooseFile(String what) {
        synchronized (lock) {
            asked.add("file:" + what);
            return Optional.ofNullable(files.pollFirst());
        }
    }

    @Override
    public Optional<Path> chooseParameterFile() {
        synchronized (lock) {
            asked.add("parameter file");
            return Optional.ofNullable(parameterFiles.pollFirst());
        }
    }

    @Override
    public Optional<Path> chooseSaveTarget() {
        synchronized (lock) {
            asked.add("save");
            return Optional.ofNullable(targets.pollFirst());
        }
    }
}
