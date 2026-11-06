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

package org.cometgui.app.matrix;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.cometgui.app.config.ApplicationServices;
import org.cometgui.app.config.ProjectSession;
import org.cometgui.app.config.RunWiring;
import org.cometgui.app.config.SessionEngine;
import org.cometgui.app.matrix.VersionMatrixPairs.Fixture;
import org.cometgui.app.performance.PerfRecorder;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.parser.ReleaseDefaults;
import org.cometgui.params.comet.schema.MetadataLoader;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.params.percolator.resolution.DownstreamStage;
import org.cometgui.params.percolator.resolution.PercolatorResolution;
import org.cometgui.params.percolator.resolution.PercolatorResolver;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.ui.viewmodel.params.ActiveRun;
import org.cometgui.ui.viewmodel.params.PercolatorRequest;
import org.cometgui.ui.viewmodel.params.RunNotStartedException;
import org.cometgui.ui.viewmodel.params.RunObserver;
import org.cometgui.workflow.engine.RunResult;
import org.cometgui.workflow.engine.StepTransition;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.Plan;
import org.cometgui.workflow.state.StepState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Performance (e), (f) and (h) over real spectra: the whole Comet -> Percolator search through the
 * product's run port ({@code RunWiring.port}, as the Run section starts it) over the one named
 * fixture input, {@code COMETGUI_MATRIX_FIXTURE} ({@value MatrixSupport#FIXTURE}). Without it this
 * class FAILS, saying so, and nothing about it is green: {@code D-006}'s CI fixture is OPEN.
 *
 * <ul>
 *   <li>{@code comet-fixture} (informational: the specification gives Comet's time no threshold)
 *       and {@code percolator-fixture}: each sample is one search in a fresh project with the
 *       product's default Comet parameters and Percolator settings; the samples are the engine's
 *       own {@code run-comet} and {@code run-percolator} steps, from {@code RUNNING} to {@code
 *       SUCCEEDED}, as its transitions date them. Every search must succeed.
 *   <li>{@code cancel-comet}: a search started the same way; once a Comet process of the installed
 *       executable has run for a second, every Comet process of the search and its descendants are
 *       snapshotted, the run's Cancel is called, and the sample is the time until all of them are
 *       gone. The attempt must end {@code CANCELLED}.
 * </ul>
 *
 * <p>The pair is the runner's ({@code fixture.comet}, {@code fixture.percolator}): the product's
 * own choice for a Limelight-enabled run. Both are installed from their pinned upstream URLs into
 * an empty cache by the product's Tool Manager. Opt-in (P15-5).
 */
@EnabledIfSystemProperty(
        named = PerfRecorder.ENABLE,
        matches = "true",
        disabledReason = PerfRecorder.DISABLED)
class PerformanceFixture {

    static final String COMET = "comet-fixture";

    static final String PERCOLATOR = "percolator-fixture";

    static final String CANCEL = "cancel-comet";

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-performance", "unknown", Instant.parse("2026-11-06T00:00:00Z"));

    private static final Duration RUN_BOUND = Duration.ofMinutes(30);

    private static final Duration CANCEL_BOUND = Duration.ofMinutes(2);

    private Path work;

    @AfterEach
    void cleanUp() throws IOException {
        MatrixSupport.restoreGuard();
        PerfRecorder.delete(work);
    }

    @Test
    @DisplayName("Comet and Percolator over the named fixture, and Cancel during Comet")
    void overTheFixture()
            throws IOException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException,
                    RunNotStartedException {
        OptionalInt searches = PerfRecorder.repeats(PERCOLATOR);
        if (searches.isEmpty()) {
            searches = PerfRecorder.repeats(COMET);
        }
        OptionalInt cancels = PerfRecorder.repeats(CANCEL);
        if (searches.isEmpty() && cancels.isEmpty()) {
            return;
        }
        String named = System.getProperty(MatrixSupport.FIXTURE, "");
        assertFalse(
                named.isBlank(),
                "REFUSED: the performance suite's fixture part needs its one named input,"
                        + " COMETGUI_MATRIX_FIXTURE (a directory of *.mzML and one *.fasta). It is"
                        + " not set, so nothing was searched and this is not a pass. D-006's CI"
                        + " fixture is OPEN; locally, scratch/fixture.");
        work = PerfRecorder.work("fixture");
        Fixture fixture = VersionMatrixPairs.fixture(Path.of(named), work.resolve("inputs"));
        MatrixSupport.openGuardForUpstream();
        Clock clock = Clock.systemUTC();
        ProcessService processes = new ProcessService(clock);
        ToolManager manager = MatrixSupport.toolManager(work.resolve("cache"), processes);
        ToolOffer comet =
                MatrixSupport.install(
                        manager,
                        ToolName.COMET,
                        ToolVersion.parse(PerfRecorder.parameter("fixture.comet")));
        ToolOffer percolator =
                MatrixSupport.install(
                        manager,
                        ToolName.PERCOLATOR,
                        ToolVersion.parse(PerfRecorder.parameter("fixture.percolator")));
        Context context =
                new Context(
                        work,
                        new AtomicInteger(),
                        clock,
                        processes,
                        manager,
                        fixture,
                        comet,
                        percolator);

        if (searches.isPresent()) {
            searches(context, searches.getAsInt());
        }
        if (cancels.isPresent()) {
            cancels(context, cancels.getAsInt());
        }
    }

    private static void searches(Context context, int repeats)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        PerfRecorder.Measurement comet = PerfRecorder.start(COMET, "s");
        PerfRecorder.Measurement percolator = PerfRecorder.start(PERCOLATOR, "s");
        List<String> failures = new ArrayList<>();
        for (int sample = 0; sample < repeats; sample++) {
            Started started = context.start();
            RunResult result = started.finished().get(RUN_BOUND.toMinutes(), TimeUnit.MINUTES);
            started.session().close();
            if (result.outcome() != AttemptOutcome.SUCCEEDED) {
                failures.add("search " + sample + ": " + result.failures());
                continue;
            }
            comet.sample(started.seconds(EngineStep.RUN_COMET));
            percolator.sample(started.seconds(EngineStep.RUN_PERCOLATOR));
        }
        for (PerfRecorder.Measurement measurement : List.of(comet, percolator)) {
            measurement.evidence("fixtureDigest", context.fixture().digest());
            measurement.evidence("comet", context.comet().version().text());
            measurement.evidence("percolator", context.percolator().version().text());
            measurement.evidence("spectra", context.fixture().spectra().size());
            measurement.checkEquals("every search succeeded", List.of(), failures);
            if (PerfRecorder.repeats(measurement == comet ? COMET : PERCOLATOR).isPresent()) {
                measurement.finish();
            }
        }
    }

    private static void cancels(Context context, int repeats)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        PerfRecorder.Measurement measurement = PerfRecorder.start(CANCEL, "ms");
        Path executable = context.comet().installedPath().orElseThrow().toAbsolutePath();
        List<String> outcomes = new ArrayList<>();
        int notGone = 0;
        int notCancelled = 0;
        for (int sample = 0; sample < repeats; sample++) {
            Started started = context.start();
            List<ProcessHandle> comets = new ArrayList<>();
            boolean running =
                    PerfRecorder.awaitCondition(
                            () -> {
                                comets.clear();
                                comets.addAll(cometsOf(executable));
                                return comets.stream().anyMatch(PerformanceFixture::ranASecond);
                            },
                            5_000,
                            CANCEL_BOUND.toMillis());
            assertFalse(!running, "no Comet process of " + executable + " ran for a second");
            List<ProcessHandle> tree = new ArrayList<>();
            for (ProcessHandle process : comets) {
                tree.add(process);
                process.descendants().forEach(tree::add);
            }
            long start = System.nanoTime();
            started.active().cancel();
            boolean gone =
                    PerfRecorder.awaitCondition(
                            () -> tree.stream().noneMatch(ProcessHandle::isAlive),
                            200,
                            CANCEL_BOUND.toMillis());
            measurement.sample(PerfRecorder.millisSince(start));
            if (!gone) {
                notGone++;
                tree.forEach(ProcessHandle::destroyForcibly);
            }
            RunResult result = started.finished().get(RUN_BOUND.toMinutes(), TimeUnit.MINUTES);
            started.session().close();
            outcomes.add(result.outcome().name() + " (" + tree.size() + " processes)");
            if (result.outcome() != AttemptOutcome.CANCELLED) {
                notCancelled++;
            }
        }
        measurement.evidence("outcomes", outcomes);
        measurement.evidence("comet", context.comet().version().text());
        measurement.checkEquals("every Comet tree was gone within " + CANCEL_BOUND, 0, notGone);
        measurement.checkEquals("every attempt ended CANCELLED", 0, notCancelled);
        measurement.finish();
    }

    /** The Comet processes of this JVM's descendants that run the installed executable. */
    private static List<ProcessHandle> cometsOf(Path executable) {
        return ProcessHandle.current()
                .descendants()
                .filter(ProcessHandle::isAlive)
                .filter(
                        process ->
                                process.info()
                                        .command()
                                        .map(command -> Path.of(command).equals(executable))
                                        .orElse(false))
                .toList();
    }

    private static boolean ranASecond(ProcessHandle process) {
        Optional<Instant> started = process.info().startInstant();
        return started.isPresent()
                && Duration.between(started.get(), Instant.now()).compareTo(Duration.ofSeconds(1))
                        >= 0;
    }

    /** What every search shares. */
    private record Context(
            Path work,
            AtomicInteger projects,
            Clock clock,
            ProcessService processes,
            ToolManager manager,
            Fixture fixture,
            ToolOffer comet,
            ToolOffer percolator) {

        /** Starts one search in a fresh project, as the Run section does. */
        Started start() throws IOException {
            Path project = work.resolve("project-" + projects.incrementAndGet());
            ApplicationServices services = VersionMatrixPairs.services(clock, processes);
            ProjectSession session = new ProjectSession(project, clock, services.runIds());
            SessionEngine engine =
                    RunWiring.port(
                            services,
                            new BoundedMessageLog(),
                            Optional.of(manager),
                            "every tool is installed by the performance suite",
                            session,
                            BUILD);
            CometParameters model =
                    ReleaseDefaults.load(MetadataLoader.loadBundled(), comet.version())
                            .withText("database_name", fixture.fasta().toString(), ValueOrigin.USER)
                            .withText("decoy_search", "1", ValueOrigin.USER)
                            .withWorkflowEnforcedOutputs();
            PercolatorResolution resolution =
                    PercolatorResolver.resolve(
                            List.of(percolator), EnumSet.of(DownstreamStage.LIMELIGHT_CONVERSION));
            PercolatorRequest request =
                    new PercolatorRequest(
                            Optional.of(percolator),
                            resolution.selected().map(percolator::equals).orElse(false),
                            Optional.of(resolution),
                            Optional.of(PercolatorSettings.defaults()),
                            List.of(),
                            false);
            CompletableFuture<RunResult> finished = new CompletableFuture<>();
            Map<EngineStep, Instant> running = new ConcurrentHashMap<>();
            Map<EngineStep, Instant> succeeded = new ConcurrentHashMap<>();
            ActiveRun active;
            try {
                active =
                        engine.start(
                                model,
                                List.of(),
                                fixture.spectra(),
                                request,
                                new RunObserver() {
                                    @Override
                                    public void planned(Plan plan, String description) {}

                                    @Override
                                    public void onTransition(StepTransition transition) {
                                        if (transition.to() == StepState.RUNNING) {
                                            running.put(transition.step(), transition.at());
                                        } else if (transition.to() == StepState.SUCCEEDED) {
                                            succeeded.put(transition.step(), transition.at());
                                        }
                                    }

                                    @Override
                                    public void onRunFinished(RunResult result) {
                                        finished.complete(result);
                                    }
                                });
            } catch (RunNotStartedException refused) {
                session.close();
                throw new AssertionError("the search was not started: " + refused.getMessage());
            }
            return new Started(session, active, finished, running, succeeded);
        }
    }

    /** A started search. */
    private record Started(
            ProjectSession session,
            ActiveRun active,
            CompletableFuture<RunResult> finished,
            Map<EngineStep, Instant> running,
            Map<EngineStep, Instant> succeeded) {

        /** A step's duration, from its RUNNING transition to its SUCCEEDED one, in seconds. */
        double seconds(EngineStep step) {
            Instant from = running.get(step);
            Instant to = succeeded.get(step);
            if (from == null || to == null) {
                throw new AssertionError(
                        step + " has no RUNNING and SUCCEEDED transitions: " + running);
            }
            return Duration.between(from, to).toNanos() / 1e9;
        }
    }
}
