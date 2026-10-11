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

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The scientist's explicit confirmation of one {@link LimelightDestination}: what {@link
 * LimelightUploads#upload} requires, and the only thing that can start an upload ({@code R-SEC-04};
 * phase 12, design decision P12-10).
 *
 * <p>Only {@link LimelightDestination#confirm()} makes one, so an upload cannot be asked for
 * without a destination having been checked and shown first. It is good for one upload: a second
 * upload needs a second explicit action, as "an explicit user action every time" requires.
 */
public final class LimelightUploadConfirmation {

    private final LimelightDestination destination;
    private final AtomicBoolean used = new AtomicBoolean();

    LimelightUploadConfirmation(LimelightDestination destination) {
        this.destination = Objects.requireNonNull(destination, "destination");
    }

    /**
     * What was confirmed.
     *
     * @return the destination
     */
    public LimelightDestination destination() {
        return destination;
    }

    /**
     * Uses the confirmation up.
     *
     * @return {@code true} the first time, {@code false} ever after
     */
    boolean use() {
        return used.compareAndSet(false, true);
    }
}
