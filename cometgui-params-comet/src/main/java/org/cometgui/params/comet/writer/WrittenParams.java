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

package org.cometgui.params.comet.writer;

import java.nio.file.Path;
import java.util.Objects;
import org.cometgui.domain.ports.FileHashes;

/**
 * A canonical parameter file as it was written to disk, once ({@code R-PARAM-12}): the path to pass
 * to Comet, and the digests of that file's bytes as read back from disk -- the values the
 * provenance record carries. It holds no text, so nothing downstream can regenerate the file.
 *
 * @param path the file written
 * @param hashes its MD5 and SHA-256, computed over the file on disk
 * @param size its length in bytes, as on disk
 */
public record WrittenParams(Path path, FileHashes hashes, long size) {

    /** Validates presence. */
    public WrittenParams {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(hashes, "hashes");
    }
}
