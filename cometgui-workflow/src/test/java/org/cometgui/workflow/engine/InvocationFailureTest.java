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

import static org.cometgui.workflow.engine.EngineAssertions.awaitResult;
import static org.cometgui.workflow.engine.EngineAssertions.parsedStageLog;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.cometgui.provenance.manifest.ManifestReader;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ProvenanceStatus;
import org.cometgui.provenance.manifest.ToolRecord;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.StepState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The first failing invocation fails the step: siblings in flight are cancelled -- proved dead by
 * their own pids -- siblings not yet started never start, and every log written is kept ({@code
 * R-CMT-05}, P8-15).
 */
class InvocationFailureTest {

    @TempDir private Path tmp;

    @Test
    void oneFailureCancelsTheSiblingsInFlightStartsNoMoreAndKeepsEveryLog()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        List<Long> pids = new ArrayList<>();
        try (EngineFixture fixture = EngineFixture.create(tmp, 5)) {
            Path records = Files.createDirectories(tmp.resolve("failure"));
            List<Invocation> invocations =
                    List.of(
                            fixture.fake(
                                    "comet-01",
                                    "hang",
                                    records.toString(),
                                    "h1",
                                    fixture.pepXml(1).toString()),
                            fixture.fake(
                                    "comet-02",
                                    "hang",
                                    records.toString(),
                                    "h2",
                                    fixture.pepXml(2).toString()),
                            // Fails only once both hanging siblings have recorded their pids.
                            fixture.fake("comet-03", "fail-after", records.toString(), "2", "3"),
                            fixture.fake(
                                    "comet-04",
                                    "succeed",
                                    fixture.pepXml(4).toString(),
                                    fixture.pin(4).toString()),
                            fixture.fake(
                                    "comet-05",
                                    "succeed",
                                    fixture.pepXml(5).toString(),
                                    fixture.pin(5).toString()));
            Map<EngineStep, StepAction> actions = new EnumMap<>(fixture.standardActions());
            actions.put(
                    EngineStep.RUN_COMET,
                    FakeStep.doing(
                            fixture.cometDeclaration(),
                            context -> context.invokeAll(invocations, 1)));
            // A cap of three: comet-01, -02 and -03 start; -04 and -05 wait for a free slot.
            WorkflowEngine engine = new WorkflowEngine(fixture.services(EngineFixture.CORES, 3));
            RunResult result;
            try {
                result =
                        awaitResult(
                                engine.start(fixture.request(actions), new RecordingListener()));
            } finally {
                for (String name : List.of("h1", "h2")) {
                    Path pid = records.resolve(name + ".pid");
                    if (Files.exists(pid)) {
                        pids.add(Long.parseLong(Files.readString(pid, StandardCharsets.UTF_8)));
                    }
                }
            }

            assertEquals(StepState.FAILED, result.states().get(EngineStep.RUN_COMET));
            assertEquals(
                    "invocation comet-03 exited with code 3; its log is "
                            + fixture.layout().cometLogFile(3),
                    result.failures().get(EngineStep.RUN_COMET));
            assertEquals(2, pids.size(), "both hanging siblings recorded their pids");
            for (long pid : pids) {
                assertFalse(
                        ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false),
                        "the cancelled sibling " + pid + " is still alive");
            }

            ProvenanceManifest manifest =
                    ManifestReader.readFrom(fixture.layout().provenanceJsonFile());
            List<String> recorded = new ArrayList<>();
            for (ToolRecord tool : manifest.tools()) {
                recorded.add(
                        tool.stageId().orElse("?")
                                + " "
                                + tool.execution().status().wireName()
                                + " "
                                + tool.execution().exitCode());
            }
            assertEquals(
                    List.of(
                            "comet-01 cancelled 143",
                            "comet-02 cancelled 143",
                            "comet-03 failed 3"),
                    recorded,
                    "143 is SIGTERM; 71 would mean the fake's watchdog, not the engine, ended it");

            for (int position = 1; position <= 3; position++) {
                List<String> lines =
                        parsedStageLog(
                                fixture.layout().cometLogFile(position),
                                EngineFixture.stageId(position));
                assertTrue(lines.get(1).contains("[cometgui] command "), lines.get(1));
            }
            assertTrue(
                    String.join("\n", parsedStageLog(fixture.layout().cometLogFile(3), "comet-03"))
                            .contains("[stderr] failing after my siblings started, with 3"));
            assertFalse(Files.exists(fixture.layout().cometLogFile(4)), "comet-04 never started");
            assertFalse(Files.exists(fixture.layout().cometLogFile(5)), "comet-05 never started");
            assertFalse(Files.exists(fixture.pepXml(4)));

            // The hanging siblings' partial outputs are recorded as partial.
            assertTrue(
                    WorkflowEngineRunTest.describe(manifest.files())
                            .contains("output pepxml " + fixture.pepXml(1) + " partial"));
            assertEquals(
                    Optional.of(ProvenanceStatus.FAILED), Optional.of(manifest.run().status()));
        } finally {
            for (long pid : pids) {
                ProcessHandle.of(pid).ifPresent(ProcessHandle::destroyForcibly);
            }
        }
    }
}
