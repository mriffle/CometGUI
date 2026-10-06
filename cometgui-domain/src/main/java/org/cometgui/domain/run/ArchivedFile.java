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

import java.util.Objects;
import org.cometgui.domain.ports.FileHashes;

/**
 * A file the run wrote into its own directory and records by a path relative to it -- the canonical
 * {@code comet.params} under {@code parameters/}.
 *
 * <p>Relative, unlike {@link RecordedInput}: the file belongs to the run, so the record stays true
 * when the project directory is moved or copied. The path uses {@code /} on every platform, and may
 * not climb out of the run.
 *
 * @param path the path relative to the run directory, {@code /}-separated
 * @param size the file's length in bytes
 * @param hashes the file's MD5 and SHA-256, as the hash service computed them over the file on disk
 */
public record ArchivedFile(String path, long size, FileHashes hashes) {

    /**
     * Validates the record.
     *
     * @throws NullPointerException naming a component that is {@code null}
     * @throws IllegalArgumentException if the path is not a relative, {@code /}-separated path of
     *     ordinary segments, or the size is negative
     */
    public ArchivedFile {
        Objects.requireNonNull(path, "path");
        if (path.isEmpty()
                || path.startsWith("/")
                || path.endsWith("/")
                || path.indexOf('\\') >= 0
                || path.indexOf(':') >= 0) {
            throw new IllegalArgumentException(
                    "an archived file's path must be relative to the run and use '/': \""
                            + path
                            + "\"");
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException(
                        "an archived file's path must not contain an empty, '.' or '..' segment: \""
                                + path
                                + "\"");
            }
        }
        if (size < 0) {
            throw new IllegalArgumentException("a file size cannot be negative, but was " + size);
        }
        Objects.requireNonNull(hashes, "hashes");
    }
}
