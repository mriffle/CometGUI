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

package org.cometgui.app.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * A file's size, MD5 and SHA-256, computed by this test's own streaming loop over {@link
 * MessageDigest} -- not by the product's {@code StreamingHashService} or {@code
 * CachingHashService}, so that a digest the product records is compared with one the product did
 * not compute (design decision P13-7).
 *
 * @param size the bytes read
 * @param md5 lower-case hexadecimal
 * @param sha256 lower-case hexadecimal
 */
record OwnDigests(long size, String md5, String sha256) {

    /**
     * Reads a file once, in 64 KiB chunks, feeding both digests.
     *
     * @param file the file
     * @return what was read
     * @throws IOException if it cannot be read
     */
    static OwnDigests of(Path file) throws IOException {
        MessageDigest md5;
        MessageDigest sha256;
        try {
            md5 = MessageDigest.getInstance("MD5");
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError("every Java runtime provides MD5 and SHA-256", impossible);
        }
        long size = 0;
        byte[] buffer = new byte[65536];
        try (InputStream in = Files.newInputStream(file)) {
            int read;
            while ((read = in.read(buffer)) >= 0) {
                md5.update(buffer, 0, read);
                sha256.update(buffer, 0, read);
                size += read;
            }
        }
        HexFormat hex = HexFormat.of();
        return new OwnDigests(size, hex.formatHex(md5.digest()), hex.formatHex(sha256.digest()));
    }
}
