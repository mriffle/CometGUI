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

package org.cometgui.install.verify;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.ports.DownloadProgressListener;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.install.download.ArtefactFetcher;
import org.cometgui.install.download.DownloadCancellation;
import org.cometgui.install.download.DownloadReport;
import org.cometgui.install.download.DownloadRequest;
import org.cometgui.install.registry.ArtefactRecord;

/**
 * Fetches an artefact and refuses to hand it over unless its SHA-256 is the one the manifest pins.
 *
 * <h2>The rule the measured server forces</h2>
 *
 * <p><strong>A resumed download that fails its checksum is discarded and fetched again from zero,
 * and is never resumed a second time.</strong>
 *
 * <p>That is not a general-purpose retry. It follows from a specific measurement: GitHub's release
 * host <em>ignores {@code If-Range}</em>. A deliberately stale validator was answered with {@code
 * 206} and the partial range rather than {@code 200} and the whole body, so if upstream re-tags an
 * asset between two attempts a resuming client splices bytes from two different files and no HTTP
 * status reveals it. The SHA-256 is the only thing that catches that, and the useful response to it
 * is to throw away the partial file -- because resuming again splices the same corruption back in
 * and fails identically, which reads as an upstream fault when it is the client's own.
 *
 * <p>So a failure after a resume is <em>suspicious</em> and worth one clean retry; a failure
 * without a resume, or after the clean retry, is a real disagreement between upstream's bytes and
 * the manifest and is reported as one.
 *
 * <h2>Nothing unverified survives</h2>
 *
 * <p>Every rejection deletes the file before raising {@link ArtefactVerificationException}. The
 * specification's supply-chain rule is "never execute a tool from an unverified temporary
 * download", and the cheapest way to keep it is for the unverified download not to exist.
 */
public final class VerifiedDownloader {

    /** How the bytes are fetched. An interface, so a test can prove which requests were made. */
    private final ArtefactFetcher fetcher;

    /** What decides whether they are the right bytes. */
    private final ArtefactVerifier verifier;

    /**
     * Composes a fetcher and a verifier.
     *
     * @param fetcher how bytes are fetched, normally {@code
     *     org.cometgui.install.download.HttpDownloader}
     * @param verifier what decides whether to keep them
     * @throws NullPointerException if either argument is {@code null}
     */
    public VerifiedDownloader(ArtefactFetcher fetcher, ArtefactVerifier verifier) {
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
        this.verifier = Objects.requireNonNull(verifier, "verifier");
    }

    /**
     * Fetches and verifies one manifest artefact.
     *
     * @param record the artefact to fetch
     * @param destination where to put it
     * @param listener notified as bytes arrive
     * @param cancellation asked between chunks whether the caller still wants the transfer
     * @return the verified file, with what each transfer did
     * @throws ArtefactVerificationException if the bytes do not match the pinned SHA-256
     * @throws IOException if the transfer fails, is cancelled, or the artefact has gone from its
     *     pinned URL
     * @throws NullPointerException if any argument is {@code null}
     */
    public VerifiedArtefact fetch(
            ArtefactRecord record,
            Path destination,
            DownloadProgressListener listener,
            DownloadCancellation cancellation)
            throws IOException {
        Objects.requireNonNull(record, "record");
        return fetch(
                record.url(),
                destination,
                record.hashes(),
                record.sizeBytes(),
                listener,
                cancellation);
    }

    /**
     * Fetches and verifies one artefact against a pinned size and digest pair.
     *
     * @param source where to fetch it from
     * @param destination where to put it
     * @param expected the digests the manifest pins
     * @param expectedSizeBytes the length the manifest pins
     * @param listener notified as bytes arrive
     * @param cancellation asked between chunks whether the caller still wants the transfer
     * @return the verified file, with what each transfer did
     * @throws ArtefactVerificationException if the bytes do not match the pinned SHA-256
     * @throws IOException if the transfer fails, is cancelled, or the artefact has gone from its
     *     pinned URL
     * @throws NullPointerException if any argument is {@code null}
     */
    public VerifiedArtefact fetch(
            URI source,
            Path destination,
            FileHashes expected,
            long expectedSizeBytes,
            DownloadProgressListener listener,
            DownloadCancellation cancellation)
            throws IOException {
        Objects.requireNonNull(expected, "expected");
        DownloadRequest request =
                new DownloadRequest(
                        source,
                        destination,
                        Objects.requireNonNull(listener, "listener"),
                        Objects.requireNonNull(cancellation, "cancellation"),
                        true,
                        Optional.of(expected.sha256()));

        List<DownloadReport> attempts = new ArrayList<>();
        DownloadReport first = fetcher.fetch(request);
        attempts.add(first);
        VerificationResult firstVerdict =
                verifier.verify(destination, expected, expectedSizeBytes, source);
        if (firstVerdict.accepted()) {
            return accepted(destination, firstVerdict, attempts);
        }

        Files.deleteIfExists(destination);
        if (!first.resumed()) {
            // Nothing was spliced, so fetching the same bytes again would fail the same way. This
            // is upstream disagreeing with the manifest, and saying so once is the honest answer.
            throw new ArtefactVerificationException(firstVerdict, attempts.size());
        }

        // resuming(false) is what makes this a restart rather than a second splice: it deletes the
        // partial file before it asks for anything.
        DownloadReport second = fetcher.fetch(request.resuming(false));
        attempts.add(second);
        VerificationResult secondVerdict =
                verifier.verify(destination, expected, expectedSizeBytes, source);
        if (secondVerdict.accepted()) {
            return accepted(destination, secondVerdict, attempts);
        }
        Files.deleteIfExists(destination);
        throw new ArtefactVerificationException(secondVerdict, attempts.size());
    }

    private static VerifiedArtefact accepted(
            Path destination, VerificationResult verdict, List<DownloadReport> attempts) {
        return new VerifiedArtefact(destination, verdict.actual().orElseThrow(), attempts);
    }
}
