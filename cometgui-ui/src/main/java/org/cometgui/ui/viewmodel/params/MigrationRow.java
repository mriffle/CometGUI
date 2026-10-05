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

package org.cometgui.ui.viewmodel.params;

import java.util.Objects;
import java.util.Optional;
import org.cometgui.params.comet.migration.MigrationEntry;

/**
 * One change of a migration under review, as the review shows it ({@code R-PARAM-13}).
 *
 * @param entry the model's report entry
 * @param displayName the parameter's display name in the configuration's release, where it models
 *     the parameter
 * @param sourceValue the value the source release had, or a note that the parameter is new
 * @param valueNow what the configuration holds now: the parameter's text, an unknown parameter's
 *     text as kept, or {@link DiffRowView#ABSENT} once removed
 * @param resolution for an entry needing the scientist: how it was resolved, empty while it is not;
 *     always empty for any other entry
 * @param focusTarget the parameter whose field to move focus to, when the configuration's release
 *     models it
 */
public record MigrationRow(
        MigrationEntry entry,
        Optional<String> displayName,
        String sourceValue,
        String valueNow,
        Optional<String> resolution,
        Optional<String> focusTarget) {

    /**
     * Validates presence.
     *
     * @throws IllegalArgumentException if an entry that needs no decision carries a resolution
     */
    public MigrationRow {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(sourceValue, "sourceValue");
        Objects.requireNonNull(valueNow, "valueNow");
        Objects.requireNonNull(resolution, "resolution");
        Objects.requireNonNull(focusTarget, "focusTarget");
        if (resolution.isPresent() && entry.outcome() != MigrationEntry.Outcome.NEEDS_ATTENTION) {
            throw new IllegalArgumentException(
                    entry.parameter() + " needed no decision, so it has no resolution");
        }
    }

    /**
     * The parameter's name.
     *
     * @return the name
     */
    public String parameter() {
        return entry.parameter();
    }

    /**
     * What migration did, in words.
     *
     * @return for example {@code Converted}
     */
    public String outcomeWords() {
        return switch (entry.outcome()) {
            case CARRIED -> "Carried";
            case RESHAPED -> "Reshaped for the new release's syntax";
            case CONVERTED -> "Converted";
            case NOTED -> "Carried, with a note";
            case ADDED -> "Added at the new release's default";
            case REMOVED_KEPT_AS_UNKNOWN -> "Not in the new release; kept as an unknown parameter";
            case UNKNOWN_CARRIED -> "Unknown parameter, kept";
            case UNKNOWN_ADOPTED -> "Unknown parameter, now read as a parameter";
            case NEEDS_ATTENTION -> "Needs your decision";
        };
    }

    /**
     * Whether migration could not keep the value and the scientist must decide.
     *
     * @return {@code true} for a {@code NEEDS_ATTENTION} entry
     */
    public boolean needsDecision() {
        return entry.outcome() == MigrationEntry.Outcome.NEEDS_ATTENTION;
    }

    /**
     * Whether the row blocks a run: it needs a decision that has not been made.
     *
     * @return {@code true} while unresolved
     */
    public boolean blocking() {
        return needsDecision() && resolution.isEmpty();
    }

    /**
     * The row's state in words.
     *
     * @return {@code Blocks the run until you decide}, the resolution, or empty for an entry that
     *     needs no decision
     */
    public String stateText() {
        if (blocking()) {
            return "Blocks the run until you decide";
        }
        return resolution.orElse("");
    }
}
