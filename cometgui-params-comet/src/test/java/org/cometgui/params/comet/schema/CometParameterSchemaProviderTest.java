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

package org.cometgui.params.comet.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.fixtures.CometFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The provider against a recording {@link ProcessRunner} whose fake process writes the REAL fixture
 * bytes (or, for a failure case, a CONSTRUCTED file, so labelled). The real binary is run through
 * the process service in {@link CometParameterSchemaProviderRealBinaryTest}.
 */
class CometParameterSchemaProviderTest {

    private static final ToolVersion V202602 = ToolVersion.parse("2026.02.2");
    private static final CuratedMetadata METADATA = MetadataLoader.loadBundled();

    private Path root;

    @BeforeEach
    void createRoot(@TempDir Path directory) {
        root = directory;
    }

    /** A runner that records what it was asked to run and plays a scripted process. */
    static class RecordingRunner implements ProcessRunner {

        private final List<ToolCommand> commands = new ArrayList<>();
        private final List<Boolean> emptyAtStart = new ArrayList<>();
        private byte[] writes;
        private int exitCode;
        private boolean exits = true;
        private boolean refuseToStart;
        private int stderrLines = 1;
        private boolean cancelled;

        static RecordingRunner writing(byte[] bytes) {
            RecordingRunner runner = new RecordingRunner();
            runner.writes = bytes == null ? null : bytes.clone();
            return runner;
        }

        void writesBytes(byte[] bytes) {
            writes = bytes.clone();
        }

        RecordingRunner exitingWith(int code) {
            exitCode = code;
            return this;
        }

        RecordingRunner neverExiting() {
            exits = false;
            return this;
        }

        RecordingRunner refusingToStart() {
            refuseToStart = true;
            return this;
        }

        RecordingRunner printingErrorLines(int count) {
            stderrLines = count;
            return this;
        }

        List<ToolCommand> commands() {
            return commands;
        }

        List<Boolean> emptyAtStart() {
            return emptyAtStart;
        }

        boolean cancelled() {
            return cancelled;
        }

        @Override
        public RunningProcess start(ToolCommand command, ProcessListener listener)
                throws IOException {
            commands.add(command);
            try (var entries = Files.list(command.workingDirectory())) {
                emptyAtStart.add(entries.findAny().isEmpty());
            }
            if (refuseToStart) {
                throw new IOException("constructed: no such executable");
            }
            if (writes != null) {
                Files.write(command.workingDirectory().resolve("comet.params.new"), writes);
            }
            listener.onStandardOutput(" Comet version \"2026.02 rev. 2 (6edec91)\"");
            for (int line = 0; line < stderrLines; line++) {
                listener.onStandardError("constructed stderr " + line);
            }
            if (exits) {
                listener.onExit(exitCode);
            }
            return new RunningProcess() {
                @Override
                public boolean isAlive() {
                    return !exits && !cancelled;
                }

                @Override
                public int waitForExit() {
                    return exitCode;
                }

                @Override
                public void requestCancellation() {
                    cancelled = true;
                }
            };
        }
    }

    private static byte[] fixture(CometFixtures.Mode mode) throws IOException {
        return CometFixtures.bytes(CometFixtures.COMET_2026_02_2, CometFixtures.LINUX_X86_64, mode);
    }

    private CometParameterSchemaProvider provider(ProcessRunner runner) {
        return new CometParameterSchemaProvider(runner, METADATA, Duration.ofSeconds(5), root);
    }

    /** Never created: the fake runner does not need it, and the workspace root stays empty. */
    private Path executable() {
        return root.resolve("comet.linux.exe").toAbsolutePath();
    }

    private CometToolIdentity identity(ToolVersion version, ToolCapability... capabilities) {
        Set<ToolCapability> set = EnumSet.noneOf(ToolCapability.class);
        set.addAll(List.of(capabilities));
        return new CometToolIdentity(executable(), version, set);
    }

    private CometSchemaException failure(RecordingRunner runner, CometToolIdentity comet) {
        return assertThrows(CometSchemaException.class, () -> provider(runner).schemaFor(comet));
    }

    private void assertWorkspaceGone() throws IOException {
        try (var entries = Files.list(root)) {
            assertEquals(List.of(), entries.toList(), "the per-call working directory was left");
        }
    }

    @Test
    @DisplayName(
            "without COMPLETE_PARAMS_QUERY it runs exactly [exe, -p] and marks the schema PARTIAL")
    void fallsBackToDefaults() throws IOException, CometSchemaException {
        RecordingRunner runner = RecordingRunner.writing(fixture(CometFixtures.Mode.DEFAULTS));
        CometParameterSchema schema =
                provider(runner).schemaFor(identity(V202602, ToolCapability.PIN_OUTPUT));
        assertEquals(1, runner.commands().size());
        ToolCommand command = runner.commands().get(0);
        assertEquals(List.of(executable().toString(), "-p"), command.argv());
        assertTrue(command.workingDirectory().isAbsolute());
        assertEquals(root, command.workingDirectory().getParent());
        assertEquals(List.of(true), runner.emptyAtStart());
        assertTrue(command.environment().isEmpty());
        assertEquals(DiscoveryMode.PARTIAL_DISCOVERY, schema.mode());
        assertEquals(96, schema.discovered().names().size());
        assertTrue(schema.drift().isClean(), schema.drift()::describe);
        assertEquals(96, schema.definitions().size());
        assertEquals("database_name", schema.definitions().get(0).name());
        assertEquals(METADATA, schema.metadata());
        assertEquals(V202602, schema.comet().version());
        assertWorkspaceGone();
    }

    @Test
    @DisplayName("with COMPLETE_PARAMS_QUERY it runs exactly [exe, -q] and the schema is COMPLETE")
    void asksForTheCompleteSet() throws IOException, CometSchemaException {
        RecordingRunner runner = RecordingRunner.writing(fixture(CometFixtures.Mode.COMPLETE));
        CometParameterSchema schema =
                provider(runner).schemaFor(identity(V202602, ToolCapability.COMPLETE_PARAMS_QUERY));
        assertEquals(List.of(executable().toString(), "-q"), runner.commands().get(0).argv());
        assertEquals(DiscoveryMode.COMPLETE, schema.mode());
        assertEquals(118, schema.definitions().size());
        assertTrue(schema.drift().isClean(), schema.drift()::describe);
        assertWorkspaceGone();
    }

    @Test
    @DisplayName("a non-zero exit fails as NON_ZERO_EXIT, with the command and the output")
    void nonZeroExit() throws IOException, CometSchemaException {
        RecordingRunner runner = RecordingRunner.writing(fixture(CometFixtures.Mode.COMPLETE));
        runner.exitingWith(1);
        CometSchemaException failure =
                failure(runner, identity(V202602, ToolCapability.COMPLETE_PARAMS_QUERY));
        assertEquals(CometSchemaException.Failure.NON_ZERO_EXIT, failure.failure());
        assertTrue(
                failure.getMessage().contains("[\"" + executable() + "\", \"-q\"]"),
                failure.getMessage());
        assertTrue(failure.getMessage().contains(" exited 1,"), failure.getMessage());
        assertEquals(
                List.of(
                        "[stdout]  Comet version \"2026.02 rev. 2 (6edec91)\"",
                        "[stderr] constructed stderr 0"),
                failure.output());
        assertWorkspaceGone();
    }

    @Test
    @DisplayName("exit 0 with no comet.params.new fails as NO_FILE_WRITTEN")
    void noFile() throws IOException, CometSchemaException {
        RecordingRunner runner = RecordingRunner.writing(null);
        CometSchemaException failure = failure(runner, identity(V202602));
        assertEquals(CometSchemaException.Failure.NO_FILE_WRITTEN, failure.failure());
        assertTrue(failure.getMessage().contains("exited 0 and wrote no comet.params.new"));
        assertWorkspaceGone();
    }

    @Test
    @DisplayName("a process that never exits is cancelled and fails as TIMED_OUT")
    void timeout() throws IOException, CometSchemaException {
        RecordingRunner runner = RecordingRunner.writing(fixture(CometFixtures.Mode.COMPLETE));
        runner.neverExiting();
        CometParameterSchemaProvider provider =
                new CometParameterSchemaProvider(runner, METADATA, Duration.ofMillis(50), root);
        CometSchemaException failure =
                assertThrows(
                        CometSchemaException.class, () -> provider.schemaFor(identity(V202602)));
        assertEquals(CometSchemaException.Failure.TIMED_OUT, failure.failure());
        assertTrue(runner.cancelled(), "the process was not cancelled");
        assertTrue(
                failure.getMessage().contains("did not finish within PT0.05S"),
                failure.getMessage());
        assertEquals(2, failure.output().size());
    }

    @Test
    @DisplayName(
            "an interrupt cancels the process, keeps the interrupt flag and fails as INTERRUPTED")
    void interrupted() throws IOException, CometSchemaException {
        // No file: an interruptible channel would refuse to write it on an interrupted thread.
        RecordingRunner runner = RecordingRunner.writing(null);
        runner.neverExiting();
        Thread.currentThread().interrupt();
        CometSchemaException failure;
        try {
            failure = failure(runner, identity(V202602));
        } finally {
            assertTrue(Thread.interrupted(), "the interrupt flag was swallowed");
        }
        assertEquals(CometSchemaException.Failure.INTERRUPTED, failure.failure());
        assertTrue(runner.cancelled());
        assertInstanceOf(InterruptedException.class, failure.getCause());
    }

    @Test
    @DisplayName("a process that cannot start fails as LAUNCH_FAILED")
    void cannotStart() throws IOException, CometSchemaException {
        RecordingRunner runner = RecordingRunner.writing(null);
        runner.refusingToStart();
        CometSchemaException failure = failure(runner, identity(V202602));
        assertEquals(CometSchemaException.Failure.LAUNCH_FAILED, failure.failure());
        assertTrue(failure.getMessage().contains("constructed: no such executable"));
        assertWorkspaceGone();
    }

    @Test
    @DisplayName("a workspace root that does not exist fails as LAUNCH_FAILED before any process")
    void noWorkspace() {
        RecordingRunner runner = RecordingRunner.writing(null);
        CometParameterSchemaProvider provider =
                new CometParameterSchemaProvider(
                        runner, METADATA, Duration.ofSeconds(1), root.resolve("missing"));
        CometSchemaException failure =
                assertThrows(
                        CometSchemaException.class, () -> provider.schemaFor(identity(V202602)));
        assertEquals(CometSchemaException.Failure.LAUNCH_FAILED, failure.failure());
        assertEquals(List.of(), runner.commands());
    }

    @Test
    @DisplayName("a file that is not a dump fails as UNREADABLE_DUMP")
    void notADump() throws IOException, CometSchemaException {
        CometSchemaException failure =
                failure(
                        RecordingRunner.writing(
                                "constructed: not a parameter file\n"
                                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        identity(V202602));
        assertEquals(CometSchemaException.Failure.UNREADABLE_DUMP, failure.failure());
        assertInstanceOf(MalformedDumpException.class, failure.getCause());
        assertWorkspaceGone();
    }

    @Test
    @DisplayName("a file that is not UTF-8 fails as UNREADABLE_DUMP")
    void notText() throws IOException, CometSchemaException {
        CometSchemaException failure =
                failure(
                        RecordingRunner.writing(new byte[] {(byte) 0xff, (byte) 0xfe, 0x41}),
                        identity(V202602));
        assertEquals(CometSchemaException.Failure.UNREADABLE_DUMP, failure.failure());
        assertTrue(failure.getMessage().contains("is not UTF-8 text"), failure.getMessage());
    }

    @Test
    @EnabledOnOs(
            value = {OS.LINUX, OS.MAC},
            disabledReason = "makes the file unreadable with POSIX permissions")
    @DisplayName("a file that exists but cannot be read fails as UNREADABLE_DUMP")
    void unreadableFile() throws IOException {
        RecordingRunner runner =
                new RecordingRunner() {
                    @Override
                    public RunningProcess start(ToolCommand command, ProcessListener listener)
                            throws IOException {
                        RunningProcess process = super.start(command, listener);
                        Files.setPosixFilePermissions(
                                command.workingDirectory().resolve("comet.params.new"), Set.of());
                        return process;
                    }
                };
        runner.writesBytes(fixture(CometFixtures.Mode.COMPLETE));
        CometSchemaException failure = failure(runner, identity(V202602));
        assertEquals(CometSchemaException.Failure.UNREADABLE_DUMP, failure.failure());
        assertTrue(failure.getMessage().contains("could not be read"), failure.getMessage());
        assertWorkspaceGone();
    }

    @Test
    @DisplayName("a dump marked with another version than the identity fails as VERSION_MISMATCH")
    void versionMismatch() throws IOException, CometSchemaException {
        CometSchemaException failure =
                failure(
                        RecordingRunner.writing(fixture(CometFixtures.Mode.COMPLETE)),
                        identity(
                                ToolVersion.parse("2026.02.3"),
                                ToolCapability.COMPLETE_PARAMS_QUERY));
        assertEquals(CometSchemaException.Failure.VERSION_MISMATCH, failure.failure());
        assertEquals(
                "the build at "
                        + executable()
                        + " was identified as Comet 2026.02.3 but its"
                        + " -q output is marked \"2026.02 rev. 2 (6edec91)\", which is Comet"
                        + " 2026.02.2",
                failure.getMessage());
        assertWorkspaceGone();
    }

    @Test
    @DisplayName("the output kept for a diagnostic is bounded")
    void boundedOutput() throws IOException, CometSchemaException {
        RecordingRunner runner = RecordingRunner.writing(null);
        runner.exitingWith(3);
        runner.printingErrorLines(500);
        CometSchemaException failure = failure(runner, identity(V202602));
        assertEquals(CometParameterSchemaProvider.MAXIMUM_OUTPUT_LINES, failure.output().size());
        assertEquals("[stderr] constructed stderr 198", failure.output().get(199));
    }

    @Test
    @DisplayName("a timeout that is not positive is refused")
    void timeoutMustBePositive() {
        for (Duration bad : List.of(Duration.ZERO, Duration.ofSeconds(-1))) {
            IllegalArgumentException failure =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    new CometParameterSchemaProvider(
                                            RecordingRunner.writing(null), METADATA, bad, root));
            assertTrue(failure.getMessage().contains("must be positive"));
        }
        new CometParameterSchemaProvider(
                RecordingRunner.writing(null), METADATA, Duration.ofMillis(1), root);
    }

    @Test
    @DisplayName("an identity needs an absolute path and Comet's own capabilities")
    void identityRules() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CometToolIdentity(Path.of("comet"), V202602, Set.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new CometToolIdentity(
                                executable(), V202602, Set.of(ToolCapability.XML_OUTPUT)));
        CometToolIdentity plain = identity(V202602, ToolCapability.PIN_OUTPUT);
        assertFalse(plain.hasCompleteParamsQuery());
        assertEquals(Set.of(ToolCapability.PIN_OUTPUT), plain.capabilities());
        assertTrue(
                identity(V202602, ToolCapability.COMPLETE_PARAMS_QUERY).hasCompleteParamsQuery());
    }
}
