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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.ports.EnvironmentReader;
import org.cometgui.domain.ports.HashService;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.params.percolator.resolution.CompatibilityAction;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.provenance.manifest.ApplicationRecord;
import org.cometgui.tools.api.JavaRuntime;
import org.cometgui.tools.limelight.LimelightConverter;
import org.cometgui.tools.pdv.PdvFigures;
import org.cometgui.tools.pdv.derived.PdvController;
import org.cometgui.ui.viewmodel.limelight.LimelightPort;
import org.cometgui.ui.viewmodel.limelight.LimelightRerun;
import org.cometgui.ui.viewmodel.params.ActiveRun;
import org.cometgui.ui.viewmodel.params.EngineCheck;
import org.cometgui.ui.viewmodel.params.PercolatorRequest;
import org.cometgui.ui.viewmodel.params.RunNotStartedException;
import org.cometgui.ui.viewmodel.params.RunObserver;
import org.cometgui.ui.viewmodel.percolator.PercolatorPort;
import org.cometgui.ui.viewmodel.percolator.RerunCheck;
import org.cometgui.ui.viewmodel.results.ResultsPort;
import org.cometgui.ui.viewmodel.visualisation.VisualisationPort;
import org.cometgui.workflow.engine.EngineServices;
import org.cometgui.workflow.engine.WorkflowEngine;
import org.cometgui.workflow.limelight.LimelightConversions;
import org.cometgui.workflow.limelight.LimelightProvisioning;
import org.cometgui.workflow.pdv.PdvLaunches;
import org.cometgui.workflow.pdv.PdvMzTabs;
import org.cometgui.workflow.pdv.PdvWindows;
import org.cometgui.workflow.pdv.PsmFigures;
import org.cometgui.workflow.steps.CometWorkflow;
import org.cometgui.workflow.steps.PercolatorRerun;

/**
 * The Run section's composition (decision P8-16): the workflow engine and the Comet workflow over
 * the one process service, the console, one hasher and the session's project.
 *
 * <h2>What is wired to what</h2>
 *
 * <ul>
 *   <li><strong>The process service</strong> is {@link ApplicationServices#requireProcessRunner()}
 *       -- the one {@code ProcessService} the composition root already holds and the Tool Manager's
 *       probes run through ({@code R-PROC-02}).
 *   <li><strong>The console</strong> receives every tool output line as {@code
 *       boundedMessageLog::append} -- Phase 03's decision: the engine gets a method reference that
 *       can append, never the log.
 *   <li><strong>One hasher</strong>: a {@link CachingHashService} over a {@link
 *       StreamingHashService}, shared by the engine and the workflow (P8-1), so the revalidated
 *       cache and its bypass are one.
 *   <li><strong>Secrets</strong> are redacted by {@link SecretRedactor#patternsOnly()}, the one
 *       rule set.
 *   <li><strong>Concurrency</strong>: the engine divides this machine's processors among per-file
 *       Comet invocations, with an explicit cap of {@link #INVOCATION_CAP} (P8-15).
 * </ul>
 *
 * <p>A composition root built without a process service (a supported configuration of {@link
 * ApplicationServices}) gets a port that says, as the engine's reason, that no process can be
 * launched.
 */
public final class RunWiring {

    /** The explicit cap on concurrent Comet invocations of one run (P8-15). */
    public static final int INVOCATION_CAP = 4;

    /** The engine's reason when the composition root has no process service. */
    public static final String NO_PROCESS_SERVICE =
            "No run can start: this application was composed without a process service, so no"
                    + " tool can be launched.";

    private RunWiring() {}

    /**
     * Where the Run section finds its tools and its project, and where PDV finds its display: the
     * composition root's choice, and a GUI test's seam.
     *
     * @param tools makes the Tool Manager, or says why this machine has none
     * @param projectDirectory where the session's project is created and locked
     * @param pdvDisplay the variables PDV is given to reach a display, from the application's
     *     environment: {@link PdvDisplay#fromHost} on a desktop, a test's own display in a test
     */
    public record Setup(
            ToolManagerSource tools, Path projectDirectory, PdvDisplaySource pdvDisplay) {

        /**
         * Requires all three.
         *
         * @throws NullPointerException naming a component that is {@code null}
         * @throws IllegalArgumentException if the project directory is not absolute
         */
        public Setup {
            Objects.requireNonNull(tools, "tools");
            Objects.requireNonNull(projectDirectory, "projectDirectory");
            Objects.requireNonNull(pdvDisplay, "pdvDisplay");
            if (!projectDirectory.isAbsolute()) {
                throw new IllegalArgumentException(
                        "the project directory must be absolute: " + projectDirectory);
            }
        }

        /**
         * The Tool Manager and the project, with PDV given this desktop's display ({@link
         * PdvDisplay#fromHost}).
         *
         * @param tools makes the Tool Manager, or says why this machine has none
         * @param projectDirectory where the session's project is created and locked
         */
        public Setup(ToolManagerSource tools, Path projectDirectory) {
            this(tools, projectDirectory, PdvDisplay::fromHost);
        }

        /**
         * The application's own: the Tool Manager for this machine ({@link
         * ToolManagerWiring#forThisApplication}) and the default project under the application data
         * directory ({@link ProjectSession#defaultDirectory}).
         *
         * @param services the composition root
         * @return the setup
         */
        public static Setup forThisApplication(ApplicationServices services) {
            Objects.requireNonNull(services, "services");
            return new Setup(
                    () ->
                            ToolManagerWiring.forThisApplication(
                                    services, ToolManagerWiring.installThreads()),
                    ProjectSession.defaultDirectory(
                            services.fileSystem().applicationDataDirectory()));
        }
    }

    /** The variables PDV is given to reach a display. */
    @FunctionalInterface
    public interface PdvDisplaySource {

        /**
         * The variables.
         *
         * @param host the application's environment
         * @return the variables; never {@code HOME} or {@code LANG}, which PDV's command sets
         */
        Map<String, String> environment(EnvironmentReader host);
    }

    /** Makes the Tool Manager, or says why there is none. */
    @FunctionalInterface
    public interface ToolManagerSource {

        /**
         * The Tool Manager.
         *
         * @return the manager
         * @throws ToolManagerUnavailableException if this machine has none, saying why
         */
        ToolManager create() throws ToolManagerUnavailableException;
    }

    /**
     * The Percolator section's port over the Tool Manager: its Percolator builds and local-binary
     * registration.
     *
     * @param tools the Tool Manager, or empty
     * @param toolsUnavailable why there is no Tool Manager, when {@code tools} is empty
     * @return the port
     */
    public static PercolatorPort percolator(Optional<ToolManager> tools, String toolsUnavailable) {
        Objects.requireNonNull(tools, "tools");
        return new ToolManagerPercolatorPort(() -> tools, toolsUnavailable);
    }

    /**
     * The engine port over the composition root: the Run section's, and the Percolator section's
     * rerun over the same session.
     *
     * @param services the composition root, whose process service and clock the engine uses
     * @param messageLog the console's log, which tool output is appended to
     * @param tools the Tool Manager, or empty
     * @param toolsUnavailable why there is no Tool Manager, when {@code tools} is empty
     * @param project the session's project
     * @param build the running build, named in every parameter file and provenance record
     * @return the port
     */
    public static SessionEngine port(
            ApplicationServices services,
            BoundedMessageLog messageLog,
            Optional<ToolManager> tools,
            String toolsUnavailable,
            ProjectSession project,
            BuildIdentity build) {
        return port(services, messageLog, tools, toolsUnavailable, project, build, hasher());
    }

    /**
     * The session's one hasher (P8-1): a {@link CachingHashService} over a {@link
     * StreamingHashService}, shared by the engine, the workflow and the Results section.
     *
     * @return a new hasher; the composition root makes exactly one
     */
    public static CachingHashService hasher() {
        return new CachingHashService(new StreamingHashService());
    }

    /**
     * The engine port over the composition root, as {@link #port(ApplicationServices,
     * BoundedMessageLog, Optional, String, ProjectSession, BuildIdentity)}, over a given hasher --
     * the session's one, which the Results section's port uses too.
     *
     * @param services the composition root, whose process service and clock the engine uses
     * @param messageLog the console's log, which tool output is appended to
     * @param tools the Tool Manager, or empty
     * @param toolsUnavailable why there is no Tool Manager, when {@code tools} is empty
     * @param project the session's project
     * @param build the running build, named in every parameter file and provenance record
     * @param hashes the session's one hasher
     * @return the port
     */
    public static SessionEngine port(
            ApplicationServices services,
            BoundedMessageLog messageLog,
            Optional<ToolManager> tools,
            String toolsUnavailable,
            ProjectSession project,
            BuildIdentity build,
            CachingHashService hashes) {
        Objects.requireNonNull(services, "services");
        Objects.requireNonNull(hashes, "hashes");
        Objects.requireNonNull(messageLog, "messageLog");
        Objects.requireNonNull(tools, "tools");
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(build, "build");
        Optional<ProcessRunner> processes = services.processRunner();
        if (processes.isEmpty()) {
            return new Unavailable(NO_PROCESS_SERVICE);
        }
        WorkflowEngine engine =
                new WorkflowEngine(
                        new EngineServices(
                                processes.get(),
                                services.clock(),
                                SecretRedactor.patternsOnly(),
                                messageLog::append,
                                hashes,
                                Runtime.getRuntime().availableProcessors(),
                                INVOCATION_CAP));
        Supplier<ApplicationRecord> application =
                () -> ApplicationRecord.capture(build.version(), build.commitId());
        return new WorkflowRunPort(
                () -> tools,
                toolsUnavailable,
                project,
                hashes,
                new CometWorkflow(hashes, build),
                engine,
                new PercolatorRerun(hashes),
                application);
    }

    /**
     * The Results section's port over the session's project (Phase 10): its runs with Percolator
     * results, their stores, view state and exports. It launches nothing: it is given no process
     * service, only the project, the one hasher, the build and the engine's executing runs.
     *
     * @param services the composition root, whose clock dates every export
     * @param project the session's project
     * @param hashes the session's one hasher
     * @param build the running build, whose version every export records
     * @param engine the session's engine, which says which runs are executing
     * @return the port
     */
    public static ResultsPort results(
            ApplicationServices services,
            ProjectSession project,
            HashService hashes,
            BuildIdentity build,
            SessionEngine engine) {
        Objects.requireNonNull(services, "services");
        Objects.requireNonNull(engine, "engine");
        return new ProjectResultsPort(
                project, hashes, build.version(), services.clock(), engine::executingRuns);
    }

    /**
     * The Visualisation section's port (phase 11): PDV's state and install through the Tool
     * Manager, drawing a PSM's spectrum, and PDV's live {@code denovo-gui} window -- both through
     * the one recording launch path, over the composition root's one process service, the session's
     * one hasher and the one secret rule set, so that every PDV launch is in the run's event log
     * before PDV starts. The live window's controller is the derived {@link PdvController} with
     * {@link PdvController.Timing#DEFAULT}; the port's {@code closeAllPdv} shuts every window down
     * and is what the application calls when it exits.
     *
     * @param services the composition root: its process service, clock and environment
     * @param project the session's project
     * @param hashes the session's one hasher
     * @param engine the session's engine, which says which runs are executing
     * @param tools the Tool Manager, or empty
     * @param toolsUnavailable why there is no Tool Manager, when {@code tools} is empty
     * @param display the variables PDV is given to reach a display
     * @return the port
     */
    public static VisualisationPort visualisation(
            ApplicationServices services,
            ProjectSession project,
            HashService hashes,
            SessionEngine engine,
            Optional<ToolManager> tools,
            String toolsUnavailable,
            PdvDisplaySource display) {
        Objects.requireNonNull(services, "services");
        Objects.requireNonNull(hashes, "hashes");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(tools, "tools");
        Objects.requireNonNull(display, "display");
        Optional<HostPlatform> host =
                HostPlatform.of(
                        services.environment().osName().orElse(""),
                        services.environment().osArch().orElse(""));
        Optional<PsmFigures> figures = Optional.empty();
        Optional<PdvWindows> windows = Optional.empty();
        String noFigures = NO_PROCESS_SERVICE;
        if (services.processRunner().isPresent() && host.isPresent()) {
            SecretRedactor redactor = SecretRedactor.patternsOnly();
            PdvLaunches launches =
                    new PdvLaunches(
                            services.processRunner().get(),
                            hashes,
                            redactor,
                            services.clock(),
                            host.get());
            figures = Optional.of(new PsmFigures(launches, PdvFigures.DEFAULT_TIMEOUT));
            windows =
                    Optional.of(
                            new PdvWindows(
                                    new PdvMzTabs(
                                            hashes,
                                            redactor,
                                            services.clock(),
                                            engine::executingRuns),
                                    launches,
                                    new PdvController(PdvController.Timing.DEFAULT),
                                    engine::executingRuns));
        } else if (host.isEmpty()) {
            noFigures =
                    "No spectrum can be drawn: this machine's operating system and architecture"
                            + " are not ones CometGUI recognises.";
        }
        EnvironmentReader environment = services.environment();
        return new ProjectVisualisationPort(
                project,
                () -> tools,
                toolsUnavailable,
                figures,
                noFigures,
                engine::executingRuns,
                () -> display.environment(environment),
                windows);
    }

    /**
     * The Limelight section's port (phase 12): the converter's state and install through the Tool
     * Manager, a run's readiness, settings and conversion through {@link LimelightConversions} --
     * the one conversion path, over the composition root's one process service, the session's one
     * hasher, the one secret rule set and the bundled Java runtime -- and Phase 09's compatible
     * rerun through the session's engine. The converter's log lines go to the console and to the
     * conversion the section is waiting on.
     *
     * @param services the composition root: its process service, clock and environment
     * @param messageLog the console's log, which the converter's lines are appended to
     * @param project the session's project
     * @param hashes the session's one hasher
     * @param engine the session's engine: its executing runs and the compatible rerun
     * @param tools the Tool Manager, or empty
     * @param toolsUnavailable why there is no Tool Manager, when {@code tools} is empty
     * @return the port
     */
    public static LimelightPort limelight(
            ApplicationServices services,
            BoundedMessageLog messageLog,
            ProjectSession project,
            HashService hashes,
            SessionEngine engine,
            Optional<ToolManager> tools,
            String toolsUnavailable) {
        Objects.requireNonNull(services, "services");
        Objects.requireNonNull(messageLog, "messageLog");
        Objects.requireNonNull(hashes, "hashes");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(tools, "tools");
        Optional<HostPlatform> host =
                HostPlatform.of(
                        services.environment().osName().orElse(""),
                        services.environment().osArch().orElse(""));
        ProjectLimelightPort.ConverterLines lines =
                new ProjectLimelightPort.ConverterLines(messageLog::append);
        Optional<LimelightConversions> conversions = Optional.empty();
        String none = NO_PROCESS_SERVICE;
        if (tools.isEmpty()) {
            none = "No run can be converted to Limelight XML: " + toolsUnavailable;
        } else if (host.isEmpty()) {
            none =
                    "No run can be converted to Limelight XML: this machine's operating system and"
                            + " architecture are not ones CometGUI recognises.";
        } else if (services.processRunner().isPresent()) {
            Optional<JavaRuntime> java = Optional.empty();
            try {
                java = Optional.of(JavaRuntime.ofThisApplication());
            } catch (IOException noLauncher) {
                none =
                        "No run can be converted to Limelight XML: the Java runtime CometGUI runs"
                                + " on cannot start the converter: "
                                + noLauncher.getMessage();
            }
            conversions =
                    java.map(
                            runtime ->
                                    new LimelightConversions(
                                            new LimelightProvisioning(tools.get()),
                                            services.processRunner().get(),
                                            hashes,
                                            SecretRedactor.patternsOnly(),
                                            lines,
                                            services.clock(),
                                            host.get(),
                                            runtime,
                                            LimelightConverter.DEFAULT_TIMEOUT,
                                            engine::executingRuns));
        }
        return new ProjectLimelightPort(
                project, () -> tools, toolsUnavailable, conversions, none, engine, lines);
    }

    /**
     * Where the Run section calls the engine: daemon threads, made as needed, so that a check, a
     * start and a cancel never wait for one another and none keeps the JVM alive.
     *
     * @return the executor; the caller shuts it down
     */
    public static ExecutorService backgroundThreads() {
        AtomicInteger next = new AtomicInteger();
        return Executors.newCachedThreadPool(
                work -> {
                    Thread thread = new Thread(work, "cometgui-run-" + next.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
    }

    /**
     * Where the Results section reads its result stores: one daemon thread, so that its reads,
     * opens and closes happen in the order they were asked for and none keeps the JVM alive.
     *
     * @return the executor; the caller shuts it down
     */
    public static ExecutorService resultThread() {
        return Executors.newSingleThreadExecutor(
                work -> {
                    Thread thread = new Thread(work, "cometgui-results");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    /** A port that can do nothing, and says why. */
    private record Unavailable(String reason) implements SessionEngine {

        @Override
        public EngineCheck check(
                CometParameters model, List<Path> spectra, PercolatorRequest percolator) {
            return EngineCheck.unavailable(reason);
        }

        @Override
        public ActiveRun start(
                CometParameters model,
                List<Path> spectra,
                PercolatorRequest percolator,
                RunObserver observer)
                throws RunNotStartedException {
            throw new RunNotStartedException(reason, null);
        }

        @Override
        public RerunCheck check(PercolatorRequest percolator) {
            return RerunCheck.refused(reason);
        }

        @Override
        public ActiveRun start(PercolatorRequest percolator, RunObserver observer)
                throws RunNotStartedException {
            throw new RunNotStartedException(reason, null);
        }

        @Override
        public Set<RunId> executingRuns() {
            return Set.of();
        }

        @Override
        public LimelightRerun rerunCompatible(
                RunLayout source,
                CompatibilityAction action,
                PercolatorSettings settings,
                RunObserver observer)
                throws RunNotStartedException {
            throw new RunNotStartedException(reason, null);
        }
    }
}
