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

package org.cometgui.ui.testing;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.ports.FileSystemAccess;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.MetadataLoader;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.ui.viewmodel.StageStepperViewModel;
import org.cometgui.ui.viewmodel.params.ExpertViewModel;
import org.cometgui.ui.viewmodel.params.FileChooserPort;
import org.cometgui.ui.viewmodel.params.ParameterEditorViewModel;
import org.cometgui.ui.viewmodel.params.ParameterSession;
import org.cometgui.ui.viewmodel.params.PercolatorRequest;
import org.cometgui.ui.viewmodel.params.RunViewModel;
import org.cometgui.ui.viewmodel.params.SpectrumInputsViewModel;
import org.cometgui.ui.viewmodel.params.StageSwitches;
import org.cometgui.ui.viewmodel.percolator.PercolatorPort;
import org.cometgui.ui.viewmodel.percolator.PercolatorRerunViewModel;
import org.cometgui.ui.viewmodel.percolator.PercolatorViewModel;
import org.cometgui.ui.viewmodel.percolator.RerunCheck;

/**
 * The parts of a Comet parameter editor for the view tests of this module: the bundled metadata,
 * both offered releases (2026.03.0 first, typed here rather than derived -- the derivation is the
 * composition root's and is tested there), a chooser that answers from a script, and a file system
 * that knows only the paths a test names.
 */
public final class Editors {

    /** The bundled metadata, loaded once. */
    public static final CuratedMetadata METADATA = MetadataLoader.loadBundled();

    /** The build a saved file's header names in these tests. */
    public static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-test", "unknown", Instant.parse("2026-10-05T00:00:00Z"));

    private Editors() {}

    /**
     * A new 2026.03.0 configuration, 2026.02.2 offered too, every stage enabled.
     *
     * @return the session
     */
    public static ParameterSession session() {
        return new ParameterSession(
                METADATA,
                List.of(ToolVersion.parse("2026.03.0"), ToolVersion.parse("2026.02.2")),
                StageSwitches.ALL_ENABLED);
    }

    /**
     * The spectrum inputs over a session.
     *
     * @param session the session
     * @param chooser the chooser
     * @param files the file system
     * @return the inputs
     */
    public static SpectrumInputsViewModel inputs(
            ParameterSession session, ScriptedChooser chooser, KnownFiles files) {
        return new SpectrumInputsViewModel(session, chooser, files);
    }

    /**
     * The editor's state over a session, its inputs and its chooser, with this class's build and a
     * streaming hash service. Its readiness's engine half says the check has not run.
     *
     * @param session the session
     * @param inputs the inputs over the session
     * @param chooser the chooser the inputs use
     * @return the editor
     */
    public static ParameterEditorViewModel editor(
            ParameterSession session, SpectrumInputsViewModel inputs, ScriptedChooser chooser) {
        return new ParameterEditorViewModel(
                session, inputs, chooser, BUILD, new StreamingHashService());
    }

    /**
     * The Run section's engine half over a session, driving a stepper, calling a scripted engine on
     * its own queues.
     *
     * @param session the session
     * @param inputs the inputs over the session
     * @param editor the editor whose readiness the run keeps
     * @param stepper the stepper the run drives
     * @param engine the scripted engine
     * @return the view-model
     */
    public static RunViewModel run(
            ParameterSession session,
            SpectrumInputsViewModel inputs,
            ParameterEditorViewModel editor,
            StageStepperViewModel stepper,
            ScriptedEngine engine) {
        return run(
                session,
                inputs,
                editor,
                stepper,
                new SimpleObjectProperty<>(Percolators.ready()),
                engine);
    }

    /**
     * The Run section's engine half as {@link #run(ParameterSession, SpectrumInputsViewModel,
     * ParameterEditorViewModel, StageStepperViewModel, ScriptedEngine)} makes it, with the
     * Percolator half a test controls.
     *
     * @param session the session
     * @param inputs the inputs over the session
     * @param editor the editor whose readiness the run keeps
     * @param stepper the stepper the run drives
     * @param percolator the Percolator half
     * @param engine the scripted engine
     * @return the view-model
     */
    public static RunViewModel run(
            ParameterSession session,
            SpectrumInputsViewModel inputs,
            ParameterEditorViewModel editor,
            StageStepperViewModel stepper,
            ObservableValue<PercolatorRequest> percolator,
            ScriptedEngine engine) {
        return new RunViewModel(
                session,
                inputs,
                editor.readiness(),
                stepper,
                percolator,
                engine,
                engine.background(),
                engine.ui());
    }

    /**
     * The Percolator section over a port, every call made and applied at once on the caller's
     * thread, registration choosing from an unscripted chooser (which cancels).
     *
     * @param port the port
     * @return the section, not yet read
     */
    public static PercolatorViewModel percolator(PercolatorPort port) {
        return new PercolatorViewModel(
                port, new ScriptedChooser(), Runnable::run, Runnable::run, () -> {});
    }

    /**
     * The rerun action of a section, over a port that refuses every rerun, called at once.
     *
     * @param section the section whose request it follows
     * @return the action
     */
    public static PercolatorRerunViewModel rerun(PercolatorViewModel section) {
        return new PercolatorRerunViewModel(
                new ScriptedRerun(request -> RerunCheck.refused("No run in this test.")),
                section.requestProperty(),
                Runnable::run,
                Runnable::run);
    }

    /**
     * The Expert level's view-model over a session, comparing with the editor's saves.
     *
     * @param session the session
     * @param editor the editor over the session
     * @return the Expert view-model
     */
    public static ExpertViewModel expert(
            ParameterSession session, ParameterEditorViewModel editor) {
        return new ExpertViewModel(session, BUILD, editor.files());
    }

    /** A chooser answering from a script, one answer per call; an unscripted call cancels. */
    public static final class ScriptedChooser implements FileChooserPort {

        private final Deque<List<Path>> spectra = new ArrayDeque<>();

        private final Deque<Path> files = new ArrayDeque<>();

        private final Deque<Path> targets = new ArrayDeque<>();

        private final Deque<Path> parameterFiles = new ArrayDeque<>();

        private final List<String> asked = new ArrayList<>();

        /**
         * What a single-file choice was asked for, oldest first: {@code "file:" + what}.
         *
         * @return the questions
         */
        public List<String> asked() {
            return List.copyOf(asked);
        }

        /**
         * Scripts the next spectrum choice.
         *
         * @param chosen the files
         * @return this chooser
         */
        public ScriptedChooser spectra(Path... chosen) {
            spectra.add(List.of(chosen));
            return this;
        }

        /**
         * Scripts the next single-file choice (database or file parameter).
         *
         * @param chosen the file
         * @return this chooser
         */
        public ScriptedChooser file(Path chosen) {
            files.add(chosen);
            return this;
        }

        /**
         * Scripts the next save target.
         *
         * @param chosen the path
         * @return this chooser
         */
        public ScriptedChooser saveTo(Path chosen) {
            targets.add(chosen);
            return this;
        }

        /**
         * Scripts the next parameter file to import.
         *
         * @param chosen the file
         * @return this chooser
         */
        public ScriptedChooser parameterFile(Path chosen) {
            parameterFiles.add(chosen);
            return this;
        }

        @Override
        public List<Path> chooseSpectrumFiles() {
            return spectra.isEmpty() ? List.of() : spectra.removeFirst();
        }

        @Override
        public Optional<Path> chooseDatabase() {
            return Optional.ofNullable(files.pollFirst());
        }

        @Override
        public Optional<Path> chooseFile(String what) {
            asked.add("file:" + what);
            return Optional.ofNullable(files.pollFirst());
        }

        @Override
        public Optional<Path> chooseParameterFile() {
            return Optional.ofNullable(parameterFiles.pollFirst());
        }

        @Override
        public Optional<Path> chooseSaveTarget() {
            return Optional.ofNullable(targets.pollFirst());
        }
    }

    /** A file system in which exactly the paths a test adds exist, as readable files. */
    public static final class KnownFiles implements FileSystemAccess {

        private final Set<Path> readable = new HashSet<>();

        /**
         * Adds a readable file.
         *
         * @param path the file
         * @return this file system
         */
        public KnownFiles with(Path path) {
            readable.add(path);
            return this;
        }

        @Override
        public boolean exists(Path path) {
            return readable.contains(path);
        }

        @Override
        public boolean isReadable(Path path) {
            return readable.contains(path);
        }

        @Override
        public boolean isDirectory(Path path) {
            return false;
        }

        @Override
        public void createDirectories(Path path) {
            throw new UnsupportedOperationException("the parameter editor creates no directory");
        }

        @Override
        public Path applicationDataDirectory() {
            return Path.of("target", "no-application-data").toAbsolutePath();
        }
    }
}
