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

package org.cometgui.ui.viewmodel.visualisation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.tools.InstallHandle;
import org.cometgui.domain.tools.InstallPhase;
import org.cometgui.domain.tools.InstallProgress;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.ui.viewmodel.NonNullProperty;
import org.cometgui.ui.viewmodel.ToolRowViewModel;
import org.cometgui.ui.viewmodel.results.ResultRowView;
import org.cometgui.ui.viewmodel.results.ResultTableViewModel;
import org.cometgui.ui.viewmodel.results.ResultsRun;
import org.cometgui.ui.viewmodel.results.ResultsViewModel;
import org.cometgui.workflow.pdv.DrawnSpectrum;
import org.cometgui.workflow.pdv.PdvSelection;
import org.cometgui.workflow.pdv.PdvStatus;
import org.cometgui.workflow.pdv.PdvWindowStatus;

/**
 * The Visualisation section (phase 11): PDV's state, the run and PSM selected in the Results
 * section, drawing that PSM's annotated spectrum with PDV, and PDV's live window -- the run opened
 * in PDV's {@code denovo-gui} mode, the PSM selected in the Results section shown in it.
 *
 * <h2>One selection</h2>
 *
 * <p>The run is the Results section's {@link ResultsViewModel#chosenRunProperty()} and the PSM is
 * its table's {@link ResultTableViewModel#anchorRowProperty()} -- the row most recently selected
 * there. This section keeps no selection of its own; choosing another run or row there is choosing
 * it here.
 *
 * <h2>Install on first use</h2>
 *
 * <p>{@link #draw()} and {@link #openInPdv()} with PDV not installed start the install through the
 * port -- the Tool Manager's -- show its progress, offer {@link #cancelInstall()}, and draw or open
 * when it is done. The install runs on the Tool Manager's own threads and the drawing and opening
 * on the {@code background} executor; nothing here waits for either, and nothing in the Run section
 * asks for PDV.
 *
 * <h2>The live window</h2>
 *
 * <p>{@link #openInPdv()} opens the Results section's run in PDV's window; {@link
 * #windowTextProperty()} follows it -- starting, ready on its port, failed with the reason, closed.
 * While it is open, every PSM selected in the Results section's table is sent to it ({@link
 * VisualisationPort#showInPdv}, debounced by the controller behind the port), from the target or
 * the decoy table as shown there, and {@link #selectionTextProperty()} says what PDV answered.
 *
 * <p><b>The result set closes</b> -- and the window with it -- when the Results section stops
 * showing that run: another run is chosen there, or none. {@link #closePdv()} closes it at once,
 * and the composition root calls {@link #closeAllWindows()} when the application exits.
 *
 * <h2>What is refused, and said</h2>
 *
 * <p>{@link #drawRefusalProperty()} is empty exactly when {@link #draw()} would start: a run open,
 * a PSM table shown, a PSM selected, the run not executing (the workflow engine holds its event
 * log, where every PDV launch is recorded), PDV installed or installable, and nothing already being
 * installed or drawn. {@link #openRefusalProperty()} is the same for {@link #openInPdv()}: a run
 * open and finished, PDV installed or installable, nothing being installed or opened. Anything the
 * port itself refuses is said in its own words.
 *
 * <h2>Two threads</h2>
 *
 * <p>The port is called only on {@code background}; every answer, every install report and every
 * window state is applied on {@code ui}.
 */
public final class VisualisationViewModel {

    /** PDV's state before it has been read. */
    public static final String NOT_READ = "PDV's state has not been read yet.";

    /** The run text when no run is open. */
    public static final String NO_RUN = "No run's results are open in the Results section.";

    /** The PSM text when none is selected. */
    public static final String NO_PSM = "No PSM is selected in the Results section.";

    /** The PSM text when a peptide table is shown. */
    public static final String PEPTIDE_TABLE =
            "The Results section shows a peptide table; select a PSM in a PSM table to draw its"
                    + " spectrum.";

    /** The outcome before anything was drawn. */
    public static final String NOTHING_DRAWN = "No spectrum has been drawn yet.";

    /** The outcome of a cancelled install. */
    public static final String INSTALL_CANCELLED =
            "The PDV install was cancelled, so nothing was drawn.";

    /** The outcome of a failed install. */
    public static final String INSTALL_FAILED =
            "PDV could not be installed, so nothing was drawn. The Tool Manager section shows the"
                    + " attempt.";

    /** The window text before any window was opened. */
    public static final String NO_WINDOW =
            "PDV's window is not open. Open a finished run in PDV to show the PSM selected in the"
                    + " Results section there.";

    /** The window text of a cancelled install. */
    public static final String WINDOW_INSTALL_CANCELLED =
            "The PDV install was cancelled, so PDV was not opened.";

    /** The window text of a failed install. */
    public static final String WINDOW_INSTALL_FAILED =
            "PDV could not be installed, so it was not opened. The Tool Manager section shows the"
                    + " attempt.";

    /** The selection text before any PSM was shown in PDV. */
    public static final String NOTHING_SHOWN = "No PSM has been shown in PDV's window yet.";

    private final VisualisationPort port;

    private final ResultsViewModel results;

    private final ResultTableViewModel table;

    private final Executor background;

    private final Executor ui;

    private final Runnable installEnded;

    // ---- state, on the interface thread ----

    private PdvStatus status;

    private long statusGeneration;

    private InstallHandle install;

    /** Whether an install this section started has not yet reported its end. */
    private boolean installActive;

    private boolean drawing;

    /** Whether a window is being opened: its mzTab prepared and PDV started. */
    private boolean opening;

    /** The run whose window this section opened last; null when none. */
    private RunId windowRun;

    /** That window's last state; null while it is being opened. */
    private PdvWindowStatus windowStatus;

    /** Changes whenever the window this section follows changes; stale answers are dropped. */
    private long windowGeneration;

    // ---- published ----

    private final NonNullProperty<String> pdvText;

    private final ReadOnlyBooleanWrapper installing;

    private final ReadOnlyDoubleWrapper installProgress;

    private final NonNullProperty<String> runText;

    private final NonNullProperty<String> psmText;

    private final NonNullProperty<String> drawRefusal;

    private final ReadOnlyBooleanWrapper drawEnabled;

    private final NonNullProperty<String> outcome;

    private final NonNullProperty<Optional<Path>> figure;

    private final NonNullProperty<String> windowText;

    private final NonNullProperty<String> openRefusal;

    private final ReadOnlyBooleanWrapper openEnabled;

    private final ReadOnlyBooleanWrapper closeEnabled;

    private final NonNullProperty<String> selectionText;

    /** What an install this section started does when it ends. */
    private interface AfterInstall {

        /** The port refused to start the install. */
        void refused(String why);

        /** The install started. */
        void started(ToolVersion version);

        /** The install finished; PDV's state is being read again. */
        void done();

        /** PDV's state, read after the install finished. */
        void installed(PdvStatus read);

        /** The install was cancelled or failed. */
        void ended(InstallPhase phase);
    }

    /**
     * The section, with PDV's state not yet read. Nothing is read until {@link #refresh()}.
     *
     * @param port the application's PDV
     * @param results the Results section, whose open run this section acts on
     * @param table the Results section's table, whose selected row this section acts on
     * @param background where the port is called: never the interface thread
     * @param ui where every answer is applied: the interface thread
     * @param installEnded called on the interface thread when an install this section started has
     *     ended, however it ended -- the composition root reads the Tool Manager again
     */
    public VisualisationViewModel(
            VisualisationPort port,
            ResultsViewModel results,
            ResultTableViewModel table,
            Executor background,
            Executor ui,
            Runnable installEnded) {
        this.port = Objects.requireNonNull(port, "port");
        this.results = Objects.requireNonNull(results, "results");
        this.table = Objects.requireNonNull(table, "table");
        this.background = Objects.requireNonNull(background, "background");
        this.ui = Objects.requireNonNull(ui, "ui");
        this.installEnded = Objects.requireNonNull(installEnded, "installEnded");
        pdvText = new NonNullProperty<>(this, "pdvText", NOT_READ);
        installing = new ReadOnlyBooleanWrapper(this, "installing", false);
        installProgress = new ReadOnlyDoubleWrapper(this, "installProgress", 0);
        runText = new NonNullProperty<>(this, "runText", NO_RUN);
        psmText = new NonNullProperty<>(this, "psmText", NO_PSM);
        drawRefusal = new NonNullProperty<>(this, "drawRefusal", NOT_READ);
        drawEnabled = new ReadOnlyBooleanWrapper(this, "drawEnabled", false);
        outcome = new NonNullProperty<>(this, "outcome", NOTHING_DRAWN);
        figure = new NonNullProperty<>(this, "figure", Optional.empty());
        windowText = new NonNullProperty<>(this, "windowText", NO_WINDOW);
        openRefusal = new NonNullProperty<>(this, "openRefusal", NOT_READ);
        openEnabled = new ReadOnlyBooleanWrapper(this, "openEnabled", false);
        closeEnabled = new ReadOnlyBooleanWrapper(this, "closeEnabled", false);
        selectionText = new NonNullProperty<>(this, "selectionText", NOTHING_SHOWN);
        results.chosenRunProperty()
                .addListener(
                        (observable, before, after) -> {
                            runChanged(after);
                            publish();
                        });
        // a table change is not a selection: until the new table's rows are read, the anchor is
        // still a row of the table shown before, which must not be sent as one of this table's
        results.chosenTableProperty().addListener((observable, before, after) -> publish());
        table.anchorRowProperty()
                .addListener(
                        (observable, before, after) -> {
                            publish();
                            showSelected();
                        });
        publish();
    }

    // =========================================================================== actions ====

    /**
     * Reads PDV's state again, on the background executor. The composition root calls this at start
     * and whenever the Tool Manager's rows are read again. While an install this section started is
     * running, its progress is shown instead and the answer is kept for when it ends.
     */
    public void refresh() {
        statusGeneration++;
        long mine = statusGeneration;
        background.execute(
                () -> {
                    PdvStatus answer = readStatus();
                    ui.execute(() -> statusRead(mine, answer));
                });
    }

    /**
     * Draws the selected PSM's annotated spectrum: at once when PDV is installed, after installing
     * it when it is not. Refused, with the reason in {@link #outcomeProperty()}, when {@link
     * #drawRefusalProperty()} is not empty.
     *
     * @return {@code true} if an install or a drawing started
     */
    public boolean draw() {
        String refusal = drawRefusal.get();
        if (!refusal.isEmpty()) {
            outcome.set(refusal);
            return false;
        }
        ResultsRun run = results.chosenRunProperty().get().orElseThrow();
        String psmId = table.anchorRowProperty().get().orElseThrow().psmId();
        if (status.state() == PdvStatus.State.INSTALLED) {
            drawNow(run, psmId);
            return true;
        }
        return install(
                new AfterInstall() {
                    @Override
                    public void refused(String why) {
                        outcome.set("PDV could not be installed, so nothing was drawn: " + why);
                    }

                    @Override
                    public void started(ToolVersion version) {
                        outcome.set(
                                "PDV "
                                        + version.text()
                                        + " is being installed; the spectrum of PSM "
                                        + psmId
                                        + " is drawn when it has finished.");
                    }

                    @Override
                    public void done() {
                        drawing = true;
                        outcome.set("PDV is installed; drawing the spectrum of PSM " + psmId + ".");
                    }

                    @Override
                    public void installed(PdvStatus read) {
                        drawing = false;
                        if (read.state() != PdvStatus.State.INSTALLED) {
                            outcome.set("Nothing was drawn: " + read.explanation());
                            publish();
                            return;
                        }
                        drawNow(run, psmId);
                    }

                    @Override
                    public void ended(InstallPhase phase) {
                        outcome.set(
                                phase == InstallPhase.CANCELLED
                                        ? INSTALL_CANCELLED
                                        : INSTALL_FAILED);
                    }
                });
    }

    /**
     * Opens the Results section's run in PDV's window: at once when PDV is installed, after
     * installing it when it is not. Refused, with the reason in {@link #windowTextProperty()}, when
     * {@link #openRefusalProperty()} is not empty. A window already open for the run is closed and
     * opened again.
     *
     * @return {@code true} if an install or an opening started
     */
    public boolean openInPdv() {
        String refusal = openRefusal.get();
        if (!refusal.isEmpty()) {
            windowText.set(refusal);
            return false;
        }
        ResultsRun run = results.chosenRunProperty().get().orElseThrow();
        if (status.state() == PdvStatus.State.INSTALLED) {
            openNow(run);
            return true;
        }
        return install(
                new AfterInstall() {
                    @Override
                    public void refused(String why) {
                        windowText.set("PDV could not be installed, so it was not opened: " + why);
                    }

                    @Override
                    public void started(ToolVersion version) {
                        windowText.set(
                                "PDV "
                                        + version.text()
                                        + " is being installed; run "
                                        + run.id().value()
                                        + " is opened in PDV when it has finished.");
                    }

                    @Override
                    public void done() {
                        opening = true;
                        windowText.set(
                                "PDV is installed; opening run " + run.id().value() + " in PDV.");
                    }

                    @Override
                    public void installed(PdvStatus read) {
                        opening = false;
                        if (read.state() != PdvStatus.State.INSTALLED) {
                            windowText.set("PDV was not opened: " + read.explanation());
                            publish();
                            return;
                        }
                        openNow(run);
                    }

                    @Override
                    public void ended(InstallPhase phase) {
                        windowText.set(
                                phase == InstallPhase.CANCELLED
                                        ? WINDOW_INSTALL_CANCELLED
                                        : WINDOW_INSTALL_FAILED);
                    }
                });
    }

    /**
     * Closes the window this section opened, if it is open. Its state follows when it is closed.
     */
    public void closePdv() {
        RunId open = windowRun;
        if (open == null || windowStatus == null || windowStatus.phase().isFinal()) {
            return;
        }
        background.execute(() -> port.closePdv(open));
    }

    /**
     * Closes every PDV window this application opened, waiting within the controller's bound: what
     * the composition root calls when the application exits. Called on the calling thread, because
     * the background threads are being stopped too.
     */
    public void closeAllWindows() {
        windowGeneration++;
        port.closeAllPdv();
    }

    /** Asks the install this section started to stop. Nothing is drawn or opened then. */
    public void cancelInstall() {
        if (installActive && install != null) {
            install.cancel();
        }
    }

    // ========================================================================= published ====

    /**
     * PDV's state, or the running install's progress, in words.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> pdvTextProperty() {
        return pdvText.getReadOnlyProperty();
    }

    /**
     * Whether an install this section started is running, so that it can be cancelled.
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty installingProperty() {
        return installing.getReadOnlyProperty();
    }

    /**
     * The install's progress: 0 to 1 while the total is known, -1 while it is not, 0 when nothing
     * is being installed.
     *
     * @return the read-only property
     */
    public ReadOnlyDoubleProperty installProgressProperty() {
        return installProgress.getReadOnlyProperty();
    }

    /**
     * The run open in the Results section.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> runTextProperty() {
        return runText.getReadOnlyProperty();
    }

    /**
     * The PSM selected in the Results section.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> psmTextProperty() {
        return psmText.getReadOnlyProperty();
    }

    /**
     * Why the spectrum cannot be drawn now.
     *
     * @return the read-only property; empty text when it can
     */
    public ReadOnlyObjectProperty<String> drawRefusalProperty() {
        return drawRefusal.getReadOnlyProperty();
    }

    /**
     * Whether the spectrum can be drawn now.
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty drawEnabledProperty() {
        return drawEnabled.getReadOnlyProperty();
    }

    /**
     * What the last action did: the figure drawn, the install's end, or why nothing was drawn.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> outcomeProperty() {
        return outcome.getReadOnlyProperty();
    }

    /**
     * The last figure drawn, a PNG PDV wrote under the run's {@code pdv/} directory.
     *
     * @return the read-only property; empty until one is drawn, and while the next is drawn
     */
    public ReadOnlyObjectProperty<Optional<Path>> figureProperty() {
        return figure.getReadOnlyProperty();
    }

    /**
     * PDV's window in words: not open, being opened, starting, ready on its port, failed with why,
     * closed.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> windowTextProperty() {
        return windowText.getReadOnlyProperty();
    }

    /**
     * Why the run cannot be opened in PDV now.
     *
     * @return the read-only property; empty text when it can
     */
    public ReadOnlyObjectProperty<String> openRefusalProperty() {
        return openRefusal.getReadOnlyProperty();
    }

    /**
     * Whether the run can be opened in PDV now.
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty openEnabledProperty() {
        return openEnabled.getReadOnlyProperty();
    }

    /**
     * Whether a window this section opened is starting or ready, so that it can be closed.
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty closeEnabledProperty() {
        return closeEnabled.getReadOnlyProperty();
    }

    /**
     * What became of the last PSM shown in PDV's window, PDV's answer included.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> selectionTextProperty() {
        return selectionText.getReadOnlyProperty();
    }

    // ========================================================================= internals ====

    private PdvStatus readStatus() {
        try {
            return port.pdv();
        } catch (IOException | RuntimeException failed) {
            return PdvStatus.unavailable("PDV's state could not be read: " + describe(failed));
        }
    }

    private void statusRead(long answered, PdvStatus read) {
        if (answered != statusGeneration) {
            return;
        }
        status = read;
        if (!installActive) {
            pdvText.set(read.explanation());
        }
        publish();
    }

    /** Starts installing the version PDV's status names; {@code false} if the port refused. */
    private boolean install(AfterInstall after) {
        ToolVersion version = status.version().orElseThrow();
        installActive = true;
        installing.set(true);
        installProgress.set(-1);
        pdvText.set("Installing PDV " + version.text() + ": starting.");
        try {
            install = port.install(version, report -> ui.execute(() -> progressed(report, after)));
        } catch (IOException | RuntimeException refused) {
            installActive = false;
            installing.set(false);
            installProgress.set(0);
            pdvText.set(status.explanation());
            after.refused(describe(refused));
            publish();
            return false;
        }
        after.started(version);
        publish();
        return true;
    }

    private void progressed(InstallProgress report, AfterInstall after) {
        if (!installActive) {
            return;
        }
        if (report.hasKnownTotal() && report.totalBytes() > 0) {
            installProgress.set((double) report.bytesTransferred() / report.totalBytes());
        } else {
            installProgress.set(-1);
        }
        pdvText.set(
                "Installing PDV "
                        + report.version().text()
                        + ": "
                        + ToolRowViewModel.progressTextFor(report)
                        + ".");
        if (!report.phase().isTerminal()) {
            return;
        }
        installActive = false;
        install = null;
        installing.set(false);
        installProgress.set(0);
        installEnded.run();
        if (report.phase() == InstallPhase.DONE) {
            after.done();
            publish();
            background.execute(
                    () -> {
                        PdvStatus read = readStatus();
                        ui.execute(() -> installedThen(read, after));
                    });
            return;
        }
        after.ended(report.phase());
        refresh();
        publish();
    }

    private void installedThen(PdvStatus read, AfterInstall after) {
        statusGeneration++;
        status = read;
        pdvText.set(read.explanation());
        after.installed(read);
    }

    private void drawNow(ResultsRun run, String psmId) {
        drawing = true;
        // the figure shown is the last drawing's; a new drawing replaces it, and none is shown
        // while PDV draws, so a figure on screen is always the outcome's
        figure.set(Optional.empty());
        outcome.set("Drawing the annotated spectrum of PSM " + psmId + " with PDV.");
        publish();
        background.execute(
                () -> {
                    DrawnSpectrum drawn = null;
                    String failure = null;
                    try {
                        drawn = port.draw(run, psmId);
                    } catch (IOException | RuntimeException failed) {
                        failure = describe(failed);
                    }
                    DrawnSpectrum answer = drawn;
                    String why = failure;
                    ui.execute(() -> drawn(answer, why));
                });
    }

    private void drawn(DrawnSpectrum drawn, String failure) {
        drawing = false;
        if (failure != null) {
            outcome.set("Nothing was drawn: " + failure);
        } else {
            figure.set(Optional.of(drawn.figure()));
            outcome.set(
                    "PDV drew the annotated spectrum of PSM "
                            + drawn.psmId()
                            + " ("
                            + drawn.peptide()
                            + ") from spectrum \""
                            + drawn.nativeId()
                            + "\" of "
                            + drawn.spectrumFile()
                            + ": "
                            + drawn.figure()
                            + ", "
                            + drawn.width()
                            + " x "
                            + drawn.height()
                            + " pixels.");
        }
        publish();
    }

    private void openNow(ResultsRun run) {
        opening = true;
        windowGeneration++;
        long mine = windowGeneration;
        windowRun = run.id();
        windowStatus = null;
        windowText.set(
                "Opening run "
                        + run.id().value()
                        + " in PDV: writing the mzTab PDV reads, then starting PDV.");
        selectionText.set(NOTHING_SHOWN);
        publish();
        background.execute(
                () -> {
                    PdvWindowStatus started = null;
                    String failure = null;
                    try {
                        started =
                                port.openInPdv(
                                        run,
                                        changed -> ui.execute(() -> windowChanged(mine, changed)));
                    } catch (IOException | RuntimeException failed) {
                        failure = describe(failed);
                    }
                    PdvWindowStatus answer = started;
                    String why = failure;
                    ui.execute(() -> opened(mine, run.id(), answer, why));
                });
    }

    private void opened(long mine, RunId run, PdvWindowStatus started, String failure) {
        if (mine != windowGeneration) {
            if (started != null) {
                // the Results section moved on while PDV was starting: this window is not wanted
                background.execute(() -> port.closePdv(run));
            }
            return;
        }
        opening = false;
        if (failure != null) {
            windowRun = null;
            windowText.set("PDV was not opened for run " + run.value() + ": " + failure);
        } else if (windowStatus == null) {
            windowChanged(mine, started);
        }
        publish();
    }

    private void windowChanged(long mine, PdvWindowStatus changed) {
        if (mine != windowGeneration) {
            return;
        }
        boolean nowReady =
                changed.phase() == PdvWindowStatus.Phase.READY
                        && (windowStatus == null
                                || windowStatus.phase() != PdvWindowStatus.Phase.READY);
        windowStatus = changed;
        windowText.set(words(changed));
        publish();
        if (nowReady) {
            showSelected();
        }
    }

    private static String words(PdvWindowStatus window) {
        String run = window.run().value();
        String port = window.port().isPresent() ? Integer.toString(window.port().getAsInt()) : "?";
        return switch (window.phase()) {
            case STARTING ->
                    "PDV is starting for run "
                            + run
                            + " on loopback port "
                            + port
                            + ", importing the run's mzTab.";
            case READY ->
                    "PDV is ready on loopback port "
                            + port
                            + ", showing run "
                            + run
                            + ". The PSM selected in the Results section is shown there.";
            case FAILED -> "PDV could not show run " + run + ": " + window.message();
            case CLOSED -> "PDV's window for run " + run + " is closed: " + window.message();
        };
    }

    /** The Results section chose another run, or none: the result set PDV shows has closed. */
    private void runChanged(Optional<ResultsRun> chosen) {
        RunId open = windowRun;
        if (open == null || chosen.map(run -> run.id().equals(open)).orElse(false)) {
            return;
        }
        windowGeneration++;
        windowRun = null;
        windowStatus = null;
        opening = false;
        windowText.set(
                "PDV's window for run "
                        + open.value()
                        + " was closed: the Results section no longer shows that run.");
        selectionText.set(NOTHING_SHOWN);
        background.execute(() -> port.closePdv(open));
    }

    /** Sends the selected PSM to the window, when one is open for the run the table shows. */
    private void showSelected() {
        RunId open = windowRun;
        Optional<ResultsRun> run = results.chosenRunProperty().get();
        Optional<TableKind> shown = results.chosenTableProperty().get();
        Optional<ResultRowView> row = table.anchorRowProperty().get();
        if (open == null
                || windowStatus == null
                || windowStatus.phase().isFinal()
                || run.isEmpty()
                || !run.get().id().equals(open)
                || shown.isEmpty()
                || !shown.get().isPsms()
                || row.isEmpty()) {
            return;
        }
        ResultsRun target = run.get();
        boolean decoy = shown.get().isDecoy();
        String psmId = row.get().psmId();
        long mine = windowGeneration;
        background.execute(
                () ->
                        port.showInPdv(target, decoy, psmId)
                                .thenAccept(
                                        selection ->
                                                ui.execute(
                                                        () ->
                                                                shownInPdv(
                                                                        mine, decoy, psmId,
                                                                        selection))));
    }

    private void shownInPdv(long mine, boolean decoy, String psmId, PdvSelection selection) {
        if (mine != windowGeneration || selection.kind() == PdvSelection.Kind.SUPERSEDED) {
            return;
        }
        selectionText.set(
                (decoy ? "Decoy" : "Target")
                        + " PSM "
                        + psmId
                        + (selection.kind() == PdvSelection.Kind.SELECTED
                                ? " is shown in PDV: "
                                : " is not shown in PDV: ")
                        + selection.message());
    }

    private void publish() {
        Optional<ResultsRun> run = results.chosenRunProperty().get();
        runText.set(
                run.map(
                                open ->
                                        "Run "
                                                + open.id().value()
                                                + ", created "
                                                + open.created()
                                                + (open.executing() ? ", executing now." : "."))
                        .orElse(NO_RUN));
        Optional<TableKind> shown = results.chosenTableProperty().get();
        Optional<ResultRowView> row = table.anchorRowProperty().get();
        boolean psmTable = shown.map(TableKind::isPsms).orElse(false);
        if (shown.isPresent() && !psmTable) {
            psmText.set(PEPTIDE_TABLE);
        } else {
            psmText.set(row.map(VisualisationViewModel::psmWords).orElse(NO_PSM));
        }
        String refusal = refusal(run, psmTable, row);
        drawRefusal.set(refusal);
        drawEnabled.set(refusal.isEmpty());
        String openRefused = openRefusal(run);
        openRefusal.set(openRefused);
        openEnabled.set(openRefused.isEmpty());
        closeEnabled.set(
                windowRun != null && windowStatus != null && !windowStatus.phase().isFinal());
    }

    private String refusal(
            Optional<ResultsRun> run, boolean psmTable, Optional<ResultRowView> row) {
        if (installActive) {
            return "PDV is being installed; the spectrum is drawn when it has finished.";
        }
        if (drawing) {
            return "A spectrum is being drawn; the next can be drawn when it has finished.";
        }
        if (run.isEmpty()) {
            return NO_RUN + " Open a run there and select a PSM.";
        }
        if (!psmTable) {
            return PEPTIDE_TABLE;
        }
        if (row.isEmpty()) {
            return NO_PSM + " Select one there to draw its spectrum.";
        }
        if (run.get().executing()) {
            return "Run "
                    + run.get().id().value()
                    + " is executing: the workflow engine holds its provenance event log, where"
                    + " every PDV launch is recorded. Draw when the run has ended.";
        }
        return installability();
    }

    private String openRefusal(Optional<ResultsRun> run) {
        if (installActive) {
            return "PDV is being installed; it can be opened when it has finished.";
        }
        if (opening) {
            return "A run is being opened in PDV; it can be opened again when that has finished.";
        }
        if (run.isEmpty()) {
            return NO_RUN + " Open a run there to show it in PDV.";
        }
        if (run.get().executing()) {
            return "Run "
                    + run.get().id().value()
                    + " is executing: its results are not final, and the workflow engine holds"
                    + " its provenance event log, where every PDV launch is recorded. Open it in"
                    + " PDV when the run has ended.";
        }
        return installability();
    }

    private String installability() {
        if (status == null) {
            return NOT_READ;
        }
        if (status.state() == PdvStatus.State.INSTALLING
                || status.state() == PdvStatus.State.UNAVAILABLE) {
            return status.explanation();
        }
        return "";
    }

    private static String psmWords(ResultRowView row) {
        return "PSM "
                + row.psmId()
                + ": peptide "
                + row.peptide()
                + ", charge "
                + row.charge()
                + ", scan "
                + row.scan()
                + " of "
                + row.sourceFile()
                + ".";
    }

    private static String describe(Exception failed) {
        String message = failed.getMessage();
        return message == null || message.isBlank() ? failed.toString() : message;
    }
}
