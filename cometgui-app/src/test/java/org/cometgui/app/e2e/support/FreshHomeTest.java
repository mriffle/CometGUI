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

package org.cometgui.app.e2e.support;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.cometgui.app.config.ApplicationServices;
import org.cometgui.domain.ports.EnvironmentReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * A fresh home (P14-4) is brand new and empty of tools; it reports itself through the product's
 * environment seam and delegates everything else; a production composition root built over it puts
 * the application data under it; and the "no tool is present" check sees each kind of tool file,
 * proved by putting one there.
 */
class FreshHomeTest {

    @TempDir private Path parent;

    /** A reader that knows only what it is given, standing in for the real environment. */
    private static EnvironmentReader known(
            Map<String, String> variables, Map<String, String> properties) {
        return new EnvironmentReader() {
            @Override
            public Optional<String> environmentVariable(String name) {
                return Optional.ofNullable(variables.get(name));
            }

            @Override
            public Optional<String> systemProperty(String name) {
                return Optional.ofNullable(properties.get(name));
            }
        };
    }

    @Test
    @DisplayName("a plain fresh home holds exactly its four empty directories, and no tool")
    void aPlainHomeIsEmpty() throws IOException {
        FreshHome fresh = FreshHome.create(parent, FreshHome.Naming.PLAIN);
        Path root = parent.toAbsolutePath().normalize().resolve("cometgui-e2e-home");
        assertAll(
                () -> assertEquals(root, fresh.root()),
                () -> assertEquals(root.resolve("home"), fresh.home()),
                () -> assertEquals(root.resolve("home/.local/share"), fresh.dataHome()),
                () -> assertEquals(root.resolve("home/AppData/Roaming"), fresh.appData()),
                () ->
                        assertEquals(
                                List.of(".local", ".local/share", "AppData", "AppData/Roaming"),
                                fresh.assertNoToolPresent().stream().sorted().toList()),
                () -> assertTrue(Files.notExists(fresh.applicationDataDirectory())));
    }

    @Test
    @DisplayName("a home that already exists is not fresh: creating it twice fails")
    void aHomeIsCreatedOnlyOnce() throws IOException {
        FreshHome.create(parent, FreshHome.Naming.PLAIN);
        assertThrows(
                FileAlreadyExistsException.class,
                () -> FreshHome.create(parent, FreshHome.Naming.PLAIN));
    }

    @Test
    @DisplayName(
            "the non-ASCII home has a space and an accented letter where the JVM can say so, and"
                    + " otherwise fails with the locale diagnostic, creating nothing")
    void theNonAsciiHomeNeedsAUtf8Jvm() throws IOException {
        if (RunnerLocale.ofThisJvm().isUtf8()) {
            FreshHome fresh = FreshHome.create(parent, FreshHome.Naming.SPACE_AND_NON_ASCII);
            assertEquals("protéomique run", fresh.root().getFileName().toString());
            assertTrue(Files.isDirectory(fresh.dataHome()));
            assertEquals(4, fresh.assertNoToolPresent().size());
        } else {
            AssertionError failed =
                    assertThrows(
                            AssertionError.class,
                            () -> FreshHome.create(parent, FreshHome.Naming.SPACE_AND_NON_ASCII));
            assertTrue(
                    failed.getMessage()
                            .startsWith("The end-to-end harness needs a UTF-8 runner, and this"),
                    failed::getMessage);
            try (Stream<Path> left = Files.list(parent)) {
                assertEquals(List.of(), left.toList(), "nothing was created");
            }
        }
    }

    @Test
    @DisplayName(
            "its environment reports user.home, HOME, XDG_DATA_HOME and APPDATA as the fresh home"
                    + " and asks the real one everything else")
    void itsEnvironmentReportsTheHomeAndDelegates() throws IOException {
        FreshHome fresh = FreshHome.create(parent, FreshHome.Naming.PLAIN);
        EnvironmentReader real =
                known(
                        Map.of(
                                "HOME", "/home/someone",
                                "XDG_DATA_HOME", "/opt/fonts/usr/share",
                                "APPDATA", "C:\\Users\\someone",
                                "PATH", "/usr/bin:/bin"),
                        Map.of("user.home", "/home/someone", "os.name", "Linux"));
        EnvironmentReader seen = fresh.environment(real);
        assertAll(
                () ->
                        assertEquals(
                                Optional.of(fresh.home().toString()),
                                seen.systemProperty("user.home")),
                () ->
                        assertEquals(
                                Optional.of(fresh.home().toString()),
                                seen.environmentVariable("HOME")),
                () ->
                        assertEquals(
                                Optional.of(fresh.dataHome().toString()),
                                seen.environmentVariable("XDG_DATA_HOME")),
                () ->
                        assertEquals(
                                Optional.of(fresh.appData().toString()),
                                seen.environmentVariable("APPDATA")),
                () -> assertEquals(Optional.of("/usr/bin:/bin"), seen.environmentVariable("PATH")),
                () -> assertEquals(Optional.of("Linux"), seen.osName()),
                () -> assertEquals(Optional.empty(), seen.environmentVariable("UNSET_HERE")));
    }

    @Test
    @DisplayName(
            "desktop variables are reported in place of the real ones, never in place of the"
                    + " home's own, and a production composition root reads them")
    void desktopVariablesAreReported() throws IOException {
        FreshHome fresh = FreshHome.create(parent, FreshHome.Naming.PLAIN);
        EnvironmentReader real =
                known(
                        Map.of("DISPLAY", ":0", "PATH", "/usr/bin:/bin"),
                        Map.of("user.home", "/home/someone"));
        EnvironmentReader seen = fresh.environment(real, Map.of("DISPLAY", ":97"));
        ApplicationServices services = fresh.services(Map.of("DISPLAY", ":98"));
        assertAll(
                () -> assertEquals(Optional.of(":97"), seen.environmentVariable("DISPLAY")),
                () -> assertEquals(Optional.of("/usr/bin:/bin"), seen.environmentVariable("PATH")),
                () ->
                        assertEquals(
                                Optional.of(fresh.home().toString()),
                                seen.environmentVariable("HOME")),
                () -> assertEquals(Optional.empty(), seen.environmentVariable("XAUTHORITY")),
                () ->
                        assertEquals(
                                Optional.of(":98"),
                                services.environment().environmentVariable("DISPLAY")),
                () ->
                        assertEquals(
                                Optional.of(":0"),
                                fresh.environment(real).environmentVariable("DISPLAY"),
                                "without desktop variables, the real one"));
        for (String own : List.of("HOME", "XDG_DATA_HOME", "APPDATA")) {
            IllegalArgumentException refused =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> fresh.environment(real, Map.of(own, "/elsewhere")));
            assertEquals(
                    own + " is the fresh home's own, not a desktop variable", refused.getMessage());
        }
        assertEquals(
                "DISPLAY is blank",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> fresh.environment(real, Map.of("DISPLAY", " ")))
                        .getMessage());
    }

    @Test
    @EnabledOnOs(
            value = OS.LINUX,
            disabledReason = "the application data directory's name is typed out for Linux here")
    @DisplayName(
            "a production composition root over it puts the application data under the fresh home,"
                    + " not under the test JVM's own XDG_DATA_HOME")
    void theProductionServicesUseIt() throws IOException {
        FreshHome fresh = FreshHome.create(parent, FreshHome.Naming.PLAIN);
        ApplicationServices services = fresh.services();
        Path expected = fresh.home().resolve(".local/share/cometgui");
        assertAll(
                () -> assertEquals(expected, fresh.applicationDataDirectory()),
                () -> assertEquals(expected, services.fileSystem().applicationDataDirectory()),
                () ->
                        assertEquals(
                                Optional.of(fresh.home().toString()),
                                services.environment().systemProperty("user.home")),
                () ->
                        assertTrue(
                                services.processRunner().isPresent(),
                                "the production process service is kept"),
                () ->
                        assertEquals(
                                ApplicationServices.forThisHost().environment().osName(),
                                services.environment().osName()));
    }

    @Test
    @DisplayName("each kind of tool file, put under the home, is found and named")
    void everyToolFileIsFound() throws IOException {
        List<String> tools =
                List.of(
                        "Downloads/comet.linux.exe",
                        "comet.exe",
                        "bin/comet",
                        "bin/percolator",
                        "x/percolator-noxml-ubuntu-portable.zip",
                        "x/PDV-2.7.0.zip",
                        "x/PDV.jar",
                        "x/cometPercolator2LimelightXML.jar");
        for (String tool : tools) {
            Path base = Files.createDirectories(parent.resolve("for-" + tool.replace('/', '-')));
            FreshHome fresh = FreshHome.create(base, FreshHome.Naming.PLAIN);
            Path file = fresh.home().resolve(tool);
            Files.createDirectories(Objects.requireNonNull(file.getParent(), "parent"));
            Files.write(file, new byte[] {1});
            AssertionError failed = assertThrows(AssertionError.class, fresh::assertNoToolPresent);
            assertTrue(
                    failed.getMessage().contains(file + " (named like a tool's file)"),
                    () -> tool + ": " + failed.getMessage());
        }
    }

    @Test
    @DisplayName(
            "an installed tool directory and a download in the cache are found, whatever their"
                    + " names")
    void theCacheIsWalked() throws IOException {
        FreshHome installed =
                FreshHome.create(
                        Files.createDirectories(parent.resolve("a")), FreshHome.Naming.PLAIN);
        Files.createDirectories(installed.applicationDataDirectory().resolve("tools/pdv/2.7.0"));
        AssertionError tools = assertThrows(AssertionError.class, installed::assertNoToolPresent);
        assertTrue(
                tools.getMessage()
                        .contains(
                                installed.applicationDataDirectory().resolve("tools/pdv")
                                        + " (an entry of "
                                        + installed.applicationDataDirectory().resolve("tools")
                                        + ")"),
                tools::getMessage);

        FreshHome downloading =
                FreshHome.create(
                        Files.createDirectories(parent.resolve("b")), FreshHome.Naming.PLAIN);
        Path partial =
                downloading.applicationDataDirectory().resolve("cache/downloads/0a1b2c/part");
        Files.createDirectories(Objects.requireNonNull(partial.getParent(), "parent"));
        Files.write(partial, new byte[] {1});
        AssertionError cache = assertThrows(AssertionError.class, downloading::assertNoToolPresent);
        assertTrue(
                cache.getMessage().contains("cache/downloads/0a1b2c (an entry of"),
                cache::getMessage);
    }

    @Test
    @DisplayName(
            "ordinary files -- notes, a comet.params, an empty tools directory -- are not tools")
    void ordinaryFilesAreNotTools() throws IOException {
        FreshHome fresh = FreshHome.create(parent, FreshHome.Naming.PLAIN);
        Files.writeString(
                fresh.home().resolve("notes.txt"), "comet and percolator", StandardCharsets.UTF_8);
        Files.writeString(
                fresh.home().resolve("comet.params"), "decoy_search = 1\n", StandardCharsets.UTF_8);
        Files.createDirectories(fresh.applicationDataDirectory().resolve("tools"));
        List<String> walked = fresh.assertNoToolPresent();
        assertTrue(
                walked.contains("notes.txt") && walked.contains("comet.params"), walked::toString);
    }
}
