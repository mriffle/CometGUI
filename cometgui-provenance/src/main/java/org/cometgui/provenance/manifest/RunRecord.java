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

package org.cometgui.provenance.manifest;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.run.RunId;

/**
 * The run itself: which run, in which project, in what state, and between which two instants.
 *
 * <p>The identifier is a {@link RunId} rather than a string. That type already refuses anything
 * that would be unsafe or ambiguous as a path segment on Linux, macOS and Windows alike, and the
 * run identifier in the manifest has to be the same identifier that names the run's directory on
 * disk -- it is what a scientist quotes when asking why two searches disagreed. A {@code String}
 * here would let a manifest carry an identifier no run directory could ever have.
 *
 * <p><strong>The end instant is absent while the run is in progress, and that is the
 * point.</strong> {@code R-PROV-05} requires a crash to leave useful history, which means a
 * manifest is a document that exists <em>during</em> a run and not only after one. A record with
 * {@link ProvenanceStatus#RUNNING} and no end is the honest description of a run that is still
 * going, and of one that was interrupted and never wrote its end; a sentinel end timestamp would
 * make those two indistinguishable from a run that finished.
 *
 * <p>Duration is derived from the two instants for the reason given on {@link ExecutionRecord} -- a
 * stored duration is a third number that can contradict the other two -- and is absent exactly when
 * the end is.
 *
 * @param runId the identifier that also names the run's directory
 * @param projectId the project the run belongs to
 * @param status the state the run is in, or was left in
 * @param start when the run began
 * @param end when it finished, absent while it is still running; never before {@code start}
 */
public record RunRecord(
        RunId runId,
        String projectId,
        ProvenanceStatus status,
        Instant start,
        Optional<Instant> end) {

    /**
     * Validates the record.
     *
     * @throws NullPointerException if any component is {@code null}
     * @throws IllegalArgumentException if {@code projectId} is blank, or if a present {@code end}
     *     is before {@code start} -- with a message naming the field and the rejected value
     */
    public RunRecord {
        Objects.requireNonNull(runId, "runId");
        projectId = ManifestChecks.requireNonBlank(projectId, "projectId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        end.ifPresent(finished -> ManifestChecks.requireNotBefore(start, finished));
    }

    /**
     * How long the run took, computed from {@link #start()} and {@link #end()}.
     *
     * <p>Absent while the run has no end. Never negative, because the constructor rejects an end
     * before a start.
     *
     * @return the elapsed time, or empty while the run is still in progress
     */
    public Optional<Duration> duration() {
        return end.map(finished -> Duration.between(start, finished));
    }
}
