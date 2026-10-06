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

package org.cometgui.domain.testing;

import java.nio.file.Path;

/**
 * Absolute paths for tests that are about naming rather than about any real file.
 *
 * <p>SpotBugs reports {@code DMI_HARDCODED_ABSOLUTE_FILENAME} for a string constant that looks like
 * an absolute path and reaches a {@link Path} -- following it through method calls -- so the
 * leading separator is added here, at run time, exactly as {@code SeededSecretArtefactSweepTest}
 * does. The project's rule is that a SpotBugs finding is fixed in the code, not excluded.
 */
public final class TestPaths {

    private TestPaths() {}

    /**
     * An absolute POSIX path built from one written without its leading separator.
     *
     * @param posixPath the path without its leading {@code /}; empty for the root
     * @return the absolute path
     */
    public static Path absolute(String posixPath) {
        return Path.of("/" + posixPath);
    }
}
