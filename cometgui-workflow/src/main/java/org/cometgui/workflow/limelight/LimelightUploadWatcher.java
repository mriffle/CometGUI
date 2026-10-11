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

/**
 * Hears an upload live, for the Limelight section: each line of {@code logs/limelight-upload.log}
 * as it is written (already through the one redactor), and the bytes of the file as they are sent.
 * Called on the uploading thread or the HTTP client's threads, never on the JavaFX application
 * thread.
 */
@FunctionalInterface
public interface LimelightUploadWatcher {

    /** A watcher that hears nothing. */
    LimelightUploadWatcher NONE = line -> {};

    /**
     * One line of the upload log, as written.
     *
     * @param line the line, redacted, without its time stamp
     */
    void line(String line);

    /**
     * More of the file has been sent.
     *
     * @param sent bytes so far
     * @param total the file's size
     */
    default void bytesSent(long sent, long total) {
        // nothing by default
    }
}
