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

/**
 * What became of one attempt to change the configuration: accepted, or refused with the reason the
 * field shows.
 *
 * @param accepted whether the configuration now holds what was asked for
 * @param refusal why not, present exactly when {@code accepted} is {@code false}; the model's own
 *     message, or the lock's reason
 */
public record EditOutcome(boolean accepted, Optional<String> refusal) {

    /**
     * Validates the pairing.
     *
     * @throws IllegalArgumentException if an accepted outcome carries a refusal, or a refused one
     *     carries none
     */
    public EditOutcome {
        Objects.requireNonNull(refusal, "refusal");
        if (accepted == refusal.isPresent()) {
            throw new IllegalArgumentException(
                    accepted ? "an accepted edit has no refusal" : "a refused edit has to say why");
        }
    }

    /**
     * An accepted change.
     *
     * @return the outcome
     */
    public static EditOutcome applied() {
        return new EditOutcome(true, Optional.empty());
    }

    /**
     * A refused change.
     *
     * @param reason why, in a sentence
     * @return the outcome
     */
    public static EditOutcome refused(String reason) {
        return new EditOutcome(false, Optional.of(reason));
    }
}
