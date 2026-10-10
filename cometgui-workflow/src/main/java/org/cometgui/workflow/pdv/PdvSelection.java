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

package org.cometgui.workflow.pdv;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import org.cometgui.domain.run.RunId;

/**
 * What became of a request to show a PSM in a run's PDV window.
 *
 * @param run the run
 * @param spectraRef the {@code spectra_ref} sent, or that would have been sent; empty when the PSM
 *     is not in the mzTab PDV was given
 * @param kind what happened
 * @param status PDV's HTTP status, when it answered
 * @param message what happened, in words, PDV's own answer included
 */
public record PdvSelection(
        RunId run, Optional<String> spectraRef, Kind kind, OptionalInt status, String message) {

    /** What can become of a selection. */
    public enum Kind {

        /** PDV answered {@code 200 selected}: it found the row and is drawing it. */
        SELECTED,

        /** PDV answered {@code 404 unknown ref}: it has no row with that reference. */
        UNKNOWN_TO_PDV,

        /** PDV answered with another status: {@code 400}, {@code 503} or {@code 500}. */
        REFUSED_BY_PDV,

        /** The PSM is not a row of the mzTab PDV was given, so nothing was sent. */
        NOT_IN_MZTAB,

        /** No window is open for the run, or it failed or was closed; nothing was sent. */
        NOT_OPEN,

        /** A later selection replaced it before it was sent. */
        SUPERSEDED,

        /** It was sent and no answer came back. */
        FAILED
    }

    /**
     * Requires every component, and a status exactly when PDV answered.
     *
     * @throws IllegalArgumentException if a status is given for a kind PDV did not answer, or
     *     missing for one it did
     * @throws NullPointerException if a component is {@code null}
     */
    public PdvSelection {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(spectraRef, "spectraRef");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(message, "message");
        boolean answered =
                kind == Kind.SELECTED || kind == Kind.UNKNOWN_TO_PDV || kind == Kind.REFUSED_BY_PDV;
        if (status.isPresent() != answered) {
            throw new IllegalArgumentException(
                    "a selection has PDV's status exactly when PDV answered: " + kind);
        }
    }
}
