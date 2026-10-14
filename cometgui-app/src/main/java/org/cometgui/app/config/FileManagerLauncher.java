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

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.domain.tools.HostOperatingSystem;

/**
 * "Open file location" (phase 13, design decision P13-6): the platform's own file manager, started
 * with an argument array through the one process service -- never {@code HostServices}, {@code
 * java.awt.Desktop} or a shell.
 *
 * <ul>
 *   <li><strong>Linux</strong>: {@code xdg-open <the file's folder>}. xdg-open opens a folder in
 *       the desktop's file manager; it has no "select this file" form.
 *   <li><strong>macOS</strong>: {@code open -R <file>}, which reveals the file in Finder.
 *       <strong>Unverified</strong>: no macOS host has run it.
 *   <li><strong>Windows</strong>: {@code explorer.exe /select,<file>}, one argument, which opens
 *       the file's folder with the file selected. <strong>Unverified</strong>: no Windows host has
 *       run it; and how the JDK quotes that one argument on Windows's command line is not observed.
 * </ul>
 *
 * <p>Only the Linux argument array has ever been observed, and only through a recording process
 * runner: no test starts a real file manager, and this host has no {@code xdg-open}.
 *
 * <h2>A failure is stated, never a silent no-op</h2>
 *
 * <p>A launcher that cannot be started -- missing from the computer, not executable -- is an {@link
 * IOException} saying so. So is a folder that no longer exists, and, on Linux and macOS, a launcher
 * that ends with a non-zero status within {@link #EXIT_BOUND} (xdg-open reports "no method
 * available" that way). {@code explorer.exe} is known to end with status 1 even when it did what
 * was asked, so on Windows only a launch that could not start is a failure. A launcher still
 * running after the bound has handed the request to the file manager and is reported as started.
 *
 * <p>Every message passes the session's redactor, as every path the Provenance section shows does;
 * the path acted on is never put in a message as it is.
 */
final class FileManagerLauncher {

    /**
     * How long the launcher is given to say it failed: {@value #EXIT_BOUND_SECONDS} seconds.
     * xdg-open and open return as soon as they have handed the request on, normally well under a
     * second; a launcher still running after this has not refused.
     */
    static final long EXIT_BOUND_SECONDS = 5;

    /** {@link #EXIT_BOUND_SECONDS} as a duration. */
    static final Duration EXIT_BOUND = Duration.ofSeconds(EXIT_BOUND_SECONDS);

    /** How many of the launcher's last error lines a failure quotes. */
    static final int ERROR_LINES = 3;

    private final Optional<HostOperatingSystem> system;

    private final Optional<ProcessRunner> processes;

    private final String unavailable;

    private final Supplier<SecretRedactor> redactor;

    private final Duration bound;

    /**
     * The launcher.
     *
     * @param system this computer's operating system, or empty when CometGUI does not recognise it
     * @param processes the one process service, or empty when the application has none
     * @param unavailable why no file manager can be started, when either is empty
     * @param redactor the session's secret rules, applied to every message
     */
    FileManagerLauncher(
            Optional<HostOperatingSystem> system,
            Optional<ProcessRunner> processes,
            String unavailable,
            Supplier<SecretRedactor> redactor) {
        this(system, processes, unavailable, redactor, EXIT_BOUND);
    }

    /**
     * The launcher with another bound on the launcher's exit, for tests.
     *
     * @param system this computer's operating system, or empty
     * @param processes the one process service, or empty
     * @param unavailable why no file manager can be started, when either is empty
     * @param redactor the session's secret rules
     * @param bound how long a launcher is given to fail
     */
    FileManagerLauncher(
            Optional<HostOperatingSystem> system,
            Optional<ProcessRunner> processes,
            String unavailable,
            Supplier<SecretRedactor> redactor,
            Duration bound) {
        this.system = Objects.requireNonNull(system, "system");
        this.processes = Objects.requireNonNull(processes, "processes");
        this.unavailable = Objects.requireNonNull(unavailable, "unavailable");
        this.redactor = Objects.requireNonNull(redactor, "redactor");
        this.bound = Objects.requireNonNull(bound, "bound");
    }

    /**
     * The argument array that shows a file in an operating system's file manager.
     *
     * @param system the operating system
     * @param file the file, absolute
     * @return the launcher and its arguments
     */
    static List<String> argv(HostOperatingSystem system, Path file) {
        Objects.requireNonNull(file, "file");
        return switch (Objects.requireNonNull(system, "system")) {
            case LINUX -> List.of("xdg-open", folderOf(file).toString());
            case MACOS -> List.of("open", "-R", file.toString());
            case WINDOWS -> List.of("explorer.exe", "/select," + file);
        };
    }

    /**
     * Starts the file manager on a file's location and waits, within the bound, for the launcher to
     * say whether it failed. Blocking; never called on the JavaFX application thread.
     *
     * @param file the file whose location is shown
     * @return what was started, in words, without the path
     * @throws IOException if no file manager could be started, or the launcher reported failure
     */
    String open(Path file) throws IOException {
        Path absolute = Objects.requireNonNull(file, "file").toAbsolutePath();
        if (system.isEmpty() || processes.isEmpty()) {
            throw new IOException(unavailable);
        }
        Path folder = folderOf(absolute);
        if (!Files.isDirectory(folder)) {
            throw new IOException(
                    "the folder that held the file no longer exists, so there is nothing to show");
        }
        List<String> argv = argv(system.get(), absolute);
        String launcher = argv.get(0);
        Outcome outcome = new Outcome();
        try {
            processes.get().start(new ToolCommand(argv, folder, Map.of()), outcome);
        } catch (IOException | RuntimeException notStarted) {
            throw new IOException(
                    launcher
                            + " could not be started -- it is missing from this computer or cannot"
                            + " be run: "
                            + clean(notStarted),
                    notStarted);
        }
        Integer status;
        try {
            status = outcome.exit.get(bound.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException stillRunning) {
            return "Started " + launcher + ": the file manager was asked to show it.";
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            InterruptedIOException stopped =
                    new InterruptedIOException(
                            "interrupted while waiting for " + launcher + " to start");
            stopped.initCause(interrupted);
            throw stopped;
        } catch (ExecutionException impossible) {
            throw new IOException(launcher + " ended without an exit status", impossible);
        }
        if (status != 0 && system.get() != HostOperatingSystem.WINDOWS) {
            throw new IOException(
                    launcher
                            + " ended with status "
                            + status
                            + " and did not show it"
                            + outcome.errors(redactor.get()));
        }
        return "Started " + launcher + ": the file manager was asked to show it.";
    }

    private String clean(Exception failure) {
        String message = failure.getMessage();
        return redactor.get()
                .redactText(
                        message == null || message.isBlank()
                                ? failure.getClass().getSimpleName()
                                : message);
    }

    private static Path folderOf(Path file) {
        Path parent = file.getParent();
        return parent == null ? file : parent;
    }

    /** What the launcher said and how it ended, from the process service's threads. */
    private static final class Outcome implements ProcessListener {

        private final CompletableFuture<Integer> exit = new CompletableFuture<>();

        private final Deque<String> lastErrors = new ArrayDeque<>();

        @Override
        public void onStandardOutput(String line) {
            // a launcher's ordinary output says nothing about whether it failed
        }

        @Override
        public void onStandardError(String line) {
            synchronized (lastErrors) {
                lastErrors.addLast(line);
                while (lastErrors.size() > ERROR_LINES) {
                    lastErrors.removeFirst();
                }
            }
        }

        @Override
        public void onExit(int exitCode) {
            exit.complete(exitCode);
        }

        String errors(SecretRedactor redactor) {
            synchronized (lastErrors) {
                return lastErrors.isEmpty()
                        ? "."
                        : ": " + redactor.redactText(String.join(" ", lastErrors));
            }
        }
    }
}
