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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;
import org.cometgui.domain.secrets.Credential;
import org.cometgui.tools.limelight.LimelightUploadOutcome.Failed;
import org.cometgui.tools.limelight.LimelightUploadOutcome.Refused;
import org.cometgui.tools.limelight.LimelightUploadOutcome.Submitted;

/**
 * Uploads one Limelight XML file to a Limelight server over the submit-import protocol of {@code
 * limelight-core} v8.3.1, in this JVM (phase 12, design decision P12-14).
 *
 * <p><strong>The four calls</strong> ({@link LimelightUploadStep}): the authentication test, which
 * checks the key and the project and learns the server's program version; the initialisation, which
 * returns the import's tracking id ({@code uploadKey}); the file, sent as one raw streamed request
 * body with its parameters -- name, SHA-256, tracking id, key -- in a header, Base64-encoded for a
 * server at version 16 or later and as raw single-line XML for an older one; and the final submit,
 * which queues the import and returns {@code importerSubDir}. No FASTA is sent, so the protocol's
 * FASTA check is never called.
 *
 * <p><strong>Where the key goes.</strong> Only where the protocol puts it: the {@code
 * userSubmitImportProgramKey} attribute of each request body, and of the file's parameters header.
 * Never in a URL, never on an argument array (nothing here starts a process), never in an exception
 * message or an outcome; {@link Credential#toString()} prints nothing of it.
 *
 * <p><strong>Answers.</strong> An answer with HTTP 200, or 400 with an XML body (the file call's
 * refusal form), is read as the protocol's answer ({@link SubmitImportWire#read}, hardened). {@code
 * statusSuccess="true"} goes on to the next call; anything else ends the upload as {@link Refused},
 * carrying the server's flags and its {@code statusFailErrorMessage} exactly as it sent them -- the
 * caller passes them through the one redactor before they reach a log or an event. Any other
 * status, a redirect included (it is never followed: a redirect would send the key to an address
 * nobody was shown), an empty or malformed body, a refused or cut connection, or a timeout ends it
 * as {@link Failed}.
 *
 * <p><strong>Timeouts</strong> bound every call: connecting, each XML call, and the file call.
 *
 * <p><strong>Proxies.</strong> The client is built with {@link ProxySelector#getDefault()}, the
 * JVM's own selector, explicitly -- so a proxy the scientist's system configures is honoured, and
 * so a test JVM's loopback sinkhole proxy (P12-16) catches any attempt to reach a non-loopback
 * address. Nothing in the product sets a proxy itself; an architecture rule holds that.
 *
 * <p>Stateless; one instance may be shared. Each upload builds its own {@link HttpClient} and
 * closes it.
 */
public final class LimelightUploadClient {

    /** How long a connection may take to open. */
    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(30);

    /** How long an XML call may take to be answered. */
    public static final Duration DEFAULT_CALL_TIMEOUT = Duration.ofMinutes(2);

    /**
     * How long the file call may take, sending included: the reference importer's read timeout, for
     * a file of up to the server's default limit of 1 GB.
     */
    public static final Duration DEFAULT_UPLOAD_TIMEOUT = Duration.ofHours(6);

    private static final String XML = "application/xml";

    private final Duration connectTimeout;
    private final Duration callTimeout;
    private final Duration uploadTimeout;

    /** A client with the default timeouts. */
    public LimelightUploadClient() {
        this(DEFAULT_CONNECT_TIMEOUT, DEFAULT_CALL_TIMEOUT, DEFAULT_UPLOAD_TIMEOUT);
    }

    /**
     * A client with timeouts of the caller's own.
     *
     * @param connectTimeout how long a connection may take to open
     * @param callTimeout how long an XML call may take to be answered
     * @param uploadTimeout how long the file call may take
     * @throws IllegalArgumentException if a timeout is not positive
     */
    public LimelightUploadClient(
            Duration connectTimeout, Duration callTimeout, Duration uploadTimeout) {
        this.connectTimeout = positive(connectTimeout, "connectTimeout");
        this.callTimeout = positive(callTimeout, "callTimeout");
        this.uploadTimeout = positive(uploadTimeout, "uploadTimeout");
    }

    /**
     * Uploads the file.
     *
     * @param request the server, project and file
     * @param key the submit key, registered with the session's redactor
     * @param listener hears each step and the bytes sent
     * @return how the upload ended
     * @throws IllegalArgumentException if the key is not printable ASCII without blanks (the
     *     message does not quote it)
     * @throws NullPointerException if an argument is {@code null}
     */
    public LimelightUploadOutcome upload(
            LimelightUploadRequest request, Credential key, LimelightUploadListener listener) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(listener, "listener");
        String secret = checkedKey(key.reveal());
        int project = request.projectId();
        try (HttpClient http = newClient()) {
            Upload upload = new Upload(http, request, listener);
            SubmitImportWire.Answer auth =
                    upload.call(
                            LimelightUploadStep.AUTH_TEST,
                            SubmitImportWire.authTest(project, secret));
            if (!auth.success()) {
                return refused(LimelightUploadStep.AUTH_TEST, upload.status, auth);
            }
            int serverVersion = serverVersion(auth);
            SubmitImportWire.Answer init =
                    upload.call(
                            LimelightUploadStep.INITIALIZE,
                            SubmitImportWire.initialize(project, secret));
            if (!init.success()) {
                return refused(LimelightUploadStep.INITIALIZE, upload.status, init);
            }
            String uploadKey =
                    init.attribute(SubmitImportWire.UPLOAD_KEY_ATTRIBUTE)
                            .filter(value -> value.matches("[A-Za-z0-9_.-]{1,64}"))
                            .orElseThrow(
                                    () ->
                                            new StepFailure(
                                                    LimelightUploadStep.INITIALIZE,
                                                    "the server accepted the initialisation but"
                                                            + " sent no usable uploadKey"));
            SubmitImportWire.Answer sent = upload.sendFile(serverVersion, uploadKey, secret);
            if (!sent.success()) {
                return refused(LimelightUploadStep.UPLOAD_FILE, upload.status, sent);
            }
            SubmitImportWire.Answer submitted =
                    upload.call(
                            LimelightUploadStep.FINAL_SUBMIT,
                            SubmitImportWire.finalSubmit(
                                    project, uploadKey, request.fileName(), secret));
            if (!submitted.success()) {
                return refused(LimelightUploadStep.FINAL_SUBMIT, upload.status, submitted);
            }
            return new Submitted(
                    serverVersion,
                    uploadKey,
                    submitted.attribute(SubmitImportWire.IMPORTER_SUB_DIR_ATTRIBUTE).orElse(""),
                    request.server().projectPage(project),
                    upload.sent.get());
        } catch (StepFailure failure) {
            return new Failed(failure.step, failure.getMessage());
        }
    }

    /**
     * The HTTP client one upload uses: HTTP/1.1 (as the reference importer speaks), the JVM's own
     * proxy selector, redirects never followed, and the connect timeout.
     *
     * @return a new client, which the caller closes
     */
    HttpClient newClient() {
        ProxySelector proxies =
                Objects.requireNonNull(
                        ProxySelector.getDefault(),
                        "the JVM has no default proxy selector, so no upload is attempted");
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(connectTimeout)
                .proxy(proxies)
                .build();
    }

    /** One upload's calls over one client. */
    private final class Upload {

        private final HttpClient http;
        private final LimelightUploadRequest request;
        private final LimelightUploadListener listener;
        private final AtomicLong sent = new AtomicLong();
        private int status;

        Upload(HttpClient http, LimelightUploadRequest request, LimelightUploadListener listener) {
            this.http = http;
            this.request = request;
            this.listener = listener;
        }

        SubmitImportWire.Answer call(LimelightUploadStep step, String document) throws StepFailure {
            HttpRequest call =
                    HttpRequest.newBuilder(request.server().endpoint(step.path()))
                            .timeout(callTimeout)
                            .header("Content-Type", XML)
                            .header("Accept", XML)
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            document, StandardCharsets.UTF_8))
                            .build();
            return exchange(step, call, callTimeout);
        }

        SubmitImportWire.Answer sendFile(int serverVersion, String uploadKey, String secret)
                throws StepFailure {
            LimelightUploadStep step = LimelightUploadStep.UPLOAD_FILE;
            boolean base64 = serverVersion >= SubmitImportWire.BASE64_PARAMETERS_MINIMUM;
            String parameters =
                    SubmitImportWire.uploadParameters(
                            request.projectId(),
                            uploadKey,
                            request.fileName(),
                            request.sha256(),
                            secret,
                            base64);
            long total;
            HttpRequest.BodyPublisher file;
            try {
                total = Files.size(request.file());
                file = HttpRequest.BodyPublishers.ofFile(request.file());
            } catch (FileNotFoundException | java.nio.file.NoSuchFileException gone) {
                throw new StepFailure(step, "the file to upload is gone: " + request.file());
            } catch (IOException unreadable) {
                throw new StepFailure(
                        step,
                        "the file to upload cannot be read: "
                                + request.file()
                                + ": "
                                + unreadable.getMessage());
            }
            HttpRequest.Builder call =
                    HttpRequest.newBuilder(request.server().endpoint(step.path()))
                            .timeout(uploadTimeout)
                            .header("Content-Type", XML)
                            .header("Accept", XML)
                            .POST(new Counting(file, total, sent, listener));
            try {
                if (base64) {
                    call.header(
                            SubmitImportWire.PARAMETERS_HEADER_BASE64,
                            Base64.getEncoder()
                                    .encodeToString(parameters.getBytes(StandardCharsets.UTF_8)));
                } else {
                    call.header(SubmitImportWire.PARAMETERS_HEADER_RAW, parameters);
                }
            } catch (IllegalArgumentException notAHeader) {
                throw new StepFailure(
                        step,
                        "the file's name cannot travel in the header a Limelight server older than"
                                + " version "
                                + SubmitImportWire.BASE64_PARAMETERS_MINIMUM
                                + " reads; rename the file to plain ASCII");
            }
            return exchange(step, call.build(), uploadTimeout);
        }

        private SubmitImportWire.Answer exchange(
                LimelightUploadStep step, HttpRequest call, Duration timeout) throws StepFailure {
            listener.stepStarted(step);
            HttpResponse<InputStream> response;
            try {
                response = http.send(call, HttpResponse.BodyHandlers.ofInputStream());
            } catch (HttpTimeoutException timedOut) {
                throw new StepFailure(
                        step, "no answer from the server within " + timeout.toSeconds() + " s");
            } catch (IOException failed) {
                throw new StepFailure(step, "the connection failed: " + describe(failed));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new StepFailure(step, "the upload was interrupted");
            }
            status = response.statusCode();
            byte[] body;
            try (InputStream in = response.body()) {
                body = in.readNBytes(SubmitImportWire.MAX_ANSWER_BYTES + 1);
            } catch (IOException failed) {
                throw new StepFailure(
                        step, "the server's answer could not be read: " + describe(failed));
            }
            if (status != 200 && !(status == 400 && body.length > 0)) {
                throw new StepFailure(
                        step,
                        "the server answered HTTP "
                                + status
                                + (status / 100 == 3
                                        ? " (a redirect, which is not followed: check the"
                                                + " server address)"
                                        : "")
                                + ", which is not a submit-import answer");
            }
            if (body.length == 0) {
                throw new StepFailure(step, "the server answered HTTP 200 with an empty body");
            }
            SubmitImportWire.Answer answer;
            try {
                answer = SubmitImportWire.read(body, step.responseRoot());
            } catch (IOException notAnAnswer) {
                throw new StepFailure(step, notAnAnswer.getMessage());
            }
            listener.stepAnswered(step, answer.success());
            return answer;
        }
    }

    /** The file as the request body, telling the listener how much has been handed over. */
    private record Counting(
            HttpRequest.BodyPublisher file,
            long total,
            AtomicLong sent,
            LimelightUploadListener listener)
            implements HttpRequest.BodyPublisher {

        @Override
        public long contentLength() {
            return file.contentLength();
        }

        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            file.subscribe(
                    new Flow.Subscriber<ByteBuffer>() {
                        @Override
                        public void onSubscribe(Flow.Subscription subscription) {
                            subscriber.onSubscribe(subscription);
                        }

                        @Override
                        public void onNext(ByteBuffer item) {
                            long now = sent.addAndGet(item.remaining());
                            listener.bytesSent(now, total);
                            subscriber.onNext(item);
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            subscriber.onError(throwable);
                        }

                        @Override
                        public void onComplete() {
                            subscriber.onComplete();
                        }
                    });
        }
    }

    /** A step that ended without a protocol answer. */
    private static final class StepFailure extends Exception {

        private static final long serialVersionUID = 1L;

        private final LimelightUploadStep step;

        StepFailure(LimelightUploadStep step, String message) {
            super(message, null, false, false);
            this.step = step;
        }
    }

    private static Refused refused(
            LimelightUploadStep step, int status, SubmitImportWire.Answer answer) {
        return new Refused(step, status, answer.flags(), answer.message());
    }

    private static int serverVersion(SubmitImportWire.Answer auth) {
        String text = auth.attribute(SubmitImportWire.SERVER_VERSION_ATTRIBUTE).orElse("");
        return text.matches("[0-9]{1,9}") ? Integer.parseInt(text) : 0;
    }

    /**
     * Checks a submit key's characters without ever quoting it.
     *
     * @param key the key
     * @return the key
     * @throws IllegalArgumentException if it holds anything but printable ASCII without blanks
     */
    static String checkedKey(String key) {
        for (int index = 0; index < key.length(); index++) {
            char character = key.charAt(index);
            if (character <= ' ' || character > '~') {
                throw new IllegalArgumentException(
                        "a Limelight submit key is printable ASCII without blanks; the one given"
                                + " has another character at position "
                                + index);
            }
        }
        return key;
    }

    private static String describe(IOException failed) {
        String message = failed.getMessage();
        return failed.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private static Duration positive(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException(name + " must be positive, but was " + duration);
        }
        return duration;
    }
}
