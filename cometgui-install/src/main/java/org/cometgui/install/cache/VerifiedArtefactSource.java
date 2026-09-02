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

package org.cometgui.install.cache;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import org.cometgui.domain.ports.DownloadProgressListener;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.install.download.DownloadCancellation;
import org.cometgui.install.verify.VerifiedArtefact;
import org.cometgui.install.verify.VerifiedDownloader;

/**
 * Steps 1 and 2 of the atomic install, as one collaborator: get the bytes and refuse to hand them
 * over unless they are the bytes the manifest pinned.
 *
 * <p>{@link VerifiedDownloader#fetch(URI, Path, FileHashes, long, DownloadProgressListener,
 * DownloadCancellation)} implements this signature exactly, so production wiring is a method
 * reference and nothing adapts anything.
 *
 * <p><strong>Why the installer does not compose the fetch and the check itself.</strong> The rule
 * that a resumed download failing its checksum is discarded and re-fetched from zero -- forced by a
 * measured server that ignores {@code If-Range} -- lives inside {@link VerifiedDownloader}. An
 * installer that called a fetcher and a verifier separately would either lose that rule or state it
 * a second time, and two statements of one rule is how this project's costliest defects have
 * started.
 *
 * <p>The interface exists rather than a direct dependency on the final class so that a test can
 * hand the installer a source that lies -- one that returns an artefact whose digests are not the
 * ones the manifest pins -- and watch step 2 refuse it. Without that, the installer's own {@code
 * R-SEC-02} boundary would be a check with no way to go red.
 */
@FunctionalInterface
public interface VerifiedArtefactSource {

    /**
     * Fetches one artefact and verifies it against a pinned size and digest pair.
     *
     * @param source where to fetch it from
     * @param destination where to put it
     * @param expected the digests the manifest pins
     * @param expectedSizeBytes the length the manifest pins
     * @param listener notified as bytes arrive
     * @param cancellation asked between chunks whether the caller still wants the transfer
     * @return the verified file, with what each transfer did
     * @throws IOException if the transfer fails, is cancelled, the artefact has gone from its
     *     pinned URL, or the bytes do not match the pinned SHA-256
     */
    VerifiedArtefact fetch(
            URI source,
            Path destination,
            FileHashes expected,
            long expectedSizeBytes,
            DownloadProgressListener listener,
            DownloadCancellation cancellation)
            throws IOException;
}
