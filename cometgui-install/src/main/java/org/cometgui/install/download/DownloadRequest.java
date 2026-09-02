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
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.cometgui.domain.ports.DownloadProgressListener;
import org.cometgui.domain.ports.FileHashes;

/**
 * One transfer, described completely: where from, where to, who is watching, who may stop it, and
 * whether a partial file left by an earlier attempt may be continued.
 *
 * <h2>Why the expected digest is here, and what it is not for</h2>
 *
 * <p>{@link #expectedSha256()} is <strong>diagnostic context and nothing else</strong>. The {@link
 * org.cometgui.domain.ports.Downloader} port says a downloader never decides whether a file is
 * trustworthy, and this one does not: it never computes a digest, never compares one, and will
 * happily complete a transfer whose bytes do not match this value. {@link
 * org.cometgui.install.verify.ArtefactVerifier} is what decides, and keeping the decision out of
 * here is what allows the verification step to be tested against a deliberately corrupted download.
 *
 * <p>It is carried because {@code D-008} requires a specific message: this project redistributes no
 * tool binary, so an artefact that has vanished from its pinned URL must be reported as an upstream
 * <em>availability</em> failure naming the URL <em>and the expected checksum</em> -- the two facts
 * a scientist needs to find out what happened. A message assembled after the fact by a caller that
 * caught a 404 would be a second place where that wording lives.
 *
 * <h2>Resume</h2>
 *
 * <p>{@code resume} true means "continue the partial file beside the destination if there is one";
 * false means "delete any partial file and start from zero". It is a request, not a promise: {@link
 * HttpDownloader} restarts cleanly whenever the server refuses the range, the recorded length or
 * {@code ETag} has changed, or no usable resume state was written.
 *
 * @param source the artefact to fetch; https, or plain http to a loopback address literal
 * @param destination the file to write once, and only once, the whole transfer has completed
 * @param listener notified as bytes arrive; use {@code (bytes, total) -> {}} rather than null
 * @param cancellation asked between chunks whether the caller still wants the transfer
 * @param resume whether a partial file from an earlier attempt may be continued
 * @param expectedSha256 the digest the manifest pins, for the availability message only; never
 *     compared to anything by the downloader
 */
public record DownloadRequest(
        URI source,
        Path destination,
        DownloadProgressListener listener,
        DownloadCancellation cancellation,
        boolean resume,
        Optional<String> expectedSha256) {

    private static final Pattern HEXADECIMAL = Pattern.compile("[0-9a-fA-F]+");

    /**
     * Validates the request.
     *
     * @throws NullPointerException if any component is {@code null}
     * @throws IllegalArgumentException if the source is not a URL this product may fetch, if the
     *     destination has no file name, or if the expected digest is not 64 hexadecimal characters
     */
    public DownloadRequest {
        source = DownloadUrls.requireFetchable(source, "source");
        Objects.requireNonNull(destination, "destination");
        if (destination.getFileName() == null) {
            throw new IllegalArgumentException(
                    "destination must name a file, but was: \"" + destination + "\"");
        }
        Objects.requireNonNull(listener, "listener");
        Objects.requireNonNull(cancellation, "cancellation");
        expectedSha256 = checkedDigest(expectedSha256);
    }

    private static Optional<String> checkedDigest(Optional<String> digest) {
        Objects.requireNonNull(digest, "expectedSha256");
        return digest.map(
                value -> {
                    if (value.length() != FileHashes.SHA256_LENGTH
                            || !HEXADECIMAL.matcher(value).matches()) {
                        throw new IllegalArgumentException(
                                "expectedSha256 must be "
                                        + FileHashes.SHA256_LENGTH
                                        + " hexadecimal characters, but was: \""
                                        + value
                                        + "\"");
                    }
                    return value.toLowerCase(Locale.ROOT);
                });
    }

    /**
     * A plain transfer: no listener, no cancellation, no resume, no expected digest.
     *
     * @param source the artefact to fetch
     * @param destination the file to write
     * @return the request
     * @throws IllegalArgumentException if the source is not a URL this product may fetch
     */
    public static DownloadRequest of(URI source, Path destination) {
        return new DownloadRequest(
                source,
                destination,
                (bytesTransferred, totalBytes) -> {},
                DownloadCancellation.never(),
                false,
                Optional.empty());
    }

    /**
     * The same request, reporting progress to a listener.
     *
     * @param newListener the listener
     * @return a copy with that listener
     */
    public DownloadRequest listeningTo(DownloadProgressListener newListener) {
        return new DownloadRequest(
                source, destination, newListener, cancellation, resume, expectedSha256);
    }

    /**
     * The same request, stoppable.
     *
     * @param newCancellation the cancellation the transfer loop asks between chunks
     * @return a copy with that cancellation
     */
    public DownloadRequest cancellableBy(DownloadCancellation newCancellation) {
        return new DownloadRequest(
                source, destination, listener, newCancellation, resume, expectedSha256);
    }

    /**
     * The same request, with resume allowed or forbidden.
     *
     * @param allowed {@code true} to continue a partial file, {@code false} to delete one and start
     *     from zero
     * @return a copy with that setting
     */
    public DownloadRequest resuming(boolean allowed) {
        return new DownloadRequest(
                source, destination, listener, cancellation, allowed, expectedSha256);
    }

    /**
     * The same request, carrying the digest the manifest pins for the availability message.
     *
     * @param sha256 the expected SHA-256, 64 hexadecimal characters
     * @return a copy carrying that digest
     * @throws IllegalArgumentException if it is not 64 hexadecimal characters
     */
    public DownloadRequest expecting(String sha256) {
        return new DownloadRequest(
                source,
                destination,
                listener,
                cancellation,
                resume,
                Optional.of(Objects.requireNonNull(sha256, "sha256")));
    }
}
