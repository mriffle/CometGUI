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

import java.io.IOException;

/**
 * Gets the bytes of one artefact onto disk and says what it did.
 *
 * <p>The seam between "moving bytes" and "deciding whether to keep them". {@link
 * org.cometgui.install.verify.VerifiedDownloader} composes the two and needs to be able to say, in
 * a test, that a failed checksum caused a <em>restart from zero</em> rather than a second resume --
 * which is a claim about the requests that were made, not about the file that resulted. Depending
 * on this interface rather than on {@link HttpDownloader} is what makes that claim checkable
 * without a server.
 *
 * <p>It is deliberately narrower than {@link org.cometgui.domain.ports.Downloader}: that port has
 * no cancellation, no resume and no report, because the domain does not need them. This one is the
 * installer's own vocabulary and stays inside {@code org.cometgui.install}.
 */
@FunctionalInterface
public interface ArtefactFetcher {

    /**
     * Performs one transfer.
     *
     * @param request what to fetch, where to put it, and whether a partial file may be continued
     * @return what the transfer did: the status, whether a range was asked for, how many bytes were
     *     kept and how many arrived
     * @throws ArtefactUnavailableException if the artefact is no longer at its URL
     * @throws TruncatedDownloadException if the body stopped short of a declared length
     * @throws DownloadCancelledException if the caller asked for the transfer to stop
     * @throws IOException if the transfer failed for any other reason
     * @throws NullPointerException if {@code request} is {@code null}
     */
    DownloadReport fetch(DownloadRequest request) throws IOException;
}
