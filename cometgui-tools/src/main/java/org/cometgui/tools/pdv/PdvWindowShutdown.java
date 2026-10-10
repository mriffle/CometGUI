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
 * How a PDV window's shutdown went: what its control server answered {@code /shutdown}, and whether
 * the process then ended within the bound.
 *
 * <p>Both halves are reported because PDV's {@code /shutdown} stops only its control server -- the
 * window and the JVM keep running (design decision P11-16) -- so the process itself is terminated
 * through the process service, and a shutdown is complete only when it has exited.
 *
 * @param controlServer what {@code /shutdown} got, in words: the answer, why it was not sent, or
 *     how it failed
 * @param exited whether the process had ended when the shutdown returned
 * @param exitCode the process's exit status, when it ended
 */
public record PdvWindowShutdown(String controlServer, boolean exited, OptionalInt exitCode) {

    /**
     * Requires every component, and an exit status exactly when the process ended.
     *
     * @throws IllegalArgumentException if an exit status is given for a process that did not end,
     *     or none for one that did
     * @throws NullPointerException if a component is {@code null}
     */
    public PdvWindowShutdown {
        Objects.requireNonNull(controlServer, "controlServer");
        Objects.requireNonNull(exitCode, "exitCode");
        if (exitCode.isPresent() != exited) {
            throw new IllegalArgumentException(
                    "a shutdown has an exit status exactly when the process ended");
        }
    }
}
