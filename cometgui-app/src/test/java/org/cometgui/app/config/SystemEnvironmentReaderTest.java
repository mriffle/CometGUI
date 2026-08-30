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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.cometgui.app.testing.Nulls;
import org.cometgui.domain.ports.EnvironmentReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The one reader that touches the real process environment.
 *
 * <p>Asserting a real value here is harder than it looks, because the obvious assertion -- {@code
 * reader.environmentVariable("PATH")} equals {@code System.getenv("PATH")} -- proves nothing: it is
 * the implementation compared with itself. So the values asserted below are ones this build
 * <em>sets from outside the JVM</em> and this test can compute independently: cometgui-app/pom.xml
 * puts {@code FONTCONFIG_PATH}, {@code XDG_DATA_HOME} and {@code LD_LIBRARY_PATH} into the forked
 * JVM's environment, all derived from the {@code cometgui.fontstackRoot} system property it also
 * sets. Reading one of them back and rebuilding the expected value from the other is a genuine
 * round trip through the process environment.
 */
class SystemEnvironmentReaderTest {

    private final SystemEnvironmentReader reader = new SystemEnvironmentReader();

    /** The font-stack root surefire passes as a system property, used to predict the variables. */
    private String fontStackRoot() {
        Optional<String> root = reader.systemProperty("cometgui.fontstackRoot");
        assertTrue(
                root.isPresent(),
                "cometgui.fontstackRoot is not set; surefire in cometgui-app/pom.xml must pass it");
        return root.orElseThrow();
    }

    @Test
    @DisplayName("an environment variable set by the build is read back with its exact value")
    void readsAnEnvironmentVariableSetByTheBuild() {
        String root = fontStackRoot();

        assertAll(
                () ->
                        assertEquals(
                                Optional.of(root + "/etc/fonts"),
                                reader.environmentVariable("FONTCONFIG_PATH"),
                                "FONTCONFIG_PATH must be the font stack's fonts directory"),
                () ->
                        assertEquals(
                                Optional.of(root + "/usr/share"),
                                reader.environmentVariable("XDG_DATA_HOME"),
                                "XDG_DATA_HOME must be the font stack's share directory"),
                () ->
                        assertEquals(
                                Optional.of(root + "/usr/lib/x86_64-linux-gnu"),
                                reader.environmentVariable("LD_LIBRARY_PATH"),
                                "LD_LIBRARY_PATH must be the font stack's library directory"));
    }

    @Test
    @DisplayName("the value read back names a directory that is really on disk")
    void theValueNamesSomethingReal() {
        Path fonts = Path.of(reader.environmentVariable("FONTCONFIG_PATH").orElseThrow());

        assertTrue(
                Files.isDirectory(fonts),
                "FONTCONFIG_PATH was read as " + fonts + ", which is not a directory");
        assertTrue(
                Files.isRegularFile(fonts.resolve("fonts.conf")),
                "the font stack's fonts.conf is missing from " + fonts);
    }

    @Test
    @DisplayName("a variable that is not set is empty, not null and not an exception")
    void anUnsetVariableIsEmpty() {
        assertAll(
                () ->
                        assertEquals(
                                Optional.empty(),
                                reader.environmentVariable(
                                        "COMETGUI_DELIBERATELY_UNSET_VARIABLE_9d3f")),
                () ->
                        assertEquals(
                                Optional.empty(),
                                reader.environmentVariable(""),
                                "an empty variable name is unset, not an error"));
    }

    @Test
    @DisplayName("system properties are read, including the three the baseline check asks for")
    void readsSystemProperties() {
        assertAll(
                () ->
                        assertEquals(
                                Optional.of(System.lineSeparator()),
                                reader.systemProperty("line.separator"),
                                "line.separator must be the JVM's own"),
                () ->
                        assertTrue(
                                reader.systemProperty("java.version")
                                        .orElseThrow()
                                        .startsWith("25"),
                                "this project pins a JDK 25 toolchain; java.version was "
                                        + reader.systemProperty("java.version")),
                () ->
                        assertTrue(
                                reader.osName().isPresent()
                                        && !reader.osName().orElseThrow().isBlank(),
                                "os.name must be readable"),
                () ->
                        assertTrue(
                                reader.osArch().isPresent()
                                        && !reader.osArch().orElseThrow().isBlank(),
                                "os.arch must be readable"),
                () ->
                        assertEquals(
                                Optional.of("64"),
                                reader.dataModel(),
                                "the project's reference platform is 64-bit; "
                                        + EnvironmentReader.DATA_MODEL_PROPERTY
                                        + " said otherwise"));
    }

    @Test
    @DisplayName("a property that is not set is empty")
    void anUnsetPropertyIsEmpty() {
        assertEquals(
                Optional.empty(), reader.systemProperty("cometgui.deliberately.unset.property"));
    }

    @Test
    @DisplayName("a null name is rejected, and an empty property name is a caller's mistake")
    void rejectsBadNames() {
        String noName = Nulls.of(String.class);

        assertAll(
                () ->
                        assertEquals(
                                "name",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> reader.environmentVariable(noName))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "name",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> reader.systemProperty(noName))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "key can't be empty",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> reader.systemProperty(""))
                                        .getMessage(),
                                "an empty property name is passed through to the JDK's own"
                                        + " rejection rather than silently reported as unset"));
    }
}
