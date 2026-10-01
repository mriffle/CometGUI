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

package org.cometgui.params.comet.fixtures;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.tools.process.ProcessService;

/**
 * Runs {@code comet -q} or {@code comet -p} in an empty directory, through the project's one
 * process launcher, and returns the bytes of the {@code comet.params.new} it wrote.
 *
 * <p>The process goes through {@link ProcessService} with an argument array and a constructed,
 * empty environment -- never a shell string, never {@link ProcessBuilder} directly. Every way the
 * run can fail to produce a file is an {@link AssertionError} naming what went wrong; none of them
 * is a skip.
 */
public final class ParameterFileCapture {

    /** How long one dump may take; the real binary answers in well under a second. */
    public static final Duration TIMEOUT = Duration.ofSeconds(60);

    private ParameterFileCapture() {}

    /**
     * Runs {@code executable mode.argument()} in {@code emptyDirectory} and returns the file it
     * wrote.
     *
     * @param executable the staged, verified binary
     * @param mode which dump to ask for
     * @param emptyDirectory an existing, empty, absolute directory to run in
     * @return the exact bytes of {@code emptyDirectory/comet.params.new}
     * @throws IOException if the process cannot be started or the file cannot be read
     * @throws InterruptedException if interrupted while waiting
     * @throws AssertionError if the directory is not empty, the run times out, exits non-zero, or
     *     writes no {@code comet.params.new}
     */
    public static byte[] capture(Path executable, CometFixtures.Mode mode, Path emptyDirectory)
            throws IOException, InterruptedException {
        try (var entries = Files.list(emptyDirectory)) {
            if (entries.findAny().isPresent()) {
                throw new AssertionError(
                        emptyDirectory
                                + " is not empty, so a comet.params.new found there afterwards"
                                + " would prove nothing about this run");
            }
        }
        List<String> argv = List.of(executable.toAbsolutePath().toString(), mode.argument());
        ToolCommand command = new ToolCommand(argv, emptyDirectory.toAbsolutePath(), Map.of());
        Collector collector = new Collector();
        RunningProcess process = new ProcessService(Clock.systemUTC()).start(command, collector);
        if (!collector.exited.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
            process.requestCancellation();
            throw new AssertionError(
                    command.displayString()
                            + " did not finish within "
                            + TIMEOUT
                            + "; it was cancelled. Output so far: "
                            + collector.transcript());
        }
        int exitCode = collector.exitCode.get();
        if (exitCode != 0) {
            throw new AssertionError(
                    command.displayString()
                            + " in "
                            + emptyDirectory
                            + " exited "
                            + exitCode
                            + " instead of 0, so whatever it wrote is not a fixture. Output: "
                            + collector.transcript());
        }
        Path written = emptyDirectory.resolve(CometFixtures.WRITTEN_FILE);
        if (!Files.isRegularFile(written)) {
            throw new AssertionError(
                    command.displayString()
                            + " exited 0 but wrote no "
                            + CometFixtures.WRITTEN_FILE
                            + " into its working directory "
                            + emptyDirectory
                            + ". Exit code 0 proves nothing; the file is the evidence. Output: "
                            + collector.transcript());
        }
        return Files.readAllBytes(written);
    }

    /** Collects both streams and the exit code, on the process service's threads. */
    private static final class Collector implements ProcessListener {

        private final List<String> lines = Collections.synchronizedList(new ArrayList<>());
        private final AtomicInteger exitCode = new AtomicInteger(Integer.MIN_VALUE);
        private final CountDownLatch exited = new CountDownLatch(1);

        @Override
        public void onStandardOutput(String line) {
            lines.add("[stdout] " + line);
        }

        @Override
        public void onStandardError(String line) {
            lines.add("[stderr] " + line);
        }

        @Override
        public void onExit(int code) {
            exitCode.set(code);
            exited.countDown();
        }

        String transcript() {
            synchronized (lines) {
                return lines.isEmpty() ? "(none)" : String.join(" | ", lines);
            }
        }
    }
}
