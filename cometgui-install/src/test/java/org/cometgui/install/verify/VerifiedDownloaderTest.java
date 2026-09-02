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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.cometgui.domain.ports.DownloadProgressListener;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.tools.ArtefactKind;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.MinimumHostRequirements;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.download.ArtefactFetcher;
import org.cometgui.install.download.ArtefactUnavailableException;
import org.cometgui.install.download.DownloadCancellation;
import org.cometgui.install.download.DownloadReport;
import org.cometgui.install.download.DownloadRequest;
import org.cometgui.install.download.HttpDownloader;
import org.cometgui.install.download.LoopbackHttpServer;
import org.cometgui.install.download.TruncatedDownloadException;
import org.cometgui.install.registry.ArtefactLicence;
import org.cometgui.install.registry.ArtefactManifestReader;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.install.testing.Nulls;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link VerifiedDownloader}.
 *
 * <p>Most of them drive a scripted fetcher rather than a server, because the claims are about
 * <em>which requests were made</em> -- did the retry resume again, or start from zero? -- and a
 * fetcher that records its requests answers that directly. The two that use a real loopback server
 * are the ones where the point is that HTTP could not have caught the fault.
 *
 * <p>Every expected digest below was computed with {@code sha256sum} and {@code md5sum}.
 */
class VerifiedDownloaderTest {

    private static final URI SOURCE =
            URI.create("https://github.com/percolator/percolator/releases/download/rel/a.zip");

    private static final String GOOD = "cometgui phase 05 unit 3 verification fixture\n";
    private static final String BAD = "cometgui phase 05 unit 3 verification fixtura\n";
    private static final long SIZE = 46L;

    private static final FileHashes GOOD_HASHES =
            new FileHashes(
                    "8a46b4484c08e0f688de288a2093d79e",
                    "64f2099b31a63a0f03f5f6f495aacd99118da3da6a1826dcf7925234baa23e19");

    /** 1000 bytes starting at 'a'; sha256 and md5 from {@code sha256sum} and {@code md5sum}. */
    private static final FileHashes BODY_A_HASHES =
            new FileHashes(
                    "303fb697b589019cb3edba04b794e575",
                    "915e53a44c18b19bb06ba5b3f5fcaf1dc4651e8404c63425cfc6174e74659d87");

    /** 1000 bytes starting at 'n'; the artefact upstream re-tagged to. */
    private static final FileHashes BODY_B_HASHES =
            new FileHashes(
                    "c0dd0214aa0ec998c7849be248f206bf",
                    "46fa2b2ba5f814ea691db703a26a585c0b3ee6baf568b33febef4abe6b617b94");

    @TempDir private Path work;

    private static byte[] body(char first) {
        byte[] bytes = new byte[1000];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (first + (i % 26));
        }
        return bytes;
    }

    private static VerifiedDownloader downloader(ArtefactFetcher fetcher) {
        return new VerifiedDownloader(fetcher, new ArtefactVerifier(new StreamingHashService()));
    }

    private VerifiedArtefact fetch(
            VerifiedDownloader downloader, Path destination, FileHashes expected)
            throws IOException {
        return downloader.fetch(
                SOURCE,
                destination,
                expected,
                SIZE,
                (bytes, total) -> {},
                DownloadCancellation.never());
    }

    /**
     * A fetcher that performs a scripted list of attempts and records every request it was given.
     */
    private static final class ScriptedFetcher implements ArtefactFetcher {

        @FunctionalInterface
        interface Attempt {
            DownloadReport perform(DownloadRequest request) throws IOException;
        }

        private final Deque<Attempt> remaining = new ArrayDeque<>();
        private final List<DownloadRequest> requests = new ArrayList<>();

        ScriptedFetcher(Attempt... attempts) {
            this.remaining.addAll(List.of(attempts));
        }

        @Override
        public DownloadReport fetch(DownloadRequest request) throws IOException {
            requests.add(request);
            Attempt attempt = remaining.poll();
            if (attempt == null) {
                throw new IllegalStateException(
                        "the downloader made more transfers than the test scripted: "
                                + requests.size());
            }
            return attempt.perform(request);
        }

        List<DownloadRequest> requests() {
            return List.copyOf(requests);
        }
    }

    /** An attempt that writes some content and reports itself as resumed from a given offset. */
    private static ScriptedFetcher.Attempt writing(String content, long resumedFrom) {
        return request -> {
            Files.writeString(request.destination(), content, StandardCharsets.UTF_8);
            long size = content.getBytes(StandardCharsets.UTF_8).length;
            return new DownloadReport(
                    request.source(),
                    request.destination(),
                    resumedFrom > 0 ? 206 : 200,
                    resumedFrom > 0,
                    resumedFrom,
                    size - resumedFrom,
                    size,
                    size);
        };
    }

    @Nested
    @DisplayName("with a scripted fetcher")
    class Scripted {

        @Test
        @DisplayName("a download that verifies is returned with the digests taken from the bytes")
        void aDownloadThatVerifiesIsReturned() throws IOException {
            ScriptedFetcher fetcher = new ScriptedFetcher(writing(GOOD, 0));
            Path file = work.resolve("artefact.zip");

            VerifiedArtefact verified = fetch(downloader(fetcher), file, GOOD_HASHES);

            assertAll(
                    () -> assertEquals(file, verified.file()),
                    () -> assertEquals(GOOD_HASHES, verified.hashes()),
                    () -> assertEquals(1, verified.attemptCount()),
                    () -> assertEquals(200, verified.lastAttempt().statusCode()),
                    () -> assertTrue(Files.exists(file)),
                    () -> assertEquals(1, fetcher.requests().size()));
        }

        @Test
        @DisplayName("the first transfer asks to resume and carries the expected digest")
        void theFirstTransferAsksToResume() throws IOException {
            ScriptedFetcher fetcher = new ScriptedFetcher(writing(GOOD, 0));
            fetch(downloader(fetcher), work.resolve("artefact.zip"), GOOD_HASHES);

            DownloadRequest first = fetcher.requests().get(0);
            assertAll(
                    () -> assertTrue(first.resume()),
                    () ->
                            assertEquals(
                                    Optional.of(GOOD_HASHES.sha256()),
                                    first.expectedSha256(),
                                    "so that a 404 can name the checksum the artefact should have"
                                            + " had (D-008)"),
                    () -> assertEquals(SOURCE, first.source()));
        }

        @Test
        @DisplayName("a fresh download that fails its checksum is not retried, and leaves no file")
        void aFreshDownloadThatFailsIsNotRetried() throws IOException {
            ScriptedFetcher fetcher = new ScriptedFetcher(writing(BAD, 0));
            Path file = work.resolve("artefact.zip");

            ArtefactVerificationException thrown =
                    assertThrows(
                            ArtefactVerificationException.class,
                            () -> fetch(downloader(fetcher), file, GOOD_HASHES));

            assertAll(
                    () -> assertEquals(1, thrown.attempts()),
                    () ->
                            assertEquals(
                                    1,
                                    fetcher.requests().size(),
                                    "nothing was spliced, so fetching the same bytes again would"
                                            + " fail the same way"),
                    () -> assertEquals(VerificationOutcome.SHA256_MISMATCH, thrown.outcome()),
                    () -> assertEquals(SOURCE, thrown.source()),
                    () -> assertEquals(GOOD_HASHES.sha256(), thrown.expectedSha256()),
                    () ->
                            assertEquals(
                                    Optional.of(
                                            "2751a8a7cf101dd06abb66704a6fb35ef1976929a834ece1beba"
                                                    + "327f0e9439f2"),
                                    thrown.actualSha256()),
                    () -> assertTrue(thrown.getMessage().contains("after 1 transfer attempt(s)")),
                    () ->
                            assertFalse(
                                    Files.exists(file),
                                    "an unverified download is deleted, so nothing runs it"));
        }

        @Test
        @DisplayName("a resumed download that fails is restarted from zero and never resumed again")
        void aResumedDownloadThatFailsIsRestartedFromZero() throws IOException {
            ScriptedFetcher fetcher = new ScriptedFetcher(writing(BAD, 20), writing(GOOD, 0));
            Path file = work.resolve("artefact.zip");

            VerifiedArtefact verified = fetch(downloader(fetcher), file, GOOD_HASHES);

            assertAll(
                    () -> assertEquals(2, verified.attemptCount()),
                    () -> assertTrue(verified.attempts().get(0).resumed()),
                    () -> assertFalse(verified.attempts().get(1).resumed()),
                    () -> assertTrue(fetcher.requests().get(0).resume()),
                    () ->
                            assertFalse(
                                    fetcher.requests().get(1).resume(),
                                    "resuming(false) is what makes the retry a restart: resuming a"
                                            + " second time splices the same corruption back in and"
                                            + " fails identically, which reads as an upstream fault"
                                            + " when it is the client's own"),
                    () ->
                            assertEquals(
                                    Optional.of(GOOD_HASHES.sha256()),
                                    fetcher.requests().get(1).expectedSha256(),
                                    "and the restart still knows what it is looking for"),
                    () -> assertEquals(GOOD_HASHES, verified.hashes()),
                    () -> assertEquals(GOOD, Files.readString(file, StandardCharsets.UTF_8)));
        }

        @Test
        @DisplayName("a restart that also fails is a genuine failure, reported once")
        void aRestartThatAlsoFailsIsAGenuineFailure() throws IOException {
            ScriptedFetcher fetcher = new ScriptedFetcher(writing(BAD, 20), writing(BAD, 0));
            Path file = work.resolve("artefact.zip");

            ArtefactVerificationException thrown =
                    assertThrows(
                            ArtefactVerificationException.class,
                            () -> fetch(downloader(fetcher), file, GOOD_HASHES));

            assertAll(
                    () -> assertEquals(2, thrown.attempts()),
                    () ->
                            assertEquals(
                                    2,
                                    fetcher.requests().size(),
                                    "two transfers and no more: this does not retry for ever"),
                    () -> assertFalse(fetcher.requests().get(1).resume()),
                    () -> assertTrue(thrown.getMessage().contains("after 2 transfer attempt(s)")),
                    () -> assertFalse(Files.exists(file)));
        }

        @Test
        @DisplayName("a download that produced no file at all is reported as absent, not retried")
        void aDownloadThatProducedNoFileIsAbsent() {
            ScriptedFetcher fetcher =
                    new ScriptedFetcher(
                            request ->
                                    new DownloadReport(
                                            request.source(),
                                            request.destination(),
                                            200,
                                            false,
                                            0,
                                            0,
                                            0,
                                            0));
            Path file = work.resolve("artefact.zip");

            ArtefactVerificationException thrown =
                    assertThrows(
                            ArtefactVerificationException.class,
                            () -> fetch(downloader(fetcher), file, GOOD_HASHES));

            assertAll(
                    () -> assertEquals(VerificationOutcome.FILE_ABSENT, thrown.outcome()),
                    () -> assertEquals(Optional.empty(), thrown.actualSha256()),
                    () -> assertEquals(1, thrown.attempts()));
        }

        @Test
        @DisplayName("a cancelled transfer propagates and is never retried")
        void aCancelledTransferIsNotRetried() {
            AtomicBoolean secondAttempt = new AtomicBoolean();
            ScriptedFetcher fetcher =
                    new ScriptedFetcher(
                            request -> {
                                throw new IOException("cancelled by the user");
                            },
                            request -> {
                                secondAttempt.set(true);
                                return writing(GOOD, 0).perform(request);
                            });

            IOException thrown =
                    assertThrows(
                            IOException.class,
                            () ->
                                    fetch(
                                            downloader(fetcher),
                                            work.resolve("artefact.zip"),
                                            GOOD_HASHES));

            assertAll(
                    () -> assertEquals("cancelled by the user", thrown.getMessage()),
                    () -> assertFalse(secondAttempt.get()),
                    () -> assertEquals(1, fetcher.requests().size()));
        }

        @Test
        @DisplayName(
                "an unavailable artefact propagates as an availability failure, not a mismatch")
        void anUnavailableArtefactPropagates() throws IOException {
            Path file = work.resolve("artefact.zip");
            try (LoopbackHttpServer server =
                            new LoopbackHttpServer(
                                    (request, out) ->
                                            LoopbackHttpServer.head(
                                                    out, 404, "Not Found", "Content-Length: 0"));
                    HttpDownloader http =
                            new HttpDownloader(
                                    Duration.ofSeconds(5),
                                    Duration.ofSeconds(5),
                                    Duration.ofSeconds(5))) {

                ArtefactUnavailableException thrown =
                        assertThrows(
                                ArtefactUnavailableException.class,
                                () ->
                                        new VerifiedDownloader(
                                                        http,
                                                        new ArtefactVerifier(
                                                                new StreamingHashService()))
                                                .fetch(
                                                        server.uri("/a.zip"),
                                                        file,
                                                        GOOD_HASHES,
                                                        SIZE,
                                                        (bytes, total) -> {},
                                                        DownloadCancellation.never()));

                assertAll(
                        () -> assertEquals(1, server.requests().size(), "and is not retried"),
                        () -> assertTrue(thrown.getMessage().contains(GOOD_HASHES.sha256())));
            }
        }

        @Test
        @DisplayName(
                "the record overload takes the URL, the size and the digests from the manifest")
        void theRecordOverloadUsesTheManifest() throws IOException {
            ArtefactRecord record =
                    ArtefactManifestReader.readFromClasspath().artefacts().stream()
                            .filter(candidate -> "percolator".equals(candidate.tool().id()))
                            .filter(candidate -> "3.07.1".equals(candidate.version().text()))
                            .filter(candidate -> "linux-x86-64".equals(candidate.platform().id()))
                            .findFirst()
                            .orElseThrow();
            AtomicReference<DownloadRequest> seen = new AtomicReference<>();
            ScriptedFetcher fetcher =
                    new ScriptedFetcher(
                            request -> {
                                seen.set(request);
                                return writing(BAD, 0).perform(request);
                            });

            ArtefactVerificationException thrown =
                    assertThrows(
                            ArtefactVerificationException.class,
                            () ->
                                    downloader(fetcher)
                                            .fetch(
                                                    record,
                                                    work.resolve("percolator.zip"),
                                                    (bytes, total) -> {},
                                                    DownloadCancellation.never()));

            assertAll(
                    () -> assertEquals(record.url(), seen.get().source()),
                    () ->
                            assertEquals(
                                    Optional.of(record.hashes().sha256()),
                                    seen.get().expectedSha256()),
                    () ->
                            assertEquals(
                                    VerificationOutcome.SIZE_MISMATCH,
                                    thrown.outcome(),
                                    "a 46-byte file is not the 946303-byte artefact the manifest"
                                            + " pins"),
                    () -> assertEquals(record.hashes().sha256(), thrown.expectedSha256()));
        }

        @Test
        @DisplayName("a manifest record whose bytes do arrive is verified and returned")
        void aRecordThatVerifiesIsReturned() throws IOException {
            ArtefactRecord record =
                    new ArtefactRecord(
                            ToolName.PERCOLATOR,
                            ToolVersion.parse("3.07.1"),
                            "rel-3-07-01",
                            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64),
                            ArtefactKind.BARE_EXECUTABLE,
                            SOURCE,
                            SIZE,
                            GOOD_HASHES,
                            Optional.empty(),
                            Optional.of("bin/percolator"),
                            true,
                            new ArtefactLicence(
                                    "Apache-2.0",
                                    URI.create("https://example.org/LICENSE"),
                                    "a synthetic record, so that the record overload is exercised"
                                            + " on the path where it succeeds"),
                            List.of(),
                            List.of(),
                            List.of(),
                            MinimumHostRequirements.none(),
                            ToolVersion.parse("0.1.0"));
            ScriptedFetcher fetcher = new ScriptedFetcher(writing(GOOD, 0));
            Path file = work.resolve("percolator");

            VerifiedArtefact verified =
                    downloader(fetcher)
                            .fetch(
                                    record,
                                    file,
                                    (bytes, total) -> {},
                                    DownloadCancellation.never());

            assertAll(
                    () -> assertEquals(GOOD_HASHES, verified.hashes()),
                    () -> assertEquals(file, verified.file()),
                    () -> assertEquals(1, verified.attemptCount()),
                    () -> assertEquals(record.url(), fetcher.requests().get(0).source()));
        }

        @Test
        @DisplayName("null arguments are rejected by name")
        void nullArgumentsAreRejected() {
            ArtefactVerifier verifier = new ArtefactVerifier(new StreamingHashService());
            ScriptedFetcher fetcher = new ScriptedFetcher();
            VerifiedDownloader subject = new VerifiedDownloader(fetcher, verifier);
            Path file = work.resolve("artefact.zip");
            DownloadProgressListener listener = (bytes, total) -> {};

            assertAll(
                    () ->
                            assertEquals(
                                    "fetcher",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new VerifiedDownloader(
                                                                    Nulls.of(ArtefactFetcher.class),
                                                                    verifier))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "verifier",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new VerifiedDownloader(
                                                                    fetcher,
                                                                    Nulls.of(
                                                                            ArtefactVerifier
                                                                                    .class)))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "expected",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            subject.fetch(
                                                                    SOURCE,
                                                                    file,
                                                                    Nulls.of(FileHashes.class),
                                                                    SIZE,
                                                                    listener,
                                                                    DownloadCancellation.never()))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "listener",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            subject.fetch(
                                                                    SOURCE,
                                                                    file,
                                                                    GOOD_HASHES,
                                                                    SIZE,
                                                                    Nulls.of(
                                                                            DownloadProgressListener
                                                                                    .class),
                                                                    DownloadCancellation.never()))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "cancellation",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            subject.fetch(
                                                                    SOURCE,
                                                                    file,
                                                                    GOOD_HASHES,
                                                                    SIZE,
                                                                    listener,
                                                                    Nulls.of(
                                                                            DownloadCancellation
                                                                                    .class)))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "record",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            subject.fetch(
                                                                    Nulls.of(ArtefactRecord.class),
                                                                    file,
                                                                    listener,
                                                                    DownloadCancellation.never()))
                                            .getMessage()));
        }
    }

    @Nested
    @DisplayName("against a real server")
    class AgainstAServer {

        @Test
        @DisplayName("a body that changes between attempts is caught by the checksum, not by HTTP")
        void aChangedBodyIsCaughtByTheChecksumNotByHttp() throws IOException {
            byte[] first = body('a');
            byte[] second = body('n');
            AtomicReference<byte[]> published = new AtomicReference<>(first);
            AtomicBoolean truncateFirst = new AtomicBoolean(true);

            try (LoopbackHttpServer server =
                            new LoopbackHttpServer(
                                    (request, out) -> {
                                        byte[] bytes = published.get();
                                        if (truncateFirst.compareAndSet(true, false)) {
                                            LoopbackHttpServer.head(
                                                    out,
                                                    200,
                                                    "OK",
                                                    "Content-Length: " + bytes.length,
                                                    "Accept-Ranges: bytes",
                                                    // The same validator throughout: the advisory
                                                    // ETag check must not be what catches this.
                                                    "ETag: \"stable\"");
                                            out.write(bytes, 0, 400);
                                            return;
                                        }
                                        LoopbackHttpServer.honouringRange(
                                                        published::get, () -> "\"stable\"")
                                                .respond(request, out);
                                    });
                    HttpDownloader http =
                            new HttpDownloader(
                                    Duration.ofSeconds(5),
                                    Duration.ofSeconds(5),
                                    Duration.ofSeconds(5))) {

                VerifiedDownloader subject =
                        new VerifiedDownloader(
                                http, new ArtefactVerifier(new StreamingHashService()));
                Path file = work.resolve("artefact.zip");
                URI url = server.uri("/a.zip");

                // The first attempt is cut short by the network, leaving 400 bytes of the OLD
                // artefact on disk. Upstream then re-tags the asset: same length, same ETag,
                // different bytes -- which is what If-Range would exist to catch and what the
                // measured GitHub host ignores it for.
                assertThrows(
                        TruncatedDownloadException.class,
                        () ->
                                subject.fetch(
                                        url,
                                        file,
                                        BODY_A_HASHES,
                                        1000,
                                        (bytes, total) -> {},
                                        DownloadCancellation.never()));
                assertEquals(400L, Files.size(file.resolveSibling("artefact.zip.part")));
                published.set(second);

                VerifiedArtefact verified =
                        subject.fetch(
                                url,
                                file,
                                BODY_B_HASHES,
                                1000,
                                (bytes, total) -> {},
                                DownloadCancellation.never());

                List<LoopbackHttpServer.Request> requests = server.requests();
                assertAll(
                        () -> assertEquals(3, requests.size()),
                        () ->
                                assertEquals(
                                        Optional.of("bytes=400-"),
                                        requests.get(1).range(),
                                        "HTTP allowed the resume: a 206 came back with the partial"
                                                + " range and the same ETag, so nothing about the"
                                                + " protocol said the file had changed"),
                        () -> assertEquals(206, verified.attempts().get(0).statusCode()),
                        () -> assertTrue(verified.attempts().get(0).resumed()),
                        () ->
                                assertEquals(
                                        Optional.empty(),
                                        requests.get(2).range(),
                                        "the checksum caught it, and the retry starts from zero"
                                                + " rather than splicing the same corruption back"
                                                + " in"),
                        () -> assertFalse(verified.attempts().get(1).resumed()),
                        () ->
                                assertEquals(
                                        1000L,
                                        verified.attempts().get(1).bytesTransferred(),
                                        "the whole artefact crossed the network the second time"),
                        () -> assertEquals(2, verified.attemptCount()),
                        () -> assertEquals(BODY_B_HASHES, verified.hashes()),
                        () -> assertArrayEquals(second, Files.readAllBytes(file)),
                        () ->
                                assertFalse(
                                        Files.exists(file.resolveSibling("artefact.zip.part")),
                                        "and the partial file is gone"));
            }
        }

        @Test
        @DisplayName("a body that changed to something the manifest never pinned fails twice")
        void aBodyThatNeverMatchesFailsTwice() throws IOException {
            byte[] first = body('a');
            byte[] second = body('n');
            AtomicReference<byte[]> published = new AtomicReference<>(first);
            AtomicBoolean truncateFirst = new AtomicBoolean(true);

            try (LoopbackHttpServer server =
                            new LoopbackHttpServer(
                                    (request, out) -> {
                                        byte[] bytes = published.get();
                                        if (truncateFirst.compareAndSet(true, false)) {
                                            LoopbackHttpServer.head(
                                                    out,
                                                    200,
                                                    "OK",
                                                    "Content-Length: " + bytes.length,
                                                    "Accept-Ranges: bytes",
                                                    "ETag: \"stable\"");
                                            out.write(bytes, 0, 400);
                                            return;
                                        }
                                        LoopbackHttpServer.honouringRange(
                                                        published::get, () -> "\"stable\"")
                                                .respond(request, out);
                                    });
                    HttpDownloader http =
                            new HttpDownloader(
                                    Duration.ofSeconds(5),
                                    Duration.ofSeconds(5),
                                    Duration.ofSeconds(5))) {

                VerifiedDownloader subject =
                        new VerifiedDownloader(
                                http, new ArtefactVerifier(new StreamingHashService()));
                Path file = work.resolve("artefact.zip");
                URI url = server.uri("/a.zip");

                assertThrows(
                        TruncatedDownloadException.class,
                        () ->
                                subject.fetch(
                                        url,
                                        file,
                                        BODY_A_HASHES,
                                        1000,
                                        (bytes, total) -> {},
                                        DownloadCancellation.never()));
                published.set(second);

                ArtefactVerificationException thrown =
                        assertThrows(
                                ArtefactVerificationException.class,
                                () ->
                                        subject.fetch(
                                                url,
                                                file,
                                                BODY_A_HASHES,
                                                1000,
                                                (bytes, total) -> {},
                                                DownloadCancellation.never()));

                assertAll(
                        () ->
                                assertEquals(
                                        2,
                                        thrown.attempts(),
                                        "the resume was tried, the checksum failed, the restart was"
                                                + " tried, and the checksum failed again: upstream"
                                                + " really is serving different bytes"),
                        () -> assertEquals(VerificationOutcome.SHA256_MISMATCH, thrown.outcome()),
                        () ->
                                assertEquals(
                                        Optional.of(BODY_B_HASHES.sha256()),
                                        thrown.actualSha256(),
                                        "and the digest reported is the one upstream is actually"
                                                + " serving, which is what a human needs to see"),
                        () -> assertFalse(Files.exists(file)),
                        () ->
                                assertEquals(
                                        Optional.empty(),
                                        server.requests().get(2).range(),
                                        "the second transfer was a restart, not a second splice"));
            }
        }
    }

    @Nested
    @DisplayName("the verified artefact")
    class TheVerifiedArtefact {

        @Test
        @DisplayName("takes a defensive copy of its attempts and refuses an empty list")
        void takesACopyAndRefusesAnEmptyList() {
            DownloadReport report =
                    new DownloadReport(SOURCE, Path.of("a.zip"), 200, false, 0, 46, 46, 46);
            List<DownloadReport> mutable = new ArrayList<>(List.of(report));
            VerifiedArtefact artefact =
                    new VerifiedArtefact(Path.of("a.zip"), GOOD_HASHES, mutable);
            mutable.clear();

            assertAll(
                    () -> assertEquals(1, artefact.attemptCount()),
                    () -> assertEquals(report, artefact.lastAttempt()),
                    () ->
                            assertThrows(
                                    UnsupportedOperationException.class,
                                    () -> artefact.attempts().add(report)),
                    () ->
                            assertTrue(
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () ->
                                                            new VerifiedArtefact(
                                                                    Path.of("a.zip"),
                                                                    GOOD_HASHES,
                                                                    List.of()))
                                            .getMessage()
                                            .startsWith("a verified artefact was produced")),
                    () ->
                            assertEquals(
                                    "file",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new VerifiedArtefact(
                                                                    Nulls.of(Path.class),
                                                                    GOOD_HASHES,
                                                                    List.of(report)))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "hashes",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new VerifiedArtefact(
                                                                    Path.of("a.zip"),
                                                                    Nulls.of(FileHashes.class),
                                                                    List.of(report)))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "attempts",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new VerifiedArtefact(
                                                                    Path.of("a.zip"),
                                                                    GOOD_HASHES,
                                                                    Nulls.of(List.class)))
                                            .getMessage()));
        }
    }
}
