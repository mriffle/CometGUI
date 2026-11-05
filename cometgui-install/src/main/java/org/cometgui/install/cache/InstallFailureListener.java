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

import java.util.Optional;

/**
 * Told why an install failed, before anyone is told that it did.
 *
 * <p>{@link ArtefactInstaller#install(org.cometgui.install.registry.ArtefactRecord,
 * org.cometgui.domain.tools.InstallProgressListener,
 * org.cometgui.install.download.DownloadCancellation, InstallFailureListener)} calls this exactly
 * once for an install that ends {@link org.cometgui.domain.tools.InstallPhase#FAILED}, and calls it
 * <strong>before</strong> the terminal progress report -- so a caller that records the reason here
 * has it in place when a listener that saw {@code FAILED} asks what happened. It is never called
 * for an install that finished, or for one the user cancelled: cancelling is not failing. The one
 * edge is an {@link Error} thrown while the pipeline is released after every step has run: it is
 * told here, and the terminal report stays {@code DONE}, as it always was for that case.
 *
 * <p>Why a second channel rather than a reason on the progress report: {@link
 * org.cometgui.domain.tools.InstallProgress} is the domain's, and its terminal phase is a fact the
 * user interface reads, while the step and the exception are the installer's own vocabulary. The
 * exception is also what the installer throws, so a caller that waited for the throw would learn
 * the reason only after the terminal report had already gone out -- which is how Phase 14's gate
 * run ended with a Percolator install reported {@code FAILED} and no record of which step had
 * failed.
 *
 * <p>Called on the install's own thread, with the install lock still held. A listener that blocks
 * stalls the install; one that throws replaces the failure it was told about.
 */
@FunctionalInterface
public interface InstallFailureListener {

    /**
     * Reports why an install failed.
     *
     * @param step the step that was running when it failed, or empty when it failed outside the
     *     eight steps: taking the install lock, clearing what an earlier attempt left, or releasing
     *     the lock and the staging directory afterwards
     * @param failure what the step threw, unchanged
     */
    void onInstallFailed(Optional<InstallStep> step, Throwable failure);
}
