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

package org.cometgui.domain.run;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Where a <em>derived</em> run's Comet results come from: the earlier run of the same project whose
 * recorded search it reuses instead of running Comet again -- the compatible-version Percolator
 * rerun (Phase 09, design decision P9-11; the specification's <em>Stage reruns</em>).
 *
 * <p>A derived run executes only the Percolator steps. Its merged PIN is a byte copy of the source
 * run's, taken only after the source's file was re-hashed and found equal to the SHA-256 the source
 * recorded ({@code R-RUN-02}); this record is how the derived run says so, once, in its immutable
 * identity.
 *
 * <h2>What the constructor holds true</h2>
 *
 * <ul>
 *   <li>{@code provenance} is the source run's manifest, {@code provenance/provenance.json},
 *       recorded by its path relative to the <em>source</em> run's directory: the digests are those
 *       of the manifest at the moment the derived run was made, so a reader can tell whether the
 *       source has been retried since.
 *   <li>{@code mergedPin} is the derived run's <em>own</em> copy, {@code inputs/pin/merged.pin},
 *       relative to the derived run's directory. Its SHA-256 is the one the source recorded for its
 *       merged PIN, because the copy is refused unless the two are equal.
 *   <li>{@code created} is truncated to milliseconds, as {@code run.json} records it, so that
 *       {@link #directoryName()} names the source's directory.
 * </ul>
 *
 * @param runId the source run's identifier
 * @param created the source run's creation time, which with {@code runId} names its directory
 * @param provenance the source run's {@code provenance/provenance.json} when the run was derived
 * @param mergedPin the derived run's copy of the source's merged PIN
 */
public record RunDerivation(
        RunId runId, Instant created, ArchivedFile provenance, ArchivedFile mergedPin) {

    /**
     * Validates the record.
     *
     * @throws NullPointerException naming a component that is {@code null}
     * @throws IllegalArgumentException if {@code provenance} is not {@code
     *     provenance/provenance.json} or {@code mergedPin} is not {@code inputs/pin/merged.pin},
     *     naming the path
     */
    public RunDerivation {
        Objects.requireNonNull(runId, "runId");
        created = Objects.requireNonNull(created, "created").truncatedTo(ChronoUnit.MILLIS);
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(mergedPin, "mergedPin");
        if (!provenance.path().equals(RunLayout.provenanceJsonRelativePath())) {
            throw new IllegalArgumentException(
                    "a derived run records its source's manifest at "
                            + RunLayout.provenanceJsonRelativePath()
                            + ", but it was recorded at \""
                            + provenance.path()
                            + "\"");
        }
        if (!mergedPin.path().equals(RunLayout.mergedPinRelativePath())) {
            throw new IllegalArgumentException(
                    "a derived run records its merged PIN at "
                            + RunLayout.mergedPinRelativePath()
                            + ", but it was recorded at \""
                            + mergedPin.path()
                            + "\"");
        }
    }

    /**
     * The source run's directory name, under the project's {@code runs/}.
     *
     * @return for example {@code 20260828T231500Z-run-0001}
     */
    public String directoryName() {
        return RunLayout.directoryName(created, runId);
    }
}
