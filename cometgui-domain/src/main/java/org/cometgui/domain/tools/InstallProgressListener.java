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
 * Receives progress while a tool is being installed.
 *
 * <p>Callbacks arrive on the installer's own thread, never on the JavaFX application thread, and a
 * listener that blocks stalls the install -- the same contract {@code
 * org.cometgui.domain.ports.DownloadProgressListener} states, for the same reason. A listener that
 * updates the user interface hops threads itself.
 *
 * <p>Exactly one report carries a terminal {@link InstallPhase}, and it is the last one. See {@link
 * InstallPhase#isTerminal()}.
 */
@FunctionalInterface
public interface InstallProgressListener {

    /**
     * Reports where the install has got to.
     *
     * @param progress the report, never {@code null}
     */
    void onInstallProgress(InstallProgress progress);
}
