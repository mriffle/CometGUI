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

/**
 * What one entry of an archive is, reduced to the four cases this extractor distinguishes.
 *
 * <p>Every container format spells these differently -- a tar type flag, a cpio mode, a zip
 * external attribute -- and the readers translate into this one vocabulary so that the guard
 * applies one rule to all of them rather than one rule per format.
 */
public enum ArchiveEntryType {

    /** A regular file, the only kind whose bytes are written. */
    FILE,

    /** A directory, created empty. */
    DIRECTORY,

    /** A symbolic link, created only when its target resolves inside the destination. */
    SYMLINK,

    /**
     * A hard link, device node, socket, FIFO or anything else. Always refused: a hard link is a
     * second name for a file that may be anywhere on the host, which is a traversal that carries no
     * {@code ..} to notice.
     */
    OTHER
}
