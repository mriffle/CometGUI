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

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.cometgui.domain.log.LogMessage;
import org.cometgui.domain.log.MessageSeverity;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.run.StageTag;

/**
 * What a stage does with each line: timestamp it, redact it, write it to disk, show it, count it.
 *
 * <p>This is the {@link ProcessListener} {@link StageRunner} hands to the process service, and it
 * is where the two halves of {@code R-PROC-03} meet -- the file that gets everything and the
 * console that keeps the newest few thousand lines.
 *
 * <h2>The order of the four steps is the contract</h2>
 *
 * <ol>
 *   <li><strong>The clock is read once</strong>, and that one instant is used for the log line and
 *       for the console message. Two reads would let the same line carry two different times.
 *   <li><strong>The line is redacted once</strong>, before it goes anywhere, so that the disk and
 *       the console cannot disagree about what was shown ({@code R-SEC-03}).
 *   <li><strong>The line is counted</strong>, before either write. The count is how many lines the
 *       tool emitted, not how many were successfully recorded, and it must not fall if a sink
 *       throws.
 *   <li><strong>The file first, the console second.</strong> The disk is the run's record and the
 *       console is a view of it. A sink that throws -- the process service catches it, so the pump
 *       survives -- must not be able to cost the disk a line, and with this order it cannot.
 * </ol>
 *
 * <h2>A log file that cannot be written says so, once</h2>
 *
 * <p>The first failed write puts one {@link MessageSeverity#WARNING} into the console naming the
 * file, because a console quietly showing output that is not reaching the disk is how a user ends
 * up with an empty log and no idea why. Only the first: a full disk fails every subsequent line
 * too, and a warning per line would bury the run's actual output. The total is carried out in
 * {@link StageOutcome#logWriteFailures()}.
 *
 * <p>Thread safe: both pump threads call {@link #onStandardOutput} and {@link #onStandardError} at
 * once, and the process service's completion thread calls {@link #onExit}.
 */
final class StageRecorder implements ProcessListener {

    /** Who asked for the stage to stop, if anyone. */
    enum Cancellation {

        /** Nobody. */
        NONE,

        /** The caller, through {@link RunningStage#requestCancellation()}. */
        BY_CALLER,

        /** The stage's own timeout. */
        BY_TIMEOUT
    }

    private final StageTag stage;
    private final String stageId;
    private final String redactedDisplayCommand;
    private final StageLogFile log;
    private final ProcessRedactor redactor;
    private final RunMessageSink sink;
    private final Clock clock;
    private final Instant startedAt;

    private final AtomicLong standardOutputLines = new AtomicLong();
    private final AtomicLong standardErrorLines = new AtomicLong();
    private final AtomicReference<Cancellation> cancellation =
            new AtomicReference<>(Cancellation.NONE);
    private final AtomicBoolean logFailureAnnounced = new AtomicBoolean();
    private final CompletableFuture<StageOutcome> completed = new CompletableFuture<>();

    /**
     * Assembled by {@link StageRunner}, which owns every argument.
     *
     * @param stage the stage being run
     * @param stageId its identifier, already validated as a file-name token
     * @param redactedDisplayCommand what was run, escaped and with credentials removed
     * @param log the open log file, taken over by this recorder and closed by {@link #onExit}
     * @param redactor applied to every line before it reaches the disk or the console
     * @param sink where console messages go; see {@link RunMessageSink}
     * @param clock the injected clock, read once per line and once at the exit
     * @param startedAt when the stage started, already read from that clock
     * @throws NullPointerException if any argument is null
     */
    StageRecorder(
            StageTag stage,
            String stageId,
            String redactedDisplayCommand,
            StageLogFile log,
            ProcessRedactor redactor,
            RunMessageSink sink,
            Clock clock,
            Instant startedAt) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.stageId = Objects.requireNonNull(stageId, "stageId");
        this.redactedDisplayCommand =
                Objects.requireNonNull(redactedDisplayCommand, "redactedDisplayCommand");
        this.log = Objects.requireNonNull(log, "log");
        this.redactor = Objects.requireNonNull(redactor, "redactor");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
    }

    @Override
    public void onStandardOutput(String line) {
        record(ToolStream.STANDARD_OUTPUT, line);
    }

    @Override
    public void onStandardError(String line) {
        record(ToolStream.STANDARD_ERROR, line);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Called once, by the process service, after the last line of both streams. The footer is
     * written and the file closed <em>before</em> the outcome is built, so that a failure to write
     * or to close the log is included in {@link StageOutcome#logWriteFailures()} rather than
     * reported one run too late.
     */
    @Override
    public void onExit(int exitCode) {
        Instant endedAt = clock.instant();
        Cancellation reason = cancellation.get();
        boolean cancellationRequested = reason != Cancellation.NONE;
        boolean timedOut = reason == Cancellation.BY_TIMEOUT;
        log.finish(
                endedAt,
                StageLogFormat.SERVICE_TAG,
                StageLogFormat.endedText(
                        stageId,
                        exitCode,
                        Duration.between(startedAt, endedAt),
                        cancellationRequested,
                        timedOut));
        completed.complete(
                new StageOutcome(
                        stage,
                        redactedDisplayCommand,
                        log.file(),
                        exitCode,
                        startedAt,
                        endedAt,
                        standardOutputLines.get(),
                        standardErrorLines.get(),
                        cancellationRequested,
                        timedOut,
                        log.failureCount()));
    }

    /**
     * Records who asked for the stage to stop. The first reason wins.
     *
     * <p>A user who cancels a stage that later hits its timeout cancelled it; a stage killed by its
     * timeout that the user then also cancels timed out. Either way the outcome names the reason
     * the process actually died of.
     *
     * @param reason {@link Cancellation#BY_CALLER} or {@link Cancellation#BY_TIMEOUT}
     */
    void markCancellation(Cancellation reason) {
        cancellation.compareAndSet(Cancellation.NONE, reason);
    }

    /**
     * The stage this recorder belongs to.
     *
     * @return the stage, never null
     */
    StageTag stage() {
        return stage;
    }

    /**
     * The log file being written, known before the stage finishes.
     *
     * @return the path, never null
     */
    Path logFile() {
        return log.file();
    }

    /**
     * The future completed, exactly once and never exceptionally, when the stage ends.
     *
     * @return the future
     */
    CompletableFuture<StageOutcome> completed() {
        return completed;
    }

    private void record(ToolStream stream, String line) {
        Instant at = clock.instant();
        String text = redactor.redact(line);
        counterFor(stream).incrementAndGet();
        if (!log.append(at, stream.tag(), text) && logFailureAnnounced.compareAndSet(false, true)) {
            sink.append(
                    LogMessage.at(
                            at,
                            stage,
                            MessageSeverity.WARNING,
                            StreamPump.FAULT_PREFIX
                                    + "the stage log "
                                    + log.file()
                                    + " could not be written: "
                                    + log.firstFailure().orElse("no reason recorded")));
        }
        sink.append(LogMessage.at(at, stage, stream.severity(), text));
    }

    private AtomicLong counterFor(ToolStream stream) {
        return stream == ToolStream.STANDARD_OUTPUT ? standardOutputLines : standardErrorLines;
    }
}
