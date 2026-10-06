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

package org.cometgui.workflow.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * A {@link FileChannel} over a byte array that records the operations made on it, after {@code
 * FileSystemDurabilityTest}'s recording channel.
 *
 * <p>Truncating, writing and forcing a lock record are invisible in the resulting file: a record
 * that was never forced is byte-identical until the power fails. Recording the calls is the only
 * way a test can see that {@code force(true)} happens, and happens last. Reads can be scripted to
 * return short counts -- including zero -- so that the read loop is held to reading the whole
 * record however the operating system chooses to deliver it.
 *
 * <p>Only the operations {@link ProjectLock} uses are implemented; every other one fails the test.
 */
final class RecordingChannel extends FileChannel {

    private byte[] content;

    private final List<String> operations = Collections.synchronizedList(new ArrayList<>());

    private final Deque<Integer> readCounts = new ArrayDeque<>();

    private final boolean failOnClose;

    RecordingChannel(byte[] content, boolean failOnClose) {
        this.content = content.clone();
        this.failOnClose = failOnClose;
    }

    /**
     * Scripts the counts successive reads return: a positive number is a short read of at most that
     * many bytes, zero is a read of nothing, a negative number is end of file. Once the script is
     * exhausted, reads return everything asked for.
     */
    RecordingChannel scriptReads(Integer... counts) {
        readCounts.addAll(Arrays.asList(counts));
        return this;
    }

    List<String> operations() {
        synchronized (operations) {
            return List.copyOf(operations);
        }
    }

    byte[] content() {
        return content.clone();
    }

    @Override
    public int read(ByteBuffer destination, long position) {
        Integer scripted = readCounts.pollFirst();
        if (scripted != null && scripted < 0) {
            operations.add("read(eof)");
            return -1;
        }
        int available = (int) Math.max(0, content.length - position);
        int count = Math.min(destination.remaining(), available);
        if (scripted != null) {
            count = Math.min(count, scripted);
        }
        destination.put(content, (int) position, count);
        operations.add("read(" + count + ")");
        return count;
    }

    @Override
    public int write(ByteBuffer source, long position) {
        int count = source.remaining();
        int end = (int) position + count;
        if (end > content.length) {
            content = Arrays.copyOf(content, end);
        }
        source.get(content, (int) position, count);
        operations.add("write(" + count + "@" + position + ")");
        return count;
    }

    @Override
    public long size() {
        return content.length;
    }

    @Override
    public FileChannel truncate(long size) {
        content = Arrays.copyOf(content, (int) Math.min(size, content.length));
        operations.add("truncate(" + size + ")");
        return this;
    }

    @Override
    public void force(boolean metaData) {
        operations.add("force(" + metaData + ")");
    }

    @Override
    protected void implCloseChannel() throws IOException {
        operations.add("close");
        if (failOnClose) {
            throw new IOException("the channel would not close");
        }
    }

    @Override
    public int read(ByteBuffer destination) {
        throw new AssertionError("ProjectLock reads only at a position");
    }

    @Override
    public long read(ByteBuffer[] destinations, int offset, int length) {
        throw new AssertionError("not used");
    }

    @Override
    public int write(ByteBuffer source) {
        throw new AssertionError("ProjectLock writes only at a position");
    }

    @Override
    public long write(ByteBuffer[] sources, int offset, int length) {
        throw new AssertionError("not used");
    }

    @Override
    public long position() {
        throw new AssertionError("not used");
    }

    @Override
    public FileChannel position(long newPosition) {
        throw new AssertionError("not used");
    }

    @Override
    public long transferTo(long position, long count, WritableByteChannel target) {
        throw new AssertionError("not used");
    }

    @Override
    public long transferFrom(ReadableByteChannel source, long position, long count) {
        throw new AssertionError("not used");
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
}
