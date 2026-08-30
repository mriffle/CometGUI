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

package org.cometgui.domain.ports;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.cometgui.domain.testing.FakeEnvironmentReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the three default methods on {@link EnvironmentReader}.
 *
 * <p>They are one-liners, and they are exactly the one-liners a typo makes silently wrong: a
 * baseline check that reads {@code os.arch} where it meant {@code sun.arch.data.model} answers
 * confidently and incorrectly. So each is asserted twice -- the value it returns, and the property
 * name it asked for.
 */
class EnvironmentReaderTest {

    /** Records the property names asked for, so the delegation itself can be asserted. */
    private static final class RecordingReader implements EnvironmentReader {
        private final List<String> requested = new ArrayList<>();

        @Override
        public Optional<String> environmentVariable(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<String> systemProperty(String name) {
            requested.add(name);
            return Optional.of("answered:" + name);
        }
    }

    @Test
    @DisplayName("the property names are the ones the JVM actually uses")
    void propertyNamesAreTheJvmNames() {
        assertAll(
                () -> assertEquals("os.name", EnvironmentReader.OS_NAME_PROPERTY),
                () -> assertEquals("os.arch", EnvironmentReader.OS_ARCH_PROPERTY),
                () -> assertEquals("sun.arch.data.model", EnvironmentReader.DATA_MODEL_PROPERTY));
    }

    @Test
    @DisplayName("osName, osArch and dataModel return the value of their own property")
    void hostAccessorsReturnTheirProperty() {
        FakeEnvironmentReader reader = new FakeEnvironmentReader().withHost("Linux", "amd64", "64");

        assertAll(
                () -> assertEquals(Optional.of("Linux"), reader.osName()),
                () -> assertEquals(Optional.of("amd64"), reader.osArch()),
                () -> assertEquals(Optional.of("64"), reader.dataModel()));
    }

    @Test
    @DisplayName("each host accessor reads exactly its own property name")
    void hostAccessorsReadTheirOwnPropertyName() {
        RecordingReader reader = new RecordingReader();

        assertAll(
                () -> assertEquals(Optional.of("answered:os.name"), reader.osName()),
                () -> assertEquals(Optional.of("answered:os.arch"), reader.osArch()),
                () -> assertEquals(Optional.of("answered:sun.arch.data.model"), reader.dataModel()),
                () ->
                        assertEquals(
                                List.of("os.name", "os.arch", "sun.arch.data.model"),
                                reader.requested));
    }

    @Test
    @DisplayName("an unset property is empty, not null and not blank")
    void anUnsetPropertyIsEmpty() {
        FakeEnvironmentReader reader = new FakeEnvironmentReader().withHost(null, null, null);

        assertAll(
                () -> assertTrue(reader.osName().isEmpty()),
                () -> assertTrue(reader.osArch().isEmpty()),
                () -> assertTrue(reader.dataModel().isEmpty()));
    }

    @Test
    @DisplayName("environment variables and system properties are separate namespaces")
    void variablesAndPropertiesAreSeparate() {
        FakeEnvironmentReader reader =
                new FakeEnvironmentReader()
                        .withVariable("COMETGUI_HOME", "/opt/cometgui")
                        .withProperty("COMETGUI_HOME", "/somewhere/else");

        assertAll(
                () ->
                        assertEquals(
                                Optional.of("/opt/cometgui"),
                                reader.environmentVariable("COMETGUI_HOME")),
                () ->
                        assertEquals(
                                Optional.of("/somewhere/else"),
                                reader.systemProperty("COMETGUI_HOME")),
                () -> assertTrue(reader.environmentVariable("PATH").isEmpty()));
    }
}
