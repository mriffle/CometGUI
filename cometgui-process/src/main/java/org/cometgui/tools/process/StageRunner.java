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
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.run.StageTag;

/**
 * Runs one workflow stage: the tool, its per-stage log file, the console, redaction, an optional
 * timeout, and the outcome.
 *
 * <p>{@link ProcessService} launches a process and streams its two outputs to a listener. This is
 * what a workflow stage actually needs on top of that, and it is one object rather than four so
 * that the caller holds one thing:
 *
 * <ul>
 *   <li>every line is written to the stage's own log file <strong>as it arrives</strong> and
 *       flushed, which is the half of {@code R-PROC-03} the console's cap does not cover;
 *   <li>the same line, redacted, is appended to the run's console through a {@link RunMessageSink};
 *   <li>an optional per-stage timeout, <strong>off unless one is given</strong>, cancels the stage
 *       through the process service's own cancellation, which terminates descendants and escalates;
 *   <li>and the result is one immutable {@link StageOutcome} carrying what a provenance record and
 *       a user interface need.
 * </ul>
 *
 * <h2>One runner per run</h2>
 *
 * <p>The log directory is fixed at construction, because a run has one. Stages are told apart by
 * their identifier, which becomes the log file's name and is validated before it does -- see {@link
 * StageLogFile#create}. Nothing is created and nothing is opened until {@link #start} is called, so
 * constructing a runner cannot fail, cannot block, and cannot leave a file behind.
 *
 * <h2>Threading</h2>
 *
 * <p>{@link #start} returns as soon as the process is running. Everything after that happens on the
 * process service's own daemon threads: two pumps and one completion thread, none of them the
 * JavaFX application thread. A {@link RunMessageSink} implementation is called from both pump
 * threads and must be thread safe; {@code BoundedMessageLog} is.
 *
 * <p>This class is thread safe and holds no per-stage state: one runner can start several stages.
 */
public final class StageRunner {

    /** The value {@link #startStage} takes when no timeout is configured. */
    private static final long NO_TIMEOUT = 0L;

    private final ProcessRunner processes;
    private final Clock clock;
    private final ProcessRedactor redactor;
    private final RunMessageSink sink;
    private final Path logDirectory;

    /**
     * A runner for one run.
     *
     * @param processes how a tool is launched; {@link ProcessService} in the assembled application
     * @param clock the injected clock {@code R-PROC-01} requires: read once at the start, once per
     *     output line, and once at the exit
     * @param redactor applied to every line, and to the rendered command ({@code R-SEC-03})
     * @param sink where console messages go; {@code boundedMessageLog::append} in the assembled
     *     application, and see {@link RunMessageSink} for why it is a method and not the log
     * @param logDirectory where this run's per-stage log files go; created when the first stage
     *     starts, not now
     * @throws NullPointerException if any argument is null
     */
    public StageRunner(
            ProcessRunner processes,
            Clock clock,
            ProcessRedactor redactor,
            RunMessageSink sink,
            Path logDirectory) {
        this.processes = Objects.requireNonNull(processes, "processes");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.redactor = Objects.requireNonNull(redactor, "redactor");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.logDirectory = Objects.requireNonNull(logDirectory, "logDirectory");
    }

    /**
     * Starts a stage with <strong>no</strong> timeout, which is the default and the ordinary case.
     *
     * <p>A Comet search over a large FASTA legitimately runs for hours, and a process service that
     * decided on its own when that had gone on too long would be the application inventing a
     * failure. A stage runs until it ends or until somebody cancels it.
     *
     * @param stage the stage; its {@link StageTag#id()} becomes the log file's name and is
     *     validated
     * @param command the validated argument array, working directory and environment
     * @return the handle on the running stage
     * @throws IOException if the log file cannot be opened or the process cannot be started; the
     *     log file, if it was opened, is closed and says what happened
     * @throws NullPointerException if either argument is null
     * @throws IllegalArgumentException if the stage identifier is not a safe file-name token
     */
    public RunningStage start(StageTag stage, ToolCommand command) throws IOException {
        return startStage(stage, command, NO_TIMEOUT);
    }

    /**
     * Starts a stage that will be cancelled if it runs longer than {@code timeout}.
     *
     * <p>The timeout is measured in real time from the moment the process starts -- {@link
     * RunningStage} explains why it cannot be measured by the injected clock -- and it cancels the
     * stage exactly as a user's cancellation would, so descendants are terminated and a process
     * that ignores the polite signal is killed. The outcome and the log footer both record that it
     * was the timeout rather than a person.
     *
     * @param stage the stage; its {@link StageTag#id()} becomes the log file's name and is
     *     validated
     * @param command the validated argument array, working directory and environment
     * @param timeout how long the stage may run; at least one millisecond, since the expiry is
     *     scheduled in milliseconds and a shorter one would round down to "immediately"
     * @return the handle on the running stage
     * @throws IOException if the log file cannot be opened or the process cannot be started
     * @throws NullPointerException if any argument is null
     * @throws IllegalArgumentException if the stage identifier is not a safe file-name token, or if
     *     the timeout is negative, zero or shorter than a millisecond
     * @throws ArithmeticException if the timeout is so long that it does not fit in a {@code long}
     *     of milliseconds, which is about 292 million years
     */
    public RunningStage start(StageTag stage, ToolCommand command, Duration timeout)
            throws IOException {
        Objects.requireNonNull(timeout, "timeout");
        long timeoutMillis = timeout.toMillis();
        if (timeoutMillis < 1L) {
            throw new IllegalArgumentException(
                    "a stage timeout must be at least one millisecond, but was: " + timeout);
        }
        return startStage(stage, command, timeoutMillis);
    }

    private RunningStage startStage(StageTag stage, ToolCommand command, long timeoutMillis)
            throws IOException {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(command, "command");
        String stageId = StageLogFile.checkedStageId(stage.id());
        Instant startedAt = clock.instant();
        String redactedDisplayCommand = redactor.redactedDisplayCommand(command);
        StageLogFile log = StageLogFile.create(logDirectory, stageId);
        log.append(
                startedAt,
                StageLogFormat.SERVICE_TAG,
                StageLogFormat.startedText(stageId, command.workingDirectory()));
        log.append(
                startedAt,
                StageLogFormat.SERVICE_TAG,
                StageLogFormat.commandText(redactedDisplayCommand));
        StageRecorder recorder =
                new StageRecorder(
                        stage,
                        stageId,
                        redactedDisplayCommand,
                        log,
                        redactor,
                        sink,
                        clock,
                        startedAt);
        RunningProcess process;
        try {
            process = processes.start(command, recorder);
        } catch (IOException | RuntimeException notStarted) {
            /* The header above already names the command and the directory, which is what the
             * reader will want; this says why nothing followed, and closes the file rather than
             * leaving an open descriptor and a two-line log behind.  The failure text goes through
             * the redactor because it renders the command: with a credential registered that is
             * removed, and with none registered ProcessRedactor documents that a per-line scan is
             * deliberately skipped. */
            log.finish(
                    startedAt,
                    StageLogFormat.SERVICE_TAG,
                    StageLogFormat.couldNotStartText(
                            stageId, redactor.redact(String.valueOf(notStarted))));
            throw notStarted;
        }
        RunningStage running = new RunningStage(recorder, process);
        if (timeoutMillis > NO_TIMEOUT) {
            running.armTimeout(timeoutMillis);
        }
        return running;
    }
}
