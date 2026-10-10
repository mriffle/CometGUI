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

package org.cometgui.workflow.pdv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.run.RunId;
import org.cometgui.tools.pdv.PdvMzTabRefs;
import org.cometgui.tools.pdv.PdvSelectOutcome;
import org.cometgui.tools.pdv.PdvWindowShutdown;
import org.cometgui.tools.pdv.PdvWindowState;
import org.cometgui.tools.pdv.derived.DenovoGuiCommand;
import org.cometgui.tools.pdv.derived.PdvController;

/**
 * The live control path's orchestration ({@code R-PDV-04}): a finished run opened in a PDV {@code
 * denovo-gui} window, a PSM selected in the Results section shown in it, and the window shut down
 * when its result set closes or the application exits.
 *
 * <h2>Open</h2>
 *
 * <p>{@link #open} refuses a run the session's engine is executing (its results are not final and
 * the engine holds its event log). Otherwise, in order:
 *
 * <ol>
 *   <li>the run's window, if one is open, is shut down -- <em>before</em> the mzTab is prepared,
 *       because a regenerated export deletes the directory the running PDV reads and writes its
 *       {@code .db} into (unit 5);
 *   <li>{@link PdvMzTabs#prepare} makes the run's mzTab, or reuses the recorded one;
 *   <li>the {@code spectra_ref} index is read from that mzTab ({@link PdvMzTabRefs}): (decoy flag,
 *       Percolator {@code PSMId}) to reference, because a target and a decoy row can share a {@code
 *       PSMId};
 *   <li>the window is launched by the derived {@link PdvController} with the derived {@link
 *       DenovoGuiCommand}, through the runner {@link PdvLaunches#forRun} gives for {@link
 *       PdvLaunchKind#DENOVO_GUI} -- so a {@code pdv.launched} event is in the run's {@code
 *       events.log} before each start, and the process belongs to the one process service. PDV's
 *       working directory and home are {@code <run>/pdv/window/} (P11-17).
 * </ol>
 *
 * <p>One window per result set: the controller keys windows on the mzTab, and each run's mzTab is
 * its own file under its own run directory.
 *
 * <h2>Select</h2>
 *
 * <p>{@link #select} looks the PSM up in the index and has the controller send its reference --
 * debounced, and kept until PDV is ready -- and reports PDV's answer. A PSM the mzTab does not hold
 * is reported, and nothing is sent.
 *
 * <h2>Close</h2>
 *
 * <p>{@link #close(RunId)} sends {@code /shutdown} and ends the process through the process
 * service, waiting for it within the controller's bound. The Visualisation section calls it when
 * the result set closes -- the Results section shows another run, or none -- and the application
 * calls {@link #close()} when it exits.
 */
public final class PdvWindows implements AutoCloseable {

    /** The context keys a window launch records. */
    static final String MZTAB = "mztab";

    static final String MZTAB_SHA256 = "mztab-sha256";

    static final String PSMS = "psms";

    private final PdvMzTabs mzTabs;

    private final PdvLaunches launches;

    private final PdvController controller;

    private final Supplier<Set<RunId>> executing;

    /** Each open run's window: its mzTab and its reference index. */
    private final Map<RunId, Window> windows = new ConcurrentHashMap<>();

    /** One window of one run. */
    private record Window(Path mzTab, PdvMzTabRefs refs) {}

    /**
     * The windows, over the session's one export, launch path and controller.
     *
     * @param mzTabs makes each run's mzTab
     * @param launches the one recording launch path
     * @param controller drives the windows
     * @param executing the runs the session's engine is executing now
     */
    public PdvWindows(
            PdvMzTabs mzTabs,
            PdvLaunches launches,
            PdvController controller,
            Supplier<Set<RunId>> executing) {
        this.mzTabs = Objects.requireNonNull(mzTabs, "mzTabs");
        this.launches = Objects.requireNonNull(launches, "launches");
        this.controller = Objects.requireNonNull(controller, "controller");
        this.executing = Objects.requireNonNull(executing, "executing");
    }

    /**
     * Opens a finished run in PDV: closes its open window, prepares its mzTab, reads the reference
     * index and launches the window. Returns once PDV has been started; the listener is told when
     * it is ready, or why it failed.
     *
     * @param request the run, its PDV, Java and display
     * @param listener told each state the window passes through, on the controller's threads
     * @return the window's state when this returns: starting
     * @throws IOException if the run is executing; the mzTab cannot be made or read (a {@link
     *     org.cometgui.tools.pdv.PdvMzTabException} names the PSM); the launch cannot be recorded;
     *     or PDV cannot be started. Nothing is then open for the run.
     */
    public synchronized PdvWindowStatus open(
            PdvWindowRequest request, Consumer<PdvWindowStatus> listener) throws IOException {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(listener, "listener");
        RunId run = request.identity().runId();
        if (executing.get().contains(run)) {
            throw new IOException(
                    "run "
                            + run.value()
                            + " is executing: its results are not final and the workflow engine"
                            + " holds its event log, where every PDV launch is recorded. Open it in"
                            + " PDV when the run has ended.");
        }
        close(run);
        PdvMzTab mzTab =
                mzTabs.prepare(
                        request.run(),
                        request.identity(),
                        request.searched(),
                        request.pdv().version());
        PdvMzTabRefs refs = PdvMzTabRefs.read(mzTab.mzTab());
        Path window = Files.createDirectories(request.windowDirectory());
        Path home = Files.createDirectories(window.resolve(PdvWindowRequest.HOME_DIRECTORY));
        DenovoGuiCommand command =
                new DenovoGuiCommand(
                        request.java(),
                        request.pdv().jar(),
                        mzTab.mzTab(),
                        mzTab.mgfs(),
                        Optional.empty(),
                        false,
                        home,
                        window,
                        request.displayEnvironment());
        Map<String, String> context = new LinkedHashMap<>();
        context.put(MZTAB, mzTab.mzTab().toString());
        context.put(MZTAB_SHA256, mzTab.mzTabHashes().sha256());
        context.put(PSMS, Long.toString(mzTab.psms()));
        ProcessRunner recorded =
                launches.forRun(
                        request.run(), run, request.pdv(), PdvLaunchKind.DENOVO_GUI, context);
        PdvWindowState state =
                controller.open(
                        mzTab.mzTab(),
                        recorded,
                        command::forPort,
                        changed -> listener.accept(status(run, changed)));
        windows.put(run, new Window(mzTab.mzTab(), refs));
        return status(run, state);
    }

    /**
     * Where a run's window stands.
     *
     * @param run the run
     * @return its window's state; empty if none is open
     */
    public Optional<PdvWindowStatus> status(RunId run) {
        Window window = windows.get(Objects.requireNonNull(run, "run"));
        return window == null
                ? Optional.empty()
                : controller.state(window.mzTab()).map(state -> status(run, state));
    }

    /**
     * Shows a PSM of a run in the run's window: its {@code spectra_ref} from the index, sent by the
     * controller.
     *
     * @param run the run
     * @param decoy whether the PSM is a row of Percolator's decoy table
     * @param psmId its Percolator {@code PSMId}
     * @return what became of it; never completed exceptionally
     */
    public CompletableFuture<PdvSelection> select(RunId run, boolean decoy, String psmId) {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(psmId, "psmId");
        Window window = windows.get(run);
        if (window == null) {
            return CompletableFuture.completedFuture(notOpen(run, Optional.empty()));
        }
        Optional<String> ref = window.refs().spectraRef(decoy, psmId);
        if (ref.isEmpty()) {
            return CompletableFuture.completedFuture(
                    new PdvSelection(
                            run,
                            Optional.empty(),
                            PdvSelection.Kind.NOT_IN_MZTAB,
                            OptionalInt.empty(),
                            "The "
                                    + (decoy ? "decoy" : "target")
                                    + " PSM "
                                    + psmId
                                    + " is not a row of the mzTab PDV was given, so it cannot be"
                                    + " shown there."));
        }
        return selectReference(run, ref.get());
    }

    /**
     * Shows the row with this {@code spectra_ref} in a run's window -- what {@link #select} does
     * once it has looked the PSM up.
     *
     * @param run the run
     * @param spectraRef the reference, verbatim
     * @return what became of it; never completed exceptionally
     */
    public CompletableFuture<PdvSelection> selectReference(RunId run, String spectraRef) {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(spectraRef, "spectraRef");
        Window window = windows.get(run);
        if (window == null) {
            return CompletableFuture.completedFuture(notOpen(run, Optional.of(spectraRef)));
        }
        return controller
                .select(window.mzTab(), spectraRef)
                .thenApply(outcome -> selection(run, outcome));
    }

    /**
     * Closes a run's window: {@code /shutdown}, then the process through the process service,
     * waited for within the controller's bound.
     *
     * @param run the run
     * @return how it went; empty if no window was open for the run
     */
    public synchronized Optional<PdvWindowShutdown> close(RunId run) {
        Window window = windows.remove(Objects.requireNonNull(run, "run"));
        return window == null ? Optional.empty() : controller.shutdown(window.mzTab());
    }

    /** Closes every run's window, as {@link #close(RunId)} does, and then the controller. */
    @Override
    public synchronized void close() {
        for (RunId run : List.copyOf(windows.keySet())) {
            close(run);
        }
        controller.close();
    }

    private static PdvWindowStatus status(RunId run, PdvWindowState state) {
        return new PdvWindowStatus(
                run,
                PdvWindowStatus.Phase.valueOf(state.phase().name()),
                state.port(),
                state.message());
    }

    private static PdvSelection notOpen(RunId run, Optional<String> ref) {
        return new PdvSelection(
                run,
                ref,
                PdvSelection.Kind.NOT_OPEN,
                OptionalInt.empty(),
                "Run " + run.value() + " is not open in PDV.");
    }

    private static PdvSelection selection(RunId run, PdvSelectOutcome outcome) {
        PdvSelection.Kind kind =
                switch (outcome.kind()) {
                    case ANSWERED -> {
                        int status = outcome.answer().orElseThrow().status();
                        yield status == 200
                                ? PdvSelection.Kind.SELECTED
                                : status == 404
                                        ? PdvSelection.Kind.UNKNOWN_TO_PDV
                                        : PdvSelection.Kind.REFUSED_BY_PDV;
                    }
                    case SUPERSEDED -> PdvSelection.Kind.SUPERSEDED;
                    case NOT_OPEN -> PdvSelection.Kind.NOT_OPEN;
                    case FAILED -> PdvSelection.Kind.FAILED;
                };
        OptionalInt status =
                outcome.answer()
                        .map(answer -> OptionalInt.of(answer.status()))
                        .orElse(OptionalInt.empty());
        return new PdvSelection(
                run, Optional.of(outcome.spectraRef()), kind, status, outcome.message());
    }
}
