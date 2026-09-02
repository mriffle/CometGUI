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
import java.util.Objects;
import org.cometgui.domain.ports.FileHashes;

/**
 * One archived process log -- where it was written, and what it contained.
 *
 * <p>The specification requires "stdout and stderr log paths and checksums" for every tool
 * invocation. The checksum is the half that is easy to leave out and the half that makes the log
 * evidence: a path alone says where a file was, which is worth nothing once anyone can edit it. A
 * scientist re-reading a run can hash the archived log and see for themselves that it is the output
 * the run recorded, and not a file that has since been trimmed, rotated or reconstructed.
 *
 * <p>Not a {@link FileRecord}, deliberately. A log is not an input or an output of the scientific
 * workflow; it has no role in the search, it is not something a downstream tool consumes, and
 * putting it in the file list would inflate the input and output counts the provenance summary
 * reports. It is a property of the execution that produced it, which is where {@link
 * ExecutionRecord} keeps it.
 *
 * @param path the absolute path of the archived log file
 * @param hashes the MD5 and SHA-256 of that file's contents
 */
public record LogRecord(Path path, FileHashes hashes) {

    /**
     * Validates the record.
     *
     * @throws NullPointerException if {@code path} or {@code hashes} is {@code null}
     * @throws IllegalArgumentException if {@code path} is relative, with a message naming the field
     *     and the rejected value
     */
    public LogRecord {
        path = ManifestChecks.requireAbsolute(path, "path");
        Objects.requireNonNull(hashes, "hashes");
    }
}
