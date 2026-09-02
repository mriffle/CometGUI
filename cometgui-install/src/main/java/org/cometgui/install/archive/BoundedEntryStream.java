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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A view of exactly the next {@code n} bytes of a shared stream, which closing does not close.
 *
 * <p>A tar and a cpio archive are one long stream whose entries are delimited by declared lengths,
 * so an entry's content has to be handed out as a window rather than as a stream of its own. Two
 * properties matter and both are load-bearing.
 *
 * <p><strong>It stops at the declared length.</strong> Reading past the window would read the next
 * entry's header as file content, which is how a truncated or lying archive turns into a file the
 * caller believes came from somewhere it did not.
 *
 * <p><strong>Closing it does not close the underlying stream.</strong> The guard writes entries
 * through try-with-resources, so a plain view would close the whole archive after the first entry.
 *
 * <h2>Why three mutants of this class time out rather than dying</h2>
 *
 * <p>Both {@code read} methods answer a request for bytes with either a byte count or -1, and never
 * with zero: that is the {@link java.io.InputStream} contract, and the decompressors this window
 * feeds rely on it. Mutants that make either method return zero therefore break the contract, and
 * the JDK's own inflater spins on the result inside its own loop -- so PIT records a timeout rather
 * than a kill, and the hang is in a library this project does not own rather than in this class.
 *
 * <p>The one place <em>this</em> code could have spun on such a stream is the guard's copy loop,
 * and it no longer can: {@code ExtractionGuard.transfer} refuses a read that delivers nothing and
 * reports no end, which {@code GuardAccountingTest} proves against a stream that does exactly that.
 */
final class BoundedEntryStream extends FilterInputStream {

    /** Bytes still inside the window. */
    private long remaining;

    /**
     * Creates a window over the next {@code length} bytes.
     *
     * @param in the shared stream
     * @param length how many bytes belong to this entry
     */
    BoundedEntryStream(InputStream in, long length) {
        super(in);
        this.remaining = length;
    }

    @Override
    public int read() throws IOException {
        if (remaining <= 0) {
            return -1;
        }
        int value = in.read();
        if (value >= 0) {
            remaining--;
        }
        return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (remaining <= 0) {
            return -1;
        }
        int wanted = (int) Math.min(length, remaining);
        int read = in.read(buffer, offset, wanted);
        /*
         * The guard is against -1, not against 0: subtracting a zero-length read would be a no-op
         * anyway, so the two forms of this condition are indistinguishable and PIT is right that
         * one of them cannot be killed.  It is written as "> 0" because that is what the sentence
         * means -- take off what was actually delivered.
         */
        if (read > 0) {
            remaining -= read;
        }
        return read;
    }

    @Override
    public int available() throws IOException {
        return (int) Math.min(in.available(), remaining);
    }

    /**
     * Closes the window, not the stream underneath it.
     *
     * <p>Deliberately empty. The archive is closed by its reader.
     */
    @Override
    public void close() {
        // The shared stream outlives this window; the reader owns it.
    }

    /**
     * How many bytes of the window have not been read.
     *
     * @return the bytes left
     */
    long remaining() {
        return remaining;
    }
}
