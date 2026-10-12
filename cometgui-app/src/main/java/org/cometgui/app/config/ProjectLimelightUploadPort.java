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

package org.cometgui.app.config;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.secrets.Credential;
import org.cometgui.domain.secrets.CredentialStore;
import org.cometgui.domain.secrets.SessionSecrets;
import org.cometgui.tools.credentials.CredentialStores;
import org.cometgui.tools.limelight.LimelightServer;
import org.cometgui.tools.limelight.LimelightUploadOutcome;
import org.cometgui.tools.limelight.LimelightUploadOutcome.Refused;
import org.cometgui.tools.limelight.LimelightUploadOutcome.Submitted;
import org.cometgui.ui.viewmodel.limelight.LimelightUploadPort;
import org.cometgui.ui.viewmodel.limelight.LimelightUploadPreview;
import org.cometgui.ui.viewmodel.limelight.LimelightUploaded;
import org.cometgui.ui.viewmodel.results.ResultsRun;
import org.cometgui.workflow.limelight.LimelightDestination;
import org.cometgui.workflow.limelight.LimelightUploadAttempt;
import org.cometgui.workflow.limelight.LimelightUploadRefusedException;
import org.cometgui.workflow.limelight.LimelightUploadWatcher;
import org.cometgui.workflow.limelight.LimelightUploads;

/**
 * The Limelight section's upload port over the session's project, {@link LimelightUploads} -- the
 * one upload path -- the session's one {@link SessionSecrets} and the credential store this
 * computer gets ({@link CredentialStores#forThisComputer}) (phase 12, work unit 7; {@link
 * LimelightUploadPort}).
 *
 * <p>Every rule is the workflow's or the store's: the server's and project's ({@link
 * LimelightServer}), the destination's checks and the upload's record ({@link LimelightUploads}),
 * where a key is kept and why ({@link CredentialStores}). This class only finds the run, chooses
 * the key -- the one typed, or the one kept for the destination's server -- turns the scientist's
 * confirmation into the workflow's ({@link LimelightDestination#confirm()}, once per destination
 * shown) and the outcome into the section's words.
 *
 * <p>The store is chosen on first use, off the interface thread: choosing it may start {@code
 * secret-tool} to probe the desktop keyring.
 */
final class ProjectLimelightUploadPort implements LimelightUploadPort {

    private final ProjectSession project;

    private final LimelightUploads uploads;

    private final SessionSecrets secrets;

    private final Supplier<CredentialStores.Choice> chooser;

    private CredentialStores.Choice chosen;

    /**
     * The port.
     *
     * @param project the session's project
     * @param uploads the one upload path
     * @param secrets the session's secrets: every typed key is admitted here, and {@code uploads}
     *     writes through their redactor
     * @param chooser chooses this computer's credential store; called once, on first use
     */
    ProjectLimelightUploadPort(
            ProjectSession project,
            LimelightUploads uploads,
            SessionSecrets secrets,
            Supplier<CredentialStores.Choice> chooser) {
        this.project = Objects.requireNonNull(project, "project");
        this.uploads = Objects.requireNonNull(uploads, "uploads");
        this.secrets = Objects.requireNonNull(secrets, "secrets");
        this.chooser = Objects.requireNonNull(chooser, "chooser");
    }

    @Override
    public String credentialStore() {
        return choice().explanation();
    }

    @Override
    public List<Path> convertedFiles(ResultsRun run) throws IOException {
        Objects.requireNonNull(run, "run");
        List<Path> files =
                new ArrayList<>(uploads.convertedFiles(ProjectRuns.locate(project, run).layout()));
        Collections.reverse(files);
        return List.copyOf(files);
    }

    @Override
    public String server(String typed) throws LimelightUploadRefusedException {
        Objects.requireNonNull(typed, "typed");
        try {
            return LimelightServer.parse(typed).toString();
        } catch (IllegalArgumentException unusable) {
            throw new LimelightUploadRefusedException(
                    LimelightUploadRefusedException.Reason.SERVER, unusable.getMessage());
        }
    }

    @Override
    public int project(String typed) throws LimelightUploadRefusedException {
        Objects.requireNonNull(typed, "typed");
        try {
            return LimelightServer.parseProjectId(typed);
        } catch (IllegalArgumentException unusable) {
            throw new LimelightUploadRefusedException(
                    LimelightUploadRefusedException.Reason.PROJECT, unusable.getMessage());
        }
    }

    @Override
    public Credential admit(String typed) {
        return secrets.admit(typed);
    }

    @Override
    public boolean keyKept(String server) throws IOException {
        return store().find(server).isPresent();
    }

    @Override
    public void forgetKey(String server) throws IOException {
        store().forget(server);
    }

    @Override
    public LimelightUploadPreview prepare(
            ResultsRun run, Path file, String server, String projectId) throws IOException {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(projectId, "projectId");
        ProjectRuns.Located located = ProjectRuns.locate(project, run);
        return new Prepared(
                uploads.destination(located.layout(), located.identity(), file, server, projectId));
    }

    @Override
    public LimelightUploaded send(
            LimelightUploadPreview confirmed,
            Optional<Credential> typed,
            boolean remember,
            LimelightUploadWatcher watcher)
            throws IOException {
        Objects.requireNonNull(confirmed, "confirmed");
        Objects.requireNonNull(typed, "typed");
        Objects.requireNonNull(watcher, "watcher");
        if (!(confirmed instanceof Prepared prepared)) {
            throw new IOException(
                    "This destination was not checked by this application, so nothing was sent;"
                            + " press Upload again.");
        }
        String account = prepared.server();
        Credential key;
        if (typed.isPresent()) {
            key = typed.get();
        } else {
            key =
                    store().find(account)
                            .orElseThrow(
                                    () ->
                                            new IOException(
                                                    "No submit key was typed and none is kept for "
                                                            + account
                                                            + ", so nothing was sent."));
        }
        if (!prepared.take()) {
            throw new LimelightUploadRefusedException(
                    LimelightUploadRefusedException.Reason.CONFIRMATION_USED,
                    "This destination was already sent once; press Upload to check it again.");
        }
        LimelightUploadAttempt attempt =
                uploads.upload(prepared.destination().confirm(), key, watcher);
        String keyNote = "";
        if (remember && typed.isPresent() && attempt.submitted()) {
            CredentialStore store = store();
            try {
                store.save(account, typed.get());
                keyNote =
                        "The submit key is kept for " + account + ": " + store.description() + ".";
            } catch (IOException notKept) {
                keyNote =
                        "The submit key could not be kept for "
                                + account
                                + ": "
                                + secrets.redactor()
                                        .redactText(String.valueOf(notKept.getMessage()));
            }
        }
        return uploaded(attempt, keyNote);
    }

    /** The section's words for how an upload ended. */
    static LimelightUploaded uploaded(LimelightUploadAttempt attempt, String keyNote) {
        LimelightUploadOutcome outcome = attempt.outcome();
        String page = attempt.destination().projectPage().toString();
        if (outcome instanceof Submitted submitted) {
            return new LimelightUploaded(
                    LimelightUploaded.Kind.SUBMITTED,
                    submitted.step().id(),
                    submitted.uploadKey(),
                    submitted.importerSubDir(),
                    page,
                    0,
                    List.of(),
                    "",
                    attempt.log(),
                    keyNote);
        }
        if (outcome instanceof Refused refused) {
            return new LimelightUploaded(
                    LimelightUploaded.Kind.REFUSED,
                    refused.step().id(),
                    "",
                    "",
                    page,
                    refused.httpStatus(),
                    List.copyOf(refused.flags().keySet()),
                    refused.message().orElse(""),
                    attempt.log(),
                    keyNote);
        }
        LimelightUploadOutcome.Failed failed = (LimelightUploadOutcome.Failed) outcome;
        return new LimelightUploaded(
                LimelightUploaded.Kind.FAILED,
                failed.step().id(),
                "",
                "",
                page,
                0,
                List.of(),
                failed.reason(),
                attempt.log(),
                keyNote);
    }

    private synchronized CredentialStores.Choice choice() {
        if (chosen == null) {
            chosen = Objects.requireNonNull(chooser.get(), "the credential store chosen");
        }
        return chosen;
    }

    private CredentialStore store() {
        return choice().store();
    }

    /**
     * A destination the workflow checked, as the section shows it: sent at most once.
     *
     * @param destination the workflow's destination
     * @param sent whether it was sent
     */
    private record Prepared(LimelightDestination destination, AtomicBoolean sent)
            implements LimelightUploadPreview {

        Prepared(LimelightDestination destination) {
            this(destination, new AtomicBoolean());
        }

        boolean take() {
            return sent.compareAndSet(false, true);
        }

        @Override
        public RunId run() {
            return destination.runId();
        }

        @Override
        public String server() {
            return destination.server().toString();
        }

        @Override
        public int projectId() {
            return destination.projectId();
        }

        @Override
        public String projectPage() {
            return destination.projectPage().toString();
        }

        @Override
        public Path file() {
            return destination.file();
        }

        @Override
        public long size() {
            return destination.size();
        }

        @Override
        public String sha256() {
            return destination.sha256();
        }
    }
}
