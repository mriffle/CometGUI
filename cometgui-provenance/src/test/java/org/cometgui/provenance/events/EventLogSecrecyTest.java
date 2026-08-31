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

package org.cometgui.provenance.events;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.domain.secrets.SecretRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The seeded secret corpus, carried through the event log and then grepped off the disk.
 *
 * <p>This is the event-log half of phase 04's exit gate item 6 -- "a seeded corpus of secrets
 * appears nowhere in JSON, RST or logs" -- proved here on the file the writer actually produced
 * rather than on a string in memory. The thirteen values are hand-transcribed from {@code
 * SeededSecretCorpusTest} beside the shared rule set, character for character; that file is the
 * authority on them and its class documentation records the two ways a sweep like this goes blind.
 * Both are inherited here deliberately:
 *
 * <ol>
 *   <li><b>A partial rewrite of the secret defeats {@code contains}.</b> So the PEM private key is
 *       swept line by line, not as one block: a rule that mangled one character of it would leave
 *       whole lines of key material for this test to find.
 *   <li><b>A leak conditioned on the input's size is invisible to long carriers.</b> So {@link
 *       #shortCarriers()} puts one twelve-character secret through each rule family, in the
 *       smallest text that rule can appear in, and their expected log lines are pinned in full. Do
 *       not lengthen them.
 * </ol>
 *
 * <p><strong>Absence is asserted second, never first.</strong> A log that redacted everything, or
 * that was empty, would satisfy a sweep and be useless, so each carrier also has a hand-typed
 * expected line: the assertion fails when a secret survives and equally when something that was not
 * a secret is destroyed.
 *
 * <p>One property here belongs to this package rather than to the redactor: redaction happens
 * <em>before</em> serialisation, so a multi-line secret is caught while it still has its newlines
 * in it. A PEM private key put into a payload arrives at the redactor as the block the PEM rule
 * matches; had the escaping run first, the rule would have been looking for newlines that were by
 * then the two characters {@code \} and {@code n}.
 */
class EventLogSecrecyTest {

    // The ten seeded secrets from the phase brief, plus the three lines of the PEM body, all
    // hand-transcribed from SeededSecretCorpusTest.
    private static final String AWS_SECRET = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY";

    private static final String GITHUB_TOKEN = "ghp_S3cr3tT0k3nExampleValue0123456789ab";

    private static final String JWT =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIn0"
                    + ".dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXk";

    private static final String PASSWORD = "hunter2-not-a-real-password";

    private static final String URL_PASSWORD = "Tr0ub4dor-26-3";

    private static final String LIMELIGHT_KEY = "ll_live_9f8e7d6c5b4a39281706";

    private static final String PASSPHRASE = "correct-horse-battery-staple";

    private static final String SWORDFISH = "swordfish-42";

    private static final String LIVE_TOKEN = "tok_live_abcdef0123456789";

    private static final String AWS_ACCESS_KEY_ID = "AKIAIOSFODNN7EXAMPLE";

    private static final String PEM_BODY =
            "MIIBVAIBADANBgkqhkiG9w0BAQEFAASCAT4wggE6AgEAAkEAnotArealKeyExample\n"
                    + "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ+/\n"
                    + "ThisIsNotARealPrivateKeyItIsAFixtureForCometGUIPhase04Tests012==";

    /** All thirteen, in the order {@code SeededSecretCorpusTest} lists them. */
    private static final List<String> CORPUS =
            List.of(
                    "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
                    "ghp_S3cr3tT0k3nExampleValue0123456789ab",
                    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIn0"
                            + ".dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXk",
                    "hunter2-not-a-real-password",
                    "Tr0ub4dor-26-3",
                    "ll_live_9f8e7d6c5b4a39281706",
                    "correct-horse-battery-staple",
                    "swordfish-42",
                    "tok_live_abcdef0123456789",
                    "AKIAIOSFODNN7EXAMPLE",
                    "MIIBVAIBADANBgkqhkiG9w0BAQEFAASCAT4wggE6AgEAAkEAnotArealKeyExample",
                    "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ+/",
                    "ThisIsNotARealPrivateKeyItIsAFixtureForCometGUIPhase04Tests012==");

    @TempDir private Path directory;

    @Test
    @DisplayName("not one of the thirteen seeded secrets is anywhere in the written log file")
    void notOneSecretReachesTheDisk() throws IOException {
        Path log = writeTheHostileRun();

        String onDisk = Files.readString(log, UTF_8);

        List<String> leaks = new ArrayList<>();
        for (int secret = 0; secret < CORPUS.size(); secret++) {
            int at = onDisk.indexOf(CORPUS.get(secret));
            if (at >= 0) {
                // The secret itself is never named, for the reason the shared rule set exists.
                leaks.add(
                        "corpus secret #"
                                + secret
                                + " (length "
                                + CORPUS.get(secret).length()
                                + ") survived at byte "
                                + at);
            }
        }

        assertAll(
                () ->
                        assertTrue(
                                onDisk.contains("[REDACTED]"),
                                "the log carries no marker at all, so nothing was redacted"),
                () ->
                        assertTrue(
                                leaks.isEmpty(), "the seeded corpus leaked into the log: " + leaks),
                () ->
                        assertTrue(
                                onDisk.contains("/data/HeLa_1ug_rep1.mzML"),
                                "an ordinary path was destroyed, which is over-redaction"),
                () ->
                        assertTrue(
                                onDisk.contains("limelight.example.org"),
                                "the upload target was destroyed, which is over-redaction"));
    }

    @Test
    @DisplayName("no secret survives into the events read back out of the log either")
    void notOneSecretSurvivesRecovery() throws IOException {
        Path log = writeTheHostileRun();

        RecoveredEventLog recovered = ProvenanceEventLogReader.recover(log);

        StringBuilder everything = new StringBuilder();
        for (ProvenanceEvent event : recovered.events()) {
            for (Map.Entry<String, String> entry : event.payload().entrySet()) {
                everything.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
            }
        }
        String payloads = everything.toString();

        List<String> leaks = new ArrayList<>();
        for (int secret = 0; secret < CORPUS.size(); secret++) {
            if (payloads.contains(CORPUS.get(secret))) {
                leaks.add("corpus secret #" + secret + " survived recovery");
            }
        }

        assertAll(
                () -> assertTrue(recovered.intact(), "the hostile run did not produce a clean log"),
                () -> assertEquals(10, recovered.events().size()),
                () -> assertTrue(leaks.isEmpty(), "the seeded corpus leaked: " + leaks));
    }

    @Test
    @DisplayName("every short carrier's line is exactly the text written here")
    void shortCarrierLinesArePinned() throws IOException {
        Path log = directory.resolve("short.log");

        try (ProvenanceEventLog events = openAt(log)) {
            for (String carrier : shortCarriers().values()) {
                events.append(ProvenanceEventType.WARNING_RAISED, Map.of("message", carrier));
            }
        }

        assertEquals(
                line(1, "09:15:00", "warning.raised", "{\"message\":\"auth=[REDACTED]\"}")
                        + line(
                                2,
                                "09:15:01",
                                "warning.raised",
                                "{\"message\":\"Bearer" + " [REDACTED]\"}")
                        + line(
                                3,
                                "09:15:02",
                                "warning.raised",
                                "{\"message\":\"ftp://u:[REDACTED]@h/\"}")
                        + line(4, "09:15:03", "warning.raised", "{\"message\":\"[REDACTED]\"}")
                        + line(5, "09:15:04", "warning.raised", "{\"message\":\"pw=[REDACTED]\"}")
                        + line(6, "09:15:05", "warning.raised", "{\"message\":\"[REDACTED]\"}"),
                Files.readString(log, UTF_8));
    }

    @Test
    @DisplayName("no short carrier is long enough for a size-conditioned defect to hide behind")
    void everyShortCarrierIsActuallyShort() {
        for (Map.Entry<String, String> carrier : shortCarriers().entrySet()) {
            assertTrue(
                    carrier.getValue().length() < 32,
                    "the \""
                            + carrier.getKey()
                            + "\" carrier has grown to "
                            + carrier.getValue().length()
                            + " characters; see blind spot (2) in this class's documentation");
        }
        assertEquals(12, SWORDFISH.length());
    }

    @Test
    @DisplayName("a payload key that names a secret loses its whole value, whatever it looks like")
    void aSecretNamedKeyLosesItsWholeValue() throws IOException {
        Path log = directory.resolve("named.log");

        try (ProvenanceEventLog events = openAt(log)) {
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("upload.token", "an ordinary looking phrase");
            payload.put("comet.params", "/data/comet.params");
            events.append(ProvenanceEventType.TOOL_INVOKED, payload);
        }

        assertEquals(
                line(
                        1,
                        "09:15:00",
                        "tool.invoked",
                        "{\"comet.params\":\"/data/comet.params\","
                                + "\"upload.token\":\"[REDACTED]\"}"),
                Files.readString(log, UTF_8));
    }

    @Test
    @DisplayName("a PEM key in a payload is redacted whole, newlines and delimiters included")
    void aPemKeyIsRedactedBeforeItIsEscaped() throws IOException {
        Path log = directory.resolve("pem.log");

        try (ProvenanceEventLog events = openAt(log)) {
            events.append(
                    ProvenanceEventType.WARNING_RAISED,
                    Map.of(
                            "message",
                            "upload: key material follows\n"
                                    + "-----BEGIN RSA PRIVATE KEY-----\n"
                                    + PEM_BODY
                                    + "\n-----END RSA PRIVATE KEY-----\n"
                                    + "upload: done"));
        }

        assertEquals(
                line(
                        1,
                        "09:15:00",
                        "warning.raised",
                        "{\"message\":\"upload: key material follows\\n[REDACTED]\\nupload:"
                                + " done\"}"),
                Files.readString(log, UTF_8));
    }

    // -------------------------------------------------------------------------------------
    // The reader's error path, which is the one place file content is quoted back.
    // -------------------------------------------------------------------------------------

    @Test
    @DisplayName("a secret in key position does not reach the defect the reader reports")
    void aSecretInKeyPositionIsRedactedOutOfTheDefect() throws IOException {
        // A key this application could never have written -- it is not lower-case and dotted --
        // carrying a corpus secret in the shape a client library's debug output uses.
        Path log = doctoredLogWithKey("password=" + PASSWORD);

        RecoveredEventLog recovered = ProvenanceEventLogReader.recover(log);

        assertAll(
                () -> assertEquals(1, recovered.defects().size()),
                () ->
                        assertEquals(
                                "a record this application would not have written: a payload key"
                                        + " must be lower-case ASCII, either one segment or dotted"
                                        + " segments, matching"
                                        + " (?:[a-z0-9]+(\\.[a-z0-9-]+)+)|[a-z0-9]+, but was:"
                                        + " \"password=[REDACTED]",
                                recovered.defects().get(0).detail()),
                () ->
                        assertFalse(
                                recovered.defects().get(0).detail().contains(PASSWORD),
                                "the seeded password reached a defect detail"));
    }

    @Test
    @DisplayName("an ordinary bad key is still named, so the diagnostic survives the redaction")
    void anOrdinaryBadKeyIsStillNamed() throws IOException {
        Path log = doctoredLogWithKey("runId");

        RecoveredEventLog recovered = ProvenanceEventLogReader.recover(log);

        // The half that stops the fix from degenerating into redacting everything: a phase author
        // who wrote runId must still be told which key was wrong.
        assertEquals(
                "a record this application would not have written: a payload key must be"
                        + " lower-case ASCII, either one segment or dotted segments, matching"
                        + " (?:[a-z0-9]+(\\.[a-z0-9-]+)+)|[a-z0-9]+, but was: \"runId\"",
                recovered.defects().get(0).detail());
    }

    @Test
    @DisplayName("a bare secret in key position needs the registry, exactly as the corpus records")
    void aBareSecretInKeyPositionNeedsTheRegistry() throws IOException {
        Path log = doctoredLogWithKey(SWORDFISH);

        RecoveredEventLog patternsOnly = ProvenanceEventLogReader.recover(log);
        RecoveredEventLog loaded =
                ProvenanceEventLogReader.recover(
                        log, SecretRedactor.with(SecretRegistry.copyOf(CORPUS)));

        assertAll(
                // Stated rather than hidden: a bare token with no surrounding syntax is exactly
                // the carrier SeededSecretCorpusTest lists as one only the registry clears, so the
                // pattern rules alone leave it standing.  That is why recover takes a redactor.
                () ->
                        assertTrue(
                                patternsOnly.defects().get(0).detail().contains(SWORDFISH),
                                "the pattern rules now clear a bare token, which would make the"
                                        + " registry overload look unnecessary"),
                () ->
                        assertEquals(
                                "a record this application would not have written: a payload key"
                                        + " must be lower-case ASCII, either one segment or dotted"
                                        + " segments, matching"
                                        + " (?:[a-z0-9]+(\\.[a-z0-9-]+)+)|[a-z0-9]+, but was:"
                                        + " \"[REDACTED]\"",
                                loaded.defects().get(0).detail()),
                () ->
                        assertFalse(
                                loaded.defects().get(0).detail().contains(SWORDFISH),
                                "the seeded token reached a defect detail"));
    }

    @Test
    @DisplayName("a megabyte of junk in key position does not become a megabyte of defect")
    void aVeryLongKeyIsBounded() throws IOException {
        Path log = doctoredLogWithKey("Y".repeat(1000));

        RecoveredEventLog recovered = ProvenanceEventLogReader.recover(log);
        String detail = recovered.defects().get(0).detail();

        assertAll(
                () -> assertEquals(512, detail.length()),
                () -> assertTrue(detail.endsWith(" [truncated]")),
                () ->
                        assertTrue(
                                detail.startsWith(
                                        "a record this application would not have written: a"
                                                + " payload key must be lower-case ASCII,"),
                                "the bound ate the diagnostic instead of the junk"));
    }

    @Test
    @DisplayName("the bound is inclusive: 512 characters survive whole, 513 are cut")
    void theBoundIsInclusive() throws IOException {
        // The fixed part of this message is 189 characters (counted with python3 over the same
        // literals), so a key of 323 Y's makes a detail of exactly 512 and one of 324 makes 513.
        // Both sides of the boundary are asserted, because an off-by-one here is invisible in
        // ordinary use: it would only ever cut twelve characters off one message in a thousand.
        Path exactly = doctoredLogWithKey("Y".repeat(323));
        Path oneMore = doctoredLogWithKey("Y".repeat(324));

        String atTheBound = ProvenanceEventLogReader.recover(exactly).defects().get(0).detail();
        String pastIt = ProvenanceEventLogReader.recover(oneMore).defects().get(0).detail();

        assertAll(
                () -> assertEquals(512, atTheBound.length()),
                () -> assertTrue(atTheBound.endsWith("Y\""), "a detail of exactly 512 was cut"),
                () ->
                        assertFalse(
                                atTheBound.endsWith(" [truncated]"),
                                "a detail of exactly 512 was cut"),
                () -> assertEquals(512, pastIt.length()),
                () -> assertTrue(pastIt.endsWith(" [truncated]"), "a detail of 513 was not cut"));
    }

    @Test
    @DisplayName("a secret straddling the cut is redacted before the cut, never after it")
    void redactionHappensBeforeTruncation() throws IOException {
        // The arithmetic, counted with python3 over the same literals: 188 characters of message
        // come before the quoted key, so 307 filler characters put the secret at offset 495, and
        // the cut falls at 500 -- five characters into it.  The trailing filler keeps the whole
        // detail (558 characters) over the bound so that it really is truncated.
        //
        // This is the one arrangement that tells the two orderings apart.  Cleaning first replaces
        // the whole token and the cut lands in the marker; cutting first leaves "sword" behind,
        // and a five-character fragment matches neither a pattern rule nor a registered literal --
        // the blind spot SeededSecretCorpusTest records, reached here through a code path rather
        // than through a carrier.
        Path log = doctoredLogWithKey("Y".repeat(307) + SWORDFISH + "Y".repeat(50));

        String detail =
                ProvenanceEventLogReader.recover(
                                log, SecretRedactor.with(SecretRegistry.copyOf(CORPUS)))
                        .defects()
                        .get(0)
                        .detail();

        assertAll(
                () -> assertEquals(512, detail.length()),
                () -> assertTrue(detail.endsWith(" [truncated]")),
                () ->
                        assertFalse(
                                detail.contains(SWORDFISH),
                                "the seeded token survived the defect whole"),
                () ->
                        assertFalse(
                                detail.contains("sword"),
                                "a fragment of the seeded token survived the cut, so the detail"
                                        + " was truncated before it was redacted"));
    }

    @Test
    @DisplayName("the bound never cuts a character in half")
    void truncationNeverSplitsACharacter() throws IOException {
        // Each of these is one code point and two Java chars, so between the two paddings the cut
        // lands once on a low surrogate and once on a high one.  A lone surrogate is not a
        // character: it becomes a question mark the moment the detail is encoded for a log or a
        // UI, which is what the round trip below detects.
        String astral = new String(Character.toChars(0x1F600)).repeat(400);
        for (String padding : List.of("", "a")) {
            Path log = doctoredLogWithKey(padding + astral);

            String detail = ProvenanceEventLogReader.recover(log).defects().get(0).detail();

            assertTrue(detail.length() <= 512, "the detail was not bounded");
            assertEquals(
                    detail,
                    new String(detail.getBytes(UTF_8), UTF_8),
                    "the cut fell inside a character, so the detail no longer survives encoding");
        }
    }

    /**
     * Writes a one-line log whose payload carries the given key, which no writer would produce.
     *
     * @param key the raw key text, placed into the line unescaped
     * @return the file
     * @throws IOException if it cannot be written
     */
    private Path doctoredLogWithKey(String key) throws IOException {
        Path log = directory.resolve("doctored-" + Math.abs(key.hashCode()) + ".log");
        Files.write(
                log,
                ("{\"seq\":1,\"time\":\"2026-08-31T09:15:00.000Z\",\"type\":\"run.started\","
                                + "\"payload\":{\""
                                + key
                                + "\":\"v\"}}\n")
                        .getBytes(UTF_8));
        return log;
    }

    // -------------------------------------------------------------------------------------
    // Fixtures.
    // -------------------------------------------------------------------------------------

    /**
     * One deliberately small carrier per rule family, name to raw input.
     *
     * <p>The longest is 23 characters and the shortest is 12, so a redactor that short-circuited on
     * small inputs could not hide behind them. {@code pw=} is here on purpose: {@code pw} is not
     * one of the redactor's secret-name keywords, so only the registry clears it.
     *
     * @return each short carrier's name and the text to put in a payload
     */
    private static Map<String, String> shortCarriers() {
        Map<String, String> carriers = new LinkedHashMap<>();
        carriers.put("short assignment", "auth=" + SWORDFISH);
        carriers.put("short bearer", "Bearer " + SWORDFISH);
        carriers.put("short credential URL", "ftp://u:" + SWORDFISH + "@h/");
        carriers.put("short token shape", AWS_ACCESS_KEY_ID);
        carriers.put("short unnamed assignment", "pw=" + SWORDFISH);
        carriers.put("short bare value", SWORDFISH);
        return carriers;
    }

    /** A run that carries every corpus secret through the payloads of ten events. */
    private Path writeTheHostileRun() throws IOException {
        Path log = directory.resolve("hostile.log");
        try (ProvenanceEventLog events = openAt(log)) {
            events.append(ProvenanceEventType.RUN_STARTED, Map.of("run.id", "R-1"));

            Map<String, String> invocation = new LinkedHashMap<>();
            invocation.put("argv.0", "/opt/limelight/bin/upload");
            invocation.put("argv.1", "--password=" + PASSWORD);
            invocation.put("argv.2", AWS_SECRET);
            invocation.put("argv.3", "--input");
            invocation.put("argv.4", "/data/HeLa_1ug_rep1.mzML");
            invocation.put(
                    "endpoint",
                    "https://limelight-user:" + URL_PASSWORD + "@limelight.example.org/api/upload");
            invocation.put("upload.api.key", LIMELIGHT_KEY);
            events.append(ProvenanceEventType.TOOL_INVOKED, invocation);

            events.append(
                    ProvenanceEventType.WARNING_RAISED,
                    Map.of("message", "GET /api/upload -> Authorization: Bearer " + JWT));
            events.append(
                    ProvenanceEventType.WARNING_RAISED,
                    Map.of("message", "response body {\"token\":\"" + LIVE_TOKEN + "\"}"));
            events.append(
                    ProvenanceEventType.WARNING_RAISED,
                    Map.of("message", "github token " + GITHUB_TOKEN + " accepted"));
            events.append(
                    ProvenanceEventType.WARNING_RAISED,
                    Map.of("message", "percolator passphrase " + PASSPHRASE));
            events.append(
                    ProvenanceEventType.WARNING_RAISED,
                    Map.of(
                            "message",
                            "-----BEGIN RSA PRIVATE KEY-----\n"
                                    + PEM_BODY
                                    + "\n-----END RSA PRIVATE KEY-----"));
            events.append(
                    ProvenanceEventType.FILE_HASHED,
                    Map.of("path", "/data/HeLa_1ug_rep1.mzML", "aws.key.id", AWS_ACCESS_KEY_ID));
            events.append(ProvenanceEventType.STAGE_FINISHED, Map.of("stage", "upload"));
            events.append(
                    ProvenanceEventType.RUN_FINISHED,
                    Map.of(ProvenanceEvent.STATUS_KEY, "completed"));
        }
        return log;
    }

    /** A log whose redactor holds every corpus value, which is how a real run is configured. */
    private ProvenanceEventLog openAt(Path log) throws IOException {
        return ProvenanceEventLog.openAppend(
                log,
                SecretRedactor.with(SecretRegistry.copyOf(CORPUS)),
                new StepClock(Instant.parse("2026-08-31T09:15:00Z"), Duration.ofSeconds(1)));
    }

    /**
     * Assembles one expected log line from its four hand-typed parts.
     *
     * @param sequence the sequence number
     * @param time the time of day, in the fixed-width form
     * @param type the event type's wire name
     * @param payload the payload object, braces included
     * @return the line, with its terminating newline
     */
    private static String line(int sequence, String time, String type, String payload) {
        return "{\"seq\":"
                + sequence
                + ",\"time\":\"2026-08-31T"
                + time
                + ".000Z\",\"type\":\""
                + type
                + "\",\"payload\":"
                + payload
                + "}\n";
    }

    /** A clock that advances by a fixed step every time it is read. */
    private static final class StepClock extends Clock {

        private final Duration step;

        private Instant next;

        StepClock(Instant first, Duration step) {
            this.next = first;
            this.step = step;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException("withZone");
        }

        @Override
        public Instant instant() {
            Instant now = next;
            next = next.plus(step);
            return now;
        }
    }
}
