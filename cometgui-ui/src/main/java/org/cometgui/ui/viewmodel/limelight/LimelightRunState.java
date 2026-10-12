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

package org.cometgui.ui.viewmodel.limelight;

import java.util.Objects;
import org.cometgui.workflow.limelight.LimelightReadiness;

/**
 * What the port read about one run: the workflow's answer to "can this run be converted now, and if
 * not, why" ({@code LimelightConversions.check}) -- which carries whether the run's own Percolator
 * wrote the XML the converter reads and the action offered when it did not -- and the run's saved
 * Limelight settings, as text.
 *
 * @param readiness the workflow's answer, for the settings saved and the output asked for
 * @param saved the run's saved settings, or the workflow's defaults when none were saved
 */
public record LimelightRunState(LimelightReadiness readiness, LimelightOptions saved) {

    /**
     * Requires both.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public LimelightRunState {
        Objects.requireNonNull(readiness, "readiness");
        Objects.requireNonNull(saved, "saved");
    }
}
