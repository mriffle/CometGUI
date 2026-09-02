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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Reads the file tree out of a Debian package: an {@code ar} archive holding a compressed tar.
 *
 * <p>A {@code .deb} is three {@code ar} members in order -- {@code debian-binary}, {@code
 * control.tar.*} and {@code data.tar.*} -- and only the last is the installed file tree. Nothing
 * here runs an installer or a maintainer script; the payload is a plain relative file tree, and
 * {@code D-002} option C leaves this kind in the product for one narrow purpose: the two Percolator
 * XSD companion files that no portable archive ships.
 *
 * <p><strong>The Java runtime decodes gzip and nothing else this format may use.</strong> A {@code
 * data.tar.xz}, {@code .zst} or {@code .bz2} is refused by name rather than half-read, because this
 * project adds no dependency to gain a codec and a wrong guess about a container would be worse
 * than a clear refusal. Every {@code .deb} in the manifest today is {@code data.tar.gz}.
 */
final class DebPayloadReader implements ArchiveReader {

    /** The eight bytes every {@code ar} archive starts with. */
    private static final String AR_MAGIC = "!<arch>\n";

    /** An {@code ar} member header is 60 bytes and ends with these two. */
    private static final String MEMBER_MAGIC = "`\n";

    /** The artefact's name, for messages. */
    private final String artefactName;

    /** The stream the {@code ar} archive is read from. */
    private final InputStream in;

    /** The tar reader over the payload, once it has been found. */
    private final TarArchiveReader payload;

    /**
     * Opens a Debian package and positions it at the start of its payload.
     *
     * @param source the package on disk
     * @throws IOException if it cannot be read
     * @throws ExtractionRejectedException if it is not an {@code ar} archive, if it has no {@code
     *     data.tar} member, or if that member uses a codec the runtime cannot decode
     */
    DebPayloadReader(Path source) throws IOException {
        this.artefactName = String.valueOf(source.getFileName());
        this.in = new BufferedInputStream(Files.newInputStream(source));
        this.payload = new TarArchiveReader(openPayload(), artefactName);
    }

    @Override
    public ArchiveEntry next() throws IOException {
        return payload.next();
    }

    @Override
    public InputStream content() {
        return payload.content();
    }

    /**
     * Closes the payload reader and the {@code ar} stream underneath it.
     *
     * <p>Both, because they are different resources: the tar reader owns a {@code GZIPInputStream}
     * with a native inflater, and closing only the outer stream would leave that to a garbage
     * collector that may never run in a short install.
     *
     * @throws IOException if either cannot be closed
     */
    @Override
    public void close() throws IOException {
        try (InputStream outer = in) {
            payload.close();
        }
    }

    private InputStream openPayload() throws IOException {
        byte[] magic = in.readNBytes(AR_MAGIC.length());
        if (!AR_MAGIC.equals(new String(magic, StandardCharsets.US_ASCII))) {
            throw malformed("it does not begin \"!<arch>\", so it is not a Debian package at all");
        }
        List<String> seen = new ArrayList<>();
        while (true) {
            byte[] header = in.readNBytes(60);
            if (header.length == 0) {
                throw malformed(
                        "it holds no \"data.tar\" member; its members are "
                                + seen
                                + ", and the payload is the only one this reader wants");
            }
            if (header.length < 60) {
                throw malformed(
                        "a member header is cut off after " + header.length + " of its 60 bytes");
            }
            String trailer = new String(header, 58, 2, StandardCharsets.US_ASCII);
            if (!MEMBER_MAGIC.equals(trailer)) {
                throw malformed(
                        "a member header ends \""
                                + trailer.replace("\n", "\\n")
                                + "\" where \"`\\n\" belongs, so it is not readable as ar");
            }
            String name = fieldName(header);
            long size = fieldSize(header, name);
            seen.add(name);
            if (name.startsWith("data.tar")) {
                return decompress(name, new BoundedEntryStream(in, size));
            }
            TarArchiveReader.skipFully(in, size + (size % 2));
        }
    }

    private static String fieldName(byte[] header) {
        String raw = new String(header, 0, 16, StandardCharsets.US_ASCII).trim();
        return raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
    }

    private long fieldSize(byte[] header, String name) throws IOException {
        String field = new String(header, 48, 10, StandardCharsets.US_ASCII).trim();
        try {
            return Long.parseLong(field);
        } catch (NumberFormatException cause) {
            throw malformed(
                    "member \""
                            + name
                            + "\" declares its length as \""
                            + field
                            + "\", which is not a number");
        }
    }

    private InputStream decompress(String name, InputStream member) throws IOException {
        if (name.endsWith(".tar")) {
            return member;
        }
        if (name.endsWith(".tar.gz")) {
            try {
                return new GZIPInputStream(member);
            } catch (IOException cause) {
                /* Named for the reason PkgPayloadReader.decompress gives for naming its own. */
                throw malformed(
                        "its \"" + name + "\" member is not a readable gzip stream: " + cause);
            }
        }
        throw ExtractionRejectedException.artefact(
                RejectionReason.UNSUPPORTED_COMPRESSION,
                artefactName,
                " -- its payload member is \""
                        + name
                        + "\", and this extractor decodes \"data.tar\" and \"data.tar.gz\" only,"
                        + " because the Java runtime ships no xz, zstd or bzip2 decoder and this"
                        + " project adds no dependency to gain one");
    }

    private ExtractionRejectedException malformed(String detail) {
        return ExtractionRejectedException.artefact(
                RejectionReason.MALFORMED_ARCHIVE, artefactName, " -- " + detail);
    }
}
