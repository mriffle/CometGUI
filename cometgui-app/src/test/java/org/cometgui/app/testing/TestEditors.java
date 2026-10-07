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
import java.time.Instant;
import java.util.List;
import org.cometgui.app.config.ApplicationServices;
import org.cometgui.app.config.ParameterEditorWiring;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.ui.view.ShellView;
import org.cometgui.ui.viewmodel.ConsoleViewModel;
import org.cometgui.ui.viewmodel.HostBaselineViewModel;
import org.cometgui.ui.viewmodel.NavigationViewModel;
import org.cometgui.ui.viewmodel.StageStepperViewModel;
import org.cometgui.ui.viewmodel.ToolManagerViewModel;
import org.cometgui.ui.viewmodel.params.ActiveRun;
import org.cometgui.ui.viewmodel.params.EngineCheck;
import org.cometgui.ui.viewmodel.params.ParameterEditorViewModel;
import org.cometgui.ui.viewmodel.params.ParameterSearchViewModel;
import org.cometgui.ui.viewmodel.params.ParameterSession;
import org.cometgui.ui.viewmodel.params.RunEnginePort;
import org.cometgui.ui.viewmodel.params.RunNotStartedException;
import org.cometgui.ui.viewmodel.params.RunObserver;
import org.cometgui.ui.viewmodel.params.RunViewModel;
import org.cometgui.ui.viewmodel.params.SpectrumInputsViewModel;
import org.cometgui.ui.viewmodel.params.VariableModsViewModel;

/**
 * The shell for a GUI test that builds its own: the Comet parameter editor composed exactly as the
 * application composes it ({@link ParameterEditorWiring}), with a chooser that cancels every
 * question and a build this test names.
 */
public final class TestEditors {

    /** The build a GUI test's saved parameter file names in its header. */
    public static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-05T00:00:00Z"));

    private TestEditors() {}

    /**
     * The shell, with the parameter editor composed exactly as the application composes it.
     *
     * @param navigation the navigation
     * @param hostBaseline the baseline banner
     * @param stepper the stage stepper
     * @param console the console
     * @param toolManager the Tool Manager
     * @return the shell
     */
    public static ShellView shell(
            NavigationViewModel navigation,
            HostBaselineViewModel hostBaseline,
            StageStepperViewModel stepper,
            ConsoleViewModel console,
            ToolManagerViewModel toolManager) {
        ParameterSession session = ParameterEditorWiring.newSession();
        ScriptedChooser chooser = new ScriptedChooser();
        SpectrumInputsViewModel inputs =
                new SpectrumInputsViewModel(
                        session, chooser, ApplicationServices.forThisHost().fileSystem());
        ParameterEditorViewModel editor =
                ParameterEditorWiring.editor(session, inputs, chooser, BUILD);
        return new ShellView(
                navigation,
                hostBaseline,
                stepper,
                console,
                toolManager,
                session,
                editor,
                inputs,
                new VariableModsViewModel(session),
                new ParameterSearchViewModel(session),
                ParameterEditorWiring.expert(session, editor, BUILD),
                new RunViewModel(
                        session,
                        inputs,
                        editor.readiness(),
                        stepper,
                        new NoEngine(),
                        Runnable::run,
                        Runnable::run));
    }

    /** The engine port of a shell built for a test that runs nothing: it says so. */
    private static final class NoEngine implements RunEnginePort {

        /** The engine's reason in such a shell. */
        static final String REASON = "This test shell has no workflow engine.";

        @Override
        public EngineCheck check(CometParameters model, List<Path> spectra) {
            return EngineCheck.unavailable(REASON);
        }

        @Override
        public ActiveRun start(CometParameters model, List<Path> spectra, RunObserver observer)
                throws RunNotStartedException {
            throw new RunNotStartedException(REASON, null);
        }
    }
}
