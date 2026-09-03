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

package org.cometgui.tools.testing;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The real upstream artefacts, from the gitignored mirror, and the binaries taken out of them.
 *
 * <p><strong>A deliberate copy of the three public members of {@code
 * org.cometgui.install.archive.ArtefactMirror}</strong>, for the reason {@code
 * org.cometgui.workflow.testing.Nulls} and {@code org.cometgui.app.testing.FxToolkit} give for
 * theirs: test classes are not published as an artefact, so this module cannot reuse another
 * module's test tree without turning it into a shipped test-jar. Here that is worse than usual --
 * {@code cometgui-tools} does not depend on {@code cometgui-install} at all, and a test-scoped
 * dependency would put that module's two hundred test classes inside {@code targetTests} for this
 * module's own mutation run. Phase 05's report records the duplication rather than hiding it.
 *
 * <p>A test that needs the mirror <strong>fails</strong> when it is absent rather than skipping: a
 * probe suite that quietly stopped running the real binaries would be a check that cannot go red,
 * and the message below says how to refill it.
 */
public final class UpstreamArtefacts {

    /** Where the mirror lives, relative to the repository root. */
    public static final String MIRROR = "scratch/phase05/artefacts";

    private UpstreamArtefacts() {}

    /**
     * The repository root, found by walking up from the module's working directory.
     *
     * @return the root
     */
    public static Path repositoryRoot() {
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
    public static Path artefact(String fileName) {
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
                            + " fails rather than skips, because a probe suite that stops running"
                            + " the real binaries stops proving anything.");
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

    private static Path parentOf(Path file) {
        Path parent = file.getParent();
        if (parent == null) {
            throw new AssertionError(
                    "a staged artefact needs a directory to go in, and " + file + " has none");
        }
        return parent;
    }

    /**
     * Copies one named member out of a mirrored ZIP.
     *
     * @param archiveFileName the mirror's file name for the archive
     * @param member the entry name inside it, as the artefact manifest names it
     * @param destination where to write it
     * @return the file written
     * @throws IOException if the archive cannot be read or the member is absent
     */
    public static Path member(String archiveFileName, String member, Path destination)
            throws IOException {
        Path archive = artefact(archiveFileName);
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            ZipEntry entry = zip.getEntry(member);
            if (entry == null) {
                throw new AssertionError(
                        "the archive " + archive + " holds no member named \"" + member + "\"");
            }
            Files.createDirectories(parentOf(destination));
            try (InputStream in = zip.getInputStream(entry)) {
                Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return destination;
    }

    /**
     * Copies one named member out of a mirrored ZIP and makes it executable.
     *
     * @param archiveFileName the mirror's file name for the archive
     * @param member the entry name inside it
     * @param destination where to write it
     * @return the file written, executable
     * @throws IOException if the archive cannot be read or the member is absent
     */
    public static Path executableMember(String archiveFileName, String member, Path destination)
            throws IOException {
        Path staged = member(archiveFileName, member, destination);
        Files.setPosixFilePermissions(
                staged,
                Set.of(
                        java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                        java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                        java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE));
        return staged;
    }

    /**
     * Copies a mirrored bare file to a destination and makes it executable.
     *
     * @param fileName the mirror's file name
     * @param destination where to write it
     * @return the file written, executable
     * @throws IOException if it cannot be copied
     */
    public static Path executableCopy(String fileName, Path destination) throws IOException {
        Files.createDirectories(parentOf(destination));
        Files.copy(artefact(fileName), destination, StandardCopyOption.REPLACE_EXISTING);
        Files.setPosixFilePermissions(
                destination,
                Set.of(
                        java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                        java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                        java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE));
        return destination;
    }
}
