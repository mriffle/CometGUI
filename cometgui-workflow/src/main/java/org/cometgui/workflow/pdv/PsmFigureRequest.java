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

package org.cometgui.workflow.pdv;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import org.cometgui.domain.run.RunIdentity;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.tools.api.JavaRuntime;

/**
 * What to draw: one PSM of one run, with the PDV and the display to draw it with.
 *
 * @param run the run the PSM is shown from, whose event log records the launch and under whose
 *     {@code pdv/} directory the figure is written
 * @param identity that run's recorded identity: its identifier and spectrum files
 * @param searched the run whose {@code outputs/comet/} holds the pepXML -- {@code run} itself, or
 *     for a derived run (a Percolator rerun) the run it was derived from, whose Comet output it
 *     rescored
 * @param psmId the PSM's identifier as Percolator wrote it
 * @param pdv the installed PDV
 * @param java the Java runtime PDV runs on
 * @param displayEnvironment the variables PDV needs to reach a display ({@code DISPLAY}, {@code
 *     XAUTHORITY}); never {@code HOME} or {@code LANG}
 */
public record PsmFigureRequest(
        RunLayout run,
        RunIdentity identity,
        RunLayout searched,
        String psmId,
        PdvInstallation pdv,
        JavaRuntime java,
        Map<String, String> displayEnvironment) {

    /**
     * Validates and copies the request.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public PsmFigureRequest {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(searched, "searched");
        Objects.requireNonNull(psmId, "psmId");
        Objects.requireNonNull(pdv, "pdv");
        Objects.requireNonNull(java, "java");
        displayEnvironment =
                Map.copyOf(Objects.requireNonNull(displayEnvironment, "displayEnvironment"));
    }

    @Override
    public Map<String, String> displayEnvironment() {
        return Map.copyOf(displayEnvironment);
    }

    /**
     * Where the figures of this run are written.
     *
     * @return {@code <run>/pdv/figures}
     */
    Path figuresDirectory() {
        return run.root().resolve(PsmFigures.PDV_DIRECTORY).resolve(PsmFigures.FIGURES_DIRECTORY);
    }
}
