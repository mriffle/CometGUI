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

/** Tests for {@link HostArchitecture}. */
class HostArchitectureTest {

    @Test
    @DisplayName("the two 64-bit architectures in the platform matrix are the two that exist")
    void theTwoArePinned() {
        List<String> names = new ArrayList<>();
        for (HostArchitecture architecture : HostArchitecture.values()) {
            names.add(architecture.name());
        }

        assertEquals(List.of("X86_64", "AARCH64"), names);
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource({"X86_64, x86-64", "AARCH64, aarch64"})
    @DisplayName("each carries the identifier the manifest uses, hyphenated not underscored")
    void identifiersArePinned(String constant, String expectedId) {
        assertEquals(expectedId, HostArchitecture.valueOf(constant).id());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource({"x86-64, X86_64", "aarch64, AARCH64"})
    @DisplayName("every hand-typed identifier resolves back to its constant")
    void identifiersResolve(String id, String expectedConstant) {
        assertEquals(HostArchitecture.valueOf(expectedConstant), HostArchitecture.fromId(id));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"amd64", "x86_64", "x64", "arm64", "X86-64", " x86-64", "i386", ""})
    @DisplayName("a JVM spelling is not a manifest identifier and is rejected here")
    void jvmSpellingsAreRejected(String unknown) {
        IllegalArgumentException rejected =
                assertThrows(
                        IllegalArgumentException.class, () -> HostArchitecture.fromId(unknown));

        assertEquals(
                "no architecture has the id \"" + unknown + "\"; expected one of [x86-64, aarch64]",
                rejected.getMessage());
    }

    @Test
    @DisplayName("the rejection message lists every identifier that is accepted")
    void theRejectionMessageDoesNotDrift() {
        IllegalArgumentException rejected =
                assertThrows(
                        IllegalArgumentException.class, () -> HostArchitecture.fromId("nonsense"));

        for (HostArchitecture architecture : HostArchitecture.values()) {
            assertTrue(
                    rejected.getMessage().contains(architecture.id()),
                    "the rejection message does not mention " + architecture.id());
        }
    }

    @Test
    @DisplayName("a null identifier is rejected as null, not as unknown")
    void nullIsRejectedByName() {
        NullPointerException rejected =
                assertThrows(
                        NullPointerException.class,
                        () -> HostArchitecture.fromId(Nulls.of(String.class)));

        assertEquals("id", rejected.getMessage());
    }
}
