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

import java.nio.file.Path;
import java.util.Objects;
import org.cometgui.tools.limelight.LimelightUploadOutcome;

/**
 * One upload, done: where it went, how it ended (the server's words already through the one
 * redactor), and its log.
 *
 * @param destination the confirmed destination
 * @param outcome how it ended
 * @param log the upload's own log, {@code logs/limelight-upload.log} or a numbered one after it
 */
public record LimelightUploadAttempt(
        LimelightDestination destination, LimelightUploadOutcome outcome, Path log) {

    /**
     * Checks the attempt.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public LimelightUploadAttempt {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(log, "log");
    }

    /**
     * Whether the server queued the import.
     *
     * @return {@code true} when it did
     */
    public boolean submitted() {
        return outcome instanceof LimelightUploadOutcome.Submitted;
    }
}
