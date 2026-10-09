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

package org.cometgui.results.export;

import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

/**
 * Copies byte ranges of a file, opened for reading only, to a stream -- each range verbatim, the
 * ranges in increasing order -- through one buffer, so that copying a million short rows costs a
 * read per buffer rather than one per row. Not thread-safe.
 */
final class ForwardCopier {

    private static final int BUFFER_BYTES = 1 << 16;

    private final Path file;
    private final FileChannel channel;
    private final ByteBuffer buffer = ByteBuffer.allocate(BUFFER_BYTES);
    private long bufferStart;

    /**
     * A copier over an open channel.
     *
     * @param file the file, named in a failure
     * @param channel the file, opened for reading; the caller closes it
     */
    ForwardCopier(Path file, FileChannel channel) {
        this.file = file;
        this.channel = channel;
        buffer.limit(0);
    }

    /**
     * Copies the bytes {@code [from, to)}.
     *
     * @param from the first byte's offset; not before the end of the previous range copied
     * @param to one past the last byte's offset
     * @param out where they go
     * @throws IOException if the file cannot be read or ends before {@code to}
     */
    void copy(long from, long to, OutputStream out) throws IOException {
        long at = from;
        while (at < to) {
            long bufferEnd = bufferStart + buffer.limit();
            if (at < bufferStart || at >= bufferEnd) {
                fill(at);
                bufferEnd = bufferStart + buffer.limit();
            }
            int offset = (int) (at - bufferStart);
            int count = (int) (Math.min(to, bufferEnd) - at);
            out.write(buffer.array(), offset, count);
            at += count;
        }
    }

    private void fill(long at) throws IOException {
        buffer.clear();
        int read;
        do {
            read = channel.read(buffer, at);
        } while (read == 0);
        if (read < 0) {
            throw new EOFException(
                    "The Percolator result table "
                            + file
                            + " ended at byte "
                            + at
                            + " while being exported");
        }
        buffer.flip();
        bufferStart = at;
    }
}
