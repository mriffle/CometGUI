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

package org.cometgui.workflow.engine;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Pattern;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.provenance.events.ProvenanceEvent;
import org.cometgui.provenance.events.ProvenanceEventType;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.provenance.manifest.ExecutionRecord;
import org.cometgui.provenance.manifest.LogRecord;
import org.cometgui.provenance.manifest.ProvenanceStatus;
import org.cometgui.provenance.manifest.ToolRecord;
import org.cometgui.tools.process.RunningStage;
import org.cometgui.tools.process.StageOutcome;
import org.cometgui.tools.process.StageRunner;
import org.cometgui.workflow.state.EngineStep;

/**
 * What a running step can do: run its tools, learn whether it is being cancelled, and add details
 * to its {@code stage.finished} event. One context per step per attempt, built by the engine.
 *
 * <h2>Running tools</h2>
 *
 * <p>{@link #invoke} and {@link #invokeAll} are the only way a step reaches the process service.
 * Each invocation is started through the run's one {@code StageRunner}, so it gets its own
 * timestamped, stream-tagged log under the run's {@code logs/} directory, its output reaches the
 * console as it arrives, and it is cancellable with its descendants ({@code
 * RunningStage.requestCancellation}). When it has exited the engine hashes its log -- the process
 * service has closed the file by then -- and records a tool record with the invocation's own
 * argument array, start, end, exit code, log and status (P8-12, {@code AC-PRV-03}, {@code
 * AC-PRV-05}).
 *
 * <h2>One log, two log members</h2>
 *
 * <p>A provenance execution record has a {@code stdout} and a {@code stderr} log member, from a
 * specification that pictured two files per process. The process service writes <em>one</em> log
 * per invocation, with every line tagged {@code [stdout]} or {@code [stderr]} and timestamped,
 * because two files lose the interleaving (Phase 03; P8-3 records the divergence). So both members
 * name that one file with its one pair of digests: it is the archive of both streams, and a reader
 * finds either stream in it by its tag. A log that could not be hashed is left out of both members
 * and the reason is added to the tool record's warnings.
 *
 * <h2>Bounded concurrency and failure</h2>
 *
 * <p>{@link #invokeAll} runs a list of invocations at most {@link ConcurrencyBound} at a time. The
 * first invocation that does not complete -- a non-zero exit, a cancellation, a process that could
 * not start -- fails the call: invocations still running are cancelled, invocations not yet started
 * are never started, and every log already written is kept. Tool records are added in <em>list</em>
 * order, whatever order the processes finished in, so concurrency never changes the provenance
 * record ({@code R-CMT-05}).
 */
public final class StepContext {

    private static final Pattern DETAIL_KEY = Pattern.compile(ProvenanceEvent.PAYLOAD_KEY_PATTERN);

    /** Payload keys the engine writes itself into a step's events. */
    static final Set<String> RESERVED_KEYS =
            Set.of("stage", "state", "attempt", "message", "invocations");

    private final EngineStep step;

    private final StepDeclaration declaration;

    private final RunLayout layout;

    private final StageRunner runner;

    private final CachingHashService hashes;

    private final int cores;

    private final int cap;

    private final EventRecorder events;

    private final Object lock = new Object();

    /** Guarded by {@link #lock}. */
    private boolean cancelled;

    /** Guarded by {@link #lock}: the invocations started and not yet finished. */
    private final Set<RunningStage> active = new HashSet<>();

    /** Guarded by {@link #lock}: every recorded invocation, in the order the step made them. */
    private final List<ToolRecord> tools = new ArrayList<>();

    /** Guarded by {@link #lock}. */
    private final Map<String, String> details = new TreeMap<>();

    StepContext(
            EngineStep step,
            StepDeclaration declaration,
            RunLayout layout,
            StageRunner runner,
            CachingHashService hashes,
            int cores,
            int cap,
            EventRecorder events) {
        this.step = Objects.requireNonNull(step, "step");
        this.declaration = Objects.requireNonNull(declaration, "declaration");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.runner = Objects.requireNonNull(runner, "runner");
        this.hashes = Objects.requireNonNull(hashes, "hashes");
        this.cores = cores;
        this.cap = cap;
        this.events = Objects.requireNonNull(events, "events");
    }

    /**
     * The step this context runs.
     *
     * @return the step
     */
    public EngineStep step() {
        return step;
    }

    /**
     * The run's directory.
     *
     * @return the layout
     */
    public RunLayout layout() {
        return layout;
    }

    /**
     * Whether the run has been asked to cancel. A step doing long work in Java checks this and
     * throws {@link StepFailedException} to stop; a step running tools need not, because the engine
     * cancels them.
     *
     * @return {@code true} once the run is cancelling
     */
    public boolean isCancellationRequested() {
        synchronized (lock) {
            return cancelled;
        }
    }

    /**
     * Runs one invocation and waits for it.
     *
     * @param invocation what to run; its stage identifier must be declared by the step
     * @return how it ended, if it completed
     * @throws StepFailedException if it exited non-zero, was cancelled or could not start
     * @throws IOException never in practice; declared for symmetry with {@link #invokeAll}
     * @throws InterruptedException if the waiting thread is interrupted; the invocation is then
     *     cancelled
     */
    public InvocationResult invoke(Invocation invocation)
            throws StepFailedException, IOException, InterruptedException {
        return invokeAll(List.of(invocation), 1).get(0);
    }

    /**
     * Runs a list of invocations with bounded concurrency and waits for them.
     *
     * @param invocations what to run, in the order provenance records them; each stage identifier
     *     must be declared by the step
     * @param threadsPerInvocation the threads each invocation uses, which bounds how many run at
     *     once; zero or less means all cores
     * @return how each ended, in list order
     * @throws StepFailedException naming the first invocation that did not complete
     * @throws IOException never in practice; a failure to start is reported as a step failure
     * @throws InterruptedException if the waiting thread is interrupted; every running invocation
     *     is then cancelled
     * @throws IllegalArgumentException if an invocation's stage identifier is not declared, or its
     *     tool could not be recorded in provenance -- before anything is started
     */
    public List<InvocationResult> invokeAll(List<Invocation> invocations, int threadsPerInvocation)
            throws StepFailedException, IOException, InterruptedException {
        List<Invocation> list = List.copyOf(invocations);
        for (Invocation invocation : list) {
            requireRecordable(invocation);
        }
        int bound = ConcurrencyBound.of(list.size(), cores, threadsPerInvocation, cap);
        BlockingQueue<Finished> finished = new LinkedBlockingQueue<>();
        InvocationResult[] results = new InvocationResult[list.size()];
        ToolRecord[] records = new ToolRecord[list.size()];
        int next = 0;
        int inFlight = 0;
        String failure = null;
        try {
            while (true) {
                while (failure == null && next < list.size() && inFlight < bound) {
                    int index = next;
                    next++;
                    Invocation invocation = list.get(index);
                    Optional<RunningStage> started;
                    try {
                        started = startUnlessCancelled(invocation);
                    } catch (IOException | RuntimeException notStarted) {
                        failure = "could not start " + invocation.stageId() + ": " + notStarted;
                        cancelRunning();
                        break;
                    }
                    if (started.isEmpty()) {
                        failure =
                                "the run was cancelled before " + invocation.stageId() + " started";
                        break;
                    }
                    inFlight++;
                    RunningStage stage = started.get();
                    Thread.ofVirtual()
                            .name("cometgui-invocation-" + invocation.stageId())
                            .start(() -> finished.add(new Finished(index, stage, await(stage))));
                }
                if (inFlight == 0) {
                    break;
                }
                Finished done = finished.take();
                inFlight--;
                synchronized (lock) {
                    active.remove(done.stage());
                }
                Invocation invocation = list.get(done.index());
                results[done.index()] = resultOf(invocation, done.outcome());
                records[done.index()] = record(invocation, done.outcome(), results[done.index()]);
                if (failure == null
                        && results[done.index()].status() != ProvenanceStatus.COMPLETED) {
                    failure = describeFailure(results[done.index()]);
                    cancelRunning();
                }
            }
        } catch (InterruptedException interrupted) {
            cancelRunning();
            throw interrupted;
        } finally {
            synchronized (lock) {
                for (ToolRecord record : records) {
                    if (record != null) {
                        tools.add(record);
                    }
                }
            }
        }
        if (failure != null) {
            throw new StepFailedException(failure);
        }
        return List.of(results);
    }

    /**
     * Adds a detail to this step's {@code stage.finished} event -- for example the PIN merge's
     * input row counts (P8-12).
     *
     * @param key a payload key matching {@link ProvenanceEvent#PAYLOAD_KEY_PATTERN}, not one the
     *     engine writes itself
     * @param value the value
     * @throws IllegalArgumentException if the key is malformed or reserved, quoting it
     */
    public void addDetail(String key, String value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        if (!DETAIL_KEY.matcher(key).matches() || RESERVED_KEYS.contains(key)) {
            throw new IllegalArgumentException(
                    "a step detail key must match "
                            + ProvenanceEvent.PAYLOAD_KEY_PATTERN
                            + " and not be one of "
                            + new TreeSet<>(RESERVED_KEYS)
                            + ", but was: \""
                            + key
                            + "\"");
        }
        synchronized (lock) {
            details.put(key, value);
        }
    }

    /** Asks every running invocation to stop and refuses to start any more. */
    void requestCancellation() {
        synchronized (lock) {
            cancelled = true;
            for (RunningStage stage : active) {
                stage.requestCancellation();
            }
        }
    }

    List<ToolRecord> toolRecords() {
        synchronized (lock) {
            return List.copyOf(tools);
        }
    }

    Map<String, String> details() {
        synchronized (lock) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(details));
        }
    }

    private void cancelRunning() {
        synchronized (lock) {
            for (RunningStage stage : active) {
                stage.requestCancellation();
            }
        }
    }

    private Optional<RunningStage> startUnlessCancelled(Invocation invocation) throws IOException {
        RunningStage stage = null;
        synchronized (lock) {
            if (!cancelled) {
                stage =
                        runner.start(
                                new InvocationTag(
                                        invocation.stageId(),
                                        invocation.stageId() + " (" + step.displayName() + ")"),
                                invocation.command());
                active.add(stage);
            }
        }
        // Returned outside the monitor, for the reason RunExecution.advance gives.
        return Optional.ofNullable(stage);
    }

    /**
     * Refuses, before anything starts, an invocation the step did not declare or whose tool the
     * provenance record would reject -- so a programming error can never leave a process running
     * that nothing will record.
     */
    private void requireRecordable(Invocation invocation) {
        if (!declaration.declares(invocation.stageId())) {
            throw new IllegalArgumentException(
                    "step "
                            + step.id()
                            + " did not declare the invocation "
                            + invocation.stageId());
        }
        toolRecord(
                invocation,
                new ExecutionRecord(
                        invocation.command(),
                        Instant.EPOCH,
                        Instant.EPOCH,
                        0,
                        Optional.empty(),
                        Optional.empty(),
                        ProvenanceStatus.COMPLETED),
                List.of());
    }

    /**
     * Waits for an invocation's outcome on the invocation's own waiter thread. That thread belongs
     * to this class and nothing interrupts it; should something do so, the wait simply resumes,
     * because the outcome always arrives -- the process service completes it when the process
     * exits, and cancellation makes it exit.
     */
    private static StageOutcome await(RunningStage stage) {
        while (true) {
            try {
                return stage.awaitOutcome();
            } catch (InterruptedException resumed) {
                // Keep waiting; see above.
            }
        }
    }

    private static InvocationResult resultOf(Invocation invocation, StageOutcome outcome) {
        ProvenanceStatus status;
        if (outcome.exitCode() == 0) {
            status = ProvenanceStatus.COMPLETED;
        } else if (outcome.cancellationRequested()) {
            status = ProvenanceStatus.CANCELLED;
        } else {
            status = ProvenanceStatus.FAILED;
        }
        return new InvocationResult(
                invocation.stageId(),
                outcome.exitCode(),
                status,
                outcome.logFile(),
                outcome.startedAt(),
                outcome.endedAt());
    }

    private ToolRecord record(
            Invocation invocation, StageOutcome outcome, InvocationResult result) {
        List<String> warnings = new ArrayList<>();
        Optional<LogRecord> log;
        try {
            Path file = outcome.logFile().toAbsolutePath().normalize();
            FileHashes logHashes = hashes.rehash(file);
            log = Optional.of(new LogRecord(file, logHashes));
        } catch (IOException unreadable) {
            log = Optional.empty();
            warnings.add("the log " + outcome.logFile() + " could not be hashed: " + unreadable);
        }
        ToolRecord record =
                toolRecord(
                        invocation,
                        new ExecutionRecord(
                                invocation.command(),
                                result.start(),
                                result.end(),
                                result.exitCode(),
                                log,
                                log,
                                result.status()),
                        warnings);
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put(ProvenanceEvent.STAGE_KEY, invocation.stageId());
        payload.put("step", step.id());
        payload.put(ProvenanceEvent.TOOL_KEY, invocation.tool().name());
        payload.put(ProvenanceEvent.TOOL_VERSION_KEY, invocation.tool().version());
        payload.put(ProvenanceEvent.STATUS_KEY, result.status().wireName());
        payload.put("exit", Integer.toString(result.exitCode()));
        events.record(ProvenanceEventType.TOOL_INVOKED, payload);
        return record;
    }

    private static ToolRecord toolRecord(
            Invocation invocation, ExecutionRecord execution, List<String> extraWarnings) {
        ToolIdentity tool = invocation.tool();
        List<String> warnings = new ArrayList<>(tool.warnings());
        warnings.addAll(extraWarnings);
        return new ToolRecord(
                tool.name(),
                tool.version(),
                tool.releaseTag(),
                tool.executablePath(),
                tool.hashes(),
                tool.managed(),
                tool.artefactIdentity(),
                tool.capabilities(),
                Optional.of(invocation.stageId()),
                execution,
                warnings);
    }

    private static String describeFailure(InvocationResult result) {
        String how =
                result.status() == ProvenanceStatus.CANCELLED
                        ? " was cancelled (exit code " + result.exitCode() + ")"
                        : " exited with code " + result.exitCode();
        return "invocation " + result.stageId() + how + "; its log is " + result.logFile();
    }

    /** One invocation's outcome, delivered to the step's thread by the invocation's waiter. */
    private record Finished(int index, RunningStage stage, StageOutcome outcome) {}
}
