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

package org.cometgui.tools.pdv.fidelity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Where the fidelity suite writes: a fresh directory under the module's build directory, so that
 * nothing derived from K562 is ever written where it could be committed ({@code D-006}).
 */
final class FidelityFiles {

    private FidelityFiles() {}

    /**
     * A fresh directory under the build directory.
     *
     * @param prefix its name's prefix
     * @return the directory
     * @throws IOException if it cannot be made
     */
    static Path workDirectory(String prefix) throws IOException {
        Path build =
                Path.of(System.getProperty("cometgui.buildDirectory", "target")).toAbsolutePath();
        Files.createDirectories(build);
        return Files.createTempDirectory(build, prefix);
    }

    /**
     * Deletes a directory and everything in it.
     *
     * @param directory the directory, or {@code null}
     * @throws IOException if something cannot be deleted
     */
    static void delete(Path directory) throws IOException {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        List<Path> paths;
        try (Stream<Path> walk = Files.walk(directory)) {
            paths = walk.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path path : paths) {
            Files.deleteIfExists(path);
        }
    }

    /**
     * Every file under a directory.
     *
     * @param directory the directory
     * @return the files, sorted
     * @throws IOException if it cannot be walked
     */
    static List<Path> files(Path directory) throws IOException {
        try (Stream<Path> walk = Files.walk(directory)) {
            return walk.filter(Files::isRegularFile).sorted().toList();
        }
    }
}
