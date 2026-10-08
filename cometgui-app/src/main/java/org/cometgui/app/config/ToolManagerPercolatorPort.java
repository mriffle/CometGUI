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

package org.cometgui.app.config;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolRegistrationException;
import org.cometgui.ui.viewmodel.percolator.PercolatorOffers;
import org.cometgui.ui.viewmodel.percolator.PercolatorPort;

/**
 * The Percolator section's port over the one Tool Manager (decision P9-12): its Percolator offers,
 * in its order, and {@link ToolManager#registerLocalBinary} for a Percolator binary already on this
 * computer. The interface never sees the installer; this is the whole of what it is given.
 *
 * <p>Called off the JavaFX thread: reading the offers verifies installed builds' checksums, and a
 * registration runs the binary through the process service to read its version and probe it.
 */
public final class ToolManagerPercolatorPort implements PercolatorPort {

    private final Supplier<Optional<ToolManager>> tools;

    private final String toolsUnavailable;

    /**
     * The port.
     *
     * @param tools the Tool Manager, or empty when this machine has none
     * @param toolsUnavailable why there is no Tool Manager, shown when {@code tools} gives none
     */
    public ToolManagerPercolatorPort(
            Supplier<Optional<ToolManager>> tools, String toolsUnavailable) {
        this.tools = Objects.requireNonNull(tools, "tools");
        this.toolsUnavailable = Objects.requireNonNull(toolsUnavailable, "toolsUnavailable");
    }

    @Override
    public PercolatorOffers offers() {
        Optional<ToolManager> manager = tools.get();
        if (manager.isEmpty()) {
            return PercolatorOffers.unavailable(
                    toolsUnavailable.isBlank() ? "no reason was given" : toolsUnavailable);
        }
        return PercolatorOffers.of(
                manager.get().offers().stream()
                        .filter(offer -> offer.tool() == ToolName.PERCOLATOR)
                        .toList());
    }

    @Override
    public ToolOffer register(Path executable) throws ToolRegistrationException {
        Objects.requireNonNull(executable, "executable");
        Optional<ToolManager> manager = tools.get();
        if (manager.isEmpty()) {
            throw new ToolRegistrationException(
                    "No Percolator can be registered, because this machine has no Tool Manager: "
                            + toolsUnavailable);
        }
        return manager.get().registerLocalBinary(ToolName.PERCOLATOR, executable);
    }
}
