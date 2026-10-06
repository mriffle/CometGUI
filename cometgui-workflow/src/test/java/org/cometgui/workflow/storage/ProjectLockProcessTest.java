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
import java.net.InetAddress;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.cometgui.domain.project.ProjectLayout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code R-RUN-05} across two real JVMs: a live second CometGUI is refused naming its pid and host,
 * the lock is released on close, and a CometGUI that died holding the lock leaves a stale lock that
 * is recovered and named.
 */
class ProjectLockProcessTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    @TempDir private Path temporary;

    private ProjectLayout project;

    @BeforeEach
    void createDirectory() throws IOException {
        project = new ProjectLayout(temporary.resolve("Shared"));
        Files.createDirectories(project.root());
    }

    private static String thisHost() {
        return ProjectLock.hostName(() -> InetAddress.getLocalHost().getHostName());
    }

    @Test
    @DisplayName(
            "a live second JVM's lock refuses this one, naming its pid and host; close releases")
    void liveHolderRefusedThenReleased() throws IOException, InterruptedException {
        Path gateFile = Files.createFile(temporary.resolve("gate"));
        ChildJvm child;
        try (FileChannel gate = FileChannel.open(gateFile, StandardOpenOption.WRITE);
                FileLock closed = gate.lock()) {
            child =
                    ChildJvm.start(
                            temporary, "hold", project.root().toString(), gateFile.toString());
            try {
                String held = child.awaitHeld(TIMEOUT);
                ProjectLockedException thrown =
                        assertThrows(
                                ProjectLockedException.class,
                                () -> ProjectLock.acquire(project, Clock.systemUTC()));
                assertAll(
                        () -> assertEquals("HELD " + child.pid() + " " + thisHost(), held),
                        () ->
                                assertEquals(
                                        ProjectLockedException.Reason.HELD_BY_ANOTHER_PROCESS,
                                        thrown.reason()),
                        () -> assertEquals(child.pid(), thrown.owner().orElseThrow().pid()),
                        () -> assertEquals(thisHost(), thrown.owner().orElseThrow().host()),
                        () ->
                                assertEquals(
                                        "the project "
                                                + project.root()
                                                + " is open in another CometGUI: "
                                                + thrown.owner().orElseThrow().describe()
                                                + ". Close it there first.",
                                        thrown.getMessage()),
                        () ->
                                assertTrue(
                                        thrown.getMessage()
                                                .contains("process " + child.pid() + " on host ")),
                        () -> assertTrue(closed.isValid()));
            } catch (AssertionError | RuntimeException | InterruptedException failure) {
                child.stop();
                throw failure;
            }
        }
        // The gate is released: the child releases the project lock and exits.
        assertEquals(0, child.awaitExit(TIMEOUT), () -> String.valueOf(child.output()));
        assertAll(
                () -> assertEquals("RELEASED true", child.output().get(child.output().size() - 1)),
                () -> assertEquals(0, Files.size(project.lockFile())));
        try (ProjectLock lock = ProjectLock.acquire(project, Clock.systemUTC())) {
            assertAll(
                    () -> assertEquals(Optional.empty(), lock.recovered()),
                    () -> assertEquals(ProcessHandle.current().pid(), lock.owner().pid()));
        }
    }

    @Test
    @DisplayName("a JVM that died holding the lock leaves a stale lock, recovered and named")
    void deadHolderRecovered() throws IOException, InterruptedException {
        ChildJvm child = ChildJvm.start(temporary, "halt", project.root().toString());
        String held = child.awaitHeld(TIMEOUT);
        assertEquals(0, child.awaitExit(TIMEOUT), () -> String.valueOf(child.output()));
        long dead = child.pid();
        assertAll(
                () -> assertEquals("HELD " + dead + " " + thisHost(), held),
                () -> assertFalse(ProjectLock.isAlive(dead), "the child is gone"),
                () ->
                        assertTrue(
                                Files.readString(project.lockFile())
                                        .contains("\"pid\": " + dead + ",")));
        try (ProjectLock lock = ProjectLock.acquire(project, Clock.systemUTC())) {
            assertAll(
                    () -> assertEquals(dead, lock.recovered().orElseThrow().pid()),
                    () -> assertEquals(thisHost(), lock.recovered().orElseThrow().host()),
                    () ->
                            assertEquals(
                                    "recovered a stale lock on "
                                            + project.root()
                                            + " left by "
                                            + lock.recovered().orElseThrow().describe()
                                            + ", which is no longer running",
                                    lock.recoveryNotice().orElseThrow()),
                    () ->
                            assertTrue(
                                    Files.readString(project.lockFile())
                                            .contains(
                                                    "\"pid\": "
                                                            + ProcessHandle.current().pid()
                                                            + ",")));
        }
    }
}
