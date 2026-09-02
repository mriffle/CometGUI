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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.install.registry.ArtefactManifestReader;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.install.verify.ArtefactVerifier;
import org.cometgui.install.verify.VerifiedArtefact;
import org.cometgui.install.verify.VerifiedDownloader;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * Fetches one real artefact from its real URL and verifies it against the pinned SHA-256.
 *
 * <h2>How to run it</h2>
 *
 * <pre>{@code
 * mvn -o -pl cometgui-install -Dcometgui.install.upstream=true \
 *     -Dtest=UpstreamArtefactTest -Dsurefire.failIfNoSpecifiedTests=false test
 * }</pre>
 *
 * <p><strong>The network test does not run in the ordinary build</strong>, and the ordinary build
 * must not depend on reaching GitHub: a suite that goes red because a release host is having a bad
 * afternoon teaches people to ignore it. {@code R-TEST-08}'s nightly manifest verification is Phase
 * 15's, and {@code scripts/ci/nightly-manifest-verify.sh} is correctly still a stub.
 *
 * <h2>The gate is visible, and it is not vacuous</h2>
 *
 * <p>A test that silently does nothing is worse than no test, so two things are arranged here.
 * {@link #theOptInTestTargetsTheShippedManifest()} <strong>always runs</strong> and pins the URL,
 * the size and both digests below against the record {@code manifests/tools.json} actually ships --
 * so if the manifest changes, the always-on half goes red rather than the opt-in half quietly
 * checking a stale copy. And the opt-in half is skipped with a stated reason, which surefire
 * records, rather than passing while doing nothing.
 */
class UpstreamArtefactTest {

    /** The property that opts in to reaching the real network. */
    private static final String OPT_IN = "cometgui.install.upstream";

    private static final URI URL =
            URI.create(
                    "https://github.com/percolator/percolator/releases/download/rel-3-07-01/"
                            + "percolator-noxml-ubuntu-portable.zip");

    private static final long SIZE_BYTES = 946_303L;

    private static final FileHashes HASHES =
            new FileHashes(
                    "9c86de1c45d2d93dae1ab43216b5864c",
                    "4d0e94af851884ff8ab6a2223e73cf28ba3ced28f6af863d4a76d541009b9dd1");

    @TempDir private Path work;

    private static ArtefactRecord shippedRecord() throws IOException {
        return ArtefactManifestReader.readFromClasspath().artefacts().stream()
                .filter(record -> "percolator".equals(record.tool().id()))
                .filter(record -> "3.07.1".equals(record.version().text()))
                .filter(record -> "linux-x86-64".equals(record.platform().id()))
                .findFirst()
                .orElseThrow(
                        () ->
                                new AssertionError(
                                        "the shipped manifest no longer has a percolator 3.07.1"
                                                + " linux-x86-64 row, so this test is checking"
                                                + " something the product does not offer"));
    }

    @Test
    @DisplayName("the opt-in test targets the artefact the shipped manifest actually pins")
    void theOptInTestTargetsTheShippedManifest() throws IOException {
        ArtefactRecord record = shippedRecord();
        assertAll(
                () -> assertEquals(record.url(), URL),
                () -> assertEquals(record.sizeBytes(), SIZE_BYTES),
                () -> assertEquals(record.hashes(), HASHES),
                () ->
                        assertEquals(
                                "https",
                                record.url().getScheme(),
                                "a managed download is https, so the real fetch below exercises the"
                                        + " TLS path and the release redirect, not the loopback"
                                        + " exception the other tests use"));
    }

    @Test
    @EnabledIfSystemProperty(
            named = OPT_IN,
            matches = "true",
            disabledReason =
                    "reaches github.com; run with -Dcometgui.install.upstream=true. The ordinary"
                            + " build must not depend on upstream being reachable.")
    @DisplayName("one real artefact is fetched from its real URL and verified against its SHA-256")
    void oneRealArtefactIsFetchedAndVerified() throws IOException {
        Path file = work.resolve("percolator-noxml-ubuntu-portable.zip");
        long startedAt = System.nanoTime();

        try (HttpDownloader http = new HttpDownloader()) {
            VerifiedDownloader downloader =
                    new VerifiedDownloader(http, new ArtefactVerifier(new StreamingHashService()));
            VerifiedArtefact verified =
                    downloader.fetch(
                            shippedRecord(),
                            file,
                            (bytes, total) -> {},
                            DownloadCancellation.never());
            long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

            DownloadReport report = verified.lastAttempt();
            assertAll(
                    () -> assertEquals(HASHES, verified.hashes()),
                    () -> assertEquals(SIZE_BYTES, Files.size(file)),
                    () -> assertEquals(SIZE_BYTES, report.fileSizeBytes()),
                    () -> assertEquals(SIZE_BYTES, report.bytesTransferred()),
                    () -> assertEquals(SIZE_BYTES, report.declaredTotalBytes()),
                    () -> assertEquals(200, report.statusCode()),
                    () -> assertEquals(1, verified.attemptCount()),
                    () ->
                            assertFalse(
                                    Files.exists(file.resolveSibling(file.getFileName() + ".part")),
                                    "the temporary file is gone"),
                    () ->
                            assertTrue(
                                    elapsedMillis >= 0,
                                    "fetched and verified in " + elapsedMillis + " ms"));
        }
    }
}
