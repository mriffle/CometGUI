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

package org.cometgui.tools.pdv;

import java.util.Objects;
import java.util.Optional;

/**
 * What became of one {@code /select} the controller was asked to send.
 *
 * @param kind what happened
 * @param spectraRef the {@code spectra_ref} that was to be selected, verbatim
 * @param answer what PDV's control server answered, for {@link Kind#ANSWERED}; otherwise empty
 * @param message what happened, in words
 */
public record PdvSelectOutcome(
        Kind kind, String spectraRef, Optional<PdvControlAnswer> answer, String message) {

    /** What can become of a selection. */
    public enum Kind {

        /** Sent; PDV's answer is {@link PdvSelectOutcome#answer()}, whatever its status. */
        ANSWERED,

        /** Never sent: a later selection for the same window replaced it within the debounce. */
        SUPERSEDED,

        /** Never sent: the window is not open, failed, or was closed before it could be. */
        NOT_OPEN,

        /** Sent, and no answer came back: the request failed, named in the message. */
        FAILED
    }

    /**
     * Requires every component, and an answer exactly for {@link Kind#ANSWERED}.
     *
     * @throws IllegalArgumentException if an answer is present for another kind, or absent for
     *     {@link Kind#ANSWERED}
     * @throws NullPointerException if a component is {@code null}
     */
    public PdvSelectOutcome {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(spectraRef, "spectraRef");
        Objects.requireNonNull(answer, "answer");
        Objects.requireNonNull(message, "message");
        if (answer.isPresent() != (kind == Kind.ANSWERED)) {
            throw new IllegalArgumentException(
                    "a selection carries PDV's answer exactly when it was answered: " + kind);
        }
    }
}
