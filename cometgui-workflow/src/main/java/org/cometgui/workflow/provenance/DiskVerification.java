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

package org.cometgui.workflow.provenance;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The result of "Verify against disk": every file a sealed record lists, hashed again and compared
 * with its record.
 *
 * <p>Inputs, outputs, archived logs and tool executables, each hashed with the product's one {@link
 * org.cometgui.domain.ports.HashService} with its cache bypassed ({@code R-PROV-02}: "when in
 * doubt, rehash"), so a result is always about the bytes on disk now and never about a remembered
 * digest.
 *
 * @param checks one entry per file record, log and distinct tool executable, in that order
 * @param complete {@code false} if the verification was cancelled before every file was checked
 */
public record DiskVerification(List<Check> checks, boolean complete) {

    /**
     * Copies the components.
     *
     * @throws NullPointerException if {@code checks} is {@code null}
     */
    public DiskVerification {
        checks = List.copyOf(checks);
    }

    /**
     * The checks.
     *
     * @return an unmodifiable copy, in order
     */
    @Override
    public List<Check> checks() {
        return List.copyOf(checks);
    }

    /**
     * How many checks ended with an outcome.
     *
     * @param outcome the outcome
     * @return the number of checks with it
     */
    public long count(DiskOutcome outcome) {
        Objects.requireNonNull(outcome, "outcome");
        return checks.stream().filter(check -> check.outcome() == outcome).count();
    }

    /**
     * Whether every file was checked and every one matches.
     *
     * @return {@code true} only for a complete verification with no finding
     */
    public boolean allMatch() {
        return complete && count(DiskOutcome.MATCHES) == checks.size();
    }

    /**
     * One file, hashed again.
     *
     * @param kind what the record lists it as: {@code input}, {@code output}, {@code log} or {@code
     *     tool}
     * @param role the file's role, the log's stage or the tool's name
     * @param path the recorded path, for display
     * @param location the recorded path, to act on; never shown
     * @param outcome what was found
     * @param recordedMd5 the MD5 the record gives
     * @param recordedSha256 the SHA-256 the record gives
     * @param foundMd5 the MD5 computed now, when the file could be read
     * @param foundSha256 the SHA-256 computed now, when the file could be read
     * @param problem why the file could not be read, when it could not, redacted
     */
    public record Check(
            String kind,
            String role,
            String path,
            Path location,
            DiskOutcome outcome,
            String recordedMd5,
            String recordedSha256,
            Optional<String> foundMd5,
            Optional<String> foundSha256,
            Optional<String> problem) {

        /**
         * Validates the components.
         *
         * @throws NullPointerException if any component is {@code null}
         */
        public Check {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(location, "location");
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(recordedMd5, "recordedMd5");
            Objects.requireNonNull(recordedSha256, "recordedSha256");
            Objects.requireNonNull(foundMd5, "foundMd5");
            Objects.requireNonNull(foundSha256, "foundSha256");
            Objects.requireNonNull(problem, "problem");
        }
    }
}
