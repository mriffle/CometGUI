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

package org.cometgui.app.gui;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.CodeSource;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.tools.process.StartedProcess;

/**
 * Runs {@link OtherCometGui} in a second JVM through {@link ProcessService} -- the one place in the
 * product allowed to build a {@link ProcessBuilder} ({@code R-PROC-02}) -- and collects what it
 * prints.
 *
 * <p>The class path is built from where each needed class was loaded, not from {@code
 * java.class.path}, which under Surefire and under PIT's minion is not the test class path (the
 * reason {@code cometgui-install}'s {@code ChildProcesses} gives, measured there).
 */
final class OtherCometGuiJvm {

    /** How long the child may take to reach any line, or to exit. A bound, never a delay. */
    static final Duration BOUND = Duration.ofMinutes(2);

    private final StartedProcess process;

    private final List<String> output = new ArrayList<>();

    private final List<Waiter> waiters = new ArrayList<>();

    private final CompletableFuture<Integer> exited = new CompletableFuture<>();

    private OtherCometGuiJvm(List<String> argv, Path workingDirectory) throws IOException {
        this.process =
                new ProcessService(Clock.systemUTC())
                        .start(
                                new ToolCommand(argv, workingDirectory.toAbsolutePath(), Map.of()),
                                new ProcessListener() {
                                    @Override
                                    public void onStandardOutput(String line) {
                                        add(line);
                                    }

                                    @Override
                                    public void onStandardError(String line) {
                                        add("stderr: " + line);
                                    }

                                    @Override
                                    public void onExit(int code) {
                                        exited.complete(code);
                                    }
                                });
    }

    /**
     * Starts the child.
     *
     * @param workingDirectory an existing directory
     * @param arguments {@link OtherCometGui}'s arguments
     * @return the running child
     * @throws IOException if it cannot be started
     */
    static OtherCometGuiJvm start(Path workingDirectory, String... arguments) throws IOException {
        List<String> argv = new ArrayList<>();
        argv.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        argv.add("-cp");
        argv.add(classPath());
        argv.add(OtherCometGui.class.getName());
        argv.addAll(List.of(arguments));
        return new OtherCometGuiJvm(argv, workingDirectory);
    }

    private static String classPath() {
        Set<String> entries = new LinkedHashSet<>();
        for (Class<?> anchor :
                List.of(
                        OtherCometGui.class,
                        org.cometgui.app.config.ProjectSession.class,
                        org.cometgui.workflow.steps.CometWorkflow.class,
                        org.cometgui.domain.project.ProjectLayout.class,
                        org.cometgui.provenance.json.JsonReader.class,
                        ProcessService.class,
                        org.cometgui.tools.percolator.SyntheticPin.class,
                        org.cometgui.results.filtering.store.TableKind.class,
                        org.cometgui.params.comet.model.CometParameters.class,
                        org.cometgui.params.percolator.PercolatorSettings.class)) {
            CodeSource source = anchor.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) {
                throw new AssertionError("no code source for " + anchor.getName());
            }
            try {
                entries.add(Path.of(source.getLocation().toURI()).toString());
            } catch (URISyntaxException notAPath) {
                throw new AssertionError("the code source of " + anchor.getName(), notAPath);
            }
        }
        return String.join(File.pathSeparator, entries);
    }

    private void add(String line) {
        synchronized (output) {
            output.add(line);
            for (Waiter waiter : waiters) {
                if (waiter.wanted().test(line)) {
                    waiter.seen().complete(line);
                }
            }
        }
    }

    /**
     * The child's process id.
     *
     * @return the pid
     */
    long pid() {
        return process.pid();
    }

    /**
     * Waits for the first line, already printed or not, that satisfies a condition -- failing at
     * once, with everything the child printed, if it exits first.
     *
     * @param wanted the condition
     * @return the line
     * @throws InterruptedException if interrupted
     */
    String awaitLine(Predicate<String> wanted) throws InterruptedException {
        CompletableFuture<String> seen = new CompletableFuture<>();
        synchronized (output) {
            for (String line : output) {
                if (wanted.test(line)) {
                    seen.complete(line);
                }
            }
            waiters.add(new Waiter(wanted, seen));
        }
        try {
            CompletableFuture.anyOf(seen, exited).get(BOUND.toMillis(), TimeUnit.MILLISECONDS);
            if (seen.isDone()) {
                return seen.get();
            }
            throw new AssertionError(
                    "the child exited " + exited.get() + " first; it printed " + output());
        } catch (TimeoutException timedOut) {
            stop();
            throw new AssertionError("no such line within " + BOUND + "; it printed " + output());
        } catch (ExecutionException impossible) {
            throw new AssertionError("the wait failed", impossible);
        }
    }

    /**
     * Waits for the child to exit.
     *
     * @return its exit code
     * @throws InterruptedException if interrupted
     */
    int awaitExit() throws InterruptedException {
        try {
            return exited.get(BOUND.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException timedOut) {
            stop();
            throw new AssertionError("the child did not exit; it printed " + output());
        } catch (ExecutionException impossible) {
            throw new AssertionError("the wait failed", impossible);
        }
    }

    /**
     * Everything the child printed.
     *
     * @return its output lines, standard error prefixed {@code stderr: }
     */
    List<String> output() {
        synchronized (output) {
            return List.copyOf(output);
        }
    }

    /** Kills the child if it is still running, so that no JVM outlives the test. */
    void stop() throws InterruptedException {
        if (process.isAlive()) {
            process.requestCancellation();
            try {
                exited.get(BOUND.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException | ExecutionException notEnded) {
                throw new AssertionError("a child JVM outlived its cancellation: " + output());
            }
        }
    }

    private record Waiter(Predicate<String> wanted, CompletableFuture<String> seen) {}
}
