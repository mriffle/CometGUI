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

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import org.cometgui.ui.viewmodel.SectionId;

/**
 * What each empty section is waiting for, and which phase brings it.
 *
 * <p>Phase 02 builds the frame and deliberately puts no scientific behaviour in it. An empty pane
 * with a heading and nothing else is indistinguishable from a broken one, so each pane says in
 * plain text which phase fills it. The phase numbers are read from {@code phases/index.rst}; they
 * are not guesses, and a phase that renumbers has to change them here.
 *
 * <p>Package-private: this is the shell's own copy for the shell's own panes, not an interface
 * anything else should build on.
 */
final class SectionArrivals {

    /** The note shown on each section's pane, by section. */
    private static final Map<SectionId, String> NOTES = notes();

    private SectionArrivals() {}

    /**
     * The note one section's pane shows.
     *
     * @param section the section
     * @return the note, never blank
     * @throws NullPointerException if {@code section} is {@code null}
     * @throws IllegalStateException if no note has been written for the section, which can only
     *     happen if a constant was added to {@link SectionId} without one -- and which is loud here
     *     rather than an empty label in the running application
     */
    static String noteFor(SectionId section) {
        Objects.requireNonNull(section, "section");
        String note = NOTES.get(section);
        if (note == null) {
            throw new IllegalStateException(
                    "no arrival note has been written for the section: " + section.id());
        }
        return note;
    }

    /**
     * The notes, written once.
     *
     * @return an immutable map holding a note for every section
     */
    private static Map<SectionId, String> notes() {
        Map<SectionId, String> notes = new EnumMap<>(SectionId.class);
        notes.put(
                SectionId.RUN,
                "This section arrives in phase 08 (Workflow Engine and Comet Adapter): the inputs,"
                        + " the validation summary and the Run and Cancel controls. The stage"
                        + " stepper below is already live and has no engine behind it yet.");
        notes.put(
                SectionId.COMET_PARAMETERS,
                "This section arrives in phase 07 (Comet Parameter Editor UI), on the parameter"
                        + " model phase 06 builds.");
        notes.put(
                SectionId.PERCOLATOR,
                "This section arrives in phase 09 (Percolator Adapter and Version Capabilities).");
        notes.put(SectionId.RESULTS, "This section arrives in phase 10 (Results Model and UI).");
        notes.put(
                SectionId.VISUALISATION,
                "This section arrives in phase 11 (PDV Integration and mzTab Export).");
        notes.put(
                SectionId.LIMELIGHT,
                "This section arrives in phase 12 (Limelight Conversion and Upload).");
        notes.put(
                SectionId.PROVENANCE,
                "This section arrives in phase 13 (Provenance UI and Reports).");
        notes.put(
                SectionId.CONSOLE,
                "This section arrives in phase 03 (Process Service): the messages a running tool"
                        + " emits. The console below is already live and its message log is"
                        + " empty until something writes to it.");
        notes.put(
                SectionId.TOOL_MANAGER,
                "This section arrives in phase 05 (Tool Registry and Installer).");
        notes.put(
                SectionId.SETTINGS,
                "No phase in phases/index.rst claims this section. It arrives with the first phase"
                        + " that needs a preference to persist between runs; until then it is"
                        + " empty on purpose rather than by omission.");
        return Map.copyOf(notes);
    }
}
