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

/**
 * Receives the output and the outcome of a process started by a {@link ProcessRunner}.
 *
 * <p>Standard output and standard error are reported separately, one call per line, because the
 * specification requires the process service to stream the two independently -- a merged stream
 * cannot tell a user which of the two a message came from, and Comet writes progress to one and
 * diagnostics to the other.
 *
 * <p>Callbacks arrive on the process service's reader threads and never on the JavaFX application
 * thread. A listener that touches the user interface hops threads itself.
 */
public interface ProcessListener {

    /**
     * Reports one line written to standard output, without its line terminator.
     *
     * @param line the line, never {@code null}
     */
    void onStandardOutput(String line);

    /**
     * Reports one line written to standard error, without its line terminator.
     *
     * @param line the line, never {@code null}
     */
    void onStandardError(String line);

    /**
     * Reports that the process has ended.
     *
     * <p>Called exactly once, after the last output line, whether the process succeeded, failed or
     * was cancelled. A cancelled process still reports the exit code the operating system gave it.
     *
     * @param exitCode the process exit code
     */
    void onExit(int exitCode);
}
