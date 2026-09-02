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

package org.cometgui.domain.ports;

import org.cometgui.domain.run.RunId;

/**
 * Supplies the identifier for a new workflow run.
 *
 * <p>The seam {@code R-PROC-01} calls the "run-ID source". A run identifier reaches the provenance
 * manifest and the run directory name, so a test that has to assert either of those needs to choose
 * the value: the production implementation derives one from the clock and a random component, and a
 * test hands out {@code run-0001}, {@code run-0002} in order.
 */
@FunctionalInterface
public interface RunIdSource {

    /**
     * Produces an identifier for a run that is about to start.
     *
     * <p>Implementations must not return the same identifier twice within one installation: the
     * identifier names a directory, and a collision overwrites an earlier run's evidence.
     *
     * @return a new run identifier, never {@code null}
     */
    RunId newRunId();
}
