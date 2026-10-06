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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.provenance.events.ProvenanceEvent;
import org.cometgui.provenance.events.ProvenanceEventType;
import org.cometgui.provenance.manifest.FileDirection;
import org.cometgui.provenance.manifest.FileRecord;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ProvenanceStatus;
import org.cometgui.provenance.manifest.ToolRecord;
import org.cometgui.workflow.engine.ReuseRefusedException;
import org.cometgui.workflow.engine.RunHandle;
import org.cometgui.workflow.engine.RunResult;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.StepState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Phase 08 exit gate item 7 against the REAL Comet 2026.03.0: a search cancelled while it is
 * searching ends with Comet and every descendant dead -- proved by pid through {@link
 * ProcessHandle}, never by an exit code -- and leaves stage logs, an event log and a {@code
 * provenance.json} that parse, the last recording the run as cancelled and the outputs Comet had
 * begun as partial.
 *
 * <p>The search is made long enough to be caught running: the WHOLE proteome (about 20 000 records,
 * not the 1000-record subset) with {@code num_threads = 1}, measured at about 31 s for one K562
 * file. The test waits on observable state -- Comet's {@code Load spectra:} line reaching the
 * console and the stage log, after which it is searching -- never on a fixed delay.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "only the linux/x86-64 Comet binary has ever been executed in this project")
class RealCancellationTest {

    @Test
    @DisplayName(
            "gate 7: cancelling the real Comet mid-search kills it and its descendants (by pid) and"
                    + " leaves parsable logs and a cancelled provenance with partial outputs")
    void gate7CancelTheRealCometMidSearch(@TempDir Path scratch)
            throws IOException,
                    InterruptedException,
                    RunBlockedException,
                    ReuseRefusedException,
                    java.util.concurrent.ExecutionException,
                    java.util.concurrent.TimeoutException {
        Path root = scratch.toRealPath();
        Path comet = RealComet.stageComet(RealComet.NEWER, root.resolve("bin/comet"));
        Path inputs = Files.createDirectories(root.resolve("inputs"));
        Path spectrum = RealComet.spectra(inputs).get(0);
        Path proteome = Files.copy(RealComet.proteome(), inputs.resolve("proteome.fasta"));
        assertEquals(RealComet.PROTEOME_SHA256, RealComet.sha256(proteome));
        try (RealProject project = RealProject.create(root.resolve("project"))) {
            PreparedRun prepared =
                    project.prepare(
                            new SearchRequest(
                                    RealComet.model(
                                            RealComet.NEWER,
                                            proteome,
                                            DecoySource.COMET_INTERNAL_CONCATENATED,
                                            1),
                                    List.of(spectrum),
                                    RealComet.selection(RealComet.NEWER, comet),
                                    IndexMode.NONE));
            RealProject.Transitions transitions = new RealProject.Transitions();
            RunHandle handle = project.workflow().start(project.engine(), prepared, transitions);

            String loaded =
                    project.sink()
                            .when(text -> text.contains("Load spectra:"))
                            .get(RealProject.RUN_BOUND.toSeconds(), TimeUnit.SECONDS);
            assertTrue(loaded.contains("Load spectra: 728"), loaded);
            Path log = prepared.layout().cometLogFile(1);
            assertTrue(
                    RunEvidence.stageLog(log).stream()
                            .anyMatch(line -> line.contains("Load spectra: 728")),
                    "the stage log shows the search under way");
            List<RealProject.Launch> launches = project.runner().launchesOf(comet);
            assertEquals(1, launches.size());
            long pid = launches.get(0).process().pid();
            ProcessHandle searching = ProcessHandle.of(pid).orElseThrow();
            assertTrue(searching.isAlive(), "Comet is searching when it is cancelled");
            List<ProcessHandle> family = new ArrayList<>();
            family.add(searching);
            family.addAll(searching.descendants().toList());

            handle.cancel();
            RunResult result = handle.await(RealProject.RUN_BOUND).orElseThrow();

            for (ProcessHandle member : family) {
                assertFalse(
                        member.isAlive(), "process " + member.pid() + " outlived the cancellation");
                assertFalse(
                        ProcessHandle.of(member.pid()).map(ProcessHandle::isAlive).orElse(false),
                        "pid " + member.pid() + " is still a live process");
            }
            assertEquals(AttemptOutcome.CANCELLED, result.outcome());
            assertEquals(StepState.CANCELLED, result.states().get(EngineStep.RUN_COMET));
            assertEquals(
                    StepState.NOT_STARTED, result.states().get(EngineStep.VALIDATE_COMET_OUTPUTS));
            assertTrue(
                    transitions.all().stream()
                            .anyMatch(
                                    change ->
                                            change.step() == EngineStep.RUN_COMET
                                                    && change.to() == StepState.CANCEL_REQUESTED));

            List<String> lines = RunEvidence.stageLog(log);
            String last = lines.get(lines.size() - 1);
            assertTrue(last.contains("[cometgui] stage comet-01 ended"), last);

            List<ProvenanceEvent> events = RunEvidence.events(prepared.layout());
            ProvenanceEvent finished = events.get(events.size() - 1);
            assertEquals(ProvenanceEventType.RUN_FINISHED, finished.type());
            assertEquals("cancelled", finished.payload().get(ProvenanceEvent.STATUS_KEY));
            Map<String, String> search =
                    RunEvidence.finished(prepared.layout(), EngineStep.RUN_COMET);
            RunEvidence.assertDetail(search, "state", "cancelled");

            ProvenanceManifest manifest = RunEvidence.manifest(prepared.layout());
            assertEquals(ProvenanceStatus.CANCELLED, manifest.run().status());
            List<ToolRecord> tools = RunEvidence.tools(manifest, CometWorkflow.TOOL_NAME);
            assertEquals(1, tools.size());
            assertEquals(Optional.of("comet-01"), tools.get(0).stageId());
            assertEquals(ProvenanceStatus.CANCELLED, tools.get(0).execution().status());
            assertNotEquals(0, tools.get(0).execution().exitCode());
            List<String> partial = new ArrayList<>();
            for (FileRecord file : manifest.files()) {
                if (file.direction() == FileDirection.OUTPUT
                        && file.status() == ProvenanceStatus.PARTIAL) {
                    partial.add(file.role() + " " + file.path().getFileName());
                }
            }
            assertEquals(List.of("pepxml k562_3.pep.xml", "pin k562_3.pin"), partial);
        }
    }
}
