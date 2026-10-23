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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.cometgui.app.e2e.scenario.CanonicalScenario;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.tools.api.ToolRunOutcome;
import org.cometgui.tools.api.ToolRunner;
import org.cometgui.tools.process.ProcessService;

/**
 * What a packaged CometGUI's PDV launch can be held to on a host without a system X11 client
 * library (Phase 14 E6), and how a test knows which host it is on.
 *
 * <p>The product gives PDV {@code DISPLAY} and {@code XAUTHORITY} and nothing else of its
 * environment ({@code PdvDisplay.fromHost}), so the JVM PDV runs on finds {@code libX11.so.6} only
 * in the system library directories. Where they hold none -- this project's build host -- PDV
 * cannot load {@code libawt_xawt.so} and draws nothing. That is a gap of the host, not of the
 * product: a Linux desktop has the library. A packaged application has no seam through which a test
 * could hand PDV the project closure's library path, and adding one would test a different product.
 */
final class PdvOnThisHost {

    /** Where a Linux x86-64 host's dynamic linker finds system libraries by default. */
    static final List<String> SYSTEM_LIBRARY_DIRECTORIES =
            List.of(
                    "/lib/x86_64-linux-gnu",
                    "/usr/lib/x86_64-linux-gnu",
                    "/lib64",
                    "/usr/lib64",
                    "/lib",
                    "/usr/lib");

    /** The host's dynamic-dependency lister. */
    static final String LDD = "/usr/bin/ldd";

    /** The X11 client library {@code libawt_xawt.so} links against. */
    static final String LIB_X11 = "libX11.so.6";

    private PdvOnThisHost() {}

    /**
     * The system X11 client library, if this host has one.
     *
     * @return its path, or empty -- as on this project's build host
     */
    static Optional<Path> systemLibX11() {
        for (String directory : SYSTEM_LIBRARY_DIRECTORIES) {
            Path library = Path.of(directory, LIB_X11);
            if (Files.exists(library)) {
                return Optional.of(library);
            }
        }
        return Optional.empty();
    }

    /**
     * The launch was the packaged product's own: the installed runtime's {@code bin/java} first in
     * the argument array, the installed PDV JAR on it, and exactly the environment the product
     * chooses for PDV on this desktop -- the application's own {@code DISPLAY}, and no library path
     * (the fresh home has no {@code .Xauthority} and the launcher sets no {@code XAUTHORITY}).
     *
     * @param launch the launch, as the run recorded it
     * @param installed the installation
     * @param application what the packaged application's JVM reported about its environment
     */
    static void assertLaunchedFromTheInstalledRuntime(
            CanonicalScenario.PdvLaunch launch,
            InstalledCometGui installed,
            Map<String, String> application) {
        assertAll(
                "step 25: PDV launched by the packaged application from its installed runtime: "
                        + launch,
                () ->
                        assertEquals(
                                installed.runtimeJava().toString(),
                                launch.argv().get(0),
                                "the Java launcher PDV ran on"),
                () ->
                        assertTrue(
                                launch.argv().contains(launch.jar().toString()),
                                "the installed PDV JAR is on its command"),
                () -> assertFalse(application.containsKey("XAUTHORITY"), "no XAUTHORITY is set"),
                () ->
                        assertEquals(
                                "DISPLAY="
                                        + application.get("DISPLAY")
                                        + "\nHOME="
                                        + Path.of(launch.workingDirectory()).resolve("home")
                                        + "\nLANG=C.UTF-8",
                                launch.environment(),
                                "the product's own choice of PDV's environment: the"
                                        + " application's DISPLAY, a run-owned HOME (P11-17),"
                                        + " LANG -- and no library path"));
    }

    /**
     * On a host with no system {@code libX11.so.6}: PDV drew nothing, the screen says so, and the
     * failure is the one that missing library causes and no other. PDV's output is not kept on
     * disk; the product shows its last lines, which end in AWT's native-library loading ({@code
     * java.awt.Toolkit.loadLibraries}). The library's absence is then proved independently: the
     * host's {@code ldd}, run on the installed runtime's {@code libawt_xawt.so} in exactly PDV's
     * environment, reports {@code libX11.so.6 => not found}.
     *
     * @param launch the launch
     * @param installed the installation
     * @return what {@code ldd} reported as not found, for the report
     * @throws IOException if {@code ldd} cannot be started
     */
    static List<String> assertFailedForWantOfLibX11Only(
            CanonicalScenario.PdvLaunch launch, InstalledCometGui installed) throws IOException {
        Path awt = installed.runtime().resolve("lib").resolve("libawt_xawt.so");
        Map<String, String> environment = new LinkedHashMap<>();
        for (String line : launch.environment().split("\n", -1)) {
            int equals = line.indexOf('=');
            environment.put(line.substring(0, equals), line.substring(equals + 1));
        }
        ToolRunOutcome ldd =
                new ToolRunner(new ProcessService(Clock.systemUTC()), Duration.ofMinutes(1))
                        .run(
                                new ToolCommand(
                                        List.of(LDD, awt.toString()),
                                        Path.of(launch.workingDirectory()),
                                        environment));
        List<String> missing =
                ldd.standardOutput().stream()
                        .map(String::strip)
                        .filter(line -> line.endsWith("=> not found"))
                        .toList();
        String outcome = launch.outcome();
        assertAll(
                "step 25 on a host with no system "
                        + LIB_X11
                        + " (E6): PDV drew nothing, and for that reason alone: "
                        + outcome,
                () ->
                        assertTrue(
                                outcome.startsWith(
                                        "Nothing was drawn: PDV exited with status 1 and drew no"
                                                + " figure for "),
                                "nothing was drawn"),
                () ->
                        assertTrue(
                                outcome.contains("java.desktop/java.awt.Toolkit.loadLibraries"),
                                "PDV failed loading AWT's native libraries"),
                () -> assertTrue(Files.isRegularFile(awt), () -> awt + " is in the runtime"),
                () -> assertTrue(ldd.exitedZero(), () -> "ldd: " + ldd.joinedOutput()),
                () ->
                        assertTrue(
                                missing.contains(LIB_X11 + " => not found"),
                                () ->
                                        "ldd, in PDV's environment, finds no "
                                                + LIB_X11
                                                + " for "
                                                + awt
                                                + ": "
                                                + ldd.joinedOutput()));
        return missing;
    }
}
