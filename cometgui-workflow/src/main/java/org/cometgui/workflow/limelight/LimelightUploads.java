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

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.HashService;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.RunIdentity;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.secrets.Credential;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.domain.secrets.SessionSecrets;
import org.cometgui.provenance.events.ProvenanceEvent;
import org.cometgui.provenance.events.ProvenanceEventLog;
import org.cometgui.provenance.events.ProvenanceEventLogReader;
import org.cometgui.provenance.events.ProvenanceEventType;
import org.cometgui.tools.limelight.LimelightServer;
import org.cometgui.tools.limelight.LimelightUploadClient;
import org.cometgui.tools.limelight.LimelightUploadListener;
import org.cometgui.tools.limelight.LimelightUploadOutcome;
import org.cometgui.tools.limelight.LimelightUploadOutcome.Failed;
import org.cometgui.tools.limelight.LimelightUploadOutcome.Refused;
import org.cometgui.tools.limelight.LimelightUploadOutcome.Submitted;
import org.cometgui.tools.limelight.LimelightUploadRequest;
import org.cometgui.tools.limelight.LimelightUploadStep;
import org.cometgui.workflow.limelight.LimelightUploadRefusedException.Reason;

/**
 * A run's Limelight XML uploaded to a Limelight server: the one path through which an upload is
 * started ({@code R-SEC-04}, {@code R-SEC-03}; phase 12, design decisions P12-10, P12-12, P12-15).
 *
 * <h2>Two steps, and the second cannot be taken without the first</h2>
 *
 * <ol>
 *   <li>{@link #destination} checks the server address (normalised, https unless loopback), the
 *       project id, that the run has ended, that the file is one of the run's own conversions --
 *       named by a {@code limelight.converted} event in its event log -- and that the file's
 *       SHA-256, hashed now, is still the one that event recorded. It sends nothing and writes
 *       nothing. The {@link LimelightDestination} it returns is what the interface shows.
 *   <li>{@link #upload} takes the {@link LimelightUploadConfirmation} that only {@link
 *       LimelightDestination#confirm()} makes -- the scientist's explicit action -- and uses it up.
 *       It checks again that the run has not started executing and that the file is unchanged,
 *       before any connection is made.
 * </ol>
 *
 * <p>Nothing else starts an upload: no engine step plans one ({@code EngineStep.UPLOAD_LIMELIGHT}
 * stays declared and unused) and an architecture rule confines the upload client to this package.
 *
 * <h2>The record</h2>
 *
 * <p>Each upload writes its own log, {@code logs/limelight-upload.log} and then {@code
 * limelight-upload.1.log}, {@code .2.log}, ... (never overwritten), every line through the one
 * redactor and, live, to a {@link LimelightUploadWatcher}. It records {@value #UPLOADING} before
 * the first byte is sent, then {@value #UPLOADED} or {@value #FAILED}. The payload keys are this
 * class's constants. <strong>The key is in neither, in any form</strong>: it is registered with the
 * session's redactor when it enters the application ({@link SessionSecrets#admit}), and this class
 * adds it to the redactor it writes with as well, so even a server message that quoted it would be
 * written {@code [REDACTED]}.
 */
public final class LimelightUploads {

    /** The wire name of the event written before the first byte is sent. */
    public static final String UPLOADING = "limelight.uploading";

    /** The wire name of the event written when the server queued the import. */
    public static final String UPLOADED = "limelight.uploaded";

    /** The wire name of the event written when it did not. */
    public static final String FAILED = "limelight.unuploaded";

    /** The run. */
    public static final String RUN_ID = ProvenanceEvent.RUN_ID_KEY;

    /** The server's normalised address. */
    public static final String SERVER = "limelight.server";

    /** The project id. */
    public static final String PROJECT_ID = "limelight.project-id";

    /** The project's page. */
    public static final String PROJECT_PAGE = "limelight.project-page";

    /** The file's absolute path. */
    public static final String FILE_PATH = "upload.file.path";

    /** Its size in bytes. */
    public static final String FILE_SIZE = "upload.file.size";

    /** Its MD5. */
    public static final String FILE_MD5 = "upload.file.md5";

    /** Its SHA-256, as sent to the server. */
    public static final String FILE_SHA256 = "upload.file.sha256";

    /** The server's {@code uploadKey}: the import's tracking id. */
    public static final String UPLOAD_KEY = "limelight.upload-key";

    /** The server's {@code importerSubDir}. */
    public static final String IMPORTER_SUB_DIR = "limelight.importer-sub-dir";

    /** The submit-program version the server reported. */
    public static final String SERVER_VERSION = "limelight.server-program-version";

    /** How many bytes of the file were sent. */
    public static final String BYTES_SENT = "upload.bytes-sent";

    /** The step an unsuccessful upload ended at. */
    public static final String STEP = "upload.step";

    /** {@value #REFUSED} or {@value #NO_ANSWER}. */
    public static final String FAILURE = "upload.failure";

    /** {@value #FAILURE} when the server answered and refused. */
    public static final String REFUSED = "refused-by-server";

    /** {@value #FAILURE} when no protocol answer came. */
    public static final String NO_ANSWER = "no-answer";

    /** The HTTP status the server's refusal came with. */
    public static final String HTTP_STATUS = "upload.http-status";

    /** The flags the server set, by its wire names, comma-separated. */
    public static final String SERVER_FLAGS = "limelight.server-flags";

    /** The server's {@code statusFailErrorMessage}, after redaction. */
    public static final String SERVER_MESSAGE = "limelight.server-message";

    /** What went wrong when no answer came. */
    public static final String MESSAGE = ProvenanceEvent.MESSAGE_KEY;

    /** The upload log's path. */
    public static final String LOG_PATH = LimelightConversions.LOG_PATH;

    /** The upload log's MD5. */
    public static final String LOG_MD5 = LimelightConversions.LOG_MD5;

    /** The upload log's SHA-256. */
    public static final String LOG_SHA256 = LimelightConversions.LOG_SHA256;

    /** The first upload log's name; later ones insert {@code .<n>} before {@code .log}. */
    public static final String LOG_NAME = "limelight-upload";

    private final LimelightUploadClient client;
    private final HashService hashes;
    private final SessionSecrets secrets;
    private final Clock clock;
    private final Supplier<Set<RunId>> executing;

    /**
     * The uploads, over the one client, hasher and session secrets.
     *
     * @param client the upload client
     * @param hashes the one hasher
     * @param secrets the session's secrets, whose redactor writes every log line and event
     * @param clock dates every line and event
     * @param executing the runs the session's engine is executing now
     */
    public LimelightUploads(
            LimelightUploadClient client,
            HashService hashes,
            SessionSecrets secrets,
            Clock clock,
            Supplier<Set<RunId>> executing) {
        this.client = Objects.requireNonNull(client, "client");
        this.hashes = Objects.requireNonNull(hashes, "hashes");
        this.secrets = Objects.requireNonNull(secrets, "secrets");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.executing = Objects.requireNonNull(executing, "executing");
    }

    /**
     * The Limelight XML files a run's conversions produced, newest last, as its event log records
     * them: what the interface offers to upload.
     *
     * @param run the run
     * @return each file's path, by the order its {@code limelight.converted} event was written
     * @throws IOException if the event log cannot be read
     */
    public List<Path> convertedFiles(RunLayout run) throws IOException {
        Objects.requireNonNull(run, "run");
        List<Path> files = new ArrayList<>();
        for (ProvenanceEvent converted : conversions(run)) {
            Path file = Path.of(converted.payload().get(LimelightConversions.OUTPUT_PATH));
            files.remove(file);
            files.add(file);
        }
        return files;
    }

    /**
     * Checks where an upload would go and what it would send. Sends nothing, writes nothing.
     *
     * @param run the run
     * @param identity its recorded identity
     * @param file the Limelight XML file, one of {@link #convertedFiles}
     * @param server the server address as the scientist typed it
     * @param projectId the project id as the scientist typed it
     * @return the destination, for the interface to show and the scientist to confirm
     * @throws LimelightUploadRefusedException naming why it cannot be used
     * @throws IOException if the event log or the file cannot be read
     */
    public LimelightDestination destination(
            RunLayout run, RunIdentity identity, Path file, String server, String projectId)
            throws IOException {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(projectId, "projectId");
        refuseIfExecuting(identity.runId());
        LimelightServer checkedServer;
        try {
            checkedServer = LimelightServer.parse(server);
        } catch (IllegalArgumentException unusable) {
            throw new LimelightUploadRefusedException(Reason.SERVER, unusable.getMessage());
        }
        int project;
        try {
            project = LimelightServer.parseProjectId(projectId);
        } catch (IllegalArgumentException unusable) {
            throw new LimelightUploadRefusedException(Reason.PROJECT, unusable.getMessage());
        }
        Path absolute = file.toAbsolutePath().normalize();
        Optional<ProvenanceEvent> converted = Optional.empty();
        for (ProvenanceEvent event : conversions(run)) {
            if (absolute.toString().equals(event.payload().get(LimelightConversions.OUTPUT_PATH))) {
                converted = Optional.of(event);
            }
        }
        if (converted.isEmpty()) {
            throw new LimelightUploadRefusedException(
                    Reason.NOT_A_CONVERSION,
                    absolute
                            + " is not a Limelight XML file this run's conversions produced, so it"
                            + " is not uploaded from run "
                            + identity.runId().value()
                            + ".");
        }
        String recorded = converted.get().payload().get(LimelightConversions.OUTPUT_SHA256);
        FileHashes now = unchanged(absolute, recorded, "since its conversion recorded it");
        return new LimelightDestination(
                run,
                identity.runId(),
                checkedServer,
                project,
                absolute,
                Files.size(absolute),
                now.md5(),
                now.sha256());
    }

    /**
     * Uploads the confirmed file to the confirmed destination: the scientist's explicit action.
     *
     * @param confirmation the confirmation, used up by this call
     * @param key the submit key, admitted to the session
     * @param watcher hears the upload log live, and the bytes sent
     * @return the attempt: how it ended, its log
     * @throws LimelightUploadRefusedException if the confirmation was used before, the run is
     *     executing, or the file changed since the destination was shown; nothing was sent and
     *     nothing was written
     * @throws IOException if the log or the event log cannot be written
     */
    public LimelightUploadAttempt upload(
            LimelightUploadConfirmation confirmation,
            Credential key,
            LimelightUploadWatcher watcher)
            throws IOException {
        Objects.requireNonNull(confirmation, "confirmation");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(watcher, "watcher");
        LimelightDestination destination = confirmation.destination();
        if (!confirmation.use()) {
            throw new LimelightUploadRefusedException(
                    Reason.CONFIRMATION_USED,
                    "This confirmation was already used for an upload; each upload needs its own.");
        }
        refuseIfExecuting(destination.runId());
        unchanged(destination.file(), destination.sha256(), "since the destination was shown");
        SecretRedactor redactor = secrets.redactor().withSecret(key.reveal());
        RunLayout run = destination.run();
        Map<String, String> recorded = new LinkedHashMap<>();
        recorded.put(RUN_ID, destination.runId().value());
        recorded.put(SERVER, destination.server().toString());
        recorded.put(PROJECT_ID, Integer.toString(destination.projectId()));
        recorded.put(PROJECT_PAGE, destination.projectPage().toString());
        recorded.put(FILE_PATH, destination.file().toString());
        recorded.put(FILE_SIZE, Long.toString(destination.size()));
        recorded.put(FILE_MD5, destination.md5());
        recorded.put(FILE_SHA256, destination.sha256());
        Path logFile = nextLog(run);
        LimelightUploadOutcome outcome;
        try (UploadLog log = new UploadLog(logFile, redactor, clock, watcher)) {
            log.line("Upload to Limelight: " + destination);
            log.line(
                    "File: "
                            + destination.file()
                            + " ("
                            + destination.size()
                            + " bytes, SHA-256 "
                            + destination.sha256()
                            + ")");
            append(run, redactor, ProvenanceEventType.LIMELIGHT_UPLOADING, recorded);
            outcome =
                    redacted(
                            client.upload(
                                    new LimelightUploadRequest(
                                            destination.server(),
                                            destination.projectId(),
                                            destination.file(),
                                            destination.sha256()),
                                    key,
                                    new Progress(log, watcher)),
                            redactor);
            describe(log, outcome);
        }
        Map<String, String> done = new LinkedHashMap<>(recorded);
        if (outcome instanceof Submitted submitted) {
            done.put(UPLOAD_KEY, submitted.uploadKey());
            done.put(IMPORTER_SUB_DIR, submitted.importerSubDir());
            done.put(SERVER_VERSION, Integer.toString(submitted.serverProgramVersion()));
            done.put(BYTES_SENT, Long.toString(submitted.bytesSent()));
        } else if (outcome instanceof Refused refused) {
            done.put(STEP, refused.step().id());
            done.put(FAILURE, REFUSED);
            done.put(HTTP_STATUS, Integer.toString(refused.httpStatus()));
            done.put(SERVER_FLAGS, String.join(",", refused.flags().keySet()));
            refused.message().ifPresent(message -> done.put(SERVER_MESSAGE, message));
        } else {
            Failed failed = (Failed) outcome;
            done.put(STEP, failed.step().id());
            done.put(FAILURE, NO_ANSWER);
            done.put(MESSAGE, failed.reason());
        }
        FileHashes logHashes = hashes.hash(logFile);
        done.put(LOG_PATH, logFile.toString());
        done.put(LOG_MD5, logHashes.md5());
        done.put(LOG_SHA256, logHashes.sha256());
        append(
                run,
                redactor,
                outcome instanceof Submitted
                        ? ProvenanceEventType.LIMELIGHT_UPLOADED
                        : ProvenanceEventType.LIMELIGHT_UPLOAD_FAILED,
                done);
        return new LimelightUploadAttempt(destination, outcome, logFile);
    }

    // ---------------------------------------------------------------------------- checks --

    private void refuseIfExecuting(RunId runId) throws LimelightUploadRefusedException {
        if (executing.get().contains(runId)) {
            throw new LimelightUploadRefusedException(
                    Reason.RUN_EXECUTING,
                    "run "
                            + runId.value()
                            + " is executing: its results are not final. Upload it when the run"
                            + " has ended.");
        }
    }

    private FileHashes unchanged(Path file, String expected, String since) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new LimelightUploadRefusedException(
                    Reason.FILE_CHANGED, file + " is no longer there, so it is not uploaded.");
        }
        FileHashes now = hashes.hash(file);
        if (!now.sha256().equals(expected)) {
            throw new LimelightUploadRefusedException(
                    Reason.FILE_CHANGED,
                    file
                            + " has changed "
                            + since
                            + " (SHA-256 "
                            + now.sha256()
                            + ", recorded "
                            + expected
                            + "), so it is not uploaded.");
        }
        return now;
    }

    private List<ProvenanceEvent> conversions(RunLayout run) throws IOException {
        if (!Files.isRegularFile(run.eventLogFile())) {
            return List.of();
        }
        List<ProvenanceEvent> converted = new ArrayList<>();
        for (ProvenanceEvent event :
                ProvenanceEventLogReader.recover(run.eventLogFile(), secrets.redactor()).events()) {
            if (event.type() == ProvenanceEventType.LIMELIGHT_CONVERTED
                    && event.payload().containsKey(LimelightConversions.OUTPUT_PATH)
                    && event.payload().containsKey(LimelightConversions.OUTPUT_SHA256)) {
                converted.add(event);
            }
        }
        return converted;
    }

    // ---------------------------------------------------------------------------- record --

    /** The next upload log's path: never one that exists. */
    static Path nextLog(RunLayout run) throws IOException {
        Path logs = Files.createDirectories(run.logsDirectory());
        Path first = logs.resolve(LOG_NAME + ".log");
        if (!Files.exists(first)) {
            return first;
        }
        for (int number = 1; ; number++) {
            Path next = logs.resolve(LOG_NAME + "." + number + ".log");
            if (!Files.exists(next)) {
                return next;
            }
        }
    }

    private void append(
            RunLayout run,
            SecretRedactor redactor,
            ProvenanceEventType type,
            Map<String, String> values)
            throws IOException {
        try (ProvenanceEventLog log =
                ProvenanceEventLog.openAppend(run.eventLogFile(), redactor, clock)) {
            log.append(type, values);
        }
    }

    /** The outcome with the server's words through the redactor, for the log, events and caller. */
    static LimelightUploadOutcome redacted(
            LimelightUploadOutcome outcome, SecretRedactor redactor) {
        if (outcome instanceof Refused refused) {
            return new Refused(
                    refused.step(),
                    refused.httpStatus(),
                    refused.flags(),
                    refused.message().map(redactor::redactText));
        }
        if (outcome instanceof Failed failed) {
            return new Failed(failed.step(), redactor.redactText(failed.reason()));
        }
        return outcome;
    }

    private static void describe(UploadLog log, LimelightUploadOutcome outcome) throws IOException {
        if (outcome instanceof Submitted submitted) {
            log.line(
                    "Submitted. The server queued the import: uploadKey (tracking id) "
                            + submitted.uploadKey()
                            + ", importerSubDir "
                            + submitted.importerSubDir()
                            + ", server program version "
                            + submitted.serverProgramVersion()
                            + ".");
            log.line(
                    "The import runs on the server; its progress and result are on "
                            + submitted.projectPage());
        } else if (outcome instanceof Refused refused) {
            log.line(
                    "Refused by the server at "
                            + refused.step().id()
                            + " (HTTP "
                            + refused.httpStatus()
                            + "). Its flags: "
                            + (refused.flags().isEmpty()
                                    ? "none"
                                    : String.join(", ", refused.flags().keySet()))
                            + ".");
            log.line("The server said: " + refused.message().orElse("(no message)"));
        } else {
            Failed failed = (Failed) outcome;
            log.line(
                    "Failed at "
                            + failed.step().id()
                            + ", with no answer from the server. Reason: "
                            + failed.reason());
        }
    }

    /** Turns the client's progress into log lines, and passes the bytes on. */
    private static final class Progress implements LimelightUploadListener {

        private final UploadLog log;
        private final LimelightUploadWatcher watcher;
        private int reportedTenths;

        Progress(UploadLog log, LimelightUploadWatcher watcher) {
            this.log = log;
            this.watcher = watcher;
        }

        @Override
        public void stepStarted(LimelightUploadStep step) {
            log.quietly("Sending " + step.id() + ".");
        }

        @Override
        public void stepAnswered(LimelightUploadStep step, boolean accepted) {
            log.quietly(
                    (accepted ? "The server accepted " : "The server refused ") + step.id() + ".");
        }

        @Override
        public synchronized void bytesSent(long sent, long total) {
            watcher.bytesSent(sent, total);
            int tenths = total <= 0 ? 10 : (int) Math.min(10, sent * 10 / total);
            if (tenths > reportedTenths) {
                reportedTenths = tenths;
                log.quietly("Sent " + sent + " of " + total + " bytes (" + tenths * 10 + "%).");
            }
        }
    }

    /** One upload's log: time-stamped lines, every one redacted, each also to the watcher. */
    static final class UploadLog implements AutoCloseable {

        private final Writer writer;
        private final SecretRedactor redactor;
        private final Clock clock;
        private final LimelightUploadWatcher watcher;
        private IOException deferred;

        UploadLog(Path file, SecretRedactor redactor, Clock clock, LimelightUploadWatcher watcher)
                throws IOException {
            this.writer =
                    Files.newBufferedWriter(
                            file, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            this.redactor = redactor;
            this.clock = clock;
            this.watcher = watcher;
        }

        synchronized void line(String text) throws IOException {
            String clean = redactor.redactText(text);
            writer.write(clock.instant() + " " + clean + "\n");
            writer.flush();
            watcher.line(clean);
        }

        /** A line from the client's threads, which cannot throw: a failure is kept for close. */
        synchronized void quietly(String text) {
            try {
                line(text);
            } catch (IOException failed) {
                if (deferred == null) {
                    deferred = failed;
                }
            }
        }

        @Override
        public synchronized void close() throws IOException {
            writer.close();
            if (deferred != null) {
                throw deferred;
            }
        }
    }
}
