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

package org.cometgui.params.comet.migration;

import java.util.Objects;
import java.util.Optional;

/**
 * What a schema migration did with one parameter.
 *
 * @param parameter the parameter name
 * @param outcome what happened to it
 * @param sourceText its value text in the source model; empty exactly when the parameter was {@link
 *     MigrationEntry.Outcome#ADDED}
 * @param targetText its value text in the migrated model, as a modelled value or as an unknown
 *     parameter
 * @param explanation one sentence saying what happened, for the user
 */
public record MigrationEntry(
        String parameter,
        Outcome outcome,
        Optional<String> sourceText,
        String targetText,
        String explanation) {

    /** What a migration can do with one parameter. */
    public enum Outcome {

        /** Modelled in both versions; the same text carried over, with its origin. */
        CARRIED,

        /**
         * Modelled in both versions; the value written in the target's syntax -- a tuple
         * re-laid-out for the target's field layout -- with the same meaning and its origin.
         */
        RESHAPED,

        /**
         * New in the target version: it takes the target's default, origin {@code COMET_DEFAULT}.
         */
        ADDED,

        /**
         * Not a parameter of the target version: kept, with its value text, as an unknown parameter
         * ({@code R-PARAM-07}) -- never dropped -- for the user to remove or keep.
         */
        REMOVED_KEPT_AS_UNKNOWN,

        /** An unknown parameter of the source model that the target does not model either; kept. */
        UNKNOWN_CARRIED,

        /**
         * An unknown parameter of the source model that the target version models: read as the
         * target's parameter, origin {@code IMPORTED}.
         */
        UNKNOWN_ADOPTED,

        /**
         * The target has the parameter but cannot hold the value with its meaning (for example two
         * neutral losses where the target's tuple takes one). The migrated model holds the target's
         * default; the source value is in the report and the user must decide.
         */
        NEEDS_ATTENTION;

        /**
         * Whether the outcome is a change the user should review.
         *
         * @return {@code false} only for {@link #CARRIED} and {@link #UNKNOWN_CARRIED}
         */
        public boolean isChange() {
            return this != CARRIED && this != UNKNOWN_CARRIED;
        }
    }

    /**
     * Validates the components.
     *
     * @throws IllegalArgumentException if the explanation is blank, or the source text is absent
     *     for any outcome but {@link Outcome#ADDED} or present for that one
     */
    public MigrationEntry {
        Objects.requireNonNull(parameter, "parameter");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(sourceText, "sourceText");
        Objects.requireNonNull(targetText, "targetText");
        Objects.requireNonNull(explanation, "explanation");
        if (explanation.isBlank()) {
            throw new IllegalArgumentException(
                    parameter + ": a migration entry needs an explanation");
        }
        if (sourceText.isEmpty() != (outcome == Outcome.ADDED)) {
            throw new IllegalArgumentException(
                    parameter
                            + ": only an ADDED parameter has no source value, and it has none; this"
                            + " one is "
                            + outcome);
        }
    }
}
