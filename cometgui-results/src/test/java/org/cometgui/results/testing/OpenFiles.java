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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * What this process holds open, from Linux's {@code /proc/self/fd} and {@code /proc/self/maps}, so
 * that a test can prove a store or reader released a file rather than merely stopped using it. On a
 * system without {@code /proc} it reports nothing, and {@link #available()} says so.
 */
public final class OpenFiles {

    private static final Path DESCRIPTORS = Path.of("/proc/self/fd");
    private static final Path MAPS = Path.of("/proc/self/maps");

    private OpenFiles() {}

    /**
     * Whether this system reports open files.
     *
     * @return {@code true} on Linux
     */
    public static boolean available() {
        return Files.isDirectory(DESCRIPTORS) && Files.isReadable(MAPS);
    }

    /**
     * Every path a file descriptor of this process points at, a deleted file's with Linux's {@code
     * " (deleted)"} suffix.
     *
     * @return the targets; empty where {@link #available()} is {@code false}
     * @throws IOException if the directory cannot be listed
     */
    public static List<String> descriptors() throws IOException {
        List<String> targets = new ArrayList<>();
        if (!available()) {
            return targets;
        }
        try (Stream<Path> links = Files.list(DESCRIPTORS)) {
            for (Path link : links.toList()) {
                try {
                    targets.add(Files.readSymbolicLink(link).toString());
                } catch (IOException closedMeanwhile) {
                    // The descriptor that listed the directory, closed by now: not a file.
                }
            }
        }
        return targets;
    }

    /**
     * Whether any descriptor points at a path containing some text.
     *
     * @param text for example a file's absolute path
     * @return {@code true} if one does
     * @throws IOException if the directory cannot be listed
     */
    public static boolean descriptorTo(String text) throws IOException {
        return descriptors().stream().anyMatch(target -> target.contains(text));
    }

    /**
     * Whether this process maps a file whose path contains some text.
     *
     * @param text for example a file's absolute path
     * @return {@code true} if one is mapped; {@code false} where {@link #available()} is not
     * @throws IOException if the maps cannot be read
     */
    public static boolean mapped(String text) throws IOException {
        return available() && Files.readString(MAPS).contains(text);
    }
}
