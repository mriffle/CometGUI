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

package org.cometgui.workflow.limelight;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.run.StageTag;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.provenance.events.ProvenanceEventLog;
import org.cometgui.provenance.events.ProvenanceEventType;
import org.cometgui.tools.process.ProcessRedactor;
import org.cometgui.tools.process.RunMessageSink;
import org.cometgui.tools.process.RunningStage;
import org.cometgui.tools.process.StageOutcome;
import org.cometgui.tools.process.StageRunner;

/**
 * The runner one conversion's converter process is started through: it records the conversion in
 * the run's event log <strong>before</strong> the process starts, then starts it through the
 * process service as a logged stage, {@code logs/limelight-converter.log} (as {@code
 * org.cometgui.workflow.pdv.PdvLaunches} records every PDV launch before it starts).
 *
 * <p>The log is written by the process service's own {@link StageRunner} -- timestamped,
 * stream-tagged, every line through {@link ProcessRedactor} over the one secret rule set -- the
 * same writer that writes {@code logs/percolator.log}. That writer never overwrites a log, so a
 * run's second conversion is logged in {@code limelight-converter.1.log}, its third in {@code
 * .2.log}, and each conversion's events name the log that is its own.
 *
 * <p>One instance serves one conversion: it starts exactly one process, and refuses a command that
 * does not run the converter JAR it was made for.
 */
final class ConverterLaunch implements ProcessRunner {

    /** The stage the process service logs the converter under: its log's name. */
    static final StageTag STAGE =
            new StageTag() {
                @Override
                public String id() {
                    return "limelight-converter";
                }

                @Override
                public String displayName() {
                    return "Limelight conversion";
                }
            };

    private final ProcessRunner processes;
    private final Clock clock;
    private final SecretRedactor redactor;
    private final RunMessageSink sink;
    private final RunLayout run;
    private final Path jar;
    private final Map<String, String> recorded;

    private RunningStage stage;
    private List<String> argv = List.of();
    private Map<String, String> payload = Map.of();

    /**
     * A runner for one conversion.
     *
     * @param processes the process service
     * @param clock dates the events and the log
     * @param redactor the one secret rule set
     * @param sink where the log's lines are shown as they arrive
     * @param run the run whose event log and log directory are written
     * @param jar the converter JAR this runner starts and nothing else
     * @param recorded what the conversion's events carry besides the command
     */
    ConverterLaunch(
            ProcessRunner processes,
            Clock clock,
            SecretRedactor redactor,
            RunMessageSink sink,
            RunLayout run,
            Path jar,
            Map<String, String> recorded) {
        this.processes = Objects.requireNonNull(processes, "processes");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.redactor = Objects.requireNonNull(redactor, "redactor");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.run = Objects.requireNonNull(run, "run");
        this.jar = Objects.requireNonNull(jar, "jar");
        this.recorded = new LinkedHashMap<>(Objects.requireNonNull(recorded, "recorded"));
    }

    @Override
    public synchronized RunningProcess start(ToolCommand command, ProcessListener listener)
            throws IOException {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(listener, "listener");
        if (stage != null || !payload.isEmpty()) {
            throw new IOException("this runner has already started its one conversion");
        }
        if (!command.argv().contains(jar.toString())) {
            throw new IOException(
                    "this runner starts the Limelight converter "
                            + jar
                            + " and nothing else, and was asked to start: "
                            + command.displayString());
        }
        Map<String, String> started = new LinkedHashMap<>(recorded);
        List<String> redacted = redactor.redactArgv(command.argv());
        started.put(LimelightConversions.ARGV_COUNT, Integer.toString(redacted.size()));
        for (int index = 0; index < redacted.size(); index++) {
            started.put(LimelightConversions.ARGV_PREFIX + index, redacted.get(index));
        }
        started.put(LimelightConversions.WORKING_DIRECTORY, command.workingDirectory().toString());
        started.put(LimelightConversions.ENVIRONMENT, environment(command.environment()));
        append(ProvenanceEventType.LIMELIGHT_CONVERTING, started);
        payload = Map.copyOf(started);
        argv = List.copyOf(redacted);
        StageRunner stages =
                new StageRunner(
                        (stageCommand, recorder) ->
                                processes.start(stageCommand, new Both(recorder, listener)),
                        clock,
                        new ProcessRedactor(redactor),
                        sink,
                        run.logsDirectory());
        RunningStage running = stages.start(STAGE, command);
        stage = running;
        return new StageProcess(running);
    }

    /**
     * Whether the conversion's process was asked for: its {@code limelight.converting} event is
     * then in the log.
     *
     * @return {@code true} once {@link #start} has recorded the conversion
     */
    synchronized boolean recorded() {
        return !payload.isEmpty();
    }

    /**
     * What the conversion's {@code limelight.converting} event carried.
     *
     * @return the payload, empty before {@link #start}
     */
    synchronized Map<String, String> payload() {
        return payload;
    }

    /**
     * The argument array that started, after redaction.
     *
     * @return it, empty before {@link #start}
     */
    synchronized List<String> argv() {
        return argv;
    }

    /**
     * The log the process service wrote for this conversion, after it was closed.
     *
     * @return the log, or empty when the process was never started as a stage, or the waiting
     *     thread was interrupted (its interrupt status is then set again)
     */
    Optional<Path> log() {
        RunningStage running;
        synchronized (this) {
            running = stage;
        }
        if (running == null) {
            return Optional.empty();
        }
        try {
            StageOutcome outcome = running.awaitOutcome();
            return Optional.of(outcome.logFile());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /**
     * Appends one event to the run's event log and closes it.
     *
     * @param type the event
     * @param values its payload
     * @throws IOException if it cannot be written
     */
    void append(ProvenanceEventType type, Map<String, String> values) throws IOException {
        try (ProvenanceEventLog log =
                ProvenanceEventLog.openAppend(run.eventLogFile(), redactor, clock)) {
            log.append(type, values);
        }
    }

    private String environment(Map<String, String> variables) {
        Map<String, String> sorted = new TreeMap<>(redactor.redactEnvironment(variables));
        List<String> lines = new ArrayList<>(sorted.size());
        for (Map.Entry<String, String> variable : sorted.entrySet()) {
            lines.add(variable.getKey() + "=" + variable.getValue());
        }
        return String.join("\n", lines);
    }

    /** The stage's recorder first, so the log is finished before the caller hears of the exit. */
    private record Both(ProcessListener first, ProcessListener second) implements ProcessListener {

        @Override
        public void onStandardOutput(String line) {
            first.onStandardOutput(line);
            second.onStandardOutput(line);
        }

        @Override
        public void onStandardError(String line) {
            first.onStandardError(line);
            second.onStandardError(line);
        }

        @Override
        public void onExit(int exitCode) {
            first.onExit(exitCode);
            second.onExit(exitCode);
        }
    }

    /** The running stage, as the process handle its caller expects. */
    private record StageProcess(RunningStage stage) implements RunningProcess {

        @Override
        public boolean isAlive() {
            return stage.isAlive();
        }

        @Override
        public int waitForExit() throws InterruptedException {
            return stage.awaitOutcome().exitCode();
        }

        @Override
        public void requestCancellation() {
            stage.requestCancellation();
        }
    }
}
