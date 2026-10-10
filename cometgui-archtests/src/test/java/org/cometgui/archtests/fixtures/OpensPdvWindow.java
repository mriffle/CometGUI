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

package org.cometgui.archtests.fixtures;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.IntFunction;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.tools.pdv.PdvWindowState;
import org.cometgui.tools.pdv.derived.PdvController;

/**
 * Opens a PDV {@code denovo-gui} window through the derived controller over a process runner it was
 * handed, outside {@code org.cometgui.workflow.pdv}: the "just open it here" shortcut that would
 * launch PDV without the run's event log ever recording it. A committed fixture, so that the proof
 * that {@code PdvLaunchPathRuleTest}'s window rule rejects it runs on every build.
 */
public final class OpensPdvWindow {

    private OpensPdvWindow() {}

    /**
     * @param controller the derived controller
     * @param mzTab the mzTab to show
     * @param processes any process runner -- the application's own, unrecorded
     * @param command the {@code denovo-gui} command for a port
     * @return the window's state
     * @throws IOException if PDV cannot be started
     */
    static PdvWindowState shortcut(
            PdvController controller,
            Path mzTab,
            ProcessRunner processes,
            IntFunction<ToolCommand> command)
            throws IOException {
        return controller.open(mzTab, processes, command, state -> {});
    }
}
