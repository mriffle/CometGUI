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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

/**
 * The repository root, the gitignored mirror of real upstream artefacts, and a binary staged out of
 * it with its SHA-256 checked against the artefact manifest.
 *
 * <p>The same rules as {@code org.cometgui.tools.testing.UpstreamArtefacts} in {@code
 * cometgui-tools}, written again here rather than shared, for the reason that class gives: test
 * classes are not a published artefact, and a test-jar dependency on another module would drag its
 * test tree into this module's mutation run. The behaviour that matters is identical and
 * deliberate:
 *
 * <ul>
 *   <li>the root is found by <strong>walking upward</strong> to the first directory holding {@code
 *       manifests/}, so the test works from the module directory, from the reactor root, and from
 *       {@code scripts/verify-test-gates.sh}'s sandbox copy of the module;
 *   <li>a missing artefact <strong>fails</strong>, it never skips: a fixture suite that quietly
 *       stopped running the real binary would be a check that cannot go red;
 *   <li>a staged binary whose SHA-256 differs from the manifest's fails before it is run.
 * </ul>
 */
public final class UpstreamMirror {

    /** Where the mirror lives, relative to the repository root. */
    public static final String MIRROR = "scratch/phase05/artefacts";

    /** The artefact manifest, relative to the repository root. */
    public static final String MANIFEST = "manifests/tools.json";

    private static final String REFILL =
            " The mirror is gitignored and holds the bytes upstream publishes, named"
                    + " <releaseTag>__<file name from the manifest URL>. Refill it by downloading"
                    + " that artefact from the \"url\" in manifests/tools.json into "
                    + MIRROR
                    + "/ under that name and checking it with sha256sum against the manifest's"
                    + " \"sha256\" before use. This test fails rather than skips, because a"
                    + " fixture suite that stops running the real binary stops proving the"
                    + " fixtures are real.";

    private UpstreamMirror() {}

    /**
     * The repository root, found by walking up from the current working directory.
     *
     * @return the root
     * @throws AssertionError if no ancestor holds a {@code manifests} directory
     */
    public static Path repositoryRoot() {
        return repositoryRootAbove(Path.of("").toAbsolutePath());
    }

    /**
     * The repository root, found by walking up from {@code start}.
     *
     * @param start the directory to start from, itself included
     * @return the first directory at or above {@code start} that holds a {@code manifests}
     *     directory
     * @throws AssertionError if there is none
     */
    public static Path repositoryRootAbove(Path start) {
        Path cursor = start.toAbsolutePath();
        while (cursor != null && !Files.isDirectory(cursor.resolve("manifests"))) {
            cursor = cursor.getParent();
        }
        if (cursor == null) {
            throw new AssertionError(
                    "no directory at or above " + start + " holds a manifests directory");
        }
        return cursor;
    }

    /**
     * The mirror's file name for an artefact: {@code <releaseTag>__<last segment of its URL>}.
     *
     * @param row the manifest row
     * @return the file name in the mirror
     */
    public static String mirrorFileName(CometManifest.Row row) {
        String url = row.url();
        return row.releaseTag() + "__" + url.substring(url.lastIndexOf('/') + 1);
    }

    /**
     * One artefact in the mirror under a given repository root.
     *
     * @param root the repository root
     * @param row the manifest row naming the artefact
     * @return the mirrored file
     * @throws AssertionError if it is not there, with a message saying how to refill the mirror
     */
    public static Path artefact(Path root, CometManifest.Row row) {
        String fileName = mirrorFileName(row);
        Path file = root.resolve(MIRROR).resolve(fileName);
        if (!Files.isRegularFile(file)) {
            throw new AssertionError(
                    "the real Comet "
                            + row.version()
                            + " "
                            + row.platform()
                            + " binary \""
                            + fileName
                            + "\" is not in the mirror at "
                            + file
                            + "."
                            + REFILL);
        }
        return file;
    }

    /**
     * Copies the mirrored binary for {@code row} to {@code destination}, makes it executable, and
     * checks its SHA-256 against the manifest's before returning it.
     *
     * @param root the repository root holding the mirror
     * @param row the manifest row whose binary to stage
     * @param destination where to put it
     * @return {@code destination}, executable and verified
     * @throws IOException if it cannot be copied or read
     * @throws AssertionError if the artefact is absent or its SHA-256 is not the manifest's
     */
    public static Path stage(Path root, CometManifest.Row row, Path destination)
            throws IOException {
        Path source = artefact(root, row);
        Path parent = destination.toAbsolutePath().getParent();
        if (parent == null) {
            throw new AssertionError("a staged binary needs a directory, and has none");
        }
        Files.createDirectories(parent);
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        Files.setPosixFilePermissions(
                destination,
                Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE));
        String actual = sha256(destination);
        if (!actual.equals(row.sha256())) {
            throw new AssertionError(
                    "the mirrored Comet "
                            + row.version()
                            + " "
                            + row.platform()
                            + " binary at "
                            + source
                            + " has SHA-256 "
                            + actual
                            + " but manifests/tools.json pins "
                            + row.sha256()
                            + "; it is not the binary the fixtures were captured from and is not"
                            + " run. Delete it and refill the mirror."
                            + REFILL);
        }
        return destination;
    }

    /**
     * The SHA-256 of a file, in lower-case hexadecimal.
     *
     * @param file the file
     * @return the digest
     * @throws IOException if the file cannot be read
     */
    public static String sha256(Path file) throws IOException {
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
}
