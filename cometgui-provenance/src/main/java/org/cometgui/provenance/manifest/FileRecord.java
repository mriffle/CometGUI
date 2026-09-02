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

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import org.cometgui.domain.ports.FileHashes;

/**
 * One file a run read or wrote, recorded in full.
 *
 * <p>The specification's hash requirements name exactly what has to be here: "for every regular
 * input and output file used or created by a run, record the canonical path at time of run;
 * role/type; byte size; modification timestamp; MD5; and SHA-256". This record carries all six,
 * plus the two things that make the six interpretable -- which side of the run the file was on, and
 * whether it is whole.
 *
 * <p><strong>Why a status belongs on a file.</strong> {@code R-PROV-01}'s last sentence is that
 * partial files from failed or cancelled stages "may be hashed but shall be marked {@code
 * partial}", and {@code AC-PRV-06} requires a failed or cancelled run to keep useful provenance.
 * Both need this field. A truncated output that is recorded like any other is worse than no record
 * at all: its hash is real, it verifies, and it describes a file that is not a result. So a partial
 * file is representable here and is marked as such, and the report and the UI can say so rather
 * than presenting eight megabytes of a search result as the search result.
 *
 * <p>The size and the modification timestamp are recorded beside the digests because they are what
 * the input-hash cache ({@code R-PROV-02}) is keyed on. Keeping them in the manifest means a later
 * verification can tell "this file changed" from "this file was hashed wrongly", which the digests
 * alone cannot distinguish.
 *
 * @param direction whether the run read this file or produced it
 * @param role what the file is to the run -- {@code spectra}, {@code fasta}, {@code pepxml} and so
 *     on; free text rather than an enum, because a role is added by whichever stage produces it and
 *     the set is not closed
 * @param path the canonical, absolute path the file had at the time of the run
 * @param sizeBytes the file's length in bytes, never negative
 * @param modifiedAt the file's last-modified timestamp at the time of the run
 * @param hashes the MD5 and SHA-256 of the content that was actually read or written
 * @param status whether this file is whole; {@link ProvenanceStatus#PARTIAL} is the marking {@code
 *     R-PROV-01} requires
 */
public record FileRecord(
        FileDirection direction,
        String role,
        Path path,
        long sizeBytes,
        Instant modifiedAt,
        FileHashes hashes,
        ProvenanceStatus status) {

    /**
     * Validates the record.
     *
     * @throws NullPointerException if any reference component is {@code null}
     * @throws IllegalArgumentException if {@code role} is blank, if {@code path} is relative, or if
     *     {@code sizeBytes} is negative -- with a message naming the field and the rejected value
     */
    public FileRecord {
        Objects.requireNonNull(direction, "direction");
        role = ManifestChecks.requireNonBlank(role, "role");
        path = ManifestChecks.requireAbsolute(path, "path");
        if (sizeBytes < 0) {
            throw new IllegalArgumentException(
                    "sizeBytes must not be negative, but was: " + sizeBytes);
        }
        Objects.requireNonNull(modifiedAt, "modifiedAt");
        Objects.requireNonNull(hashes, "hashes");
        Objects.requireNonNull(status, "status");
    }
}
