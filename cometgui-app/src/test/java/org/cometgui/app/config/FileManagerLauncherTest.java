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

package org.cometgui.app.config;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Open file location (P13-6): the argument array per platform, typed out; the launch through the
 * process runner it is given -- here a recording one, never a real file manager -- with the folder
 * as working directory and nothing added to the environment; and every failure stated: a launcher
 * that cannot start, one that ends with a non-zero status, a folder that is gone, no process
 * service, an unrecognised system, an interrupt. macOS and Windows are graded only as argument
 * arrays: no macOS or Windows host has run them.
 */
class FileManagerLauncherTest {

    private static final String SEED = "CGSEED-LAUNCHER-7f3a";

    @TempDir private Path work;

    /** A process runner that records the command and answers as scripted, starting nothing. */
    private static final class Recorder implements ProcessRunner {

        private final List<ToolCommand> commands = new ArrayList<>();

        private IOException refusal;

        private Integer exit = 0;

        private List<String> errors = List.of();

        @Override
        public RunningProcess start(ToolCommand command, ProcessListener listener)
                throws IOException {
            commands.add(command);
            if (refusal != null) {
                throw refusal;
            }
            for (String line : errors) {
                listener.onStandardError(line);
            }
            if (exit != null) {
                listener.onExit(exit);
            }
            return new RunningProcess() {
                @Override
                public boolean isAlive() {
                    return exit == null;
                }

                @Override
                public int waitForExit() {
                    return exit == null ? 0 : exit;
                }

                @Override
                public void requestCancellation() {
                    // nothing runs
                }
            };
        }
    }

    private Path file() throws IOException {
        Path folder = Files.createDirectories(work.resolve("run/outputs/comet"));
        return Files.writeString(folder.resolve("a.pep.xml"), "<x/>");
    }

    private static FileManagerLauncher launcher(HostOperatingSystem system, Recorder runner) {
        return new FileManagerLauncher(
                Optional.of(system),
                Optional.of(runner),
                "unused",
                () -> SecretRedactor.patternsOnly().withSecret(SEED),
                Duration.ofMillis(200));
    }

    @Test
    @DisplayName("the argument array on each platform, typed out")
    void argv() {
        Path file = work.resolve("run one").resolve("a.pep.xml");
        String folder = work + "/run one";
        assertAll(
                () ->
                        assertEquals(
                                List.of("xdg-open", folder),
                                FileManagerLauncher.argv(HostOperatingSystem.LINUX, file)),
                () ->
                        assertEquals(
                                List.of("open", "-R", folder + "/a.pep.xml"),
                                FileManagerLauncher.argv(HostOperatingSystem.MACOS, file)),
                () ->
                        assertEquals(
                                List.of("explorer.exe", "/select," + folder + "/a.pep.xml"),
                                FileManagerLauncher.argv(HostOperatingSystem.WINDOWS, file)),
                () ->
                        assertEquals(
                                List.of("xdg-open", "/"),
                                FileManagerLauncher.argv(
                                        HostOperatingSystem.LINUX, work.getRoot())));
    }

    @Test
    @DisplayName("Linux: xdg-open on the file's folder, run in that folder, environment untouched")
    void linux() throws IOException {
        Path file = file();
        Path folder = work.resolve("run/outputs/comet");
        Recorder runner = new Recorder();
        String said = launcher(HostOperatingSystem.LINUX, runner).open(file);
        assertAll(
                () ->
                        assertEquals(
                                "Started xdg-open: the file manager was asked to show it.", said),
                () -> assertEquals(1, runner.commands.size()),
                () ->
                        assertEquals(
                                List.of("xdg-open", folder.toString()),
                                runner.commands.get(0).argv()),
                () -> assertEquals(folder, runner.commands.get(0).workingDirectory()),
                () -> assertEquals(Map.of(), runner.commands.get(0).environment()),
                () -> assertFalse(said.contains(file.toString()), "the path is never said"));
    }

    @Test
    @DisplayName("a launcher that cannot be started is a stated failure, redacted")
    void aMissingLauncher() throws IOException {
        Path file = file();
        Recorder runner = new Recorder();
        runner.refusal =
                new IOException(
                        "Cannot run program \"xdg-open\" (in directory \"/home/u/"
                                + SEED
                                + "\"): error=2, No such file or directory");
        IOException thrown =
                assertThrows(
                        IOException.class,
                        () -> launcher(HostOperatingSystem.LINUX, runner).open(file));
        assertAll(
                () ->
                        assertTrue(
                                thrown.getMessage()
                                        .startsWith(
                                                "xdg-open could not be started -- it is missing"
                                                        + " from this computer or cannot be run:"
                                                        + " Cannot run program \"xdg-open\""),
                                thrown.getMessage()),
                () ->
                        assertTrue(
                                thrown.getMessage().endsWith("error=2, No such file or directory"),
                                thrown.getMessage()),
                () -> assertFalse(thrown.getMessage().contains(SEED), "the secret is redacted"));
        runner.refusal = null;
        runner.exit = null;
        // and a RuntimeException from the runner is the same stated failure
        ProcessRunner throwing =
                (command, listener) -> {
                    throw new IllegalArgumentException();
                };
        IOException wrapped =
                assertThrows(
                        IOException.class,
                        () ->
                                new FileManagerLauncher(
                                                Optional.of(HostOperatingSystem.MACOS),
                                                Optional.of(throwing),
                                                "unused",
                                                SecretRedactor::patternsOnly)
                                        .open(file));
        assertEquals(
                "open could not be started -- it is missing from this computer or cannot be run:"
                        + " IllegalArgumentException",
                wrapped.getMessage());
    }

    @Test
    @DisplayName(
            "a non-zero status within the bound is a failure on Linux and macOS, quoting the"
                    + " launcher's last error lines; on Windows explorer.exe's 1 is not")
    void aNonZeroStatus() throws IOException {
        Path file = file();
        Recorder runner = new Recorder();
        runner.exit = 3;
        runner.errors =
                List.of(
                        "first line, dropped",
                        "xdg-open: no method available for opening",
                        "'" + SEED + "'",
                        "last line");
        IOException thrown =
                assertThrows(
                        IOException.class,
                        () -> launcher(HostOperatingSystem.LINUX, runner).open(file));
        assertEquals(
                "xdg-open ended with status 3 and did not show it: xdg-open: no method available"
                        + " for opening '[REDACTED]' last line",
                thrown.getMessage());
        runner.errors = List.of();
        IOException silent =
                assertThrows(
                        IOException.class,
                        () -> launcher(HostOperatingSystem.MACOS, runner).open(file));
        assertEquals("open ended with status 3 and did not show it.", silent.getMessage());
        runner.exit = 1;
        assertEquals(
                "Started explorer.exe: the file manager was asked to show it.",
                launcher(HostOperatingSystem.WINDOWS, runner).open(file));
        assertEquals(
                List.of("explorer.exe", "/select," + file),
                runner.commands.get(runner.commands.size() - 1).argv());
    }

    @Test
    @DisplayName("a launcher still running after the bound has handed the request on")
    void stillRunning() throws IOException {
        Recorder runner = new Recorder();
        runner.exit = null;
        assertEquals(
                "Started open: the file manager was asked to show it.",
                launcher(HostOperatingSystem.MACOS, runner).open(file()));
    }

    @Test
    @DisplayName(
            "a folder that is gone, no process service, an unrecognised system: stated, nothing"
                    + " launched")
    void nothingToStart() throws IOException {
        Recorder runner = new Recorder();
        IOException gone =
                assertThrows(
                        IOException.class,
                        () ->
                                launcher(HostOperatingSystem.LINUX, runner)
                                        .open(work.resolve("deleted/a.pep.xml")));
        assertEquals(
                "the folder that held the file no longer exists, so there is nothing to show",
                gone.getMessage());
        IOException noService =
                assertThrows(
                        IOException.class,
                        () ->
                                new FileManagerLauncher(
                                                Optional.of(HostOperatingSystem.LINUX),
                                                Optional.empty(),
                                                "no process service here",
                                                SecretRedactor::patternsOnly)
                                        .open(file()));
        assertEquals("no process service here", noService.getMessage());
        IOException unknown =
                assertThrows(
                        IOException.class,
                        () ->
                                new FileManagerLauncher(
                                                Optional.empty(),
                                                Optional.of(runner),
                                                "an unrecognised system",
                                                SecretRedactor::patternsOnly)
                                        .open(file()));
        assertAll(
                () -> assertEquals("an unrecognised system", unknown.getMessage()),
                () -> assertEquals(List.of(), runner.commands));
    }

    @Test
    @DisplayName("an interrupt while waiting stops the wait, keeps the interrupt and says so")
    void interrupted() throws IOException {
        Path file = file();
        Recorder runner = new Recorder();
        runner.exit = null;
        Thread.currentThread().interrupt();
        try {
            InterruptedIOException thrown =
                    assertThrows(
                            InterruptedIOException.class,
                            () -> launcher(HostOperatingSystem.LINUX, runner).open(file));
            assertAll(
                    () ->
                            assertEquals(
                                    "interrupted while waiting for xdg-open to start",
                                    thrown.getMessage()),
                    () -> assertTrue(Thread.currentThread().isInterrupted()),
                    () -> assertTrue(thrown.getCause() instanceof InterruptedException));
        } finally {
            Thread.interrupted();
        }
    }
}
