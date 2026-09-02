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

package org.cometgui.tools.process;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.ToolCommand;

/**
 * The only place in the product that constructs a {@link ProcessBuilder} ({@code R-PROC-02}).
 *
 * <p>An ArchUnit rule in {@code cometgui-archtests} enforces that, and the reason is concrete: a
 * FASTA file named {@code my proteins;rm -rf.fasta} is an ordinary filename and an execution
 * vulnerability the moment anything joins arguments with spaces and hands the result to a shell.
 * Nothing here ever builds a command string; {@link ToolCommand#argv()} is passed to {@link
 * ProcessBuilder} as an array and reaches the operating system as one.
 *
 * <h2>The environment is constructed, never inherited ({@code R-PROC-04})</h2>
 *
 * <p>{@link ProcessBuilder#environment()} starts as a copy of this JVM's environment. This service
 * <strong>clears it</strong> and puts back exactly {@link ToolCommand#environment()} -- nothing
 * else. A tool therefore sees no {@code PATH}, no {@code HOME}, no {@code TMPDIR}, no {@code LANG}
 * and, on Windows, no {@code SystemRoot} unless the caller put it in the command.
 *
 * <p>That is deliberate and it is what makes a run reproducible: a search whose result depends on
 * which shell launched the application is a search that cannot be repeated, and the provenance
 * record would be describing a run that nobody can reconstruct. <strong>A caller that needs an
 * inherited variable must name it in the {@link ToolCommand}</strong>, where it is recorded, rather
 * than relying on it being there.
 *
 * <h2>The working directory is explicit ({@code R-PROC-04})</h2>
 *
 * <p>Every process is started in {@link ToolCommand#workingDirectory()}, which is checked here
 * before the launch so that the diagnostic names the directory. Left to {@link ProcessBuilder}, a
 * missing directory produces {@code error=2, No such file or directory} naming the
 * <em>executable</em>, which sends the reader looking for the wrong problem.
 *
 * <h2>Standard input is closed, not inherited</h2>
 *
 * <p>No tool in this workflow reads standard input. Redirecting it to {@code INHERIT} would let a
 * tool that did read it block on the launching terminal -- invisibly, forever, inside a desktop
 * application that has no terminal. The default pipe is kept and closed immediately after the
 * start, so such a tool sees end of file at once instead of hanging.
 *
 * <h2>Threading</h2>
 *
 * <p>{@link #start} returns as soon as the process is running. Three daemon threads per process
 * drain standard output, drain standard error, and complete the run. <strong>Every {@link
 * ProcessListener} callback happens on one of those threads and never on the JavaFX application
 * thread</strong>; a listener that touches the user interface must hop threads itself. The two
 * streams are never merged: {@code redirectErrorStream} is not used, because a merged stream cannot
 * tell a user which of the two a message came from.
 *
 * <p>Thread safe and stateless: one instance serves the whole application.
 */
public final class ProcessService implements ProcessRunner {

    /** How long a cancelled process has to end politely before it is killed. */
    private static final Duration DEFAULT_TERMINATION_GRACE = Duration.ofSeconds(5);

    private final Clock clock;
    private final Charset charset;
    private final Duration terminationGrace;

    /**
     * A service decoding tool output as UTF-8 and allowing five seconds for a polite termination.
     *
     * @param clock the injectable clock {@code R-PROC-01} requires, read once at start and once at
     *     exit so that a test can assert an exact duration
     * @throws NullPointerException if {@code clock} is null
     */
    public ProcessService(Clock clock) {
        this(clock, StandardCharsets.UTF_8, DEFAULT_TERMINATION_GRACE);
    }

    /**
     * A service with an explicit output encoding and termination grace.
     *
     * @param clock the injectable clock {@code R-PROC-01} requires
     * @param charset how tool output is decoded; malformed and unmappable input is replaced rather
     *     than throwing, so a stray byte cannot silence the rest of a run's log
     * @param terminationGrace how long a cancelled process is given to end after {@code SIGTERM}
     *     before it and its descendants are killed forcibly; must be positive
     * @throws NullPointerException if any argument is null
     * @throws IllegalArgumentException if {@code terminationGrace} is not positive
     */
    public ProcessService(Clock clock, Charset charset, Duration terminationGrace) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.charset = Objects.requireNonNull(charset, "charset");
        this.terminationGrace = Objects.requireNonNull(terminationGrace, "terminationGrace");
        if (terminationGrace.isNegative() || terminationGrace.isZero()) {
            throw new IllegalArgumentException(
                    "terminationGrace must be positive, but was: " + terminationGrace);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns {@link StartedProcess} rather than the port's {@code RunningProcess}: a covariant
     * return, which satisfies the port exactly and still lets the caller that started the process
     * read its pid, its start instant and its duration.
     *
     * @param command the validated argument array, working directory and environment
     * @param listener receives output lines and the exit code, on the service's own threads
     * @return the handle on the started process
     * @throws IOException if the working directory is missing or the process cannot be started; the
     *     message names the directory or renders the command, and never prints an environment value
     * @throws NullPointerException if either argument is null
     */
    @Override
    public StartedProcess start(ToolCommand command, ProcessListener listener) throws IOException {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(listener, "listener");
        Path workingDirectory = command.workingDirectory();
        if (!Files.isDirectory(workingDirectory)) {
            throw new IOException(
                    "the working directory does not exist or is not a directory: "
                            + workingDirectory
                            + " -- required by R-PROC-04 for "
                            + command);
        }
        ProcessBuilder builder = new ProcessBuilder(command.argv());
        builder.directory(workingDirectory.toFile());
        builder.environment().clear();
        builder.environment().putAll(command.environment());
        Process process = startOrExplain(builder, command);
        Instant startedAt = clock.instant();
        try {
            closeStandardInput(process);
        } catch (IOException notClosed) {
            process.toHandle().destroy();
            throw new IOException("could not close standard input of " + command, notClosed);
        }
        GuardedListener guarded = new GuardedListener(listener);
        AtomicBoolean cancellationRequested = new AtomicBoolean();
        long pid = process.pid();
        Thread standardOutputPump =
                pumpThread(
                        process.getInputStream(),
                        "standard output",
                        guarded::onStandardOutput,
                        cancellationRequested::get,
                        pid,
                        "stdout");
        Thread standardErrorPump =
                pumpThread(
                        process.getErrorStream(),
                        "standard error",
                        guarded::onStandardError,
                        cancellationRequested::get,
                        pid,
                        "stderr");
        StartedProcess started =
                new StartedProcess(
                        process,
                        command,
                        clock,
                        startedAt,
                        terminationGrace,
                        guarded,
                        standardOutputPump,
                        standardErrorPump,
                        cancellationRequested);
        standardOutputPump.start();
        standardErrorPump.start();
        Thread completion =
                new Thread(started::awaitCompletionAndNotify, "cometgui-process-" + pid + "-exit");
        completion.setDaemon(true);
        completion.start();
        return started;
    }

    /**
     * Closes the process's standard input so a tool that reads it sees end of file immediately.
     *
     * <p>Package-private and static so that it can be proved directly: "the pipe was closed" is
     * otherwise unobservable from outside, and an unobservable step is one that can be deleted
     * without any test noticing.
     *
     * @param process the freshly started process
     * @throws IOException if the pipe cannot be closed
     */
    static void closeStandardInput(Process process) throws IOException {
        process.getOutputStream().close();
    }

    private static Process startOrExplain(ProcessBuilder builder, ToolCommand command)
            throws IOException {
        try {
            return builder.start();
        } catch (IOException cannotStart) {
            /* command.toString() renders the argv safely and prints environment NAMES only, never
             * values, so this message cannot leak a token into a log (R-SEC-03). */
            throw new IOException("could not start " + command, cannotStart);
        }
    }

    private Thread pumpThread(
            InputStream source,
            String streamName,
            Consumer<String> lineSink,
            BooleanSupplier cancellationRequested,
            long pid,
            String threadSuffix) {
        StreamPump pump =
                new StreamPump(
                        source,
                        newDecoder(),
                        streamName,
                        LineSplitter.DEFAULT_MAXIMUM_LINE_LENGTH,
                        lineSink,
                        cancellationRequested);
        Thread thread = new Thread(pump, "cometgui-process-" + pid + "-" + threadSuffix);
        thread.setDaemon(true);
        return thread;
    }

    /**
     * A decoder that replaces bad input rather than throwing.
     *
     * <p>One per stream, because a {@link CharsetDecoder} is stateful.
     *
     * @return a fresh decoder for the configured charset
     */
    private CharsetDecoder newDecoder() {
        return charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
    }
}
