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

package org.cometgui.app.bootstrap;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.cometgui.app.config.ApplicationServices;
import org.cometgui.app.config.BuildIdentityResource;
import org.cometgui.app.config.FxFileChooser;
import org.cometgui.app.config.ParameterEditorWiring;
import org.cometgui.app.config.ProjectSession;
import org.cometgui.app.config.RunWiring;
import org.cometgui.app.config.SessionEngine;
import org.cometgui.app.config.ToolManagerUnavailableException;
import org.cometgui.app.config.ToolManagerWiring;
import org.cometgui.app.config.derived.AtlantaFxThemes;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.log.LogMessage;
import org.cometgui.domain.log.MessageSeverity;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.platform.HostBaselineReport;
import org.cometgui.domain.platform.HostBaselineVerifier;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.ui.view.ShellView;
import org.cometgui.ui.viewmodel.ConsoleViewModel;
import org.cometgui.ui.viewmodel.HostBaselineViewModel;
import org.cometgui.ui.viewmodel.NavigationViewModel;
import org.cometgui.ui.viewmodel.StageStepperViewModel;
import org.cometgui.ui.viewmodel.ToolManagerViewModel;
import org.cometgui.ui.viewmodel.ToolRowViewModel;
import org.cometgui.ui.viewmodel.params.FileChooserPort;
import org.cometgui.ui.viewmodel.params.ParameterEditorViewModel;
import org.cometgui.ui.viewmodel.params.ParameterSearchViewModel;
import org.cometgui.ui.viewmodel.params.ParameterSession;
import org.cometgui.ui.viewmodel.params.RunViewModel;
import org.cometgui.ui.viewmodel.params.SpectrumInputsViewModel;
import org.cometgui.ui.viewmodel.params.VariableModsViewModel;
import org.cometgui.ui.viewmodel.percolator.PercolatorRerunViewModel;
import org.cometgui.ui.viewmodel.percolator.PercolatorViewModel;
import org.cometgui.ui.viewmodel.results.DisplayFiltersViewModel;
import org.cometgui.ui.viewmodel.results.ResultTableViewModel;
import org.cometgui.ui.viewmodel.results.ResultsViewModel;
import org.cometgui.ui.viewmodel.results.WeightsViewModel;

/**
 * The running application: build the composition root, check the host, build the shell, show the
 * window.
 *
 * <h2>What happens, in order</h2>
 *
 * <ol>
 *   <li>The AtlantaFX theme is applied as the JavaFX user-agent stylesheet. Theming is optional --
 *       {@link AtlantaFxThemes#applyAsUserAgentStylesheet()} returns {@code false} and the
 *       application keeps JavaFX's default look if the library is absent.
 *   <li>{@link HostBaselineVerifier} is run ({@code R-PLAT-01}) and its report is turned into a
 *       {@link HostBaselineViewModel}.
 *   <li>The report is appended to the shared message log, so the console carries the same statement
 *       the banner does and a later provenance record can quote it.
 *   <li>The shell is built with that view-model and the others -- the Tool Manager's, and the Comet
 *       parameter editor's from {@link ParameterEditorWiring} -- put into a {@link Scene}, given to
 *       the primary {@link Stage}, and shown.
 * </ol>
 *
 * <p><strong>The order is the requirement, not an implementation detail.</strong> {@code R-PLAT-01}
 * says the baseline is verified <em>at startup</em> and reported to the user, so the banner is
 * populated before {@link Stage#show()} is called -- never filled in afterwards by a listener,
 * which would leave a window on screen that briefly says nothing about a host that cannot run
 * anything. {@code CometGuiApplicationBaselineTest} asserts this by reading the banner from a
 * listener on the stage's {@code showing} property.
 *
 * <h2>A blocking outcome is reported, and does not exit the JVM</h2>
 *
 * <p>{@link org.cometgui.domain.platform.HostBaselineOutcome#NOT_64_BIT} and {@link
 * org.cometgui.domain.platform.HostBaselineOutcome#GLIBC_TOO_OLD} are blocking: no managed tool can
 * run on such a host. <strong>This phase reports them and starts anyway.</strong> There is no
 * {@code Platform.exit()} and no {@code System.exit()} on this path, deliberately: a user whose
 * machine is unsupported should be able to read the diagnostic, look at the tool manager, and copy
 * the message -- not watch a window vanish. The phase that owns running a workflow owns refusing to
 * start one, which is where a blocking outcome has to bite.
 *
 * <p>Blocking and warning are distinguishable in <em>text</em>, not by colour alone: {@link
 * HostBaselineViewModel#bannerText()} begins with {@code "Cannot continue: "} for a blocking
 * outcome and {@code "Warning: "} for a warning, and the shell shows no banner at all for a
 * supported host. That is the specification's accessibility principle applied -- a screen reader
 * that never sees a border still reads the severity.
 *
 * <h2>The glibc floor checked at startup is the lowest one in the product</h2>
 *
 * <p>{@link HostBaselineVerifier} takes the required glibc version as an argument and its
 * documentation forbids it to grow a hard-coded floor, because the trustworthy statement about a
 * particular binary comes from executing it ({@code R-PLAT-02}, phase 05's runtime probe). At
 * startup no tool has been selected yet, so the only question that can honestly be asked is whether
 * this host is below the floor of <em>everything the product could ever offer</em>. That is {@link
 * #STARTUP_GLIBC_FLOOR}: {@code 2.14}, which specification.rst records as the requirement of
 * Percolator 3.06.5's portable Linux build and "the lowest floor found anywhere". A host below it
 * can load no managed tool at all. <strong>It is not a per-tool requirement and must never be used
 * as one</strong> -- a host that passes this check may still be too old for the tools the user goes
 * on to choose, and phase 05's probe is what settles that.
 *
 * <h2>No logic beyond wiring</h2>
 *
 * <p>There is no scientific logic here, no hashing, no download, no parsing and no {@code
 * ProcessBuilder}. Everything this class does is choose implementations and connect them, which is
 * what makes the shell testable without it.
 */
public final class CometGuiApplication extends Application {

    /** The window title, stable because tests and later phases identify the window by it. */
    public static final String WINDOW_TITLE = "CometGUI";

    /** The window's initial width in pixels. */
    public static final double INITIAL_WIDTH = 1280;

    /** The window's initial height in pixels. */
    public static final double INITIAL_HEIGHT = 800;

    /**
     * The glibc floor checked at startup: {@code 2.14}, the lowest requirement of any binary the
     * product attempts to manage. See this class's documentation for why it is this number and why
     * it is not a per-tool requirement.
     */
    public static final GlibcVersion STARTUP_GLIBC_FLOOR = GlibcVersion.of(2, 14, 0);

    /** The theme a started application applies. */
    public static final AtlantaFxThemes THEME = AtlantaFxThemes.defaultTheme();

    private final ApplicationServices services;

    private final BoundedMessageLog messageLog;

    private final Supplier<BuildIdentity> build;

    private final Function<Supplier<Window>, FileChooserPort> choosers;

    private final RunWiring.Setup runSetup;

    /** The session's project, locked while the application runs; set by {@link #start}. */
    private ProjectSession project;

    /** Where the Run section calls the engine; set by {@link #start}. */
    private ExecutorService runThreads;

    /** Where the Results section reads its stores, one task at a time; set by {@link #start}. */
    private ExecutorService resultThread;

    /** The Results section, whose open stores are closed when the application stops. */
    private ResultsViewModel results;

    /**
     * The constructor JavaFX itself calls: the real services for this host and a fresh run message
     * log.
     *
     * <p>{@link Application} is instantiated reflectively by the JavaFX launcher, which requires a
     * public no-argument constructor. {@link ApplicationServices#forThisHost()} does no I/O, so
     * constructing this class does none either.
     */
    public CometGuiApplication() {
        this(ApplicationServices.forThisHost(), new BoundedMessageLog());
    }

    /**
     * The application over a given composition root and a given run message log.
     *
     * <p>This is how a test starts the real application against a host it chose -- a 32-bit
     * machine, a musl one -- none of which exists in this project's environment, and how it reads
     * back what startup wrote to the console's log. Both are genuine parameters rather than holes
     * opened for a test: the composition root is exactly the thing that is meant to vary, and the
     * log is a collaborator shared with whatever writes to it (phase 03's process service, when it
     * arrives), which is why it is injected here rather than published from {@link
     * ApplicationServices}.
     *
     * @param services the wiring to run with
     * @param messageLog the bounded log the console shows
     * @throws NullPointerException if either argument is {@code null}
     */
    public CometGuiApplication(ApplicationServices services, BoundedMessageLog messageLog) {
        this(services, messageLog, BuildIdentityResource::load, FxFileChooser::new);
    }

    /**
     * The application over a given composition root, run message log, build identity and file
     * chooser.
     *
     * <p>The parameter editor's two seams a GUI test needs (Phase 07): the build the canonical
     * {@code comet.params} header names, and the file chooser -- the specification's "file chooser
     * abstraction and its test injection". Production passes {@link BuildIdentityResource#load()},
     * read when the window is built, and {@link FxFileChooser} over the application window; a test
     * passes a build it names and a chooser that answers from a script.
     *
     * @param services the wiring to run with
     * @param messageLog the bounded log the console shows
     * @param build the running build, asked for once when the window is built
     * @param choosers makes the parameter editor's file chooser, given the window it is modal over
     * @throws NullPointerException if any argument is {@code null}
     */
    public CometGuiApplication(
            ApplicationServices services,
            BoundedMessageLog messageLog,
            Supplier<BuildIdentity> build,
            Function<Supplier<Window>, FileChooserPort> choosers) {
        this(
                services,
                messageLog,
                build,
                choosers,
                RunWiring.Setup.forThisApplication(Objects.requireNonNull(services, "services")));
    }

    /**
     * The application over a given composition root, run message log, build identity, file chooser
     * and run setup.
     *
     * <p>The Run section's seams (Phase 08): where the Tool Manager -- and so the selected Comet --
     * comes from, and where the session's project lives. Production passes {@link
     * RunWiring.Setup#forThisApplication}: this machine's Tool Manager and the default project
     * under the application data directory. A GUI test passes a Tool Manager that offers a staged
     * Comet and a project in a temporary directory.
     *
     * @param services the wiring to run with
     * @param messageLog the bounded log the console shows, which tool output is appended to
     * @param build the running build, asked for once when the window is built
     * @param choosers makes the parameter editor's file chooser, given the window it is modal over
     * @param runSetup the Tool Manager and the project the Run section uses
     * @throws NullPointerException if any argument is {@code null}
     */
    public CometGuiApplication(
            ApplicationServices services,
            BoundedMessageLog messageLog,
            Supplier<BuildIdentity> build,
            Function<Supplier<Window>, FileChooserPort> choosers,
            RunWiring.Setup runSetup) {
        this.services = Objects.requireNonNull(services, "services");
        this.messageLog = Objects.requireNonNull(messageLog, "messageLog");
        this.build = Objects.requireNonNull(build, "build");
        this.choosers = Objects.requireNonNull(choosers, "choosers");
        this.runSetup = Objects.requireNonNull(runSetup, "runSetup");
    }

    /**
     * Builds and shows the application window.
     *
     * <p>Called by the JavaFX launcher on the application thread. See this class's documentation
     * for the order of the four steps and why it is the order.
     *
     * @param primaryStage the stage JavaFX created for this application
     * @throws NullPointerException if {@code primaryStage} is {@code null}
     */
    @Override
    public void start(Stage primaryStage) {
        Objects.requireNonNull(primaryStage, "primaryStage");

        THEME.applyAsUserAgentStylesheet();

        HostBaselineReport baseline =
                new HostBaselineVerifier(services.environment(), services.glibcVersions())
                        .verify(STARTUP_GLIBC_FLOOR);
        HostBaselineViewModel hostBaseline = new HostBaselineViewModel(baseline);
        recordBaseline(hostBaseline);

        Optional<ToolManager> tools = Optional.empty();
        String toolsUnavailable = "";
        try {
            tools = Optional.of(runSetup.tools().create());
        } catch (ToolManagerUnavailableException unavailable) {
            toolsUnavailable = unavailable.getMessage();
        }
        /*
         * One chooser over the application window, made before the Tool Manager because the Tool
         * Manager is where a scientist with no managed build of a tool registers their own
         * (D-011), and the parameter editor below uses the same one.  A registration runs the
         * chosen binary, so it runs on an install thread and never on this one.
         */
        FileChooserPort chooser = choosers.apply(() -> primaryStage);
        ToolManagerViewModel toolManager =
                tools.isPresent()
                        ? new ToolManagerViewModel(
                                tools.get(),
                                Platform::runLater,
                                ToolManagerWiring.installThreads(),
                                chooser)
                        : ToolManagerViewModel.unavailable(toolsUnavailable, Platform::runLater);

        /*
         * The parameter editor's collaborators are made here and injected into the views, never
         * handed out by a holder: one session, the spectrum inputs, the variable-modification
         * editor, the parameter search and the Expert level over it, and the editor's state.
         */
        ParameterSession parameterSession = ParameterEditorWiring.newSession();
        SpectrumInputsViewModel spectrumInputs =
                new SpectrumInputsViewModel(parameterSession, chooser, services.fileSystem());
        BuildIdentity running = build.get();
        ParameterEditorViewModel parameterEditor =
                ParameterEditorWiring.editor(parameterSession, spectrumInputs, chooser, running);

        /*
         * The Run section (phase 08): the workflow engine behind a port, over the one process
         * service, the console's log (as a method reference), the Tool Manager and the session's
         * project. The engine is called on daemon threads and every answer is applied on this
         * thread; the stepper is the one the shell draws.
         */
        StageStepperViewModel stepper = new StageStepperViewModel();
        project =
                new ProjectSession(
                        runSetup.projectDirectory(), services.clock(), services.runIds());
        runThreads = RunWiring.backgroundThreads();
        CachingHashService hashes = RunWiring.hasher();
        SessionEngine engine =
                RunWiring.port(
                        services, messageLog, tools, toolsUnavailable, project, running, hashes);

        /*
         * THE ONE DISPLAY-FILTER STATE (design decision P10-1): built here once and given to both
         * the Percolator section and the Results section, so a filter changed in one is the
         * filter the other shows.
         */
        DisplayFiltersViewModel displayFilters = new DisplayFiltersViewModel();

        /*
         * The Percolator section (phase 09): the Tool Manager's Percolator builds behind a port,
         * read on the Run section's daemon threads and applied on this one. Its request is the
         * Percolator half every Run check and run carries, and its rerun action works over the
         * same session engine -- the same project, hasher and last run. A registration refreshes
         * the Tool Manager's own list too.
         */
        PercolatorViewModel percolator =
                new PercolatorViewModel(
                        RunWiring.percolator(tools, toolsUnavailable),
                        chooser,
                        runThreads,
                        Platform::runLater,
                        toolManager::refresh,
                        displayFilters);
        PercolatorRerunViewModel percolatorRerun =
                new PercolatorRerunViewModel(
                        engine, percolator.requestProperty(), runThreads, Platform::runLater);
        RunViewModel run =
                new RunViewModel(
                        parameterSession,
                        spectrumInputs,
                        parameterEditor.readiness(),
                        stepper,
                        percolator.requestProperty(),
                        engine,
                        runThreads,
                        Platform::runLater);

        /*
         * The Results section (phase 10): the session project's runs with Percolator results,
         * read through a port that holds the project, the one hasher and the build -- and no
         * process service, so that a filter change cannot launch anything (gate item 2). Every
         * store is read on one daemon thread, in order, so a page is never asked of a store that
         * a newer choice is closing; every answer is applied on this thread.
         */
        resultThread = RunWiring.resultThread();
        ResultTableViewModel resultTable =
                new ResultTableViewModel(displayFilters, resultThread, Platform::runLater);
        WeightsViewModel weights = new WeightsViewModel();
        results =
                new ResultsViewModel(
                        RunWiring.results(services, project, hashes, running, engine),
                        displayFilters,
                        resultTable,
                        weights,
                        resultThread,
                        Platform::runLater);

        ShellView shell =
                new ShellView(
                        new NavigationViewModel(),
                        hostBaseline,
                        stepper,
                        new ConsoleViewModel(messageLog),
                        toolManager,
                        parameterSession,
                        parameterEditor,
                        spectrumInputs,
                        new VariableModsViewModel(parameterSession),
                        new ParameterSearchViewModel(parameterSession),
                        ParameterEditorWiring.expert(parameterSession, parameterEditor, running),
                        run,
                        percolator,
                        percolatorRerun,
                        results,
                        resultTable,
                        weights,
                        displayFilters);

        /*
         * READ AFTER THE SHELL IS BUILT, NOT INSIDE IT.  Asking the port for the offered builds
         * verifies every installed tool against the checksums its completion marker records, so it
         * is work rather than construction; doing it here means the window exists first and the
         * Tool Manager's own view is already listening when the rows arrive.  This call is also
         * what makes the section show real data at all -- a view-model nobody refreshes holds no
         * rows, deliberately.
         */
        toolManager.refresh();

        /*
         * The pre-run check reads which Comet is installed, and the Percolator section which
         * Percolator builds there are, so both read again whenever the Tool Manager's rows are read
         * again -- after an install or a registration -- and once now. A Percolator change checks
         * Run again by itself. When a run ends, the rerun action asks again whether Percolator can
         * be rerun from it.
         */
        toolManager
                .rows()
                .addListener(
                        (ListChangeListener<ToolRowViewModel>)
                                change -> {
                                    run.recheck();
                                    percolator.refresh();
                                });
        run.runningProperty()
                .addListener(
                        (observable, before, after) -> {
                            if (!after) {
                                percolatorRerun.refresh();
                            }
                        });
        /*
         * The Results section lists the runs with results, and whether each is executing, as of
         * its last read: so it reads again now, and whenever a run or a Percolator rerun starts or
         * ends.
         */
        run.runningProperty().addListener((observable, before, after) -> results.refresh());
        percolatorRerun
                .runningProperty()
                .addListener((observable, before, after) -> results.refresh());
        percolator.refresh();
        run.recheck();
        percolatorRerun.refresh();
        results.refresh();

        primaryStage.setTitle(WINDOW_TITLE);
        primaryStage.setScene(new Scene(shell, INITIAL_WIDTH, INITIAL_HEIGHT));
        primaryStage.show();
    }

    /**
     * Closes the Results section's stores, releases the session's project lock and stops the Run
     * section's threads.
     *
     * <p>Called by JavaFX when the application ends. A run still in progress is not waited for: its
     * threads are daemons, and its tools are the process service's to end.
     *
     * @throws IOException if the project lock cannot be released
     */
    @Override
    public void stop() throws IOException {
        if (results != null) {
            // closes the open run's stores, on the results thread, after anything it is reading
            results.close();
        }
        if (resultThread != null) {
            resultThread.shutdown();
        }
        if (runThreads != null) {
            runThreads.shutdownNow();
        }
        if (project != null) {
            project.close();
        }
    }

    /**
     * Puts the baseline statement into the shared message log, at a severity that matches the
     * banner: an error for a blocking outcome, a warning for a warning, information for a host that
     * is fine.
     *
     * @param hostBaseline the view-model built from the report
     */
    private void recordBaseline(HostBaselineViewModel hostBaseline) {
        MessageSeverity severity;
        if (hostBaseline.blocking()) {
            severity = MessageSeverity.ERROR;
        } else if (hostBaseline.bannerVisible()) {
            severity = MessageSeverity.WARNING;
        } else {
            severity = MessageSeverity.INFO;
        }
        messageLog.append(
                LogMessage.recordedBy(services.clock(), null, severity, hostBaseline.bannerText()));
    }
}
