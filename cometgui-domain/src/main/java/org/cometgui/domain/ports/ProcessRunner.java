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
import java.util.Objects;

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

    /**
     * Starts the command with a few bytes written to its standard input, which is then closed.
     *
     * <p>Added by phase 12 (design decisions P12-12 and P12-17) for the one kind of tool that must
     * be handed a secret: an OS keychain's command-line tool, such as {@code secret-tool store},
     * which reads the secret from standard input so that it never appears on an argument array --
     * where {@code /proc/<pid>/cmdline}, {@code ps} and a provenance record's argv would all show
     * it. The bytes are not part of the {@link ToolCommand}, so nothing that renders, records or
     * redacts a command ever sees them.
     *
     * <p>The default refuses: a runner that has not been written to feed standard input must not
     * pretend it did. The process service overrides it.
     *
     * @param command the validated argument array, working directory and environment
     * @param standardInput the bytes to write before standard input is closed
     * @param listener receives output lines and the exit code
     * @return a handle on the started process
     * @throws IOException if the process cannot be started or its input cannot be written; the
     *     message never contains the input
     * @throws NullPointerException if any argument is {@code null}
     */
    default RunningProcess startWithInput(
            ToolCommand command, byte[] standardInput, ProcessListener listener)
            throws IOException {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(standardInput, "standardInput");
        Objects.requireNonNull(listener, "listener");
        throw new IOException(
                "this process runner cannot write to a process's standard input, so it did not"
                        + " start "
                        + command.argv().get(0));
    }
}
