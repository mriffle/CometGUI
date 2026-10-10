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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.RunIdentity;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.tools.InstallHandle;
import org.cometgui.domain.tools.InstallProgressListener;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.tools.api.JavaRuntime;
import org.cometgui.ui.viewmodel.results.ResultsRun;
import org.cometgui.ui.viewmodel.visualisation.VisualisationPort;
import org.cometgui.workflow.pdv.DrawnSpectrum;
import org.cometgui.workflow.pdv.PdvInstallation;
import org.cometgui.workflow.pdv.PdvProvisioning;
import org.cometgui.workflow.pdv.PdvStatus;
import org.cometgui.workflow.pdv.PsmFigureRequest;
import org.cometgui.workflow.pdv.PsmFigures;

/**
 * The Visualisation section's port over the Tool Manager and the session's project (phase 11;
 * {@link VisualisationPort}).
 *
 * <p>PDV's state and its install are {@link PdvProvisioning}'s over the one Tool Manager -- the
 * object the Tool Manager section shows. A drawing is {@link PsmFigures}', whose every PDV launch
 * goes through the one recording launch path; this class only finds the run, refuses a run that is
 * executing, takes the project's lock and supplies PDV, the Java runtime and the display.
 *
 * <h2>A run that is executing</h2>
 *
 * <p>Refused, whatever the caller checked: the workflow engine holds that run's provenance event
 * log while it executes, and every PDV launch is recorded there before PDV starts. The same rule
 * Phase 10 made for exports.
 *
 * <p>Every method may block and is called off the JavaFX thread.
 */
final class ProjectVisualisationPort implements VisualisationPort {

    private final ProjectSession project;

    private final Supplier<Optional<ToolManager>> tools;

    private final String toolsUnavailable;

    private final Optional<PsmFigures> figures;

    private final String noFigures;

    private final Supplier<Set<RunId>> executing;

    private final Supplier<Map<String, String>> display;

    /**
     * The port.
     *
     * @param project the session's project
     * @param tools the Tool Manager, or empty
     * @param toolsUnavailable why there is no Tool Manager, when {@code tools} is empty
     * @param figures the drawing, over the one recording launch path; empty when this application
     *     can launch nothing
     * @param noFigures why nothing can be drawn, when {@code figures} is empty
     * @param executing the runs the session's engine is executing now
     * @param display the variables PDV needs to reach the display
     */
    ProjectVisualisationPort(
            ProjectSession project,
            Supplier<Optional<ToolManager>> tools,
            String toolsUnavailable,
            Optional<PsmFigures> figures,
            String noFigures,
            Supplier<Set<RunId>> executing,
            Supplier<Map<String, String>> display) {
        this.project = Objects.requireNonNull(project, "project");
        this.tools = Objects.requireNonNull(tools, "tools");
        this.toolsUnavailable = Objects.requireNonNull(toolsUnavailable, "toolsUnavailable");
        this.figures = Objects.requireNonNull(figures, "figures");
        this.noFigures = Objects.requireNonNull(noFigures, "noFigures");
        this.executing = Objects.requireNonNull(executing, "executing");
        this.display = Objects.requireNonNull(display, "display");
    }

    @Override
    public PdvStatus pdv() {
        return tools.get()
                .map(manager -> new PdvProvisioning(manager).status())
                .orElseGet(() -> PdvStatus.unavailable(toolsUnavailable));
    }

    @Override
    public InstallHandle install(ToolVersion version, InstallProgressListener listener)
            throws IOException {
        ToolManager manager = tools.get().orElseThrow(() -> new IOException(toolsUnavailable));
        return new PdvProvisioning(manager).install(version, listener);
    }

    @Override
    public DrawnSpectrum draw(ResultsRun run, String psmId) throws IOException {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(psmId, "psmId");
        if (executing.get().contains(run.id())) {
            throw new IOException(
                    "run "
                            + run.id().value()
                            + " is executing: the workflow engine holds its provenance event log,"
                            + " where every PDV launch is recorded. Draw when the run has ended.");
        }
        PsmFigures drawing = figures.orElseThrow(() -> new IOException(noFigures));
        PdvStatus status = pdv();
        PdvInstallation pdv =
                status.installation()
                        .orElseThrow(
                                () ->
                                        new IOException(
                                                "PDV is not installed: " + status.explanation()));
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
        project.lock();
        return drawing.draw(
                new PsmFigureRequest(
                        layout,
                        identity,
                        searched,
                        psmId,
                        pdv,
                        JavaRuntime.ofThisApplication(),
                        display.get()));
    }
}
