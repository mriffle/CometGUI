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

package org.cometgui.ui.viewmodel.limelight;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * How an upload ended, as the upload service recorded it (phase 12, design decision P12-15): the
 * server's own words already through the session's one redactor. None of it is, or can carry, the
 * submit key.
 *
 * @param kind submitted, refused by the server, or failed with no answer
 * @param step the protocol step it ended at, by the workflow's name for it
 * @param trackingId the server's {@code uploadKey}, the import's tracking id; empty unless
 *     submitted
 * @param importerSubDir the server's {@code importerSubDir}; empty unless submitted
 * @param projectPage where the project's imports are followed
 * @param httpStatus the HTTP status of a refusal; 0 otherwise
 * @param flags every flag a refusing server set, by its wire name; empty otherwise
 * @param message the server's {@code statusFailErrorMessage} for a refusal, what went wrong for a
 *     failure, empty for a submitted upload or a refusal without a message
 * @param log the upload's own log file
 * @param keyNote what became of the typed key when it was to be kept; empty when it was not
 */
public record LimelightUploaded(
        Kind kind,
        String step,
        String trackingId,
        String importerSubDir,
        String projectPage,
        int httpStatus,
        List<String> flags,
        String message,
        Path log,
        String keyNote) {

    /** How an upload ended. */
    public enum Kind {
        /** The server took the file and queued its import. */
        SUBMITTED,
        /** The server answered and refused. */
        REFUSED,
        /** No answer the protocol defines arrived. */
        FAILED
    }

    /**
     * Requires every component and copies the flags.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public LimelightUploaded {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(trackingId, "trackingId");
        Objects.requireNonNull(importerSubDir, "importerSubDir");
        Objects.requireNonNull(projectPage, "projectPage");
        flags = List.copyOf(Objects.requireNonNull(flags, "flags"));
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(keyNote, "keyNote");
    }
}
