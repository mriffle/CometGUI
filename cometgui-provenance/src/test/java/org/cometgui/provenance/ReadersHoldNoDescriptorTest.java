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

package org.cometgui.provenance;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.MalformedInputException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.cometgui.provenance.events.EventLogDefect;
import org.cometgui.provenance.events.EventLogDefectKind;
import org.cometgui.provenance.events.ProvenanceEventLogReader;
import org.cometgui.provenance.events.RecoveredEventLog;
import org.cometgui.provenance.manifest.ManifestReader;
import org.cometgui.provenance.manifest.ManifestReader.InvalidManifestException;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves that this module's two readers are never a <em>holder</em> of the file they read: once
 * {@link ManifestReader#readFrom(Path)} or {@link ProvenanceEventLogReader#recover(Path)} returns
 * -- or throws -- no open file descriptor of this process refers to that file.
 *
 * <p><strong>Why it matters</strong> (Phase 13 design decision P13-1). {@code provenance.json} is
 * finalised by renaming a temporary file over it, and on Windows that rename can be refused while
 * another process holds the target open. The Provenance viewer reads the record through these
 * readers; if they left a handle behind, the viewer would itself be the holder that makes a later
 * finalise fail. The bounded retry in {@code AtomicDocumentWriter} is for holders the product does
 * not control; this test is the half that says the product is not one of them.
 *
 * <p><strong>Why Linux only.</strong> The observation is {@code /proc/self/fd}: one symbolic link
 * per open descriptor, naming the file it refers to. That is the kernel's own account of what the
 * process holds, independent of anything Java believes it closed, and it exists only on Linux. The
 * readers are the same code on every platform, so this is a testing gap on the others, not
 * different behaviour.
 *
 * <p><strong>Why the check can fail.</strong> A scan of {@code /proc/self/fd} that found nothing
 * because it was looking in the wrong place, or comparing the wrong spelling of the path, would
 * pass every test here vacuously. The {@link Control} group therefore holds the file open itself,
 * through a channel and through a stream, and requires the scan to count exactly that descriptor;
 * then closes it and requires zero.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "/proc/self/fd, the kernel's list of this process's open files, is Linux's")
class ReadersHoldNoDescriptorTest {

    /**
     * A running run's manifest, hand-typed, carrying no path so that it is valid on any platform.
     * It is the same document {@code ManifestReaderTest} reads, typed again rather than shared.
     */
    private static final String MANIFEST =
            """
            {
              "schemaVersion": 1,
              "run": {
                "runId": "run-20260831-101500",
                "projectId": "project-beta",
                "status": "running",
                "start": "2026-08-31T10:15:00.000Z",
                "end": null,
                "durationMillis": null
              },
              "application": {
                "cometGuiVersion": "0.1.0-SNAPSHOT",
                "buildIdentifier": "9f8c1d2e4b7a",
                "osName": "Windows 11",
                "osVersion": "10.0",
                "architecture": "aarch64",
                "jvmVersion": "25.0.4.1",
                "locale": "und",
                "formatLocale": "tr-TR",
                "zoneId": "UTC"
              },
              "settings": {},
              "tools": [],
              "files": []
            }
            """;

    private static final String LINE_ONE =
            "{\"seq\":1,\"time\":\"2026-08-31T09:15:00.000Z\",\"type\":\"run.started\","
                    + "\"payload\":{\"run.id\":\"R-1\"}}";

    private static final String LINE_TWO =
            "{\"seq\":2,\"time\":\"2026-08-31T09:15:01.000Z\",\"type\":\"stage.started\","
                    + "\"payload\":{\"stage\":\"comet\"}}";

    @TempDir private Path directory;

    @Nested
    @DisplayName("control: the scan sees a descriptor that is really open")
    class Control {

        @Test
        @DisplayName("a channel held open is counted once, and not after it is closed")
        void anOpenChannelIsSeen() throws IOException {
            Path file = directory.resolve("provenance.json");
            Files.writeString(file, MANIFEST, UTF_8);

            long whileOpen;
            try (FileChannel held = FileChannel.open(file, StandardOpenOption.READ)) {
                whileOpen = descriptorsFor(file);
            }

            assertAll(
                    () -> assertEquals(1L, whileOpen, "the scan did not see a held channel"),
                    () -> assertEquals(0L, descriptorsFor(file)));
        }

        @Test
        @DisplayName("two streams held open are counted twice")
        void twoOpenStreamsAreSeen() throws IOException {
            Path file = directory.resolve("events.log");
            Files.writeString(file, LINE_ONE + "\n", UTF_8);

            long whileOpen;
            try (InputStream first = Files.newInputStream(file);
                    InputStream second = Files.newInputStream(file)) {
                whileOpen = descriptorsFor(file);
            }

            assertAll(
                    () -> assertEquals(2L, whileOpen, "the scan did not see two held streams"),
                    () -> assertEquals(0L, descriptorsFor(file)));
        }

        @Test
        @DisplayName("a directory opened as a stream is seen, as the throwing cases below need")
        void anOpenDirectoryIsSeen() throws IOException {
            Path sub = Files.createDirectory(directory.resolve("provenance-dir"));

            long whileOpen;
            try (InputStream held = Files.newInputStream(sub)) {
                whileOpen = descriptorsFor(sub);
            }

            assertAll(
                    () -> assertEquals(1L, whileOpen, "the scan did not see a held directory"),
                    () -> assertEquals(0L, descriptorsFor(sub)));
        }
    }

    @Nested
    @DisplayName("ManifestReader.readFrom")
    class Manifest {

        @Test
        @DisplayName("holds nothing after reading a valid manifest")
        void afterAValidRead() throws IOException {
            Path file = directory.resolve("provenance.json");
            Files.writeString(file, MANIFEST, UTF_8);

            ProvenanceManifest manifest = ManifestReader.readFrom(file);

            assertAll(
                    () -> assertEquals("project-beta", manifest.run().projectId()),
                    () -> assertEquals(0L, descriptorsFor(file)));
        }

        @Test
        @DisplayName("holds nothing after refusing a document that is not JSON")
        void afterAnUnparsableDocument() throws IOException {
            Path file = directory.resolve("provenance.json");
            Files.writeString(file, MANIFEST.substring(0, 120), UTF_8);

            assertThrows(InvalidManifestException.class, () -> ManifestReader.readFrom(file));

            assertEquals(0L, descriptorsFor(file));
        }

        @Test
        @DisplayName("holds nothing after refusing bytes that are not UTF-8")
        void afterBytesThatAreNotUtf8() throws IOException {
            Path file = directory.resolve("provenance.json");
            byte[] document = MANIFEST.getBytes(UTF_8);
            document[10] = (byte) 0xff;
            Files.write(file, document);

            assertThrows(MalformedInputException.class, () -> ManifestReader.readFrom(file));

            assertEquals(0L, descriptorsFor(file));
        }

        @Test
        @DisplayName("holds nothing after a read that fails once the path is open")
        void afterAManifestReadThatFailsMidway() throws IOException {
            // A directory opens for reading on Linux and then fails on the first read, so this is
            // the one way to make the read itself throw after a descriptor exists.
            Path sub = Files.createDirectory(directory.resolve("provenance-dir"));

            IOException thrown =
                    assertThrows(IOException.class, () -> ManifestReader.readFrom(sub));

            assertAll(
                    () -> assertEquals("Is a directory", thrown.getMessage()),
                    () -> assertEquals(0L, descriptorsFor(sub)));
        }
    }

    @Nested
    @DisplayName("ProvenanceEventLogReader.recover")
    class EventLog {

        @Test
        @DisplayName("holds nothing after recovering an intact log")
        void afterAnIntactLog() throws IOException {
            Path file = directory.resolve("events.log");
            Files.writeString(file, LINE_ONE + "\n" + LINE_TWO + "\n", UTF_8);

            RecoveredEventLog recovered = ProvenanceEventLogReader.recover(file);

            assertAll(
                    () -> assertEquals(2, recovered.events().size()),
                    () -> assertEquals(List.of(), recovered.defects()),
                    () -> assertEquals(0L, descriptorsFor(file)));
        }

        @Test
        @DisplayName("holds nothing after recovering a damaged log -- which does not throw")
        void afterADamagedLog() throws IOException {
            // Damaged content never throws from recover(); it is reported as defects.  The
            // failure path is the directory case below.
            Path file = directory.resolve("events.log");
            Files.writeString(file, LINE_ONE + "\nnot json\n" + "{\"seq\":2,\"ti", UTF_8);

            RecoveredEventLog recovered = ProvenanceEventLogReader.recover(file);

            assertAll(
                    () -> assertEquals(1, recovered.events().size()),
                    () ->
                            assertEquals(
                                    List.of(
                                            EventLogDefectKind.MALFORMED_LINE,
                                            EventLogDefectKind.TORN_FINAL_LINE),
                                    recovered.defects().stream()
                                            .map(EventLogDefect::kind)
                                            .toList()),
                    () -> assertEquals(0L, descriptorsFor(file)));
        }

        @Test
        @DisplayName("holds nothing after a read that fails once the path is open")
        void afterALogReadThatFailsMidway() throws IOException {
            Path sub = Files.createDirectory(directory.resolve("events-dir"));

            IOException thrown =
                    assertThrows(IOException.class, () -> ProvenanceEventLogReader.recover(sub));

            assertAll(
                    () -> assertEquals("Is a directory", thrown.getMessage()),
                    () -> assertEquals(0L, descriptorsFor(sub)));
        }
    }

    /**
     * Counts this process's open descriptors that refer to {@code path}, as the kernel reports them
     * in {@code /proc/self/fd}.
     *
     * <p>The path is resolved to its real form first, because that is what the kernel's links name:
     * {@code @TempDir} may sit under a symbolic link. A descriptor that closes while the listing is
     * being read -- the listing's own, for one -- has no link to read, and is skipped.
     */
    private static long descriptorsFor(Path path) throws IOException {
        Path real = path.toRealPath();
        long count = 0;
        try (DirectoryStream<Path> descriptors =
                Files.newDirectoryStream(Paths.get("/proc/self/fd"))) {
            for (Path descriptor : descriptors) {
                Path refersTo;
                try {
                    refersTo = Files.readSymbolicLink(descriptor);
                } catch (NoSuchFileException closedMeanwhile) {
                    continue;
                }
                if (real.equals(refersTo)) {
                    count++;
                }
            }
        }
        return count;
    }
}
