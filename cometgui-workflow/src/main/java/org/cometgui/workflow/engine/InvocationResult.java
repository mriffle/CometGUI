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

package org.cometgui.workflow.engine;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import org.cometgui.provenance.manifest.ProvenanceStatus;

/**
 * How one invocation ended, as the engine recorded it.
 *
 * @param stageId the invocation's stage identifier
 * @param exitCode the process's exit code
 * @param status {@link ProvenanceStatus#COMPLETED} for exit code zero, {@link
 *     ProvenanceStatus#CANCELLED} for a non-zero exit after cancellation was requested, {@link
 *     ProvenanceStatus#FAILED} otherwise
 * @param logFile the stage log the process service wrote
 * @param start when the process started
 * @param end when it ended
 */
public record InvocationResult(
        String stageId,
        int exitCode,
        ProvenanceStatus status,
        Path logFile,
        Instant start,
        Instant end) {

    /**
     * Requires every component.
     *
     * @throws NullPointerException naming a component that is {@code null}
     */
    public InvocationResult {
        Objects.requireNonNull(stageId, "stageId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(logFile, "logFile");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
    }
}
