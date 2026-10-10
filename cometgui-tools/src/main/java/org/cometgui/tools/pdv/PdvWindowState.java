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
import java.util.OptionalInt;

/**
 * Where one PDV {@code denovo-gui} window stands, as its controller reports it.
 *
 * @param phase the phase
 * @param port the loopback port of its control server, once one has been chosen
 * @param message the state in words; for {@link Phase#FAILED}, what went wrong and what to do
 */
public record PdvWindowState(Phase phase, OptionalInt port, String message) {

    /** The phases of a window, in the order a window passes through them. */
    public enum Phase {

        /** Launched; its control server has not yet answered {@code /ready} with 200. */
        STARTING,

        /** Its control server answered {@code /ready} with 200: selections are sent to it. */
        READY,

        /** It did not become ready, or could not be started; the message says why. */
        FAILED,

        /** It was shut down, or its window was closed. */
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
    public PdvWindowState {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(port, "port");
        Objects.requireNonNull(message, "message");
    }
}
