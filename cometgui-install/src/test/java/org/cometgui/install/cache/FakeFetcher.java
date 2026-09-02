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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.cometgui.install.download.ArtefactFetcher;
import org.cometgui.install.download.DownloadReport;
import org.cometgui.install.download.DownloadRequest;

/**
 * A fetcher that serves bytes a test chose, so that the real {@code VerifiedDownloader} runs.
 *
 * <p>The installer is always given the production composition of fetch-and-verify, and corruption
 * is expressed by serving different bytes rather than by faking a verdict. That way a test about a
 * corrupted download exercises the same {@code R-SEC-02} rejection the product performs, rather
 * than a test double's idea of it.
 */
final class FakeFetcher implements ArtefactFetcher {

    /** What each URL answers with. */
    private final Map<URI, byte[]> served = new LinkedHashMap<>();

    /** Every URL that was asked for, in order. */
    private final List<URI> requested = new ArrayList<>();

    /** Run before each transfer, so a test can make one slow or make one crash. */
    private Runnable beforeEachTransfer = () -> {};

    /**
     * Serves some bytes at a URL.
     *
     * @param url the URL
     * @param body the bytes
     * @return this fetcher
     */
    FakeFetcher serve(URI url, byte[] body) {
        served.put(url, body);
        return this;
    }

    /**
     * Runs something before every transfer.
     *
     * @param action what to run
     * @return this fetcher
     */
    FakeFetcher before(Runnable action) {
        this.beforeEachTransfer = action;
        return this;
    }

    /**
     * Every URL that was asked for, in order.
     *
     * @return the requested URLs
     */
    List<URI> requested() {
        return List.copyOf(requested);
    }

    @Override
    public DownloadReport fetch(DownloadRequest request) throws IOException {
        beforeEachTransfer.run();
        byte[] body = served.get(request.source());
        if (body == null) {
            throw new IOException("nothing is served at " + request.source());
        }
        requested.add(request.source());
        Path parent = request.destination().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(request.destination(), body);
        request.listener().onProgress(body.length, body.length);
        return new DownloadReport(
                request.source(),
                request.destination(),
                200,
                false,
                0L,
                body.length,
                body.length,
                body.length);
    }
}
