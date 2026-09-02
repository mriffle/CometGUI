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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/**
 * Reads one container format, entry by entry, and does nothing else.
 *
 * <p>A reader describes what it finds; it never decides where anything goes and it cannot write.
 * That is the shape {@code R-SEC-05} asks for: the per-format code is separate, because a zip and a
 * cpio have nothing in common, but every entry any of them produces is placed by the single {@link
 * ExtractionGuard}. {@code GuardBypassStructureTest} proves it by reading the compiled classes
 * rather than by trusting this paragraph.
 *
 * <p>Readers are sequential. {@link #content()} is valid only until the next call to {@link
 * #next()}, and closing the stream it returns does not close the reader.
 */
interface ArchiveReader extends Closeable {

    /**
     * Advances to the next entry.
     *
     * @return the entry, or {@code null} when the container is exhausted
     * @throws IOException if the container cannot be read
     * @throws ExtractionRejectedException if the container's structure is malformed
     */
    ArchiveEntry next() throws IOException;

    /**
     * The bytes of the entry {@link #next()} last returned.
     *
     * @return a stream over the entry's content, empty for a directory or a symbolic link
     * @throws IOException if the content cannot be read
     */
    InputStream content() throws IOException;
}
