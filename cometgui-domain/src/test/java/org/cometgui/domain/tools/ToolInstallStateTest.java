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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ToolInstallState}.
 *
 * <p>The one assertion that carries weight is that the last two states are distinct constants:
 * "upstream publishes nothing for you" and "upstream publishes something your machine cannot run"
 * are different sentences to a scientist, with different remedies, and collapsing them is the
 * simplification a later refactor is most likely to make.
 */
class ToolInstallStateTest {

    @Test
    @DisplayName("the six states are the six that exist")
    void theSixArePinned() {
        List<String> names = new ArrayList<>();
        for (ToolInstallState state : ToolInstallState.values()) {
            names.add(state.name());
        }

        assertEquals(
                List.of(
                        "NOT_INSTALLED",
                        "INSTALLING",
                        "INSTALLED",
                        "FAILED",
                        "UNAVAILABLE_ON_THIS_PLATFORM",
                        "HOST_REQUIREMENTS_NOT_MET"),
                names);
    }

    @Test
    @DisplayName("no artefact and an unrunnable artefact are different states")
    void theTwoUnavailableStatesAreDistinct() {
        assertNotEquals(
                ToolInstallState.UNAVAILABLE_ON_THIS_PLATFORM,
                ToolInstallState.HOST_REQUIREMENTS_NOT_MET);
    }
}
