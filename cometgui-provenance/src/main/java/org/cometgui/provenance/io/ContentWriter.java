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

package org.cometgui.provenance.io;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Produces the bytes of one document, on demand, into a stream someone else owns.
 *
 * <p>This is the shape {@link AtomicDocumentWriter} needs and the shape a serialiser already has.
 * The alternative -- handing the writer a finished {@code byte[]} -- would force the whole
 * provenance manifest into memory before any of it could be written, and the manifest grows with
 * the number of spectrum files in a run. A callback keeps a large document streaming while still
 * letting the writer own the file: the writer opens it, the callback fills it, the writer syncs and
 * renames it.
 *
 * <p><strong>The stream is not yours to close.</strong> {@link AtomicDocumentWriter} closes it, and
 * has to, because the bytes must be forced to the disk <em>before</em> the file is renamed into
 * place and a closed channel cannot be forced. Closing it here would break that ordering. Flushing
 * it is harmless; the writer flushes anyway.
 *
 * <p><strong>Throwing is a supported outcome, not a failure of contract.</strong> A serialiser that
 * discovers halfway through that it cannot continue should throw. {@link AtomicDocumentWriter}
 * treats any {@code IOException}, {@code RuntimeException} or {@code Error} out of {@link
 * #writeTo(OutputStream)} as an abandoned write: it removes the temporary file and leaves the
 * target byte-for-byte as it found it. That is the whole reason the callback writes to a temporary
 * file rather than to the target.
 */
@FunctionalInterface
public interface ContentWriter {

    /**
     * Writes the complete document to the given stream.
     *
     * <p>Called exactly once per {@code AtomicDocumentWriter.write}. The stream is positioned at
     * byte zero of an empty file and must not be closed.
     *
     * @param out where the document's bytes go; owned and closed by the caller
     * @throws IOException if the document cannot be produced or cannot be written; the exception
     *     reaches the caller of {@code AtomicDocumentWriter.write} unchanged, and the target file
     *     is left untouched
     */
    void writeTo(OutputStream out) throws IOException;
}
