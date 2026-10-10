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

package org.cometgui.ui.viewmodel.visualisation;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.tools.InstallHandle;
import org.cometgui.domain.tools.InstallProgressListener;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.ui.viewmodel.results.ResultsRun;
import org.cometgui.workflow.pdv.DrawnSpectrum;
import org.cometgui.workflow.pdv.PdvSelection;
import org.cometgui.workflow.pdv.PdvStatus;
import org.cometgui.workflow.pdv.PdvWindowStatus;

/**
 * What the Visualisation section asks of the application: PDV's state, its install, drawing a PSM's
 * spectrum, and the live control path -- a run opened in PDV's {@code denovo-gui} window, the PSM
 * selected in the Results section shown in it, and the window closed. Every method may block and is
 * called off the interface thread.
 */
public interface VisualisationPort {

    /**
     * Where PDV stands, from the Tool Manager.
     *
     * @return the status
     * @throws IOException if it cannot be read
     */
    PdvStatus pdv() throws IOException;

    /**
     * Starts installing PDV through the Tool Manager and returns as soon as it is under way.
     *
     * @param version the version a {@link PdvStatus.State#NOT_INSTALLED} status named
     * @param listener told of progress and of how the install ends, on the install's own thread
     * @return the handle that cancels it
     * @throws IOException if no install can be started
     */
    InstallHandle install(ToolVersion version, InstallProgressListener listener) throws IOException;

    /**
     * Draws one PSM's annotated spectrum with the installed PDV, recording the launch in the run's
     * event log before PDV starts.
     *
     * @param run the run the PSM is shown from
     * @param psmId the PSM's identifier as Percolator wrote it
     * @return the verified figure
     * @throws IOException naming why nothing was drawn: the run is executing, PDV is not installed,
     *     no documented mapping, an unsupported spectrum file, or PDV's own failure
     */
    DrawnSpectrum draw(ResultsRun run, String psmId) throws IOException;

    /**
     * Opens a finished run in PDV's window: closes the run's open window, prepares the run's mzTab,
     * and launches PDV through the one recording launch path. Returns once PDV has been started.
     *
     * @param run the run
     * @param listener told each state the window passes through -- ready, failed with why, closed
     *     -- on the application's own threads
     * @return the window's state when this returns
     * @throws IOException naming why nothing was opened: the run is executing, PDV is not
     *     installed, an mzTab that cannot be made faithfully, an unsupported spectrum file, or PDV
     *     not starting
     */
    PdvWindowStatus openInPdv(ResultsRun run, Consumer<PdvWindowStatus> listener)
            throws IOException;

    /**
     * Shows a PSM of a run in the run's PDV window, debounced; returns at once.
     *
     * @param run the run
     * @param decoy whether the PSM is a row of Percolator's decoy table
     * @param psmId its Percolator {@code PSMId}
     * @return what became of it, with PDV's own answer; never completed exceptionally
     */
    CompletableFuture<PdvSelection> showInPdv(ResultsRun run, boolean decoy, String psmId);

    /**
     * Closes a run's PDV window, if one is open, and waits within a bound for PDV to end.
     *
     * @param run the run
     */
    void closePdv(RunId run);

    /** Closes every PDV window this application opened: the application is exiting. */
    void closeAllPdv();
}
