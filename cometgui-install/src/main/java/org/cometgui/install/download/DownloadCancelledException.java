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
 * The caller stopped the transfer, and nothing was left behind.
 *
 * <p>Cancelling is not failing, and this type exists so that the two cannot be confused by a {@code
 * catch (IOException)}. By the time it is thrown the partial file and its resume state have been
 * deleted and the destination has never existed, so no later install step can find something that
 * looks like a finished download.
 *
 * <p>{@link #bytesTransferred()} is the evidence that the cancellation happened <em>during</em> the
 * transfer rather than before it started: a test that only checks that the call returned cannot
 * tell a cancellation from a downloader that never connected.
 */
public final class DownloadCancelledException extends DownloadException {

    private static final long serialVersionUID = 1L;

    /** Bytes that had arrived when the caller asked to stop. */
    private final long bytesTransferred;

    /**
     * Creates the cancellation.
     *
     * @param source the URL the transfer was for
     * @param bytesTransferred how many bytes had arrived when the caller asked to stop
     */
    DownloadCancelledException(URI source, long bytesTransferred) {
        super(
                "the download was cancelled after "
                        + bytesTransferred
                        + " byte(s) from "
                        + source
                        + "; the partial file and its resume state have been deleted and no"
                        + " destination file was created",
                source);
        this.bytesTransferred = bytesTransferred;
    }

    /**
     * How much had arrived when the caller asked to stop.
     *
     * @return the bytes received before the cancellation was noticed
     */
    public long bytesTransferred() {
        return bytesTransferred;
    }
}
