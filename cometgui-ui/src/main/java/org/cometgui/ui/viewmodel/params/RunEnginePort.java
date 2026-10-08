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

package org.cometgui.ui.viewmodel.params;

import java.nio.file.Path;
import java.util.List;
import org.cometgui.params.comet.model.CometParameters;

/**
 * The workflow engine, as the Run section's view-models see it (decision P8-16): the pre-run check
 * and the start of a run. The composition root implements it over the one workflow engine, the
 * selected Comet, the Percolator the Percolator section names (Phase 09) and the session's project;
 * a test replaces it.
 *
 * <p>The interface layer may not reach the tool adapters or the installer, and the engine's
 * collaborators -- the process service, the console sink, the Tool Manager -- are chosen in the
 * composition root, so this port is where the two meet. Everything it does reads files or starts
 * processes, so <strong>neither method is ever called on the JavaFX application thread</strong>:
 * {@link RunViewModel} calls both from its background executor and applies the answers on the
 * interface thread.
 */
public interface RunEnginePort {

    /**
     * The engine's half of Run readiness for a configuration: which Comet would run it, the pre-run
     * check over the files (decoy census, index header, the one validator) and over the Percolator
     * build that would rescore it, and, after an earlier run in this session, the rerun preview.
     * Writes nothing into a run and launches nothing. Blocks while it reads the FASTA.
     *
     * <p>A Percolator half that cannot run ({@link PercolatorRequest#problems()}) is not a reason
     * this port repeats -- {@link RunViewModel} shows those itself -- but the port still checks the
     * Comet half, so that every reason is shown at once, and gives no rerun preview.
     *
     * @param model the parameters as they are now
     * @param spectra the spectrum files, in the order chosen
     * @param percolator the Percolator half as the Percolator section has it now
     * @return the answer; a failure to check is an answer with a reason, never an exception
     */
    EngineCheck check(CometParameters model, List<Path> spectra, PercolatorRequest percolator);

    /**
     * Starts a run of a configuration: a retry of the session's last run when nothing about the
     * configuration changed, otherwise a new run. Before the engine starts, {@link
     * RunObserver#planned} is called with the plan; every step change and the end of the run arrive
     * on the observer from engine threads.
     *
     * @param model the parameters, which become the run's immutable parameter file
     * @param spectra the spectrum files, in order
     * @param percolator the Percolator half: the build, its settings and the enabled downstream
     *     stages; the run rescores the merged PIN with it
     * @param observer told the plan, every transition and the outcome
     * @return the running attempt, to cancel
     * @throws RunNotStartedException if the run was refused or could not be created -- the
     *     Percolator half cannot run, among others -- saying why; nothing was launched
     */
    ActiveRun start(
            CometParameters model,
            List<Path> spectra,
            PercolatorRequest percolator,
            RunObserver observer)
            throws RunNotStartedException;
}
