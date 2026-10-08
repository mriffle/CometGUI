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

package org.cometgui.results.filtering.store;

import java.io.Closeable;
import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.zip.CRC32C;

/**
 * What the disk-backed store's index and sort files have in common: a fixed header followed by a
 * body, both big-endian, each with its own CRC-32C, written to a temporary file in the index
 * directory and moved into place atomically, so that a reader sees either the old file or the whole
 * new one and never half of one.
 */
final class CheckedFiles {

    /** A big-endian {@code long} at any offset. */
    static final ValueLayout.OfLong LONG =
            ValueLayout.JAVA_LONG_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);

    /** A big-endian {@code int} at any offset. */
    static final ValueLayout.OfInt INT =
            ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);

    /** A big-endian {@code double} at any offset. */
    static final ValueLayout.OfDouble DOUBLE =
            ValueLayout.JAVA_DOUBLE_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);

    private static final long CRC_SLICE = 1L << 28;

    private CheckedFiles() {}

    /**
     * The CRC-32C of part of a segment.
     *
     * @param segment the segment
     * @param from the first byte
     * @param to one past the last byte
     * @return the checksum
     */
    static int crc(MemorySegment segment, long from, long to) {
        CRC32C crc = new CRC32C();
        for (long start = from; start < to; start += CRC_SLICE) {
            crc.update(segment.asSlice(start, Math.min(CRC_SLICE, to - start)).asByteBuffer());
        }
        return (int) crc.getValue();
    }

    /**
     * Writes one checked file: the body first, through {@link #body}, then the header, then the
     * move into place. Closing a writer that has not {@linkplain #finish finished} deletes its
     * temporary file, so a failed build leaves nothing behind.
     */
    static final class Writer implements Closeable {

        private static final int CHUNK_BYTES = 1 << 20;

        private final Path target;
        private final Path temporary;
        private final FileChannel channel;
        private final ByteBuffer chunk = ByteBuffer.allocate(CHUNK_BYTES);
        private final CRC32C bodyCrc = new CRC32C();
        private boolean finished;

        /**
         * Starts a file.
         *
         * @param target where the file goes once finished
         * @param headerBytes how many bytes the header takes, before the body
         * @throws IOException if the temporary file cannot be made
         */
        Writer(Path target, int headerBytes) throws IOException {
            this.target = target;
            Path directory =
                    Objects.requireNonNull(
                            target.toAbsolutePath().getParent(), "the directory of " + target);
            this.temporary =
                    Files.createTempFile(directory, String.valueOf(target.getFileName()), ".tmp");
            FileChannel opened = null;
            try {
                opened = FileChannel.open(temporary, StandardOpenOption.WRITE);
                opened.position(headerBytes);
            } catch (IOException | RuntimeException failed) {
                if (opened != null) {
                    opened.close();
                }
                Files.deleteIfExists(temporary);
                throw failed;
            }
            this.channel = opened;
        }

        /**
         * Room in the body for at least some bytes.
         *
         * @param bytes how many bytes the caller is about to put, at most the chunk size
         * @return the buffer to put them into
         * @throws IOException if a full chunk cannot be written
         */
        ByteBuffer body(int bytes) throws IOException {
            if (chunk.remaining() < bytes) {
                flush();
            }
            return chunk;
        }

        /**
         * Writes the header and moves the file into place.
         *
         * @param header the header, with every field but the checksums filled in, positioned at its
         *     end; the body's checksum is put at {@code bodyCrcAt} and the header's own, over every
         *     byte before it, at {@code bodyCrcAt + 4}
         * @param bodyCrcAt where the body's checksum goes in the header
         * @throws IOException if the file cannot be written or moved
         */
        void finish(ByteBuffer header, int bodyCrcAt) throws IOException {
            flush();
            header.putInt(bodyCrcAt, (int) bodyCrc.getValue());
            CRC32C headerCrc = new CRC32C();
            headerCrc.update(header.array(), 0, bodyCrcAt + 4);
            header.putInt(bodyCrcAt + 4, (int) headerCrc.getValue());
            header.clear();
            long at = 0;
            while (header.hasRemaining()) {
                at += channel.write(header, at);
            }
            channel.close();
            Files.move(
                    temporary,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            finished = true;
        }

        @Override
        public void close() throws IOException {
            if (!finished) {
                try {
                    channel.close();
                } finally {
                    Files.deleteIfExists(temporary);
                }
            }
        }

        private void flush() throws IOException {
            chunk.flip();
            bodyCrc.update(chunk.duplicate());
            while (chunk.hasRemaining()) {
                channel.write(chunk);
            }
            chunk.clear();
        }
    }
}
