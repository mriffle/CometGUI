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

/**
 * Asked, between chunks, whether the caller still wants the transfer.
 *
 * <p>The {@link org.cometgui.domain.ports.Downloader} port has no cancellation parameter, because
 * cancellation is the installer's concern rather than the port's. This is that concern, expressed
 * as the smallest thing that can carry it: a question the transfer loop asks after every chunk it
 * writes.
 *
 * <p><strong>Cancellation is not a failure and is not reported as one.</strong> A cancelled
 * transfer raises {@link DownloadCancelledException}, deletes the partial file and the resume state
 * beside it, and never creates the destination -- so nothing is left that a later install step
 * could mistake for a finished download. That is why the two are separate types: an installer that
 * treats "the user pressed Cancel" as "upstream is broken" tells a scientist the wrong thing.
 *
 * <p>Implementations are read from the transfer thread and are usually written from another, so an
 * implementation backed by a field must make that field {@code volatile} or use an {@link
 * java.util.concurrent.atomic.AtomicBoolean}.
 */
@FunctionalInterface
public interface DownloadCancellation {

    /**
     * A cancellation that never fires.
     *
     * <p>A method, not a constant field: the transfer loop calls this on every chunk, and a shared
     * mutable field is one edit away from being the thing that cancels every download in the
     * process.
     *
     * @return a cancellation that always answers {@code false}
     */
    static DownloadCancellation never() {
        return () -> false;
    }

    /**
     * Whether the caller has asked for the transfer to stop.
     *
     * @return {@code true} to abandon the transfer at the next chunk boundary
     */
    boolean isCancelled();
}
