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
import static org.cometgui.domain.run.RunFixtures.fingerprint;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.cometgui.domain.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Recorded fingerprints and attempts. */
class RunAttemptTest {

    private static final Instant T0 = Instant.parse("2026-08-28T23:15:01.000Z");

    private static final Instant T1 = Instant.parse("2026-08-28T23:20:00.000Z");

    @Test
    @DisplayName("a fingerprint keeps its value and its digests sorted by input kind")
    void fingerprintValues() {
        Map<String, String> digests = new HashMap<>();
        digests.put("spectrum-files", DIGEST_2);
        digests.put("comet-parameters", DIGEST_1);
        RecordedFingerprint recorded = new RecordedFingerprint(DIGEST_3, digests);
        digests.put("fasta", DIGEST_3);
        assertAll(
                () -> assertEquals(DIGEST_3, recorded.value()),
                () ->
                        assertEquals(
                                List.of("comet-parameters", "spectrum-files"),
                                List.copyOf(recorded.inputDigests().keySet())),
                () -> assertEquals(DIGEST_1, recorded.inputDigests().get("comet-parameters")),
                () ->
                        assertThrows(
                                UnsupportedOperationException.class,
                                () -> recorded.inputDigests().put("x", DIGEST_1)));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(
            strings = {
                "",
                "abc",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                "111111111111111111111111111111111111111111111111111111111111111",
                "11111111111111111111111111111111111111111111111111111111111111111",
                "g111111111111111111111111111111111111111111111111111111111111111"
            })
    @DisplayName("a fingerprint that is not 64 lower-case hex characters is refused, quoting it")
    void fingerprintRefusesValue(String value) {
        assertEquals(
                "fingerprint must be 64 lower-case hexadecimal characters, but was: \""
                        + value
                        + "\"",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new RecordedFingerprint(value, Map.of()))
                        .getMessage());
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"", "Comet", "comet_parameters", "-comet", "comet-", "a--b", "a b"})
    @DisplayName("an input-kind key that is not a hyphenated identifier is refused")
    void fingerprintRefusesKey(String key) {
        assertEquals(
                "input kind must be lower-case letters and digits in hyphen-separated words, but"
                        + " was: \""
                        + key
                        + "\"",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new RecordedFingerprint(DIGEST_1, Map.of(key, DIGEST_2)))
                        .getMessage());
    }

    @Test
    @DisplayName("a malformed input digest and nulls are refused by name")
    void fingerprintRefusesDigest() {
        assertAll(
                () ->
                        assertEquals(
                                "digest of fasta must be 64 lower-case hexadecimal characters, but"
                                        + " was: \"x\"",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new RecordedFingerprint(
                                                                DIGEST_1, Map.of("fasta", "x")))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "fingerprint",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new RecordedFingerprint(
                                                                Nulls.of(String.class), Map.of()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "inputDigests",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new RecordedFingerprint(
                                                                DIGEST_1, Nulls.of(Map.class)))
                                        .getMessage()));
    }

    @Test
    @DisplayName("a started attempt is running, has no end and no fingerprints")
    void started() {
        RunAttempt attempt = RunAttempt.started(1, Instant.parse("2026-08-28T23:15:01.999999Z"));
        assertAll(
                () -> assertEquals(1, attempt.number()),
                () -> assertEquals(Instant.parse("2026-08-28T23:15:01.999Z"), attempt.started()),
                () -> assertEquals(Optional.empty(), attempt.ended()),
                () -> assertEquals(AttemptOutcome.RUNNING, attempt.outcome()),
                () -> assertEquals(Map.of(), attempt.succeededSteps()));
    }

    @Test
    @DisplayName("steps recorded as succeeded accumulate, sorted; the same one twice is harmless")
    void accumulates() {
        RunAttempt attempt =
                RunAttempt.started(2, T0)
                        .withStepSucceeded("run-comet", fingerprint(DIGEST_1))
                        .withStepSucceeded("hash-inputs", fingerprint(DIGEST_2))
                        .withStepSucceeded("run-comet", fingerprint(DIGEST_1));
        assertAll(
                () ->
                        assertEquals(
                                List.of("hash-inputs", "run-comet"),
                                List.copyOf(attempt.succeededSteps().keySet())),
                () ->
                        assertEquals(
                                fingerprint(DIGEST_1), attempt.succeededSteps().get("run-comet")),
                () ->
                        assertThrows(
                                UnsupportedOperationException.class,
                                () -> attempt.succeededSteps().clear()));
    }

    @Test
    @DisplayName("a step cannot be re-recorded with a different fingerprint")
    void conflictingFingerprint() {
        RunAttempt attempt =
                RunAttempt.started(1, T0).withStepSucceeded("run-comet", fingerprint(DIGEST_1));
        assertEquals(
                "attempt 1 already records step run-comet as succeeded with a different"
                        + " fingerprint",
                assertThrows(
                                IllegalStateException.class,
                                () ->
                                        assertNotNull(
                                                attempt.withStepSucceeded(
                                                        "run-comet", fingerprint(DIGEST_2))))
                        .getMessage());
    }

    @Test
    @DisplayName("finishing sets the end and outcome; an ended attempt cannot change")
    void finishes() {
        RunAttempt running =
                RunAttempt.started(1, T0).withStepSucceeded("hash-inputs", fingerprint(DIGEST_1));
        RunAttempt ended = running.finished(AttemptOutcome.FAILED, T1);
        assertAll(
                () -> assertEquals(AttemptOutcome.FAILED, ended.outcome()),
                () -> assertEquals(Optional.of(T1), ended.ended()),
                () -> assertEquals(running.succeededSteps(), ended.succeededSteps()),
                () -> assertEquals(T0, ended.started()),
                () ->
                        assertEquals(
                                "attempt 1 has already ended (failed)",
                                assertThrows(
                                                IllegalStateException.class,
                                                () ->
                                                        assertNotNull(
                                                                ended.finished(
                                                                        AttemptOutcome.SUCCEEDED,
                                                                        T1)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "attempt 1 has ended (failed) and cannot change",
                                assertThrows(
                                                IllegalStateException.class,
                                                () ->
                                                        assertNotNull(
                                                                ended.withStepSucceeded(
                                                                        "run-comet",
                                                                        fingerprint(DIGEST_1))))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "attempt 1 is running but has an end time",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        assertNotNull(
                                                                running.finished(
                                                                        AttemptOutcome.RUNNING,
                                                                        T1)))
                                        .getMessage()));
    }

    @Test
    @DisplayName("an attempt may end at the instant it started, never before")
    void endBeforeStart() {
        assertAll(
                () ->
                        assertEquals(
                                Optional.of(T0),
                                RunAttempt.started(1, T0)
                                        .finished(AttemptOutcome.CANCELLED, T0)
                                        .ended()),
                () ->
                        assertEquals(
                                "attempt 1 ends before it starts",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        assertNotNull(
                                                                RunAttempt.started(1, T1)
                                                                        .finished(
                                                                                AttemptOutcome
                                                                                        .FAILED,
                                                                                T1.minusMillis(1))))
                                        .getMessage()));
    }

    @Test
    @DisplayName("the end is truncated to milliseconds")
    void endTruncated() {
        RunAttempt ended =
                RunAttempt.started(1, T0)
                        .finished(
                                AttemptOutcome.SUCCEEDED,
                                Instant.parse("2026-08-28T23:20:00.000999Z"));
        assertEquals(Optional.of(T1), ended.ended());
    }

    @Test
    @DisplayName("the constructor refuses a bad number, a terminal attempt without end, and nulls")
    void constructorRefuses() {
        assertAll(
                () ->
                        assertEquals(
                                "an attempt number is 1-based, but was 0",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> RunAttempt.started(0, T0))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "attempt 3 is succeeded but has no end time",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new RunAttempt(
                                                                3,
                                                                T0,
                                                                Optional.empty(),
                                                                AttemptOutcome.SUCCEEDED,
                                                                Map.of()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "step id must be lower-case letters and digits in hyphen-separated"
                                        + " words, but was: \"RUN_COMET\"",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new RunAttempt(
                                                                1,
                                                                T0,
                                                                Optional.empty(),
                                                                AttemptOutcome.RUNNING,
                                                                Map.of(
                                                                        "RUN_COMET",
                                                                        fingerprint(DIGEST_1))))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "started",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        RunAttempt.started(
                                                                1, Nulls.of(Instant.class)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "ended",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new RunAttempt(
                                                                1,
                                                                T0,
                                                                Nulls.of(Optional.class),
                                                                AttemptOutcome.RUNNING,
                                                                Map.of()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "outcome",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new RunAttempt(
                                                                1,
                                                                T0,
                                                                Optional.empty(),
                                                                Nulls.of(AttemptOutcome.class),
                                                                Map.of()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "succeededSteps",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new RunAttempt(
                                                                1,
                                                                T0,
                                                                Optional.empty(),
                                                                AttemptOutcome.RUNNING,
                                                                Nulls.of(Map.class)))
                                        .getMessage()));
    }

    @Test
    @DisplayName("a null fingerprint value in the map is refused naming the step")
    void nullFingerprint() {
        Map<String, RecordedFingerprint> steps = new HashMap<>();
        steps.put("run-comet", null);
        assertEquals(
                "fingerprint of run-comet",
                assertThrows(
                                NullPointerException.class,
                                () ->
                                        new RunAttempt(
                                                1,
                                                T0,
                                                Optional.empty(),
                                                AttemptOutcome.RUNNING,
                                                steps))
                        .getMessage());
    }

    @Test
    @DisplayName("the attempt's map is a copy: changing the caller's map changes nothing")
    void copies() {
        Map<String, RecordedFingerprint> steps = new HashMap<>();
        steps.put("run-comet", fingerprint(DIGEST_1));
        RunAttempt attempt = new RunAttempt(1, T0, Optional.empty(), AttemptOutcome.RUNNING, steps);
        steps.clear();
        assertEquals(1, attempt.succeededSteps().size());
    }
}
