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
 * What opening a finished run in PDV's {@code denovo-gui} window needs.
 *
 * @param run the run whose results are shown: its mzTab, its event log and its PDV directory
 * @param identity that run's recorded identity: its identifier and spectrum files
 * @param searched the run whose {@code outputs/comet/} holds the pepXML: {@code run} itself, or for
 *     a derived run the run it was derived from
 * @param pdv the installed PDV
 * @param java the Java runtime PDV runs on
 * @param displayEnvironment what PDV needs to reach a display; never {@code HOME} or {@code LANG}
 */
public record PdvWindowRequest(
        RunLayout run,
        RunIdentity identity,
        RunLayout searched,
        PdvInstallation pdv,
        JavaRuntime java,
        Map<String, String> displayEnvironment) {

    /** The window's directory in the run's PDV directory: its working directory and home. */
    public static final String WINDOW_DIRECTORY = "window";

    /** PDV's home, inside the window's directory (design decision P11-17). */
    public static final String HOME_DIRECTORY = "home";

    /**
     * Validates and copies the request.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public PdvWindowRequest {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(searched, "searched");
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
     * Where the window runs.
     *
     * @return {@code <run>/pdv/window}
     */
    public Path windowDirectory() {
        return run.root().resolve(PsmFigures.PDV_DIRECTORY).resolve(WINDOW_DIRECTORY);
    }
}
