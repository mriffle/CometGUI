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

package org.cometgui.tools.testing;

import java.io.File;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.tools.api.ToolRunOutcome;
import org.cometgui.tools.api.ToolRunner;
import org.cometgui.tools.process.ProcessService;

/**
 * The project-local virtual display PDV needs, started and stopped by a test (design decision
 * P11-5).
 *
 * <p>PDV builds a Swing frame before it reads its first argument, so it cannot run headless. This
 * starts the Xvfb under {@code tools/x11-bookworm-20260829/} exactly as {@code
 * scripts/feasibility/pdv_spike.sh} and {@code scripts/spikes/pdv_mztab_spike.py} do -- never the
 * host's X, of which there is none -- including the {@code xkbcomp} path shim, compiled from {@code
 * scripts/feasibility/gui-spike/xkbcomp-path-shim.c} with the host's {@code gcc} into this module's
 * build directory, because Xvfb runs {@code /usr/bin/xkbcomp} by a path baked in at build time.
 *
 * <p><strong>Both processes go through the process service</strong>, like every process in the
 * product. The display number is chosen by Xvfb itself ({@code -displayfd 1}) and read from its
 * standard output, so two test JVMs -- PIT runs several -- can never pick the same one, and the
 * wait for it is a latch with a deadline, never a sleep.
 *
 * <p>Anything missing <strong>fails</strong> the test, naming what and how to get it back. A real
 * PDV test that skipped without a display would be a check that cannot go red.
 */
public final class ProjectXvfb implements AutoCloseable {

    /** The X11 closure, relative to the repository root. */
    public static final String X11_ROOT = "tools/x11-bookworm-20260829/root";

    /** The font closure, relative to the repository root. */
    public static final String FONT_ROOT = "tools/fontstack-bookworm-20260829/root";

    /** The shim's source, relative to the repository root. */
    public static final String SHIM_SOURCE = "scripts/feasibility/gui-spike/xkbcomp-path-shim.c";

    private static final Duration START_DEADLINE = Duration.ofSeconds(30);
    private static final Duration STOP_DEADLINE = Duration.ofSeconds(15);
    private static final String REBUILD =
            " The X11 and font closures are project-local and gitignored; rebuild them with bash"
                    + " scripts/feasibility/javafx-smoke.sh then bash"
                    + " scripts/feasibility/javafx-headed-xvfb.sh. Real-PDV tests fail rather than"
                    + " skip without them.";

    private final RunningProcess server;
    private final CountDownLatch exited;
    private final String display;
    private final Map<String, String> clientEnvironment;

    private ProjectXvfb(
            RunningProcess server,
            CountDownLatch exited,
            String display,
            Map<String, String> clientEnvironment) {
        this.server = server;
        this.exited = exited;
        this.display = display;
        this.clientEnvironment = clientEnvironment;
    }

    /**
     * Starts a virtual display.
     *
     * @param workDirectory a directory for the compiled shim, the font cache and the server log
     * @return the running display
     * @throws IOException if a file cannot be written or a process cannot be started
     */
    public static ProjectXvfb start(Path workDirectory) throws IOException {
        Path root = UpstreamArtefacts.repositoryRoot();
        Path x11 = root.resolve(X11_ROOT);
        Path fonts = root.resolve(FONT_ROOT);
        Path xvfb = x11.resolve("usr/bin/Xvfb");
        Path xkbcomp = x11.resolve("usr/bin/xkbcomp");
        Path xkb = x11.resolve("usr/share/X11/xkb");
        Path shimSource = root.resolve(SHIM_SOURCE);
        for (Path required : List.of(xvfb, xkbcomp, xkb, fonts.resolve("etc/fonts/fonts.conf"))) {
            if (!Files.exists(required)) {
                throw new AssertionError(
                        "the project-local display needs "
                                + required
                                + ", which is missing."
                                + REBUILD);
            }
        }
        if (!Files.isRegularFile(shimSource)) {
            throw new AssertionError(
                    "the xkbcomp path shim's source " + shimSource + " is missing from the tree");
        }
        Path work = Files.createDirectories(workDirectory);
        Path shim = compileShim(shimSource, work);
        Path cache = Files.createDirectories(work.resolve("fontconfig-cache"));

        Map<String, String> client = new LinkedHashMap<>();
        client.put(
                "LD_LIBRARY_PATH",
                String.join(
                        File.pathSeparator,
                        x11.resolve("usr/lib/x86_64-linux-gnu").toString(),
                        x11.resolve("lib/x86_64-linux-gnu").toString(),
                        fonts.resolve("usr/lib/x86_64-linux-gnu").toString()));
        client.put("FONTCONFIG_PATH", fonts.resolve("etc/fonts").toString());
        client.put("XDG_DATA_HOME", fonts.resolve("usr/share").toString());
        client.put("XDG_CACHE_HOME", cache.toString());

        Map<String, String> serverEnvironment = new LinkedHashMap<>(client);
        serverEnvironment.put("LD_PRELOAD", shim.toString());
        serverEnvironment.put("XKBCOMP_PATH", xkbcomp.toString());
        serverEnvironment.put("XKB_CONFIG_ROOT", xkb.toString());
        ToolCommand command =
                new ToolCommand(
                        List.of(
                                xvfb.toString(),
                                "-displayfd",
                                "1",
                                "-screen",
                                "0",
                                "1280x1024x24",
                                "-nolisten",
                                "tcp",
                                "-xkbdir",
                                xkb.toString()),
                        work,
                        serverEnvironment);
        AtomicReference<String> number = new AtomicReference<>();
        CountDownLatch announced = new CountDownLatch(1);
        CountDownLatch exited = new CountDownLatch(1);
        List<String> log = Collections.synchronizedList(new ArrayList<>());
        RunningProcess server =
                new ProcessService(Clock.systemUTC())
                        .start(
                                command,
                                new ProcessListener() {
                                    @Override
                                    public void onStandardOutput(String line) {
                                        if (line.strip().matches("[0-9]+")
                                                && number.compareAndSet(null, line.strip())) {
                                            announced.countDown();
                                        }
                                    }

                                    @Override
                                    public void onStandardError(String line) {
                                        log.add(line);
                                    }

                                    @Override
                                    public void onExit(int exitCode) {
                                        log.add("Xvfb exited with status " + exitCode);
                                        exited.countDown();
                                        announced.countDown();
                                    }
                                });
        if (!await(announced, START_DEADLINE) || number.get() == null) {
            server.requestCancellation();
            await(exited, STOP_DEADLINE);
            throw new AssertionError(
                    "the project-local Xvfb did not announce a display within "
                            + START_DEADLINE
                            + "; it said: "
                            + String.join(" | ", log)
                            + "."
                            + REBUILD);
        }
        String display = ":" + number.get();
        client.put("DISPLAY", display);
        return new ProjectXvfb(server, exited, display, Collections.unmodifiableMap(client));
    }

    private static Path compileShim(Path source, Path work) throws IOException {
        Path gcc = onPath("gcc");
        Path shim = work.resolve("xkbcomp-path-shim.so");
        ToolRunOutcome outcome =
                new ToolRunner(new ProcessService(Clock.systemUTC()), Duration.ofMinutes(2))
                        .run(
                                new ToolCommand(
                                        List.of(
                                                gcc.toString(),
                                                "-shared",
                                                "-fPIC",
                                                "-O1",
                                                "-o",
                                                shim.toString(),
                                                source.toString()),
                                        work,
                                        Map.of("PATH", System.getenv("PATH"))));
        if (!outcome.exitedZero() || !Files.isRegularFile(shim) || Files.size(shim) == 0) {
            throw new AssertionError(
                    "gcc did not build the xkbcomp path shim "
                            + shim
                            + ": "
                            + outcome.joinedOutput());
        }
        return shim;
    }

    private static Path onPath(String program) {
        String path = System.getenv("PATH");
        if (path != null) {
            for (String directory : path.split(File.pathSeparator)) {
                Path candidate = Path.of(directory, program);
                if (Files.isExecutable(candidate)) {
                    return candidate;
                }
            }
        }
        throw new AssertionError(
                "no "
                        + program
                        + " on PATH: the project-local Xvfb cannot start without the xkbcomp path"
                        + " shim it compiles, so the real-PDV tests cannot run. They fail rather"
                        + " than skip.");
    }

    private static boolean await(CountDownLatch latch, Duration deadline) {
        try {
            return latch.await(deadline.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * The display name, such as {@code :97}.
     *
     * @return the display
     */
    public String display() {
        return display;
    }

    /**
     * What a client of this display needs in its environment: {@code DISPLAY}, and the library,
     * fontconfig and cache locations of the project-local closures.
     *
     * @return the variables, unmodifiable
     */
    public Map<String, String> clientEnvironment() {
        return Map.copyOf(clientEnvironment);
    }

    /** Stops the server and waits, with a deadline, for it to exit. */
    @Override
    public void close() {
        server.requestCancellation();
        if (!await(exited, STOP_DEADLINE)) {
            throw new AssertionError(
                    "the project-local Xvfb on "
                            + display
                            + " did not exit within "
                            + STOP_DEADLINE);
        }
    }
}
