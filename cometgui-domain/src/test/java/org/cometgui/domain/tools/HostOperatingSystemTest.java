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

package org.cometgui.domain.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.cometgui.domain.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link HostOperatingSystem}. */
class HostOperatingSystemTest {

    @Test
    @DisplayName("the three operating systems in the platform matrix are the three that exist")
    void theThreeArePinned() {
        List<String> names = new ArrayList<>();
        for (HostOperatingSystem operatingSystem : HostOperatingSystem.values()) {
            names.add(operatingSystem.name());
        }

        assertEquals(List.of("LINUX", "MACOS", "WINDOWS"), names);
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource({"LINUX, linux", "MACOS, macos", "WINDOWS, windows"})
    @DisplayName("each carries the identifier the manifest uses")
    void identifiersArePinned(String constant, String expectedId) {
        assertEquals(expectedId, HostOperatingSystem.valueOf(constant).id());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource({"linux, LINUX", "macos, MACOS", "windows, WINDOWS"})
    @DisplayName("every hand-typed identifier resolves back to its constant")
    void identifiersResolve(String id, String expectedConstant) {
        assertEquals(HostOperatingSystem.valueOf(expectedConstant), HostOperatingSystem.fromId(id));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"Linux", "LINUX", " linux", "osx", "mac", "darwin", "freebsd", ""})
    @DisplayName("an unknown identifier is rejected by name, with no trimming and no case folding")
    void unknownIdentifiersAreRejected(String unknown) {
        IllegalArgumentException rejected =
                assertThrows(
                        IllegalArgumentException.class, () -> HostOperatingSystem.fromId(unknown));

        assertEquals(
                "no operating system has the id \""
                        + unknown
                        + "\"; expected one of [linux, macos, windows]",
                rejected.getMessage());
    }

    @Test
    @DisplayName("the rejection message lists every identifier that is accepted")
    void theRejectionMessageDoesNotDrift() {
        IllegalArgumentException rejected =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> HostOperatingSystem.fromId("nonsense"));

        for (HostOperatingSystem operatingSystem : HostOperatingSystem.values()) {
            assertTrue(
                    rejected.getMessage().contains(operatingSystem.id()),
                    "the rejection message does not mention " + operatingSystem.id());
        }
    }

    @Test
    @DisplayName("a null identifier is rejected as null, not as unknown")
    void nullIsRejectedByName() {
        NullPointerException rejected =
                assertThrows(
                        NullPointerException.class,
                        () -> HostOperatingSystem.fromId(Nulls.of(String.class)));

        assertEquals("id", rejected.getMessage());
    }
}
