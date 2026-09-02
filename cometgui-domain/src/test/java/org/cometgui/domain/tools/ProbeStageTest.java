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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ProbeStage}.
 *
 * <p>{@code R-TOOL-06} requires three <em>ordered</em> stages, so the order is asserted rather than
 * assumed: a reordering would make {@link ProbeFailureKind}'s "earliest stage" rule mean something
 * else without any other test noticing.
 */
class ProbeStageTest {

    @Test
    @DisplayName("the three stages are the three R-TOOL-06 names, in probe order")
    void theThreeStagesArePinnedInOrder() {
        List<String> names = new ArrayList<>();
        for (ProbeStage stage : ProbeStage.values()) {
            names.add(stage.name());
        }

        assertEquals(List.of("LOADABILITY", "IDENTITY", "CAPABILITY"), names);
    }

    @Test
    @DisplayName("loadability runs before identity, which runs before capability")
    void theOrderIsTheProbeOrder() {
        assertAll(
                () ->
                        assertTrue(
                                ProbeStage.LOADABILITY.compareTo(ProbeStage.IDENTITY) < 0,
                                "loadability before identity"),
                () ->
                        assertTrue(
                                ProbeStage.IDENTITY.compareTo(ProbeStage.CAPABILITY) < 0,
                                "identity before capability"),
                () -> assertEquals(0, ProbeStage.LOADABILITY.ordinal()),
                () -> assertEquals(2, ProbeStage.CAPABILITY.ordinal()));
    }
}
