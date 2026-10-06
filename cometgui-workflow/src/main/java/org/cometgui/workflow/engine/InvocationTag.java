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

package org.cometgui.workflow.engine;

import java.util.Objects;
import org.cometgui.domain.run.StageTag;

/**
 * The process service's stage tag for one invocation: its identifier names the log file and the
 * provenance record; its display name tells the console which step it belongs to.
 *
 * @param id the invocation's stage identifier
 * @param displayName the identifier and the step's display name
 */
record InvocationTag(String id, String displayName) implements StageTag {

    InvocationTag {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
    }
}
