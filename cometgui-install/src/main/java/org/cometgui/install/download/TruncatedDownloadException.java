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

package org.cometgui.install.download;

import java.net.URI;

/**
 * The server declared a length and then sent less than that.
 *
 * <p>Distinct from {@link ArtefactUnavailableException} because the artefact is still there, and
 * distinct from {@link DownloadFailedException} because the two counts are known and are worth
 * printing: a body that stopped short is the shape a dropped connection and a proxy that gave up
 * both take, and it is the one download failure that is safe to retry by resuming.
 *
 * <p>The partial file is deliberately <em>kept</em> when this is raised, which is what makes the
 * resume path reachable at all.
 */
public final class TruncatedDownloadException extends DownloadException {

    private static final long serialVersionUID = 1L;

    /** Bytes the server said the artefact holds. */
    private final long declaredTotalBytes;

    /** Bytes actually on disk when the body ended. */
    private final long receivedBytes;

    /**
     * Creates the truncation failure.
     *
     * @param source the URL the body came from
     * @param declaredTotalBytes the length the server declared
     * @param receivedBytes the length actually received
     * @param cause the transport failure that ended the body, or {@code null} if it merely stopped
     */
    TruncatedDownloadException(
            URI source, long declaredTotalBytes, long receivedBytes, Throwable cause) {
        super(message(source, declaredTotalBytes, receivedBytes), source, cause);
        this.declaredTotalBytes = declaredTotalBytes;
        this.receivedBytes = receivedBytes;
    }

    private static String message(URI source, long declaredTotalBytes, long receivedBytes) {
        return "the download stopped short: the server declared "
                + declaredTotalBytes
                + " bytes and "
                + receivedBytes
                + " arrived, from "
                + source
                + ". The artefact is still published; the transfer failed. The partial file is kept"
                + " so the next attempt can resume it.";
    }

    /**
     * The length the server declared.
     *
     * @return the declared total in bytes
     */
    public long declaredTotalBytes() {
        return declaredTotalBytes;
    }

    /**
     * The length actually received.
     *
     * @return the bytes on disk when the body ended
     */
    public long receivedBytes() {
        return receivedBytes;
    }
}
