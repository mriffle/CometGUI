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
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.cometgui.app.testing.FakeEnvironment;
import org.cometgui.app.testing.Nulls;
import org.cometgui.domain.ports.EnvironmentReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The real filesystem access: the four questions against a real temporary directory, and the
 * application data directory against a chosen environment.
 *
 * <p><strong>Why the paths come from a temporary directory rather than from literals.</strong> Two
 * reasons, and the second is the interesting one. A hard-coded absolute path in a test is a
 * SpotBugs finding ({@code DMI_HARDCODED_ABSOLUTE_FILENAME}) that this project fixes rather than
 * excludes. And an absolute path that this filesystem can really represent is what the code under
 * test actually receives on the platform it runs on, so the expectations below are built the same
 * way the implementation builds them -- by resolving segments, never by pasting a string.
 *
 * <p><strong>A note on the Windows and macOS cases.</strong> They run on this Linux JVM, so the
 * paths are POSIX-shaped: {@code Path.of("C:\\Users\\x")} on Linux is one relative segment, not an
 * absolute Windows path, and asserting Windows path <em>syntax</em> here would be asserting
 * something this JVM cannot produce. What is asserted is the part genuinely under test -- which
 * environment value is consulted, in which order, and what directory name is appended. The syntax
 * itself is {@code java.nio.file}'s problem, and it is right on the platform it runs on.
 */
class PlatformFileSystemAccessTest {

    @TempDir private Path sandbox;

    /** The home directory of the imaginary user every case below runs as. */
    private Path home() {
        return sandbox.resolve("home").resolve("tester");
    }

    private FakeEnvironment environment(String osName) {
        return FakeEnvironment.linux64()
                .withProperty(EnvironmentReader.OS_NAME_PROPERTY, osName)
                .withProperty(PlatformFileSystemAccess.USER_HOME_PROPERTY, home().toString());
    }

    private static Path dataDirectory(EnvironmentReader environment) {
        return new PlatformFileSystemAccess(environment).applicationDataDirectory();
    }

    @Test
    @DisplayName("Windows uses APPDATA when it is an absolute path")
    void windowsPrefersAppData() {
        Path roaming = sandbox.resolve("roaming");

        Path data =
                dataDirectory(
                        environment("Windows 11")
                                .withVariable(
                                        PlatformFileSystemAccess.APPDATA_VARIABLE,
                                        roaming.toString()));

        assertEquals(roaming.resolve("CometGUI"), data);
    }

    @Test
    @DisplayName("Windows falls back to the home directory when APPDATA is unusable")
    void windowsFallsBackToHome() {
        String appData = PlatformFileSystemAccess.APPDATA_VARIABLE;
        Path expected = home().resolve("AppData").resolve("Roaming").resolve("CometGUI");

        assertAll(
                () -> assertEquals(expected, dataDirectory(environment("Windows 10")), "unset"),
                () ->
                        assertEquals(
                                expected,
                                dataDirectory(
                                        environment("Windows 10").withVariable(appData, "   ")),
                                "blank"),
                () ->
                        assertEquals(
                                expected,
                                dataDirectory(
                                        environment("Windows 10")
                                                .withVariable(appData, "roaming/data")),
                                "relative"),
                () ->
                        assertEquals(
                                expected,
                                dataDirectory(
                                        environment("Windows 10")
                                                .withVariable(appData, "/roaming\u0000data")),
                                "a value holding a NUL byte is not a path this filesystem can"
                                        + " represent, and must be ignored rather than thrown"));
    }

    @Test
    @DisplayName("macOS uses Library/Application Support, and Darwin is not mistaken for Windows")
    void macOsUsesApplicationSupport() {
        Path expected =
                home().resolve("Library").resolve("Application Support").resolve("CometGUI");

        assertAll(
                () -> assertEquals(expected, dataDirectory(environment("Mac OS X"))),
                () ->
                        assertEquals(
                                expected,
                                dataDirectory(environment("Darwin")),
                                "\"darwin\" contains \"win\": a contains-based Windows test sends"
                                        + " this host to the wrong branch, and did"));
    }

    @Test
    @DisplayName("Linux uses XDG_DATA_HOME when it is an absolute path, and lower-cases the name")
    void linuxPrefersXdgDataHome() {
        Path share = sandbox.resolve("share");

        Path data =
                dataDirectory(
                        environment("Linux")
                                .withVariable(
                                        PlatformFileSystemAccess.XDG_DATA_HOME_VARIABLE,
                                        share.toString()));

        assertEquals(share.resolve("cometgui"), data);
    }

    @Test
    @DisplayName(
            "Linux falls back to ~/.local/share, and so does an operating system we cannot name")
    void linuxFallsBackToLocalShare() {
        Path expected = home().resolve(".local").resolve("share").resolve("cometgui");
        String xdg = PlatformFileSystemAccess.XDG_DATA_HOME_VARIABLE;

        assertAll(
                () -> assertEquals(expected, dataDirectory(environment("Linux"))),
                () ->
                        assertEquals(
                                expected,
                                dataDirectory(
                                        environment("Linux").withVariable(xdg, "relative/share")),
                                "a relative XDG_DATA_HOME is invalid per the XDG specification"),
                () ->
                        assertEquals(
                                expected,
                                dataDirectory(
                                        environment("Linux")
                                                .withoutProperty(
                                                        EnvironmentReader.OS_NAME_PROPERTY)),
                                "an unknown operating system gets the XDG layout"),
                () ->
                        assertEquals(
                                expected,
                                dataDirectory(environment("SunOS")),
                                "so does one this project does not support"));
    }

    @Test
    @DisplayName("a missing user.home is a loud failure naming the property, not a silent guess")
    void missingHomeIsReported() {
        FakeEnvironment noHome =
                environment("Linux").withoutProperty(PlatformFileSystemAccess.USER_HOME_PROPERTY);

        IllegalStateException thrown =
                assertThrows(IllegalStateException.class, () -> dataDirectory(noHome));

        assertEquals(
                "cannot locate the CometGUI application data directory: the system property"
                        + " user.home is not set",
                thrown.getMessage());
    }

    @Test
    @DisplayName("nothing is created: the data directory is computed, never made")
    void createsNothing() {
        PlatformFileSystemAccess files = new PlatformFileSystemAccess(environment("Linux"));

        Path data = files.applicationDataDirectory();

        assertAll(
                () -> assertEquals(home().resolve(".local/share/cometgui"), data),
                () -> assertFalse(Files.exists(data), data + " was created as a side effect"),
                () ->
                        assertFalse(
                                Files.exists(home()),
                                "not even the home directory may be created"));
    }

    @Test
    @DisplayName("exists, isReadable and isDirectory answer for what is really on disk")
    void answersAboutRealFiles() throws IOException {
        PlatformFileSystemAccess files = new PlatformFileSystemAccess(environment("Linux"));
        Path directory = Files.createDirectory(sandbox.resolve("a-directory"));
        Path file = Files.writeString(sandbox.resolve("a-file"), "comet");
        Path absent = sandbox.resolve("nothing-here");

        assertAll(
                () -> assertTrue(files.exists(directory)),
                () -> assertTrue(files.isDirectory(directory)),
                () -> assertTrue(files.isReadable(directory)),
                () -> assertTrue(files.exists(file)),
                () -> assertFalse(files.isDirectory(file), "a regular file is not a directory"),
                () -> assertTrue(files.isReadable(file)),
                () -> assertFalse(files.exists(absent)),
                () -> assertFalse(files.isDirectory(absent)),
                () -> assertFalse(files.isReadable(absent)));
    }

    @Test
    @DisplayName("createDirectories makes every missing parent, and is idempotent")
    void createsDirectories() throws IOException {
        PlatformFileSystemAccess files = new PlatformFileSystemAccess(environment("Linux"));
        Path nested = sandbox.resolve("one").resolve("two").resolve("three");

        files.createDirectories(nested);
        files.createDirectories(nested);

        assertAll(
                () -> assertTrue(Files.isDirectory(nested), nested + " was not created"),
                () -> assertTrue(Files.isDirectory(sandbox.resolve("one"))),
                () -> assertTrue(Files.isDirectory(sandbox.resolve("one").resolve("two"))));
    }

    @Test
    @DisplayName("createDirectories over an existing regular file fails rather than pretending")
    void createDirectoriesOverAFileFails() throws IOException {
        PlatformFileSystemAccess files = new PlatformFileSystemAccess(environment("Linux"));
        Path file = Files.writeString(sandbox.resolve("in-the-way"), "comet");

        assertThrows(FileAlreadyExistsException.class, () -> files.createDirectories(file));
    }

    @Test
    @DisplayName("null arguments are rejected, naming the argument")
    void rejectsNulls() {
        PlatformFileSystemAccess files = new PlatformFileSystemAccess(environment("Linux"));
        Path noPath = Nulls.of(Path.class);

        assertAll(
                () ->
                        assertEquals(
                                "environment",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new PlatformFileSystemAccess(
                                                                Nulls.of(EnvironmentReader.class)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "path",
                                assertThrows(NullPointerException.class, () -> files.exists(noPath))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "path",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> files.isReadable(noPath))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "path",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> files.isDirectory(noPath))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "path",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> files.createDirectories(noPath))
                                        .getMessage()));
    }
}
