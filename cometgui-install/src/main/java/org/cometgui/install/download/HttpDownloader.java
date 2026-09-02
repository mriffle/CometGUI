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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Flow;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.cometgui.domain.ports.DownloadProgressListener;
import org.cometgui.domain.ports.Downloader;

/**
 * Gets an artefact from upstream onto disk over {@code java.net.http}, and nothing else.
 *
 * <p>It never decides whether what it fetched is trustworthy. That is {@link
 * org.cometgui.install.verify.ArtefactVerifier}'s job, and keeping the two apart is what lets the
 * verification step be tested against a deliberately corrupted download.
 *
 * <h2>Designed from what the real server does</h2>
 *
 * <p>Three facts were measured against GitHub's release host before this class was written, and
 * each of them is load-bearing.
 *
 * <ul>
 *   <li><strong>A release URL is a redirect to a signed URL that expires in about an hour.</strong>
 *       {@code https://github.com/<owner>/<repo>/releases/download/<tag>/<file>} answers {@code
 *       302} with {@code content-length: 0} and points at {@code
 *       release-assets.githubusercontent.com}. {@code HttpClient}'s default redirect policy is
 *       {@link HttpClient.Redirect#NEVER}, so a downloader that forgets to change it writes a
 *       <em>zero-byte file and reports success</em>. See {@link #REDIRECT_POLICY}.
 *   <li><strong>Resume works.</strong> The asset host answers {@code accept-ranges: bytes} and a
 *       ranged request returns {@code 206} with a {@code content-range} and an {@code ETag}.
 *   <li><strong>{@code If-Range} is ignored.</strong> A deliberately stale validator was still
 *       answered {@code 206} with the partial range instead of {@code 200} with the whole body --
 *       through the redirect and again directly against the signed URL. The standard "tell me if
 *       the file changed under my partial download" mechanism therefore <em>does not work
 *       here</em>, so this class does not send {@code If-Range} and does not pretend to. It records
 *       the length and the {@code ETag} instead and treats a change in either as a reason to start
 *       again; that check is advisory, and {@link org.cometgui.install.verify.VerifiedDownloader}
 *       carries the recovery the checksum forces.
 * </ul>
 *
 * <p>Because the signature expires, a resume always re-requests the <em>original</em> URL rather
 * than a stored redirect target. Nothing here ever persists a URL; see {@link PartialDownload}.
 *
 * <h2>What it guarantees</h2>
 *
 * <ul>
 *   <li>The URL is checked before a socket is opened ({@link DownloadUrls}), and the URL the
 *       response actually came from is checked again, so a redirect cannot move the transfer
 *       somewhere the rule would have refused.
 *   <li>Bytes go to a temporary file and the destination is created by one move at the end, so no
 *       half-written artefact ever appears where an installer would look for one.
 *   <li>Progress is monotonically non-decreasing and its last report is the true size of the file.
 *       The total is negative -- never zero, never a guess -- when the server declared none.
 *   <li>Cancellation deletes the partial file and its state, creates no destination, and is
 *       reported as {@link DownloadCancelledException} rather than as a failure.
 *   <li>A connect timeout, a response timeout and a stall timeout, so a hung server is a failure
 *       rather than a hung application.
 * </ul>
 *
 * <h2>Lifecycle</h2>
 *
 * <p>Holds an {@link HttpClient}, which owns threads. Close it, or the threads outlive the work;
 * one instance is meant to be created once and shared, and it is safe to use from several threads
 * at once as long as they are not downloading to the same destination.
 */
public final class HttpDownloader implements Downloader, ArtefactFetcher, AutoCloseable {

    /**
     * Follow redirects, but never from {@code https} to {@code http}.
     *
     * <p>The default is {@link HttpClient.Redirect#NEVER}, and with the default a release download
     * fetches the body of a {@code 302} -- zero bytes -- and returns normally. {@link
     * HttpClient.Redirect#NORMAL} rather than {@code ALWAYS} because {@code ALWAYS} would follow a
     * downgrade to plain HTTP, which is the one thing {@link DownloadUrls} exists to prevent.
     */
    static final HttpClient.Redirect REDIRECT_POLICY = HttpClient.Redirect.NORMAL;

    /** How long to wait for a TCP connection and a TLS handshake. */
    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(30);

    /** How long to wait for response headers after the request has gone out. */
    public static final Duration DEFAULT_RESPONSE_TIMEOUT = Duration.ofSeconds(60);

    /**
     * How long a transfer may deliver no bytes at all before it is called stalled.
     *
     * <p>Per chunk, not per transfer: a 99 MB download over a slow link is not stalled, and a
     * whole-transfer deadline would fail exactly the download this product most needs to survive.
     */
    public static final Duration DEFAULT_STALL_TIMEOUT = Duration.ofSeconds(60);

    /** {@code bytes <start>-<end>/<total>}, where the total may be {@code *}. */
    private static final Pattern CONTENT_RANGE =
            Pattern.compile("bytes\\s+(\\d+)-(\\d+)/(\\d+|\\*)", Pattern.CASE_INSENSITIVE);

    private static final int HTTP_OK = 200;
    private static final int HTTP_PARTIAL_CONTENT = 206;
    private static final int HTTP_NOT_FOUND = 404;
    private static final int HTTP_GONE = 410;
    private static final int HTTP_RANGE_NOT_SATISFIABLE = 416;

    private final HttpClient client;
    private final Duration connectTimeout;
    private final Duration responseTimeout;
    private final Duration stallTimeout;

    /** Creates a downloader with the production timeouts. */
    public HttpDownloader() {
        this(DEFAULT_CONNECT_TIMEOUT, DEFAULT_RESPONSE_TIMEOUT, DEFAULT_STALL_TIMEOUT);
    }

    /**
     * Creates a downloader with explicit timeouts.
     *
     * <p>Public because a test that proves the stall timeout works cannot wait a production minute
     * for it, and a timeout nobody has watched fire is a timeout nobody knows works.
     *
     * @param connectTimeout how long to wait for a connection
     * @param responseTimeout how long to wait for response headers
     * @param stallTimeout how long a transfer may deliver nothing before it is called stalled
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if any duration is not positive
     */
    public HttpDownloader(
            Duration connectTimeout, Duration responseTimeout, Duration stallTimeout) {
        this.connectTimeout = requirePositive(connectTimeout, "connectTimeout");
        this.client =
                HttpClient.newBuilder()
                        .followRedirects(REDIRECT_POLICY)
                        .connectTimeout(this.connectTimeout)
                        .build();
        this.responseTimeout = requirePositive(responseTimeout, "responseTimeout");
        this.stallTimeout = requirePositive(stallTimeout, "stallTimeout");
    }

    private static Duration requirePositive(Duration value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(
                    field + " must be a positive duration, but was: " + value);
        }
        return value;
    }

    /**
     * Downloads a URI to a file, from zero, replacing anything already there.
     *
     * <p>The port carries no expected checksum, so this method never resumes: continuing a partial
     * file that nothing can be checked against is how bytes from two different artefacts get
     * spliced together. {@link #fetch(DownloadRequest)} is the entry point that can resume, and
     * {@link org.cometgui.install.verify.VerifiedDownloader} is what makes resuming safe.
     *
     * @param source the artefact to fetch
     * @param destination the file to write
     * @param listener notified as bytes arrive
     * @throws IOException if the transfer fails or the destination cannot be written
     * @throws NullPointerException if any argument is {@code null}
     */
    @Override
    public void download(URI source, Path destination, DownloadProgressListener listener)
            throws IOException {
        Objects.requireNonNull(listener, "listener");
        fetch(DownloadRequest.of(source, destination).listeningTo(listener));
    }

    /**
     * Performs one transfer, resuming a partial file if the request allows it and one is usable.
     *
     * @param request what to fetch and where to put it
     * @return what the transfer did
     * @throws ArtefactUnavailableException if the pinned URL answers 404 or 410
     * @throws TruncatedDownloadException if the body stops short of a declared length
     * @throws DownloadCancelledException if the caller asks for the transfer to stop
     * @throws DownloadFailedException if the network or the server fails
     * @throws IOException if the destination cannot be written
     * @throws NullPointerException if {@code request} is {@code null}
     */
    @Override
    public DownloadReport fetch(DownloadRequest request) throws IOException {
        Objects.requireNonNull(request, "request");
        Path destination = request.destination();
        Path parent = destination.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        PartialDownload partial = PartialDownload.beside(destination);

        Optional<PartialDownload.ResumePoint> resume = Optional.empty();
        if (request.resume()) {
            resume = partial.resumePoint();
        }
        if (resume.isEmpty()) {
            // Either the caller forbade resuming, or there is nothing safe to continue: a partial
            // file with no state file beside it is bytes of unknown provenance, and appending to
            // it would be the splice this class refuses to risk.
            partial.discard();
        }
        if (request.cancellation().isCancelled()) {
            // Asked before anything was opened. Reported the same way as a cancellation half way
            // through, because to the caller it is the same event.
            throw new DownloadCancelledException(request.source(), 0L);
        }

        DownloadReport report = transfer(request, partial, resume);
        partial.moveInto(destination);
        return report;
    }

    /** Closes the underlying {@link HttpClient} and the threads it owns. */
    @Override
    public void close() {
        client.close();
    }

    /**
     * Runs the exchange, restarting once from zero if the server or the recorded state says the
     * partial file cannot be continued.
     *
     * <p>The loop can only go round a second time while {@code resume} is present, and the second
     * pass is entered with it empty, so it terminates. Every restart trigger is guarded by that.
     */
    private DownloadReport transfer(
            DownloadRequest request,
            PartialDownload partial,
            Optional<PartialDownload.ResumePoint> resume)
            throws IOException {
        Optional<PartialDownload.ResumePoint> point = resume;
        while (true) {
            URI source = request.source();
            long offset = point.map(PartialDownload.ResumePoint::offsetBytes).orElse(0L);
            boolean rangeRequested = offset > 0;
            HttpRequest.Builder builder =
                    HttpRequest.newBuilder(source).timeout(responseTimeout).GET();
            if (rangeRequested) {
                builder.header("Range", "bytes=" + offset + "-");
            }
            HttpResponse<Flow.Publisher<List<ByteBuffer>>> response = send(builder.build(), source);

            try (ResponseBody body = ResponseBody.subscribeTo(response.body())) {
                int status = response.statusCode();
                requireFetchableFinalUri(response.uri(), source);
                if (status == HTTP_NOT_FOUND || status == HTTP_GONE) {
                    throw new ArtefactUnavailableException(
                            source, status, request.expectedSha256().orElse(null));
                }
                if (status == HTTP_RANGE_NOT_SATISFIABLE && point.isPresent()) {
                    // The partial file is longer than the artefact now is. Nothing about it can be
                    // trusted; start again with no range.
                    partial.discard();
                    point = Optional.empty();
                    continue;
                }
                requireBodyStatus(status, rangeRequested, source);

                long declaredTotal = declaredTotal(response, status, offset, source);
                String etag = response.headers().firstValue("etag").orElse("");
                if (point.isPresent()
                        && status == HTTP_PARTIAL_CONTENT
                        && !continues(point.get(), declaredTotal, etag)) {
                    // Advisory only: the artefact upstream is not the one the partial file came
                    // from, as far as anything HTTP will tell us. If-Range would be the mechanism
                    // for this and the measured server ignores it, so SHA-256 remains the only
                    // integrity authority; this merely saves a download that would surely fail.
                    partial.discard();
                    point = Optional.empty();
                    continue;
                }
                if (status == HTTP_OK && rangeRequested) {
                    // The server refused the range and sent the whole artefact: a clean restart,
                    // and the byte count in the report is what says so.
                    offset = 0L;
                }
                return copy(
                        request,
                        partial,
                        body,
                        status,
                        rangeRequested,
                        offset,
                        declaredTotal,
                        etag);
            }
        }
    }

    private HttpResponse<Flow.Publisher<List<ByteBuffer>>> send(HttpRequest request, URI source)
            throws IOException {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofPublisher());
        } catch (HttpTimeoutException e) {
            // Both timeouts land here: HttpConnectTimeoutException is a subclass, and its own
            // message says which one fired. One catch rather than two, because a second catch
            // clause for the connect case would be a branch no test on a loopback address can
            // reach -- a connection to 127.0.0.1 is refused instantly or accepted instantly, and
            // it never times out.
            throw new DownloadFailedException(
                    "timed out requesting "
                            + source
                            + ": "
                            + DownloadFailedException.describe(e)
                            + " (connect timeout "
                            + connectTimeout.toMillis()
                            + " ms, response timeout "
                            + responseTimeout.toMillis()
                            + " ms)",
                    source,
                    e);
        } catch (IOException e) {
            throw new DownloadFailedException(
                    "the request to "
                            + source
                            + " failed before any body arrived: "
                            + DownloadFailedException.describe(e),
                    source,
                    e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DownloadFailedException(
                    "the thread was interrupted while requesting " + source, source, e);
        }
    }

    private static void requireFetchableFinalUri(URI finalUri, URI source) throws IOException {
        if (!DownloadUrls.isFetchable(finalUri)) {
            throw new DownloadFailedException(
                    "the request for "
                            + source
                            + " was redirected to a URL this product will not fetch from: \""
                            + finalUri
                            + "\". A redirect may not move a managed download off https, onto a"
                            + " non-loopback plain-HTTP host, or onto a URL carrying credentials.",
                    source);
        }
    }

    private static void requireBodyStatus(int status, boolean rangeRequested, URI source)
            throws IOException {
        if (status == HTTP_PARTIAL_CONTENT && !rangeRequested) {
            throw new DownloadFailedException(
                    "the server answered 206 Partial Content to a request that asked for no range,"
                            + " so the body cannot be assumed to be the whole artefact: "
                            + source,
                    source);
        }
        if (status != HTTP_OK && status != HTTP_PARTIAL_CONTENT) {
            throw new DownloadFailedException(
                    "unexpected HTTP status " + status + " from " + source, source);
        }
    }

    /**
     * The whole artefact's length as this response describes it, or a negative number when the
     * server declared none.
     */
    private static long declaredTotal(HttpResponse<?> response, int status, long offset, URI source)
            throws IOException {
        if (status != HTTP_PARTIAL_CONTENT) {
            return response.headers()
                    .firstValueAsLong("content-length")
                    .orElse(DownloadReport.NO_DECLARED_TOTAL);
        }
        String header =
                response.headers()
                        .firstValue("content-range")
                        .orElseThrow(
                                () ->
                                        new DownloadFailedException(
                                                "the server answered 206 Partial Content with no"
                                                        + " content-range header, so where the body"
                                                        + " belongs in the artefact is unknown: "
                                                        + source,
                                                source));
        Matcher matcher = CONTENT_RANGE.matcher(header.trim());
        if (!matcher.matches()) {
            throw new DownloadFailedException(
                    "the server answered 206 Partial Content with an unreadable content-range"
                            + " header \""
                            + header
                            + "\" from "
                            + source,
                    source);
        }
        long start = Long.parseLong(matcher.group(1));
        if (start != offset) {
            throw new DownloadFailedException(
                    "the server answered 206 Partial Content starting at byte "
                            + start
                            + ", but the partial file holds "
                            + offset
                            + " bytes, so appending the body would corrupt it: "
                            + source,
                    source);
        }
        String total = matcher.group(3);
        return "*".equals(total) ? DownloadReport.NO_DECLARED_TOTAL : Long.parseLong(total);
    }

    /**
     * Whether the artefact this response describes looks like the one the partial file came from.
     *
     * <p>Advisory. An {@code ETag} is under no obligation to stay stable and the measured server
     * ignores {@code If-Range}, so a {@code true} here is not a guarantee of anything -- it is only
     * the cheap half of the check, and {@code R-SEC-02}'s SHA-256 is the other half.
     */
    private static boolean continues(
            PartialDownload.ResumePoint point, long declaredTotal, String etag) {
        return point.declaredTotalBytes() == declaredTotal && point.etag().equals(etag);
    }

    private DownloadReport copy(
            DownloadRequest request,
            PartialDownload partial,
            ResponseBody body,
            int status,
            boolean rangeRequested,
            long offset,
            long declaredTotal,
            String etag)
            throws IOException {
        URI source = request.source();
        DownloadProgressListener listener = request.listener();
        partial.recordState(declaredTotal, etag);

        long transferred = 0L;
        boolean cancelled = false;
        StandardOpenOption[] options =
                offset > 0
                        ? new StandardOpenOption[] {
                            StandardOpenOption.CREATE,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.APPEND
                        }
                        : new StandardOpenOption[] {
                            StandardOpenOption.CREATE,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.TRUNCATE_EXISTING
                        };
        try (SeekableByteChannel out = Files.newByteChannel(partial.file(), options)) {
            listener.onProgress(offset, declaredTotal);
            while (true) {
                List<ByteBuffer> chunk =
                        nextChunk(body, source, offset + transferred, declaredTotal);
                if (chunk == null) {
                    break;
                }
                for (ByteBuffer buffer : chunk) {
                    transferred += buffer.remaining();
                    while (buffer.hasRemaining()) {
                        out.write(buffer);
                    }
                }
                listener.onProgress(offset + transferred, declaredTotal);
                if (request.cancellation().isCancelled()) {
                    cancelled = true;
                    break;
                }
            }
        }

        long size = offset + transferred;
        if (cancelled) {
            // The channel is closed by now, which matters on Windows: a file with an open handle
            // cannot be deleted there, and a cancellation that left the partial file behind would
            // leave exactly what this exception promises is gone.
            partial.discard();
            throw new DownloadCancelledException(source, size);
        }
        if (declaredTotal >= 0 && size < declaredTotal) {
            // A body longer than declared is deliberately not checked here: it cannot pass the
            // mandatory SHA-256, and inventing a second rule for it would be a rule nothing tests.
            throw new TruncatedDownloadException(source, declaredTotal, size, null);
        }
        listener.onProgress(size, declaredTotal);
        return new DownloadReport(
                source,
                request.destination(),
                status,
                rangeRequested,
                offset,
                transferred,
                size,
                declaredTotal);
    }

    private List<ByteBuffer> nextChunk(
            ResponseBody body, URI source, long onDisk, long declaredTotal) throws IOException {
        try {
            return body.next(stallTimeout);
        } catch (HttpTimeoutException e) {
            throw new DownloadFailedException(
                    "the download stalled: no bytes arrived from "
                            + source
                            + " for "
                            + stallTimeout.toMillis()
                            + " ms, after "
                            + onDisk
                            + " byte(s)",
                    source,
                    e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DownloadFailedException(
                    "the thread was interrupted while downloading " + source, source, e);
        } catch (IOException e) {
            if (declaredTotal >= 0 && onDisk < declaredTotal) {
                throw new TruncatedDownloadException(source, declaredTotal, onDisk, e);
            }
            throw new DownloadFailedException(
                    "the response body from "
                            + source
                            + " failed after "
                            + onDisk
                            + " byte(s): "
                            + DownloadFailedException.describe(e),
                    source,
                    e);
        }
    }
}
