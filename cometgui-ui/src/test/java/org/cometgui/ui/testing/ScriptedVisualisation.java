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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.tools.InstallHandle;
import org.cometgui.domain.tools.InstallPhase;
import org.cometgui.domain.tools.InstallProgress;
import org.cometgui.domain.tools.InstallProgressListener;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.ui.viewmodel.results.ResultsRun;
import org.cometgui.ui.viewmodel.visualisation.VisualisationPort;
import org.cometgui.workflow.pdv.DrawnSpectrum;
import org.cometgui.workflow.pdv.PdvSelection;
import org.cometgui.workflow.pdv.PdvStatus;
import org.cometgui.workflow.pdv.PdvWindowStatus;

/**
 * A Visualisation port that answers from a script and records what it was asked: PDV's state as
 * set, an install whose reports the test sends, and drawings answered with a figure or a failure.
 */
public final class ScriptedVisualisation implements VisualisationPort {

    private final List<String> opens = new ArrayList<>();

    private final List<String> shows = new ArrayList<>();

    private final List<String> closes = new ArrayList<>();

    private volatile Consumer<PdvWindowStatus> windowListener;

    private volatile String openFailure;

    private volatile Function<String, PdvSelection> showAnswer;

    /** What {@link #pdv()} says until a test sets a status. */
    public static final String NO_PDV = "No PDV in this test.";

    private volatile PdvStatus status = PdvStatus.unavailable(NO_PDV);

    private volatile String statusFailure;

    private volatile String installFailure;

    private volatile String drawFailure;

    private final Path root;

    private volatile InstallProgressListener listener;

    private final List<ToolVersion> installs = new ArrayList<>();

    private final List<String> draws = new ArrayList<>();

    private int cancels;

    private int statusReads;

    /** A port whose figures are named under the working directory. */
    public ScriptedVisualisation() {
        this(Path.of("").toAbsolutePath());
    }

    /**
     * A port whose figures are named under a directory.
     *
     * @param root where the scripted run's paths are
     */
    public ScriptedVisualisation(Path root) {
        this.root = Objects.requireNonNull(root, "root");
    }

    /**
     * Sets what {@link #pdv()} answers.
     *
     * @param now the status
     */
    public void status(PdvStatus now) {
        status = Objects.requireNonNull(now, "now");
        statusFailure = null;
    }

    /**
     * Makes {@link #pdv()} fail.
     *
     * @param failure the failure's message
     */
    public void statusFails(String failure) {
        statusFailure = failure;
    }

    /**
     * Makes {@link #install} fail.
     *
     * @param failure the failure's message
     */
    public void installFails(String failure) {
        installFailure = failure;
    }

    /**
     * Makes {@link #draw} fail with this message, or succeed again with {@code null}.
     *
     * @param failure the failure's message, or {@code null}
     */
    public void drawFails(String failure) {
        drawFailure = failure;
    }

    @Override
    public synchronized PdvStatus pdv() throws IOException {
        statusReads++;
        if (statusFailure != null) {
            throw new IOException(statusFailure);
        }
        return status;
    }

    @Override
    public synchronized InstallHandle install(ToolVersion version, InstallProgressListener progress)
            throws IOException {
        if (installFailure != null) {
            throw new IOException(installFailure);
        }
        installs.add(version);
        listener = progress;
        return () -> {
            synchronized (this) {
                cancels++;
            }
        };
    }

    /**
     * Sends a progress report to the install's listener, as the Tool Manager would.
     *
     * @param phase the phase
     * @param bytes bytes transferred
     * @param total the total, negative when unknown
     */
    public void report(InstallPhase phase, long bytes, long total) {
        listener.onInstallProgress(
                new InstallProgress(ToolName.PDV, ToolVersion.parse("2.7.0"), phase, bytes, total));
    }

    @Override
    public synchronized DrawnSpectrum draw(ResultsRun run, String psmId) throws IOException {
        draws.add(run.id().value() + " " + psmId);
        if (drawFailure != null) {
            throw new IOException(drawFailure);
        }
        return new DrawnSpectrum(
                psmId,
                "controllerType=0 controllerNumber=1 scan=11367",
                root.resolve("k562_3.mzML"),
                root.resolve("outputs/comet/k562_3.pep.xml"),
                "FNLTYVSHDGDDK",
                figure(),
                800,
                400);
    }

    /**
     * Where the scripted figure is said to be.
     *
     * @return {@code <root>/pdv/figures/0001/figures/FNLTYVSHDGDDK.png}
     */
    public Path figure() {
        return root.resolve("pdv/figures/0001/figures/FNLTYVSHDGDDK.png");
    }

    /**
     * The versions installs were started for.
     *
     * @return them, in order
     */
    public synchronized List<ToolVersion> installs() {
        return List.copyOf(installs);
    }

    /**
     * Each drawing asked for, as {@code <run id> <psm id>}.
     *
     * @return them, in order
     */
    public synchronized List<String> draws() {
        return List.copyOf(draws);
    }

    /**
     * How many times the install was cancelled.
     *
     * @return the count
     */
    public synchronized int cancels() {
        return cancels;
    }

    /**
     * How many times PDV's state was read.
     *
     * @return the count
     */
    public synchronized int statusReads() {
        return statusReads;
    }

    /**
     * Makes {@link #openInPdv} fail with this message, or succeed again with {@code null}.
     *
     * @param failure the failure's message, or {@code null}
     */
    public void openFails(String failure) {
        openFailure = failure;
    }

    /**
     * How {@link #showInPdv} answers, given {@code <decoy|target> <psm id>}; by default every PSM
     * is selected.
     *
     * @param answer the answer
     */
    public void answeringShow(Function<String, PdvSelection> answer) {
        showAnswer = answer;
    }

    @Override
    public synchronized PdvWindowStatus openInPdv(ResultsRun run, Consumer<PdvWindowStatus> states)
            throws IOException {
        opens.add(run.id().value());
        if (openFailure != null) {
            throw new IOException(openFailure);
        }
        windowListener = states;
        return window(run.id(), PdvWindowStatus.Phase.STARTING, "starting");
    }

    /**
     * Sends a window state to the listener the last opening gave, as the controller would.
     *
     * @param run the run
     * @param phase the phase
     * @param message the message
     */
    public void windowState(RunId run, PdvWindowStatus.Phase phase, String message) {
        windowListener.accept(window(run, phase, message));
    }

    private static PdvWindowStatus window(RunId run, PdvWindowStatus.Phase phase, String message) {
        return new PdvWindowStatus(run, phase, OptionalInt.of(41_547), message);
    }

    @Override
    public synchronized CompletableFuture<PdvSelection> showInPdv(
            ResultsRun run, boolean decoy, String psmId) {
        String key = (decoy ? "decoy " : "target ") + psmId;
        shows.add(run.id().value() + " " + key);
        Function<String, PdvSelection> answer = showAnswer;
        return CompletableFuture.completedFuture(
                answer != null
                        ? answer.apply(key)
                        : new PdvSelection(
                                run.id(),
                                Optional.of("ms_run[1]:index=" + (shows.size() - 1)),
                                PdvSelection.Kind.SELECTED,
                                OptionalInt.of(200),
                                "PDV answered /select with 200 selected."));
    }

    @Override
    public synchronized void closePdv(RunId run) {
        closes.add(run.value());
    }

    @Override
    public synchronized void closeAllPdv() {
        closes.add("all");
    }

    /**
     * The runs opened in PDV, in order.
     *
     * @return their identifiers
     */
    public synchronized List<String> opens() {
        return List.copyOf(opens);
    }

    /**
     * The PSMs shown in PDV, in order, as {@code <run id> <decoy|target> <psm id>}.
     *
     * @return them
     */
    public synchronized List<String> shows() {
        return List.copyOf(shows);
    }

    /**
     * The windows closed, in order: a run's identifier, or {@code all}.
     *
     * @return them
     */
    public synchronized List<String> closes() {
        return List.copyOf(closes);
    }
}
