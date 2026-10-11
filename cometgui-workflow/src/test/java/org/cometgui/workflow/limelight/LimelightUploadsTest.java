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

package org.cometgui.workflow.limelight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.secrets.Credential;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.domain.secrets.SessionSecrets;
import org.cometgui.provenance.events.ProvenanceEvent;
import org.cometgui.provenance.events.ProvenanceEventLog;
import org.cometgui.provenance.events.ProvenanceEventType;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.tools.limelight.LimelightUploadClient;
import org.cometgui.tools.limelight.LimelightUploadOutcome.Failed;
import org.cometgui.tools.limelight.LimelightUploadOutcome.Refused;
import org.cometgui.tools.limelight.LimelightUploadOutcome.Submitted;
import org.cometgui.tools.limelight.LimelightUploadStep;
import org.cometgui.tools.limelight.fake.FakeLimelightServer;
import org.cometgui.tools.limelight.fake.LoopbackGuard;
import org.cometgui.workflow.limelight.LimelightFixtures.Converters;
import org.cometgui.workflow.limelight.LimelightFixtures.FinishedRun;
import org.cometgui.workflow.limelight.LimelightFixtures.ScriptedConverter;
import org.cometgui.workflow.limelight.LimelightFixtures.Writes;
import org.cometgui.workflow.limelight.LimelightUploadRefusedException.Reason;
import org.cometgui.workflow.storage.LimelightSettings;
import org.cometgui.workflow.testing.LoopbackHttp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link LimelightUploads} against the one fake Limelight server, over a run converted through the
 * one conversion path (a scripted converter writing the real known-good Limelight XML): the
 * destination first, the upload only on its confirmation, and every value in the record the fake's
 * own or this test's own.
 */
@EnabledOnOs(value = OS.LINUX, disabledReason = "the run fixtures' paths and modes are POSIX")
class LimelightUploadsTest {

    /** The seeded submit key, 36 characters. */
    static final String KEY = "LLworkflow-seeded-key-0123456789abcd";

    static final int PROJECT = 7;

    private static final String AUTH =
            "/d/rws/for-page/project-upload-data-upload-submit-pgm-auth-test";
    private static final String INIT =
            "/d/rws/for-page/project-upload-data-upload-initialize-from-submit-pgm";
    private static final String FILE =
            "/d/rws/for-page/project-upload-data-upload-file-from-submit-pgm";
    private static final String SUBMIT =
            "/d/rws/for-page/project-upload-data-upload-submit-from-submit-pgm";

    @TempDir private Path root;

    private final SessionSecrets secrets = new SessionSecrets();

    @AfterEach
    void nothingExecutingAndNothingLeft() {
        LimelightFixtures.EXECUTING.clear();
        assertEquals(List.of(), LoopbackGuard.takeCaught(), "the sinkhole caught a request");
    }

    /** A finished run converted once, through the one conversion path. */
    record Converted(FinishedRun run, Path file) {

        RunLayout layout() {
            return run.layout();
        }
    }

    static Converted converted(Path root) throws IOException {
        FinishedRun run = LimelightFixtures.finishedRun(root, LimelightFixtures.TARGETS, false);
        Path jar = LimelightFixtures.converterJar(root.resolve("cache/limelight-converter"));
        Converters tools =
                new Converters(LimelightFixtures.installed(LimelightFixtures.VERSION, jar));
        ScriptedConverter converter =
                new ScriptedConverter(Writes.VALID, LimelightFixtures.VERSION, run.layout());
        Path file =
                LimelightFixtures.conversions(tools, converter)
                        .convert(
                                run.layout(),
                                run.identity(),
                                LimelightSettings.DEFAULTS,
                                Optional.empty())
                        .file();
        return new Converted(run, file);
    }

    static LimelightUploads uploads(SessionSecrets secrets) {
        return new LimelightUploads(
                new LimelightUploadClient(
                        Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(120)),
                new StreamingHashService(),
                secrets,
                LimelightFixtures.CLOCK,
                () -> Set.copyOf(LimelightFixtures.EXECUTING));
    }

    private LimelightDestination destination(Converted world, FakeLimelightServer fake)
            throws IOException {
        return uploads(secrets)
                .destination(
                        world.layout(),
                        world.run().identity(),
                        world.file(),
                        fake.baseUrl() + "/",
                        " " + PROJECT);
    }

    private static List<ProvenanceEvent> events(Converted world, ProvenanceEventType type)
            throws IOException {
        return world.run().events().stream().filter(event -> event.type() == type).toList();
    }

    @Test
    @DisplayName("upload: the fake received the file's own bytes; the record holds its ids")
    void theConvertedFileIsUploadedAndRecorded() throws IOException {
        Converted world = converted(root);
        String sha256 = LimelightFixtures.sha256(world.file());
        String md5 = LimelightFixtures.md5(world.file());
        long size = Files.size(world.file());
        List<String> live = Collections.synchronizedList(new ArrayList<>());
        try (FakeLimelightServer fake = FakeLimelightServer.start(KEY, PROJECT)) {
            LimelightDestination destination = destination(world, fake);

            LimelightUploadAttempt attempt =
                    uploads(secrets).upload(destination.confirm(), secrets.admit(KEY), live::add);

            FakeLimelightServer.Upload received = fake.uploads().get(0);
            assertEquals(sha256, received.receivedSha256());
            assertEquals(size, received.receivedBytes());
            assertEquals("VALID", received.verdict());
            assertTrue(received.submitted());
            assertEquals(List.of(AUTH, INIT, FILE, SUBMIT), fake.calls());
            Submitted submitted = assertInstanceOf(Submitted.class, attempt.outcome());
            assertTrue(attempt.submitted());
            assertEquals(received.trackingId(), submitted.uploadKey());

            Path log = world.layout().logsDirectory().resolve("limelight-upload.log");
            assertEquals(log, attempt.log());
            ProvenanceEvent uploading =
                    events(world, ProvenanceEventType.LIMELIGHT_UPLOADING).get(0);
            ProvenanceEvent uploaded = events(world, ProvenanceEventType.LIMELIGHT_UPLOADED).get(0);
            Map<String, String> before = new LinkedHashMap<>();
            before.put("run.id", "run-0001");
            before.put("limelight.server", fake.baseUrl());
            before.put("limelight.project-id", "7");
            before.put("limelight.project-page", fake.baseUrl() + "/d/pg/project/7");
            before.put("upload.file.path", world.file().toString());
            before.put("upload.file.size", Long.toString(size));
            before.put("upload.file.md5", md5);
            before.put("upload.file.sha256", sha256);
            assertEquals(before, uploading.payload());
            Map<String, String> after = new LinkedHashMap<>(before);
            after.put("limelight.upload-key", received.trackingId());
            after.put("limelight.importer-sub-dir", received.importerSubDir());
            after.put("limelight.server-program-version", "16");
            after.put("upload.bytes-sent", Long.toString(size));
            after.put("log.path", log.toString());
            after.put("log.md5", LimelightFixtures.md5(log));
            after.put("log.sha256", LimelightFixtures.sha256(log));
            assertEquals(after, uploaded.payload());
            assertEquals("4100", uploaded.payload().get("limelight.upload-key"));
            assertEquals(
                    "import_dir_for_id_0004100",
                    uploaded.payload().get("limelight.importer-sub-dir"));
            assertTrue(uploading.sequence() < uploaded.sequence());
            assertEquals(List.of(), events(world, ProvenanceEventType.LIMELIGHT_UPLOAD_FAILED));

            List<String> lines = logLines(log);
            assertEquals(live, lines, "the watcher did not hear the log as it was written");
            assertEquals(
                    "Upload to Limelight: "
                            + fake.baseUrl()
                            + ", project 7 ("
                            + fake.baseUrl()
                            + "/d/pg/project/7)",
                    lines.get(0));
            assertEquals(
                    "File: " + world.file() + " (" + size + " bytes, SHA-256 " + sha256 + ")",
                    lines.get(1));
            assertTrue(lines.contains("Sending auth-test."), lines.toString());
            assertTrue(lines.contains("The server accepted final-submit."));
            assertTrue(lines.contains("Sent " + size + " of " + size + " bytes (100%)."));
            assertEquals(
                    "Submitted. The server queued the import: uploadKey (tracking id) 4100,"
                            + " importerSubDir import_dir_for_id_0004100, server program version"
                            + " 16.",
                    lines.get(lines.size() - 2));
            assertEquals(List.of(), fake.wireViolations());
        }
    }

    @Test
    @DisplayName("the destination is shown first: it sends nothing and writes nothing")
    void theDestinationSendsNothing() throws IOException {
        Converted world = converted(root);
        int events = world.run().events().size();
        try (FakeLimelightServer fake = FakeLimelightServer.start(KEY, PROJECT)) {
            LimelightDestination destination = destination(world, fake);

            assertEquals(fake.baseUrl(), destination.server().toString());
            assertEquals(PROJECT, destination.projectId());
            assertEquals(URI.create(fake.baseUrl() + "/d/pg/project/7"), destination.projectPage());
            assertEquals(world.file(), destination.file());
            assertEquals(LimelightFixtures.sha256(world.file()), destination.sha256());
            assertEquals(LimelightFixtures.md5(world.file()), destination.md5());
            assertEquals(Files.size(world.file()), destination.size());
            assertEquals("run-0001", destination.runId().value());
            assertEquals(world.layout(), destination.run());
            assertEquals(List.of(), fake.calls());
            assertEquals(events, world.run().events().size());
            assertFalse(
                    Files.exists(world.layout().logsDirectory().resolve("limelight-upload.log")));
            assertEquals(List.of(world.file()), uploads(secrets).convertedFiles(world.layout()));
        }
    }

    @Test
    @DisplayName("the destination is refused: bad server, bad project, executing run")
    void destinationRefusals() throws IOException {
        Converted world = converted(root);
        LimelightUploads uploads = uploads(secrets);

        LimelightUploadRefusedException server =
                assertThrows(
                        LimelightUploadRefusedException.class,
                        () ->
                                uploads.destination(
                                        world.layout(),
                                        world.run().identity(),
                                        world.file(),
                                        "http://limelight.example.org/limelight",
                                        "7"));
        LimelightUploadRefusedException project =
                assertThrows(
                        LimelightUploadRefusedException.class,
                        () ->
                                uploads.destination(
                                        world.layout(),
                                        world.run().identity(),
                                        world.file(),
                                        "https://limelight.example.org/limelight",
                                        "seven"));
        LimelightFixtures.EXECUTING.add(world.run().identity().runId());
        LimelightUploadRefusedException executing =
                assertThrows(
                        LimelightUploadRefusedException.class,
                        () ->
                                uploads.destination(
                                        world.layout(),
                                        world.run().identity(),
                                        world.file(),
                                        "https://limelight.example.org/limelight",
                                        "7"));

        assertEquals(Reason.SERVER, server.reason());
        assertEquals(
                "This address cannot be used as a Limelight server: plain http would send the"
                        + " submit key across the network unencrypted; use https.",
                server.getMessage());
        assertEquals(Reason.PROJECT, project.reason());
        assertEquals(
                "a Limelight project id is a positive whole number, not \"seven\"",
                project.getMessage());
        assertEquals(Reason.RUN_EXECUTING, executing.reason());
        assertEquals(
                "run run-0001 is executing: its results are not final. Upload it when the run has"
                        + " ended.",
                executing.getMessage());
    }

    @Test
    @DisplayName("only the run's own recorded conversion, unchanged, can be a destination")
    void onlyTheRunsOwnUnchangedConversion() throws IOException {
        Converted world = converted(root);
        LimelightUploads uploads = uploads(secrets);
        Path other = Files.copy(world.file(), root.resolve("elsewhere.xml"));

        LimelightUploadRefusedException notOurs =
                assertThrows(
                        LimelightUploadRefusedException.class,
                        () ->
                                uploads.destination(
                                        world.layout(),
                                        world.run().identity(),
                                        other,
                                        "https://limelight.example.org/limelight",
                                        "7"));
        String recorded = LimelightFixtures.sha256(world.file());
        tamper(world.file());
        String now = LimelightFixtures.sha256(world.file());
        LimelightUploadRefusedException changed =
                assertThrows(
                        LimelightUploadRefusedException.class,
                        () ->
                                uploads.destination(
                                        world.layout(),
                                        world.run().identity(),
                                        world.file(),
                                        "https://limelight.example.org/limelight",
                                        "7"));
        Files.delete(world.file());
        LimelightUploadRefusedException gone =
                assertThrows(
                        LimelightUploadRefusedException.class,
                        () ->
                                uploads.destination(
                                        world.layout(),
                                        world.run().identity(),
                                        world.file(),
                                        "https://limelight.example.org/limelight",
                                        "7"));

        assertEquals(Reason.NOT_A_CONVERSION, notOurs.reason());
        assertEquals(
                other
                        + " is not a Limelight XML file this run's conversions produced, so it is"
                        + " not uploaded from run run-0001.",
                notOurs.getMessage());
        assertEquals(Reason.FILE_CHANGED, changed.reason());
        assertEquals(
                world.file()
                        + " has changed since its conversion recorded it (SHA-256 "
                        + now
                        + ", recorded "
                        + recorded
                        + "), so it is not uploaded.",
                changed.getMessage());
        assertEquals(Reason.FILE_CHANGED, gone.reason());
        assertEquals(
                world.file() + " is no longer there, so it is not uploaded.", gone.getMessage());
    }

    @Test
    @DisplayName("a file changed after the destination was shown: refused before any connection")
    void aFileChangedAfterTheDestination() throws IOException {
        Converted world = converted(root);
        try (FakeLimelightServer fake = FakeLimelightServer.start(KEY, PROJECT)) {
            LimelightDestination destination = destination(world, fake);
            int events = world.run().events().size();
            tamper(world.file());

            LimelightUploadRefusedException refused =
                    assertThrows(
                            LimelightUploadRefusedException.class,
                            () ->
                                    uploads(secrets)
                                            .upload(
                                                    destination.confirm(),
                                                    secrets.admit(KEY),
                                                    LimelightUploadWatcher.NONE));

            assertEquals(Reason.FILE_CHANGED, refused.reason());
            assertTrue(refused.getMessage().contains("since the destination was shown"));
            assertEquals(List.of(), fake.calls());
            assertEquals(events, world.run().events().size());
            assertFalse(
                    Files.exists(world.layout().logsDirectory().resolve("limelight-upload.log")));
        }
    }

    @Test
    @DisplayName("a run that started executing: refused before any connection")
    void aRunExecutingAtUploadTime() throws IOException {
        Converted world = converted(root);
        try (FakeLimelightServer fake = FakeLimelightServer.start(KEY, PROJECT)) {
            LimelightDestination destination = destination(world, fake);
            LimelightFixtures.EXECUTING.add(world.run().identity().runId());

            LimelightUploadRefusedException refused =
                    assertThrows(
                            LimelightUploadRefusedException.class,
                            () ->
                                    uploads(secrets)
                                            .upload(
                                                    destination.confirm(),
                                                    secrets.admit(KEY),
                                                    LimelightUploadWatcher.NONE));

            assertEquals(Reason.RUN_EXECUTING, refused.reason());
            assertEquals(List.of(), fake.calls());
        }
    }

    @Test
    @DisplayName("one confirmation, one upload: a second needs a second explicit action")
    void aConfirmationIsUsedOnce() throws IOException {
        Converted world = converted(root);
        try (FakeLimelightServer fake = FakeLimelightServer.start(KEY, PROJECT)) {
            LimelightUploads uploads = uploads(secrets);
            LimelightDestination destination = destination(world, fake);
            LimelightUploadConfirmation confirmation = destination.confirm();
            Credential key = secrets.admit(KEY);
            uploads.upload(confirmation, key, LimelightUploadWatcher.NONE);

            LimelightUploadRefusedException again =
                    assertThrows(
                            LimelightUploadRefusedException.class,
                            () -> uploads.upload(confirmation, key, LimelightUploadWatcher.NONE));
            assertEquals(Reason.CONFIRMATION_USED, again.reason());
            assertEquals(
                    "This confirmation was already used for an upload; each upload needs its own.",
                    again.getMessage());
            assertEquals(4, fake.calls().size());
            assertEquals(destination, confirmation.destination());

            LimelightUploadAttempt second =
                    uploads.upload(destination.confirm(), key, LimelightUploadWatcher.NONE);
            assertEquals(
                    world.layout().logsDirectory().resolve("limelight-upload.1.log"), second.log());
            assertEquals("4101", ((Submitted) second.outcome()).uploadKey());
            assertEquals(2, events(world, ProvenanceEventType.LIMELIGHT_UPLOADED).size());
        }
    }

    @Test
    @DisplayName("a wrong key and a wrong project: refused, the server's words recorded")
    void refusedByTheServer() throws IOException {
        Converted world = converted(root);
        try (FakeLimelightServer fake = FakeLimelightServer.start(KEY, PROJECT)) {
            LimelightUploads uploads = uploads(secrets);
            LimelightUploadAttempt wrongKey =
                    uploads.upload(
                            destination(world, fake).confirm(),
                            secrets.admit("WRong-workflow-key-000000"),
                            LimelightUploadWatcher.NONE);
            LimelightUploadAttempt wrongProject =
                    uploads.upload(
                            uploads.destination(
                                            world.layout(),
                                            world.run().identity(),
                                            world.file(),
                                            fake.baseUrl(),
                                            "8")
                                    .confirm(),
                            secrets.admit(KEY),
                            LimelightUploadWatcher.NONE);

            assertInstanceOf(Refused.class, wrongKey.outcome());
            assertInstanceOf(Refused.class, wrongProject.outcome());
            List<ProvenanceEvent> failed =
                    events(world, ProvenanceEventType.LIMELIGHT_UPLOAD_FAILED);
            assertEquals(2, failed.size());
            Map<String, String> key = failed.get(0).payload();
            assertEquals("auth-test", key.get("upload.step"));
            assertEquals("refused-by-server", key.get("upload.failure"));
            assertEquals("200", key.get("upload.http-status"));
            assertEquals("userSubmitImportProgramKeyNotFound", key.get("limelight.server-flags"));
            assertEquals(
                    "The supplied submit import key (in param '--user-submit-import-key') is"
                            + " invalid.  The first 2 characters are 'WR'.",
                    key.get("limelight.server-message"));
            assertFalse(key.containsKey("limelight.upload-key"));
            Map<String, String> project = failed.get(1).payload();
            assertEquals("8", project.get("limelight.project-id"));
            assertEquals("projectIdNotFound", project.get("limelight.server-flags"));
            assertEquals(
                    "Unable to upload to this project as it is Not Found.",
                    project.get("limelight.server-message"));
            assertEquals(List.of(AUTH, AUTH), fake.calls());
            List<String> lines = logLines(wrongKey.log());
            assertEquals(
                    "Refused by the server at auth-test (HTTP 200). Its flags:"
                            + " userSubmitImportProgramKeyNotFound.",
                    lines.get(lines.size() - 2));
            assertEquals(
                    "The server said: The supplied submit import key (in param"
                            + " '--user-submit-import-key') is invalid.  The first 2 characters"
                            + " are 'WR'.",
                    lines.get(lines.size() - 1));
            assertEquals(List.of(), events(world, ProvenanceEventType.LIMELIGHT_UPLOADED));
        }
    }

    @Test
    @DisplayName("a corrupt file: refused by the fake's schema check, its message recorded")
    void aCorruptFileIsRefusedWithItsMessage() throws IOException {
        Converted world = converted(root);
        Path corrupt = corruptConversion(world);
        try (FakeLimelightServer fake = FakeLimelightServer.start(KEY, PROJECT)) {
            LimelightUploads uploads = uploads(secrets);
            LimelightUploadAttempt attempt =
                    uploads.upload(
                            uploads.destination(
                                            world.layout(),
                                            world.run().identity(),
                                            corrupt,
                                            fake.baseUrl(),
                                            "7")
                                    .confirm(),
                            secrets.admit(KEY),
                            LimelightUploadWatcher.NONE);

            Refused refused = assertInstanceOf(Refused.class, attempt.outcome());
            assertEquals(LimelightUploadStep.UPLOAD_FILE, refused.step());
            Map<String, String> failed =
                    events(world, ProvenanceEventType.LIMELIGHT_UPLOAD_FAILED).get(0).payload();
            assertEquals("upload-file", failed.get("upload.step"));
            assertEquals("400", failed.get("upload.http-status"));
            assertEquals("limelightXMLFileFailsInitialParse", failed.get("limelight.server-flags"));
            assertEquals(
                    "INVALID: ERROR line 2 col 18: cvc-complex-type.4: Attribute 'fasta_filename'"
                            + " must appear on element 'limelight_input'.",
                    failed.get("limelight.server-message"));
            assertEquals(List.of(AUTH, INIT, FILE), fake.calls(), "a refused file was submitted");
            assertFalse(fake.uploads().get(0).submitted());
        }
    }

    @Test
    @DisplayName("a connection cut mid-file: recorded as no answer, at upload-file")
    void aCutConnectionIsRecorded() throws IOException {
        Converted world = converted(root);
        try (FakeLimelightServer fake = FakeLimelightServer.start(KEY, PROJECT)) {
            fake.cutUploadAfter(65_536);
            LimelightUploadAttempt attempt =
                    uploads(secrets)
                            .upload(
                                    destination(world, fake).confirm(),
                                    secrets.admit(KEY),
                                    LimelightUploadWatcher.NONE);

            assertInstanceOf(Failed.class, attempt.outcome());
            Map<String, String> failed =
                    events(world, ProvenanceEventType.LIMELIGHT_UPLOAD_FAILED).get(0).payload();
            assertEquals("upload-file", failed.get("upload.step"));
            assertEquals("no-answer", failed.get("upload.failure"));
            assertTrue(
                    failed.get("message").startsWith("the connection failed: "), failed.toString());
            assertEquals(List.of(AUTH, INIT, FILE), fake.calls());
            List<String> lines = logLines(attempt.log());
            assertTrue(
                    lines.get(lines.size() - 1)
                            .startsWith(
                                    "Failed at upload-file, with no answer from the server."
                                            + " Reason: the connection failed: "),
                    lines.toString());
        }
    }

    @Test
    @DisplayName("a server message that quotes the key: [REDACTED] in the log, events and outcome")
    void theServersWordsAreRedacted() throws IOException {
        Converted world = converted(root);
        String answer =
                "<submitImport_AuthTest_Response_PgmXML statusSuccess=\"false\""
                        + " userSubmitImportProgramKeyNotFound=\"true\""
                        + " statusFailErrorMessage=\"no such key: "
                        + KEY
                        + "\"/>";
        try (LoopbackHttp server =
                LoopbackHttp.serve(0, request -> new LoopbackHttp.Response(200, answer))) {
            LimelightUploads uploads = uploads(secrets);
            List<String> live = Collections.synchronizedList(new ArrayList<>());
            LimelightUploadAttempt attempt =
                    uploads.upload(
                            uploads.destination(
                                            world.layout(),
                                            world.run().identity(),
                                            world.file(),
                                            "http://127.0.0.1:" + server.port() + "/limelight",
                                            "7")
                                    .confirm(),
                            new SessionSecrets().admit(KEY),
                            live::add);

            Refused refused = assertInstanceOf(Refused.class, attempt.outcome());
            assertEquals(Optional.of("no such key: [REDACTED]"), refused.message());
            assertEquals(
                    "no such key: [REDACTED]",
                    events(world, ProvenanceEventType.LIMELIGHT_UPLOAD_FAILED)
                            .get(0)
                            .payload()
                            .get("limelight.server-message"));
            assertTrue(live.contains("The server said: no such key: [REDACTED]"), live.toString());
            assertFalse(Files.readString(attempt.log(), StandardCharsets.UTF_8).contains(KEY));
            assertFalse(
                    Files.readString(world.layout().eventLogFile(), StandardCharsets.UTF_8)
                            .contains(KEY));
        }
    }

    @Test
    @DisplayName("upload logs are numbered and never overwritten")
    void logsAreNumbered() throws IOException {
        RunLayout layout = new RunLayout(root.resolve("run"));
        assertEquals(
                layout.logsDirectory().resolve("limelight-upload.log"),
                LimelightUploads.nextLog(layout));
        Files.writeString(layout.logsDirectory().resolve("limelight-upload.log"), "x");
        Files.writeString(layout.logsDirectory().resolve("limelight-upload.1.log"), "x");
        assertEquals(
                layout.logsDirectory().resolve("limelight-upload.2.log"),
                LimelightUploads.nextLog(layout));
    }

    @Test
    @DisplayName("a run with no event log has no conversions to upload")
    void noEventLog() throws IOException {
        assertEquals(
                List.of(), uploads(secrets).convertedFiles(new RunLayout(root.resolve("empty"))));
    }

    // ------------------------------------------------------------------------- helpers --

    static List<String> logLines(Path log) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String line : Files.readAllLines(log, StandardCharsets.UTF_8)) {
            lines.add(line.substring(line.indexOf(' ') + 1));
        }
        return lines;
    }

    /** Changes one byte of a read-only conversion output. */
    static void tamper(Path file) throws IOException {
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));
        byte[] bytes = Files.readAllBytes(file);
        bytes[bytes.length - 2] = (byte) ' ';
        Files.write(file, bytes);
    }

    /**
     * A corrupt Limelight XML in the run's outputs, recorded as a conversion: a file the server's
     * schema check refuses, though its conversion record matches it. (No real conversion can leave
     * one -- the conversion judges its output -- so the record is written here, through the event
     * log's own writer, to exercise the server's refusal end to end.)
     */
    static Path corruptConversion(Converted world) throws IOException {
        Path source =
                LimelightFixtures.pinned(
                        "scratch/scientific-path/corrupt/missing-attribute.xml",
                        "56c2f1a1c10dbfc1d75069c459343befccde149a9095755a8b34a690330d746f");
        Path corrupt =
                Files.copy(
                        source, world.layout().limelightOutputDirectory().resolve("corrupt.xml"));
        try (ProvenanceEventLog log =
                ProvenanceEventLog.openAppend(
                        world.layout().eventLogFile(),
                        SecretRedactor.patternsOnly(),
                        LimelightFixtures.CLOCK)) {
            log.append(
                    ProvenanceEventType.LIMELIGHT_CONVERTED,
                    Map.of(
                            LimelightConversions.OUTPUT_PATH, corrupt.toString(),
                            LimelightConversions.OUTPUT_SHA256, LimelightFixtures.sha256(corrupt)));
        }
        return corrupt;
    }
}
