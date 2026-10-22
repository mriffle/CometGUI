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

package org.cometgui.app.e2e.tiera;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.cometgui.app.e2e.scenario.OracleProcesses;
import org.cometgui.app.uidriver.Await;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.tools.process.StartedProcess;

/**
 * Tier A's runner for the scenario's own oracle commands: the project's process service, an
 * argument array, never a shell -- the one way this project launches a process. Its exit is waited
 * for through {@link Await} on the process being alive, never with a fixed sleep.
 */
final class ProcessServiceOracle implements OracleProcesses {

    private final ProcessService service = new ProcessService(Clock.systemUTC());

    @Override
    public int run(List<String> argv, Path workingDirectory, Duration bound) throws IOException {
        StartedProcess started =
                service.start(
                        new ToolCommand(argv, workingDirectory, Map.of()),
                        new ProcessListener() {
                            @Override
                            public void onStandardOutput(String line) {
                                // the oracle reads the file the command writes, not its output
                            }

                            @Override
                            public void onStandardError(String line) {
                                // as above
                            }

                            @Override
                            public void onExit(int exitCode) {
                                // read below, from the process itself
                            }
                        });
        Await.until(String.join(" ", argv) + " to exit", started::isAlive, alive -> !alive, bound);
        try {
            return started.waitForExit();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted waiting for " + argv, interrupted);
        }
    }
}
