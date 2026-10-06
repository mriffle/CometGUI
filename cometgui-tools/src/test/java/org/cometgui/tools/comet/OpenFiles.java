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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The file descriptors this JVM holds open on a file, read from Linux's {@code /proc/self/fd}: how
 * a test proves a reader closed its file, which no return value can show.
 */
final class OpenFiles {

    private OpenFiles() {}

    /**
     * The open descriptors whose target is one of the files.
     *
     * @param files the files, which must exist
     * @return the descriptors, empty when none is open
     * @throws IOException if a file's real path or the descriptor table cannot be read
     */
    static List<Path> to(Path... files) throws IOException {
        List<Path> targets = new ArrayList<>();
        for (Path file : files) {
            targets.add(file.toRealPath());
        }
        List<Path> open = new ArrayList<>();
        try (DirectoryStream<Path> descriptors =
                Files.newDirectoryStream(TestPaths.absolute("proc/self/fd"))) {
            for (Path descriptor : descriptors) {
                Path target;
                try {
                    target = Files.readSymbolicLink(descriptor);
                } catch (IOException closedWhileListing) {
                    continue; // the directory stream's own descriptor, gone by now
                }
                if (targets.contains(target)) {
                    open.add(descriptor);
                }
            }
        }
        return open;
    }
}
