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

package org.cometgui.ui.viewmodel.percolator;

import java.nio.file.Path;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolRegistrationException;

/**
 * The Tool Manager, as the Percolator section sees it (decision P9-12): the Percolator builds this
 * computer may be shown, and the registration of a local binary. The composition root implements it
 * over the one Tool Manager; a test replaces it.
 *
 * <p>Both methods read files or start processes -- {@code offers()} verifies every installed build
 * against its recorded checksums, a registration runs the binary to read its version and probe its
 * capabilities -- so <strong>neither is ever called on the JavaFX application thread</strong>:
 * {@link PercolatorViewModel} calls both from its background executor and applies the answers on
 * the interface thread.
 */
public interface PercolatorPort {

    /**
     * Every Percolator build the Tool Manager offers on this computer, in its order -- installed,
     * registered, installable, and the ones that cannot run here -- or why there is no Tool
     * Manager.
     *
     * @return the answer; never an exception for a missing Tool Manager
     */
    PercolatorOffers offers();

    /**
     * Registers a Percolator binary already on this computer ({@code R-TOOL-08}): the Tool Manager
     * runs it, reads its version, checks the minimum, records its checksums and probes its
     * capabilities. From then on it is among {@link #offers()}.
     *
     * @param executable the file the scientist chose, absolute
     * @return the registered build, {@code LOCAL} and installed
     * @throws ToolRegistrationException if it is not a Percolator this product supports, or cannot
     *     be probed, saying why
     */
    ToolOffer register(Path executable) throws ToolRegistrationException;
}
