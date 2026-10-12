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

import java.nio.file.Path;
import java.util.Objects;
import org.cometgui.domain.run.RunId;
import org.cometgui.ui.viewmodel.params.ActiveRun;

/**
 * A compatible Percolator rerun the Limelight section started: the new, derived run and the handle
 * that cancels it.
 *
 * @param run the derived run's identifier
 * @param directory the derived run's directory
 * @param handle cancels the rerun
 */
public record LimelightRerun(RunId run, Path directory, ActiveRun handle) {

    /**
     * Requires every component.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public LimelightRerun {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(handle, "handle");
    }
}
