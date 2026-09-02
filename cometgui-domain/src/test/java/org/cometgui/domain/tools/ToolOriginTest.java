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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link ToolOrigin}. */
class ToolOriginTest {

    @Test
    @DisplayName("the two origins are the two that exist")
    void theTwoArePinned() {
        List<String> names = new ArrayList<>();
        for (ToolOrigin origin : ToolOrigin.values()) {
            names.add(origin.name());
        }

        assertEquals(List.of("MANAGED", "LOCAL"), names);
    }

    @Test
    @DisplayName("only a managed install reports itself as managed")
    void managedFlag() {
        assertAll(
                () -> assertTrue(ToolOrigin.MANAGED.isManaged()),
                () -> assertFalse(ToolOrigin.LOCAL.isManaged()));
    }
}
