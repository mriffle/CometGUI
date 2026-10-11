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

package org.cometgui.workflow.limelight;

import java.nio.file.Path;
import java.util.Objects;
import org.cometgui.domain.tools.ToolVersion;

/**
 * An installed, verified Limelight converter: the version the Tool Manager offers it as, and its
 * JAR.
 *
 * @param version the version the Tool Manager's offer names
 * @param jar the installed JAR, absolute
 */
public record LimelightConverterInstallation(ToolVersion version, Path jar) {

    /**
     * Validates the installation.
     *
     * @throws NullPointerException if a component is {@code null}
     * @throws IllegalArgumentException if the JAR's path is relative
     */
    public LimelightConverterInstallation {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(jar, "jar");
        if (!jar.isAbsolute()) {
            throw new IllegalArgumentException(
                    "the Limelight converter's JAR path must be absolute, but was: " + jar);
        }
    }
}
