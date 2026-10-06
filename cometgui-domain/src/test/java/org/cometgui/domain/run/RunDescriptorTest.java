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

package org.cometgui.domain.run;

import static org.cometgui.domain.run.RunFixtures.DIGEST_1;
import static org.cometgui.domain.run.RunFixtures.DIGEST_2;
import static org.cometgui.domain.run.RunFixtures.DIGEST_3;
import static org.cometgui.domain.run.RunFixtures.HASHES_A;
import static org.cometgui.domain.run.RunFixtures.HASHES_B;
import static org.cometgui.domain.run.RunFixtures.copy;
import static org.cometgui.domain.run.RunFixtures.fingerprint;
import static org.cometgui.domain.run.RunFixtures.identity;
import static org.cometgui.domain.run.RunFixtures.input;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.cometgui.domain.project.ProjectId;
import org.cometgui.domain.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@code R-RUN-06}: what may change in a run's record and what may not, as {@link
 * RunDescriptor#requireSuccessor(RunDescriptor)} decides it.
 */
class RunDescriptorTest {

    private static final Instant T1 = Instant.parse("2026-08-28T23:15:01.000Z");

    private static final Instant T2 = Instant.parse("2026-08-28T23:16:00.000Z");

    private static final Instant T3 = Instant.parse("2026-08-28T23:17:00.000Z");

    private static final RunDescriptor FRESH = RunDescriptor.of(identity());

    /** One failed attempt, then a second still running with one step recorded. */
    private static RunDescriptor retried() {
        return FRESH.withNewAttempt(T1)
                .withStepSucceeded("hash-inputs", fingerprint(DIGEST_1))
                .withAttemptFinished(AttemptOutcome.FAILED, T2)
                .withNewAttempt(T3)
                .withStepSucceeded("run-comet", fingerprint(DIGEST_2));
    }

    @Test
    @DisplayName("a fresh run has its identity, no attempts and nothing running")
    void fresh() {
        // Built here, not read from the static field: a static initialiser runs once per JVM, so
        // a mutation of RunDescriptor.of would never be seen through FRESH.
        RunDescriptor fresh = RunDescriptor.of(identity());
        assertAll(
                () -> assertEquals(1, RunDescriptor.SCHEMA_VERSION),
                () -> assertEquals(identity(), fresh.identity()),
                () -> assertEquals(List.of(), fresh.attempts()),
                () -> assertEquals(Optional.empty(), fresh.runningAttempt()),
                () -> assertEquals(Map.of(), fresh.succeededFingerprints()));
    }

    @Test
    @DisplayName("attempts are numbered in order and only the latest runs")
    void attempts() {
        RunDescriptor run = retried();
        assertAll(
                () -> assertEquals(2, run.attempts().size()),
                () -> assertEquals(1, run.attempts().get(0).number()),
                () -> assertEquals(AttemptOutcome.FAILED, run.attempts().get(0).outcome()),
                () -> assertEquals(Optional.of(T2), run.attempts().get(0).ended()),
                () -> assertEquals(2, run.attempts().get(1).number()),
                () -> assertEquals(T3, run.attempts().get(1).started()),
                () -> assertEquals(Optional.of(run.attempts().get(1)), run.runningAttempt()),
                () ->
                        assertThrows(
                                UnsupportedOperationException.class, () -> run.attempts().clear()));
    }

    @Test
    @DisplayName("the run's succeeded fingerprints merge every attempt, the latest winning")
    void mergedFingerprints() {
        RunDescriptor run =
                retried()
                        .withStepSucceeded("hash-inputs", fingerprint(DIGEST_3))
                        .withAttemptFinished(AttemptOutcome.SUCCEEDED, T3);
        assertAll(
                () ->
                        assertEquals(
                                Map.of(
                                        "hash-inputs", fingerprint(DIGEST_3),
                                        "run-comet", fingerprint(DIGEST_2)),
                                run.succeededFingerprints()),
                () ->
                        assertEquals(
                                List.of("hash-inputs", "run-comet"),
                                List.copyOf(run.succeededFingerprints().keySet())),
                () -> assertEquals(Optional.empty(), run.runningAttempt()));
    }

    @Test
    @DisplayName("a new attempt cannot start while one is running")
    void oneRunningAtATime() {
        assertEquals(
                "attempt 2 of run run-0001 is still running; a new attempt starts only after it has"
                        + " ended",
                assertThrows(
                                IllegalStateException.class,
                                () -> assertNotNull(retried().withNewAttempt(T3)))
                        .getMessage());
    }

    @Test
    @DisplayName("recording a step or finishing needs a running attempt")
    void needsRunning() {
        RunDescriptor ended =
                FRESH.withNewAttempt(T1).withAttemptFinished(AttemptOutcome.CANCELLED, T2);
        assertAll(
                () ->
                        assertEquals(
                                "run run-0001 has no running attempt",
                                assertThrows(
                                                IllegalStateException.class,
                                                () ->
                                                        assertNotNull(
                                                                FRESH.withStepSucceeded(
                                                                        "run-comet",
                                                                        fingerprint(DIGEST_1))))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "run run-0001 has no running attempt",
                                assertThrows(
                                                IllegalStateException.class,
                                                () ->
                                                        assertNotNull(
                                                                ended.withAttemptFinished(
                                                                        AttemptOutcome.FAILED, T3)))
                                        .getMessage()));
    }

    @Test
    @DisplayName(
            "the constructor refuses misnumbered attempts and a running attempt that is not last")
    void constructorRefuses() {
        RunAttempt first = RunAttempt.started(1, T1);
        RunAttempt second = RunAttempt.started(2, T2);
        assertAll(
                () ->
                        assertEquals(
                                "attempts[0] has number 2, but attempts are numbered 1, 2, 3 ... in"
                                        + " order",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new RunDescriptor(
                                                                identity(), List.of(second)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "attempts[0] is running, but only the latest attempt may be"
                                        + " running",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new RunDescriptor(
                                                                identity(), List.of(first, second)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "identity",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new RunDescriptor(
                                                                Nulls.of(RunIdentity.class),
                                                                List.of()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "attempts",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new RunDescriptor(
                                                                identity(), Nulls.of(List.class)))
                                        .getMessage()));
    }

    @Test
    @DisplayName("every permitted change is accepted: start, record, finish, retry, record again")
    void permittedSuccessors() {
        RunDescriptor a = FRESH;
        RunDescriptor b = a.withNewAttempt(T1);
        RunDescriptor c = b.withStepSucceeded("hash-inputs", fingerprint(DIGEST_1));
        RunDescriptor d = c.withAttemptFinished(AttemptOutcome.FAILED, T2);
        RunDescriptor e = d.withNewAttempt(T3);
        RunDescriptor f = e.withStepSucceeded("run-comet", fingerprint(DIGEST_2));
        a.requireSuccessor(b);
        b.requireSuccessor(c);
        c.requireSuccessor(d);
        d.requireSuccessor(e);
        e.requireSuccessor(f);
        a.requireSuccessor(f);
        f.requireSuccessor(f);
        assertEquals(2, f.attempts().size());
    }

    static Stream<Arguments> identityChanges() {
        RunIdentity g = identity();
        return Stream.of(
                Arguments.of(
                        "runId",
                        copy(
                                g,
                                new RunId("run-0002"),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)),
                Arguments.of(
                        "projectId",
                        copy(
                                g,
                                null,
                                new ProjectId("other"),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)),
                Arguments.of(
                        "created",
                        copy(
                                g,
                                null,
                                null,
                                g.created().plusMillis(1),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)),
                Arguments.of(
                        "cometRelease",
                        copy(g, null, null, null, "2026.02.2", null, null, null, null, null)),
                Arguments.of(
                        "spectra",
                        copy(
                                g,
                                null,
                                null,
                                null,
                                null,
                                RunIdentity.spectraOf(
                                        List.of(input("data/in/k562_3.mzML", HASHES_A))),
                                null,
                                null,
                                null,
                                null)),
                Arguments.of(
                        "spectra[1]",
                        copy(
                                g,
                                null,
                                null,
                                null,
                                null,
                                RunIdentity.spectraOf(
                                        List.of(
                                                input("data/in/k562_3.mzML", HASHES_A),
                                                input("data/in/k562_4.mzML", HASHES_A))),
                                null,
                                null,
                                null,
                                null)),
                Arguments.of(
                        "spectra[0]",
                        copy(
                                g,
                                null,
                                null,
                                null,
                                null,
                                RunIdentity.spectraOf(
                                        List.of(
                                                input("data/in/k562_3.mzML", HASHES_B),
                                                input("data/in/k562_4.mzML", HASHES_B))),
                                null,
                                null,
                                null,
                                null)),
                Arguments.of(
                        "fasta",
                        copy(
                                g,
                                null,
                                null,
                                null,
                                null,
                                null,
                                input("data/db/sub.fasta", HASHES_A),
                                null,
                                null,
                                null)),
                Arguments.of(
                        "parameters",
                        copy(
                                g,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                new ArchivedFile(
                                        "parameters/comet.params", 12346, RunFixtures.HASHES_P),
                                null,
                                null)),
                Arguments.of(
                        "indexMode",
                        copy(g, null, null, null, null, null, null, null, IndexMode.PEPTIDE, null)),
                Arguments.of(
                        "databaseDelivery",
                        copy(
                                g,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                DatabaseDelivery.COMMAND_LINE)));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("identityChanges")
    @DisplayName("a change to any identity member is refused, naming the member")
    void identityImmutable(String member, RunIdentity changed) {
        RunDescriptor before = retried();
        RunDescriptor after = new RunDescriptor(changed, before.attempts());
        RunImmutabilityException thrown =
                assertThrows(RunImmutabilityException.class, () -> before.requireSuccessor(after));
        assertAll(
                () -> assertEquals(member, thrown.member()),
                () ->
                        assertEquals(
                                "run run-0001: \""
                                        + member
                                        + "\" is part of the run's identity, which is written once"
                                        + " when the run starts and never changed; a different"
                                        + " configuration is a new run",
                                thrown.getMessage()));
    }

    @Test
    @DisplayName("removing an attempt is refused")
    void attemptRemoved() {
        RunDescriptor before = retried();
        RunDescriptor after = new RunDescriptor(identity(), before.attempts().subList(0, 1));
        RunImmutabilityException thrown =
                assertThrows(RunImmutabilityException.class, () -> before.requireSuccessor(after));
        assertAll(
                () -> assertEquals("attempts", thrown.member()),
                () ->
                        assertEquals(
                                "run run-0001 records 2 attempt(s) and an attempt is never removed,"
                                        + " but the update has 1",
                                thrown.getMessage()));
    }

    @Test
    @DisplayName("an ended attempt cannot change: not its outcome, end or fingerprints")
    void endedAttemptFrozen() {
        RunDescriptor before =
                FRESH.withNewAttempt(T1).withAttemptFinished(AttemptOutcome.FAILED, T2);
        RunAttempt original = before.attempts().get(0);
        List<RunAttempt> rewrites =
                List.of(
                        new RunAttempt(1, T1, Optional.of(T2), AttemptOutcome.SUCCEEDED, Map.of()),
                        new RunAttempt(1, T1, Optional.of(T3), AttemptOutcome.FAILED, Map.of()),
                        new RunAttempt(
                                1,
                                T1,
                                Optional.of(T2),
                                AttemptOutcome.FAILED,
                                Map.of("run-comet", fingerprint(DIGEST_1))),
                        RunAttempt.started(1, T1));
        for (RunAttempt rewrite : rewrites) {
            RunDescriptor after = new RunDescriptor(identity(), List.of(rewrite));
            RunImmutabilityException thrown =
                    assertThrows(
                            RunImmutabilityException.class,
                            () -> before.requireSuccessor(after),
                            rewrite::toString);
            assertAll(
                    () -> assertEquals("attempts[0]", thrown.member()),
                    () ->
                            assertEquals(
                                    "run run-0001: attempt 1 has ended (failed) and its record"
                                            + " cannot change; a retry is a new attempt",
                                    thrown.getMessage()));
        }
        before.requireSuccessor(new RunDescriptor(identity(), List.of(original)));
    }

    @Test
    @DisplayName("a running attempt's start cannot change")
    void runningStartFrozen() {
        RunDescriptor before = FRESH.withNewAttempt(T1);
        RunDescriptor after = new RunDescriptor(identity(), List.of(RunAttempt.started(1, T2)));
        RunImmutabilityException thrown =
                assertThrows(RunImmutabilityException.class, () -> before.requireSuccessor(after));
        assertAll(
                () -> assertEquals("attempts[0].started", thrown.member()),
                () ->
                        assertEquals(
                                "run run-0001: the start of attempt 1 cannot change",
                                thrown.getMessage()));
    }

    @Test
    @DisplayName("a running attempt's recorded fingerprint cannot be removed or changed")
    void runningFingerprintsFrozen() {
        RunDescriptor before = retried();
        RunDescriptor removed =
                new RunDescriptor(
                        identity(), List.of(before.attempts().get(0), RunAttempt.started(2, T3)));
        RunDescriptor changed =
                new RunDescriptor(
                        identity(),
                        List.of(
                                before.attempts().get(0),
                                RunAttempt.started(2, T3)
                                        .withStepSucceeded("run-comet", fingerprint(DIGEST_3))));
        for (RunDescriptor after : List.of(removed, changed)) {
            RunImmutabilityException thrown =
                    assertThrows(
                            RunImmutabilityException.class, () -> before.requireSuccessor(after));
            assertAll(
                    () -> assertEquals("attempts[1].succeededSteps.run-comet", thrown.member()),
                    () ->
                            assertEquals(
                                    "run run-0001: attempt 2 recorded step run-comet as succeeded,"
                                            + " and that record cannot be changed or removed",
                                    thrown.getMessage()));
        }
    }

    @Test
    @DisplayName("a null successor is refused by name")
    void nullSuccessor() {
        assertEquals(
                "next",
                assertThrows(
                                NullPointerException.class,
                                () -> FRESH.requireSuccessor(Nulls.of(RunDescriptor.class)))
                        .getMessage());
    }
}
