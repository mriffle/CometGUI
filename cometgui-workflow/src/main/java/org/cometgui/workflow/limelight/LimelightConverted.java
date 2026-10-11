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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.tools.limelight.LimelightXmlSummary;

/**
 * A conversion that produced a valid Limelight XML file, now read-only at its final path and
 * recorded in the run's event log.
 *
 * @param output what the file holds, as the judge read it
 * @param hashes the file's MD5 and SHA-256, as recorded
 * @param argv the argument array that ran, after redaction, as recorded
 * @param log the converter's log for this conversion, when the process service wrote one
 */
public record LimelightConverted(
        LimelightXmlSummary output, FileHashes hashes, List<String> argv, Optional<Path> log) {

    /**
     * Validates the result and copies the argument array.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public LimelightConverted {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(hashes, "hashes");
        argv = List.copyOf(Objects.requireNonNull(argv, "argv"));
        Objects.requireNonNull(log, "log");
    }

    /**
     * The argument array that ran.
     *
     * @return the arguments, immutable
     */
    @Override
    public List<String> argv() {
        return List.copyOf(argv);
    }

    /**
     * Where the Limelight XML file is.
     *
     * @return its path
     */
    public Path file() {
        return output.file();
    }
}
