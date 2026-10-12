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

package org.cometgui.app.config;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import org.cometgui.domain.run.RunIdentity;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.ui.viewmodel.results.ResultsRun;

/**
 * A run the Results section lists, found in the session's project: its directory, its recorded
 * identity, and the run whose {@code outputs/comet/} holds its pepXML -- itself, or for a
 * Percolator rerun the run it was derived from. Shared by the Visualisation and Limelight ports,
 * which act on the Results section's chosen run.
 */
final class ProjectRuns {

    private ProjectRuns() {}

    /**
     * A run's directory, its recorded identity, and the run that searched.
     *
     * @param layout the run's directory
     * @param identity its recorded identity
     * @param searched the run whose {@code outputs/comet/} holds its pepXML
     */
    record Located(RunLayout layout, RunIdentity identity, RunLayout searched) {}

    /**
     * Finds a run in the session's project and reads its identity, which must name it.
     *
     * @param project the session's project
     * @param run the run, as the Results section lists it
     * @return where it is and what it recorded
     * @throws IOException if the run's record cannot be read, or records another run
     */
    static Located locate(ProjectSession project, ResultsRun run) throws IOException {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(run, "run");
        Path runs = project.layout().runsDirectory();
        RunLayout layout =
                new RunLayout(
                        runs.resolve(RunLayout.directoryName(run.created(), run.id()))
                                .toAbsolutePath());
        RunIdentity identity = project.readRun(layout).identity();
        if (!identity.runId().equals(run.id())) {
            throw new IOException(
                    layout.root()
                            + " records run "
                            + identity.runId().value()
                            + ", not run "
                            + run.id().value());
        }
        RunLayout searched =
                identity.derivedFrom()
                        .map(
                                source ->
                                        new RunLayout(
                                                runs.resolve(source.directoryName())
                                                        .toAbsolutePath()))
                        .orElse(layout);
        return new Located(layout, identity, searched);
    }
}
