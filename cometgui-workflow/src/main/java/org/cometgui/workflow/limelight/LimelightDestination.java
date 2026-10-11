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

import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.tools.limelight.LimelightServer;

/**
 * Where an upload would go and what it would send, checked and shown to the scientist before
 * anything is sent ({@code R-SEC-04}: "shall show the exact destination server and project before
 * sending"; phase 12, design decisions P12-10 and P12-15).
 *
 * <p>Only {@link LimelightUploads#destination} makes one, after checking that the run has ended,
 * that the file is one of the run's own recorded conversions, and that its SHA-256 is still the one
 * recorded. The interface shows every accessor below; the scientist's explicit confirmation is
 * {@link #confirm()}, which is the one way to obtain the {@link LimelightUploadConfirmation} that
 * {@link LimelightUploads#upload} requires. There is no other way to start an upload.
 */
public final class LimelightDestination {

    private final RunLayout run;
    private final RunId runId;
    private final LimelightServer server;
    private final int projectId;
    private final Path file;
    private final long size;
    private final String md5;
    private final String sha256;

    LimelightDestination(
            RunLayout run,
            RunId runId,
            LimelightServer server,
            int projectId,
            Path file,
            long size,
            String md5,
            String sha256) {
        this.run = Objects.requireNonNull(run, "run");
        this.runId = Objects.requireNonNull(runId, "runId");
        this.server = Objects.requireNonNull(server, "server");
        this.projectId = LimelightServer.checkedProjectId(projectId);
        this.file = Objects.requireNonNull(file, "file");
        this.size = size;
        this.md5 = Objects.requireNonNull(md5, "md5");
        this.sha256 = Objects.requireNonNull(sha256, "sha256");
    }

    /**
     * The scientist's explicit confirmation of exactly this destination: one upload, once.
     *
     * @return a confirmation that {@link LimelightUploads#upload} accepts once
     */
    public LimelightUploadConfirmation confirm() {
        return new LimelightUploadConfirmation(this);
    }

    /**
     * The run whose file is sent.
     *
     * @return its layout
     */
    public RunLayout run() {
        return run;
    }

    /**
     * The run's id.
     *
     * @return it
     */
    public RunId runId() {
        return runId;
    }

    /**
     * The server, normalised: exactly what the scientist is shown.
     *
     * @return the server
     */
    public LimelightServer server() {
        return server;
    }

    /**
     * The project's id.
     *
     * @return it, positive
     */
    public int projectId() {
        return projectId;
    }

    /**
     * Where the scientist will see the import ({@code <server>/d/pg/project/<id>}).
     *
     * @return the page
     */
    public URI projectPage() {
        return server.projectPage(projectId);
    }

    /**
     * The Limelight XML file that would be sent.
     *
     * @return its absolute path
     */
    public Path file() {
        return file;
    }

    /**
     * Its size.
     *
     * @return bytes
     */
    public long size() {
        return size;
    }

    /**
     * Its MD5, as hashed when the destination was made.
     *
     * @return the digest
     */
    public String md5() {
        return md5;
    }

    /**
     * Its SHA-256, as hashed when the destination was made and as its conversion recorded it.
     *
     * @return the digest
     */
    public String sha256() {
        return sha256;
    }

    /**
     * The destination in one line, as the upload log opens with it.
     *
     * @return for example {@code https://host/limelight, project 7
     *     (https://host/limelight/d/pg/project/7)}
     */
    @Override
    public String toString() {
        return server + ", project " + projectId + " (" + projectPage() + ")";
    }
}
