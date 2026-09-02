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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Tests for {@link InstallPhase}. */
class InstallPhaseTest {

    @Test
    @DisplayName("the eight phases are the eight that exist, in the order they occur")
    void theEightArePinnedInOrder() {
        List<String> names = new ArrayList<>();
        for (InstallPhase phase : InstallPhase.values()) {
            names.add(phase.name());
        }

        assertEquals(
                List.of(
                        "DOWNLOADING",
                        "VERIFYING",
                        "EXTRACTING",
                        "INSTALLING",
                        "PROBING",
                        "DONE",
                        "CANCELLED",
                        "FAILED"),
                names);
    }

    @Test
    @DisplayName("exactly three phases are terminal, and cancelling is not failing")
    void theTerminalPhasesArePinned() {
        List<String> terminal = new ArrayList<>();
        for (InstallPhase phase : InstallPhase.values()) {
            if (phase.isTerminal()) {
                terminal.add(phase.name());
            }
        }

        assertEquals(List.of("DONE", "CANCELLED", "FAILED"), terminal);
        assertTrue(InstallPhase.CANCELLED.isTerminal());
        assertNotEquals(InstallPhase.CANCELLED, InstallPhase.FAILED);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(
            value = InstallPhase.class,
            names = {"DOWNLOADING", "VERIFYING", "EXTRACTING", "INSTALLING", "PROBING"})
    @DisplayName("a phase that is still working is not terminal")
    void workingPhasesAreNotTerminal(InstallPhase phase) {
        assertFalse(phase.isTerminal(), phase.name() + " is still working");
    }
}
