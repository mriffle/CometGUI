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
import org.cometgui.params.comet.schema.StaticModTarget;

/**
 * One row of the static-modification table: a residue or terminus, the mass added to it, and
 * whether that is the release's default. A snapshot; edits go through {@link StaticModsViewModel}.
 *
 * @param target what the mass is added to, from the model
 * @param massText the mass in the configuration, or the refused text while a refusal is pending
 * @param defaultText the release's default mass
 * @param originText where the value came from, in words
 * @param atDefault whether the configuration holds the release's default value
 */
public record StaticModRow(
        StaticModTarget target,
        String massText,
        String defaultText,
        String originText,
        boolean atDefault) {

    /** Validates presence. */
    public StaticModRow {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(massText, "massText");
        Objects.requireNonNull(defaultText, "defaultText");
        Objects.requireNonNull(originText, "originText");
    }

    /**
     * The parameter the row edits.
     *
     * @return for example {@code add_C_cysteine}
     */
    public String parameter() {
        return target.parameter();
    }

    /**
     * The residue or terminus in words.
     *
     * @return for example {@code cysteine (C)} or {@code peptide N-terminus}
     */
    public String words() {
        return target.words();
    }

    /**
     * The row's state in words, for the table's default/reset column.
     *
     * @return {@code Default} or {@code Changed from default <text>}, then where the value came
     *     from
     */
    public String stateText() {
        return (atDefault ? "Default" : "Changed from default " + defaultText)
                + " -- "
                + originText;
    }
}
