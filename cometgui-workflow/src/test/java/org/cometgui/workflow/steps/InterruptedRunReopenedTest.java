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

package org.cometgui.workflow.steps;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.project.ProjectLayout;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.RunAttempt;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.provenance.events.ProvenanceEvent;
import org.cometgui.provenance.events.ProvenanceEventType;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.provenance.manifest.ProvenanceStatus;
import org.cometgui.workflow.engine.ReuseRefusedException;
import org.cometgui.workflow.engine.RunResult;
import org.cometgui.workflow.provenance.LoadedRun;
import org.cometgui.workflow.provenance.ProvenanceReader;
import org.cometgui.workflow.provenance.RecordState;
import org.cometgui.workflow.provenance.RunCondition;
import org.cometgui.workflow.provenance.RunListing;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The specification's failure path <strong>"a stale or partial run reopened"</strong>, through the
 * real workflow, engine, lock and provenance reader (Phase 14, gate item 4).
 *
 * <p>A second JVM ({@link RunChild} {@code halt}) creates a project, runs a search through the real
 * workflow with the stand-in tools, and -- while Percolator is running, the Comet half recorded
 * succeeded -- halts itself with {@code Runtime.halt}, releasing nothing: a CometGUI that crashed
 * or was killed mid-run. This JVM then reopens the project as the next CometGUI would.
 *
 * <ul>
 *   <li><strong>What the crash left</strong>, proved first so nothing below is vacuous: the JVM is
 *       gone, the lock file still names it, {@code run.json} records the attempt running with the
 *       Comet half succeeded, there is no {@code provenance.json}, and the event log stops without
 *       the run's end. The stand-in Percolator the crashed JVM started is still running -- a crash
 *       strands its tool; nothing in a dead process can stop it.
 *   <li><strong>The stale lock</strong> is recovered and named: the dead JVM's pid and this host.
 *   <li><strong>The run is listed as interrupted</strong> with no sealed record, and loads as a run
 *       without one, with the warning that says so -- never as completed, failed or running.
 *   <li><strong>The next run is correct</strong>: a new run of the same search in the reopened
 *       project succeeds and is sealed as completed, the interrupted run is still listed as
 *       interrupted, and not one byte, mtime or entry of the interrupted run's directory changed.
 * </ul>
 *
 * <p>No file outside this module is read. Waits are on lines the child prints and on its exit.
 */
@EnabledOnOs(
        value = {OS.LINUX, OS.MAC},
        disabledReason = "the stand-in tools are POSIX shell scripts")
class InterruptedRunReopenedTest {

    private static final String NO_RECORD = "this run has no sealed provenance record";

    @Test
    @DisplayName(
            "a run whose CometGUI halted mid-Percolator: the stale lock recovered and named, the"
                    + " run listed as interrupted and unsealed, the next run correct and the"
                    + " interrupted run untouched")
    void haltedMidPercolatorThenReopened(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Path root = directory.toRealPath();
        Path search = Files.createDirectories(root.resolve("search"));
        RunChildJvm child = RunChildJvm.start(root, "halt", search.toString());
        long tool = -1;
        long sleeper = -1;
        try {
            String halting = child.awaitLine(line -> line.startsWith("HALTING "));
            int exit = child.awaitExit();
            String[] parts = halting.split(" ", 4);
            tool = Long.parseLong(parts[1]);
            sleeper = Long.parseLong(parts[2]);
            long crashed = child.pid();
            Path project = search.resolve("project");
            RunLayout run = new RunLayout(Path.of(parts[3]));
            RunAttempt left = attemptOf(project, run);
            List<ProvenanceEvent> events = RunEvidence.events(run);
            long stranded = tool;
            assertAll(
                    "what the crash left",
                    () -> assertEquals(0, exit, "Runtime.halt(0)"),
                    () -> assertFalse(StandInTools.running(crashed), "the JVM is gone"),
                    () ->
                            assertTrue(
                                    Files.readString(project.resolve("project.lock"))
                                            .contains("\"pid\": " + crashed + ","),
                                    "the lock file still names the dead JVM"),
                    () -> assertEquals(AttemptOutcome.RUNNING, left.outcome()),
                    () -> assertEquals(Optional.empty(), left.ended()),
                    () ->
                            assertEquals(
                                    List.of(
                                            "hash-inputs",
                                            "merge-pin",
                                            "resolve-comet",
                                            "resolve-percolator",
                                            "run-comet",
                                            "serialise-comet-params",
                                            "validate-comet-outputs",
                                            "validate-configuration"),
                                    left.succeededSteps().keySet().stream().sorted().toList()),
                    () -> assertFalse(Files.exists(run.provenanceJsonFile())),
                    () -> assertFalse(Files.exists(run.provenanceRstFile())),
                    () ->
                            assertTrue(
                                    events.stream()
                                            .noneMatch(
                                                    event ->
                                                            event.type()
                                                                    == ProvenanceEventType
                                                                            .RUN_FINISHED),
                                    "the event log stops without the run's end"),
                    () ->
                            assertTrue(
                                    StandInTools.running(stranded),
                                    "the crashed JVM's Percolator is still running"));
            Map<String, String> interrupted = RealComet.snapshot(run.root());

            try (RealProject reopened = RealProject.open(project, "after")) {
                ProvenanceReader reader =
                        new ProvenanceReader(
                                SecretRedactor::patternsOnly,
                                new CachingHashService(new StreamingHashService()));
                List<RunListing> listedOnReopen = reader.list(reopened.project(), Set.of());
                LoadedRun loaded = reader.load(run);
                assertAll(
                        "reopened",
                        () ->
                                assertEquals(
                                        crashed,
                                        reopened.lock().recovered().orElseThrow().pid(),
                                        "the stale lock was recovered from the dead JVM"),
                        () ->
                                assertEquals(
                                        "recovered a stale lock on "
                                                + project
                                                + " left by "
                                                + reopened.lock()
                                                        .recovered()
                                                        .orElseThrow()
                                                        .describe()
                                                + ", which is no longer running",
                                        reopened.lock().recoveryNotice().orElseThrow()),
                        () ->
                                assertTrue(
                                        reopened.lock()
                                                .recovered()
                                                .orElseThrow()
                                                .describe()
                                                .contains("process " + crashed + " on host "),
                                        "named by pid and host"),
                        () -> assertEquals(1, listedOnReopen.size()),
                        () ->
                                assertEquals(
                                        RunCondition.INTERRUPTED,
                                        listedOnReopen.get(0).condition()),
                        () -> assertEquals("interrupted", listedOnReopen.get(0).condition().word()),
                        () -> assertFalse(listedOnReopen.get(0).sealedRecord()),
                        () -> assertEquals(Optional.of("run-0001"), listedOnReopen.get(0).runId()),
                        () -> assertEquals(RecordState.NO_SEALED_RECORD, loaded.state()),
                        () -> assertEquals(Optional.empty(), loaded.manifest()),
                        () ->
                                assertTrue(
                                        loaded.view().warnings().stream()
                                                .anyMatch(
                                                        warning ->
                                                                warning.message()
                                                                        .startsWith(NO_RECORD)),
                                        () ->
                                                "the missing record is warned about: "
                                                        + loaded.view().warnings()));

                Path percolator =
                        FakePercolator.write(
                                search.resolve("bin/percolator-after"),
                                FakePercolator.Behaviour.normal());
                FakeSearch fake =
                        new FakeSearch(
                                search,
                                search.resolve("comet"),
                                List.of(
                                        search.resolve("inputs/k562_3.mzML"),
                                        search.resolve("inputs/k562_4.mzML")),
                                search.resolve("inputs/db.fasta"),
                                project);
                PreparedRun next = reopened.prepare(StandInTools.request(fake, percolator));
                RunResult result = reopened.run(next);
                List<RunListing> listedAfter = reader.list(reopened.project(), Set.of());
                assertAll(
                        "the next run",
                        () -> assertNotEquals(run.root(), next.layout().root()),
                        () -> assertEquals("after-0001", next.identity().runId().value()),
                        () -> assertEquals(AttemptOutcome.SUCCEEDED, result.outcome()),
                        () ->
                                assertEquals(
                                        ProvenanceStatus.COMPLETED,
                                        RunEvidence.manifest(next.layout()).run().status()),
                        () ->
                                assertEquals(
                                        List.of(
                                                "after-0001 completed sealed",
                                                "run-0001 interrupted unsealed"),
                                        describe(listedAfter)),
                        () ->
                                assertEquals(
                                        List.of(),
                                        RealComet.differences(
                                                interrupted, RealComet.snapshot(run.root())),
                                        "the interrupted run's directory is untouched"));
            }
        } finally {
            child.stop();
            for (long pid : new long[] {sleeper, tool}) {
                if (pid > 0) {
                    ProcessHandle.of(pid).ifPresent(ProcessHandle::destroyForcibly);
                }
            }
        }
    }

    private static RunAttempt attemptOf(Path project, RunLayout run) throws IOException {
        List<RunAttempt> attempts =
                new org.cometgui.workflow.storage.RunStore(
                                new ProjectLayout(project),
                                java.time.Clock.systemUTC(),
                                () -> {
                                    throw new AssertionError("no run is created here");
                                })
                        .read(run)
                        .attempts();
        assertEquals(1, attempts.size(), () -> "one attempt: " + attempts);
        return attempts.get(0);
    }

    private static List<String> describe(List<RunListing> listed) {
        return listed.stream()
                .map(
                        run ->
                                run.runId().orElse("?")
                                        + " "
                                        + run.condition().word()
                                        + " "
                                        + (run.sealedRecord() ? "sealed" : "unsealed"))
                .sorted()
                .toList();
    }
}
