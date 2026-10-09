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

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javafx.beans.property.SimpleObjectProperty;
import org.cometgui.app.config.ApplicationServices;
import org.cometgui.app.config.ParameterEditorWiring;
import org.cometgui.app.config.RunWiring;
import org.cometgui.app.config.SessionEngine;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.run.RunId;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.results.export.TableExport;
import org.cometgui.results.export.WeightsExport;
import org.cometgui.results.filtering.DisplayFilters;
import org.cometgui.results.filtering.QValueFilter;
import org.cometgui.results.filtering.store.Category;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.results.parser.WeightsSummary;
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
import org.cometgui.ui.viewmodel.params.PercolatorRequest;
import org.cometgui.ui.viewmodel.params.RunNotStartedException;
import org.cometgui.ui.viewmodel.params.RunObserver;
import org.cometgui.ui.viewmodel.params.RunViewModel;
import org.cometgui.ui.viewmodel.params.SpectrumInputsViewModel;
import org.cometgui.ui.viewmodel.params.VariableModsViewModel;
import org.cometgui.ui.viewmodel.percolator.PercolatorRerunViewModel;
import org.cometgui.ui.viewmodel.percolator.PercolatorViewModel;
import org.cometgui.ui.viewmodel.percolator.RerunCheck;
import org.cometgui.ui.viewmodel.results.DisplayFiltersViewModel;
import org.cometgui.ui.viewmodel.results.OpenedResults;
import org.cometgui.ui.viewmodel.results.ResultTableViewModel;
import org.cometgui.ui.viewmodel.results.ResultsPort;
import org.cometgui.ui.viewmodel.results.ResultsRun;
import org.cometgui.ui.viewmodel.results.ResultsViewModel;
import org.cometgui.ui.viewmodel.results.WeightsViewModel;

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
        NoEngine engine = new NoEngine();
        DisplayFiltersViewModel filters = new DisplayFiltersViewModel();
        PercolatorViewModel percolator =
                new PercolatorViewModel(
                        RunWiring.percolator(Optional.empty(), NoEngine.REASON),
                        chooser,
                        Runnable::run,
                        Runnable::run,
                        () -> {},
                        filters);
        ResultTableViewModel table =
                new ResultTableViewModel(filters, Runnable::run, Runnable::run);
        WeightsViewModel weights = new WeightsViewModel();
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
                        new SimpleObjectProperty<>(
                                PercolatorRequest.blocked(List.of(NoEngine.REASON))),
                        engine,
                        Runnable::run,
                        Runnable::run),
                percolator,
                new PercolatorRerunViewModel(
                        engine, percolator.requestProperty(), Runnable::run, Runnable::run),
                new ResultsViewModel(
                        new NoResults(), filters, table, weights, Runnable::run, Runnable::run),
                table,
                weights,
                filters);
    }

    /** The Results port of a shell built for a test that has no project: no run has results. */
    private static final class NoResults implements ResultsPort {

        @Override
        public List<ResultsRun> runs() {
            return List.of();
        }

        @Override
        public OpenedResults open(RunId run) throws IOException {
            throw new IOException(NoEngine.REASON);
        }

        @Override
        public void close(OpenedResults opened) {
            // nothing is ever opened
        }

        @Override
        public void saveViewState(RunId run, DisplayFilters filters) throws IOException {
            throw new IOException(NoEngine.REASON);
        }

        @Override
        public TableExport exportTable(
                RunId run, TableKind kind, QValueFilter filter, Category category)
                throws IOException {
            throw new IOException(NoEngine.REASON);
        }

        @Override
        public WeightsExport exportWeights(RunId run, WeightsSummary weights) throws IOException {
            throw new IOException(NoEngine.REASON);
        }
    }

    /** The engine port of a shell built for a test that runs nothing: it says so. */
    private static final class NoEngine implements SessionEngine {

        /** The engine's reason in such a shell. */
        static final String REASON = "This test shell has no workflow engine.";

        @Override
        public EngineCheck check(
                CometParameters model, List<Path> spectra, PercolatorRequest percolator) {
            return EngineCheck.unavailable(REASON);
        }

        @Override
        public ActiveRun start(
                CometParameters model,
                List<Path> spectra,
                PercolatorRequest percolator,
                RunObserver observer)
                throws RunNotStartedException {
            throw new RunNotStartedException(REASON, null);
        }

        @Override
        public RerunCheck check(PercolatorRequest percolator) {
            return RerunCheck.refused(REASON);
        }

        @Override
        public ActiveRun start(PercolatorRequest percolator, RunObserver observer)
                throws RunNotStartedException {
            throw new RunNotStartedException(REASON, null);
        }

        @Override
        public Set<RunId> executingRuns() {
            return Set.of();
        }
    }
}
