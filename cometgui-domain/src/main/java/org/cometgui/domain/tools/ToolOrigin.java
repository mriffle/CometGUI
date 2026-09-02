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

package org.cometgui.domain.tools;

/**
 * Where a tool binary came from: CometGUI installed it, or the user pointed at one already on the
 * machine.
 *
 * <p>The distinction is not bookkeeping. A managed install is attributable to an upstream artefact
 * with a pinned URL and a verified SHA-256, so its provenance record can name what was downloaded;
 * a local binary has no such attribution, and {@code R-TOOL-08} requires it to be probed
 * conservatively -- absent positive evidence of a capability, the capability is absent. Local
 * registration is also the documented remedy wherever no managed XML-capable build exists for a
 * platform, so it is a first-class state and not a fallback.
 */
public enum ToolOrigin {

    /** CometGUI downloaded, verified, installed and owns this binary. */
    MANAGED(true),

    /** The user pointed CometGUI at a binary already on the machine ({@code R-TOOL-08}). */
    LOCAL(false);

    private final boolean managed;

    ToolOrigin(boolean managed) {
        this.managed = managed;
    }

    /**
     * Whether the application installed and owns this binary.
     *
     * <p>This is the value {@code org.cometgui.provenance.manifest.ToolRecord} records as its
     * {@code managed} flag.
     *
     * @return {@code true} for a managed install, {@code false} for a registered local binary
     */
    public boolean isManaged() {
        return managed;
    }
}
