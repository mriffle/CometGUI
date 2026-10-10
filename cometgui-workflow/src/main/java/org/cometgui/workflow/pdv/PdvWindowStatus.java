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
import java.util.OptionalInt;
import org.cometgui.domain.run.RunId;

/**
 * Where a run's PDV window stands: what the Visualisation section shows.
 *
 * @param run the run whose results the window shows
 * @param phase the phase
 * @param port the loopback port of its control server, once one was chosen
 * @param message the state in words; for {@link Phase#FAILED}, what went wrong and what to do
 */
public record PdvWindowStatus(RunId run, Phase phase, OptionalInt port, String message) {

    /** The phases of a window, in the order a window passes through them. */
    public enum Phase {

        /** Launched; PDV is importing the mzTab. */
        STARTING,

        /** PDV has imported the mzTab: a selection in the Results section is shown in it. */
        READY,

        /** It did not become ready, or could not be closed; the message says why. */
        FAILED,

        /** It was closed, by CometGUI or by the scientist closing PDV's window. */
        CLOSED;

        /**
         * Whether a window in this phase has ended for good.
         *
         * @return {@code true} for {@link #FAILED} and {@link #CLOSED}
         */
        public boolean isFinal() {
            return this == FAILED || this == CLOSED;
        }
    }

    /**
     * Requires every component.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public PdvWindowStatus {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(port, "port");
        Objects.requireNonNull(message, "message");
    }
}
