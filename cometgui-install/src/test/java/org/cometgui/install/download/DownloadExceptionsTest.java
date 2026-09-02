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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the four download failures, and for the one helper that exists because {@code
 * java.net}'s exceptions frequently carry no message of their own.
 */
class DownloadExceptionsTest {

    private static final URI SOURCE =
            URI.create("https://github.com/percolator/percolator/releases/download/rel/a.zip");

    private static final String SHA256 =
            "4d0e94af851884ff8ab6a2223e73cf28ba3ced28f6af863d4a76d541009b9dd1";

    @Test
    @DisplayName("describe names the class when the cause carries no message")
    void describeNamesTheClassWhenThereIsNoMessage() {
        assertAll(
                () ->
                        assertEquals(
                                "java.net.ConnectException",
                                DownloadFailedException.describe(new ConnectException()),
                                "a refused loopback connection really does arrive with a null"
                                        + " message, and \"null\" is not a diagnostic"),
                () ->
                        assertEquals(
                                "java.io.IOException",
                                DownloadFailedException.describe(new IOException("   ")),
                                "a blank message is no better than none"),
                () ->
                        assertEquals(
                                "java.io.IOException: fixed content-length: 200, bytes received:"
                                        + " 40",
                                DownloadFailedException.describe(
                                        new IOException(
                                                "fixed content-length: 200, bytes received: 40"))));
    }

    @Test
    @DisplayName("every failure carries the URL it was for")
    void everyFailureCarriesTheUrl() {
        assertAll(
                () ->
                        assertEquals(
                                SOURCE,
                                new ArtefactUnavailableException(SOURCE, 404, SHA256).source()),
                () ->
                        assertEquals(
                                SOURCE,
                                new TruncatedDownloadException(SOURCE, 1000, 40, null).source()),
                () -> assertEquals(SOURCE, new DownloadFailedException("nope", SOURCE).source()),
                () -> assertEquals(SOURCE, new DownloadCancelledException(SOURCE, 40).source()));
    }

    @Test
    @DisplayName("the availability failure names the status, the URL and the expected checksum")
    void theAvailabilityFailureNamesEverything() {
        ArtefactUnavailableException gone = new ArtefactUnavailableException(SOURCE, 410, SHA256);
        assertAll(
                () -> assertEquals(410, gone.statusCode()),
                () -> assertEquals(Optional.of(SHA256), gone.expectedSha256()),
                () -> assertTrue(gone.getMessage().contains("HTTP 410")),
                () -> assertTrue(gone.getMessage().contains(SOURCE.toString())),
                () -> assertTrue(gone.getMessage().contains(SHA256)),
                () ->
                        assertTrue(
                                gone.getMessage().contains("D-008"),
                                "the reason there is nothing to fall back on"),
                () ->
                        assertTrue(
                                gone.getMessage()
                                        .contains("not a corrupt download and not a probe failure"),
                                "the message says what it is not, because those are the two things"
                                        + " a reader would otherwise assume"));
    }

    @Test
    @DisplayName("the truncation failure names both counts and says the artefact is still there")
    void theTruncationFailureNamesBothCounts() {
        IOException cause = new IOException("fixed content-length: 1000, bytes received: 40");
        TruncatedDownloadException thrown = new TruncatedDownloadException(SOURCE, 1000, 40, cause);
        assertAll(
                () -> assertEquals(1000L, thrown.declaredTotalBytes()),
                () -> assertEquals(40L, thrown.receivedBytes()),
                () -> assertSame(cause, thrown.getCause()),
                () ->
                        assertTrue(
                                thrown.getMessage().contains("declared 1000 bytes and 40 arrived")),
                () -> assertTrue(thrown.getMessage().contains("still published")),
                () -> assertTrue(thrown.getMessage().contains("partial file is kept")));
    }

    @Test
    @DisplayName("the cancellation names the bytes that had arrived and promises nothing is left")
    void theCancellationNamesTheBytes() {
        DownloadCancelledException thrown = new DownloadCancelledException(SOURCE, 4096);
        assertAll(
                () -> assertEquals(4096L, thrown.bytesTransferred()),
                () -> assertTrue(thrown.getMessage().contains("cancelled after 4096 byte(s)")),
                () -> assertTrue(thrown.getMessage().contains("no destination file was created")));
    }

    @Test
    @DisplayName("a failure with a cause keeps it")
    void aFailureKeepsItsCause() {
        IOException cause = new ConnectException();
        DownloadFailedException thrown = new DownloadFailedException("nope", SOURCE, cause);
        assertSame(cause, thrown.getCause());
    }
}
