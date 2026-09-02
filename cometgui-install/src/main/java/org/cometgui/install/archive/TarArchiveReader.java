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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Reads a ustar or GNU tar stream, header by header.
 *
 * <p>The format is 512-byte blocks: a header, then the file's bytes rounded up to a block boundary,
 * ending with two zero blocks. Every header carries a checksum over itself, computed with the
 * checksum field blanked, and this reader verifies it -- which is what turns a truncated or spliced
 * archive into a rejection rather than into a file assembled from whatever followed.
 *
 * <p>Two GNU extensions are handled because real {@code .deb} payloads use them: type {@code L} for
 * a name longer than 100 bytes and type {@code K} for a link target longer than 100 bytes, each
 * carried as a pseudo-entry whose content is the string for the entry that follows. Pax headers
 * ({@code x} and {@code g}) are stepped over.
 *
 * <p><strong>Hard links are not "another file".</strong> Type {@code 1} names an existing file
 * anywhere on the host and asks for a second name for it -- a traversal with no {@code ..} in it to
 * notice -- so it arrives here as {@link ArchiveEntryType#OTHER} and the guard refuses it.
 */
final class TarArchiveReader implements ArchiveReader {

    /** One tar block. */
    private static final int BLOCK = 512;

    /** The stream the archive is read from; already decompressed if it needed to be. */
    private final InputStream in;

    /** The artefact's name, for messages. */
    private final String artefactName;

    /** The window over the current entry, so that an unread remainder can be stepped over. */
    private BoundedEntryStream content = new BoundedEntryStream(InputStream.nullInputStream(), 0);

    /** Padding still owed at the end of the current entry. */
    private long padding;

    /**
     * Reads a tar stream.
     *
     * @param in the decompressed stream
     * @param artefactName the artefact's name, for messages
     */
    TarArchiveReader(InputStream in, String artefactName) {
        this.in = in;
        this.artefactName = artefactName;
    }

    @Override
    public ArchiveEntry next() throws IOException {
        finishCurrentEntry();
        String longName = null;
        String longLink = null;
        while (true) {
            byte[] header = in.readNBytes(BLOCK);
            if (header.length == 0) {
                return null;
            }
            if (header.length < BLOCK) {
                throw malformed(
                        "a header block is "
                                + header.length
                                + " bytes long and every block is 512");
            }
            if (isAllZero(header)) {
                return null;
            }
            verifyChecksum(header);
            char typeFlag = (char) (header[156] & 0xFF);
            long size = octal(header, 124, 12);
            String name = longName != null ? longName : nameOf(header);
            String linkTarget = longLink != null ? longLink : text(header, 157, 100);
            if (typeFlag == 'L' || typeFlag == 'K') {
                String value = new String(readEntryBody(size), StandardCharsets.UTF_8);
                int nul = value.indexOf('\0');
                String trimmed = nul >= 0 ? value.substring(0, nul) : value;
                if (typeFlag == 'L') {
                    longName = trimmed;
                } else {
                    longLink = trimmed;
                }
                continue;
            }
            if (typeFlag == 'x' || typeFlag == 'g') {
                readEntryBody(size);
                continue;
            }
            openBody(size);
            return switch (typeFlag) {
                case '0', '\0' -> ArchiveEntry.file(name, size);
                case '5' -> ArchiveEntry.directory(name);
                case '2' -> ArchiveEntry.symlink(name, linkTarget);
                default -> ArchiveEntry.other(name, size);
            };
        }
    }

    @Override
    public InputStream content() {
        return content;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    private void openBody(long size) {
        content = new BoundedEntryStream(in, size);
        padding = (BLOCK - (size % BLOCK)) % BLOCK;
    }

    private byte[] readEntryBody(long size) throws IOException {
        openBody(size);
        byte[] body = content.readAllBytes();
        finishCurrentEntry();
        return body;
    }

    private void finishCurrentEntry() throws IOException {
        skipFully(in, content.remaining() + padding);
        content = new BoundedEntryStream(InputStream.nullInputStream(), 0);
        padding = 0;
    }

    /*
     * A tar header's name may be split across a 155-byte prefix and a 100-byte name, which is how
     * ustar carries a path longer than 100 bytes without a GNU extension.
     */
    private static String nameOf(byte[] header) {
        String name = text(header, 0, 100);
        String prefix = text(header, 345, 155);
        return prefix.isEmpty() ? name : prefix + "/" + name;
    }

    /*
     * The stored checksum is computed with the checksum field itself read as eight spaces.  Both
     * the unsigned and the historical signed sum are accepted, because both exist in the wild.
     */
    private void verifyChecksum(byte[] header) throws IOException {
        long stored = octal(header, 148, 8);
        long unsigned = 0;
        long signed = 0;
        for (int index = 0; index < BLOCK; index++) {
            int value = index >= 148 && index < 156 ? ' ' : header[index] & 0xFF;
            unsigned += value;
            signed += index >= 148 && index < 156 ? ' ' : header[index];
        }
        if (stored != unsigned && stored != signed) {
            throw malformed(
                    "a header block's stored checksum is "
                            + stored
                            + " and the bytes sum to "
                            + unsigned
                            + ", so the archive is truncated, spliced or not a tar at all");
        }
    }

    private static boolean isAllZero(byte[] block) {
        for (byte value : block) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }

    private static String text(byte[] header, int offset, int length) {
        int end = offset;
        while (end < offset + length && header[end] != 0) {
            end++;
        }
        return new String(header, offset, end - offset, StandardCharsets.UTF_8);
    }

    private long octal(byte[] header, int offset, int length) throws IOException {
        String field = text(header, offset, length).trim();
        if (field.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(field, 8);
        } catch (NumberFormatException cause) {
            throw malformed("a header block holds \"" + field + "\" where an octal number belongs");
        }
    }

    private ExtractionRejectedException malformed(String detail) {
        return ExtractionRejectedException.artefact(
                RejectionReason.MALFORMED_ARCHIVE, artefactName, " -- " + detail);
    }

    /**
     * Steps over exactly {@code count} bytes, failing rather than stopping short.
     *
     * @param in the stream
     * @param count how many bytes to step over
     * @throws IOException if the stream ends first
     */
    static void skipFully(InputStream in, long count) throws IOException {
        try {
            /*
             * The runtime's own implementation, not a hand-rolled loop: InputStream.skip may
             * legitimately return zero without being at the end, and every hand-rolled version of
             * this has a branch for that which no test can reach.  skipNBytes gets it right and
             * says so by throwing at the end of the stream.
             */
            in.skipNBytes(count);
        } catch (EOFException ended) {
            throw new IOException(
                    "the archive ended before " + count + " bytes could be stepped over", ended);
        }
    }
}
