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

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.cometgui.app.e2e.bridge.BridgeAgent;
import org.cometgui.app.testing.RealSearch;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.tools.api.ToolRunOutcome;
import org.cometgui.tools.api.ToolRunner;
import org.cometgui.tools.process.ProcessService;
import org.junit.jupiter.api.Assertions;

/**
 * The bridge's agent jar, built from the test classes this JVM is running by {@code
 * scripts/e2e/build-bridge.sh} -- so a Tier B run always attaches the bridge it was compiled with,
 * never a stale copy.
 *
 * <p>The three JUnit jars the driver's assertions need are found from the classes this JVM loaded
 * them from, not from {@code java.class.path}, which surefire may have reduced to one manifest-only
 * jar. The script runs through the process service, like every process here.
 */
public final class BridgeJar {

    /** The script, relative to the repository root. */
    static final String SCRIPT = "scripts/e2e/build-bridge.sh";

    private BridgeJar() {}

    /**
     * Builds the jar.
     *
     * @param jar where to write it: absolute, with no whitespace in the path ({@code
     *     JAVA_TOOL_OPTIONS} is split on whitespace)
     * @return the jar
     * @throws IOException if the script cannot be started
     * @throws AssertionError if the script failed or wrote nothing, with its output
     */
    public static Path build(Path jar) throws IOException {
        LaunchedCometGui.requireNoWhitespace(jar, "the bridge jar");
        Path root = RealSearch.repositoryRoot();
        String classPath =
                String.join(
                        File.pathSeparator,
                        locationOf(Assertions.class).toString(),
                        locationOf(named("org.opentest4j.AssertionFailedError")).toString(),
                        locationOf(named("org.apiguardian.api.API")).toString());
        Path directory = jar.getParent();
        if (directory == null) {
            throw new IllegalArgumentException("the bridge jar needs a directory: " + jar);
        }
        Files.createDirectories(directory);
        ToolRunOutcome outcome =
                new ToolRunner(new ProcessService(Clock.systemUTC()), Duration.ofMinutes(5))
                        .run(
                                new ToolCommand(
                                        List.of(
                                                "/bin/bash",
                                                root.resolve(SCRIPT).toString(),
                                                "--classes",
                                                locationOf(BridgeAgent.class).toString(),
                                                "--class-path",
                                                classPath,
                                                "--out",
                                                jar.toString()),
                                        root,
                                        Map.of("PATH", "/usr/bin:/bin")));
        if (!outcome.exitedZero()
                || !Files.isRegularFile(jar)
                || outcome.standardOutput().stream()
                        .noneMatch(line -> line.startsWith("build-bridge.sh: PASSED"))) {
            throw new AssertionError(
                    SCRIPT + " did not build " + jar + ": " + outcome.joinedOutput());
        }
        return jar;
    }

    /**
     * A class JUnit's own jars bring, by name: this module declares JUnit, not its two transitive
     * jars, so they are found rather than imported.
     *
     * @param name the class's binary name
     * @return the class
     */
    private static Class<?> named(String name) {
        try {
            return Class.forName(name, false, BridgeJar.class.getClassLoader());
        } catch (ClassNotFoundException missing) {
            throw new AssertionError(
                    name + " is not on the test class path; the bridge jar needs its jar", missing);
        }
    }

    /**
     * Where a class was loaded from: a jar, or a classes directory.
     *
     * @param type the class
     * @return the jar or directory
     */
    static Path locationOf(Class<?> type) {
        try {
            return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException impossible) {
            throw new IllegalStateException(type + " has no file location", impossible);
        }
    }
}
