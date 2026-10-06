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

package org.cometgui.domain.run;

import java.io.Serial;
import java.util.Objects;

/**
 * Thrown when an update to a run's record would change something {@code R-RUN-06} makes immutable:
 * an identity member, or an attempt that has ended.
 *
 * <p>It names the member, so the message says exactly what the update tried to change.
 */
public final class RunImmutabilityException extends RuntimeException {

    @Serial private static final long serialVersionUID = 1L;

    /** The member the update tried to change, as {@code run.json} names it. */
    private final String member;

    /**
     * Creates the refusal.
     *
     * @param member the member, as {@code run.json} names it
     * @param message the full explanation
     */
    RunImmutabilityException(String member, String message) {
        super(message);
        this.member = Objects.requireNonNull(member, "member");
    }

    /**
     * The member the update tried to change.
     *
     * @return for example {@code fasta}, {@code spectra[0]} or {@code attempts[0]}
     */
    public String member() {
        return member;
    }
}
