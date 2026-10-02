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

package org.cometgui.params.comet.schema;

import java.util.Objects;

/**
 * One disagreement between a binary's own parameter dump and the curated metadata.
 *
 * @param kind what kind of disagreement
 * @param parameter the parameter it concerns, or the marker text for {@link Kind#VERSION_RECORD}
 * @param message the whole story, naming the parameter and both sides
 */
public record DriftFinding(Kind kind, String parameter, String message) {

    /** Validates the components. */
    public DriftFinding {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(parameter, "parameter");
        Objects.requireNonNull(message, "message");
    }

    /** The kinds of disagreement the specification's drift test fails on. */
    public enum Kind {

        /**
         * The binary declares a parameter with no metadata for its version that is not
         * allow-listed.
         */
        UNMODELLED,

        /**
         * The metadata claims a parameter for this version, or allow-lists it, and a complete dump
         * does not declare it. Never reported from a partial dump ({@code R-PARAM-02}).
         */
        NOT_DECLARED,

        /** The curated default differs from the one the binary writes. */
        DEFAULT_DIFFERS,

        /**
         * The metadata has no record of the dump's version, or records a different marker for it,
         * so nothing curated has been checked against this build.
         */
        VERSION_RECORD
    }
}
