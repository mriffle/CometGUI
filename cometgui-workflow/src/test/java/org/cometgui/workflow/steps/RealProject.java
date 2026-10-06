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

package org.cometgui.workflow.steps;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.cometgui.domain.log.LogMessage;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.project.ProjectId;
import org.cometgui.domain.project.ProjectLayout;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.provenance.manifest.ApplicationRecord;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.tools.process.RunMessageSink;
import org.cometgui.tools.process.StartedProcess;
import org.cometgui.workflow.engine.EngineServices;
import org.cometgui.workflow.engine.ReuseRefusedException;
import org.cometgui.workflow.engine.RunHandle;
import org.cometgui.workflow.engine.RunResult;
import org.cometgui.workflow.engine.StepStateListener;
import org.cometgui.workflow.engine.StepTransition;
import org.cometgui.workflow.engine.WorkflowEngine;
import org.cometgui.workflow.storage.ProjectLock;
import org.cometgui.workflow.storage.ProjectStore;
import org.cometgui.workflow.storage.RunStore;

/**
 * A real project, wired as the application wires it: the real process service behind a counting
 * decorator, the one caching hasher shared by the workflow and the engine, a console sink that
 * keeps every line, and the engine. One per test class.
 */
final class RealProject implements AutoCloseable {

    /** How long any one real run may take before the test fails. A bound, never a delay. */
    static final Duration RUN_BOUND = Duration.ofMinutes(5);

    /** The processors the engine divides among invocations in these tests. */
    static final int CORES = 8;

    /** The cap on concurrent invocations in these tests (P8-6: at most 2). */
    static final int CAP = 2;

    private final ProjectLayout project;

    private final ProjectLock lock;

    private final RunStore store;

    private final CachingHashService hashes;

    private final CountingRunner runner;

    private final LineSink sink;

    private final CometWorkflow workflow;

    private final WorkflowEngine engine;

    private RealProject(
            ProjectLayout project,
            ProjectLock lock,
            RunStore store,
            CachingHashService hashes,
            CountingRunner runner,
            LineSink sink) {
        this.project = project;
        this.lock = lock;
        this.store = store;
        this.hashes = hashes;
        this.runner = runner;
        this.sink = sink;
        this.workflow = new CometWorkflow(hashes, RealComet.BUILD);
        Clock clock = Clock.systemUTC();
        this.engine =
                new WorkflowEngine(
                        new EngineServices(
                                runner,
                                clock,
                                SecretRedactor.patternsOnly(),
                                sink,
                                hashes,
                                CORES,
                                CAP));
    }

    /**
     * Creates a project directory, its {@code project.json} and {@code runs/}, and locks it.
     *
     * @param root the project directory, which must not hold a project yet
     * @return the project
     */
    static RealProject create(Path root) throws IOException {
        Clock clock = Clock.systemUTC();
        ProjectLayout project = new ProjectLayout(root);
        new ProjectStore(clock).create(project, new ProjectId("project-real"));
        ProjectLock lock = ProjectLock.acquire(project, clock);
        AtomicInteger next = new AtomicInteger();
        RunStore store =
                new RunStore(
                        project,
                        clock,
                        () ->
                                new RunId(
                                        String.format(
                                                Locale.ROOT, "run-%04d", next.incrementAndGet())));
        return new RealProject(
                project,
                lock,
                store,
                new CachingHashService(new StreamingHashService()),
                new CountingRunner(new ProcessService(clock)),
                new LineSink());
    }

    ProjectLayout project() {
        return project;
    }

    ProjectLock lock() {
        return lock;
    }

    RunStore store() {
        return store;
    }

    CachingHashService hashes() {
        return hashes;
    }

    CountingRunner runner() {
        return runner;
    }

    LineSink sink() {
        return sink;
    }

    CometWorkflow workflow() {
        return workflow;
    }

    WorkflowEngine engine() {
        return engine;
    }

    static ApplicationRecord application() {
        return ApplicationRecord.capture("0.1.0-test", "real-comet-tests");
    }

    PreparedRun prepare(SearchRequest request) throws IOException, RunBlockedException {
        return workflow.prepare(store, lock, request, application());
    }

    /** Starts an attempt and waits, within {@link #RUN_BOUND}, for it to finish. */
    RunResult run(PreparedRun run, StepStateListener listener)
            throws IOException, ReuseRefusedException, InterruptedException {
        RunHandle handle = workflow.start(engine, run, listener);
        return handle.await(RUN_BOUND)
                .orElseThrow(
                        () -> {
                            handle.cancel();
                            return new AssertionError("the run did not finish within " + RUN_BOUND);
                        });
    }

    RunResult run(PreparedRun run) throws IOException, ReuseRefusedException, InterruptedException {
        return run(run, StepStateListener.NONE);
    }

    /** The run directories of the project, by name. */
    List<String> runDirectories() throws IOException {
        try (Stream<Path> listed = Files.list(project.runsDirectory())) {
            return listed.map(path -> String.valueOf(path.getFileName())).sorted().toList();
        }
    }

    @Override
    public void close() throws IOException {
        lock.close();
    }

    /**
     * The real process service, with every launch recorded: what a test counts to prove that no
     * Comet process started, and holds to learn a running Comet's pid.
     */
    static final class CountingRunner implements ProcessRunner {

        private final ProcessService delegate;

        private final List<Launch> launches = new ArrayList<>();

        CountingRunner(ProcessService delegate) {
            this.delegate = delegate;
        }

        @Override
        public StartedProcess start(ToolCommand command, ProcessListener listener)
                throws IOException {
            StartedProcess started = delegate.start(command, listener);
            synchronized (launches) {
                launches.add(new Launch(command, started));
            }
            return started;
        }

        List<Launch> launches() {
            synchronized (launches) {
                return List.copyOf(launches);
            }
        }

        /** The launches of one executable. */
        List<Launch> launchesOf(Path executable) {
            List<Launch> of = new ArrayList<>();
            for (Launch launch : launches()) {
                if (launch.command().argv().get(0).equals(executable.toString())) {
                    of.add(launch);
                }
            }
            return of;
        }
    }

    /**
     * One recorded launch.
     *
     * @param command what was launched
     * @param process the started process
     */
    record Launch(ToolCommand command, StartedProcess process) {}

    /** The console stand-in: keeps every line and completes futures when a wanted line arrives. */
    static final class LineSink implements RunMessageSink {

        private final Object lock = new Object();

        private final List<LogMessage> messages = new ArrayList<>();

        private final List<Waiter> waiters = new ArrayList<>();

        @Override
        public void append(LogMessage message) {
            synchronized (lock) {
                messages.add(message);
                for (Waiter waiter : waiters) {
                    if (waiter.wanted().test(message.text())) {
                        waiter.seen().complete(message.text());
                    }
                }
            }
        }

        /** A future completed by the first line, already received or not, matching a predicate. */
        CompletableFuture<String> when(Predicate<String> wanted) {
            CompletableFuture<String> seen = new CompletableFuture<>();
            synchronized (lock) {
                for (LogMessage message : messages) {
                    if (wanted.test(message.text())) {
                        seen.complete(message.text());
                    }
                }
                waiters.add(new Waiter(wanted, seen));
            }
            return seen;
        }

        private record Waiter(Predicate<String> wanted, CompletableFuture<String> seen) {}
    }

    /** Keeps every transition, in order. */
    static final class Transitions implements StepStateListener {

        private final List<StepTransition> seen = new ArrayList<>();

        @Override
        public void onTransition(StepTransition transition) {
            synchronized (seen) {
                seen.add(transition);
            }
        }

        List<StepTransition> all() {
            synchronized (seen) {
                return List.copyOf(seen);
            }
        }
    }
}
