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

package org.cometgui.domain.ports;

/**
 * Receives progress reports while a {@link Downloader} transfers a file.
 *
 * <p>Callbacks arrive on the downloader's own thread, never on the JavaFX application thread. A
 * listener that updates the user interface is responsible for hopping threads itself; a listener
 * that blocks stalls the transfer.
 */
@FunctionalInterface
public interface DownloadProgressListener {

    /**
     * Reports how much of the transfer has completed.
     *
     * @param bytesTransferred bytes written so far, never negative
     * @param totalBytes the expected total size, or a negative number when the server did not
     *     declare one -- which is why a caller must not divide by it without checking
     */
    void onProgress(long bytesTransferred, long totalBytes);
}
