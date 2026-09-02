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

package org.cometgui.install.cache;

import java.util.Objects;
import org.cometgui.domain.ports.FileHashes;

/**
 * One installed file as the completion marker records it: where it is, how long it is, and what it
 * hashes to.
 *
 * <p>This is the second half of {@code R-TOOL-04}. A marker that only said "the install finished"
 * would make a corrupted or swapped cache entry indistinguishable from a good one; a marker that
 * carries these makes {@link ToolCache#verify} able to say the entry is no longer what was
 * installed.
 *
 * <p>The path is relative to the tool's install directory and is spelled with {@code /} separators
 * on every platform, so a marker written on one machine reads the same on another -- the same
 * reason {@code org.cometgui.install.registry.ArchiveMember} spells its install paths that way.
 *
 * @param path where the file is, relative to the tool's install directory
 * @param sizeBytes its length, checked before the digest because a length mismatch is cheap to see
 * @param hashes its MD5 and SHA-256; {@code R-SEC-02} makes the SHA-256 the trust mechanism and the
 *     MD5 a provenance record, never the other way round
 */
public record RecordedFile(String path, long sizeBytes, FileHashes hashes) {

    /**
     * Validates the record.
     *
     * @throws NullPointerException if the path or the digests are {@code null}
     * @throws IllegalArgumentException if the path is blank or the size is negative, naming the
     *     field and the rejected value
     */
    public RecordedFile {
        Objects.requireNonNull(path, "path");
        if (path.isBlank()) {
            throw new IllegalArgumentException(
                    "a recorded file's path must not be blank, but was: \"" + path + "\"");
        }
        Objects.requireNonNull(hashes, "hashes");
        if (sizeBytes < 0) {
            throw new IllegalArgumentException(
                    "a recorded file's sizeBytes must not be negative, but \""
                            + path
                            + "\" was recorded as "
                            + sizeBytes);
        }
    }
}
