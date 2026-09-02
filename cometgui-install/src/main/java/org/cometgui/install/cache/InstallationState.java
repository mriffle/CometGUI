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

/**
 * What looking at one tool directory in the cache found.
 *
 * <p>Exactly one value means the tool may be used, and every other value is a reason it may not.
 * That asymmetry is {@code R-TOOL-04} written as a type: <em>"a tool directory shall be considered
 * installed only when a completion marker written last is present and its recorded checksums
 * match"</em>. A boolean would let a caller write {@code if (!failed())} and lose which half of the
 * rule was broken -- and the halves have different meanings. {@link #NO_MARKER} is an install that
 * was interrupted; {@link #CHECKSUM_MISMATCH} is an install that finished and has since been
 * corrupted, swapped or edited, which is a different thing to tell a scientist.
 *
 * <p>None of them is repaired in place. Every value but {@link #INSTALLED} and {@link #NOT_PRESENT}
 * means the directory is discarded whole and rebuilt.
 */
public enum InstallationState {

    /** There is no directory for this tool, version and platform. Nothing has been attempted. */
    NOT_PRESENT,

    /**
     * The directory exists and holds no completion marker.
     *
     * <p>The signature of an interrupted install: the payload was moved into the cache and the
     * process stopped before the marker was written. The marker is written last precisely so that
     * this case is detectable rather than looking like a finished install.
     */
    NO_MARKER,

    /** The marker exists and is not a marker this version of CometGUI can read. */
    MARKER_UNREADABLE,

    /**
     * The marker is readable and describes a different tool, version or platform from the directory
     * it was found in.
     *
     * <p>Distinct from {@link #MARKER_UNREADABLE} because the document is valid: something moved or
     * copied a cache entry, and the entry is not what its path claims.
     */
    MARKER_DESCRIBES_ANOTHER_ARTEFACT,

    /**
     * The marker is readable and the directory holds a different number of files from the one it
     * recorded.
     *
     * <p>The cheap half of the completeness check, and the only one that covers a whole-archive
     * install: the manifest pins a digest for the executable and for every companion member, and
     * for nothing else, so for a 222-entry archive this count is what notices that files have gone.
     */
    CONTENT_COUNT_MISMATCH,

    /** A file the marker records is not in the directory. */
    FILE_MISSING,

    /**
     * A file the marker records is there and is not the file that was installed.
     *
     * <p>The second half of {@code R-TOOL-04}, and the half that is easy to forget. A marker whose
     * recorded digest no longer matches the bytes on disk makes the entry <em>not installed</em>:
     * an executable that has been swapped is not the executable that was probed, whatever the
     * marker says about it.
     */
    CHECKSUM_MISMATCH,

    /** The marker is present, and every file it records is present with the digest it records. */
    INSTALLED;

    /**
     * Whether the tool may be offered and launched.
     *
     * @return {@code true} only for {@link #INSTALLED}
     */
    public boolean installed() {
        return this == INSTALLED;
    }
}
