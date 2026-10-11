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

package org.cometgui.archtests.fixtures;

import java.io.IOException;
import org.cometgui.domain.secrets.Credential;
import org.cometgui.workflow.limelight.LimelightUploadAttempt;
import org.cometgui.workflow.limelight.LimelightUploadConfirmation;
import org.cometgui.workflow.limelight.LimelightUploadWatcher;
import org.cometgui.workflow.limelight.LimelightUploads;

/**
 * Starts an upload from somewhere that is neither the Limelight section nor the upload service --
 * the shape of a run step that uploads by itself, which {@code R-SEC-04} forbids. A committed
 * fixture for {@code LimelightUploadRulesTest}.
 */
public final class StartsAnUploadFromTheEngine {

    private StartsAnUploadFromTheEngine() {}

    /**
     * @param uploads the service
     * @param confirmation a confirmation from somewhere
     * @param key a key
     * @return an upload no scientist asked for
     * @throws IOException never, here
     */
    static LimelightUploadAttempt automatic(
            LimelightUploads uploads, LimelightUploadConfirmation confirmation, Credential key)
            throws IOException {
        return uploads.upload(confirmation, key, LimelightUploadWatcher.NONE);
    }
}
