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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Path;
import org.cometgui.install.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Tests for {@link DownloadReport}, the record that makes "it resumed" falsifiable. */
class DownloadReportTest {

    private static final URI SOURCE = URI.create("https://example.org/a.zip");
    private static final Path DESTINATION = Path.of("a.zip");

    private static DownloadReport report(
            long resumedFrom, long transferred, long size, long total) {
        return new DownloadReport(
                SOURCE, DESTINATION, 206, true, resumedFrom, transferred, size, total);
    }

    @Test
    @DisplayName("a resumed transfer and a fresh one are told apart by the bytes kept")
    void resumedIsAboutTheBytesKept() {
        assertAll(
                () -> assertTrue(report(400, 600, 1000, 1000).resumed()),
                () -> assertFalse(report(0, 1000, 1000, 1000).resumed()),
                () ->
                        assertTrue(
                                report(1, 999, 1000, 1000).resumed(),
                                "one byte kept is still a resume"));
    }

    @Test
    @DisplayName("a declared total and an undeclared one are told apart by the sign")
    void totalWasDeclared() {
        assertAll(
                () -> assertTrue(report(0, 10, 10, 10).totalWasDeclared()),
                () ->
                        assertTrue(
                                report(0, 0, 0, 0).totalWasDeclared(),
                                "zero is a declared length, not the absence of one"),
                () -> assertFalse(report(0, 10, 10, -1).totalWasDeclared()),
                () -> assertEquals(-1L, DownloadReport.NO_DECLARED_TOTAL));
    }

    /*
     * The fourth column is the sum the message must quote, HAND-TYPED rather than computed from
     * the first two. The rule's arithmetic and the message's arithmetic are two separate additions
     * in the source, and only the first of them was ever asserted: a message reading "400 kept plus
     * 600 received is -200" passed every test here, because the assertion stopped at the opening
     * words. A diagnostic that states a total it computed wrongly sends its reader to the wrong
     * place, so the number in the text is pinned exactly like the number in the rule.
     */
    @ParameterizedTest(name = "{0} kept plus {1} received is not a file of {2} bytes")
    @CsvSource({"400,600,1001,1000", "400,600,999,1000", "0,0,1,0", "500,0,1000,500"})
    void theBytesMustAddUp(long resumedFrom, long transferred, long size, long quotedSum) {
        IllegalArgumentException thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> report(resumedFrom, transferred, size, size));
        assertEquals(
                "a report must account for every byte of the finished file: "
                        + resumedFrom
                        + " kept plus "
                        + transferred
                        + " received is "
                        + quotedSum
                        + ", but the file is "
                        + size
                        + " bytes",
                thrown.getMessage());
    }

    @ParameterizedTest(name = "a negative {0} is refused")
    @CsvSource({"resumedFromBytes", "bytesTransferred", "fileSizeBytes"})
    void negativeCountsAreRefused(String field) {
        IllegalArgumentException thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new DownloadReport(
                                        SOURCE,
                                        DESTINATION,
                                        200,
                                        false,
                                        "resumedFromBytes".equals(field) ? -1 : 0,
                                        "bytesTransferred".equals(field) ? -1 : 0,
                                        "fileSizeBytes".equals(field) ? -1 : 0,
                                        -1));
        assertTrue(
                thrown.getMessage().startsWith(field + " must not be negative"),
                thrown.getMessage());
    }

    @ParameterizedTest(name = "a null {0} is refused by name")
    @CsvSource({"source", "destination"})
    void nullComponentsAreRefused(String field) {
        NullPointerException thrown =
                assertThrows(
                        NullPointerException.class,
                        () ->
                                new DownloadReport(
                                        "source".equals(field) ? Nulls.of(URI.class) : SOURCE,
                                        "destination".equals(field)
                                                ? Nulls.of(Path.class)
                                                : DESTINATION,
                                        200,
                                        false,
                                        0,
                                        0,
                                        0,
                                        -1));
        assertEquals(field, thrown.getMessage());
    }

    @Test
    @DisplayName("every component is readable")
    void everyComponentIsReadable() {
        DownloadReport read =
                new DownloadReport(SOURCE, DESTINATION, 206, true, 400, 600, 1000, 1000);
        assertAll(
                () -> assertEquals(SOURCE, read.source()),
                () -> assertEquals(DESTINATION, read.destination()),
                () -> assertEquals(206, read.statusCode()),
                () -> assertTrue(read.rangeRequested()),
                () -> assertEquals(400L, read.resumedFromBytes()),
                () -> assertEquals(600L, read.bytesTransferred()),
                () -> assertEquals(1000L, read.fileSizeBytes()),
                () -> assertEquals(1000L, read.declaredTotalBytes()));
    }
}
