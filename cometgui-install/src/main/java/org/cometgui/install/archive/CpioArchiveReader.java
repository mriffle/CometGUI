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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Reads a cpio archive in the old ASCII ({@code 070707}) or SVR4 ({@code 070701}, {@code 070702})
 * flavour.
 *
 * <p>Upstream Percolator's macOS {@code .pkg} payload is {@code 070707}, whose header is 76 ASCII
 * characters of octal followed by the name and then the bytes, with no padding anywhere. The two
 * SVR4 flavours are read as well -- 110 hexadecimal characters, name and data each padded to four
 * -- so that a repackaging upstream does not turn into a failed install.
 *
 * <p>The entry type is in the mode field, the same {@code S_IF*} bits a {@code stat} returns, and a
 * symbolic link's target is its <em>content</em> rather than a header field. That is the difference
 * from tar and it is why the target is read here.
 */
final class CpioArchiveReader implements ArchiveReader {

    /** Old ASCII, octal fields, 76-byte header, no padding. */
    private static final String MAGIC_ODC = "070707";

    /** SVR4 "newc", hexadecimal fields, 110-byte header, four-byte padding. */
    private static final String MAGIC_NEWC = "070701";

    /** SVR4 with a checksum; the same layout as "newc". */
    private static final String MAGIC_CRC = "070702";

    /** The name that ends every cpio archive. */
    private static final String TRAILER = "TRAILER!!!";

    /** {@code S_IFMT}. */
    private static final int FILE_TYPE_MASK = 0170000;

    /** {@code S_IFREG}. */
    private static final int REGULAR_FILE = 0100000;

    /** {@code S_IFDIR}. */
    private static final int DIRECTORY = 0040000;

    /** {@code S_IFLNK}. */
    private static final int SYMLINK = 0120000;

    /** The longest name or symbolic-link target this reader will read. */
    private static final int MAX_NAME_BYTES = 65536;

    /** The stream the archive is read from, already decompressed. */
    private final InputStream in;

    /** The artefact's name, for messages. */
    private final String artefactName;

    /** The window over the current entry. */
    private BoundedEntryStream content = new BoundedEntryStream(InputStream.nullInputStream(), 0);

    /** Padding still owed at the end of the current entry. */
    private long padding;

    /**
     * Reads a cpio stream.
     *
     * @param in the decompressed stream
     * @param artefactName the artefact's name, for messages
     */
    CpioArchiveReader(InputStream in, String artefactName) {
        this.in = in;
        this.artefactName = artefactName;
    }

    @Override
    public ArchiveEntry next() throws IOException {
        TarArchiveReader.skipFully(in, content.remaining() + padding);
        content = new BoundedEntryStream(InputStream.nullInputStream(), 0);
        padding = 0;
        byte[] magic = in.readNBytes(6);
        if (magic.length == 0) {
            return null;
        }
        String flavour = new String(magic, StandardCharsets.US_ASCII);
        return switch (flavour) {
            case MAGIC_ODC -> oldAscii();
            case MAGIC_NEWC, MAGIC_CRC -> newAscii();
            default ->
                    throw malformed(
                            "an entry header begins \""
                                    + flavour
                                    + "\" where a cpio magic number -- 070707, 070701 or 070702 --"
                                    + " belongs");
        };
    }

    @Override
    public InputStream content() {
        return content;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    private ArchiveEntry oldAscii() throws IOException {
        byte[] header = readHeader(70, MAGIC_ODC);
        int mode = (int) number(header, 12, 6, 8, MAGIC_ODC);
        long nameSize = number(header, 53, 6, 8, MAGIC_ODC);
        long fileSize = number(header, 59, 11, 8, MAGIC_ODC);
        return entry(readName(nameSize), mode, fileSize, 0);
    }

    private ArchiveEntry newAscii() throws IOException {
        byte[] header = readHeader(104, MAGIC_NEWC);
        int mode = (int) number(header, 8, 8, 16, MAGIC_NEWC);
        long fileSize = number(header, 48, 8, 16, MAGIC_NEWC);
        long nameSize = number(header, 88, 8, 16, MAGIC_NEWC);
        long namePadding = (4 - ((110 + nameSize) % 4)) % 4;
        String name = readName(nameSize);
        TarArchiveReader.skipFully(in, namePadding);
        return entry(name, mode, fileSize, (4 - (fileSize % 4)) % 4);
    }

    private ArchiveEntry entry(String name, int mode, long fileSize, long trailingPadding)
            throws IOException {
        if (TRAILER.equals(name)) {
            return null;
        }
        int type = mode & FILE_TYPE_MASK;
        if (type == SYMLINK) {
            byte[] target = readBounded(fileSize, "a symbolic link's target");
            TarArchiveReader.skipFully(in, trailingPadding);
            return ArchiveEntry.symlink(name, new String(target, StandardCharsets.UTF_8));
        }
        content = new BoundedEntryStream(in, fileSize);
        padding = trailingPadding;
        if (type == DIRECTORY) {
            return ArchiveEntry.directory(name);
        }
        if (type == REGULAR_FILE || type == 0) {
            return ArchiveEntry.file(name, fileSize);
        }
        return ArchiveEntry.other(name, fileSize);
    }

    private byte[] readHeader(int length, String flavour) throws IOException {
        byte[] header = in.readNBytes(length);
        if (header.length < length) {
            throw malformed(
                    "a "
                            + flavour
                            + " header is cut off after "
                            + (6 + header.length)
                            + " bytes, so the archive is truncated");
        }
        return header;
    }

    private String readName(long nameSize) throws IOException {
        byte[] raw = readBounded(nameSize, "an entry name");
        int end = raw.length;
        while (end > 0 && raw[end - 1] == 0) {
            end--;
        }
        return new String(raw, 0, end, StandardCharsets.UTF_8);
    }

    private byte[] readBounded(long size, String what) throws IOException {
        if (size > MAX_NAME_BYTES) {
            throw malformed(
                    what
                            + " declares "
                            + size
                            + " bytes and this reader reads at most "
                            + MAX_NAME_BYTES);
        }
        byte[] raw = in.readNBytes((int) size);
        if (raw.length < size) {
            throw malformed(
                    what + " declares " + size + " bytes and only " + raw.length + " are there");
        }
        return raw;
    }

    private long number(byte[] header, int offset, int length, int radix, String flavour)
            throws IOException {
        String field = new String(header, offset, length, StandardCharsets.US_ASCII).trim();
        try {
            return Long.parseLong(field, radix);
        } catch (NumberFormatException cause) {
            throw malformed(
                    "a "
                            + flavour
                            + " header holds \""
                            + field
                            + "\" where a base-"
                            + radix
                            + " number belongs");
        }
    }

    private ExtractionRejectedException malformed(String detail) {
        return ExtractionRejectedException.artefact(
                RejectionReason.MALFORMED_ARCHIVE, artefactName, " -- " + detail);
    }
}
