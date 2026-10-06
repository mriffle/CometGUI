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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.provenance.events.ProvenanceEvent;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.provenance.manifest.ProvenanceStatus;
import org.cometgui.provenance.manifest.ToolRecord;
import org.cometgui.tools.process.ProcessRedactor;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.tools.process.StageRunner;
import org.cometgui.workflow.state.EngineStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A step's context on its own: what it refuses before starting anything, and what it records. */
class StepContextTest {

    @TempDir private Path tmp;

    private final List<String> events = new ArrayList<>();

    private StepContext context(EngineFixture fixture, CachingHashService hashes) {
        Clock clock = Clock.systemUTC();
        StageRunner runner =
                new StageRunner(
                        new ProcessService(clock),
                        clock,
                        new ProcessRedactor(SecretRedactor.patternsOnly()),
                        fixture.sink(),
                        fixture.layout().logsDirectory());
        return new StepContext(
                EngineStep.RUN_COMET,
                new StepDeclaration(List.of(), List.of("comet-01", "comet-02")),
                fixture.layout(),
                runner,
                hashes,
                4,
                8,
                (type, payload) -> {
                    synchronized (events) {
                        events.add(type.wireName() + " " + new TreeMap<>(payload));
                    }
                });
    }

    private static List<Path> logs(EngineFixture fixture) throws IOException {
        try (Stream<Path> files = Files.list(fixture.layout().logsDirectory())) {
            return files.toList();
        }
    }

    @Test
    void invocationsReturnInListOrderAndAreRecordedWithTheirOwnEvents()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 2)) {
            StepContext context = context(fixture, fixture.hashes());
            assertEquals(EngineStep.RUN_COMET, context.step());
            assertEquals(fixture.layout(), context.layout());
            assertFalse(context.isCancellationRequested());
            List<InvocationResult> results =
                    context.invokeAll(
                            List.of(
                                    fixture.fake(
                                            "comet-01", "succeed", fixture.pepXml(1).toString()),
                                    fixture.fake(
                                            "comet-02", "succeed", fixture.pepXml(2).toString())),
                            1);
            assertEquals(2, results.size());
            for (int index = 0; index < 2; index++) {
                InvocationResult result = results.get(index);
                assertEquals("comet-0" + (index + 1), result.stageId());
                assertEquals(0, result.exitCode());
                assertEquals(ProvenanceStatus.COMPLETED, result.status());
                assertEquals(fixture.layout().cometLogFile(index + 1), result.logFile());
                assertFalse(result.end().isBefore(result.start()));
            }
            assertEquals(
                    List.of(
                            "tool.invoked {exit=0, stage=comet-01, status=completed,"
                                    + " step=run-comet, tool=fake-comet, tool.version=1.0}",
                            "tool.invoked {exit=0, stage=comet-02, status=completed,"
                                    + " step=run-comet, tool=fake-comet, tool.version=1.0}"),
                    sortedPayloads());
            List<ToolRecord> tools = context.toolRecords();
            assertEquals(Optional.of("comet-01"), tools.get(0).stageId());
            assertEquals(Optional.of("comet-02"), tools.get(1).stageId());
            assertEquals(Optional.of("v1.0"), tools.get(0).releaseTag());
            assertEquals(List.of(), tools.get(0).warnings());
            assertFalse(tools.get(0).managed());
        }
    }

    @Test
    void detailsAreKeptAndReservedOrMalformedKeysRefused()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            StepContext context = context(fixture, fixture.hashes());
            context.addDetail("merge.rows", "6472");
            context.addDetail("files", "2");
            assertEquals(Map.of("merge.rows", "6472", "files", "2"), context.details());
            String expected =
                    "a step detail key must match "
                            + ProvenanceEvent.PAYLOAD_KEY_PATTERN
                            + " and not be one of [attempt, invocations, message, stage, state],"
                            + " but was: \"";
            for (String key : List.of("state", "stage", "attempt", "message", "invocations")) {
                assertEquals(
                        expected + key + "\"",
                        assertThrows(
                                        IllegalArgumentException.class,
                                        () -> context.addDetail(key, "x"))
                                .getMessage());
            }
            assertEquals(
                    expected + "Merge Rows\"",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> context.addDetail("Merge Rows", "x"))
                            .getMessage());
            assertEquals(
                    Set.of("stage", "state", "attempt", "message", "invocations"),
                    StepContext.RESERVED_KEYS);
        }
    }

    @Test
    void anUndeclaredInvocationOrAnUnrecordableToolIsRefusedBeforeAnythingStarts()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            StepContext context = context(fixture, fixture.hashes());
            Invocation undeclared = fixture.fake("comet-09", "succeed");
            assertEquals(
                    "step run-comet did not declare the invocation comet-09",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            context.invokeAll(
                                                    List.of(
                                                            fixture.fake("comet-01", "succeed"),
                                                            undeclared),
                                                    1))
                            .getMessage());
            ToolIdentity relative =
                    new ToolIdentity(
                            "fake",
                            "1",
                            Optional.empty(),
                            Path.of("relative/tool"),
                            fixture.fakeTool().hashes(),
                            false,
                            Optional.empty(),
                            Set.of(),
                            List.of());
            Invocation unrecordable =
                    new Invocation(
                            "comet-01", relative, fixture.fake("comet-01", "succeed").command());
            assertThrows(IllegalArgumentException.class, () -> context.invoke(unrecordable));
            assertEquals(List.of(), logs(fixture), "nothing was started");
            assertEquals(List.of(), context.toolRecords());
        }
    }

    @Test
    void aCancelledContextStartsNothing()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            StepContext context = context(fixture, fixture.hashes());
            context.requestCancellation();
            assertTrue(context.isCancellationRequested());
            assertEquals(
                    "the run was cancelled before comet-01 started",
                    assertThrows(
                                    StepFailedException.class,
                                    () -> context.invoke(fixture.fake("comet-01", "succeed")))
                            .getMessage());
            assertEquals(List.of(), logs(fixture));
        }
    }

    @Test
    void aToolThatCannotStartFailsTheCallAndNothingAfterItStarts()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            StepContext context = context(fixture, fixture.hashes());
            Invocation missing =
                    new Invocation(
                            "comet-01",
                            fixture.fakeTool(),
                            new ToolCommand(
                                    List.of(tmp.resolve("no-such-tool").toString()),
                                    fixture.layout().root(),
                                    Map.of()));
            StepFailedException failed =
                    assertThrows(
                            StepFailedException.class,
                            () ->
                                    context.invokeAll(
                                            List.of(missing, fixture.fake("comet-02", "succeed")),
                                            1));
            assertTrue(
                    failed.getMessage().startsWith("could not start comet-01: java.io.IOException"),
                    failed.getMessage());
            assertFalse(Files.exists(fixture.layout().cometLogFile(2)), "comet-02 never started");
            assertEquals(List.of(), context.toolRecords());
        }
    }

    @Test
    void oneInvocationReturnsItsOwnResult()
            throws IOException, InterruptedException, StepFailedException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            StepContext context = context(fixture, fixture.hashes());
            InvocationResult result =
                    context.invoke(
                            fixture.fake("comet-02", "exit", "0", fixture.pin(1).toString()));
            assertEquals("comet-02", result.stageId());
            assertEquals(0, result.exitCode());
            assertEquals(ProvenanceStatus.COMPLETED, result.status());
            assertEquals(fixture.layout().cometLogFile(2), result.logFile());
        }
    }

    @Test
    void aToolThatCannotStartCancelsTheSiblingAlreadyRunning()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            StepContext context = context(fixture, fixture.hashes());
            Invocation hanging =
                    fixture.fake("comet-01", "fail-after", fixture.records().toString(), "1", "0");
            Invocation missing =
                    new Invocation(
                            "comet-02",
                            fixture.fakeTool(),
                            new ToolCommand(
                                    List.of(tmp.resolve("no-such-tool").toString()),
                                    fixture.layout().root(),
                                    Map.of()));
            // Two may run at once. comet-01 waits for a pid record that never comes, so only
            // cancellation ends it; comet-02 cannot start at all.
            StepFailedException failed =
                    assertTimeoutPreemptively(
                            Duration.ofSeconds(60),
                            () ->
                                    assertThrows(
                                            StepFailedException.class,
                                            () -> context.invokeAll(List.of(hanging, missing), 1)));
            assertTrue(
                    failed.getMessage().startsWith("could not start comet-02: java.io.IOException"),
                    failed.getMessage());
            ToolRecord sibling = context.toolRecords().get(0);
            assertEquals(Optional.of("comet-01"), sibling.stageId());
            assertEquals(ProvenanceStatus.CANCELLED, sibling.execution().status());
            assertEquals(143, sibling.execution().exitCode());
        }
    }

    @Test
    void aLogThatCannotBeHashedIsLeftOutAndSaidSo()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        CachingHashService refusingLogs =
                new CachingHashService(
                        path -> {
                            if (path.toString().endsWith(".log")) {
                                throw new IOException("refused");
                            }
                            return new StreamingHashService().hash(path);
                        });
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            StepContext context = context(fixture, refusingLogs);
            context.invoke(fixture.fake("comet-01", "succeed"));
            ToolRecord tool = context.toolRecords().get(0);
            assertEquals(Optional.empty(), tool.execution().stdout());
            assertEquals(Optional.empty(), tool.execution().stderr());
            assertEquals(
                    List.of(
                            "the log "
                                    + fixture.layout().cometLogFile(1)
                                    + " could not be hashed: java.io.IOException: refused"),
                    tool.warnings());
        }
    }

    @Test
    void interruptingTheWaitingStepCancelsWhatIsRunning()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            StepContext context = context(fixture, fixture.hashes());
            CompletableFuture<String> announced = fixture.sink().when(l -> l.startsWith("pid "));
            AtomicReference<Throwable> thrown = new AtomicReference<>();
            Thread step =
                    new Thread(
                            () -> {
                                try {
                                    context.invoke(
                                            fixture.fake(
                                                    "comet-01",
                                                    "hang",
                                                    fixture.records().toString(),
                                                    "interrupted"));
                                } catch (Exception | AssertionError caught) {
                                    thrown.set(caught);
                                }
                            });
            step.start();
            long pid = Long.parseLong(announced.get(60, TimeUnit.SECONDS).substring(4));
            ProcessHandle process = ProcessHandle.of(pid).orElseThrow();
            try {
                step.interrupt();
                step.join(TimeUnit.SECONDS.toMillis(60));
                assertFalse(step.isAlive(), "the step thread returned");
                assertInstanceOf(InterruptedException.class, thrown.get());
                process.onExit().get(60, TimeUnit.SECONDS);
                assertFalse(process.isAlive(), "the invocation was cancelled");
            } finally {
                process.destroyForcibly();
            }
        }
    }

    private List<String> sortedPayloads() {
        synchronized (events) {
            List<String> sorted = new ArrayList<>(events);
            sorted.sort(String::compareTo);
            return sorted;
        }
    }
}
