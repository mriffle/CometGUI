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

package org.cometgui.domain.platform;

import java.util.Objects;

/**
 * The result of a host baseline check: one outcome and the sentence shown to the user.
 *
 * <p>The message is part of the value rather than something the user interface composes from the
 * outcome, because {@code R-PLAT-03} requires the diagnostic to name the host's value and the
 * required value -- and both of those are known here and nowhere else.
 *
 * @param outcome what was found
 * @param message a complete, human-readable sentence naming the host's value and the requirement
 */
public record HostBaselineReport(HostBaselineOutcome outcome, String message) {

    /**
     * Validates the report.
     *
     * @throws NullPointerException if either component is {@code null}
     * @throws IllegalArgumentException if the message is blank, which would leave a user with an
     *     outcome and no explanation
     */
    public HostBaselineReport {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(message, "message");
        if (message.isBlank()) {
            throw new IllegalArgumentException(
                    "a host baseline report for " + outcome + " must carry a message");
        }
    }

    /**
     * Whether this report must stop the workflow.
     *
     * @return {@link HostBaselineOutcome#blocking()} of the outcome
     */
    public boolean blocking() {
        return outcome.blocking();
    }
}
