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

import java.io.IOException;
import java.util.function.Consumer;
import org.cometgui.domain.tools.InstallHandle;
import org.cometgui.domain.tools.InstallProgressListener;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.params.percolator.resolution.CompatibilityAction;
import org.cometgui.ui.viewmodel.params.RunNotStartedException;
import org.cometgui.ui.viewmodel.params.RunObserver;
import org.cometgui.ui.viewmodel.results.ResultsRun;
import org.cometgui.workflow.limelight.LimelightConversionRefusedException;
import org.cometgui.workflow.limelight.LimelightConverterStatus;

/**
 * What the Limelight section asks of the application: the converter's state and its install, a
 * run's readiness and settings, the conversion, and -- for a run whose Percolator wrote no XML --
 * the Percolator install and Phase 09's compatible rerun. Every method may block and is called off
 * the interface thread.
 *
 * <p>Every answer is the workflow's ({@code org.cometgui.workflow.limelight}): the port finds the
 * run in the session's project, and hands text, switches and actions to it. It refuses a run that
 * is executing whatever the caller checked.
 *
 * <p>The output path is given as the text the scientist typed: blank for a new file under the run's
 * {@code outputs/limelight/}, otherwise a path the workflow accepts or refuses.
 */
public interface LimelightPort {

    /**
     * Where the Limelight converter stands, from the Tool Manager.
     *
     * @return the status
     * @throws IOException if it cannot be read
     */
    LimelightConverterStatus converter() throws IOException;

    /**
     * Starts installing the converter through the Tool Manager and returns as soon as it is under
     * way.
     *
     * @param version the version a {@link LimelightConverterStatus.State#NOT_INSTALLED} status
     *     named
     * @param listener told of progress and of how the install ends, on the install's own thread
     * @return the handle that cancels it
     * @throws IOException if no install can be started
     */
    InstallHandle installConverter(ToolVersion version, InstallProgressListener listener)
            throws IOException;

    /**
     * Starts installing a Percolator build through the Tool Manager -- the build a compatible rerun
     * needs when it is not installed yet ({@link CompatibilityAction#installFirst()}) -- and
     * returns as soon as it is under way.
     *
     * @param version the build's version, as the action names it
     * @param listener told of progress and of how the install ends, on the install's own thread
     * @return the handle that cancels it
     * @throws IOException if no install can be started
     */
    InstallHandle installPercolator(ToolVersion version, InstallProgressListener listener)
            throws IOException;

    /**
     * Whether a run can be converted now with its saved settings, and every reason it cannot;
     * whether its own Percolator wrote the XML the converter reads, and what is offered when it did
     * not. Reads and hashes; starts and writes nothing.
     *
     * @param run the run, as the Results section lists it
     * @param output the output path as typed; blank for the default
     * @return the run's state
     * @throws IOException if the run cannot be found or read
     */
    LimelightRunState check(ResultsRun run, String output) throws IOException;

    /**
     * Saves a run's settings -- each value checked by the workflow's own rule, decoy import refused
     * when the run's Percolator output holds no decoy PSMs -- and then answers as {@link #check}.
     *
     * @param run the run
     * @param options the settings as typed
     * @param output the output path as typed; blank for the default
     * @return the run's state with the settings saved
     * @throws IOException naming the setting refused and the rule, or why nothing could be saved;
     *     nothing was saved
     */
    LimelightRunState save(ResultsRun run, LimelightOptions options, String output)
            throws IOException;

    /**
     * Saves a run's settings and converts it to Limelight XML through the workflow's one conversion
     * path, with the installed converter.
     *
     * @param run the run
     * @param options the settings as typed
     * @param output the output path as typed; blank for a new file
     * @param log told each line the converter writes to its log, as it arrives, on the process
     *     service's threads
     * @return the valid, recorded Limelight XML
     * @throws LimelightConversionRefusedException naming every prerequisite not met; nothing was
     *     started
     * @throws IOException naming why the converter's output was not kept -- the output judge's
     *     reason -- or why nothing could be run
     */
    LimelightResult convert(
            ResultsRun run, LimelightOptions options, String output, Consumer<String> log)
            throws IOException;

    /**
     * Starts Phase 09's compatible rerun of a run's Percolator stage with the build an action
     * names: a new, derived run reusing the run's merged PIN, running no Comet. The action must
     * still be the one the run's compatibility offers now.
     *
     * @param run the run whose Percolator wrote no XML
     * @param action the rerun the section showed
     * @param settings the Percolator settings the rerun runs with
     * @param observer told the plan, every transition and the outcome
     * @return the derived run and its handle
     * @throws RunNotStartedException naming why nothing was started
     */
    LimelightRerun rerun(
            ResultsRun run,
            CompatibilityAction action,
            PercolatorSettings settings,
            RunObserver observer)
            throws RunNotStartedException;
}
