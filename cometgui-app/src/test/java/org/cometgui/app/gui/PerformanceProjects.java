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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.cometgui.app.config.ApplicationServices;
import org.cometgui.app.config.ProjectSession;
import org.cometgui.app.config.RunWiring;
import org.cometgui.app.config.SessionEngine;
import org.cometgui.app.performance.PerfRecorder;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.log.LogMessage;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.domain.secrets.SessionSecrets;
import org.cometgui.domain.tools.CapabilityEvidence;
import org.cometgui.domain.tools.DeclaredCapability;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.parser.ReleaseDefaults;
import org.cometgui.params.comet.schema.MetadataLoader;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.params.percolator.resolution.PercolatorResolver;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.provenance.manifest.ApplicationRecord;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.tools.process.RunMessageSink;
import org.cometgui.ui.viewmodel.provenance.OpenedRecord;
import org.cometgui.ui.viewmodel.provenance.ProvenancePort;
import org.cometgui.ui.viewmodel.results.OpenedResults;
import org.cometgui.ui.viewmodel.results.ResultsPort;
import org.cometgui.ui.viewmodel.results.ResultsRun;
import org.cometgui.workflow.engine.EngineServices;
import org.cometgui.workflow.engine.ReuseRefusedException;
import org.cometgui.workflow.engine.RunHandle;
import org.cometgui.workflow.engine.RunResult;
import org.cometgui.workflow.engine.StepStateListener;
import org.cometgui.workflow.engine.WorkflowEngine;
import org.cometgui.workflow.provenance.RunListing;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.StepState;
import org.cometgui.workflow.steps.CometSelection;
import org.cometgui.workflow.steps.CometWorkflow;
import org.cometgui.workflow.steps.PercolatorChoice;
import org.cometgui.workflow.steps.PercolatorSelection;
import org.cometgui.workflow.steps.PreparedRun;
import org.cometgui.workflow.steps.RunBlockedException;
import org.cometgui.workflow.steps.SearchRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * Performance (h), the fake tool's half, and (i): cancellation latency and project reopen time,
 * through the product's own workflow, engine and process service with the stand-in tools of {@link
 * StandInRun} -- POSIX shell scripts that write what Comet and Percolator write, so the runs are
 * real runs of record ({@code run.json}, the event log, a sealed {@code provenance.json}) made in
 * seconds and with no spectra.
 *
 * <p><strong>{@code cancel-fake-tool}</strong>: a search whose stand-in Percolator, its files
 * written, starts a child ({@code /bin/sleep}) and waits for it, so only a cancellation ends the
 * stage. Once the tool has printed both process ids and the engine reports {@code run-percolator}
 * running, the tool's process tree is snapshotted, the run's {@link RunHandle#cancel()} is called
 * -- what the Run section's Cancel does -- and the sample is the time until every process of the
 * tree is gone (exited, or a zombie, which {@link StandInRun#running} counts as gone). Each sample
 * checks the attempt ended {@code CANCELLED}.
 *
 * <p><strong>{@code project-reopen}</strong>: a project holding {@code reopen.runs} sealed runs is
 * made first, each by the engine with the stand-in tools and one generated spectrum file of {@code
 * reopen.spectrumBytes} bytes. Each sample then reopens it as a relaunched application does: a new
 * {@link ProjectSession} over the directory, the composition root's ports for the Results and
 * Provenance sections (built before the clock starts, as the window's composition is), and timed:
 * the project's lock taken, both sections' run lists read, and the most recent run opened in both
 * -- its result tables' stores and its sealed record. Checks: every run is listed in both, every
 * record is sealed, and the opened record has no problem.
 *
 * <p>Opt-in (P15-5). POSIX only: the stand-in tools are shell scripts.
 */
@EnabledOnOs(
        value = {OS.LINUX, OS.MAC},
        disabledReason = "the stand-in tools are POSIX shell scripts")
@EnabledIfSystemProperty(
        named = PerfRecorder.ENABLE,
        matches = "true",
        disabledReason = PerfRecorder.DISABLED)
class PerformanceProjects {

    static final String CANCEL = "cancel-fake-tool";

    static final String REOPEN = "project-reopen";

    /** How long a tree may take to go after Cancel before the sample fails. A bound. */
    private static final Duration CANCEL_BOUND = Duration.ofMinutes(2);

    /** How long one stand-in run may take. A bound, never a delay. */
    private static final Duration RUN_BOUND = Duration.ofMinutes(2);

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-performance", "unknown", Instant.parse("2026-11-06T00:00:00Z"));

    private static final Set<ToolCapability> PERCOLATOR_CAPABILITIES =
            Set.of(
                    ToolCapability.XML_OUTPUT,
                    ToolCapability.XML_DECOY_OUTPUT,
                    ToolCapability.PSM_TSV_OUTPUT,
                    ToolCapability.PEPTIDE_TSV_OUTPUT,
                    ToolCapability.DECOY_OUTPUT,
                    ToolCapability.WEIGHTS_OUTPUT,
                    ToolCapability.THREAD_OPTION,
                    ToolCapability.SEED_OPTION,
                    ToolCapability.TEST_FDR_OPTION,
                    ToolCapability.TRAIN_FDR_OPTION,
                    ToolCapability.MAX_ITERATIONS_OPTION,
                    ToolCapability.NO_ANALYTICS_OPTION);

    private Path work;

    private static final List<Long> STRAYS = new ArrayList<>();

    @AfterEach
    void cleanUp() throws IOException {
        for (long pid : STRAYS) {
            StandInRun.kill(pid);
        }
        PerfRecorder.delete(work);
    }

    @Test
    @DisplayName("Cancel to the fake tool's process tree gone, and a project of many runs reopened")
    void cancellationAndReopen()
            throws IOException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException,
                    RunBlockedException,
                    ReuseRefusedException {
        OptionalInt cancels = PerfRecorder.repeats(CANCEL);
        OptionalInt reopens = PerfRecorder.repeats(REOPEN);
        if (cancels.isEmpty() && reopens.isEmpty()) {
            return;
        }
        Path directory = PerfRecorder.work("projects");
        work = directory;
        if (cancels.isPresent()) {
            cancellation(directory, cancels.getAsInt());
        }
        if (reopens.isPresent()) {
            reopen(directory, reopens.getAsInt());
        }
    }

    private static void cancellation(Path work, int repeats)
            throws IOException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException,
                    RunBlockedException,
                    ReuseRefusedException {
        Path tools = work.resolve("cancel-tools");
        Path comet = StandInRun.comet(tools.resolve("comet"));
        Path percolator = StandInRun.percolator(tools.resolve("percolator"), true);
        List<Path> inputs = StandInRun.inputs(work.resolve("cancel-inputs"));
        Stand stand = new Stand(work.resolve("cancel-project"), "cancel");
        PerfRecorder.Measurement measurement = PerfRecorder.start(CANCEL, "ms");
        int notCancelled = 0;
        int notGone = 0;
        List<String> outcomes = new ArrayList<>();
        try {
            for (int sample = 0; sample < repeats; sample++) {
                Lines lines = stand.lines;
                CompletableFuture<String> childLine = lines.whenNext("child ");
                CompletableFuture<String> toolLine = lines.whenNext("pid ");
                RunHandle handle =
                        stand.start(comet, percolator, inputs.get(0), inputs.get(1).toString());
                long tool = Long.parseLong(toolLine.get(2, TimeUnit.MINUTES).substring(4).trim());
                long child = Long.parseLong(childLine.get(2, TimeUnit.MINUTES).substring(6).trim());
                assertEquals(
                        StepState.RUNNING,
                        handle.states().get(EngineStep.RUN_PERCOLATOR),
                        "the stand-in Percolator is running");
                List<Long> tree = new ArrayList<>();
                tree.add(tool);
                ProcessHandle.of(tool)
                        .ifPresent(root -> root.descendants().forEach(d -> tree.add(d.pid())));
                if (!tree.contains(child)) {
                    tree.add(child);
                }
                STRAYS.addAll(tree);
                long start = System.nanoTime();
                handle.cancel();
                boolean gone =
                        PerfRecorder.awaitCondition(
                                () -> tree.stream().noneMatch(StandInRun::running),
                                200,
                                CANCEL_BOUND.toMillis());
                measurement.sample(PerfRecorder.millisSince(start));
                if (!gone) {
                    notGone++;
                    tree.forEach(StandInRun::kill);
                }
                Optional<RunResult> result = handle.await(RUN_BOUND);
                String outcome = result.map(r -> r.outcome().name()).orElse("NOT FINISHED");
                outcomes.add(outcome);
                if (!AttemptOutcome.CANCELLED.name().equals(outcome)) {
                    notCancelled++;
                }
                measurement.evidence("treeOfSample" + sample, tree.size() + " processes");
            }
        } finally {
            stand.close();
        }
        measurement.evidence("outcomes", outcomes);
        measurement.checkEquals("every tree was gone within " + CANCEL_BOUND, 0, notGone);
        measurement.checkEquals("every attempt ended CANCELLED", 0, notCancelled);
        measurement.finish();
    }

    private static void reopen(Path work, int repeats)
            throws IOException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException,
                    RunBlockedException,
                    ReuseRefusedException {
        int runs = (int) PerfRecorder.longParameter("reopen.runs");
        long spectrumBytes = PerfRecorder.longParameter("reopen.spectrumBytes");
        Path tools = work.resolve("reopen-tools");
        Path comet = StandInRun.comet(tools.resolve("comet"));
        Path percolator = StandInRun.percolator(tools.resolve("percolator"), false);
        List<Path> inputs = StandInRun.inputs(work.resolve("reopen-inputs"));
        Path spectrum = inputs.get(0).resolveSibling("large.mzML");
        writeSpectrum(spectrum, spectrumBytes);
        Path projectDirectory = work.resolve("reopen-project");
        long makeStart = System.nanoTime();
        try (Stand stand = new Stand(projectDirectory, "run")) {
            for (int run = 0; run < runs; run++) {
                RunHandle handle =
                        stand.start(comet, percolator, spectrum, inputs.get(1).toString());
                Optional<RunResult> result = handle.await(RUN_BOUND);
                assertTrue(
                        result.isPresent() && result.get().outcome() == AttemptOutcome.SUCCEEDED,
                        "stand-in run " + run + " did not succeed: " + result);
            }
        }
        double makeMillis = PerfRecorder.millisSince(makeStart);

        PerfRecorder.Measurement measurement = PerfRecorder.start(REOPEN, "ms");
        measurement.evidence("runs", runs);
        measurement.evidence("spectrumBytes", spectrumBytes);
        measurement.evidence("projectMadeInMillis", Math.round(makeMillis));
        ApplicationServices services = ApplicationServices.forThisHost();
        int wrong = 0;
        List<String> wrongs = new ArrayList<>();
        for (int sample = 0; sample < repeats; sample++) {
            try (ProjectSession session =
                    new ProjectSession(projectDirectory, Clock.systemUTC(), services.runIds())) {
                CachingHashService hashes = RunWiring.hasher();
                SessionEngine engine =
                        RunWiring.port(
                                services,
                                new BoundedMessageLog(),
                                Optional.empty(),
                                "no Tool Manager: the performance suite reopens a project",
                                session,
                                BUILD,
                                hashes);
                ResultsPort results = RunWiring.results(services, session, hashes, BUILD, engine);
                ProvenancePort provenance =
                        RunWiring.provenance(
                                services, session, hashes, engine, new SessionSecrets());

                long start = System.nanoTime();
                session.lock();
                List<ResultsRun> listed = results.runs();
                List<RunListing> records = provenance.runs();
                ResultsRun newest =
                        listed.stream()
                                .max(Comparator.comparing(ResultsRun::created))
                                .orElseThrow();
                OpenedResults opened = results.open(newest.id());
                RunListing newestRecord =
                        records.stream()
                                .filter(r -> r.runId().equals(Optional.of(newest.id().value())))
                                .findFirst()
                                .orElseThrow();
                OpenedRecord record = provenance.open(newestRecord);
                measurement.sample(PerfRecorder.millisSince(start));

                List<String> problems = new ArrayList<>();
                if (listed.size() != runs) {
                    problems.add("the Results section lists " + listed.size());
                }
                if (records.size() != runs) {
                    problems.add("the Provenance section lists " + records.size());
                }
                long unsealed = records.stream().filter(r -> !r.sealedRecord()).count();
                if (unsealed != 0) {
                    problems.add(unsealed + " records not sealed");
                }
                if (record.recordProblem().isPresent()) {
                    problems.add("the newest record: " + record.recordProblem().get());
                }
                if (!opened.stores().containsKey(TableKind.TARGET_PSMS)) {
                    problems.add("the newest run has no target PSM store");
                }
                if (record.view().tools().isEmpty()) {
                    problems.add("the newest record names no tool");
                }
                results.close(opened);
                if (!problems.isEmpty()) {
                    wrong++;
                    wrongs.add("sample " + sample + ": " + problems);
                }
            }
        }
        measurement.evidence("problems", wrongs);
        measurement.checkEquals(
                "every sample listed all " + runs + " runs, sealed, and opened the newest",
                0,
                wrong);
        measurement.finish();
    }

    /** A generated spectrum file: never parsed by the stand-in Comet, hashed by the engine. */
    private static void writeSpectrum(Path file, long bytes) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(1 << 20);
        for (int index = 0; index < buffer.capacity(); index++) {
            buffer.put(index, (byte) ('a' + index % 26));
        }
        try (FileChannel channel =
                FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            long remaining = bytes;
            while (remaining > 0) {
                buffer.clear();
                buffer.limit((int) Math.min(buffer.capacity(), remaining));
                remaining -= buffer.remaining();
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
            }
        }
    }

    /** A project with an engine over the stand-in tools, as {@link OtherCometGui} builds one. */
    private static final class Stand implements AutoCloseable {

        private final Lines lines = new Lines();

        private final ProjectSession session;

        private final CachingHashService hashes = RunWiring.hasher();

        private final WorkflowEngine engine;

        private final CometWorkflow workflow = new CometWorkflow(hashes, BUILD);

        Stand(Path project, String prefix) {
            Clock clock = Clock.systemUTC();
            AtomicInteger next = new AtomicInteger();
            this.session =
                    new ProjectSession(
                            project,
                            clock,
                            () ->
                                    new RunId(
                                            String.format(
                                                    Locale.ROOT,
                                                    "%s-%04d",
                                                    prefix,
                                                    next.incrementAndGet())));
            this.engine =
                    new WorkflowEngine(
                            new EngineServices(
                                    new ProcessService(clock),
                                    clock,
                                    SecretRedactor.patternsOnly(),
                                    lines,
                                    hashes,
                                    Runtime.getRuntime().availableProcessors(),
                                    4));
        }

        RunHandle start(Path comet, Path percolator, Path spectrum, String fasta)
                throws IOException, RunBlockedException, ReuseRefusedException {
            ToolVersion release = ToolVersion.parse(StandInRun.COMET_RELEASE);
            CometParameters model =
                    ReleaseDefaults.load(MetadataLoader.loadBundled(), release)
                            .withText("database_name", fasta, ValueOrigin.USER)
                            .withDecoySource(
                                    DecoySource.COMET_INTERNAL_CONCATENATED, ValueOrigin.USER)
                            .withWorkflowEnforcedOutputs();
            ToolOffer offer = percolatorOffer(percolator);
            PercolatorChoice choice =
                    new PercolatorChoice(
                            new PercolatorSelection(
                                    offer, percolator, hashes.hash(percolator).sha256()),
                            PercolatorSettings.defaults(),
                            Set.of(),
                            PercolatorResolver.resolve(List.of(offer), Set.of()));
            SearchRequest request =
                    new SearchRequest(
                            model,
                            List.of(spectrum),
                            new CometSelection(
                                    comet,
                                    release,
                                    hashes.hash(comet).sha256(),
                                    false,
                                    Optional.empty()),
                            IndexMode.NONE,
                            Optional.of(choice));
            PreparedRun prepared =
                    workflow.prepare(
                            session.store(),
                            session.lock(),
                            request,
                            ApplicationRecord.capture(BUILD.version(), BUILD.commitId()));
            return workflow.start(engine, prepared, StepStateListener.NONE);
        }

        @Override
        public void close() throws IOException {
            session.close();
        }
    }

    private static ToolOffer percolatorOffer(Path executable) {
        List<DeclaredCapability> declared = new ArrayList<>();
        for (ToolCapability capability : ToolCapability.values()) {
            if (PERCOLATOR_CAPABILITIES.contains(capability)) {
                declared.add(
                        new DeclaredCapability(
                                capability,
                                CapabilityEvidence.OBSERVED_BY_EXECUTION,
                                "observed by a test's stand-in"));
            }
        }
        return new ToolOffer(
                ToolName.PERCOLATOR,
                ToolVersion.parse("3.07.1"),
                ToolOrigin.MANAGED,
                ToolInstallState.INSTALLED,
                declared,
                List.of(),
                Optional.empty(),
                Optional.of(executable),
                OptionalLong.of(1000));
    }

    /** The console stand-in: completes a future with the next line that begins so. */
    private static final class Lines implements RunMessageSink {

        private final List<Waiter> waiters = new ArrayList<>();

        @Override
        public void append(LogMessage message) {
            synchronized (waiters) {
                for (Waiter waiter : List.copyOf(waiters)) {
                    if (message.text().startsWith(waiter.prefix())) {
                        waiter.line().complete(message.text());
                        waiters.remove(waiter);
                    }
                }
            }
        }

        CompletableFuture<String> whenNext(String prefix) {
            CompletableFuture<String> line = new CompletableFuture<>();
            synchronized (waiters) {
                waiters.add(new Waiter(prefix, line));
            }
            return line;
        }

        private record Waiter(String prefix, CompletableFuture<String> line) {}
    }
}
