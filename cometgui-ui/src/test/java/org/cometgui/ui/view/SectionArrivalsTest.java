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

package org.cometgui.ui.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.cometgui.ui.viewmodel.SectionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The arrival notes: every section has one, and the phase each one names is the phase {@code
 * phases/index.rst} gives that work.
 *
 * <p>The numbers are asserted here rather than eyeballed because an empty pane pointing at the
 * wrong phase is worse than an empty pane pointing at nothing: it sends the next reader to a phase
 * document that says nothing about the section.
 */
class SectionArrivalsTest {

    /** The phase each section is waiting for, read from phases/index.rst. */
    private static final Map<SectionId, String> OWNING_PHASE =
            Map.of(
                    SectionId.RUN, "phase 08",
                    SectionId.COMET_PARAMETERS, "phase 07",
                    SectionId.PERCOLATOR, "phase 09",
                    SectionId.RESULTS, "phase 10",
                    SectionId.VISUALISATION, "phase 11",
                    SectionId.LIMELIGHT, "phase 12",
                    SectionId.PROVENANCE, "phase 13",
                    SectionId.CONSOLE, "phase 03",
                    SectionId.TOOL_MANAGER, "phase 05");

    @Test
    @DisplayName("every section has a note, and it is not blank")
    void everySectionHasANote() {
        for (SectionId section : SectionId.displayOrder()) {
            String note = SectionArrivals.noteFor(section);
            assertFalse(note.isBlank(), "the arrival note for " + section.id() + " is blank");
        }
        assertEquals(
                10,
                SectionId.displayOrder().size(),
                "ten sections, so ten notes; a new one needs a note here");
    }

    @Test
    @DisplayName("each note names the phase phases/index.rst gives that section's work")
    void eachNoteNamesTheOwningPhase() {
        for (Map.Entry<SectionId, String> owner : OWNING_PHASE.entrySet()) {
            String note = SectionArrivals.noteFor(owner.getKey());
            assertTrue(
                    note.contains(owner.getValue()),
                    () ->
                            "the note for "
                                    + owner.getKey().id()
                                    + " must name "
                                    + owner.getValue()
                                    + ", but reads: "
                                    + note);
        }
    }

    @Test
    @DisplayName("Settings says plainly that no phase claims it, rather than naming a guess")
    void settingsSaysNoPhaseClaimsIt() {
        String note = SectionArrivals.noteFor(SectionId.SETTINGS);
        assertEquals(
                "No phase in phases/index.rst claims this section. It arrives with the first phase"
                        + " that needs a preference to persist between runs; until then it is empty"
                        + " on purpose rather than by omission.",
                note);
        assertTrue(
                OWNING_PHASE.keySet().size() + 1 == SectionId.displayOrder().size(),
                "Settings is the only section with no owning phase");
    }

    @Test
    @DisplayName("asking for a null section is rejected")
    void aNullSectionIsRejected() {
        assertThrows(NullPointerException.class, () -> SectionArrivals.noteFor(null));
    }
}
