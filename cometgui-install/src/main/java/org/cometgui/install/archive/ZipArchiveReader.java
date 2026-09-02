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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads a zip container, using both of its tables and comparing them.
 *
 * <h2>Why the central directory is parsed by hand</h2>
 *
 * <p>{@code java.util.zip} does not expose an entry's external attributes, and those attributes are
 * where a zip records that an entry is a <strong>symbolic link</strong>. Without them a link is
 * extracted as an ordinary file containing its target's text -- harmless, but it means the
 * unsafe-symlink rule could never fire on a zip, and a rule that cannot fire on a kind is a rule
 * that can be switched off for that kind without anything going red. So the sixteen bytes that
 * matter are read directly.
 *
 * <p>Two further things come out of the same read. The declared uncompressed size is taken from the
 * central directory, which the guard then checks against the bytes actually delivered; and every
 * entry the stream yields must appear in the central directory, which refuses the
 * <em>zip-confusion</em> shape where the two tables describe different archives and a reader is
 * chosen to decide which one a consumer sees.
 */
final class ZipArchiveReader implements ArchiveReader {

    /** The end-of-central-directory signature, little-endian. */
    private static final int END_OF_CENTRAL_DIRECTORY = 0x06054b50;

    /** The Zip64 end-of-central-directory locator signature. */
    private static final int ZIP64_LOCATOR = 0x07064b50;

    /** The Zip64 end-of-central-directory record signature. */
    private static final int ZIP64_END_OF_CENTRAL_DIRECTORY = 0x06064b50;

    /** A central-directory file header's signature. */
    private static final int CENTRAL_FILE_HEADER = 0x02014b50;

    /** The largest end-of-central-directory record plus its 65535-byte comment. */
    private static final int MAX_END_RECORD_BYTES = 22 + 0xFFFF;

    /** The most central-directory bytes this reader will hold in memory. */
    private static final int MAX_CENTRAL_DIRECTORY_BYTES = 64 * 1024 * 1024;

    /** The 32-bit fields whose value means "look in the Zip64 extra field". */
    private static final long ZIP64_SENTINEL_32 = 0xFFFFFFFFL;

    /** {@code versionMadeBy}'s high byte for an archive written on a Unix host. */
    private static final int HOST_UNIX = 3;

    /** The file-type mask of a Unix mode, and the value that means a symbolic link. */
    private static final int UNIX_FILE_TYPE_MASK = 0xF000;

    /** {@code S_IFLNK}. */
    private static final int UNIX_SYMLINK = 0xA000;

    /** {@code S_IFREG}. */
    private static final int UNIX_REGULAR_FILE = 0x8000;

    /** {@code S_IFDIR}. */
    private static final int UNIX_DIRECTORY = 0x4000;

    /** The longest symbolic-link target this reader will read out of an entry. */
    private static final int MAX_SYMLINK_TARGET_BYTES = 4096;

    /** The artefact's file name, for messages. */
    private final String artefactName;

    /** What the central directory says, by entry name and in central-directory order. */
    private final Map<String, Catalogued> catalogue;

    /** The sequential view of the same archive. */
    private final ZipInputStream zip;

    /** The window over the current entry's bytes. */
    private InputStream content = InputStream.nullInputStream();

    /**
     * Opens a zip archive.
     *
     * @param source the archive on disk
     * @throws IOException if it cannot be read
     * @throws ExtractionRejectedException if its central directory is malformed
     */
    ZipArchiveReader(Path source) throws IOException {
        this.artefactName = String.valueOf(source.getFileName());
        this.catalogue = readCentralDirectory(source, artefactName);
        this.zip =
                new ZipInputStream(
                        new BufferedInputStream(Files.newInputStream(source)),
                        StandardCharsets.UTF_8);
    }

    @Override
    public ArchiveEntry next() throws IOException {
        ZipEntry entry = zip.getNextEntry();
        if (entry == null) {
            return null;
        }
        String name = entry.getName();
        Catalogued catalogued = catalogue.get(name);
        if (catalogued == null) {
            throw ExtractionRejectedException.artefact(
                    RejectionReason.MALFORMED_ARCHIVE,
                    artefactName,
                    " -- the entry stream carries \""
                            + name
                            + "\" and the central directory does not, so the archive's two tables"
                            + " describe different contents");
        }
        content = zip;
        if (name.endsWith("/") || catalogued.isDirectory()) {
            content = InputStream.nullInputStream();
            return ArchiveEntry.directory(name);
        }
        if (catalogued.isSymlink()) {
            byte[] target = zip.readNBytes(MAX_SYMLINK_TARGET_BYTES);
            content = InputStream.nullInputStream();
            return ArchiveEntry.symlink(name, new String(target, StandardCharsets.UTF_8));
        }
        if (catalogued.isUnrecognisedType()) {
            return ArchiveEntry.other(name, catalogued.sizeBytes());
        }
        return ArchiveEntry.file(name, catalogued.sizeBytes());
    }

    /**
     * The bytes of the entry {@link #next()} last returned.
     *
     * <p>Handed out unwrapped. A zip's entry stream is already bounded by the container, and no
     * caller closes an entry stream -- the reader owns it -- so a non-closing wrapper here would be
     * a branch with nothing behind it.
     *
     * @return the current entry's bytes, empty for a directory or a symbolic link
     */
    @Override
    public InputStream content() {
        return content;
    }

    @Override
    public void close() throws IOException {
        zip.close();
    }

    /*
     * What one central-directory record says about an entry.  Only the three facts the guard
     * needs are kept: how long the entry is, whether the archive was written on a Unix host, and
     * what that host's mode bits say it is.
     */
    private record Catalogued(long sizeBytes, boolean unixHost, int unixMode) {

        boolean isSymlink() {
            return unixHost && (unixMode & UNIX_FILE_TYPE_MASK) == UNIX_SYMLINK;
        }

        boolean isDirectory() {
            return unixHost && (unixMode & UNIX_FILE_TYPE_MASK) == UNIX_DIRECTORY;
        }

        /*
         * A Unix host that recorded a mode which is neither a regular file, a directory nor a
         * symbolic link -- a device node, a socket, a FIFO.  A mode of zero is not a claim about
         * anything and is treated as an ordinary file, because that is what a zip written by a
         * tool that does not record modes looks like.
         */
        boolean isUnrecognisedType() {
            int type = unixMode & UNIX_FILE_TYPE_MASK;
            return unixHost && type != 0 && type != UNIX_REGULAR_FILE;
        }
    }

    private static Map<String, Catalogued> readCentralDirectory(Path source, String artefactName)
            throws IOException {
        try (SeekableByteChannel channel = Files.newByteChannel(source, StandardOpenOption.READ)) {
            long fileSize = channel.size();
            ByteBuffer tail =
                    read(
                            channel,
                            Math.max(0, fileSize - MAX_END_RECORD_BYTES),
                            (int) Math.min(fileSize, MAX_END_RECORD_BYTES),
                            artefactName);
            int endOffset = lastIndexOfSignature(tail, END_OF_CENTRAL_DIRECTORY);
            if (endOffset < 0) {
                throw ExtractionRejectedException.artefact(
                        RejectionReason.MALFORMED_ARCHIVE,
                        artefactName,
                        " -- it has no end-of-central-directory record, so it is not a zip archive"
                                + " or it was truncated");
            }
            long entryCount = tail.getShort(endOffset + 10) & 0xFFFFL;
            long directorySize = tail.getInt(endOffset + 12) & ZIP64_SENTINEL_32;
            long directoryOffset = tail.getInt(endOffset + 16) & ZIP64_SENTINEL_32;
            if (entryCount == 0xFFFFL
                    || directorySize == ZIP64_SENTINEL_32
                    || directoryOffset == ZIP64_SENTINEL_32) {
                long[] zip64 = readZip64(channel, tail, endOffset, artefactName);
                entryCount = zip64[0];
                directorySize = zip64[1];
                directoryOffset = zip64[2];
            }
            if (directorySize > MAX_CENTRAL_DIRECTORY_BYTES) {
                throw ExtractionRejectedException.artefact(
                        RejectionReason.MALFORMED_ARCHIVE,
                        artefactName,
                        " -- its central directory declares "
                                + directorySize
                                + " bytes and this reader holds at most "
                                + MAX_CENTRAL_DIRECTORY_BYTES);
            }
            ByteBuffer directory =
                    read(channel, directoryOffset, (int) directorySize, artefactName);
            return parseCentralDirectory(directory, entryCount, artefactName);
        }
    }

    private static long[] readZip64(
            SeekableByteChannel channel, ByteBuffer tail, int endOffset, String artefactName)
            throws IOException {
        int locatorOffset = endOffset - 20;
        if (locatorOffset < 0 || tail.getInt(locatorOffset) != ZIP64_LOCATOR) {
            throw ExtractionRejectedException.artefact(
                    RejectionReason.MALFORMED_ARCHIVE,
                    artefactName,
                    " -- its end-of-central-directory record uses the Zip64 sentinel values and no"
                            + " Zip64 locator precedes it");
        }
        long recordOffset = tail.getLong(locatorOffset + 8);
        ByteBuffer record = read(channel, recordOffset, 56, artefactName);
        if (record.getInt(0) != ZIP64_END_OF_CENTRAL_DIRECTORY) {
            throw ExtractionRejectedException.artefact(
                    RejectionReason.MALFORMED_ARCHIVE,
                    artefactName,
                    " -- its Zip64 locator points at offset "
                            + recordOffset
                            + ", where there is no Zip64 end-of-central-directory record");
        }
        return new long[] {record.getLong(32), record.getLong(40), record.getLong(48)};
    }

    private static Map<String, Catalogued> parseCentralDirectory(
            ByteBuffer directory, long entryCount, String artefactName) throws IOException {
        Map<String, Catalogued> catalogue = new LinkedHashMap<>();
        int position = 0;
        for (long index = 0; index < entryCount; index++) {
            if (position + 46 > directory.limit()
                    || directory.getInt(position) != CENTRAL_FILE_HEADER) {
                throw ExtractionRejectedException.artefact(
                        RejectionReason.MALFORMED_ARCHIVE,
                        artefactName,
                        " -- its central directory declares "
                                + entryCount
                                + " entries and record "
                                + (index + 1)
                                + " is not there");
            }
            int versionMadeBy = directory.getShort(position + 4) & 0xFFFF;
            int nameLength = directory.getShort(position + 28) & 0xFFFF;
            int extraLength = directory.getShort(position + 30) & 0xFFFF;
            int commentLength = directory.getShort(position + 32) & 0xFFFF;
            long externalAttributes = directory.getInt(position + 38) & ZIP64_SENTINEL_32;
            long uncompressedSize = directory.getInt(position + 24) & ZIP64_SENTINEL_32;
            byte[] nameBytes = new byte[nameLength];
            directory.get(position + 46, nameBytes);
            String name = new String(nameBytes, StandardCharsets.UTF_8);
            if (uncompressedSize == ZIP64_SENTINEL_32) {
                uncompressedSize =
                        zip64UncompressedSize(
                                directory,
                                position + 46 + nameLength,
                                extraLength,
                                name,
                                artefactName);
            }
            boolean unixHost = (versionMadeBy >>> 8) == HOST_UNIX;
            int unixMode = (int) (externalAttributes >>> 16) & 0xFFFF;
            if (catalogue.put(name, new Catalogued(uncompressedSize, unixHost, unixMode)) != null) {
                throw ExtractionRejectedException.entry(
                        RejectionReason.DUPLICATE_ENTRY_NAME,
                        name,
                        " -- the central directory lists it twice");
            }
            position += 46 + nameLength + extraLength + commentLength;
        }
        return catalogue;
    }

    private static long zip64UncompressedSize(
            ByteBuffer directory,
            int extraOffset,
            int extraLength,
            String name,
            String artefactName)
            throws IOException {
        int cursor = extraOffset;
        int end = extraOffset + extraLength;
        /*
         * TERMINATION IS NOT AN ACCIDENT OF THE DATA.  dataSize is masked to 0..65535, so the step
         * below is at least four and the cursor always advances, whatever an attacker writes in the
         * extra field.  There is no input for which this does not terminate.
         *
         * Its own comparison survives mutation and cannot do otherwise.  The four bytes are a
         * header about to be read, so "<=" is the rule; the only input that separates it from "<"
         * is an extra field ending in a bare header with no payload, and such a header cannot be
         * the eight-byte size field being looked for -- so both forms reach the same refusal below.
         */
        while (cursor + 4 <= end) {
            int headerId = directory.getShort(cursor) & 0xFFFF;
            int dataSize = directory.getShort(cursor + 2) & 0xFFFF;
            if (headerId == 0x0001 && dataSize >= 8) {
                return directory.getLong(cursor + 4);
            }
            cursor += 4 + dataSize;
        }
        throw ExtractionRejectedException.artefact(
                RejectionReason.MALFORMED_ARCHIVE,
                artefactName,
                " -- entry \""
                        + name
                        + "\" declares the Zip64 size sentinel and carries no Zip64 extra field"
                        + " holding the real size");
    }

    private static int lastIndexOfSignature(ByteBuffer buffer, int signature) {
        for (int offset = buffer.limit() - 4; offset >= 0; offset--) {
            if (buffer.getInt(offset) == signature) {
                return offset;
            }
        }
        return -1;
    }

    private static ByteBuffer read(
            SeekableByteChannel channel, long offset, int length, String artefactName)
            throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
        channel.position(offset);
        while (buffer.hasRemaining()) {
            /* -1 is the end of the file; a file channel never answers a non-empty buffer with 0. */
            if (channel.read(buffer) < 0) {
                throw ExtractionRejectedException.artefact(
                        RejectionReason.MALFORMED_ARCHIVE,
                        artefactName,
                        " -- it ends before offset "
                                + (offset + length)
                                + ", which its own tables point at");
            }
        }
        return buffer.flip().order(ByteOrder.LITTLE_ENDIAN);
    }
}
