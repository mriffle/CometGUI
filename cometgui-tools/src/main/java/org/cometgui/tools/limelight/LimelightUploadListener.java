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

/**
 * Hears an upload as it happens: each step as it starts and ends, and the bytes of the file as they
 * are sent. Called on the uploading thread or the HTTP client's own threads, never on the JavaFX
 * application thread; a listener that touches the interface hops threads itself. Nothing it is
 * given is secret: no call carries the key.
 */
public interface LimelightUploadListener {

    /** A listener that hears nothing. */
    LimelightUploadListener NONE = new LimelightUploadListener() {};

    /**
     * A step is about to be sent.
     *
     * @param step the step
     */
    default void stepStarted(LimelightUploadStep step) {
        // nothing by default
    }

    /**
     * The server answered a step.
     *
     * @param step the step
     * @param accepted whether it answered {@code statusSuccess="true"}
     */
    default void stepAnswered(LimelightUploadStep step, boolean accepted) {
        // nothing by default
    }

    /**
     * More of the file has been handed to the network.
     *
     * @param sent the bytes sent so far
     * @param total the file's size
     */
    default void bytesSent(long sent, long total) {
        // nothing by default
    }
}
