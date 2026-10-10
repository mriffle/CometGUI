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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.cometgui.domain.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link NoManagedBuild}, the {@code D-011} fact a Tool Manager reports. */
class NoManagedBuildTest {

    private static final HostPlatform INTEL_MAC =
            new HostPlatform(HostOperatingSystem.MACOS, HostArchitecture.X86_64);

    @Test
    @DisplayName("the fact carries the tool, the host and whether registration is possible")
    void carriesItsComponents() {
        NoManagedBuild fact = new NoManagedBuild(ToolName.COMET, INTEL_MAC, true);

        assertAll(
                () -> assertEquals(ToolName.COMET, fact.tool()),
                () -> assertEquals(INTEL_MAC, fact.host()),
                () -> assertEquals(true, fact.localRegistration()),
                () ->
                        assertNotEquals(
                                fact,
                                new NoManagedBuild(ToolName.COMET, INTEL_MAC, false),
                                "whether a registration is possible is part of the fact"));
    }

    @Test
    @DisplayName("a fact with no tool or no host is refused, naming the component")
    void nullsAreRefused() {
        NullPointerException noTool =
                assertThrows(
                        NullPointerException.class,
                        () -> new NoManagedBuild(Nulls.of(ToolName.class), INTEL_MAC, true));
        NullPointerException noHost =
                assertThrows(
                        NullPointerException.class,
                        () ->
                                new NoManagedBuild(
                                        ToolName.COMET, Nulls.of(HostPlatform.class), true));

        assertAll(
                () -> assertEquals("tool", noTool.getMessage()),
                () -> assertEquals("host", noHost.getMessage()));
    }
}
