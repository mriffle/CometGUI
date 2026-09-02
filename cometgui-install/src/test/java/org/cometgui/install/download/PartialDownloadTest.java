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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link PartialDownload}: the temporary file, the resume state beside it, and the rule
 * that a partial file nothing can vouch for is discarded rather than continued.
 */
class PartialDownloadTest {

    @TempDir private Path work;

    @Test
    @DisplayName("the two files sit beside the destination and are named after it")
    void theTwoFilesSitBesideTheDestination() {
        Path destination = work.resolve("percolator.zip");
        PartialDownload partial = PartialDownload.beside(destination);
        assertAll(
                () -> assertEquals(work.resolve("percolator.zip.part"), partial.file()),
                () -> assertEquals(work.resolve("percolator.zip.part.state"), partial.stateFile()));
    }

    @Test
    @DisplayName("the state file records the length and the ETag, and no URL at all")
    void theStateFileRecordsNoUrl() throws IOException {
        PartialDownload partial = PartialDownload.beside(work.resolve("a.zip"));
        partial.recordState(946303, "\"0x8DC9130344F5BFC\"");
        String written = Files.readString(partial.stateFile(), StandardCharsets.UTF_8);

        assertAll(
                () -> assertTrue(written.startsWith(PartialDownload.STATE_MAGIC)),
                () -> assertTrue(written.contains("total=946303")),
                () -> assertTrue(written.contains("etag=\"0x8DC9130344F5BFC\"")),
                () ->
                        assertFalse(
                                written.contains("http"),
                                "a signed release URL expires in about an hour, so storing one"
                                        + " would produce a resume that works for an hour and then"
                                        + " fails for a reason nobody can see: "
                                        + written));
    }

    @Test
    @DisplayName("a partial file with matching state is resumable from its own length")
    void aPartialFileWithStateIsResumable() throws IOException {
        PartialDownload partial = PartialDownload.beside(work.resolve("a.zip"));
        Files.write(partial.file(), new byte[400]);
        partial.recordState(1000, "\"v1\"");

        Optional<PartialDownload.ResumePoint> point = partial.resumePoint();
        assertAll(
                () -> assertTrue(point.isPresent()),
                () -> assertEquals(400L, point.orElseThrow().offsetBytes()),
                () -> assertEquals(1000L, point.orElseThrow().declaredTotalBytes()),
                () -> assertEquals("\"v1\"", point.orElseThrow().etag()));
    }

    @Test
    @DisplayName("a server that sent no ETag round-trips as the empty string, not as null")
    void anAbsentEtagRoundTrips() throws IOException {
        PartialDownload partial = PartialDownload.beside(work.resolve("a.zip"));
        Files.write(partial.file(), new byte[7]);
        partial.recordState(-1, "");

        PartialDownload.ResumePoint point = partial.resumePoint().orElseThrow();
        assertAll(
                () -> assertEquals("", point.etag()),
                () -> assertEquals(-1L, point.declaredTotalBytes()));
    }

    @Test
    @DisplayName("no partial file means nothing to resume")
    void noPartialFileMeansNothingToResume() throws IOException {
        PartialDownload partial = PartialDownload.beside(work.resolve("a.zip"));
        partial.recordState(1000, "\"v1\"");
        assertEquals(Optional.empty(), partial.resumePoint());
    }

    @Test
    @DisplayName("no state file means the bytes have no provenance, so nothing to resume")
    void noStateFileMeansNothingToResume() throws IOException {
        PartialDownload partial = PartialDownload.beside(work.resolve("a.zip"));
        Files.write(partial.file(), new byte[400]);
        assertEquals(Optional.empty(), partial.resumePoint());
    }

    @Test
    @DisplayName("an empty partial file is nothing to resume")
    void anEmptyPartialFileIsNothingToResume() throws IOException {
        PartialDownload partial = PartialDownload.beside(work.resolve("a.zip"));
        Files.write(partial.file(), new byte[0]);
        partial.recordState(1000, "\"v1\"");
        assertEquals(Optional.empty(), partial.resumePoint());
    }

    @ParameterizedTest(name = "a state file reading {0} is not trusted")
    @ValueSource(
            strings = {
                "",
                "cometgui-download-state 1",
                "cometgui-download-state 1\ntotal=1000",
                "cometgui-download-state 2\ntotal=1000\netag=\"v1\"",
                "cometgui-download-state 1\nsize=1000\netag=\"v1\"",
                "cometgui-download-state 1\ntotal=one thousand\netag=\"v1\"",
                "cometgui-download-state 1\ntotal=1000\nvalidator=\"v1\"",
                "cometgui-download-state 1\ntotal=1000\netag=\"v1\"\nurl=https://example.org/a.zip"
            })
    void anUnreadableStateFileIsNotTrusted(String content) throws IOException {
        PartialDownload partial = PartialDownload.beside(work.resolve("a.zip"));
        Files.write(partial.file(), new byte[400]);
        Files.writeString(partial.stateFile(), content, StandardCharsets.UTF_8);
        assertEquals(Optional.empty(), partial.resumePoint());
    }

    @Test
    @DisplayName("discarding removes both files and does not mind if they are absent")
    void discardingRemovesBothFiles() throws IOException {
        PartialDownload partial = PartialDownload.beside(work.resolve("a.zip"));
        partial.discard();
        Files.write(partial.file(), new byte[3]);
        partial.recordState(3, "\"v1\"");

        partial.discard();

        assertAll(
                () -> assertFalse(Files.exists(partial.file())),
                () -> assertFalse(Files.exists(partial.stateFile())));
    }

    @Test
    @DisplayName("moving into place replaces the destination and removes the state")
    void movingIntoPlaceReplacesTheDestination() throws IOException {
        Path destination = work.resolve("a.zip");
        Files.write(destination, "old".getBytes(StandardCharsets.UTF_8));
        PartialDownload partial = PartialDownload.beside(destination);
        Files.write(partial.file(), "new".getBytes(StandardCharsets.UTF_8));
        partial.recordState(3, "\"v1\"");

        partial.moveInto(destination);

        assertAll(
                () ->
                        assertArrayEquals(
                                "new".getBytes(StandardCharsets.UTF_8),
                                Files.readAllBytes(destination)),
                () -> assertFalse(Files.exists(partial.file())),
                () -> assertFalse(Files.exists(partial.stateFile())));
    }
}
