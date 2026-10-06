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

package org.cometgui.workflow.storage;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.CodeSource;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.tools.process.StartedProcess;

/**
 * Runs {@link LockHolderChild} in a second JVM, through {@link ProcessService} -- the only place in
 * the product allowed to build a {@link ProcessBuilder} ({@code R-PROC-02}, an ArchUnit rule).
 *
 * <p>A per-module test helper, after {@code cometgui-install}'s {@code ChildProcesses}, whose
 * Javadoc records why: the class path is built from where each needed class was loaded, not from
 * {@code java.class.path}, which under Surefire and under PIT's minion is not the test class path.
 */
final class ChildJvm {

    private final StartedProcess process;

    private final List<String> output = Collections.synchronizedList(new ArrayList<>());

    private final CountDownLatch held = new CountDownLatch(1);

    private final CountDownLatch exited = new CountDownLatch(1);

    private final AtomicInteger exitCode = new AtomicInteger(Integer.MIN_VALUE);

    private ChildJvm(List<String> argv, Path workingDirectory) throws IOException {
        this.process =
                new ProcessService(Clock.systemUTC())
                        .start(
                                new ToolCommand(argv, workingDirectory.toAbsolutePath(), Map.of()),
                                new ProcessListener() {
                                    @Override
                                    public void onStandardOutput(String line) {
                                        output.add(line);
                                        if (line.startsWith("HELD ")) {
                                            held.countDown();
                                        }
                                    }

                                    @Override
                                    public void onStandardError(String line) {
                                        output.add("stderr: " + line);
                                    }

                                    @Override
                                    public void onExit(int code) {
                                        exitCode.set(code);
                                        exited.countDown();
                                    }
                                });
    }

    /**
     * Starts the child.
     *
     * @param workingDirectory an existing directory
     * @param arguments the child's arguments
     * @return the running child
     * @throws IOException if it cannot be started
     */
    static ChildJvm start(Path workingDirectory, String... arguments) throws IOException {
        List<String> argv = new ArrayList<>();
        argv.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        argv.add("-cp");
        argv.add(classPath());
        argv.add(LockHolderChild.class.getName());
        argv.addAll(List.of(arguments));
        return new ChildJvm(argv, workingDirectory);
    }

    private static String classPath() {
        Set<String> entries = new LinkedHashSet<>();
        for (Class<?> anchor :
                List.of(
                        LockHolderChild.class,
                        ProjectLock.class,
                        org.cometgui.domain.project.ProjectLayout.class,
                        org.cometgui.provenance.json.JsonReader.class)) {
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

    /**
     * The child's process id, as the process service saw it.
     *
     * @return the pid
     */
    long pid() {
        return process.pid();
    }

    /**
     * Waits for the child to print {@code HELD}.
     *
     * @param timeout how long to wait
     * @return the {@code HELD} line
     * @throws InterruptedException if interrupted
     */
    String awaitHeld(Duration timeout) throws InterruptedException {
        if (!held.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            stop();
            throw new AssertionError("the child never took the lock; it printed " + output);
        }
        synchronized (output) {
            return output.stream()
                    .filter(line -> line.startsWith("HELD "))
                    .findFirst()
                    .orElseThrow();
        }
    }

    /**
     * Waits for the child to exit.
     *
     * @param timeout how long to wait
     * @return its exit code
     * @throws InterruptedException if interrupted
     */
    int awaitExit(Duration timeout) throws InterruptedException {
        if (!exited.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            stop();
            throw new AssertionError("the child did not exit; it printed " + output);
        }
        return exitCode.get();
    }

    /**
     * Everything the child printed.
     *
     * @return its output lines
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
            if (!exited.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("a child JVM outlived its cancellation: " + output());
            }
        }
    }
}
