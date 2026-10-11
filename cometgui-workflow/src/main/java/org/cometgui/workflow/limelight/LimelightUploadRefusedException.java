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

package org.cometgui.workflow.limelight;

import java.io.IOException;
import java.util.Objects;

/**
 * An upload, or its destination, refused before anything was sent: nothing reached the network and
 * nothing was recorded.
 */
public final class LimelightUploadRefusedException extends IOException {

    private static final long serialVersionUID = 1L;

    /** Why. */
    public enum Reason {
        /** The run is still executing: its results are not final. */
        RUN_EXECUTING,
        /** The server address cannot be used. */
        SERVER,
        /** The project id is not a positive whole number. */
        PROJECT,
        /** The file is not one of the run's recorded Limelight conversions. */
        NOT_A_CONVERSION,
        /** The file's SHA-256 is not the one its conversion recorded, or not the confirmed one. */
        FILE_CHANGED,
        /** The confirmation was already used for an upload. */
        CONFIRMATION_USED
    }

    private final Reason reason;

    /**
     * A refusal.
     *
     * @param reason why
     * @param message the reason in words, for the scientist
     */
    public LimelightUploadRefusedException(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /**
     * Why it was refused.
     *
     * @return the reason
     */
    public Reason reason() {
        return reason;
    }
}
