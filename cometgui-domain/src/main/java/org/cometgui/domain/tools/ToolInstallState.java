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
 * What the Tool Manager can say about one tool build on this machine.
 *
 * <p><strong>The last two states are distinct on purpose.</strong> "Upstream publishes nothing for
 * your platform" and "upstream publishes something your machine cannot run" are different sentences
 * to a scientist and have different remedies: the first is answered by choosing another version or
 * registering a local binary, the second by choosing a build with a lower host floor. Percolator
 * makes both real -- 3.09 publishes no Linux portable archive at all, while its Linux {@code .deb}
 * publishes one that needs {@code GLIBC_2.38} and will not load on Debian 12.
 *
 * <p>{@code R-TOOL-03} exists so the second can be said <em>in advance</em>, from the manifest's
 * {@link MinimumHostRequirements}, rather than discovered when the probe fails. A user who is told
 * before downloading 946 KB that this build will not run here has been given something; one who is
 * told afterwards has been given a diagnostic.
 */
public enum ToolInstallState {

    /** An artefact exists for this host and has not been installed yet. */
    NOT_INSTALLED,

    /** An install is running. Progress arrives through an install progress listener. */
    INSTALLING,

    /**
     * Installed, probed and usable. {@code R-TOOL-04} makes this true only when the completion
     * marker written last is present and its recorded checksums still match.
     */
    INSTALLED,

    /**
     * An install was attempted and did not succeed -- a download, a checksum, an extraction or a
     * probe failed. Distinct from the two states below, which are known before anything is
     * attempted.
     */
    FAILED,

    /**
     * Upstream publishes no artefact of this tool and version for this platform, so there is
     * nothing to offer. {@code R-PERC-01} forbids presenting it as a one-click install anyway;
     * saying so plainly is better than an empty list.
     */
    UNAVAILABLE_ON_THIS_PLATFORM,

    /**
     * An artefact exists for this platform but this machine does not meet its declared {@link
     * MinimumHostRequirements} -- too old a glibc, too old a macOS, a missing host library. The
     * artefact is real; this host cannot run it.
     */
    HOST_REQUIREMENTS_NOT_MET
}
