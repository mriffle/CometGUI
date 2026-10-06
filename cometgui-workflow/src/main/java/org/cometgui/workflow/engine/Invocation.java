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
import org.cometgui.domain.ports.ToolCommand;

/**
 * One tool invocation a step asks the engine to run.
 *
 * @param stageId the process-service stage identifier, which names the invocation's log file and is
 *     the {@code stageId} of its provenance record; must be declared by the step
 * @param tool the tool being run
 * @param command the argument array, working directory and environment; recorded exactly
 */
public record Invocation(String stageId, ToolIdentity tool, ToolCommand command) {

    /**
     * Requires every component.
     *
     * @throws NullPointerException naming a component that is {@code null}
     */
    public Invocation {
        Objects.requireNonNull(stageId, "stageId");
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(command, "command");
    }
}
