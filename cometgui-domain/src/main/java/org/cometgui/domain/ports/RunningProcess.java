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
 * A handle on a process that a {@link ProcessRunner} has already started.
 *
 * <p>The handle is deliberately thin: it can be asked whether the process is still running, waited
 * on, and asked to stop. It cannot be used to write to the process, because no tool in this
 * workflow reads from standard input, and offering a stream that is never used invites a caller to
 * block on one.
 */
public interface RunningProcess {

    /**
     * Whether the process is still running.
     *
     * @return {@code true} until the process has ended
     */
    boolean isAlive();

    /**
     * Waits for the process to end and returns its exit code.
     *
     * @return the exit code
     * @throws InterruptedException if the waiting thread is interrupted; the process is left
     *     running, so a caller that gives up must also call {@link #requestCancellation()}
     */
    int waitForExit() throws InterruptedException;

    /**
     * Asks the process to stop.
     *
     * <p>A request, not a guarantee, and it returns without waiting: the implementation terminates
     * the process and attempts to terminate its descendants, and the caller learns that it worked
     * from {@link #waitForExit()} returning. Calling it more than once, or after the process has
     * already ended, does nothing.
     */
    void requestCancellation();
}
