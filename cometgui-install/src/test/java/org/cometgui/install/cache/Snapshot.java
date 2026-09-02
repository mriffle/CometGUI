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

package org.cometgui.install.cache;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * Every file under a directory, by relative path, with its length and digest.
 *
 * <p>The point of the type is that two snapshots compare by value. "A failed install leaves the
 * tool cache untouched" is a claim about the whole subtree, and the honest way to check it is to
 * describe the subtree before and after and require the two descriptions to be equal -- not to
 * catch an exception and believe the code that threw it.
 *
 * @param files relative path to a {@code size sha256} description, sorted
 */
record Snapshot(Map<String, String> files) {

    /**
     * Describes a directory tree.
     *
     * @param directory the tree; a directory that does not exist describes as empty, which is what
     *     "the cache holds nothing" means before the first install
     * @return the snapshot
     * @throws IOException if the tree cannot be read
     */
    static Snapshot of(Path directory) throws IOException {
        Map<String, String> files = new TreeMap<>();
        if (!Files.isDirectory(directory)) {
            return new Snapshot(files);
        }
        Files.walkFileTree(
                directory,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                            throws IOException {
                        files.put(
                                directory.relativize(file).toString().replace('\\', '/'),
                                Files.size(file) + " " + sha256(file));
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult preVisitDirectory(
                            Path child, BasicFileAttributes attributes) {
                        if (!child.equals(directory)) {
                            files.put(
                                    directory.relativize(child).toString().replace('\\', '/') + "/",
                                    "directory");
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
        return new Snapshot(Map.copyOf(files));
    }

    private static String sha256(Path file) throws IOException {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError("every Java runtime provides SHA-256", impossible);
        }
    }

    /**
     * Whether the tree held nothing.
     *
     * @return {@code true} when there was no file and no subdirectory
     */
    boolean isEmpty() {
        return files.isEmpty();
    }
}
