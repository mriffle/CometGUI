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

package org.cometgui.app.testing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.cometgui.domain.ports.DownloadProgressListener;
import org.cometgui.install.download.ArtefactFetcher;
import org.cometgui.install.download.DownloadReport;
import org.cometgui.install.download.DownloadRequest;
import org.cometgui.install.download.HttpDownloader;

/**
 * The real artefact bytes over real HTTP, at the seam production composes the downloader at.
 *
 * <p>Everything below this class is the product's own transfer: the real {@link HttpDownloader},
 * the real {@code VerifiedDownloader}, the real SHA-256, the real extractor. What is rewritten is
 * the <em>host</em> of each request, because {@code ArtefactValues} requires every manifest URL to
 * be {@code https} -- correctly -- and a test server holding a private key would be strictly worse
 * than a loopback literal, which {@code DownloadUrls} carves out for exactly this reason. The URL
 * the manifest pins still decides which file is served, so a fetch of the wrong artefact is a 404
 * rather than a silent substitution.
 *
 * <p><strong>This is a sibling of {@code org.cometgui.install.manager.ServedArtefacts} and of the
 * {@code LoopbackHttpServer} it wraps, not a reuse of them.</strong> Test classes are not published
 * as an artefact, so cometgui-app cannot see cometgui-install's test tree without turning it into a
 * shipped test-jar -- the same trade {@link FxToolkit} records for its own copy. The server is
 * trimmed to what an install needs: serve a file, honour a {@code Range}, record what was asked
 * for.
 *
 * <p>Raw sockets rather than {@code com.sun.net.httpserver}, which Checkstyle's {@code
 * IllegalImport} forbids in this repository including its test sources.
 */
public final class LoopbackArtefactServer implements ArtefactFetcher, AutoCloseable {

    /** The real downloader, with production timeouts. */
    private final HttpDownloader downloader = new HttpDownloader();

    /** What each request path answers with. */
    private final Map<String, Path> files = new ConcurrentHashMap<>();

    /** How many bytes of one URL may arrive before {@link #onThreshold} runs. */
    private final Map<URI, Long> cancelAfter = new ConcurrentHashMap<>();

    /** The byte count the listener saw when it tripped, per URL. */
    private final Map<URI, Long> trippedAt = new ConcurrentHashMap<>();

    /** Every manifest URL that was asked for, in order. */
    private final List<URI> requested = Collections.synchronizedList(new ArrayList<>());

    /** Every request the server answered, in order. */
    private final List<String> served = Collections.synchronizedList(new ArrayList<>());

    /** The {@code Range} header of each request, in order; empty where none was sent. */
    private final List<Optional<String>> ranges = Collections.synchronizedList(new ArrayList<>());

    private final ServerSocket socket;

    private final Thread acceptor;

    /** What tripping does, set by the test that is cancelling. */
    private volatile Runnable onThreshold = () -> {};

    /**
     * Starts a server on a free loopback port.
     *
     * @throws IOException if the socket cannot be bound
     */
    public LoopbackArtefactServer() throws IOException {
        this.socket = new ServerSocket(0, 16, InetAddress.getLoopbackAddress());
        this.acceptor = new Thread(this::acceptLoop, "cometgui-loopback-artefacts");
        this.acceptor.setDaemon(true);
        this.acceptor.start();
    }

    /**
     * Serves a file at the path one manifest URL names.
     *
     * @param manifestUrl the URL the manifest pins
     * @param file the bytes to serve there
     * @return this
     * @throws NullPointerException if either argument is {@code null}
     */
    public LoopbackArtefactServer serve(URI manifestUrl, Path file) {
        Objects.requireNonNull(manifestUrl, "manifestUrl");
        files.put(manifestUrl.getPath(), Objects.requireNonNull(file, "file"));
        return this;
    }

    /**
     * Runs {@code action} once this many bytes of one URL have reached the progress listener.
     *
     * @param manifestUrl the transfer to trip inside
     * @param bytes how many bytes to let through first
     * @param action what to do, normally pressing the row's Cancel control
     * @return this
     */
    public LoopbackArtefactServer trippingAfter(URI manifestUrl, long bytes, Runnable action) {
        cancelAfter.put(manifestUrl, bytes);
        onThreshold = Objects.requireNonNull(action, "action");
        return this;
    }

    /**
     * How many bytes had arrived when the listener tripped.
     *
     * @param manifestUrl the transfer
     * @return the count, or empty if that transfer never reached the threshold
     */
    public Optional<Long> trippedAt(URI manifestUrl) {
        return Optional.ofNullable(trippedAt.get(manifestUrl));
    }

    /**
     * Every manifest URL that was fetched, in order.
     *
     * @return the URLs
     */
    public List<URI> requested() {
        return List.copyOf(requested);
    }

    /**
     * Every request path the server answered, in order.
     *
     * @return the paths
     */
    public List<String> served() {
        return List.copyOf(served);
    }

    /**
     * The {@code Range} header of each answered request, in order.
     *
     * @return the ranges, an empty optional where the client sent none
     */
    public List<Optional<String>> ranges() {
        return List.copyOf(ranges);
    }

    @Override
    public DownloadReport fetch(DownloadRequest request) throws IOException {
        URI manifestUrl = request.source();
        requested.add(manifestUrl);
        Long threshold = cancelAfter.get(manifestUrl);
        DownloadProgressListener listener =
                threshold == null
                        ? request.listener()
                        : tripping(manifestUrl, threshold, request.listener());
        return downloader.fetch(
                new DownloadRequest(
                        URI.create(
                                "http://127.0.0.1:"
                                        + socket.getLocalPort()
                                        + manifestUrl.getPath()),
                        request.destination(),
                        listener,
                        request.cancellation(),
                        request.resume(),
                        request.expectedSha256()));
    }

    @Override
    public void close() throws IOException {
        socket.close();
        downloader.close();
        try {
            acceptor.join(5_000);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
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

    private void acceptLoop() {
        while (!socket.isClosed()) {
            try (Socket connection = socket.accept()) {
                connection.setTcpNoDelay(true);
                Map<String, String> headers = new LinkedHashMap<>();
                String path = readRequest(connection.getInputStream(), headers);
                if (path == null) {
                    continue;
                }
                served.add(path);
                ranges.add(Optional.ofNullable(headers.get("range")));
                respond(path, headers, connection.getOutputStream());
                connection.getOutputStream().flush();
            } catch (IOException closedOrCancelled) {
                /*
                 * A client that cancels mid-transfer breaks the connection while the body is being
                 * written, which is normal here and not a server failure.  Nothing is recorded,
                 * because every claim this class supports is about the requests that arrived.
                 */
                continue;
            }
        }
    }

    /*
     * Streamed from the file rather than read into a byte[]: PDV is 103 407 417 bytes and a
     * responder that buffers it holds a hundred megabytes per request for no reason.
     */
    private void respond(String path, Map<String, String> headers, OutputStream out)
            throws IOException {
        Path file = files.get(path);
        if (file == null) {
            head(out, 404, "Not Found", "Content-Length: 0");
            return;
        }
        long size = Files.size(file);
        long from = rangeStart(headers.get("range"));
        if (from > 0) {
            head(
                    out,
                    206,
                    "Partial Content",
                    "Content-Length: " + (size - from),
                    "Content-Range: bytes " + from + "-" + (size - 1) + "/" + size,
                    "Accept-Ranges: bytes",
                    "ETag: \"served\"");
        } else {
            head(
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

    private static long rangeStart(String range) {
        if (range == null) {
            return 0L;
        }
        return Long.parseLong(range.substring(range.indexOf('=') + 1, range.indexOf('-')));
    }

    private static void head(OutputStream out, int status, String reason, String... headers)
            throws IOException {
        StringBuilder text =
                new StringBuilder("HTTP/1.1 ").append(status).append(' ').append(reason);
        for (String header : headers) {
            text.append("\r\n").append(header);
        }
        out.write(
                text.append("\r\nConnection: close\r\n\r\n")
                        .toString()
                        .getBytes(StandardCharsets.ISO_8859_1));
    }

    private static String readRequest(InputStream in, Map<String, String> headers)
            throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int newlines = 0;
        int read;
        while ((read = in.read()) != -1) {
            head.write(read);
            if (read == '\n') {
                newlines++;
                if (newlines == 2) {
                    break;
                }
            } else if (read != '\r') {
                newlines = 0;
            }
        }
        String text = head.toString(StandardCharsets.ISO_8859_1);
        if (text.isBlank()) {
            return null;
        }
        String[] lines = text.split("\r\n");
        for (int index = 1; index < lines.length; index++) {
            int colon = lines[index].indexOf(':');
            if (colon > 0) {
                headers.put(
                        lines[index].substring(0, colon).toLowerCase(Locale.ROOT),
                        lines[index].substring(colon + 1).trim());
            }
        }
        String[] start = lines[0].split(" ");
        return start.length > 1 ? start[1] : "";
    }
}
