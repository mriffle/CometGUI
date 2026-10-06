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

package org.cometgui.workflow.storage;

import java.time.Instant;
import java.util.Objects;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.RunLayout;

/**
 * A run whose directory exists but whose {@code run.json} has not been written yet: what {@link
 * RunStore#reserve} hands back.
 *
 * <p>The order is the point. The canonical {@code comet.params} must be written into {@link
 * RunLayout#cometParamsFile()} and hashed <em>before</em> {@code run.json} can record its hashes,
 * so a run is reserved first -- its id and creation time fixed, its directories made -- and its
 * identity recorded second, with {@link RunStore#record}.
 *
 * @param runId the run's identifier, from the injected run-id source
 * @param created when it was created, from the injected clock, truncated to milliseconds
 * @param layout its directory
 */
public record ReservedRun(RunId runId, Instant created, RunLayout layout) {

    /**
     * Validates presence.
     *
     * @throws NullPointerException naming a component that is {@code null}
     */
    public ReservedRun {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(created, "created");
        Objects.requireNonNull(layout, "layout");
    }
}
