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

package org.cometgui.tools.comet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.HashService;

/**
 * A {@link HashService} for tests that records what it was asked to hash and what the file held at
 * that moment, so a test can prove the merge hashed the final file after it was closed.
 *
 * <p>This module cannot depend on {@code cometgui-provenance}, where the product's hash service
 * lives, so the merge is handed this one; its digests are the JDK's, independent of the code under
 * test.
 */
final class TestHashes implements HashService {

    private final List<Path> hashed = new ArrayList<>();

    private final List<byte[]> contents = new ArrayList<>();

    @Override
    public FileHashes hash(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        hashed.add(path);
        contents.add(bytes);
        return of(bytes);
    }

    /**
     * The MD5 and SHA-256 of some bytes.
     *
     * @param bytes the bytes
     * @return their checksums
     */
    static FileHashes of(byte[] bytes) {
        try {
            return new FileHashes(
                    HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(bytes)),
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError("every Java runtime provides MD5 and SHA-256", impossible);
        }
    }

    /**
     * The files hashed, in order.
     *
     * @return the paths
     */
    List<Path> hashed() {
        return List.copyOf(hashed);
    }

    /**
     * What each file held when it was hashed, in order.
     *
     * @return the contents
     */
    List<byte[]> contents() {
        return List.copyOf(contents);
    }
}
