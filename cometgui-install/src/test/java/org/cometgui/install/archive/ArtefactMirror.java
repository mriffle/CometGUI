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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Finds the real upstream artefacts, and hashes what came out of them.
 *
 * <p>The artefacts are the bytes upstream publishes, fetched by pinned URL with mandatory SHA-256
 * verification and kept in a gitignored mirror -- nothing downloaded enters git. A test that needs
 * them <strong>fails</strong> when they are absent rather than skipping: an extraction suite that
 * quietly stops checking the real containers is the fifth shape of a check that cannot go red, and
 * the message below says exactly how to refill the mirror.
 */
final class ArtefactMirror {

    /** Where the mirror lives, relative to the repository root. */
    static final String MIRROR = "scratch/phase05/artefacts";

    private ArtefactMirror() {}

    /**
     * The repository root, found by walking up from the module's working directory.
     *
     * @return the root
     */
    static Path repositoryRoot() {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null && !Files.isDirectory(cursor.resolve("manifests"))) {
            cursor = cursor.getParent();
        }
        if (cursor == null) {
            throw new AssertionError(
                    "no repository root above "
                            + Path.of("").toAbsolutePath()
                            + " holds a manifests directory");
        }
        return cursor;
    }

    /**
     * One artefact from the mirror.
     *
     * @param fileName the mirror's file name, {@code <release-tag>__<upstream file name>}
     * @return the artefact
     */
    static Path artefact(String fileName) {
        Path file = repositoryRoot().resolve(MIRROR).resolve(fileName);
        if (!Files.isRegularFile(file)) {
            throw new AssertionError(
                    "the real artefact \""
                            + fileName
                            + "\" is not in the mirror at "
                            + file
                            + ". The mirror is gitignored and holds the bytes upstream publishes;"
                            + " refill it by fetching each artefact from the URL in"
                            + " manifests/tools.json and checking its SHA-256 before use. This test"
                            + " fails rather than skips, because an extraction suite that stops"
                            + " reading the real containers stops proving anything.");
        }
        return file;
    }

    /**
     * The SHA-256 of a file, in lower-case hexadecimal.
     *
     * @param file the file
     * @return the digest
     * @throws IOException if the file cannot be read
     */
    static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            try (InputStream in = Files.newInputStream(file)) {
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError("every Java runtime provides SHA-256", impossible);
        }
    }

    /**
     * Asserts that an extracted file has exactly the size and digest unit 0 pinned for it.
     *
     * @param file the extracted file
     * @param expectedSizeBytes the pinned length
     * @param expectedSha256 the pinned digest
     * @throws IOException if the file cannot be read
     */
    static void assertContent(Path file, long expectedSizeBytes, String expectedSha256)
            throws IOException {
        assertEquals(
                expectedSizeBytes,
                Files.size(file),
                () -> "extracted " + file + " has the wrong length");
        assertEquals(
                expectedSha256,
                sha256(file),
                () -> "extracted " + file + " is not the bytes unit 0 pinned");
    }
}
