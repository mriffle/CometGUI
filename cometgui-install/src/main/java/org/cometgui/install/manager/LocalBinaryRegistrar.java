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

package org.cometgui.install.manager;

import java.nio.file.Path;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolRegistrationException;

/**
 * Probes one tool's binary that was already on the machine, and turns it into a row.
 *
 * <p>The seam exists for the same reason {@code CapabilityProber} does: the adapter that knows how
 * to run Percolator lives in {@code org.cometgui.tools}, which cannot see {@code
 * org.cometgui.install} and which this module cannot see either. So the vocabulary is the domain's
 * alone and the composition root joins the two with a method reference -- the route unit 7 proved
 * for the probe.
 *
 * <p>One registrar registers one tool. {@link ManagedToolManager} holds them by {@link
 * org.cometgui.domain.tools.ToolName} and refuses a tool it has none for, which is why nothing here
 * takes a tool: a registrar that could be asked about the wrong tool would have to answer that
 * question twice, once at the map and once inside itself.
 */
@FunctionalInterface
public interface LocalBinaryRegistrar {

    /**
     * Probes a local file and registers it if it is the tool this registrar is for.
     *
     * @param executable the absolute path of the executable or JAR the user chose
     * @return the offer for the registered binary, with {@link
     *     org.cometgui.domain.tools.ToolOrigin#LOCAL}
     * @throws ToolRegistrationException if the file is not that tool, is too old, or cannot be
     *     probed -- with a message naming what was found and what was required
     */
    ToolOffer register(Path executable) throws ToolRegistrationException;
}
