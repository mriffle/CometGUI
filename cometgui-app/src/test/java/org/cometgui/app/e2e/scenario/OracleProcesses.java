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

package org.cometgui.app.e2e.scenario;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * How a scenario runs a tool for an oracle of its own -- never for the product: the installed
 * Comet's {@code -q}, which writes Comet's complete default parameter file, is what canonical step
 * 17's expectation is built from, so that the {@code comet.params} the product wrote is compared
 * with a file its writer did not produce.
 *
 * <p>An interface so the scenario stays tier-neutral (P14-3): Tier A runs the command through the
 * project's process service (processes are launched with argument arrays and only through it); Tier
 * B can do the same in the test JVM. Neither ever goes through a shell.
 */
public interface OracleProcesses {

    /**
     * Runs a command to its end.
     *
     * @param argv the executable and its arguments
     * @param workingDirectory the directory it runs in, absolute and existing
     * @param bound how long it may take at most: a deadlock detector, never a timing assumption
     * @return its exit code
     * @throws IOException if it cannot be started
     * @throws AssertionError if it is still running when the bound passes
     */
    int run(List<String> argv, Path workingDirectory, Duration bound) throws IOException;
}
