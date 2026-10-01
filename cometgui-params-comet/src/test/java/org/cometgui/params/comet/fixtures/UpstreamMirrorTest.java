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

package org.cometgui.params.comet.fixtures;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The mirror locator against a <em>temporary</em> repository root: a missing artefact and a
 * checksum mismatch each fail with a diagnostic, and the shared mirror is never disturbed.
 */
class UpstreamMirrorTest {

    /** A constructed row; the URL is never fetched. */
    private static CometManifest.Row row(String sha256) {
        return new CometManifest.Row(
                "2026.02.2",
                "v2026.02.2",
                "linux",
                "x86-64",
                "https://github.com/UWPR/Comet/releases/download/v2026.02.2/comet.linux.exe",
                sha256);
    }

    private static Path temporaryRoot(Path directory) throws IOException {
        Files.createDirectories(directory.resolve("manifests"));
        return directory;
    }

    @Test
    @DisplayName("the mirror name is <releaseTag>__<last URL segment>, as Phase 05 names it")
    void theMirrorFileName() throws IOException {
        CometManifest.Row real =
                CometManifest.cometRows(CometManifest.repositoryManifest()).stream()
                        .filter(CometManifest.Row::isLinuxX8664)
                        .findFirst()
                        .orElseThrow();

        assertEquals("v2026.02.2__comet.linux.exe", UpstreamMirror.mirrorFileName(real));
    }

    @Test
    @DisplayName("a missing mirror binary FAILS, naming the file and how to refill the mirror")
    void aMissingBinaryFails(@TempDir Path directory) throws IOException {
        Path root = temporaryRoot(directory);

        AssertionError failure =
                assertThrows(
                        AssertionError.class,
                        () -> UpstreamMirror.stage(root, row("a".repeat(64)), root.resolve("c")));

        String message = failure.getMessage();
        assertTrue(
                message.contains(
                                "\"v2026.02.2__comet.linux.exe\" is not in the mirror at "
                                        + root.resolve(UpstreamMirror.MIRROR)
                                                .resolve("v2026.02.2__comet.linux.exe"))
                        && message.contains("Refill it by downloading")
                        && message.contains("fails rather than skips"),
                message);
    }

    @Test
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "staging sets POSIX permissions")
    @DisplayName("a mirror binary whose SHA-256 is not the manifest's FAILS before it is run")
    void aChecksumMismatchFails(@TempDir Path directory) throws IOException {
        Path root = temporaryRoot(directory);
        Path mirror = Files.createDirectories(root.resolve(UpstreamMirror.MIRROR));
        Files.writeString(
                mirror.resolve("v2026.02.2__comet.linux.exe"), "constructed, not Comet\n");
        String pinned = "b".repeat(64);

        AssertionError failure =
                assertThrows(
                        AssertionError.class,
                        () -> UpstreamMirror.stage(root, row(pinned), root.resolve("bin/comet")));

        String message = failure.getMessage();
        assertTrue(
                message.contains("but manifests/tools.json pins " + pinned)
                        && message.contains("is not run")
                        && message.contains("refill the mirror"),
                message);
    }

    @Test
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "staging sets POSIX permissions")
    @DisplayName("a mirror binary with the manifest's SHA-256 is staged, executable, unchanged")
    void aMatchingBinaryIsStaged(@TempDir Path directory) throws IOException {
        Path root = temporaryRoot(directory);
        Path mirror = Files.createDirectories(root.resolve(UpstreamMirror.MIRROR));
        Path source = mirror.resolve("v2026.02.2__comet.linux.exe");
        Files.writeString(source, "constructed, not Comet\n", StandardCharsets.UTF_8);

        Path staged =
                UpstreamMirror.stage(
                        root, row(UpstreamMirror.sha256(source)), root.resolve("bin/comet"));

        assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(staged));
        assertTrue(Files.isExecutable(staged));
    }

    @Test
    @DisplayName("SHA-256 is computed correctly: the empty-file vector")
    void sha256OfNothing(@TempDir Path directory) throws IOException {
        Path empty = Files.createFile(directory.resolve("empty"));

        assertEquals(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                UpstreamMirror.sha256(empty));
    }

    @Test
    @DisplayName("the repository root is found by walking upward, as the sandbox copy needs")
    void theRootIsFoundAbove(@TempDir Path directory) throws IOException {
        Path root = temporaryRoot(directory);
        Path deep = Files.createDirectories(root.resolve("a/b/c"));

        assertEquals(root.toAbsolutePath(), UpstreamMirror.repositoryRootAbove(deep));
        assertEquals(root.toAbsolutePath(), UpstreamMirror.repositoryRootAbove(root));
    }

    @Test
    @DisplayName("and with no manifests directory anywhere above, that fails rather than guessing")
    void noRootFails(@TempDir Path directory) throws IOException {
        Path deep = Files.createDirectories(directory.resolve("x/y"));
        /* Only meaningful when no ancestor of the temporary directory holds manifests/. */
        boolean ancestorHasOne = false;
        for (Path cursor = directory; cursor != null; cursor = cursor.getParent()) {
            ancestorHasOne |= Files.isDirectory(cursor.resolve("manifests"));
        }
        if (ancestorHasOne) {
            throw new AssertionError(
                    "an ancestor of " + directory + " holds manifests/, so this case is invalid");
        }

        AssertionError failure =
                assertThrows(AssertionError.class, () -> UpstreamMirror.repositoryRootAbove(deep));
        assertTrue(failure.getMessage().contains("holds a manifests directory"));
    }
}
