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

package org.cometgui.tools.limelight.fake;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * THE fake Limelight server ({@code D-007}; phase 12, design decision P12-16): the server side of
 * the submit-import protocol of {@code limelight-core} v8.3.1, on loopback only, for every test
 * that uploads.
 *
 * <p><strong>One original, byte-identical copies.</strong> This file lives in {@code
 * cometgui-tools}'s test sources. A module whose tests need it elsewhere carries a copy at the same
 * package path together with {@code LoopbackGuard} and this package's resources, and a test there
 * fails if any copy's bytes differ from the original. It therefore depends on nothing but the JDK
 * and its own resource.
 *
 * <p><strong>What makes it credible rather than a stub.</strong>
 *
 * <ul>
 *   <li>Every request body, every file-parameters header and every answer it sends is validated
 *       against the wire schema JAXB generated from the reference importer's own classes ({@code
 *       submit-import-wire-v16.xsd}, a copy of the one committed under {@code
 *       scripts/spikes/limelight_upload_spike/}). A request that does not match is answered HTTP
 *       500 and recorded in {@link #wireViolations()}, which a test asserts is empty.
 *   <li>Every Limelight XML file it receives is checked for its root element and validated against
 *       {@code limelight-xml.xsd} read from the pinned converter JAR, both held to their SHA-256.
 *       An invalid file is refused in the protocol's own form: HTTP 400 with {@code
 *       limelightXMLFileFailsInitialParse="true"} and the validator's first error as {@code
 *       statusFailErrorMessage}. (The real server stores the file and validates it later in its
 *       importer; the fake refuses at once, so that a test can see the refusal.)
 *   <li>It recomputes the SHA-256 over what it receives and refuses a mismatch with the server's
 *       flag; it refuses a wrong key and a wrong project with the server's flags and messages
 *       (copied from the server's submit-key access check in {@code limelight-core} v8.3.1).
 *   <li>The real importer JAR uploads to it and is accepted, and is refused on a corrupt file
 *       ({@code FakeLimelightServerTest}).
 * </ul>
 *
 * <p><strong>Loopback only.</strong> It binds {@code 127.0.0.1} on an ephemeral port, and refuses a
 * peer that is not loopback. It writes nothing to disk: what it received is kept in memory, raw,
 * for tests to assert on -- including where the key arrived ({@link #rawTraffic()}).
 *
 * <p><strong>What it can be told to do</strong>: refuse every key, refuse the project, cut the
 * connection part-way through a file, or claim another server program version.
 */
public final class FakeLimelightServer implements AutoCloseable {

    /** The web application's context path. */
    public static final String CONTEXT = "/limelight";

    /** The program version it claims: {@code limelight-core} v8.3.1's. */
    public static final int SERVER_PROGRAM_VERSION = 16;

    /** The first tracking id ({@code uploadKey}) it issues. */
    public static final int FIRST_TRACKING_ID = 4100;

    /** The pinned converter JAR, relative to the repository root. */
    public static final String CONVERTER_JAR =
            "scratch/phase05/artefacts/v2.8.1__cometPercolator2LimelightXML.jar";

    /** Its SHA-256, as {@code manifests/tools.json} pins it. */
    public static final String CONVERTER_JAR_SHA256 =
            "843573396ce0654a0ac81582b378c496923e49dde71f40d750d890947774ece1";

    /** The schema entry's SHA-256, as Phase 00 recorded it. */
    public static final String LIMELIGHT_XSD_SHA256 =
            "e8c1b92098db3b4364f06101b1def43675cd956ddf8758de25b692e3f698d242";

    /** The wire schema, beside this class. */
    public static final String WIRE_SCHEMA = "submit-import-wire-v16.xsd";

    /** The wire schema's SHA-256: the spike's committed file. */
    public static final String WIRE_SCHEMA_SHA256 =
            "775f6895cebe8e73981b5acc87d2613d18f886d6f70bc11fff2acb3ec55f8b7f";

    private static final String PROLOG =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";
    private static final String PATHS = "/d/rws/for-page/";
    private static final String AUTH = PATHS + "project-upload-data-upload-submit-pgm-auth-test";
    private static final String FASTA =
            PATHS + "project-upload-data-upload-get-fasta-file-upload-accepted-submit-program";
    private static final String INIT =
            PATHS + "project-upload-data-upload-initialize-from-submit-pgm";
    private static final String UPLOAD = PATHS + "project-upload-data-upload-file-from-submit-pgm";
    private static final String SUBMIT =
            PATHS + "project-upload-data-upload-submit-from-submit-pgm";
    private static final String HEADER_BASE64 = "limelight_upload_file_params_xml_base_64";
    private static final String HEADER_RAW = "limelight_upload_file_params_xml";
    private static final String KEY = "userSubmitImportProgramKey";
    private static final int MAX_HEADER_BYTES = 64 * 1024;
    private static final int MAX_XML_BODY_BYTES = 1 << 20;

    /** Attributes the wire schema marks required, emitted false/0 unless set. */
    private static final Map<String, List<String>> REQUIRED =
            Map.of(
                    "submitImport_AuthTest_Response_PgmXML", accessFlags(),
                    "submitImport_Init_Response_PgmXML", accessFlags(),
                    "submitImport_Get_FASTAFileUploadAccepted_Response_PgmXML",
                            List.of(
                                    "projectIdNotFound",
                                    "projectMarkedForDeletion",
                                    "projectNotEnabled",
                                    "userSubmitImportProgramKeyNotFound",
                                    "userNotAuthorizedForProject",
                                    "userNotAcceptLatestTermsOfService",
                                    "submitProgramVersionNumberNotAccepted",
                                    "fastaFileSubmitConfigured",
                                    "statusSuccess"),
                    "submitImport_UploadFile_Response_PgmXML",
                            List.of(
                                    "fileSizeLimitExceeded",
                                    "maxSize",
                                    "uploadFileFieldNameInvalid",
                                    "moreThanOneuploadedFile",
                                    "filenameInFormNotMatchFilenameInQueryString",
                                    "noUploadedFile",
                                    "uploadKeyNotValid",
                                    "limelightXMLFileFailsInitialParse",
                                    "limelightXMLFilerootXMLNodeIncorrect",
                                    "scanFileNotAllowed",
                                    "scanFilenameSuffixNotValid",
                                    "scanFileFailsInitialParse",
                                    "scanFilerootXMLNodeIncorrect",
                                    "projectLocked",
                                    "uploadedFileSHA256HashNotMatchParamFileSHA256Hash",
                                    "statusSuccess"),
                    "submitImport_FinalSubmit_Response_PgmXML",
                            List.of(
                                    "submitProgramVersionNumberNotAccepted",
                                    "projectLocked",
                                    "submittedScanFileNotAllowed",
                                    "statusSuccess"));

    private final String expectedKey;
    private final int project;
    private final Schema limelight;
    private final Schema wire;
    private final ServerSocket socket;
    private final Thread acceptor;
    private final AtomicInteger nextTrackingId = new AtomicInteger(FIRST_TRACKING_ID);
    private final List<Received> received = Collections.synchronizedList(new ArrayList<>());
    private final List<String> violations = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Upload> uploads = Collections.synchronizedMap(new LinkedHashMap<>());

    private volatile boolean refuseKey;
    private volatile boolean refuseProject;
    private volatile long cutUploadAfter = -1;
    private volatile int serverVersion = SERVER_PROGRAM_VERSION;

    private FakeLimelightServer(String expectedKey, int project, Schema limelight, Schema wire)
            throws IOException {
        this.expectedKey = expectedKey;
        this.project = project;
        this.limelight = limelight;
        this.wire = wire;
        this.socket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        this.acceptor = new Thread(this::serve, "fake-limelight-" + socket.getLocalPort());
        acceptor.setDaemon(true);
        acceptor.start();
    }

    /**
     * Starts a fake that accepts one key for one project, validating received Limelight XML against
     * the schema in the pinned converter JAR found under the repository root.
     *
     * @param expectedKey the key it accepts
     * @param project the project it holds
     * @return the running fake
     * @throws IOException if it cannot bind or read its schemas
     */
    public static FakeLimelightServer start(String expectedKey, int project) throws IOException {
        return start(expectedKey, project, pinnedConverterJar());
    }

    /**
     * Starts a fake over a converter JAR given by the caller, held to its SHA-256.
     *
     * @param expectedKey the key it accepts
     * @param project the project it holds
     * @param converterJar the pinned converter JAR
     * @return the running fake
     * @throws IOException if it cannot bind or read its schemas
     */
    public static FakeLimelightServer start(String expectedKey, int project, Path converterJar)
            throws IOException {
        Objects.requireNonNull(expectedKey, "expectedKey");
        return new FakeLimelightServer(
                expectedKey, project, limelightSchema(converterJar), wireSchema());
    }

    /**
     * The pinned converter JAR under the repository root (the nearest directory up from here that
     * holds {@code manifests}), held to its SHA-256. Missing or changed fails, never skips.
     *
     * @return its absolute path
     * @throws IOException if it cannot be hashed
     */
    public static Path pinnedConverterJar() throws IOException {
        Path root = null;
        for (Path at = Path.of("").toAbsolutePath(); at != null; at = at.getParent()) {
            if (Files.isDirectory(at.resolve("manifests"))) {
                root = at;
                break;
            }
        }
        if (root == null) {
            throw new AssertionError(
                    "no manifests directory above " + Path.of("").toAbsolutePath());
        }
        Path jar = root.resolve(CONVERTER_JAR);
        if (!Files.isRegularFile(jar)) {
            throw new AssertionError(
                    jar + " is missing: it is gitignored scratch, refilled as Phase 05 describes");
        }
        String actual = sha256(Files.readAllBytes(jar));
        if (!CONVERTER_JAR_SHA256.equals(actual)) {
            throw new AssertionError(
                    jar + " has SHA-256 " + actual + ", pinned " + CONVERTER_JAR_SHA256);
        }
        return jar;
    }

    /**
     * The web application's address.
     *
     * @return {@code http://127.0.0.1:<port>/limelight}
     */
    public String baseUrl() {
        return "http://127.0.0.1:" + socket.getLocalPort() + CONTEXT;
    }

    /**
     * Refuse every key from now on, as the server does for a key it does not know.
     *
     * @param refuse whether to
     */
    public void refuseKey(boolean refuse) {
        this.refuseKey = refuse;
    }

    /**
     * Refuse the project from now on, as the server does for one that does not exist.
     *
     * @param refuse whether to
     */
    public void refuseProject(boolean refuse) {
        this.refuseProject = refuse;
    }

    /**
     * Cut the connection after receiving this many bytes of a file, without answering.
     *
     * @param bytes the bytes to read first, or a negative number to receive whole files again
     */
    public void cutUploadAfter(long bytes) {
        this.cutUploadAfter = bytes;
    }

    /**
     * Claim another server program version in the authentication answer.
     *
     * @param version the version
     */
    public void serverProgramVersion(int version) {
        this.serverVersion = version;
    }

    /**
     * Every request received, in order.
     *
     * @return a copy
     */
    public List<Received> requests() {
        synchronized (received) {
            return List.copyOf(received);
        }
    }

    /**
     * The paths called, in order, without the context.
     *
     * @return for example {@code [.../auth-test, .../initialize-from-submit-pgm]}
     */
    public List<String> calls() {
        return requests().stream().map(Received::operation).toList();
    }

    /**
     * Every byte received, every request's raw bytes in order: request lines, headers, bodies.
     *
     * @return a copy
     */
    public byte[] rawTraffic() {
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        for (Received request : requests()) {
            all.writeBytes(request.raw());
        }
        return all.toByteArray();
    }

    /**
     * Every request or answer that did not match the wire schema.
     *
     * @return a copy; a credible exchange leaves it empty
     */
    public List<String> wireViolations() {
        synchronized (violations) {
            return List.copyOf(violations);
        }
    }

    /**
     * Every import initialised, by tracking id, in order.
     *
     * @return a copy
     */
    public List<Upload> uploads() {
        synchronized (uploads) {
            return List.copyOf(uploads.values());
        }
    }

    @Override
    public void close() throws IOException {
        socket.close();
        try {
            acceptor.join(10_000);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * One request as it arrived.
     *
     * @param method the method
     * @param path the path, context included
     * @param headers the headers, names lower-cased
     * @param body the body
     * @param raw every byte of the request
     * @param status the status it was answered with, or 0 when the connection was cut
     * @param outcome what the fake made of it
     */
    public record Received(
            String method,
            String path,
            Map<String, String> headers,
            byte[] body,
            byte[] raw,
            int status,
            String outcome) {

        /**
         * Copies the request.
         *
         * @throws NullPointerException if a component is {@code null}
         */
        public Received {
            headers = Map.copyOf(headers);
            body = body.clone();
            raw = raw.clone();
            Objects.requireNonNull(outcome, "outcome");
        }

        /**
         * The body.
         *
         * @return a copy
         */
        @Override
        public byte[] body() {
            return body.clone();
        }

        /**
         * The raw request.
         *
         * @return a copy
         */
        @Override
        public byte[] raw() {
            return raw.clone();
        }

        /**
         * The path without the context.
         *
         * @return it
         */
        public String operation() {
            return path.startsWith(CONTEXT) ? path.substring(CONTEXT.length()) : path;
        }

        /**
         * The body as text.
         *
         * @return it, UTF-8
         */
        public String bodyText() {
            return new String(body, StandardCharsets.UTF_8);
        }

        /**
         * The file parameters header, decoded.
         *
         * @return the parameters document, or empty text when there is no such header
         */
        public String parameters() {
            String base64 = headers.get(HEADER_BASE64);
            if (base64 != null) {
                return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
            }
            return headers.getOrDefault(HEADER_RAW, "");
        }
    }

    /**
     * One import, as the fake tracks it.
     *
     * @param trackingId its {@code uploadKey}
     * @param fileName the file name the parameters gave
     * @param receivedBytes how many bytes of the file arrived
     * @param receivedSha256 their SHA-256
     * @param declaredSha256 the SHA-256 the parameters declared
     * @param verdict {@code VALID}, or the first reason it was refused
     * @param submitted whether the final submit queued it
     * @param importerSubDir the directory the final submit named, or empty text
     */
    public record Upload(
            String trackingId,
            String fileName,
            long receivedBytes,
            String receivedSha256,
            String declaredSha256,
            String verdict,
            boolean submitted,
            String importerSubDir) {}

    // ---------------------------------------------------------------------------- serving --

    private synchronized void violation(String what) {
        violations.add(what);
    }

    private void serve() {
        while (!socket.isClosed()) {
            try (Socket connection = socket.accept()) {
                connection.setSoTimeout(60_000);
                handle(connection);
            } catch (SocketException closed) {
                return;
            } catch (IOException | RuntimeException broken) {
                violation("the fake failed on a request: " + broken);
            }
        }
    }

    private synchronized void handle(Socket connection) throws IOException {
        if (!connection.getInetAddress().isLoopbackAddress()) {
            respond(connection.getOutputStream(), 403, "");
            return;
        }
        InputStream in = connection.getInputStream();
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        byte[] head = readHead(in, raw);
        String[] lines = new String(head, StandardCharsets.ISO_8859_1).split("\r\n");
        String[] requestLine = lines[0].split(" ");
        String method = requestLine[0];
        String path = requestLine.length > 1 ? requestLine[1] : "";
        Map<String, String> headers = new LinkedHashMap<>();
        for (int index = 1; index < lines.length; index++) {
            int colon = lines[index].indexOf(':');
            if (colon > 0) {
                headers.put(
                        lines[index].substring(0, colon).trim().toLowerCase(Locale.ROOT),
                        lines[index].substring(colon + 1).trim());
            }
        }
        Exchange exchange = new Exchange(method, path, headers, raw, connection);
        try {
            route(exchange, in);
        } catch (Cut cut) {
            connection.setSoLinger(true, 0);
            exchange.record(0, cut.getMessage());
        }
    }

    private synchronized void route(Exchange exchange, InputStream in) throws IOException, Cut {
        if (!"POST".equals(exchange.method) || !exchange.path.startsWith(CONTEXT + "/")) {
            exchange.answer(404, "", "not a submit-import call");
            return;
        }
        String length = exchange.headers.get("content-length");
        if (length == null || exchange.headers.containsKey("transfer-encoding")) {
            exchange.answer(411, "", "no Content-Length");
            return;
        }
        long contentLength = Long.parseLong(length);
        String operation = exchange.path.substring(CONTEXT.length());
        if (UPLOAD.equals(operation)) {
            upload(exchange, in, contentLength);
            return;
        }
        if (contentLength > MAX_XML_BODY_BYTES) {
            exchange.answer(413, "", "an XML call larger than any real one");
            return;
        }
        exchange.received(in.readNBytes((int) contentLength));
        switch (operation) {
            case AUTH -> authTest(exchange);
            case FASTA -> fasta(exchange);
            case INIT -> initialize(exchange);
            case SUBMIT -> submit(exchange);
            default -> exchange.answer(404, "", "unknown call " + operation);
        }
    }

    private synchronized void authTest(Exchange exchange) throws IOException {
        Map<String, String> request = request(exchange, "submitImport_AuthTest_Request_PgmXML");
        if (request == null) {
            return;
        }
        Map<String, String> answer = new LinkedHashMap<>();
        answer.put("submitProgramVersionNumberCurrentPerWebapp", Integer.toString(serverVersion));
        boolean allowed = access(request, answer);
        answer.put("statusSuccess", Boolean.toString(allowed));
        exchange.answerXml(
                200,
                "submitImport_AuthTest_Response_PgmXML",
                answer,
                allowed ? "auth ok" : "auth refused");
    }

    private synchronized void fasta(Exchange exchange) throws IOException {
        Map<String, String> request =
                request(exchange, "submitImport_Get_FASTAFileUploadAccepted_Request_PgmXML");
        if (request == null) {
            return;
        }
        Map<String, String> answer = new LinkedHashMap<>();
        boolean allowed = access(request, answer);
        answer.put("statusSuccess", Boolean.toString(allowed));
        exchange.answerXml(
                200,
                "submitImport_Get_FASTAFileUploadAccepted_Response_PgmXML",
                answer,
                "fasta not configured");
    }

    private synchronized void initialize(Exchange exchange) throws IOException {
        Map<String, String> request = request(exchange, "submitImport_Init_Request_PgmXML");
        if (request == null) {
            return;
        }
        Map<String, String> answer = new LinkedHashMap<>();
        if (!access(request, answer)) {
            answer.put("statusSuccess", "false");
            exchange.answerXml(200, "submitImport_Init_Response_PgmXML", answer, "init refused");
            return;
        }
        String id = Integer.toString(nextTrackingId.getAndIncrement());
        uploads.put(id, new Upload(id, "", 0, "", "", "NOT RECEIVED", false, ""));
        answer.put("statusSuccess", "true");
        answer.put("uploadKey", id);
        answer.put("uploadTempSubdir", "upload_tmp_" + id);
        exchange.answerXml(
                200, "submitImport_Init_Response_PgmXML", answer, "init ok, uploadKey " + id);
    }

    private synchronized void upload(Exchange exchange, InputStream in, long contentLength)
            throws IOException, Cut {
        String base64 = exchange.headers.get(HEADER_BASE64);
        String rawParameters = exchange.headers.get(HEADER_RAW);
        byte[] parameters;
        if (base64 != null && !base64.isEmpty()) {
            parameters = Base64.getDecoder().decode(base64);
        } else if (rawParameters != null && !rawParameters.isEmpty()) {
            parameters = rawParameters.getBytes(StandardCharsets.UTF_8);
        } else {
            drain(exchange, in, contentLength);
            exchange.answer(400, "", "no parameters header");
            return;
        }
        String violation = wireViolation(parameters, "file parameters header");
        Map<String, String> request =
                violation == null
                        ? attributes(parameters, "submitImport_UploadFile_Request_Common")
                        : null;
        if (request == null) {
            drain(exchange, in, contentLength);
            exchange.answer(500, "", violation == null ? "parameters unreadable" : violation);
            return;
        }
        String root = "submitImport_UploadFile_Response_PgmXML";
        Map<String, String> answer = new LinkedHashMap<>();
        if (!access(request, answer)) {
            drain(exchange, in, contentLength);
            answer.put("statusSuccess", "false");
            exchange.answerXml(200, root, answer, "upload refused: access");
            return;
        }
        String id = request.getOrDefault("uploadKey", "");
        Upload known = uploads.get(id);
        if (known == null) {
            drain(exchange, in, contentLength);
            answer.put("statusSuccess", "false");
            answer.put("uploadKeyNotValid", "true");
            exchange.answerXml(400, root, answer, "unknown uploadKey");
            return;
        }
        if (!"1".equals(request.get("fileType"))) {
            drain(exchange, in, contentLength);
            answer.put("statusSuccess", "false");
            answer.put("scanFileNotAllowed", "true");
            exchange.answerXml(400, root, answer, "only Limelight XML is accepted");
            return;
        }
        MessageDigest digest = sha256Digest();
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        byte[] buffer = new byte[1 << 16];
        long remaining = contentLength;
        long cutAt = cutUploadAfter;
        while (remaining > 0) {
            int want = (int) Math.min(buffer.length, remaining);
            if (cutAt >= 0) {
                want = (int) Math.min(want, Math.max(0, cutAt - file.size()));
                if (want == 0) {
                    exchange.received(file.toByteArray());
                    throw new Cut("cut after " + file.size() + " bytes of the file");
                }
            }
            int read = in.read(buffer, 0, want);
            if (read < 0) {
                break;
            }
            digest.update(buffer, 0, read);
            file.write(buffer, 0, read);
            remaining -= read;
        }
        exchange.received(file.toByteArray());
        String got = HexFormat.of().formatHex(digest.digest());
        String declared = request.getOrDefault("fileSHA256Hash", "");
        String name = request.getOrDefault("filename", "");
        if (!got.equalsIgnoreCase(declared)) {
            uploads.put(
                    id,
                    new Upload(
                            id, name, file.size(), got, declared, "SHA-256 MISMATCH", false, ""));
            answer.put("statusSuccess", "false");
            answer.put("uploadedFileSHA256HashNotMatchParamFileSHA256Hash", "true");
            exchange.answerXml(400, root, answer, "sha256 mismatch");
            return;
        }
        String verdict = judge(exchange.body());
        uploads.put(id, new Upload(id, name, file.size(), got, declared, verdict, false, ""));
        if (verdict.startsWith("ROOT:")) {
            answer.put("statusSuccess", "false");
            answer.put("limelightXMLFilerootXMLNodeIncorrect", "true");
            answer.put("statusFailErrorMessage", verdict);
            exchange.answerXml(400, root, answer, "refused: wrong root");
            return;
        }
        if (!"VALID".equals(verdict)) {
            answer.put("statusSuccess", "false");
            answer.put("limelightXMLFileFailsInitialParse", "true");
            answer.put("statusFailErrorMessage", verdict);
            exchange.answerXml(400, root, answer, "refused by limelight-xml.xsd");
            return;
        }
        answer.put("statusSuccess", "true");
        exchange.answerXml(200, root, answer, "accepted, valid against limelight-xml.xsd");
    }

    private synchronized void submit(Exchange exchange) throws IOException {
        Map<String, String> request = request(exchange, "submitImport_FinalSubmit_Request_PgmXML");
        if (request == null) {
            return;
        }
        String root = "submitImport_FinalSubmit_Response_PgmXML";
        Map<String, String> answer = new LinkedHashMap<>();
        if (!access(request, answer)) {
            answer.put("statusSuccess", "false");
            exchange.answerXml(200, root, answer, "submit refused: access");
            return;
        }
        String id = request.getOrDefault("uploadKey", "");
        Upload known = uploads.get(id);
        boolean hasXmlItem =
                new String(exchange.body(), StandardCharsets.UTF_8).contains("<fileItems ")
                        && new String(exchange.body(), StandardCharsets.UTF_8)
                                .contains("fileType=\"1\"");
        if (known == null || !hasXmlItem || !"VALID".equals(known.verdict())) {
            answer.put("statusSuccess", "false");
            answer.put("statusFailErrorMessage", "no validated Limelight XML for uploadKey " + id);
            exchange.answerXml(200, root, answer, "submit refused");
            return;
        }
        String directory =
                String.format(Locale.ROOT, "import_dir_for_id_%07d", Integer.parseInt(id));
        uploads.put(
                id,
                new Upload(
                        id,
                        known.fileName(),
                        known.receivedBytes(),
                        known.receivedSha256(),
                        known.declaredSha256(),
                        known.verdict(),
                        true,
                        directory));
        answer.put("statusSuccess", "true");
        answer.put("importerSubDir", directory);
        exchange.answerXml(200, root, answer, "submitted; import queued for " + id);
    }

    /** The server's key and project checks, in its order; false when refused. */
    private synchronized boolean access(Map<String, String> request, Map<String, String> answer) {
        if (refuseProject || !Integer.toString(project).equals(request.get("projectIdentifier"))) {
            answer.put("projectIdNotFound", "true");
            answer.put(
                    "statusFailErrorMessage",
                    "Unable to upload to this project as it is Not Found.");
            return false;
        }
        String key = request.getOrDefault(KEY, "");
        boolean known =
                !refuseKey
                        && !key.isEmpty()
                        && MessageDigest.isEqual(
                                key.getBytes(StandardCharsets.UTF_8),
                                expectedKey.getBytes(StandardCharsets.UTF_8));
        if (!known) {
            answer.put("userSubmitImportProgramKeyNotFound", "true");
            String first = key.length() > 2 ? key.substring(0, 2) : key;
            answer.put(
                    "statusFailErrorMessage",
                    "The supplied submit import key (in param '--user-submit-import-key') is"
                            + " invalid."
                            + (key.isEmpty()
                                    ? ""
                                    : "  The first 2 characters are '" + first + "'."));
            return false;
        }
        return true;
    }

    /** A request body, wire-checked and read; null (and answered 500) when it is not one. */
    private synchronized Map<String, String> request(Exchange exchange, String root)
            throws IOException {
        String violation = wireViolation(exchange.body(), "request to " + exchange.path);
        if (violation != null) {
            exchange.answer(500, "", violation);
            return null;
        }
        Map<String, String> attributes = attributes(exchange.body(), root);
        if (attributes == null) {
            violation("request to " + exchange.path + " is not <" + root + ">");
            exchange.answer(500, "", "wrong root");
        }
        return attributes;
    }

    // ------------------------------------------------------------------------- the schemas --

    /** VALID, ROOT:..., or INVALID: and the validator's first error. */
    private String judge(byte[] file) {
        try {
            XMLStreamReader reader =
                    hardened().createXMLStreamReader(new ByteArrayInputStream(file));
            int event = reader.next();
            while (event != XMLStreamConstants.START_ELEMENT) {
                if (event == XMLStreamConstants.DTD) {
                    return "INVALID: carries a DOCTYPE";
                }
                event = reader.next();
            }
            if (!"limelight_input".equals(reader.getLocalName())) {
                return "ROOT: root element is not limelight_input";
            }
        } catch (XMLStreamException | RuntimeException malformed) {
            return "INVALID: not well-formed: " + malformed.getMessage();
        }
        List<String> errors = new ArrayList<>();
        try {
            Validator validator = limelight.newValidator();
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            validator.setErrorHandler(
                    new ErrorHandler() {
                        @Override
                        public void warning(SAXParseException warning) {
                            // warnings do not refuse a file
                        }

                        @Override
                        public void error(SAXParseException error) {
                            errors.add(format("ERROR", error));
                        }

                        @Override
                        public void fatalError(SAXParseException fatal) throws SAXParseException {
                            errors.add(format("FATAL", fatal));
                            throw fatal;
                        }
                    });
            validator.validate(new StreamSource(new ByteArrayInputStream(file)));
        } catch (SAXException | IOException failed) {
            if (errors.isEmpty()) {
                errors.add("FATAL " + failed.getMessage());
            }
        }
        return errors.isEmpty() ? "VALID" : "INVALID: " + errors.get(0);
    }

    private static String format(String kind, SAXParseException error) {
        return kind
                + " line "
                + error.getLineNumber()
                + " col "
                + error.getColumnNumber()
                + ": "
                + error.getMessage();
    }

    /** Null when the document matches the wire schema, else the violation (recorded). */
    private synchronized String wireViolation(byte[] document, String what) {
        try {
            Validator validator = wire.newValidator();
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            validator.validate(new StreamSource(new ByteArrayInputStream(document)));
            return null;
        } catch (SAXException | IOException wrong) {
            String violation =
                    "WIRE: " + what + " does not match the wire schema: " + wrong.getMessage();
            violation(violation);
            return violation;
        }
    }

    /** The root's attributes when the root is as expected, else null. */
    private static Map<String, String> attributes(byte[] document, String root) {
        try {
            XMLStreamReader reader =
                    hardened().createXMLStreamReader(new ByteArrayInputStream(document));
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                    if (!root.equals(reader.getLocalName())) {
                        return null;
                    }
                    Map<String, String> attributes = new LinkedHashMap<>();
                    for (int index = 0; index < reader.getAttributeCount(); index++) {
                        attributes.put(
                                reader.getAttributeLocalName(index),
                                reader.getAttributeValue(index));
                    }
                    return attributes;
                }
            }
            return null;
        } catch (XMLStreamException malformed) {
            return null;
        }
    }

    private static XMLInputFactory hardened() {
        XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        return factory;
    }

    private static SchemaFactory schemaFactory() throws SAXException {
        SchemaFactory factory = SchemaFactory.newDefaultInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory;
    }

    /**
     * The wire schema, from beside this class, held to its SHA-256.
     *
     * @return the compiled schema
     * @throws IOException if it is missing or cannot be compiled
     */
    static Schema wireSchema() throws IOException {
        byte[] bytes = wireSchemaBytes();
        try {
            return schemaFactory()
                    .newSchema(new StreamSource(new ByteArrayInputStream(bytes), WIRE_SCHEMA));
        } catch (SAXException notASchema) {
            throw new IOException("the wire schema does not compile: " + notASchema, notASchema);
        }
    }

    /**
     * The wire schema's bytes, held to their SHA-256.
     *
     * @return them
     * @throws IOException if the resource is missing
     */
    public static byte[] wireSchemaBytes() throws IOException {
        try (InputStream in = FakeLimelightServer.class.getResourceAsStream(WIRE_SCHEMA)) {
            if (in == null) {
                throw new IOException(WIRE_SCHEMA + " is not beside " + FakeLimelightServer.class);
            }
            byte[] bytes = in.readAllBytes();
            String actual = sha256(bytes);
            if (!WIRE_SCHEMA_SHA256.equals(actual)) {
                throw new AssertionError(
                        WIRE_SCHEMA + " has SHA-256 " + actual + ", pinned " + WIRE_SCHEMA_SHA256);
            }
            return bytes;
        }
    }

    /** limelight-xml.xsd from the converter JAR, both held to their SHA-256. */
    private static Schema limelightSchema(Path jar) throws IOException {
        String jarSha = sha256(Files.readAllBytes(jar));
        if (!CONVERTER_JAR_SHA256.equals(jarSha)) {
            throw new AssertionError(
                    jar + " has SHA-256 " + jarSha + ", pinned " + CONVERTER_JAR_SHA256);
        }
        byte[] xsd;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry("limelight-xml.xsd");
            if (entry == null) {
                throw new IOException(jar + " holds no limelight-xml.xsd");
            }
            try (InputStream in = zip.getInputStream(entry)) {
                xsd = in.readAllBytes();
            }
        }
        String xsdSha = sha256(xsd);
        if (!LIMELIGHT_XSD_SHA256.equals(xsdSha)) {
            throw new AssertionError(
                    "limelight-xml.xsd has SHA-256 " + xsdSha + ", pinned " + LIMELIGHT_XSD_SHA256);
        }
        try {
            return schemaFactory()
                    .newSchema(
                            new StreamSource(new ByteArrayInputStream(xsd), "limelight-xml.xsd"));
        } catch (SAXException notASchema) {
            throw new IOException("limelight-xml.xsd does not compile: " + notASchema, notASchema);
        }
    }

    private static List<String> accessFlags() {
        return List.of(
                "projectIdNotFound",
                "projectMarkedForDeletion",
                "projectNotEnabled",
                "userSubmitImportProgramKeyNotFound",
                "userNotAuthorizedForProject",
                "userNotAcceptLatestTermsOfService",
                "submitProgramVersionNumberNotAccepted",
                "projectLocked",
                "statusSuccess");
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException(missing);
        }
    }

    /**
     * A SHA-256 in lower-case hexadecimal.
     *
     * @param bytes the bytes
     * @return the digest
     */
    public static String sha256(byte[] bytes) {
        return HexFormat.of().formatHex(sha256Digest().digest(bytes));
    }

    // --------------------------------------------------------------------------- plumbing --

    private static byte[] readHead(InputStream in, ByteArrayOutputStream raw) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int matched = 0;
        byte[] end = {'\r', '\n', '\r', '\n'};
        while (matched < end.length) {
            int next = in.read();
            if (next < 0) {
                throw new IOException("the connection closed inside the request head");
            }
            head.write(next);
            raw.write(next);
            matched = next == end[matched] ? matched + 1 : (next == '\r' ? 1 : 0);
            if (head.size() > MAX_HEADER_BYTES) {
                throw new IOException("a request head larger than " + MAX_HEADER_BYTES + " bytes");
            }
        }
        byte[] bytes = head.toByteArray();
        byte[] withoutEnd = new byte[bytes.length - end.length];
        System.arraycopy(bytes, 0, withoutEnd, 0, withoutEnd.length);
        return withoutEnd;
    }

    private synchronized void drain(Exchange exchange, InputStream in, long length)
            throws IOException {
        exchange.received(in.readNBytes((int) Math.min(length, Integer.MAX_VALUE - 16)));
    }

    private static void respond(OutputStream out, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String head =
                "HTTP/1.1 "
                        + status
                        + " "
                        + (status == 200 ? "OK" : "Status")
                        + "\r\nContent-Type: application/xml\r\nContent-Length: "
                        + bytes.length
                        + "\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.write(bytes);
        out.flush();
    }

    /** One request being answered. */
    private final class Exchange {

        private final String method;
        private final String path;
        private final Map<String, String> headers;
        private final ByteArrayOutputStream raw;
        private final Socket connection;
        private byte[] body = new byte[0];

        Exchange(
                String method,
                String path,
                Map<String, String> headers,
                ByteArrayOutputStream raw,
                Socket connection) {
            this.method = method;
            this.path = path;
            this.headers = headers;
            this.raw = raw;
            this.connection = connection;
        }

        synchronized void received(byte[] bytes) {
            body = bytes.clone();
            raw.writeBytes(bytes);
        }

        synchronized byte[] body() {
            return body.clone();
        }

        synchronized void answer(int status, String text, String outcome) throws IOException {
            record(status, outcome);
            respond(connection.getOutputStream(), status, text);
        }

        synchronized void answerXml(
                int status, String root, Map<String, String> given, String outcome)
                throws IOException {
            Map<String, String> attributes = new LinkedHashMap<>();
            for (String required : REQUIRED.getOrDefault(root, List.of())) {
                attributes.put(required, "maxSize".equals(required) ? "0" : "false");
            }
            attributes.putAll(given);
            StringBuilder xml = new StringBuilder(PROLOG).append("\n<").append(root);
            for (Map.Entry<String, String> attribute : attributes.entrySet()) {
                xml.append(' ')
                        .append(attribute.getKey())
                        .append("=\"")
                        .append(escape(attribute.getValue()))
                        .append('"');
            }
            String document = xml.append("/>\n").toString();
            String violation =
                    wireViolation(document.getBytes(StandardCharsets.UTF_8), "answer " + root);
            if (violation != null) {
                answer(500, "", violation);
                return;
            }
            answer(status, document, outcome);
        }

        synchronized void record(int status, String outcome) {
            received.add(
                    new Received(method, path, headers, body, raw.toByteArray(), status, outcome));
        }
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /** The connection was cut on purpose. */
    private static final class Cut extends Exception {

        private static final long serialVersionUID = 1L;

        Cut(String message) {
            super(message, null, false, false);
        }
    }
}
