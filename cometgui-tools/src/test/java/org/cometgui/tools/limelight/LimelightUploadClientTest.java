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

package org.cometgui.tools.limelight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.cometgui.domain.secrets.Credential;
import org.cometgui.domain.secrets.SessionSecrets;
import org.cometgui.tools.limelight.LimelightUploadOutcome.Failed;
import org.cometgui.tools.limelight.LimelightUploadOutcome.Refused;
import org.cometgui.tools.limelight.LimelightUploadOutcome.Submitted;
import org.cometgui.tools.limelight.fake.FakeLimelightServer;
import org.cometgui.tools.limelight.fake.LoopbackGuard;
import org.cometgui.tools.testing.LoopbackHttp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link LimelightUploadClient} against the one fake Limelight server: every value below -- the
 * tracking id, the importer directory, the SHA-256 of what arrived, the server's flags and messages
 * -- is the fake's own, compared with a value typed here or computed by the test, never with what
 * the client says about itself.
 */
class LimelightUploadClientTest {

    /** The seeded submit key, 34 characters. */
    static final String KEY = "LLcometgui-seeded-key-0123456789ab";

    /** The project the fake holds. */
    static final int PROJECT = 7;

    /** Phase 00's real Limelight XML's size, by {@code wc -c}. */
    static final long REAL_SIZE = 12_797_113L;

    private static final String AUTH =
            "/d/rws/for-page/project-upload-data-upload-submit-pgm-auth-test";
    private static final String INIT =
            "/d/rws/for-page/project-upload-data-upload-initialize-from-submit-pgm";
    private static final String FILE =
            "/d/rws/for-page/project-upload-data-upload-file-from-submit-pgm";
    private static final String SUBMIT =
            "/d/rws/for-page/project-upload-data-upload-submit-from-submit-pgm";

    private final SessionSecrets secrets = new SessionSecrets();

    @AfterEach
    void nothingLeftTheMachine() {
        assertEquals(List.of(), LoopbackGuard.takeCaught(), "the sinkhole caught a request");
    }

    static Path realFile() throws IOException {
        return LimelightFixtures.real(
                LimelightFixtures.LIMELIGHT_XML, LimelightFixtures.LIMELIGHT_XML_SHA256);
    }

    private LimelightUploadRequest request(
            FakeLimelightServer fake, int project, Path file, String sha256) {
        return new LimelightUploadRequest(
                LimelightServer.parse(fake.baseUrl()), project, file, sha256);
    }

    private static LimelightUploadClient client() {
        return new LimelightUploadClient(
                Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(120));
    }

    @Test
    @DisplayName("the real file is accepted; the ids and the bytes are the fake's own")
    void theRealFileIsSubmitted() throws IOException {
        Path file = realFile();
        Recording listener = new Recording();
        try (FakeLimelightServer fake = FakeLimelightServer.start(KEY, PROJECT)) {
            LimelightUploadOutcome outcome =
                    client().upload(
                                    request(
                                            fake,
                                            PROJECT,
                                            file,
                                            LimelightFixtures.LIMELIGHT_XML_SHA256),
                                    secrets.admit(KEY),
                                    listener);

            Submitted submitted = assertInstanceOf(Submitted.class, outcome);
            assertEquals("4100", submitted.uploadKey());
            assertEquals("import_dir_for_id_0004100", submitted.importerSubDir());
            assertEquals(URI.create(fake.baseUrl() + "/d/pg/project/7"), submitted.projectPage());
            assertEquals(16, submitted.serverProgramVersion());
            assertEquals(REAL_SIZE, submitted.bytesSent());
            assertEquals(LimelightUploadStep.FINAL_SUBMIT, submitted.step());
            FakeLimelightServer.Upload received = fake.uploads().get(0);
            assertEquals(LimelightFixtures.LIMELIGHT_XML_SHA256, received.receivedSha256());
            assertEquals(REAL_SIZE, received.receivedBytes());
            assertEquals("limelight.xml", received.fileName());
            assertEquals("VALID", received.verdict());
            assertTrue(received.submitted());
            assertEquals(List.of(AUTH, INIT, FILE, SUBMIT), fake.calls());
            assertEquals(List.of(), fake.wireViolations());
            assertEquals(
                    List.of(
                            "start auth-test",
                            "answer auth-test true",
                            "start initialize",
                            "answer initialize true",
                            "start upload-file",
                            "answer upload-file true",
                            "start final-submit",
                            "answer final-submit true"),
                    listener.steps());
            assertEquals(REAL_SIZE, listener.lastSent());
            assertTrue(listener.monotonic(), "byte progress went backwards");
            assertTrue(listener.reports() > 1, "byte progress was reported once, at the end");
        }
    }

    @Test
    @DisplayName("the key arrives only where the protocol puts it, and no local path is sent")
    void theKeyGoesOnlyWhereItMust() throws IOException {
        Path file = realFile();
        try (FakeLimelightServer fake = FakeLimelightServer.start(KEY, PROJECT)) {
            assertInstanceOf(
                    Submitted.class,
                    client().upload(
                                    request(
                                            fake,
                                            PROJECT,
                                            file,
                                            LimelightFixtures.LIMELIGHT_XML_SHA256),
                                    secrets.admit(KEY),
                                    LimelightUploadListener.NONE));

            List<FakeLimelightServer.Received> requests = fake.requests();
            String attribute = "userSubmitImportProgramKey=\"" + KEY + "\"";
            for (FakeLimelightServer.Received request : requests) {
                assertFalse(request.path().contains(KEY), "the key is in a URL");
                for (Map.Entry<String, String> header : request.headers().entrySet()) {
                    assertFalse(
                            header.getValue().contains(KEY), "the key is in " + header.getKey());
                }
            }
            assertEquals(1, count(requests.get(0).bodyText(), KEY));
            assertEquals(1, count(requests.get(0).bodyText(), attribute));
            assertEquals(1, count(requests.get(1).bodyText(), attribute));
            assertEquals(0, count(requests.get(2).bodyText(), KEY), "the key is in the file body");
            assertEquals(1, count(requests.get(2).parameters(), attribute));
            assertEquals(1, count(requests.get(3).bodyText(), attribute));
            String everything = new String(fake.rawTraffic(), StandardCharsets.ISO_8859_1);
            assertEquals(3, count(everything, KEY), "raw: three XML bodies, nothing else");
            assertFalse(requests.get(2).parameters().contains("FilenameWPathOnSubmitMachine"));
            assertFalse(everything.contains(file.getParent().toString()), "a local path was sent");
            assertTrue(requests.get(1).bodyText().contains("submitterSameMachine=\"false\""));
        }
    }

    @Test
    @DisplayName("a corrupt file: refused by the fake's schema check, its message kept, no submit")
    void aCorruptFileIsRefused() throws IOException {
        Path corrupt =
                LimelightFixtures.real(
                        LimelightFixtures.CORRUPT_ATTRIBUTE,
                        LimelightFixtures.CORRUPT_ATTRIBUTE_SHA256);
        try (FakeLimelightServer fake = FakeLimelightServer.start(KEY, PROJECT)) {
            LimelightUploadOutcome outcome =
                    client().upload(
                                    request(
                                            fake,
                                            PROJECT,
                                            corrupt,
                                            LimelightFixtures.CORRUPT_ATTRIBUTE_SHA256),
                                    secrets.admit(KEY),
                                    LimelightUploadListener.NONE);

            Refused refused = assertInstanceOf(Refused.class, outcome);
            assertEquals(LimelightUploadStep.UPLOAD_FILE, refused.step());
            assertEquals(400, refused.httpStatus());
            assertEquals(Map.of("limelightXMLFileFailsInitialParse", "true"), refused.flags());
            assertEquals(
                    Optional.of(
                            "INVALID: ERROR line 2 col 18: cvc-complex-type.4: Attribute"
                                    + " 'fasta_filename' must appear on element"
                                    + " 'limelight_input'."),
                    refused.message());
            assertEquals(List.of(AUTH, INIT, FILE), fake.calls(), "a refused file was submitted");
            assertFalse(fake.uploads().get(0).submitted());
            assertEquals(List.of(), fake.wireViolations());
        }
    }

    @Test
    @DisplayName("a truncated file is refused as not well-formed, with the validator's words")
    void aTruncatedFileIsRefused() throws IOException {
        Path truncated =
                LimelightFixtures.real(
                        LimelightFixtures.CORRUPT_TRUNCATED,
                        LimelightFixtures.CORRUPT_TRUNCATED_SHA256);
        try (FakeLimelightServer fake = FakeLimelightServer.start(KEY, PROJECT)) {
            Refused refused =
                    assertInstanceOf(
                            Refused.class,
                            client().upload(
                                            request(
                                                    fake,
                                                    PROJECT,
                                                    truncated,
                                                    LimelightFixtures.CORRUPT_TRUNCATED_SHA256),
                                            secrets.admit(KEY),
                                            LimelightUploadListener.NONE));

            assertEquals(
                    Optional.of(
                            "INVALID: FATAL line 114132 col 1: XML document structures must start"
                                    + " and end within the same entity."),
                    refused.message());
            assertEquals(List.of(AUTH, INIT, FILE), fake.calls());
        }
    }

    @Test
    @DisplayName("a wrong key: refused at the authentication test with the server's words")
    void aWrongKeyIsRefused() throws IOException {
        try (FakeLimelightServer fake = FakeLimelightServer.start(KEY, PROJECT)) {
            Refused refused =
                    assertInstanceOf(
                            Refused.class,
                            client().upload(
                                            request(
                                                    fake,
                                                    PROJECT,
                                                    realFile(),
                                                    LimelightFixtures.LIMELIGHT_XML_SHA256),
                                            secrets.admit("WRong-key-0000000000000"),
                                            LimelightUploadListener.NONE));

            assertEquals(LimelightUploadStep.AUTH_TEST, refused.step());
            assertEquals(200, refused.httpStatus());
            assertEquals(Map.of("userSubmitImportProgramKeyNotFound", "true"), refused.flags());
            assertEquals(
                    Optional.of(
                            "The supplied submit import key (in param '--user-submit-import-key')"
                                    + " is invalid.  The first 2 characters are 'WR'."),
                    refused.message());
            assertEquals(List.of(AUTH), fake.calls());
            assertEquals(List.of(), fake.uploads());
        }
    }

    @Test
    @DisplayName("a project the server does not have: refused with the server's words")
    void aWrongProjectIsRefused() throws IOException {
        try (FakeLimelightServer fake = FakeLimelightServer.start(KEY, PROJECT)) {
            Refused refused =
                    assertInstanceOf(
                            Refused.class,
                            client().upload(
                                            request(
                                                    fake,
                                                    8,
                                                    realFile(),
                                                    LimelightFixtures.LIMELIGHT_XML_SHA256),
                                            secrets.admit(KEY),
                                            LimelightUploadListener.NONE));

            assertEquals(Map.of("projectIdNotFound", "true"), refused.flags());
            assertEquals(
                    Optional.of("Unable to upload to this project as it is Not Found."),
                    refused.message());
            assertEquals(List.of(AUTH), fake.calls());
        }
    }

    @Test
    @DisplayName("told to refuse the key, then the project, the fake does, and nothing is sent")
    void theFakeRefusesOnRequest() throws IOException {
        try (FakeLimelightServer fake = FakeLimelightServer.start(KEY, PROJECT)) {
            LimelightUploadRequest good =
                    request(fake, PROJECT, realFile(), LimelightFixtures.LIMELIGHT_XML_SHA256);
            fake.refuseKey(true);
            Refused key =
                    assertInstanceOf(
                            Refused.class,
                            client().upload(
                                            good,
                                            secrets.admit(KEY),
                                            LimelightUploadListener.NONE));
            fake.refuseKey(false);
            fake.refuseProject(true);
            Refused project =
                    assertInstanceOf(
                            Refused.class,
                            client().upload(
                                            good,
                                            secrets.admit(KEY),
                                            LimelightUploadListener.NONE));

            assertEquals(Map.of("userSubmitImportProgramKeyNotFound", "true"), key.flags());
            assertEquals(Map.of("projectIdNotFound", "true"), project.flags());
            assertEquals(List.of(AUTH, AUTH), fake.calls());
        }
    }

    @Test
    @DisplayName("a connection cut part-way through the file: failed at upload-file, no submit")
    void aCutConnectionFails() throws IOException {
        try (FakeLimelightServer fake = FakeLimelightServer.start(KEY, PROJECT)) {
            fake.cutUploadAfter(1_000_000);
            Failed failed =
                    assertInstanceOf(
                            Failed.class,
                            client().upload(
                                            request(
                                                    fake,
                                                    PROJECT,
                                                    realFile(),
                                                    LimelightFixtures.LIMELIGHT_XML_SHA256),
                                            secrets.admit(KEY),
                                            LimelightUploadListener.NONE));

            assertEquals(LimelightUploadStep.UPLOAD_FILE, failed.step());
            assertTrue(failed.reason().startsWith("the connection failed: "), failed.reason());
            assertEquals(List.of(AUTH, INIT, FILE), fake.calls());
            assertEquals(0, fake.requests().get(2).status());
            assertEquals(1_000_000, fake.requests().get(2).body().length);
            assertFalse(fake.uploads().get(0).submitted());
        }
    }

    @Test
    @DisplayName("a server older than version 16 gets the raw parameters header, and accepts")
    void anOlderServerGetsTheRawHeader() throws IOException {
        try (FakeLimelightServer fake = FakeLimelightServer.start(KEY, PROJECT)) {
            fake.serverProgramVersion(15);
            Submitted submitted =
                    assertInstanceOf(
                            Submitted.class,
                            client().upload(
                                            request(
                                                    fake,
                                                    PROJECT,
                                                    realFile(),
                                                    LimelightFixtures.LIMELIGHT_XML_SHA256),
                                            secrets.admit(KEY),
                                            LimelightUploadListener.NONE));

            Map<String, String> headers = fake.requests().get(2).headers();
            assertTrue(headers.containsKey("limelight_upload_file_params_xml"), headers.toString());
            assertFalse(headers.containsKey("limelight_upload_file_params_xml_base_64"));
            assertFalse(
                    fake.requests().get(2).parameters().contains("newHeaderParamsFormatVersion"));
            assertEquals(15, submitted.serverProgramVersion());
            assertEquals(List.of(), fake.wireViolations());
        }
    }

    @Test
    @DisplayName("a server version of 16 sends the Base64 header with the format version")
    void version16GetsTheBase64Header() throws IOException {
        try (FakeLimelightServer fake = FakeLimelightServer.start(KEY, PROJECT)) {
            client().upload(
                            request(
                                    fake,
                                    PROJECT,
                                    realFile(),
                                    LimelightFixtures.LIMELIGHT_XML_SHA256),
                            secrets.admit(KEY),
                            LimelightUploadListener.NONE);

            Map<String, String> headers = fake.requests().get(2).headers();
            assertTrue(headers.containsKey("limelight_upload_file_params_xml_base_64"));
            assertFalse(headers.containsKey("limelight_upload_file_params_xml"));
            assertTrue(
                    fake.requests()
                            .get(2)
                            .parameters()
                            .contains("newHeaderParamsFormatVersion=\"1\""));
            assertTrue(
                    fake.requests()
                            .get(2)
                            .parameters()
                            .contains(
                                    "fileSHA256Hash=\""
                                            + LimelightFixtures.LIMELIGHT_XML_SHA256
                                            + "\""));
        }
    }

    @Test
    @DisplayName("a declared SHA-256 that is not the file's: refused with the server's flag")
    void aWrongDigestIsRefused() throws IOException {
        try (FakeLimelightServer fake = FakeLimelightServer.start(KEY, PROJECT)) {
            Refused refused =
                    assertInstanceOf(
                            Refused.class,
                            client().upload(
                                            request(fake, PROJECT, realFile(), "0".repeat(64)),
                                            secrets.admit(KEY),
                                            LimelightUploadListener.NONE));

            assertEquals(
                    Map.of("uploadedFileSHA256HashNotMatchParamFileSHA256Hash", "true"),
                    refused.flags());
            assertEquals(List.of(AUTH, INIT, FILE), fake.calls());
        }
    }

    @Test
    @DisplayName("a file that has gone: failed at upload-file before the body is sent")
    void aMissingFileFails(@TempDir Path tmp) throws IOException {
        try (FakeLimelightServer fake = FakeLimelightServer.start(KEY, PROJECT)) {
            Path gone = tmp.resolve("gone.xml");
            Failed failed =
                    assertInstanceOf(
                            Failed.class,
                            client().upload(
                                            request(fake, PROJECT, gone, "a".repeat(64)),
                                            secrets.admit(KEY),
                                            LimelightUploadListener.NONE));

            assertEquals(LimelightUploadStep.UPLOAD_FILE, failed.step());
            assertEquals("the file to upload is gone: " + gone, failed.reason());
            assertEquals(List.of(AUTH, INIT), fake.calls());
        }
    }

    @Test
    @DisplayName("redirects, errors, empty, DOCTYPE and wrong answers: failed, never followed")
    void answersThatAreNotTheProtocols(@TempDir Path tmp) throws IOException {
        Path file = Files.writeString(tmp.resolve("x.xml"), "<limelight_input/>");
        String doctype =
                "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]>"
                        + "<submitImport_AuthTest_Response_PgmXML statusSuccess=\"true\"/>";
        assertEquals(
                "the server answered HTTP 302 (a redirect, which is not followed: check the server"
                        + " address), which is not a submit-import answer",
                failure(302, "", file));
        assertEquals(
                "the server answered HTTP 500, which is not a submit-import answer",
                failure(500, "<x/>", file));
        assertEquals(
                "the server answered HTTP 400, which is not a submit-import answer",
                failure(400, "", file));
        assertEquals("the server answered HTTP 200 with an empty body", failure(200, "", file));
        assertEquals(
                "the server's answer carries a DOCTYPE, so it is not read",
                failure(200, doctype, file));
        assertEquals(
                "the server answered with <submitImport_Init_Response_PgmXML>, not"
                        + " <submitImport_AuthTest_Response_PgmXML>",
                failure(200, "<submitImport_Init_Response_PgmXML statusSuccess=\"true\"/>", file));
        assertTrue(failure(200, "not xml at all", file).startsWith("the server's answer is not"));
    }

    private String failure(int status, String body, Path file) throws IOException {
        List<String> paths = Collections.synchronizedList(new ArrayList<>());
        try (LoopbackHttp server =
                LoopbackHttp.serve(
                        0,
                        request -> {
                            paths.add(request.path());
                            return new LoopbackHttp.Response(status, body);
                        })) {
            LimelightUploadOutcome outcome =
                    client().upload(
                                    new LimelightUploadRequest(
                                            LimelightServer.parse(
                                                    "http://127.0.0.1:"
                                                            + server.port()
                                                            + "/limelight"),
                                            PROJECT,
                                            file,
                                            "b".repeat(64)),
                                    secrets.admit(KEY),
                                    LimelightUploadListener.NONE);
            Failed failed = assertInstanceOf(Failed.class, outcome);
            assertEquals(LimelightUploadStep.AUTH_TEST, failed.step());
            assertEquals(List.of("/limelight" + AUTH), paths, "a second call was made");
            assertFalse(failed.toString().contains(KEY));
            return failed.reason();
        }
    }

    @Test
    @DisplayName("a scripted server: init refused, no usable uploadKey, submit refused")
    void laterStepsAnswerForThemselves(@TempDir Path tmp) throws IOException {
        Path file = Files.writeString(tmp.resolve("x.xml"), "<limelight_input/>");
        String ok = "statusSuccess=\"true\"";

        Refused init =
                assertInstanceOf(
                        Refused.class,
                        scripted(
                                file,
                                ok,
                                "statusSuccess=\"false\" projectLocked=\"true\"",
                                ok,
                                ok));
        Failed noKey = assertInstanceOf(Failed.class, scripted(file, ok, ok, ok, ok));
        Failed badKey =
                assertInstanceOf(
                        Failed.class, scripted(file, ok, ok + " uploadKey=\"41 00\"", ok, ok));
        Refused submit =
                assertInstanceOf(
                        Refused.class,
                        scripted(
                                file,
                                ok,
                                ok + " uploadKey=\"77\"",
                                ok,
                                "statusSuccess=\"false\" statusFailErrorMessage=\"no\""));
        Submitted noDirectory =
                assertInstanceOf(
                        Submitted.class, scripted(file, ok, ok + " uploadKey=\"78\"", ok, ok));

        assertEquals(LimelightUploadStep.INITIALIZE, init.step());
        assertEquals(Map.of("projectLocked", "true"), init.flags());
        assertEquals(LimelightUploadStep.INITIALIZE, noKey.step());
        assertEquals(
                "the server accepted the initialisation but sent no usable uploadKey",
                noKey.reason());
        assertEquals(noKey.reason(), badKey.reason());
        assertEquals(LimelightUploadStep.FINAL_SUBMIT, submit.step());
        assertEquals(Optional.of("no"), submit.message());
        assertEquals("78", noDirectory.uploadKey());
        assertEquals("", noDirectory.importerSubDir());
        assertEquals(0, noDirectory.serverProgramVersion(), "no version given is version 0");
    }

    private LimelightUploadOutcome scripted(
            Path file, String auth, String init, String upload, String submit) throws IOException {
        Map<String, String> answers =
                Map.of(
                        "/limelight" + AUTH,
                        "<submitImport_AuthTest_Response_PgmXML " + auth + "/>",
                        "/limelight" + INIT,
                        "<submitImport_Init_Response_PgmXML " + init + "/>",
                        "/limelight" + FILE,
                        "<submitImport_UploadFile_Response_PgmXML " + upload + "/>",
                        "/limelight" + SUBMIT,
                        "<submitImport_FinalSubmit_Response_PgmXML " + submit + "/>");
        try (LoopbackHttp server =
                LoopbackHttp.serve(
                        0,
                        request ->
                                new LoopbackHttp.Response(
                                        200, answers.getOrDefault(request.path(), "")))) {
            return client().upload(
                            new LimelightUploadRequest(
                                    LimelightServer.parse(
                                            "http://127.0.0.1:" + server.port() + "/limelight"),
                                    PROJECT,
                                    file,
                                    "a".repeat(64)),
                            secrets.admit(KEY),
                            LimelightUploadListener.NONE);
        }
    }

    @Test
    @DisplayName("a server that never answers: failed when the call's timeout runs out")
    void aSilentServerTimesOut(@TempDir Path tmp) throws IOException {
        Path file = Files.writeString(tmp.resolve("x.xml"), "<limelight_input/>");
        try (ServerSocket silent = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
            LimelightUploadClient impatient =
                    new LimelightUploadClient(
                            Duration.ofSeconds(5), Duration.ofSeconds(1), Duration.ofSeconds(1));
            Failed failed =
                    assertInstanceOf(
                            Failed.class,
                            impatient.upload(
                                    new LimelightUploadRequest(
                                            LimelightServer.parse(
                                                    "http://127.0.0.1:"
                                                            + silent.getLocalPort()
                                                            + "/limelight"),
                                            PROJECT,
                                            file,
                                            "c".repeat(64)),
                                    secrets.admit(KEY),
                                    LimelightUploadListener.NONE));

            assertEquals("no answer from the server within 1 s", failed.reason());
        }
    }

    @Test
    @DisplayName("nobody listening: failed with the connection's own reason")
    void nobodyListening(@TempDir Path tmp) throws IOException {
        Path file = Files.writeString(tmp.resolve("x.xml"), "<limelight_input/>");
        int port;
        try (ServerSocket closed = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
            port = closed.getLocalPort();
        }
        Failed failed =
                assertInstanceOf(
                        Failed.class,
                        client().upload(
                                        new LimelightUploadRequest(
                                                LimelightServer.parse(
                                                        "http://127.0.0.1:" + port + "/ll"),
                                                PROJECT,
                                                file,
                                                "d".repeat(64)),
                                        secrets.admit(KEY),
                                        LimelightUploadListener.NONE));

        assertEquals(LimelightUploadStep.AUTH_TEST, failed.step());
        assertTrue(
                failed.reason().startsWith("the connection failed: ConnectException"),
                failed.reason());
    }

    @Test
    @DisplayName("a key with a blank or non-ASCII character is refused, and is not quoted")
    void keysAreChecked() {
        IllegalArgumentException blank =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> LimelightUploadClient.checkedKey("abcd efgh-secret"));
        assertEquals(
                "a Limelight submit key is printable ASCII without blanks; the one given has"
                        + " another character at position 4",
                blank.getMessage());
        assertThrows(
                IllegalArgumentException.class,
                () -> LimelightUploadClient.checkedKey("abcdéfgh-secret"));
        assertThrows(
                IllegalArgumentException.class,
                () -> LimelightUploadClient.checkedKey("abcdefgh\u007f"));
        assertEquals("!~azAZ09", LimelightUploadClient.checkedKey("!~azAZ09"));
    }

    @Test
    @DisplayName("an unusable key is refused before any connection is made")
    void anUnusableKeyIsRefusedFirst(@TempDir Path tmp) throws IOException {
        Path file = Files.writeString(tmp.resolve("x.xml"), "<limelight_input/>");
        Credential spaced = secrets.admit("has a space in it");
        try (FakeLimelightServer fake = FakeLimelightServer.start(KEY, PROJECT)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            client().upload(
                                            request(fake, PROJECT, file, "e".repeat(64)),
                                            spaced,
                                            LimelightUploadListener.NONE));
            assertEquals(List.of(), fake.calls());
        }
    }

    @Test
    @DisplayName("no outcome prints the key")
    void outcomesDoNotPrintTheKey() throws IOException {
        try (FakeLimelightServer fake = FakeLimelightServer.start(KEY, PROJECT)) {
            LimelightUploadOutcome submitted =
                    client().upload(
                                    request(
                                            fake,
                                            PROJECT,
                                            realFile(),
                                            LimelightFixtures.LIMELIGHT_XML_SHA256),
                                    secrets.admit(KEY),
                                    LimelightUploadListener.NONE);
            fake.refuseKey(true);
            LimelightUploadOutcome refused =
                    client().upload(
                                    request(
                                            fake,
                                            PROJECT,
                                            realFile(),
                                            LimelightFixtures.LIMELIGHT_XML_SHA256),
                                    secrets.admit(KEY),
                                    LimelightUploadListener.NONE);

            assertFalse(submitted.toString().contains(KEY), submitted.toString());
            assertFalse(refused.toString().contains(KEY), refused.toString());
            assertFalse(refused.toString().contains(KEY.substring(0, 10)));
        }
    }

    @Test
    @DisplayName("timeouts must be positive")
    void timeoutsArePositive() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new LimelightUploadClient(
                                Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(1)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new LimelightUploadClient(
                                Duration.ofSeconds(1),
                                Duration.ofSeconds(-1),
                                Duration.ofSeconds(1)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new LimelightUploadClient(
                                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ZERO));
        assertEquals(Duration.ofSeconds(30), LimelightUploadClient.DEFAULT_CONNECT_TIMEOUT);
        assertEquals(Duration.ofMinutes(2), LimelightUploadClient.DEFAULT_CALL_TIMEOUT);
        assertEquals(Duration.ofHours(6), LimelightUploadClient.DEFAULT_UPLOAD_TIMEOUT);
    }

    private static int count(String text, String part) {
        int found = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) {
            found++;
        }
        return found;
    }

    /** Records what the client told it. */
    static final class Recording implements LimelightUploadListener {

        private final List<String> steps = Collections.synchronizedList(new ArrayList<>());
        private final List<Long> sent = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void stepStarted(LimelightUploadStep step) {
            steps.add("start " + step.id());
        }

        @Override
        public void stepAnswered(LimelightUploadStep step, boolean accepted) {
            steps.add("answer " + step.id() + " " + accepted);
        }

        @Override
        public void bytesSent(long bytes, long total) {
            assertEquals(REAL_SIZE, total);
            sent.add(bytes);
        }

        List<String> steps() {
            return List.copyOf(steps);
        }

        long lastSent() {
            return sent.isEmpty() ? -1 : sent.get(sent.size() - 1);
        }

        int reports() {
            return sent.size();
        }

        boolean monotonic() {
            List<Long> copy = List.copyOf(sent);
            for (int index = 1; index < copy.size(); index++) {
                if (copy.get(index) < copy.get(index - 1)) {
                    return false;
                }
            }
            return true;
        }
    }
}
