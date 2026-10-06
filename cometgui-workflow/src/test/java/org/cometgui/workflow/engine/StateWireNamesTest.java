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

package org.cometgui.workflow.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.cometgui.workflow.state.StepState;
import org.junit.jupiter.api.Test;

/** The event-log tokens of the nine states, and the transitions the engine allows. */
class StateWireNamesTest {

    @Test
    void everyStateHasItsHandTypedWireName() {
        List<String> names = new ArrayList<>();
        for (StepState state : StepState.values()) {
            names.add(StateWireNames.of(state));
        }
        assertEquals(
                List.of(
                        "not-started",
                        "validating",
                        "ready",
                        "running",
                        "succeeded",
                        "failed",
                        "cancel-requested",
                        "cancelled",
                        "skipped"),
                names);
    }

    @Test
    void exactlyTheDocumentedTransitionsAreAllowed() {
        Set<String> allowed = new TreeSet<>();
        for (StepState from : StepState.values()) {
            for (StepState to : StepState.values()) {
                if (StateWireNames.allowed(from, to)) {
                    allowed.add(from + " -> " + to);
                }
            }
        }
        assertEquals(
                new TreeSet<>(
                        Set.of(
                                "NOT_STARTED -> VALIDATING",
                                "NOT_STARTED -> READY",
                                "NOT_STARTED -> SKIPPED",
                                "NOT_STARTED -> CANCELLED",
                                "VALIDATING -> READY",
                                "VALIDATING -> FAILED",
                                "VALIDATING -> CANCEL_REQUESTED",
                                "READY -> RUNNING",
                                "READY -> CANCEL_REQUESTED",
                                "RUNNING -> SUCCEEDED",
                                "RUNNING -> FAILED",
                                "RUNNING -> CANCEL_REQUESTED",
                                "CANCEL_REQUESTED -> CANCELLED",
                                "CANCEL_REQUESTED -> SUCCEEDED")),
                allowed);
    }
}
