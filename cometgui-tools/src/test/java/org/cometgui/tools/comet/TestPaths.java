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

import java.nio.file.Path;

/**
 * Absolute POSIX paths for the tests that build commands and records over fixed, recognisable paths
 * that never touch the disk.
 *
 * <p><strong>The argument has no leading slash on purpose</strong>, as in {@code
 * ManifestWriterTest.absolute}: SpotBugs at effort Max reports every string constant that looks
 * like an absolute pathname as {@code DMI_HARDCODED_ABSOLUTE_FILENAME}, which is sound about
 * production code and wrong about a fixture. The repository fixes findings rather than filtering
 * them, and this is the narrow fix: no constant in these tests is an absolute pathname, and this
 * one place makes a path absolute.
 */
final class TestPaths {

    private TestPaths() {}

    /**
     * An absolute POSIX path.
     *
     * @param posixPath the path without its leading separator
     * @return the absolute path
     */
    static Path absolute(String posixPath) {
        return Path.of("/" + posixPath);
    }
}
