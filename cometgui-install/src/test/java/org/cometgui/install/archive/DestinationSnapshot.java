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
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Every path under a directory, so that a rejection can be checked against the disk rather than
 * against the exception.
 *
 * <p>"Nothing was written outside the destination" is a claim about the file system, and an
 * exception is not evidence for it: a guard can throw after it has already written. So each
 * rejection test snapshots the destination's <strong>parent</strong> before the attempt and
 * compares it afterwards, which sees a file placed anywhere the traversal was aiming at.
 */
final class DestinationSnapshot {

    private DestinationSnapshot() {}

    /**
     * Every path under a directory, relative to it and sorted, with symbolic links listed but not
     * followed.
     *
     * @param root the directory
     * @return the paths
     * @throws IOException if the tree cannot be walked
     */
    static List<String> of(Path root) throws IOException {
        List<String> paths = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(path -> !path.equals(root))
                    .map(path -> root.relativize(path).toString())
                    .sorted()
                    .forEach(paths::add);
        }
        return paths;
    }

    /**
     * Asserts that a directory tree is exactly as it was.
     *
     * @param root the directory
     * @param before the snapshot taken before the attempt
     * @param what what was attempted, for the message
     * @throws IOException if the tree cannot be walked
     */
    static void assertUnchanged(Path root, List<String> before, String what) throws IOException {
        assertEquals(
                before,
                of(root),
                () ->
                        "something was written outside the destination while "
                                + what
                                + "; the tree under "
                                + root
                                + " changed");
    }

    /**
     * Every path under a root that is not inside one directory of it.
     *
     * <p>The destination is expected to change -- an extraction that rejects its fourth entry has
     * legitimately written the first three -- so what has to be proved is that nothing appeared
     * anywhere else.
     *
     * @param root the tree to walk
     * @param destination the directory whose contents are allowed to change
     * @return the paths outside it, relative to the root and sorted
     * @throws IOException if the tree cannot be walked
     */
    static List<String> outside(Path root, Path destination) throws IOException {
        String prefix = root.relativize(destination).toString();
        List<String> paths = new ArrayList<>();
        for (String path : of(root)) {
            if (!path.equals(prefix) && !path.startsWith(prefix + java.io.File.separator)) {
                paths.add(path);
            }
        }
        return paths;
    }

    /**
     * Asserts that nothing appeared outside the destination.
     *
     * @param root the tree to walk
     * @param destination the directory whose contents are allowed to change
     * @param before the snapshot taken before the attempt
     * @param what what was attempted, for the message
     * @throws IOException if the tree cannot be walked
     */
    static void assertNothingOutside(Path root, Path destination, List<String> before, String what)
            throws IOException {
        assertEquals(
                before,
                outside(root, destination),
                () ->
                        "something was written outside the destination while "
                                + what
                                + "; the tree under "
                                + root
                                + ", ignoring "
                                + destination
                                + ", changed");
    }

    /**
     * Asserts that a path does not exist, following no links.
     *
     * @param path the path
     */
    static void assertAbsent(Path path) {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new AssertionError(
                    "the guard rejected the entry and yet " + path + " exists on disk");
        }
    }
}
