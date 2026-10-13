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
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.workflow.provenance.DiskOutcome;
import org.cometgui.workflow.provenance.DiskVerification;
import org.cometgui.workflow.provenance.LoadedRun;
import org.cometgui.workflow.provenance.ProvenanceReader;
import org.cometgui.workflow.provenance.ProvenanceView;
import org.cometgui.workflow.provenance.RecordState;
import org.cometgui.workflow.provenance.WarningSource;
import org.cometgui.workflow.state.EngineStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The read model over records the engine itself wrote -- a completed, a failed and a cancelled run
 * of fake tools -- loaded in one call and exported in one more. The exports are byte-identical to
 * the documents the engine sealed, because the engine and the export use the same writers and,
 * here, the same redactor.
 */
class ProvenanceReaderOnEngineRunsTest {

    @TempDir private Path tmp;

    private static ProvenanceReader reader() {
        return new ProvenanceReader(
                SecretRedactor::patternsOnly, new CachingHashService(new StreamingHashService()));
    }

    private void exportsAreTheSealedDocuments(LoadedRun loaded, EngineFixture fixture)
            throws IOException {
        Path json = loaded.exportJson(tmp.resolve("export.json"), false);
        Path rst = loaded.exportRst(tmp.resolve("export.rst"), false);
        assertArrayEquals(
                Files.readAllBytes(fixture.layout().provenanceJsonFile()),
                Files.readAllBytes(json));
        assertArrayEquals(
                Files.readAllBytes(fixture.layout().provenanceRstFile()), Files.readAllBytes(rst));
    }

    @Test
    void aCompletedRun()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 2)) {
            RunResult result =
                    awaitResult(
                            fixture.engine()
                                    .start(
                                            fixture.request(fixture.standardActions()),
                                            new RecordingListener()));
            assertEquals(AttemptOutcome.SUCCEEDED, result.outcome());

            LoadedRun loaded = reader().load(fixture.layout());

            ProvenanceView view = loaded.view();
            assertAll(
                    () -> assertEquals(RecordState.SEALED, loaded.state()),
                    () -> assertEquals("completed", view.summary().status()),
                    () -> assertEquals("run-0001", view.summary().runId()),
                    () -> assertEquals(List.of("fake-comet 1.0"), view.summary().toolVersions()),
                    () ->
                            assertEquals(
                                    List.of(Optional.of("comet-01"), Optional.of("comet-02")),
                                    view.tools().stream()
                                            .map(ProvenanceView.Tool::stageId)
                                            .toList()),
                    () -> assertEquals(2, view.timeline().executions().size()),
                    () ->
                            assertTrue(
                                    view.tools()
                                            .get(1)
                                            .command()
                                            .endsWith(
                                                    "\"succeed\", \""
                                                            + fixture.pepXml(2)
                                                            + "\", \""
                                                            + fixture.pin(2)
                                                            + "\"]"),
                                    view.tools().get(1).command()),
                    () -> assertTrue(view.files().stream().noneMatch(ProvenanceView.File::partial)),
                    () -> assertEquals(List.of(), view.warnings()));
            exportsAreTheSealedDocuments(loaded, fixture);
            DiskVerification verified = loaded.verifyAgainstDisk(() -> false);
            assertTrue(verified.allMatch(), verified.toString());
        }
    }

    @Test
    void aFailedRun()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            Map<EngineStep, StepAction> actions = new EnumMap<>(fixture.standardActions());
            actions.put(
                    EngineStep.RUN_COMET,
                    FakeStep.doing(
                            fixture.cometDeclaration(),
                            context ->
                                    context.invoke(
                                            fixture.fake(
                                                    "comet-01",
                                                    "partial-then-fail",
                                                    fixture.pepXml(1).toString(),
                                                    "3"))));
            RunResult result =
                    awaitResult(
                            fixture.engine()
                                    .start(fixture.request(actions), new RecordingListener()));
            assertEquals(AttemptOutcome.FAILED, result.outcome());

            LoadedRun loaded = reader().load(fixture.layout());

            ProvenanceView view = loaded.view();
            ProvenanceView.File pepXml =
                    view.files().stream()
                            .filter(file -> file.location().equals(fixture.pepXml(1)))
                            .findFirst()
                            .orElseThrow();
            assertAll(
                    () -> assertEquals("failed", view.summary().status()),
                    () -> assertEquals(3, view.timeline().executions().get(0).exitCode()),
                    () -> assertEquals("failed", view.timeline().executions().get(0).status()),
                    () -> assertEquals("partial", pepXml.status()),
                    () -> assertTrue(pepXml.partial()),
                    () ->
                            assertEquals(
                                    new ProvenanceView.Warning(
                                            WarningSource.STEP_FAILURE,
                                            "run-comet",
                                            "invocation comet-01 exited with code 3; its log is "
                                                    + fixture.layout().cometLogFile(1)),
                                    view.warnings().stream()
                                            .filter(w -> w.source() == WarningSource.STEP_FAILURE)
                                            .findFirst()
                                            .orElseThrow()),
                    () -> assertTrue(partialWarningFor(view, fixture.pepXml(1))));
            exportsAreTheSealedDocuments(loaded, fixture);
            DiskVerification verified = loaded.verifyAgainstDisk(() -> false);
            assertEquals(0L, verified.count(DiskOutcome.DIFFERS), verified.toString());
        }
    }

    private static boolean partialWarningFor(ProvenanceView view, Path file) {
        return view.warnings().stream()
                .anyMatch(
                        warning ->
                                warning.source() == WarningSource.PARTIAL_FILE
                                        && warning.subject().equals(file.toString()));
    }

    @Test
    void aCancelledRun()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        List<ProcessHandle> started = new java.util.ArrayList<>();
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            Map<EngineStep, StepAction> actions = new EnumMap<>(fixture.standardActions());
            actions.put(
                    EngineStep.RUN_COMET,
                    FakeStep.doing(
                            fixture.cometDeclaration(),
                            context ->
                                    context.invoke(
                                            fixture.fake(
                                                    "comet-01",
                                                    "hang",
                                                    fixture.records().toString(),
                                                    "tool",
                                                    fixture.pepXml(1).toString()))));
            CompletableFuture<String> pid = fixture.sink().when(l -> l.startsWith("pid "));
            RunHandle handle =
                    fixture.engine().start(fixture.request(actions), new RecordingListener());
            String line = pid.get(60, TimeUnit.SECONDS);
            ProcessHandle.of(Long.parseLong(line.substring(4).trim())).ifPresent(started::add);
            handle.cancel();
            RunResult result = awaitResult(handle);
            assertEquals(AttemptOutcome.CANCELLED, result.outcome());

            LoadedRun loaded = reader().load(fixture.layout());

            ProvenanceView view = loaded.view();
            assertAll(
                    () -> assertEquals("cancelled", view.summary().status()),
                    () -> assertEquals("cancelled", view.timeline().executions().get(0).status()),
                    () ->
                            assertTrue(
                                    view.files().stream()
                                            .anyMatch(
                                                    file ->
                                                            file.location()
                                                                            .equals(
                                                                                    fixture.pepXml(
                                                                                            1))
                                                                    && file.partial())),
                    () ->
                            assertEquals(
                                    "the run was cancelled; outputs it left are marked partial",
                                    view.warnings().stream()
                                            .filter(w -> w.source() == WarningSource.RUN_OUTCOME)
                                            .findFirst()
                                            .orElseThrow()
                                            .message()));
            exportsAreTheSealedDocuments(loaded, fixture);
        } finally {
            for (ProcessHandle process : started) {
                process.destroyForcibly();
            }
        }
    }
}
