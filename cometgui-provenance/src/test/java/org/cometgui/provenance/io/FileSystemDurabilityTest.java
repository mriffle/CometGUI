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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link FileSystemDurability}.
 *
 * <p>Every one of these operations is invisible in its result: a forced file and an unforced file
 * are byte-identical, and a directory that was synced and one that was not look exactly the same.
 * So the assertions here are made against a {@link RecordingFileChannel} that writes down what was
 * asked of it, and every expectation is a hand-typed literal list. The single fact that a recorder
 * cannot establish -- that the shipped opener really can open a real directory on this platform --
 * is asserted separately, against the real filesystem, and pinned from the other side by {@code
 * AtomicDocumentWriterTest.theShippedDurabilitySyncsTheDirectoryOnThisPlatform}.
 */
class FileSystemDurabilityTest {

    /** Content used wherever a real file has to be moved or read. */
    private static final String DOCUMENT = "new-provenance-document\n";

    @Test
    @DisplayName("syncFile forces data and metadata, which is force(true) and not force(false)")
    void syncFileForcesDataAndMetadata() throws IOException {
        RecordingFileChannel channel = new RecordingFileChannel();

        new FileSystemDurability().syncFile(channel);

        assertEquals(List.of("force(true)"), channel.operations());
    }

    @Test
    @DisplayName("syncDirectory opens the directory, forces it, and closes it")
    void syncDirectoryOpensForcesAndCloses(@TempDir Path dir) throws IOException {
        RecordingFileChannel channel = new RecordingFileChannel();
        List<Path> opened = new ArrayList<>();
        FileSystemDurability durability =
                new FileSystemDurability(
                        path -> {
                            opened.add(path);
                            return channel;
                        });

        durability.syncDirectory(dir);

        assertAll(
                () -> assertEquals(List.of(dir), opened),
                () -> assertEquals(List.of("force(true)", "close"), channel.operations()));
    }

    @Test
    @DisplayName("moveIntoPlace renames atomically and replaces whatever was there")
    void moveIntoPlaceReplacesTheTarget(@TempDir Path dir) throws IOException {
        Path temporary = dir.resolve("provenance.json.tmp-0");
        Path target = dir.resolve("provenance.json");
        Files.writeString(temporary, DOCUMENT, UTF_8);
        Files.writeString(target, "old-provenance\n", UTF_8);

        new FileSystemDurability().moveIntoPlace(temporary, target);

        assertAll(
                () -> assertFalse(Files.exists(temporary), "the temporary file survived the move"),
                () -> assertEquals("new-provenance-document\n", Files.readString(target, UTF_8)),
                () -> assertEquals(List.of("provenance.json"), listing(dir)));
    }

    @Test
    @DisplayName("the shipped opener really opens the path it is given")
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void theShippedOpenerReallyOpensThePath(@TempDir Path dir) throws IOException {
        FileSystemDurability durability = new FileSystemDurability();
        Path missing = dir.resolve("no-such-directory");

        // An opener that did nothing would not notice a missing directory.  This one does.
        NoSuchFileException thrown =
                assertThrows(NoSuchFileException.class, () -> durability.syncDirectory(missing));

        // And a directory that does exist is syncable here, which is the platform half of the
        // asymmetry AtomicDocumentWriter documents: legal on Linux and macOS, illegal on Windows.
        durability.syncDirectory(dir);

        assertAll(
                () -> assertEquals(missing.toString(), thrown.getMessage()),
                () -> assertEquals(List.of(), listing(dir)));
    }

    @Test
    @DisplayName("a null opener is rejected by name")
    void aNullOpenerIsRejected() {
        NullPointerException thrown =
                assertThrows(NullPointerException.class, () -> new FileSystemDurability(null));

        assertEquals("directoryOpener", thrown.getMessage());
    }

    /** The names in a directory, sorted, so a listing can be compared to a literal list. */
    private static List<String> listing(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.map(entry -> directory.relativize(entry).toString()).sorted().toList();
        }
    }

    /**
     * A {@link FileChannel} that records the two calls this seam is allowed to make and refuses
     * everything else.
     *
     * <p>Refusing the rest is deliberate. {@link FileSystemDurability} may force a channel and
     * close it; if a future edit made it read, write, seek or map instead, that would be a change
     * in what "durability" means here and it should fail loudly rather than be recorded quietly.
     */
    private static final class RecordingFileChannel extends FileChannel {

        private final List<String> operations = new ArrayList<>();

        List<String> operations() {
            return List.copyOf(operations);
        }

        @Override
        public void force(boolean metaData) {
            operations.add("force(" + metaData + ")");
        }

        @Override
        protected void implCloseChannel() {
            operations.add("close");
        }

        @Override
        public int read(ByteBuffer destination) {
            throw new UnsupportedOperationException("read");
        }

        @Override
        public long read(ByteBuffer[] destinations, int offset, int length) {
            throw new UnsupportedOperationException("read");
        }

        @Override
        public int write(ByteBuffer source) {
            throw new UnsupportedOperationException("write");
        }

        @Override
        public long write(ByteBuffer[] sources, int offset, int length) {
            throw new UnsupportedOperationException("write");
        }

        @Override
        public long position() {
            throw new UnsupportedOperationException("position");
        }

        @Override
        public FileChannel position(long newPosition) {
            throw new UnsupportedOperationException("position");
        }

        @Override
        public long size() {
            throw new UnsupportedOperationException("size");
        }

        @Override
        public FileChannel truncate(long size) {
            throw new UnsupportedOperationException("truncate");
        }

        @Override
        public long transferTo(long position, long count, WritableByteChannel destination) {
            throw new UnsupportedOperationException("transferTo");
        }

        @Override
        public long transferFrom(ReadableByteChannel source, long position, long count) {
            throw new UnsupportedOperationException("transferFrom");
        }

        @Override
        public int read(ByteBuffer destination, long position) {
            throw new UnsupportedOperationException("read");
        }

        @Override
        public int write(ByteBuffer source, long position) {
            throw new UnsupportedOperationException("write");
        }

        @Override
        public MappedByteBuffer map(MapMode mode, long position, long size) {
            throw new UnsupportedOperationException("map");
        }

        @Override
        public FileLock lock(long position, long size, boolean shared) {
            throw new UnsupportedOperationException("lock");
        }

        @Override
        public FileLock tryLock(long position, long size, boolean shared) {
            throw new UnsupportedOperationException("tryLock");
        }
    }
}
