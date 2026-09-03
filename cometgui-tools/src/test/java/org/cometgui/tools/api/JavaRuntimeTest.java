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

package org.cometgui.tools.api;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Finding the Java launcher, and building the command that starts a JAR with it. */
class JavaRuntimeTest {

    private static Path fakeJavaHome(Path root, String launcherName) throws IOException {
        Path bin = Files.createDirectories(root.resolve("bin"));
        Files.writeString(bin.resolve(launcherName), "not really a launcher");
        return root;
    }

    @Test
    @DisplayName("only Windows names the launcher java.exe, and the name decides the path")
    void theLauncherIsNamedPerPlatform(@TempDir Path root) throws IOException {
        Path posixHome = fakeJavaHome(root.resolve("posix"), "java");
        Path windowsHome = fakeJavaHome(root.resolve("windows"), "java.exe");

        assertAll(
                () ->
                        assertEquals(
                                posixHome.resolve("bin").resolve("java"),
                                JavaRuntime.ofJavaHome(posixHome, HostOperatingSystem.LINUX)
                                        .launcher()),
                () ->
                        assertEquals(
                                posixHome.resolve("bin").resolve("java"),
                                JavaRuntime.ofJavaHome(posixHome, HostOperatingSystem.MACOS)
                                        .launcher()),
                () ->
                        assertEquals(
                                windowsHome.resolve("bin").resolve("java.exe"),
                                JavaRuntime.ofJavaHome(windowsHome, HostOperatingSystem.WINDOWS)
                                        .launcher()));
    }

    @Test
    @DisplayName("a runtime with no launcher is refused, naming the path and saying why it matters")
    void aRuntimeWithNoLauncher(@TempDir Path root) throws IOException {
        Path home = fakeJavaHome(root.resolve("posix"), "java");

        assertEquals(
                "no Java launcher at "
                        + home.resolve("bin").resolve("java.exe")
                        + ", so a JAR tool cannot be started: PDV and the Limelight converter are"
                        + " JARs, and a JAR is not an executable file on any platform",
                assertThrows(
                                IOException.class,
                                () -> JavaRuntime.ofJavaHome(home, HostOperatingSystem.WINDOWS))
                        .getMessage());
    }

    @Test
    @DisplayName("the command is java -jar <jar> then the arguments, run in the JAR's directory")
    void theJarCommand(@TempDir Path root) throws IOException {
        Path home = fakeJavaHome(root.resolve("posix"), "java");
        JavaRuntime runtime = JavaRuntime.ofJavaHome(home, HostOperatingSystem.LINUX);
        Path jarDirectory = Files.createDirectories(root.resolve("installed"));
        Path jar = Files.writeString(jarDirectory.resolve("tool.jar"), "PK");

        ToolCommand command = runtime.jarCommand(jar, List.of("--version"), Map.of("A", "b"));

        assertAll(
                () ->
                        assertEquals(
                                List.of(
                                        home.resolve("bin").resolve("java").toString(),
                                        "-jar",
                                        jar.toString(),
                                        "--version"),
                                command.argv()),
                () -> assertEquals(jarDirectory, command.workingDirectory()),
                () -> assertEquals(Map.of("A", "b"), command.environment()));
    }

    @Test
    @DisplayName("a relative JAR path is refused, because the working directory is derived from it")
    void aRelativeJarIsRefused(@TempDir Path root) throws IOException {
        JavaRuntime runtime =
                JavaRuntime.ofJavaHome(
                        fakeJavaHome(root.resolve("posix"), "java"), HostOperatingSystem.LINUX);

        assertEquals(
                "jar must be an absolute path, but was: tool.jar",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> runtime.jarCommand(Path.of("tool.jar"), List.of(), Map.of()))
                        .getMessage());
    }

    @Test
    @DisplayName("a JAR with no directory to run in is refused")
    void aJarWithNoDirectory(@TempDir Path root) throws IOException {
        JavaRuntime runtime =
                JavaRuntime.ofJavaHome(
                        fakeJavaHome(root.resolve("posix"), "java"), HostOperatingSystem.LINUX);
        Path missingDirectory = root.resolve("gone").resolve("tool.jar");

        assertEquals(
                "the JAR "
                        + missingDirectory
                        + " has no directory to run in, so it cannot be probed",
                assertThrows(
                                IOException.class,
                                () -> runtime.jarCommand(missingDirectory, List.of(), Map.of()))
                        .getMessage());
    }

    @Test
    @DisplayName("a relative launcher is refused: an argument array is not resolved against a PATH")
    void theLauncherMustBeAbsolute() {
        assertAll(
                () ->
                        assertEquals(
                                "the java launcher path must be absolute, but was: java",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> new JavaRuntime(Path.of("java")))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "launcher",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> new JavaRuntime(null))
                                        .getMessage()));
    }

    @Test
    @DisplayName("this application's own runtime is found, and it is a file that exists")
    void thisApplicationsRuntime() throws IOException {
        JavaRuntime runtime = JavaRuntime.ofThisApplication();

        assertAll(
                () -> assertTrue(Files.isRegularFile(runtime.launcher()), runtime.toString()),
                () ->
                        assertTrue(
                                runtime.launcher()
                                        .startsWith(Path.of(System.getProperty("java.home"))),
                                "the launcher must come from java.home, which inside a packaged"
                                        + " CometGUI is the bundled runtime and not whatever java"
                                        + " happens to be on the PATH: "
                                        + runtime.launcher()));
    }
}
