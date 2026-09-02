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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Optional;
import org.cometgui.domain.ports.DownloadProgressListener;
import org.cometgui.install.registry.ArtefactManifestReader;
import org.cometgui.install.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link DownloadRequest} and, through it, the URL rule in {@code DownloadUrls}.
 *
 * <p>The rejection table below is graded over the axes the rule does <em>not</em> depend on, which
 * is this project's tenth shape of a check that cannot fail: a plausible added conjunct -- "refuse
 * credentials, unless the scheme is https", say -- is invisible to branch coverage and to a
 * mutation score alike, because no mutation operator adds a conjunct. So credentials are tested
 * over both schemes and over loopback and routable hosts; the loopback exception is tested over
 * literals, names and both IP families; and the scheme is tested over more than the two this
 * product uses.
 */
class DownloadRequestTest {

    private static final Path DESTINATION = Path.of("artefact.zip");

    private static final String SHA256 =
            "4d0e94af851884ff8ab6a2223e73cf28ba3ced28f6af863d4a76d541009b9dd1";

    @Nested
    @DisplayName("the URL rule")
    class UrlRule {

        @ParameterizedTest(name = "{0} is fetchable")
        @ValueSource(
                strings = {
                    "https://github.com/percolator/percolator/releases/download/rel-3-07-01/a.zip",
                    "https://example.org/a.zip?query=1#fragment",
                    "HTTPS://EXAMPLE.ORG/a.zip",
                    "https://127.0.0.1:8443/a.zip",
                    "http://127.0.0.1:41235/a.zip",
                    "http://127.0.0.1/a.zip",
                    "http://127.5.6.7:9/a.zip",
                    "http://[::1]:8080/a.zip"
                })
        void fetchableUrls(String url) {
            assertNotNull(DownloadRequest.of(URI.create(url), DESTINATION));
        }

        @ParameterizedTest(name = "{0} is refused")
        @ValueSource(
                strings = {
                    // plain http off the machine, which is the rule itself
                    "http://github.com/a.zip",
                    "http://192.168.0.1/a.zip",
                    "http://126.255.255.255/a.zip",
                    "http://128.0.0.1/a.zip",
                    // a NAME that resolves to loopback is still a name: the check would be
                    // answering a different question from the connection made afterwards
                    "http://localhost:41235/a.zip",
                    "http://LOCALHOST/a.zip",
                    "http://localhost.localdomain/a.zip",
                    // credentials, over BOTH schemes and over loopback as well as routable hosts
                    "https://user:secret@github.com/a.zip",
                    "https://user@github.com/a.zip",
                    "https://@github.com/a.zip",
                    "http://user@127.0.0.1:41235/a.zip",
                    "https://user@127.0.0.1/a.zip",
                    // no host, or no authority at all
                    "https:///a.zip",
                    "https://:8443/a.zip",
                    // schemes that are neither
                    "ftp://example.org/a.zip",
                    "file:///tmp/a.zip",
                    "gopher://example.org/a.zip",
                    // not absolute
                    "/releases/download/a.zip",
                    "a.zip"
                })
        void refusedUrls(String url) {
            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> DownloadRequest.of(URI.create(url), DESTINATION));
            assertAll(
                    () -> assertTrue(thrown.getMessage().startsWith("source must be an https URL")),
                    () -> assertTrue(thrown.getMessage().contains(url), thrown.getMessage()),
                    () ->
                            assertTrue(
                                    thrown.getMessage().contains("R-SEC-02"),
                                    "the message says why the rule exists: "
                                            + thrown.getMessage()));
        }

        @Test
        @DisplayName("no URL the product can be asked to fetch reaches the loopback exception")
        void theShippedManifestCannotReachTheLoopbackException() throws java.io.IOException {
            // The loopback exception exists so that the installer can be tested against a real
            // HTTP server. This asserts that product data cannot reach it: every URL and every
            // companion URL in the manifest this build ships is https, so the exception is
            // unreachable outside a test. Asserted against the real file rather than a fixture,
            // because a fixture would only prove the rule about itself.
            var manifest = ArtefactManifestReader.readFromClasspath();
            var urls =
                    manifest.artefacts().stream()
                            .flatMap(
                                    record ->
                                            java.util.stream.Stream.concat(
                                                    java.util.stream.Stream.of(record.url()),
                                                    record.companions().stream()
                                                            .map(companion -> companion.url())))
                            .toList();
            assertAll(
                    () -> assertFalse(urls.isEmpty(), "the shipped manifest has URLs to check"),
                    () ->
                            assertEquals(
                                    java.util.List.of(),
                                    urls.stream()
                                            .filter(url -> !"https".equals(url.getScheme()))
                                            .toList(),
                                    "every managed download is https, so DownloadUrls' loopback"
                                            + " exception is unreachable from product data"),
                    () ->
                            assertEquals(
                                    java.util.List.of(),
                                    urls.stream().filter(url -> url.getUserInfo() != null).toList(),
                                    "and none of them carries credentials"));
        }

        @Test
        @DisplayName("the URL rule is a utility class and cannot be instantiated")
        void theUtilityClassCannotBeInstantiated() throws ReflectiveOperationException {
            var constructor = DownloadUrls.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            var thrown =
                    assertThrows(
                            java.lang.reflect.InvocationTargetException.class,
                            constructor::newInstance);
            assertEquals(
                    "DownloadUrls is a utility class and is never instantiated",
                    thrown.getCause().getMessage());
        }

        @Test
        @DisplayName("a null source is rejected by name")
        void aNullSourceIsRejected() {
            NullPointerException thrown =
                    assertThrows(
                            NullPointerException.class,
                            () -> DownloadRequest.of(Nulls.of(URI.class), DESTINATION));
            assertEquals("source", thrown.getMessage());
        }
    }

    @Nested
    @DisplayName("the rest of the request")
    class TheRest {

        @Test
        @DisplayName("a destination with no file name is rejected")
        void aDestinationWithNoFileNameIsRejected() {
            // A file-system root is the one path with no file name component, and it is the one a
            // caller could plausibly arrive at by resolving a relative path badly.
            Path root = FileSystems.getDefault().getRootDirectories().iterator().next();
            assertNull(root.getFileName(), "a root directory has no file name");

            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> DownloadRequest.of(URI.create("https://example.org/a"), root));
            assertTrue(thrown.getMessage().startsWith("destination must name a file"));
        }

        @ParameterizedTest(name = "a null {0} is rejected by name")
        @CsvSource({"destination", "listener", "cancellation", "expectedSha256"})
        void nullComponentsAreRejected(String field) {
            URI source = URI.create("https://example.org/a.zip");
            NullPointerException thrown =
                    assertThrows(
                            NullPointerException.class,
                            () ->
                                    new DownloadRequest(
                                            source,
                                            "destination".equals(field)
                                                    ? Nulls.of(Path.class)
                                                    : DESTINATION,
                                            "listener".equals(field)
                                                    ? Nulls.of(DownloadProgressListener.class)
                                                    : (bytes, total) -> {},
                                            "cancellation".equals(field)
                                                    ? Nulls.of(DownloadCancellation.class)
                                                    : DownloadCancellation.never(),
                                            false,
                                            "expectedSha256".equals(field)
                                                    ? Nulls.of(Optional.class)
                                                    : Optional.empty()));
            assertEquals(field, thrown.getMessage());
        }

        @ParameterizedTest(name = "\"{0}\" is not an expected SHA-256")
        @ValueSource(
                strings = {
                    "",
                    "4d0e94af",
                    "4d0e94af851884ff8ab6a2223e73cf28ba3ced28f6af863d4a76d541009b9dd1a",
                    "4d0e94af851884ff8ab6a2223e73cf28ba3ced28f6af863d4a76d541009b9ddz",
                    "9c86de1c45d2d93dae1ab43216b5864c"
                })
        void aMalformedExpectedDigestIsRejected(String digest) {
            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    assertNotNull(
                                            DownloadRequest.of(
                                                            URI.create("https://example.org/a.zip"),
                                                            DESTINATION)
                                                    .expecting(digest)));
            assertTrue(thrown.getMessage().startsWith("expectedSha256 must be 64"));
        }

        @Test
        @DisplayName("an expected digest given in upper case is accepted and lower-cased")
        void anUpperCaseDigestIsCanonicalised() {
            DownloadRequest request =
                    DownloadRequest.of(URI.create("https://example.org/a.zip"), DESTINATION)
                            .expecting(SHA256.toUpperCase(java.util.Locale.ROOT));
            assertEquals(Optional.of(SHA256), request.expectedSha256());
        }

        @Test
        @DisplayName("the withers change one thing each and keep the rest")
        void theWithersChangeOneThingEach() {
            URI source = URI.create("https://example.org/a.zip");
            DownloadRequest plain = DownloadRequest.of(source, DESTINATION);
            DownloadProgressListener listener = (bytes, total) -> {};
            DownloadCancellation cancellation = () -> true;

            DownloadRequest full =
                    plain.listeningTo(listener)
                            .cancellableBy(cancellation)
                            .resuming(true)
                            .expecting(SHA256);

            assertAll(
                    () -> assertFalse(plain.resume(), "the plain request never resumes"),
                    () -> assertEquals(Optional.empty(), plain.expectedSha256()),
                    () -> assertFalse(plain.cancellation().isCancelled()),
                    () -> assertSame(source, full.source()),
                    () -> assertSame(DESTINATION, full.destination()),
                    () -> assertSame(listener, full.listener()),
                    () -> assertSame(cancellation, full.cancellation()),
                    () -> assertTrue(full.resume()),
                    () -> assertEquals(Optional.of(SHA256), full.expectedSha256()),
                    () -> assertFalse(full.resuming(false).resume()),
                    () ->
                            assertEquals(
                                    Optional.of(SHA256),
                                    full.resuming(false).expectedSha256(),
                                    "resuming(false) keeps the digest, which is what"
                                            + " VerifiedDownloader's restart depends on"));
        }

        @Test
        @DisplayName("expecting(null) is rejected by name")
        void expectingNullIsRejected() {
            NullPointerException thrown =
                    assertThrows(
                            NullPointerException.class,
                            () ->
                                    assertNotNull(
                                            DownloadRequest.of(
                                                            URI.create("https://example.org/a.zip"),
                                                            DESTINATION)
                                                    .expecting(Nulls.of(String.class))));
            assertEquals("sha256", thrown.getMessage());
        }

        @Test
        @DisplayName("a cancellation that never fires answers false")
        void neverAnswersFalse() {
            assertFalse(DownloadCancellation.never().isCancelled());
        }
    }
}
