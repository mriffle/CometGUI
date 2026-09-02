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

package org.cometgui.domain.ports;

import java.io.IOException;

/**
 * Starts an external tool.
 *
 * <p>The signature is the specification's, from <em>Software Architecture / Key interfaces</em>.
 * There is no implementation in this module and none in this phase: phase 03 builds the process
 * service, and {@code R-PROC-02}'s ArchUnit rule confines {@link ProcessBuilder} to it, so this
 * interface is the only thing the rest of the product is allowed to know about launching a process.
 *
 * <p>{@link #start} returns as soon as the process is running. Output is delivered to the listener
 * as it arrives rather than collected and returned, because a Comet search emits output for minutes
 * and {@code R-PROC-03} caps what may be held in memory.
 */
@FunctionalInterface
public interface ProcessRunner {

    /**
     * Starts the command and returns a handle on the running process.
     *
     * @param command the validated argument array, working directory and environment
     * @param listener receives output lines and the exit code
     * @return a handle on the started process
     * @throws IOException if the process cannot be started -- the executable is missing, is not
     *     executable, or the working directory does not exist
     * @throws NullPointerException if either argument is {@code null}
     */
    RunningProcess start(ToolCommand command, ProcessListener listener) throws IOException;
}
