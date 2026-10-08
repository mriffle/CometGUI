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
                "This section is live: phase 08 (Workflow Engine and Comet Adapter) put the"
                        + " workflow engine behind it, and phase 09 added Percolator. Run searches"
                        + " the spectrum files chosen in Comet Parameters with the installed Comet"
                        + " of the selected release, once per file, merges the PIN files and"
                        + " rescores them with the Percolator chosen in the Percolator section;"
                        + " Cancel stops it; the stage stepper follows the run; and before a rerun"
                        + " the preview below says which steps execute again. Every reason Run is"
                        + " disabled is stated in words: the parameters' (phase 07) and the"
                        + " engine's, the Percolator section's among them. Not yet live: the"
                        + " results arrive in phase 10; index modes are not offered here.");
        notes.put(
                SectionId.COMET_PARAMETERS,
                "This section is being filled by phase 07 (Comet Parameter Editor UI), on the"
                        + " parameter model phase 06 built: the release, the Essentials and"
                        + " Advanced levels and the validation summary below are live; the Expert"
                        + " level and the search preset choice are still to come.");
        notes.put(
                SectionId.PERCOLATOR,
                "This section is live: phase 09 (Percolator Adapter and Version Capabilities)"
                        + " filled it. Which Percolator the next run uses and why, what it can do,"
                        + " the Limelight-conversion switch that decides whether it must write"
                        + " Percolator XML, local-binary registration, the result filters, the"
                        + " Advanced settings, and the rerun of Percolator alone from the last run"
                        + " are below. The Limelight converter itself arrives in phase 12, and the"
                        + " filters' effect on the result tables in phase 10.");
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
                "This section is live: phase 05 (Tool Registry and Installer) filled it. Every"
                        + " tool build this machine can have is listed below -- including the ones"
                        + " upstream does not publish for this platform and the ones this host"
                        + " cannot run, because a build that is absent is a fact a user needs"
                        + " rather than a row to hide.");
        return Map.copyOf(notes);
    }
}
