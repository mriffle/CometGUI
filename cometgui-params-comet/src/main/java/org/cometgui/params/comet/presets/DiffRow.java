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

package org.cometgui.params.comet.presets;

import java.util.Objects;
import java.util.Optional;

/**
 * One row of a reviewable diff -- the specification's <em>Parameter, Current, Preset</em>:
 * something that differs, its current text and the other side's text. A row exists only for a
 * difference.
 *
 * @param kind what the row is about
 * @param key the parameter name, or for an enzyme row its number
 * @param current the text on the current side; empty if the current side does not have it
 * @param other the text on the other side (the preset, or the other parameter set); empty if the
 *     other side does not have it
 */
public record DiffRow(Kind kind, String key, Optional<String> current, Optional<String> other) {

    /** What a row is about. */
    public enum Kind {

        /** A modelled parameter; the texts are its value texts. */
        PARAMETER,

        /** A row of the {@code [COMET_ENZYME_INFO]} table; the texts are the whole rows. */
        ENZYME_ROW,

        /** An unknown parameter; the texts are its value texts as imported. */
        UNKNOWN_PARAMETER
    }

    /**
     * Validates the row.
     *
     * @throws IllegalArgumentException if the key is blank or the two sides do not differ
     */
    public DiffRow {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(other, "other");
        if (key.isBlank()) {
            throw new IllegalArgumentException("a diff row needs a key");
        }
        if (current.equals(other)) {
            throw new IllegalArgumentException(
                    key + " does not differ, so it is not a row of a diff");
        }
    }

    /**
     * The row's first column, as the editor shows it.
     *
     * @return the parameter name, or {@code [COMET_ENZYME_INFO] row N} for an enzyme row
     */
    public String label() {
        return kind == Kind.ENZYME_ROW ? "[COMET_ENZYME_INFO] row " + key : key;
    }
}
