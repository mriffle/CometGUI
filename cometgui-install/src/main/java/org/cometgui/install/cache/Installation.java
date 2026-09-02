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

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.cometgui.domain.tools.ToolCapability;

/**
 * A tool that is installed in the cache, and how it got that way.
 *
 * <p>{@link #alreadyInstalled()} is not a detail. {@code R-TOOL-05} allows concurrent installation
 * of the same artefact to be handled "by a lock file or by being made idempotent", and this
 * installer does both: the second process waits for the lock, then finds a complete entry and
 * returns it without downloading anything. Distinguishing the two outcomes is what lets a test
 * prove that <em>exactly one</em> of two processes did the work -- which is the observation that
 * goes red if the lock is removed.
 *
 * @param directory the tool's install directory
 * @param executable the installed executable or JAR, an absolute path
 * @param marker the completion marker that makes this entry installed
 * @param alreadyInstalled {@code true} when the entry was already complete and this call did no
 *     work, {@code false} when this call performed the install
 */
public record Installation(
        Path directory, Path executable, InstallationMarker marker, boolean alreadyInstalled) {

    /**
     * Validates the installation.
     *
     * @throws NullPointerException if any component is {@code null}
     * @throws IllegalArgumentException if the executable is not inside the directory
     */
    public Installation {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(executable, "executable");
        Objects.requireNonNull(marker, "marker");
        if (!executable.startsWith(directory)) {
            throw new IllegalArgumentException(
                    "the executable must be inside the tool directory, but "
                            + executable
                            + " is not inside "
                            + directory);
        }
    }

    /**
     * What the probe confirmed the installed build can do.
     *
     * @return the capabilities, read back from the marker, possibly empty
     */
    public List<ToolCapability> capabilities() {
        return marker.capabilities();
    }
}
