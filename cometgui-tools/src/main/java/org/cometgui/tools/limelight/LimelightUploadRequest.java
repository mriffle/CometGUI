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

package org.cometgui.tools.limelight;

import java.nio.file.Path;
import java.util.Objects;

/**
 * What one upload sends, and where: the server, the project, and the Limelight XML file with the
 * SHA-256 the server is told to expect (it recomputes it over what it receives).
 *
 * @param server the server
 * @param projectId the project's id, positive
 * @param file the Limelight XML file, absolute
 * @param sha256 its SHA-256, 64 lower-case hexadecimal characters, as the caller verified it
 */
public record LimelightUploadRequest(
        LimelightServer server, int projectId, Path file, String sha256) {

    /**
     * Checks the request.
     *
     * @throws IllegalArgumentException if the project id is not positive, the file is not absolute,
     *     or the SHA-256 is not 64 lower-case hexadecimal characters
     * @throws NullPointerException if a component is {@code null}
     */
    public LimelightUploadRequest {
        Objects.requireNonNull(server, "server");
        LimelightServer.checkedProjectId(projectId);
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(sha256, "sha256");
        if (!file.isAbsolute()) {
            throw new IllegalArgumentException("the file to upload must be absolute: " + file);
        }
        if (file.getFileName() == null) {
            throw new IllegalArgumentException("the file to upload has no name: " + file);
        }
        if (!sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "a SHA-256 is 64 lower-case hexadecimal characters, not \"" + sha256 + "\"");
        }
    }

    /**
     * The file's name, the only part of its path the server is told.
     *
     * @return the name
     */
    public String fileName() {
        return Objects.requireNonNull(file.getFileName(), "file name").toString();
    }
}
