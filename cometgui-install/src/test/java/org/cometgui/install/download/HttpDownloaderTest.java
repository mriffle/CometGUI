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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.cometgui.domain.ports.DownloadProgressListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link HttpDownloader} against a real HTTP server on a loopback socket.
 *
 * <p>Every assertion here is about something a wrong implementation would still get past a weaker
 * test. "The file is right" cannot tell a resume from a silent full re-download, so the byte counts
 * are asserted; "it did not throw" cannot tell a cancellation from a downloader that never
 * connected, so the bytes at the moment of cancellation are asserted; and "it downloaded something"
 * cannot tell a followed redirect from a zero-byte body, so the size is asserted with a message
 * saying which failure it is.
 */
class HttpDownloaderTest {

    /**
     * A stall timeout short enough for a test and long enough not to fire on a healthy transfer.
     */
    private static final Duration STALL = Duration.ofMillis(500);

    private static final Duration CONNECT = Duration.ofSeconds(5);
    private static final Duration RESPONSE = Duration.ofSeconds(5);

    /** A digest-shaped string; nothing in this package ever compares it to anything. */
    private static final String PINNED_SHA256 =
            "4d0e94af851884ff8ab6a2223e73cf28ba3ced28f6af863d4a76d541009b9dd1";

    @TempDir private Path work;

    private static HttpDownloader downloader() {
        return new HttpDownloader(CONNECT, RESPONSE, STALL);
    }

    /** Deterministic bytes, so a spliced file is visibly wrong rather than merely different. */
    private static byte[] bytes(int length, char first) {
        byte[] body = new byte[length];
        for (int i = 0; i < length; i++) {
            body[i] = (byte) (first + (i % 26));
        }
        return body;
    }

    private static LoopbackHttpServer.Responder serving(byte[] body) {
        return LoopbackHttpServer.honouringRange(() -> body, () -> "\"v1\"");
    }

    @Nested
    @DisplayName("redirects")
    class Redirects {

        @Test
        @DisplayName("a cross-host redirect is followed and the whole body arrives")
        void aCrossHostRedirectIsFollowed() throws IOException {
            byte[] body = bytes(4096, 'a');
            try (LoopbackHttpServer assets = new LoopbackHttpServer(serving(body));
                    LoopbackHttpServer releases =
                            new LoopbackHttpServer(
                                    (request, out) ->
                                            LoopbackHttpServer.head(
                                                    out,
                                                    302,
                                                    "Found",
                                                    "Location: " + assets.uri("/signed"),
                                                    "Content-Length: 0"));
                    HttpDownloader downloader = downloader()) {

                Path file = work.resolve("artefact.zip");
                DownloadReport report =
                        downloader.fetch(
                                DownloadRequest.of(
                                        releases.uri("/releases/download/tag/a.zip"), file));

                assertAll(
                        () ->
                                assertEquals(
                                        body.length,
                                        Files.size(file),
                                        "a downloader left on HttpClient's default redirect policy"
                                                + " (Redirect.NEVER) fetches the body of the 302 --"
                                                + " zero bytes -- and returns normally, which is"
                                                + " this"
                                                + " unit's version of \"exit code 0 proves"
                                                + " nothing\""),
                        () -> assertArrayEquals(body, Files.readAllBytes(file)),
                        () -> assertEquals(body.length, report.bytesTransferred()),
                        () -> assertEquals(200, report.statusCode()),
                        () ->
                                assertEquals(
                                        1,
                                        releases.requests().size(),
                                        "the release URL is requested once"),
                        () ->
                                assertEquals(
                                        1,
                                        assets.requests().size(),
                                        "and the signed asset URL once, on the other host"));
            }
        }

        @Test
        @DisplayName("the redirect policy is not the JDK default, and not the unsafe one either")
        void theRedirectPolicyIsChosenDeliberately() {
            assertAll(
                    () ->
                            assertNotEquals(
                                    HttpClient.Redirect.NEVER,
                                    HttpDownloader.REDIRECT_POLICY,
                                    "the JDK default writes a zero-byte file for every GitHub"
                                            + " release download"),
                    () ->
                            assertNotEquals(
                                    HttpClient.Redirect.ALWAYS,
                                    HttpDownloader.REDIRECT_POLICY,
                                    "ALWAYS would follow a downgrade from https to http, which is"
                                            + " what DownloadUrls exists to prevent"),
                    () -> assertEquals(HttpClient.Redirect.NORMAL, HttpDownloader.REDIRECT_POLICY));
        }

        @Test
        @DisplayName(
                "a redirect onto a URL carrying credentials is refused, and nothing is written")
        void aRedirectOntoCredentialsIsRefused() throws IOException {
            byte[] body = bytes(64, 'a');
            AtomicReference<URI> target = new AtomicReference<>();
            try (LoopbackHttpServer server =
                            new LoopbackHttpServer(
                                    (request, out) -> {
                                        if (request.path().startsWith("/start")) {
                                            LoopbackHttpServer.head(
                                                    out,
                                                    302,
                                                    "Found",
                                                    "Location: " + target.get(),
                                                    "Content-Length: 0");
                                        } else {
                                            LoopbackHttpServer.head(
                                                    out,
                                                    200,
                                                    "OK",
                                                    "Content-Length: " + body.length);
                                            out.write(body);
                                        }
                                    });
                    HttpDownloader downloader = downloader()) {

                URI base = server.uri("/real");
                target.set(URI.create("http://user@127.0.0.1:" + base.getPort() + "/real"));
                Path file = work.resolve("artefact.zip");

                DownloadFailedException thrown =
                        assertThrows(
                                DownloadFailedException.class,
                                () ->
                                        downloader.fetch(
                                                DownloadRequest.of(server.uri("/start"), file)));

                assertAll(
                        () ->
                                assertTrue(
                                        thrown.getMessage().contains("will not fetch from"),
                                        thrown.getMessage()),
                        () -> assertTrue(thrown.getMessage().contains("user@127.0.0.1")),
                        () -> assertFalse(Files.exists(file), "no destination file"));
            }
        }
    }

    @Nested
    @DisplayName("the scheme rule")
    class SchemeRule {

        @Test
        @DisplayName("plain http to a routable host is refused before any connection is opened")
        void plainHttpIsRefusedBeforeConnecting() throws IOException {
            byte[] body = bytes(64, 'a');
            try (LoopbackHttpServer server = new LoopbackHttpServer(serving(body));
                    HttpDownloader downloader = downloader()) {

                // "localhost" resolves to this very server, so a downloader that resolved names
                // rather than reading the address literal would download it happily. The refusal
                // has to happen without a connection, and the server's empty request log is what
                // proves it did.
                URI byName = URI.create("http://localhost:" + server.uri("/x").getPort() + "/x");
                Path file = work.resolve("artefact.zip");

                IllegalArgumentException thrown =
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> downloader.download(byName, file, (bytes, total) -> {}));

                assertAll(
                        () ->
                                assertTrue(
                                        thrown.getMessage().contains("must be an https URL"),
                                        thrown.getMessage()),
                        () ->
                                assertTrue(
                                        thrown.getMessage().contains("SHA-256"),
                                        "the message says why the rule exists: "
                                                + thrown.getMessage()),
                        () -> assertTrue(thrown.getMessage().contains(byName.toString())),
                        () ->
                                assertEquals(
                                        List.of(),
                                        server.requests(),
                                        "the server that would have answered saw nothing"),
                        () -> assertFalse(Files.exists(file)));
            }
        }
    }

    @Nested
    @DisplayName("progress")
    class Progress {

        @Test
        @DisplayName("is monotone, reports the declared total, and ends at the true byte count")
        void isMonotoneAndEndsAtTheTrueTotal() throws IOException {
            byte[] body = bytes(300_000, 'a');
            try (LoopbackHttpServer server = new LoopbackHttpServer(serving(body));
                    HttpDownloader downloader = downloader()) {

                Path file = work.resolve("artefact.zip");
                RecordingProgressListener progress = new RecordingProgressListener();
                downloader.download(server.uri("/a.zip"), file, progress);

                assertAll(
                        () -> assertTrue(progress.isMonotone(), progress.byteCounts().toString()),
                        () -> assertEquals(body.length, progress.lastByteCount()),
                        () -> assertEquals(body.length, Files.size(file)),
                        () ->
                                assertEquals(
                                        java.util.Set.of((long) body.length),
                                        progress.totals(),
                                        "one declared total, reported unchanged throughout"),
                        () ->
                                assertTrue(
                                        progress.events().size() > 2,
                                        "a 300 kB body arrives in more than one chunk, so progress"
                                                + " is genuinely incremental: "
                                                + progress.events().size()
                                                + " report(s)"),
                        () ->
                                assertEquals(
                                        0L,
                                        progress.byteCounts().get(0),
                                        "the first report is the bytes already on disk"),
                        () -> assertEquals(List.of(), server.errors()));
            }
        }

        @Test
        @DisplayName("reports a negative total when the server declares no length")
        void reportsANegativeTotalWhenNoLengthIsDeclared() throws IOException {
            byte[] body = bytes(50_000, 'a');
            try (LoopbackHttpServer server =
                            new LoopbackHttpServer(
                                    (request, out) -> {
                                        // Chunked: a well-formed body with no Content-Length at
                                        // all.
                                        LoopbackHttpServer.head(
                                                out, 200, "OK", "Transfer-Encoding: chunked");
                                        LoopbackHttpServer.write(
                                                out, Integer.toHexString(body.length) + "\r\n");
                                        out.write(body);
                                        LoopbackHttpServer.write(out, "\r\n0\r\n\r\n");
                                    });
                    HttpDownloader downloader = downloader()) {

                Path file = work.resolve("artefact.zip");
                RecordingProgressListener progress = new RecordingProgressListener();
                DownloadReport report =
                        downloader.fetch(
                                DownloadRequest.of(server.uri("/a.zip"), file)
                                        .listeningTo(progress));

                assertAll(
                        () ->
                                assertEquals(
                                        java.util.Set.of(-1L),
                                        progress.totals(),
                                        "negative, never 0 and never a guess -- a caller must not"
                                                + " divide by it unchecked"),
                        () -> assertEquals(body.length, progress.lastByteCount()),
                        () -> assertTrue(progress.isMonotone()),
                        () -> assertFalse(report.totalWasDeclared()),
                        () -> assertEquals(-1L, report.declaredTotalBytes()),
                        () -> assertEquals(body.length, report.fileSizeBytes()),
                        () -> assertArrayEquals(body, Files.readAllBytes(file)));
            }
        }
    }

    @Nested
    @DisplayName("cancellation")
    class Cancellation {

        @Test
        @DisplayName("mid-transfer leaves no destination, no partial file, and is not a failure")
        void midTransferLeavesNothingBehind() throws IOException {
            byte[] body = bytes(2_000_000, 'a');
            try (LoopbackHttpServer server = new LoopbackHttpServer(serving(body));
                    HttpDownloader downloader = downloader()) {

                Path file = work.resolve("artefact.zip");
                AtomicBoolean stop = new AtomicBoolean();
                // Cancel from the progress listener, so the cancellation is guaranteed to happen
                // while bytes are arriving rather than before or after the transfer.
                DownloadProgressListener trigger =
                        (transferred, total) -> {
                            if (transferred >= 1000) {
                                stop.set(true);
                            }
                        };

                DownloadCancelledException thrown =
                        assertThrows(
                                DownloadCancelledException.class,
                                () ->
                                        downloader.fetch(
                                                DownloadRequest.of(server.uri("/a.zip"), file)
                                                        .listeningTo(trigger)
                                                        .cancellableBy(stop::get)));

                PartialDownload partial = PartialDownload.beside(file);
                assertAll(
                        () ->
                                assertTrue(
                                        thrown.bytesTransferred() >= 1000,
                                        "cancelled after bytes had arrived, not before: "
                                                + thrown.bytesTransferred()),
                        () ->
                                assertTrue(
                                        thrown.bytesTransferred() < body.length,
                                        "and before the transfer finished: "
                                                + thrown.bytesTransferred()
                                                + " of "
                                                + body.length),
                        () -> assertFalse(Files.exists(file), "no destination file"),
                        () ->
                                assertFalse(
                                        Files.exists(partial.file()),
                                        "and no partial file a caller could mistake for one"),
                        () -> assertFalse(Files.exists(partial.stateFile()), "and no resume state"),
                        () ->
                                assertInstanceOf(
                                        DownloadException.class,
                                        thrown,
                                        "it is still a download outcome"),
                        () ->
                                assertEquals(
                                        DownloadCancelledException.class,
                                        thrown.getClass(),
                                        "and it is its own type: a caller that catches"
                                                + " DownloadFailedException,"
                                                + " TruncatedDownloadException"
                                                + " or ArtefactUnavailableException must not catch"
                                                + " a"
                                                + " cancellation with it"));
            }
        }

        @Test
        @DisplayName("before the first byte opens no connection at all")
        void beforeTheFirstByteOpensNoConnection() throws IOException {
            byte[] body = bytes(64, 'a');
            try (LoopbackHttpServer server = new LoopbackHttpServer(serving(body));
                    HttpDownloader downloader = downloader()) {

                Path file = work.resolve("artefact.zip");
                DownloadCancelledException thrown =
                        assertThrows(
                                DownloadCancelledException.class,
                                () ->
                                        downloader.fetch(
                                                DownloadRequest.of(server.uri("/a.zip"), file)
                                                        .cancellableBy(() -> true)));

                assertAll(
                        () -> assertEquals(0L, thrown.bytesTransferred()),
                        () -> assertEquals(List.of(), server.requests()),
                        () -> assertFalse(Files.exists(file)));
            }
        }

        @Test
        @DisplayName("does not delete a destination that was already there")
        void doesNotDeleteAnExistingDestination() throws IOException {
            byte[] body = bytes(64, 'a');
            try (LoopbackHttpServer server = new LoopbackHttpServer(serving(body));
                    HttpDownloader downloader = downloader()) {

                Path file = work.resolve("artefact.zip");
                byte[] previous = bytes(11, 'z');
                Files.write(file, previous);

                assertThrows(
                        DownloadCancelledException.class,
                        () ->
                                downloader.fetch(
                                        DownloadRequest.of(server.uri("/a.zip"), file)
                                                .cancellableBy(() -> true)));

                assertArrayEquals(
                        previous,
                        Files.readAllBytes(file),
                        "a cancelled re-download must not destroy the artefact already installed");
            }
        }
    }

    @Nested
    @DisplayName("resume and clean restart")
    class Resume {

        @Test
        @DisplayName("sends a Range header and transfers only the remainder")
        void sendsARangeHeaderAndTransfersOnlyTheRemainder() throws IOException {
            byte[] body = bytes(1000, 'a');
            AtomicBoolean truncateFirst = new AtomicBoolean(true);
            try (LoopbackHttpServer server =
                            new LoopbackHttpServer(
                                    (request, out) -> {
                                        if (truncateFirst.compareAndSet(true, false)) {
                                            LoopbackHttpServer.head(
                                                    out,
                                                    200,
                                                    "OK",
                                                    "Content-Length: " + body.length,
                                                    "Accept-Ranges: bytes",
                                                    "ETag: \"v1\"");
                                            out.write(body, 0, 400);
                                            return;
                                        }
                                        serving(body).respond(request, out);
                                    });
                    HttpDownloader downloader = downloader()) {

                Path file = work.resolve("artefact.zip");
                DownloadRequest request =
                        DownloadRequest.of(server.uri("/a.zip"), file).resuming(true);

                TruncatedDownloadException first =
                        assertThrows(
                                TruncatedDownloadException.class, () -> downloader.fetch(request));
                PartialDownload partial = PartialDownload.beside(file);
                assertAll(
                        () -> assertEquals(1000L, first.declaredTotalBytes()),
                        () -> assertEquals(400L, first.receivedBytes()),
                        () -> assertFalse(Files.exists(file), "no destination yet"),
                        () ->
                                assertEquals(
                                        400L,
                                        Files.size(partial.file()),
                                        "the partial file is kept so the next attempt can resume"));

                DownloadReport second = downloader.fetch(request);

                assertAll(
                        () ->
                                assertEquals(
                                        Optional.of("bytes=400-"),
                                        server.requests().get(1).range(),
                                        "the second request actually asked for a range"),
                        () -> assertEquals(206, second.statusCode()),
                        () -> assertTrue(second.resumed()),
                        () -> assertEquals(400L, second.resumedFromBytes()),
                        () ->
                                assertEquals(
                                        600L,
                                        second.bytesTransferred(),
                                        "only the remainder crossed the network; a silent full"
                                                + " re-download would say 1000 here and would still"
                                                + " produce the right file"),
                        () -> assertEquals(1000L, second.fileSizeBytes()),
                        () -> assertArrayEquals(body, Files.readAllBytes(file)),
                        () ->
                                assertFalse(
                                        Files.exists(partial.stateFile()),
                                        "the resume state is removed once the file is in place"));
            }
        }

        @Test
        @DisplayName("a server that refuses Range causes a clean restart of the whole file")
        void aServerThatRefusesRangeRestartsCleanly() throws IOException {
            byte[] body = bytes(1000, 'a');
            AtomicBoolean truncateFirst = new AtomicBoolean(true);
            try (LoopbackHttpServer server =
                            new LoopbackHttpServer(
                                    (request, out) -> {
                                        if (truncateFirst.compareAndSet(true, false)) {
                                            LoopbackHttpServer.head(
                                                    out,
                                                    200,
                                                    "OK",
                                                    "Content-Length: " + body.length,
                                                    "ETag: \"v1\"");
                                            out.write(body, 0, 400);
                                            return;
                                        }
                                        LoopbackHttpServer.ignoringRange(() -> body)
                                                .respond(request, out);
                                    });
                    HttpDownloader downloader = downloader()) {

                Path file = work.resolve("artefact.zip");
                DownloadRequest request =
                        DownloadRequest.of(server.uri("/a.zip"), file).resuming(true);
                assertThrows(TruncatedDownloadException.class, () -> downloader.fetch(request));

                DownloadReport second = downloader.fetch(request);

                assertAll(
                        () ->
                                assertEquals(
                                        Optional.of("bytes=400-"),
                                        server.requests().get(1).range(),
                                        "a range was asked for"),
                        () -> assertEquals(200, second.statusCode(), "and refused"),
                        () -> assertTrue(second.rangeRequested()),
                        () -> assertFalse(second.resumed()),
                        () -> assertEquals(0L, second.resumedFromBytes()),
                        () ->
                                assertEquals(
                                        1000L,
                                        second.bytesTransferred(),
                                        "the whole file crossed the network -- assert the count,"
                                                + " because the file on disk looks identical either"
                                                + " way"),
                        () -> assertArrayEquals(body, Files.readAllBytes(file)));
            }
        }

        @Test
        @DisplayName("a changed ETag discards the partial file and restarts from zero")
        void aChangedEtagRestartsFromZero() throws IOException {
            byte[] body = bytes(1000, 'a');
            AtomicReference<String> etag = new AtomicReference<>("\"v1\"");
            AtomicBoolean truncateFirst = new AtomicBoolean(true);
            try (LoopbackHttpServer server =
                            new LoopbackHttpServer(
                                    (request, out) -> {
                                        if (truncateFirst.compareAndSet(true, false)) {
                                            LoopbackHttpServer.head(
                                                    out,
                                                    200,
                                                    "OK",
                                                    "Content-Length: " + body.length,
                                                    "Accept-Ranges: bytes",
                                                    "ETag: " + etag.get());
                                            out.write(body, 0, 400);
                                            return;
                                        }
                                        LoopbackHttpServer.honouringRange(() -> body, etag::get)
                                                .respond(request, out);
                                    });
                    HttpDownloader downloader = downloader()) {

                Path file = work.resolve("artefact.zip");
                DownloadRequest request =
                        DownloadRequest.of(server.uri("/a.zip"), file).resuming(true);
                assertThrows(TruncatedDownloadException.class, () -> downloader.fetch(request));

                etag.set("\"v2\"");
                DownloadReport second = downloader.fetch(request);

                assertAll(
                        () ->
                                assertEquals(
                                        3, server.requests().size(), "range, then a fresh request"),
                        () ->
                                assertEquals(
                                        Optional.of("bytes=400-"),
                                        server.requests().get(1).range(),
                                        "the resume was attempted"),
                        () ->
                                assertEquals(
                                        Optional.empty(),
                                        server.requests().get(2).range(),
                                        "and abandoned: the restart asks for no range at all"),
                        () -> assertEquals(200, second.statusCode()),
                        () -> assertEquals(0L, second.resumedFromBytes()),
                        () -> assertEquals(1000L, second.bytesTransferred()),
                        () -> assertArrayEquals(body, Files.readAllBytes(file)));
            }
        }

        @Test
        @DisplayName("a changed length discards the partial file and restarts from zero")
        void aChangedLengthRestartsFromZero() throws IOException {
            byte[] shortBody = bytes(1000, 'a');
            byte[] longBody = bytes(1500, 'a');
            AtomicReference<byte[]> served = new AtomicReference<>(shortBody);
            AtomicBoolean truncateFirst = new AtomicBoolean(true);
            try (LoopbackHttpServer server =
                            new LoopbackHttpServer(
                                    (request, out) -> {
                                        byte[] body = served.get();
                                        if (truncateFirst.compareAndSet(true, false)) {
                                            LoopbackHttpServer.head(
                                                    out,
                                                    200,
                                                    "OK",
                                                    "Content-Length: " + body.length,
                                                    "Accept-Ranges: bytes",
                                                    "ETag: \"same\"");
                                            out.write(body, 0, 400);
                                            return;
                                        }
                                        LoopbackHttpServer.honouringRange(
                                                        served::get, () -> "\"same\"")
                                                .respond(request, out);
                                    });
                    HttpDownloader downloader = downloader()) {

                Path file = work.resolve("artefact.zip");
                DownloadRequest request =
                        DownloadRequest.of(server.uri("/a.zip"), file).resuming(true);
                assertThrows(TruncatedDownloadException.class, () -> downloader.fetch(request));

                served.set(longBody);
                DownloadReport second = downloader.fetch(request);

                assertAll(
                        () -> assertEquals(3, server.requests().size()),
                        () -> assertEquals(0L, second.resumedFromBytes()),
                        () -> assertEquals(1500L, second.bytesTransferred()),
                        () -> assertArrayEquals(longBody, Files.readAllBytes(file)));
            }
        }

        @Test
        @DisplayName("a partial file with no resume state beside it is discarded, not resumed")
        void aPartialFileWithNoStateIsNotResumed() throws IOException {
            byte[] body = bytes(1000, 'a');
            try (LoopbackHttpServer server = new LoopbackHttpServer(serving(body));
                    HttpDownloader downloader = downloader()) {

                Path file = work.resolve("artefact.zip");
                PartialDownload partial = PartialDownload.beside(file);
                Files.write(partial.file(), bytes(400, 'z'));

                DownloadReport report =
                        downloader.fetch(
                                DownloadRequest.of(server.uri("/a.zip"), file).resuming(true));

                assertAll(
                        () ->
                                assertEquals(
                                        Optional.empty(),
                                        server.requests().get(0).range(),
                                        "bytes of unknown provenance are not appended to"),
                        () -> assertEquals(1000L, report.bytesTransferred()),
                        () -> assertArrayEquals(body, Files.readAllBytes(file)));
            }
        }

        @Test
        @DisplayName("an unreadable resume state is discarded, not guessed at")
        void anUnreadableResumeStateIsDiscarded() throws IOException {
            byte[] body = bytes(1000, 'a');
            try (LoopbackHttpServer server = new LoopbackHttpServer(serving(body));
                    HttpDownloader downloader = downloader()) {

                Path file = work.resolve("artefact.zip");
                PartialDownload partial = PartialDownload.beside(file);
                Files.write(partial.file(), bytes(400, 'z'));
                Files.writeString(partial.stateFile(), "who knows what this is\n");

                DownloadReport report =
                        downloader.fetch(
                                DownloadRequest.of(server.uri("/a.zip"), file).resuming(true));

                assertAll(
                        () -> assertEquals(Optional.empty(), server.requests().get(0).range()),
                        () -> assertEquals(1000L, report.bytesTransferred()),
                        () -> assertArrayEquals(body, Files.readAllBytes(file)));
            }
        }

        @Test
        @DisplayName("a resume re-requests the original URL rather than a stored redirect target")
        void aResumeReRequestsTheOriginalUrl() throws IOException {
            byte[] body = bytes(1000, 'a');
            AtomicBoolean truncateFirst = new AtomicBoolean(true);
            try (LoopbackHttpServer assets =
                            new LoopbackHttpServer(
                                    (request, out) -> {
                                        if (truncateFirst.compareAndSet(true, false)) {
                                            LoopbackHttpServer.head(
                                                    out,
                                                    200,
                                                    "OK",
                                                    "Content-Length: " + body.length,
                                                    "Accept-Ranges: bytes",
                                                    "ETag: \"v1\"");
                                            out.write(body, 0, 400);
                                            return;
                                        }
                                        serving(body).respond(request, out);
                                    });
                    LoopbackHttpServer releases =
                            new LoopbackHttpServer(
                                    (request, out) ->
                                            LoopbackHttpServer.head(
                                                    out,
                                                    302,
                                                    "Found",
                                                    "Location: "
                                                            + assets.uri(
                                                                    "/signed?expires-in-an-hour"),
                                                    "Content-Length: 0"));
                    HttpDownloader downloader = downloader()) {

                Path file = work.resolve("artefact.zip");
                DownloadRequest request =
                        DownloadRequest.of(releases.uri("/releases/download/tag/a.zip"), file)
                                .resuming(true);
                assertThrows(TruncatedDownloadException.class, () -> downloader.fetch(request));

                DownloadReport second = downloader.fetch(request);

                assertAll(
                        () ->
                                assertEquals(
                                        2,
                                        releases.requests().size(),
                                        "the resume went back to the release URL for a fresh"
                                                + " signature; the signed asset URL expires in"
                                                + " about"
                                                + " an hour and is never stored"),
                        () ->
                                assertEquals(
                                        Optional.of("bytes=400-"),
                                        releases.requests().get(1).range(),
                                        "and carried the range through the redirect"),
                        () -> assertEquals(600L, second.bytesTransferred()),
                        () -> assertArrayEquals(body, Files.readAllBytes(file)));
            }
        }

        @Test
        @DisplayName("a 416 on a resume restarts from zero")
        void a416OnAResumeRestartsFromZero() throws IOException {
            byte[] body = bytes(300, 'a');
            try (LoopbackHttpServer server =
                            new LoopbackHttpServer(
                                    (request, out) -> {
                                        if (request.range().isPresent()) {
                                            LoopbackHttpServer.head(
                                                    out,
                                                    416,
                                                    "Range Not Satisfiable",
                                                    "Content-Length: 0");
                                            return;
                                        }
                                        serving(body).respond(request, out);
                                    });
                    HttpDownloader downloader = downloader()) {

                Path file = work.resolve("artefact.zip");
                PartialDownload partial = PartialDownload.beside(file);
                Files.write(partial.file(), bytes(900, 'z'));
                partial.recordState(900, "\"v1\"");

                DownloadReport report =
                        downloader.fetch(
                                DownloadRequest.of(server.uri("/a.zip"), file).resuming(true));

                assertAll(
                        () -> assertEquals(2, server.requests().size()),
                        () -> assertEquals(Optional.empty(), server.requests().get(1).range()),
                        () -> assertEquals(300L, report.bytesTransferred()),
                        () -> assertEquals(0L, report.resumedFromBytes()),
                        () -> assertArrayEquals(body, Files.readAllBytes(file)));
            }
        }

        @Test
        @DisplayName("the Downloader port method never resumes, even with a usable partial file")
        void thePortMethodNeverResumes() throws IOException {
            byte[] body = bytes(1000, 'a');
            try (LoopbackHttpServer server = new LoopbackHttpServer(serving(body));
                    HttpDownloader downloader = downloader()) {

                Path file = work.resolve("artefact.zip");
                PartialDownload partial = PartialDownload.beside(file);
                Files.write(partial.file(), java.util.Arrays.copyOf(body, 400));
                partial.recordState(1000, "\"v1\"");

                downloader.download(server.uri("/a.zip"), file, (bytes, total) -> {});

                assertAll(
                        () ->
                                assertEquals(
                                        Optional.empty(),
                                        server.requests().get(0).range(),
                                        "the port carries no expected checksum, so continuing a"
                                                + " partial file that nothing can be checked"
                                                + " against"
                                                + " is not a risk it may take"),
                        () -> assertArrayEquals(body, Files.readAllBytes(file)));
            }
        }
    }

    @Nested
    @DisplayName("failures, told apart")
    class Failures {

        @ParameterizedTest(name = "HTTP {0} is an availability failure")
        @ValueSource(ints = {404, 410})
        void aVanishedArtefactIsAnAvailabilityFailure(int status) throws IOException {
            try (LoopbackHttpServer server =
                            new LoopbackHttpServer(
                                    (request, out) ->
                                            LoopbackHttpServer.head(
                                                    out, status, "Gone", "Content-Length: 0"));
                    HttpDownloader downloader = downloader()) {

                Path file = work.resolve("artefact.zip");
                URI url = server.uri("/releases/download/tag/a.zip");
                ArtefactUnavailableException thrown =
                        assertThrows(
                                ArtefactUnavailableException.class,
                                () ->
                                        downloader.fetch(
                                                DownloadRequest.of(url, file)
                                                        .expecting(PINNED_SHA256)));

                assertAll(
                        () -> assertEquals(status, thrown.statusCode()),
                        () -> assertEquals(url, thrown.source()),
                        () -> assertEquals(Optional.of(PINNED_SHA256), thrown.expectedSha256()),
                        () -> assertTrue(thrown.getMessage().contains(url.toString())),
                        () -> assertTrue(thrown.getMessage().contains(PINNED_SHA256)),
                        () -> assertTrue(thrown.getMessage().contains("D-008")),
                        () -> assertFalse(Files.exists(file)));
            }
        }

        @Test
        @DisplayName("an availability failure says so even when the caller pinned no checksum")
        void anAvailabilityFailureWithoutAChecksum() throws IOException {
            try (LoopbackHttpServer server =
                            new LoopbackHttpServer(
                                    (request, out) ->
                                            LoopbackHttpServer.head(
                                                    out, 404, "Not Found", "Content-Length: 0"));
                    HttpDownloader downloader = downloader()) {

                ArtefactUnavailableException thrown =
                        assertThrows(
                                ArtefactUnavailableException.class,
                                () ->
                                        downloader.fetch(
                                                DownloadRequest.of(
                                                        server.uri("/a.zip"),
                                                        work.resolve("artefact.zip"))));

                assertAll(
                        () -> assertEquals(Optional.empty(), thrown.expectedSha256()),
                        () ->
                                assertTrue(
                                        thrown.getMessage()
                                                .contains("not supplied by the caller")));
            }
        }

        @ParameterizedTest(name = "HTTP {0} is a download failure, not an availability failure")
        @ValueSource(ints = {401, 403, 416, 429, 500, 503, 204})
        void otherStatusesAreFailuresRatherThanAvailability(int status) throws IOException {
            try (LoopbackHttpServer server =
                            new LoopbackHttpServer(
                                    (request, out) ->
                                            LoopbackHttpServer.head(
                                                    out, status, "Nope", "Content-Length: 0"));
                    HttpDownloader downloader = downloader()) {

                DownloadException thrown =
                        assertThrows(
                                DownloadException.class,
                                () ->
                                        downloader.fetch(
                                                DownloadRequest.of(
                                                                server.uri("/a.zip"),
                                                                work.resolve("artefact.zip"))
                                                        .expecting(PINNED_SHA256)));

                assertAll(
                        () ->
                                assertInstanceOf(
                                        DownloadFailedException.class,
                                        thrown,
                                        "a 403 is an expired signature and a 5xx is a bad day"
                                                + " upstream; neither means the artefact has been"
                                                + " deleted, and calling them availability failures"
                                                + " would send someone to look at a release page"
                                                + " that is fine"),
                        () -> assertTrue(thrown.getMessage().contains(String.valueOf(status))));
            }
        }

        @ParameterizedTest(name = "with resume={0} the 404 is still an availability failure")
        @CsvSource({"true", "false"})
        void availabilityDoesNotDependOnWhetherAResumeWasAttempted(boolean resume)
                throws IOException {
            try (LoopbackHttpServer server =
                            new LoopbackHttpServer(
                                    (request, out) ->
                                            LoopbackHttpServer.head(
                                                    out, 404, "Not Found", "Content-Length: 0"));
                    HttpDownloader downloader = downloader()) {

                Path file = work.resolve("artefact.zip");
                PartialDownload partial = PartialDownload.beside(file);
                Files.write(partial.file(), bytes(40, 'z'));
                partial.recordState(1000, "\"v1\"");

                assertThrows(
                        ArtefactUnavailableException.class,
                        () ->
                                downloader.fetch(
                                        DownloadRequest.of(server.uri("/a.zip"), file)
                                                .resuming(resume)
                                                .expecting(PINNED_SHA256)));
            }
        }

        @Test
        @DisplayName("a body that stops short is truncation, and keeps the partial file")
        void aBodyThatStopsShortIsTruncation() throws IOException {
            byte[] body = bytes(1000, 'a');
            try (LoopbackHttpServer server =
                            new LoopbackHttpServer(
                                    (request, out) -> {
                                        LoopbackHttpServer.head(
                                                out, 200, "OK", "Content-Length: " + body.length);
                                        out.write(body, 0, 250);
                                    });
                    HttpDownloader downloader = downloader()) {

                Path file = work.resolve("artefact.zip");
                TruncatedDownloadException thrown =
                        assertThrows(
                                TruncatedDownloadException.class,
                                () ->
                                        downloader.fetch(
                                                DownloadRequest.of(server.uri("/a.zip"), file)
                                                        .expecting(PINNED_SHA256)));

                PartialDownload partial = PartialDownload.beside(file);
                assertAll(
                        () -> assertEquals(1000L, thrown.declaredTotalBytes()),
                        () -> assertEquals(250L, thrown.receivedBytes()),
                        () -> assertTrue(thrown.getMessage().contains("stopped short")),
                        () ->
                                assertTrue(
                                        thrown.getMessage().contains("still published"),
                                        "a truncated body is not an availability failure"),
                        () -> assertFalse(Files.exists(file), "no destination file"),
                        () -> assertEquals(250L, Files.size(partial.file())),
                        () -> assertTrue(Files.exists(partial.stateFile())));
            }
        }

        @Test
        @DisplayName("a 206 whose content-range total exceeds what arrives is truncation")
        void aContentRangeTotalLargerThanTheBodyIsTruncation() throws IOException {
            byte[] body = bytes(400, 'a');
            try (LoopbackHttpServer server =
                            new LoopbackHttpServer(
                                    (request, out) -> {
                                        LoopbackHttpServer.head(
                                                out,
                                                206,
                                                "Partial Content",
                                                "Content-Length: 350",
                                                "Content-Range: bytes 50-399/1000",
                                                "ETag: \"v1\"");
                                        out.write(body, 50, 350);
                                    });
                    HttpDownloader downloader = downloader()) {

                Path file = work.resolve("artefact.zip");
                PartialDownload partial = PartialDownload.beside(file);
                Files.write(partial.file(), java.util.Arrays.copyOf(body, 50));
                partial.recordState(1000, "\"v1\"");

                TruncatedDownloadException thrown =
                        assertThrows(
                                TruncatedDownloadException.class,
                                () ->
                                        downloader.fetch(
                                                DownloadRequest.of(server.uri("/a.zip"), file)
                                                        .resuming(true)));

                assertAll(
                        () -> assertEquals(1000L, thrown.declaredTotalBytes()),
                        () -> assertEquals(400L, thrown.receivedBytes()),
                        () -> assertFalse(Files.exists(file)));
            }
        }

        @Test
        @DisplayName("a refused connection is a network failure naming the URL")
        void aRefusedConnectionIsANetworkFailure() throws IOException {
            int deadPort;
            try (LoopbackHttpServer server =
                    new LoopbackHttpServer(
                            (request, out) -> LoopbackHttpServer.head(out, 200, "OK"))) {
                deadPort = server.uri("/x").getPort();
            }
            try (HttpDownloader downloader = downloader()) {
                URI url = URI.create("http://127.0.0.1:" + deadPort + "/a.zip");
                DownloadFailedException thrown =
                        assertThrows(
                                DownloadFailedException.class,
                                () ->
                                        downloader.fetch(
                                                DownloadRequest.of(
                                                                url, work.resolve("artefact.zip"))
                                                        .expecting(PINNED_SHA256)));

                assertAll(
                        () -> assertTrue(thrown.getMessage().contains(url.toString())),
                        () ->
                                assertTrue(
                                        thrown.getMessage().contains("ConnectException")
                                                || thrown.getMessage()
                                                        .contains("Connection refused"),
                                        "java.net.ConnectException often carries a null message, so"
                                                + " the class name is named explicitly: "
                                                + thrown.getMessage()));
            }
        }

        @Test
        @DisplayName("a stalled server is a failure rather than a hang")
        void aStalledServerIsAFailure() throws IOException {
            byte[] body = bytes(2000, 'a');
            try (LoopbackHttpServer server =
                            new LoopbackHttpServer(
                                    (request, out) -> {
                                        LoopbackHttpServer.head(
                                                out, 200, "OK", "Content-Length: " + body.length);
                                        out.write(body, 0, 100);
                                        out.flush();
                                        try {
                                            Thread.sleep(3_000);
                                        } catch (InterruptedException e) {
                                            Thread.currentThread().interrupt();
                                        }
                                    });
                    HttpDownloader downloader = downloader()) {

                long started = System.nanoTime();
                DownloadFailedException thrown =
                        assertThrows(
                                DownloadFailedException.class,
                                () ->
                                        downloader.fetch(
                                                DownloadRequest.of(
                                                        server.uri("/a.zip"),
                                                        work.resolve("artefact.zip"))));
                long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;

                assertAll(
                        () ->
                                assertTrue(
                                        thrown.getMessage().contains("stalled"),
                                        thrown.getMessage()),
                        () ->
                                assertTrue(
                                        thrown.getMessage().contains("after 100 byte(s)"),
                                        thrown.getMessage()),
                        () ->
                                assertTrue(
                                        elapsedMillis < 2_500,
                                        "it gave up on the stall rather than waiting for the"
                                                + " server:"
                                                + " "
                                                + elapsedMillis
                                                + " ms"));
            }
        }

        @Test
        @DisplayName("a server that accepts the connection and sends no headers times out")
        void aServerThatSendsNoHeadersTimesOut() throws IOException {
            try (LoopbackHttpServer server =
                            new LoopbackHttpServer(
                                    (request, out) -> {
                                        try {
                                            Thread.sleep(3_000);
                                        } catch (InterruptedException e) {
                                            Thread.currentThread().interrupt();
                                        }
                                    });
                    HttpDownloader downloader =
                            new HttpDownloader(CONNECT, Duration.ofMillis(300), STALL)) {

                long started = System.nanoTime();
                DownloadFailedException thrown =
                        assertThrows(
                                DownloadFailedException.class,
                                () ->
                                        downloader.fetch(
                                                DownloadRequest.of(
                                                        server.uri("/a.zip"),
                                                        work.resolve("artefact.zip"))));
                long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;

                assertAll(
                        () -> assertTrue(thrown.getMessage().contains("timed out requesting")),
                        () -> assertTrue(thrown.getMessage().contains("response timeout 300 ms")),
                        () ->
                                assertTrue(
                                        thrown.getMessage().contains("connect timeout"),
                                        "both timeouts are named, because the exception says which"
                                                + " one fired: "
                                                + thrown.getMessage()),
                        () -> assertTrue(elapsedMillis < 2_500, elapsedMillis + " ms"));
            }
        }

        @Test
        @DisplayName("a body with no declared length that fails is a failure, not a truncation")
        void aBodyWithNoDeclaredLengthThatFailsIsAFailure() throws IOException {
            byte[] body = bytes(1000, 'a');
            try (LoopbackHttpServer server =
                            new LoopbackHttpServer(
                                    (request, out) -> {
                                        LoopbackHttpServer.head(
                                                out, 200, "OK", "Transfer-Encoding: chunked");
                                        LoopbackHttpServer.write(out, "3e8\r\n");
                                        out.write(body, 0, 200);
                                        out.flush();
                                        // and then the connection simply goes away, mid-chunk
                                    });
                    HttpDownloader downloader = downloader()) {

                Path file = work.resolve("artefact.zip");
                DownloadFailedException thrown =
                        assertThrows(
                                DownloadFailedException.class,
                                () ->
                                        downloader.fetch(
                                                DownloadRequest.of(server.uri("/a.zip"), file)));

                assertAll(
                        () ->
                                assertTrue(
                                        thrown.getMessage().contains("response body from"),
                                        thrown.getMessage()),
                        () ->
                                assertTrue(
                                        thrown.getMessage().contains("byte(s)"),
                                        "how much arrived is named even though the total is not"
                                                + " known"),
                        () -> assertFalse(Files.exists(file)));
            }
        }

        @Test
        @DisplayName("a 206 whose content-range total is * reports a negative total, not a guess")
        void a206WithAnUnknownTotalReportsANegativeTotal() throws IOException {
            byte[] body = bytes(400, 'a');
            try (LoopbackHttpServer server =
                            new LoopbackHttpServer(
                                    (request, out) -> {
                                        LoopbackHttpServer.head(
                                                out,
                                                206,
                                                "Partial Content",
                                                "Content-Length: 350",
                                                "Content-Range: bytes 50-399/*",
                                                "ETag: \"v1\"");
                                        out.write(body, 50, 350);
                                    });
                    HttpDownloader downloader = downloader()) {

                Path file = work.resolve("artefact.zip");
                PartialDownload partial = PartialDownload.beside(file);
                Files.write(partial.file(), java.util.Arrays.copyOf(body, 50));
                partial.recordState(-1, "\"v1\"");

                RecordingProgressListener progress = new RecordingProgressListener();
                DownloadReport report =
                        downloader.fetch(
                                DownloadRequest.of(server.uri("/a.zip"), file)
                                        .resuming(true)
                                        .listeningTo(progress));

                assertAll(
                        () -> assertEquals(-1L, report.declaredTotalBytes()),
                        () -> assertFalse(report.totalWasDeclared()),
                        () -> assertEquals(java.util.Set.of(-1L), progress.totals()),
                        () -> assertEquals(50L, report.resumedFromBytes()),
                        () -> assertEquals(350L, report.bytesTransferred()),
                        () -> assertEquals(400L, report.fileSizeBytes()),
                        () -> assertArrayEquals(body, Files.readAllBytes(file)));
            }
        }

        @Test
        @DisplayName("an interrupted thread stops the request rather than swallowing the interrupt")
        void anInterruptedThreadStopsTheRequest() throws IOException {
            byte[] body = bytes(64, 'a');
            LoopbackHttpServer server = new LoopbackHttpServer(serving(body));
            HttpDownloader downloader = downloader();
            try {
                Thread.currentThread().interrupt();
                DownloadFailedException thrown =
                        assertThrows(
                                DownloadFailedException.class,
                                () ->
                                        downloader.fetch(
                                                DownloadRequest.of(
                                                        server.uri("/a.zip"),
                                                        work.resolve("artefact.zip"))));

                assertAll(
                        () ->
                                assertTrue(
                                        thrown.getMessage().contains("interrupted"),
                                        thrown.getMessage()),
                        () ->
                                assertTrue(
                                        Thread.interrupted(),
                                        "the interrupt is restored rather than swallowed"));
            } finally {
                Thread.interrupted();
                downloader.close();
                server.close();
            }
        }

        @Test
        @DisplayName("an interrupt during the body stops the transfer and restores the flag")
        void anInterruptDuringTheBodyStopsTheTransfer() throws IOException {
            byte[] body = bytes(200_000, 'a');
            LoopbackHttpServer server = new LoopbackHttpServer(serving(body));
            HttpDownloader downloader = downloader();
            Path file = work.resolve("artefact.zip");
            try {
                DownloadProgressListener interrupter =
                        (transferred, total) -> Thread.currentThread().interrupt();
                DownloadFailedException thrown =
                        assertThrows(
                                DownloadFailedException.class,
                                () ->
                                        downloader.fetch(
                                                DownloadRequest.of(server.uri("/a.zip"), file)
                                                        .listeningTo(interrupter)));

                assertAll(
                        () ->
                                assertTrue(
                                        thrown.getMessage()
                                                .contains("interrupted while downloading"),
                                        thrown.getMessage()),
                        () -> assertTrue(Thread.interrupted()),
                        () -> assertFalse(Files.exists(file)));
            } finally {
                Thread.interrupted();
                downloader.close();
                server.close();
            }
        }

        @Test
        @DisplayName("a 206 answered to a request with no range is refused")
        void a206WithoutARangeRequestIsRefused() throws IOException {
            byte[] body = bytes(300, 'a');
            try (LoopbackHttpServer server =
                            new LoopbackHttpServer(
                                    (request, out) -> {
                                        LoopbackHttpServer.head(
                                                out,
                                                206,
                                                "Partial Content",
                                                "Content-Length: 100",
                                                "Content-Range: bytes 0-99/300");
                                        out.write(body, 0, 100);
                                    });
                    HttpDownloader downloader = downloader()) {

                Path file = work.resolve("artefact.zip");
                DownloadFailedException thrown =
                        assertThrows(
                                DownloadFailedException.class,
                                () ->
                                        downloader.fetch(
                                                DownloadRequest.of(server.uri("/a.zip"), file)));

                assertAll(
                        () -> assertTrue(thrown.getMessage().contains("asked for no range")),
                        () -> assertFalse(Files.exists(file)));
            }
        }

        @ParameterizedTest(name = "a 206 with content-range \"{0}\" is refused")
        @ValueSource(strings = {"bytes 0-299/1000", "bytes forty to fifty", "bytes 50-99/*/*"})
        void a206WithAWrongOrUnreadableContentRangeIsRefused(String contentRange)
                throws IOException {
            byte[] body = bytes(400, 'a');
            try (LoopbackHttpServer server =
                            new LoopbackHttpServer(
                                    (request, out) -> {
                                        LoopbackHttpServer.head(
                                                out,
                                                206,
                                                "Partial Content",
                                                "Content-Length: 350",
                                                "Content-Range: " + contentRange,
                                                "ETag: \"v1\"");
                                        out.write(body, 50, 350);
                                    });
                    HttpDownloader downloader = downloader()) {

                Path file = work.resolve("artefact.zip");
                PartialDownload partial = PartialDownload.beside(file);
                Files.write(partial.file(), java.util.Arrays.copyOf(body, 50));
                partial.recordState(1000, "\"v1\"");

                DownloadFailedException thrown =
                        assertThrows(
                                DownloadFailedException.class,
                                () ->
                                        downloader.fetch(
                                                DownloadRequest.of(server.uri("/a.zip"), file)
                                                        .resuming(true)));

                assertAll(
                        () -> assertTrue(thrown.getMessage().contains("206 Partial Content")),
                        () -> assertFalse(Files.exists(file)));
            }
        }

        @Test
        @DisplayName("a 206 with no content-range at all is refused")
        void a206WithNoContentRangeIsRefused() throws IOException {
            byte[] body = bytes(400, 'a');
            try (LoopbackHttpServer server =
                            new LoopbackHttpServer(
                                    (request, out) -> {
                                        LoopbackHttpServer.head(
                                                out, 206, "Partial Content", "Content-Length: 350");
                                        out.write(body, 50, 350);
                                    });
                    HttpDownloader downloader = downloader()) {

                Path file = work.resolve("artefact.zip");
                PartialDownload partial = PartialDownload.beside(file);
                Files.write(partial.file(), java.util.Arrays.copyOf(body, 50));
                partial.recordState(1000, "\"v1\"");

                DownloadFailedException thrown =
                        assertThrows(
                                DownloadFailedException.class,
                                () ->
                                        downloader.fetch(
                                                DownloadRequest.of(server.uri("/a.zip"), file)
                                                        .resuming(true)));
                assertTrue(thrown.getMessage().contains("no content-range header"));
            }
        }

        @Test
        @DisplayName("the three failures read differently from one another")
        void theThreeFailuresReadDifferently() throws IOException {
            Path file = work.resolve("artefact.zip");
            byte[] body = bytes(1000, 'a');
            String availability;
            String truncated;
            String network;

            try (LoopbackHttpServer gone =
                            new LoopbackHttpServer(
                                    (request, out) ->
                                            LoopbackHttpServer.head(
                                                    out, 404, "Not Found", "Content-Length: 0"));
                    HttpDownloader downloader = downloader()) {
                availability =
                        assertThrows(
                                        ArtefactUnavailableException.class,
                                        () ->
                                                downloader.fetch(
                                                        DownloadRequest.of(gone.uri("/a.zip"), file)
                                                                .expecting(PINNED_SHA256)))
                                .getMessage();
            }
            try (LoopbackHttpServer shortBody =
                            new LoopbackHttpServer(
                                    (request, out) -> {
                                        LoopbackHttpServer.head(
                                                out, 200, "OK", "Content-Length: " + body.length);
                                        out.write(body, 0, 250);
                                    });
                    HttpDownloader downloader = downloader()) {
                truncated =
                        assertThrows(
                                        TruncatedDownloadException.class,
                                        () ->
                                                downloader.fetch(
                                                        DownloadRequest.of(
                                                                        shortBody.uri("/a.zip"),
                                                                        file)
                                                                .expecting(PINNED_SHA256)))
                                .getMessage();
            }
            int deadPort;
            try (LoopbackHttpServer server =
                    new LoopbackHttpServer(
                            (request, out) -> LoopbackHttpServer.head(out, 200, "OK"))) {
                deadPort = server.uri("/x").getPort();
            }
            try (HttpDownloader downloader = downloader()) {
                network =
                        assertThrows(
                                        DownloadFailedException.class,
                                        () ->
                                                downloader.fetch(
                                                        DownloadRequest.of(
                                                                        URI.create(
                                                                                "http://127.0.0.1:"
                                                                                        + deadPort
                                                                                        + "/a.zip"),
                                                                        file)
                                                                .expecting(PINNED_SHA256)))
                                .getMessage();
            }

            assertAll(
                    () -> assertTrue(availability.contains("no longer available upstream")),
                    () -> assertTrue(availability.contains(PINNED_SHA256)),
                    () -> assertFalse(truncated.contains("no longer available upstream")),
                    () -> assertTrue(truncated.contains("stopped short")),
                    () -> assertFalse(network.contains("no longer available upstream")),
                    () -> assertFalse(network.contains("stopped short")),
                    () -> assertTrue(network.contains("failed before any body arrived")));
        }
    }

    @Nested
    @DisplayName("what a downloader is not")
    class NotADownloadersJob {

        @Test
        @DisplayName("the expected checksum is carried for the message and never checked")
        void theExpectedChecksumIsNeverChecked() throws IOException {
            byte[] body = bytes(500, 'a');
            try (LoopbackHttpServer server = new LoopbackHttpServer(serving(body));
                    HttpDownloader downloader = downloader()) {

                Path file = work.resolve("artefact.zip");
                DownloadReport report =
                        downloader.fetch(
                                DownloadRequest.of(server.uri("/a.zip"), file)
                                        .expecting(PINNED_SHA256));

                assertAll(
                        () ->
                                assertArrayEquals(
                                        body,
                                        Files.readAllBytes(file),
                                        "the bytes are nothing like that digest, and the download"
                                                + " succeeds anyway: a downloader never decides"
                                                + " whether"
                                                + " a file is trustworthy, which is what lets the"
                                                + " verifier be tested against a corrupted"
                                                + " download"),
                        () -> assertEquals(500L, report.fileSizeBytes()));
            }
        }

        @Test
        @DisplayName("the destination appears only when the transfer is complete")
        void theDestinationAppearsOnlyAtTheEnd() throws IOException {
            byte[] body = bytes(1000, 'a');
            AtomicReference<Boolean> destinationExistedMidTransfer = new AtomicReference<>(false);
            try (LoopbackHttpServer server = new LoopbackHttpServer(serving(body));
                    HttpDownloader downloader = downloader()) {

                Path file = work.resolve("artefact.zip");
                DownloadProgressListener watcher =
                        (transferred, total) -> {
                            if (Files.exists(file)) {
                                destinationExistedMidTransfer.set(true);
                            }
                        };
                downloader.fetch(
                        DownloadRequest.of(server.uri("/a.zip"), file).listeningTo(watcher));

                assertAll(
                        () ->
                                assertFalse(
                                        destinationExistedMidTransfer.get(),
                                        "no half-written artefact ever appears where an installer"
                                                + " would look for one"),
                        () -> assertTrue(Files.exists(file)),
                        () ->
                                assertFalse(
                                        Files.exists(PartialDownload.beside(file).file()),
                                        "and the temporary file is gone afterwards"));
            }
        }

        @Test
        @DisplayName("a destination with no parent directory needs none created")
        void aDestinationWithNoParentNeedsNoDirectory() throws IOException {
            // Path.of("a.zip").getParent() is null, and Files.createDirectories(null) throws. The
            // server answers 404 so that nothing is ever written into the working directory.
            try (LoopbackHttpServer server =
                            new LoopbackHttpServer(
                                    (request, out) ->
                                            LoopbackHttpServer.head(
                                                    out, 404, "Not Found", "Content-Length: 0"));
                    HttpDownloader downloader = downloader()) {

                Path bare = Path.of("a-download-that-never-happens.zip");
                assertNull(bare.getParent(), "the case under test");

                assertThrows(
                        ArtefactUnavailableException.class,
                        () -> downloader.fetch(DownloadRequest.of(server.uri("/a.zip"), bare)));

                assertAll(
                        () -> assertFalse(Files.exists(bare)),
                        () ->
                                assertFalse(
                                        Files.exists(
                                                Path.of("a-download-that-never-happens.zip.part")),
                                        "and nothing was left in the working directory"));
            }
        }

        @Test
        @DisplayName("a missing parent directory is created")
        void aMissingParentDirectoryIsCreated() throws IOException {
            byte[] body = bytes(64, 'a');
            try (LoopbackHttpServer server = new LoopbackHttpServer(serving(body));
                    HttpDownloader downloader = downloader()) {

                Path file = work.resolve("cache").resolve("downloads").resolve("artefact.zip");
                downloader.download(server.uri("/a.zip"), file, (transferred, total) -> {});
                assertArrayEquals(body, Files.readAllBytes(file));
            }
        }
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @ParameterizedTest(name = "a {0} timeout is refused")
        @CsvSource({
            "connectTimeout,PT0S", "responseTimeout,PT0S", "stallTimeout,PT0S",
            "connectTimeout,PT-1S", "responseTimeout,PT-1S", "stallTimeout,PT-1S"
        })
        void aNonPositiveTimeoutIsRefused(String field, String duration) {
            Duration bad = Duration.parse(duration);
            Duration good = Duration.ofSeconds(1);
            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    close(
                                            new HttpDownloader(
                                                    "connectTimeout".equals(field) ? bad : good,
                                                    "responseTimeout".equals(field) ? bad : good,
                                                    "stallTimeout".equals(field) ? bad : good)));
            assertTrue(thrown.getMessage().startsWith(field), thrown.getMessage());
        }

        @Test
        @DisplayName("the production timeouts are all positive and stated")
        void theProductionTimeoutsArePositive() {
            assertAll(
                    () ->
                            assertEquals(
                                    Duration.ofSeconds(30), HttpDownloader.DEFAULT_CONNECT_TIMEOUT),
                    () ->
                            assertEquals(
                                    Duration.ofSeconds(60),
                                    HttpDownloader.DEFAULT_RESPONSE_TIMEOUT),
                    () ->
                            assertEquals(
                                    Duration.ofSeconds(60), HttpDownloader.DEFAULT_STALL_TIMEOUT));
        }

        @Test
        @DisplayName("the no-argument constructor is usable and closes cleanly")
        void theNoArgumentConstructorWorks() throws IOException {
            try (LoopbackHttpServer server = new LoopbackHttpServer(serving(bytes(32, 'a')));
                    HttpDownloader downloader = new HttpDownloader()) {
                Path file = work.resolve("artefact.zip");
                downloader.download(server.uri("/a.zip"), file, (transferred, total) -> {});
                assertEquals(32L, Files.size(file));
            }
        }

        private static void close(HttpDownloader downloader) {
            downloader.close();
        }
    }
}
