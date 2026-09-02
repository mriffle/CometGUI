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

    /**
     * Creates the cancellation.
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
    }

    /**
     * The step that would have run next.
     *
     * @return the step the install stopped before
     */
    public InstallStep nextStep() {
        return nextStep;
    }
}
