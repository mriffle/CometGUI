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

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.cometgui.domain.ports.FileHashes;

/**
 * One input file as a run records it, without copying it ({@code R-RUN-03}): its canonical path,
 * its size, its modification time, and its MD5 and SHA-256.
 *
 * <p>"Large input spectra and FASTA files shall not be copied into the project by default. The
 * project shall store canonical path, size, timestamps, MD5 and SHA-256." This is that record. The
 * hashes are supplied by the caller, from the one {@code HashService}; this type neither reads nor
 * hashes a file.
 *
 * <p>{@code modified} is truncated to milliseconds, the precision {@code run.json} records.
 *
 * @param path the file's canonical path: absolute and normalised, as {@code Path.toRealPath()}
 *     returns it
 * @param size the file's length in bytes
 * @param modified the file's last-modified time
 * @param hashes the file's MD5 and SHA-256
 */
public record RecordedInput(Path path, long size, Instant modified, FileHashes hashes) {

    /**
     * Validates the record.
     *
     * @throws NullPointerException naming a component that is {@code null}
     * @throws IllegalArgumentException if the path is not absolute or not normalised, or the size
     *     is negative
     */
    public RecordedInput {
        Objects.requireNonNull(path, "path");
        if (!path.isAbsolute() || !path.equals(path.normalize())) {
            throw new IllegalArgumentException(
                    "an input's recorded path must be absolute and normalised: " + path);
        }
        if (size < 0) {
            throw new IllegalArgumentException("a file size cannot be negative, but was " + size);
        }
        modified = Objects.requireNonNull(modified, "modified").truncatedTo(ChronoUnit.MILLIS);
        Objects.requireNonNull(hashes, "hashes");
    }
}
