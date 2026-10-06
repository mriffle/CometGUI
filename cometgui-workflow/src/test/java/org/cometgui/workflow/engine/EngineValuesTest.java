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

import static org.cometgui.workflow.testing.TestPaths.absolute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.provenance.manifest.ApplicationRecord;
import org.cometgui.provenance.manifest.FileDirection;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ProvenanceStatus;
import org.cometgui.provenance.manifest.RunRecord;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.RerunPreview;
import org.cometgui.workflow.state.RunState;
import org.cometgui.workflow.state.StepState;
import org.cometgui.workflow.testing.Nulls;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The engine's value types: what each refuses, and that none can be changed from outside. */
class EngineValuesTest {

    private static final FileHashes HASHES = new FileHashes("0".repeat(32), "1".repeat(64));

    @TempDir private Path tmp;

    @Test
    void aDeclaredFileIsAbsoluteNormalisedAndHasARole() {
        DeclaredFile input = DeclaredFile.input("spectrum", absolute("data/./in/../in/a.mzML"));
        assertEquals(FileDirection.INPUT, input.direction());
        assertEquals(absolute("data/in/a.mzML"), input.path());
        assertEquals(
                FileDirection.OUTPUT, DeclaredFile.output("pin", absolute("o/a.pin")).direction());
        assertEquals(
                "a declared file must be absolute: rel/a.pin",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> DeclaredFile.output("pin", Path.of("rel/a.pin")))
                        .getMessage());
        assertEquals(
                "a declared file needs a role",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> DeclaredFile.output(" ", absolute("o/a.pin")))
                        .getMessage());
        assertEquals(
                "direction",
                assertThrows(
                                NullPointerException.class,
                                () -> new DeclaredFile(null, "r", absolute("a")))
                        .getMessage());
        assertEquals(
                "role",
                assertThrows(
                                NullPointerException.class,
                                () -> new DeclaredFile(FileDirection.INPUT, null, absolute("a")))
                        .getMessage());
        assertEquals(
                "path",
                assertThrows(
                                NullPointerException.class,
                                () -> new DeclaredFile(FileDirection.INPUT, "r", null))
                        .getMessage());
    }

    @Test
    void aDeclarationRefusesRepeatsAndMalformedInvocationIdentifiers() {
        Path a = absolute("o/a");
        assertEquals(
                "the output /o/a is declared twice",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new StepDeclaration(
                                                List.of(
                                                        DeclaredFile.output("x", a),
                                                        DeclaredFile.output("y", a)),
                                                List.of()))
                        .getMessage());
        StepDeclaration both =
                new StepDeclaration(
                        List.of(DeclaredFile.output("x", a), DeclaredFile.input("x", a)),
                        List.of("comet-01", "Comet_2"));
        assertEquals(2, both.files().size(), "one path may be read and written");
        assertTrue(both.declares("comet-01"));
        assertTrue(both.declares("Comet_2"));
        assertFalse(both.declares("comet-03"));
        assertEquals(
                "an invocation identifier must match [A-Za-z0-9_-]{1,64}, but was: \"comet 01\"",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new StepDeclaration(List.of(), List.of("comet 01")))
                        .getMessage());
        assertThrows(
                IllegalArgumentException.class,
                () -> new StepDeclaration(List.of(), List.of("x".repeat(65))));
        assertEquals(
                new StepDeclaration(List.of(), List.of("x".repeat(64))).invocationIds(),
                List.of("x".repeat(64)));
        assertEquals(
                "the invocation comet-01 is declared twice",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new StepDeclaration(
                                                List.of(), List.of("comet-01", "comet-01")))
                        .getMessage());
        assertEquals(List.of(), StepDeclaration.NOTHING.files());
        assertEquals(List.of(), StepDeclaration.NOTHING.invocationIds());
        List<String> ids = new ArrayList<>(List.of("a"));
        StepDeclaration copied = new StepDeclaration(List.of(), ids);
        ids.add("b");
        assertEquals(List.of("a"), copied.invocationIds(), "the declaration copies its lists");
    }

    @Test
    void aToolIdentityCopiesItsCollections() {
        Set<String> capabilities = new HashSet<>(Set.of("xml"));
        List<String> warnings = new ArrayList<>(List.of("advisory"));
        ToolIdentity tool =
                new ToolIdentity(
                        "comet",
                        "2026.03.0",
                        Optional.empty(),
                        absolute("t/comet"),
                        HASHES,
                        true,
                        Optional.empty(),
                        capabilities,
                        warnings);
        capabilities.add("later");
        warnings.add("later");
        assertEquals(Set.of("xml"), tool.capabilities());
        assertEquals(List.of("advisory"), tool.warnings());
    }

    @Test
    void theSmallRecordsRequireEveryComponent() {
        ToolCommand command = new ToolCommand(List.of("/bin/x"), absolute("w"), Map.of());
        ToolIdentity tool = tool();
        assertEquals(
                "stageId",
                assertThrows(NullPointerException.class, () -> new Invocation(null, tool, command))
                        .getMessage());
        assertEquals(
                "tool",
                assertThrows(NullPointerException.class, () -> new Invocation("a", null, command))
                        .getMessage());
        assertEquals(
                "command",
                assertThrows(NullPointerException.class, () -> new Invocation("a", tool, null))
                        .getMessage());
        Instant now = Instant.parse("2026-10-06T00:00:00Z");
        Path log = absolute("l");
        ProvenanceStatus ok = ProvenanceStatus.COMPLETED;
        assertEquals(
                "stageId",
                assertThrows(
                                NullPointerException.class,
                                () -> new InvocationResult(null, 0, ok, log, now, now))
                        .getMessage());
        assertEquals(
                "status",
                assertThrows(
                                NullPointerException.class,
                                () -> new InvocationResult("a", 0, null, log, now, now))
                        .getMessage());
        assertEquals(
                "logFile",
                assertThrows(
                                NullPointerException.class,
                                () -> new InvocationResult("a", 0, ok, null, now, now))
                        .getMessage());
        assertEquals(
                "start",
                assertThrows(
                                NullPointerException.class,
                                () -> new InvocationResult("a", 0, ok, log, null, now))
                        .getMessage());
        assertEquals(
                "end",
                assertThrows(
                                NullPointerException.class,
                                () -> new InvocationResult("a", 0, ok, log, now, null))
                        .getMessage());
        EngineStep step = EngineStep.RUN_COMET;
        StepState s = StepState.RUNNING;
        RunState r = RunState.RUNNING;
        assertEquals(
                "step",
                assertThrows(
                                NullPointerException.class,
                                () -> new StepTransition(null, s, s, now, r))
                        .getMessage());
        assertEquals(
                "from",
                assertThrows(
                                NullPointerException.class,
                                () -> new StepTransition(step, null, s, now, r))
                        .getMessage());
        assertEquals(
                "to",
                assertThrows(
                                NullPointerException.class,
                                () -> new StepTransition(step, s, null, now, r))
                        .getMessage());
        assertEquals(
                "at",
                assertThrows(
                                NullPointerException.class,
                                () -> new StepTransition(step, s, s, null, r))
                        .getMessage());
        assertEquals(
                "runState",
                assertThrows(
                                NullPointerException.class,
                                () -> new StepTransition(step, s, s, now, null))
                        .getMessage());
        assertEquals(
                "id",
                assertThrows(NullPointerException.class, () -> new InvocationTag(null, "d"))
                        .getMessage());
        assertEquals(
                "displayName",
                assertThrows(NullPointerException.class, () -> new InvocationTag("i", null))
                        .getMessage());
        StepStateListener.NONE.onTransition(new StepTransition(step, s, s, now, r));
    }

    @Test
    void engineServicesRequireAtLeastOneCoreAndACapOfAtLeastOne() {
        Clock clock = Clock.systemUTC();
        ProcessService processes = new ProcessService(clock);
        CachingHashService hashes = new CachingHashService(new StreamingHashService());
        SecretRedactor redactor = SecretRedactor.patternsOnly();
        assertEquals(
                "availableCores must be at least 1, but was 0",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new EngineServices(
                                                processes, clock, redactor, m -> {}, hashes, 0, 1))
                        .getMessage());
        assertEquals(
                "invocationCap must be at least 1, but was 0",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new EngineServices(
                                                processes, clock, redactor, m -> {}, hashes, 1, 0))
                        .getMessage());
        EngineServices services =
                new EngineServices(processes, clock, redactor, m -> {}, hashes, 1, 1);
        assertEquals(1, services.availableCores());
        assertEquals(1, services.invocationCap());
        assertEquals(
                "processes",
                assertThrows(
                                NullPointerException.class,
                                () ->
                                        new EngineServices(
                                                null, clock, redactor, m -> {}, hashes, 1, 1))
                        .getMessage());
        assertEquals(
                "clock",
                assertThrows(
                                NullPointerException.class,
                                () ->
                                        new EngineServices(
                                                processes, null, redactor, m -> {}, hashes, 1, 1))
                        .getMessage());
        assertEquals(
                "redactor",
                assertThrows(
                                NullPointerException.class,
                                () ->
                                        new EngineServices(
                                                processes, clock, null, m -> {}, hashes, 1, 1))
                        .getMessage());
        assertEquals(
                "sink",
                assertThrows(
                                NullPointerException.class,
                                () ->
                                        new EngineServices(
                                                processes, clock, redactor, null, hashes, 1, 1))
                        .getMessage());
        assertEquals(
                "hashes",
                assertThrows(
                                NullPointerException.class,
                                () ->
                                        new EngineServices(
                                                processes, clock, redactor, m -> {}, null, 1, 1))
                        .getMessage());
        assertEquals(
                "services",
                assertThrows(NullPointerException.class, () -> new WorkflowEngine(null))
                        .getMessage());
    }

    @Test
    void aRunRequestRefusesMalformedAndReservedSettingsAndCopiesWhatItHolds() throws IOException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            RunRequest base = fixture.request(fixture.standardActions());
            assertEquals(
                    "a settings key must match [a-z0-9]+(\\.[a-z0-9-]+)+, but was: \"Comet\"",
                    assertThrows(IllegalArgumentException.class, () -> withSettings(base, "Comet"))
                            .getMessage());
            assertEquals(
                    "the settings key \"workflow.attempt\" is reserved for the engine",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> withSettings(base, "workflow.attempt"))
                            .getMessage());
            assertEquals(
                    "the settings key \"workflow.plan\" is reserved for the engine",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> withSettings(base, "workflow.plan"))
                            .getMessage());
            Map<EngineStep, StepAction> actions = new EnumMap<>(fixture.standardActions());
            Set<EngineStep> forced = new HashSet<>(Set.of(EngineStep.RUN_COMET));
            Map<String, String> settings = new HashMap<>(Map.of("comet.release", "x"));
            RunRequest request =
                    new RunRequest(
                            base.store(),
                            base.lock(),
                            base.layout(),
                            base.plan(),
                            base.inputs(),
                            forced,
                            actions,
                            base.application(),
                            settings);
            actions.remove(EngineStep.RUN_COMET);
            forced.clear();
            settings.put("comet.other", "y");
            assertTrue(request.actions().containsKey(EngineStep.RUN_COMET));
            assertEquals(Set.of(EngineStep.RUN_COMET), request.forced());
            assertEquals(Map.of("comet.release", "x"), request.settings());
            RunRequest forcedMerge = request.withForced(Set.of(EngineStep.MERGE_PIN));
            assertEquals(Set.of(EngineStep.MERGE_PIN), forcedMerge.forced());
            assertEquals(Set.of(EngineStep.RUN_COMET), request.forced());
            assertSame(request.plan(), forcedMerge.plan());
            assertEquals(request.settings(), forcedMerge.settings());
            Map<EngineStep, StepAction> nullAction = new HashMap<>();
            nullAction.put(EngineStep.RUN_COMET, null);
            assertEquals(
                    "actions has a null action",
                    assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            new RunRequest(
                                                    base.store(),
                                                    base.lock(),
                                                    base.layout(),
                                                    base.plan(),
                                                    base.inputs(),
                                                    Set.of(),
                                                    nullAction,
                                                    base.application(),
                                                    Map.of()))
                            .getMessage());
        }
    }

    private static RunRequest withSettings(RunRequest base, String key) {
        return new RunRequest(
                base.store(),
                base.lock(),
                base.layout(),
                base.plan(),
                base.inputs(),
                Set.of(),
                base.actions(),
                base.application(),
                Map.of(key, "value"));
    }

    @Test
    void aRunResultCopiesItsCollections() {
        Map<EngineStep, StepState> states = new EnumMap<>(EngineStep.class);
        states.put(EngineStep.RUN_COMET, StepState.SUCCEEDED);
        Map<EngineStep, String> failures = new EnumMap<>(EngineStep.class);
        List<String> errors = new ArrayList<>();
        ProvenanceManifest manifest =
                ProvenanceManifest.current(
                        new RunRecord(
                                new org.cometgui.domain.run.RunId("r"),
                                "p",
                                ProvenanceStatus.COMPLETED,
                                Instant.EPOCH,
                                Optional.of(Instant.EPOCH)),
                        ApplicationRecord.capture("v", "b"),
                        Map.of(),
                        List.of(),
                        List.of());
        RunResult result =
                new RunResult(
                        1,
                        AttemptOutcome.SUCCEEDED,
                        RunState.SUCCEEDED,
                        states,
                        failures,
                        manifest,
                        errors,
                        0L);
        states.put(EngineStep.MERGE_PIN, StepState.FAILED);
        failures.put(EngineStep.MERGE_PIN, "x");
        errors.add("x");
        assertEquals(Map.of(EngineStep.RUN_COMET, StepState.SUCCEEDED), result.states());
        assertEquals(Map.of(), result.failures());
        assertEquals(List.of(), result.finalisationErrors());
    }

    @Test
    void theExceptionsCarryTheirMessageCauseAndCheck() {
        IOException cause = new IOException("disk");
        StepFailedException failed = new StepFailedException("it failed", cause);
        assertEquals("it failed", failed.getMessage());
        assertSame(cause, failed.getCause());
        assertEquals(
                "check",
                assertThrows(
                                NullPointerException.class,
                                () -> {
                                    throw new ReuseRefusedException(Nulls.of(ReuseCheck.class));
                                })
                        .getMessage());
    }

    @Test
    void aReuseCheckOffersAPlanExactlyWhenItRefuses() throws IOException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            RerunPreview preview =
                    RerunPreview.compute(EngineFixture.PLAN, fixture.inputs(), Map.of());
            assertEquals(
                    "a plan is offered exactly when reuse is refused",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            new ReuseCheck(
                                                    preview,
                                                    List.of(),
                                                    Optional.of(preview),
                                                    Map.of(),
                                                    Map.of()))
                            .getMessage());
            ReuseMismatch mismatch =
                    new ReuseMismatch(
                            EngineStep.RUN_COMET,
                            ReuseMismatch.Kind.NOT_RECORDED,
                            "/x",
                            "r",
                            Optional.empty(),
                            Optional.empty());
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new ReuseCheck(
                                    preview,
                                    List.of(mismatch),
                                    Optional.empty(),
                                    Map.of(),
                                    Map.of()));
            ReuseCheck refused =
                    new ReuseCheck(
                            preview, List.of(mismatch), Optional.of(preview), Map.of(), Map.of());
            assertFalse(refused.accepted());
            assertEquals(Set.of(EngineStep.RUN_COMET), refused.offeredForced());
        }
    }

    private static ToolIdentity tool() {
        return new ToolIdentity(
                "comet",
                "1",
                Optional.empty(),
                absolute("t/comet"),
                HASHES,
                true,
                Optional.empty(),
                Set.of(),
                List.of());
    }
}
