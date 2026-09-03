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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolCapability;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The manifest's {@code gatesCapability} rule, as a value: what has to be beside the executable.
 */
class CompanionGateTest {

    private static final HostPlatform WINDOWS =
            new HostPlatform(HostOperatingSystem.WINDOWS, HostArchitecture.X86_64);
    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    private static final Set<String> THREE = Set.of("one.dll", "two.dll", "three.dll");

    private static CompanionGate gate() {
        return new CompanionGate(
                ToolCapability.THERMO_RAW_WINDOWS, HostOperatingSystem.WINDOWS, THREE);
    }

    private static Path executableWith(Path directory, String... present) throws IOException {
        Path bin = Files.createDirectories(directory.resolve("bin"));
        for (String name : present) {
            Files.writeString(bin.resolve(name), "bytes");
        }
        return Files.writeString(bin.resolve("comet.exe"), "MZ");
    }

    @Test
    @DisplayName("with every companion beside it, on the gate's own platform, the gate is open")
    void openWithEverythingPresent(@TempDir Path directory) throws IOException {
        Path executable = executableWith(directory, "one.dll", "two.dll", "three.dll");

        assertTrue(gate().isOpenFor(WINDOWS, executable));
    }

    @ParameterizedTest(name = "[{index}] missing {0}")
    @ValueSource(strings = {"one.dll", "two.dll", "three.dll"})
    @DisplayName("each companion is load-bearing on its own: leave any one out and the gate shuts")
    void everyCompanionIsRequired(String missing, @TempDir Path directory) throws IOException {
        String[] present =
                THREE.stream().filter(name -> !name.equals(missing)).toArray(String[]::new);
        Path executable = executableWith(directory, present);

        assertFalse(
                gate().isOpenFor(WINDOWS, executable),
                "R-TOOL-02 says an install missing them shall not advertise the capability, and"
                        + " \"them\" is all three");
    }

    @Test
    @DisplayName("with none of them the gate is shut, which is the half that makes the rule a rule")
    void shutWithNothingPresent(@TempDir Path directory) throws IOException {
        Path executable = executableWith(directory);

        assertFalse(gate().isOpenFor(WINDOWS, executable));
    }

    @Test
    @DisplayName("on another operating system the gate stays shut however many files are there")
    void shutOnAnotherOperatingSystem(@TempDir Path directory) throws IOException {
        Path executable = executableWith(directory, "one.dll", "two.dll", "three.dll");

        assertFalse(
                gate().isOpenFor(LINUX, executable),
                "the three libraries gate a WINDOWS capability; a Linux Comet with three files of"
                        + " those names beside it still cannot read a Thermo RAW file");
    }

    @Test
    @DisplayName("a directory of the right name is not a companion file")
    void aDirectoryIsNotAFile(@TempDir Path directory) throws IOException {
        Path executable = executableWith(directory, "one.dll", "two.dll");
        Files.createDirectory(executable.getParent().resolve("three.dll"));

        assertFalse(gate().isOpenFor(WINDOWS, executable));
    }

    @Test
    @DisplayName(
            "the companions are looked for beside the executable, not in the current directory")
    void companionsAreBesideTheExecutable(@TempDir Path directory) throws IOException {
        Path executable = executableWith(directory);
        for (String name : THREE) {
            Files.writeString(directory.resolve(name), "bytes");
        }

        assertFalse(
                gate().isOpenFor(WINDOWS, executable),
                "the files are one directory up from the executable, where Windows would not look"
                        + " for them either");
    }

    @Test
    @DisplayName("an executable with no directory above it cannot have companions beside it")
    void anExecutableWithNoParent() {
        assertFalse(
                gate().isOpenFor(WINDOWS, Path.of("/")),
                "the file system root has no parent directory, so there is nowhere for a companion"
                        + " to be");
    }

    @Test
    @DisplayName("a gate with no names would open for everyone, so it is rejected")
    void aGateWithNoNames() {
        assertEquals(
                "a companion gate with no file names would grant its capability to every install,"
                        + " which is the opposite of what R-TOOL-02 asks for",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new CompanionGate(
                                                ToolCapability.THERMO_RAW_WINDOWS,
                                                HostOperatingSystem.WINDOWS,
                                                Set.of()))
                        .getMessage());
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(
            strings = {
                "lib/one.dll",
                "..\\one.dll",
                "/usr/lib/one.dll",
                "/one.dll",
                "\\one.dll",
                "one.dll/",
                "one.dll\\"
            })
    @DisplayName("a path is not a name: a gate is about what sits beside the executable")
    void aPathIsNotAName(String path) {
        assertEquals(
                "a companion file name must be a name beside the executable, not a path, but was:"
                        + " \""
                        + path
                        + "\"",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new CompanionGate(
                                                ToolCapability.THERMO_RAW_WINDOWS,
                                                HostOperatingSystem.WINDOWS,
                                                Set.of(path)))
                        .getMessage());
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"", " ", "\t"})
    @DisplayName("a blank name is rejected, quoting what was rejected")
    void aBlankName(String blank) {
        assertEquals(
                "a companion file name must not be blank, but was: \"" + blank + "\"",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new CompanionGate(
                                                ToolCapability.THERMO_RAW_WINDOWS,
                                                HostOperatingSystem.WINDOWS,
                                                Set.of(blank)))
                        .getMessage());
    }

    @Test
    @DisplayName("every component is required, and the names are copied")
    void everyComponentIsRequired(@TempDir Path directory) throws IOException {
        Path executable = executableWith(directory);

        assertAll(
                () ->
                        assertEquals(
                                "capability",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new CompanionGate(
                                                                null,
                                                                HostOperatingSystem.WINDOWS,
                                                                THREE))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "operatingSystem",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new CompanionGate(
                                                                ToolCapability.THERMO_RAW_WINDOWS,
                                                                null,
                                                                THREE))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "fileNames",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new CompanionGate(
                                                                ToolCapability.THERMO_RAW_WINDOWS,
                                                                HostOperatingSystem.WINDOWS,
                                                                null))
                                        .getMessage()),
                () ->
                        assertThrows(
                                UnsupportedOperationException.class,
                                () -> gate().fileNames().add("four.dll")),
                () ->
                        assertEquals(
                                "host",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> gate().isOpenFor(null, executable))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "executable",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> gate().isOpenFor(WINDOWS, null))
                                        .getMessage()));
    }
}
