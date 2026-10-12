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

package org.cometgui.ui.viewmodel.limelight;

import java.nio.file.Path;
import org.cometgui.domain.run.RunId;

/**
 * A destination the workflow checked and the section shows before anything is sent ({@code
 * R-SEC-04}: "shall show the exact destination server and project before sending"): exactly what an
 * upload would send, and where.
 *
 * <p>The port makes these ({@link LimelightUploadPort#prepare}) and takes them back ({@link
 * LimelightUploadPort#send}); the section only shows them and hands one back when the scientist
 * presses Send. Each accessor's value is the workflow's own: the server as normalised, the file's
 * size and SHA-256 as hashed when the destination was checked.
 */
public interface LimelightUploadPreview {

    /**
     * The run whose file would be sent.
     *
     * @return its id
     */
    RunId run();

    /**
     * The server, normalised.
     *
     * @return for example {@code https://limelight.example.org/limelight}
     */
    String server();

    /**
     * The project's id.
     *
     * @return it, positive
     */
    int projectId();

    /**
     * Where the scientist sees the project's imports.
     *
     * @return {@code <server>/d/pg/project/<id>}
     */
    String projectPage();

    /**
     * The Limelight XML file that would be sent.
     *
     * @return its absolute path
     */
    Path file();

    /**
     * Its size.
     *
     * @return bytes
     */
    long size();

    /**
     * Its SHA-256, as hashed when the destination was checked.
     *
     * @return the digest
     */
    String sha256();
}
