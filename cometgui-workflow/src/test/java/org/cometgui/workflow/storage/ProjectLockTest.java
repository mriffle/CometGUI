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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import org.cometgui.domain.project.LockOwner;
import org.cometgui.domain.project.ProjectLayout;
import org.cometgui.domain.project.SchemaVerdict;
import org.cometgui.domain.project.UnsupportedSchemaVersionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code R-RUN-05} inside one JVM: the record written, the second holder refused, release, and
 * every branch of the stale-lock judgement with the owner's liveness chosen by the test. The real
 * second process is {@link ProjectLockProcessTest}.
 */
class ProjectLockTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-06T09:00:00.125Z"), ZoneOffset.UTC);

    private static final String LAB_PC = "lab-pc";

    @TempDir private Path temporary;

    private ProjectLayout project;

    @BeforeEach
    void createDirectory() throws IOException {
        project = new ProjectLayout(temporary.resolve("P"));
        Files.createDirectories(project.root());
    }

    private static String record(long pid, String host) {
        return "{\n  \"schemaVersion\": 1,\n  \"pid\": "
                + pid
                + ",\n  \"host\": \""
                + host
                + "\",\n  \"started\": \"2026-10-05T08:00:00.000Z\"\n}\n";
    }

    private static String sha256(Path file) throws IOException, NoSuchAlgorithmException {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }

    private ProjectLock acquireAs(long pid, String host, boolean ownerAlive) throws IOException {
        return ProjectLock.acquire(project, pid, host, CLOCK, recorded -> ownerAlive);
    }

    @Test
    @DisplayName("taking the lock writes the owner's record, byte for byte")
    void writesTheRecord() throws IOException {
        try (ProjectLock lock = acquireAs(4242, LAB_PC, false)) {
            assertAll(
                    () ->
                            assertEquals(
                                    "{\n  \"schemaVersion\": 1,\n  \"pid\": 4242,\n  \"host\":"
                                            + " \"lab-pc\",\n  \"started\":"
                                            + " \"2026-10-06T09:00:00.125Z\"\n}\n",
                                    Files.readString(project.lockFile(), StandardCharsets.UTF_8)),
                    () ->
                            assertEquals(
                                    new LockOwner(
                                            4242,
                                            LAB_PC,
                                            Instant.parse("2026-10-06T09:00:00.125Z")),
                                    lock.owner()),
                    () -> assertTrue(lock.held()),
                    () -> assertEquals(Optional.empty(), lock.recovered()),
                    () -> assertEquals(Optional.empty(), lock.recoveryNotice()),
                    () -> assertEquals(project, lock.project()),
                    () ->
                            assertEquals(
                                    project.root().toRealPath().resolve("project.lock"),
                                    lock.file()),
                    () ->
                            assertEquals(
                                    "ProjectLock[file="
                                            + lock.file()
                                            + ", owner=process 4242 on host lab-pc, since"
                                            + " 2026-10-06T09:00:00Z, held=true]",
                                    lock.toString()));
        }
    }

    @Test
    @DisplayName("a second holder in this JVM is refused naming the owner's pid and host")
    void secondHolderInThisJvm() throws IOException {
        try (ProjectLock first = acquireAs(4242, LAB_PC, false)) {
            ProjectLockedException thrown =
                    assertThrows(
                            ProjectLockedException.class, () -> acquireAs(5151, LAB_PC, false));
            assertAll(
                    () ->
                            assertEquals(
                                    ProjectLockedException.Reason.HELD_IN_THIS_PROCESS,
                                    thrown.reason()),
                    () -> assertEquals(Optional.of(first.owner()), thrown.owner()),
                    () ->
                            assertEquals(
                                    "the project "
                                            + project.root()
                                            + " is already open in this CometGUI (process 4242 on"
                                            + " host lab-pc, since 2026-10-06T09:00:00Z)",
                                    thrown.getMessage()),
                    () -> assertTrue(first.held()),
                    () ->
                            assertEquals(
                                    "4242",
                                    Files.readString(project.lockFile())
                                            .replaceAll("(?s).*\"pid\": (\\d+).*", "$1")));
        }
    }

    @Test
    @DisplayName("the production acquire records this process and this host")
    void productionAcquire() throws IOException {
        try (ProjectLock lock = ProjectLock.acquire(project, CLOCK)) {
            assertAll(
                    () -> assertEquals(ProcessHandle.current().pid(), lock.owner().pid()),
                    () -> assertFalse(lock.owner().host().isBlank()),
                    () -> assertTrue(ProjectLock.isAlive(lock.owner().pid())));
        }
    }

    @Test
    @DisplayName(
            "closing empties the record and releases; the lock can be taken again with no recovery")
    void closeReleases() throws IOException {
        ProjectLock lock = acquireAs(4242, LAB_PC, false);
        lock.close();
        assertAll(
                () -> assertFalse(lock.held()),
                () -> assertEquals(0, Files.size(project.lockFile())));
        try (ProjectLock again = acquireAs(5151, LAB_PC, true)) {
            String record = Files.readString(project.lockFile());
            // Closing the old lock a second time must not touch the new holder's lock.
            lock.close();
            assertAll(
                    () -> assertEquals(Optional.empty(), again.recovered()),
                    () -> assertEquals(5151, again.owner().pid()),
                    () -> assertTrue(again.held()),
                    () -> assertTrue(record.contains("\"pid\": 5151,"), record),
                    () -> assertEquals(record, Files.readString(project.lockFile())),
                    () ->
                            assertEquals(
                                    ProjectLockedException.Reason.HELD_IN_THIS_PROCESS,
                                    assertThrows(
                                                    ProjectLockedException.class,
                                                    () -> acquireAs(6161, LAB_PC, false))
                                            .reason()));
        }
    }

    @Test
    @DisplayName("a dead owner's lock on this host is recovered, and the recovery names it")
    void staleRecovered() throws IOException {
        Files.writeString(project.lockFile(), record(999_999, LAB_PC));
        try (ProjectLock lock = acquireAs(4242, LAB_PC, false)) {
            assertAll(
                    () ->
                            assertEquals(
                                    Optional.of(
                                            new LockOwner(
                                                    999_999,
                                                    LAB_PC,
                                                    Instant.parse("2026-10-05T08:00:00.000Z"))),
                                    lock.recovered()),
                    () ->
                            assertEquals(
                                    Optional.of(
                                            "recovered a stale lock on "
                                                    + project.root()
                                                    + " left by process 999999 on host lab-pc,"
                                                    + " since 2026-10-05T08:00:00Z, which is no"
                                                    + " longer running"),
                                    lock.recoveryNotice()),
                    () ->
                            assertTrue(
                                    Files.readString(project.lockFile())
                                            .contains("\"pid\": 4242")));
        }
    }

    @Test
    @DisplayName("this process's own leftover record is recovered even though it is alive")
    void ownLeftoverRecovered() throws IOException {
        Files.writeString(project.lockFile(), record(4242, LAB_PC));
        try (ProjectLock lock = acquireAs(4242, LAB_PC, true)) {
            assertEquals(4242, lock.recovered().orElseThrow().pid());
        }
    }

    @Test
    @DisplayName(
            "a record naming a live process on this host is not stale: refused, file unchanged")
    void liveOwnerRefused() throws IOException, NoSuchAlgorithmException {
        Files.writeString(project.lockFile(), record(999_999, LAB_PC));
        String before = sha256(project.lockFile());
        ProjectLockedException thrown =
                assertThrows(ProjectLockedException.class, () -> acquireAs(4242, LAB_PC, true));
        assertAll(
                () ->
                        assertEquals(
                                ProjectLockedException.Reason.OWNER_STILL_RUNNING, thrown.reason()),
                () -> assertEquals(999_999, thrown.owner().orElseThrow().pid()),
                () ->
                        assertEquals(
                                "the project "
                                        + project.root()
                                        + " is locked by process 999999 on host lab-pc, since"
                                        + " 2026-10-05T08:00:00Z, which no longer holds the lock"
                                        + " but"
                                        + " is still running on this computer; if that process is"
                                        + " not CometGUI, delete "
                                        + project.root().toRealPath().resolve("project.lock")
                                        + " by hand.",
                                thrown.getMessage()),
                () -> assertEquals(before, sha256(project.lockFile())));
        // The refused attempt released its claim in this JVM.
        try (ProjectLock lock = acquireAs(999_999, LAB_PC, true)) {
            assertEquals(999_999, lock.recovered().orElseThrow().pid());
        }
    }

    @Test
    @DisplayName("a lock from another host is never broken, even when that pid is dead here")
    void otherHostNeverBroken() throws IOException, NoSuchAlgorithmException {
        Files.writeString(project.lockFile(), record(999_999, "other-pc"));
        String before = sha256(project.lockFile());
        ProjectLockedException thrown =
                assertThrows(ProjectLockedException.class, () -> acquireAs(4242, LAB_PC, false));
        assertAll(
                () ->
                        assertEquals(
                                ProjectLockedException.Reason.OWNER_ON_ANOTHER_HOST,
                                thrown.reason()),
                () -> assertEquals("other-pc", thrown.owner().orElseThrow().host()),
                () ->
                        assertEquals(
                                "the project "
                                        + project.root()
                                        + " is locked by process 999999 on host other-pc, since"
                                        + " 2026-10-05T08:00:00Z. A lock taken on another computer"
                                        + " is never broken from this one, because this computer"
                                        + " cannot tell whether that process is still running; if"
                                        + " it"
                                        + " is not, close CometGUI there or delete "
                                        + project.root().toRealPath().resolve("project.lock")
                                        + " by hand.",
                                thrown.getMessage()),
                () -> assertEquals(before, sha256(project.lockFile())));
    }

    @Test
    @DisplayName("an unreadable, a newer and an oversized record are refused and left as they are")
    void unreadableRecords() throws IOException, NoSuchAlgorithmException {
        Files.writeString(project.lockFile(), "pid=4242\n");
        String garbage = sha256(project.lockFile());
        InvalidDocumentException notJson =
                assertThrows(InvalidDocumentException.class, () -> acquireAs(1, LAB_PC, false));
        assertEquals(garbage, sha256(project.lockFile()));

        Files.writeString(
                project.lockFile(),
                record(999_999, LAB_PC).replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"));
        String newer = sha256(project.lockFile());
        UnsupportedSchemaVersionException version =
                assertThrows(
                        UnsupportedSchemaVersionException.class, () -> acquireAs(1, LAB_PC, false));
        assertEquals(newer, sha256(project.lockFile()));

        Files.writeString(project.lockFile(), " ".repeat(ProjectLock.MAX_RECORD_BYTES + 1));
        InvalidDocumentException large =
                assertThrows(InvalidDocumentException.class, () -> acquireAs(1, LAB_PC, false));
        assertAll(
                () ->
                        assertTrue(
                                notJson.getMessage().contains(" is not well-formed JSON: "),
                                notJson.getMessage()),
                () -> assertEquals(SchemaVerdict.NEWER, version.verdict()),
                () ->
                        assertEquals(
                                project.root().toRealPath().resolve("project.lock")
                                        + " is not valid: the document is 65537 bytes, larger than"
                                        + " the 65536 a lock record can be",
                                large.getMessage()));
    }

    @Test
    @DisplayName("a lock record with a member this build does not know is refused, unchanged")
    void unknownMemberInRecord() throws IOException, NoSuchAlgorithmException {
        Files.writeString(
                project.lockFile(),
                record(999_999, LAB_PC)
                        .replace("\"pid\": 999999,", "\"pid\": 999999, \"port\": 1,"));
        String before = sha256(project.lockFile());
        InvalidDocumentException thrown =
                assertThrows(InvalidDocumentException.class, () -> acquireAs(1, LAB_PC, false));
        assertAll(
                () ->
                        assertEquals(
                                project.root().toRealPath().resolve("project.lock")
                                        + " is not valid: the document has 1 member(s) this build"
                                        + " does not know; its schema version defines exactly"
                                        + " [schemaVersion, pid, host, started]",
                                thrown.getMessage()),
                () -> assertEquals(before, sha256(project.lockFile())));
    }

    @Test
    @DisplayName("a record exactly at the size bound is read")
    void recordAtBound() throws IOException {
        String body = record(999_999, LAB_PC);
        Files.writeString(
                project.lockFile(),
                body + " ".repeat(ProjectLock.MAX_RECORD_BYTES - body.length()));
        try (ProjectLock lock = acquireAs(4242, LAB_PC, false)) {
            assertEquals(999_999, lock.recovered().orElseThrow().pid());
        }
    }

    @Test
    @DisplayName("a project directory that does not exist is refused")
    void noDirectory() {
        ProjectLayout missing = new ProjectLayout(temporary.resolve("missing"));
        assertThrows(
                NoSuchFileException.class,
                () -> ProjectLock.acquire(missing, 1, LAB_PC, CLOCK, pid -> false));
    }

    @Test
    @DisplayName("the host name falls back when it cannot be determined")
    void hostName() {
        assertAll(
                () -> assertEquals("lab-pc", ProjectLock.hostName(() -> "lab-pc")),
                () -> assertEquals("unknown-host", ProjectLock.hostName(() -> " ")),
                () -> assertEquals("unknown-host", ProjectLock.hostName(() -> null)),
                () ->
                        assertEquals(
                                "unknown-host",
                                ProjectLock.hostName(
                                        () -> {
                                            throw new IOException("no name");
                                        })),
                () ->
                        assertEquals(
                                "unknown-host",
                                ProjectLock.hostName(
                                        () -> {
                                            throw new IllegalStateException("no name");
                                        })));
    }

    @Test
    @DisplayName("liveness: this process is alive, a pid that cannot exist is not")
    void liveness() {
        assertAll(
                () -> assertTrue(ProjectLock.isAlive(ProcessHandle.current().pid())),
                () -> assertFalse(ProjectLock.isAlive(Long.MAX_VALUE)));
    }

    @Test
    @DisplayName("the locked byte lies beyond any record")
    void lockedRegion() {
        assertAll(
                () -> assertEquals(1L << 30, ProjectLock.LOCKED_REGION_START),
                () -> assertTrue(ProjectLock.LOCKED_REGION_START > ProjectLock.MAX_RECORD_BYTES));
    }
}
