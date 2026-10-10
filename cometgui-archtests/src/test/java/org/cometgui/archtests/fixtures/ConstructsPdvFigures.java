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

import java.time.Duration;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.tools.pdv.PdvFigures;

/**
 * Constructs PDV's figure command over a process runner it was handed, outside {@code
 * org.cometgui.workflow.pdv}: the "just draw it here" shortcut that would launch PDV without the
 * run's event log ever recording it. A committed fixture, so that the proof that {@code
 * PdvLaunchPathRuleTest}'s rule rejects it runs on every build.
 */
public final class ConstructsPdvFigures {

    private ConstructsPdvFigures() {}

    /**
     * @param processes any process runner -- the application's own, unrecorded
     * @return a figure command no record will be made for
     */
    static PdvFigures shortcut(ProcessRunner processes) {
        return new PdvFigures(processes, Duration.ofMinutes(2));
    }
}
