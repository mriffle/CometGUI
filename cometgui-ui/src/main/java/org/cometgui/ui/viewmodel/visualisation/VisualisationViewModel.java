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
import org.cometgui.workflow.pdv.PdvStatus;

/**
 * The Visualisation section's first form (phase 11): PDV's state, the run and PSM selected in the
 * Results section, and drawing that PSM's annotated spectrum with PDV.
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
 * <p>{@link #draw()} with PDV not installed starts the install through the port -- the Tool
 * Manager's -- shows its progress, offers {@link #cancelInstall()}, and draws when it is done. The
 * install runs on the Tool Manager's own threads and the drawing on the {@code background}
 * executor; nothing here waits for either, and nothing in the Run section asks for PDV.
 *
 * <h2>What is refused, and said</h2>
 *
 * <p>{@link #drawRefusalProperty()} is empty exactly when {@link #draw()} would start: a run open,
 * a PSM table shown, a PSM selected, the run not executing (the workflow engine holds its event
 * log, where every PDV launch is recorded), PDV installed or installable, and nothing already being
 * installed or drawn. Anything the drawing itself refuses -- an unsupported spectrum file, no
 * documented mapping, PDV's own failure -- is {@link #outcomeProperty()}, in the refusal's own
 * words.
 *
 * <h2>Two threads</h2>
 *
 * <p>The port is called only on {@code background}; every answer, and every install report, is
 * applied on {@code ui}.
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
        results.chosenRunProperty().addListener((observable, before, after) -> publish());
        results.chosenTableProperty().addListener((observable, before, after) -> publish());
        table.anchorRowProperty().addListener((observable, before, after) -> publish());
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
                    PdvStatus answer;
                    try {
                        answer = port.pdv();
                    } catch (IOException | RuntimeException failed) {
                        answer =
                                PdvStatus.unavailable(
                                        "PDV's state could not be read: " + describe(failed));
                    }
                    PdvStatus read = answer;
                    ui.execute(() -> statusRead(mine, read));
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
        ToolVersion version = status.version().orElseThrow();
        installActive = true;
        installing.set(true);
        installProgress.set(-1);
        pdvText.set("Installing PDV " + version.text() + ": starting.");
        try {
            install =
                    port.install(
                            version, report -> ui.execute(() -> progressed(report, run, psmId)));
        } catch (IOException | RuntimeException refused) {
            installActive = false;
            installing.set(false);
            installProgress.set(0);
            pdvText.set(status.explanation());
            outcome.set("PDV could not be installed, so nothing was drawn: " + describe(refused));
            return false;
        }
        outcome.set(
                "PDV "
                        + version.text()
                        + " is being installed; the spectrum of PSM "
                        + psmId
                        + " is drawn when it has finished.");
        publish();
        return true;
    }

    /** Asks the install this section started to stop. Nothing is drawn then. */
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

    // ========================================================================= internals ====

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

    private void progressed(InstallProgress report, ResultsRun run, String psmId) {
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
            drawing = true;
            outcome.set("PDV is installed; drawing the spectrum of PSM " + psmId + ".");
            publish();
            background.execute(
                    () -> {
                        PdvStatus answer;
                        try {
                            answer = port.pdv();
                        } catch (IOException | RuntimeException failed) {
                            answer =
                                    PdvStatus.unavailable(
                                            "PDV's state could not be read: " + describe(failed));
                        }
                        PdvStatus read = answer;
                        ui.execute(() -> installedThenDraw(read, run, psmId));
                    });
            return;
        }
        outcome.set(report.phase() == InstallPhase.CANCELLED ? INSTALL_CANCELLED : INSTALL_FAILED);
        refresh();
        publish();
    }

    private void installedThenDraw(PdvStatus read, ResultsRun run, String psmId) {
        statusGeneration++;
        status = read;
        pdvText.set(read.explanation());
        drawing = false;
        if (read.state() != PdvStatus.State.INSTALLED) {
            outcome.set("Nothing was drawn: " + read.explanation());
            publish();
            return;
        }
        drawNow(run, psmId);
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
