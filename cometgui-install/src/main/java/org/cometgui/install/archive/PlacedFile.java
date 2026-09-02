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

package org.cometgui.install.archive;

import java.util.Objects;

/**
 * One thing an extraction put on disk, and where it put it.
 *
 * <p>Reported so that a caller can prove what happened rather than inspect the directory and hope.
 * The path is relative to the destination directory and is the path the guard accepted, which for a
 * named member is the manifest's own string and never the archive's.
 *
 * @param path where it was written, relative to the destination directory, with {@code /}
 *     separators
 * @param type what was created
 * @param sizeBytes the bytes written, or zero for a directory or a symbolic link
 */
public record PlacedFile(String path, ArchiveEntryType type, long sizeBytes) {

    /**
     * Validates the record.
     *
     * @throws NullPointerException if the path or type is {@code null}
     * @throws IllegalArgumentException if the size is negative
     */
    public PlacedFile {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(type, "type");
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must not be negative: " + sizeBytes);
        }
    }
}
