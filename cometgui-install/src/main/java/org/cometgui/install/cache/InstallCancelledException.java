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

package org.cometgui.install.cache;

import java.io.IOException;
import java.util.Objects;

/**
 * The user asked for the install to stop, and it stopped.
 *
 * <p>A separate type from {@link InstallRejectedException} for the reason {@code
 * org.cometgui.install.download.DownloadCancellation} gives: a user who cancelled a 99 MB PDV
 * download has not encountered an error, and an installer that reports one tells a scientist the
 * wrong thing. {@link org.cometgui.domain.tools.InstallPhase#CANCELLED} and {@link
 * org.cometgui.domain.tools.InstallPhase#FAILED} are two terminal phases for the same reason.
 *
 * <p>Nothing is left behind: the staging directory is discarded, so the tool cache is exactly as it
 * was before the install started.
 */
public final class InstallCancelledException extends IOException {

    private static final long serialVersionUID = 1L;

    /** Where the install had got to when the caller asked it to stop. */
    private final InstallStep nextStep;

    /** Whether it stopped inside that step rather than at the boundary before it. */
    private final boolean duringTheStep;

    /**
     * Creates the cancellation of an install stopped at a step boundary.
     *
     * @param artefact how the artefact is named in a diagnostic
     * @param nextStep the step that would have run next
     */
    InstallCancelledException(String artefact, InstallStep nextStep) {
        super(
                "the install of "
                        + Objects.requireNonNull(artefact, "artefact")
                        + " was cancelled before step "
                        + Objects.requireNonNull(nextStep, "nextStep").number()
                        + ", "
                        + nextStep
                        + "; nothing was written to the tool cache");
        this.nextStep = nextStep;
        this.duringTheStep = false;
    }

    /*
     * A CANCELLATION THAT COULD NOT WAIT FOR A STEP BOUNDARY, which this class could not express
     * until phase 05 unit 8.  The pipeline asks the caller between steps, but it also hands the
     * same DownloadCancellation down into the transfer, which honours it between chunks -- because
     * a 99 MB download that only stopped at the end of its own step would not be cancellable at
     * all.  That arrives as a DownloadCancelledException in the middle of step 1, and nothing
     * translated it, so it left ArtefactInstaller through the catch (IOException) arm and the
     * listener was told the install had FAILED.  InstallHandle.cancel() forbids that in as many
     * words: "never InstallPhase#FAILED -- a user who cancelled has not encountered an error".
     *
     * The cause is kept rather than flattened into the message, because
     * DownloadCancelledException.bytesTransferred() is the evidence that the transfer really was
     * under way, and a number a log can read is worth more than a sentence it cannot.
     */
    InstallCancelledException(String artefact, InstallStep step, IOException duringThatStep) {
        super(
                "the install of "
                        + Objects.requireNonNull(artefact, "artefact")
                        + " was cancelled during step "
                        + Objects.requireNonNull(step, "step").number()
                        + ", "
                        + step
                        + "; nothing was written to the tool cache",
                Objects.requireNonNull(duringThatStep, "duringThatStep"));
        this.nextStep = step;
        this.duringTheStep = true;
    }

    /**
     * The step the install stopped at.
     *
     * <p>The step that would have run next when the cancellation was noticed at a boundary, and the
     * step that <em>was</em> running when it was noticed inside one. {@link
     * #cancelledDuringThatStep()} says which of the two this is; the distinction matters because
     * cancelling a 99 MB transfer and cancelling between two steps are the same user action landing
     * in different places, and this phase found the product treating them differently.
     *
     * @return the step the install stopped at
     */
    public InstallStep nextStep() {
        return nextStep;
    }

    /**
     * Whether the install stopped inside {@link #nextStep()} rather than before it.
     *
     * @return {@code true} when a transfer was abandoned part way, {@code false} when the
     *     cancellation was noticed at the boundary before the step ran
     */
    public boolean cancelledDuringThatStep() {
        return duringTheStep;
    }
}
