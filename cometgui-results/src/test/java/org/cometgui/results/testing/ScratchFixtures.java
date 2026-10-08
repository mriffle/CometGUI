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

package org.cometgui.results.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Fixtures that live in the repository's gitignored {@code scratch/} directory rather than on the
 * test classpath: data that is too large to commit (the Phase 10 large fixture) or that CometGUI
 * may not redistribute ({@code D-006}: the K562 search's Percolator output). Each is held to a
 * pinned SHA-256 before use, and a missing or different file <strong>fails</strong> the test that
 * needs it, naming how to make it again; nothing skips.
 */
public final class ScratchFixtures {

    private ScratchFixtures() {}

    /**
     * The repository root: the nearest directory at or above the working directory (a module's
     * directory under Surefire) that holds {@code manifests/}, as the other modules' real-fixture
     * tests find it. In the test-gates sandbox that root carries a symbolic link to the real {@code
     * scratch/}.
     *
     * @return the root
     * @throws AssertionError if there is none
     */
    public static Path repositoryRoot() {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null && !Files.isDirectory(cursor.resolve("manifests"))) {
            cursor = cursor.getParent();
        }
        if (cursor == null) {
            throw new AssertionError("no repository root above " + Path.of("").toAbsolutePath());
        }
        return cursor;
    }

    /**
     * A scratch file, held to its pinned SHA-256 first.
     *
     * @param file the file
     * @param sha256 its pinned digest, lower-case hexadecimal
     * @param remake how to make it again, for the failure message
     * @return the same file
     * @throws org.opentest4j.AssertionFailedError if it is missing or its digest differs
     */
    public static Path verified(Path file, String sha256, String remake) {
        assertTrue(
                Files.isRegularFile(file),
                () ->
                        "the fixture "
                                + file
                                + " does not exist. It is gitignored scratch, never committed. "
                                + remake
                                + " This test fails rather than skips.");
        assertEquals(
                sha256,
                sha256(file),
                () ->
                        "the fixture "
                                + file
                                + " is not the file whose SHA-256 is pinned here. "
                                + remake
                                + " This test fails rather than skips.");
        return file;
    }

    /**
     * A file's SHA-256, streamed, so a 150 MB fixture is not read into the heap.
     *
     * @param file the file
     * @return the digest, lower-case hexadecimal
     */
    public static String sha256(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1 << 20];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
