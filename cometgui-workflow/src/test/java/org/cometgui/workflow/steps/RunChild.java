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
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.workflow.engine.ReuseRefusedException;
import org.cometgui.workflow.engine.RunHandle;
import org.cometgui.workflow.engine.RunResult;
import org.cometgui.workflow.state.EngineStep;

/**
 * A second JVM that runs a search through the real workflow and engine, with the stand-in tools, in
 * a fresh project: the CometGUI that crashed mid-run, or the one whose disk filled. Started by
 * {@link RunChildJvm} through the project's process service.
 *
 * <p>Arguments: the mode and an empty directory, under which the made-up search files and the
 * project ({@code <directory>/project}) are created.
 *
 * <ul>
 *   <li>{@code halt}: starts the run with a stand-in Percolator that keeps running, waits for that
 *       Percolator to print its pid -- the run is then mid-Percolator, {@code run.json} records the
 *       attempt running and the project lock is held -- prints {@code HALTING <percolator pid>
 *       <child pid> <run directory>}, and halts the JVM without releasing or recording anything:
 *       {@code Runtime.halt}, as a crash or a killed process would.
 *   <li>{@code full <bytes>}: prepares a run of both spectrum files with a stand-in Percolator that
 *       ends normally, then lowers its own file-size limit ({@code RLIMIT_FSIZE}) to {@code bytes}
 *       through {@code /usr/bin/prlimit}, run through the process service -- every later write past
 *       that size, its own or a tool's, is refused by the kernel -- runs the search to its end and
 *       prints what the result says: {@code RUN <directory>}, {@code OUTCOME <outcome>}, one {@code
 *       FAILURE <step> <message>} per failed step and one {@code FINALISATION <message>} per
 *       finalisation error, then {@code DONE}.
 * </ul>
 *
 * <p>Nothing here sleeps: every wait is on a line a stand-in prints, or on the run's end.
 */
public final class RunChild {

    private RunChild() {}

    /**
     * Runs the child.
     *
     * @param args the mode, the directory, and for {@code full} the byte limit
     * @throws IOException if the files or the project cannot be written
     * @throws InterruptedException if a wait is interrupted
     * @throws ExecutionException if a wait for a line fails
     * @throws TimeoutException if a line never comes
     * @throws RunBlockedException if the pre-run check refuses the search
     * @throws ReuseRefusedException if the engine refuses to start it
     */
    public static void main(String[] args)
            throws IOException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException,
                    RunBlockedException,
                    ReuseRefusedException {
        String mode = args[0];
        FakeSearch fake = FakeSearch.create(Path.of(args[1]));
        StandInTools.comet(fake.executable());
        Path bin = fake.root().resolve("bin/percolator");
        if ("halt".equals(mode)) {
            halt(fake, StandInTools.percolator(bin, StandInTools.Ending.CANCELLED_ONLY));
        } else if ("full".equals(mode)) {
            full(
                    fake,
                    FakePercolator.write(bin, FakePercolator.Behaviour.normal()),
                    Long.parseLong(args[2]));
        } else {
            throw new IllegalArgumentException("unknown mode " + mode);
        }
    }

    private static void halt(FakeSearch fake, Path percolator)
            throws IOException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException,
                    RunBlockedException,
                    ReuseRefusedException {
        // Not closed: the lock is left held, and the JVM halts holding it.
        RealProject project = RealProject.create(fake.project());
        PreparedRun prepared = project.prepare(StandInTools.request(fake, percolator));
        CompletableFuture<String> childLine =
                project.sink().when(text -> text.startsWith("child "));
        CompletableFuture<String> toolLine = project.sink().when(text -> text.startsWith("pid "));
        StandInTools.Watching watching = new StandInTools.Watching();
        RunHandle handle = project.workflow().start(project.engine(), prepared, watching);
        long child =
                StandInTools.processOf(StandInTools.awaitLine(childLine, watching), "child ").pid();
        long tool =
                StandInTools.processOf(StandInTools.awaitLine(toolLine, watching), "pid ").pid();
        if (handle.states().get(EngineStep.RUN_PERCOLATOR)
                != org.cometgui.workflow.state.StepState.RUNNING) {
            throw new IllegalStateException("Percolator is not running: " + handle.states());
        }
        System.out.println("HALTING " + tool + " " + child + " " + prepared.layout().root());
        System.out.flush();
        Runtime.getRuntime().halt(0);
    }

    private static void full(FakeSearch fake, Path percolator, long bytes)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        try (RealProject project = RealProject.create(fake.project())) {
            PreparedRun prepared =
                    project.prepare(StandInTools.request(fake, percolator, fake.spectra()));
            limitFileSize(bytes);
            RunResult result = project.run(prepared);
            System.out.println("RUN " + prepared.layout().root());
            System.out.println("OUTCOME " + result.outcome());
            for (Map.Entry<EngineStep, org.cometgui.workflow.state.StepState> state :
                    result.states().entrySet()) {
                System.out.println("STATE " + state.getKey().id() + " " + state.getValue());
            }
            for (Map.Entry<EngineStep, String> failure : result.failures().entrySet()) {
                System.out.println(
                        "FAILURE " + failure.getKey().id() + " " + oneLine(failure.getValue()));
            }
            for (String error : result.finalisationErrors()) {
                System.out.println("FINALISATION " + oneLine(error));
            }
            System.out.println("DONE");
            System.out.flush();
        }
    }

    /** Lowers this JVM's file-size limit, soft and hard, through {@code /usr/bin/prlimit}. */
    private static void limitFileSize(long bytes) throws IOException, InterruptedException {
        List<String> argv =
                List.of(
                        "/usr/bin/prlimit",
                        "--pid",
                        Long.toString(ProcessHandle.current().pid()),
                        "--fsize=" + bytes + ":" + bytes);
        CountDownLatch exited = new CountDownLatch(1);
        AtomicInteger code = new AtomicInteger(Integer.MIN_VALUE);
        new ProcessService(Clock.systemUTC())
                .start(
                        new ToolCommand(argv, Path.of(".").toAbsolutePath(), Map.of()),
                        new ProcessListener() {
                            @Override
                            public void onStandardOutput(String line) {
                                System.out.println("prlimit: " + line);
                            }

                            @Override
                            public void onStandardError(String line) {
                                System.out.println("prlimit: " + line);
                            }

                            @Override
                            public void onExit(int status) {
                                code.set(status);
                                exited.countDown();
                            }
                        });
        if (!exited.await(RealProject.RUN_BOUND.toSeconds(), TimeUnit.SECONDS) || code.get() != 0) {
            throw new IllegalStateException("prlimit did not lower the limit: " + code.get());
        }
        for (String line : java.nio.file.Files.readAllLines(Path.of("/proc/self/limits"))) {
            if (line.startsWith("Max file size")) {
                System.out.println("LIMITED " + line.replaceAll("\\s+", " ").trim());
            }
        }
    }

    private static String oneLine(String text) {
        return text.replace('\n', ' ');
    }
}
