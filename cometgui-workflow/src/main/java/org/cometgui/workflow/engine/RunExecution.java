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
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.RunDescriptor;
import org.cometgui.domain.run.RunIdentity;
import org.cometgui.provenance.events.ProvenanceEvent;
import org.cometgui.provenance.events.ProvenanceEventLog;
import org.cometgui.provenance.events.ProvenanceEventType;
import org.cometgui.provenance.manifest.FileDirection;
import org.cometgui.provenance.manifest.FileRecord;
import org.cometgui.provenance.manifest.ManifestWriter;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ProvenanceStatus;
import org.cometgui.provenance.manifest.RunRecord;
import org.cometgui.provenance.manifest.ToolRecord;
import org.cometgui.provenance.report.ProvenanceReportWriter;
import org.cometgui.tools.process.ProcessRedactor;
import org.cometgui.tools.process.StageRunner;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.Plan;
import org.cometgui.workflow.state.RerunPreview;
import org.cometgui.workflow.state.RunState;
import org.cometgui.workflow.state.StepState;

/**
 * One attempt in progress: the step states, the dispatching, the cancellation and the finalisation.
 * Everything that changes a step's state does so under {@link #lock}, in one place ({@link
 * #transition}), which is what makes the order of listener callbacks and event-log records the
 * order the changes happened.
 */
final class RunExecution implements EventRecorder {

    private final EngineServices services;

    private final RunRequest request;

    private final Plan plan;

    private final Map<EngineStep, StepDeclaration> declarations;

    private final StepStateListener listener;

    private final StageRunner runner;

    private final ProvenanceEventLog events;

    private final ProvenanceLedger ledger = new ProvenanceLedger();

    private final ExecutorService workers;

    private final ExecutorService notifier;

    private final CompletableFuture<RunResult> result = new CompletableFuture<>();

    private final List<String> recordingErrors = Collections.synchronizedList(new ArrayList<>());

    private final AtomicLong listenerFailures = new AtomicLong();

    private final Instant started;

    private final int attempt;

    private final Object lock = new Object();

    /** Guarded by {@link #lock}. */
    private final Map<EngineStep, StepState> states = new EnumMap<>(EngineStep.class);

    /** Guarded by {@link #lock}: the steps dispatched and not yet concluded. */
    private final Map<EngineStep, StepContext> inFlight = new EnumMap<>(EngineStep.class);

    /** Guarded by {@link #lock}. */
    private final Map<EngineStep, String> failures = new EnumMap<>(EngineStep.class);

    /** Guarded by {@link #lock}. */
    private boolean cancelRequested;

    /** Guarded by {@link #lock}. */
    private boolean failed;

    /** Guarded by {@link #lock}. */
    private boolean finishing;

    private final Object journalLock = new Object();

    /** Guarded by {@link #journalLock}: the run's record as last written. */
    private RunDescriptor descriptor;

    private RunExecution(
            EngineServices services,
            RunRequest request,
            Map<EngineStep, StepDeclaration> declarations,
            StepStateListener listener,
            ProvenanceEventLog events,
            RunDescriptor descriptor,
            Instant started) {
        this.services = services;
        this.request = request;
        this.plan = request.plan();
        this.declarations = declarations;
        this.listener = listener;
        this.events = events;
        this.descriptor = descriptor;
        this.started = started;
        this.attempt = descriptor.attempts().size();
        this.runner =
                new StageRunner(
                        services.processes(),
                        services.clock(),
                        new ProcessRedactor(services.redactor()),
                        services.sink(),
                        request.layout().logsDirectory());
        this.workers = Executors.newCachedThreadPool(daemons("cometgui-step-"));
        this.notifier = Executors.newSingleThreadExecutor(daemons("cometgui-run-events-"));
    }

    /**
     * Records the attempt, opens the event log, marks reused steps and dispatches the first steps.
     */
    static RunExecution begin(
            EngineServices services,
            RunRequest request,
            Map<EngineStep, StepDeclaration> declarations,
            ReuseCheck check,
            StepStateListener listener)
            throws IOException {
        RunDescriptor recorded = request.store().read(request.layout());
        Instant started = services.clock().instant();
        ProvenanceEventLog events =
                ProvenanceEventLog.openAppend(
                        request.layout().eventLogFile(), services.redactor(), services.clock());
        RunDescriptor withAttempt;
        try {
            withAttempt = request.store().update(request.lock(), recorded.withNewAttempt(started));
        } catch (IOException | RuntimeException notRecorded) {
            events.close();
            throw notRecorded;
        }
        RunExecution execution =
                new RunExecution(
                        services, request, declarations, listener, events, withAttempt, started);
        execution.start(check);
        return execution;
    }

    private void start(ReuseCheck check) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put(ProvenanceEvent.RUN_ID_KEY, runIdentity().runId().value());
        payload.put("attempt", Integer.toString(attempt));
        payload.put("plan", planIds());
        record(ProvenanceEventType.RUN_STARTED, payload);
        synchronized (lock) {
            for (EngineStep step : plan.steps()) {
                states.put(step, StepState.NOT_STARTED);
            }
            for (EngineStep step : plan.steps()) {
                if (!check.preview().verdict(step).executes()) {
                    ledger.put(
                            step,
                            check.carriedTools().getOrDefault(step, List.of()),
                            check.verifiedFiles().getOrDefault(step, List.of()));
                    transition(step, StepState.SKIPPED, Map.of());
                }
            }
            dispatchReady(check.preview());
            finishIfSettled();
        }
    }

    // ------------------------------------------------------------------ dispatching --

    /** Starts every step whose upstream steps are done. Called with {@link #lock} held. */
    private void dispatchReady(RerunPreview rerun) {
        if (cancelRequested || failed) {
            return;
        }
        for (EngineStep step : plan.steps()) {
            if (states.get(step) == StepState.NOT_STARTED
                    && rerun.verdict(step).executes()
                    && upstreamDone(step)) {
                StepAction action = request.actions().get(step);
                StepContext context =
                        new StepContext(
                                step,
                                declarations.get(step),
                                request.layout(),
                                runner,
                                services.hashes(),
                                services.availableCores(),
                                services.invocationCap(),
                                this);
                inFlight.put(step, context);
                transition(
                        step,
                        action.validates() ? StepState.VALIDATING : StepState.READY,
                        Map.of());
                workers.execute(() -> work(step, action, context, rerun));
            }
        }
    }

    private boolean upstreamDone(EngineStep step) {
        for (EngineStep upstream : plan.upstreamOf(step)) {
            StepState state = states.get(upstream);
            if (state != StepState.SUCCEEDED && state != StepState.SKIPPED) {
                return false;
            }
        }
        return true;
    }

    private void work(EngineStep step, StepAction action, StepContext context, RerunPreview rerun) {
        boolean succeeded = false;
        String message = null;
        try {
            boolean proceed = true;
            if (action.validates()) {
                action.validate(context);
                proceed = advance(step, StepState.READY);
            }
            if (proceed && advance(step, StepState.RUNNING)) {
                action.execute(context);
                succeeded = true;
            } else {
                message = "the run was cancelled before " + step.id() + " started its work";
            }
        } catch (StepFailedException | IOException | RuntimeException failure) {
            message = String.valueOf(failure.getMessage());
        } catch (InterruptedException interrupted) {
            // The engine never interrupts its own workers, so this interrupt was the action's
            // own signal and ends with the action. It is cleared rather than re-asserted: the
            // worker still has to hash the step's files and write run.json, and an interrupted
            // thread's file channels close themselves (ClosedByInterruptException) -- the step's
            // record would be lost to an interrupt nobody outside the action asked for.
            Thread.interrupted();
            message = step.id() + " was interrupted";
        }
        conclude(step, context, succeeded, message, rerun);
    }

    /**
     * Moves a dispatched step forward, unless it has been asked to cancel.
     *
     * @return {@code false} if the step is {@code CANCEL_REQUESTED} and must not proceed
     */
    private boolean advance(EngineStep step, StepState next) {
        boolean proceed;
        synchronized (lock) {
            proceed = states.get(step) != StepState.CANCEL_REQUESTED;
            if (proceed) {
                transition(step, next, Map.of());
            }
        }
        // Returned outside the monitor: a return inside a synchronized block compiles to a shape
        // PIT reports as an unkillable "replaced return value" mutation of that very statement.
        return proceed;
    }

    /**
     * Ends a step: hashes its declared files now that its processes have exited, records its
     * fingerprint if it succeeded, and moves it to its terminal state.
     */
    private void conclude(
            EngineStep step,
            StepContext context,
            boolean actionSucceeded,
            String actionMessage,
            RerunPreview rerun) {
        boolean succeeded = actionSucceeded;
        String message = actionMessage;
        StepDeclaration declaration = declarations.get(step);
        if (succeeded) {
            Optional<DeclaredFile> absent = firstAbsent(declaration);
            if (absent.isPresent()) {
                succeeded = false;
                message =
                        step.id()
                                + " reported success, but its declared "
                                + FileFacts.roleOf(absent.get())
                                + " "
                                + absent.get().path()
                                + " does not exist";
            }
        }
        Map<DeclaredFile, FileHashes> hashed = new LinkedHashMap<>();
        for (DeclaredFile file : declaration.files()) {
            if (Files.isRegularFile(file.path())) {
                try {
                    hashed.put(file, hashOf(file));
                } catch (IOException unreadable) {
                    recordingErrors.add("could not hash " + file.path() + ": " + unreadable);
                    if (succeeded) {
                        succeeded = false;
                        message = "could not hash " + file.path() + ": " + unreadable;
                    }
                }
            }
        }
        if (succeeded) {
            try {
                recordSucceeded(step, rerun);
            } catch (IOException | RuntimeException notRecorded) {
                succeeded = false;
                message = "could not record that " + step.id() + " succeeded: " + notRecorded;
            }
        }
        List<FileRecord> files = new ArrayList<>();
        for (Map.Entry<DeclaredFile, FileHashes> entry : hashed.entrySet()) {
            DeclaredFile file = entry.getKey();
            ProvenanceStatus status =
                    file.direction() == FileDirection.OUTPUT && !succeeded
                            ? ProvenanceStatus.PARTIAL
                            : ProvenanceStatus.COMPLETED;
            try {
                FileRecord recorded = FileFacts.record(file, entry.getValue(), status);
                files.add(recorded);
                recordHashed(step, recorded);
            } catch (IOException unreadable) {
                recordingErrors.add("could not describe " + file.path() + ": " + unreadable);
            }
        }
        List<ToolRecord> tools = context.toolRecords();
        ledger.put(step, tools, files);
        synchronized (lock) {
            StepState end;
            if (succeeded) {
                end = StepState.SUCCEEDED;
            } else if (cancelRequested) {
                end = StepState.CANCELLED;
            } else {
                end = StepState.FAILED;
                failed = true;
            }
            Map<String, String> payload = new LinkedHashMap<>(context.details());
            List<String> invocations = new ArrayList<>();
            for (ToolRecord tool : tools) {
                invocations.add(tool.stageId().orElse(""));
            }
            payload.put("invocations", String.join(" ", invocations));
            if (!succeeded) {
                payload.put(ProvenanceEvent.MESSAGE_KEY, message);
                failures.put(step, message);
            }
            inFlight.remove(step);
            transition(step, end, payload);
            dispatchReady(rerun);
            finishIfSettled();
        }
    }

    private static Optional<DeclaredFile> firstAbsent(StepDeclaration declaration) {
        for (DeclaredFile file : declaration.files()) {
            if (!Files.isRegularFile(file.path())) {
                return Optional.of(file);
            }
        }
        return Optional.empty();
    }

    /**
     * An output was just written by a process that has exited, so it is read again whatever the
     * cache holds; an input may be served by the cache, which revalidates it on every use.
     */
    private FileHashes hashOf(DeclaredFile file) throws IOException {
        return file.direction() == FileDirection.OUTPUT
                ? services.hashes().rehash(file.path())
                : services.hashes().hash(file.path());
    }

    private void recordHashed(EngineStep step, FileRecord file) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put(ProvenanceEvent.STAGE_KEY, step.id());
        payload.put(ProvenanceEvent.FILE_PATH_KEY, file.path().toString());
        payload.put(ProvenanceEvent.FILE_MD5_KEY, file.hashes().md5());
        payload.put(ProvenanceEvent.FILE_SHA256_KEY, file.hashes().sha256());
        payload.put("direction", file.direction().wireName());
        payload.put("role", file.role());
        payload.put(ProvenanceEvent.STATUS_KEY, file.status().wireName());
        record(ProvenanceEventType.FILE_HASHED, payload);
    }

    private void recordSucceeded(EngineStep step, RerunPreview rerun) throws IOException {
        synchronized (journalLock) {
            descriptor =
                    request.store()
                            .update(
                                    request.lock(),
                                    descriptor.withStepSucceeded(
                                            step.id(),
                                            RecordedFingerprints.toRecorded(
                                                    rerun.fingerprints().get(step))));
        }
    }

    // ------------------------------------------------------------------ cancellation --

    void cancel() {
        synchronized (lock) {
            if (finishing || cancelRequested) {
                return;
            }
            cancelRequested = true;
            for (Map.Entry<EngineStep, StepContext> running : inFlight.entrySet()) {
                transition(running.getKey(), StepState.CANCEL_REQUESTED, Map.of());
                running.getValue().requestCancellation();
            }
        }
    }

    // ------------------------------------------------------------------ transitions --

    /** Changes one step's state, in order, everywhere it is observed. Called with lock held. */
    private void transition(EngineStep step, StepState to, Map<String, String> details) {
        StepState from = states.get(step);
        if (!StateWireNames.allowed(from, to)) {
            throw new IllegalStateException(
                    "step " + step.id() + " cannot move from " + from + " to " + to);
        }
        states.put(step, to);
        Instant at = services.clock().instant();
        RunState runState = RunState.deriveFrom(plan, states);
        Map<String, String> payload = new LinkedHashMap<>(details);
        payload.put(ProvenanceEvent.STAGE_KEY, step.id());
        payload.put("state", StateWireNames.of(to));
        payload.put("attempt", Integer.toString(attempt));
        record(
                to.isTerminal()
                        ? ProvenanceEventType.STAGE_FINISHED
                        : ProvenanceEventType.STAGE_STARTED,
                payload);
        StepTransition transition = new StepTransition(step, from, to, at, runState);
        notifier.execute(() -> deliver(transition));
    }

    private void deliver(StepTransition transition) {
        try {
            listener.onTransition(transition);
        } catch (RuntimeException thrown) {
            listenerFailures.incrementAndGet();
        }
    }

    @Override
    public void record(ProvenanceEventType type, Map<String, String> payload) {
        try {
            events.append(type, payload);
        } catch (IOException | RuntimeException notAppended) {
            recordingErrors.add("could not append " + type.wireName() + ": " + notAppended);
        }
    }

    // ------------------------------------------------------------------ finishing --

    /**
     * Starts finalising once nothing is in flight. Called with {@link #lock} held, after every
     * dispatch.
     */
    private void finishIfSettled() {
        if (finishing || !inFlight.isEmpty()) {
            return;
        }
        finishing = true;
        if (cancelRequested
                && !states.containsValue(StepState.CANCELLED)
                && !states.containsValue(StepState.FAILED)) {
            for (EngineStep step : plan.steps()) {
                if (states.get(step) == StepState.NOT_STARTED && upstreamDone(step)) {
                    String message = "the run was cancelled before " + step.id() + " started";
                    failures.put(step, message);
                    transition(
                            step,
                            StepState.CANCELLED,
                            Map.of(ProvenanceEvent.MESSAGE_KEY, message));
                }
            }
        }
        Map<EngineStep, StepState> finalStates = new EnumMap<>(states);
        Map<EngineStep, String> finalFailures = new EnumMap<>(failures);
        workers.execute(() -> finish(finalStates, finalFailures));
    }

    private void finish(
            Map<EngineStep, StepState> finalStates, Map<EngineStep, String> finalFailures) {
        try {
            RunState runState = RunState.deriveFrom(plan, finalStates);
            AttemptOutcome outcome =
                    switch (runState) {
                        case SUCCEEDED -> AttemptOutcome.SUCCEEDED;
                        case CANCELLED -> AttemptOutcome.CANCELLED;
                        default -> AttemptOutcome.FAILED;
                    };
            ProvenanceStatus status =
                    switch (outcome) {
                        case SUCCEEDED -> ProvenanceStatus.COMPLETED;
                        case CANCELLED -> ProvenanceStatus.CANCELLED;
                        default -> ProvenanceStatus.FAILED;
                    };
            Instant ended = services.clock().instant();
            RunIdentity identity = runIdentity();
            Map<String, String> settings = new TreeMap<>(request.settings());
            settings.put(WorkflowEngine.ATTEMPT_SETTING, Integer.toString(attempt));
            settings.put(WorkflowEngine.PLAN_SETTING, planIds());
            ProvenanceManifest manifest =
                    ledger.manifest(
                            plan,
                            new RunRecord(
                                    identity.runId(),
                                    identity.projectId().value(),
                                    status,
                                    started,
                                    Optional.of(ended)),
                            request.application(),
                            settings);
            try {
                ManifestWriter.redactingWith(services.redactor())
                        .writeTo(request.layout().provenanceJsonFile(), manifest);
            } catch (IOException | RuntimeException notWritten) {
                recordingErrors.add("could not write provenance.json: " + notWritten);
            }
            try {
                ProvenanceReportWriter.redactingWith(services.redactor())
                        .writeTo(request.layout().provenanceRstFile(), manifest);
            } catch (IOException | RuntimeException notWritten) {
                recordingErrors.add("could not write provenance.rst: " + notWritten);
            }
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put(ProvenanceEvent.STATUS_KEY, status.wireName());
            payload.put(ProvenanceEvent.RUN_ID_KEY, identity.runId().value());
            payload.put("attempt", Integer.toString(attempt));
            record(ProvenanceEventType.RUN_FINISHED, payload);
            try {
                synchronized (journalLock) {
                    descriptor =
                            request.store()
                                    .update(
                                            request.lock(),
                                            descriptor.withAttemptFinished(outcome, ended));
                }
            } catch (IOException | RuntimeException notRecorded) {
                recordingErrors.add("could not record the attempt's end: " + notRecorded);
            }
            closeEvents();
            notifier.execute(
                    () -> {
                        RunResult done =
                                new RunResult(
                                        attempt,
                                        outcome,
                                        runState,
                                        finalStates,
                                        finalFailures,
                                        manifest,
                                        List.copyOf(recordingErrors),
                                        listenerFailures.get());
                        try {
                            listener.onRunFinished(done);
                        } catch (RuntimeException thrown) {
                            listenerFailures.incrementAndGet();
                        }
                        result.complete(done);
                    });
        } catch (RuntimeException unexpected) {
            closeEvents();
            result.completeExceptionally(unexpected);
        } finally {
            notifier.shutdown();
            workers.shutdown();
        }
    }

    private void closeEvents() {
        try {
            events.close();
        } catch (IOException notClosed) {
            recordingErrors.add("could not close the event log: " + notClosed);
        }
    }

    // ------------------------------------------------------------------ queries --

    RunResult await() throws InterruptedException {
        try {
            return result.get();
        } catch (ExecutionException broken) {
            throw new IllegalStateException("the run could not be finalised", broken.getCause());
        }
    }

    Optional<RunResult> await(Duration timeout) throws InterruptedException {
        try {
            return Optional.of(result.get(timeout.toMillis(), TimeUnit.MILLISECONDS));
        } catch (TimeoutException notYet) {
            return Optional.empty();
        } catch (ExecutionException broken) {
            throw new IllegalStateException("the run could not be finalised", broken.getCause());
        }
    }

    Map<EngineStep, StepState> states() {
        synchronized (lock) {
            return Collections.unmodifiableMap(new EnumMap<>(states));
        }
    }

    Plan plan() {
        return plan;
    }

    int attempt() {
        return attempt;
    }

    private RunIdentity runIdentity() {
        synchronized (journalLock) {
            return descriptor.identity();
        }
    }

    private String planIds() {
        List<String> ids = new ArrayList<>();
        for (EngineStep step : plan.steps()) {
            ids.add(step.id());
        }
        return String.join(" ", ids);
    }

    private static ThreadFactory daemons(String prefix) {
        AtomicLong count = new AtomicLong();
        return task -> {
            Thread thread = new Thread(task, prefix + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
