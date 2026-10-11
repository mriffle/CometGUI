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

import java.nio.file.Path;
import org.cometgui.domain.secrets.Credential;
import org.cometgui.tools.limelight.LimelightServer;
import org.cometgui.tools.limelight.LimelightUploadClient;
import org.cometgui.tools.limelight.LimelightUploadListener;
import org.cometgui.tools.limelight.LimelightUploadOutcome;
import org.cometgui.tools.limelight.LimelightUploadRequest;

/**
 * Uploads with the client directly, outside {@code org.cometgui.workflow.limelight}: the shortcut
 * that would send a file with no destination shown, no confirmation, no log and no event. A
 * committed fixture, so that the proof that {@code LimelightUploadRulesTest}'s rule rejects it runs
 * on every build.
 */
public final class UploadsOutsideTheService {

    private UploadsOutsideTheService() {}

    /**
     * @param key a key
     * @param file a file
     * @return an outcome nobody recorded
     */
    static LimelightUploadOutcome shortcut(Credential key, Path file) {
        return new LimelightUploadClient()
                .upload(
                        new LimelightUploadRequest(
                                LimelightServer.parse("https://limelight.invalid/limelight"),
                                1,
                                file,
                                "0".repeat(64)),
                        key,
                        LimelightUploadListener.NONE);
    }
}
