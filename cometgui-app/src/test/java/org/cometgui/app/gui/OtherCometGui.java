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

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.cometgui.app.config.ProjectSession;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.log.LogMessage;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.secrets.SecretRedactor;
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
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.provenance.manifest.ApplicationRecord;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.tools.process.RunMessageSink;
import org.cometgui.workflow.engine.EngineServices;
import org.cometgui.workflow.engine.ReuseRefusedException;
import org.cometgui.workflow.engine.RunHandle;
import org.cometgui.workflow.engine.StepStateListener;
import org.cometgui.workflow.engine.WorkflowEngine;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.StepState;
import org.cometgui.workflow.steps.CometSelection;
import org.cometgui.workflow.steps.CometWorkflow;
import org.cometgui.workflow.steps.PercolatorChoice;
import org.cometgui.workflow.steps.PercolatorSelection;
import org.cometgui.workflow.steps.PreparedRun;
import org.cometgui.workflow.steps.RunBlockedException;
import org.cometgui.workflow.steps.SearchRequest;

/**
 * Another CometGUI, in a second JVM, on the same project directory as the application under test:
 * the product's own {@link ProjectSession} opens and locks the project exactly as the application
 * does, and the real {@link CometWorkflow} and {@link WorkflowEngine} run its search. Started by
 * {@link OtherCometGuiJvm} through the project's process service.
 *
 * <ul>
 *   <li>{@code hold <project> <gate>}: opens and locks the project, prints {@code HELD <pid>
 *       <host>}, then blocks on an exclusive lock of the gate file -- held by the test until it has
 *       seen the refusal -- and then closes the session, releasing the project lock, prints {@code
 *       RELEASED} and exits. No sleeping: the child waits on the test's lock, not on a clock.
 *   <li>{@code halt <project> <comet> <percolator> <spectrum> <fasta>}: opens and locks the
 *       project, prepares run {@value #CRASHED_RUN} of the stand-in Comet ({@link StandInRun}) and
 *       a waiting stand-in Percolator, starts it, waits for that Percolator to print its pid -- the
 *       Comet half recorded succeeded, Percolator's tables written, {@code run.json} recording the
 *       attempt running -- prints {@code HALTING <percolator pid> <child pid> <run directory>} and
 *       halts the JVM with {@code Runtime.halt}, releasing and recording nothing, as a crash or a
 *       kill would.
 * </ul>
 */
public final class OtherCometGui {

    /** The identifier of the run the crashed CometGUI leaves behind. */
    static final String CRASHED_RUN = "crashed-0001";

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-23T00:00:00Z"));

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

    private OtherCometGui() {}

    /**
     * Runs the child.
     *
     * @param args the mode and its arguments
     * @throws IOException if the project cannot be opened or the run written
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
        Clock clock = Clock.systemUTC();
        ProjectSession session =
                new ProjectSession(Path.of(args[1]), clock, () -> new RunId(CRASHED_RUN));
        if ("hold".equals(args[0])) {
            hold(session, Path.of(args[2]));
        } else if ("halt".equals(args[0])) {
            halt(session, clock, Path.of(args[2]), Path.of(args[3]), Path.of(args[4]), args[5]);
        } else {
            throw new IllegalArgumentException("unknown mode " + args[0]);
        }
    }

    private static void hold(ProjectSession session, Path gate) throws IOException {
        System.out.println(
                "HELD " + session.lock().owner().pid() + " " + session.lock().owner().host());
        System.out.flush();
        try (FileChannel channel = FileChannel.open(gate, StandardOpenOption.WRITE);
                FileLock released = channel.lock()) {
            session.close();
            System.out.println("RELEASED " + released.isValid());
        }
    }

    private static void halt(
            ProjectSession session,
            Clock clock,
            Path comet,
            Path percolator,
            Path spectrum,
            String fasta)
            throws IOException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException,
                    RunBlockedException,
                    ReuseRefusedException {
        CachingHashService hashes = new CachingHashService(new StreamingHashService());
        Lines lines = new Lines();
        WorkflowEngine engine =
                new WorkflowEngine(
                        new EngineServices(
                                new ProcessService(clock),
                                clock,
                                SecretRedactor.patternsOnly(),
                                lines,
                                hashes,
                                Runtime.getRuntime().availableProcessors(),
                                4));
        CometWorkflow workflow = new CometWorkflow(hashes, BUILD);
        ToolVersion release = ToolVersion.parse(StandInRun.COMET_RELEASE);
        CometParameters model =
                ReleaseDefaults.load(MetadataLoader.loadBundled(), release)
                        .withText("database_name", fasta, ValueOrigin.USER)
                        .withDecoySource(DecoySource.COMET_INTERNAL_CONCATENATED, ValueOrigin.USER)
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
        CompletableFuture<String> childLine = lines.when("child ");
        CompletableFuture<String> toolLine = lines.when("pid ");
        RunHandle handle = workflow.start(engine, prepared, StepStateListener.NONE);
        String child = childLine.get(2, TimeUnit.MINUTES);
        String tool = toolLine.get(2, TimeUnit.MINUTES);
        if (handle.states().get(EngineStep.RUN_PERCOLATOR) != StepState.RUNNING) {
            throw new IllegalStateException("Percolator is not running: " + handle.states());
        }
        System.out.println(
                "HALTING "
                        + tool.substring("pid ".length()).trim()
                        + " "
                        + child.substring("child ".length()).trim()
                        + " "
                        + prepared.layout().root());
        System.out.flush();
        Runtime.getRuntime().halt(0);
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

    /** The console stand-in: completes a future with the first line that begins so. */
    private static final class Lines implements RunMessageSink {

        private final List<String> seen = new ArrayList<>();

        private final List<Waiter> waiters = new ArrayList<>();

        @Override
        public void append(LogMessage message) {
            synchronized (seen) {
                seen.add(message.text());
                for (Waiter waiter : waiters) {
                    if (message.text().startsWith(waiter.prefix())) {
                        waiter.line().complete(message.text());
                    }
                }
            }
        }

        CompletableFuture<String> when(String prefix) {
            CompletableFuture<String> line = new CompletableFuture<>();
            synchronized (seen) {
                for (String text : seen) {
                    if (text.startsWith(prefix)) {
                        line.complete(text);
                    }
                }
                waiters.add(new Waiter(prefix, line));
            }
            return line;
        }

        private record Waiter(String prefix, CompletableFuture<String> line) {}
    }
}
