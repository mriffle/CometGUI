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

package org.cometgui.app.e2e.tierb;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.cometgui.app.e2e.support.RunnerLocale;
import org.cometgui.app.testing.RealSearch;
import org.cometgui.app.uidriver.Await;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.tools.api.ToolRunOutcome;
import org.cometgui.tools.api.ToolRunner;
import org.cometgui.tools.process.ProcessService;

/**
 * One launch of the installed CometGUI with the test bridge attached: started through the process
 * service, attached to over loopback, and stopped the way a user stops it.
 *
 * <h2>Starting</h2>
 *
 * <p>The installed {@code bin/CometGUI} -- the unmodified native launcher -- is started with no
 * arguments, from a launch directory of the test's choosing, in exactly the environment {@link
 * PackagedDisplay#launchEnvironment} gives, plus one variable:
 *
 * <pre>
 *   JAVA_TOOL_OPTIONS=-javaagent:&lt;bridge jar&gt;=port-file=&lt;port file&gt; [extra JVM options]
 * </pre>
 *
 * <p>which the launcher's JVM reads like any JVM does (measured by Phase 14 unit 5: it prints
 * "Picked up JAVA_TOOL_OPTIONS" on standard error; {@code JDK_JAVA_OPTIONS} is ignored by the
 * jpackage launcher). That variable is split on whitespace, so neither path may contain any -- the
 * fresh home, with its space and accented letter, is not in it.
 *
 * <h2>Attaching</h2>
 *
 * <p>{@link #attach(Duration)} waits -- through {@link Await} -- until the port file exists or the
 * process has exited (a failed start is reported with what the application printed, not as a
 * timeout), connects, and asks the bridge to wait for the window and attach its driver.
 *
 * <h2>Stopping</h2>
 *
 * <p>{@link #closeWindow()} sends the window {@code WM_DELETE_WINDOW} -- what a window manager's
 * close button sends -- with {@code scripts/package/x11_windows.py}, from outside the application;
 * {@link #awaitExit(Duration)} then waits for the process to end and returns its status, which the
 * process service has reaped.
 */
public final class LaunchedCometGui implements AutoCloseable {

    /** The window's title, which the product sets. */
    public static final String WINDOW_TITLE = "CometGUI";

    private static final String X11_HELPER = "scripts/package/x11_windows.py";

    private static final Duration HELPER_BOUND = Duration.ofMinutes(2);

    private final InstalledCometGui installed;

    private final PackagedDisplay display;

    private final Path portFile;

    private final RunningProcess process;

    private final List<String> output;

    private final CountDownLatch exited;

    private final AtomicInteger exitStatus;

    private BridgeClient bridge;

    private LaunchedCometGui(
            InstalledCometGui installed,
            PackagedDisplay display,
            Path portFile,
            RunningProcess process,
            List<String> output,
            CountDownLatch exited,
            AtomicInteger exitStatus) {
        this.installed = installed;
        this.display = display;
        this.portFile = portFile;
        this.process = process;
        this.output = output;
        this.exited = exited;
        this.exitStatus = exitStatus;
    }

    /**
     * Starts the installed launcher with the bridge attached.
     *
     * @param installed the installation
     * @param display the display and the launcher's environment
     * @param home the fresh user home
     * @param launchDirectory the working directory to start it from
     * @param bridgeJar the bridge's agent jar ({@link BridgeJar})
     * @param portFile where the bridge is to write its port and token; must not exist yet
     * @param extraJvmOptions further options for {@code JAVA_TOOL_OPTIONS}, each without whitespace
     * @return the launch
     * @throws IOException if the process cannot be started
     */
    public static LaunchedCometGui start(
            InstalledCometGui installed,
            PackagedDisplay display,
            Path home,
            Path launchDirectory,
            Path bridgeJar,
            Path portFile,
            List<String> extraJvmOptions)
            throws IOException {
        return start(
                installed,
                display,
                home,
                launchDirectory,
                bridgeJar,
                portFile,
                extraJvmOptions,
                Set.of());
    }

    /**
     * As {@link #start(InstalledCometGui, PackagedDisplay, Path, Path, Path, Path, List)}, with
     * some of {@link PackagedDisplay#launchEnvironment}'s variables left out -- for the harness's
     * own locale control, which launches the application with no locale at all (P14-9).
     *
     * @param installed the installation
     * @param display the display and the launcher's environment
     * @param home the fresh user home
     * @param launchDirectory the working directory to start it from
     * @param bridgeJar the bridge's agent jar ({@link BridgeJar})
     * @param portFile where the bridge is to write its port and token; must not exist yet
     * @param extraJvmOptions further options for {@code JAVA_TOOL_OPTIONS}, each without whitespace
     * @param withoutVariables the launch environment's variables to leave out, such as {@code
     *     LC_ALL}; never {@code JAVA_TOOL_OPTIONS}, which attaches the bridge
     * @return the launch
     * @throws IOException if the process cannot be started
     * @throws IllegalArgumentException if a variable to leave out is not in the launch environment
     */
    public static LaunchedCometGui start(
            InstalledCometGui installed,
            PackagedDisplay display,
            Path home,
            Path launchDirectory,
            Path bridgeJar,
            Path portFile,
            List<String> extraJvmOptions,
            Set<String> withoutVariables)
            throws IOException {
        requireNoWhitespace(bridgeJar, "the bridge jar");
        requireNoWhitespace(portFile, "the bridge's port file");
        if (Files.exists(portFile)) {
            throw new IllegalArgumentException(
                    portFile + " exists already; a launch needs a new one");
        }
        List<String> options = new ArrayList<>();
        options.add("-javaagent:" + bridgeJar + "=port-file=" + portFile);
        for (String option : extraJvmOptions) {
            if (option.isBlank() || option.chars().anyMatch(Character::isWhitespace)) {
                throw new IllegalArgumentException(
                        "a JAVA_TOOL_OPTIONS option cannot hold whitespace: \"" + option + "\"");
            }
            options.add(option);
        }
        Map<String, String> environment = new LinkedHashMap<>(display.launchEnvironment(home));
        for (String variable : withoutVariables) {
            if (environment.remove(variable) == null) {
                throw new IllegalArgumentException(
                        variable + " is not in the launch environment " + environment.keySet());
            }
        }
        environment.put("JAVA_TOOL_OPTIONS", String.join(" ", options));

        List<String> output = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch exited = new CountDownLatch(1);
        AtomicInteger status = new AtomicInteger(Integer.MIN_VALUE);
        RunningProcess process =
                new ProcessService(Clock.systemUTC())
                        .start(
                                new ToolCommand(
                                        List.of(installed.launcher().toString()),
                                        Files.createDirectories(launchDirectory),
                                        environment),
                                new ProcessListener() {
                                    @Override
                                    public void onStandardOutput(String line) {
                                        output.add(line);
                                    }

                                    @Override
                                    public void onStandardError(String line) {
                                        output.add(line);
                                    }

                                    @Override
                                    public void onExit(int exitCode) {
                                        status.set(exitCode);
                                        exited.countDown();
                                    }
                                });
        return new LaunchedCometGui(installed, display, portFile, process, output, exited, status);
    }

    /**
     * Waits for the bridge, connects, and attaches it to the application's window.
     *
     * @param bound how long each of the two waits may take at most
     * @return the window's title
     * @throws AssertionError if the process exited first, or nothing appeared in time -- with what
     *     the application printed
     */
    public String attach(Duration bound) {
        return connect(bound).text("bridge.awaitApplication", Math.toIntExact(bound.toSeconds()));
    }

    /**
     * Waits for the bridge and connects to it, without waiting for the application's window: for
     * what the launched JVM reports about itself ({@link #environment()}, {@link #locale()}) when
     * the application may never show one.
     *
     * @param bound how long the wait may take at most
     * @return the connection
     * @throws AssertionError if the process exited before its bridge started, with what it printed
     */
    public BridgeClient connect(Duration bound) {
        Await.until(
                "the packaged application's bridge to write "
                        + portFile
                        + " (or the process to end)",
                () -> Files.isRegularFile(portFile) || hasExited(),
                Boolean::booleanValue,
                bound);
        if (!Files.isRegularFile(portFile)) {
            throw new AssertionError(
                    "the packaged CometGUI exited with status "
                            + exitStatus.get()
                            + " before its bridge started; it printed: "
                            + output());
        }
        BridgeClient connected = BridgeClient.connect(BridgeEndpoint.read(portFile));
        bridge = connected;
        return connected;
    }

    /**
     * The bridge connection.
     *
     * @return the connection
     * @throws IllegalStateException before {@link #attach(Duration)}
     */
    public BridgeClient bridge() {
        if (bridge == null) {
            throw new IllegalStateException("not attached yet");
        }
        return bridge;
    }

    /**
     * Where the bridge listens.
     *
     * @return the endpoint, with the launcher's pid
     */
    public BridgeEndpoint endpoint() {
        return bridge().endpoint();
    }

    /**
     * The driver of the packaged application.
     *
     * @return a {@link BridgedUiDriver} over the bridge
     */
    public BridgedUiDriver driver() {
        return new BridgedUiDriver(bridge());
    }

    /**
     * The packaged application's file dialogs.
     *
     * @return a {@link BridgedChooserScript} over the bridge
     */
    public BridgedChooserScript chooser() {
        return new BridgedChooserScript(bridge());
    }

    /**
     * What the packaged application's JVM reports about itself: its encodings, {@code java.home},
     * {@code pid} and the environment variables a test asserts on.
     *
     * @return {@code name -> value}, in the order reported
     */
    public Map<String, String> environment() {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : bridge().lines("bridge.environment")) {
            int equals = line.indexOf('=');
            values.put(line.substring(0, equals), line.substring(equals + 1));
        }
        return values;
    }

    /**
     * The launched process's locale, as it reported it, for {@link RunnerLocale#assertUtf8()}
     * (P14-9).
     *
     * @return the locale
     */
    public RunnerLocale locale() {
        Map<String, String> reported = environment();
        return RunnerLocale.reportedBy(
                "the packaged CometGUI (pid " + reported.get("pid") + ")", reported);
    }

    /**
     * Sends the application's window {@code WM_DELETE_WINDOW}, as a window manager's close button
     * does, after finding it by title and by the launcher's pid.
     *
     * @return the window's X identifier
     * @throws IOException if the helper cannot be started
     * @throws AssertionError if no such window exists or the message could not be sent
     */
    public String closeWindow() throws IOException {
        Path helper = RealSearch.repositoryRoot().resolve(X11_HELPER);
        ToolRunOutcome found =
                helper(
                        List.of(
                                "/usr/bin/python3",
                                helper.toString(),
                                "wait",
                                "--pid",
                                Long.toString(endpoint().pid()),
                                "--title",
                                WINDOW_TITLE,
                                "--deadline",
                                "60"));
        if (!found.exitedZero() || found.standardOutput().isEmpty()) {
            throw new AssertionError(
                    "no viewable window titled "
                            + WINDOW_TITLE
                            + " owned by pid "
                            + endpoint().pid()
                            + ": "
                            + found.joinedOutput());
        }
        String window = found.standardOutput().get(0).split(" ", 2)[0];
        ToolRunOutcome closed =
                helper(List.of("/usr/bin/python3", helper.toString(), "close", "--window", window));
        if (!closed.exitedZero()) {
            throw new AssertionError(
                    "WM_DELETE_WINDOW could not be sent to "
                            + window
                            + ": "
                            + closed.joinedOutput());
        }
        return window;
    }

    /**
     * Waits for the process to end.
     *
     * @param bound how long to wait at most
     * @return its exit status
     * @throws AssertionError if it is still running when the bound passes
     */
    public int awaitExit(Duration bound) {
        try {
            if (!exited.await(bound.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new AssertionError(
                        "the packaged CometGUI was still running "
                                + bound
                                + " after its window was closed; it printed: "
                                + output());
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "interrupted waiting for CometGUI to exit", interrupted);
        }
        if (bridge != null) {
            bridge.close();
        }
        return exitStatus.get();
    }

    /**
     * Whether the process has ended.
     *
     * @return {@code true} once the process service has seen it exit
     */
    public boolean hasExited() {
        return exited.getCount() == 0;
    }

    /**
     * Everything the application printed so far, standard output and standard error interleaved as
     * they arrived.
     *
     * @return the lines
     */
    public List<String> output() {
        synchronized (output) {
            return List.copyOf(output);
        }
    }

    /**
     * The installation this launch runs.
     *
     * @return the installation
     */
    public InstalledCometGui installed() {
        return installed;
    }

    /** Ends the process if it is still running, and closes the bridge connection. */
    @Override
    public void close() {
        if (bridge != null) {
            bridge.close();
        }
        if (!hasExited()) {
            process.requestCancellation();
            boolean ended;
            try {
                ended = exited.await(60, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted ending CometGUI", interrupted);
            }
            if (!ended) {
                throw new AssertionError(
                        "the packaged CometGUI was still running 60 s after it was cancelled");
            }
        }
    }

    private ToolRunOutcome helper(List<String> argv) throws IOException {
        return new ToolRunner(new ProcessService(Clock.systemUTC()), HELPER_BOUND)
                .run(
                        new ToolCommand(
                                argv, RealSearch.repositoryRoot(), display.helperEnvironment()));
    }

    /**
     * Refuses a path {@code JAVA_TOOL_OPTIONS} could not carry.
     *
     * @param path the path
     * @param what what it is, for the message
     * @throws IllegalArgumentException if it is relative or holds whitespace
     */
    static void requireNoWhitespace(Path path, String what) {
        Objects.requireNonNull(path, what);
        if (!path.isAbsolute() || path.toString().chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(
                    what
                            + " must be an absolute path without whitespace, because"
                            + " JAVA_TOOL_OPTIONS is split on whitespace: "
                            + path);
        }
    }
}
