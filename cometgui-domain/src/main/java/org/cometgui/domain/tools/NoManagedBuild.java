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

import java.util.Objects;

/**
 * A tool of which CometGUI can install no build at all on this machine, because the artefact
 * manifest names no build of any release that this platform can run.
 *
 * <p><strong>The absence of a manifest row is the whole fact.</strong> Nothing here names a
 * platform, a release or a reason that is not derived from the manifest: a tool is in this state on
 * a host exactly when the manifest's selection for that host and tool is empty. The case that
 * motivated it is {@code D-011} -- upstream has never published an x86-64 macOS Comet, so an Intel
 * Mac has no managed Comet -- but the rule does not know that, and an upstream asset added to the
 * manifest later removes the state without a line of code changing.
 *
 * <p>What the user can do instead is register a binary already on the machine ({@code R-TOOL-08}),
 * where the product can register one for that tool. Whether it can is part of the fact, because
 * offering a registration that would then be refused is the same mistake as offering an install
 * that cannot run.
 *
 * @param tool the tool with no managed build here
 * @param host the machine the manifest was asked about
 * @param localRegistration whether a binary of this tool already on the machine can be registered
 *     through {@link ToolManager#registerLocalBinary}
 */
public record NoManagedBuild(ToolName tool, HostPlatform host, boolean localRegistration) {

    /**
     * Validates the fact.
     *
     * @throws NullPointerException if {@code tool} or {@code host} is {@code null}
     */
    public NoManagedBuild {
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(host, "host");
    }
}
