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

package org.cometgui.workflow.engine;

import static org.cometgui.workflow.engine.EngineAssertions.awaitResult;
import static org.cometgui.workflow.testing.TestPaths.absolute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.tools.process.ProcessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The run's event log is closed on every way out of a run, proved by the process's own table of
 * open files. A handle left open costs nothing visible on Linux and blocks renaming or deleting the
 * run on Windows, which is why it is worth a test. Linux only, because {@code /proc/self/fd} is.
 */
@EnabledOnOs(OS.LINUX)
class ResourceReleaseTest {

    @TempDir private Path tmp;

    @Test
    void theEventLogIsClosedWhenARunFinishes()
            throws IOException, InterruptedException, ReuseRefusedException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            awaitResult(
                    fixture.engine()
                            .start(
                                    fixture.request(fixture.standardActions()),
                                    new RecordingListener()));
            assertTrue(Files.isRegularFile(fixture.layout().eventLogFile()));
            assertEquals(List.of(), openDescriptorsTo(fixture.layout().eventLogFile()));
        }
    }

    @Test
    void theEventLogIsClosedWhenTheAttemptCannotBeRecorded() throws IOException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            fixture.lock().close();
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            fixture.engine()
                                    .start(
                                            fixture.request(fixture.standardActions()),
                                            new RecordingListener()));
            assertTrue(Files.isRegularFile(fixture.layout().eventLogFile()), "it was opened");
            assertEquals(List.of(), openDescriptorsTo(fixture.layout().eventLogFile()));
        }
    }

    @Test
    void theEventLogIsClosedWhenTheRecordCannotBeBuilt() throws IOException, ReuseRefusedException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 1)) {
            Clock backwards = new BackwardsClock();
            EngineServices services =
                    new EngineServices(
                            new ProcessService(backwards),
                            backwards,
                            SecretRedactor.patternsOnly(),
                            fixture.sink(),
                            fixture.hashes(),
                            EngineFixture.CORES,
                            EngineFixture.CAP);
            RunHandle handle =
                    new WorkflowEngine(services)
                            .start(
                                    fixture.request(fixture.standardActions()),
                                    new RecordingListener());
            assertThrows(IllegalStateException.class, handle::await);
            assertEquals(List.of(), openDescriptorsTo(fixture.layout().eventLogFile()));
        }
    }

    /** Every descriptor this JVM holds open on the file, read from {@code /proc/self/fd}. */
    static List<Path> openDescriptorsTo(Path file) throws IOException {
        Path real = file.toRealPath();
        List<Path> open = new ArrayList<>();
        try (DirectoryStream<Path> descriptors =
                Files.newDirectoryStream(absolute("proc/self/fd"))) {
            for (Path descriptor : descriptors) {
                try {
                    if (Files.readSymbolicLink(descriptor).equals(real)) {
                        open.add(descriptor);
                    }
                } catch (IOException closedMeanwhile) {
                    // The descriptor was closed while the directory was being read.
                }
            }
        }
        return open;
    }

    /** Reads once at noon, then always an hour earlier. */
    private static final class BackwardsClock extends Clock {

        private final AtomicBoolean first = new AtomicBoolean(true);

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return first.getAndSet(false)
                    ? Instant.parse("2026-10-06T12:00:00Z")
                    : Instant.parse("2026-10-06T11:00:00Z");
        }
    }
}
