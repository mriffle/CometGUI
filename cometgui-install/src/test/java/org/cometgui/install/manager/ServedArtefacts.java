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

package org.cometgui.install.manager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.cometgui.domain.ports.DownloadProgressListener;
import org.cometgui.install.download.ArtefactFetcher;
import org.cometgui.install.download.DownloadReport;
import org.cometgui.install.download.DownloadRequest;
import org.cometgui.install.download.HttpDownloader;
import org.cometgui.install.download.LoopbackHttpServer;

/**
 * The real artefact bytes, over real HTTP, at the seam where production composes the downloader.
 *
 * <p><strong>Why the rewrite is here and not in the manifest.</strong> {@code
 * ArtefactValues.downloadUrl} requires https of every manifest record and {@code DownloadUrls}
 * carves out plain HTTP for a loopback literal one layer lower, in {@link DownloadRequest} -- so a
 * record cannot carry a {@code http://127.0.0.1} URL, and should not be able to. The source is
 * therefore rewritten at the {@link ArtefactFetcher} seam, which is exactly where production puts
 * the real {@link HttpDownloader}: everything below this class is the product's own transfer,
 * including the between-chunks cancellation check that these tests are about.
 *
 * <p>A {@link org.cometgui.install.cache.ToolProbe} can be faked and a transfer cannot, which is
 * the whole point: a fetcher that ignores cancellation grades a step boundary and calls it a
 * transfer.
 */
final class ServedArtefacts implements ArtefactFetcher, AutoCloseable {

    /** The real downloader, with production timeouts. */
    private final HttpDownloader downloader = new HttpDownloader();

    /** What each request path answers with. */
    private final Map<String, Path> files = new ConcurrentHashMap<>();

    /** How many bytes of a URL may arrive before the cancellation is tripped. */
    private final Map<URI, Long> cancelAfter = new ConcurrentHashMap<>();

    /** The count the listener saw when it tripped, per URL. */
    private final Map<URI, Long> trippedAt = new ConcurrentHashMap<>();

    /** Every manifest URL that was asked for, in order. */
    private final List<URI> requested = Collections.synchronizedList(new java.util.ArrayList<>());

    /** The server, on a free loopback port. */
    private final LoopbackHttpServer server;

    /** Held closed while a test arranges the handle it is about to cancel with. */
    private volatile CountDownLatch gate = new CountDownLatch(0);

    /** What tripping does, set by the test that is cancelling. */
    private volatile Runnable onThreshold = () -> {};

    ServedArtefacts() throws IOException {
        this.server = new LoopbackHttpServer(this::respond);
    }

    /**
     * Serves a file at the path a manifest URL names.
     *
     * @param manifestUrl the URL the manifest pins
     * @param file the bytes to serve there
     * @return this
     */
    ServedArtefacts serve(URI manifestUrl, Path file) {
        files.put(manifestUrl.getPath(), file);
        return this;
    }

    /**
     * Trips {@code action} once this many bytes of one URL have arrived.
     *
     * @param manifestUrl the transfer to cancel inside
     * @param bytes how many bytes to let through first
     * @param action what to do, normally the install handle's {@code cancel}
     * @return this
     */
    ServedArtefacts cancelAfter(URI manifestUrl, long bytes, Runnable action) {
        cancelAfter.put(manifestUrl, bytes);
        onThreshold = action;
        return this;
    }

    /** Holds every transfer until {@link #release()}, so a test can take the handle first. */
    void holdTransfers() {
        gate = new CountDownLatch(1);
    }

    /** Lets held transfers run. */
    void release() {
        gate.countDown();
    }

    /**
     * How many bytes had arrived when the listener tripped the cancellation.
     *
     * @param manifestUrl the transfer
     * @return the count, or empty if that transfer never reached the threshold
     */
    Optional<Long> trippedAt(URI manifestUrl) {
        return Optional.ofNullable(trippedAt.get(manifestUrl));
    }

    /**
     * Every manifest URL that was fetched, in order.
     *
     * @return the URLs
     */
    List<URI> requested() {
        return List.copyOf(requested);
    }

    @Override
    public DownloadReport fetch(DownloadRequest request) throws IOException {
        await();
        URI manifestUrl = request.source();
        requested.add(manifestUrl);
        Long threshold = cancelAfter.get(manifestUrl);
        DownloadProgressListener listener =
                threshold == null
                        ? request.listener()
                        : tripping(manifestUrl, threshold, request.listener());
        return downloader.fetch(
                new DownloadRequest(
                        server.uri(manifestUrl.getPath()),
                        request.destination(),
                        listener,
                        request.cancellation(),
                        request.resume(),
                        request.expectedSha256()));
    }

    @Override
    public void close() throws IOException {
        server.close();
    }

    private DownloadProgressListener tripping(
            URI manifestUrl, long threshold, DownloadProgressListener delegate) {
        return (transferred, total) -> {
            delegate.onProgress(transferred, total);
            if (transferred >= threshold
                    && trippedAt.putIfAbsent(manifestUrl, transferred) == null) {
                onThreshold.run();
            }
        };
    }

    private void await() throws IOException {
        try {
            if (!gate.await(60, TimeUnit.SECONDS)) {
                throw new IOException("the test never released the transfer gate");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while waiting for the transfer gate", interrupted);
        }
    }

    /*
     * Streamed from the file rather than read into a byte[]: PDV is 103 407 417 bytes and a
     * responder that buffers it holds a hundred megabytes per test for no reason.
     */
    private void respond(LoopbackHttpServer.Request request, OutputStream out) throws IOException {
        Path file = files.get(request.path());
        if (file == null) {
            LoopbackHttpServer.head(out, 404, "Not Found", "Content-Length: 0");
            return;
        }
        long size = Files.size(file);
        long from = LoopbackHttpServer.rangeStart(request).map(Integer::longValue).orElse(0L);
        if (from > 0) {
            LoopbackHttpServer.head(
                    out,
                    206,
                    "Partial Content",
                    "Content-Length: " + (size - from),
                    "Content-Range: bytes " + from + "-" + (size - 1) + "/" + size,
                    "Accept-Ranges: bytes",
                    "ETag: \"served\"");
        } else {
            LoopbackHttpServer.head(
                    out,
                    200,
                    "OK",
                    "Content-Length: " + size,
                    "Accept-Ranges: bytes",
                    "ETag: \"served\"");
        }
        try (InputStream in = Files.newInputStream(file)) {
            in.skipNBytes(from);
            in.transferTo(out);
        }
    }
}
