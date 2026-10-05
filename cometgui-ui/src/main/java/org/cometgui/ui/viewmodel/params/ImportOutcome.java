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

import java.util.List;
import java.util.Objects;

/**
 * What became of one step of importing a parameter file.
 *
 * @param kind what happened
 * @param messages what to tell the scientist, one line each: the parse's warnings for a file read,
 *     the migration's headline for one migrated, the question for an offer, the reasons for a
 *     refusal
 */
public record ImportOutcome(Kind kind, List<String> messages) {

    /** What one step of an import did. */
    public enum Kind {

        /** The file was read and adopted as the configuration. */
        IMPORTED,

        /** The file was migrated to the selected release and adopted, under review. */
        MIGRATED,

        /**
         * The file names another release; nothing changed yet, and {@link
         * ParameterFilesViewModel#offer()} holds the choices.
         */
        OFFERED,

        /** Nothing changed; the messages say why. */
        REFUSED
    }

    /**
     * Validates presence and takes an immutable copy.
     *
     * @throws IllegalArgumentException if an offer or a refusal says nothing
     */
    public ImportOutcome {
        Objects.requireNonNull(kind, "kind");
        messages = List.copyOf(messages);
        if (messages.isEmpty() && (kind == Kind.OFFERED || kind == Kind.REFUSED)) {
            throw new IllegalArgumentException("an import " + kind + " has to say why");
        }
    }
}
