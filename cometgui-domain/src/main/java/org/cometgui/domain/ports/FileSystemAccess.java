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

package org.cometgui.domain.ports;

import java.io.IOException;
import java.nio.file.Path;

/**
 * The filesystem seam {@code R-PROC-01} asks for "where useful".
 *
 * <p>It is deliberately the smallest set of questions the shell and the host-baseline check
 * actually ask, plus the one directory the application owns. Later phases widen it -- reading and
 * writing files, atomic installs, temporary directories -- and each addition should be driven by a
 * caller that exists, not by symmetry: a wide filesystem port is a second {@link
 * java.nio.file.Files} that every fake has to implement.
 *
 * <p>Production code uses a {@code java.nio.file}-backed implementation; tests use an in-memory
 * fake, which is what makes a test for "the application data directory does not exist yet" cheap.
 */
public interface FileSystemAccess {

    /**
     * Tests whether a path exists.
     *
     * @param path the path to test
     * @return {@code true} if the path exists
     * @throws NullPointerException if {@code path} is {@code null}
     */
    boolean exists(Path path);

    /**
     * Tests whether a path exists and is readable by this process.
     *
     * @param path the path to test
     * @return {@code true} if the path is readable
     * @throws NullPointerException if {@code path} is {@code null}
     */
    boolean isReadable(Path path);

    /**
     * Tests whether a path exists and is a directory.
     *
     * @param path the path to test
     * @return {@code true} if the path is a directory
     * @throws NullPointerException if {@code path} is {@code null}
     */
    boolean isDirectory(Path path);

    /**
     * Creates a directory and every missing parent, doing nothing if it already exists.
     *
     * @param path the directory to create
     * @throws IOException if the directory cannot be created
     * @throws NullPointerException if {@code path} is {@code null}
     */
    void createDirectories(Path path) throws IOException;

    /**
     * The directory this installation keeps its own state in -- the tool cache, downloads and
     * application settings.
     *
     * <p>It is a port method rather than a constant because the location is per-platform and a test
     * must be able to point it at a temporary directory instead of the real home directory. The
     * directory is not guaranteed to exist; the caller creates it.
     *
     * @return the application data directory, never {@code null}
     */
    Path applicationDataDirectory();
}
