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

package org.cometgui.workflow.storage;

import static org.cometgui.workflow.testing.TestPaths.absolute;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.cometgui.domain.project.LockOwner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The lock record's input and output, on a recording channel: the order of truncate, write and
 * force, which no inspection of the resulting file can show, and a read loop that survives short
 * reads.
 */
class LockRecordIoTest {

    private static final LockOwner OWNER =
            new LockOwner(4242, "lab-pc", Instant.parse("2026-10-06T09:00:00.125Z"));

    private static final Path FILE = absolute("p/project.lock");

    private static byte[] recordBytes() {
        return LockJson.render(OWNER).getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("writing a record truncates, writes it whole, and forces it last")
    void writeOrder() throws IOException {
        RecordingChannel channel =
                new RecordingChannel("x".repeat(500).getBytes(StandardCharsets.UTF_8), false);
        ProjectLock.writeRecord(channel, OWNER);
        int length = recordBytes().length;
        assertAll(
                () -> assertArrayEquals(recordBytes(), channel.content()),
                () ->
                        assertEquals(
                                List.of("truncate(0)", "write(" + length + "@0)", "force(true)"),
                                channel.operations()));
    }

    @Test
    @DisplayName("emptying a record truncates and then forces")
    void emptyOrder() throws IOException {
        RecordingChannel channel = new RecordingChannel(recordBytes(), false);
        ProjectLock.emptyRecord(channel);
        assertAll(
                () -> assertEquals(0, channel.content().length),
                () -> assertEquals(List.of("truncate(0)", "force(true)"), channel.operations()));
    }

    @Test
    @DisplayName("a record delivered in short reads, one of them empty, is read whole")
    void shortReads() throws IOException {
        RecordingChannel channel = new RecordingChannel(recordBytes(), false).scriptReads(7, 0, 5);
        Optional<LockOwner> read = ProjectLock.readRecord(channel, FILE);
        int rest = recordBytes().length - 12;
        assertAll(
                () -> assertEquals(Optional.of(OWNER), read),
                () ->
                        assertEquals(
                                List.of("read(7)", "read(0)", "read(5)", "read(" + rest + ")"),
                                channel.operations()));
    }

    @Test
    @DisplayName("a record that ends early is read as far as it goes and then refused")
    void endsEarly() {
        RecordingChannel channel = new RecordingChannel(recordBytes(), false).scriptReads(10, -1);
        InvalidDocumentException thrown =
                assertThrows(
                        InvalidDocumentException.class,
                        () -> ProjectLock.readRecord(channel, FILE));
        assertAll(
                () -> assertEquals(List.of("read(10)", "read(eof)"), channel.operations()),
                () -> assertEquals(FILE.toString(), thrown.document()));
    }

    @Test
    @DisplayName("an empty file has no owner and is not read at all")
    void emptyFile() throws IOException {
        RecordingChannel channel = new RecordingChannel(new byte[0], false);
        assertAll(
                () -> assertEquals(Optional.empty(), ProjectLock.readRecord(channel, FILE)),
                () -> assertEquals(List.of(), channel.operations()));
    }

    @Test
    @DisplayName("a failure to close after a failed acquisition is attached, not thrown")
    void closeFailureSuppressed() {
        RecordingChannel channel = new RecordingChannel(new byte[0], true);
        IOException failure = new IOException("the acquisition failed");
        ProjectLock.closeAfter(channel, failure);
        assertAll(
                () -> assertEquals(List.of("close"), channel.operations()),
                () -> assertEquals(1, failure.getSuppressed().length),
                () ->
                        assertEquals(
                                "the channel would not close",
                                failure.getSuppressed()[0].getMessage()));
    }

    @Test
    @DisplayName("a channel that closes cleanly adds nothing to the failure")
    void closeCleanly() {
        RecordingChannel channel = new RecordingChannel(new byte[0], false);
        IOException failure = new IOException("the acquisition failed");
        ProjectLock.closeAfter(channel, failure);
        assertAll(
                () -> assertEquals(List.of("close"), channel.operations()),
                () -> assertEquals(0, failure.getSuppressed().length));
    }
}
