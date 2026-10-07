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

package org.cometgui.tools.percolator;

import java.util.Objects;
import org.cometgui.domain.tools.ToolCapability;

/**
 * Something a run asked for that its command does not pass, because the build was not observed to
 * accept it ({@code R-PERC-06}): for provenance and the interface to say so.
 *
 * @param option the option left out
 * @param reason the whole explanation, naming the option and the missing capability
 */
public record NotEmitted(PercolatorOption option, String reason) {

    /**
     * Validates the record.
     *
     * @throws NullPointerException if a component is {@code null}
     * @throws IllegalArgumentException if the reason is blank
     */
    public NotEmitted {
        Objects.requireNonNull(option, "option");
        Objects.requireNonNull(reason, "reason");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("a left-out option needs a reason");
        }
    }

    /**
     * The capability the build lacks.
     *
     * @return {@code option().capability()}
     */
    public ToolCapability missing() {
        return option.capability();
    }
}
