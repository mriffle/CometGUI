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

package org.cometgui.domain.tools;

/**
 * A handle on an install that is already running: the one thing a caller can still do to it.
 *
 * <p>Cancellation is the only operation, because everything else a caller needs to know arrives
 * through its {@link InstallProgressListener} -- including how the install ended. A handle that
 * also reported state would be a second source of truth for the same fact, and the two would
 * disagree the moment one of them was read on a different thread.
 *
 * <p>There is no implementation in this module. Phase 05 unit 5 owns the install pipeline this
 * hands control to, and {@code PDV is a ~99 MB download}, which is why cancellation is a
 * first-class operation rather than an afterthought.
 */
public interface InstallHandle {

    /**
     * Asks the install to stop.
     *
     * <p>Returns immediately; the install stops when it reaches a point where it safely can. The
     * listener then sees a report carrying {@link InstallPhase#CANCELLED}, never {@link
     * InstallPhase#FAILED} -- a user who cancelled has not encountered an error. {@code R-TOOL-04}
     * requires the interrupted install to leave nothing that reports itself installed.
     *
     * <p>Calling this on an install that has already finished does nothing and is not an error: the
     * user's click and the install's last step race by nature, and making the caller synchronise
     * that race would move it somewhere worse.
     */
    void cancel();
}
