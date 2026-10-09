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

package org.cometgui.app.config;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.percolator.resolution.PercolatorResolution;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.provenance.manifest.ApplicationRecord;
import org.cometgui.ui.viewmodel.params.ActiveRun;
import org.cometgui.ui.viewmodel.params.EngineCheck;
import org.cometgui.ui.viewmodel.params.PercolatorRequest;
import org.cometgui.ui.viewmodel.params.RerunOutlook;
import org.cometgui.ui.viewmodel.params.RunNotStartedException;
import org.cometgui.ui.viewmodel.params.RunObserver;
import org.cometgui.ui.viewmodel.percolator.RerunCheck;
import org.cometgui.workflow.engine.ReuseCheck;
import org.cometgui.workflow.engine.ReuseRefusedException;
import org.cometgui.workflow.engine.RunHandle;
import org.cometgui.workflow.engine.RunResult;
import org.cometgui.workflow.engine.StepStateListener;
import org.cometgui.workflow.engine.StepTransition;
import org.cometgui.workflow.engine.WorkflowEngine;
import org.cometgui.workflow.state.InputKind;
import org.cometgui.workflow.state.InputValue;
import org.cometgui.workflow.state.StepInputs;
import org.cometgui.workflow.steps.CometSelection;
import org.cometgui.workflow.steps.CometWorkflow;
import org.cometgui.workflow.steps.DerivedRun;
import org.cometgui.workflow.steps.PercolatorChoice;
import org.cometgui.workflow.steps.PercolatorRerun;
import org.cometgui.workflow.steps.PercolatorRerunPreview;
import org.cometgui.workflow.steps.PercolatorSelection;
import org.cometgui.workflow.steps.PreRunReport;
import org.cometgui.workflow.steps.PreparedRun;
import org.cometgui.workflow.steps.RerunRefusedException;
import org.cometgui.workflow.steps.RunBlockedException;
import org.cometgui.workflow.steps.SearchRequest;
import org.cometgui.workflow.storage.ProjectLock;

/**
 * The Run section's engine port over the one workflow engine (decision P8-16; {@code
 * org.cometgui.ui.viewmodel.params.RunEnginePort}): the selected Comet from the Tool Manager, the
 * session's project, {@link CometWorkflow} and {@link WorkflowEngine}.
 *
 * <h2>Which Comet</h2>
 *
 * <p>The installed Comet of the release the parameters are for: the first {@link ToolOffer} of
 * Comet at that version which the Tool Manager reports {@link ToolInstallState#INSTALLED} with a
 * path -- a managed install before a registered local binary, in the Tool Manager's own order. Its
 * SHA-256 is taken by hashing that file through the one hasher when it is selected, and the pre-run
 * check and the run's {@code resolve-comet} step re-hash it and refuse a mismatch. No index mode is
 * offered by the interface yet, so every search is {@link IndexMode#NONE}.
 *
 * <h2>Which Percolator</h2>
 *
 * <p>The Percolator section's {@link PercolatorRequest}: its build -- installed, with a path -- is
 * hashed through the one hasher when the check or the run is made, as the Comet is, and with the
 * settings and the resolution it was chosen against becomes the search's {@link PercolatorChoice}
 * ({@link SearchRequest#withPercolator}); the pre-run check and the run's {@code
 * resolve-percolator} step re-hash it. A request with problems is not run; its problems are the Run
 * section's own reasons, so the check here then covers the Comet half alone and gives no preview.
 *
 * <h2>The compatible-version rerun</h2>
 *
 * <p>The same last run is the source of the Percolator section's rerun ({@link #check(
 * PercolatorRequest)}, {@link #start(PercolatorRequest, RunObserver)}; design decision P9-11): a
 * new, derived run with the Percolator half as it is now, reusing the last run's merged PIN through
 * {@link PercolatorRerun}, executing no Comet. Whether it can be made, and what it executes, are
 * the workflow's answers, shown as they are.
 *
 * <h2>Retry or new run</h2>
 *
 * <p>The last run this session prepared is kept. When the configuration's inputs -- spectra,
 * database, parameter file, index mode, Comet -- digest exactly as that run recorded them, Run is a
 * retry of it: another attempt in the same run directory, which reuses every step that succeeded
 * (re-hashed against the record, P8-14) and, if a recorded result no longer matches, takes the plan
 * the engine offers. Otherwise Run prepares a new run ({@code R-RUN-06}). The rerun preview the
 * check returns describes exactly that choice ({@link RerunOutlook}).
 *
 * <h2>Which runs are executing</h2>
 *
 * <p>{@link #executingRuns()} holds every run this port started -- a new run, a retry or a rerun --
 * from just before the engine starts it until the engine reports it finished, which it does after
 * closing the run's provenance event log. The Results section refuses to export from a run listed
 * there, because the engine holds that log while the run executes.
 *
 * <p>Every method may block on file I/O and is called off the JavaFX thread.
 */
public final class WorkflowRunPort implements SessionEngine {

    /** Why there is no Percolator rerun before a run of this session. */
    public static final String NO_RUN_TO_RERUN =
            "No run has been made in this session, so there is no merged PIN to rerun Percolator"
                    + " from. Run a search first.";

    private final Supplier<Optional<ToolManager>> tools;

    private final String toolsUnavailable;

    private final ProjectSession project;

    private final CachingHashService hashes;

    private final CometWorkflow workflow;

    private final WorkflowEngine engine;

    private final PercolatorRerun rerun;

    private final Supplier<ApplicationRecord> application;

    private volatile PreparedRun last;

    /** The runs started here whose end the engine has not yet reported. */
    private final Set<RunId> executing = ConcurrentHashMap.newKeySet();

    /**
     * The port.
     *
     * @param tools the Tool Manager, or empty when this machine has none
     * @param toolsUnavailable why there is no Tool Manager, shown when {@code tools} gives none
     * @param project the session's project
     * @param hashes the one hasher, shared with {@code workflow} and {@code engine}
     * @param workflow the Comet workflow
     * @param engine the workflow engine
     * @param rerun the compatible-version Percolator rerun, over the same hasher
     * @param application the application record each run's provenance starts from, captured when
     *     the run is prepared
     */
    public WorkflowRunPort(
            Supplier<Optional<ToolManager>> tools,
            String toolsUnavailable,
            ProjectSession project,
            CachingHashService hashes,
            CometWorkflow workflow,
            WorkflowEngine engine,
            PercolatorRerun rerun,
            Supplier<ApplicationRecord> application) {
        this.tools = Objects.requireNonNull(tools, "tools");
        this.toolsUnavailable = Objects.requireNonNull(toolsUnavailable, "toolsUnavailable");
        this.project = Objects.requireNonNull(project, "project");
        this.hashes = Objects.requireNonNull(hashes, "hashes");
        this.workflow = Objects.requireNonNull(workflow, "workflow");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.rerun = Objects.requireNonNull(rerun, "rerun");
        this.application = Objects.requireNonNull(application, "application");
    }

    @Override
    public EngineCheck check(
            CometParameters model, List<Path> spectra, PercolatorRequest percolator) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(spectra, "spectra");
        Objects.requireNonNull(percolator, "percolator");
        Selected selected = select(model.version());
        if (selected.reason().isPresent()) {
            return EngineCheck.unavailable(selected.reason().get());
        }
        ProjectLock lock;
        try {
            lock = project.lock();
        } catch (IOException unusable) {
            return EngineCheck.unavailable(projectRefusal(unusable));
        }
        SearchRequest comet =
                new SearchRequest(model, spectra, selected.comet().orElseThrow(), IndexMode.NONE);
        if (!percolator.runnable()) {
            return EngineCheck.checked(workflow.check(lock.project(), comet), Optional.empty());
        }
        Chosen chosen = choiceOf(percolator);
        if (chosen.reason().isPresent()) {
            return new EngineCheck(
                    List.of(chosen.reason().get()),
                    Optional.of(workflow.check(lock.project(), comet)),
                    Optional.empty());
        }
        SearchRequest request = comet.withPercolator(chosen.choice().orElseThrow());
        PreRunReport report = workflow.check(lock.project(), request);
        if (report.blocked()) {
            return EngineCheck.checked(report, Optional.empty());
        }
        try {
            return EngineCheck.checked(report, outlook(request));
        } catch (IOException unreadable) {
            return new EngineCheck(
                    List.of(
                            "The rerun preview against run "
                                    + last.identity().runId().value()
                                    + " cannot be computed, so what Run would execute is not"
                                    + " known: "
                                    + unreadable.getMessage()),
                    Optional.of(report),
                    Optional.empty());
        }
    }

    @Override
    public ActiveRun start(
            CometParameters model,
            List<Path> spectra,
            PercolatorRequest percolator,
            RunObserver observer)
            throws RunNotStartedException {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(spectra, "spectra");
        Objects.requireNonNull(percolator, "percolator");
        Objects.requireNonNull(observer, "observer");
        Selected selected = select(model.version());
        if (selected.reason().isPresent()) {
            throw new RunNotStartedException(selected.reason().get(), null);
        }
        PercolatorChoice choice = requireChoice(percolator);
        SearchRequest request =
                new SearchRequest(model, spectra, selected.comet().orElseThrow(), IndexMode.NONE)
                        .withPercolator(choice);
        try {
            ProjectLock lock = project.lock();
            synchronized (this) {
                PreparedRun previous = last;
                if (previous != null && changedSince(request, previous).isEmpty()) {
                    return retry(previous, observer);
                }
                PreparedRun prepared =
                        workflow.prepare(project.store(), lock, request, application.get());
                last = prepared;
                observer.planned(prepared.plan(), describe(prepared, false));
                RunId id = prepared.identity().runId();
                RunHandle handle =
                        tracked(id, () -> workflow.start(engine, prepared, ended(id, observer)));
                return handle::cancel;
            }
        } catch (RunBlockedException blocked) {
            throw new RunNotStartedException(blocked.getMessage(), blocked);
        } catch (ReuseRefusedException refused) {
            throw new RunNotStartedException(refused.getMessage(), refused);
        } catch (IOException failed) {
            throw new RunNotStartedException(
                    "the run could not be created or started: " + failed.getMessage(), failed);
        }
    }

    @Override
    public RerunCheck check(PercolatorRequest percolator) {
        Objects.requireNonNull(percolator, "percolator");
        PreparedRun source = last;
        if (source == null) {
            return RerunCheck.refused(NO_RUN_TO_RERUN);
        }
        if (!percolator.runnable()) {
            return RerunCheck.refused(
                    "Percolator cannot be rerun from run "
                            + source.identity().runId().value()
                            + " until the Percolator section names a build that can run: "
                            + String.join(" ", percolator.problems()));
        }
        Chosen chosen = choiceOf(percolator);
        if (chosen.reason().isPresent()) {
            return RerunCheck.refused(chosen.reason().get());
        }
        try {
            ProjectLock lock = project.lock();
            PercolatorRerunPreview preview =
                    rerun.preview(
                            project.store(),
                            lock.project(),
                            source.layout(),
                            chosen.choice().orElseThrow());
            return RerunCheck.possible(preview.source().value(), preview.lines());
        } catch (RerunRefusedException refused) {
            return RerunCheck.refused(refused.getMessage());
        } catch (IOException unreadable) {
            return RerunCheck.refused(
                    "Whether Percolator can be rerun from run "
                            + source.identity().runId().value()
                            + " cannot be checked: "
                            + unreadable.getMessage());
        }
    }

    @Override
    public ActiveRun start(PercolatorRequest percolator, RunObserver observer)
            throws RunNotStartedException {
        Objects.requireNonNull(percolator, "percolator");
        Objects.requireNonNull(observer, "observer");
        PreparedRun source = last;
        if (source == null) {
            throw new RunNotStartedException(NO_RUN_TO_RERUN, null);
        }
        PercolatorChoice choice = requireChoice(percolator);
        try {
            ProjectLock lock = project.lock();
            synchronized (this) {
                DerivedRun derived =
                        rerun.prepare(
                                project.store(), lock, source.layout(), choice, application.get());
                observer.planned(
                        derived.plan(),
                        "run "
                                + derived.identity().runId().value()
                                + " in "
                                + derived.layout().root()
                                + ", rescoring the merged PIN of run "
                                + derived.source().runId().value());
                RunId id = derived.identity().runId();
                RunHandle handle =
                        tracked(id, () -> rerun.start(engine, derived, ended(id, observer)));
                return handle::cancel;
            }
        } catch (RerunRefusedException refused) {
            throw new RunNotStartedException(refused.getMessage(), refused);
        } catch (ReuseRefusedException refused) {
            throw new RunNotStartedException(refused.getMessage(), refused);
        } catch (IOException failed) {
            throw new RunNotStartedException(
                    "the Percolator rerun could not be created or started: " + failed.getMessage(),
                    failed);
        }
    }

    @Override
    public Set<RunId> executingRuns() {
        return Set.copyOf(executing);
    }

    /** Starts a run, holding it as executing from before the start; a refused start is not. */
    private RunHandle tracked(RunId id, Starter starter) throws IOException, ReuseRefusedException {
        executing.add(id);
        boolean started = false;
        try {
            RunHandle handle = starter.start();
            started = true;
            return handle;
        } finally {
            if (!started) {
                executing.remove(id);
            }
        }
    }

    /**
     * The listener the engine is given: the observer's, which first marks the run no longer
     * executing when the engine reports it finished -- so that whoever the observer tells already
     * sees the run as ended.
     */
    private StepStateListener ended(RunId id, StepStateListener observer) {
        return new StepStateListener() {
            @Override
            public void onTransition(StepTransition transition) {
                observer.onTransition(transition);
            }

            @Override
            public void onRunFinished(RunResult result) {
                executing.remove(id);
                observer.onRunFinished(result);
            }
        };
    }

    /** One start of the engine. */
    @FunctionalInterface
    private interface Starter {

        /**
         * Starts it.
         *
         * @return the run's handle
         * @throws IOException if it cannot start
         * @throws ReuseRefusedException if the engine refuses to reuse a recorded result
         */
        RunHandle start() throws IOException, ReuseRefusedException;
    }

    /** The search's Percolator half, or the refusal that stops a run or a rerun starting. */
    private PercolatorChoice requireChoice(PercolatorRequest percolator)
            throws RunNotStartedException {
        if (!percolator.runnable()) {
            throw new RunNotStartedException(
                    "the Percolator section names no build that can run: "
                            + String.join(" ", percolator.problems()),
                    null);
        }
        Chosen chosen = choiceOf(percolator);
        if (chosen.reason().isPresent()) {
            throw new RunNotStartedException(chosen.reason().get(), null);
        }
        return chosen.choice().orElseThrow();
    }

    /**
     * The workflow's Percolator half of a runnable request: its build hashed now, through the one
     * hasher, as a Comet is when it is selected.
     */
    private Chosen choiceOf(PercolatorRequest percolator) {
        ToolOffer offer = percolator.build().orElseThrow();
        Path executable = offer.installedPath().orElseThrow();
        PercolatorResolution resolution = percolator.resolution().orElseThrow();
        try {
            PercolatorSelection selection =
                    new PercolatorSelection(offer, executable, hashes.hash(executable).sha256());
            return new Chosen(
                    Optional.of(
                            new PercolatorChoice(
                                    selection,
                                    percolator.settings().orElseThrow(),
                                    resolution.enabledStages(),
                                    resolution)),
                    Optional.empty());
        } catch (IOException | IllegalArgumentException unusable) {
            return new Chosen(
                    Optional.empty(),
                    Optional.of(
                            "The selected Percolator "
                                    + offer.version().text()
                                    + " at "
                                    + executable
                                    + " cannot be used: "
                                    + unusable.getMessage()));
        }
    }

    /** Another attempt of the last run, on the engine's offered plan if reuse is refused. */
    private ActiveRun retry(PreparedRun previous, RunObserver observer)
            throws IOException, ReuseRefusedException {
        ReuseCheck own = engine.checkReuse(previous.request());
        observer.planned(previous.plan(), describe(previous, true));
        RunId id = previous.identity().runId();
        StepStateListener listener = ended(id, observer);
        RunHandle handle =
                tracked(
                        id,
                        () ->
                                own.accepted()
                                        ? workflow.start(engine, previous, listener)
                                        : engine.start(
                                                previous.request().withForced(own.offeredForced()),
                                                listener));
        return handle::cancel;
    }

    /** The rerun preview against the last run, if there is one. */
    private Optional<RerunOutlook> outlook(SearchRequest request) throws IOException {
        PreparedRun previous = last;
        if (previous == null) {
            return Optional.empty();
        }
        String runId = previous.identity().runId().value();
        Set<InputKind> changed = changedSince(request, previous);
        ReuseCheck own = engine.checkReuse(previous.request());
        Optional<String> refusal = own.accepted() ? Optional.empty() : Optional.of(own.message());
        if (changed.isEmpty()) {
            return Optional.of(
                    new RerunOutlook(
                            runId,
                            true,
                            own.accepted() ? own.preview() : own.offered().orElseThrow(),
                            refusal,
                            changed));
        }
        ReuseCheck compared = workflow.preview(engine, previous, request);
        return Optional.of(new RerunOutlook(runId, false, compared.preview(), refusal, changed));
    }

    /**
     * The inputs whose digests differ between a configuration as its files are now and a run's
     * record.
     */
    private Set<InputKind> changedSince(SearchRequest request, PreparedRun run) throws IOException {
        Map<InputKind, InputValue> now = workflow.inputsOf(request).values();
        StepInputs recorded = run.inputs();
        Set<InputKind> changed = EnumSet.noneOf(InputKind.class);
        Set<InputKind> kinds = EnumSet.noneOf(InputKind.class);
        kinds.addAll(now.keySet());
        kinds.addAll(recorded.values().keySet());
        for (InputKind kind : kinds) {
            Optional<String> was = recorded.get(kind).map(InputValue::digest);
            Optional<String> is = Optional.ofNullable(now.get(kind)).map(InputValue::digest);
            if (!was.equals(is)) {
                changed.add(kind);
            }
        }
        return changed;
    }

    private static String describe(PreparedRun run, boolean retry) {
        return (retry ? "a retry of run " : "run ")
                + run.identity().runId().value()
                + " in "
                + run.layout().root();
    }

    private String projectRefusal(IOException unusable) {
        return "The project at "
                + project.directory()
                + " cannot be used, so no run can start: "
                + unusable.getMessage();
    }

    /** The installed Comet of a release, or why there is none. */
    private Selected select(ToolVersion release) {
        Optional<ToolManager> manager = tools.get();
        if (manager.isEmpty()) {
            return Selected.none(
                    "No Comet can be selected, because this machine has no Tool Manager: "
                            + toolsUnavailable);
        }
        List<ToolOffer> offers = manager.get().offers();
        Optional<ToolOffer> installed =
                offers.stream()
                        .filter(offer -> offer.tool() == ToolName.COMET)
                        .filter(offer -> offer.version().equals(release))
                        .filter(offer -> offer.state() == ToolInstallState.INSTALLED)
                        .filter(offer -> offer.installedPath().isPresent())
                        .findFirst();
        if (installed.isEmpty()) {
            return Selected.none(
                    "Comet "
                            + release.text()
                            + " is not installed, and the parameters are for that release:"
                            + " install it in the Tool Manager section, or register a Comet "
                            + release.text()
                            + " already on this computer there.");
        }
        Path executable = installed.get().installedPath().orElseThrow();
        try {
            return new Selected(
                    Optional.of(
                            new CometSelection(
                                    executable,
                                    release,
                                    hashes.hash(executable).sha256(),
                                    installed.get().origin() == ToolOrigin.MANAGED,
                                    Optional.empty())),
                    Optional.empty());
        } catch (IOException unreadable) {
            return Selected.none(
                    "The installed Comet "
                            + release.text()
                            + " at "
                            + executable
                            + " cannot be read: "
                            + unreadable.getMessage());
        }
    }

    /** A Percolator half for the workflow, or why there is none. */
    private record Chosen(Optional<PercolatorChoice> choice, Optional<String> reason) {}

    /** A selected Comet, or why there is none. */
    private record Selected(Optional<CometSelection> comet, Optional<String> reason) {

        static Selected none(String reason) {
            return new Selected(Optional.empty(), Optional.of(reason));
        }
    }
}
