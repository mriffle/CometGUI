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

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

/**
 * Fetches one artefact from a URI into a file.
 *
 * <p>The seam {@code R-PROC-01} calls the "downloader". There is no implementation in this module
 * and none in this phase: phase 05 owns the tool registry and the install pipeline, and with them
 * the retry policy, the resume behaviour, the atomic move into place and the checksum verification
 * that decides whether a download is kept. This interface exists now so that everything built
 * before phase 05 can be written against it and tested with a fake that writes a fixture file.
 *
 * <p>A downloader never decides whether a file is trustworthy. It moves bytes; verification is
 * {@link HashService} and the installer's business, and keeping the two apart is what allows the
 * verification step to be tested against a deliberately corrupted download.
 */
@FunctionalInterface
public interface Downloader {

    /**
     * Downloads {@code source} to {@code destination}, replacing any file already there.
     *
     * @param source the artefact to fetch
     * @param destination the file to write
     * @param listener notified as bytes arrive; use a no-op listener rather than {@code null}
     * @throws IOException if the transfer fails or the destination cannot be written
     * @throws NullPointerException if any argument is {@code null}
     */
    void download(URI source, Path destination, DownloadProgressListener listener)
            throws IOException;
}
