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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link ForwardCopier}: ranges copied verbatim across its 64 KiB buffer's edges. */
class ForwardCopierTest {

    @TempDir private Path work;

    @Test
    @DisplayName("ranges inside, across and beyond one buffer are copied byte for byte")
    void rangesAcrossBufferEdges() throws IOException {
        byte[] bytes = new byte[200_000];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i * 31 + i / 251);
        }
        Path file = Files.write(work.resolve("bytes"), bytes);
        long[][] ranges = {
            {0, 10},
            {10, 10},
            {10, 65_530},
            {65_530, 65_540},
            {65_540, 131_072},
            {131_072, 131_073},
            {140_000, 199_999},
            {199_999, 200_000}
        };
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            ForwardCopier copier = new ForwardCopier(file, channel);
            for (long[] range : ranges) {
                copier.copy(range[0], range[1], out);
                expected.write(bytes, (int) range[0], (int) (range[1] - range[0]));
            }
            copier.copy(5, 70_000, out);
            expected.write(bytes, 5, 70_000 - 5);
        }
        assertArrayEquals(expected.toByteArray(), out.toByteArray());
    }

    @Test
    @DisplayName(
            "a buffer is read once: a thousand short rows inside it, and its first row copied"
                    + " again, cost no further read; crossing its edge costs one")
    void oneReadPerBuffer() throws IOException {
        byte[] bytes = new byte[100_000];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i * 7 + i / 13);
        }
        Path file = Files.write(work.resolve("rows"), bytes);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        try (CountingChannel channel =
                new CountingChannel(FileChannel.open(file, StandardOpenOption.READ))) {
            ForwardCopier copier = new ForwardCopier(file, channel);
            for (int row = 0; row < 1000; row++) {
                copier.copy(row * 60L, row * 60L + 60, out);
                expected.write(bytes, row * 60, 60);
            }
            assertEquals(1, channel.reads, "reads for 1000 rows inside the first buffer");
            copier.copy(0, 60, out);
            expected.write(bytes, 0, 60);
            assertEquals(1, channel.reads, "reads after the buffer's own first row again");
            copier.copy(60_000, 70_000, out);
            expected.write(bytes, 60_000, 10_000);
            assertEquals(2, channel.reads, "reads after a range across the buffer's edge");
        }
        assertArrayEquals(expected.toByteArray(), out.toByteArray());
    }

    @Test
    @DisplayName("a range past the end of the file is refused, naming the file and the offset")
    void pastTheEnd() throws IOException {
        Path file = Files.write(work.resolve("short"), new byte[100]);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            ForwardCopier copier = new ForwardCopier(file, channel);
            EOFException ended = assertThrows(EOFException.class, () -> copier.copy(90, 110, out));
            assertEquals(
                    "The Percolator result table "
                            + file
                            + " ended at byte 100 while being exported",
                    ended.getMessage());
        }
        assertArrayEquals(new byte[10], out.toByteArray(), "the bytes before the end");
    }

    /** A file channel that counts its positional reads, the ones {@link ForwardCopier} makes. */
    private static final class CountingChannel extends FileChannel {

        private final FileChannel file;
        private int reads;

        CountingChannel(FileChannel file) {
            this.file = file;
        }

        @Override
        public int read(ByteBuffer destination, long position) throws IOException {
            reads++;
            return file.read(destination, position);
        }

        @Override
        public int read(ByteBuffer destination) throws IOException {
            throw new AssertionError("ForwardCopier reads by position only");
        }

        @Override
        public long read(ByteBuffer[] destinations, int offset, int length) {
            throw new AssertionError("ForwardCopier reads by position only");
        }

        @Override
        public int write(ByteBuffer source) {
            throw new AssertionError("read only");
        }

        @Override
        public long write(ByteBuffer[] sources, int offset, int length) {
            throw new AssertionError("read only");
        }

        @Override
        public int write(ByteBuffer source, long position) {
            throw new AssertionError("read only");
        }

        @Override
        public long position() throws IOException {
            return file.position();
        }

        @Override
        public FileChannel position(long newPosition) throws IOException {
            file.position(newPosition);
            return this;
        }

        @Override
        public long size() throws IOException {
            return file.size();
        }

        @Override
        public FileChannel truncate(long size) {
            throw new AssertionError("read only");
        }

        @Override
        public void force(boolean metaData) {
            throw new AssertionError("read only");
        }

        @Override
        public long transferTo(long position, long count, WritableByteChannel target) {
            throw new AssertionError("not used");
        }

        @Override
        public long transferFrom(ReadableByteChannel source, long position, long count) {
            throw new AssertionError("read only");
        }

        @Override
        public MappedByteBuffer map(MapMode mode, long position, long size) {
            throw new AssertionError("not used");
        }

        @Override
        public FileLock lock(long position, long size, boolean shared) {
            throw new AssertionError("not used");
        }

        @Override
        public FileLock tryLock(long position, long size, boolean shared) {
            throw new AssertionError("not used");
        }

        @Override
        protected void implCloseChannel() throws IOException {
            file.close();
        }
    }
}
