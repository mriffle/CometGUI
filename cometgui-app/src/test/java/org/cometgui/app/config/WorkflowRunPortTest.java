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

package org.cometgui.app.config;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.cometgui.app.testing.InstalledComet;
import org.cometgui.app.testing.TestPercolators;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.project.ProjectId;
import org.cometgui.domain.project.ProjectLayout;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.tools.CapabilityEvidence;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolRegistrationException;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.validation.Finding;
import org.cometgui.ui.viewmodel.params.EngineCheck;
import org.cometgui.ui.viewmodel.params.PercolatorRequest;
import org.cometgui.ui.viewmodel.params.RunEnginePort;
import org.cometgui.ui.viewmodel.params.RunNotStartedException;
import org.cometgui.ui.viewmodel.params.RunObserver;
import org.cometgui.ui.viewmodel.percolator.PercolatorOffers;
import org.cometgui.ui.viewmodel.percolator.PercolatorPort;
import org.cometgui.workflow.engine.RunResult;
import org.cometgui.workflow.engine.StepTransition;
import org.cometgui.workflow.state.Plan;
import org.cometgui.workflow.storage.ProjectLock;
import org.cometgui.workflow.storage.ProjectStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Run section's engine port as the application composes it ({@link RunWiring}): which Comet it
 * selects and the words when there is none, the session's project opened and locked only when the
 * engine needs it, a lock held elsewhere stated in words, and the pre-run check's report -- each
 * proved without launching anything. A real run through the port is the GUI tests'.
 */
class WorkflowRunPortTest {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-test", "unknown", Instant.parse("2026-10-07T00:00:00Z"));

    private static final RunObserver NOBODY =
            new RunObserver() {
                @Override
                public void planned(Plan plan, String description) {}

                @Override
                public void onTransition(StepTransition transition) {}
            };

    /** A Percolator half that cannot run: the Comet half is what these tests are about. */
    private static final PercolatorRequest BLOCKED =
            PercolatorRequest.blocked(
                    List.of("The Percolator builds on this computer have not been read yet."));

    @TempDir private Path scratch;

    private ProjectSession project(Path root) {
        return new ProjectSession(
                root.resolve("project"),
                Clock.systemUTC(),
                ApplicationServices.forThisHost().runIds());
    }

    private static SessionEngine port(
            Optional<org.cometgui.domain.tools.ToolManager> tools, ProjectSession project) {
        return RunWiring.port(
                ApplicationServices.forThisHost(),
                new BoundedMessageLog(),
                tools,
                "the artefact manifest cannot be read",
                project,
                BUILD);
    }

    /**
     * The release's starting parameters with a database; no spectral library, as every new
     * configuration starts (D-012).
     */
    private static CometParameters model(Path database) {
        return ParameterEditorWiring.newSession()
                .model()
                .withText("database_name", database.toString(), ValueOrigin.USER);
    }

    private Path fasta(Path root) throws IOException {
        return Files.writeString(
                Files.createDirectories(root.resolve("inputs")).resolve("targets.fasta"),
                ">sp|P00001|ONE\nPEPTIDEK\n",
                StandardCharsets.US_ASCII);
    }

    private Path fakePercolator(Path root) throws IOException {
        Path percolator = Files.createDirectories(root.resolve("bin")).resolve("percolator");
        Files.writeString(percolator, "#!/bin/sh\nexit 0\n", StandardCharsets.US_ASCII);
        Files.setPosixFilePermissions(percolator, PosixFilePermissions.fromString("rwx------"));
        return percolator;
    }

    private Path fakeComet(Path root) throws IOException {
        Path comet = Files.createDirectories(root.resolve("bin")).resolve("comet");
        Files.writeString(comet, "#!/bin/sh\nexit 0\n", StandardCharsets.US_ASCII);
        Files.setPosixFilePermissions(comet, PosixFilePermissions.fromString("rwx------"));
        return comet;
    }

    @Test
    @DisplayName("no Tool Manager: the reason names why, and no project is created")
    void noToolManager() throws IOException {
        Path root = scratch.toRealPath();
        ProjectSession project = project(root);
        RunEnginePort port = port(Optional.empty(), project);
        String reason =
                "No Comet can be selected, because this machine has no Tool Manager: the artefact"
                        + " manifest cannot be read";
        assertEquals(List.of(reason), port.check(model(fasta(root)), List.of(), BLOCKED).reasons());
        assertEquals(
                reason,
                assertThrows(
                                RunNotStartedException.class,
                                () -> port.start(model(fasta(root)), List.of(), BLOCKED, NOBODY))
                        .getMessage());
        assertFalse(Files.exists(project.directory()), "nothing written for a run that cannot be");
    }

    @Test
    @DisplayName("no Comet of the release installed: the reason says which and where to get it")
    void cometNotInstalled() throws IOException {
        Path root = scratch.toRealPath();
        ProjectSession project = project(root);
        EngineCheck check =
                port(Optional.of(InstalledComet.nothing()), project)
                        .check(model(fasta(root)), List.of(), BLOCKED);
        assertEquals(
                List.of(
                        "Comet 2026.03.0 is not installed, and the parameters are for that"
                                + " release: install it in the Tool Manager section, or register a"
                                + " Comet 2026.03.0 already on this computer there."),
                check.reasons());
        assertEquals(Optional.empty(), check.report());
        assertFalse(Files.exists(project.directory()));
        InstalledComet older = InstalledComet.at("2026.02.2", fakeComet(root));
        assertEquals(
                1,
                port(Optional.of(older), project)
                        .check(model(fasta(root)), List.of(), BLOCKED)
                        .reasons()
                        .size(),
                "an installed Comet of another release is not selected");
    }

    @Test
    @DisplayName("a composition root without a process service: no run, in words")
    void noProcessService() throws IOException {
        Path root = scratch.toRealPath();
        ApplicationServices host = ApplicationServices.forThisHost();
        ApplicationServices without =
                new ApplicationServices(
                        host.clock(),
                        host.environment(),
                        host.fileSystem(),
                        host.runIds(),
                        host.glibcVersions(),
                        null,
                        null,
                        null);
        RunEnginePort port =
                RunWiring.port(
                        without,
                        new BoundedMessageLog(),
                        Optional.of(InstalledComet.at("2026.03.0", fakeComet(root))),
                        "",
                        project(root),
                        BUILD);
        assertEquals(
                List.of(
                        "No run can start: this application was composed without a process"
                                + " service, so no tool can be launched."),
                port.check(model(fasta(root)), List.of(), BLOCKED).reasons());
        assertThrows(
                RunNotStartedException.class,
                () -> port.start(model(fasta(root)), List.of(), BLOCKED, NOBODY));
    }

    @Test
    @DisplayName(
            "the pre-run check over the files: the project opened and locked, the problems"
                    + " and the decoy block reported, nothing run")
    void preRunCheck() throws IOException {
        Path root = scratch.toRealPath();
        ProjectSession project = project(root);
        Path fasta = fasta(root);
        RunEnginePort port =
                port(Optional.of(InstalledComet.at("2026.03.0", fakeComet(root))), project);
        PercolatorRequest ready =
                TestPercolators.ready(
                        TestPercolators.installed(
                                "3.07.1",
                                ToolOrigin.MANAGED,
                                fakePercolator(root),
                                TestPercolators.ALL,
                                List.of()));
        EngineCheck check = port.check(model(fasta), List.of(), ready);
        assertAll(
                () -> assertEquals(List.of(), check.reasons()),
                () ->
                        assertEquals(
                                List.of("there is no spectrum file to search"),
                                check.report().orElseThrow().problems()),
                () ->
                        assertEquals(
                                List.of(
                                        "decoy_search = 0 (no internal decoys) and "
                                                + fasta
                                                + " holds no entry whose accession begins with"
                                                + " DECOY_ (0 of 1 records): Percolator would have"
                                                + " no negative examples; set decoy_search to 1 or"
                                                + " 2 so that Comet makes decoys, or choose a FASTA"
                                                + " whose decoys begin with DECOY_"),
                                check.report().orElseThrow().validation().errors().stream()
                                        .map(Finding::message)
                                        .toList()),
                () -> assertEquals(Optional.empty(), check.outlook(), "blocked: no preview"),
                () -> assertTrue(project.isOpen(), "the session holds the project's lock"),
                () -> assertTrue(Files.isRegularFile(project.directory().resolve("project.json"))));
        RunNotStartedException refused =
                assertThrows(
                        RunNotStartedException.class,
                        () -> port.start(model(fasta), List.of(), ready, NOBODY));
        assertTrue(
                refused.getMessage()
                        .startsWith(
                                "the run cannot start:\n- there is no spectrum file to search\n-"
                                        + " [decoy.none_anywhere] decoy_search = 0"),
                refused::getMessage);
        try (var runs = Files.list(project.directory().resolve("runs"))) {
            assertEquals(0, runs.count(), "a refused run creates no run directory");
        }
        project.close();
        assertFalse(project.isOpen());
    }

    @Test
    @DisplayName("a project another CometGUI holds: refused, in words, naming the owner")
    void lockRefused() throws IOException {
        Path root = scratch.toRealPath();
        ProjectSession project = project(root);
        ProjectLayout layout = new ProjectLayout(project.directory());
        new ProjectStore(Clock.systemUTC()).create(layout, new ProjectId("held-elsewhere"));
        try (ProjectLock held = ProjectLock.acquire(layout, Clock.systemUTC())) {
            List<String> reasons =
                    port(Optional.of(InstalledComet.at("2026.03.0", fakeComet(root))), project)
                            .check(model(fasta(root)), List.of(), BLOCKED)
                            .reasons();
            assertEquals(1, reasons.size());
            assertTrue(
                    reasons.get(0)
                            .startsWith(
                                    "The project at "
                                            + project.directory()
                                            + " cannot be used, so no run can start: the project "
                                            + project.directory()
                                            + " is already open in this CometGUI ("),
                    reasons.get(0));
            assertTrue(
                    reasons.get(0).contains(Long.toString(ProcessHandle.current().pid())),
                    () -> "the owner's pid is named: " + reasons.get(0));
            assertFalse(project.isOpen());
        }
    }

    @Test
    @DisplayName(
            "a Percolator half that cannot run: the Comet half alone is checked, no preview, and"
                    + " a run is refused naming the half's problems")
    void blockedPercolatorHalf() throws IOException {
        Path root = scratch.toRealPath();
        ProjectSession project = project(root);
        SessionEngine port =
                port(Optional.of(InstalledComet.at("2026.03.0", fakeComet(root))), project);
        Path fasta = fasta(root);
        EngineCheck check = port.check(model(fasta), List.of(), BLOCKED);
        assertAll(
                () -> assertEquals(List.of(), check.reasons(), "the section states its own"),
                () ->
                        assertEquals(
                                List.of("there is no spectrum file to search"),
                                check.report().orElseThrow().problems(),
                                "the Comet half was checked all the same"),
                () -> assertEquals(Optional.empty(), check.outlook()));
        assertEquals(
                "the Percolator section names no build that can run: The Percolator builds on"
                        + " this computer have not been read yet.",
                assertThrows(
                                RunNotStartedException.class,
                                () -> port.start(model(fasta), List.of(), BLOCKED, NOBODY))
                        .getMessage());
    }

    @Test
    @DisplayName(
            "a runnable half whose executable cannot be read: the reason names it, beside the"
                    + " Comet half's report")
    void unreadablePercolator() throws IOException {
        Path root = scratch.toRealPath();
        ProjectSession project = project(root);
        SessionEngine port =
                port(Optional.of(InstalledComet.at("2026.03.0", fakeComet(root))), project);
        Path missing = root.resolve("bin/no-percolator");
        PercolatorRequest gone =
                TestPercolators.ready(
                        TestPercolators.installed(
                                "3.09", ToolOrigin.LOCAL, missing, TestPercolators.ALL, List.of()));
        EngineCheck check = port.check(model(fasta(root)), List.of(), gone);
        assertAll(
                () -> assertEquals(1, check.reasons().size()),
                () ->
                        assertTrue(
                                check.reasons()
                                        .get(0)
                                        .startsWith(
                                                "The selected Percolator 3.09 at "
                                                        + missing
                                                        + " cannot be used: "),
                                () -> check.reasons().get(0)),
                () -> assertTrue(check.report().isPresent()),
                () -> assertEquals(Optional.empty(), check.outlook()));
        RunNotStartedException refused =
                assertThrows(
                        RunNotStartedException.class,
                        () -> port.start(model(fasta(root)), List.of(), gone, NOBODY));
        assertEquals(check.reasons().get(0), refused.getMessage());
    }

    @Test
    @DisplayName(
            "a runnable half whose capabilities cannot rescore: the pre-run check refuses it"
                    + " before anything runs")
    void percolatorRefusedByThePreRunCheck() throws IOException {
        Path root = scratch.toRealPath();
        ProjectSession project = project(root);
        SessionEngine port =
                port(Optional.of(InstalledComet.at("2026.03.0", fakeComet(root))), project);
        PercolatorRequest noTables =
                TestPercolators.ready(
                        TestPercolators.installed(
                                "3.10",
                                ToolOrigin.LOCAL,
                                fakePercolator(root),
                                TestPercolators.allBut(ToolCapability.PSM_TSV_OUTPUT),
                                List.of()));
        EngineCheck check = port.check(model(fasta(root)), List.of(), noTables);
        List<String> problems = check.report().orElseThrow().problems();
        assertTrue(
                problems.stream().anyMatch(problem -> problem.contains("PSM_TSV_OUTPUT")),
                () -> "the builder's refusal is among the problems: " + problems);
        assertTrue(check.report().orElseThrow().blocked());
    }

    @Test
    @DisplayName("no run yet: there is no Percolator rerun, in words, and none can start")
    void noRerunBeforeARun() throws IOException {
        Path root = scratch.toRealPath();
        SessionEngine port =
                port(Optional.of(InstalledComet.at("2026.03.0", fakeComet(root))), project(root));
        String none =
                "No run has been made in this session, so there is no merged PIN to rerun"
                        + " Percolator from. Run a search first.";
        assertEquals(Optional.of(none), port.check(BLOCKED).refusal());
        assertEquals(
                none,
                assertThrows(RunNotStartedException.class, () -> port.start(BLOCKED, NOBODY))
                        .getMessage());
    }

    @Test
    @DisplayName("no process service: the rerun is refused with the same words as Run")
    void noProcessServiceNoRerun() throws IOException {
        Path root = scratch.toRealPath();
        ApplicationServices host = ApplicationServices.forThisHost();
        SessionEngine port =
                RunWiring.port(
                        new ApplicationServices(
                                host.clock(),
                                host.environment(),
                                host.fileSystem(),
                                host.runIds(),
                                host.glibcVersions(),
                                null,
                                null,
                                null),
                        new BoundedMessageLog(),
                        Optional.empty(),
                        "",
                        project(root),
                        BUILD);
        assertEquals(Optional.of(RunWiring.NO_PROCESS_SERVICE), port.check(BLOCKED).refusal());
        assertThrows(RunNotStartedException.class, () -> port.start(BLOCKED, NOBODY));
    }

    @Test
    @DisplayName(
            "the Percolator section's port: the Tool Manager's Percolator offers only, in its"
                    + " order; registration delegated; no Tool Manager said in words")
    void percolatorPort() throws IOException, ToolRegistrationException {
        Path root = scratch.toRealPath();
        ToolOffer local =
                TestPercolators.installed(
                        "3.09",
                        ToolOrigin.LOCAL,
                        fakePercolator(root),
                        TestPercolators.allBut(
                                ToolCapability.XML_OUTPUT, ToolCapability.XML_DECOY_OUTPUT),
                        List.of());
        ToolOffer managed =
                TestPercolators.notInstalled(
                        "3.07.1",
                        Set.of(ToolCapability.XML_OUTPUT),
                        CapabilityEvidence.OBSERVED_BY_EXECUTION);
        InstalledComet tools =
                InstalledComet.at("2026.03.0", fakeComet(root))
                        .with(managed)
                        .registering((tool, executable) -> local);
        PercolatorPort port = RunWiring.percolator(Optional.of(tools), "");
        assertEquals(PercolatorOffers.of(List.of(managed)), port.offers());
        assertEquals(local, port.register(local.installedPath().orElseThrow()));
        assertEquals(
                PercolatorOffers.of(List.of(managed, local)),
                port.offers(),
                "a registered build is offered from then on");

        PercolatorPort none = RunWiring.percolator(Optional.empty(), "the manifest is unreadable");
        assertEquals(PercolatorOffers.unavailable("the manifest is unreadable"), none.offers());
        assertEquals(
                "No Percolator can be registered, because this machine has no Tool Manager: the"
                        + " manifest is unreadable",
                assertThrows(ToolRegistrationException.class, () -> none.register(root))
                        .getMessage());
        assertEquals(
                PercolatorOffers.unavailable("no reason was given"),
                RunWiring.percolator(Optional.empty(), " ").offers());
    }

    @Test
    @DisplayName(
            "a started run is executing from its first transition until the engine reports it"
                    + " finished, and not when its observer is told")
    void executingRuns()
            throws IOException,
                    RunNotStartedException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException {
        Path root = scratch.toRealPath();
        ProjectSession project = project(root);
        Path fasta = fasta(root);
        Path spectrum =
                Files.writeString(
                        root.resolve("inputs").resolve("sample.mzML"),
                        "<mzML/>\n",
                        StandardCharsets.US_ASCII);
        SessionEngine port =
                port(Optional.of(InstalledComet.at("2026.03.0", fakeComet(root))), project);
        PercolatorRequest ready =
                TestPercolators.ready(
                        TestPercolators.installed(
                                "3.07.1",
                                ToolOrigin.MANAGED,
                                fakePercolator(root),
                                TestPercolators.ALL,
                                List.of()));
        List<Set<RunId>> whileRunning = new CopyOnWriteArrayList<>();
        CompletableFuture<Set<RunId>> whenFinished = new CompletableFuture<>();
        RunObserver observer =
                new RunObserver() {
                    @Override
                    public void planned(Plan plan, String description) {}

                    @Override
                    public void onTransition(StepTransition transition) {
                        whileRunning.add(port.executingRuns());
                    }

                    @Override
                    public void onRunFinished(RunResult result) {
                        whenFinished.complete(port.executingRuns());
                    }
                };
        assertEquals(Set.of(), port.executingRuns(), "nothing has started");
        port.start(
                model(fasta).withText("decoy_search", "1", ValueOrigin.USER),
                List.of(spectrum),
                ready,
                observer);
        Set<RunId> atTheEnd = whenFinished.get(2, TimeUnit.MINUTES);
        List<String> runDirectories;
        try (var runs = Files.list(project.directory().resolve("runs"))) {
            runDirectories = runs.map(run -> String.valueOf(run.getFileName())).toList();
        }
        assertEquals(1, runDirectories.size(), "one run: " + runDirectories);
        assertAll(
                () -> assertFalse(whileRunning.isEmpty(), "the engine reported transitions"),
                () ->
                        assertEquals(
                                1,
                                Set.copyOf(whileRunning).size(),
                                "one executing set throughout: " + whileRunning),
                () -> assertEquals(1, whileRunning.get(0).size(), whileRunning::toString),
                () ->
                        assertTrue(
                                runDirectories
                                        .get(0)
                                        .endsWith(
                                                "-"
                                                        + whileRunning
                                                                .get(0)
                                                                .iterator()
                                                                .next()
                                                                .value()),
                                () -> "the executing run is the run made: " + runDirectories),
                () -> assertEquals(Set.of(), atTheEnd, "ended before its observer is told"),
                () -> assertEquals(Set.of(), port.executingRuns()));
        project.close();
    }

    @Test
    @DisplayName("the default project is projects/default under the application data directory")
    void defaultDirectory() {
        Path data = scratch.resolve("data");
        assertEquals(
                data.resolve("projects").resolve("default"), ProjectSession.defaultDirectory(data));
        assertEquals(
                "default", String.valueOf(ProjectSession.defaultDirectory(data).getFileName()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RunWiring.Setup(InstalledComet::nothing, Path.of("relative")));
    }
}
